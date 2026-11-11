package dev.vfyjxf.cumulus.mc.pref;

import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Heading;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.DoorView;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The siting score: the ordered weights summed over one candidate's measured facts. Ties fall back to
 * the candidate's canonical enumeration order, so a scrambled input list ranks identically.
 */
public final class SitingCandidateScorer {

    /** The bonus of a pose whose declared front heading matches the player's facing. */
    public static final int facingBonus = 100;
    /** The penalty per cell the pose has to clear first. */
    public static final int removalPenalty = 10;
    /** The bonus per cell the pose already satisfies. */
    public static final int progressBonus = 10;
    /** The penalty per block of horizontal distance from the player. */
    public static final int distanceStep = 10;
    /** The distance penalty ceiling keeps the sum int-safe at a far corner of the lattice. */
    public static final int maxDistance = 1_000;
    /** A remembered pose leads the order outright. */
    public static final int rememberedBonus = 1_000_000;
    private static final int maxMeasured = 1_000_000;

    /**
     * One candidate and what the adapter measured at its siting; {@code order} is the canonical
     * enumeration index, and the two support percentages measure the world face below and above the
     * sited bounds.
     */
    public record Candidate(Siting siting, int order, int satisfied, int removals, int supportBelow, int supportAbove) {

        public Candidate {
            Objects.requireNonNull(siting, "siting");
            if (order < 0) {
                throw new IllegalArgumentException("order >= 0");
            }
            if (satisfied < 0 || satisfied > maxMeasured) {
                throw new IllegalArgumentException("satisfied out of range: " + satisfied);
            }
            if (removals < 0 || removals > maxMeasured) {
                throw new IllegalArgumentException("removals out of range: " + removals);
            }
            if (supportBelow < 0 || supportBelow > 100 || supportAbove < 0 || supportAbove > 100) {
                throw new IllegalArgumentException("a support rate is a percentage");
            }
        }
    }

    /**
     * What the player and the blueprint contribute to every candidate: the player's position and
     * horizontal facing, the declaration frame's front heading, the blueprint's memory and whether it
     * declares a hanging pose.
     */
    public record View(
        LatticePoint player,
        Heading playerFacing,
        Heading front,
        @Nullable ChoiceHistory memory,
        boolean hanging
    ) {

        public View {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(playerFacing, "playerFacing");
            Objects.requireNonNull(front, "front");
        }
    }

    /** A ranked candidate; equal scores keep the canonical order. */
    public record Scored(Siting siting, int score, int order) {

        public Scored {
            Objects.requireNonNull(siting, "siting");
        }
    }

    public SitingCandidateScorer() {}

    /** Ranks the candidates by descending score, then by canonical order. */
    public List<Scored> rank(List<Candidate> candidates, View view, @Nullable List<PlacementWeight> weights) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(view, "view");
        List<PlacementWeight> ordered = weights == null ? List.of() : List.copyOf(weights);
        List<Scored> scored = new ArrayList<>(candidates.size());
        Set<Integer> orders = new HashSet<>();
        Set<Siting> sitings = new HashSet<>();
        for (Candidate candidate : candidates) {
            Objects.requireNonNull(candidate, "candidate");
            if (!orders.add(candidate.order())) {
                throw new IllegalArgumentException("duplicate canonical order " + candidate.order());
            }
            if (!sitings.add(candidate.siting())) {
                throw new IllegalArgumentException("duplicate siting " + candidate.siting());
            }
            scored.add(new Scored(candidate.siting(), score(candidate, view, ordered), candidate.order()));
        }
        scored.sort(Comparator.comparingInt(Scored::score).reversed().thenComparingInt(Scored::order));
        return List.copyOf(scored);
    }

    /** One candidate's score; a null or empty weight list is the canonical order, so the score is 0. */
    public int score(Candidate candidate, View view, @Nullable List<PlacementWeight> weights) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(view, "view");
        if (weights == null || weights.isEmpty()) {
            return 0;
        }
        long total = 0L;
        for (PlacementWeight weight : weights) {
            total += switch (weight) {
                case facingPlayer ->
                    candidate.siting().placement().orientation().apply(view.front()) == view.playerFacing()
                            ? facingBonus
                            : 0;
                case fewestRemovals -> -((long) removalPenalty * candidate.removals());
                case mostProgress -> (long) progressBonus * candidate.satisfied();
                case nearestToPlayer -> -((long) distanceStep * distance(candidate.siting().origin(), view.player()));
                case remembered -> remembered(candidate.siting(), view.memory()) ? rememberedBonus : 0;
                case support -> view.hanging() ? candidate.supportAbove() : candidate.supportBelow();
                case align -> 0;
            };
        }
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, total));
    }

    /**
     * The declaration frame's front heading: the door focus's side of the blueprint when a check
     * declares one, otherwise the identity front ({@code -Z}).
     */
    public static Heading frontOf(Blueprint blueprint) {
        Objects.requireNonNull(blueprint, "blueprint");
        for (Check<?> check : blueprint.checks()) {
            if (check.focus().subject() != DoorView.class) {
                continue;
            }
            List<LatticePoint> cells = cells(check.focus().staticCells(check.bay().cells()));
            if (cells.isEmpty()) {
                continue;
            }
            Heading front = frontOf(cells, blueprint.bounds());
            if (front != null) {
                return front;
            }
        }
        return Heading.north;
    }

    /** The dominant side of the bounds the given declaration cells sit on; null when centred. */
    static @Nullable Heading frontOf(List<LatticePoint> cells, Cuboid bounds) {
        Objects.requireNonNull(cells, "cells");
        Objects.requireNonNull(bounds, "bounds");
        if (cells.isEmpty()) {
            return null;
        }
        long sumX = 0L;
        long sumZ = 0L;
        for (LatticePoint cell : cells) {
            sumX += cell.x();
            sumZ += cell.z();
        }
        long centerX = (long) (bounds.min().x() + bounds.max().x()) * cells.size();
        long centerZ = (long) (bounds.min().z() + bounds.max().z()) * cells.size();
        long deltaX = 2L * sumX - centerX;
        long deltaZ = 2L * sumZ - centerZ;
        if (deltaX == 0L && deltaZ == 0L) {
            return null;
        }
        if (Math.abs(deltaX) >= Math.abs(deltaZ)) {
            return deltaX > 0L ? Heading.east : Heading.west;
        }
        return deltaZ > 0L ? Heading.south : Heading.north;
    }

    private static boolean remembered(Siting siting, @Nullable ChoiceHistory memory) {
        if (memory == null) {
            return false;
        }
        Siting preferred = memory.preferred();
        return preferred != null && preferred.equals(siting);
    }

    private static long distance(LatticePoint origin, LatticePoint player) {
        long dx = Math.abs((long) origin.x() - player.x());
        long dz = Math.abs((long) origin.z() - player.z());
        return Math.min(Math.max(dx, dz), maxDistance);
    }

    private static List<LatticePoint> cells(CuboidSet set) {
        List<LatticePoint> cells = new ArrayList<>();
        for (Cuboid box : set.decomposition()) {
            for (int x = box.min().x(); x <= box.max().x(); x++) {
                for (int y = box.min().y(); y <= box.max().y(); y++) {
                    for (int z = box.min().z(); z <= box.max().z(); z++) {
                        cells.add(LatticePoint.of(x, y, z));
                    }
                }
            }
        }
        return List.copyOf(cells);
    }
}
