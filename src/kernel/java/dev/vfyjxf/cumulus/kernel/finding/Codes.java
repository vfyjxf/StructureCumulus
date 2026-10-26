package dev.vfyjxf.cumulus.kernel.finding;

import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;

import java.util.List;
/** The shipped code table. */
public final class Codes {

    public static final CodeEntry LOCUS_UNMAPPED = entry(FindingCode.Family.SPC, 1,
            Severity.ERROR,
            "A cell or locus could not be mapped into the requested frame.",
            "A siting or locus map returned no image for the requested frame. Check the placement, "
                    + "the origin and the lattice bounds before retrying.");

    public static final CodeEntry CUBOID_OFF_LATTICE = entry(FindingCode.Family.SPC, 2,
            Severity.ERROR,
            "A box operation would leave the lattice.",
            "A translated box or footprint no longer fits the lattice. Check the offsets and clamp "
                    + "the declaration into the valid range.");

    public static final CodeEntry UNREADABLE_CELL = entry(FindingCode.Family.OCC, 1,
            Severity.ERROR,
            "The cell could not be read from the snapshot.",
            "The view has no readable occupant for the requested cell. Check the capture bounds, "
                    + "the depth and host permission.");

    public static final CodeEntry FOOTPRINT_CLIPPED = entry(FindingCode.Family.OCC, 2,
            Severity.WARNING,
            "An occupant footprint leaves the snapshot.",
            "Only the in-snapshot intersection was compared. Check the capture bounds and the "
                    + "occupant's full body.");

    public static final CodeEntry NESTED_CUT = entry(FindingCode.Family.OCC, 3,
            Severity.ERROR,
            "A nested view was cut by depth or a cap.",
            "The nested view stopped before the checked cells were available. Check the capture "
                    + "depth and element ceiling.");

    public static final CodeEntry EXPECTED = entry(FindingCode.Family.EXP, 1,
            Severity.ERROR,
            "The expected element or state did not match.",
            "The default leaf test refuted the subject. Check the occupant type, properties and "
                    + "state.");

    public static final CodeEntry NEGATION_MATCHED = entry(FindingCode.Family.EXP, 2,
            Severity.ERROR,
            "A negated test matched.",
            "The child test was expected to fail and succeeded. Check the inner expectation or "
                    + "invert the declaration.");

    public static final CodeEntry THRESHOLD_IMPOSSIBLE = entry(FindingCode.Family.EXP, 3,
            Severity.ERROR,
            "The requested threshold cannot be met.",
            "The child count is below or above what the threshold allows. Check the child tests "
                    + "and the count.");

    public static final CodeEntry FACTORY_UNKNOWN = entry(FindingCode.Family.EXP, 4,
            Severity.ERROR,
            "A factory kind is unknown.",
            "The compiler was asked for a factory that is not registered. Check the factory id and "
                    + "the catalog.");

    public static final CodeEntry FACTORY_BAD = entry(FindingCode.Family.EXP, 5,
            Severity.ERROR,
            "A factory returned null or malformed arguments.",
            "The registered factory refused its arguments or returned no value. Check the factory "
                    + "implementation and the document arguments.");

    public static final CodeEntry CLAIM_CONFLICT = entry(FindingCode.Family.CLM, 1,
            Severity.ERROR,
            "Two claims conflict over a cell.",
            "At least one row is exclusive and the owner or siting differs. Check the contested "
                    + "cells and both sharing declarations.");

    public static final CodeEntry LEDGER_UNAVAILABLE = entry(FindingCode.Family.CLM, 2,
            Severity.WARNING,
            "The ledger cannot answer.",
            "The claim view was unavailable at check time. Check the ledger handle and the world "
                    + "scope.");

    public static final CodeEntry LEDGER_MOVED = entry(FindingCode.Family.CLM, 3,
            Severity.ERROR,
            "The ledger revision moved twice.",
            "The compare-and-set lost a second race after a retry. Re-read the ledger and rebuild "
                    + "the settlement.");

    public static final CodeEntry BINDING_INVALID = entry(FindingCode.Family.CLM, 4,
            Severity.ERROR,
            "A settle binding is invalid.",
            "A row names a foreign snapshot, frame, owner, siting or bay. Check the planner input "
                    + "before settling.");

    public static final CodeEntry BOUND_EMPTY = entry(FindingCode.Family.CLM, 5,
            Severity.ERROR,
            "An exclusive bound footprint is empty.",
            "An exclusive bay bound to live subjects owns no cell. Check the bound focus and the "
                    + "subjects it returned.");

    public static final CodeEntry BLUEPRINT_INVALID = entry(FindingCode.Family.BPT, 1,
            Severity.ERROR,
            "A blueprint has no checks or bounds.",
            "The build found nothing to check or a missing bounds declaration. Add at least one "
                    + "check and declare the blueprint bounds.");

    public static final CodeEntry BAYS_OVERLAP = entry(FindingCode.Family.BPT, 2,
            Severity.ERROR,
            "Two bays overlap with an exclusive side.",
            "At least one overlapping bay is exclusive. Split the cells or make both bays shared.");

    public static final CodeEntry BAY_OUTSIDE = entry(FindingCode.Family.BPT, 3,
            Severity.ERROR,
            "A bay is empty or outside the blueprint bounds.",
            "Every bay must be non-empty and contained in the root bounds. Check the bay cells "
                    + "against the declaration.");

    public static final CodeEntry DUPLICATE_ID = entry(FindingCode.Family.BPT, 4,
            Severity.ERROR,
            "A bay, check, seam or anchor id is duplicated.",
            "Identifiers must be unique within their family. Rename one declaration.");

    public static final CodeEntry COVERAGE_INVALID = entry(FindingCode.Family.BPT, 5,
            Severity.ERROR,
            "The coverage or footprint rules failed.",
            "The declared coverage and footprint cannot be satisfied by the checks. Check rules R1 "
                    + "to R4 for this bay.");

    public static final CodeEntry ANCHOR_INVALID = entry(FindingCode.Family.BPT, 6,
            Severity.ERROR,
            "The anchor or orientation policy is invalid.",
            "The anchor role, allowed orientations or upright promise cannot be resolved. Check the "
                    + "anchor property and policy.");

    public static final CodeEntry PLAN_INVALID = entry(FindingCode.Family.BPT, 7,
            Severity.ERROR,
            "A computation plan is cyclic or has no producer.",
            "The section and aggregate order cannot be built. Check computation outputs and "
                    + "derivation references.");

    public static final CodeEntry DERIVATION_INVALID = entry(FindingCode.Family.BPT, 8,
            Severity.ERROR,
            "A derivation order or output is invalid.",
            "The derivation spec names a bad order, a missing output or a duplicate. Check the "
                    + "derivation declarations.");

    public static final CodeEntry SEAM_OUTSIDE = entry(FindingCode.Family.BPT, 9,
            Severity.ERROR,
            "A seam is outside every bay.",
            "Each seam cell must belong to at least one bay. Check the seam declaration against the "
                    + "bay cells.");

    public static final CodeEntry FOOTPRINT_OUTSIDE = entry(FindingCode.Family.BPT, 10,
            Severity.ERROR,
            "A declared footprint is outside the bounds.",
            "The declared cells must stay inside the blueprint bounds. Check the declaration or grow "
                    + "the bounds.");

    public static final CodeEntry SUBJECT_MISMATCH = entry(FindingCode.Family.CHK, 1,
            Severity.ERROR,
            "A check pairs different subject types.",
            "The focus and expectation must name the same subject class. Check the check "
                    + "declaration.");

    public static final CodeEntry CHECK_READS_DERIVATION = entry(FindingCode.Family.CHK, 2,
            Severity.ERROR,
            "A check reads a derivation output.",
            "Checks may read only snapshot facts and check context. Move the value to a deriver or a "
                    + "computation.");

    public static final CodeEntry FOCUS_OUTSIDE = entry(FindingCode.Family.CHK, 3,
            Severity.ERROR,
            "A focus's static cells leave its bay.",
            "The focus declares cells outside the bay it belongs to. Check the focus cells or the "
                    + "bay.");

    public static final CodeEntry SCHEMA_VERSION = entry(FindingCode.Family.DOC, 1,
            Severity.ERROR,
            "The document schema version is unsupported.",
            "The reader found a version this compiler does not accept. Check the schemaVersion "
                    + "field.");

    public static final CodeEntry DOCUMENT_FIELD = entry(FindingCode.Family.DOC, 2,
            Severity.ERROR,
            "A document field is missing or of the wrong kind.",
            "The field does not match the schema. Check its name, kind and position.");

    public static final CodeEntry DOCUMENT_FACTORY = entry(FindingCode.Family.DOC, 3,
            Severity.ERROR,
            "The document names an unknown factory kind.",
            "The compiler has no factory for this declaration. Check the factory id and the "
                    + "registered catalogs.");

    public static final CodeEntry DOCUMENT_DUPLICATE = entry(FindingCode.Family.DOC, 4,
            Severity.ERROR,
            "A document id is duplicated.",
            "Two declarations share one id. Rename one or merge them.");

    public static final CodeEntry PLACEMENT_OUTSIDE = entry(FindingCode.Family.DOC, 5,
            Severity.ERROR,
            "A placement leaves the root bounds.",
            "The placed definition does not fit the declared root. Check the origin, orientation "
                    + "and bounds.");

    public static final CodeEntry BAY_UNRESOLVED = entry(FindingCode.Family.DOC, 6,
            Severity.ERROR,
            "A check names no merged bay.",
            "The check refers to a bay that is not declared or merged. Check the bay id.");

    public static final CodeEntry KEY_UNDECLARED = entry(FindingCode.Family.DOC, 7,
            Severity.ERROR,
            "A spec names an undeclared key.",
            "The key must be declared before it is used. Add the declaration or fix the reference.");

    public static final CodeEntry KEY_TYPE = entry(FindingCode.Family.DOC, 8,
            Severity.ERROR,
            "A key's declared type does not match its producer.",
            "The declaration and the factory disagree on the value type. Check the key id and the "
                    + "type argument.");

    public static final CodeEntry DEFINITION_CYCLE = entry(FindingCode.Family.DOC, 9,
            Severity.ERROR,
            "The definitions form a cycle.",
            "A definition refers back to itself. Break the cycle or inline one definition.");

    public static final CodeEntry DERIVE_DUPLICATE = entry(FindingCode.Family.DOC, 10,
            Severity.ERROR,
            "Two derivations share an output or order.",
            "Each output and order slot must be unique. Check the derivation specs.");

    public static final CodeEntry DEFINITION_MISSING = entry(FindingCode.Family.DOC, 11,
            Severity.ERROR,
            "A placement names a missing definition.",
            "The placement refers to an undeclared definition. Add it or fix the id.");

    public static final CodeEntry NO_POSITIONS = entry(FindingCode.Family.DOC, 12,
            Severity.INFO,
            "The reader produced no source spans.",
            "The document text is available but no offsets were reported. Rendering falls back to "
                    + "document labels.");

    public static final CodeEntry READER_FAILED = entry(FindingCode.Family.DOC, 13,
            Severity.ERROR,
            "The reader or writer failed.",
            "The port could not parse or format the document. Check the text and the port.");

    public static final CodeEntry COMPUTATION_FAILED = entry(FindingCode.Family.CTX, 1,
            Severity.ERROR,
            "A computation produced no value.",
            "The computation returned no value for its output key. Check the computation and its "
                    + "inputs.");

    public static final CodeEntry DERIVER_MISSING = entry(FindingCode.Family.CTX, 4,
            Severity.ERROR,
            "A derivation names a missing deriver.",
            "No deriver is registered for the declared id. Check the catalog and the definition.");

    public static final CodeEntry DERIVER_FAILED = entry(FindingCode.Family.CTX, 5,
            Severity.ERROR,
            "A deriver failed.",
            "The deriver threw or returned no value. Check its inputs and implementation.");

    public static final CodeEntry FOLD_REFUSED = entry(FindingCode.Family.CTX, 6,
            Severity.ERROR,
            "A fold has a refused section.",
            "One section could not be read, so the fold is not total. Check the refused section and "
                    + "the merge policy.");

    public static final CodeEntry KEY_ABSENT = entry(FindingCode.Family.CTX, 7,
            Severity.ERROR,
            "A required key is absent.",
            "A context read required a value that was not published. Check the key declaration and "
                    + "the producing unit.");

    public static final CodeEntry BUDGET_EXHAUSTED = entry(FindingCode.Family.ENG, 1,
            Severity.ERROR,
            "The budget ran out.",
            "The attempt stopped before it could conclude. Raise the budget or reduce the read "
                    + "scope.");

    public static final CodeEntry SNAPSHOT_STALE = entry(FindingCode.Family.ENG, 2,
            Severity.WARNING,
            "The snapshot is no longer current.",
            "The lease stopped matching the live world. Re-capture the snapshot before retrying.");

    public static final CodeEntry DESIGN_DEFECT = entry(FindingCode.Family.ENG, 4,
            Severity.ERROR,
            "An internal plan invariant failed.",
            "The engine reached a state the plan says is impossible. Check the blueprint build and "
                    + "the plan.");

    public static final CodeEntry CANCELLED = entry(FindingCode.Family.ENG, 5,
            Severity.WARNING,
            "The attempt was cancelled.",
            "The host cancel token fired. The attempt wrote nothing.");

    public static final CodeEntry BUNDLE_TRUNCATED = entry(FindingCode.Family.DIA, 1,
            Severity.WARNING,
            "The bundle was truncated.",
            "Caps or the info floor omitted findings. Check the options and the elided and filtered "
                    + "counts.");

    public static final CodeEntry REMEDY_FAILED = entry(FindingCode.Family.RMD, 1,
            Severity.ERROR,
            "A remedy provider threw.",
            "The provider failed while solving. Check the provider and its inputs.");

    public static final CodeEntry REMEDY_MALFORMED = entry(FindingCode.Family.RMD, 2,
            Severity.ERROR,
            "A provider returned a malformed remedy.",
            "The remedy value is incomplete or inconsistent. Check the provider's output.");

    public static final CodeEntry EXTENSION_FAILED = entry(FindingCode.Family.SPI, 1,
            Severity.ERROR,
            "An extension threw or returned a malformed finding.",
            "The engine caught a failure from an extension. Check the extension and its inputs.");

    public static final CodeEntry EXTENSION_UNKNOWN = entry(FindingCode.Family.SPI, 2,
            Severity.ERROR,
            "A document names an unregistered factory.",
            "The catalog has no entry for this factory id. Register it or fix the document.");

    public static final CodeEntry CATALOG_DUPLICATE = entry(FindingCode.Family.SPI, 3,
            Severity.ERROR,
            "A catalog build found a duplicate entry.",
            "One id was added twice with different values. Keep one registration.");

    public static final List<CodeEntry> ALL = List.of(
            LOCUS_UNMAPPED,
            CUBOID_OFF_LATTICE,
            UNREADABLE_CELL,
            FOOTPRINT_CLIPPED,
            NESTED_CUT,
            EXPECTED,
            NEGATION_MATCHED,
            THRESHOLD_IMPOSSIBLE,
            FACTORY_UNKNOWN,
            FACTORY_BAD,
            CLAIM_CONFLICT,
            LEDGER_UNAVAILABLE,
            LEDGER_MOVED,
            BINDING_INVALID,
            BOUND_EMPTY,
            BLUEPRINT_INVALID,
            BAYS_OVERLAP,
            BAY_OUTSIDE,
            DUPLICATE_ID,
            COVERAGE_INVALID,
            ANCHOR_INVALID,
            PLAN_INVALID,
            DERIVATION_INVALID,
            SEAM_OUTSIDE,
            FOOTPRINT_OUTSIDE,
            SUBJECT_MISMATCH,
            CHECK_READS_DERIVATION,
            FOCUS_OUTSIDE,
            SCHEMA_VERSION,
            DOCUMENT_FIELD,
            DOCUMENT_FACTORY,
            DOCUMENT_DUPLICATE,
            PLACEMENT_OUTSIDE,
            BAY_UNRESOLVED,
            KEY_UNDECLARED,
            KEY_TYPE,
            DEFINITION_CYCLE,
            DERIVE_DUPLICATE,
            DEFINITION_MISSING,
            NO_POSITIONS,
            READER_FAILED,
            COMPUTATION_FAILED,
            DERIVER_MISSING,
            DERIVER_FAILED,
            FOLD_REFUSED,
            KEY_ABSENT,
            BUDGET_EXHAUSTED,
            SNAPSHOT_STALE,
            DESIGN_DEFECT,
            CANCELLED,
            BUNDLE_TRUNCATED,
            REMEDY_FAILED,
            REMEDY_MALFORMED,
            EXTENSION_FAILED,
            EXTENSION_UNKNOWN,
            CATALOG_DUPLICATE);

    private Codes() {
    }

    private static CodeEntry entry(FindingCode.Family family, int number, Severity severity,
                                   String summary, String body) {
        FindingCode code = FindingCode.of(family, number);
        Explanation explanation = new Explanation(code, summary, body,
                List.of(NamespacedId.of("structurecumulus", "modules/" + topic(family))));
        return new CodeEntry(code, severity, explanation);
    }

    private static String topic(FindingCode.Family family) {
        return switch (family) {
            case SPC -> "space";
            case OCC -> "world";
            case EXP, BPT, CHK -> "structure";
            case CLM -> "claim";
            case DOC -> "document";
            case CTX -> "context";
            case ENG -> "engine";
            case DIA -> "finding";
            case RMD -> "remedy";
            case SPI -> "spi";
            case MC -> "adapter";
        };
    }
}
