package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.CancelToken;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextView;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.world.CellRead;
import dev.vfyjxf.cumulus.kernel.world.HostType;
import dev.vfyjxf.cumulus.kernel.world.OccupantDescriptor;
import dev.vfyjxf.cumulus.kernel.world.OccupantFilter;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantRef;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.SectionSlot;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class HostExpectationTest {

    private static final World world = World.of(NamespacedId.of("test", "world"));
    private static final SnapshotId snapshot = new SnapshotId(world, 1);
    private static final InstanceId instance = new InstanceId(world, 1);
    private static final LatticePoint cell = LatticePoint.of(2, 0, 3);
    private static final NamespacedId furnace = NamespacedId.of("minecraft", "furnace");
    private static final HostType<String> blockState = HostType.of(NamespacedId.of("structurecumulus", "block_state"));
    private static final FindingCode propertyMismatch = FindingCode.of(FindingCode.Family.mc, 6);
    private static final FindingCode hostNotCaptured = FindingCode.of(FindingCode.Family.mc, 10);

    @Test
    void aCapturedHostValueMatchesAtTheAnchor() {
        HostView view = new HostView();
        view.hosts.put(cell, "lit");
        AtomicReference<LatticePoint> seen = new AtomicReference<>();
        Expectation<OccupantSnapshot> expectation = Expectations.host(blockState, (value, pos) -> {
            seen.set(pos);
            return value.equals("lit");
        }, propertyMismatch, hostNotCaptured, "expected lit");
        assertInstanceOf(Reply.Match.class, expectation.test(context(view)));
        assertEquals(cell, seen.get(), "the predicate sees the subject's anchor");
    }

    @Test
    void aMissingHostValueIsUnreadableWithTheSuppliedAbsentCode() {
        Expectation<OccupantSnapshot> expectation = Expectations
                .host(blockState, (value, pos) -> true, propertyMismatch, hostNotCaptured, "expected lit");
        Reply.Unreadable unreadable = assertInstanceOf(Reply.Unreadable.class, expectation.test(context(new View())));
        Finding finding = Objects.requireNonNull(unreadable.findings().primary(), "finding");
        assertEquals(hostNotCaptured, finding.code(), "a missing host value is never a mismatch");
        assertEquals(Phase.check, finding.phase());
    }

    @Test
    void anAbsentValueUsesTheCallersCode() {
        FindingCode callerAbsent = FindingCode.of(FindingCode.Family.mc, 99);
        Expectation<OccupantSnapshot> expectation = Expectations
                .host(blockState, (value, pos) -> true, propertyMismatch, callerAbsent, "expected lit");
        Reply.Unreadable unreadable = assertInstanceOf(Reply.Unreadable.class, expectation.test(context(new View())));
        Finding finding = Objects.requireNonNull(unreadable.findings().primary(), "finding");
        assertEquals(callerAbsent, finding.code());
    }

    @Test
    void aFalsePredicateIsAMismatchWithTheSuppliedCodeAndDescription() {
        HostView view = new HostView();
        view.hosts.put(cell, "unlit");
        Expectation<OccupantSnapshot> expectation = Expectations.host(
            blockState,
            (value, pos) -> value.equals("lit"),
            propertyMismatch,
            hostNotCaptured,
            "expected lit"
        );
        Reply.Mismatch mismatch = assertInstanceOf(Reply.Mismatch.class, expectation.test(context(view)));
        Finding finding = Objects.requireNonNull(mismatch.findings().primary(), "finding");
        assertEquals(propertyMismatch, finding.code());
        assertEquals("expected lit", finding.message());
    }

    @Test
    void theMetaCarriesTheSuppliedMismatchCode() {
        Expectation<OccupantSnapshot> expectation = Expectations
                .host(blockState, (value, pos) -> true, propertyMismatch, hostNotCaptured, "expected lit");
        assertEquals(NamespacedId.of("structurecumulus", "host"), expectation.meta().id());
        assertEquals(propertyMismatch, expectation.meta().mismatchCode());
        assertEquals(OccupantSnapshot.class, expectation.subject());
    }

    @Test
    void hostReadsDeclareNoReadScope() {
        Expectation<OccupantSnapshot> expectation = Expectations
                .host(blockState, (value, pos) -> true, propertyMismatch, hostNotCaptured, "expected lit");
        assertEquals(ReadScope.none(), expectation.reads());
    }

    @Test
    void theDefaultHostReadIsNullAndTheRefFormUsesTheAnchor() {
        OccupantSnapshot subject = occupant();
        assertNull(new View().get(cell, blockState), "an unbridged view answers null");
        HostView view = new HostView();
        view.hosts.put(cell, "lit");
        assertEquals("lit", view.get(subject.ref(), blockState), "the ref form reads the anchor");
    }

    private static ExpectationContext<OccupantSnapshot> context(WorldView view) {
        OccupantSnapshot subject = occupant();
        return ExpectationContext.of(new FakeFocus(view, subject), subject, List.of(subject));
    }

    private static OccupantSnapshot occupant() {
        OccupantRef ref = new OccupantRef(snapshot, 0, furnace, OccupantKind.block, cell);
        return new OccupantSnapshot(
            ref,
            new OccupantDescriptor(furnace, OccupantKind.block),
            Cuboid.of(cell),
            List.of()
        );
    }

    private static class View implements WorldView {

        @Override
        public SnapshotId snapshotId() {
            return snapshot;
        }

        @Override
        public int frame() {
            return 0;
        }

        @Override
        public int depth() {
            return 0;
        }

        @Override
        public LatticePoint origin() {
            return LatticePoint.origin;
        }

        @Override
        public @Nullable WorldView parent() {
            return null;
        }

        @Override
        public int cellSize() {
            return 16;
        }

        @Override
        public Cuboid bounds() {
            return Cuboid.of(cell);
        }

        @Override
        public SnapshotPolicy policy() {
            return SnapshotPolicy.minimal();
        }

        @Override
        public SectionKey sectionOf(LatticePoint point) {
            return SectionKey.of(world, 0, point, cellSize());
        }

        @Override
        public SectionSlot slot(SectionKey key) {
            return new SectionSlot.Air(0);
        }

        @Override
        public CellRead read(LatticePoint point) {
            return new CellRead.Found(OccupantSnapshot.air(snapshot, 0, point));
        }

        @Override
        public @Nullable OccupantSnapshot occupant(OccupantRef ref) {
            return null;
        }

        @Override
        public List<OccupantSnapshot> occupants(Cuboid box, OccupantFilter filter) {
            return List.of();
        }

        @Override
        public @Nullable WorldView nested(LatticePoint point) {
            return null;
        }

        @Override
        public long contentDigest(SectionKey key) {
            return 0;
        }
    }

    private static final class HostView extends View {

        private final Map<LatticePoint, String> hosts = new LinkedHashMap<>();

        @SuppressWarnings("unchecked")
        @Override
        public <H> @Nullable H get(LatticePoint pos, HostType<H> type) {
            if (!type.equals(blockState)) {
                return null;
            }
            String value = hosts.get(pos);
            return value == null ? null : (H) value;
        }
    }

    private record FakeFocus(WorldView view, OccupantSnapshot occupant) implements FocusContext {

        @Override
        public BayView bay() {
            return new BayView(NamespacedId.of("test", "bay"), cells(), view, siting());
        }

        @Override
        public Siting siting() {
            return Siting.of(LatticePoint.origin, Orientation.identity);
        }

        @Override
        public InstanceId instance() {
            return instance;
        }

        @Override
        public CuboidSet cells() {
            return CuboidSet.of(Cuboid.of(cell));
        }

        @Override
        public Phase phase() {
            return Phase.check;
        }

        @Override
        public Locus locus() {
            return Locus.world(CuboidSet.of(Cuboid.of(cell)));
        }

        @Override
        public ContextView context() {
            return EmptyContext.instance;
        }

        @Override
        public Budget remaining() {
            return Budget.unbounded;
        }

        @Override
        public CancelToken cancel() {
            return CancelToken.never();
        }

        @Override
        public CellRead read(LatticePoint point) {
            return new CellRead.Found(occupant);
        }

        @Override
        public List<ClaimRow> claimsAt(LatticePoint point) {
            return List.of();
        }
    }

    private enum EmptyContext implements ContextView {
        instance;

        @Override
        public <T> @Nullable T get(ContextKey<T> key) {
            return null;
        }

        @Override
        public <T> Outcome<T> require(ContextKey<T> key) {
            return Outcome.missing();
        }

        @Override
        public boolean has(ContextKey<?> key) {
            return false;
        }

        @Override
        public Set<ContextKey<?>> keys() {
            return Set.of();
        }
    }
}
