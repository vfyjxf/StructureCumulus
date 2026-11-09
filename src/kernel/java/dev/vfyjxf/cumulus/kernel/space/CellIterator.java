package dev.vfyjxf.cumulus.kernel.space;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;

/** Merges a decomposition's disjoint boxes into one cell stream in lattice order; disjointness means no dedup. */
final class CellIterator implements Iterator<LatticePoint> {

    private final PriorityQueue<Cursor> heap = new PriorityQueue<>();

    CellIterator(CuboidSet set) {
        for (Cuboid box : set.decomposition()) {
            heap.add(new Cursor(box));
        }
    }

    static int compare(CuboidSet left, CuboidSet right) {
        Iterator<LatticePoint> a = new CellIterator(left);
        Iterator<LatticePoint> b = new CellIterator(right);
        while (a.hasNext() && b.hasNext()) {
            int order = a.next().compareTo(b.next());
            if (order != 0) {
                return order;
            }
        }
        return Boolean.compare(a.hasNext(), b.hasNext());
    }

    @Override
    public boolean hasNext() {
        return !heap.isEmpty();
    }

    @Override
    public LatticePoint next() {
        Cursor cursor = heap.poll();
        if (cursor == null) {
            throw new NoSuchElementException();
        }
        LatticePoint cell = cursor.cell();
        cursor.advance();
        if (!cursor.exhausted()) {
            heap.add(cursor);
        }
        return cell;
    }

    /** One box's cells in (y, z, x) order, matching LatticePoint.compareTo. */
    private static final class Cursor implements Comparable<Cursor> {

        private final Cuboid box;
        private int x;
        private int y;
        private int z;

        private Cursor(Cuboid box) {
            this.box = box;
            this.x = box.min().x();
            this.y = box.min().y();
            this.z = box.min().z();
        }

        private LatticePoint cell() {
            return LatticePoint.of(x, y, z);
        }

        private void advance() {
            x++;
            if (x == box.max().x()) {
                x = box.min().x();
                z++;
                if (z == box.max().z()) {
                    z = box.min().z();
                    y++;
                }
            }
        }

        private boolean exhausted() {
            return y == box.max().y();
        }

        @Override
        @SuppressWarnings("PMD.OverrideBothEqualsAndHashCodeOnComparable") // a heap cursor mutates after insertion, so only compareTo is sound
        public int compareTo(Cursor other) {
            return cell().compareTo(other.cell());
        }
    }
}
