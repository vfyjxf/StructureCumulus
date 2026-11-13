package dev.vfyjxf.cumulus.mc.preview.net;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.document.DistanceMetric;
import dev.vfyjxf.cumulus.kernel.document.LinkMode;
import dev.vfyjxf.cumulus.kernel.remedy.CellMark;
import dev.vfyjxf.cumulus.kernel.remedy.Marker;
import dev.vfyjxf.cumulus.kernel.remedy.MarkerKind;
import dev.vfyjxf.cumulus.kernel.remedy.OverlayModel;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Heading;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.LocusFrame;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Placement;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The wire codecs of the preview session and of the build feedback; a locus travels as world cells. */
final class PreviewCodecs {

    static final StreamCodec<RegistryFriendlyByteBuf, PreviewSyncPayload> sync = StreamCodec
            .of(PreviewCodecs::encodeSync, PreviewCodecs::decodeSync);

    static final StreamCodec<RegistryFriendlyByteBuf, PreviewControlPayload> control = StreamCodec
            .of(PreviewCodecs::encodeControl, PreviewCodecs::decodeControl);

    static final StreamCodec<RegistryFriendlyByteBuf, PreviewCatalogPayload> catalog = StreamCodec
            .of(PreviewCodecs::encodeCatalog, PreviewCodecs::decodeCatalog);

    static final StreamCodec<RegistryFriendlyByteBuf, PreviewOpenPayload> open = StreamCodec
            .of(PreviewCodecs::encodeOpen, PreviewCodecs::decodeOpen);

    static final StreamCodec<RegistryFriendlyByteBuf, BuildCommitPayload> commit = StreamCodec
            .of(PreviewCodecs::encodeCommit, PreviewCodecs::decodeCommit);

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
        writePresentation(buffer, payload.presentation());
        writeVariants(buffer, payload.variants());
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
        PreviewPresentation presentation = readPresentation(buffer);
        VariantSummary variants = readVariants(buffer);
        return new PreviewSyncPayload(
            blueprintId,
            candidates,
            current,
            new OverlayModel(bounds, cells, overlayMarkers),
            ghosts,
            markers,
            presentation,
            variants
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
            writeEntry(buffer, entry);
        }
        // trailing-optional: an older client stops after the entries and never sees the sets
        if (payload.sets().isEmpty()) {
            return;
        }
        buffer.writeVarInt(payload.sets().size());
        for (PreviewCatalogPayload.SetEntry set : payload.sets()) {
            writeSetEntry(buffer, set);
        }
    }

    private static PreviewCatalogPayload decodeCatalog(RegistryFriendlyByteBuf buffer) {
        int entryCount = buffer.readVarInt();
        List<PreviewCatalogPayload.Entry> entries = new ArrayList<>(entryCount);
        for (int entry = 0; entry < entryCount; entry++) {
            entries.add(readEntry(buffer));
        }
        if (buffer.readableBytes() <= 0) {
            return new PreviewCatalogPayload(entries);
        }
        int setCount = buffer.readVarInt();
        List<PreviewCatalogPayload.SetEntry> sets = new ArrayList<>(setCount);
        for (int set = 0; set < setCount; set++) {
            sets.add(readSetEntry(buffer));
        }
        return new PreviewCatalogPayload(entries, sets);
    }

    private static void writeEntry(RegistryFriendlyByteBuf buffer, PreviewCatalogPayload.Entry entry) {
        writeId(buffer, entry.blueprintId());
        buffer.writeVarInt(entry.cells().size());
        for (GhostCell ghost : entry.cells()) {
            writeGhost(buffer, ghost);
        }
        writeMaterials(buffer, entry.materials());
        writePresentation(buffer, entry.presentation());
        writeVariants(buffer, entry.variants());
    }

    private static PreviewCatalogPayload.Entry readEntry(RegistryFriendlyByteBuf buffer) {
        NamespacedId blueprintId = readId(buffer);
        int cellCount = buffer.readVarInt();
        List<GhostCell> cells = new ArrayList<>(cellCount);
        for (int cell = 0; cell < cellCount; cell++) {
            cells.add(readGhost(buffer));
        }
        List<PreviewCatalogPayload.MaterialLine> materials = readMaterials(buffer);
        return new PreviewCatalogPayload.Entry(
            blueprintId,
            cells,
            materials,
            readPresentation(buffer),
            readVariants(buffer)
        );
    }

    /** A set entry's members carry composed cells, so the client never maps an orientation (§3). */
    private static void writeSetEntry(RegistryFriendlyByteBuf buffer, PreviewCatalogPayload.SetEntry set) {
        writeId(buffer, set.setId());
        buffer.writeVarInt(set.members().size());
        for (PreviewCatalogPayload.Member member : set.members()) {
            writeId(buffer, member.blueprintId());
            buffer.writeUtf(member.label());
            buffer.writeBoolean(member.required());
            buffer.writeVarInt(member.cells().size());
            for (GhostCell ghost : member.cells()) {
                writeGhost(buffer, ghost);
            }
            buffer.writeVarInt(member.presence().size());
            for (Cuboid box : member.presence()) {
                writeCuboid(buffer, box);
            }
            buffer.writeVarInt(member.links().size());
            for (PreviewCatalogPayload.Link link : member.links()) {
                writeLink(buffer, link);
            }
        }
        writeMaterials(buffer, set.materials());
    }

    private static PreviewCatalogPayload.SetEntry readSetEntry(RegistryFriendlyByteBuf buffer) {
        NamespacedId setId = readId(buffer);
        int memberCount = buffer.readVarInt();
        List<PreviewCatalogPayload.Member> members = new ArrayList<>(memberCount);
        for (int index = 0; index < memberCount; index++) {
            NamespacedId blueprintId = readId(buffer);
            String label = buffer.readUtf();
            boolean required = buffer.readBoolean();
            int cellCount = buffer.readVarInt();
            List<GhostCell> cells = new ArrayList<>(cellCount);
            for (int cell = 0; cell < cellCount; cell++) {
                cells.add(readGhost(buffer));
            }
            int presenceCount = buffer.readVarInt();
            List<Cuboid> presence = new ArrayList<>(presenceCount);
            for (int box = 0; box < presenceCount; box++) {
                presence.add(readCuboid(buffer));
            }
            int linkCount = buffer.readVarInt();
            List<PreviewCatalogPayload.Link> links = new ArrayList<>(linkCount);
            for (int link = 0; link < linkCount; link++) {
                links.add(readLink(buffer));
            }
            members.add(new PreviewCatalogPayload.Member(blueprintId, label, required, cells, presence, links));
        }
        return new PreviewCatalogPayload.SetEntry(setId, members, readMaterials(buffer));
    }

    private static void writeMaterials(RegistryFriendlyByteBuf buffer, List<PreviewCatalogPayload.MaterialLine> lines) {
        buffer.writeVarInt(lines.size());
        for (PreviewCatalogPayload.MaterialLine line : lines) {
            writeId(buffer, line.item());
            buffer.writeVarInt(line.count());
        }
    }

    private static List<PreviewCatalogPayload.MaterialLine> readMaterials(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<PreviewCatalogPayload.MaterialLine> lines = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            lines.add(new PreviewCatalogPayload.MaterialLine(readId(buffer), buffer.readVarInt()));
        }
        return lines;
    }

    private static void writeLink(RegistryFriendlyByteBuf buffer, PreviewCatalogPayload.Link link) {
        buffer.writeByte(link.mode().ordinal());
        buffer.writeBoolean(link.parentCell() != null);
        if (link.parentCell() != null) {
            writePoint(buffer, link.parentCell());
        }
        buffer.writeBoolean(link.satelliteCell() != null);
        if (link.satelliteCell() != null) {
            writePoint(buffer, link.satelliteCell());
        }
        buffer.writeVarInt(link.maxDistance());
        buffer.writeByte(link.metric().ordinal());
    }

    private static PreviewCatalogPayload.Link readLink(RegistryFriendlyByteBuf buffer) {
        LinkMode[] modes = LinkMode.values();
        LinkMode mode = modes[buffer.readUnsignedByte()];
        LatticePoint parent = buffer.readBoolean() ? readPoint(buffer) : null;
        LatticePoint satellite = buffer.readBoolean() ? readPoint(buffer) : null;
        int maxDistance = buffer.readVarInt();
        DistanceMetric[] metrics = DistanceMetric.values();
        return new PreviewCatalogPayload.Link(mode, parent, satellite, maxDistance, metrics[buffer.readUnsignedByte()]);
    }

    /**
     * The heading is a trailing optional byte: an open from a client that predates it ends after the
     * id and reads as absent, and a server that predates it never reads the byte (design 17 §3.2).
     */
    private static void encodeOpen(RegistryFriendlyByteBuf buffer, PreviewOpenPayload payload) {
        writeId(buffer, payload.blueprintId());
        Heading facing = horizontal(payload.facing());
        if (facing != null) {
            buffer.writeByte(facing.ordinal());
        }
    }

    private static PreviewOpenPayload decodeOpen(RegistryFriendlyByteBuf buffer) {
        NamespacedId blueprintId = readId(buffer);
        if (buffer.readableBytes() <= 0) {
            return new PreviewOpenPayload(blueprintId);
        }
        Heading[] headings = Heading.values();
        int ordinal = buffer.readUnsignedByte();
        Heading read = ordinal < headings.length ? headings[ordinal] : null;
        return new PreviewOpenPayload(blueprintId, horizontal(read));
    }

    /** The four horizons travel; {@code up}/{@code down} read as absent (design 17 §3.2). */
    private static @Nullable Heading horizontal(@Nullable Heading heading) {
        if (heading == null) {
            return null;
        }
        return switch (heading) {
            case up, down -> null;
            default -> heading;
        };
    }

    private static void encodeCommit(RegistryFriendlyByteBuf buffer, BuildCommitPayload payload) {
        buffer.writeByte(payload.status().ordinal());
        buffer.writeBoolean(payload.formed());
        buffer.writeVarInt(payload.truncated());
        buffer.writeVarInt(payload.cells().size());
        for (BuildCommitPayload.CommitCell cell : payload.cells()) {
            writePoint(buffer, cell.cell());
            writeId(buffer, cell.block());
            buffer.writeVarInt(cell.properties().size());
            for (Map.Entry<String, String> property : cell.properties().entrySet()) {
                buffer.writeUtf(property.getKey());
                buffer.writeUtf(property.getValue());
            }
        }
    }

    private static BuildCommitPayload decodeCommit(RegistryFriendlyByteBuf buffer) {
        BuildCommitPayload.Status[] statuses = BuildCommitPayload.Status.values();
        BuildCommitPayload.Status status = statuses[buffer.readUnsignedByte()];
        boolean formed = buffer.readBoolean();
        int truncated = buffer.readVarInt();
        int cellCount = buffer.readVarInt();
        List<BuildCommitPayload.CommitCell> cells = new ArrayList<>(cellCount);
        for (int index = 0; index < cellCount; index++) {
            LatticePoint cell = readPoint(buffer);
            NamespacedId block = readId(buffer);
            int propertyCount = buffer.readVarInt();
            Map<String, String> properties = new TreeMap<>();
            for (int property = 0; property < propertyCount; property++) {
                properties.put(buffer.readUtf(), buffer.readUtf());
            }
            cells.add(new BuildCommitPayload.CommitCell(cell, block, properties));
        }
        return new BuildCommitPayload(cells, status, formed, truncated);
    }

    private static void writeGhost(RegistryFriendlyByteBuf buffer, GhostCell ghost) {
        writePoint(buffer, ghost.cell());
        writeId(buffer, ghost.block());
        buffer.writeVarInt(ghost.properties().size());
        for (Map.Entry<String, String> property : ghost.properties().entrySet()) {
            buffer.writeUtf(property.getKey());
            buffer.writeUtf(property.getValue());
        }
        buffer.writeVarInt(ghost.stageIndex());
    }

    private static GhostCell readGhost(RegistryFriendlyByteBuf buffer) {
        LatticePoint cell = readPoint(buffer);
        NamespacedId block = readId(buffer);
        int propertyCount = buffer.readVarInt();
        Map<String, String> properties = new TreeMap<>();
        for (int property = 0; property < propertyCount; property++) {
            properties.put(buffer.readUtf(), buffer.readUtf());
        }
        return new GhostCell(cell, block, properties, buffer.readVarInt());
    }

    private static void writePresentation(RegistryFriendlyByteBuf buffer, PreviewPresentation presentation) {
        writeOptional(buffer, presentation.description());
        buffer.writeVarInt(presentation.stages().size());
        for (Stage stage : presentation.stages()) {
            buffer.writeUtf(stage.name());
            buffer.writeVarInt(stage.expectedCells());
            buffer.writeVarInt(stage.satisfiedCells());
            writeOptional(buffer, stage.hint());
            writeOptional(buffer, stage.description());
            writeId(buffer, stage.bay());
        }
        buffer.writeVarInt(presentation.currentStage());
        buffer.writeVarInt(presentation.materials().size());
        for (MaterialStatus material : presentation.materials()) {
            writeId(buffer, material.item());
            buffer.writeVarInt(material.need());
            buffer.writeVarInt(material.have());
        }
        buffer.writeVarInt(presentation.moreMaterials());
    }

    private static PreviewPresentation readPresentation(RegistryFriendlyByteBuf buffer) {
        String description = readOptional(buffer);
        int stageCount = buffer.readVarInt();
        List<Stage> stages = new ArrayList<>(stageCount);
        for (int index = 0; index < stageCount; index++) {
            String name = buffer.readUtf();
            int expectedCells = buffer.readVarInt();
            int satisfiedCells = buffer.readVarInt();
            String hint = readOptional(buffer);
            String stageDescription = readOptional(buffer);
            NamespacedId bay = readId(buffer);
            stages.add(new Stage(name, expectedCells, satisfiedCells, hint, stageDescription, bay));
        }
        int currentStage = buffer.readVarInt();
        int materialCount = buffer.readVarInt();
        List<MaterialStatus> materials = new ArrayList<>(materialCount);
        for (int index = 0; index < materialCount; index++) {
            materials.add(new MaterialStatus(readId(buffer), buffer.readVarInt(), buffer.readVarInt()));
        }
        int moreMaterials = buffer.readVarInt();
        return new PreviewPresentation(description, stages, currentStage, materials, moreMaterials);
    }

    /**
     * The variant summary: the group id and its members in order. The declaration is capped at compile
     * time (16 members, 128-character labels), so a summary never needs a wire cap of its own.
     */
    private static void writeVariants(RegistryFriendlyByteBuf buffer, @Nullable VariantSummary variants) {
        buffer.writeBoolean(variants != null);
        if (variants == null) {
            return;
        }
        writeId(buffer, variants.group());
        buffer.writeVarInt(variants.members().size());
        for (VariantSummary.Member member : variants.members()) {
            writeId(buffer, member.id());
            writeOptional(buffer, member.label());
        }
    }

    private static @Nullable VariantSummary readVariants(RegistryFriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return null;
        }
        NamespacedId group = readId(buffer);
        int count = buffer.readVarInt();
        List<VariantSummary.Member> members = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            members.add(new VariantSummary.Member(readId(buffer), readOptional(buffer)));
        }
        return new VariantSummary(group, members);
    }

    private static void writeOptional(RegistryFriendlyByteBuf buffer, @Nullable String text) {
        buffer.writeBoolean(text != null);
        if (text != null) {
            buffer.writeUtf(text);
        }
    }

    private static @Nullable String readOptional(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readUtf() : null;
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
