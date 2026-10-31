package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimVerdict;
import dev.vfyjxf.cumulus.kernel.claim.SettleRequest;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.StructureAttempt;
import dev.vfyjxf.cumulus.kernel.context.StructureEvent;
import dev.vfyjxf.cumulus.kernel.context.StructureInstance;
import dev.vfyjxf.cumulus.kernel.context.StructureManager;
import dev.vfyjxf.cumulus.kernel.context.StructureSpec;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.mc.SavedClaims;
import dev.vfyjxf.cumulus.mc.ServerState;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.NullMarked;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Dev-only stage setters for the client-test scenarios: they drive the production {@link ServerState}
 * stores directly because no production command can form several owners on one siting. The stores are
 * package-private to the production mod, so the dev-only harness reaches them reflectively.
 */
@NullMarked
final class VisualFixtureSupport {

    static final OwnerId ownerA = OwnerId.of(NamespacedId.of("structurecumulus", "visual_owner_a"));
    static final OwnerId ownerB = OwnerId.of(NamespacedId.of("structurecumulus", "visual_owner_b"));
    static final OwnerId ownerC = OwnerId.of(NamespacedId.of("structurecumulus", "visual_owner_c"));
    static final OwnerId ownerD = OwnerId.of(NamespacedId.of("structurecumulus", "visual_owner_d"));

    private static final List<OwnerId> fixtureOwners = List.of(ownerA, ownerB, ownerC, ownerD);
    private static final NamespacedId blueprint = NamespacedId.of("structurecumulus", "visual_fixture");
    private static final ContextKey<Long> visualCountKey = ContextKeys
            .of(NamespacedId.of("structurecumulus", "visual_count"), Long.class, Retention.instance);
    private static final ContextKey<String> visualLabelKey = ContextKeys
            .of(NamespacedId.of("structurecumulus", "visual_label"), String.class, Retention.instance);
    private static final NamespacedId claimBay = NamespacedId.of("structurecumulus", "visual_claim");
    private static final Cuboid lattice = Cuboid.of(
        LatticePoint.minXz,
        LatticePoint.minY,
        LatticePoint.minXz,
        LatticePoint.maxXz,
        LatticePoint.maxY,
        LatticePoint.maxXz
    );

    private static final Map<ServerLevel, List<StructureInstance>> unloadedFixtures = new HashMap<>();

    private static final Method currentMethod;
    private static final Method structuresMethod;
    private static final Method claimsMethod;
    private static final Method worldMethod;

    static {
        try {
            currentMethod = ServerState.class.getDeclaredMethod("current");
            structuresMethod = ServerState.class.getDeclaredMethod("structures", ServerLevel.class);
            claimsMethod = ServerState.class.getDeclaredMethod("claims", ServerLevel.class);
            worldMethod = ServerState.class.getDeclaredMethod("world", ServerLevel.class);
            structuresMethod.setAccessible(true);
            claimsMethod.setAccessible(true);
            worldMethod.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private VisualFixtureSupport() {}

    static boolean stageOverlap(ServerLevel level, LatticePoint origin) {
        ServerState state = state();
        if (state == null) {
            return false;
        }
        clear(level);
        LatticePoint right = Objects.requireNonNull(origin.offset(1, 0, 0), "right cell");
        LatticePoint back = Objects.requireNonNull(origin.offset(0, 0, 1), "back cell");
        Siting shared = Siting.of(origin, Orientation.identity);
        form(state, level, ownerA, shared, cells(origin));
        form(state, level, ownerB, shared, cells(origin, right));
        form(state, level, ownerC, shared, cells(origin, back));
        claim(state, level, ownerA, shared, cells(origin), Sharing.shared);
        claim(state, level, ownerB, shared, cells(origin, right), Sharing.shared);
        claim(state, level, ownerC, shared, cells(origin, back), Sharing.shared);
        return true;
    }

    /**
     * Two owners on one cell: owner A holds an exclusive row, owner B overlaps without a row and
     * carries the real claim-conflict finding, so the highlight layer can show the red conflict.
     */
    static boolean stageConflict(ServerLevel level, LatticePoint origin) {
        ServerState state = state();
        if (state == null) {
            return false;
        }
        clear(level);
        Siting siting = Siting.of(origin, Orientation.identity);
        form(state, level, ownerA, siting, cells(origin));
        claim(state, level, ownerA, siting, cells(origin), Sharing.exclusive);
        StructureManager manager = structures(state, level);
        StructureAttempt attempt = manager.begin(world(state, level), new StructureSpec(ownerB, siting, blueprint));
        Finding conflict = Finding.builder(Codes.claimConflict).phase(Phase.settle)
                .message(ownerB.id() + " cannot hold the exclusive cell " + origin).build();
        attempt.publish(attempt.id(), Map.of(), FindingBundle.of(List.of(conflict)), Spend.zero);
        attempt.commit(cells(origin));
        return true;
    }

    /** One context-rich shared instance: two keys published and one shared claim row. */
    static boolean stageContext(ServerLevel level, LatticePoint origin) {
        ServerState state = state();
        if (state == null) {
            return false;
        }
        clear(level);
        Siting siting = Siting.of(origin, Orientation.identity);
        StructureManager manager = structures(state, level);
        StructureAttempt attempt = manager.begin(world(state, level), new StructureSpec(ownerA, siting, blueprint));
        Map<ContextKey<?>, Object> context = VisualContexts
                .forMember(VisualPartRole.controller, "structurecumulus:context", "上下文演示", 0, 2, "已捕获");
        attempt.publish(attempt.id(), context, FindingBundle.empty(), new Spend(2, 0));
        StructureInstance instance = attempt.commit(cells(origin));
        VisualContexts.record(instance, context);
        VisualPart part = new VisualPart(
            instance.id(),
            "上下文控制器",
            VisualPartRole.controller,
            VisualPartRole.controller.color(),
            VisualPartRole.controller.description(),
            cells(origin)
        );
        VisualPartState.set(List.of(part));
        claim(state, level, ownerA, siting, cells(origin), Sharing.shared);
        return true;
    }

    /** Two independent fixture instances with one shared claim each. */
    static boolean stagePair(ServerLevel level, LatticePoint origin) {
        ServerState state = state();
        if (state == null) {
            return false;
        }
        clear(level);
        Siting first = Siting.of(origin, Orientation.identity);
        LatticePoint backOrigin = Objects.requireNonNull(origin.offset(0, 0, 2), "back origin");
        Siting second = Siting.of(backOrigin, Orientation.identity);
        form(state, level, ownerA, first, cells(origin));
        claim(state, level, ownerA, first, cells(origin), Sharing.shared);
        form(state, level, ownerB, second, cells(backOrigin));
        claim(state, level, ownerB, second, cells(backOrigin), Sharing.shared);
        return true;
    }

    /** Root plus six satellites with one claim per member and explicit root-to-satellite links. */
    static boolean stageSet(ServerLevel level, LatticePoint origin) {
        ServerState state = state();
        if (state == null) {
            return false;
        }
        clear(level);
        List<LatticePoint> members = new ArrayList<>();
        members.add(origin);
        int[][] offsets = {{2, 0, 0}, {-2, 0, 0}, {0, 0, 2}, {0, 0, -2}, {2, 0, 2}, {-2, 0, -2}};
        List<VisualSetLink> links = new ArrayList<>();
        for (int[] offset : offsets) {
            LatticePoint satellite = Objects
                    .requireNonNull(origin.offset(offset[0], offset[1], offset[2]), "satellite");
            members.add(satellite);
            links.add(new VisualSetLink(origin, satellite));
        }
        for (LatticePoint member : members) {
            Siting siting = Siting.of(member, Orientation.identity);
            form(state, level, ownerD, siting, cells(member));
            claim(state, level, ownerD, siting, cells(member), Sharing.shared);
        }
        VisualClientState.setSetSummary(
            new VisualSetSummary(
                "structurecumulus:visual_set",
                members.size(),
                members.size(),
                members.size(),
                7,
                64,
                links
            )
        );
        return true;
    }

    /** One instance with a half-turned siting and world-frame bounds/claims. */
    static boolean stageOrientation(ServerLevel level, LatticePoint origin) {
        ServerState state = state();
        if (state == null) {
            return false;
        }
        clear(level);
        Orientation orientation = Orientation.of(Turn.half, Tilt.level, Flip.none);
        Siting siting = Siting.of(origin, orientation);
        Cuboid localBounds = Cuboid.of(0, 0, 0, 2, 1, 1);
        CuboidSet worldBounds = Objects.requireNonNull(siting.toWorld(localBounds.asSet()), "world bounds");
        form(state, level, ownerA, siting, worldBounds);
        claim(state, level, ownerA, siting, worldBounds, Sharing.shared);
        VisualClientState.setSetSummary(null);
        return true;
    }

    /** A compact 8x8 grid of 64 one-cell instances with one shared claim each. */
    static boolean stageLarge(ServerLevel level, LatticePoint origin) {
        ServerState state = state();
        if (state == null) {
            return false;
        }
        clear(level);
        int index = 0;
        for (int gridX = 0; gridX < 8; gridX++) {
            for (int gridZ = 0; gridZ < 8; gridZ++) {
                LatticePoint member = Objects.requireNonNull(origin.offset(gridX * 2, 0, gridZ * 2), "large member");
                Siting siting = Siting.of(member, Orientation.identity);
                form(state, level, ownerA, siting, cells(member));
                claim(state, level, ownerA, siting, cells(member), Sharing.shared);
                index++;
            }
        }
        if (index != 64) {
            throw new IllegalStateException("the large fixture must form 64 instances, formed " + index);
        }
        VisualClientState.setSetSummary(null);
        return true;
    }

    /** Moves the first fixture owner's instance and translates its claim rows with it. */
    static boolean moveFixture(ServerLevel level, LatticePoint origin, int dx, int dy, int dz) {
        ServerState state = state();
        if (state == null) {
            return false;
        }
        StructureManager manager = structures(state, level);
        StructureInstance target = null;
        for (StructureInstance instance : manager.instances()) {
            if (instance.owner().equals(ownerA) && instance.siting().origin().equals(origin)) {
                target = instance;
                break;
            }
        }
        if (target == null) {
            return false;
        }
        LatticePoint newOrigin = origin.offset(dx, dy, dz);
        if (newOrigin == null) {
            return false;
        }
        CuboidSet movedBounds = target.bounds().translate(dx, dy, dz);
        if (movedBounds == null) {
            return false;
        }
        Siting oldSiting = target.siting();
        Siting newSiting = oldSiting.withOrigin(newOrigin);
        SavedClaims ledger = claims(state, level);
        List<ClaimRow> oldRows = new ArrayList<>();
        for (ClaimRow row : ledger.read(lattice).rows()) {
            if (row.owner().equals(target.owner()) && row.siting().equals(oldSiting)) {
                oldRows.add(row);
            }
        }
        ledger.release(target.owner(), oldSiting);
        List<ClaimRow> translated = new ArrayList<>(oldRows.size());
        for (ClaimRow row : oldRows) {
            LatticePoint cell = row.cell().point().offset(dx, dy, dz);
            if (cell == null) {
                return false;
            }
            translated.add(new ClaimRow(LatticeKey.of(cell), row.sharing(), row.owner(), newSiting, row.bay()));
        }
        ClaimVerdict verdict = ledger
                .settle(new SettleRequest(target.owner(), newSiting, translated, ledger.revision()));
        if (!(verdict instanceof ClaimVerdict.Settled)) {
            return false;
        }
        manager.onEvent(new StructureEvent.Moved(target.id(), oldSiting, newSiting, movedBounds));
        VisualClientState.noteEvent("moved");
        return true;
    }

    /** Unloads the level's fixture instances; claim rows stay persisted by manager contract. */
    static boolean unloadFixture(ServerLevel level) {
        ServerState state = state();
        if (state == null) {
            return false;
        }
        StructureManager manager = structures(state, level);
        List<StructureInstance> ours = new ArrayList<>();
        for (StructureInstance instance : manager.instances()) {
            if (fixtureOwners.contains(instance.owner())) {
                ours.add(instance);
            }
        }
        unloadedFixtures.put(level, List.copyOf(ours));
        VisualClientState.expectLifecycle("unloaded");
        VisualClientState.noteEvent("unloaded");
        manager.onEvent(new StructureEvent.Unloaded(world(state, level)));
        return true;
    }

    /** Restores the instances saved by {@link #unloadFixture(ServerLevel)}; claims are already persisted. */
    static boolean loadFixture(ServerLevel level) {
        ServerState state = state();
        if (state == null) {
            return false;
        }
        List<StructureInstance> saved = unloadedFixtures.remove(level);
        if (saved == null) {
            return false;
        }
        structures(state, level).onEvent(new StructureEvent.Loaded(world(state, level), saved));
        VisualClientState.noteEvent("loaded");
        return true;
    }

    /** The failing path: a missing blueprint attempt publishes an error finding, then aborts. */
    static boolean stageError(ServerLevel level, LatticePoint origin) {
        ServerState state = state();
        if (state == null) {
            return false;
        }
        clear(level);
        StructureManager manager = structures(state, level);
        StructureAttempt attempt = manager.begin(
            world(state, level),
            new StructureSpec(
                ownerA,
                Siting.of(origin, Orientation.identity),
                NamespacedId.of("structurecumulus", "missing")
            )
        );
        Finding failure = Finding.builder(Codes.definitionMissing).phase(Phase.settle)
                .message("缺少蓝图 structurecumulus:missing").build();
        attempt.publish(attempt.id(), Map.of(), FindingBundle.of(List.of(failure)), Spend.zero);
        attempt.abort();
        VisualClientState.noteError("缺少蓝图 structurecumulus:missing");
        return true;
    }

    /** Removes every instance and persisted row in the harness world; scenario isolation only. */
    static boolean clearAll(ServerLevel level) {
        ServerState state = state();
        if (state == null) {
            return false;
        }
        StructureManager manager = structures(state, level);
        for (StructureInstance instance : manager.instances()) {
            manager.onEvent(new StructureEvent.Broken(instance.id()));
        }
        SavedClaims ledger = claims(state, level);
        Set<OwnerSiting> keys = new LinkedHashSet<>();
        for (ClaimRow row : ledger.read(lattice).rows()) {
            keys.add(new OwnerSiting(row.owner(), row.siting()));
        }
        for (OwnerSiting key : keys) {
            ledger.release(key.owner(), key.siting());
        }
        unloadedFixtures.remove(level);
        VisualClientState.setSetSummary(null);
        VisualGroupState.clear();
        VisualPartState.clear();
        VisualContextValueState.clear();
        VisualInspectState.clear();
        VisualShowcaseState.clear(level);
        VisualClientState.resetTransientState();
        return true;
    }

    /** Removes every fixture instance and claim row; other owners are never touched. */
    static boolean clear(ServerLevel level) {
        ServerState state = state();
        if (state == null) {
            return false;
        }
        StructureManager manager = structures(state, level);
        List<StructureInstance> ours = new ArrayList<>();
        for (StructureInstance instance : manager.instances()) {
            if (fixtureOwners.contains(instance.owner())) {
                ours.add(instance);
            }
        }
        for (StructureInstance instance : ours) {
            manager.onEvent(new StructureEvent.Broken(instance.id()));
        }
        SavedClaims ledger = claims(state, level);
        Set<OwnerSiting> keys = new LinkedHashSet<>();
        for (ClaimRow row : ledger.read(lattice).rows()) {
            if (fixtureOwners.contains(row.owner())) {
                keys.add(new OwnerSiting(row.owner(), row.siting()));
            }
        }
        for (OwnerSiting key : keys) {
            ledger.release(key.owner(), key.siting());
        }
        unloadedFixtures.remove(level);
        VisualClientState.setSetSummary(null);
        VisualGroupState.clear();
        VisualPartState.clear();
        VisualContextValueState.clear();
        VisualInspectState.clear();
        VisualShowcaseState.clear(level);
        VisualClientState.resetTransientState();
        return true;
    }

    static ServerState state() {
        try {
            return (ServerState) currentMethod.invoke(null);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("cannot read the server state", failure);
        }
    }

    static StructureManager structures(ServerState state, ServerLevel level) {
        try {
            return (StructureManager) structuresMethod.invoke(state, level);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("cannot read the structure manager", failure);
        }
    }

    static SavedClaims claims(ServerState state, ServerLevel level) {
        try {
            return (SavedClaims) claimsMethod.invoke(state, level);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("cannot read the claim ledger", failure);
        }
    }

    static World world(ServerState state, ServerLevel level) {
        try {
            return (World) worldMethod.invoke(state, level);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("cannot read the level world", failure);
        }
    }

    static StructureInstance form(
        ServerState state,
        ServerLevel level,
        OwnerId owner,
        Siting siting,
        CuboidSet bounds
    ) {
        return form(state, level, owner, siting, bounds, Map.of(), new Spend(1, 0));
    }

    static StructureInstance form(
        ServerState state,
        ServerLevel level,
        OwnerId owner,
        Siting siting,
        CuboidSet bounds,
        Map<ContextKey<?>, Object> context,
        Spend spend
    ) {
        StructureManager manager = structures(state, level);
        StructureAttempt attempt = manager.begin(world(state, level), new StructureSpec(owner, siting, blueprint));
        attempt.publish(attempt.id(), context, FindingBundle.empty(), spend);
        return attempt.commit(bounds);
    }

    static void claim(
        ServerState state,
        ServerLevel level,
        OwnerId owner,
        Siting siting,
        CuboidSet cells,
        Sharing sharing
    ) {
        List<ClaimRow> rows = new ArrayList<>();
        for (Cuboid box : cells.boxes()) {
            for (int x = box.min().x(); x < box.max().x(); x++) {
                for (int y = box.min().y(); y < box.max().y(); y++) {
                    for (int z = box.min().z(); z < box.max().z(); z++) {
                        LatticePoint cell = LatticePoint.of(x, y, z);
                        rows.add(new ClaimRow(LatticeKey.of(cell), sharing, owner, siting, claimBay));
                    }
                }
            }
        }
        SavedClaims ledger = claims(state, level);
        ClaimVerdict verdict = ledger.settle(new SettleRequest(owner, siting, rows, ledger.revision()));
        if (!(verdict instanceof ClaimVerdict.Settled)) {
            throw new IllegalStateException("the fixture claim was rejected: " + verdict);
        }
    }

    static CuboidSet cells(LatticePoint... cells) {
        List<Cuboid> boxes = new ArrayList<>(cells.length);
        for (LatticePoint cell : cells) {
            boxes.add(Cuboid.of(cell));
        }
        return CuboidSet.of(boxes);
    }

    private record OwnerSiting(OwnerId owner, Siting siting) {}
}
