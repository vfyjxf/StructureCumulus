package dev.vfyjxf.cumulus.kernel.predicate;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.base.Outcome;
import dev.vfyjxf.cumulus.kernel.base.ReadScope;
import dev.vfyjxf.cumulus.kernel.base.Stage;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/** The one place a focus and an expectation meet: one bay, one subject type, one reply. */
public record Check<T>(NamespacedId id, Stage stage, NamespacedId bay, Focus<T> focus,
                       Expectation<T> expectation, @Nullable String description) {

    public Check {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(bay, "SC-CHK-0107: a check belongs to exactly one bay");
        Objects.requireNonNull(focus, "focus");
        Objects.requireNonNull(expectation, "expectation");
        if (!focus.subject().equals(expectation.subject())) {
            throw new IllegalArgumentException("SC-CHK-0101: focus " + focus.subject().getName()
                    + " cannot be paired with expectation " + expectation.subject().getName());
        }
    }

    public ReadScope reads(Cuboid bayCells) {
        return focus.reads(bayCells).withKeys(expectation.meta().requires());
    }

    public CheckResult run(FocusContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        Outcome<T> outcome = focus.take(ctx);
        if (outcome instanceof Outcome.Found<T> found) {
            Reply reply = expectation.test(ExpectationContext.of(ctx, found.value(), found.subjects()));
            return new CheckResult(reply, found.subjects(), found.notes());
        }
        Outcome.Missing<T> missing = (Outcome.Missing<T>) outcome;
        return new CheckResult(Reply.unreadable(focus.describeMissing()), List.of(), missing.findings());
    }
}
