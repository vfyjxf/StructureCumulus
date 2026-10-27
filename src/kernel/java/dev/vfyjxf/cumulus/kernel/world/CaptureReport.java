package dev.vfyjxf.cumulus.kernel.world;

import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * The capture's own findings: an ordered, immutable list of entries; {@link #empty()} is the normal
 * answer. The kernel carries the report and never interprets it.
 */
public record CaptureReport(List<CaptureReport.Entry> entries) {

    private static final CaptureReport emptyReport = new CaptureReport(List.of());

    public CaptureReport {
        entries = List.copyOf(entries);
    }

    public static CaptureReport empty() {
        return emptyReport;
    }

    public static CaptureReport of(List<Entry> entries) {
        return new CaptureReport(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    /** One opaque host fact: a code, prose that is never parsed, ordered notes and optional cells. */
    public record Entry(FindingCode code, String message, List<String> notes, @Nullable CuboidSet cells) {

        public Entry {
            Objects.requireNonNull(code, "code");
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("an entry message must not be blank");
            }
            notes = List.copyOf(notes);
            for (String note : notes) {
                if (note.isBlank()) {
                    throw new IllegalArgumentException("an entry note must not be blank");
                }
            }
        }

        public Entry(FindingCode code, String message) {
            this(code, message, List.of(), null);
        }

        public static Entry at(FindingCode code, String message, CuboidSet cells) {
            Objects.requireNonNull(cells, "cells");
            return new Entry(code, message, List.of(), cells);
        }
    }
}
