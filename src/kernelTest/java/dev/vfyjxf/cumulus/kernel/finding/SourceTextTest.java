package dev.vfyjxf.cumulus.kernel.finding;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SourceTextTest {

    private static final NamespacedId DOC = NamespacedId.of("test", "doc");

    @Test
    void crlfAndEmptyLines() {
        SourceText source = SourceText.of(DOC, "first\r\nsecond\n\nlast");
        assertEquals("first", source.lineAt(0));
        assertEquals("first", source.lineAt(5));
        assertEquals("first", source.lineAt(6));
        assertEquals("second", source.lineAt(7));
        assertEquals(2, source.lineNumber(7));
        assertEquals(1, source.column(7));
        assertEquals("", source.lineAt(14));
        assertEquals(3, source.lineNumber(14));
        assertEquals("last", source.lineAt(15));
        assertEquals(4, source.lineNumber(15));
        assertEquals("last", source.lineAt(19));
        assertEquals(4, source.lineNumber(19));
        assertEquals(5, source.column(19));
    }

    @Test
    void noTrailingNewlineAndTrailingNewline() {
        SourceText plain = SourceText.of(DOC, "abc");
        assertEquals("abc", plain.lineAt(0));
        assertEquals("abc", plain.lineAt(3));
        assertEquals(4, plain.column(3));
        assertNull(plain.lineAt(4));

        SourceText trailing = SourceText.of(DOC, "abc\n");
        assertEquals("", trailing.lineAt(4));
        assertEquals(2, trailing.lineNumber(4));
        assertEquals(1, trailing.column(4));
    }

    @Test
    void emptyTextAndOutOfRange() {
        SourceText empty = SourceText.of(DOC, "");
        assertEquals("", empty.lineAt(0));
        assertEquals(1, empty.lineNumber(0));
        assertEquals(1, empty.column(0));
        assertNull(empty.lineAt(1));
        assertThrows(IllegalArgumentException.class, () -> empty.lineNumber(1));
        assertThrows(IllegalArgumentException.class, () -> empty.column(-1));
    }

    @Test
    void bareCarriageReturnIsALineBreak() {
        SourceText source = SourceText.of(DOC, "a\rb");
        assertEquals("a", source.lineAt(1));
        assertEquals("b", source.lineAt(2));
        assertEquals(2, source.lineNumber(2));
    }
}
