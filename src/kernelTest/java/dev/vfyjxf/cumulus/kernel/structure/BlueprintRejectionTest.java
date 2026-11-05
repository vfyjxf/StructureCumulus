package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A build refusal stays an IllegalArgumentException and carries its code entry. */
class BlueprintRejectionTest {

    private static NamespacedId id(String path) {
        return NamespacedId.of("test", path);
    }

    @Test
    void theRejectionIsStillAnIllegalArgumentException() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        Bay bay = Bay.of(id("bay"), cells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        Check<?> check = Check.of(id("cells"), bay, Focuses.bay(), Expectations.eachCell(Expectations.anyOccupant()));
        IllegalArgumentException rejected = assertThrows(
            IllegalArgumentException.class,
            () -> Blueprint.builder(id("boundless")).check(check).build()
        );
        assertTrue(rejected instanceof BlueprintRejected, () -> String.valueOf(rejected.getClass()));
        assertEquals(Codes.blueprintInvalid, ((BlueprintRejected) rejected).code());
        String message = java.util.Objects.requireNonNull(rejected.getMessage(), "message");
        assertTrue(message.startsWith("SC-BPT-0001:"), message);
    }

    @Test
    void theOverlapRejectionCarriesItsCodeEntry() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        Bay left = Bay.of(id("left"), cells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        Bay right = Bay.of(id("right"), cells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        BlueprintBuilder builder = Blueprint.builder(id("overlap")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                .check(Check.of(id("left_cells"), left, Focuses.bay(), Expectations.eachCell(Expectations.anyOccupant())))
                .check(
                    Check.of(id("right_cells"), right, Focuses.bay(), Expectations.eachCell(Expectations.anyOccupant()))
                );
        BlueprintRejected rejected = assertThrows(BlueprintRejected.class, builder::build);
        assertEquals(Codes.baysOverlap, rejected.code());
        assertEquals("SC-BPT-0002", rejected.code().code().text());
    }
}
