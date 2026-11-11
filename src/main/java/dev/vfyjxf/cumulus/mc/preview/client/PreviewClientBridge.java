package dev.vfyjxf.cumulus.mc.preview.client;

import dev.vfyjxf.cumulus.mc.preview.net.PreviewSyncPayload;

/**
 * The one client-side entry the common payload handler resolves: reached only inside a handler that
 * runs on the physical client, so the class never loads on a dedicated server.
 */
public final class PreviewClientBridge {

    private PreviewClientBridge() {}

    public static void accept(PreviewSyncPayload payload) {
        PreviewClientState.accept(payload);
    }
}
