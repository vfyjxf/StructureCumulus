package dev.vfyjxf.cumulus.kernel.finding;

import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodesTest {

    /** Mirrors the producer table in finding.md §6; DASH stands for the table's label-less marker. */
    private static final String producers = """
            SC-SPC-0001 ERROR P space
            SC-SPC-0002 ERROR P space
            SC-OCC-0001 ERROR P world
            SC-OCC-0002 WARNING P world
            SC-OCC-0003 ERROR P world
            SC-OCC-0004 ERROR P world
            SC-EXP-0001 ERROR P structure
            SC-EXP-0002 ERROR P structure
            SC-EXP-0003 ERROR P structure
            SC-EXP-0004 ERROR P document
            SC-EXP-0005 ERROR P document
            SC-EXP-0006 ERROR P structure
            SC-CLM-0001 ERROR P claim
            SC-CLM-0002 WARNING DASH claim
            SC-CLM-0003 ERROR DASH claim
            SC-CLM-0004 ERROR P claim
            SC-CLM-0005 ERROR P claim
            SC-BPT-0001 ERROR P structure
            SC-BPT-0002 ERROR P structure
            SC-BPT-0003 ERROR P structure
            SC-BPT-0004 ERROR P structure
            SC-BPT-0005 ERROR P structure
            SC-BPT-0006 ERROR P structure
            SC-BPT-0007 ERROR P structure
            SC-BPT-0008 ERROR P structure
            SC-BPT-0009 ERROR P structure
            SC-BPT-0010 ERROR P structure
            SC-CHK-0001 ERROR T structure
            SC-CHK-0002 ERROR P structure
            SC-CHK-0003 ERROR P structure
            SC-DOC-0001 ERROR P document
            SC-DOC-0002 ERROR P document
            SC-DOC-0003 ERROR P document
            SC-DOC-0004 ERROR P document
            SC-DOC-0005 ERROR P document
            SC-DOC-0006 ERROR P document
            SC-DOC-0007 ERROR P document
            SC-DOC-0008 ERROR P document
            SC-DOC-0009 ERROR P document
            SC-DOC-0010 ERROR P document
            SC-DOC-0011 ERROR P document
            SC-DOC-0012 INFO DASH document
            SC-DOC-0013 ERROR DASH document
            SC-DOC-0014 ERROR P document
            SC-DOC-0015 ERROR P document
            SC-CTX-0001 ERROR P context
            SC-CTX-0004 ERROR P context
            SC-CTX-0005 ERROR P context
            SC-CTX-0006 ERROR P context
            SC-CTX-0007 ERROR P context
            SC-ENG-0001 ERROR DASH engine
            SC-ENG-0002 WARNING DASH engine
            SC-ENG-0004 ERROR DASH engine
            SC-ENG-0005 WARNING DASH engine
            SC-DIA-0001 WARNING DASH finding
            SC-RMD-0001 ERROR P remedy
            SC-RMD-0002 ERROR P remedy
            SC-SPI-0001 ERROR P spi
            SC-SPI-0003 ERROR T spi
            SC-SET-0001 ERROR DASH adapter
            SC-SET-0002 ERROR DASH document
            SC-SET-0003 ERROR DASH document
            SC-SET-0004 ERROR DASH adapter
            SC-SET-0005 ERROR DASH document
            SC-SET-0006 ERROR DASH document
            SC-SET-0007 ERROR DASH adapter
            SC-SET-0008 ERROR DASH document
            """;

    private static final Set<String> owners = Set.of(
        "core",
        "space",
        "finding",
        "world",
        "claim",
        "structure",
        "context",
        "document",
        "engine",
        "remedy",
        "spi",
        "adapter"
    );

    private static Severity severity(String wire) {
        for (Severity severity : Severity.values()) {
            if (severity.wireName().equals(wire)) {
                return severity;
            }
        }
        throw new IllegalArgumentException("unknown severity " + wire);
    }

    @Test
    void tableMatchesTheProducerRowsExactlyOnce() {
        List<String[]> rows = new ArrayList<>();
        for (String line : producers.split("\\R")) {
            if (!line.isBlank()) {
                rows.add(line.split("\\s+"));
            }
        }
        assertEquals(67, rows.size());
        assertEquals(rows.size(), Codes.all.size());

        Set<FindingCode> seen = new HashSet<>();
        FindingCode previous = null;
        for (int index = 0; index < rows.size(); index++) {
            String[] parts = rows.get(index);
            assertEquals(4, parts.length, "each row carries code, severity, label and owner: " + line(rows, index));
            FindingCode code = FindingCode.of(parts[0]);
            CodeEntry entry = Codes.all.get(index);
            assertEquals(code, entry.code());
            assertEquals(severity(parts[1]), entry.severity());
            assertEquals(code, entry.explanation().code());
            assertTrue(parts[2].equals("P") || parts[2].equals("DASH") || parts[2].equals("T"), parts[0]);
            assertTrue(owners.contains(parts[3]), "unknown owning module for " + parts[0] + ": " + parts[3]);
            assertTrue(seen.add(code), "duplicate code: " + code);
            if (previous != null) {
                assertTrue(previous.compareTo(code) < 0, "codes are not in canonical order");
            }
            previous = code;
        }
    }

    @Test
    void labelPolicyIsKnownForEveryCode() {
        for (String line : producers.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\s+");
            if (parts[2].equals("T")) {
                assertTrue(
                    parts[0].equals("SC-CHK-0001") || parts[0].equals("SC-SPI-0003"),
                    "unexpected thrown-only code: " + parts[0]
                );
            }
        }
    }

    @Test
    void setFamilyCarriesItsSubject() {
        assertEquals("structure sets and satellites", FindingCode.Family.set.subject());
        assertEquals("SC-SET-0001", FindingCode.of(FindingCode.Family.set, 1).text());
    }

    @Test
    void everyEntryCarriesAUsableExplanation() {
        for (CodeEntry entry : Codes.all) {
            Explanation explanation = entry.explanation();
            assertFalse(explanation.summary().isBlank());
            assertFalse(explanation.body().isBlank());
            assertFalse(explanation.seeAlso().isEmpty());
            assertTrue(explanation.summary().endsWith("."));
            assertEquals(
                entry.code().text(),
                Objects.requireNonNull(FindingCode.parse(entry.code().text()), "parsed").text()
            );
        }
    }

    @Test
    void adapterCodesAreAbsent() {
        for (CodeEntry entry : Codes.all) {
            assertFalse(entry.code().family() == FindingCode.Family.mc);
        }
        assertNotNull(Codes.bundleTruncated);
        assertEquals("SC-DIA-0001", Codes.bundleTruncated.code().text());
        assertEquals(Severity.warning, Codes.bundleTruncated.severity());
    }

    private static String line(List<String[]> rows, int index) {
        return String.join(" ", rows.get(index));
    }
}
