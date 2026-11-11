package dev.vfyjxf.cumulus.mc.preview;

import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.mc.build.BuildCodes;

/** The preview's own code: design 15 §10 makes an invalidation SC-BLD-0008, owned by the build family. */
final class PreviewCodes {

    static final FindingCode sessionInvalidated = BuildCodes.previewStale;

    private PreviewCodes() {}
}
