package dev.vfyjxf.cumulus.mc.preview;

import dev.vfyjxf.cumulus.StructureCumulus;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewCatalogPayload;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewPresentation;
import dev.vfyjxf.cumulus.mc.preview.net.Stage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The JEI catalog of the merged document source: every blueprint that compiles, projected to the
 * expected states of its identity orientation and counted into its material lines. An entry past a
 * cap is left out and named in the log — never truncated into a wrong scene (design 15 §6).
 */
final class PreviewCatalog {

    /** Per-entry ghost cap; the in-world session uses the same number (design 15 §3). */
    static final int maxCells = 512;
    /** Per-entry material-line cap. */
    static final int maxMaterials = 64;
    /** Whole-catalog cap: one payload must stay well inside the network frame limit. */
    static final int maxTotalCells = 4096;

    private PreviewCatalog() {}

    static PreviewCatalogPayload build(StructureKernel kernel, List<NamespacedId> ids) {
        List<PreviewCatalogPayload.Entry> entries = new ArrayList<>(ids.size());
        int cells = 0;
        for (NamespacedId id : ids) {
            PreviewCatalogPayload.Entry entry = entry(kernel, id);
            if (entry == null) {
                continue;
            }
            if (cells + entry.cells().size() > maxTotalCells) {
                StructureCumulus.logger.warn(
                    "preview catalog: {} left out, the catalog is over the {} cell cap",
                    id.text(),
                    maxTotalCells
                );
                continue;
            }
            entries.add(entry);
            cells += entry.cells().size();
        }
        StructureCumulus.logger.info("preview catalog: {} of {} blueprint(s) previewable", entries.size(), ids.size());
        return new PreviewCatalogPayload(entries);
    }

    private static PreviewCatalogPayload.@Nullable Entry entry(StructureKernel kernel, NamespacedId id) {
        Document document = kernel.document(id);
        if (document == null) {
            return null;
        }
        Blueprint blueprint = kernel.compile(document).blueprint();
        if (blueprint == null) {
            StructureCumulus.logger.warn("preview catalog: {} does not compile and is left out", id.text());
            return null;
        }
        PreviewExpected.Outcome outcome = PreviewExpected.of(blueprint, Orientation.identity);
        if (outcome.filter() != null) {
            PreviewExpected.Filter filter = outcome.filter();
            StructureCumulus.logger.warn(
                "preview catalog: {} cannot express the identity orientation ({}.{} = {}) and is left out",
                id.text(),
                filter.block().text(),
                filter.property(),
                filter.value()
            );
            return null;
        }
        Map<LatticePoint, BlockState> states = outcome.states();
        boolean draws = false;
        for (BlockState state : states.values()) {
            if (!state.isAir()) {
                draws = true;
                break;
            }
        }
        if (!draws) {
            StructureCumulus.logger.warn(
                "preview catalog: {} draws nothing (no expected cell resolves to a drawn block) and is left out",
                id.text()
            );
            return null;
        }
        if (states.size() > maxCells) {
            StructureCumulus.logger.warn(
                "preview catalog: {} declares {} expected cells, over the {} cap, and is left out",
                id.text(),
                states.size(),
                maxCells
            );
            return null;
        }
        StageModel.Table table = StageModel.of(blueprint, states.keySet());
        List<Stage> stages = new ArrayList<>(table.stages().size());
        List<GhostCell> cells = new ArrayList<>(states.size());
        for (int index = 0; index < table.stages().size(); index++) {
            StageModel.Stage stage = table.stages().get(index);
            stages.add(
                new Stage(stage.name(), stage.cells().size(), 0, stage.hint(), stage.description(), stage.bay())
            );
            for (LatticePoint cell : stage.cells()) {
                BlockState value = states.get(cell);
                if (value == null) {
                    continue;
                }
                cells.add(
                    new GhostCell(
                        cell,
                        PreviewExpected.blockId(value.getBlock()),
                        PreviewExpected.properties(value),
                        index
                    )
                );
            }
        }
        List<PreviewCatalogPayload.MaterialLine> materials = materials(states);
        if (materials.size() > maxMaterials) {
            StructureCumulus.logger.warn(
                "preview catalog: {} needs {} distinct materials, over the {} cap, and is left out",
                id.text(),
                materials.size(),
                maxMaterials
            );
            return null;
        }
        // a page satisfies nothing, so its frontier is the first stage whenever it has one (design 16 §5)
        PreviewPresentation presentation = new PreviewPresentation(
            blueprint.description(),
            stages,
            stages.isEmpty() ? -1 : 0,
            List.of(),
            0
        );
        return new PreviewCatalogPayload.Entry(id, cells, materials, presentation, PreviewVariants.summary(blueprint));
    }

    private static List<PreviewCatalogPayload.MaterialLine> materials(Map<LatticePoint, BlockState> states) {
        Map<NamespacedId, Integer> counts = new TreeMap<>();
        for (BlockState state : states.values()) {
            Item item = state.getBlock().asItem();
            if (item == Items.AIR) {
                continue;
            }
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
            counts.merge(NamespacedId.of(key.getNamespace(), key.getPath()), 1, Integer::sum);
        }
        List<PreviewCatalogPayload.MaterialLine> lines = new ArrayList<>(counts.size());
        counts.forEach((item, count) -> lines.add(new PreviewCatalogPayload.MaterialLine(item, count)));
        return List.copyOf(lines);
    }
}
