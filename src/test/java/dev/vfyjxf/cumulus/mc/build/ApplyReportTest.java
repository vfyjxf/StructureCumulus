package dev.vfyjxf.cumulus.mc.build;

import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplyReportTest {

    @Test
    void refusedCarriesNoStepsAndTheFindingsThatCausedIt() {
        Finding finding = Finding.builder(BuildCodes.policyRefusalEntry).phase(Phase.check).message("refused").build();
        ApplyReport report = ApplyReport.refused(FindingBundle.of(List.of(finding)));
        assertEquals(ApplyReport.Status.refused, report.status());
        assertTrue(report.steps().isEmpty());
        assertTrue(report.itemsConsumed().isEmpty());
        assertEquals(1, report.findings().size());
    }

    @Test
    void formedIsTheEmptyCommittedReport() {
        ApplyReport report = ApplyReport.formed();
        assertEquals(ApplyReport.Status.committed, report.status());
        assertTrue(report.steps().isEmpty());
        assertTrue(report.findings().isEmpty());
    }

    @Test
    void stepsKeepTheirPlanOrderAndTheConsumedItemsAreCopies() {
        ItemStack consumed = new ItemStack(Items.STONE, 3);
        ApplyReport report = new ApplyReport(
            ApplyReport.Status.committed,
            List.of(
                new ApplyReport.Step(0, ApplyReport.Outcome.applied, null),
                new ApplyReport.Step(1, ApplyReport.Outcome.skipped, null)
            ),
            List.of(consumed),
            List.of(),
            FindingBundle.empty()
        );
        assertEquals(List.of(0, 1), report.steps().stream().map(ApplyReport.Step::index).toList());
        consumed.setCount(64);
        assertEquals(3, report.itemsConsumed().get(0).getCount(), "the report copies what it is given");
    }

    @Test
    @SuppressWarnings("NullAway")
    void aStepNeedsANonNegativeIndexAndAnOutcome() {
        assertThrows(IllegalArgumentException.class, () -> new ApplyReport.Step(-1, ApplyReport.Outcome.applied, null));
        assertThrows(NullPointerException.class, () -> new ApplyReport.Step(0, null, null));
    }
}
