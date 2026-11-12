package dev.vfyjxf.cumulus.mc.preview;

import dev.vfyjxf.cumulus.StructureCumulus;
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
import dev.vfyjxf.cumulus.kernel.finding.Applicability;
import dev.vfyjxf.cumulus.kernel.remedy.CellMark;
import dev.vfyjxf.cumulus.kernel.remedy.EditPlan;
import dev.vfyjxf.cumulus.kernel.remedy.Marker;
import dev.vfyjxf.cumulus.kernel.remedy.MarkerKind;
import dev.vfyjxf.cumulus.kernel.remedy.OverlayModel;
import dev.vfyjxf.cumulus.kernel.remedy.Remedy;
import dev.vfyjxf.cumulus.kernel.remedy.RemedyContext;
import dev.vfyjxf.cumulus.kernel.remedy.SolvePreferences;
import dev.vfyjxf.cumulus.kernel.remedy.SolveResult;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.Heading;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.OrientationPolicy;
import dev.vfyjxf.cumulus.kernel.world.CaptureRequest;
import dev.vfyjxf.cumulus.kernel.world.CaptureResult;
import dev.vfyjxf.cumulus.kernel.world.EntityWindow;
import dev.vfyjxf.cumulus.kernel.world.SnapshotLease;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import dev.vfyjxf.cumulus.mc.LevelAccess;
import dev.vfyjxf.cumulus.mc.LevelCapture;
import dev.vfyjxf.cumulus.mc.build.BuildMaterials;
import dev.vfyjxf.cumulus.mc.build.CreativeItemSource;
import dev.vfyjxf.cumulus.mc.build.ItemSource;
import dev.vfyjxf.cumulus.mc.build.PlayerInventorySource;
import dev.vfyjxf.cumulus.mc.pref.BuildPreferences;
import dev.vfyjxf.cumulus.mc.pref.PreferencesSavedData;
import dev.vfyjxf.cumulus.mc.pref.SitingCandidateScorer;
import dev.vfyjxf.cumulus.mc.pref.SolvePreferenceAssembler;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;
import dev.vfyjxf.cumulus.mc.preview.net.MaterialStatus;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewControlPayload;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewPresentation;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewSyncPayload;
import dev.vfyjxf.cumulus.mc.preview.net.Stage;
import dev.vfyjxf.cumulus.mc.preview.net.VariantSummary;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * One server-side preview: the ranked cycle, the current overlay marks and ghosts, the last block
 * revision it saw and its lifetime. Everything here runs on the server thread and never writes the
 * world; the one exit is confirm, which runs the build executor of design 14 §10.
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
    private static final int maxElements = 1_000_000;
    private static final int maxShownShortages = 3;

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
    private final @Nullable VariantSummary variants;
    private final PreviewCycle cycle;
    private final BuildPreferences preferences;
    private final ItemSource source;
    private final Map<NamespacedId, LatticePoint> pinChoice = new TreeMap<>();

    private @Nullable SnapshotLease lease;
    private @Nullable OverlayModel overlay;
    private List<GhostCell> ghosts = List.of();
    private List<Marker> markers = List.of();
    private List<LatticePoint> pending = List.of();
    private StageModel.@Nullable Table stageTable;
    private PreviewPresentation presentation = new PreviewPresentation(null, List.of(), -1, List.of(), 0);
    private List<BuildMaterials.MaterialShortage> missing = List.of();
    private List<String> shortages = List.of();
    private boolean planned;
    private @Nullable ClaimView claims;
    private List<String> findings = List.of();
    private long revision;
    private long syncedAt;
    private boolean retracted;
    private boolean invalidated;
    private @Nullable String lastNote;
    private @Nullable NamespacedId variantSwitch;
    private boolean cancelled;

    private PreviewSession(
        ServerPlayer player,
        Blueprint blueprint,
        PreviewCycle cycle,
        BuildPreferences preferences,
        ItemSource source
    ) {
        this.playerId = player.getUUID();
        this.level = player.serverLevel();
        this.blueprintId = blueprint.id();
        this.blueprint = blueprint;
        this.variants = PreviewVariants.summary(blueprint);
        this.cycle = cycle;
        this.preferences = preferences;
        this.source = source;
        this.syncedAt = level.getGameTime();
    }

    static OpenResult open(ServerPlayer player, Blueprint blueprint, List<LatticePoint> origins, Direction facing) {
        ServerLevel level = player.serverLevel();
        PreferencesSavedData store = PreferencesSavedData.get(level);
        BuildPreferences preferences = store.preferences(player.getUUID());
        Heading playerFacing = PreviewCycle.headingOf(facing);
        if (playerFacing == null) {
            playerFacing = Heading.north;
        }
        Turn start = PreviewCycle.turnOf(preferences.startHeading(playerFacing));
        if (start == null) {
            start = Turn.none;
        }
        PreviewCycle cycle = cycle(blueprint, origins, start, level);
        if (cycle.isEmpty()) {
            return OpenResult.failed(
                blueprint.anchors().isEmpty()
                        ? "no expressible orientation for " + blueprint.id().text()
                        : "no formed anchor of " + blueprint.id().text() + " at the origin"
            );
        }
        PreviewSession session = new PreviewSession(
            player,
            blueprint,
            cycle,
            preferences,
            player.isCreative() ? new CreativeItemSource() : new PlayerInventorySource(player)
        );
        session.seedPins(store);
        session.rank(player, playerFacing);
        cycle.lockProgress();
        String failure = session.compute();
        if (failure != null) {
            session.close();
            return OpenResult.failed(failure);
        }
        session.sync();
        return OpenResult.opened(session);
    }

    private static PreviewCycle cycle(Blueprint blueprint, List<LatticePoint> origins, Turn start, ServerLevel level) {
        List<Siting> anchored = PreviewAnchors.resolve(level, blueprint, origins);
        return anchored.isEmpty()
                ? PreviewCycle.build(blueprint, origins, start, level)
                : PreviewCycle.ofSittings(blueprint, anchored, level);
    }

    UUID playerId() {
        return playerId;
    }

    NamespacedId blueprintId() {
        return blueprintId;
    }

    /** The pose this session currently holds; a variant switch carries its origin (design 15 §12). */
    Siting siting() {
        return cycle.slot().siting();
    }

    /** The group summary the client shows, or null for a standalone blueprint. */
    @Nullable
    VariantSummary variants() {
        return variants;
    }

    /** The member a {@code nextVariant} control asked for, read once when the session ends. */
    @Nullable
    NamespacedId variantSwitch() {
        NamespacedId requested = variantSwitch;
        variantSwitch = null;
        return requested;
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

    /** The part cells this session holds, keyed by part id in the declaration frame. */
    Map<NamespacedId, LatticePoint> pins() {
        return Collections.unmodifiableMap(new TreeMap<>(pinChoice));
    }

    /** Applies one control from the player who sent it; false means the session is over. */
    boolean apply(ServerPlayer player, PreviewControlPayload.Control control, @Nullable LatticePoint target) {
        invalidated = false;
        if (cycles(control) && cycle.locked()) {
            lastNote = "the built cells fix this siting; cycling is off (1.5)";
            retracted = false;
            if (compute() != null) {
                return false;
            }
            sync();
            return true;
        }
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
            case nextVariant -> {
                if (variants == null) {
                    lastNote = "this blueprint is not a variant group";
                    break;
                }
                NamespacedId next = PreviewVariants.next(variants, blueprintId, 1);
                if (next == null) {
                    lastNote = "the variant group has no other member";
                    break;
                }
                variantSwitch = next;
                return false;
            }
            case confirm -> {
                return confirm(player);
            }
            case cancel -> {
                cancelled = true;
                return false;
            }
        }
        retracted = false;
        String failure = compute();
        if (failure != null) {
            StructureCumulus.logger.info("preview {} ended: {}", blueprintId.text(), failure);
            return false;
        }
        sync();
        return true;
    }

    /** Re-validates and re-syncs after a world change; false means no candidate survived. */
    boolean refresh(boolean invalidated) {
        this.invalidated = invalidated;
        retracted = false;
        String failure = compute();
        if (failure != null) {
            StructureCumulus.logger.info("preview {} ended: {}", blueprintId.text(), failure);
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
            markers,
            presentation,
            variants
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

    private boolean confirm(ServerPlayer player) {
        if (compute() != null) {
            return false;
        }
        if (lease != null) {
            lease.close();
            lease = null;
        }
        SolvePreferences solvePreferences = SolvePreferenceAssembler
                .assemble(pending, pinChoice, cycle.slot().siting());
        PreviewConfirmHandler handler = PreviewService.confirmHandler();
        boolean accepted = handler.confirm(
            player,
            new PreviewConfirmHandler.Request(blueprintId, cycle.slot().siting(), findings, source, solvePreferences)
        );
        if (accepted) {
            cancelled = true;
            // the built world is the answer: recount it before the session goes away
            if (compute() == null) {
                ghosts = List.of();
                List<Marker> closing = new ArrayList<>(markers);
                closing.add(0, new Marker(boundsLocus(), "build committed; the session closes", MarkerKind.info));
                markers = cap(closing, boundsLocus());
                sync();
            }
            return false;
        }
        lastNote = "the build did not form; the plan stays here — confirm again to continue (5.1)";
        compute();
        sync();
        return true;
    }

    private static boolean cycles(PreviewControlPayload.Control control) {
        return switch (control) {
            case turnCw, turnCcw, flip, nextOrigin, prevOrigin, reset, resetIdentity -> true;
            default -> false;
        };
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
        pending = pendingCells(marks, expected.states(), siting);
        List<Marker> built = new ArrayList<>();
        EditPlan repair = null;
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
                    SolvePreferenceAssembler.assemble(pending, pinChoice, siting)
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
                }
                if (repair == null
                        && remedy instanceof Remedy.Repair candidate
                        && candidate.applicability() == Applicability.machineApplicable) {
                    repair = candidate.plan();
                }
            }
        } finally {
            abort(draft);
        }
        planned = repair != null;
        missing = repair == null
                ? List.of()
                : BuildMaterials.missing(repair, siting, level, source, LevelAccess.materializers());
        shortages = BuildMaterials.shortages(missing);
        overlay = new OverlayModel(bounds, marks, List.of());
        if (stageTable == null) {
            evaluate(expected.states());
        }
        presentation = presentation(marks, siting);
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
        if (cycle.locked()) {
            built.add(new Marker(locus, "locked: the built cells agree with one siting only (1.5)", MarkerKind.info));
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
        StageModel.Table table = stageTable;
        if (table != null && table.truncated()) {
            built.add(
                new Marker(
                    locus,
                    "stage cap " + StageModel.maxStages + " reached: showing " + table.stages().size() + " of "
                            + table.expectedStages() + " stages",
                    MarkerKind.warning
                )
            );
        }
        built.addAll(blockedMarkers(marks));
        built.add(materialsMarker(locus));
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
        if (lastNote != null) {
            built.add(new Marker(locus, lastNote, MarkerKind.info));
            lastNote = null;
        }
        return built;
    }

    /** 4.4: the materials list of the current plan, read from the dry run's item simulation. */
    private Marker materialsMarker(Locus locus) {
        if (!planned) {
            return new Marker(locus, "materials: nothing left to place", MarkerKind.info);
        }
        if (source instanceof CreativeItemSource) {
            return new Marker(locus, "materials: the creative source covers the plan", MarkerKind.info);
        }
        if (shortages.isEmpty()) {
            return new Marker(locus, "materials: " + source.id().text() + " covers the plan", MarkerKind.info);
        }
        StringBuilder text = new StringBuilder("materials: ");
        for (int index = 0; index < shortages.size() && index < maxShownShortages; index++) {
            if (index > 0) {
                text.append("; ");
            }
            text.append(shortages.get(index));
        }
        if (shortages.size() > maxShownShortages) {
            text.append("; +").append(shortages.size() - maxShownShortages).append(" more");
        }
        return new Marker(locus, text.toString(), MarkerKind.warning);
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

    /** The stage table, derived once: it is a function of the blueprint (design 16 §4.3). */
    private void evaluate(Map<LatticePoint, BlockState> expected) {
        stageTable = StageModel.of(blueprint, expected.keySet());
    }

    /** The wire presentation: the table with its satisfied share, the frontier and the plan's shortages. */
    private PreviewPresentation presentation(Map<LatticeKey, CellMark> marks, Siting siting) {
        StageModel.Table table = stageTable;
        List<StageModel.Stage> derived = table == null ? List.of() : table.stages();
        List<Stage> stages = new ArrayList<>(derived.size());
        int frontier = -1;
        for (int index = 0; index < derived.size(); index++) {
            StageModel.Stage stage = derived.get(index);
            int satisfied = 0;
            for (LatticePoint cell : stage.cells()) {
                LatticePoint world = siting.toWorld(cell);
                if (world != null && marks.get(world.key()) == CellMark.present) {
                    satisfied++;
                }
            }
            if (frontier < 0 && satisfied < stage.cells().size()) {
                frontier = index;
            }
            stages.add(new Stage(stage.name(), stage.cells().size(), satisfied, stage.hint(), stage.description()));
        }
        if (frontier < 0 && !stages.isEmpty()) {
            frontier = stages.size() - 1;
        }
        List<MaterialStatus> materials = new ArrayList<>(maxShownShortages);
        int more = 0;
        if (!(source instanceof CreativeItemSource)) {
            more = Math.max(0, missing.size() - maxShownShortages);
            for (int index = 0; index < missing.size() && index < maxShownShortages; index++) {
                BuildMaterials.MaterialShortage shortage = missing.get(index);
                materials.add(new MaterialStatus(itemId(shortage.template()), shortage.need(), shortage.have()));
            }
        }
        return new PreviewPresentation(blueprint.description(), stages, frontier, materials, more);
    }

    /** The ghosts in play order: the stage table's stages in order, each in canonical cell order. */
    private List<GhostCell> ghosts(
        Map<LatticeKey, CellMark> marks,
        Map<LatticePoint, BlockState> expected,
        Siting siting
    ) {
        StageModel.Table table = stageTable;
        List<StageModel.Stage> stages = table == null ? List.of() : table.stages();
        List<GhostCell> built = new ArrayList<>();
        for (int stageIndex = 0; stageIndex < stages.size() && built.size() < maxGhosts; stageIndex++) {
            for (LatticePoint cell : stages.get(stageIndex).cells()) {
                if (built.size() >= maxGhosts) {
                    break;
                }
                BlockState state = expected.get(cell);
                if (state == null) {
                    continue;
                }
                LatticePoint world = siting.toWorld(cell);
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
                built.add(
                    new GhostCell(
                        world,
                        PreviewExpected.blockId(state.getBlock()),
                        PreviewExpected.properties(state),
                        stageIndex
                    )
                );
            }
        }
        return List.copyOf(built);
    }

    private static NamespacedId itemId(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return NamespacedId.of(key.getNamespace(), key.getPath());
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

    /** The declaration-frame cells the current pose still needs, in canonical order. */
    private List<LatticePoint> pendingCells(
        Map<LatticeKey, CellMark> marks,
        Map<LatticePoint, BlockState> expected,
        Siting siting
    ) {
        List<LatticePoint> pendingCells = new ArrayList<>();
        for (LatticePoint local : expected.keySet()) {
            LatticePoint world = siting.toWorld(local);
            if (world == null) {
                continue;
            }
            CellMark mark = marks.get(world.key());
            if (mark == CellMark.missing || mark == CellMark.expected) {
                pendingCells.add(local);
            }
        }
        pendingCells.sort(LatticePoint::compareTo);
        return List.copyOf(pendingCells);
    }

    /** 2.1: picks the next legal cell of the clicked part and remembers it as an implicit signal (7.2). */
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
            LatticePoint pinned = legal.get(index);
            pinChoice.put(bay.id(), pinned);
            PreferencesSavedData.get(level).rememberPart(playerId, blueprintId, bay.id(), pinned);
            lastNote = "part " + bay.id().text() + " pinned to " + describe(List.of(pinned));
            return;
        }
        lastNote = "no bay covers the clicked cell";
    }

    private void seedPins(PreferencesSavedData store) {
        for (Bay bay : blueprint.bays()) {
            LatticePoint remembered = store.partPin(playerId, blueprintId, bay.id());
            if (remembered != null) {
                pinChoice.put(bay.id(), remembered);
            }
        }
    }

    private void rank(ServerPlayer player, Heading playerFacing) {
        BlockPos pos = player.blockPosition();
        LatticePoint position = LatticePoint.inLattice(pos.getX(), pos.getY(), pos.getZ())
                ? LatticePoint.of(pos.getX(), pos.getY(), pos.getZ())
                : cycle.slot().origin();
        SitingCandidateScorer.View view = new SitingCandidateScorer.View(
            position,
            playerFacing,
            SitingCandidateScorer.frontOf(blueprint),
            preferences.history(blueprintId),
            hanging(blueprint)
        );
        cycle.rank(new SitingCandidateScorer(), preferences.weights(), view);
    }

    /** 3.2: a blueprint that declares an inverted pose is a hanging one, so its support is above. */
    private static boolean hanging(Blueprint blueprint) {
        OrientationPolicy policy = blueprint.orientation();
        if (policy == null) {
            return false;
        }
        for (Orientation allowed : policy.allowed()) {
            if (allowed.tilt() == Tilt.inverted) {
                return true;
            }
        }
        return false;
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
