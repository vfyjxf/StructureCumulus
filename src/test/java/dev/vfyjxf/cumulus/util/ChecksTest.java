package dev.vfyjxf.cumulus.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChecksTest {

    @Test
    void checkNotNullReturnsTheValue() {
        assertEquals("value", Checks.checkNotNull("value", "value"));
    }

    @Test
    void checkNotNullRejectsNull() {
        assertThrows(NullPointerException.class, () -> Checks.checkNotNull(null, "value"));
    }

    @Test
    void checkArgumentRejectsFalse() {
        assertThrows(IllegalArgumentException.class, () -> Checks.checkArgument(false, "broken"));
    }
}
