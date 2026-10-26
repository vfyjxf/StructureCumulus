package dev.vfyjxf.cumulus.kernel.finding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
class CodesTest {

    private static final String PRODUCERS = """
            SC-SPC-0001 ERROR
            SC-SPC-0002 ERROR
            SC-OCC-0001 ERROR
            SC-OCC-0002 WARNING
            SC-OCC-0003 ERROR
            SC-EXP-0001 ERROR
            SC-EXP-0002 ERROR
            SC-EXP-0003 ERROR
            SC-EXP-0004 ERROR
            SC-EXP-0005 ERROR
            SC-CLM-0001 ERROR
            SC-CLM-0002 WARNING
            SC-CLM-0003 ERROR
            SC-CLM-0004 ERROR
            SC-CLM-0005 ERROR
            SC-BPT-0001 ERROR
            SC-BPT-0002 ERROR
            SC-BPT-0003 ERROR
            SC-BPT-0004 ERROR
            SC-BPT-0005 ERROR
            SC-BPT-0006 ERROR
            SC-BPT-0007 ERROR
            SC-BPT-0008 ERROR
            SC-BPT-0009 ERROR
            SC-BPT-0010 ERROR
            SC-CHK-0001 ERROR
            SC-CHK-0002 ERROR
            SC-CHK-0003 ERROR
            SC-DOC-0001 ERROR
            SC-DOC-0002 ERROR
            SC-DOC-0003 ERROR
            SC-DOC-0004 ERROR
            SC-DOC-0005 ERROR
            SC-DOC-0006 ERROR
            SC-DOC-0007 ERROR
            SC-DOC-0008 ERROR
            SC-DOC-0009 ERROR
            SC-DOC-0010 ERROR
            SC-DOC-0011 ERROR
            SC-DOC-0012 INFO
            SC-DOC-0013 ERROR
            SC-CTX-0001 ERROR
            SC-CTX-0004 ERROR
            SC-CTX-0005 ERROR
            SC-CTX-0006 ERROR
            SC-CTX-0007 ERROR
            SC-ENG-0001 ERROR
            SC-ENG-0002 WARNING
            SC-ENG-0004 ERROR
            SC-ENG-0005 WARNING
            SC-DIA-0001 WARNING
            SC-RMD-0001 ERROR
            SC-RMD-0002 ERROR
            SC-SPI-0001 ERROR
            SC-SPI-0002 ERROR
            SC-SPI-0003 ERROR
            """;

    @Test
    void tableMatchesTheProducerRowsExactlyOnce() {
        List<String> rows = new ArrayList<>();
        for (String line : PRODUCERS.split("\\R")) {
            if (!line.isBlank()) {
                rows.add(line);
            }
        }
        assertEquals(56, rows.size());
        assertEquals(rows.size(), Codes.ALL.size());

        Set<FindingCode> seen = new HashSet<>();
        FindingCode previous = null;
        for (int index = 0; index < rows.size(); index++) {
            String[] parts = rows.get(index).split("\\s+");
            FindingCode code = FindingCode.of(parts[0]);
            CodeEntry entry = Codes.ALL.get(index);
            assertEquals(code, entry.code());
            assertEquals(Severity.valueOf(parts[1]), entry.severity());
            assertEquals(code, entry.explanation().code());
            assertTrue(seen.add(code), "duplicate code: " + code);
            if (previous != null) {
                assertTrue(previous.compareTo(code) < 0, "codes are not in canonical order");
            }
            previous = code;
        }
    }

    @Test
    void everyEntryCarriesAUsableExplanation() {
        for (CodeEntry entry : Codes.ALL) {
            Explanation explanation = entry.explanation();
            assertFalse(explanation.summary().isBlank());
            assertFalse(explanation.body().isBlank());
            assertFalse(explanation.seeAlso().isEmpty());
            assertTrue(explanation.summary().endsWith("."));
            assertEquals(entry.code().text(), FindingCode.parse(entry.code().text()).text());
        }
    }

    @Test
    void adapterCodesAreAbsent() {
        for (CodeEntry entry : Codes.ALL) {
            assertFalse(entry.code().family() == FindingCode.Family.MC);
        }
        assertNotNull(Codes.BUNDLE_TRUNCATED);
        assertEquals("SC-DIA-0001", Codes.BUNDLE_TRUNCATED.code().text());
        assertEquals(Severity.WARNING, Codes.BUNDLE_TRUNCATED.severity());
    }
}
