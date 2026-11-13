package dev.vfyjxf.cumulus.mc.preview;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.VariantGroup;
import dev.vfyjxf.cumulus.kernel.structure.VariantMember;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The demo content of design 17 §11: the three shipped files compile, keep the catalogue's identity
 * projection drawable, carry the bay and stage counts the design names, and repeat their group
 * declaration verbatim.
 */
class DemoDocumentsTest {

    private static final StructureKernel kernel = DemoDocuments.kernel();

    @Test
    void theThreeShippedDocumentsCompileWithoutFindings() {
        for (NamespacedId id : DemoDocuments.ids) {
            Blueprint blueprint = DemoDocuments.compiled(kernel, id);
            assertEquals(id, blueprint.id(), () -> id + " keeps its declared root id");
            assertFalse(blueprint.bays().isEmpty(), () -> id + " declares bays");
            assertFalse(blueprint.checks().isEmpty(), () -> id + " declares checks");
        }
    }

    @Test
    void theIdentityProjectionReportsNoFilter() {
        for (NamespacedId id : DemoDocuments.ids) {
            PreviewExpected.Outcome outcome = PreviewExpected
                    .of(DemoDocuments.compiled(kernel, id), Orientation.identity);
            assertNull(outcome.filter(), () -> id + " cannot express the identity orientation");
            assertFalse(outcome.states().isEmpty(), () -> id + " draws nothing");
            assertTrue(
                hasDirectionalState(outcome.states()),
                () -> id + " carries at least one direction-sensitive block"
            );
        }
    }

    @Test
    void theBayAndStageCountsAreTheOnesTheDesignNames() {
        assertEquals(
            List.of(
                "structurecumulus:plinth",
                "structurecumulus:housing",
                "structurecumulus:core",
                "structurecumulus:press"
            ),
            stageNames(DemoDocuments.machine)
        );
        assertEquals(List.of(25, 32, 18, 25), stageCells(DemoDocuments.machine));
        assertEquals(100, DemoDocuments.compiled(kernel, DemoDocuments.machine).bounds().volume());

        assertEquals(
            List.of(
                "structurecumulus:base",
                "structurecumulus:shell",
                "structurecumulus:boiler",
                "structurecumulus:cap"
            ),
            stageNames(DemoDocuments.compact)
        );
        assertEquals(List.of(9, 32, 4, 9), stageCells(DemoDocuments.compact));

        assertEquals(
            List.of("structurecumulus:plinth", "structurecumulus:shell", "structurecumulus:roof"),
            stageNames(DemoDocuments.tower)
        );
        assertEquals(List.of(25, 78, 25), stageCells(DemoDocuments.tower));
    }

    @Test
    void theTwoMachineMembersRepeatTheirGroupDeclarationVerbatim() {
        Blueprint machine = DemoDocuments.compiled(kernel, DemoDocuments.machine);
        Blueprint compact = DemoDocuments.compiled(kernel, DemoDocuments.compact);

        assertEquals(
            DemoDocuments.variants(DemoDocuments.machine),
            DemoDocuments.variants(DemoDocuments.compact),
            "the two members write the same variants block"
        );

        VariantGroup group = machine.variants();
        assertNotNull(group, "the machine declares a group");
        assertEquals(NamespacedId.of("structurecumulus", "demo_machine"), group.group());
        assertEquals(
            List.of(
                NamespacedId.of("structurecumulus", "demo_machine"),
                NamespacedId.of("structurecumulus", "demo_machine_compact")
            ),
            group.members().stream().map(VariantMember::id).toList()
        );
        assertEquals(List.of("full", "compact"), group.members().stream().map(VariantMember::label).toList());
        assertEquals(group, compact.variants(), "the compact member carries the same compiled group");
        assertNull(DemoDocuments.compiled(kernel, DemoDocuments.tower).variants(), "the tower stands alone");
    }

    @Test
    void eachDocumentMarksItselfAsADemonstrationInItsRootDescription() {
        for (NamespacedId id : DemoDocuments.ids) {
            String description = DemoDocuments.compiled(kernel, id).description();
            assertNotNull(description, () -> id + " carries a root description");
            assertTrue(
                description.startsWith("a demonstration document:"),
                () -> id + " opens its description with the demonstration marking: " + description
            );
        }
    }

    @Test
    void everyMachineBayCarriesItsDescriptionAndHint() {
        Blueprint machine = DemoDocuments.compiled(kernel, DemoDocuments.machine);
        for (var bay : machine.bays()) {
            assertNotNull(bay.description(), () -> bay.id() + " carries a description");
            assertNotNull(bay.hint(), () -> bay.id() + " carries a hint");
        }
    }

    private static List<String> stageNames(NamespacedId id) {
        return stages(id).stream().map(StageModel.Stage::name).toList();
    }

    private static List<Integer> stageCells(NamespacedId id) {
        return stages(id).stream().map(stage -> stage.cells().size()).toList();
    }

    private static List<StageModel.Stage> stages(NamespacedId id) {
        Blueprint blueprint = DemoDocuments.compiled(kernel, id);
        PreviewExpected.Outcome outcome = PreviewExpected.of(blueprint, Orientation.identity);
        assertNull(outcome.filter(), () -> id + " cannot express the identity orientation");
        return StageModel.of(blueprint, outcome.states().keySet()).stages();
    }

    private static boolean hasDirectionalState(Map<LatticePoint, BlockState> states) {
        for (BlockState state : states.values()) {
            for (Property<?> property : state.getProperties()) {
                if (property.getValueClass() == Direction.class) {
                    return true;
                }
            }
        }
        return false;
    }
}
