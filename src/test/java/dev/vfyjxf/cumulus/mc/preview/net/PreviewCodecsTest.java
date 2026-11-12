package dev.vfyjxf.cumulus.mc.preview.net;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The catalog codec carries every payload fact, the stage's bay id included (design 17 §3.2). */
class PreviewCodecsTest {

    private static final NamespacedId stone = NamespacedId.of("minecraft", "stone");
    private static final NamespacedId hearth = NamespacedId.of("structurecumulus", "hearth");

    @Test
    void aCatalogRoundTripKeepsEveryStageFactIncludingItsBay() {
        Stage stage = new Stage("hearth", 9, 3, "lay the ring first", "the firebox", hearth);
        PreviewPresentation presentation = new PreviewPresentation(
            "a furnace",
            List.of(stage),
            0,
            List.of(new MaterialStatus(stone, 9, 4)),
            2
        );
        GhostCell cell = new GhostCell(LatticePoint.of(0, 0, 0), stone, Map.of("facing", "north"), 0);
        PreviewCatalogPayload catalog = new PreviewCatalogPayload(
            List.of(new PreviewCatalogPayload.Entry(hearth, List.of(cell), List.of(), presentation, null))
        );
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
            Unpooled.buffer(),
            RegistryAccess.EMPTY,
            ConnectionType.OTHER
        );
        PreviewCodecs.catalog.encode(buffer, catalog);
        PreviewCatalogPayload decoded = PreviewCodecs.catalog.decode(buffer);
        assertEquals(catalog, decoded);
        assertEquals(0, buffer.readableBytes());
        assertEquals(hearth, decoded.entries().get(0).presentation().stages().get(0).bay());
    }
}
