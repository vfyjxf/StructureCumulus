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

    @Test
    void aStageCarriesItsGroupAndAnAbsentGroupCostsOneByte() {
        Stage grouped = new Stage("root \u00B7 hearth", 9, 0, null, null, hearth, "root");
        Stage plain = new Stage("hearth", 9, 0, null, null, hearth);
        PreviewPresentation withGroup = new PreviewPresentation(null, List.of(grouped), 0, List.of(), 0);
        PreviewPresentation withoutGroup = new PreviewPresentation(null, List.of(plain), 0, List.of(), 0);
        RegistryFriendlyByteBuf first = buffer();
        PreviewCodecs.catalog.encode(
            first,
            new PreviewCatalogPayload(
                List.of(new PreviewCatalogPayload.Entry(hearth, List.of(), List.of(), withGroup, null))
            )
        );
        RegistryFriendlyByteBuf second = buffer();
        PreviewCodecs.catalog.encode(
            second,
            new PreviewCatalogPayload(
                List.of(new PreviewCatalogPayload.Entry(hearth, List.of(), List.of(), withoutGroup, null))
            )
        );
        assertTrue(
            first.writerIndex() > second.writerIndex(),
            "a carried group writes its string; an absent one writes the marker alone"
        );
        PreviewCatalogPayload decoded = PreviewCodecs.catalog.decode(first);
        assertEquals(grouped, decoded.entries().get(0).presentation().stages().get(0));
    }

    @Test
    void aSetEntryRoundTripsItsFlattenedStageTable() {
        PreviewCatalogPayload.SetEntry set = setEntryWithStages();
        PreviewCatalogPayload decoded = roundTrip(new PreviewCatalogPayload(List.of(), List.of(set)));
        assertEquals(set, decoded.sets().get(0));
        assertEquals(2, decoded.sets().get(0).stages().size());
        assertEquals("root \u00B7 hearth", decoded.sets().get(0).stages().get(0).name());
        assertEquals("root", decoded.sets().get(0).stages().get(0).group());
    }

    @Test
    void aSetWithoutAStageTableIsThePrefixOfOneWithIt() {
        PreviewCatalogPayload.SetEntry plain = setEntry();
        PreviewCatalogPayload.SetEntry staged = setEntryWithStages();
        byte[] without = PreviewCodecProbe.encode(new PreviewCatalogPayload(List.of(), List.of(plain)));
        byte[] with = PreviewCodecProbe.encode(new PreviewCatalogPayload(List.of(), List.of(staged)));
        assertTrue(with.length > without.length, "the flattened table is a trailing addition");
        assertEquals(
            Arrays.toString(without),
            Arrays.toString(Arrays.copyOf(with, without.length)),
            "an older client reads the members and never sees the table"
        );
    }

    @Test
    void anOldSetPayloadReadsBackWithNoStageTable() {
        PreviewCatalogPayload decoded = roundTrip(new PreviewCatalogPayload(List.of(), List.of(setEntry())));
        assertEquals(List.of(), decoded.sets().get(0).stages());
    }

    private static PreviewCatalogPayload roundTrip(PreviewCatalogPayload payload) {
        RegistryFriendlyByteBuf buffer = buffer();
        PreviewCodecs.catalog.encode(buffer, payload);
        PreviewCatalogPayload decoded = PreviewCodecs.catalog.decode(buffer);
        assertEquals(0, buffer.readableBytes());
        return decoded;
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);
    }

    private static PreviewCatalogPayload.SetEntry setEntryWithStages() {
        PreviewCatalogPayload.SetEntry base = setEntry();
        return new PreviewCatalogPayload.SetEntry(
            base.setId(),
            base.members(),
            base.materials(),
            List.of(
                new Stage("root \u00B7 hearth", 1, 0, null, null, hearth, "root"),
                new Stage(
                    "structurecumulus:satellite \u00B7 hearth",
                    1,
                    0,
                    null,
                    null,
                    hearth,
                    "structurecumulus:satellite"
                )
            )
        );
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
