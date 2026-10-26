package dev.vfyjxf.cumulus.kernel.core;

import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A stable finding identity: {@code SC-<FAMILY>-<NNNN>}. */
public record FindingCode(Family family, int number) implements Comparable<FindingCode> {

    private static final Pattern TEXT = Pattern.compile("SC-([A-Z]{2,6})-(\\d{4})");

    public enum Family {
        SPC, OCC, EXP, CLM, BPT, CHK, DOC, CTX, ENG, DIA, RMD, SPI, MC;

        public String subject() {
            return switch (this) {
                case SPC -> "space and frames";
                case OCC -> "occupants and snapshots";
                case EXP -> "expectations";
                case CLM -> "claims and the ledger";
                case BPT -> "blueprint shape";
                case CHK -> "checks and plans";
                case DOC -> "documents and compilation";
                case CTX -> "context and derivations";
                case ENG -> "the attempt";
                case DIA -> "the finding layer";
                case RMD -> "remedies";
                case SPI -> "catalogs and extensions";
                case MC -> "the Minecraft adapter";
            };
        }
    }

    public FindingCode {
        Objects.requireNonNull(family, "family");
        if (number < 1 || number > 9999) {
            throw new IllegalArgumentException("number must be 1..9999");
        }
    }

    public static FindingCode of(Family family, int number) {
        return new FindingCode(family, number);
    }

    public static FindingCode of(String text) {
        FindingCode code = parse(text);
        if (code == null) {
            throw new IllegalArgumentException("malformed finding code: " + text);
        }
        return code;
    }

    public static @Nullable FindingCode parse(@Nullable String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = TEXT.matcher(text);
        if (!matcher.matches()) {
            return null;
        }
        Family family;
        try {
            family = Family.valueOf(matcher.group(1));
        } catch (IllegalArgumentException rejected) {
            return null;
        }
        int number;
        try {
            number = Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException rejected) {
            return null;
        }
        if (number < 1 || number > 9999) {
            return null;
        }
        return new FindingCode(family, number);
    }

    public String text() {
        return "SC-" + family.name() + "-" + String.format(Locale.ROOT, "%04d", number);
    }

    @Override
    public int compareTo(FindingCode other) {
        int byFamily = family.compareTo(other.family);
        return byFamily != 0 ? byFamily : Integer.compare(number, other.number);
    }

    @Override
    public String toString() {
        return text();
    }
}
