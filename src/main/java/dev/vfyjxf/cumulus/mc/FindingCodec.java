package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.SourceSpan;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.finding.Applicability;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.finding.LabelRole;
import dev.vfyjxf.cumulus.kernel.finding.Note;
import dev.vfyjxf.cumulus.kernel.finding.Severity;
import dev.vfyjxf.cumulus.kernel.finding.Suggestion;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.KeyLocus;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.LocusFrame;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/** NBT codec for the findings stored with one context. */
@NullMarked
final class FindingCodec {

    private static final String findingsKey = "findings";
    private static final String codeKey = "code";
    private static final String severityKey = "severity";
    private static final String phaseKey = "phase";
    private static final String messageKey = "message";
    private static final String labelsKey = "labels";
    private static final String notesKey = "notes";
    private static final String suggestionKey = "suggestion";
    private static final String roleKey = "role";
    private static final String locusKey = "locus";
    private static final String frameKey = "frame";
    private static final String cellsKey = "cells";
    private static final String keyKey = "key";
    private static final String sourceKey = "source";
    private static final String documentKey = "document";
    private static final String startKey = "start";
    private static final String endKey = "end";
    private static final String worldKey = "world";
    private static final String serialKey = "serial";
    private static final String minKey = "min";
    private static final String maxKey = "max";
    private static final String applicabilityKey = "applicability";
    private static final String remedyKey = "remedy";

    static void write(CompoundTag target, FindingBundle bundle) {
        ListTag encoded = new ListTag();
        for (Finding finding : bundle.findings()) {
            encoded.add(write(finding));
        }
        target.put(findingsKey, encoded);
    }

    static List<Finding> read(CompoundTag target) {
        ListTag encoded = target.getList(findingsKey, Tag.TAG_COMPOUND);
        List<Finding> findings = new ArrayList<>(encoded.size());
        for (int index = 0; index < encoded.size(); index++) {
            try {
                findings.add(readFinding(encoded.getCompound(index)));
            } catch (RuntimeException ignored) {
                // A stored finding that cannot be decoded is dropped.
            }
        }
        return List.copyOf(findings);
    }

    private static CompoundTag write(Finding finding) {
        CompoundTag tag = new CompoundTag();
        tag.putString(codeKey, finding.code().text());
        tag.putString(severityKey, finding.severity().name());
        tag.putString(phaseKey, finding.phase().name());
        tag.putString(messageKey, finding.message());
        ListTag labels = new ListTag();
        for (Label label : finding.labels()) {
            labels.add(write(label));
        }
        tag.put(labelsKey, labels);
        ListTag notes = new ListTag();
        for (Note note : finding.notes()) {
            notes.add(write(note));
        }
        tag.put(notesKey, notes);
        if (finding.suggestion() != null) {
            tag.put(suggestionKey, write(finding.suggestion()));
        }
        return tag;
    }

    private static Finding readFinding(CompoundTag tag) {
        FindingCode code = FindingCode.of(requiredString(tag, codeKey));
        Severity severity = Severity.valueOf(requiredString(tag, severityKey));
        Phase phase = Phase.valueOf(requiredString(tag, phaseKey));
        String message = requiredString(tag, messageKey);
        List<Label> labels = readLabels(tag);
        List<Note> notes = readNotes(tag);
        Suggestion suggestion = tag.contains(suggestionKey, Tag.TAG_COMPOUND)
                ? readSuggestion(tag.getCompound(suggestionKey))
                : null;
        return new Finding(code, severity, message, labels, notes, suggestion, phase);
    }

    private static CompoundTag write(Label label) {
        CompoundTag tag = new CompoundTag();
        tag.put(locusKey, write(label.locus()));
        tag.putString(messageKey, label.message());
        tag.putString(roleKey, label.role().name());
        return tag;
    }

    private static List<Label> readLabels(CompoundTag tag) {
        ListTag encoded = tag.getList(labelsKey, Tag.TAG_COMPOUND);
        List<Label> labels = new ArrayList<>(encoded.size());
        for (int index = 0; index < encoded.size(); index++) {
            CompoundTag entry = encoded.getCompound(index);
            labels.add(
                new Label(
                    readLocus(requiredCompound(entry, locusKey)),
                    requiredString(entry, messageKey),
                    LabelRole.valueOf(requiredString(entry, roleKey))
                )
            );
        }
        return List.copyOf(labels);
    }

    private static CompoundTag write(Note note) {
        CompoundTag tag = new CompoundTag();
        tag.putString(messageKey, note.message());
        if (note.locus() != null) {
            tag.put(locusKey, write(note.locus()));
        }
        return tag;
    }

    private static List<Note> readNotes(CompoundTag tag) {
        ListTag encoded = tag.getList(notesKey, Tag.TAG_COMPOUND);
        List<Note> notes = new ArrayList<>(encoded.size());
        for (int index = 0; index < encoded.size(); index++) {
            CompoundTag entry = encoded.getCompound(index);
            Locus locus = entry.contains(locusKey, Tag.TAG_COMPOUND) ? readLocus(entry.getCompound(locusKey)) : null;
            notes.add(new Note(requiredString(entry, messageKey), locus));
        }
        return List.copyOf(notes);
    }

    private static CompoundTag write(Suggestion suggestion) {
        CompoundTag tag = new CompoundTag();
        tag.putString(messageKey, suggestion.message());
        tag.putString(applicabilityKey, suggestion.applicability().name());
        if (suggestion.remedy() != null) {
            tag.putString(remedyKey, suggestion.remedy().text());
        }
        return tag;
    }

    private static Suggestion readSuggestion(CompoundTag tag) {
        NamespacedId remedy = tag.contains(remedyKey, Tag.TAG_STRING)
                ? NamespacedId.parse(tag.getString(remedyKey))
                : null;
        if (tag.contains(remedyKey, Tag.TAG_STRING) && remedy == null) {
            throw new IllegalArgumentException("invalid suggestion remedy");
        }
        return new Suggestion(
            requiredString(tag, messageKey),
            Applicability.valueOf(requiredString(tag, applicabilityKey)),
            remedy
        );
    }

    private static CompoundTag write(Locus locus) {
        CompoundTag tag = new CompoundTag();
        tag.putString(frameKey, locus.frame().name());
        ListTag cells = new ListTag();
        for (Cuboid box : locus.cells().boxes()) {
            cells.add(write(box));
        }
        tag.put(cellsKey, cells);
        if (locus.key() != null) {
            tag.put(keyKey, write(locus.key()));
        }
        if (locus.source() != null) {
            tag.put(sourceKey, write(locus.source()));
        }
        return tag;
    }

    private static Locus readLocus(CompoundTag tag) {
        LocusFrame frame = LocusFrame.valueOf(requiredString(tag, frameKey));
        CuboidSet cells = readCells(tag);
        KeyLocus key = tag.contains(keyKey, Tag.TAG_COMPOUND) ? readKey(tag.getCompound(keyKey)) : null;
        SourceSpan source = tag.contains(sourceKey, Tag.TAG_COMPOUND) ? readSource(tag.getCompound(sourceKey)) : null;
        return new Locus(frame, cells, key, source);
    }

    private static CompoundTag write(Cuboid box) {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray(minKey, point(box.min()));
        tag.putIntArray(maxKey, point(box.max()));
        return tag;
    }

    private static CuboidSet readCells(CompoundTag tag) {
        ListTag encoded = tag.getList(cellsKey, Tag.TAG_COMPOUND);
        List<Cuboid> boxes = new ArrayList<>(encoded.size());
        for (int index = 0; index < encoded.size(); index++) {
            boxes.add(readBox(encoded.getCompound(index)));
        }
        return CuboidSet.of(boxes);
    }

    private static Cuboid readBox(CompoundTag tag) {
        return Cuboid.of(point(tag, minKey), point(tag, maxKey));
    }

    private static CompoundTag write(KeyLocus key) {
        CompoundTag tag = new CompoundTag();
        tag.putString(worldKey, key.instance().world().id().text());
        tag.putLong(serialKey, key.instance().serial());
        tag.putString(keyKey, key.key().text());
        return tag;
    }

    private static KeyLocus readKey(CompoundTag tag) {
        NamespacedId world = requiredId(tag, worldKey);
        long serial = requiredLong(tag, serialKey);
        NamespacedId key = requiredId(tag, keyKey);
        return new KeyLocus(new InstanceId(World.of(world), serial), key);
    }

    private static CompoundTag write(SourceSpan source) {
        CompoundTag tag = new CompoundTag();
        tag.putString(documentKey, source.document().text());
        tag.putInt(startKey, source.start());
        tag.putInt(endKey, source.end());
        return tag;
    }

    private static SourceSpan readSource(CompoundTag tag) {
        return new SourceSpan(requiredId(tag, documentKey), requiredInt(tag, startKey), requiredInt(tag, endKey));
    }

    private static int[] point(LatticePoint point) {
        return new int[]{point.x(), point.y(), point.z()};
    }

    private static LatticePoint point(CompoundTag tag, String key) {
        int[] values = tag.getIntArray(key);
        if (values.length != 3) {
            throw new IllegalArgumentException(key + " must hold three ints");
        }
        return LatticePoint.of(values[0], values[1], values[2]);
    }

    private static int requiredInt(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_INT)) {
            throw new IllegalArgumentException("missing int " + key);
        }
        return tag.getInt(key);
    }

    private static long requiredLong(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_LONG)) {
            throw new IllegalArgumentException("missing long " + key);
        }
        return tag.getLong(key);
    }

    private static String requiredString(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_STRING)) {
            throw new IllegalArgumentException("missing string " + key);
        }
        return tag.getString(key);
    }

    private static NamespacedId requiredId(CompoundTag tag, String key) {
        NamespacedId id = NamespacedId.parse(requiredString(tag, key));
        if (id == null) {
            throw new IllegalArgumentException("invalid id in " + key);
        }
        return id;
    }

    private static CompoundTag requiredCompound(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("missing compound " + key);
        }
        return tag.getCompound(key);
    }

    private FindingCodec() {}
}
