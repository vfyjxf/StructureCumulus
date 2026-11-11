package dev.vfyjxf.cumulus.mc.pref;

import dev.vfyjxf.cumulus.StructureCumulus;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Heading;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Placement;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The per-level preference store: each player's explicit settings, the remembered siting choices and
 * the per-part pin memories, keyed by player UUID in this dimension's data storage. The world owns the
 * instance through {@code DimensionDataStorage}, so a read never holds it across an unload.
 */
public final class PreferencesSavedData extends SavedData {

    /** The data file id inside a dimension's data folder. */
    public static final String fileId = "structurecumulus_preferences";
    /** The stored format; a version this code does not know is dropped rather than guessed at. */
    public static final int formatVersion = 1;

    private static final SavedData.Factory<PreferencesSavedData> factory = new SavedData.Factory<>(
        PreferencesSavedData::new,
        PreferencesSavedData::load
    );

    private final Map<UUID, PlayerPreferences> players = new HashMap<>();

    PreferencesSavedData() {}

    /**
     * This level's store; {@code ServerState.openLevel} warms it at {@code LevelEvent.Load}, so the
     * command and preview paths always find it created inside the level's own lifecycle.
     */
    public static PreferencesSavedData get(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        return level.getDataStorage().computeIfAbsent(factory, fileId);
    }

    /** The player's effective preferences: the config defaults, the explicit settings and the memory. */
    public BuildPreferences preferences(UUID player) {
        Objects.requireNonNull(player, "player");
        PlayerPreferences stored = players.get(player);
        if (stored == null) {
            return PrefConfig.defaults();
        }
        BuildPreferences base = stored.settings == null ? PrefConfig.defaults() : stored.settings;
        return base.withRemembered(stored.remembered);
    }

    /** Replaces the explicit settings; the memory moves only through {@link #remember}. */
    public void set(UUID player, BuildPreferences settings) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(settings, "settings");
        PlayerPreferences stored = players.computeIfAbsent(player, ignored -> new PlayerPreferences());
        stored.settings = BuildPreferences
                .of(settings.orientationMode(), settings.fixedHeading(), settings.removalPolicy(), settings.weights());
        setDirty();
    }

    public void remember(UUID player, NamespacedId blueprint, Siting choice) {
        remember(player, blueprint, choice, PrefConfig.rememberedCapacity());
    }

    /** Appends one implicit choice; the history keeps the newest {@code capacity} blueprints. */
    public void remember(UUID player, NamespacedId blueprint, Siting choice, int capacity) {
        Objects.requireNonNull(player, "player");
        PlayerPreferences stored = players.computeIfAbsent(player, ignored -> new PlayerPreferences());
        stored.remembered = BuildPreferences.remember(stored.remembered, blueprint, choice, capacity);
        setDirty();
    }

    /** 2.4: the part memory is keyed {@code (player, blueprint, part)} and never migrates. */
    public void rememberPart(UUID player, NamespacedId blueprint, NamespacedId part, LatticePoint cell) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(part, "part");
        Objects.requireNonNull(cell, "cell");
        PlayerPreferences stored = players.computeIfAbsent(player, ignored -> new PlayerPreferences());
        stored.partPins.remove(new Part(blueprint, part));
        stored.partPins.put(new Part(blueprint, part), cell);
        int capacity = PrefConfig.rememberedCapacity();
        while (stored.partPins.size() > capacity) {
            Part eldest = stored.partPins.keySet().iterator().next();
            stored.partPins.remove(eldest);
        }
        setDirty();
    }

    public @Nullable LatticePoint partPin(UUID player, NamespacedId blueprint, NamespacedId part) {
        PlayerPreferences stored = players.get(player);
        return stored == null ? null : stored.partPins.get(new Part(blueprint, part));
    }

    /** Every remembered part cell, grouped by blueprint; {@code /cumulus prefs show} lists them. */
    public Map<NamespacedId, Map<NamespacedId, LatticePoint>> partPins(UUID player) {
        PlayerPreferences stored = players.get(player);
        if (stored == null) {
            return Collections.emptyMap();
        }
        Map<NamespacedId, Map<NamespacedId, LatticePoint>> grouped = new TreeMap<>();
        for (Map.Entry<Part, LatticePoint> pin : stored.partPins.entrySet()) {
            grouped.computeIfAbsent(pin.getKey().blueprint(), ignored -> new TreeMap<>())
                    .put(pin.getKey().part(), pin.getValue());
        }
        return Collections.unmodifiableMap(grouped);
    }

    /** Every remembered part cell of one blueprint, in insertion order. */
    public Map<NamespacedId, LatticePoint> partPins(UUID player, NamespacedId blueprint) {
        PlayerPreferences stored = players.get(player);
        if (stored == null) {
            return Collections.emptyMap();
        }
        Map<NamespacedId, LatticePoint> pins = new LinkedHashMap<>();
        for (Map.Entry<Part, LatticePoint> pin : stored.partPins.entrySet()) {
            if (pin.getKey().blueprint().equals(blueprint)) {
                pins.put(pin.getKey().part(), pin.getValue());
            }
        }
        return Collections.unmodifiableMap(pins);
    }

    /** Clears one blueprint's memory and part pins, or every player's when the blueprint is null. */
    public void forget(UUID player, @Nullable NamespacedId blueprint) {
        PlayerPreferences stored = players.get(player);
        if (stored == null) {
            return;
        }
        if (blueprint == null) {
            stored.remembered = Map.of();
            stored.partPins.clear();
        } else {
            Map<NamespacedId, ChoiceHistory> remembered = new LinkedHashMap<>(stored.remembered);
            remembered.remove(blueprint);
            stored.remembered = remembered;
            stored.partPins.keySet().removeIf(part -> part.blueprint().equals(blueprint));
        }
        setDirty();
    }

    /** How many blueprints this player remembers; {@code /cumulus prefs show} reports it. */
    public int rememberedBlueprints(UUID player) {
        PlayerPreferences stored = players.get(player);
        return stored == null ? 0 : stored.remembered.size();
    }

    public int partPinCount(UUID player) {
        PlayerPreferences stored = players.get(player);
        return stored == null ? 0 : stored.partPins.size();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Version", formatVersion);
        ListTag rows = new ListTag();
        for (Map.Entry<UUID, PlayerPreferences> entry : new TreeMap<>(players).entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putUUID("Id", entry.getKey());
            PlayerPreferences stored = entry.getValue();
            if (stored.settings != null) {
                row.put("Settings", settingsTag(stored.settings));
            }
            ListTag remembered = new ListTag();
            for (Map.Entry<NamespacedId, ChoiceHistory> history : stored.remembered.entrySet()) {
                CompoundTag choice = new CompoundTag();
                choice.putString("Blueprint", history.getKey().text());
                ListTag choices = new ListTag();
                for (Siting siting : history.getValue().recent()) {
                    choices.add(sitingTag(siting));
                }
                choice.put("Choices", choices);
                remembered.add(choice);
            }
            row.put("Remembered", remembered);
            ListTag pins = new ListTag();
            for (Map.Entry<Part, LatticePoint> pin : stored.partPins.entrySet()) {
                CompoundTag entryTag = new CompoundTag();
                entryTag.putString("Blueprint", pin.getKey().blueprint().text());
                entryTag.putString("Part", pin.getKey().part().text());
                entryTag.putIntArray("Cell", point(pin.getValue()));
                pins.add(entryTag);
            }
            row.put("PartPins", pins);
            rows.add(row);
        }
        tag.put("Players", rows);
        return tag;
    }

    static PreferencesSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PreferencesSavedData data = new PreferencesSavedData();
        int version = tag.contains("Version") ? tag.getInt("Version") : 0;
        if (version != formatVersion) {
            StructureCumulus.logger.warn(
                "preferences: dropping a format {} store; this build writes format {}",
                version,
                formatVersion
            );
            return data;
        }
        ListTag rows = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int index = 0; index < rows.size(); index++) {
            CompoundTag row = rows.getCompound(index);
            if (!row.hasUUID("Id")) {
                continue;
            }
            UUID player = row.getUUID("Id");
            PlayerPreferences stored = new PlayerPreferences();
            try {
                if (row.contains("Settings")) {
                    stored.settings = settings(row.getCompound("Settings"));
                }
                stored.remembered = remembered(row.getList("Remembered", Tag.TAG_COMPOUND));
                stored.partPins = partPins(row.getList("PartPins", Tag.TAG_COMPOUND));
            } catch (RuntimeException malformed) {
                StructureCumulus.logger
                        .warn("preferences: dropping the entry of {}: {}", player, malformed.getMessage());
                continue;
            }
            if (stored.settings != null || !stored.remembered.isEmpty() || !stored.partPins.isEmpty()) {
                data.players.put(player, stored);
            }
        }
        return data;
    }

    private static CompoundTag settingsTag(BuildPreferences settings) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Orientation", settings.orientationMode().key());
        tag.putString("Heading", settings.fixedHeading() == null ? "" : settings.fixedHeading().name());
        tag.putString("Removal", settings.removalPolicy().key());
        ListTag weights = new ListTag();
        for (PlacementWeight weight : settings.weights()) {
            weights.add(StringTag.valueOf(weight.key()));
        }
        tag.put("Weights", weights);
        return tag;
    }

    private static @Nullable BuildPreferences settings(CompoundTag tag) {
        OrientationMode mode = OrientationMode.parse(tag.getString("Orientation"));
        RemovalPolicy removal = RemovalPolicy.parse(tag.getString("Removal"));
        Heading heading = null;
        String headingName = tag.getString("Heading");
        if (!headingName.isEmpty()) {
            try {
                heading = Heading.valueOf(headingName);
            } catch (IllegalArgumentException unknown) {
                heading = null;
            }
        }
        if (mode == null || removal == null) {
            return null;
        }
        List<PlacementWeight> weights = new ArrayList<>();
        ListTag list = tag.getList("Weights", Tag.TAG_STRING);
        for (int index = 0; index < list.size(); index++) {
            PlacementWeight weight = PlacementWeight.parse(list.getString(index));
            if (weight != null && !weights.contains(weight)) {
                weights.add(weight);
            }
        }
        return BuildPreferences.of(mode, heading, removal, weights);
    }

    private static Map<NamespacedId, ChoiceHistory> remembered(ListTag rows) {
        Map<NamespacedId, ChoiceHistory> remembered = new LinkedHashMap<>();
        for (int index = 0; index < rows.size(); index++) {
            CompoundTag row = rows.getCompound(index);
            NamespacedId blueprint = NamespacedId.parse(row.getString("Blueprint"));
            if (blueprint == null) {
                continue;
            }
            List<Siting> choices = new ArrayList<>();
            ListTag list = row.getList("Choices", Tag.TAG_COMPOUND);
            for (int choice = 0; choice < list.size(); choice++) {
                Siting siting = siting(list.getCompound(choice));
                if (siting != null) {
                    choices.add(siting);
                }
            }
            if (!choices.isEmpty()) {
                remembered.put(blueprint, new ChoiceHistory(choices));
            }
        }
        return remembered;
    }

    private static LinkedHashMap<Part, LatticePoint> partPins(ListTag rows) {
        LinkedHashMap<Part, LatticePoint> pins = new LinkedHashMap<>();
        for (int index = 0; index < rows.size(); index++) {
            CompoundTag row = rows.getCompound(index);
            NamespacedId blueprint = NamespacedId.parse(row.getString("Blueprint"));
            NamespacedId part = NamespacedId.parse(row.getString("Part"));
            LatticePoint cell = point(row.getIntArray("Cell"));
            if (blueprint == null || part == null || cell == null) {
                continue;
            }
            pins.put(new Part(blueprint, part), cell);
        }
        return pins;
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

    /** One player's row: the explicit settings, the choice histories and the part memories. */
    private static final class PlayerPreferences {

        private @Nullable BuildPreferences settings;
        private Map<NamespacedId, ChoiceHistory> remembered = Map.of();
        private LinkedHashMap<Part, LatticePoint> partPins = new LinkedHashMap<>();
    }

    /** The part-memory key: a part id never migrates to another blueprint (2.4). */
    private record Part(NamespacedId blueprint, NamespacedId part) {}
}
