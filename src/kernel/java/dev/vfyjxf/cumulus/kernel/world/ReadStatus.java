package dev.vfyjxf.cumulus.kernel.world;

public enum ReadStatus {
    readable, outOfBounds, notCaptured, denied;

    public boolean readable() {
        return this == readable;
    }
}
