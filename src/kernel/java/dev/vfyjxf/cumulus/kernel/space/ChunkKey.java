package dev.vfyjxf.cumulus.kernel.space;

public record ChunkKey(int cx, int cy, int cz) implements Comparable<ChunkKey> {

    @Override
    public int compareTo(ChunkKey other) {
        int byY = Integer.compare(cy, other.cy);
        if (byY != 0) {
            return byY;
        }
        int byZ = Integer.compare(cz, other.cz);
        return byZ != 0 ? byZ : Integer.compare(cx, other.cx);
    }
}
