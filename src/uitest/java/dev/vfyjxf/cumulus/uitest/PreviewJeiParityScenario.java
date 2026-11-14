package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.mc.preview.client.screen.StageTimeline;
import dev.vfyjxf.cumulus.mc.preview.jei.JeiPreview;
import dev.vfyjxf.cumulus.mc.preview.net.Stage;

import java.util.ArrayList;
import java.util.List;

/**
 * The JEI face's parity (design 19 §5): the compact timeline of the station's ten stages, a segment
 * click showing one stage at a time, the wheel's clamped zoom, a double click's frame-all, and the set
 * page's constraint overlay with the same link strokes and member labels as the screen. Assertions read
 * the face's model (timeline, stage, fit scale, overlay counters), never pixels.
 */
@ClientScenario(
    name = "jei.parity",
    group = "jei",
    tags = {"jei", "preview", "timeline"},
    timeoutMs = 120_000,
    scenarioTimeoutMs = 240_000
)
public final class PreviewJeiParityScenario {

    private static final NamespacedId station = NamespacedId.of("structurecumulus", "demo_station");
    private static final NamespacedId campus = NamespacedId.of("structurecumulus", "demo_campus");
    private static final NamespacedId bay = NamespacedId.of("structurecumulus", "jei_parity/bay");
    private static final int stationCells = 473;

    private static float fitScale;
    private static int stageCells;
    private static int memberStageCells;
    private static boolean firstClick;
    private static boolean secondClick;

    public void define(ScenarioBuilder build) {
        build = PreviewScenarioSupport.stage(build).log(
            "jei.parity: the compact timeline selects one stage, the wheel zooms and clamps, a double click frames, the set overlay draws"
        );

        build.waitUntil(
            "the catalog carries the station and the campus set",
            () -> PreviewJeiProbe.catalogReady()
                    && PreviewJeiProbe.catalogIds().contains(station)
                    && PreviewJeiProbe.setEntry(campus) != null
        ).action("open the Multiblock Preview category through the JEI API", context -> PreviewJeiProbe.openCategory())
                .waitUntil("JEI's recipe screen owns the frame", PreviewJeiProbe::screenOpen)
                .waitUntil("the category holds the synced entries", PreviewJeiProbe::categoryHasRecipes)
                .action("page the category to the station", context -> JeiPreview.show(station)).frames(6)
                .check("the compact timeline is one headless lane of ten segments", () -> {
                    StageTimeline timeline = JeiPreview.timeline(station);
                    return timeline != null
                            && timeline.lanes().size() == 1
                            && timeline.lanes().get(0).head() == null
                            && timeline.segments().size() == 10
                            && timeline.segments().stream().allMatch(segment -> segment.y() == 0)
                            && timeline.segments().stream()
                                    .allMatch(segment -> segment.width() >= StageTimeline.minSegmentWidth)
                            && timeline.height() == 11;
                }, () -> String.valueOf(JeiPreview.timeline(station)))
                .check("the face's drawn labels are the short station names, the wire keeps the full ids", () -> {
                    StageTimeline timeline = JeiPreview.timeline(station);
                    return timeline != null
                            && timeline.segments().get(0).label().equals("plinth")
                            && timeline.segments().stream()
                                    .noneMatch(segment -> segment.label().startsWith("structurecumulus:"))
                            && PreviewJeiProbe.presentation(station).stages().get(0).name()
                                    .equals("structurecumulus:plinth");
                }, () -> String.valueOf(JeiPreview.timeline(station)))
                .check("a segment hover names the whole stage id and its cells", () -> {
                    StageTimeline timeline = JeiPreview.timeline(station);
                    if (timeline == null) {
                        return false;
                    }
                    StageTimeline.Segment segment = timeline.segments().get(5);
                    Stage stage = PreviewJeiProbe.presentation(station).stages().get(5);
                    String expected = stage.name() + " \u00B7 " + stage.expectedCells() + " cells";
                    return expected.equals(
                        JeiPreview.timelineHover(
                            station,
                            segment.x() + segment.width() / 2.0D,
                            segment.y() + segment.height() / 2.0D
                        )
                    );
                }, () -> {
                    StageTimeline timeline = JeiPreview.timeline(station);
                    if (timeline == null) {
                        return "no timeline";
                    }
                    StageTimeline.Segment segment = timeline.segments().get(5);
                    return String.valueOf(
                        JeiPreview.timelineHover(
                            station,
                            segment.x() + segment.width() / 2.0D,
                            segment.y() + segment.height() / 2.0D
                        )
                    );
                }).action("record the fitted scale and the whole page's cells", context -> {
                    fitScale = JeiPreview.drawnPanels().get(0).scale();
                    stageCells = PreviewJeiProbe.presentation(station).stages().get(5).expectedCells();
                }).check("the isometric face draws the whole station before any stage is selected", () -> {
                    List<JeiPreview.Panel> panels = JeiPreview.drawnPanels();
                    return panels.size() == 1
                            && panels.get(0).cells() == stationCells
                            && JeiPreview.drawnCells() == stationCells
                            && JeiPreview.stage(station) == -1
                            && Math.abs(JeiPreview.zoom(station) - 1.0F) < 1.0E-4F;
                }, PreviewJeiParityScenario::panels)
                .action("click the sixth segment of the station's timeline", context -> {
                    StageTimeline timeline = JeiPreview.timeline(station);
                    StageTimeline.Segment segment = timeline.segments().get(5);
                    JeiPreview.clickTimeline(
                        station,
                        segment.x() + segment.width() / 2,
                        segment.y() + segment.height() / 2
                    );
                }).frames(6).check("a segment click shows that stage alone, the JEI face's one-stage reading", () -> {
                    List<JeiPreview.Panel> panels = JeiPreview.drawnPanels();
                    return JeiPreview.stage(station) == 5
                            && panels.size() == 1
                            && panels.get(0).cells() == stageCells
                            && JeiPreview.drawnCells() == stageCells;
                },
                    () -> "stage=" + JeiPreview.stage(station) + " cells=" + JeiPreview.drawnCells() + " expected="
                            + stageCells + " panels=" + panels()
                ).screenshot("jei-timeline").frames(2).checkCaptured("jei-timeline")
                .action("click the same segment again", context -> {
                    StageTimeline timeline = JeiPreview.timeline(station);
                    StageTimeline.Segment segment = timeline.segments().get(5);
                    JeiPreview.clickTimeline(
                        station,
                        segment.x() + segment.width() / 2,
                        segment.y() + segment.height() / 2
                    );
                }).frames(6)
                .check(
                    "a second click on the selected segment returns the whole page",
                    () -> JeiPreview.stage(station) == -1 && JeiPreview.drawnCells() == stationCells
                ).action("turn the wheel two notches over the scene", context -> JeiPreview.zoomBy(station, 2.0F))
                .frames(6).check("the wheel's zoom scales the fit and stays the wheel's factor", () -> {
                    float zoom = JeiPreview.zoom(station);
                    float scale = JeiPreview.drawnPanels().get(0).scale();
                    return Math.abs(zoom - 1.21F) < 1.0E-3F
                            && Math.abs(scale - fitScale * zoom) < 0.01F
                            && scale > fitScale;
                },
                    () -> "zoom=" + JeiPreview.zoom(station) + " scale=" + JeiPreview.drawnPanels().get(0).scale()
                            + " fit=" + fitScale
                ).action("turn the wheel far past the ceiling", context -> JeiPreview.zoomBy(station, 100.0F)).frames(6)
                .check("the zoom clamps at the camera's 8x ceiling", () -> {
                    float scale = JeiPreview.drawnPanels().get(0).scale();
                    return JeiPreview.zoom(station) == 8.0F && Math.abs(scale - fitScale * 8.0F) < 0.05F;
                }, () -> "zoom=" + JeiPreview.zoom(station) + " scale=" + JeiPreview.drawnPanels().get(0).scale())
                .action("turn the wheel far past the floor", context -> JeiPreview.zoomBy(station, -100.0F)).frames(6)
                .check("the zoom clamps at the camera's 0.25x floor", () -> {
                    float scale = JeiPreview.drawnPanels().get(0).scale();
                    return JeiPreview.zoom(station) == 0.25F && Math.abs(scale - fitScale * 0.25F) < 0.01F;
                }, () -> "zoom=" + JeiPreview.zoom(station) + " scale=" + JeiPreview.drawnPanels().get(0).scale())
                .action("double click the scene", context -> {
                    firstClick = JeiPreview.clickScene(station);
                    secondClick = JeiPreview.clickScene(station);
                }).frames(6)
                .check("a double click frames the drawn hull: the fit is back to one, the scene whole", () -> {
                    float scale = JeiPreview.drawnPanels().get(0).scale();
                    return !firstClick
                            && secondClick
                            && JeiPreview.zoom(station) == 1.0F
                            && Math.abs(scale - fitScale) < 0.01F;
                },
                    () -> "first=" + firstClick + " second=" + secondClick + " zoom=" + JeiPreview.zoom(station)
                            + " scale=" + JeiPreview.drawnPanels().get(0).scale() + " fit=" + fitScale
                ).check("a compact lane wraps at the face's own width instead of shrinking below the floor", () -> {
                    List<Stage> stages = new ArrayList<>();
                    for (int index = 0; index < 20; index++) {
                        stages.add(new Stage("structurecumulus:part" + index, 1, 0, null, null, bay));
                    }
                    StageTimeline compact = StageTimeline.of(
                        stages,
                        172,
                        new StageTimeline.Metrics(11, 2, 1, 8, 96, 8, 1, 38, name -> name.length() * 5),
                        StageTimeline.Labels.of(NamespacedId.of("structurecumulus", "station"))
                    );
                    return compact.lanes().size() == 1
                            && compact.lanes().get(0).rows() > 1
                            && compact.segments().size() == 20
                            && compact.segments().stream()
                                    .allMatch(segment -> segment.width() >= StageTimeline.minSegmentWidth);
                }, () -> "wrap=compact")
                .action("page the category to the campus set", context -> JeiPreview.show(campus)).frames(8)
                .check("the set page's timeline keeps its three member lanes, short heads and 17 segments", () -> {
                    StageTimeline timeline = JeiPreview.timeline(campus);
                    return timeline != null
                            && timeline.lanes().size() == 3
                            && timeline.segments().size() == 17
                            && timeline.lanes().stream().map(lane -> lane.head().label()).toList()
                                    .equals(List.of("root", "campus_machine", "campus_tower"))
                            && JeiPreview.stage(campus) == -1;
                }, () -> String.valueOf(JeiPreview.timeline(campus)))
                .check("a lane head's hover still answers with the member's whole id", () -> {
                    StageTimeline timeline = JeiPreview.timeline(campus);
                    StageTimeline.Lane lane = timeline.lanes().get(1);
                    StageTimeline.Lane.Head head = lane.head();
                    return head.group().equals("structurecumulus:campus_machine")
                            && head.group().equals(
                                JeiPreview
                                        .timelineHover(campus, head.width() / 2.0D, lane.y() + lane.rowHeight() / 2.0D)
                            );
                }, () -> {
                    StageTimeline timeline = JeiPreview.timeline(campus);
                    StageTimeline.Lane lane = timeline.lanes().get(1);
                    StageTimeline.Lane.Head head = lane.head();
                    return String.valueOf(
                        JeiPreview.timelineHover(campus, head.width() / 2.0D, lane.y() + lane.rowHeight() / 2.0D)
                    );
                }).check("the set page draws the screen's link strokes and member labels, on by default", () -> {
                    List<JeiPreview.Panel> panels = JeiPreview.drawnPanels();
                    return JeiPreview.constraints(campus)
                            && panels.size() == 1
                            && panels.get(0).cells() == 701
                            && JeiPreview.drawnLinks() > 0
                            && JeiPreview.drawnMemberLabels() == 3;
                },
                    () -> "constraints=" + JeiPreview.constraints(campus) + " links=" + JeiPreview.drawnLinks()
                            + " probes=" + JeiPreview.drawnProbes() + " labels=" + JeiPreview.drawnMemberLabels()
                ).screenshot("jei-set-links").frames(2).checkCaptured("jei-set-links")
                .action("press the constraints toggle", context -> JeiPreview.toggleConstraints(campus)).frames(6)
                .check(
                    "the toggle clears every link stroke and member label",
                    () -> !JeiPreview.constraints(campus)
                            && JeiPreview.drawnLinks() == 0
                            && JeiPreview.drawnMemberLabels() == 0
                ).action("press the constraints toggle again", context -> JeiPreview.toggleConstraints(campus))
                .frames(6)
                .check(
                    "the overlay comes back on with the same link strokes",
                    () -> JeiPreview.constraints(campus) && JeiPreview.drawnLinks() > 0
                ).action("record the member stage's own cell count", context -> {
                    memberStageCells = PreviewJeiProbe.setEntry(campus).stages().get(10).expectedCells();
                }).check("a set page's segment click still shows one member stage alone", () -> {
                    StageTimeline timeline = JeiPreview.timeline(campus);
                    StageTimeline.Segment segment = timeline.segments().get(10);
                    JeiPreview.clickTimeline(
                        campus,
                        segment.x() + segment.width() / 2,
                        segment.y() + segment.height() / 2
                    );
                    return JeiPreview.stage(campus) == 10;
                }).frames(6)
                .check(
                    "the selected member stage draws that stage's own cells",
                    () -> JeiPreview.drawnCells() == memberStageCells,
                    () -> "stage=" + JeiPreview.stage(campus) + " cells=" + JeiPreview.drawnCells() + " expected="
                            + memberStageCells
                )
                .timelineRecord(
                    "jei-parity",
                    () -> "station=" + stationCells + ", stage=" + JeiPreview.stage(campus) + ", fit=" + fitScale
                            + ", links=" + JeiPreview.drawnLinks()
                ).closeScreen();

        PreviewScenarioSupport.cleanup(build);
    }

    private static String panels() {
        StringBuilder text = new StringBuilder();
        for (JeiPreview.Panel panel : JeiPreview.drawnPanels()) {
            text.append(panel.cells()).append(" cells ").append(panel.width()).append('x').append(panel.height())
                    .append(" scale=").append(panel.scale()).append("; ");
        }
        return text.toString();
    }
}
