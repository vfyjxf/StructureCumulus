package dev.vfyjxf.cumulus.mc.preview.net;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.document.DistanceMetric;
import dev.vfyjxf.cumulus.kernel.document.LinkMode;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void aSetEntryRoundTripKeepsItsMembersPresenceAndLinks() {
        PreviewCatalogPayload.SetEntry set = setEntry();
        PreviewCatalogPayload catalog = new PreviewCatalogPayload(List.of(), List.of(set));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
            Unpooled.buffer(),
            RegistryAccess.EMPTY,
            ConnectionType.OTHER
        );
        PreviewCodecs.catalog.encode(buffer, catalog);
        PreviewCatalogPayload decoded = PreviewCodecs.catalog.decode(buffer);
        assertEquals(catalog, decoded);
        assertEquals(0, buffer.readableBytes());
        assertEquals(set, decoded.sets().get(0));
    }

    @Test
    void aSetlessPayloadIsByteIdenticalToTheOlderCatalog() {
        PreviewCatalogPayload.Entry entry = entry();
        byte[] legacy = PreviewCodecProbe.encode(new PreviewCatalogPayload(List.of(entry)));
        byte[] current = PreviewCodecProbe.encode(new PreviewCatalogPayload(List.of(entry), List.of()));
        assertEquals(Arrays.toString(legacy), Arrays.toString(current), "an empty set list writes no trailing bytes");
    }

    @Test
    void aSetPayloadKeepsTheOldCatalogAsItsPrefix() {
        PreviewCatalogPayload.Entry entry = entry();
        byte[] withSets = PreviewCodecProbe.encode(new PreviewCatalogPayload(List.of(entry), List.of(setEntry())));
        byte[] withoutSets = PreviewCodecProbe.encode(new PreviewCatalogPayload(List.of(entry)));
        assertTrue(withSets.length > withoutSets.length);
        assertEquals(
            Arrays.toString(withoutSets),
            Arrays.toString(Arrays.copyOf(withSets, withoutSets.length)),
            "an older client reads the blueprint entries and never sees the sets"
        );
    }

    @Test
    void anOldCatalogReadsBackWithNoSets() {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
            Unpooled.buffer(),
            RegistryAccess.EMPTY,
            ConnectionType.OTHER
        );
        PreviewCodecs.catalog.encode(buffer, new PreviewCatalogPayload(List.of(entry())));
        PreviewCatalogPayload decoded = PreviewCodecs.catalog.decode(buffer);
        assertEquals(List.of(), decoded.sets());
    }

    private static PreviewCatalogPayload.Entry entry() {
        Stage stage = new Stage("hearth", 9, 0, null, null, hearth);
        PreviewPresentation presentation = new PreviewPresentation("a furnace", List.of(stage), 0, List.of(), 0);
        GhostCell cell = new GhostCell(LatticePoint.of(0, 0, 0), stone, Map.of("facing", "north"), 0);
        return new PreviewCatalogPayload.Entry(hearth, List.of(cell), List.of(), presentation, null);
    }

    private static PreviewCatalogPayload.SetEntry setEntry() {
        GhostCell composed = new GhostCell(LatticePoint.of(3, 0, 4), stone, Map.of(), 1);
        PreviewCatalogPayload.Member root = new PreviewCatalogPayload.Member(
            hearth,
            "root",
            true,
            List.of(new GhostCell(LatticePoint.of(0, 0, 0), stone, Map.of(), 0)),
            List.of(),
            List.of()
        );
        PreviewCatalogPayload.Member satellite = new PreviewCatalogPayload.Member(
            stone,
            "structurecumulus:satellite",
            false,
            List.of(composed),
            List.of(Cuboid.of(3, 0, 4, 4, 1, 5)),
            List.of(
                new PreviewCatalogPayload.Link(
                    LinkMode.range,
                    LatticePoint.of(0, 0, 0),
                    LatticePoint.of(3, 0, 4),
                    5,
                    DistanceMetric.manhattan
                )
            )
        );
        return new PreviewCatalogPayload.SetEntry(
            NamespacedId.of("structurecumulus", "codec_set"),
            List.of(root, satellite),
            List.of(new PreviewCatalogPayload.MaterialLine(stone, 2))
        );
    }
}
