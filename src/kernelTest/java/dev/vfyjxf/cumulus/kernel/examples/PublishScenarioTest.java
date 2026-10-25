package dev.vfyjxf.cumulus.kernel.examples;

import dev.vfyjxf.cumulus.kernel.base.FindingCode;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.base.Outcome;
import dev.vfyjxf.cumulus.kernel.context.ContextDraft;
import dev.vfyjxf.cumulus.kernel.context.ContextKey;
import dev.vfyjxf.cumulus.kernel.context.ContextKeys;
import dev.vfyjxf.cumulus.kernel.context.ContextStore;
import dev.vfyjxf.cumulus.kernel.context.ContextTier;
import dev.vfyjxf.cumulus.kernel.context.Retention;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 22 §8.3, P10; 12 §5: one writer per instance, one revision order, a stale base refused. */
class PublishScenarioTest {

    private static final ContextKey<Long> CAPACITY = ContextKeys.of(NamespacedId.of("mymod", "capacity"),
            Long.class, Retention.CONTEXT, null, null);

    @Test
    void aStaleBaseRevisionIsRefusedAndTheNewerFrameSurvives() {
        ContextStore store = new ContextStore(TestWorld.DEFAULT_INSTANCE);
        ContextDraft form = store.draft(ContextTier.FORM, 0L);
        form.put(CAPACITY, 400L);
        assertInstanceOf(Outcome.Found.class, store.publish(form), "the FORM frame publishes at revision 1");
        assertEquals(1L, store.revision());

        ContextDraft settle = store.draft(ContextTier.SETTLE, store.revision());
        settle.put(CAPACITY, 512L);
        assertInstanceOf(Outcome.Found.class, store.publish(settle));
        assertEquals(2L, store.revision());

        ContextDraft stale = store.draft(ContextTier.FORM, 0L);
        stale.put(CAPACITY, 999L);
        Outcome.Missing<Void> refused = assertInstanceOf(Outcome.Missing.class, store.publish(stale),
                "22 §8.3: a publish whose baseRevision is not the store's revision is refused");
        assertFalse(refused.findings().ofCode(FindingCode.of("SC-CTX-0108")).isEmpty(),
                "22 §8.3: a stale base is SC-CTX-0108, not a silent overwrite");

        assertEquals(2L, store.revision(), "a refused publish writes nothing, including the revision");
        assertEquals(512L, store.context(TestWorld.DEFAULT_INSTANCE).get(CAPACITY),
                "P10: the newer frame survives; the stale writer never wins");
        assertEquals(400L, store.context(TestWorld.DEFAULT_INSTANCE).at(ContextTier.FORM).get(CAPACITY),
                "22 §8.3: a reader at tier t sees the newest frame at or below t");
        assertNull(store.context(TestWorld.DEFAULT_INSTANCE).at(ContextTier.PROBE).get(CAPACITY),
                "neither frame is visible below the tier that published it");
    }

    @Test
    void aDraftPublishesOnce() {
        ContextStore store = new ContextStore(TestWorld.DEFAULT_INSTANCE);
        ContextDraft draft = store.draft(ContextTier.FORM, 0L);
        draft.put(CAPACITY, 1L);
        assertInstanceOf(Outcome.Found.class, store.publish(draft));

        assertThrows(IllegalStateException.class, () -> store.publish(draft),
                "a published draft is consumed; publishing it twice is a programming error, not a merge");
        assertEquals(1L, store.revision());
    }

    @Test
    void twoValuesForOneKeyInOneFrameAreRefused() {
        ContextStore store = new ContextStore(TestWorld.DEFAULT_INSTANCE);
        ContextDraft draft = store.draft(ContextTier.FORM, 0L);
        draft.put(CAPACITY, 400L);
        draft.put(CAPACITY, 401L);

        Outcome.Missing<Void> refused = assertInstanceOf(Outcome.Missing.class, store.publish(draft),
                "22 §8.3: two different values for one key in one frame is SC-CTX-0107");
        assertFalse(refused.findings().ofCode(FindingCode.of("SC-CTX-0107")).isEmpty(),
                "SC-CTX-0107 names the key and the frame, so the second writer is findable");
        assertEquals(0L, store.revision(), "nothing was published");
        assertNull(store.context(TestWorld.DEFAULT_INSTANCE).get(CAPACITY));
    }
}
