package dev.vfyjxf.cumulus.mc.preview.scene;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The base plate of design 17 §4.7: two-cell slabs, one tile past the footprint, on the hull's floor. */
class BasePlateTest {

    @Test
    void theGridIsTwoCellTilesSnappedToOneTilePastTheFootprint() {
        BasePlate plate = BasePlate.of(new Hull(2.0F, 3.0F, 4.0F, 5.0F, 7.0F, 6.0F));
        assertEquals(
            0,
            plate.minX(),
            "the footprint's first tile starts at 2, and the plate starts one tile before it"
        );
        assertEquals(2, plate.minZ());
        assertEquals(8, plate.maxX(), "the footprint ends at x = 5, so its last tile ends at 6 and the plate at 8");
        assertEquals(8, plate.maxZ());
        assertEquals(12, plate.tiles(), "a four-by-three tile platform, two cells a tile");
    }

    @Test
    void everyTileIsABoxUnderTheHullsFloor() {
        BasePlate plate = BasePlate.of(new Hull(0.0F, 4.0F, 0.0F, 1.0F, 5.0F, 1.0F));
        assertEquals(4, plate.y(), "the plate's tops lie in the drawn hull's bottom plane (§4.7)");
        Hull tile = plate.tile(plate.minX(), plate.minZ());
        assertEquals(4.0F, tile.maxY(), "the tile's top is the plate's own floor");
        assertEquals(4.0F - BasePlate.thickness, tile.minY(), "a slab is half a cell thick");
        assertEquals(BasePlate.tileSize, (int) (tile.maxX() - tile.minX()), "a slab is two cells on a side");
        assertEquals(BasePlate.tileSize, (int) (tile.maxZ() - tile.minZ()));
        assertEquals(plate.tiles() * 6, plate.quads(), "every tile is a closed box: tops, sides and bottom");
    }

    @Test
    void anEmptyHullDrawsNoPlate() {
        BasePlate plate = BasePlate.of(new Hull(0.0F, 0.0F, 0.0F, -1.0F, -1.0F, -1.0F));
        assertTrue(plate.isEmpty());
        assertEquals(0, plate.tiles());
        assertEquals(0, plate.quads());
        assertEquals(BasePlate.empty(), plate);
    }

    @Test
    void theTwoGreysAlternateTileByTile() {
        BasePlate plate = BasePlate.of(new Hull(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 4.0F));
        assertNotEquals(BasePlate.lightTile, BasePlate.darkTile, "the plate needs two greys to alternate at all");
        for (int x = plate.minX(); x < plate.maxX(); x += BasePlate.tileSize) {
            for (int z = plate.minZ(); z < plate.maxZ(); z += BasePlate.tileSize) {
                assertNotEquals(plate.color(x, z), plate.color(x + BasePlate.tileSize, z), "the next tile flips");
                assertNotEquals(plate.color(x, z), plate.color(x, z + BasePlate.tileSize), "on z too");
                assertEquals(
                    plate.color(x, z),
                    plate.color(x + 2 * BasePlate.tileSize, z),
                    "two tiles on is the same grey again"
                );
            }
        }
    }

    @Test
    void thePlateIsNeverTheHull() {
        Hull hull = new Hull(0.0F, 0.0F, 0.0F, 2.0F, 1.0F, 3.0F);
        BasePlate plate = BasePlate.of(hull);
        assertFalse(plate.isEmpty());
        assertEquals(0.0F, hull.minX(), "the hull's own box never grows to hold the plate");
        assertEquals(2.0F, hull.maxX());
        assertEquals(3.0F, hull.maxZ());
        assertTrue(plate.minX() < hull.minX() && plate.maxX() > hull.maxX(), "the plate is the wider box");
        assertTrue(plate.minZ() < hull.minZ() && plate.maxZ() > hull.maxZ());
        assertEquals(plate, BasePlate.of(hull), "the plate is a pure function of the drawn hull");
    }
}
