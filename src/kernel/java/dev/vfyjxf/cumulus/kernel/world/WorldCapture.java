package dev.vfyjxf.cumulus.kernel.world;

/** The host capture port; one per world. */
public interface WorldCapture {

    CaptureResult capture(CaptureRequest request);

    long currentRevision();
}
