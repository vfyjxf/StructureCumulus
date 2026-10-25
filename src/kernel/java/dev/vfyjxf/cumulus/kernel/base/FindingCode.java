package dev.vfyjxf.cumulus.kernel.base;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

public record FindingCode(Family family, int number) implements Comparable<FindingCode> {

    public enum Family {
        SPC("space"),
        OCC("occupants"),
        EXP("expectations"),
        FOC("focuses"),
        CLM("claims"),
        BPT("blueprint"),
        BAY("bays"),
        CHK("checks"),
        DOC("documents"),
        ENG("engine"),
        CTX("context"),
        DIA("findings"),
        RMD("remedies"),
        SPI("spi"),
        MC("adapter");

        private final String subject;

        Family(String subject) {
            this.subject = subject;
        }

        public String subject() {
            return subject;
        }
    }

    public FindingCode {
        if (number < 1 || number > 9999) {
            throw new IllegalArgumentException("number must be within 1..9999: " + number);
        }
    }

    public static FindingCode of(Family family, int number) {
        return new FindingCode(family, number);
    }

    public static FindingCode of(String text) {
        FindingCode parsed = parse(text);
        if (parsed == null) {
            throw new IllegalArgumentException("not a finding code: " + text);
        }
        return parsed;
    }

    public static @Nullable FindingCode parse(String text) {
        if (!text.startsWith("SC-")) {
            return null;
        }
        int dash = text.indexOf('-', 3);
        if (dash < 0) {
            return null;
        }
        String familyName = text.substring(3, dash);
        String digits = text.substring(dash + 1);
        if (digits.length() != 4) {
            return null;
        }
        for (Family family : Family.values()) {
            if (!family.name().equals(familyName)) {
                continue;
            }
            for (int i = 0; i < digits.length(); i++) {
                if (digits.charAt(i) < '0' || digits.charAt(i) > '9') {
                    return null;
                }
            }
            int number = Integer.parseInt(digits);
            return number >= 1 ? new FindingCode(family, number) : null;
        }
        return null;
    }

    public String text() {
        return "SC-" + family.name() + "-" + String.format(Locale.ROOT, "%04d", number);
    }

    @Override
    public int compareTo(FindingCode other) {
        int byFamily = Integer.compare(family.ordinal(), other.family.ordinal());
        return byFamily != 0 ? byFamily : Integer.compare(number, other.number);
    }

    @Override
    public String toString() {
        return text();
    }
}
