package dev.vfyjxf.cumulus.mc.build;

import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.finding.Explanation;
import dev.vfyjxf.cumulus.kernel.finding.Severity;

import java.util.List;

/** The {@code SC-BLD} family: the build layer's codes, catalogued by {@code AdapterCodes}. */
public final class BuildCodes {

    public static final FindingCode noMachineRepair = buildCode(1);
    public static final FindingCode policyRefusal = buildCode(2);
    public static final FindingCode itemShortage = buildCode(3);
    public static final FindingCode materializationFailure = buildCode(4);
    public static final FindingCode stepFailure = buildCode(5);
    public static final FindingCode incompleteRollback = buildCode(6);
    public static final FindingCode recheckNotFormed = buildCode(7);
    public static final FindingCode previewStale = buildCode(8);

    public static final CodeEntry noMachineRepairEntry = entry(
        noMachineRepair,
        Severity.warning,
        "No machine-applicable remedy exists.",
        "Every remedy the solve pass offered needs a human choice. The run stops without writing; "
                + "pick a repair yourself or adjust the blueprint."
    );

    public static final CodeEntry policyRefusalEntry = entry(
        policyRefusal,
        Severity.error,
        "The build policy refused the plan.",
        "The plan is over the touch ceiling (maxTouchedVolume), contains a removal with removal off "
                + "(allowRemoval), or touches a cell held by a foreign exclusive claim."
    );

    public static final CodeEntry itemShortageEntry = entry(
        itemShortage,
        Severity.error,
        "The item source cannot cover the plan.",
        "The simulation found fewer template-matching items than the plan needs; the report carries "
                + "the per-item counts."
    );

    public static final CodeEntry materializationFailureEntry = entry(
        materializationFailure,
        Severity.error,
        "An edit could not be materialized.",
        "The edit names an unknown block, property or value, or its state cannot express the siting "
                + "orientation. Nothing is written for that plan."
    );

    public static final CodeEntry stepFailureEntry = entry(
        stepFailure,
        Severity.error,
        "A plan step failed in the world.",
        "The transaction failed at this step; the steps already written were restored in reverse order."
    );

    public static final CodeEntry incompleteRollbackEntry = entry(
        incompleteRollback,
        Severity.warning,
        "The rollback is incomplete.",
        "A restore or an item return did not fully succeed. The report names the remaining cells and "
                + "the unreturned items; nothing is retried."
    );

    public static final CodeEntry recheckNotFormedEntry = entry(
        recheckNotFormed,
        Severity.error,
        "The structure is still not formed after the build.",
        "The post-build check reports the same result as the pre-build check. The write is kept; the "
                + "user re-runs the build after fixing the cause."
    );

    public static final CodeEntry previewStaleEntry = entry(
        previewStale,
        Severity.warning,
        "The preview session is stale.",
        "The world inside the footprint changed, so the session's candidates no longer describe it."
    );

    public static List<CodeEntry> entries() {
        return List.of(
            noMachineRepairEntry,
            policyRefusalEntry,
            itemShortageEntry,
            materializationFailureEntry,
            stepFailureEntry,
            incompleteRollbackEntry,
            recheckNotFormedEntry,
            previewStaleEntry
        );
    }

    private static FindingCode buildCode(int number) {
        return FindingCode.of(FindingCode.Family.bld, number);
    }

    private static CodeEntry entry(FindingCode code, Severity severity, String summary, String body) {
        return new CodeEntry(code, severity, new Explanation(code, summary, body, List.of()));
    }

    private BuildCodes() {}
}
