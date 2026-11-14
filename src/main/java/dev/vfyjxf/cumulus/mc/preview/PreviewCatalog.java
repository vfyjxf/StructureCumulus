package dev.vfyjxf.cumulus.mc.preview;

import dev.vfyjxf.cumulus.StructureCumulus;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.document.CompiledSatellite;
import dev.vfyjxf.cumulus.kernel.document.CompiledStructureSet;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.document.LinkMode;
import dev.vfyjxf.cumulus.kernel.document.LinkSpec;
import dev.vfyjxf.cumulus.kernel.document.SatelliteSpec;
import dev.vfyjxf.cumulus.kernel.document.StructureSetCompileResult;
import dev.vfyjxf.cumulus.kernel.document.StructureSetDocument;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.Severity;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.mc.OrientationMapping;
import dev.vfyjxf.cumulus.mc.StructureSet;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The JEI catalog of the merged document source: every blueprint that compiles, projected to the
 * expected states of its identity orientation and counted into its material lines, plus one composed
 * entry per structure set (design 18 §3). An entry past a cap is left out and named in the log — never
 * truncated into a wrong scene (design 15 §6, design 18 §3).
 */
final class PreviewCatalog {

    /** Per-entry ghost cap; the in-world session uses the same number (design 15 §3). */
    static final int maxCells = 512;
    /** A set entry's composite cap: a campus is bigger than a machine (design 18 §3). */
    static final int maxSetCells = 1024;
    /** Per-entry material-line cap. */
    static final int maxMaterials = 64;
    /** Whole-catalog cap: one payload must stay well inside the network frame limit. */
    static final int maxTotalCells = 4096;

    private PreviewCatalog() {}

    static PreviewCatalogPayload build(StructureKernel kernel, List<NamespacedId> ids) {
        return build(kernel, ids, List.of());
    }

    static PreviewCatalogPayload build(StructureKernel kernel, List<NamespacedId> ids, List<NamespacedId> setIds) {
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
        List<PreviewCatalogPayload.SetEntry> sets = new ArrayList<>(setIds.size());
        for (NamespacedId id : setIds) {
            PreviewCatalogPayload.SetEntry set = setEntry(kernel, id);
            if (set == null) {
                continue;
            }
            int composed = composedCells(set);
            if (cells + composed > maxTotalCells) {
                StructureCumulus.logger.warn(
                    "preview catalog: the set {} left out, the catalog is over the {} cell cap",
                    id.text(),
                    maxTotalCells
                );
                continue;
            }
            sets.add(set);
            cells += composed;
        }
        StructureCumulus.logger.info(
            "preview catalog: {} of {} blueprint(s) and {} of {} set(s) previewable",
            entries.size(),
            ids.size(),
            sets.size(),
            setIds.size()
        );
        return new PreviewCatalogPayload(entries, sets);
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
        if (!draws(states.values())) {
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
            // a blueprint's stages already are its bays: the bay's name is the group (design 19 §2)
            stages.add(
                new Stage(
                    stage.name(),
                    stage.cells().size(),
                    0,
                    stage.hint(),
                    stage.description(),
                    stage.bay(),
                    stage.bay().text()
                )
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
        List<PreviewCatalogPayload.MaterialLine> materials = materials(states.values());
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

    /**
     * One set, composed by the server into pure geometry: the root at the origin and every satellite at
     * {@code rootSiting.compose(relativeSiting)}, with the kernel's orientation mapping applied to the
     * member's states. A member the composition cannot express leaves the whole entry out (§3).
     */
    private static PreviewCatalogPayload.@Nullable SetEntry setEntry(StructureKernel kernel, NamespacedId id) {
        StructureSetDocument document = kernel.structureSet(id);
        if (document == null) {
            return null;
        }
        StructureSetCompileResult compiled = kernel.compileSet(document);
        CompiledStructureSet set = compiled.set();
        if (set == null) {
            StructureCumulus.logger.warn(
                "preview catalog: the set {} does not compile and is left out: {}",
                id.text(),
                firstFinding(compiled.findings().findings())
            );
            return null;
        }
        Siting rootSiting = Siting.of(LatticePoint.origin, Orientation.identity);
        StructureSet.Placement placement = StructureSet.place(set, rootSiting);
        StructureSet placed = placement.set();
        if (placed == null) {
            StructureCumulus.logger.warn(
                "preview catalog: the set {} cannot be placed and is left out: {}",
                id.text(),
                firstFinding(placement.findings())
            );
            return null;
        }
        List<Finding> findings = StructureSet.validate(placed);
        if (hasErrors(findings)) {
            StructureCumulus.logger.warn(
                "preview catalog: the set {} fails validation and is left out: {}",
                id.text(),
                firstFinding(findings)
            );
            return null;
        }
        List<PreviewCatalogPayload.Member> members = new ArrayList<>(set.satellites().size() + 1);
        List<Stage> stages = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        ComposedMember root = member(id, set.root(), "root", true, rootSiting, rootSiting, null, 0, 0, states);
        if (root == null) {
            return null;
        }
        members.add(root.member());
        stages.addAll(root.stages());
        for (int index = 0; index < set.satellites().size(); index++) {
            CompiledSatellite candidate = set.satellites().get(index);
            SatelliteSpec spec = candidate.spec();
            StructureSet.Satellite satellite = placed.satellite(spec.id());
            if (satellite == null) {
                StructureCumulus.logger.warn(
                    "preview catalog: the set {} lost the satellite {} and is left out",
                    id.text(),
                    spec.id().text()
                );
                return null;
            }
            ComposedMember member = member(
                id,
                candidate.blueprint(),
                spec.id().text(),
                spec.required(),
                rootSiting,
                satellite.siting(),
                spec,
                index + 1,
                stages.size(),
                states
            );
            if (member == null) {
                return null;
            }
            members.add(member.member());
            stages.addAll(member.stages());
        }
        int composed = 0;
        for (PreviewCatalogPayload.Member member : members) {
            composed += member.cells().size();
        }
        if (composed > maxSetCells) {
            StructureCumulus.logger.warn(
                "preview catalog: the set {} composes to {} expected cells, over the {} cap, and is left out",
                id.text(),
                composed,
                maxSetCells
            );
            return null;
        }
        if (!draws(states)) {
            StructureCumulus.logger.warn(
                "preview catalog: the set {} draws nothing (no composed cell resolves to a drawn block) "
                        + "and is left out",
                id.text()
            );
            return null;
        }
        List<PreviewCatalogPayload.MaterialLine> materials = materials(states);
        if (materials.size() > maxMaterials) {
            StructureCumulus.logger.warn(
                "preview catalog: the set {} needs {} distinct materials, over the {} cap, and is left out",
                id.text(),
                materials.size(),
                maxMaterials
            );
            return null;
        }
        return new PreviewCatalogPayload.SetEntry(id, members, materials, stages);
    }

    /** One member's composed facts beside the stages its own StageModel derived (design 19 §2). */
    private record ComposedMember(PreviewCatalogPayload.Member member, List<Stage> stages) {}

    /**
     * One member at its composed siting: its identity states mapped through the placement's orientation,
     * its cells placed in the set's frame, each carrying the flattened index of its own stage. Null
     * leaves the whole set out, never a partial scene.
     */
    private static @Nullable ComposedMember member(
        NamespacedId setId,
        Blueprint blueprint,
        String label,
        boolean required,
        Siting rootSiting,
        Siting siting,
        @Nullable SatelliteSpec satellite,
        int memberIndex,
        int stageBase,
        List<BlockState> states
    ) {
        PreviewExpected.Outcome outcome = PreviewExpected.of(blueprint, Orientation.identity);
        if (outcome.filter() != null) {
            PreviewExpected.Filter filter = outcome.filter();
            StructureCumulus.logger.warn(
                "preview catalog: the set {} left out, the member {} cannot express its identity "
                        + "orientation ({}.{} = {})",
                setId.text(),
                label,
                filter.block().text(),
                filter.property(),
                filter.value()
            );
            return null;
        }
        if (outcome.states().isEmpty()) {
            StructureCumulus.logger.warn(
                "preview catalog: the set {} left out, the member {} declares no expected cells",
                setId.text(),
                label
            );
            return null;
        }
        // the member's own stage model, derived before the orientation mapping: the flattening is the
        // server's and the cells carry the flattened index, so the client composes nothing (design 19 §2)
        StageModel.Table table = StageModel.of(blueprint, outcome.states().keySet());
        NamespacedId bay = PreviewCatalogPayload.SetEntry.memberBay(setId, memberIndex);
        Map<LatticePoint, Integer> stageOf = new HashMap<>();
        List<Stage> memberStages = new ArrayList<>(table.stages().size());
        for (int local = 0; local < table.stages().size(); local++) {
            StageModel.Stage stage = table.stages().get(local);
            for (LatticePoint cell : stage.cells()) {
                stageOf.put(cell, stageBase + local);
            }
            memberStages.add(
                new Stage(
                    label + " \u00B7 " + stage.name(),
                    stage.cells().size(),
                    0,
                    stage.hint(),
                    stage.description(),
                    bay,
                    label
                )
            );
        }
        if (memberStages.isEmpty()) {
            StructureCumulus.logger
                    .warn("preview catalog: the set {} left out, the member {} derives no stage", setId.text(), label);
            return null;
        }
        Orientation orientation = siting.placement().orientation();
        List<GhostCell> cells = new ArrayList<>(outcome.states().size());
        for (Map.Entry<LatticePoint, BlockState> declared : outcome.states().entrySet()) {
            Integer stageIndex = stageOf.get(declared.getKey());
            if (stageIndex == null) {
                StructureCumulus.logger.warn(
                    "preview catalog: the set {} left out, a cell of {} belongs to no stage",
                    setId.text(),
                    label
                );
                return null;
            }
            OrientationMapping.MappingResult mapped = OrientationMapping.transform(orientation, declared.getValue());
            if (mapped instanceof OrientationMapping.MappingResult.Unrepresentable rejected) {
                StructureCumulus.logger.warn(
                    "preview catalog: the set {} left out, the member {} cannot express the orientation "
                            + "({}.{} = {})",
                    setId.text(),
                    label,
                    PreviewExpected.blockId(declared.getValue().getBlock()).text(),
                    rejected.property(),
                    rejected.value()
                );
                return null;
            }
            BlockState state = ((OrientationMapping.MappingResult.Mapped) mapped).state();
            LatticePoint cell = siting.toWorld(declared.getKey());
            if (cell == null) {
                StructureCumulus.logger.warn(
                    "preview catalog: the set {} left out, the member {} leaves the lattice",
                    setId.text(),
                    label
                );
                return null;
            }
            states.add(state);
            cells.add(
                new GhostCell(
                    cell,
                    PreviewExpected.blockId(state.getBlock()),
                    PreviewExpected.properties(state),
                    stageIndex
                )
            );
        }
        List<Cuboid> presence = new ArrayList<>();
        if (satellite != null && satellite.presence() != null) {
            for (Cuboid box : satellite.presence().cells().decomposition()) {
                Cuboid world = siting.toWorld(box);
                if (world == null) {
                    StructureCumulus.logger.warn(
                        "preview catalog: the set {} left out, the presence probe of {} leaves the lattice",
                        setId.text(),
                        label
                    );
                    return null;
                }
                presence.add(world);
            }
        }
        List<PreviewCatalogPayload.Link> links = new ArrayList<>();
        if (satellite != null) {
            for (LinkSpec link : satellite.links()) {
                if (link.mode() == LinkMode.none) {
                    continue;
                }
                LatticePoint parent = link.parentCell();
                LatticePoint satelliteCell = link.satelliteCell();
                LatticePoint parentWorld = parent == null ? null : rootSiting.toWorld(parent);
                LatticePoint satelliteWorld = satelliteCell == null ? null : siting.toWorld(satelliteCell);
                if (parentWorld == null || satelliteWorld == null) {
                    StructureCumulus.logger.warn(
                        "preview catalog: the set {} left out, a link of {} leaves the lattice",
                        setId.text(),
                        label
                    );
                    return null;
                }
                links.add(
                    new PreviewCatalogPayload.Link(
                        link.mode(),
                        parentWorld,
                        satelliteWorld,
                        link.maxDistance(),
                        link.metric()
                    )
                );
            }
        }
        return new ComposedMember(
            new PreviewCatalogPayload.Member(blueprint.id(), label, required, cells, presence, links),
            memberStages
        );
    }

    /** The composite cell count: every member's composed cells, in member order (§3). */
    private static int composedCells(PreviewCatalogPayload.SetEntry set) {
        int cells = 0;
        for (PreviewCatalogPayload.Member member : set.members()) {
            cells += member.cells().size();
        }
        return cells;
    }

    private static List<PreviewCatalogPayload.MaterialLine> materials(Collection<BlockState> states) {
        Map<NamespacedId, Integer> counts = new TreeMap<>();
        for (BlockState state : states) {
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

    /** True when at least one expected cell resolves to a block that draws (design 17 §3.3). */
    private static boolean draws(Collection<BlockState> states) {
        for (BlockState state : states) {
            if (!state.isAir()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasErrors(List<Finding> findings) {
        for (Finding finding : findings) {
            if (finding.severity() == Severity.error) {
                return true;
            }
        }
        return false;
    }

    private static String firstFinding(List<Finding> findings) {
        return findings.isEmpty() ? "no finding" : findings.get(0).message();
    }
}
