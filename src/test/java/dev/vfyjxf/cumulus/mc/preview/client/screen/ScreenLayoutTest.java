package dev.vfyjxf.cumulus.mc.preview.client.screen;

import dev.vfyjxf.cumulus.mc.preview.client.screen.ScreenLayout.Rect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The four-viewport layout of design 17 §5.1: the constants, the split and the honest collapse. */
class ScreenLayoutTest {

    @Test
    void theConstantsArePinned() {
        assertEquals(20, ScreenLayout.titleHeight);
        assertEquals(56, ScreenLayout.controlHeight);
        assertEquals(4, ScreenLayout.panelGap);
        assertEquals(0.72F, ScreenLayout.mainFraction, 1.0e-6F);
    }

    @Test
    void aRegularScreenSplitsIntoTheMainAndTheSideColumn() {
        ScreenLayout layout = ScreenLayout.of(320, 240);
        assertEquals(new Rect(0, 0, 320, 20), layout.title());
        assertEquals(new Rect(0, 184, 320, 56), layout.control());
        assertEquals(new Rect(4, 24, 224, 156), layout.main());
        assertEquals(new Rect(232, 24, 84, 49), layout.front());
        assertEquals(new Rect(232, 77, 84, 49), layout.side());
        assertEquals(new Rect(232, 130, 84, 49), layout.top());
    }

    @Test
    void theStripsAndTheGapsLeaveTheViewportArea() {
        ScreenLayout layout = ScreenLayout.of(320, 240);
        Rect main = layout.main();
        assertEquals(ScreenLayout.titleHeight + ScreenLayout.panelGap, main.y());
        assertEquals(240 - ScreenLayout.controlHeight - ScreenLayout.panelGap, main.y() + main.height());
        assertEquals(ScreenLayout.panelGap, main.x());
    }

    @Test
    void theTimelinesOwnHeightSetsTheControlStripAndThePanelsTakeTheRest() {
        ScreenLayout tall = ScreenLayout.of(320, 240, 84);
        assertEquals(new Rect(0, 156, 320, 84), tall.control());
        assertEquals(new Rect(4, 24, 224, 128), tall.main());
        assertEquals(new Rect(232, 24, 84, 40), tall.front());
        assertEquals(152, tall.main().y() + tall.main().height());
        assertTrue(tall.main().height() < ScreenLayout.of(320, 240).main().height());
    }

    @Test
    void aNarrowScreenDropsTheSideColumnAndTheMainKeepsItsShare() {
        ScreenLayout layout = ScreenLayout.of(20, 240);
        assertNull(layout.front());
        assertNull(layout.side());
        assertNull(layout.top());
        assertEquals(new Rect(4, 24, 8, 156), layout.main());
    }

    @Test
    void aShortScreenCollapsesEveryViewportHonestly() {
        ScreenLayout layout = ScreenLayout.of(320, 60);
        assertTrue(layout.main().isEmpty());
        assertNull(layout.front());
        Rect title = layout.title();
        assertEquals(20, title.height());
        Rect control = layout.control();
        assertEquals(4, control.y());
        assertEquals(56, control.height());
    }

    @Test
    void noTwoViewportsOverlapAndNoneIsZeroAreaWhileItExists() {
        for (int width = 40; width <= 900; width += 20) {
            for (int height = 40; height <= 700; height += 20) {
                ScreenLayout layout = ScreenLayout.of(width, height);
                Rect[] viewports = {layout.main(), layout.front(), layout.side(), layout.top()};
                for (int first = 0; first < viewports.length; first++) {
                    for (int second = first + 1; second < viewports.length; second++) {
                        Rect left = viewports[first];
                        Rect right = viewports[second];
                        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
                            continue;
                        }
                        String where = width + "x" + height + ": " + left + " and " + right;
                        assertFalse(overlaps(left, right), where);
                    }
                }
                for (Rect viewport : viewports) {
                    if (viewport == null) {
                        continue;
                    }
                    if (!viewport.isEmpty()) {
                        assertTrue(viewport.x() >= 0 && viewport.y() >= 0);
                        assertTrue(viewport.x() + viewport.width() <= width);
                    }
                }
            }
        }
    }

    private static boolean overlaps(Rect left, Rect right) {
        return left.x() < right.x() + right.width()
                && right.x() < left.x() + left.width()
                && left.y() < right.y() + right.height()
                && right.y() < left.y() + left.height();
    }
}
