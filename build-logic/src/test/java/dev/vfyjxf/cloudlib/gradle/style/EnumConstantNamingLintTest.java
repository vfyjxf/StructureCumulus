package dev.vfyjxf.cloudlib.gradle.style;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The enum half of the constant naming rule. The cases that matter are the
 * ones a line-oriented scan gets wrong: a whole constant list on one line, an
 * annotated constant, and a constant with a body.
 */
class EnumConstantNamingLintTest {

    @Test
    void acceptsLowerCamelConstantsOnOneLine() {
        assertDoesNotThrow(() -> check("""
                enum Facing {
                    down, up, north, south, west, east;
                }
                """));
    }

    @Test
    void reportsUpperSnakeConstants() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> check("""
                enum Facing {
                    DOWN,
                    UP;
                }
                """));
        assertTrue(error.getMessage().contains("'DOWN'"), error.getMessage());
        assertTrue(error.getMessage().contains("'UP'"), error.getMessage());
    }

    @Test
    void checksEveryConstantOfAOneLineList() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> check("enum Facing { down, UP; }"));
        assertTrue(error.getMessage().contains("'UP'"), error.getMessage());
        assertFalse(error.getMessage().contains("'down'"), error.getMessage());
    }

    @Test
    void reportsUnderscoresEvenWhenTheRestIsLowercase() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> check("""
                enum Corner {
                    TOP_LEFT,
                    top_right;
                }
                """));
        assertTrue(error.getMessage().contains("'TOP_LEFT'"), error.getMessage());
        assertTrue(error.getMessage().contains("'top_right'"), error.getMessage());
    }

    @Test
    void acceptsAnnotationsAndConstantBodies() {
        assertDoesNotThrow(() -> check("""
                enum State {
                    @Deprecated
                    old,
                    fresh {
                        void run() {
                        }
                    };
                }
                """));
    }

    @Test
    void ignoresEnumTextInCommentsAndLiterals() {
        assertDoesNotThrow(() -> check("""
                // enum Bad { UPPER; }
                /* enum Worse { OTHER; } */
                class Fine {
                    String text = "enum Bad { UPPER; }";
                }
                """));
    }

    private static String check(String source) {
        return EnumConstantNamingLint.check(source, "Test.java");
    }
}
