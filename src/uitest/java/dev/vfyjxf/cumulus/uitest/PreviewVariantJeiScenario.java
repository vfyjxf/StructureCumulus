package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.mc.preview.jei.JeiPreview;

import java.util.List;

/**
 * The variant page end to end: a group of two documents is one category page keyed by its first
 * shipped member, the page's arrows switch the member in place without adding a page, and "Ghost to
 * world" opens the in-world session on the selected member — the same id the build executor then
 * receives.
 */
@ClientScenario(
    name = "jei.variants",
    group = "jei",
    tags = {"jei", "preview", "variants"},
    timeoutMs = 120_000,
    scenarioTimeoutMs = 180_000
)
public final class PreviewVariantJeiScenario {

    public void define(ScenarioBuilder build) {
        build = PreviewScenarioSupport.stage(build).log(
            "jei.variants: the coil group is one page, the arrows switch in place, the button opens the selected member"
        );

        build = build.waitUntil("the server pushed the catalog with the coil group", PreviewJeiProbe::catalogReady)
                .check(
                    "the catalog still carries both member documents",
                    () -> PreviewJeiProbe.catalogIds().containsAll(
                        List.of(PreviewScenarioSupport.coilBasic, PreviewScenarioSupport.coilReinforced)
                    ),
                    () -> PreviewJeiProbe.catalogIds().toString()
                )
                .check(
                    "the group is one page keyed by its first member, so the entry count does not grow",
                    () -> PreviewJeiProbe.pageIds().contains(PreviewScenarioSupport.coilBasic)
                            && !PreviewJeiProbe.pageIds().contains(PreviewScenarioSupport.coilReinforced),
                    () -> PreviewJeiProbe.pageIds().toString()
                )
                .check(
                    "the page lists the declaration order",
                    () -> JeiPreview.variants(PreviewScenarioSupport.coilBasic)
                            .equals(List.of(PreviewScenarioSupport.coilBasic, PreviewScenarioSupport.coilReinforced)),
                    () -> JeiPreview.variants(PreviewScenarioSupport.coilBasic).toString()
                )
                .action(
                    "open the Multiblock Preview category through the JEI API",
                    context -> PreviewJeiProbe.openCategory()
                ).waitUntil("JEI's recipe screen owns the frame", PreviewJeiProbe::screenOpen)
                .waitUntil("the category holds the synced pages", PreviewJeiProbe::categoryHasRecipes)
                .action(
                    "page the category to the coil page",
                    context -> JeiPreview.show(PreviewScenarioSupport.coilBasic)
                ).frames(6)
                .check(
                    "the page starts on the first member",
                    () -> JeiPreview.variantIndex(PreviewScenarioSupport.coilBasic) == 0
                            && PreviewScenarioSupport.coilBasic
                                    .equals(JeiPreview.selectedBlueprintId(PreviewScenarioSupport.coilBasic))
                            && JeiPreview.drawnCells() == 4,
                    () -> "index=" + JeiPreview.variantIndex(PreviewScenarioSupport.coilBasic) + " selected="
                            + JeiPreview.selectedBlueprintId(PreviewScenarioSupport.coilBasic) + " cells="
                            + JeiPreview.drawnCells()
                )
                .action(
                    "switch the page right",
                    context -> JeiPreview.cycleVariant(PreviewScenarioSupport.coilBasic, 1)
                ).frames(4)
                .check(
                    "the right arrow switches the member in place",
                    () -> JeiPreview.variantIndex(PreviewScenarioSupport.coilBasic) == 1
                            && PreviewScenarioSupport.coilReinforced
                                    .equals(JeiPreview.selectedBlueprintId(PreviewScenarioSupport.coilBasic))
                            && JeiPreview.drawnCells() == 4,
                    () -> "index=" + JeiPreview.variantIndex(PreviewScenarioSupport.coilBasic) + " selected="
                            + JeiPreview.selectedBlueprintId(PreviewScenarioSupport.coilBasic) + " cells="
                            + JeiPreview.drawnCells()
                ).screenshot("jei-variants").frames(2).checkCaptured("jei-variants")
                .action(
                    "switch the page left",
                    context -> JeiPreview.cycleVariant(PreviewScenarioSupport.coilBasic, -1)
                ).frames(2)
                .check(
                    "the left arrow returns to the first member",
                    () -> JeiPreview.variantIndex(PreviewScenarioSupport.coilBasic) == 0,
                    () -> Integer.toString(JeiPreview.variantIndex(PreviewScenarioSupport.coilBasic))
                )
                .action(
                    "switch the page right again for the world open",
                    context -> JeiPreview.cycleVariant(PreviewScenarioSupport.coilBasic, 1)
                )
                .action(
                    "press the page's Ghost to world",
                    context -> JeiPreview.openSelected(PreviewScenarioSupport.coilBasic)
                ).waitUntil("the server opened the session on the selected member", PreviewScenarioSupport::synced)
                .check(
                    "the session is the selected tier, not the page's first id",
                    () -> PreviewScenarioSupport.coilReinforced.equals(PreviewProbe.blueprintId())
                            && PreviewProbe.hudHas("variant 2/2: 强化线圈"),
                    () -> String.valueOf(PreviewProbe.blueprintId())
                ).timelineRecord(
                    "jei-variants",
                    () -> "pages=" + PreviewJeiProbe.pageIds().size() + ", selected=" + PreviewProbe.blueprintId()
                            + ", drawn=" + JeiPreview.drawnCells()
                );

        PreviewScenarioSupport.cancel(build);
        PreviewScenarioSupport.cleanup(build);
    }
}
