package dev.vfyjxf.cumulus.kernel.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vfyjxf.cumulus.kernel.base.Finding;
import dev.vfyjxf.cumulus.kernel.base.FindingBundle;
import dev.vfyjxf.cumulus.kernel.base.FindingCode;
import dev.vfyjxf.cumulus.kernel.base.InstanceId;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.base.Outcome;
import dev.vfyjxf.cumulus.kernel.base.OwnerId;
import dev.vfyjxf.cumulus.kernel.base.Severity;
import dev.vfyjxf.cumulus.kernel.base.Stage;
import dev.vfyjxf.cumulus.kernel.blueprint.Bay;
import dev.vfyjxf.cumulus.kernel.blueprint.BayBuilder;
import dev.vfyjxf.cumulus.kernel.blueprint.Blueprint;
import dev.vfyjxf.cumulus.kernel.blueprint.Coverage;
import dev.vfyjxf.cumulus.kernel.blueprint.Requirement;
import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimMode;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimScope;
import dev.vfyjxf.cumulus.kernel.claim.ClaimSet;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.ClaimVerdict;
import dev.vfyjxf.cumulus.kernel.claim.InMemoryClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.SettleRequest;
import dev.vfyjxf.cumulus.kernel.context.Computation;
import dev.vfyjxf.cumulus.kernel.context.ComputationContext;
import dev.vfyjxf.cumulus.kernel.context.ContextKey;
import dev.vfyjxf.cumulus.kernel.context.ContextKeys;
import dev.vfyjxf.cumulus.kernel.context.ContextStore;
import dev.vfyjxf.cumulus.kernel.context.ContextTier;
import dev.vfyjxf.cumulus.kernel.context.DeriveContext;
import dev.vfyjxf.cumulus.kernel.context.Deriver;
import dev.vfyjxf.cumulus.kernel.context.Merges;
import dev.vfyjxf.cumulus.kernel.context.Retention;
import dev.vfyjxf.cumulus.kernel.context.StatePhase;
import dev.vfyjxf.cumulus.kernel.context.StructureContext;
import dev.vfyjxf.cumulus.kernel.predicate.BayView;
import dev.vfyjxf.cumulus.kernel.predicate.Check;
import dev.vfyjxf.cumulus.kernel.predicate.Expectation;
import dev.vfyjxf.cumulus.kernel.predicate.ExpectationContext;
import dev.vfyjxf.cumulus.kernel.predicate.Expectations;
import dev.vfyjxf.cumulus.kernel.predicate.Focuses;
import dev.vfyjxf.cumulus.kernel.predicate.Reply;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.spi.Catalogs;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class MatchSessionTest {

    private static final NamespacedId BLUEPRINT = id("test", "blueprint");
    private static final NamespacedId BAY = id("test", "bay");
    private static final NamespacedId STONE = id("minecraft", "stone");
    private static final LatticePoint ORIGIN = LatticePoint.ORIGIN;
    private static final Siting SITING = Siting.of(ORIGIN, Orientation.IDENTITY);
    private static final InstanceId INSTANCE = InstanceId.of(EngineWorld.DIMENSION, 1L);
    private static final SnapshotId SNAPSHOT = SnapshotId.of(EngineWorld.DIMENSION, 1L);
    private static final OwnerId OWNER = OwnerId.of(EngineWorld.DIMENSION, id("test", "owner"));
    private static final OwnerId OTHER = OwnerId.of(EngineWorld.DIMENSION, id("test", "other"));
    private static final Cuboid ONE_CELL = Cuboid.of(0, 0, 0, 1, 1, 1);

    private static final ContextKey<Long> IRON = ContextKeys.of(id("test", "iron"), Long.class,
            Retention.CONTEXT, Merges.sum(), null);

    @Test
    void budgetExhaustionStopsBeforeTheSecondCheck() {
        BayBuilder shell = cubicBay(ONE_CELL);
        Blueprint blueprint = blueprint(ONE_CELL, shell, List.of(),
                stoneCheck(shell.bayId(), id("test", "stone_a"), Stage.FORM),
                stoneCheck(shell.bayId(), id("test", "stone_b"), Stage.FORM));
        EngineWorld world = EngineWorld.builder().putBlock(ORIGIN, STONE).build();
        InstanceBudget budget = InstanceBudget.of(Budget.of(1, 100));

        MatchResult result = session(blueprint, Stage.FORM, world, budget, null).run();

        MatchResult.Undecided undecided = assertInstanceOf(MatchResult.Undecided.class, result);
        assertEquals(UndecidedReason.BUDGET_EXHAUSTED, undecided.reason());
        assertEquals(FindingCode.of("SC-ENG-0001"), undecided.findings().get(0).code());
        assertEquals(1, budget.spend().cells(), "one cell was read before the second check was refused");
    }

    @Test
    void aRefusedSectionMakesTheContextKeyAbsentAndTheCheckUnreadable() {
        Computation<Long> iron = new IronComputation(CuboidSet.of(Cuboid.of(0, 0, 0, 17, 1, 1)));
        BayBuilder shell = BayBuilder.start(BAY, Cuboid.of(0, 0, 0, 17, 1, 1), Coverage.PRESENCE,
                ClaimMode.SHARED, new ClaimScope.Audit());
        Check<Long> check = new Check<>(id("test", "iron_check"), Stage.FORM, shell.bayId(),
                Focuses.context(IRON), atLeastOne(), null);
        Blueprint blueprint = blueprint(Cuboid.of(0, 0, 0, 17, 1, 1), shell, List.of(iron), check);
        EngineWorld world = EngineWorld.builder().putBlock(ORIGIN, STONE).denySection(LatticePoint.of(16, 0, 0))
                .build();

        MatchResult result = session(blueprint, Stage.FORM, world, InstanceBudget.unbounded(), null).run();

        MatchResult.Undecided undecided = assertInstanceOf(MatchResult.Undecided.class, result);
        assertEquals(UndecidedReason.SUBJECT_ABSENT, undecided.reason());
        List<Finding> findings = undecided.findings();
        assertTrue(findings.stream().anyMatch(finding -> finding.code().equals(FindingCode.of("SC-CTX-0202"))),
                "a refused section is a missing value, never a short sum: " + findings);
        assertNotNull(undecided.partial(), "the partial outcome names what was read");
        assertNull(undecided.partial().value(IRON), "the key is absent, never zero");
    }

    @Test
    void formedIsReturnedOnlyAfterASettledVerdict() {
        BayBuilder shell = cubicBay(ONE_CELL);
        Blueprint blueprint = blueprint(ONE_CELL, shell, List.of(),
                stoneCheck(shell.bayId(), id("test", "stone"), Stage.SETTLE));
        EngineWorld world = EngineWorld.builder().putBlock(ORIGIN, STONE).build();
        InMemoryClaimLedger ledger = new InMemoryClaimLedger();

        MatchResult result = kernel(ledger).check(request(blueprint, Stage.SETTLE, world, SITING, ledger));

        MatchResult.Formed formed = assertInstanceOf(MatchResult.Formed.class, result);
        assertEquals(1L, formed.ledgerRevision(), "the settle answered at revision 1");
        assertEquals(1L, formed.contextRevision());
        assertEquals(1L, formed.generation(), "a counter owns the generation");
        assertEquals(1, formed.claims().rows().size());
        assertEquals(LatticeKey.of(ORIGIN), formed.claims().rows().get(0).cell());
        assertEquals(OWNER, formed.claims().rows().get(0).owner());
        assertSame(world, formed.outcome().view(), "the outcome read the leased view");
        assertEquals(Stage.SETTLE, formed.stage());
    }

    @Test
    void closingAResultReleasesItsChildHandleOnly() {
        BayBuilder shell = cubicBay(ONE_CELL);
        Blueprint blueprint = blueprint(ONE_CELL, shell, List.of(),
                stoneCheck(shell.bayId(), id("test", "stone"), Stage.SETTLE));
        EngineWorld world = EngineWorld.builder().putBlock(ORIGIN, STONE).build();
        InMemoryClaimLedger ledger = new InMemoryClaimLedger();
        TestLease lease = TestLease.captured(SNAPSHOT, world, 8L);
        MatchRequest request = request(blueprint, Stage.SETTLE, lease, ledger, InstanceBudget.unbounded());

        MatchResult.Formed formed = assertInstanceOf(MatchResult.Formed.class, kernel(ledger).check(request));
        SnapshotLease child = formed.lease();

        assertSame(lease, request.lease());
        assertNotSame(lease, child, "the result holds a child handle, not the request's");
        assertTrue(child.isOpen() && lease.isOpen());

        formed.close();

        assertFalse(child.isOpen());
        assertTrue(lease.isOpen(), "the instance keeps its own reference");
    }

    @Test
    void aClosedLeaseNeverRuns() {
        BayBuilder shell = cubicBay(ONE_CELL);
        Blueprint blueprint = blueprint(ONE_CELL, shell, List.of(),
                stoneCheck(shell.bayId(), id("test", "stone"), Stage.FORM));
        EngineWorld world = EngineWorld.builder().putBlock(ORIGIN, STONE).build();
        TestLease lease = TestLease.captured(SNAPSHOT, world, 8L);
        lease.close();

        MatchResult result = session(blueprint, Stage.FORM, lease, InstanceBudget.unbounded(), null).run();

        MatchResult.Undecided undecided = assertInstanceOf(MatchResult.Undecided.class, result);
        assertEquals(UndecidedReason.LEASE_CLOSED, undecided.reason());
    }

    @Test
    void aLedgerThatKeepsMovingIsUndecidedNotFormed() {
        BayBuilder shell = cubicBay(ONE_CELL);
        Blueprint blueprint = blueprint(ONE_CELL, shell, List.of(),
                stoneCheck(shell.bayId(), id("test", "stone"), Stage.SETTLE));
        EngineWorld world = EngineWorld.builder().putBlock(ORIGIN, STONE).build();
        MatchSettler settler = MatchSettler.inline(new MovingLedger(42L));
        MatchRequest request = request(blueprint, Stage.SETTLE, TestLease.captured(SNAPSHOT, world, 8L),
                null, InstanceBudget.unbounded());

        MatchResult result = new MatchSession(request, settler).run();

        MatchResult.Undecided undecided = assertInstanceOf(MatchResult.Undecided.class, result);
        assertEquals(UndecidedReason.LEDGER_MOVED, undecided.reason());
        assertNotNull(undecided.retry(), "a moved ledger hands back a token");
        assertEquals(FindingCode.of("SC-ENG-0003"), undecided.findings().get(0).code());
    }

    @Test
    void aClaimConflictIsFailedWithTheClaimCode() {
        BayBuilder shell = cubicBay(ONE_CELL);
        Blueprint blueprint = blueprint(ONE_CELL, shell, List.of(),
                stoneCheck(shell.bayId(), id("test", "stone"), Stage.SETTLE));
        EngineWorld world = EngineWorld.builder().putBlock(ORIGIN, STONE).build();
        ClaimRow incumbent = new ClaimRow(LatticeKey.of(ORIGIN), ClaimMode.EXCLUSIVE, OTHER, SITING,
                id("test", "other_bay"));
        InMemoryClaimLedger ledger = new InMemoryClaimLedger(new ClaimSet(List.of(incumbent), 1L));

        MatchResult result = kernel(ledger).check(request(blueprint, Stage.SETTLE, world, SITING, ledger));

        MatchResult.Failed failed = assertInstanceOf(MatchResult.Failed.class, result);
        assertTrue(failed.findings().stream()
                        .anyMatch(finding -> finding.code().equals(FindingCode.of("SC-CLM-0002"))),
                "a conflict reports the incumbent rows: " + failed.findings());
        assertFalse(failed.findings().isEmpty());
    }

    @Test
    void aStalePublishWithoutAPreviousFormedIsUndecided() {
        BayBuilder shell = cubicBay(ONE_CELL);
        Blueprint blueprint = blueprint(ONE_CELL, shell, List.of(),
                stoneCheck(shell.bayId(), id("test", "stone"), Stage.FORM));
        EngineWorld world = EngineWorld.builder().putBlock(ORIGIN, STONE).build();
        ContextStore store = new ContextStore(INSTANCE);
        MatchRequest request = request(blueprint, Stage.FORM, TestLease.captured(SNAPSHOT, world, 8L),
                null, InstanceBudget.unbounded());
        MatchSession stalled = new MatchSession(request, null, store);

        assertInstanceOf(Outcome.Found.class, store.publish(store.draft(ContextTier.FORM, store.revision())));
        MatchResult result = stalled.run();

        MatchResult.Undecided undecided = assertInstanceOf(MatchResult.Undecided.class, result);
        assertEquals(UndecidedReason.DIRTY_UNSTABLE, undecided.reason());
        assertTrue(undecided.findings().stream()
                        .anyMatch(finding -> finding.code().equals(FindingCode.of("SC-MC-0404"))),
                "a dirty attempt names SC-MC-0404: " + undecided.findings());
    }

    @Test
    void aStalePublishReemitsThePreviousFormedWithTheNextGeneration() {
        BayBuilder shell = cubicBay(ONE_CELL);
        Blueprint blueprint = blueprint(ONE_CELL, shell, List.of(),
                stoneCheck(shell.bayId(), id("test", "stone"), Stage.FORM));
        EngineWorld world = EngineWorld.builder().putBlock(ORIGIN, STONE).build();
        ContextStore store = new ContextStore(INSTANCE);
        InstanceRecord record = InstanceRecord.of(INSTANCE);
        TestLease previous = TestLease.captured(SNAPSHOT, world, 8L);
        MatchResult.Formed emitted = new MatchResult.Formed(INSTANCE, SITING,
                MatchOutcome.empty(Stage.FORM, SITING, world), ClaimSet.EMPTY, 1L, 0L, 0L, previous,
                FindingBundle.empty());
        record.remember(emitted);
        MatchRequest request = new MatchRequest(blueprint, SITING, Stage.FORM,
                TestLease.captured(SNAPSHOT, world, 8L), MatchPolicy.defaults(),
                InstanceBudget.unbounded(), PinBudget.unbounded(), record, OWNER, Ticket.of(1L));
        MatchSession stalled = new MatchSession(request, null, store);

        assertInstanceOf(Outcome.Found.class, store.publish(store.draft(ContextTier.FORM, store.revision())));
        MatchResult result = stalled.run();

        MatchResult.Formed again = assertInstanceOf(MatchResult.Formed.class, result);
        assertEquals(2L, again.generation(), "a re-emission takes the next generation");
        assertEquals(emitted.id(), again.id());
        assertEquals(emitted.outcome(), again.outcome(), "the previous outcome is repeated, never recomputed");
        assertEquals(emitted.claims(), again.claims());
        assertEquals(emitted.lease(), again.lease());
        assertFalse(again.bundle().ofCode(FindingCode.of("SC-MC-0404")).isEmpty(),
                "a re-emission still says why: " + again.bundle().findings());
        assertEquals(2L, record.generation());
    }

    @Test
    void derivePublishesEachTierAndLaterDeriversReadEarlierValues() {
        BayBuilder shell = cubicBay(ONE_CELL);
        Blueprint blueprint = blueprint(ONE_CELL, shell, List.of(),
                stoneCheck(shell.bayId(), id("test", "stone"), Stage.SETTLE));
        EngineWorld world = EngineWorld.builder().putBlock(ORIGIN, STONE).build();
        InMemoryClaimLedger ledger = new InMemoryClaimLedger();
        StructureKernel kernel = kernel(ledger);
        MatchResult.Formed formed = assertInstanceOf(MatchResult.Formed.class,
                kernel.check(request(blueprint, Stage.SETTLE, world, SITING, ledger)));
        ContextKey<Long> capacity = ContextKeys.of(id("test", "capacity"), Long.class,
                Retention.CONTEXT, null, null);
        ContextKey<Long> doubled = ContextKeys.of(id("test", "doubled"), Long.class,
                Retention.CONTEXT, null, null);
        Deriver<Long> constant = deriver(capacity, ctx -> 400L);
        Deriver<Long> dependent = deriver(doubled,
                ctx -> ctx.read(capacity).fold((value, subjects) -> value * 2L, findings -> null));

        StructureContext context = kernel.derive(formed, new ContextStore(formed.id()),
                List.of(constant, dependent));

        long capacityValue = context.require(capacity).fold((value, subjects) -> value, findings -> -1L);
        long doubledValue = context.require(doubled).fold((value, subjects) -> value, findings -> -1L);
        assertEquals(400L, capacityValue);
        assertEquals(800L, doubledValue);
        assertEquals(1L, context.revision(), "one FORM frame carries both derivers");
    }

    private static Deriver<Long> deriver(ContextKey<Long> output,
                                         java.util.function.Function<DeriveContext, Long> body) {
        return new Deriver<>() {
            @Override
            public ContextKey<Long> output() {
                return output;
            }

            @Override
            public Set<ContextKey<?>> requires() {
                return Set.of();
            }

            @Override
            public ContextTier tier() {
                return ContextTier.FORM;
            }

            @Override
            public Long derive(DeriveContext ctx) {
                return body.apply(ctx);
            }
        };
    }

    @Test
    void aChangeAfterCaptureDropsTheAttempt() {
        BayBuilder shell = cubicBay(ONE_CELL);
        Blueprint blueprint = blueprint(ONE_CELL, shell, List.of(),
                stoneCheck(shell.bayId(), id("test", "stone"), Stage.FORM));
        EngineWorld world = EngineWorld.builder().putBlock(ORIGIN, STONE).build();
        TestLease lease = TestLease.captured(SNAPSHOT, world, 8L).mark();

        MatchResult result = new MatchSession(request(blueprint, Stage.FORM, lease, null,
                InstanceBudget.unbounded()), null).run();

        MatchResult.Dropped dropped = assertInstanceOf(MatchResult.Dropped.class, result);
        assertEquals(DropCause.SNAPSHOT_CHANGED, dropped.cause());
        assertTrue(dropped.findings().stream()
                        .anyMatch(finding -> finding.code().equals(FindingCode.of("SC-ENG-0003"))),
                "a change after capture names SC-ENG-0003: " + dropped.findings());
    }

    @Test
    void aReplacedWorldDropsTheAttempt() {
        BayBuilder shell = cubicBay(ONE_CELL);
        Blueprint blueprint = blueprint(ONE_CELL, shell, List.of(),
                stoneCheck(shell.bayId(), id("test", "stone"), Stage.FORM));
        EngineWorld world = EngineWorld.builder().putBlock(ORIGIN, STONE).build();
        TestLease lease = TestLease.captured(SNAPSHOT, world, 8L).invalidate();

        MatchResult result = new MatchSession(request(blueprint, Stage.FORM, lease, null,
                InstanceBudget.unbounded()), null).run();

        MatchResult.Dropped dropped = assertInstanceOf(MatchResult.Dropped.class, result);
        assertEquals(DropCause.CAPTURE_INVALID, dropped.cause());
        assertTrue(dropped.findings().stream()
                        .anyMatch(finding -> finding.code().equals(FindingCode.of("SC-ENG-0006"))),
                "an invalid capture names SC-ENG-0006: " + dropped.findings());
    }

    private static MatchSession session(Blueprint blueprint, Stage stage, EngineWorld world,
                                        InstanceBudget budget, @Nullable ClaimLedger ledger) {
        MatchRequest request = request(blueprint, stage, TestLease.captured(SNAPSHOT, world, 8L),
                ledger, budget);
        return new MatchSession(request, ledger == null ? null : MatchSettler.inline(ledger));
    }

    private static MatchSession session(Blueprint blueprint, Stage stage, TestLease lease,
                                        InstanceBudget budget, @Nullable ClaimLedger ledger) {
        MatchRequest request = request(blueprint, stage, lease, ledger, budget);
        return new MatchSession(request, ledger == null ? null : MatchSettler.inline(ledger));
    }

    private static MatchRequest request(Blueprint blueprint, Stage stage, EngineWorld world, Siting siting,
                                        @Nullable ClaimLedger ledger) {
        return request(blueprint, stage, siting, TestLease.captured(SNAPSHOT, world, 8L), ledger,
                InstanceBudget.unbounded());
    }

    @Test
    void aRefusedPinStopsTheAttemptBeforeAnyCheck() {
        BayBuilder shell = cubicBay(ONE_CELL);
        Blueprint blueprint = blueprint(ONE_CELL, shell, List.of(),
                stoneCheck(shell.bayId(), id("test", "stone"), Stage.FORM));
        EngineWorld world = EngineWorld.builder().putBlock(ORIGIN, STONE).build();
        MatchRequest request = new MatchRequest(blueprint, SITING, Stage.FORM,
                TestLease.captured(SNAPSHOT, world, 8L), MatchPolicy.defaults(),
                InstanceBudget.unbounded(), PinBudget.of(0L), InstanceRecord.of(INSTANCE), OWNER,
                Ticket.of(1L));

        MatchResult result = new MatchSession(request, null).run();

        MatchResult.Undecided undecided = assertInstanceOf(MatchResult.Undecided.class, result);
        assertEquals(UndecidedReason.BUDGET_EXHAUSTED, undecided.reason());
        assertTrue(undecided.findings().stream()
                        .anyMatch(finding -> finding.code().text().equals("SC-MC-0502")),
                "a pin over the budget is SC-MC-0502, not a silent unbounded retain");
    }

    private static MatchRequest request(Blueprint blueprint, Stage stage, TestLease lease,
                                        @Nullable ClaimLedger ledger, InstanceBudget budget) {
        return request(blueprint, stage, SITING, lease, ledger, budget);
    }

    private static MatchRequest request(Blueprint blueprint, Stage stage, Siting siting, SnapshotLease lease,
                                        @Nullable ClaimLedger ledger, InstanceBudget budget) {
        return new MatchRequest(blueprint, siting, stage, lease,
                MatchPolicy.defaults(), budget, PinBudget.unbounded(), InstanceRecord.of(INSTANCE), OWNER,
                Ticket.of(1L));
    }


    private static StructureKernel kernel(ClaimLedger ledger) {
        return StructureKernel.create(Catalogs.empty(), ledger);
    }

    private static BayBuilder cubicBay(Cuboid cells) {
        return BayBuilder.start(BAY, cells, Coverage.CUBIC, ClaimMode.EXCLUSIVE, new ClaimScope.Owned());
    }

    private static Blueprint blueprint(Cuboid bounds, BayBuilder shell, List<Computation<?>> computations,
                                       Check<?>... checks) {
        Requirement requirement = new Requirement(List.of(checks), computations);
        return Blueprint.builder(BLUEPRINT).bounds(bounds).bay(shell.attach(requirement)).build();
    }

    private static Check<BayView> stoneCheck(NamespacedId bay, NamespacedId id, Stage stage) {
        return new Check<>(id, stage, bay, Focuses.bay(),
                Expectations.eachCell(Expectations.block(STONE)), null);
    }

    private static Expectation<Long> atLeastOne() {
        return new Expectation<>() {

            @Override
            public Reply test(ExpectationContext<Long> ctx) {
                if (ctx.subject() >= 1L) {
                    return Reply.match();
                }
                Finding finding = Finding.builder(FindingCode.of("SC-EXP-0002"))
                        .severity(Severity.ERROR)
                        .message("expected at least one")
                        .stage(ctx.stage())
                        .build();
                return Reply.mismatch(finding);
            }

            @Override
            public Class<Long> subject() {
                return Long.class;
            }
        };
    }

    private static NamespacedId id(String namespace, String path) {
        return NamespacedId.of(namespace, path);
    }

    private record IronComputation(CuboidSet region) implements Computation<Long> {

        @Override
        public NamespacedId id() {
            return NamespacedId.of("test", "iron_count");
        }

        @Override
        public Stage stage() {
            return Stage.FORM;
        }

        @Override
        public StatePhase phase() {
            return StatePhase.SECTION;
        }

        @Override
        public ContextKey<Long> output() {
            return IRON;
        }

        @Override
        public Set<ContextKey<?>> inputs() {
            return Set.of();
        }

        @Override
        public Outcome<Long> compute(ComputationContext ctx) {
            return new Outcome.Found<>(1L, List.of(), FindingBundle.empty());
        }
    }

    private static final class MovingLedger implements ClaimLedger {

        private final long actual;

        private MovingLedger(long actual) {
            this.actual = actual;
        }

        @Override
        public long revision() {
            return 0L;
        }

        @Override
        public ClaimVerdict settle(SettleRequest request) {
            return new ClaimVerdict.RevisionMoved(request.expectedRevision(), actual);
        }

        @Override
        public ClaimView read(Cuboid box) {
            return new ClaimView(List.of(), 0L, true);
        }
    }
}
