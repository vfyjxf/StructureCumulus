package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cloudlib.uitest.api.TestContext;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.mc.preview.client.screen.PreviewScreen;
import dev.vfyjxf.cumulus.mc.preview.client.screen.ScreenLayout;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;
import dev.vfyjxf.cumulus.mc.preview.net.Stage;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.ClientCommandHandler;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The set page end to end (design 18 §4–§7): the campus composite scene, four viewports of composed
 * cells, the member stage table, the constraint overlay's default and toggle, the member drill-down
 * and its breadcrumb, and the {@code <id>#set} command form. Assertions read the model, never pixels.
 */
@ClientScenario(
    name = "preview.campus.demo",
    group = "demo",
    tags = {"preview", "demo", "set"},
    timeoutMs = 120_000,
    scenarioTimeoutMs = 240_000
)
public final class PreviewCampusScenario {

    private static final NamespacedId campus = NamespacedId.of("structurecumulus", "demo_campus");
    private static final NamespacedId machine = NamespacedId.of("structurecumulus", "demo_machine");
    private static final NamespacedId stagedBlueprint = NamespacedId.of("structurecumulus", "preview_stages");
    /** root 473 + machine 100 + tower 128, all drawn in each of the four viewports. */
    private static final int composedCells = 701;
    private static final float[] camera = new float[2];
    private static final float[] memberPoint = new float[2];

    public void define(ScenarioBuilder build) {
        build = PreviewScenarioSupport.stage(build).runCommand("cumulusvisual hud hide").runCommand(
            "cumulusvisual tutorial hide"
        ).action(
            "park the previous act's command output",
            context -> Minecraft.getInstance().gui.getChat().clearMessages(true)
        ).log("preview.campus.demo: the campus set page - the composite scene, the constraint overlay, the drill-down")
                .waitUntil("the catalogue carries the campus set", () -> PreviewJeiProbe.setEntry(campus) != null)
                .check("the set entry composes its three members, root first", () -> {
                    var set = PreviewJeiProbe.setEntry(campus);
                    return set != null
                            && set.members().size() == 3
                            && "root".equals(set.members().get(0).label())
                            && set.members().stream().mapToInt(member -> member.cells().size()).sum() == composedCells
                            && set.members().stream().mapToInt(member -> member.links().size()).sum() == 2;
                }).check("opening the page creates no world session before it", () -> !PreviewProbe.hasSession())
                .openScreen(() -> {
                    PreviewScreen.open(campus);
                    return Minecraft.getInstance().screen;
                }).awaitScreen(PreviewScreen.class).frames(3)
                .check(
                    "the campus opens as a set page with the three members as lanes",
                    () -> screen().isSetPage()
                            && screen().pageId().equals(campus)
                            && screen().timeline().lanes().size() == 3
                            && screen().timeline().segments().size() == 17
                            && screen().explode().groups().size() == 3
                            && screen().canExplode(),
                    () -> "set=" + screen().isSetPage() + " lanes=" + screen().timeline().lanes().size() + " stages="
                            + screen().timeline().segments().size()
                ).check(
                    "the composite scene draws every composed cell in all four viewports",
                    () -> screen().drawnCells() == 4 * composedCells,
                    () -> Integer.toString(screen().drawnCells())
                ).check(
                    "the constraints overlay is on by default and draws both links",
                    () -> screen().constraintsOn() && screen().drawnLinkStrokes() >= 8,
                    () -> "constraints=" + screen().constraintsOn() + " strokes=" + screen().drawnLinkStrokes()
                ).check(
                    "the page says the world question is set check's",
                    () -> screen().footerLine() != null && screen().footerLine().contains("set check")
                )
                .check(
                    "the timeline's lanes are the members in build order, each holding its own stages",
                    () -> screen().timeline().lanes().stream().map(lane -> lane.group()).toList()
                            .equals(List.of("root", "structurecumulus:campus_machine", "structurecumulus:campus_tower"))
                            && screen().timeline().lanes().stream().map(lane -> lane.stageCount()).toList()
                                    .equals(List.of(10, 4, 3))
                            && screen().playback().stages().stream().map(stage -> stage.name()).toList().get(10)
                                    .equals("structurecumulus:campus_machine \u00B7 structurecumulus:plinth"),
                    () -> String.valueOf(screen().timeline())
                ).screenshot("01-campus-set").checkCaptured("01-campus-set")
                .action("click the constraints button off", context -> clickLabel(context, "constraints")).frames(2)
                .check(
                    "the overlay's toggle clears every link stroke",
                    () -> !screen().constraintsOn() && screen().drawnLinkStrokes() == 0
                ).action("click the constraints button on", context -> clickLabel(context, "constraints")).frames(2)
                .check("the overlay comes back on", () -> screen().constraintsOn() && screen().drawnLinkStrokes() >= 8)
                .action("click the engine member's first stage segment", context -> {
                    int machineStage = machineStage();
                    var segment = screen().segmentRect(machineStage);
                    PreviewScreen open = (PreviewScreen) context.screenOrThrow();
                    float x = segment.x() + segment.width() / 2.0F;
                    float y = segment.y() + segment.height() / 2.0F;
                    context.driver().mouseDown(open, x, y, 0);
                    context.driver().mouseUp(open, x, y, 0);
                }).frames(2).check("the cursor reads the stage's own next-step line, grouped under its member", () -> {
                    int machineStage = machineStage();
                    String line = screen().summaryLine();
                    return screen().playback().cursor() == machineStage
                            && line != null
                            && line.startsWith("place ")
                            && line.contains(
                                "(stage " + (machineStage + 1) + "/17: "
                                        + screen().playback().stages().get(machineStage).name() + ")"
                            );
                }, () -> screen().summaryLine())
                .action("capture the camera and the stage before the drill", context -> {
                    camera[0] = screen().camera().yaw();
                    camera[1] = screen().camera().zoom();
                }).frames(2)
                .action("find the engine member's cell under the main camera", context -> memberPoint(memberPoint))
                .frames(1).action("click that member's cell", context -> {
                    PreviewScreen open = (PreviewScreen) context.screenOrThrow();
                    open.mouseClicked(memberPoint[0], memberPoint[1], 0);
                    open.mouseReleased(memberPoint[0], memberPoint[1], 0);
                })
                .waitUntil(
                    "the drill-down opens the member's own page",
                    () -> Minecraft.getInstance().screen instanceof PreviewScreen
                            && !((PreviewScreen) Minecraft.getInstance().screen).isSetPage()
                ).frames(2)
                .check(
                    "the member page is the machine's, with the set breadcrumb",
                    () -> screen().pageId().equals(machine)
                            && screen().returnState() != null
                            && screen().returnState().setId().equals(campus)
                            && screen().buttonLabels().contains("\u2039 " + campus.text()),
                    () -> "page=" + screen().pageId() + " buttons=" + screen().buttonLabels()
                ).screenshot("02-campus-member").checkCaptured("02-campus-member")
                .action("click the breadcrumb", context -> clickLabel(context, "\u2039 " + campus.text())).frames(3)
                .check(
                    "the breadcrumb returns to the set page with camera and stage kept",
                    () -> screen().isSetPage()
                            && screen().pageId().equals(campus)
                            && screen().camera().yaw() == camera[0]
                            && screen().camera().zoom() == camera[1]
                            && screen().playback().cursor() == machineStage()
                            && screen().constraintsOn(),
                    () -> "set=" + screen().isSetPage() + " yaw=" + screen().camera().yaw() + "/" + camera[0]
                            + " cursor=" + screen().playback().cursor()
                ).screenshot("03-campus-return").checkCaptured("03-campus-return")
                .action("Esc closes the set page", PreviewCampusScenario::escape).frames(2)
                .waitUntil("the set page closed", () -> Minecraft.getInstance().screen == null)
                .action(
                    "type the qualified set command as a chat command",
                    context -> Minecraft.getInstance().getConnection()
                            .sendCommand("cumulus preview screen " + campus.text() + "#set")
                )
                .waitUntil(
                    "the qualified command opens the set page",
                    () -> Minecraft.getInstance().screen instanceof PreviewScreen
                            && ((PreviewScreen) Minecraft.getInstance().screen).isSetPage()
                ).frames(2).check("the #set command opens the campus set", () -> screen().pageId().equals(campus))
                .action("Esc closes the qualified page", PreviewCampusScenario::escape).frames(2)
                .waitUntil("the qualified page closed", () -> Minecraft.getInstance().screen == null);

        // blueprint priority: a blueprint and a set share this id, so the bare form is the blueprint
        build = build.action("run the bare and the qualified commands on the shared id", context -> {
            PreviewCampusScenario.bareBlueprintOpened = ClientCommandHandler
                    .runCommand("cumulus preview screen " + stagedBlueprint.text());
            PreviewCampusScenario.bareWasBlueprint = Minecraft.getInstance().screen instanceof PreviewScreen
                    && !((PreviewScreen) Minecraft.getInstance().screen).isSetPage()
                    && ((PreviewScreen) Minecraft.getInstance().screen).pageId().equals(stagedBlueprint);
            PreviewCampusScenario.qualifiedOpened = execute(
                "cumulus preview screen " + stagedBlueprint.text() + "#set"
            );
            PreviewCampusScenario.qualifiedIsSet = Minecraft.getInstance().screen instanceof PreviewScreen
                    && ((PreviewScreen) Minecraft.getInstance().screen).isSetPage();
        }).frames(2)
                .check(
                    "a shared id resolves the blueprint, and <id>#set reaches the set",
                    () -> bareBlueprintOpened && bareWasBlueprint && qualifiedOpened == 1 && qualifiedIsSet,
                    () -> "bare=" + bareBlueprintOpened + " blueprint=" + bareWasBlueprint + " qualified="
                            + qualifiedOpened + " set=" + qualifiedIsSet
                ).check("opening a set page creates no world session", () -> !PreviewProbe.hasSession())
                .action("Esc closes the shared-id page", PreviewCampusScenario::escape).frames(2).timelineRecord(
                    "preview-campus-demo",
                    () -> "members=3, composed=" + composedCells + ", session=" + PreviewProbe.hasSession()
                );
        PreviewScenarioSupport.cleanup(build).runCommand("cumulusvisual hud show");
    }

    private static boolean bareBlueprintOpened;
    private static boolean bareWasBlueprint;
    private static int qualifiedOpened;
    private static boolean qualifiedIsSet;

    /** Clicks a control-strip button by its label; the rect comes from the screen's own layout. */
    private static void clickLabel(TestContext context, String label) {
        PreviewScreen open = (PreviewScreen) context.screenOrThrow();
        ScreenLayout.Rect rect = open.buttonRect(label);
        if (rect == null) {
            throw new IllegalStateException("no " + label + " button in " + open.buttonLabels());
        }
        context.driver().mouseDown(open, rect.x() + rect.width() / 2, rect.y() + rect.height() / 2, 0);
        context.driver().mouseUp(open, rect.x() + rect.width() / 2, rect.y() + rect.height() / 2, 0);
    }

    /** The engine member's first stage in the flattened table (the station's ten stages come first). */
    private static int machineStage() {
        List<Stage> stages = screen().playback().stages();
        for (int index = 0; index < stages.size(); index++) {
            if (stages.get(index).group() != null && stages.get(index).group().endsWith("campus_machine")) {
                return index;
            }
        }
        throw new IllegalStateException("the campus page lost the engine member's stages");
    }

    /** The first engine-member cell whose projection the main viewport answers with that member. */
    private static void memberPoint(float[] out) {
        PreviewScreen open = screen();
        var member = open.setEntry() == null ? null : open.setEntry().members().get(1);
        if (member == null) {
            throw new IllegalStateException("the campus page lost its engine member");
        }
        for (GhostCell cell : member.cells()) {
            Vector2f point = open.cellPoint(0, cell.cell());
            if (point == null) {
                continue;
            }
            NamespacedId under = open.memberUnder(0, point.x, point.y);
            if (machine.equals(under)) {
                out[0] = point.x;
                out[1] = point.y;
                return;
            }
        }
        throw new IllegalStateException("no engine-member cell is visible from the main viewport");
    }

    private static int execute(String command) {
        try {
            return ClientCommandHandler.getDispatcher().execute(command, ClientCommandHandler.getSource());
        } catch (Exception failure) {
            return -1;
        }
    }

    private static void escape(TestContext context) {
        var open = context.screenOrThrow();
        context.driver().keyDown(open, GLFW.GLFW_KEY_ESCAPE, 0, 0);
        context.driver().keyUp(open, GLFW.GLFW_KEY_ESCAPE, 0, 0);
    }

    private static PreviewScreen screen() {
        return (PreviewScreen) Minecraft.getInstance().screen;
    }
}
