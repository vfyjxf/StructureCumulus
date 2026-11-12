package dev.vfyjxf.cumulus.mc.build;

import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The executor's only answer: what happened to every plan step, what it cost and which cells the
 * transaction wrote.
 */
public record ApplyReport(
    Status status,
    List<Step> steps,
    List<ItemStack> itemsConsumed,
    List<Change> changes,
    FindingBundle findings
) {

    public ApplyReport {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(steps, "steps");
        Objects.requireNonNull(itemsConsumed, "itemsConsumed");
        Objects.requireNonNull(changes, "changes");
        Objects.requireNonNull(findings, "findings");
        List<ItemStack> consumed = new ArrayList<>(itemsConsumed.size());
        for (ItemStack stack : itemsConsumed) {
            consumed.add(stack.copy());
        }
        steps = List.copyOf(steps);
        itemsConsumed = List.copyOf(consumed);
        changes = List.copyOf(changes);
    }

    /** A run that refused: no write happened and the findings are the reason. */
    public static ApplyReport refused(FindingBundle findings) {
        return new ApplyReport(Status.refused, List.of(), List.of(), List.of(), findings);
    }

    /** A structure already formed: the idempotent empty answer. */
    public static ApplyReport formed() {
        return new ApplyReport(Status.committed, List.of(), List.of(), List.of(), FindingBundle.empty());
    }

    public enum Status {
        committed, rolledBack, partiallyRolledBack, refused
    }

    public record Step(int index, Outcome outcome, @Nullable String reason) {

        public Step {
            if (index < 0) {
                throw new IllegalArgumentException("index >= 0");
            }
            Objects.requireNonNull(outcome, "outcome");
        }

        public boolean failed() {
            return outcome == Outcome.failed;
        }
    }

    public enum Outcome {
        applied, skipped, failed, restored
    }

    /** One cell the transaction wrote and the state it left there, in write order. */
    public record Change(LatticePoint cell, BlockPlacement placement) {

        public Change {
            Objects.requireNonNull(cell, "cell");
            Objects.requireNonNull(placement, "placement");
        }
    }
}
