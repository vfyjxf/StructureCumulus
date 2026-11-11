package dev.vfyjxf.cumulus.kernel.core;

import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A stable finding identity: {@code SC-<FAMILY>-<NNNN>}. */
public record FindingCode(Family family, int number) implements Comparable<FindingCode> {

    private static final Pattern textPattern = Pattern.compile("SC-([A-Z]{2,6})-(\\d{4})");

    public enum Family {
        spc("SPC"), occ("OCC"), exp("EXP"), clm("CLM"), bpt("BPT"), chk("CHK"), doc("DOC"), ctx("CTX"), eng("ENG"), dia(
            "DIA"
        ), rmd("RMD"), spi("SPI"), set("SET"), mc("MC"), bld("BLD");

        private final String tag;

        Family(String tag) {
            this.tag = tag;
        }

        private String tag() {
            return tag;
        }

        private static @Nullable Family fromTag(String tag) {
            for (Family family : values()) {
                if (family.tag.equals(tag)) {
                    return family;
                }
            }
            return null;
        }

        public String subject() {
            return switch (this) {
                case spc -> "space and frames";
                case occ -> "occupants and snapshots";
                case exp -> "expectations";
                case clm -> "claims and the ledger";
                case bpt -> "blueprint shape";
                case chk -> "checks and plans";
                case doc -> "documents and compilation";
                case ctx -> "context and derivations";
                case eng -> "the attempt";
                case dia -> "the finding layer";
                case rmd -> "remedies";
                case spi -> "catalogs and extensions";
                case set -> "structure sets and satellites";
                case mc -> "the Minecraft adapter";
                case bld -> "the host build executor";
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
        Matcher matcher = textPattern.matcher(text);
        if (!matcher.matches()) {
            return null;
        }
        Family family = Family.fromTag(matcher.group(1));
        if (family == null) {
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
        return "SC-" + family.tag() + "-" + String.format(Locale.ROOT, "%04d", number);
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
