package dev.vfyjxf.cumulus.mc.pref;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Heading;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildPreferencesTest {

    private static final NamespacedId stone = NamespacedId.of("structurecumulus", "stone");
    private static final NamespacedId pair = NamespacedId.of("structurecumulus", "pair");
    private static final NamespacedId third = NamespacedId.of("structurecumulus", "third");

    @Test
    void rememberingKeepsTheMostRecentlyUsedBlueprints() {
        BuildPreferences preferences = defaults();
        preferences = preferences.remember(stone, siting(1), 2);
        preferences = preferences.remember(pair, siting(2), 2);
        preferences = preferences.remember(third, siting(3), 2);
        assertEquals(List.of(pair, third), List.copyOf(preferences.remembered().keySet()));
    }

    @Test
    void rememberingAnExistingBlueprintRefreshesItsPlaceInTheEvictionOrder() {
        BuildPreferences preferences = defaults();
        preferences = preferences.remember(stone, siting(1), 2);
        preferences = preferences.remember(pair, siting(2), 2);
        preferences = preferences.remember(stone, siting(3), 2);
        preferences = preferences.remember(third, siting(4), 2);
        assertEquals(List.of(stone, third), List.copyOf(preferences.remembered().keySet()));
        assertEquals(List.of(siting(1), siting(3)), Objects.requireNonNull(preferences.history(stone)).recent());
    }

    @Test
    void theHistoryKeepsTheLastEightChoicesPerBlueprint() {
        BuildPreferences preferences = defaults();
        for (int index = 0; index < 10; index++) {
            preferences = preferences.remember(stone, siting(index), 64);
        }
        assertEquals(ChoiceHistory.capacity, Objects.requireNonNull(preferences.history(stone)).recent().size());
        assertEquals(siting(9), preferences.preferred(stone));
    }

    @Test
    void forgettingOneBlueprintKeepsTheOthers() {
        BuildPreferences preferences = defaults().remember(stone, siting(1), 64).remember(pair, siting(2), 64);
        BuildPreferences forgotten = preferences.forget(stone);
        assertNull(forgotten.history(stone));
        assertEquals(siting(2), forgotten.preferred(pair));
        assertTrue(forgotten.forget(null).remembered().isEmpty());
    }

    @Test
    void theSettingsSurviveAMemoryWrite() {
        BuildPreferences preferences = new BuildPreferences(
            OrientationMode.fixedHeading,
            Heading.west,
            RemovalPolicy.allow,
            List.of(PlacementWeight.support),
            Map.of()
        );
        BuildPreferences remembered = preferences.remember(stone, siting(1), 64);
        assertEquals(OrientationMode.fixedHeading, remembered.orientationMode());
        assertEquals(Heading.west, remembered.fixedHeading());
        assertEquals(RemovalPolicy.allow, remembered.removalPolicy());
        assertEquals(List.of(PlacementWeight.support), remembered.weights());
    }

    @Test
    void theWeightKeysAreTheConfigSpelling() {
        BuildPreferences preferences = BuildPreferences.of(
            OrientationMode.autoAnchor,
            null,
            RemovalPolicy.forbid,
            List.of(PlacementWeight.facingPlayer, PlacementWeight.mostProgress, PlacementWeight.align)
        );
        assertEquals(List.of("facing_player", "most_progress", "align"), preferences.weightKeys());
    }

    @Test
    void theStartHeadingFollowsTheMode() {
        BuildPreferences auto = BuildPreferences
                .of(OrientationMode.autoAnchor, Heading.east, RemovalPolicy.forbid, List.of());
        BuildPreferences face = auto.withOrientationMode(OrientationMode.facePlayer);
        BuildPreferences fixed = auto.withOrientationMode(OrientationMode.fixedHeading);
        assertEquals(Heading.north, auto.startHeading(Heading.north));
        assertEquals(Heading.north, face.startHeading(Heading.north));
        assertEquals(Heading.east, fixed.startHeading(Heading.north));
        assertEquals(Heading.north, fixed.withFixedHeading(null).startHeading(Heading.north));
    }

    @Test
    void theRememberedMapCannotBeEditedThroughTheValue() {
        BuildPreferences preferences = defaults().remember(stone, siting(1), 64);
        assertThrows(
            UnsupportedOperationException.class,
            () -> preferences.remembered().put(pair, ChoiceHistory.empty())
        );
        assertEquals(
            Map.of(),
            BuildPreferences.of(OrientationMode.autoAnchor, null, RemovalPolicy.forbid, List.of()).remembered()
        );
    }

    @Test
    void aNonPositiveCapacityIsRejected() {
        BuildPreferences preferences = defaults();
        assertThrows(IllegalArgumentException.class, () -> preferences.remember(stone, siting(1), 0));
    }

    private static BuildPreferences defaults() {
        return BuildPreferences
                .of(OrientationMode.autoAnchor, null, RemovalPolicy.forbid, List.of(PlacementWeight.facingPlayer));
    }

    private static Siting siting(int z) {
        return Siting.of(LatticePoint.of(0, 64, z), Orientation.identity);
    }
}
