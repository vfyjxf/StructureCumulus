package dev.vfyjxf.cumulus.mc.pref;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Heading;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreferencesSavedDataTest {

    private static final UUID player = UUID.nameUUIDFromBytes("preferences-test".getBytes());
    private static final NamespacedId stone = NamespacedId.of("structurecumulus", "stone");
    private static final NamespacedId pair = NamespacedId.of("structurecumulus", "pair");
    private static final NamespacedId shell = NamespacedId.of("structurecumulus", "shell");
    private static final NamespacedId port = NamespacedId.of("structurecumulus", "port");

    @Test
    void aSaveAndLoadKeepsTheSettingsTheHistoryAndThePins() {
        PreferencesSavedData store = new PreferencesSavedData();
        store.set(
            player,
            BuildPreferences.of(
                OrientationMode.fixedHeading,
                Heading.west,
                RemovalPolicy.allow,
                List.of(PlacementWeight.support, PlacementWeight.remembered)
            )
        );
        store.remember(player, stone, siting(1), 64);
        store.remember(player, stone, siting(2), 64);
        store.remember(player, pair, siting(3), 64);
        store.rememberPart(player, stone, shell, LatticePoint.of(0, 0, 0));
        store.rememberPart(player, pair, port, LatticePoint.of(1, 0, 1));

        PreferencesSavedData loaded = roundTrip(store);
        BuildPreferences preferences = loaded.preferences(player);
        assertEquals(OrientationMode.fixedHeading, preferences.orientationMode());
        assertEquals(Heading.west, preferences.fixedHeading());
        assertEquals(RemovalPolicy.allow, preferences.removalPolicy());
        assertEquals(List.of(PlacementWeight.support, PlacementWeight.remembered), preferences.weights());
        assertEquals(List.of(siting(1), siting(2)), Objects.requireNonNull(preferences.history(stone)).recent());
        assertEquals(siting(3), preferences.preferred(pair));
        assertEquals(LatticePoint.of(0, 0, 0), loaded.partPin(player, stone, shell));
        assertEquals(LatticePoint.of(1, 0, 1), loaded.partPin(player, pair, port));
        assertEquals(2, loaded.rememberedBlueprints(player));
        assertEquals(2, loaded.partPinCount(player));
    }

    @Test
    void aFutureFormatVersionIsDroppedRatherThanGuessedAt() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", PreferencesSavedData.formatVersion + 1);
        PreferencesSavedData loaded = PreferencesSavedData.load(tag, RegistryAccess.EMPTY);
        assertEquals(0, loaded.rememberedBlueprints(player));
        assertEquals(0, loaded.partPinCount(player));
    }

    @Test
    void aMalformedChoiceIsSkippedAndTheRestSurvive() {
        PreferencesSavedData store = new PreferencesSavedData();
        store.remember(player, stone, siting(1), 64);
        store.remember(player, stone, siting(2), 64);
        CompoundTag tag = store.save(new CompoundTag(), RegistryAccess.EMPTY);
        CompoundTag row = tag.getList("Players", Tag.TAG_COMPOUND).getCompound(0);
        ListTag remembered = row.getList("Remembered", Tag.TAG_COMPOUND);
        ListTag choices = remembered.getCompound(0).getList("Choices", Tag.TAG_COMPOUND);
        choices.getCompound(0).putInt("Orientation", 99);

        PreferencesSavedData loaded = PreferencesSavedData.load(tag, RegistryAccess.EMPTY);
        assertEquals(List.of(siting(2)), Objects.requireNonNull(loaded.preferences(player).history(stone)).recent());
    }

    @Test
    void rememberingEvictsTheOldestBlueprint() {
        PreferencesSavedData store = new PreferencesSavedData();
        store.remember(player, stone, siting(1), 2);
        store.remember(player, pair, siting(2), 2);
        store.remember(player, NamespacedId.of("structurecumulus", "third"), siting(3), 2);
        assertEquals(2, store.rememberedBlueprints(player));
        assertNull(store.preferences(player).history(stone));
        assertEquals(siting(2), store.preferences(player).preferred(pair));
    }

    @Test
    void forgettingOneBlueprintKeepsTheOtherMemoryAndTheSettings() {
        PreferencesSavedData store = new PreferencesSavedData();
        store.set(player, BuildPreferences.of(OrientationMode.facePlayer, null, RemovalPolicy.ask, List.of()));
        store.remember(player, stone, siting(1), 64);
        store.remember(player, pair, siting(2), 64);
        store.rememberPart(player, stone, shell, LatticePoint.of(0, 0, 0));
        store.forget(player, stone);
        assertNull(store.preferences(player).history(stone));
        assertEquals(siting(2), store.preferences(player).preferred(pair));
        assertEquals(0, store.partPins(player, stone).size());
        assertEquals(OrientationMode.facePlayer, store.preferences(player).orientationMode());
    }

    @Test
    void forgettingEveryBlueprintClearsTheHistoryAndThePinsButKeepsTheSettings() {
        PreferencesSavedData store = new PreferencesSavedData();
        store.set(
            player,
            BuildPreferences
                    .of(OrientationMode.autoAnchor, null, RemovalPolicy.forbid, List.of(PlacementWeight.support))
        );
        store.remember(player, stone, siting(1), 64);
        store.rememberPart(player, stone, shell, LatticePoint.of(0, 0, 0));
        store.forget(player, null);
        assertTrue(store.preferences(player).remembered().isEmpty());
        assertEquals(0, store.partPinCount(player));
        assertEquals(List.of(PlacementWeight.support), store.preferences(player).weights());
    }

    @Test
    void aPartPinIsRememberedPerBlueprintAndPerPart() {
        PreferencesSavedData store = new PreferencesSavedData();
        store.rememberPart(player, stone, shell, LatticePoint.of(0, 0, 0));
        store.rememberPart(player, stone, shell, LatticePoint.of(1, 0, 0));
        store.rememberPart(player, pair, shell, LatticePoint.of(2, 0, 0));
        assertEquals(LatticePoint.of(1, 0, 0), store.partPin(player, stone, shell));
        assertEquals(LatticePoint.of(2, 0, 0), store.partPin(player, pair, shell));
        assertNull(store.partPin(player, NamespacedId.of("structurecumulus", "other"), shell));
        assertEquals(Map.of(shell, LatticePoint.of(1, 0, 0)), store.partPins(player, stone));
    }

    @Test
    void anUnknownPlayerFallsBackToTheConfiguredDefaults() {
        PreferencesSavedData store = new PreferencesSavedData();
        assertEquals(PrefConfig.defaults(), store.preferences(UUID.nameUUIDFromBytes("nobody".getBytes())));
    }

    @Test
    void aSecondPlayerRowDoesNotLeakIntoTheFirst() {
        UUID other = UUID.nameUUIDFromBytes("other".getBytes());
        PreferencesSavedData store = new PreferencesSavedData();
        store.remember(player, stone, siting(1), 64);
        store.remember(other, pair, siting(2), 64);
        PreferencesSavedData loaded = roundTrip(store);
        assertEquals(1, loaded.rememberedBlueprints(player));
        assertEquals(1, loaded.rememberedBlueprints(other));
        assertNull(loaded.preferences(player).history(pair));
    }

    @Test
    void aSaveAndLoadKeepsThePresetsSortedByName() {
        PreferencesSavedData store = new PreferencesSavedData();
        store.savePreset(player, preset("zeta", stone, RemovalPolicy.ask));
        store.savePreset(player, preset("alpha", pair, RemovalPolicy.forbid));
        store.savePreset(other(), preset("mine", shell, RemovalPolicy.allow));

        PreferencesSavedData loaded = roundTrip(store);
        assertEquals(List.of("alpha", "zeta"), loaded.presets(player).stream().map(BuildPreset::name).toList());
        assertEquals(
            List.of(preset("alpha", pair, RemovalPolicy.forbid), preset("zeta", stone, RemovalPolicy.ask)),
            loaded.presets(player)
        );
        assertEquals(1, loaded.presetCount(other()));
        assertNull(loaded.preset(player, "mine"), "another player's preset never leaks into this row");
    }

    @Test
    void aPresetIsExplicitDataSoForgetAndTheLruNeverRemoveIt() {
        PreferencesSavedData store = new PreferencesSavedData();
        store.savePreset(player, preset("kept", stone, RemovalPolicy.ask));
        for (int index = 0; index < 8; index++) {
            store.remember(player, pair, siting(index), 1);
        }
        store.forget(player, pair);
        store.forget(player, null);
        PreferencesSavedData loaded = roundTrip(store);
        assertEquals(1, loaded.presetCount(player), "the memory's clears never touch the presets");
        assertEquals(preset("kept", stone, RemovalPolicy.ask), loaded.preset(player, "kept"));
    }

    @Test
    void aFullStoreRefusesANewNameAndStillReplacesAKnownOne() {
        PreferencesSavedData store = new PreferencesSavedData();
        for (int index = 0; index < BuildPreset.capacity; index++) {
            assertEquals(
                PreferencesSavedData.SaveResult.saved,
                store.savePreset(player, preset("preset-" + index, stone, RemovalPolicy.ask))
            );
        }
        assertEquals(BuildPreset.capacity, store.presetCount(player));
        assertEquals(
            PreferencesSavedData.SaveResult.full,
            store.savePreset(player, preset("one-too-many", stone, RemovalPolicy.ask)),
            "a full store refuses the next new name"
        );
        assertEquals(BuildPreset.capacity, store.presetCount(player));
        assertEquals(
            PreferencesSavedData.SaveResult.replaced,
            store.savePreset(player, preset("preset-0", pair, RemovalPolicy.forbid)),
            "a known name is overwritten under the same bound"
        );
        assertEquals(preset("preset-0", pair, RemovalPolicy.forbid), store.preset(player, "preset-0"));
        assertEquals(BuildPreset.capacity, store.presetCount(player));
    }

    @Test
    void deleteRemovesOneNameAndAnswersFalseForAnUnknownOne() {
        PreferencesSavedData store = new PreferencesSavedData();
        store.savePreset(player, preset("mine", stone, RemovalPolicy.ask));
        assertTrue(store.deletePreset(player, "mine"));
        assertEquals(0, store.presetCount(player));
        assertFalse(store.deletePreset(player, "mine"));
        assertFalse(store.deletePreset(other(), "mine"));
    }

    @Test
    void theDefaultNameIsTheFirstFreePresetN() {
        PreferencesSavedData store = new PreferencesSavedData();
        assertEquals("preset-1", store.nextPresetName(player));
        store.savePreset(player, preset("preset-1", stone, RemovalPolicy.ask));
        assertEquals("preset-2", store.nextPresetName(player));
        store.savePreset(player, preset("preset-3", stone, RemovalPolicy.ask));
        assertEquals("preset-2", store.nextPresetName(player), "the first free index wins, not the next one");
        store.savePreset(player, preset("preset-2", stone, RemovalPolicy.ask));
        assertEquals("preset-4", store.nextPresetName(player));
        store.deletePreset(player, "preset-2");
        assertEquals("preset-2", store.nextPresetName(player), "a deleted default name is reused deterministically");
    }

    @Test
    void anUnknownPresetShapeIsSkippedAndTheRestSurvive() {
        PreferencesSavedData store = new PreferencesSavedData();
        store.savePreset(player, preset("first", stone, RemovalPolicy.ask));
        store.savePreset(player, preset("second", pair, RemovalPolicy.ask));
        CompoundTag tag = store.save(new CompoundTag(), RegistryAccess.EMPTY);
        CompoundTag row = tag.getList("Players", Tag.TAG_COMPOUND).getCompound(0);
        ListTag presets = row.getList("Presets", Tag.TAG_COMPOUND);
        presets.getCompound(0).putInt("Version", BuildPreset.formatVersion + 99);

        PreferencesSavedData loaded = PreferencesSavedData.load(tag, RegistryAccess.EMPTY);
        assertEquals(1, loaded.presetCount(player));
        assertEquals(preset("second", pair, RemovalPolicy.ask), loaded.preset(player, "second"));
    }

    private static PreferencesSavedData roundTrip(PreferencesSavedData store) {
        return PreferencesSavedData.load(store.save(new CompoundTag(), RegistryAccess.EMPTY), RegistryAccess.EMPTY);
    }

    private static BuildPreset preset(String name, NamespacedId blueprint, RemovalPolicy policy) {
        return new BuildPreset(
            name,
            blueprint,
            siting(7),
            BuildPreset.Source.container(LatticePoint.of(2, 64, 3)),
            policy
        );
    }

    private static UUID other() {
        return UUID.nameUUIDFromBytes("other".getBytes());
    }

    private static Siting siting(int z) {
        return Siting.of(LatticePoint.of(0, 64, z), Orientation.identity);
    }
}
