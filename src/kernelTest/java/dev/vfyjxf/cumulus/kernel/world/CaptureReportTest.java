package dev.vfyjxf.cumulus.kernel.world;

import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureReportTest {

    private static FindingCode hostCode(int number) {
        return FindingCode.of(FindingCode.Family.mc, number);
    }

    @Test
    void emptyReportIsEmptyAndValueEqual() {
        CaptureReport empty = CaptureReport.empty();
        assertTrue(empty.isEmpty());
        assertEquals(0, empty.size());
        assertTrue(empty.entries().isEmpty());
        assertEquals(CaptureReport.of(List.of()), empty);
    }

    @Test
    void ofCopiesEntriesAndKeepsInsertionOrder() {
        List<CaptureReport.Entry> source = new ArrayList<>();
        source.add(new CaptureReport.Entry(hostCode(3), "first"));
        source.add(new CaptureReport.Entry(hostCode(4), "second"));
        CaptureReport report = CaptureReport.of(source);
        source.clear();
        assertEquals(2, report.size());
        assertEquals("first", report.entries().get(0).message());
        assertEquals("second", report.entries().get(1).message());
        assertThrows(
            UnsupportedOperationException.class,
            () -> report.entries().add(new CaptureReport.Entry(hostCode(5), "third"))
        );
    }

    @Test
    void entryCopiesItsNotesAndKeepsThemOrdered() {
        List<String> notes = new ArrayList<>(List.of("first note", "second note"));
        CaptureReport.Entry entry = new CaptureReport.Entry(hostCode(4), "the state cannot be expressed", notes, null);
        notes.clear();
        assertEquals(List.of("first note", "second note"), entry.notes());
        assertThrows(UnsupportedOperationException.class, () -> entry.notes().add("third note"));
        assertThrows(
            IllegalArgumentException.class,
            () -> new CaptureReport.Entry(hostCode(4), "message", List.of("first note", " "), null)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new CaptureReport.Entry(hostCode(4), "message", List.of(""), null)
        );
    }

    @Test
    void twoArgumentEntryHasNoNotesAndNoCells() {
        CaptureReport.Entry entry = new CaptureReport.Entry(hostCode(3), "a nested child was cut");
        assertTrue(entry.notes().isEmpty());
        assertNull(entry.cells());
        assertEquals(hostCode(3), entry.code());
    }

    @Test
    void atEntryCarriesItsWorldRegion() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(4, 64, 4, 6, 66, 6));
        CaptureReport.Entry entry = CaptureReport.Entry.at(hostCode(5), "the capture is over the cap", cells);
        assertEquals(cells, entry.cells());
        assertTrue(entry.notes().isEmpty());
        assertEquals(hostCode(5), entry.code());
    }

    @Test
    @SuppressWarnings("NullAway")
    void entryRejectsNullCodeAndBlankMessage() {
        assertThrows(NullPointerException.class, () -> new CaptureReport.Entry(null, "message"));
        assertThrows(IllegalArgumentException.class, () -> new CaptureReport.Entry(hostCode(3), " "));
        assertThrows(NullPointerException.class, () -> CaptureReport.Entry.at(hostCode(3), "message", null));
    }

    @Test
    void capturedArmCarriesTheReportAndTheCompatibilityConstructorDefaults() {
        StubView view = new StubView();
        StubLease lease = new StubLease(view);
        CaptureResult.Captured compatibility = new CaptureResult.Captured(view, lease);
        assertSame(view, compatibility.view());
        assertSame(lease, compatibility.lease());
        assertTrue(compatibility.report().isEmpty());

        CaptureReport report = CaptureReport.of(List.of(new CaptureReport.Entry(hostCode(4), "mapped tilt")));
        CaptureResult.Captured explicit = new CaptureResult.Captured(view, lease, report);
        assertEquals(report, explicit.report());
    }

    @Test
    void armsEqualByValueAndTheReportIsPartOfIt() {
        StubView view = new StubView();
        StubLease lease = new StubLease(view);
        CaptureReport report = CaptureReport.of(List.of(new CaptureReport.Entry(hostCode(4), "mapped tilt")));
        CaptureReport other = CaptureReport.of(List.of(new CaptureReport.Entry(hostCode(5), "over cap")));

        assertEquals(
            new CaptureResult.Captured(view, lease),
            new CaptureResult.Captured(view, lease, CaptureReport.empty())
        );
        assertNotEquals(
            new CaptureResult.Captured(view, lease, report),
            new CaptureResult.Captured(view, lease, other)
        );

        Cuboid bounds = Cuboid.of(0, 0, 0, 4, 4, 4);
        assertEquals(
            new CaptureResult.TooLarge(bounds, 4096),
            new CaptureResult.TooLarge(bounds, 4096, CaptureReport.empty())
        );
        assertNotEquals(
            new CaptureResult.TooLarge(bounds, 4096, report),
            new CaptureResult.TooLarge(bounds, 4096, other)
        );

        assertEquals(
            new CaptureResult.Unavailable(ReadStatus.notCaptured, hostCode(2)),
            new CaptureResult.Unavailable(ReadStatus.notCaptured, hostCode(2), CaptureReport.empty())
        );
        assertNotEquals(
            new CaptureResult.Unavailable(ReadStatus.notCaptured, hostCode(2), report),
            new CaptureResult.Unavailable(ReadStatus.notCaptured, hostCode(2), other)
        );
    }

    @Test
    void tooLargeAndUnavailableArmsCarryTheReport() {
        Cuboid bounds = Cuboid.of(0, 0, 0, 4, 4, 4);
        CaptureResult.TooLarge tooLarge = new CaptureResult.TooLarge(bounds, 4096);
        assertEquals(bounds, tooLarge.bounds());
        assertEquals(4096, tooLarge.elements());
        assertTrue(tooLarge.report().isEmpty());

        CaptureReport report = CaptureReport.of(List.of(new CaptureReport.Entry(hostCode(5), "over cap")));
        assertEquals(report, new CaptureResult.TooLarge(bounds, 4096, report).report());

        FindingCode code = FindingCode.of(FindingCode.Family.occ, 4);
        CaptureResult.Unavailable unavailable = new CaptureResult.Unavailable(ReadStatus.notCaptured, code);
        assertEquals(ReadStatus.notCaptured, unavailable.status());
        assertEquals(code, unavailable.code());
        assertTrue(unavailable.report().isEmpty());
        assertEquals(report, new CaptureResult.Unavailable(ReadStatus.notCaptured, code, report).report());
    }

    @Test
    void entryFactsAreNotKernelFindings() {
        CaptureReport.Entry entry = new CaptureReport.Entry(hostCode(3), "a nested child was cut");
        assertFalse(entry.code().text().isBlank());
        assertEquals(FindingCode.Family.mc, entry.code().family());
        assertEquals(3, entry.code().number());
        assertTrue(
            Codes.all.stream().noneMatch(codeEntry -> codeEntry.code().equals(entry.code())),
            "a host code is never a kernel code"
        );
    }

    private static final class StubView implements WorldView {

        private static final SnapshotId snapshot = new SnapshotId(World.of(NamespacedId.of("test", "world")), 1);

        @Override
        public SnapshotId snapshotId() {
            return snapshot;
        }

        @Override
        public int frame() {
            return 0;
        }

        @Override
        public int depth() {
            return 0;
        }

        @Override
        public LatticePoint origin() {
            return LatticePoint.origin;
        }

        @Override
        public @Nullable WorldView parent() {
            return null;
        }

        @Override
        public int cellSize() {
            return 16;
        }

        @Override
        public Cuboid bounds() {
            return Cuboid.of(LatticePoint.origin);
        }

        @Override
        public SnapshotPolicy policy() {
            return SnapshotPolicy.minimal();
        }

        @Override
        public SectionKey sectionOf(LatticePoint cell) {
            return SectionKey.of(snapshot.world(), 0, cell, 16);
        }

        @Override
        public SectionSlot slot(SectionKey key) {
            return new SectionSlot.Air(0);
        }

        @Override
        public CellRead read(LatticePoint cell) {
            return new CellRead.Found(OccupantSnapshot.air(snapshot, 0, cell));
        }

        @Override
        public @Nullable OccupantSnapshot occupant(OccupantRef ref) {
            return null;
        }

        @Override
        public List<OccupantSnapshot> occupants(Cuboid box, OccupantFilter filter) {
            return List.of();
        }

        @Override
        public @Nullable WorldView nested(LatticePoint cell) {
            return null;
        }

        @Override
        public long contentDigest(SectionKey key) {
            return 0;
        }
    }

    private static final class StubLease implements SnapshotLease {

        private final WorldView view;
        private boolean current = true;

        private StubLease(WorldView view) {
            this.view = view;
        }

        @Override
        public SnapshotId id() {
            return view.snapshotId();
        }

        @Override
        public WorldView view() {
            return view;
        }

        @Override
        public boolean isCurrent() {
            return current;
        }

        @Override
        public void close() {
            current = false;
        }
    }
}
