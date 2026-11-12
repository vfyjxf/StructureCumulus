package dev.vfyjxf.cumulus.mc.preview.net;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Heading;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The open payload's two facts and the heading's trailing-optional wire form (design 17 §3.2). */
class PreviewOpenPayloadTest {

    private static final NamespacedId hearth = NamespacedId.of("structurecumulus", "hearth");

    @Test
    void aRoundTripKeepsTheFourHorizons() {
        for (Heading facing : List.of(Heading.north, Heading.south, Heading.west, Heading.east)) {
            PreviewOpenPayload payload = new PreviewOpenPayload(hearth, facing);
            assertEquals(payload, roundTrip(payload), facing.name());
        }
    }

    @Test
    void anAbsentHeadingIsTheDefaultAndCostsNoByte() {
        PreviewOpenPayload absent = new PreviewOpenPayload(hearth);
        assertNull(absent.facing());
        assertEquals(absent, roundTrip(absent));
        assertEquals(framedBytes(new PreviewOpenPayload(hearth, null)), framedBytes(absent));
        assertEquals(
            framedBytes(absent) + 1,
            framedBytes(new PreviewOpenPayload(hearth, Heading.north)),
            "the heading is one trailing byte, so an older server ignores it"
        );
    }

    @Test
    void anOldClientWithoutTheHeadingReadsAsAbsent() {
        RegistryFriendlyByteBuf buffer = buffer();
        // exactly what a client that predates the field writes: the id alone
        buffer.writeUtf(hearth.namespace());
        buffer.writeUtf(hearth.path());
        PreviewOpenPayload decoded = PreviewCodecs.open.decode(buffer);
        assertEquals(hearth, decoded.blueprintId());
        assertNull(decoded.facing());
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void aVerticalReadingTravelsAsAbsent() {
        for (Heading facing : List.of(Heading.up, Heading.down)) {
            PreviewOpenPayload payload = new PreviewOpenPayload(hearth, facing);
            assertEquals(new PreviewOpenPayload(hearth), roundTrip(payload), facing.name());
        }
        RegistryFriendlyByteBuf buffer = buffer();
        buffer.writeUtf(hearth.namespace());
        buffer.writeUtf(hearth.path());
        buffer.writeByte(Heading.up.ordinal());
        assertNull(PreviewCodecs.open.decode(buffer).facing());
    }

    @Test
    void anUnknownTrailingReadingIsAbsentAndNotACrash() {
        RegistryFriendlyByteBuf buffer = buffer();
        buffer.writeUtf(hearth.namespace());
        buffer.writeUtf(hearth.path());
        buffer.writeByte(200);
        PreviewOpenPayload decoded = PreviewCodecs.open.decode(buffer);
        assertEquals(hearth, decoded.blueprintId());
        assertNull(decoded.facing());
    }

    private static PreviewOpenPayload roundTrip(PreviewOpenPayload payload) {
        RegistryFriendlyByteBuf buffer = buffer();
        PreviewCodecs.open.encode(buffer, payload);
        PreviewOpenPayload decoded = PreviewCodecs.open.decode(buffer);
        assertEquals(0, buffer.readableBytes());
        return decoded;
    }

    private static int framedBytes(PreviewOpenPayload payload) {
        RegistryFriendlyByteBuf buffer = buffer();
        PreviewCodecs.open.encode(buffer, payload);
        return buffer.writerIndex();
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);
    }
}
