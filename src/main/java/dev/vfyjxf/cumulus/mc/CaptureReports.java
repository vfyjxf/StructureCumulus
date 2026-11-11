package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.Catalog;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.finding.Severity;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.world.CaptureReport;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The report site: one opaque capture entry becomes one finding under the kernel's trust guard. */
public final class CaptureReports {

    public static FindingBundle relay(CaptureReport report, Catalog<CodeEntry> codes) {
        List<Finding> findings = new ArrayList<>(report.size());
        for (CaptureReport.Entry entry : report.entries()) {
            findings.add(relay(entry, codes));
        }
        return FindingBundle.of(findings);
    }

    public static Finding relay(CaptureReport.Entry entry, Catalog<CodeEntry> codes) {
        boolean kernelCode = Codes.all.stream().anyMatch(candidate -> candidate.code().equals(entry.code()));
        Finding.Builder builder;
        List<String> notes = new ArrayList<>(entry.notes());
        if (kernelCode) {
            builder = Finding.builder(Codes.unreadableRead).phase(Phase.check).message(entry.message());
            notes.add("relayed host code " + entry.code().text() + " is a kernel code; reported as SC-OCC-0004");
        } else {
            CodeEntry host = codes
                    .get(NamespacedId.of("structurecumulus", entry.code().text().toLowerCase(Locale.ROOT)));
            Severity severity = host == null ? Severity.error : host.severity();
            builder = Finding.builder(entry.code(), severity).phase(Phase.check).message(entry.message());
        }
        if (entry.cells() != null) {
            builder.label(Locus.world(entry.cells()), "capture region");
        }
        for (String note : notes) {
            builder.note(note);
        }
        return builder.build();
    }

    private CaptureReports() {}
}
