package dev.vfyjxf.cumulus.mc.preview;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * The placeholder confirm handler: it prints the executor call a landed build flow would receive and
 * accepts nothing, so a confirm can never write before P5 wires the executor.
 */
final class PreviewConfirms implements PreviewConfirmHandler {

    @Override
    public boolean confirm(ServerPlayer player, Request request) {
        String origin = request.siting().origin().x() + " " + request.siting().origin().y() + " "
                + request.siting().origin().z();
        player.sendSystemMessage(
            Component.literal(
                "preview confirm: " + request.blueprintId().text() + " at origin " + origin + " orientation "
                        + request.siting().placement().orientation().ordinal()
                        + " -> build executor call /cumulus build " + request.blueprintId().text() + " " + origin
                        + (request.codes().isEmpty() ? "" : " (findings: " + codes(request) + ")")
            )
        );
        return false;
    }

    private static String codes(Request request) {
        return String.join(", ", request.codes()).toLowerCase(Locale.ROOT);
    }
}
