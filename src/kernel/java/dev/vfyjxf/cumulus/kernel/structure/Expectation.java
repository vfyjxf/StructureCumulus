package dev.vfyjxf.cumulus.kernel.structure;

/** A total, re-entrant test over one subject type. */
public interface Expectation<T> {

    Reply test(ExpectationContext<T> ctx);

    Class<T> subject();

    default ExpectationMeta meta() {
        return ExpectationMeta.anonymous();
    }

    default ReadScope reads() {
        return ReadScope.none();
    }

    /** The declaration as a value (D-91); anything the kernel cannot see through is undescribable. */
    default ExpectationDeclaration declaration() {
        return new ExpectationDeclaration.Undescribable();
    }
}
