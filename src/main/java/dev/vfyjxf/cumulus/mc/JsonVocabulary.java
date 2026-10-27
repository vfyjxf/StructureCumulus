package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.SeamKind;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** The lowercase document vocabulary and the canonical id text. */
@NullMarked
final class JsonVocabulary {

    private static final Map<String, Orientation> orientations = orientationNames();

    static String idText(NamespacedId id) {
        return id.namespace().equals(NamespacedId.defaultNamespace) ? id.path() : id.text();
    }

    static String coverageName(Coverage coverage) {
        return switch (coverage) {
            case everyCell -> "every_cell";
            case oneSubject -> "one_subject";
            case anySubject -> "any_subject";
        };
    }

    static @Nullable Coverage coverage(String name) {
        return switch (name) {
            case "every_cell" -> Coverage.everyCell;
            case "one_subject" -> Coverage.oneSubject;
            case "any_subject" -> Coverage.anySubject;
            default -> null;
        };
    }

    static String sharingName(Sharing sharing) {
        return switch (sharing) {
            case exclusive -> "exclusive";
            case shared -> "shared";
        };
    }

    static @Nullable Sharing sharing(String name) {
        return switch (name) {
            case "exclusive" -> Sharing.exclusive;
            case "shared" -> Sharing.shared;
            default -> null;
        };
    }

    static String retentionName(Retention retention) {
        return switch (retention) {
            case match -> "match";
            case instance -> "instance";
        };
    }

    static @Nullable Retention retention(String name) {
        return switch (name) {
            case "match" -> Retention.match;
            case "instance" -> Retention.instance;
            default -> null;
        };
    }

    static String seamKindName(SeamKind kind) {
        return switch (kind) {
            case item -> "item";
            case fluid -> "fluid";
            case energy -> "energy";
            case structural -> "structural";
            case custom -> "custom";
        };
    }

    static @Nullable SeamKind seamKind(String name) {
        return switch (name) {
            case "item" -> SeamKind.item;
            case "fluid" -> SeamKind.fluid;
            case "energy" -> SeamKind.energy;
            case "structural" -> SeamKind.structural;
            case "custom" -> SeamKind.custom;
            default -> null;
        };
    }

    static @Nullable Orientation orientation(String name) {
        return orientations.get(name);
    }

    static String orientationName(Orientation orientation) {
        StringBuilder name = new StringBuilder();
        Turn turn = orientation.turn();
        Tilt tilt = orientation.tilt();
        if (turn == Turn.none && tilt == Tilt.level) {
            name.append("identity");
        } else {
            if (turn != Turn.none) {
                name.append(turnName(turn));
            }
            if (tilt != Tilt.level) {
                if (!name.isEmpty()) {
                    name.append('_');
                }
                name.append(tiltName(tilt));
            }
        }
        if (orientation.isMirror()) {
            name.append("_mirror");
        }
        return name.toString();
    }

    private static String turnName(Turn turn) {
        return switch (turn) {
            case none -> "identity";
            case clockwise -> "clockwise";
            case half -> "half";
            case counterclockwise -> "counterclockwise";
        };
    }

    private static String tiltName(Tilt tilt) {
        return switch (tilt) {
            case level -> "level";
            case pitchUp -> "pitch_up";
            case pitchDown -> "pitch_down";
            case rollLeft -> "roll_left";
            case rollRight -> "roll_right";
            case inverted -> "inverted";
        };
    }

    private static Map<String, Orientation> orientationNames() {
        Map<String, Orientation> names = new HashMap<>();
        for (Orientation orientation : Orientation.all()) {
            if (names.put(orientationName(orientation), orientation) != null) {
                throw new IllegalStateException("duplicate orientation name");
            }
        }
        return Map.copyOf(names);
    }

    private JsonVocabulary() {}
}
