package dev.vfyjxf.cumulus.mc.preview.client.screen;

import org.jspecify.annotations.Nullable;

/**
 * The screen's rectangles (design 17 §5.1): the title strip, the main viewport, the three axis-locked
 * panels and the control strip. A pure function of the screen size and the control strip's own height,
 * which the timeline sets for the frame (design 19 §3); the small panels collapse to nothing when their
 * column has no room and come back when it does.
 */
public record ScreenLayout(
    Rect title,
    Rect main,
    @Nullable Rect front,
    @Nullable Rect side,
    @Nullable Rect top,
    Rect control
) {

    public static final int titleHeight = 20;
    public static final int controlHeight = 56;
    public static final int panelGap = 4;
    public static final float mainFraction = 0.72F;

    /** One rectangle in GUI pixels; an empty one is never drawn. */
    public record Rect(int x, int y, int width, int height) {

        public static final Rect empty = new Rect(0, 0, 0, 0);

        public boolean isEmpty() {
            return width <= 0 || height <= 0;
        }
    }

    public static ScreenLayout of(int width, int height) {
        return of(width, height, controlHeight);
    }

    /** The layout with the control strip's frame height: the timeline grows, the panels take the rest. */
    public static ScreenLayout of(int width, int height, int controlHeight) {
        int strip = Math.max(0, controlHeight);
        Rect title = new Rect(0, 0, Math.max(0, width), Math.min(titleHeight, Math.max(0, height)));
        Rect control = new Rect(
            0,
            Math.max(0, height - strip),
            Math.max(0, width),
            Math.min(strip, Math.max(0, height))
        );
        int viewportX = panelGap;
        int viewportY = titleHeight + panelGap;
        int viewportWidth = width - 2 * panelGap;
        int viewportHeight = height - titleHeight - strip - 2 * panelGap;
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            return new ScreenLayout(title, Rect.empty, null, null, null, control);
        }
        int mainWidth = (int) (viewportWidth * mainFraction);
        int sideWidth = viewportWidth - mainWidth - panelGap;
        int panelHeight = (viewportHeight - 2 * panelGap) / 3;
        Rect main = new Rect(viewportX, viewportY, mainWidth, viewportHeight);
        if (sideWidth <= 0 || panelHeight <= 0) {
            return new ScreenLayout(title, main, null, null, null, control);
        }
        int sideX = viewportX + mainWidth + panelGap;
        Rect front = new Rect(sideX, viewportY, sideWidth, panelHeight);
        Rect side = new Rect(sideX, viewportY + panelHeight + panelGap, sideWidth, panelHeight);
        Rect top = new Rect(sideX, viewportY + 2 * (panelHeight + panelGap), sideWidth, panelHeight);
        return new ScreenLayout(title, main, front, side, top, control);
    }
}
