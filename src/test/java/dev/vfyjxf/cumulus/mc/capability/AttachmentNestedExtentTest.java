package dev.vfyjxf.cumulus.mc.capability;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentNestedExtentTest {

    private static final NamespacedId type = NamespacedId.of("test", "attachment_child");
    private static final NestedExtent extent = new NestedExtent(
        type,
        Cuboid.of(0, 0, 0, 2, 2, 2),
        LatticePoint.of(10, 0, 10)
    );

    @Test
    void readsExistingDataAndMapsItThroughTheReader() {
        AttachmentType<String> attachment = attachmentType("read");
        FakeHolder holder = new FakeHolder();
        holder.setData(attachment, "origin");
        AtomicReference<@Nullable String> seen = new AtomicReference<>();
        AttachmentNestedExtentProvider<String, String> provider = AttachmentNestedExtentProviders.readOnly(
            NestedExtentHolder.of(NamespacedId.of("test", "read_holder")),
            attachment,
            actual -> holder,
            data -> {
                seen.set(data);
                return extent;
            }
        );

        assertTrue(provider.hasData("subject"));
        assertEquals(extent, provider.getExistingDataOrNull("subject"));
        assertEquals("origin", seen.get(), "the reader receives the stored raw value");
    }

    @Test
    void absentDataIsNullAndExistingDataReadsNeverCreateTheDefault() {
        AttachmentType<String> attachment = attachmentType("absent");
        FakeHolder holder = new FakeHolder();
        AttachmentNestedExtentProvider<String, String> provider = AttachmentNestedExtentProviders.readOnly(
            NestedExtentHolder.of(NamespacedId.of("test", "absent_holder")),
            attachment,
            actual -> holder,
            data -> extent
        );

        assertFalse(provider.hasData("subject"));
        assertNull(provider.getExistingDataOrNull("subject"));
        assertFalse(holder.hasAttachments(), "the capture read never creates a default attachment");
        assertEquals(extent, provider.getData("subject"), "the explicit getData path maps the created default");
        assertTrue(holder.hasAttachments(), "the explicit getData path creates the default");
        assertEquals("", holder.getExistingDataOrNull(attachment));
    }

    @Test
    void aNullHolderIsAbsentForEveryOperation() {
        AttachmentType<String> attachment = attachmentType("null_holder");
        AttachmentNestedExtentProvider<String, String> readWrite = AttachmentNestedExtentProviders.readWrite(
            NestedExtentHolder.of(NamespacedId.of("test", "null_holder")),
            attachment,
            actual -> null,
            data -> extent,
            value -> "written"
        );

        assertFalse(readWrite.hasData("subject"));
        assertNull(readWrite.getExistingDataOrNull("subject"));
        assertNull(readWrite.getData("subject"));
        assertNull(readWrite.setData("subject", extent));
        assertNull(readWrite.removeData("subject"));
    }

    @Test
    void readOnlyRejectsWritesButAllowsRemoval() {
        AttachmentType<String> attachment = attachmentType("read_only");
        FakeHolder holder = new FakeHolder();
        holder.setData(attachment, "stored");
        AttachmentNestedExtentProvider<String, String> provider = AttachmentNestedExtentProviders.readOnly(
            NestedExtentHolder.of(NamespacedId.of("test", "read_only")),
            attachment,
            actual -> holder,
            data -> extent
        );

        assertThrows(UnsupportedOperationException.class, () -> provider.setData("subject", extent));
        assertEquals(extent, provider.removeData("subject"), "removal is not a write and answers the previous extent");
        assertNull(holder.getExistingDataOrNull(attachment));
    }

    @Test
    void readWriteMapsSetAndRemoveThroughTheWriter() {
        AttachmentType<String> attachment = attachmentType("read_write");
        FakeHolder holder = new FakeHolder();
        AtomicReference<@Nullable NestedExtent> written = new AtomicReference<>();
        AttachmentNestedExtentProvider<String, String> provider = AttachmentNestedExtentProviders.readWrite(
            NestedExtentHolder.of(NamespacedId.of("test", "read_write")),
            attachment,
            actual -> holder,
            data -> extent,
            value -> {
                written.set(value);
                return "raw:" + value.type().text();
            }
        );

        assertNull(provider.setData("subject", extent), "no previous value");
        assertEquals(extent, written.get(), "the writer receives the extent");
        assertEquals("raw:test:attachment_child", holder.getExistingDataOrNull(attachment));
        assertEquals(extent, provider.getExistingDataOrNull("subject"));
        assertEquals(
            extent,
            provider.setData("subject", extent),
            "the previous value is mapped back through the reader"
        );
        assertEquals(extent, provider.removeData("subject"));
        assertNull(holder.getExistingDataOrNull(attachment));
        assertNull(provider.removeData("subject"), "removing absent data answers null");
    }

    @Test
    void aReadWriteWriterThatAnswersNullIsRejected() {
        AttachmentType<String> attachment = attachmentType("null_writer");
        FakeHolder holder = new FakeHolder();
        AttachmentNestedExtentProvider<String, String> provider = AttachmentNestedExtentProviders.readWrite(
            NestedExtentHolder.of(NamespacedId.of("test", "null_writer")),
            attachment,
            actual -> holder,
            data -> extent,
            value -> null
        );

        assertThrows(NullPointerException.class, () -> provider.setData("subject", extent));
        assertFalse(holder.hasAttachments(), "a rejected write leaves the holder untouched");
    }

    @Test
    void theStandardHolderAdaptersAreAvailable() {
        assertNotNull(AttachmentNestedExtentProviders.blockEntity());
        assertNotNull(AttachmentNestedExtentProviders.entity());
    }

    @Test
    void theLookupFallsBackFromCapabilityToAttachment() {
        AttachmentType<String> attachment = attachmentType("lookup");
        FakeHolder holder = new FakeHolder();
        holder.setData(attachment, "origin");
        NestedExtentHolder<String> attachmentHolder = NestedExtentHolder
                .of(NamespacedId.of("test", "attachment_lookup"));
        NestedExtentLookup lookup = NestedExtentLookup.builder()
                .add(
                    AttachmentNestedExtentProviders
                            .readOnly(attachmentHolder, attachment, actual -> holder, data -> extent)
                ).build();

        assertNull(lookup.getCapability(attachmentHolder, "subject", Direction.WEST), "no capability provider");
        assertEquals(extent, lookup.getAttachment(attachmentHolder, "subject"));
        assertEquals(extent, lookup.get(attachmentHolder, "subject", Direction.WEST), "the capture get falls back");
        assertTrue(lookup.knows(attachmentHolder));
        assertNull(lookup.get(NestedExtentHolder.of(NamespacedId.of("test", "unknown")), "subject", Direction.WEST));
    }

    @Test
    void theLookupPrefersTheCapabilitySourceForAProviderHolder() {
        NestedExtent capabilityExtent = new NestedExtent(
            NamespacedId.of("test", "capability_child"),
            Cuboid.of(0, 0, 0, 1, 1, 1),
            LatticePoint.origin
        );
        NestedExtentHolder<String> holder = NestedExtentHolder.of(NamespacedId.of("test", "capability_first"));
        NestedExtentLookup lookup = NestedExtentLookup.builder()
                .add(new CapabilityNestedExtentProvider<String, @Nullable Void>() {
                    @Override
                    public NestedExtentHolder<String> holder() {
                        return holder;
                    }

                    @Override
                    public @Nullable NestedExtent get(String actual, @Nullable Void context) {
                        return actual.equals("hit") ? capabilityExtent : null;
                    }
                }).build();

        assertEquals(capabilityExtent, lookup.get(holder, "hit", (Void) null), "the capability path answers");
        assertNull(lookup.getAttachment(holder, "hit"), "an attachment-only method never reads the capability");
    }

    @Test
    void theLookupAllowsTheSameHolderInBothSourcesAndPrefersTheCapability() {
        NestedExtentHolder<String> holder = NestedExtentHolder.of(NamespacedId.of("test", "both_sources"));
        AttachmentType<String> attachment = attachmentType("both_sources");
        FakeHolder fake = new FakeHolder();
        fake.setData(attachment, "origin");
        NestedExtent capabilityExtent = new NestedExtent(
            NamespacedId.of("test", "capability_child"),
            Cuboid.of(0, 0, 0, 1, 1, 1),
            LatticePoint.origin
        );
        CapabilityNestedExtentProvider<String, @Nullable Void> capability = new CapabilityNestedExtentProvider<>() {
            @Override
            public NestedExtentHolder<String> holder() {
                return holder;
            }

            @Override
            public @Nullable NestedExtent get(String actual, @Nullable Void context) {
                return actual.equals("hit") ? capabilityExtent : null;
            }
        };
        AttachmentNestedExtentProvider<String, String> attachmentProvider = AttachmentNestedExtentProviders
                .readOnly(holder, attachment, actual -> fake, data -> extent);
        NestedExtentLookup lookup = NestedExtentLookup.builder().add(capability).add(attachmentProvider).build();

        assertTrue(lookup.knows(holder), "one holder id may exist in both sources");
        assertEquals(capabilityExtent, lookup.get(holder, "hit", (Void) null), "the capability wins when it answers");
        assertEquals(capabilityExtent, lookup.getCapability(holder, "hit", (Void) null));
        assertEquals(
            extent,
            lookup.getAttachment(holder, "hit"),
            "the explicit attachment lookup stays source-specific"
        );
    }

    @Test
    void theLookupFallsBackToTheAttachmentWhenTheCapabilityAnswersNull() {
        NestedExtentHolder<String> holder = NestedExtentHolder.of(NamespacedId.of("test", "fallback_sources"));
        AttachmentType<String> attachment = attachmentType("fallback_sources");
        FakeHolder fake = new FakeHolder();
        fake.setData(attachment, "origin");
        CapabilityNestedExtentProvider<String, @Nullable Void> capability = new CapabilityNestedExtentProvider<>() {
            @Override
            public NestedExtentHolder<String> holder() {
                return holder;
            }

            @Override
            public @Nullable NestedExtent get(String actual, @Nullable Void context) {
                return null;
            }
        };
        AttachmentNestedExtentProvider<String, String> attachmentProvider = AttachmentNestedExtentProviders
                .readOnly(holder, attachment, actual -> fake, data -> extent);
        NestedExtentLookup lookup = NestedExtentLookup.builder().add(capability).add(attachmentProvider).build();

        assertNull(lookup.getCapability(holder, "hit", (Void) null), "the capability answers null");
        assertEquals(extent, lookup.getAttachment(holder, "hit"), "the attachment source answers");
        assertEquals(
            extent,
            lookup.get(holder, "hit", (Void) null),
            "the capture get falls back after a null capability"
        );
    }

    @Test
    void theAttachmentSourceStillRejectsADuplicateHolder() {
        NestedExtentHolder<String> holder = NestedExtentHolder.of(NamespacedId.of("test", "attachment_duplicate"));
        AttachmentType<String> attachment = attachmentType("attachment_duplicate");
        FakeHolder fake = new FakeHolder();
        AttachmentNestedExtentProvider<String, String> first = AttachmentNestedExtentProviders
                .readOnly(holder, attachment, actual -> fake, data -> extent);
        AttachmentNestedExtentProvider<String, String> second = AttachmentNestedExtentProviders
                .readOnly(holder, attachment, actual -> fake, data -> extent);
        NestedExtentLookup.Builder builder = NestedExtentLookup.builder().add(first);

        assertThrows(IllegalArgumentException.class, () -> builder.add(second));
    }

    @Test
    void theAttachmentLookupNeverCreatesADefault() {
        AttachmentType<String> attachment = attachmentType("no_default_lookup");
        FakeHolder holder = new FakeHolder();
        NestedExtentHolder<String> holderId = NestedExtentHolder.of(NamespacedId.of("test", "no_default_lookup"));
        NestedExtentLookup lookup = NestedExtentLookup.builder()
                .add(AttachmentNestedExtentProviders.readOnly(holderId, attachment, actual -> holder, data -> extent))
                .build();

        assertNull(lookup.getAttachment(holderId, "subject"));
        assertFalse(holder.hasAttachments(), "a capture read leaves the holder without the default attachment");
    }

    private static AttachmentType<String> attachmentType(String path) {
        return AttachmentType.builder(() -> "").build();
    }

    private static final class FakeHolder implements IAttachmentHolder {

        private final Map<AttachmentType<?>, Object> data = new LinkedHashMap<>();
        private final Function<AttachmentType<?>, Object> defaults = ignored -> "";

        @Override
        public boolean hasAttachments() {
            return !data.isEmpty();
        }

        @Override
        public boolean hasData(AttachmentType<?> type) {
            return data.containsKey(type);
        }

        @Override
        public <T> T getData(AttachmentType<T> type) {
            T existing = getExistingDataOrNull(type);
            if (existing != null) {
                return existing;
            }
            @SuppressWarnings("unchecked")
            T created = (T) Objects.requireNonNull(defaults.apply(type), "default");
            data.put(type, created);
            return created;
        }

        @Override
        public <T> @Nullable T getExistingDataOrNull(AttachmentType<T> type) {
            @SuppressWarnings("unchecked")
            T value = (T) data.get(type);
            return value;
        }

        @Override
        public <T> @Nullable T setData(AttachmentType<T> type, T value) {
            @SuppressWarnings("unchecked")
            T previous = (T) data.put(type, value);
            return previous;
        }

        @Override
        public <T> @Nullable T removeData(AttachmentType<T> type) {
            @SuppressWarnings("unchecked")
            T previous = (T) data.remove(type);
            return previous;
        }
    }
}
