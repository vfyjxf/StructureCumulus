package dev.vfyjxf.cumulus.mc.build;

import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.finding.Applicability;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.remedy.Edit;
import dev.vfyjxf.cumulus.kernel.remedy.EditPlan;
import dev.vfyjxf.cumulus.kernel.remedy.EditStep;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildGatesTest {

    private static final LatticePoint origin = LatticePoint.of(10, 20, 30);
    private static final Siting siting = Siting.of(origin, Orientation.identity);
    private static final NamespacedId bay = NamespacedId.of("structurecumulus", "gametest_bay");
    private static final InstanceId instance = new InstanceId(World.of(NamespacedId.of("minecraft", "overworld")), 1);

    @Test
    void aCleanPlanHasNoGates() {
        assertTrue(BuildGates.evaluate(plan(place()), steps(1), BuildPolicy.defaults(), null, source(4)).isEmpty());
    }

    @Test
    void theVolumeCeilingRefusesAPlanOverIt() {
        CuboidSet three = Cuboid.of(origin, LatticePoint.of(origin.x() + 3, origin.y() + 1, origin.z() + 1)).asSet();
        List<Finding> findings = BuildGates.evaluate(
            plan(new Edit.Place(three, NamespacedId.of("minecraft", "stone"), Map.of())),
            steps(three, 3),
            BuildPolicy.defaults().withMaxTouchedVolume(2),
            null,
            source(3)
        );
        assertEquals(1, findings.size());
        assertEquals(BuildCodes.policyRefusal, findings.get(0).code());
        assertTrue(findings.get(0).message().contains("3 cells"));
    }

    @Test
    void aRemoveIsRefusedUnlessRemovalIsLifted() {
        EditPlan remove = plan(new Edit.Remove(cells()));
        List<Finding> refused = BuildGates.evaluate(remove, noItems(), BuildPolicy.defaults(), null, source(0));
        assertEquals(1, refused.size());
        assertEquals(BuildCodes.policyRefusal, refused.get(0).code());
        assertTrue(
            BuildGates.evaluate(remove, noItems(), BuildPolicy.defaults().withAllowRemoval(true), null, source(0))
                    .isEmpty()
        );
    }

    @Test
    void aForeignExclusiveClaimRefusesTheRun() {
        ClaimView exclusive = claims(Sharing.exclusive);
        List<Finding> findings = BuildGates
                .evaluate(plan(place()), steps(1), BuildPolicy.defaults(), exclusive, source(1));
        assertEquals(1, findings.size());
        assertEquals(BuildCodes.policyRefusal, findings.get(0).code());
        assertTrue(findings.get(0).message().contains("foreign exclusive claim"));
    }

    @Test
    void aSharedClaimIsAllowed() {
        assertTrue(
            BuildGates.evaluate(plan(place()), steps(1), BuildPolicy.defaults(), claims(Sharing.shared), source(1))
                    .isEmpty()
        );
    }

    @Test
    void aShortageNamesThePerItemCounts() {
        List<Finding> findings = BuildGates.evaluate(plan(place()), steps(4), BuildPolicy.defaults(), null, source(2));
        assertEquals(1, findings.size());
        assertEquals(BuildCodes.itemShortage, findings.get(0).code());
        assertTrue(findings.get(0).notes().stream().anyMatch(note -> note.message().contains("2/4")));
    }

    private static List<MaterializedEdit> steps(int stones) {
        return steps(cells(), stones);
    }

    /** One step writing exactly the given cells, so a step and its plan never disagree on the volume. */
    private static List<MaterializedEdit> steps(CuboidSet cells, int stones) {
        Map<LatticePoint, BlockPlacement> placements = new TreeMap<>();
        for (Cuboid box : cells.decomposition()) {
            for (int x = box.min().x(); x < box.max().x(); x++) {
                for (int y = box.min().y(); y < box.max().y(); y++) {
                    for (int z = box.min().z(); z < box.max().z(); z++) {
                        placements.put(
                            LatticePoint.of(x, y, z),
                            new BlockPlacement(Blocks.STONE.defaultBlockState(), null)
                        );
                    }
                }
            }
        }
        return List.of(
            new MaterializedEdit(
                placements,
                List.of(new ItemRequirement(new ItemStack(Items.STONE), stones)),
                List.of()
            )
        );
    }

    private static List<MaterializedEdit> noItems() {
        return List.of(
            new MaterializedEdit(
                Map.of(origin, new BlockPlacement(Blocks.AIR.defaultBlockState(), null)),
                List.of(),
                List.of()
            )
        );
    }

    private static EditPlan plan(Edit edit) {
        return new EditPlan(List.of(new EditStep(edit, Locus.world(cells()), Applicability.machineApplicable)), "test");
    }

    private static Edit.Place place() {
        return new Edit.Place(cells(), NamespacedId.of("minecraft", "stone"), Map.of());
    }

    private static CuboidSet cells() {
        return CuboidSet.of(Cuboid.of(origin));
    }

    private static ClaimView claims(Sharing sharing) {
        return new ClaimView(List.of(new ClaimRow(LatticeKey.of(origin), sharing, siting, bay, instance)), 1, true);
    }

    private static ItemSource source(int stone) {
        return new ItemHandlerSource(
            new FakeItemHandler(1, 64, stone == 0 ? ItemStack.EMPTY : new ItemStack(Items.STONE, stone))
        );
    }
}
