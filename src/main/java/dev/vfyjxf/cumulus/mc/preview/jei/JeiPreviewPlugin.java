package dev.vfyjxf.cumulus.mc.preview.jei;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.mc.plugin.ModPlugin;
import dev.vfyjxf.cumulus.mc.plugin.PluginMarker;
import dev.vfyjxf.cumulus.mc.preview.client.PreviewCatalogClient;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewCatalogPayload;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The conditional built-in plugin: JEI's annotation scan instantiates it, and the adapter's loader pass
 * validates the same id through {@link JeiPreviewPlugins}. The category is a singleton, so both
 * instances share one registration and one recipe table.
 */
@JeiPlugin
@PluginMarker
public final class JeiPreviewPlugin implements IModPlugin, ModPlugin {

    static final NamespacedId id = NamespacedId.of(Constants.modId, "jei_preview");

    private static final Map<NamespacedId, BlueprintPreviewRecipe> recipes = new LinkedHashMap<>();
    private static final Set<BlueprintPreviewRecipe> sent = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<BlueprintPreviewRecipe> hidden = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Runnable onCatalog = JeiPreviewPlugin::reconcile;
    private static @Nullable IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(id.namespace(), id.path());
    }

    @Override
    public NamespacedId pluginId() {
        return id;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper helper = registration.getJeiHelpers().getGuiHelper();
        BlueprintPreviewCategory.instance().icon(helper.createDrawableIngredient(VanillaTypes.ITEM_STACK, iconStack()));
        registration.addRecipeCategories(BlueprintPreviewCategory.instance());
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<BlueprintPreviewRecipe> seeded = new ArrayList<>();
        for (PreviewCatalogPayload.Entry entry : PreviewCatalogClient.entries()) {
            BlueprintPreviewRecipe recipe = recipeFor(entry);
            if (sent.add(recipe)) {
                seeded.add(recipe);
            }
        }
        registration.addRecipes(BlueprintPreviewCategory.type, seeded);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        JeiPreview.resetFrames();
        PreviewCatalogClient.listen(onCatalog);
        reconcile();
    }

    @Override
    public void onRuntimeUnavailable() {
        PreviewCatalogClient.unlisten(onCatalog);
        runtime = null;
        recipes.clear();
        sent.clear();
        hidden.clear();
    }

    static List<NamespacedId> listedIds() {
        return List.copyOf(recipes.keySet());
    }

    static boolean show(NamespacedId blueprintId) {
        IJeiRuntime current = runtime;
        BlueprintPreviewRecipe recipe = recipes.get(blueprintId);
        if (current == null || recipe == null) {
            return false;
        }
        current.getRecipesGui().showRecipes(BlueprintPreviewCategory.instance(), List.of(recipe), List.of());
        return true;
    }

    private static BlueprintPreviewRecipe recipeFor(PreviewCatalogPayload.Entry entry) {
        BlueprintPreviewRecipe recipe = recipes.get(entry.blueprintId());
        if (recipe == null) {
            recipe = new BlueprintPreviewRecipe(entry);
            recipes.put(entry.blueprintId(), recipe);
        } else {
            recipe.update(entry);
        }
        return recipe;
    }

    /** Adds new entries and hides vanished ones; JEI has no removal, so a reload hides instead. */
    private static void reconcile() {
        IJeiRuntime current = runtime;
        if (current == null) {
            return;
        }
        IRecipeManager manager = current.getRecipeManager();
        Set<NamespacedId> listed = new HashSet<>();
        List<BlueprintPreviewRecipe> added = new ArrayList<>();
        List<BlueprintPreviewRecipe> restored = new ArrayList<>();
        for (PreviewCatalogPayload.Entry entry : PreviewCatalogClient.entries()) {
            listed.add(entry.blueprintId());
            BlueprintPreviewRecipe recipe = recipeFor(entry);
            if (sent.add(recipe)) {
                added.add(recipe);
            } else if (hidden.remove(recipe)) {
                restored.add(recipe);
            }
        }
        if (!added.isEmpty()) {
            manager.addRecipes(BlueprintPreviewCategory.type, added);
        }
        if (!restored.isEmpty()) {
            manager.unhideRecipes(BlueprintPreviewCategory.type, restored);
        }
        for (Map.Entry<NamespacedId, BlueprintPreviewRecipe> known : recipes.entrySet()) {
            if (!listed.contains(known.getKey()) && hidden.add(known.getValue())) {
                manager.hideRecipes(BlueprintPreviewCategory.type, List.of(known.getValue()));
            }
        }
    }

    private static ItemStack iconStack() {
        for (BlueprintPreviewRecipe recipe : recipes.values()) {
            if (!recipe.materials().isEmpty()) {
                return recipe.materials().get(0).stack();
            }
        }
        return new ItemStack(Items.STRUCTURE_BLOCK);
    }
}
