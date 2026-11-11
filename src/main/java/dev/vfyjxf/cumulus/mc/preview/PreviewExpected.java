package dev.vfyjxf.cumulus.mc.preview;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationDeclaration;
import dev.vfyjxf.cumulus.mc.OrientationMapping;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The declared expected states of one blueprint under one orientation, read through the declaration
 * port (D-91) and materialized against the registry. An orientation the declarations cannot express
 * reports the offending block instead of a partial map.
 */
final class PreviewExpected {

    /** One unrepresentable declaration: the block, its property and the value the orientation needs. */
    record Filter(NamespacedId block, String property, String value) {}

    /** The oriented local states plus the filter that stopped the walk, if any. */
    record Outcome(Map<LatticePoint, BlockState> states, @Nullable Filter filter) {

        Outcome {
            states = Collections.unmodifiableMap(new TreeMap<>(states));
        }
    }

    private PreviewExpected() {}

    static Outcome of(Blueprint blueprint, Orientation orientation) {
        Map<LatticePoint, BlockState> declared = new TreeMap<>();
        for (Check<?> check : blueprint.checks()) {
            List<NamespacedId> perCell = new ArrayList<>();
            List<NamespacedId> focused = new ArrayList<>();
            walk(check.expectation().declaration(), false, false, perCell, focused);
            if (!perCell.isEmpty()) {
                for (LatticePoint cell : cells(check.bay().cells())) {
                    declare(declared, cell, perCell);
                }
            }
            if (!focused.isEmpty()) {
                for (LatticePoint cell : cells(check.focus().staticCells(check.bay().cells()))) {
                    declare(declared, cell, focused);
                }
            }
        }
        Map<LatticePoint, BlockState> oriented = new TreeMap<>();
        for (Map.Entry<LatticePoint, BlockState> entry : declared.entrySet()) {
            BlockState state = entry.getValue();
            OrientationMapping.MappingResult result = OrientationMapping.transform(orientation, state);
            if (result instanceof OrientationMapping.MappingResult.Mapped mapped) {
                oriented.put(entry.getKey(), mapped.state());
            } else if (result instanceof OrientationMapping.MappingResult.Unrepresentable rejected) {
                return new Outcome(
                    Map.of(),
                    new Filter(blockId(state.getBlock()), rejected.property(), rejected.value())
                );
            }
        }
        return new Outcome(oriented, null);
    }

    static Map<String, String> properties(BlockState state) {
        Map<String, String> properties = new TreeMap<>();
        for (Property<?> property : state.getProperties()) {
            properties.put(property.getName(), valueName(state, property));
        }
        return properties;
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    static NamespacedId blockId(Block block) {
        ResourceLocation location = BuiltInRegistries.BLOCK.getKey(block);
        return NamespacedId.of(location.getNamespace(), location.getPath());
    }

    static List<LatticePoint> cells(CuboidSet set) {
        List<LatticePoint> out = new ArrayList<>();
        for (Cuboid box : set.decomposition()) {
            for (int x = box.min().x(); x < box.max().x(); x++) {
                for (int y = box.min().y(); y < box.max().y(); y++) {
                    for (int z = box.min().z(); z < box.max().z(); z++) {
                        out.add(LatticePoint.of(x, y, z));
                    }
                }
            }
        }
        return out;
    }

    private static void declare(Map<LatticePoint, BlockState> states, LatticePoint cell, List<NamespacedId> declared) {
        if (states.containsKey(cell)) {
            return;
        }
        for (NamespacedId id : declared) {
            ResourceLocation location = location(id);
            // A defaulted registry answers air for an unknown key, so absence is asked before the value.
            if (!BuiltInRegistries.BLOCK.containsKey(location)) {
                continue;
            }
            states.put(cell, BuiltInRegistries.BLOCK.get(location).defaultBlockState());
            return;
        }
    }

    /** Mirrors the kernel's per-cell leaf law: an {@code eachCell} arm judges every bay cell. */
    private static void walk(
        ExpectationDeclaration declaration,
        boolean negated,
        boolean perCell,
        List<NamespacedId> eachCell,
        List<NamespacedId> focused
    ) {
        switch (declaration) {
            case ExpectationDeclaration.BlockLeaf leaf -> {
                if (!negated) {
                    (perCell ? eachCell : focused).add(leaf.type());
                }
            }
            case ExpectationDeclaration.Composite composite -> {
                boolean children = perCell || composite.kind() == ExpectationDeclaration.CompositeKind.eachCell;
                boolean inverted = composite.kind() == ExpectationDeclaration.CompositeKind.not ? !negated : negated;
                for (ExpectationDeclaration child : composite.children()) {
                    walk(child, inverted, children, eachCell, focused);
                }
            }
            case ExpectationDeclaration.Threshold threshold -> {
                for (ExpectationDeclaration child : threshold.children()) {
                    walk(child, negated, perCell, eachCell, focused);
                }
            }
            default -> {
                // traits, hosts and markers declare no block state to draw
            }
        }
    }

    private static ResourceLocation location(NamespacedId id) {
        return ResourceLocation.fromNamespaceAndPath(id.namespace(), id.path());
    }
}
