package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.mc.preview.jei.JeiPreview;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewPresentation;

import java.util.List;
import java.util.Set;

/**
 * The JEI category end to end: the catalog of the merged document source arrives, the category opens,
 * the isometric view draws cells, and the button opens the ordinary in-world session at the crosshair.
 * Assertions read the model (ids, cells, frame counters), never pixels.
 */
@ClientScenario(
    name = "jei.preview",
    group = "jei",
    tags = {"jei", "preview"},
    timeoutMs = 120_000,
    scenarioTimeoutMs = 180_000
)
public final class PreviewJeiScenario {

    private static final NamespacedId airCell = NamespacedId.of("structurecumulus", "air_cell");
    private static final NamespacedId setRoot = NamespacedId.of("structurecumulus", "set_root");
    private static final NamespacedId setWing = NamespacedId.of("structurecumulus", "set_wing");
    private static final NamespacedId setDemo = NamespacedId.of("structurecumulus", "set_demo");
    private static final NamespacedId staged = NamespacedId.of("structurecumulus", "preview_stages");
    private static final String stagedDescription = "the staged demo: a brick floor with a chimney above it";
    private static final String floorHint = "lay the brick ring before the corners";
    private static final String stackHint = "place it after the floor is complete";

    public void define(ScenarioBuilder build) {
        build = PreviewScenarioSupport.stage(build).yawPitch(0.0F, 40.0F)
                .log("jei.preview: the catalog syncs, the category draws, the button opens the session");

        build.waitUntil("the server pushed the catalog with the datapack probe", PreviewJeiProbe::catalogReady)
                .check(
                    "the catalog lists every built-in blueprint and the datapack probe",
                    () -> PreviewJeiProbe.catalogIds()
                            .containsAll(List.of(airCell, setRoot, setWing, PreviewScenarioSupport.probe)),
                    () -> PreviewJeiProbe.catalogIds().toString()
                )
                .check(
                    "the structure set of the same source is not a blueprint entry",
                    () -> !PreviewJeiProbe.catalogIds().contains(setDemo)
                )
                .check(
                    "the probe entry carries the four expected cells",
                    () -> PreviewJeiProbe.catalogCells(PreviewScenarioSupport.probe) == 4
                )
                .check(
                    "the category lists exactly the ids the catalog carries",
                    () -> Set.copyOf(JeiPreview.blueprintIds()).equals(Set.copyOf(PreviewJeiProbe.catalogIds()))
                ).check("the catalog entry carries the document's description and each part's hint", () -> {
                    PreviewPresentation presentation = PreviewJeiProbe.presentation(staged);
                    return presentation != null
                            && stagedDescription.equals(presentation.description())
                            && presentation.stages().size() == 2
                            && floorHint.equals(presentation.stages().get(0).hint())
                            && stackHint.equals(presentation.stages().get(1).hint());
                }, () -> String.valueOf(PreviewJeiProbe.presentation(staged)))
                .action(
                    "open the Multiblock Preview category through the JEI API",
                    context -> PreviewJeiProbe.openCategory()
                ).waitUntil("JEI's recipe screen owns the frame", PreviewJeiProbe::screenOpen)
                .waitUntil("the category holds the synced entries", PreviewJeiProbe::categoryHasRecipes)
                .check(
                    "the JEI entry lists the description and one line per part with its hint",
                    () -> PreviewJeiProbe.catalogLines(staged).equals(
                        List.of(
                            stagedDescription,
                            "1. structurecumulus:base (9) · " + floorHint,
                            "2. structurecumulus:stack (3) · " + stackHint
                        )
                    ),
                    () -> PreviewJeiProbe.catalogLines(staged).toString()
                )
                .action(
                    "page the category to the probe blueprint",
                    context -> JeiPreview.show(PreviewScenarioSupport.probe)
                ).frames(6).check("the isometric view drew frames", () -> JeiPreview.drawnFrames() > 0)
                .check(
                    "the drawn frame covered the probe's four expected cells",
                    () -> JeiPreview.drawnCells() == 4,
                    () -> Integer.toString(JeiPreview.drawnCells())
                ).screenshot("jei").frames(2).checkCaptured("jei")
                .action("capture the catalog before a datapack reload", context -> PreviewJeiProbe.captureCatalog())
                .runCommand("reload")
                .waitUntil("the server pushed a fresh catalog after the reload", PreviewJeiProbe::catalogRefreshed)
                .waitUntil("JEI restarted and holds the synced entries again", PreviewJeiProbe::categoryHasRecipes)
                .check(
                    "the reload did not duplicate a recipe page",
                    () -> PreviewJeiProbe.recipeCount() == PreviewJeiProbe.catalogIds().size(),
                    () -> PreviewJeiProbe.recipeCount() + " recipes for " + PreviewJeiProbe.catalogIds().size()
                            + " entries"
                )
                .action(
                    "page the restarted category to the probe blueprint",
                    context -> JeiPreview.show(PreviewScenarioSupport.probe)
                ).frames(6)
                .check("the restarted category still draws the probe's four cells", () -> JeiPreview.drawnCells() == 4)
                .action("press Ghost to world", context -> JeiPreview.openWorld(PreviewScenarioSupport.probe))
                .waitUntil("the server opened the session at the crosshair", PreviewScenarioSupport::synced)
                .check("the probe previews as its four stairs", () -> PreviewProbe.ghostCount() == 4).timelineRecord(
                    "jei-preview",
                    () -> "entries=" + PreviewJeiProbe.catalogIds().size() + ", drawn=" + JeiPreview.drawnCells()
                            + ", frames=" + JeiPreview.drawnFrames()
                );

        PreviewScenarioSupport.cancel(build);
        PreviewScenarioSupport.cleanup(build);
    }
}
