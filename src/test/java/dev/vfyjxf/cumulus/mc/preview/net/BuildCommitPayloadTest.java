package dev.vfyjxf.cumulus.mc.preview.net;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildCommitPayloadTest {

    private static final NamespacedId stone = NamespacedId.of("minecraft", "stone");

    private static BuildCommitPayload.CommitCell cell(int x) {
        return new BuildCommitPayload.CommitCell(LatticePoint.of(x, 0, 0), stone, Map.of());
    }

    @Test
    void aCommitCarriesItsCellsInTheOrderItWasGivenAndCopiesThem() {
        List<BuildCommitPayload.CommitCell> cells = new ArrayList<>(List.of(cell(1), cell(2)));
        BuildCommitPayload payload = new BuildCommitPayload(cells, BuildCommitPayload.Status.committed, true, 0);
        cells.clear();
        assertEquals(2, payload.cells().size(), "the payload copies its list");
        assertEquals(1, payload.cells().get(0).cell().x());
        assertTrue(payload.formed());
    }

    @Test
    void onlyACommittedRunCanBeFormed() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new BuildCommitPayload(List.of(cell(0)), BuildCommitPayload.Status.rolledBack, true, 0)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new BuildCommitPayload(List.of(cell(0)), BuildCommitPayload.Status.undone, true, 0)
        );
    }

    @Test
    void anOverflowTruncatesTheListAndCountsTheRest() {
        List<BuildCommitPayload.CommitCell> cells = new ArrayList<>();
        for (int index = 0; index < BuildCommitPayload.maxAnimatedCells; index++) {
            cells.add(cell(index));
        }
        BuildCommitPayload full = new BuildCommitPayload(cells, BuildCommitPayload.Status.committed, false, 3);
        assertEquals(BuildCommitPayload.maxAnimatedCells, full.cells().size());
        assertEquals(3, full.truncated());
        cells.add(cell(BuildCommitPayload.maxAnimatedCells));
        assertThrows(
            IllegalArgumentException.class,
            () -> new BuildCommitPayload(cells, BuildCommitPayload.Status.committed, false, 0)
        );
    }

    @Test
    void aNegativeOverflowIsRejected() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new BuildCommitPayload(List.of(cell(0)), BuildCommitPayload.Status.committed, false, -1)
        );
    }

    @Test
    void aCellsPropertiesAreCopied() {
        Map<String, String> properties = new HashMap<>(Map.of("facing", "north"));
        BuildCommitPayload.CommitCell cell = new BuildCommitPayload.CommitCell(
            LatticePoint.of(0, 0, 0),
            stone,
            properties
        );
        properties.put("facing", "south");
        assertEquals("north", cell.properties().get("facing"));
    }
}
