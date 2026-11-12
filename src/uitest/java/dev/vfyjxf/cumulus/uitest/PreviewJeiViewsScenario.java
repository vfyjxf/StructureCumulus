package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.mc.preview.jei.JeiPreview;
import dev.vfyjxf.cumulus.mc.preview.jei.PreviewViewMode;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The category's three view modes: the button cycles isometric → three-view → slice grid, the three
 * panels fit one entry in one row and answer a hover each, and one slice moves all of them. Assertions
 * read the model the face draws from (panel counts, cell points, tooltip lines), never pixels.
 */
@ClientScenario(
    name = "jei.views",
    group = "jei",
    tags = {"jei", "preview"},
    timeoutMs = 120_000,
    scenarioTimeoutMs = 240_000
)
public final class PreviewJeiViewsScenario {

    private static final NamespacedId staged = NamespacedId.of("structurecumulus", "preview_stages");
    private static final NamespacedId probe = PreviewScenarioSupport.probe;
    private static final String basePart = "structurecumulus:base";
    private static final String shellPart = "structurecumulus:shell";
    private static final String floorHint = "lay the brick ring before the corners";

    public void define(ScenarioBuilder build) {
        build = PreviewScenarioSupport.stage(build).log(
            "jei.views: the mode button cycles, the three panels fit one entry, a hover answers and one slice moves them"
        );

        // one panel width captured from the default view, so the grid's full-size claim compares to it
        int[] isometricWidth = {-1};

        build.waitUntil("the server pushed the catalog", PreviewJeiProbe::catalogReady)
                .action(
                    "open the Multiblock Preview category through the JEI API",
                    context -> PreviewJeiProbe.openCategory()
                ).waitUntil("JEI's recipe screen owns the frame", PreviewJeiProbe::screenOpen)
                .waitUntil("the category holds the synced entries", PreviewJeiProbe::categoryHasRecipes)
                .action("page the category to the staged blueprint", context -> JeiPreview.show(staged)).frames(6)
                .check("the isometric view is the default and draws the whole entry in one panel", () -> {
                    List<JeiPreview.Panel> panels = JeiPreview.drawnPanels();
                    if (panels.size() != 1) {
                        return false;
                    }
                    isometricWidth[0] = panels.get(0).width();
                    return JeiPreview.viewMode(staged) == PreviewViewMode.isometric && panels.get(0).cells() == 12;
                }, PreviewJeiViewsScenario::panels)
                .action("cycle the view button once", context -> JeiPreview.cycleViewMode(staged)).frames(6)
                .check("the button lands on the three-view: front, side and top in one row", () -> {
                    List<JeiPreview.Panel> panels = JeiPreview.drawnPanels();
                    return JeiPreview.viewMode(staged) == PreviewViewMode.threeView
                            && panels.size() == 3
                            && panels.get(0).cells() > 0
                            && panels.get(1).cells() > 0
                            && panels.get(2).cells() > 0
                            && panels.get(0).y() == panels.get(1).y()
                            && panels.get(1).y() == panels.get(2).y()
                            && panels.get(0).width() == panels.get(1).width()
                            && panels.get(1).width() == panels.get(2).width()
                            && panels.get(0).x() < panels.get(1).x()
                            && panels.get(1).x() < panels.get(2).x();
                }, PreviewJeiViewsScenario::panels)
                .check(
                    "all three panels highlight the page's one slice",
                    () -> sameLayer(JeiPreview.slice(staged)),
                    PreviewJeiViewsScenario::panels
                ).screenshot("jei-views").frames(2).checkCaptured("jei-views")
                .check("the front panel looks along +Z: two cells of one column share a point", () -> {
                    double[] first = point(staged, 0, PreviewJeiProbe.cellIndex(staged, 0, 0, 0));
                    double[] column = point(staged, 0, PreviewJeiProbe.cellIndex(staged, 0, 0, 2));
                    double[] firstSide = point(staged, 1, PreviewJeiProbe.cellIndex(staged, 0, 0, 0));
                    double[] columnSide = point(staged, 1, PreviewJeiProbe.cellIndex(staged, 0, 0, 2));
                    return same(first, column) && !same(firstSide, columnSide);
                }, () -> pointText(staged, 0) + " vs " + pointText(staged, 1))
                .check("the side panel looks along +X: two cells of one row share a point", () -> {
                    double[] first = point(staged, 1, PreviewJeiProbe.cellIndex(staged, 0, 0, 0));
                    double[] row = point(staged, 1, PreviewJeiProbe.cellIndex(staged, 2, 0, 0));
                    double[] firstFront = point(staged, 0, PreviewJeiProbe.cellIndex(staged, 0, 0, 0));
                    double[] rowFront = point(staged, 0, PreviewJeiProbe.cellIndex(staged, 2, 0, 0));
                    return same(first, row) && !same(firstFront, rowFront);
                }, () -> pointText(staged, 1) + " vs " + pointText(staged, 0))
                .check("a cell's point lands inside the panel it was fitted to", () -> {
                    List<JeiPreview.Panel> panels = JeiPreview.drawnPanels();
                    return panels.size() == 3
                            && inside(panels.get(2), point(staged, 2, PreviewJeiProbe.cellIndex(staged, 1, 3, 1)));
                }, PreviewJeiViewsScenario::panels)
                .check(
                    "the top panel draws only the page's layer",
                    () -> point(staged, 2, PreviewJeiProbe.cellIndex(staged, 0, 0, 0)) == null
                            && point(staged, 2, PreviewJeiProbe.cellIndex(staged, 1, 3, 1)) != null,
                    () -> pointText(staged, 2)
                ).check("a hovered cell of the front panel names its block, its part and that part's hint", () -> {
                    int index = PreviewJeiProbe.cellIndex(staged, 0, 0, 0);
                    double[] at = point(staged, 0, index);
                    return at != null
                            && JeiPreview.hoverText(staged, at[0], at[1])
                                    .equals(List.of("minecraft:stone_bricks", "part: " + basePart + " · " + floorHint));
                }, () -> hoverText(staged, 0, PreviewJeiProbe.cellIndex(staged, 0, 0, 0)))
                .action("page the category to the probe blueprint", context -> JeiPreview.show(probe))
                .action("cycle the probe's page to the three-view", context -> JeiPreview.cycleViewMode(probe))
                .frames(6)
                .check("the probe's stairs answer a hover with their id, every property and their part", () -> {
                    int index = PreviewJeiProbe.cellIndex(probe, 0, 0, 0);
                    double[] at = point(probe, 0, index);
                    return at != null
                            && JeiPreview.hoverText(probe, at[0], at[1]).equals(cellLines(probe, index, shellPart));
                }, () -> {
                    int index = PreviewJeiProbe.cellIndex(probe, 0, 0, 0);
                    return "expected " + cellLines(probe, index, shellPart) + ", hovered " + hoverText(probe, 0, index);
                }).action("page the category back to the staged blueprint", context -> JeiPreview.show(staged))
                .action("move the slice to the bottom layer", context -> JeiPreview.setSlice(staged, 0)).frames(6)
                .check("one slice moves all three panels: the top one keeps that layer's nine cells", () -> {
                    List<JeiPreview.Panel> panels = JeiPreview.drawnPanels();
                    return panels.size() == 3
                            && JeiPreview.slice(staged) == 0
                            && panels.get(0).cells() == 12
                            && panels.get(1).cells() == 12
                            && panels.get(2).cells() == 9
                            && sameLayer(0);
                }, PreviewJeiViewsScenario::panels)
                .action("cycle the button to the slice grid", context -> JeiPreview.cycleViewMode(staged)).frames(6)
                .check("the slice grid is the top panel alone at the page's full width, one layer only", () -> {
                    List<JeiPreview.Panel> panels = JeiPreview.drawnPanels();
                    return JeiPreview.viewMode(staged) == PreviewViewMode.sliceGrid
                            && panels.size() == 1
                            && panels.get(0).cells() == 9
                            && panels.get(0).layer() == 0
                            && panels.get(0).width() == isometricWidth[0];
                }, PreviewJeiViewsScenario::panels).screenshot("jei-grid").frames(2).checkCaptured("jei-grid")
                .action("cycle the button back to the isometric view", context -> JeiPreview.cycleViewMode(staged))
                .frames(6).check("the isometric view is not regressed: one panel, the layers up to the slice", () -> {
                    List<JeiPreview.Panel> panels = JeiPreview.drawnPanels();
                    return JeiPreview.viewMode(staged) == PreviewViewMode.isometric
                            && panels.size() == 1
                            && panels.get(0).cells() == 9
                            && panels.get(0).layer() == 0;
                }, PreviewJeiViewsScenario::panels)
                .timelineRecord(
                    "jei-views",
                    () -> "mode=" + JeiPreview.viewMode(staged) + ", slice=" + JeiPreview.slice(staged) + ", panels="
                            + panels()
                ).closeScreen();

        PreviewScenarioSupport.cleanup(build);
    }

    private static String panels() {
        StringBuilder text = new StringBuilder();
        for (JeiPreview.Panel panel : JeiPreview.drawnPanels()) {
            text.append(panel.cells()).append(" cells @ layer ").append(panel.layer()).append(" ").append(panel.x())
                    .append(',').append(panel.y()).append(' ').append(panel.width()).append('x').append(panel.height())
                    .append("; ");
        }
        return text.toString();
    }

    private static boolean sameLayer(int layer) {
        List<JeiPreview.Panel> panels = JeiPreview.drawnPanels();
        if (panels.isEmpty()) {
            return false;
        }
        for (JeiPreview.Panel panel : panels) {
            if (panel.layer() != layer) {
                return false;
            }
        }
        return true;
    }

    /** The recipe-local point of one expected cell in one panel, or null when that panel hides it. */
    private static double[] point(NamespacedId blueprintId, int panel, int cellIndex) {
        Optional<JeiPreview.CellPoint> point = JeiPreview.cellPoint(blueprintId, panel, cellIndex);
        return point.map(cell -> new double[]{cell.x(), cell.y()}).orElse(null);
    }

    private static boolean same(double[] left, double[] right) {
        return left != null
                && right != null
                && Math.abs(left[0] - right[0]) < 0.01D
                && Math.abs(left[1] - right[1]) < 0.01D;
    }

    private static boolean inside(JeiPreview.Panel panel, double[] point) {
        return point != null
                && point[0] >= panel.x()
                && point[0] <= panel.x() + panel.width()
                && point[1] >= panel.y()
                && point[1] <= panel.y() + panel.height();
    }

    private static String pointText(NamespacedId blueprintId, int panel) {
        int cells = PreviewJeiProbe.catalogCells(blueprintId);
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < cells; index++) {
            double[] point = point(blueprintId, panel, index);
            text.append(index).append('=');
            if (point == null) {
                text.append('-');
            } else {
                text.append(round(point[0])).append(',').append(round(point[1]));
            }
            text.append(' ');
        }
        return text.toString();
    }

    private static String round(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String hoverText(NamespacedId blueprintId, int panel, int cellIndex) {
        double[] at = point(blueprintId, panel, cellIndex);
        return at == null ? "<no point>" : JeiPreview.hoverText(blueprintId, at[0], at[1]).toString();
    }

    /** The tooltip the formatter owes one cell: its id, its properties in key order, then its part. */
    private static List<String> cellLines(NamespacedId blueprintId, int index, String part) {
        GhostCell cell = PreviewJeiProbe.cell(blueprintId, index);
        List<String> lines = new ArrayList<>();
        lines.add(cell.block().text());
        cell.properties().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(property -> lines.add(property.getKey() + ": " + property.getValue()));
        lines.add("part: " + part);
        return lines;
    }
}
