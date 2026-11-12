package dev.vfyjxf.cumulus.mc.pref;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Placement;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * One saved build context: the blueprint id, the siting, the item-source form and the removal policy a
 * preview session held (design 15 §13). The value is explicit user data: it is never evicted by the
 * remembered-siting LRU, and only {@code /cumulus build preset save}/{@code delete} writes it.
 *
 * <p>Applying a preset is the plain build command with every argument typed out (D-99): the stored
 * pose is the request, and the executor validates the live world before it writes anything.
 */
public record BuildPreset(
    String name,
    NamespacedId blueprintId,
    Siting siting,
    Source source,
    RemovalPolicy removalPolicy
) {

    /** The stored preset shape; a tag of another version is skipped rather than guessed at. */
    public static final int formatVersion = 1;
    /** The per-player bound; a full store refuses the next new name instead of evicting one (D-99). */
    public static final int capacity = 32;
    /** One name's bound; a longer name is refused by the constructor. */
    public static final int maxNameLength = 32;

    public BuildPreset {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(blueprintId, "blueprintId");
        Objects.requireNonNull(siting, "siting");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(removalPolicy, "removalPolicy");
        if (name.isBlank() || name.length() > maxNameLength || !name.strip().equals(name)) {
            throw new IllegalArgumentException(
                "a preset name is 1.." + maxNameLength + " characters without surrounding whitespace: " + name
            );
        }
    }

    /** The chat line {@code list} prints: the pose and the source a later apply would use. */
    public String text() {
        return name + ": " + blueprintId.text() + " at " + pose(siting) + " [" + source.text() + "], removal "
                + removalPolicy.key();
    }

    /** The pose as the chat writes it: the origin and the orientation's ordinal (the stored form). */
    public static String pose(Siting siting) {
        return cellText(siting.origin()) + " orientation " + siting.placement().orientation().ordinal();
    }

    /** One cell as the chat writes it; the preset's own text never prints a record's toString. */
    public static String cellText(LatticePoint cell) {
        return "(" + cell.x() + "," + cell.y() + "," + cell.z() + ")";
    }

    /**
     * Where a preset's blocks come from: the build command's four source forms (design 14 §12). The
     * form is stored, not a resolved source: a container or a registered kind is resolved against the
     * applying player's level at apply time, so a moved container refuses instead of building from
     * somewhere else.
     */
    public record Source(Kind kind, @Nullable LatticePoint container, @Nullable NamespacedId provider) {

        public enum Kind {
            creative, player, container, registered
        }

        public Source {
            Objects.requireNonNull(kind, "kind");
            if ((kind == Kind.container) != (container != null) || (kind == Kind.registered) != (provider != null)) {
                throw new IllegalArgumentException("a " + kind + " source carries exactly its own target: " + kind);
            }
        }

        public static Source creative() {
            return new Source(Kind.creative, null, null);
        }

        public static Source player() {
            return new Source(Kind.player, null, null);
        }

        public static Source container(LatticePoint position) {
            return new Source(Kind.container, Objects.requireNonNull(position, "position"), null);
        }

        public static Source registered(NamespacedId id) {
            return new Source(Kind.registered, null, Objects.requireNonNull(id, "id"));
        }

        /** The form as the command writes it: {@code creative}, {@code player}, a position or a kind id. */
        public String text() {
            return switch (kind) {
                case creative -> "creative";
                case player -> "player";
                case container -> "container " + cellText(Objects.requireNonNull(container, "container"));
                case registered -> Objects.requireNonNull(provider, "provider").text();
            };
        }
    }

    /** The stored form: deterministic field order, with the shape version every preset tag carries. */
    static CompoundTag write(BuildPreset preset) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", formatVersion);
        tag.putString("Name", preset.name());
        tag.putString("Blueprint", preset.blueprintId().text());
        tag.put("Siting", sitingTag(preset.siting()));
        tag.put("Source", sourceTag(preset.source()));
        tag.putString("Removal", preset.removalPolicy().key());
        return tag;
    }

    /** One preset back, or null when the tag is malformed or its shape version is unknown. */
    static @Nullable BuildPreset read(CompoundTag tag) {
        if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != formatVersion) {
            return null;
        }
        NamespacedId blueprint = NamespacedId.parse(tag.getString("Blueprint"));
        Siting siting = siting(tag.getCompound("Siting"));
        Source source = source(tag.getCompound("Source"));
        RemovalPolicy removal = RemovalPolicy.parse(tag.getString("Removal"));
        if (blueprint == null || siting == null || source == null || removal == null) {
            return null;
        }
        try {
            return new BuildPreset(tag.getString("Name"), blueprint, siting, source, removal);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private static CompoundTag sitingTag(Siting siting) {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("Origin", point(siting.origin()));
        tag.putInt("Orientation", siting.placement().orientation().ordinal());
        tag.putIntArray("Offset", point(siting.placement().offset()));
        return tag;
    }

    private static @Nullable Siting siting(CompoundTag tag) {
        LatticePoint origin = point(tag.getIntArray("Origin"));
        LatticePoint offset = point(tag.getIntArray("Offset"));
        if (origin == null || offset == null) {
            return null;
        }
        int ordinal = tag.getInt("Orientation");
        if (ordinal < 0 || ordinal >= Orientation.all().size()) {
            return null;
        }
        return Siting.of(origin, new Placement(new Orientation(ordinal), offset));
    }

    private static CompoundTag sourceTag(Source source) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Kind", source.kind().name());
        if (source.container() != null) {
            tag.putIntArray("Container", point(source.container()));
        }
        if (source.provider() != null) {
            tag.putString("Provider", source.provider().text());
        }
        return tag;
    }

    private static @Nullable Source source(CompoundTag tag) {
        Source.Kind kind = kind(tag.getString("Kind"));
        if (kind == null) {
            return null;
        }
        try {
            return switch (kind) {
                case creative -> Source.creative();
                case player -> Source.player();
                case container -> {
                    LatticePoint container = point(tag.getIntArray("Container"));
                    yield container == null ? null : Source.container(container);
                }
                case registered -> {
                    NamespacedId provider = NamespacedId.parse(tag.getString("Provider"));
                    yield provider == null ? null : Source.registered(provider);
                }
            };
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private static Source.@Nullable Kind kind(String name) {
        for (Source.Kind kind : Source.Kind.values()) {
            if (kind.name().equals(name)) {
                return kind;
            }
        }
        return null;
    }

    private static int[] point(LatticePoint cell) {
        return new int[]{cell.x(), cell.y(), cell.z()};
    }

    private static @Nullable LatticePoint point(int[] values) {
        if (values.length != 3) {
            return null;
        }
        if (!LatticePoint.inLattice(values[0], values[1], values[2])) {
            return null;
        }
        return LatticePoint.of(values[0], values[1], values[2]);
    }
}
