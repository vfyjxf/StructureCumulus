package dev.vfyjxf.cumulus.kernel.world;

public enum ReadStatus {
    readable, outOfBounds, notCaptured, denied, failed;

    public boolean readable() {
        return this == readable;
    }
}
