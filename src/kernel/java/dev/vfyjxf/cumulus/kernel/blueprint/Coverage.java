package dev.vfyjxf.cumulus.kernel.blueprint;

import dev.vfyjxf.cumulus.kernel.claim.ClaimScope;

/** What a MATCH asserts about a bay's cells; independent of what the match owns. */
public enum Coverage {
    CUBIC,
    SUBJECT,
    PRESENCE;

    /** The legal {@code coverage} × {@code scope} rows; any other pair is {@code SC-CHK-0106}. */
    public boolean accepts(ClaimScope scope) {
        return switch (this) {
            case CUBIC -> true;
            case SUBJECT -> !(scope instanceof ClaimScope.Owned);
            case PRESENCE -> scope instanceof ClaimScope.Audit || scope instanceof ClaimScope.Declared;
        };
    }
}
