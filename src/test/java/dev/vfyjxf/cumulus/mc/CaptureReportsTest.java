package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.Catalog;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.finding.Severity;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.world.CaptureReport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureReportsTest {

    private static final Catalog<CodeEntry> codes = AdapterCodes.catalog();

    @Test
    void aHostCodeUsesItsCatalogSeverityAndNotes() {
        CaptureReport.Entry entry = new CaptureReport.Entry(
            AdapterCodes.cut,
            "a nested extent was cut: depth",
            List.of("reason: depth", "frame: 0"),
            null
        );
        Finding finding = CaptureReports.relay(entry, codes);
        assertEquals(AdapterCodes.cut, finding.code());
        assertEquals(Severity.warning, finding.severity());
        assertEquals(Phase.check, finding.phase());
        assertEquals(
            List.of("reason: depth", "frame: 0"),
            finding.notes().stream().map(note -> note.message()).toList()
        );
    }

    @Test
    void anUnregisteredHostCodeFallsBackToError() {
        FindingCode hostCode = FindingCode.of(FindingCode.Family.mc, 99);
        Finding finding = CaptureReports.relay(new CaptureReport.Entry(hostCode, "host detail"), codes);
        assertEquals(hostCode, finding.code());
        assertEquals(Severity.error, finding.severity());
    }

    @Test
    void aKernelCodeIsReplacedByOcc0004WithANote() {
        CaptureReport.Entry entry = new CaptureReport.Entry(
            Codes.unreadableRead.code(),
            "the host tried to mint a kernel code",
            List.of("host note"),
            CuboidSet.of(Cuboid.of(1, 2, 3, 2, 3, 4))
        );
        Finding finding = CaptureReports.relay(entry, codes);
        assertEquals(Codes.unreadableRead.code(), finding.code());
        assertEquals(Severity.error, finding.severity());
        assertTrue(
            finding.notes().stream().anyMatch(note -> note.message().contains("is a kernel code")),
            "the trust guard adds the relay note"
        );
        assertEquals(
            CuboidSet.of(Cuboid.of(1, 2, 3, 2, 3, 4)),
            Objects.requireNonNull(finding.primaryLabel()).locus().cells()
        );
    }

    @Test
    void worldCellsBecomeThePrimaryLabelAndAnAbsentRegionStaysLabelLess() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(4, 64, 4, 6, 66, 6));
        Finding located = CaptureReports
                .relay(new CaptureReport.Entry(AdapterCodes.cut, "a cut", List.of(), cells), codes);
        assertEquals(Locus.world(cells), Objects.requireNonNull(located.primaryLabel()).locus());

        Finding labelLess = CaptureReports
                .relay(new CaptureReport.Entry(AdapterCodes.cut, "a cut", List.of(), null), codes);
        assertNull(labelLess.primaryLabel());
    }

    @Test
    void theAdapterCatalogCarriesTheCanonicalSeverities() {
        Catalog<CodeEntry> catalog = AdapterCodes.catalog();
        assertEquals(Severity.warning, catalog.require(key("sc-mc-0003")).severity());
        assertEquals(Severity.warning, catalog.require(key("sc-mc-0004")).severity());
        assertEquals(Severity.error, catalog.require(key("sc-mc-0005")).severity());
    }

    private static NamespacedId key(String text) {
        return NamespacedId.of("structurecumulus", text);
    }

    @Test
    void anEmptyReportRelaysToAnEmptyBundle() {
        FindingBundle bundle = CaptureReports.relay(CaptureReport.empty(), codes);
        assertTrue(bundle.isEmpty());
        assertEquals(0, bundle.findings().size());
    }
}
