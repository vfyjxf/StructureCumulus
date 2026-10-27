package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.Merges;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The build rule: an INSTANCE requirement needs no producer, a MATCH requirement does. */
class ContextBuildValidationTest {

    private static final Cuboid bounds = Cuboid.of(0, 0, 0, 1, 1, 1);
    private static final CuboidSet region = CuboidSet.of(bounds);

    @Test
    void anInstanceRequirementWithoutAComputationBuilds() {
        ContextKey<Long> key = ContextKeys.of(id("instance_key"), Long.class, Retention.instance);
        assertDoesNotThrow(() -> requiringBlueprint("instance", key).build());
    }

    @Test
    void aWhenAbsentInstanceRequirementWithoutAComputationBuilds() {
        ContextKey<Long> key = ContextKeys.of(id("when_absent_key"), Long.class, Retention.instance, null, 7L);
        assertDoesNotThrow(() -> requiringBlueprint("when_absent", key).build());
    }

    @Test
    void aDerivationOutputInstanceRequirementBuilds() {
        ContextKey<Long> key = ContextKeys.of(id("derived_key"), Long.class, Retention.instance);
        BlueprintBuilder builder = requiringBlueprint("derived", key)
                .derive(new DerivationSpec(id("deriver"), Value.object(), key.id(), 0));
        assertDoesNotThrow(builder::build);
    }

    @Test
    void aMatchRequirementWithoutAComputationIsScChk0002() {
        ContextKey<Long> key = ContextKeys.of(id("match_key"), Long.class, Retention.match);
        IllegalArgumentException rejected = assertThrows(
            IllegalArgumentException.class,
            () -> requiringBlueprint("match", key).build()
        );
        String message = Objects.requireNonNull(rejected.getMessage(), "message");
        assertTrue(message.contains("SC-CHK-0002"), message);
    }

    @Test
    void aMatchRequirementWithAComputationBuilds() {
        ContextKey<Long> key = ContextKeys.of(id("match_key"), Long.class, Retention.match, Merges.sum());
        BlueprintBuilder builder = requiringBlueprint("match_produced", key)
                .computation(Computations.count(key, region));
        assertDoesNotThrow(builder::build);
    }

    @Test
    void duplicateOutputKeysStillFail() {
        ContextKey<Long> key = ContextKeys.of(id("duplicate_key"), Long.class, Retention.instance, Merges.sum());
        BlueprintBuilder builder = requiringBlueprint("duplicate", key).computation(Computations.count(key, region))
                .computation(Computations.volume(key, region));
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class, builder::build);
        String message = Objects.requireNonNull(rejected.getMessage(), "message");
        assertTrue(message.contains("SC-BPT-0007"), message);
    }

    private static NamespacedId id(String path) {
        return NamespacedId.of("test", path);
    }

    private static Bay bay(String path) {
        return Bay.of(id(path + "_bay"), region, Coverage.anySubject, Footprint.none(), Sharing.shared);
    }

    private static BlueprintBuilder requiringBlueprint(String path, ContextKey<Long> key) {
        Check<Long> check = Check
                .of(id(path + "_check"), bay(path), Focuses.context(key), Expectations.context(key, value -> true));
        return Blueprint.builder(id(path)).bounds(bounds).check(check);
    }
}
