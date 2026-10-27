package dev.vfyjxf.cumulus.kernel.finding;

import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;

import java.util.List;

/** The shipped code table. */
public final class Codes {

    public static final CodeEntry locusUnmapped = entry(
        FindingCode.Family.spc,
        1,
        Severity.error,
        "A cell or locus could not be mapped into the requested frame.",
        "A siting or locus map returned no image for the requested frame. Check the placement, "
                + "the origin and the lattice bounds before retrying."
    );

    public static final CodeEntry cuboidOffLattice = entry(
        FindingCode.Family.spc,
        2,
        Severity.error,
        "A box operation would leave the lattice.",
        "A translated box or footprint no longer fits the lattice. Check the offsets and clamp "
                + "the declaration into the valid range."
    );

    public static final CodeEntry unreadableCell = entry(
        FindingCode.Family.occ,
        1,
        Severity.error,
        "The cell could not be read from the snapshot.",
        "The view has no readable occupant for the requested cell. Check the capture bounds, "
                + "the depth and host permission."
    );

    public static final CodeEntry footprintClipped = entry(
        FindingCode.Family.occ,
        2,
        Severity.warning,
        "An occupant footprint leaves the snapshot.",
        "Only the in-snapshot intersection was compared. Check the capture bounds and the " + "occupant's full body."
    );

    public static final CodeEntry nestedCut = entry(
        FindingCode.Family.occ,
        3,
        Severity.error,
        "A nested view was cut by depth or a cap.",
        "The nested view stopped before the checked cells were available. Check the capture "
                + "depth and element ceiling."
    );

    public static final CodeEntry unreadableRead = entry(
        FindingCode.Family.occ,
        4,
        Severity.error,
        "A required read is unavailable.",
        "A cell, section or trait a check required could not be read. Check the capture bounds, "
                + "depth and host permission, then retry with the required reads declared."
    );

    public static final CodeEntry expected = entry(
        FindingCode.Family.exp,
        1,
        Severity.error,
        "The expected element or state did not match.",
        "The default leaf test refuted the subject. Check the occupant type, properties and " + "state."
    );

    public static final CodeEntry negationMatched = entry(
        FindingCode.Family.exp,
        2,
        Severity.error,
        "A negated test matched.",
        "The child test was expected to fail and succeeded. Check the inner expectation or " + "invert the declaration."
    );

    public static final CodeEntry thresholdImpossible = entry(
        FindingCode.Family.exp,
        3,
        Severity.error,
        "The requested threshold cannot be met.",
        "The child count is below or above what the threshold allows. Check the child tests " + "and the count."
    );

    public static final CodeEntry factoryUnknown = entry(
        FindingCode.Family.exp,
        4,
        Severity.error,
        "A factory kind is unknown.",
        "The compiler was asked for a factory that is not registered. Check the factory id and " + "the catalog."
    );

    public static final CodeEntry factoryBad = entry(
        FindingCode.Family.exp,
        5,
        Severity.error,
        "A factory returned null or malformed arguments.",
        "The registered factory refused its arguments or returned no value. Check the factory "
                + "implementation and the document arguments."
    );

    public static final CodeEntry envelopeOpen = entry(
        FindingCode.Family.exp,
        6,
        Severity.error,
        "The envelope is not closed.",
        "A cell inside the blueprint bounds but outside every bay and declared footprint holds a "
                + "non-air occupant. The derived envelope is the closed world: declare a bay for a port "
                + "or any cell, or remove the occupant. A refused read stays SC-OCC-0004."
    );

    public static final CodeEntry claimConflict = entry(
        FindingCode.Family.clm,
        1,
        Severity.error,
        "Two claims conflict over a cell.",
        "At least one row is exclusive and the owner or siting differs. Check the contested "
                + "cells and both sharing declarations."
    );

    public static final CodeEntry ledgerUnavailable = entry(
        FindingCode.Family.clm,
        2,
        Severity.warning,
        "The ledger cannot answer.",
        "The claim view was unavailable at check time. Check the ledger handle and the world " + "scope."
    );

    public static final CodeEntry ledgerMoved = entry(
        FindingCode.Family.clm,
        3,
        Severity.error,
        "The ledger revision moved twice.",
        "The compare-and-set lost a second race after a retry. Re-read the ledger and rebuild " + "the settlement."
    );

    public static final CodeEntry bindingInvalid = entry(
        FindingCode.Family.clm,
        4,
        Severity.error,
        "A settle binding is invalid.",
        "A row names a foreign snapshot, frame, owner, siting or bay. Check the planner input " + "before settling."
    );

    public static final CodeEntry boundEmpty = entry(
        FindingCode.Family.clm,
        5,
        Severity.error,
        "An exclusive bound footprint is empty.",
        "An exclusive bay bound to live subjects owns no cell. Check the bound focus and the " + "subjects it returned."
    );

    public static final CodeEntry blueprintInvalid = entry(
        FindingCode.Family.bpt,
        1,
        Severity.error,
        "A blueprint has no checks or bounds.",
        "The build found nothing to check or a missing bounds declaration. Add at least one "
                + "check and declare the blueprint bounds."
    );

    public static final CodeEntry baysOverlap = entry(
        FindingCode.Family.bpt,
        2,
        Severity.error,
        "Two bays overlap with an exclusive side.",
        "At least one overlapping bay is exclusive. Split the cells or make both bays shared."
    );

    public static final CodeEntry bayOutside = entry(
        FindingCode.Family.bpt,
        3,
        Severity.error,
        "A bay is empty or outside the blueprint bounds.",
        "Every bay must be non-empty and contained in the root bounds. Check the bay cells "
                + "against the declaration."
    );

    public static final CodeEntry duplicateId = entry(
        FindingCode.Family.bpt,
        4,
        Severity.error,
        "A bay, check, seam or anchor id is duplicated.",
        "Identifiers must be unique within their family. Rename one declaration."
    );

    public static final CodeEntry coverageInvalid = entry(
        FindingCode.Family.bpt,
        5,
        Severity.error,
        "The coverage or footprint rules failed.",
        "The declared coverage and footprint cannot be satisfied by the checks. Check rules R1 " + "to R4 for this bay."
    );

    public static final CodeEntry anchorInvalid = entry(
        FindingCode.Family.bpt,
        6,
        Severity.error,
        "The anchor or orientation policy is invalid.",
        "The anchor role, allowed orientations or upright promise cannot be resolved. Check the "
                + "anchor property and policy."
    );

    public static final CodeEntry planInvalid = entry(
        FindingCode.Family.bpt,
        7,
        Severity.error,
        "A computation plan is cyclic or has no producer.",
        "The section and aggregate order cannot be built. Check computation outputs and " + "derivation references."
    );

    public static final CodeEntry derivationInvalid = entry(
        FindingCode.Family.bpt,
        8,
        Severity.error,
        "A derivation order or output is invalid.",
        "The derivation spec names a bad order, a missing output or a duplicate. Check the "
                + "derivation declarations."
    );

    public static final CodeEntry seamOutside = entry(
        FindingCode.Family.bpt,
        9,
        Severity.error,
        "A seam is outside every bay.",
        "Each seam cell must belong to at least one bay. Check the seam declaration against the " + "bay cells."
    );

    public static final CodeEntry footprintOutside = entry(
        FindingCode.Family.bpt,
        10,
        Severity.error,
        "A declared footprint is outside the bounds.",
        "The declared cells must stay inside the blueprint bounds. Check the declaration or grow " + "the bounds."
    );

    public static final CodeEntry subjectMismatch = entry(
        FindingCode.Family.chk,
        1,
        Severity.error,
        "A check pairs different subject types.",
        "The focus and expectation must name the same subject class. Check the check " + "declaration."
    );

    public static final CodeEntry checkReadsDerivation = entry(
        FindingCode.Family.chk,
        2,
        Severity.error,
        "A check reads a derivation output.",
        "Checks may read only snapshot facts and check context. Move the value to a deriver or a " + "computation."
    );

    public static final CodeEntry focusOutside = entry(
        FindingCode.Family.chk,
        3,
        Severity.error,
        "A focus's static cells leave its bay.",
        "The focus declares cells outside the bay it belongs to. Check the focus cells or the " + "bay."
    );

    public static final CodeEntry schemaVersion = entry(
        FindingCode.Family.doc,
        1,
        Severity.error,
        "The document schema version is unsupported.",
        "The reader found a version this compiler does not accept. Check the schemaVersion " + "field."
    );

    public static final CodeEntry documentField = entry(
        FindingCode.Family.doc,
        2,
        Severity.error,
        "A document field is missing or of the wrong kind.",
        "The field does not match the schema. Check its name, kind and position."
    );

    public static final CodeEntry documentFactory = entry(
        FindingCode.Family.doc,
        3,
        Severity.error,
        "The document names an unknown factory kind.",
        "The compiler has no factory for this declaration. Check the factory id and the " + "registered catalogs."
    );

    public static final CodeEntry documentDuplicate = entry(
        FindingCode.Family.doc,
        4,
        Severity.error,
        "A document id is duplicated.",
        "Two declarations share one id. Rename one or merge them."
    );

    public static final CodeEntry placementOutside = entry(
        FindingCode.Family.doc,
        5,
        Severity.error,
        "A placement leaves the root bounds.",
        "The placed definition does not fit the declared root. Check the origin, orientation " + "and bounds."
    );

    public static final CodeEntry bayUnresolved = entry(
        FindingCode.Family.doc,
        6,
        Severity.error,
        "A check names no merged bay.",
        "The check refers to a bay that is not declared or merged. Check the bay id."
    );

    public static final CodeEntry keyUndeclared = entry(
        FindingCode.Family.doc,
        7,
        Severity.error,
        "A spec names an undeclared key.",
        "The key must be declared before it is used. Add the declaration or fix the reference."
    );

    public static final CodeEntry keyType = entry(
        FindingCode.Family.doc,
        8,
        Severity.error,
        "A key's declared type does not match its producer.",
        "The declaration and the factory disagree on the value type. Check the key id and the " + "type argument."
    );

    public static final CodeEntry definitionCycle = entry(
        FindingCode.Family.doc,
        9,
        Severity.error,
        "The definitions form a cycle.",
        "A definition refers back to itself. Break the cycle or inline one definition."
    );

    public static final CodeEntry deriveDuplicate = entry(
        FindingCode.Family.doc,
        10,
        Severity.error,
        "Two derivations share an output or order.",
        "Each output and order slot must be unique. Check the derivation specs."
    );

    public static final CodeEntry definitionMissing = entry(
        FindingCode.Family.doc,
        11,
        Severity.error,
        "A placement names a missing definition.",
        "The placement refers to an undeclared definition. Add it or fix the id."
    );

    public static final CodeEntry noPositions = entry(
        FindingCode.Family.doc,
        12,
        Severity.info,
        "The reader produced no source spans.",
        "The document text is available but no offsets were reported. Rendering falls back to " + "document labels."
    );

    public static final CodeEntry readerFailed = entry(
        FindingCode.Family.doc,
        13,
        Severity.error,
        "The reader or writer failed.",
        "The port could not parse or format the document. Check the text and the port."
    );

    public static final CodeEntry computationFailed = entry(
        FindingCode.Family.ctx,
        1,
        Severity.error,
        "A computation produced no value.",
        "The computation returned no value for its output key. Check the computation and its " + "inputs."
    );

    public static final CodeEntry deriverMissing = entry(
        FindingCode.Family.ctx,
        4,
        Severity.error,
        "A derivation names a missing deriver.",
        "No deriver is registered for the declared id. Check the catalog and the definition."
    );

    public static final CodeEntry deriverFailed = entry(
        FindingCode.Family.ctx,
        5,
        Severity.error,
        "A deriver failed.",
        "The deriver threw or returned no value. Check its inputs and implementation."
    );

    public static final CodeEntry foldRefused = entry(
        FindingCode.Family.ctx,
        6,
        Severity.error,
        "A fold has a refused section.",
        "One section could not be read, so the fold is not total. Check the refused section and " + "the merge policy."
    );

    public static final CodeEntry keyAbsent = entry(
        FindingCode.Family.ctx,
        7,
        Severity.error,
        "A required key is absent.",
        "A context read required a value that was not published. Check the key declaration and " + "the producing unit."
    );

    public static final CodeEntry budgetExhausted = entry(
        FindingCode.Family.eng,
        1,
        Severity.error,
        "The budget ran out.",
        "The attempt stopped before it could conclude. Raise the budget or reduce the read " + "scope."
    );

    public static final CodeEntry snapshotStale = entry(
        FindingCode.Family.eng,
        2,
        Severity.warning,
        "The snapshot is no longer current.",
        "The lease stopped matching the live world. Re-capture the snapshot before retrying."
    );

    public static final CodeEntry designDefect = entry(
        FindingCode.Family.eng,
        4,
        Severity.error,
        "An internal plan invariant failed.",
        "The engine reached a state the plan says is impossible. Check the blueprint build and " + "the plan."
    );

    public static final CodeEntry cancelled = entry(
        FindingCode.Family.eng,
        5,
        Severity.warning,
        "The attempt was cancelled.",
        "The host cancel token fired. The attempt wrote nothing."
    );

    public static final CodeEntry bundleTruncated = entry(
        FindingCode.Family.dia,
        1,
        Severity.warning,
        "The bundle was truncated.",
        "Caps or the info floor omitted findings. Check the options and the elided and filtered " + "counts."
    );

    public static final CodeEntry remedyFailed = entry(
        FindingCode.Family.rmd,
        1,
        Severity.error,
        "A remedy provider threw.",
        "The provider failed while solving. Check the provider and its inputs."
    );

    public static final CodeEntry remedyMalformed = entry(
        FindingCode.Family.rmd,
        2,
        Severity.error,
        "A provider returned a malformed remedy.",
        "The remedy value is incomplete or inconsistent. Check the provider's output."
    );

    public static final CodeEntry extensionFailed = entry(
        FindingCode.Family.spi,
        1,
        Severity.error,
        "An extension threw or returned a malformed finding.",
        "The engine caught a failure from an extension. Check the extension and its inputs."
    );

    public static final CodeEntry catalogDuplicate = entry(
        FindingCode.Family.spi,
        3,
        Severity.error,
        "A catalog build found a duplicate entry.",
        "One id was added twice with different values. Keep one registration."
    );

    public static final List<CodeEntry> all = List.of(
        locusUnmapped,
        cuboidOffLattice,
        unreadableCell,
        footprintClipped,
        nestedCut,
        unreadableRead,
        expected,
        negationMatched,
        thresholdImpossible,
        factoryUnknown,
        factoryBad,
        envelopeOpen,
        claimConflict,
        ledgerUnavailable,
        ledgerMoved,
        bindingInvalid,
        boundEmpty,
        blueprintInvalid,
        baysOverlap,
        bayOutside,
        duplicateId,
        coverageInvalid,
        anchorInvalid,
        planInvalid,
        derivationInvalid,
        seamOutside,
        footprintOutside,
        subjectMismatch,
        checkReadsDerivation,
        focusOutside,
        schemaVersion,
        documentField,
        documentFactory,
        documentDuplicate,
        placementOutside,
        bayUnresolved,
        keyUndeclared,
        keyType,
        definitionCycle,
        deriveDuplicate,
        definitionMissing,
        noPositions,
        readerFailed,
        computationFailed,
        deriverMissing,
        deriverFailed,
        foldRefused,
        keyAbsent,
        budgetExhausted,
        snapshotStale,
        designDefect,
        cancelled,
        bundleTruncated,
        remedyFailed,
        remedyMalformed,
        extensionFailed,
        catalogDuplicate
    );

    private Codes() {}

    private static CodeEntry entry(
        FindingCode.Family family,
        int number,
        Severity severity,
        String summary,
        String body
    ) {
        FindingCode code = FindingCode.of(family, number);
        Explanation explanation = new Explanation(
            code,
            summary,
            body,
            List.of(NamespacedId.of("structurecumulus", "modules/" + topic(family)))
        );
        return new CodeEntry(code, severity, explanation);
    }

    private static String topic(FindingCode.Family family) {
        return switch (family) {
            case spc -> "space";
            case occ -> "world";
            case exp, bpt, chk -> "structure";
            case clm -> "claim";
            case doc -> "document";
            case ctx -> "context";
            case eng -> "engine";
            case dia -> "finding";
            case rmd -> "remedy";
            case spi -> "spi";
            case mc -> "adapter";
        };
    }
}
