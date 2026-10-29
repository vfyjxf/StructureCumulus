package dev.vfyjxf.cumulus.mc.capability;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** An immutable nested-extent provider registry keyed by holder id, capability or attachment. */
@NullMarked
public final class NestedExtentLookup {

    private static final NestedExtentLookup capabilities = buildCapabilities();

    private final Map<NamespacedId, CapabilityNestedExtentProvider<?, ?>> capabilityProviders;
    private final Map<NamespacedId, AttachmentNestedExtentProvider<?, ?>> attachmentProviders;

    private NestedExtentLookup(
        Map<NamespacedId, CapabilityNestedExtentProvider<?, ?>> capabilityProviders,
        Map<NamespacedId, AttachmentNestedExtentProvider<?, ?>> attachmentProviders
    ) {
        this.capabilityProviders = Map.copyOf(capabilityProviders);
        this.attachmentProviders = Map.copyOf(attachmentProviders);
    }

    public static NestedExtentLookup capabilities() {
        return capabilities;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The explicit capability lookup; an attachment provider never answers it. */
    @SuppressWarnings("unchecked")
    public <H, C extends @Nullable Object> @Nullable NestedExtent getCapability(
        NestedExtentHolder<H> holder,
        H actual,
        @Nullable C context
    ) {
        Objects.requireNonNull(holder, "holder");
        CapabilityNestedExtentProvider<H, C> provider = (CapabilityNestedExtentProvider<H, C>) capabilityProviders
                .get(holder.id());
        return provider == null ? null : provider.get(actual, context);
    }

    /** The explicit attachment lookup; it reads existing data and never creates a default. */
    @SuppressWarnings("unchecked")
    public <H> @Nullable NestedExtent getAttachment(NestedExtentHolder<H> holder, H actual) {
        Objects.requireNonNull(holder, "holder");
        AttachmentNestedExtentProvider<H, ?> provider = (AttachmentNestedExtentProvider<H, ?>) attachmentProviders
                .get(holder.id());
        return provider == null ? null : provider.getExistingDataOrNull(actual);
    }

    /** Capability first, then the attachment source when the capability answers null. */
    public <H, C extends @Nullable Object> @Nullable NestedExtent get(
        NestedExtentHolder<H> holder,
        H actual,
        @Nullable C context
    ) {
        Objects.requireNonNull(holder, "holder");
        NestedExtent capability = getCapability(holder, actual, context);
        return capability != null ? capability : getAttachment(holder, actual);
    }

    public boolean knows(NestedExtentHolder<?> holder) {
        Objects.requireNonNull(holder, "holder");
        return capabilityProviders.containsKey(holder.id()) || attachmentProviders.containsKey(holder.id());
    }

    private static NestedExtentLookup buildCapabilities() {
        return builder().add(NestedExtentProviders.block).add(NestedExtentProviders.entity).build();
    }

    public static final class Builder {

        private final Map<NamespacedId, CapabilityNestedExtentProvider<?, ?>> capabilityProviders = new LinkedHashMap<>();
        private final Map<NamespacedId, AttachmentNestedExtentProvider<?, ?>> attachmentProviders = new LinkedHashMap<>();

        private Builder() {}

        public <H, C extends @Nullable Object> Builder add(CapabilityNestedExtentProvider<H, C> provider) {
            Objects.requireNonNull(provider, "provider");
            NamespacedId holder = Objects.requireNonNull(provider.holder().id(), "holder id");
            if (capabilityProviders.putIfAbsent(holder, provider) != null) {
                throw new IllegalArgumentException("duplicate capability nested extent provider for " + holder);
            }
            return this;
        }

        public <H, T> Builder add(AttachmentNestedExtentProvider<H, T> provider) {
            Objects.requireNonNull(provider, "provider");
            NamespacedId holder = Objects.requireNonNull(provider.holder().id(), "holder id");
            if (attachmentProviders.putIfAbsent(holder, provider) != null) {
                throw new IllegalArgumentException("duplicate attachment nested extent provider for " + holder);
            }
            return this;
        }

        public NestedExtentLookup build() {
            return new NestedExtentLookup(capabilityProviders, attachmentProviders);
        }
    }
}
