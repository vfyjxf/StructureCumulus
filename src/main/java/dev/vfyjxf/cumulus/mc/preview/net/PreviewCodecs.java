package dev.vfyjxf.cumulus.mc.preview.net;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.remedy.CellMark;
import dev.vfyjxf.cumulus.kernel.remedy.Marker;
import dev.vfyjxf.cumulus.kernel.remedy.MarkerKind;
import dev.vfyjxf.cumulus.kernel.remedy.OverlayModel;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.LocusFrame;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Placement;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The two wire codecs of a preview session. Marker loci travel as world cells, never as source spans. */
final class PreviewCodecs {

    static final StreamCodec<RegistryFriendlyByteBuf, PreviewSyncPayload> sync = StreamCodec
            .of(PreviewCodecs::encodeSync, PreviewCodecs::decodeSync);

    static final StreamCodec<RegistryFriendlyByteBuf, PreviewControlPayload> control = StreamCodec
            .of(PreviewCodecs::encodeControl, PreviewCodecs::decodeControl);

    static final StreamCodec<RegistryFriendlyByteBuf, PreviewCatalogPayload> catalog = StreamCodec
            .of(PreviewCodecs::encodeCatalog, PreviewCodecs::decodeCatalog);

    static final StreamCodec<RegistryFriendlyByteBuf, PreviewOpenPayload> open = StreamCodec
            .of(PreviewCodecs::encodeOpen, PreviewCodecs::decodeOpen);

    private PreviewCodecs() {}

    private static void encodeSync(RegistryFriendlyByteBuf buffer, PreviewSyncPayload payload) {
        writeId(buffer, payload.blueprintId());
        buffer.writeVarInt(payload.candidates().size());
        for (SitingCandidate candidate : payload.candidates()) {
            writeSiting(buffer, candidate.siting());
            buffer.writeVarInt(candidate.score());
        }
        buffer.writeVarInt(payload.current());
        OverlayModel overlay = payload.overlay();
        writeCuboid(buffer, overlay.bounds());
        buffer.writeVarInt(overlay.cells().size());
        for (Map.Entry<LatticeKey, CellMark> cell : overlay.cells().entrySet()) {
            buffer.writeLong(cell.getKey().packed());
            buffer.writeByte(cell.getValue().ordinal());
        }
        writeMarkers(buffer, overlay.markers());
        buffer.writeVarInt(payload.ghostCells().size());
        for (GhostCell ghost : payload.ghostCells()) {
            writeGhost(buffer, ghost);
        }
        writeMarkers(buffer, payload.markers());
    }

    private static PreviewSyncPayload decodeSync(RegistryFriendlyByteBuf buffer) {
        NamespacedId blueprintId = readId(buffer);
        int candidateCount = buffer.readVarInt();
        List<SitingCandidate> candidates = new ArrayList<>(candidateCount);
        for (int index = 0; index < candidateCount; index++) {
            Siting siting = readSiting(buffer);
            candidates.add(new SitingCandidate(siting, buffer.readVarInt()));
        }
        int current = buffer.readVarInt();
        Cuboid bounds = readCuboid(buffer);
        int cellCount = buffer.readVarInt();
        Map<LatticeKey, CellMark> cells = new TreeMap<>();
        CellMark[] marks = CellMark.values();
        for (int index = 0; index < cellCount; index++) {
            cells.put(new LatticeKey(buffer.readLong()), marks[buffer.readUnsignedByte()]);
        }
        List<Marker> overlayMarkers = readMarkers(buffer, bounds);
        int ghostCount = buffer.readVarInt();
        List<GhostCell> ghosts = new ArrayList<>(ghostCount);
        for (int index = 0; index < ghostCount; index++) {
            ghosts.add(readGhost(buffer));
        }
        List<Marker> markers = readMarkers(buffer, bounds);
        return new PreviewSyncPayload(
            blueprintId,
            candidates,
            current,
            new OverlayModel(bounds, cells, overlayMarkers),
            ghosts,
            markers
        );
    }

    private static void encodeControl(RegistryFriendlyByteBuf buffer, PreviewControlPayload payload) {
        buffer.writeByte(payload.control().ordinal());
        buffer.writeBoolean(payload.target() != null);
        if (payload.target() != null) {
            writePoint(buffer, payload.target());
        }
    }

    private static PreviewControlPayload decodeControl(RegistryFriendlyByteBuf buffer) {
        PreviewControlPayload.Control[] controls = PreviewControlPayload.Control.values();
        PreviewControlPayload.Control control = controls[buffer.readUnsignedByte()];
        LatticePoint target = buffer.readBoolean() ? readPoint(buffer) : null;
        return new PreviewControlPayload(control, target);
    }

    private static void encodeCatalog(RegistryFriendlyByteBuf buffer, PreviewCatalogPayload payload) {
        buffer.writeVarInt(payload.entries().size());
        for (PreviewCatalogPayload.Entry entry : payload.entries()) {
            writeId(buffer, entry.blueprintId());
            buffer.writeVarInt(entry.cells().size());
            for (GhostCell ghost : entry.cells()) {
                writeGhost(buffer, ghost);
            }
            buffer.writeVarInt(entry.materials().size());
            for (PreviewCatalogPayload.MaterialLine line : entry.materials()) {
                writeId(buffer, line.item());
                buffer.writeVarInt(line.count());
            }
        }
    }

    private static PreviewCatalogPayload decodeCatalog(RegistryFriendlyByteBuf buffer) {
        int entryCount = buffer.readVarInt();
        List<PreviewCatalogPayload.Entry> entries = new ArrayList<>(entryCount);
        for (int entry = 0; entry < entryCount; entry++) {
            NamespacedId blueprintId = readId(buffer);
            int cellCount = buffer.readVarInt();
            List<GhostCell> cells = new ArrayList<>(cellCount);
            for (int cell = 0; cell < cellCount; cell++) {
                cells.add(readGhost(buffer));
            }
            int materialCount = buffer.readVarInt();
            List<PreviewCatalogPayload.MaterialLine> materials = new ArrayList<>(materialCount);
            for (int material = 0; material < materialCount; material++) {
                materials.add(new PreviewCatalogPayload.MaterialLine(readId(buffer), buffer.readVarInt()));
            }
            entries.add(new PreviewCatalogPayload.Entry(blueprintId, cells, materials));
        }
        return new PreviewCatalogPayload(entries);
    }

    private static void encodeOpen(RegistryFriendlyByteBuf buffer, PreviewOpenPayload payload) {
        writeId(buffer, payload.blueprintId());
    }

    private static PreviewOpenPayload decodeOpen(RegistryFriendlyByteBuf buffer) {
        return new PreviewOpenPayload(readId(buffer));
    }

    private static void writeGhost(RegistryFriendlyByteBuf buffer, GhostCell ghost) {
        writePoint(buffer, ghost.cell());
        writeId(buffer, ghost.block());
        buffer.writeVarInt(ghost.properties().size());
        for (Map.Entry<String, String> property : ghost.properties().entrySet()) {
            buffer.writeUtf(property.getKey());
            buffer.writeUtf(property.getValue());
        }
    }

    private static GhostCell readGhost(RegistryFriendlyByteBuf buffer) {
        LatticePoint cell = readPoint(buffer);
        NamespacedId block = readId(buffer);
        int propertyCount = buffer.readVarInt();
        Map<String, String> properties = new TreeMap<>();
        for (int property = 0; property < propertyCount; property++) {
            properties.put(buffer.readUtf(), buffer.readUtf());
        }
        return new GhostCell(cell, block, properties);
    }

    private static void writeMarkers(RegistryFriendlyByteBuf buffer, List<Marker> markers) {
        buffer.writeVarInt(markers.size());
        for (Marker marker : markers) {
            Locus locus = marker.locus();
            buffer.writeByte(locus.frame().ordinal());
            buffer.writeVarInt(locus.cells().decomposition().size());
            for (Cuboid box : locus.cells().decomposition()) {
                writeCuboid(buffer, box);
            }
            buffer.writeUtf(marker.label());
            buffer.writeByte(marker.kind().ordinal());
        }
    }

    /** A marker whose locus is not world-frame is re-anchored at the overlay bounds on the client. */
    private static List<Marker> readMarkers(RegistryFriendlyByteBuf buffer, Cuboid bounds) {
        int count = buffer.readVarInt();
        List<Marker> markers = new ArrayList<>(count);
        LocusFrame[] frames = LocusFrame.values();
        MarkerKind[] kinds = MarkerKind.values();
        for (int index = 0; index < count; index++) {
            LocusFrame frame = frames[buffer.readUnsignedByte()];
            int boxCount = buffer.readVarInt();
            List<Cuboid> boxes = new ArrayList<>(boxCount);
            for (int box = 0; box < boxCount; box++) {
                boxes.add(readCuboid(buffer));
            }
            String label = buffer.readUtf();
            MarkerKind kind = kinds[buffer.readUnsignedByte()];
            CuboidSet cells = boxes.isEmpty() ? CuboidSet.empty() : CuboidSet.of(boxes);
            Locus locus = frame == LocusFrame.world && !cells.isEmpty()
                    ? Locus.world(cells)
                    : Locus.world(bounds.asSet());
            markers.add(new Marker(locus, label, kind));
        }
        return markers;
    }

    private static void writeId(RegistryFriendlyByteBuf buffer, NamespacedId id) {
        buffer.writeUtf(id.namespace());
        buffer.writeUtf(id.path());
    }

    private static NamespacedId readId(RegistryFriendlyByteBuf buffer) {
        return NamespacedId.of(buffer.readUtf(), buffer.readUtf());
    }

    private static void writePoint(RegistryFriendlyByteBuf buffer, LatticePoint point) {
        buffer.writeVarInt(point.x());
        buffer.writeVarInt(point.y());
        buffer.writeVarInt(point.z());
    }

    private static LatticePoint readPoint(RegistryFriendlyByteBuf buffer) {
        return LatticePoint.of(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    private static void writeCuboid(RegistryFriendlyByteBuf buffer, Cuboid box) {
        writePoint(buffer, box.min());
        writePoint(buffer, box.max());
    }

    private static Cuboid readCuboid(RegistryFriendlyByteBuf buffer) {
        return Cuboid.of(readPoint(buffer), readPoint(buffer));
    }

    private static void writeSiting(RegistryFriendlyByteBuf buffer, Siting siting) {
        writePoint(buffer, siting.origin());
        buffer.writeByte(siting.placement().orientation().ordinal());
        writePoint(buffer, siting.placement().offset());
    }

    private static Siting readSiting(RegistryFriendlyByteBuf buffer) {
        LatticePoint origin = readPoint(buffer);
        Orientation orientation = new Orientation(buffer.readUnsignedByte());
        LatticePoint offset = readPoint(buffer);
        return Siting.of(origin, new Placement(orientation, offset));
    }
}
