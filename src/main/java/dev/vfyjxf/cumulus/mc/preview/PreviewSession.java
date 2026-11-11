package dev.vfyjxf.cumulus.mc.preview;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.context.StructureAttempt;
import dev.vfyjxf.cumulus.kernel.context.StructureInstance;
import dev.vfyjxf.cumulus.kernel.context.StructureManager;
import dev.vfyjxf.cumulus.kernel.context.StructureSpec;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.engine.MatchOutcome;
import dev.vfyjxf.cumulus.kernel.engine.MatchRequest;
import dev.vfyjxf.cumulus.kernel.engine.MatchResult;
import dev.vfyjxf.cumulus.kernel.remedy.CellMark;
import dev.vfyjxf.cumulus.kernel.remedy.Marker;
import dev.vfyjxf.cumulus.kernel.remedy.MarkerKind;
import dev.vfyjxf.cumulus.kernel.remedy.OverlayModel;
import dev.vfyjxf.cumulus.kernel.remedy.Remedy;
import dev.vfyjxf.cumulus.kernel.remedy.RemedyContext;
import dev.vfyjxf.cumulus.kernel.remedy.SolvePreferences;
import dev.vfyjxf.cumulus.kernel.remedy.SolveResult;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.world.CaptureRequest;
import dev.vfyjxf.cumulus.kernel.world.CaptureResult;
import dev.vfyjxf.cumulus.kernel.world.EntityWindow;
import dev.vfyjxf.cumulus.kernel.world.SnapshotLease;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import dev.vfyjxf.cumulus.mc.LevelAccess;
import dev.vfyjxf.cumulus.mc.LevelCapture;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewControlPayload;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * One server-side preview: the cycle, the current overlay marks and ghosts, the last block revision
 * it saw and its lifetime. Everything here runs on the server thread and never writes the world.
 */
final class PreviewSession {

    /** The ghost description cap; overflow is reported, never silent (design 15 §3). */
    static final int maxGhosts = 512;
    /** The marker cap keeps one payload bounded while the HUD stays readable. */
    static final int maxMarkers = 48;
    /** Retract the ghosts after this many ticks without a control (4.6); the session stays alive. */
    static final long retractAfterTicks = 1_200L;
    private static final int maxRemedies = 8;
    private static final int nextGroupSize = 8;
    private static final int maxPreferredCells = 16;
    private static final int maxElements = 1_000_000;

    /** The open outcome: a live session, or the reason a command should report. */
    record OpenResult(@Nullable PreviewSession session, @Nullable String failure) {

        static OpenResult opened(PreviewSession session) {
            return new OpenResult(session, null);
        }

        static OpenResult failed(String failure) {
            return new OpenResult(null, failure);
        }
    }

    private final UUID playerId;
    private final ServerLevel level;
    private final NamespacedId blueprintId;
    private final Blueprint blueprint;
    private final PreviewCycle cycle;
    private final Map<NamespacedId, LatticePoint> pinChoice = new TreeMap<>();

    private @Nullable SnapshotLease lease;
    private @Nullable OverlayModel overlay;
    private List<GhostCell> ghosts = List.of();
    private List<Marker> markers = List.of();
    private @Nullable ClaimView claims;
    private List<String> findings = List.of();
    private long revision;
    private long syncedAt;
    private boolean retracted;
    private boolean invalidated;
    private boolean materialsNoted;
    private @Nullable String lastNote;
    private boolean cancelled;

    private PreviewSession(ServerPlayer player, Blueprint blueprint, PreviewCycle cycle) {
        this.playerId = player.getUUID();
        this.level = player.serverLevel();
        this.blueprintId = blueprint.id();
        this.blueprint = blueprint;
        this.cycle = cycle;
        this.syncedAt = level.getGameTime();
    }

    static OpenResult open(ServerPlayer player, Blueprint blueprint, List<LatticePoint> origins, Direction facing) {
        Direction horizontal = facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
        Turn facingTurn = PreviewCycle.turnOf(horizontal);
        if (facingTurn == null) {
            facingTurn = Turn.none;
        }
        PreviewCycle cycle = cycle(blueprint, origins, facingTurn, player.serverLevel());
        if (cycle.isEmpty()) {
            return OpenResult.failed(
                blueprint.anchors().isEmpty()
                        ? "no expressible orientation for " + blueprint.id().text()
                        : "no formed anchor of " + blueprint.id().text() + " at the origin"
            );
        }
        PreviewSession session = new PreviewSession(player, blueprint, cycle);
        String failure = session.compute();
        if (failure != null) {
            session.close();
            return OpenResult.failed(failure);
        }
        session.sync();
        return OpenResult.opened(session);
    }

    private static PreviewCycle cycle(Blueprint blueprint, List<LatticePoint> origins, Turn facing, ServerLevel level) {
        List<Siting> anchored = PreviewAnchors.resolve(level, blueprint, origins);
        return anchored.isEmpty()
                ? PreviewCycle.build(blueprint, origins, facing, level)
                : PreviewCycle.ofSittings(blueprint, anchored, level);
    }

    UUID playerId() {
        return playerId;
    }

    ServerLevel level() {
        return level;
    }

    long syncedAt() {
        return syncedAt;
    }

    boolean cancelled() {
        return cancelled;
    }

    boolean retracted() {
        return retracted;
    }

    @Nullable
    OverlayModel overlay() {
        return overlay;
    }

    List<GhostCell> ghosts() {
        return ghosts;
    }

    List<Marker> markers() {
        return markers;
    }

    /** Applies one control; false means the session is over. */
    boolean apply(PreviewControlPayload.Control control, @Nullable LatticePoint target) {
        invalidated = false;
        switch (control) {
            case turnCw -> note(cycle.turn(true), "no other turn is expressible at this origin");
            case turnCcw -> note(cycle.turn(false), "no other turn is expressible at this origin");
            case flip -> note(cycle.flip(), "the mirrored pose is filtered out");
            case nextOrigin -> note(cycle.origin(1), "no other origin candidate is expressible");
            case prevOrigin -> note(cycle.origin(-1), "no other origin candidate is expressible");
            case reset -> cycle.reset();
            case resetIdentity -> {
                if (!cycle.resetIdentity()) {
                    lastNote = "the identity pose is filtered out; reset to the first candidate";
                }
            }
            case cyclePart -> cyclePart(target);
            case confirm -> {
                return confirm();
            }
            case cancel -> {
                cancelled = true;
                return false;
            }
        }
        retracted = false;
        if (compute() != null) {
            return false;
        }
        sync();
        return true;
    }

    /** Re-validates and re-syncs after a world change; false means no candidate survived. */
    boolean refresh(boolean invalidated) {
        this.invalidated = invalidated;
        retracted = false;
        if (compute() != null) {
            return false;
        }
        sync();
        return true;
    }

    /** 4.6: the ghosts go away, the session and its plan stay. */
    void retract() {
        retracted = true;
        ghosts = List.of();
        List<Marker> built = new ArrayList<>(markers);
        built.add(0, new Marker(boundsLocus(), "session retracted; press P to recall", MarkerKind.info));
        markers = cap(built, boundsLocus());
        sync();
    }

    /** True while the world under the session still matches the revision it computed against. */
    boolean isValid() {
        if (invalidated) {
            return false;
        }
        OverlayModel model = overlay;
        if (model == null) {
            return false;
        }
        return revision == LevelAccess.blockRevision(level, model.bounds());
    }

    PreviewSyncPayload payload() {
        return new PreviewSyncPayload(
            blueprintId,
            cycle.candidates(),
            cycle.current(),
            overlayOrEmpty(),
            ghosts,
            markers
        );
    }

    void sync() {
        ServerPlayer player = player();
        if (player == null) {
            return;
        }
        syncedAt = level.getGameTime();
        PacketDistributor.sendToPlayer(player, payload());
    }

    void close() {
        if (lease != null) {
            lease.close();
            lease = null;
        }
        ghosts = List.of();
        overlay = null;
    }

    private boolean confirm() {
        ServerPlayer player = player();
        if (player == null) {
            return false;
        }
        if (compute() != null) {
            return false;
        }
        PreviewConfirmHandler handler = PreviewService.confirmHandler();
        boolean accepted = handler
                .confirm(player, new PreviewConfirmHandler.Request(blueprintId, cycle.slot().siting(), findings));
        if (accepted) {
            cancelled = true;
            return false;
        }
        lastNote = "the build refused; the plan stays here — confirm again once the shortage is supplied";
        compute();
        sync();
        return true;
    }

    private void note(boolean applied, String why) {
        if (!applied) {
            lastNote = why;
        }
    }

    /** Recomputes the overlay, the ghosts and the markers; null means success, else the failure. */
    @Nullable
    private String compute() {
        if (lease != null) {
            lease.close();
            lease = null;
        }
        StructureKernel kernel = LevelAccess.kernel();
        LevelCapture capture = LevelAccess.capture(level);
        ClaimLedger ledger = LevelAccess.claims(level);
        StructureManager structures = LevelAccess.structures(level);
        World world = LevelAccess.world(level);
        OwnerId owner = LevelAccess.owner();
        if (kernel == null
                || capture == null
                || ledger == null
                || structures == null
                || world == null
                || owner == null) {
            return "StructureCumulus is not running";
        }
        PreviewCycle.Slot slot = cycle.slot();
        Siting siting = slot.siting();
        Cuboid bounds = siting.toWorld(blueprint.bounds());
        if (bounds == null) {
            return "the sited bounds of " + blueprintId.text() + " leave the world";
        }
        PreviewExpected.Outcome expected = PreviewExpected.of(blueprint, siting.placement().orientation());
        CaptureResult captured = capture
                .capture(new CaptureRequest(bounds, policy(siting)), siting.placement().orientation());
        if (!(captured instanceof CaptureResult.Captured live)) {
            return "capture of " + blueprintId.text() + " was refused";
        }
        lease = live.lease();
        ClaimView view = remainingClaims(ledger, bounds, siting);
        claims = view;
        Map<LatticeKey, CellMark> marks = derivedMarks(expected.states(), siting);
        List<Marker> built = new ArrayList<>();
        StructureAttempt draft = structures.begin(world, new StructureSpec(owner, siting, blueprintId));
        try {
            MatchRequest request = MatchRequest
                    .of(blueprint, siting, live.lease(), draft.id(), owner, Budget.unbounded(), view);
            MatchResult checked = kernel.check(request);
            findings = checked.findings().findings().stream().map(finding -> finding.code().text()).distinct().toList();
            SolveResult solved = kernel.solve(
                checked.findings(),
                new RemedyContext(
                    blueprint,
                    siting,
                    view,
                    view.revision(),
                    outcomeOf(checked),
                    null,
                    maxRemedies,
                    new SolvePreferences(preferredCells(marks), pins(siting))
                )
            );
            for (Remedy remedy : solved.remedies()) {
                if (remedy instanceof Remedy.Preview preview) {
                    // the louder mark wins: a claim conflict outranks the derived missing, a weak
                    // provider unknown never hides what the declarations already prove absent
                    for (Map.Entry<LatticeKey, CellMark> mark : preview.overlay().cells().entrySet()) {
                        marks.merge(mark.getKey(), mark.getValue(), CellMark::louder);
                    }
                    built.addAll(preview.overlay().markers());
                    break;
                }
            }
        } finally {
            abort(draft);
        }
        overlay = new OverlayModel(bounds, marks, List.of());
        ghosts = ghosts(marks, expected.states(), siting);
        markers = cap(sessionMarkers(built, marks, bounds), Locus.world(bounds.asSet()));
        revision = LevelAccess.blockRevision(level, bounds);
        syncedAt = level.getGameTime();
        return null;
    }

    private OverlayModel overlayOrEmpty() {
        OverlayModel model = overlay;
        return model == null ? new OverlayModel(Cuboid.of(blueprint.bounds().min()), Map.of(), List.of()) : model;
    }

    private Locus boundsLocus() {
        OverlayModel model = overlay;
        return model == null ? Locus.world(blueprint.bounds().asSet()) : Locus.world(model.bounds().asSet());
    }

    private List<Marker> sessionMarkers(List<Marker> solved, Map<LatticeKey, CellMark> marks, Cuboid bounds) {
        List<Marker> built = new ArrayList<>(solved);
        Locus locus = Locus.world(bounds.asSet());
        Map<String, Integer> filtered = new LinkedHashMap<>();
        for (PreviewCycle.Filtered refused : cycle.filtered()) {
            String key = refused.filter().block().text() + " cannot express " + refused.filter().property() + "="
                    + refused.filter().value();
            filtered.merge(key, 1, Integer::sum);
        }
        if (cycle.anchored()) {
            built.add(new Marker(locus, "anchored pose: the formed anchor fixes the siting (1.5)", MarkerKind.info));
        }
        for (Map.Entry<String, Integer> entry : filtered.entrySet()) {
            built.add(
                new Marker(locus, entry.getValue() + " orientation(s) filtered: " + entry.getKey(), MarkerKind.warning)
            );
        }
        int drawn = ghosts.size();
        int expectedGhosts = 0;
        for (CellMark mark : marks.values()) {
            if (mark == CellMark.missing || mark == CellMark.expected) {
                expectedGhosts++;
            }
        }
        if (drawn < expectedGhosts) {
            built.add(
                new Marker(
                    locus,
                    "ghost cap " + maxGhosts + " reached: showing " + drawn + " of " + expectedGhosts + " cells",
                    MarkerKind.warning
                )
            );
        }
        built.addAll(blockedMarkers(marks));
        List<LatticePoint> next = nextGroup(marks);
        if (!next.isEmpty()) {
            built.add(new Marker(locus, "next: " + describe(next), MarkerKind.anchor));
        }
        if (invalidated) {
            built.add(
                new Marker(
                    locus,
                    "session invalidated: the world changed inside the footprint ("
                            + PreviewCodes.sessionInvalidated.text() + ")",
                    MarkerKind.warning
                )
            );
        }
        if (!materialsNoted) {
            materialsNoted = true;
            built.add(
                new Marker(
                    locus,
                    "materials: no item source is bound to a session yet, so there is no shortage list",
                    MarkerKind.info
                )
            );
        }
        if (lastNote != null) {
            built.add(new Marker(locus, lastNote, MarkerKind.info));
            lastNote = null;
        }
        return built;
    }

    /** 6.2: a blocked cell names its claim's owner and structure, and offers no force-break. */
    private List<Marker> blockedMarkers(Map<LatticeKey, CellMark> marks) {
        List<Marker> built = new ArrayList<>();
        ClaimView view = claims;
        StructureManager structures = LevelAccess.structures(level);
        if (view == null || structures == null) {
            return built;
        }
        for (Map.Entry<LatticeKey, CellMark> mark : marks.entrySet()) {
            if (mark.getValue() != CellMark.blocked) {
                continue;
            }
            for (ClaimRow row : view.rows()) {
                if (!row.cell().equals(mark.getKey())) {
                    continue;
                }
                StructureInstance owner = structures.find(row.instance());
                String name = owner == null ? row.instance().toString() : owner.owner().id().text();
                built.add(
                    new Marker(
                        Locus.world(Cuboid.of(mark.getKey().point()).asSet()),
                        "blocked by " + name + " (structure " + row.instance() + "); coordinate with its owner",
                        MarkerKind.warning
                    )
                );
            }
        }
        return built;
    }

    private List<LatticePoint> nextGroup(Map<LatticeKey, CellMark> marks) {
        List<LatticePoint> missing = new ArrayList<>();
        for (Map.Entry<LatticeKey, CellMark> mark : marks.entrySet()) {
            if (mark.getValue() == CellMark.missing || mark.getValue() == CellMark.expected) {
                missing.add(mark.getKey().point());
            }
        }
        missing.sort(LatticePoint::compareTo);
        return missing.size() <= nextGroupSize ? List.copyOf(missing) : List.copyOf(missing.subList(0, nextGroupSize));
    }

    private static String describe(List<LatticePoint> cells) {
        StringBuilder text = new StringBuilder();
        for (LatticePoint cell : cells) {
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append('(').append(cell.x()).append(',').append(cell.y()).append(',').append(cell.z()).append(')');
        }
        return text.toString();
    }

    private List<GhostCell> ghosts(
        Map<LatticeKey, CellMark> marks,
        Map<LatticePoint, BlockState> expected,
        Siting siting
    ) {
        List<GhostCell> built = new ArrayList<>();
        for (Map.Entry<LatticePoint, BlockState> entry : expected.entrySet()) {
            LatticePoint world = siting.toWorld(entry.getKey());
            if (world == null) {
                continue;
            }
            CellMark mark = marks.get(world.key());
            if (mark == CellMark.present
                    || mark == CellMark.wrong
                    || mark == CellMark.extra
                    || mark == CellMark.blocked
                    || mark == CellMark.unknown) {
                continue;
            }
            if (built.size() >= maxGhosts) {
                break;
            }
            BlockState state = entry.getValue();
            built.add(
                new GhostCell(world, PreviewExpected.blockId(state.getBlock()), PreviewExpected.properties(state))
            );
        }
        return List.copyOf(built);
    }

    /** The declaration-derived marks: present when the live state already matches, missing otherwise. */
    private Map<LatticeKey, CellMark> derivedMarks(Map<LatticePoint, BlockState> expected, Siting siting) {
        Map<LatticeKey, CellMark> marks = new TreeMap<>();
        for (Map.Entry<LatticePoint, BlockState> entry : expected.entrySet()) {
            LatticePoint world = siting.toWorld(entry.getKey());
            if (world == null) {
                continue;
            }
            BlockState live = level.getBlockState(new BlockPos(world.x(), world.y(), world.z()));
            marks.put(world.key(), live.equals(entry.getValue()) ? CellMark.present : CellMark.missing);
        }
        return marks;
    }

    private List<CuboidSet> preferredCells(Map<LatticeKey, CellMark> marks) {
        List<LatticePoint> ordered = nextGroup(marks);
        List<CuboidSet> preferred = new ArrayList<>();
        for (LatticePoint cell : ordered) {
            if (preferred.size() >= maxPreferredCells) {
                break;
            }
            preferred.add(Cuboid.of(cell).asSet());
        }
        return preferred;
    }

    private Map<NamespacedId, CuboidSet> pins(Siting siting) {
        Map<NamespacedId, CuboidSet> world = new TreeMap<>();
        for (Map.Entry<NamespacedId, LatticePoint> pin : pinChoice.entrySet()) {
            CuboidSet cells = siting.toWorld(Cuboid.of(pin.getValue()).asSet());
            if (cells != null) {
                world.put(pin.getKey(), cells);
            }
        }
        return world;
    }

    /** 2.1: picks the next legal cell of the clicked part; the pin reaches the kernel as a preference. */
    private void cyclePart(@Nullable LatticePoint target) {
        if (target == null) {
            lastNote = "no ghost part under the crosshair";
            return;
        }
        Siting siting = cycle.slot().siting();
        LatticePoint local = siting.toLocal(target);
        if (local == null) {
            lastNote = "the clicked cell is not part of the siting";
            return;
        }
        for (Bay bay : blueprint.bays()) {
            if (!bay.cells().contains(local)) {
                continue;
            }
            List<LatticePoint> legal = PreviewExpected.cells(bay.cells());
            if (legal.size() < 2) {
                lastNote = "part " + bay.id().text() + " has one legal cell";
                return;
            }
            LatticePoint chosen = pinChoice.get(bay.id());
            int index = chosen == null ? 0 : Math.floorMod(legal.indexOf(chosen) + 1, legal.size());
            pinChoice.put(bay.id(), legal.get(index));
            lastNote = "part " + bay.id().text() + " pinned to " + describe(List.of(legal.get(index)));
            return;
        }
        lastNote = "no bay covers the clicked cell";
    }

    private SnapshotPolicy policy(Siting siting) {
        List<EntityWindow> windows = new ArrayList<>();
        for (EntityWindow window : blueprint.reads().windows()) {
            Cuboid world = siting.toWorld(window.cells());
            if (world != null) {
                windows.add(new EntityWindow(world, window.type()));
            }
        }
        return new SnapshotPolicy(
            maxElements,
            SnapshotPolicy.defaultMaxDepth,
            blueprint.reads().kinds(),
            blueprint.reads().traits(),
            windows
        );
    }

    private ClaimView remainingClaims(ClaimLedger ledger, Cuboid bounds, Siting siting) {
        ClaimView view = ledger.read(bounds);
        List<ClaimRow> kept = new ArrayList<>(view.rows().size());
        for (ClaimRow row : view.rows()) {
            if (!row.siting().equals(siting)) {
                kept.add(row);
            }
        }
        return new ClaimView(kept, view.revision(), view.available());
    }

    private static @Nullable MatchOutcome outcomeOf(MatchResult result) {
        return switch (result) {
            case MatchResult.Admitted admitted -> admitted.outcome();
            case MatchResult.Formed formed -> formed.outcome();
            case MatchResult.Undecided undecided -> undecided.partial();
            case MatchResult.Failed failed -> failed.outcome();
            case MatchResult.Dropped ignored -> null;
        };
    }

    private static void abort(StructureAttempt draft) {
        try {
            draft.abort();
        } catch (IllegalStateException ignored) {
            // the manager already closed it
        }
    }

    private static List<Marker> cap(List<Marker> markers, Locus locus) {
        if (markers.size() <= maxMarkers) {
            return List.copyOf(markers);
        }
        List<Marker> capped = new ArrayList<>(markers.subList(0, maxMarkers - 1));
        capped.add(
            new Marker(
                locus,
                "marker cap " + maxMarkers + " reached: showing " + capped.size() + " of " + markers.size(),
                MarkerKind.warning
            )
        );
        return List.copyOf(capped);
    }

    private @Nullable ServerPlayer player() {
        return level.getServer().getPlayerList().getPlayer(playerId);
    }
}
