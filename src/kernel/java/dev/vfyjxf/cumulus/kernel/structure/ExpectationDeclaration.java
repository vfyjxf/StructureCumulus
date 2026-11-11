package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.world.TraitKey;

import java.util.List;
import java.util.Objects;

/**
 * An expectation's declaration as a pure value (D-91): the remedy module reads payloads from here,
 * never from finding text. The port is total; a custom body describes as {@link Undescribable}, and a
 * remedy over an undescribable expectation is advisory, never machineApplicable.
 */
public sealed interface ExpectationDeclaration {

    /** The block leaf: its type. The leaf declares no properties; those are the materializer's. */
    record BlockLeaf(NamespacedId type) implements ExpectationDeclaration {
        public BlockLeaf {
            Objects.requireNonNull(type, "type");
        }
    }

    /** A trait or traitAbsent leaf: its key; {@code present == false} is the traitAbsent arm. */
    record TraitLeaf(TraitKey<?> key, boolean present) implements ExpectationDeclaration {
        public TraitLeaf {
            Objects.requireNonNull(key, "key");
        }
    }

    /** The host leaf: its host-type token and its mismatch and absent codes. */
    record HostLeaf(NamespacedId type, FindingCode mismatchCode, FindingCode absentCode)
            implements
                ExpectationDeclaration {
        public HostLeaf {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(mismatchCode, "mismatchCode");
            Objects.requireNonNull(absentCode, "absentCode");
        }
    }

    /** The marker leaves, which carry no payload. */
    enum MarkerLeaf implements ExpectationDeclaration {
        air, anyOccupant, empty, nonFluid, envelopeAir
    }

    /** A junction, negation, sequence or per-cell composite, with its children described. */
    record Composite(CompositeKind kind, List<ExpectationDeclaration> children) implements ExpectationDeclaration {
        public Composite {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(children, "children");
            if (children.isEmpty()) {
                throw new IllegalArgumentException("a composite needs at least one child");
            }
            children = List.copyOf(children);
        }
    }

    enum CompositeKind {
        allOf, anyOf, not, sequence, eachCell
    }

    /** A threshold composite: its kind, its count and its children described. */
    record Threshold(ThresholdKind kind, int count, List<ExpectationDeclaration> children)
            implements
                ExpectationDeclaration {
        public Threshold {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(children, "children");
            if (count < 0) {
                throw new IllegalArgumentException("count >= 0");
            }
            if (children.isEmpty()) {
                throw new IllegalArgumentException("a threshold needs at least one child");
            }
            children = List.copyOf(children);
        }
    }

    enum ThresholdKind {
        atLeast, atMost, exactly
    }

    /** A custom or otherwise opaque expectation: the kernel cannot know what it expects. */
    record Undescribable() implements ExpectationDeclaration {}
}
