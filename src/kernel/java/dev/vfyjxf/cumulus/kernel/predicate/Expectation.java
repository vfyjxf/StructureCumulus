package dev.vfyjxf.cumulus.kernel.predicate;

import dev.vfyjxf.cumulus.kernel.base.ReadScope;

/** One test over the subject a focus bound; total, re-entrant and stateless. */
public interface Expectation<T> {

    Reply test(ExpectationContext<T> ctx);

    /** The one subject type this test accepts; {@code Check} pairs it with a focus of the same type. */
    Class<T> subject();

    default ReadScope reads() {
        return ReadScope.none();
    }

    default ExpectationMeta meta() {
        return ExpectationMeta.anonymous();
    }
}
