package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.base.Stage;
import java.util.Objects;

/** The handle a terminal {@code Deferred} or {@code Undecided} hands the scheduler; it is never reused. */
public record RetryToken(Ticket ticket, Stage stage) {

    public RetryToken {
        Objects.requireNonNull(ticket, "ticket");
        Objects.requireNonNull(stage, "stage");
    }

    public static RetryToken of(Ticket ticket, Stage stage) {
        return new RetryToken(ticket, stage);
    }

    public RetryToken next() {
        return new RetryToken(ticket.next(), stage);
    }
}
