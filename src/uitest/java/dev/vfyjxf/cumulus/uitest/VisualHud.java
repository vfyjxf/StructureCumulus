package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.SceneContext;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.base.host.GlobalSceneHost;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyles;
import dev.vfyjxf.cloudlib.ui.widget.BoxWidget;
import dev.vfyjxf.cloudlib.ui.widget.ColumnWidget;
import dev.vfyjxf.cloudlib.ui.widget.LabelWidget;
import dev.vfyjxf.cloudlib.ui.widget.RowWidget;
import dev.vfyjxf.cloudlib.ui.widget.SectionHeaderWidget;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import java.util.List;

/**
 * The dev-only diagnostic HUD: a CloudLib {@link Scene} drawn twice-scaled from a
 * {@link RegisterGuiLayersEvent} layer. It renders the structured {@link VisualHudModel} as a
 * two-column card (summary left, selection/context right) and a compact single-column card, so it
 * stays readable at 2560x1440 with guiScale 2. It never writes to chat.
 */
public final class VisualHud {

    /** The card is drawn at this scale; the layout area is divided by it. */
    private static final float hudScale = 1.5F;
    private static final int titleColor = 0xFFFFFF;
    private static final int textColor = 0xE0E0E0;
    private static final int noteColor = 0xA8A8A8;
    private static final int selectedColor = 0xFFE080;
    private static final int conflictColor = 0xFF7070;

    private static final ResourceLocation layerId = ResourceLocation
            .fromNamespaceAndPath(UiTestMod.modId, "visual_hud");

    private static Scene scene;
    private static VisualHudModel builtModel = VisualHudModel.empty();
    private static boolean layerRegistered;

    private VisualHud() {}

    public static void register(RegisterGuiLayersEvent event) {
        if (layerRegistered) {
            return;
        }
        layerRegistered = true;
        event.registerAboveAll(layerId, VisualHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!VisualClientState.hudEnabled()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        VisualHudModel model = VisualProbe.hudModel();
        if (model.summaryLines().isEmpty()) {
            return;
        }
        if (!model.equals(builtModel)) {
            rebuild(model);
        }
        Scene current = scene;
        if (current == null) {
            return;
        }
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        current.setLayoutArea(Math.max(1, Math.round(width / hudScale)), Math.max(1, Math.round(height / hudScale)));
        current.layout();
        current.root().applyLayout();
        graphics.pose().pushPose();
        graphics.pose().scale(hudScale, hudScale, 1.0F);
        current.render(graphics, 0, 0, deltaTracker.getGameTimeDeltaPartialTick(false));
        graphics.pose().popPose();
        VisualClientState.markHudRendered();
    }

    private static void rebuild(VisualHudModel model) {
        Scene previous = scene;
        if (previous != null) {
            previous.destroy();
        }
        WidgetGroup<Widget> root = new WidgetGroup<>();
        root.useStyle(
            UIStyle.of(UIStyles.flexColumn(), UIStyles.sizeFull(), UIStyles.alignItemsFlexEnd(), UIStyles.padding(6f))
        );
        BoxWidget card = BoxWidget.create();
        card.useStyle(
            UIStyle.of(
                UIStyles.backgroundColor(0xE0121418),
                UIStyles.padding(6f),
                UIStyles.maxWidth(520f),
                UIStyles.maxHeight(560f)
            )
        );
        ColumnWidget cardBody = ColumnWidget.create(4);
        cardBody.addWidget(LabelWidget.of(model.title()).setColor(titleColor).setShadow(false));
        if (model.compact()) {
            ColumnWidget column = ColumnWidget.create(2);
            column.useStyle(UIStyles.widthOf(380f));
            column.addWidget(SectionHeaderWidget.of("概要（紧凑）"));
            addLines(column, model.summaryLines());
            for (String flowLine : model.flowLines()) {
                column.addWidget(line(flowLine, noteColor));
            }
            if (!model.detailLines().isEmpty()) {
                column.addWidget(line(model.detailLines().get(0), selectedColor));
            }
            cardBody.addWidget(column);
        } else {
            RowWidget columns = RowWidget.create(8);
            ColumnWidget left = ColumnWidget.create(2);
            left.useStyle(UIStyles.widthOf(225f));
            left.addWidget(SectionHeaderWidget.of("概要、声明与高亮"));
            addLines(left, model.summaryLines());
            if (!model.groupLines().isEmpty()) {
                left.addWidget(SectionHeaderWidget.of("分组"));
                addLines(left, model.groupLines());
            }
            for (String legend : model.legendLines()) {
                left.addWidget(line(legend, noteColor));
            }
            ColumnWidget right = ColumnWidget.create(2);
            right.useStyle(UIStyles.widthOf(225f));
            right.addWidget(SectionHeaderWidget.of("选中与上下文"));
            addLines(right, model.detailLines());
            if (!model.hierarchyLines().isEmpty()) {
                right.addWidget(SectionHeaderWidget.of("层级"));
                addLines(right, model.hierarchyLines());
            }
            columns.addWidget(left);
            columns.addWidget(right);
            cardBody.addWidget(columns);
            addFlowStrip(cardBody, model);
        }
        card.addChild(cardBody);
        root.addWidget(card);
        if (!model.inspectLines().isEmpty()) {
            BoxWidget inspectCard = BoxWidget.create();
            inspectCard.useStyle(
                UIStyle.of(UIStyles.backgroundColor(0xE0241018), UIStyles.padding(6f), UIStyles.maxWidth(520f))
            );
            ColumnWidget inspectBody = ColumnWidget.create(2);
            inspectBody.addWidget(LabelWidget.of("检视部件").setColor(selectedColor).setShadow(false));
            addLines(inspectBody, model.inspectLines());
            inspectCard.addChild(inspectBody);
            root.addWidget(inspectCard);
        }
        Scene next = new Scene(root);
        next.init();
        next.mount(SceneContext.create(new GlobalSceneHost()));
        Minecraft minecraft = Minecraft.getInstance();
        next.setLayoutArea(
            Math.max(1, Math.round(minecraft.getWindow().getGuiScaledWidth() / hudScale)),
            Math.max(1, Math.round(minecraft.getWindow().getGuiScaledHeight() / hudScale))
        );
        next.stabilize();
        next.layout();
        next.root().applyLayout();
        scene = next;
        builtModel = model;
    }

    private static void addFlowStrip(ColumnWidget cardBody, VisualHudModel model) {
        VisualFlowModel flow = VisualProbe.flow();
        if (flow == null || model.flowLines().isEmpty()) {
            return;
        }
        cardBody.addWidget(SectionHeaderWidget.of("流程"));
        RowWidget strip = RowWidget.create(3);
        for (VisualFlowModel.Phase phase : VisualFlowModel.Phase.values()) {
            String marker = phase.equals(flow.current()) ? "[>]" : flow.completed().contains(phase) ? "[x]" : "[ ]";
            strip.addWidget(
                LabelWidget.of(marker + " " + phase.displayName()).setColor(phaseColor(phase)).setShadow(false)
            );
        }
        cardBody.addWidget(strip);
        for (int index = 1; index < model.flowLines().size(); index++) {
            cardBody.addWidget(line(model.flowLines().get(index), noteColor));
        }
    }

    private static int phaseColor(VisualFlowModel.Phase phase) {
        return switch (phase) {
            case draft -> 0xFFD080;
            case check -> 0xFFFFFF;
            case settle -> 0x80B0FF;
            case formed -> 0x80FFFF;
            case claims -> 0x80FFFF;
            case context -> noteColor;
            case capture -> 0xFFFFFF;
        };
    }

    private static void addLines(ColumnWidget column, List<String> lines) {
        for (String text : lines) {
            column.addWidget(line(text, colorOf(text)));
        }
    }

    private static LabelWidget line(String text, int color) {
        return LabelWidget.of(text).setColor(color).setShadow(false);
    }

    private static int colorOf(String line) {
        if (line.startsWith("冲突: ")) {
            return line.startsWith("冲突: 0 ") ? noteColor : conflictColor;
        }
        if (line.startsWith("选中: ")) {
            return selectedColor;
        }
        if (line.startsWith("错误: ")) {
            return conflictColor;
        }
        if (line.startsWith("图例: ")
                || line.startsWith("错误码: ")
                || line.startsWith("事件: ")
                || line.startsWith("上下文键: ")) {
            return noteColor;
        }
        return textColor;
    }
}
