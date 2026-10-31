package dev.vfyjxf.cumulus.uitest;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.vfyjxf.cloudlib.api.ui.inworld.render.WorldOverlay;
import dev.vfyjxf.cloudlib.api.ui.inworld.render.WorldUiPanel;
import dev.vfyjxf.cloudlib.api.ui.inworld.render.WorldUiRenderer;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.mc.visual.VisualClaim;
import dev.vfyjxf.cumulus.mc.visual.VisualInstance;
import dev.vfyjxf.cumulus.mc.visual.VisualSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The dev-only world highlight layer: bounds boxes, claim cells, conflict/draft/transient colors and
 * the crosshair selection, registered on CloudLib's in-world UI pass every frame.
 */
public final class VisualHighlights {

    /** How long a moved/invalidated color survives, in client ticks. */
    private static final long transientTicks = 120;

    /** Bounds cap for large snapshots: the selected boxes win over the oldest instance boxes. */
    private static final int maxBoundsBoxes = 48;
    /** Claim-cell cap keeps the per-frame quad count bounded for large sets. */
    private static final int maxClaimCells = 128;

    private static final Map<InstanceId, Tracked> tracked = new LinkedHashMap<>();
    private static final Map<InstanceId, Ghost> ghosts = new LinkedHashMap<>();
    private static final Map<InstanceId, Long> movedUntil = new HashMap<>();

    private static volatile boolean registered;

    private VisualHighlights() {}

    /** Registers the level-stage listener and forces CloudLib's renderer to register its own. */
    static void register() {
        if (registered) {
            return;
        }
        registered = true;
        WorldUiRenderer.get();
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, RenderLevelStageEvent.class, VisualHighlights::onLevelStage);
    }

    /** Called on the client thread whenever a fresh snapshot arrives; keeps the transient colors. */
    static void onSnapshot(@Nullable VisualSnapshot previous, VisualSnapshot latest) {
        long now = now();
        String expected = VisualClientState.consumeExpectedLifecycle();
        synchronized (VisualHighlights.class) {
            if (previous != null) {
                for (VisualInstance before : previous.instances()) {
                    VisualInstance after = find(latest, before.id());
                    if (after == null) {
                        if (!"unloaded".equals(expected)) {
                            ghosts.put(before.id(), new Ghost(before.bounds(), now + transientTicks));
                            VisualClientState.noteEvent("invalidated");
                        }
                    } else if (!after.siting().equals(before.siting()) || !after.bounds().equals(before.bounds())) {
                        movedUntil.put(before.id(), now + transientTicks);
                        VisualClientState.noteEvent("moved");
                    }
                }
            }
            prune(now);
            tracked.clear();
            for (VisualInstance instance : latest.instances()) {
                tracked.put(instance.id(), new Tracked(instance.siting(), instance.bounds()));
            }
            while (ghosts.size() > 16) {
                InstanceId first = ghosts.keySet().iterator().next();
                ghosts.remove(first);
            }
        }
    }

    /** Drops expired transients; called once per client tick. */
    static void tick() {
        synchronized (VisualHighlights.class) {
            prune(now());
        }
    }

    static VisualHighlightSummary summary() {
        Model model = model();
        int boundsCuboids = 0;
        for (BoxHighlight box : model.boxes()) {
            boundsCuboids += boxesOf(box.bounds());
        }
        return new VisualHighlightSummary(
            model.boxes().size(),
            boundsCuboids,
            model.selectedBoxes(),
            model.cells().size(),
            model.conflictCells(),
            model.conflictInstances(),
            model.draftCells(),
            model.movedInstances(),
            model.invalidatedInstances(),
            model.links().size(),
            VisualGroupState.distinctGroupColors(),
            model.selected()
        );
    }

    static List<String> legend() {
        return List.of("图例: 仅边框 青=实例 白=选中 红=冲突 琥珀=草稿 蓝=移动 灰=失效 天蓝=结构集链接 组色=层级链接");
    }

    static List<Integer> colors() {
        Model model = model();
        Set<Integer> colors = new LinkedHashSet<>();
        for (BoxHighlight box : model.boxes()) {
            colors.add(box.color());
        }
        for (CellHighlight cell : model.cells()) {
            colors.add(cell.color());
        }
        for (LinkHighlight link : model.links()) {
            colors.add(link.color());
        }
        return List.copyOf(colors);
    }

    private static void onLevelStage(RenderLevelStageEvent event) {
        if (!VisualClientState.highlightsEnabled() || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        Model model = model();
        if (model.boxes().isEmpty() && model.cells().isEmpty() && model.links().isEmpty() && model.ghosts().isEmpty()) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        List<WorldOverlay> overlays = WorldUiRenderer.get().overlays();
        for (BoxHighlight box : model.boxes()) {
            overlays.add(new BoxOverlay(box.bounds(), renderColor(box.color()), sortDistance(box.bounds(), camera)));
        }
        for (Ghost ghost : model.ghosts()) {
            overlays.add(
                new BoxOverlay(
                    ghost.bounds(),
                    renderColor(roleColor(Role.invalidated)),
                    sortDistance(ghost.bounds(), camera)
                )
            );
        }
        for (CellHighlight cell : model.cells()) {
            overlays.add(new CellOverlay(cell.cell(), cell.color(), cellDistance(cell.cell(), camera)));
        }
        for (LinkHighlight link : model.links()) {
            overlays.add(new LinkOverlay(link, camera));
        }
    }

    private static Model model() {
        VisualSnapshot snapshot = VisualClientState.snapshot();
        if (snapshot == null) {
            return Model.empty();
        }
        InstanceId selected = VisualClientState.selectedInstanceId();
        Map<LatticeKey, List<VisualClaim>> byCell = new LinkedHashMap<>();
        for (VisualClaim claim : snapshot.claims()) {
            byCell.computeIfAbsent(claim.cell(), ignored -> new ArrayList<>()).add(claim);
        }
        Set<SitingKey> instanceKeys = new LinkedHashSet<>();
        for (VisualInstance instance : snapshot.instances()) {
            instanceKeys.add(new SitingKey(instance.owner().id().text(), instance.siting()));
        }
        Set<LatticeKey> conflictCells = new LinkedHashSet<>();
        for (Map.Entry<LatticeKey, List<VisualClaim>> entry : byCell.entrySet()) {
            List<VisualClaim> rows = entry.getValue();
            if (hasRowConflict(rows)) {
                conflictCells.add(entry.getKey());
                continue;
            }
            LatticePoint cell = entry.getKey().point();
            for (VisualClaim row : rows) {
                if (row.sharing() != Sharing.exclusive) {
                    continue;
                }
                for (VisualInstance instance : snapshot.instances()) {
                    if (!isSameKey(row, instance) && instance.bounds().contains(cell)) {
                        conflictCells.add(entry.getKey());
                        break;
                    }
                }
            }
        }
        // The snapshot carries formed instances and persisted claims only; it has no draft rows, so
        // claims without an instance are persisted claims (for example after an unload), not drafts.
        Set<LatticeKey> draftCells = new LinkedHashSet<>();
        long now = now();
        List<BoxHighlight> boxes = new ArrayList<>();
        Set<LatticeKey> conflictFromFindings = new LinkedHashSet<>();
        int conflictInstances = 0;
        int selectedBoxes = 0;
        int movedInstances = 0;
        for (VisualInstance instance : snapshot.instances()) {
            Role role;
            if (instance.findings().stream().anyMatch(code -> code.equals(conflictCode()))) {
                conflictInstances++;
                role = Role.conflict;
                conflictFromFindings.addAll(claimCellsOf(snapshot, instance));
            } else if (isMoved(instance.id(), now)) {
                movedInstances++;
                role = Role.moved;
            } else if (instance.id().equals(selected)) {
                role = Role.selected;
            } else {
                role = Role.instance;
            }
            VisualPart part = VisualPartState.part(instance.id());
            int groupColor = VisualGroupState.colorOf(instance.id());
            int color = role != Role.instance
                    ? roleColor(role)
                    : part != null ? part.color() : groupColor != -1 ? groupColor : roleColor(role);
            boxes.add(new BoxHighlight(instance.bounds(), role, color));
        }
        List<CellHighlight> cells = new ArrayList<>();
        for (Map.Entry<LatticeKey, List<VisualClaim>> entry : byCell.entrySet()) {
            LatticeKey cell = entry.getKey();
            Role role;
            if (conflictCells.contains(cell) || conflictFromFindings.contains(cell)) {
                role = Role.claimConflict;
                conflictCells.add(cell);
            } else if (draftCells.contains(cell)) {
                role = Role.claimDraft;
            } else if (entry.getValue().stream().anyMatch(row -> row.sharing() == Sharing.exclusive)) {
                role = Role.claimExclusive;
            } else {
                role = Role.claimShared;
            }
            int partColor = partColorForClaims(snapshot, entry.getValue());
            int groupColor = groupColorForClaims(snapshot, entry.getValue());
            int color = (role == Role.claimShared || role == Role.claimExclusive) && partColor != -1
                    ? partColor
                    : (role == Role.claimShared || role == Role.claimExclusive) && groupColor != -1
                            ? groupColor
                            : roleColor(role);
            cells.add(new CellHighlight(cell, role, color));
        }
        boxes = capBoxes(boxes);
        cells = capCells(cells);
        selectedBoxes = 0;
        for (BoxHighlight box : boxes) {
            if (box.role() == Role.selected) {
                selectedBoxes += boxesOf(box.bounds());
            }
        }
        List<LinkHighlight> links = new ArrayList<>();
        VisualSetSummary setSummary = VisualClientState.setSummary();
        if (setSummary != null) {
            for (VisualSetLink link : setSummary.links()) {
                links.add(new LinkHighlight(link.from(), link.to(), 0xFF60D0FF));
            }
        }
        for (VisualGroupLink link : VisualGroupState.coloredHierarchyLinks()) {
            links.add(new LinkHighlight(link.from(), link.to(), link.color()));
        }
        List<Ghost> ghostList = new ArrayList<>();
        synchronized (VisualHighlights.class) {
            for (Ghost ghost : ghosts.values()) {
                if (ghost.expiresAtTick() > now) {
                    ghostList.add(ghost);
                }
            }
        }
        return new Model(
            List.copyOf(boxes),
            List.copyOf(cells),
            List.copyOf(links),
            selectedBoxes,
            conflictCells.size(),
            conflictInstances,
            draftCells.size(),
            movedInstances,
            ghostList.size(),
            selected,
            List.copyOf(ghostList)
        );
    }

    private static Set<LatticeKey> claimCellsOf(VisualSnapshot snapshot, VisualInstance instance) {
        Set<LatticeKey> cells = new LinkedHashSet<>();
        for (VisualClaim claim : snapshot.claims()) {
            if (isSameKey(claim, instance)) {
                cells.add(claim.cell());
            }
        }
        return cells;
    }

    private static int partColorForClaims(VisualSnapshot snapshot, List<VisualClaim> rows) {
        for (VisualClaim row : rows) {
            for (VisualInstance instance : snapshot.instances()) {
                if (instance.owner().equals(row.owner()) && instance.siting().equals(row.siting())) {
                    VisualPart part = VisualPartState.part(instance.id());
                    if (part != null) {
                        return part.color();
                    }
                }
            }
        }
        return -1;
    }

    private static int groupColorForClaims(VisualSnapshot snapshot, List<VisualClaim> rows) {
        for (VisualClaim row : rows) {
            for (VisualInstance instance : snapshot.instances()) {
                if (instance.owner().equals(row.owner()) && instance.siting().equals(row.siting())) {
                    int color = VisualGroupState.colorOf(instance.id());
                    if (color != -1) {
                        return color;
                    }
                }
            }
        }
        return -1;
    }

    private static boolean hasRowConflict(List<VisualClaim> rows) {
        for (int index = 0; index < rows.size(); index++) {
            for (int other = index + 1; other < rows.size(); other++) {
                VisualClaim first = rows.get(index);
                VisualClaim second = rows.get(other);
                boolean sameKey = first.owner().equals(second.owner()) && first.siting().equals(second.siting());
                if (!sameKey && (first.sharing() == Sharing.exclusive || second.sharing() == Sharing.exclusive)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isSameKey(VisualClaim claim, VisualInstance instance) {
        return claim.owner().equals(instance.owner()) && claim.siting().equals(instance.siting());
    }

    private static boolean isMoved(InstanceId id, long now) {
        synchronized (VisualHighlights.class) {
            Long until = movedUntil.get(id);
            return until != null && until > now;
        }
    }

    private static void prune(long now) {
        ghosts.values().removeIf(ghost -> ghost.expiresAtTick() <= now);
        movedUntil.values().removeIf(until -> until <= now);
    }

    private static long now() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0L : minecraft.level.getGameTime();
    }

    private static @Nullable VisualInstance find(VisualSnapshot snapshot, InstanceId id) {
        for (VisualInstance instance : snapshot.instances()) {
            if (instance.id().equals(id)) {
                return instance;
            }
        }
        return null;
    }

    private static int boxesOf(CuboidSet bounds) {
        return bounds.boxes().size();
    }

    private static double sortDistance(CuboidSet bounds, Vec3 camera) {
        double distance = 0.0D;
        for (Cuboid box : bounds.boxes()) {
            distance = Math.max(distance, camera.distanceToSqr(center(box)));
        }
        return Math.sqrt(distance);
    }

    private static double cellDistance(LatticeKey cell, Vec3 camera) {
        LatticePoint point = cell.point();
        Vec3 center = new Vec3(point.x() + 0.5D, point.y() + 0.5D, point.z() + 0.5D);
        return Math.sqrt(camera.distanceToSqr(center));
    }

    private static Vec3 center(Cuboid box) {
        return new Vec3(
            (box.min().x() + box.max().x()) / 2.0D,
            (box.min().y() + box.max().y()) / 2.0D,
            (box.min().z() + box.max().z()) / 2.0D
        );
    }

    private static String conflictCode() {
        return Codes.claimConflict.code().text();
    }

    private static int roleColor(Role role) {
        return switch (role) {
            case instance -> 0xC040C0FF;
            case selected -> 0xFFFFFFFF;
            case conflict -> 0xFFFF4040;
            case moved -> 0xFF40A0FF;
            case invalidated -> 0x80808080;
            case claimShared -> 0x5530C060;
            case claimExclusive -> 0x66FFC040;
            case claimConflict -> 0x99FF3030;
            case claimDraft -> 0x88FFA030;
        };
    }

    private enum Role {
        instance, selected, conflict, moved, invalidated, claimShared, claimExclusive, claimConflict, claimDraft
    }

    private record Tracked(Siting siting, CuboidSet bounds) {}

    private record SitingKey(String owner, Siting siting) {}

    private record Ghost(CuboidSet bounds, long expiresAtTick) {}

    private record BoxHighlight(CuboidSet bounds, Role role, int color) {}

    private record CellHighlight(LatticeKey cell, Role role, int color) {}

    private record LinkHighlight(LatticePoint from, LatticePoint to, int color) {}

    private static List<BoxHighlight> capBoxes(List<BoxHighlight> boxes) {
        if (boxes.size() <= maxBoundsBoxes) {
            return boxes;
        }
        List<BoxHighlight> limited = new ArrayList<>(maxBoundsBoxes);
        for (BoxHighlight box : boxes) {
            if (box.role() == Role.selected && limited.size() < maxBoundsBoxes) {
                limited.add(box);
            }
        }
        for (BoxHighlight box : boxes) {
            if (limited.size() >= maxBoundsBoxes) {
                break;
            }
            if (box.role() != Role.selected) {
                limited.add(box);
            }
        }
        return limited;
    }

    private static List<CellHighlight> capCells(List<CellHighlight> cells) {
        return cells.size() <= maxClaimCells ? cells : List.copyOf(cells.subList(0, maxClaimCells));
    }

    private record Model(
        List<BoxHighlight> boxes,
        List<CellHighlight> cells,
        List<LinkHighlight> links,
        int selectedBoxes,
        int conflictCells,
        int conflictInstances,
        int draftCells,
        int movedInstances,
        int invalidatedInstances,
        @Nullable InstanceId selected,
        List<Ghost> ghosts
    ) {

        static Model empty() {
            return new Model(List.of(), List.of(), List.of(), 0, 0, 0, 0, 0, 0, null, List.of());
        }
    }

    private static final class LinkOverlay implements WorldOverlay {

        private final LinkHighlight link;
        private final double distance;

        private LinkOverlay(LinkHighlight link, Vec3 camera) {
            this.link = link;
            this.distance = Math.sqrt(camera.distanceToSqr(linkCenter(link)));
        }

        @Override
        public double sortDistance(Vec3 cameraPos) {
            return distance;
        }

        @Override
        public void draw(WorldUiPanel.Frame frame) {
            BufferBuilder buffer = Tesselator.getInstance()
                    .begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
            Vec3 from = linkCenter(link);
            Vec3 to = linkTarget(link);
            edge(
                buffer,
                frame.worldToView(),
                (float) from.x,
                (float) from.y,
                (float) from.z,
                (float) to.x,
                (float) to.y,
                (float) to.z,
                renderColor(link.color())
            );
            drawLines(buffer);
        }
    }

    private static Vec3 linkCenter(LinkHighlight link) {
        LatticePoint point = link.from();
        return new Vec3(point.x() + 0.5D, point.y() + 0.5D, point.z() + 0.5D);
    }

    private static Vec3 linkTarget(LinkHighlight link) {
        LatticePoint point = link.to();
        return new Vec3(point.x() + 0.5D, point.y() + 0.5D, point.z() + 0.5D);
    }

    private static final class BoxOverlay implements WorldOverlay {

        private final CuboidSet bounds;
        private final int color;
        private final double distance;

        private BoxOverlay(CuboidSet bounds, int color, double distance) {
            this.bounds = bounds;
            this.color = color;
            this.distance = distance;
        }

        @Override
        public double sortDistance(Vec3 cameraPos) {
            return distance;
        }

        @Override
        public void draw(WorldUiPanel.Frame frame) {
            BufferBuilder buffer = Tesselator.getInstance()
                    .begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
            for (Cuboid box : bounds.boxes()) {
                emitBox(buffer, frame.worldToView(), box, color);
            }
            drawLines(buffer);
        }
    }

    private static final class CellOverlay implements WorldOverlay {

        private final LatticeKey cell;
        private final int color;
        private final double distance;

        private CellOverlay(LatticeKey cell, int color, double distance) {
            this.cell = cell;
            this.color = color;
            this.distance = distance;
        }

        @Override
        public double sortDistance(Vec3 cameraPos) {
            return distance;
        }

        @Override
        public void draw(WorldUiPanel.Frame frame) {
            BufferBuilder buffer = Tesselator.getInstance()
                    .begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
            emitBox(buffer, frame.worldToView(), Cuboid.of(cell.point()), renderColor(color));
            drawLines(buffer);
        }
    }

    private static void emitBox(BufferBuilder buffer, Matrix4f matrix, Cuboid box, int argb) {
        float x0 = box.min().x() - 0.002F;
        float y0 = box.min().y() - 0.002F;
        float z0 = box.min().z() - 0.002F;
        float x1 = box.max().x() + 0.002F;
        float y1 = box.max().y() + 0.002F;
        float z1 = box.max().z() + 0.002F;
        edge(buffer, matrix, x0, y0, z0, x1, y0, z0, argb);
        edge(buffer, matrix, x0, y0, z1, x1, y0, z1, argb);
        edge(buffer, matrix, x0, y1, z0, x1, y1, z0, argb);
        edge(buffer, matrix, x0, y1, z1, x1, y1, z1, argb);
        edge(buffer, matrix, x0, y0, z0, x0, y1, z0, argb);
        edge(buffer, matrix, x1, y0, z0, x1, y1, z0, argb);
        edge(buffer, matrix, x0, y0, z1, x0, y1, z1, argb);
        edge(buffer, matrix, x1, y0, z1, x1, y1, z1, argb);
        edge(buffer, matrix, x0, y0, z0, x0, y0, z1, argb);
        edge(buffer, matrix, x1, y0, z0, x1, y0, z1, argb);
        edge(buffer, matrix, x0, y1, z0, x0, y1, z1, argb);
        edge(buffer, matrix, x1, y1, z0, x1, y1, z1, argb);
    }

    private static void edge(
        BufferBuilder buffer,
        Matrix4f matrix,
        float x0,
        float y0,
        float z0,
        float x1,
        float y1,
        float z1,
        int argb
    ) {
        buffer.addVertex(matrix, x0, y0, z0).setColor(argb);
        buffer.addVertex(matrix, x1, y1, z1).setColor(argb);
    }

    private static int renderColor(int argb) {
        int alpha = Math.min(0xB0, Math.max(0x50, (argb >>> 24) & 0xFF));
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;
        red = red * 3 / 4;
        green = green * 3 / 4;
        blue = blue * 3 / 4;
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static void drawLines(BufferBuilder buffer) {
        MeshData mesh = buffer.build();
        if (mesh == null) {
            return;
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        BufferUploader.drawWithShader(mesh);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
    }

}
