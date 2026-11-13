package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.preview.client.PreviewCatalogClient;
import dev.vfyjxf.cumulus.mc.preview.jei.JeiPreview;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewCatalogPayload;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewPresentation;
import dev.vfyjxf.cumulus.mc.preview.net.VariantSummary;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The harness's own JEI plugin: it captures the runtime so a scenario can open the production
 * category through the public JEI API instead of reaching into the mod's package.
 */
@JeiPlugin
public final class PreviewJeiProbe implements IModPlugin {

    private static final ResourceLocation uid = ResourceLocation
            .fromNamespaceAndPath("structurecumulusuitest", "jei_probe");

    private static volatile @Nullable IJeiRuntime runtime;

    private static @Nullable List<PreviewCatalogPayload.Entry> captured;

    @Override
    public ResourceLocation getPluginUid() {
        return uid;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    static boolean runtimeAvailable() {
        return runtime != null;
    }

    static boolean catalogReady() {
        List<PreviewCatalogPayload.Entry> entries = PreviewCatalogClient.entries();
        for (PreviewCatalogPayload.Entry entry : entries) {
            if (entry.blueprintId().equals(PreviewScenarioSupport.probe)) {
                return true;
            }
        }
        return false;
    }

    static List<NamespacedId> catalogIds() {
        return PreviewCatalogClient.entries().stream().map(PreviewCatalogPayload.Entry::blueprintId).toList();
    }

    /**
     * The pages the category should list: a standalone entry is its own page, a group is one page keyed
     * by the first shipped member in declaration order (design 15 §12.3).
     */
    static List<NamespacedId> pageIds() {
        List<PreviewCatalogPayload.Entry> entries = PreviewCatalogClient.entries();
        Set<NamespacedId> shipped = new HashSet<>(catalogIds());
        Set<NamespacedId> pages = new LinkedHashSet<>();
        for (PreviewCatalogPayload.Entry entry : entries) {
            VariantSummary summary = entry.variants();
            if (summary == null) {
                pages.add(entry.blueprintId());
                continue;
            }
            for (VariantSummary.Member member : summary.members()) {
                if (shipped.contains(member.id())) {
                    pages.add(member.id());
                    break;
                }
            }
        }
        // a set page is one more page; a blueprint that shares its id keeps the bare key (design 18 §7)
        for (PreviewCatalogPayload.SetEntry set : PreviewCatalogClient.sets()) {
            NamespacedId key = set.setId();
            while (!pages.add(key)) {
                key = NamespacedId.of(key.namespace(), key.path() + "/set");
            }
        }
        return List.copyOf(pages);
    }

    /** The catalogue's set entry for {@code setId}, or null when it carries none. */
    static PreviewCatalogPayload.@Nullable SetEntry setEntry(NamespacedId setId) {
        for (PreviewCatalogPayload.SetEntry set : PreviewCatalogClient.sets()) {
            if (set.setId().equals(setId)) {
                return set;
            }
        }
        return null;
    }

    static @Nullable PreviewPresentation presentation(NamespacedId blueprintId) {
        for (PreviewCatalogPayload.Entry entry : PreviewCatalogClient.entries()) {
            if (entry.blueprintId().equals(blueprintId)) {
                return entry.presentation();
            }
        }
        return null;
    }

    static List<String> catalogLines(NamespacedId blueprintId) {
        return JeiPreview.lines(blueprintId);
    }

    static int catalogCells(NamespacedId blueprintId) {
        for (PreviewCatalogPayload.Entry entry : PreviewCatalogClient.entries()) {
            if (entry.blueprintId().equals(blueprintId)) {
                return entry.cells().size();
            }
        }
        return -1;
    }

    static @Nullable GhostCell cell(NamespacedId blueprintId, int index) {
        for (PreviewCatalogPayload.Entry entry : PreviewCatalogClient.entries()) {
            if (entry.blueprintId().equals(blueprintId)) {
                return index < 0 || index >= entry.cells().size() ? null : entry.cells().get(index);
            }
        }
        return null;
    }

    /** The entry's cell at one lattice point, or -1 when the entry declares none there. */
    static int cellIndex(NamespacedId blueprintId, int x, int y, int z) {
        for (PreviewCatalogPayload.Entry entry : PreviewCatalogClient.entries()) {
            if (entry.blueprintId().equals(blueprintId)) {
                for (int index = 0; index < entry.cells().size(); index++) {
                    LatticePoint at = entry.cells().get(index).cell();
                    if (at.x() == x && at.y() == y && at.z() == z) {
                        return index;
                    }
                }
            }
        }
        return -1;
    }

    static boolean openCategory() {
        IJeiRuntime current = runtime;
        if (current == null) {
            return false;
        }
        Optional<RecipeType<?>> type = current.getRecipeManager()
                .getRecipeType(ResourceLocation.fromNamespaceAndPath("structurecumulus", "multiblock_preview"));
        if (type.isEmpty()) {
            return false;
        }
        current.getRecipesGui().showTypes(List.of(type.get()));
        return true;
    }

    static boolean screenOpen() {
        var screen = Minecraft.getInstance().screen;
        return screen != null && screen.getClass().getName().equals("mezz.jei.gui.recipes.RecipesGui");
    }

    static void captureCatalog() {
        captured = PreviewCatalogClient.entries();
    }

    static boolean catalogRefreshed() {
        return captured != null && PreviewCatalogClient.entries() != captured;
    }

    static long recipeCount() {
        IJeiRuntime current = runtime;
        if (current == null) {
            return -1L;
        }
        return current.getRecipeManager()
                .getRecipeType(ResourceLocation.fromNamespaceAndPath("structurecumulus", "multiblock_preview"))
                .map(type -> current.getRecipeManager().createRecipeLookup(unchecked(type)).get().count()).orElse(-1L);
    }

    static boolean categoryHasRecipes() {
        IJeiRuntime current = runtime;
        if (current == null) {
            return false;
        }
        Optional<RecipeType<?>> type = current.getRecipeManager()
                .getRecipeType(ResourceLocation.fromNamespaceAndPath("structurecumulus", "multiblock_preview"));
        return type.filter(
            recipeType -> current.getRecipeManager().createRecipeLookup(unchecked(recipeType)).get().findAny()
                    .isPresent()
        ).isPresent();
    }

    @SuppressWarnings("unchecked")
    private static <T> RecipeType<T> unchecked(RecipeType<?> type) {
        return (RecipeType<T>) type;
    }
}
