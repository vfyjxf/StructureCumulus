package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.ContextStore;
import dev.vfyjxf.cumulus.kernel.context.StructureContext;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationMeta;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.structure.Reply;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A check with a context focus cannot see the instance values already published in ContextStore (D2). */
class ContextInstanceScenarioTest {

    private static final ContextKey<Long> capacity = ContextKeys
            .of(TechFixtures.id("context/capacity"), Long.class, Retention.instance);

    @Test
    @DisplayName("known gap D2: check cannot read persisted instance context; current SC-CTX-0007")
    void checkCannotReadPersistedInstanceContext() {
        ContextStore store = ContextStore.inMemory();
        StructureContext published = store
                .publish(TechFixtures.instance, Map.of(capacity, 42L), FindingBundle.empty(), Spend.zero);
        assertEquals(42L, published.get(capacity), "the instance value is persisted");

        Blueprint blueprint = contextBlueprint("context/check_persisted");
        Siting siting = TechFixtures.identity(LatticePoint.origin);
        MatchResult checked = TechFixtures.check(blueprint, siting, TechFixtures.view(), TechFixtures.emptyClaims());
        MatchResult.Undecided undecided = assertInstanceOf(
            MatchResult.Undecided.class,
            checked,
            "check has no ContextStore argument and sees an empty local context"
        );
        assertEquals(UndecidedReason.unreadable, undecided.reason(), "the missing key is unreadable");
        assertTrue(TechFixtures.hasCode(undecided, Codes.keyAbsent), "the current failure is SC-CTX-0007");
        assertEquals(42L, store.context(TechFixtures.instance).get(capacity), "the store still holds the value");
        assertEquals(
            published.revision(),
            store.context(TechFixtures.instance).revision(),
            "the check does not publish a new context revision"
        );
    }

    private static Blueprint contextBlueprint(String path) {
        CuboidSet cells = TechFixtures.cells(TechFixtures.cell(LatticePoint.origin));
        Bay bay = TechFixtures
                .bay(TechFixtures.id(path + "/bay"), cells, Coverage.anySubject, Footprint.none(), Sharing.shared);
        Check<Long> check = Check.of(
            TechFixtures.id(path + "/capacity"),
            bay,
            Focuses.context(capacity),
            Expectations.custom(Long.class, ExpectationMeta.anonymous(), ctx -> Reply.match())
        );
        return Blueprint.builder(TechFixtures.id(path)).bounds(TechFixtures.box(0, 0, 0, 1, 1, 1)).check(check).build();
    }
}
