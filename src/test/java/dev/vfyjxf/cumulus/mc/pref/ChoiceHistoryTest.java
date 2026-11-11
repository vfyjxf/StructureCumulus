package dev.vfyjxf.cumulus.mc.pref;

import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChoiceHistoryTest {

    private static final LatticePoint origin = LatticePoint.of(1, 64, 1);

    @Test
    void theWindowKeepsTheLastEightChoices() {
        ChoiceHistory history = ChoiceHistory.empty();
        for (int index = 0; index < 10; index++) {
            history = history.append(siting(index));
        }
        assertEquals(ChoiceHistory.capacity, history.recent().size());
        assertEquals(siting(2), history.recent().get(0));
        assertEquals(siting(9), history.recent().get(ChoiceHistory.capacity - 1));
    }

    @Test
    void theModeIsThePreferredChoice() {
        ChoiceHistory history = new ChoiceHistory(List.of(siting(1), siting(2), siting(1)));
        assertEquals(siting(1), history.preferred());
    }

    @Test
    void aTieGoesToTheChoiceWhoseLatestOccurrenceIsTheMostRecent() {
        ChoiceHistory history = new ChoiceHistory(List.of(siting(1), siting(2), siting(1), siting(2)));
        assertEquals(siting(2), history.preferred());
        ChoiceHistory reversed = new ChoiceHistory(List.of(siting(2), siting(1), siting(2), siting(1)));
        assertEquals(siting(1), reversed.preferred());
    }

    @Test
    void anEmptyHistoryHasNoPreference() {
        assertNull(ChoiceHistory.empty().preferred());
        assertNull(new ChoiceHistory(List.of()).preferred());
        assertTrue(ChoiceHistory.empty().isEmpty());
    }

    @Test
    void theConstructorCapsAnOversizedList() {
        List<Siting> oversized = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            oversized.add(siting(index));
        }
        ChoiceHistory history = new ChoiceHistory(oversized);
        assertEquals(ChoiceHistory.capacity, history.recent().size());
        assertEquals(siting(4), history.recent().get(0));
    }

    @Test
    void anAppendLeavesTheOriginalValueAlone() {
        ChoiceHistory original = new ChoiceHistory(List.of(siting(1)));
        ChoiceHistory appended = original.append(siting(2));
        assertEquals(List.of(siting(1)), original.recent());
        assertEquals(List.of(siting(1), siting(2)), appended.recent());
    }

    private static Siting siting(int z) {
        return Siting.of(LatticePoint.of(origin.x(), origin.y(), origin.z() + z), Orientation.identity);
    }
}
