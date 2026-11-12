package dev.vfyjxf.cumulus.mc.pref;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Placement;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildPresetTest {

    private static final NamespacedId stone = NamespacedId.of("structurecumulus", "stone");
    private static final NamespacedId source = NamespacedId.of("structurecumulus", "chest_source");

    @Test
    void aRoundTripKeepsEverySourceForm() {
        for (BuildPreset.Source form : List.of(
            BuildPreset.Source.creative(),
            BuildPreset.Source.player(),
            BuildPreset.Source.container(LatticePoint.of(3, 64, -2)),
            BuildPreset.Source.registered(source)
        )) {
            BuildPreset preset = new BuildPreset("probe", stone, siting(), form, RemovalPolicy.allow);
            BuildPreset loaded = BuildPreset.read(BuildPreset.write(preset));
            assertNotNull(loaded, "the round trip of " + form);
            assertEquals(preset, loaded, "every field survives " + form);
        }
    }

    @Test
    void aRoundTripKeepsEachRemovalPolicy() {
        for (RemovalPolicy policy : RemovalPolicy.values()) {
            BuildPreset preset = new BuildPreset("probe", stone, siting(), BuildPreset.Source.player(), policy);
            BuildPreset loaded = BuildPreset.read(BuildPreset.write(preset));
            assertNotNull(loaded);
            assertEquals(policy, loaded.removalPolicy());
        }
    }

    @Test
    void anEqualPresetWritesAnEqualTag() {
        BuildPreset first = new BuildPreset(
            "probe",
            stone,
            siting(),
            BuildPreset.Source.container(LatticePoint.of(1, 2, 3)),
            RemovalPolicy.ask
        );
        BuildPreset second = new BuildPreset(
            "probe",
            stone,
            siting(),
            BuildPreset.Source.container(LatticePoint.of(1, 2, 3)),
            RemovalPolicy.ask
        );
        assertEquals(BuildPreset.write(first), BuildPreset.write(second), "the encoding has no hash order");
    }

    @Test
    void anUnknownShapeVersionIsRefused() {
        CompoundTag tag = BuildPreset.write(valid());
        tag.putInt("Version", BuildPreset.formatVersion + 1);
        assertNull(BuildPreset.read(tag), "a presets shape this build does not know is skipped");
    }

    @Test
    void aTagWithoutAVersionIsNotAPreset() {
        CompoundTag tag = BuildPreset.write(valid());
        tag.remove("Version");
        assertNull(BuildPreset.read(tag));
    }

    @Test
    void aMalformedSourceIsRefused() {
        CompoundTag unknownKind = BuildPreset.write(valid());
        unknownKind.getCompound("Source").putString("Kind", "somewhere");
        assertNull(BuildPreset.read(unknownKind));

        CompoundTag missingContainer = BuildPreset.write(valid());
        missingContainer.getCompound("Source").putString("Kind", "container");
        assertNull(BuildPreset.read(missingContainer), "a container form carries its position");

        CompoundTag missingProvider = BuildPreset.write(valid());
        missingProvider.getCompound("Source").putString("Kind", "registered");
        assertNull(BuildPreset.read(missingProvider), "a registered form carries its kind id");
    }

    @Test
    void aMalformedSitingIsRefused() {
        CompoundTag tag = BuildPreset.write(valid());
        tag.getCompound("Siting").putInt("Orientation", 99);
        assertNull(BuildPreset.read(tag));
        CompoundTag outside = BuildPreset.write(valid());
        outside.getCompound("Siting").putIntArray("Origin", new int[]{0, 0});
        assertNull(BuildPreset.read(outside));
    }

    @Test
    void aNameIsBounded() {
        assertThrows(IllegalArgumentException.class, () -> preset(""));
        assertThrows(IllegalArgumentException.class, () -> preset("   "));
        assertThrows(IllegalArgumentException.class, () -> preset(" pad "));
        assertThrows(IllegalArgumentException.class, () -> preset("x".repeat(BuildPreset.maxNameLength + 1)));
        assertNotNull(preset("x".repeat(BuildPreset.maxNameLength)));
    }

    @Test
    void aSourceFormCarriesExactlyItsOwnTarget() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new BuildPreset.Source(BuildPreset.Source.Kind.container, null, null)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new BuildPreset.Source(BuildPreset.Source.Kind.registered, null, null)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new BuildPreset.Source(BuildPreset.Source.Kind.creative, LatticePoint.of(0, 0, 0), null)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new BuildPreset.Source(BuildPreset.Source.Kind.player, null, source)
        );
    }

    @Test
    void theTextNamesThePoseAndTheSource() {
        BuildPreset preset = new BuildPreset(
            "probe",
            stone,
            siting(),
            BuildPreset.Source.container(LatticePoint.of(3, 64, -2)),
            RemovalPolicy.forbid
        );
        String text = preset.text();
        assertTrue(
            text.startsWith("probe: structurecumulus:stone at (1,64,2) orientation 3 [container (3,64,-2)]"),
            text
        );
        assertTrue(text.endsWith("removal forbid"), text);
    }

    private static BuildPreset valid() {
        return new BuildPreset("probe", stone, siting(), BuildPreset.Source.creative(), RemovalPolicy.ask);
    }

    private static BuildPreset preset(String name) {
        return new BuildPreset(name, stone, siting(), BuildPreset.Source.creative(), RemovalPolicy.ask);
    }

    private static Siting siting() {
        return Siting.of(LatticePoint.of(1, 64, 2), new Placement(new Orientation(3), LatticePoint.of(0, 0, 0)));
    }
}
