package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OccupantTraitsTest {

    @Test
    void keysAreStableIdsAndTypes() {
        assertEquals(NamespacedId.of("structurecumulus", "inventory"), OccupantTraits.inventory.id());
        assertEquals(List.class, OccupantTraits.inventory.type());
        assertEquals(NamespacedId.of("structurecumulus", "energy"), OccupantTraits.energy.id());
        assertEquals(Integer.class, OccupantTraits.energy.type());
        assertEquals(NamespacedId.of("structurecumulus", "fluid"), OccupantTraits.fluid.id());
        assertEquals(List.class, OccupantTraits.fluid.type());
        assertEquals(NamespacedId.of("structurecumulus", "block_entity_nbt"), OccupantTraits.blockEntityNbt.id());
        assertEquals(CompoundTag.class, OccupantTraits.blockEntityNbt.type());
        assertEquals(NamespacedId.of("structurecumulus", "entity_nbt"), OccupantTraits.entityNbt.id());
        assertEquals(CompoundTag.class, OccupantTraits.entityNbt.type());
    }
}
