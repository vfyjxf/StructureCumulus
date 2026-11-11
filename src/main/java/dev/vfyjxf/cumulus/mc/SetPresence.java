package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.world.CellRead;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The set layer's presence law: a satellite is present when its presence region holds at least one
 * non-air block-family occupant, and every probed cell is charged {@code (1, 0)} against the set's one
 * budget before it is read.
 */
public final class SetPresence {

    public enum State {
        present, absent, unknown
    }

    /** The probe's answer: the state, the cell that decided it, and whether the budget cut it short. */
    public record Probe(State state, @Nullable LatticePoint cell, boolean budgetExhausted) {

        public Probe {
            Objects.requireNonNull(state, "state");
        }
    }

    /** The set's one budget: presence charges every read, an admitted attempt spends its outcome. */
    public static final class Budget {

        private int cells;
        private int sections;

        public Budget(dev.vfyjxf.cumulus.kernel.core.Budget budget) {
            Objects.requireNonNull(budget, "budget");
            this.cells = budget.cells();
            this.sections = budget.sections();
        }

        /** What presence and the earlier attempts left for the next read. */
        public dev.vfyjxf.cumulus.kernel.core.Budget remaining() {
            return dev.vfyjxf.cumulus.kernel.core.Budget.of(cells, sections);
        }

        boolean charge(Spend spend) {
            if (spend.cells() > cells || spend.sections() > sections) {
                return false;
            }
            cells -= spend.cells();
            sections -= spend.sections();
            return true;
        }

        void spend(Spend spend) {
            cells = Math.max(0, cells - spend.cells());
            sections = Math.max(0, sections - spend.sections());
        }
    }

    /** The required satellites first, then the optional ones, each group in declaration order. */
    public static List<StructureSet.Satellite> requiredFirst(List<StructureSet.Satellite> satellites) {
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

    /** Reads the region cell by cell: the first non-air block proves presence, the first refusal unknown. */
    public static Probe probe(StructureSet.Satellite satellite, WorldView view, Budget budget) {
        for (Cuboid box : satellite.presenceCells().decomposition()) {
            for (int x = box.min().x(); x < box.max().x(); x++) {
                for (int y = box.min().y(); y < box.max().y(); y++) {
                    for (int z = box.min().z(); z < box.max().z(); z++) {
                        LatticePoint cell = LatticePoint.of(x, y, z);
                        if (!budget.charge(new Spend(1, 0))) {
                            return new Probe(State.unknown, cell, true);
                        }
                        CellRead read = view.read(cell);
                        if (read instanceof CellRead.Unreadable) {
                            return new Probe(State.unknown, cell, false);
                        }
                        OccupantSnapshot occupant = ((CellRead.Found) read).occupant();
                        if (provesPresence(occupant)) {
                            return new Probe(State.present, cell, false);
                        }
                    }
                }
            }
        }
        return new Probe(State.absent, null, false);
    }

    /** The {@code SC-SET-0004} finding of an unprovable presence, shared by the check and the build. */
    public static Finding unreadable(StructureSet.Satellite satellite, Probe probe) {
        String where = probe.cell() == null ? "its presence region" : "the cell " + probe.cell();
        String detail = probe.budgetExhausted() ? " (the set budget is exhausted)" : "";
        String message = "the presence of " + satellite.id().text() + " is unreadable at " + where + detail;
        Locus locus = probe.cell() == null
                ? Locus.world(satellite.presenceCells())
                : Locus.world(CuboidSet.of(Cuboid.of(probe.cell())));
        return Finding.builder(Codes.presenceUnreadable).phase(Phase.check).message(message)
                .label(Label.primary(locus, message)).build();
    }

    private static boolean provesPresence(OccupantSnapshot occupant) {
        if (occupant.isAir()) {
            return false;
        }
        return occupant.ref().kind().equals(OccupantKind.block)
                || occupant.ref().kind().equals(OccupantKind.blockEntity);
    }

    private SetPresence() {}
}
