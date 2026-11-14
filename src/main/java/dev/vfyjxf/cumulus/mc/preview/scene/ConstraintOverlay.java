package dev.vfyjxf.cumulus.mc.preview.scene;

import dev.vfyjxf.cumulus.kernel.document.DistanceMetric;
import dev.vfyjxf.cumulus.kernel.document.LinkMode;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewCatalogPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * The set page's constraint chrome as pure geometry (design 18 §5): every link as a segment between the
 * two cells' faces, every declared presence probe as a cell, and the optional members as the dashed
 * silhouettes. Nothing here reads a world, so nothing here is green or red — the page shows the set as
 * declared.
 */
public final class ConstraintOverlay {

    /** A link's stroke: the chrome family's steel blue, never the mark palette (design 18 §5). */
    public static final int linkColor = 0xFF8FB4D9;
    /** A presence probe's cross: document chrome, not a world finding (design 18 §5). */
    public static final int probeColor = 0xFFC0C0C0;

    /** A segment in the set's composed frame, exploded by {@code t} where a member moved. */
    public record Segment(float ax, float ay, float az, float bx, float by, float bz) {

        public boolean degenerate() {
            return Math.abs(ax - bx) < 1.0E-4F && Math.abs(ay - by) < 1.0E-4F && Math.abs(az - bz) < 1.0E-4F;
        }
    }

    /** One link's stroke: the member that declares it, its two cells and the distance it measures. */
    public record LinkLine(
        int member,
        LatticePoint parent,
        LatticePoint satellite,
        LinkMode mode,
        int actual,
        int maxDistance,
        DistanceMetric metric,
        Segment segment
    ) {

        /** The at-a-glance reading: {@code actual ≤ max} (design 18 §5). */
        public String caption() {
            return actual + " \u2264 " + maxDistance;
        }

        /** The tooltip's one line: the metric names itself here, never on the stroke. */
        public String tooltip() {
            return "link: " + parent + " \u2194 " + satellite + " \u00B7 " + metric.name() + " " + actual + " \u2264 "
                    + maxDistance;
        }
    }

    /** One presence probe cell, already displaced with its member. */
    public record Probe(int member, float x, float y, float z) {}

    private ConstraintOverlay() {}

    /**
     * Every declared link of every member, its endpoints trimmed to the two cells' faces: the root cell
     * keeps its stage-0 displacement and the satellite its own, so the stroke follows an exploded part.
     */
    public static List<LinkLine> links(SetPage page, float t, ExplodeModel explode) {
        List<LinkLine> lines = new ArrayList<>();
        for (int member = 0; member < page.entry().members().size(); member++) {
            PreviewCatalogPayload.Member spec = page.entry().members().get(member);
            ExplodeModel.Offset satellite = explode.offset(SetPage.bay(page.id(), member), t);
            ExplodeModel.Offset parent = explode.offset(SetPage.bay(page.id(), 0), t);
            for (PreviewCatalogPayload.Link link : spec.links()) {
                if (link.mode() == LinkMode.none || link.parentCell() == null || link.satelliteCell() == null) {
                    continue;
                }
                LatticePoint from = link.parentCell();
                LatticePoint to = link.satelliteCell();
                int dx = to.x() - from.x();
                int dy = to.y() - from.y();
                int dz = to.z() - from.z();
                int actual = distance(link.metric(), dx, dy, dz);
                Segment segment = segment(from, to, dx, dy, dz, parent, satellite, link.mode() == LinkMode.face);
                // a face link has no document max: adjacency is exactly one, so the caption reads 1 ≤ 1
                int max = link.mode() == LinkMode.face ? 1 : link.maxDistance();
                lines.add(new LinkLine(member, from, to, link.mode(), actual, max, link.metric(), segment));
            }
        }
        return List.copyOf(lines);
    }

    /** The declared presence regions' cells, each displaced with its member (design 18 §5). */
    public static List<Probe> probes(SetPage page, float t, ExplodeModel explode) {
        List<Probe> probes = new ArrayList<>();
        for (int member = 0; member < page.entry().members().size(); member++) {
            ExplodeModel.Offset offset = explode.offset(SetPage.bay(page.id(), member), t);
            for (Cuboid box : page.entry().members().get(member).presence()) {
                for (int x = box.min().x(); x < box.max().x(); x++) {
                    for (int y = box.min().y(); y < box.max().y(); y++) {
                        for (int z = box.min().z(); z < box.max().z(); z++) {
                            probes.add(
                                new Probe(member, x + 0.5F + offset.x(), y + 0.5F + offset.y(), z + 0.5F + offset.z())
                            );
                        }
                    }
                }
            }
        }
        return List.copyOf(probes);
    }

    /** The members whose cells carry the dashed silhouette instead of the bare solid one (§5). */
    public static List<Integer> optionalMembers(SetPage page) {
        List<Integer> optional = new ArrayList<>();
        for (int member = 0; member < page.entry().members().size(); member++) {
            if (!page.entry().members().get(member).required()) {
                optional.add(member);
            }
        }
        return List.copyOf(optional);
    }

    private static Segment segment(
        LatticePoint from,
        LatticePoint to,
        int dx,
        int dy,
        int dz,
        ExplodeModel.Offset parent,
        ExplodeModel.Offset satellite,
        boolean face
    ) {
        float ax = from.x() + 0.5F + parent.x();
        float ay = from.y() + 0.5F + parent.y();
        float az = from.z() + 0.5F + parent.z();
        float bx = to.x() + 0.5F + satellite.x();
        float by = to.y() + 0.5F + satellite.y();
        float bz = to.z() + 0.5F + satellite.z();
        if (face) {
            // the two cells share a face, so the stroke meets that face rather than either cell centre
            float mx = (ax + bx) * 0.5F;
            float my = (ay + by) * 0.5F;
            float mz = (az + bz) * 0.5F;
            return new Segment(mx, my, mz, mx, my, mz);
        }
        float longest = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        if (longest < 1.0E-4F) {
            return new Segment(ax, ay, az, bx, by, bz);
        }
        float exit = 0.5F / longest;
        float entry = 1.0F - exit;
        return new Segment(
            ax + dx * exit,
            ay + dy * exit,
            az + dz * exit,
            ax + dx * entry,
            ay + dy * entry,
            az + dz * entry
        );
    }

    private static int distance(DistanceMetric metric, int dx, int dy, int dz) {
        int x = Math.abs(dx);
        int y = Math.abs(dy);
        int z = Math.abs(dz);
        return switch (metric) {
            case chebyshev -> Math.max(x, Math.max(y, z));
            case manhattan -> x + y + z;
            case euclidean -> (int) Math.round(Math.sqrt((double) x * x + (double) y * y + (double) z * z));
        };
    }
}
