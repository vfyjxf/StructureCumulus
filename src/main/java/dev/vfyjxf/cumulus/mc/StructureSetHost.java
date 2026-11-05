package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimVerdict;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.context.StructureAttempt;
import dev.vfyjxf.cumulus.kernel.context.StructureEvent;
import dev.vfyjxf.cumulus.kernel.context.StructureInstance;
import dev.vfyjxf.cumulus.kernel.context.StructureManager;
import dev.vfyjxf.cumulus.kernel.context.StructureSpec;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.document.DistanceMetric;
import dev.vfyjxf.cumulus.kernel.document.LinkMode;
import dev.vfyjxf.cumulus.kernel.document.LinkSpec;
import dev.vfyjxf.cumulus.kernel.engine.MatchOutcome;
import dev.vfyjxf.cumulus.kernel.engine.MatchRequest;
import dev.vfyjxf.cumulus.kernel.engine.MatchResult;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.finding.Severity;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.world.CellRead;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.SnapshotLease;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Host orchestration for one placed structure set: presence, links, check-all, settle-all and rollback.
 *
 * <p>The kernel still checks and settles one blueprint at a time; this class sequences those independent
 * attempts under one set-scoped budget. Before the settle pass the host releases the formed instance at
 * each member's owner and siting; that previous occupant is never restored (D-79). Member commits are
 * deferred until every settle succeeds, so a failed settlement rolls back only what this run settled,
 * releases claims in reverse order, invalidates every settled draft or instance through the bound
 * manager, and reports a cleanup failure as {@code SC-SET-0007}.
 */
public final class StructureSetHost {

    private final StructureKernel kernel;
    private final StructureManager structures;
    private final boolean verifyManagerBinding;

    public StructureSetHost(StructureKernel kernel) {
        this(kernel, kernel.structures(), false);
    }

    /**
     * Binds the host to the manager its drafts come from. Rollback and commit verification target this
     * manager, so a caller that uses two managers cannot silently leave the formed contexts behind.
     */
    public StructureSetHost(StructureKernel kernel, StructureManager structures) {
        this(kernel, structures, true);
    }

    private StructureSetHost(StructureKernel kernel, StructureManager structures, boolean verifyManagerBinding) {
        this.kernel = Objects.requireNonNull(kernel, "kernel");
        this.structures = Objects.requireNonNull(structures, "structures");
        this.verifyManagerBinding = verifyManagerBinding;
    }

    /**
     * Runs the set in the fixed order of design 09: presence of every satellite, link distance and
     * coverage, the root check, then every present satellite check, then the root and the satellites in
     * declaration order. Any failure before a settle means no settle; the commits run only after every
     * member settles, and a settle failure releases the already-settled members in reverse order.
     */
    public StructureSetResult run(
        StructureSet set,
        SnapshotLease lease,
        OwnerId owner,
        Budget budget,
        Function<StructureSpec, StructureAttempt> drafts
    ) {
        return run(set, lease, owner, budget, structures, drafts, true, verifyManagerBinding);
    }

    /** Runs with an explicit manager, used when the caller supplies a manager-bound drafts function. */
    public StructureSetResult run(
        StructureSet set,
        SnapshotLease lease,
        OwnerId owner,
        Budget budget,
        StructureManager structures,
        Function<StructureSpec, StructureAttempt> drafts
    ) {
        return run(set, lease, owner, budget, structures, drafts, true, true);
    }

    /**
     * Runs every check of the set exactly as {@link #run} does and stops before the settle pass: no
     * claim is written, nothing is settled and nothing is rolled back.
     */
    public StructureSetResult check(
        StructureSet set,
        SnapshotLease lease,
        OwnerId owner,
        Budget budget,
        Function<StructureSpec, StructureAttempt> drafts
    ) {
        return run(set, lease, owner, budget, structures, drafts, false, verifyManagerBinding);
    }

    /** Checks with an explicit manager binding; no commit or rollback is reached. */
    public StructureSetResult check(
        StructureSet set,
        SnapshotLease lease,
        OwnerId owner,
        Budget budget,
        StructureManager structures,
        Function<StructureSpec, StructureAttempt> drafts
    ) {
        return run(set, lease, owner, budget, structures, drafts, false, true);
    }

    private StructureSetResult run(
        StructureSet set,
        SnapshotLease lease,
        OwnerId owner,
        Budget budget,
        StructureManager structures,
        Function<StructureSpec, StructureAttempt> drafts,
        boolean settle,
        boolean verifyBinding
    ) {
        Objects.requireNonNull(set, "set");
        Objects.requireNonNull(lease, "lease");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(structures, "structures");
        Objects.requireNonNull(drafts, "drafts");
        List<Finding> setFindings = new ArrayList<>();
        Map<NamespacedId, MatchOutcome> outcomes = new LinkedHashMap<>();
        Map<NamespacedId, List<ClaimRow>> claims = new LinkedHashMap<>();
        List<NamespacedId> settled = new ArrayList<>();
        List<Finding> rollbackFailures = new ArrayList<>();
        List<NamespacedId> rolledBack = new ArrayList<>();
        if (!lease.isCurrent()) {
            setFindings.add(
                problem(Codes.snapshotStale, "the lease is not current; the set was not checked", null, Phase.check)
            );
            return result(outcomes, setFindings, settled, rolledBack, rollbackFailures, claims);
        }
        List<Finding> validation = StructureSet.validate(set);
        if (hasErrors(validation)) {
            setFindings.addAll(validation);
            return result(outcomes, setFindings, settled, rolledBack, rollbackFailures, claims);
        }
        setFindings.addAll(validation);
        WorldView view = lease.view();
        SetBudget setBudget = new SetBudget(budget);
        Map<NamespacedId, Boolean> present = new LinkedHashMap<>();
        for (StructureSet.Satellite satellite : requiredFirst(set.satellites())) {
            Presence probe = probe(satellite, view, setBudget);
            if (probe.state() == PresenceState.unknown) {
                setFindings.add(presenceUnreadable(satellite, probe.cell(), probe.budgetExhausted()));
                return result(outcomes, setFindings, settled, rolledBack, rollbackFailures, claims);
            }
            boolean isPresent = probe.state() == PresenceState.present;
            present.put(satellite.id(), isPresent);
            if (!isPresent && satellite.required()) {
                setFindings.add(satelliteAbsent(satellite));
                return result(outcomes, setFindings, settled, rolledBack, rollbackFailures, claims);
            }
        }
        for (StructureSet.Satellite satellite : set.satellites()) {
            setFindings.addAll(linkFindings(set, satellite));
        }
        if (hasErrors(setFindings)) {
            return result(outcomes, setFindings, settled, rolledBack, rollbackFailures, claims);
        }
        List<Attempt> attempts = new ArrayList<>();
        boolean checkFailed = false;
        Attempt root;
        try {
            root = checkOne(
                set.root(),
                set.rootSiting(),
                set.rootBounds(),
                set.root().id(),
                lease,
                owner,
                setBudget,
                structures,
                drafts,
                setFindings,
                outcomes
            );
            if (root != null) {
                attempts.add(root);
                for (StructureSet.Satellite satellite : set.satellites()) {
                    if (!Boolean.TRUE.equals(present.get(satellite.id()))) {
                        continue;
                    }
                    Attempt attempt = checkOne(
                        satellite.blueprint(),
                        satellite.siting(),
                        satellite.worldBounds(),
                        satellite.id(),
                        lease,
                        owner,
                        setBudget,
                        structures,
                        drafts,
                        setFindings,
                        outcomes
                    );
                    if (attempt == null) {
                        checkFailed = true;
                        break;
                    }
                    attempts.add(attempt);
                }
            }
        } catch (RuntimeException failure) {
            List<Finding> cleanup = new ArrayList<>();
            abortFrom(attempts, 0, cleanup);
            attachCleanup(failure, cleanup);
            throw failure;
        }
        if (root == null || checkFailed) {
            abortFrom(attempts, 0, rollbackFailures);
            return result(outcomes, setFindings, settled, rolledBack, rollbackFailures, claims);
        }
        if (!settle) {
            abortFrom(attempts, 0, rollbackFailures);
            return result(outcomes, setFindings, settled, rolledBack, rollbackFailures, claims);
        }
        ClaimLedger ledger = kernel.ledger();
        try {
            takeOver(ledger, structures, owner, attempts, rollbackFailures);
        } catch (RuntimeException failure) {
            List<Finding> cleanup = new ArrayList<>();
            abortFrom(attempts, 0, cleanup);
            attachCleanup(failure, cleanup);
            throw failure;
        }
        if (!rollbackFailures.isEmpty()) {
            abortFrom(attempts, 0, rollbackFailures);
            return result(outcomes, setFindings, settled, rolledBack, rollbackFailures, claims);
        }
        List<Settled> settledMembers = new ArrayList<>();
        for (int index = 0; index < attempts.size(); index++) {
            Attempt attempt = attempts.get(index);
            MatchResult settleResult;
            try {
                settleResult = kernel.settle(attempt.request(), attempt.admitted(), attempt.draft());
            } catch (RuntimeException failure) {
                List<Finding> cleanup = new ArrayList<>();
                List<Attempt> rollbackMembers = attemptsOf(settledMembers);
                rollbackMembers.add(attempt);
                rollbackAttempts(rollbackMembers, Map.of(), ledger, rolledBack, cleanup);
                abortFrom(attempts, index + 1, cleanup);
                attachCleanup(failure, cleanup);
                throw failure;
            }
            if (settleResult instanceof MatchResult.Formed formedResult) {
                setFindings.addAll(formedResult.findings().findings());
                settled.add(attempt.key());
                claims.put(attempt.key(), claimsOf(formedResult, attempt.blueprint()));
                settledMembers.add(new Settled(attempt, formedResult));
                continue;
            }
            setFindings.addAll(settleResult.findings().findings());
            List<Attempt> rollbackMembers = attemptsOf(settledMembers);
            rollbackAttempts(rollbackMembers, Map.of(), ledger, rolledBack, rollbackFailures);
            abortFrom(attempts, index, rollbackFailures);
            return result(outcomes, setFindings, settled, rolledBack, rollbackFailures, claims);
        }
        Map<NamespacedId, StructureInstance> instances = new LinkedHashMap<>();
        List<StructureInstance> committed = new ArrayList<>();
        try {
            for (Settled member : settledMembers) {
                StructureInstance instance = member.attempt().draft().commit(member.attempt().worldBounds().asSet());
                if (verifyBinding && structures.find(instance.id()) == null) {
                    throw new IllegalStateException(
                        "the drafts function committed " + member.attempt().key().text()
                                + " into a manager other than this host's manager"
                    );
                }
                instances.put(member.attempt().key(), instance);
                committed.add(instance);
            }
        } catch (RuntimeException failure) {
            List<Finding> cleanup = new ArrayList<>();
            List<Attempt> rollbackMembers = attemptsOf(settledMembers);
            rollbackAttempts(rollbackMembers, instances, ledger, rolledBack, cleanup);
            attachCleanup(failure, cleanup);
            throw failure;
        }
        return result(outcomes, setFindings, settled, rolledBack, rollbackFailures, claims);
    }

    private static List<StructureSet.Satellite> requiredFirst(List<StructureSet.Satellite> satellites) {
        List<StructureSet.Satellite> ordered = new ArrayList<>(satellites.size());
        for (StructureSet.Satellite satellite : satellites) {
            if (satellite.required()) {
                ordered.add(satellite);
            }
        }
        for (StructureSet.Satellite satellite : satellites) {
            if (!satellite.required()) {
                ordered.add(satellite);
            }
        }
        return ordered;
    }

    private @Nullable Attempt checkOne(
        Blueprint blueprint,
        Siting siting,
        Cuboid worldBounds,
        NamespacedId key,
        SnapshotLease lease,
        OwnerId owner,
        SetBudget budget,
        StructureManager structures,
        Function<StructureSpec, StructureAttempt> drafts,
        List<Finding> setFindings,
        Map<NamespacedId, MatchOutcome> outcomes
    ) {
        ClaimLedger ledger = kernel.ledger();
        @Nullable
        ClaimView claims = ledger == null ? null : remainingClaims(ledger, worldBounds, siting, owner, structures);
        StructureAttempt draft = drafts.apply(new StructureSpec(owner, siting, blueprint.id()));
        try {
            MatchRequest request = MatchRequest
                    .of(blueprint, siting, lease, draft.id(), owner, budget.remaining(), claims);
            MatchResult checked = kernel.check(request);
            if (checked instanceof MatchResult.Admitted admitted) {
                outcomes.put(key, admitted.outcome());
                budget.spend(admitted.outcome().spend());
                return new Attempt(key, blueprint, siting, worldBounds, request, admitted, draft);
            }
            draft.abort();
            setFindings.addAll(checked.findings().findings());
            return null;
        } catch (RuntimeException failure) {
            try {
                draft.abort();
            } catch (RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    private void rollbackAttempts(
        List<Attempt> members,
        Map<NamespacedId, StructureInstance> instances,
        @Nullable ClaimLedger ledger,
        List<NamespacedId> rolledBack,
        List<Finding> rollbackFailures
    ) {
        for (int index = members.size() - 1; index >= 0; index--) {
            Attempt attempt = members.get(index);
            StructureInstance instance = instances.get(attempt.key());
            InstanceId contextId = instance == null ? attempt.draft().id() : instance.id();
            if (ledger == null) {
                // A Formed structure without a ledger wrote no rows; nothing is left to release.
                rolledBack.add(attempt.key());
            } else {
                try {
                    ClaimVerdict verdict = ledger.release(contextId);
                    if (verdict instanceof ClaimVerdict.Settled) {
                        rolledBack.add(attempt.key());
                    } else {
                        rollbackFailures.add(
                            rollbackFailure(attempt, "release answered " + verdict.getClass().getSimpleName())
                        );
                    }
                } catch (RuntimeException failure) {
                    rollbackFailures.add(rollbackFailure(attempt, detail(failure)));
                }
            }
            try {
                structures.onEvent(new StructureEvent.Broken(contextId));
            } catch (RuntimeException failure) {
                rollbackFailures.add(
                    rollbackFailure(attempt, "the structure context could not be removed: " + detail(failure))
                );
            }
        }
    }

    private static List<Attempt> attemptsOf(List<Settled> members) {
        List<Attempt> attempts = new ArrayList<>(members.size());
        for (Settled member : members) {
            attempts.add(member.attempt());
        }
        return attempts;
    }

    private void abortFrom(List<Attempt> attempts, int from, List<Finding> rollbackFailures) {
        for (int index = from; index < attempts.size(); index++) {
            Attempt attempt = attempts.get(index);
            try {
                attempt.draft().abort();
            } catch (RuntimeException failure) {
                rollbackFailures
                        .add(rollbackFailure(attempt, "the draft context could not be removed: " + detail(failure)));
            }
        }
    }

    private static void takeOver(
        @Nullable ClaimLedger ledger,
        StructureManager structures,
        OwnerId owner,
        List<Attempt> attempts,
        List<Finding> failures
    ) {
        if (ledger == null) {
            return;
        }
        for (Attempt attempt : attempts) {
            @Nullable
            StructureInstance previous = structures.find(attempt.draft().id().world(), owner, attempt.siting());
            if (previous == null) {
                continue;
            }
            try {
                ledger.release(previous.id());
            } catch (RuntimeException failure) {
                failures.add(
                    rollbackFailure(attempt, "the previous instance could not be released: " + detail(failure))
                );
            }
        }
    }

    private ClaimView remainingClaims(
        ClaimLedger ledger,
        Cuboid bounds,
        Siting siting,
        OwnerId owner,
        StructureManager structures
    ) {
        ClaimView view = ledger.read(bounds);
        List<ClaimRow> kept = new ArrayList<>(view.rows().size());
        for (ClaimRow row : view.rows()) {
            if (!replacedBy(row, siting, owner, structures)) {
                kept.add(row);
            }
        }
        return new ClaimView(kept, view.revision(), view.available());
    }

    private static boolean replacedBy(ClaimRow row, Siting siting, OwnerId owner, StructureManager structures) {
        if (!row.siting().equals(siting)) {
            return false;
        }
        StructureInstance formed = structures.find(row.instance());
        return formed != null && formed.owner().equals(owner);
    }

    private static void attachCleanup(RuntimeException failure, List<Finding> cleanup) {
        if (cleanup.isEmpty()) {
            return;
        }
        IllegalStateException cleanupFailure = new IllegalStateException("cleanup reported " + cleanup);
        failure.addSuppressed(cleanupFailure);
    }

    private static String detail(RuntimeException failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private static List<ClaimRow> claimsOf(MatchResult.Formed formed, Blueprint blueprint) {
        List<ClaimRow> own = new ArrayList<>();
        for (ClaimRow row : formed.claims().rows()) {
            if (row.instance().equals(formed.instance()) && blueprint.bay(row.bay()) != null) {
                own.add(row);
            }
        }
        return List.copyOf(own);
    }

    private static Presence probe(StructureSet.Satellite satellite, WorldView view, SetBudget budget) {
        for (Cuboid box : satellite.presenceCells().boxes()) {
            for (int x = box.min().x(); x < box.max().x(); x++) {
                for (int y = box.min().y(); y < box.max().y(); y++) {
                    for (int z = box.min().z(); z < box.max().z(); z++) {
                        LatticePoint cell = LatticePoint.of(x, y, z);
                        if (!budget.charge(new Spend(1, 0))) {
                            return new Presence(PresenceState.unknown, cell, true);
                        }
                        CellRead read = view.read(cell);
                        if (read instanceof CellRead.Unreadable) {
                            return new Presence(PresenceState.unknown, cell, false);
                        }
                        OccupantSnapshot occupant = ((CellRead.Found) read).occupant();
                        if (provesPresence(occupant)) {
                            return new Presence(PresenceState.present, cell, false);
                        }
                    }
                }
            }
        }
        return new Presence(PresenceState.absent, null, false);
    }

    private static boolean provesPresence(OccupantSnapshot occupant) {
        if (occupant.isAir()) {
            return false;
        }
        return occupant.ref().kind().equals(OccupantKind.block)
                || occupant.ref().kind().equals(OccupantKind.blockEntity);
    }

    private static List<Finding> linkFindings(StructureSet set, StructureSet.Satellite satellite) {
        List<Finding> findings = new ArrayList<>();
        Blueprint root = set.root();
        for (LinkSpec link : satellite.links()) {
            if (link.mode() == LinkMode.none) {
                continue;
            }
            LatticePoint parent = link.parentCell();
            LatticePoint port = link.satelliteCell();
            if (parent == null || port == null) {
                findings.add(
                    problem(
                        Codes.linkCoverageInvalid,
                        "the link of " + satellite.id().text() + " needs both cells",
                        Locus.local(satellite.blueprint().bounds().asSet()),
                        Phase.check
                    )
                );
                continue;
            }
            if (!root.bounds().contains(parent) || !covers(root, parent)) {
                findings.add(
                    problem(
                        Codes.linkCoverageInvalid,
                        "the parent cell " + parent + " of " + satellite.id().text()
                                + " is outside the root or not read by any root check",
                        localOrWorld(set.rootSiting(), parent),
                        Phase.check
                    )
                );
                continue;
            }
            Blueprint satelliteBlueprint = satellite.blueprint();
            if (!satelliteBlueprint.bounds().contains(port) || !covers(satelliteBlueprint, port)) {
                findings.add(
                    problem(
                        Codes.linkCoverageInvalid,
                        "the satellite cell " + port + " of " + satellite.id().text()
                                + " is outside the satellite or not read by any satellite check",
                        localOrWorld(satellite.siting(), port),
                        Phase.check
                    )
                );
                continue;
            }
            LatticePoint parentWorld = set.rootSiting().toWorld(parent);
            LatticePoint portWorld = satellite.siting().toWorld(port);
            if (parentWorld == null || portWorld == null) {
                findings.add(
                    problem(
                        Codes.locusUnmapped,
                        "the link of " + satellite.id().text() + " cannot be mapped into the world",
                        Locus.local(CuboidSet.of(Cuboid.of(parent), Cuboid.of(port))),
                        Phase.check
                    )
                );
                continue;
            }
            long dx = (long) portWorld.x() - parentWorld.x();
            long dy = (long) portWorld.y() - parentWorld.y();
            long dz = (long) portWorld.z() - parentWorld.z();
            Locus cells = Locus.world(CuboidSet.of(Cuboid.of(parentWorld), Cuboid.of(portWorld)));
            long manhattan = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
            if (link.mode() == LinkMode.face) {
                if (manhattan != 1) {
                    findings.add(
                        problem(
                            Codes.linkDistanceInvalid,
                            "the face link of " + satellite.id().text() + " is not adjacent (Manhattan " + manhattan
                                    + ")",
                            cells,
                            Phase.check
                        )
                    );
                }
            } else if (!within(link.metric(), dx, dy, dz, link.maxDistance())) {
                findings.add(
                    problem(
                        Codes.linkDistanceInvalid,
                        "the range link of " + satellite.id().text() + " exceeds " + link.maxDistance() + " under "
                                + link.metric(),
                        cells,
                        Phase.check
                    )
                );
            }
        }
        return findings;
    }

    private static boolean covers(Blueprint blueprint, LatticePoint cell) {
        for (Check<?> check : blueprint.checks()) {
            if (check.reads().cells().contains(cell)) {
                return true;
            }
        }
        return false;
    }

    private static boolean within(DistanceMetric metric, long dx, long dy, long dz, int maxDistance) {
        long x = Math.abs(dx);
        long y = Math.abs(dy);
        long z = Math.abs(dz);
        return switch (metric) {
            case chebyshev -> Math.max(x, Math.max(y, z)) <= maxDistance;
            case manhattan -> x + y + z <= maxDistance;
            case euclidean -> {
                if (x > maxDistance || y > maxDistance || z > maxDistance) {
                    yield false;
                }
                long limit = (long) maxDistance * maxDistance;
                long sum = x * x + y * y;
                yield sum <= limit && z * z <= limit - sum;
            }
        };
    }

    private static Locus localOrWorld(Siting siting, LatticePoint local) {
        LatticePoint world = siting.toWorld(local);
        return world == null
                ? Locus.local(CuboidSet.of(Cuboid.of(local)))
                : Locus.world(CuboidSet.of(Cuboid.of(world)));
    }

    private static Finding satelliteAbsent(StructureSet.Satellite satellite) {
        String message = "the required satellite " + satellite.id().text() + " is absent";
        return problem(Codes.satelliteAbsent, message, Locus.world(satellite.presenceCells()), Phase.check);
    }

    private static Finding presenceUnreadable(
        StructureSet.Satellite satellite,
        @Nullable LatticePoint cell,
        boolean exhausted
    ) {
        String where = cell == null ? "its presence region" : "the cell " + cell;
        String detail = exhausted ? " (the set budget is exhausted)" : "";
        String message = "the presence of " + satellite.id().text() + " is unreadable at " + where + detail;
        Locus locus = cell == null
                ? Locus.world(satellite.presenceCells())
                : Locus.world(CuboidSet.of(Cuboid.of(cell)));
        return problem(Codes.presenceUnreadable, message, locus, Phase.check);
    }

    private static Finding rollbackFailure(Attempt attempt, String detail) {
        String message = "the rollback of " + attempt.key().text() + " at " + attempt.siting().origin() + " failed: "
                + detail;
        return problem(Codes.rollbackFailed, message, Locus.world(CuboidSet.of(attempt.worldBounds())), Phase.settle);
    }

    private static boolean hasErrors(List<Finding> findings) {
        for (Finding finding : findings) {
            if (finding.severity() == Severity.error) {
                return true;
            }
        }
        return false;
    }

    private static Finding problem(CodeEntry entry, String message, @Nullable Locus locus, Phase phase) {
        Finding.Builder builder = Finding.builder(entry).phase(phase).message(message);
        if (locus != null) {
            builder.label(Label.primary(locus, message));
        }
        return builder.build();
    }

    private static StructureSetResult result(
        Map<NamespacedId, MatchOutcome> outcomes,
        List<Finding> setFindings,
        List<NamespacedId> settled,
        List<NamespacedId> rolledBack,
        List<Finding> rollbackFailures,
        Map<NamespacedId, List<ClaimRow>> claims
    ) {
        return new StructureSetResult(outcomes, setFindings, settled, rolledBack, rollbackFailures, claims);
    }

    private record Attempt(
        NamespacedId key,
        Blueprint blueprint,
        Siting siting,
        Cuboid worldBounds,
        MatchRequest request,
        MatchResult.Admitted admitted,
        StructureAttempt draft
    ) {}

    private record Settled(Attempt attempt, MatchResult.Formed result) {}

    private enum PresenceState {
        present, absent, unknown
    }

    private record Presence(PresenceState state, @Nullable LatticePoint cell, boolean budgetExhausted) {}

    private static final class SetBudget {

        private int cells;
        private int sections;

        private SetBudget(Budget budget) {
            this.cells = budget.cells();
            this.sections = budget.sections();
        }

        private Budget remaining() {
            return Budget.of(cells, sections);
        }

        private boolean charge(Spend spend) {
            if (spend.cells() > cells || spend.sections() > sections) {
                return false;
            }
            cells -= spend.cells();
            sections -= spend.sections();
            return true;
        }

        private void spend(Spend spend) {
            cells = Math.max(0, cells - spend.cells());
            sections = Math.max(0, sections - spend.sections());
        }

    }
}
