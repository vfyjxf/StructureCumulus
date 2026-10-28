package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OccupantTraitsTest {

    @Test
    void keysAreStableIdsAndDirectCapabilityTypes() {
        assertEquals(NamespacedId.of("structurecumulus", "inventory"), OccupantTraits.inventory.id());
        assertEquals(IItemHandler.class, OccupantTraits.inventory.type());
        assertEquals(NamespacedId.of("structurecumulus", "energy"), OccupantTraits.energy.id());
        assertEquals(IEnergyStorage.class, OccupantTraits.energy.type());
        assertEquals(NamespacedId.of("structurecumulus", "fluid"), OccupantTraits.fluid.id());
        assertEquals(IFluidHandler.class, OccupantTraits.fluid.type());
    }

    @Test
    void onlyTheThreeCapabilityKeysExist() {
        Set<String> names = Arrays.stream(OccupantTraits.class.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers())).map(Field::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("inventory", "energy", "fluid"), names);
    }
}
