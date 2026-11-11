package dev.vfyjxf.cumulus.mc.preview;

import dev.vfyjxf.cumulus.StructureCumulus;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.mc.LevelAccess;
import dev.vfyjxf.cumulus.mc.LevelCapture;
import dev.vfyjxf.cumulus.mc.build.ApplyReport;
import dev.vfyjxf.cumulus.mc.build.BuildCodes;
import dev.vfyjxf.cumulus.mc.build.BuildConfig;
import dev.vfyjxf.cumulus.mc.build.BuildExecutor;
import dev.vfyjxf.cumulus.mc.build.BuildPolicy;
import dev.vfyjxf.cumulus.mc.build.BuildRequest;
import dev.vfyjxf.cumulus.mc.pref.BuildPreferences;
import dev.vfyjxf.cumulus.mc.pref.PreferencesSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * The landed confirm handler: the build executor runs on the session's siting, item source and solve
 * preferences, under the server gate narrowed by the player's removal policy. A confirm that forms
 * the structure writes the choice back as the implicit memory signal (7.2).
 */
final class PreviewBuilds implements PreviewConfirmHandler {

    @Override
    public boolean confirm(ServerPlayer player, Request request) {
        ServerLevel level = player.serverLevel();
        StructureKernel kernel = LevelAccess.kernel();
        LevelCapture capture = LevelAccess.capture(level);
        OwnerId owner = LevelAccess.owner();
        if (kernel == null || capture == null || owner == null) {
            player.sendSystemMessage(Component.literal("build refused: StructureCumulus is not running"));
            return false;
        }
        PreferencesSavedData store = PreferencesSavedData.get(level);
        BuildPreferences preferences = store.preferences(player.getUUID());
        BuildPolicy configured = BuildConfig.policy();
        BuildPolicy policy = switch (preferences.removalPolicy()) {
            case forbid -> configured.withAllowRemoval(false);
            case ask -> configured;
            case allow -> configured.withAllowRemoval(true);
        };
        ApplyReport report = new BuildExecutor(capture, kernel, owner, policy).run(
            new BuildRequest(
                request.blueprintId(),
                request.siting(),
                request.source(),
                policy,
                false,
                request.preferences(),
                player
            )
        );
        boolean formed = report.status() == ApplyReport.Status.committed
                && !hasCode(report, BuildCodes.recheckNotFormed);
        StructureCumulus.logger.info(
            "preview build {} at {}: {}{}",
            request.blueprintId().text(),
            request.siting().origin(),
            report.status(),
            report.findings().findings().isEmpty()
                    ? ""
                    : " " + report.findings().findings().get(0).code().text() + " "
                            + report.findings().findings().get(0).message()
        );
        if (formed) {
            store.remember(player.getUUID(), request.blueprintId(), request.siting());
            player.sendSystemMessage(Component.literal("build " + request.blueprintId().text() + " committed"));
            return true;
        }
        if (report.status() == ApplyReport.Status.committed) {
            // one build run applies one repair: the plan stays so the next confirm continues it (5.1)
            player.sendSystemMessage(
                Component.literal(
                    "build " + request.blueprintId().text() + " committed; the structure is not formed yet"
                )
            );
            return false;
        }
        player.sendSystemMessage(
            Component.literal(
                "build " + request.blueprintId().text() + " " + report.status().name().toLowerCase(Locale.ROOT)
                        + (report.findings().findings().isEmpty()
                                ? ""
                                : ": " + report.findings().findings().get(0).code().text())
            )
        );
        return false;
    }

    private static boolean hasCode(ApplyReport report, FindingCode code) {
        return report.findings().findings().stream().anyMatch(finding -> finding.code().equals(code));
    }
}
