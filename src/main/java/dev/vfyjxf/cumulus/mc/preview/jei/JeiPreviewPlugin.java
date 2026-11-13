package dev.vfyjxf.cumulus.mc.preview.jei;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.mc.plugin.ModPlugin;
import dev.vfyjxf.cumulus.mc.plugin.PluginMarker;
import dev.vfyjxf.cumulus.mc.preview.client.PreviewCatalogClient;
import dev.vfyjxf.cumulus.mc.preview.jei.BlueprintPreviewCategory.PageView;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewCatalogPayload;
import dev.vfyjxf.cumulus.mc.preview.net.VariantSummary;
import dev.vfyjxf.cumulus.mc.preview.scene.BlueprintPreviewRecipe;
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
import java.util.Objects;
import java.util.Set;

/**
 * The conditional built-in plugin: JEI's annotation scan instantiates it, and the adapter's loader pass
 * validates the same id through {@link JeiPreviewPlugins}. The category is a singleton, so both
 * instances share one registration and one recipe table.
 *
 * <p>A variant group is one page, keyed by the first member the catalog ships in declaration order;
 * the group's other members are not listed as pages, so the entry count never grows with the number
 * of variants (design 15 §12.3).
 */
@JeiPlugin
@PluginMarker
public final class JeiPreviewPlugin implements IModPlugin, ModPlugin {

    static final NamespacedId id = NamespacedId.of(Constants.modId, "jei_preview");

    private static final Map<NamespacedId, BlueprintPreviewRecipe> recipes = new LinkedHashMap<>();
    private static final Map<NamespacedId, PageView> views = new LinkedHashMap<>();
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
        for (PageSpec page : pages()) {
            BlueprintPreviewRecipe recipe = recipeFor(page);
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
        views.clear();
        sent.clear();
        hidden.clear();
    }

    /** One page in the category: a blueprint group or one set entry (design 18 §7). */
    private record PageSpec(
        NamespacedId key,
        @Nullable List<PreviewCatalogPayload.Entry> group,
        PreviewCatalogPayload.@Nullable SetEntry set
    ) {}

    static List<NamespacedId> listedIds() {
        return List.copyOf(recipes.keySet());
    }

    static @Nullable BlueprintPreviewRecipe recipe(NamespacedId blueprintId) {
        return recipes.get(blueprintId);
    }

    /** One page's JEI-side view state, or null when the category does not list the page. */
    static @Nullable PageView view(NamespacedId blueprintId) {
        BlueprintPreviewRecipe recipe = recipes.get(blueprintId);
        return recipe == null ? null : viewFor(recipe);
    }

    /** The page's view state, clamped to the page's current layer count. */
    static PageView viewFor(BlueprintPreviewRecipe recipe) {
        PageView view = views.computeIfAbsent(recipe.blueprintId(), id -> new PageView());
        view.clamp(recipe.scene().layers());
        return view;
    }

    static List<String> lines(NamespacedId blueprintId) {
        BlueprintPreviewRecipe recipe = recipes.get(blueprintId);
        return recipe == null ? List.of() : recipe.lines();
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

    /**
     * The catalog reshaped into pages: a standalone blueprint is its own page, a group is one page
     * keyed by the first member the catalog ships, and a set entry is one composite page. A set whose
     * id a blueprint also carries keeps its own page under a derived key, so neither shadows the other
     * (design 18 §7). A member left out by a catalogue cap is not switchable; the catalogue log names
     * it, as every cap does (design 15 §6.1, §12.3).
     */
    private static List<PageSpec> pages() {
        List<PreviewCatalogPayload.Entry> entries = PreviewCatalogClient.entries();
        Map<NamespacedId, PreviewCatalogPayload.Entry> byId = new LinkedHashMap<>();
        for (PreviewCatalogPayload.Entry entry : entries) {
            byId.put(entry.blueprintId(), entry);
        }
        List<PageSpec> pages = new ArrayList<>();
        Set<NamespacedId> taken = new HashSet<>();
        for (PreviewCatalogPayload.Entry entry : entries) {
            VariantSummary summary = entry.variants();
            List<PreviewCatalogPayload.Entry> members = new ArrayList<>();
            if (summary == null) {
                members.add(entry);
            } else {
                for (VariantSummary.Member member : summary.members()) {
                    PreviewCatalogPayload.Entry shipped = byId.get(member.id());
                    if (shipped != null) {
                        members.add(shipped);
                    }
                }
            }
            if (members.isEmpty()) {
                members.add(entry);
            }
            NamespacedId key = members.get(0).blueprintId();
            if (taken.add(key)) {
                pages.add(new PageSpec(key, List.copyOf(members), null));
            }
        }
        for (PreviewCatalogPayload.SetEntry set : PreviewCatalogClient.sets()) {
            NamespacedId key = set.setId();
            while (!taken.add(key)) {
                key = NamespacedId.of(key.namespace(), key.path() + "/set");
            }
            pages.add(new PageSpec(key, null, set));
        }
        return List.copyOf(pages);
    }

    private static BlueprintPreviewRecipe recipeFor(PageSpec page) {
        BlueprintPreviewRecipe recipe = recipes.get(page.key());
        if (recipe == null) {
            recipe = page.set() != null
                    ? new BlueprintPreviewRecipe(page.set(), page.key())
                    : new BlueprintPreviewRecipe(Objects.requireNonNull(page.group(), "group"));
            recipes.put(page.key(), recipe);
        } else if (page.set() != null) {
            recipe.updateSet(page.set());
        } else {
            recipe.update(Objects.requireNonNull(page.group(), "group"));
        }
        viewFor(recipe);
        return recipe;
    }

    /** Adds new pages and hides vanished ones; JEI has no removal, so a reload hides instead. */
    private static void reconcile() {
        IJeiRuntime current = runtime;
        if (current == null) {
            return;
        }
        IRecipeManager manager = current.getRecipeManager();
        Set<NamespacedId> listed = new HashSet<>();
        List<BlueprintPreviewRecipe> added = new ArrayList<>();
        List<BlueprintPreviewRecipe> restored = new ArrayList<>();
        for (PageSpec page : pages()) {
            listed.add(page.key());
            BlueprintPreviewRecipe recipe = recipeFor(page);
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
