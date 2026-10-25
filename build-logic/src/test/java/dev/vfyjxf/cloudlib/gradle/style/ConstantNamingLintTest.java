package dev.vfyjxf.cloudlib.gradle.style;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The field half of the constant naming rule.
 */
class ConstantNamingLintTest {

    @Test
    void acceptsLowerCamelStaticFinalFields() {
        assertDoesNotThrow(() -> ConstantNamingLint.check("""
                class Constants {
                    public static final String modId = "cumulus";
                    static final int maxWidth = 120;
                }
                """, "Constants.java"));
    }

    @Test
    void reportsUpperSnakeFields() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ConstantNamingLint.check("""
                        class Constants {
                            public static final String MOD_ID = "cumulus";
                        }
                        """, "Constants.java"));
        assertTrue(error.getMessage().contains("MOD_ID"), error.getMessage());
    }

    @Test
    void reportsSingleLetterConstants() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ConstantNamingLint.check("""
                        class Matrix {
                            static final int H = 3;
                        }
                        """, "Matrix.java"));
        assertTrue(error.getMessage().contains("'H'"), error.getMessage());
    }

    @Test
    void leavesEnumConstantsToTheDedicatedStep() {
        assertDoesNotThrow(() -> ConstantNamingLint.check("""
                enum Axis {
                    X, Y, Z;
                }
                """, "Axis.java"));
    }
}
