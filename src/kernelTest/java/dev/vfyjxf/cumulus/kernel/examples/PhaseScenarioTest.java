package dev.vfyjxf.cumulus.kernel.examples;

import dev.vfyjxf.cumulus.kernel.base.Outcome;
import dev.vfyjxf.cumulus.kernel.context.AggregateContext;
import dev.vfyjxf.cumulus.kernel.context.Computation;
import dev.vfyjxf.cumulus.kernel.context.ContextKey;
import dev.vfyjxf.cumulus.kernel.context.ContextView;
import dev.vfyjxf.cumulus.kernel.context.SectionContext;
import dev.vfyjxf.cumulus.kernel.context.StatePhase;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P6, 22 §7.3: the section phase has no {@code read(ContextKey)}. The compile-time proof is the
 * absence of the method; this reflection assertion is the runnable stand-in until a sketch that
 * fails to compile can live in the build.
 */
class PhaseScenarioTest {

    @Test
    void aSectionContextCannotReadAggregateState() {
        assertThrows(NoSuchMethodException.class,
                () -> SectionContext.class.getMethod("read", ContextKey.class),
                "P6: a SECTION unit must not read a ContextKey; there is no such method and no such overload");
        for (Method method : SectionContext.class.getMethods()) {
            assertNotEquals("read", method.getName(),
                    "22 §7.3: SectionContext exposes the section, its cells and its occupants — never read(...)");
            assertNotEquals(Outcome.class, method.getReturnType(),
                    "22 §7.3: no section member answers an Outcome, so absence has no channel below the barrier");
        }
        assertFalse(AggregateContext.class.isAssignableFrom(SectionContext.class),
                "22 §7.3: the phase split is two interfaces — a section handle cannot widen into an aggregate one");
        assertTrue(SectionContext.class.isInterface(), "SectionContext is a view, not a value");
    }

    @Test
    void theSectionPhaseHasTheSectionAndTheAggregatePhaseHasRead() throws NoSuchMethodException {
        assertNotNull(SectionContext.class.getMethod("section"), "a section unit knows which section it runs for");
        assertEquals(List.class, SectionContext.class.getMethod("occupants").getReturnType());
        assertNotNull(SectionContext.class.getMethod("occupants", dev.vfyjxf.cumulus.kernel.space.Cuboid.class),
                "a section unit reads its own occupants, bounded by a box inside its section");

        Method read = AggregateContext.class.getMethod("read", ContextKey.class);
        assertEquals(Outcome.class, read.getReturnType(), "22 §7.3: read(ContextKey) is the aggregate channel");
        assertEquals(ContextView.class, AggregateContext.class.getMethod("context").getReturnType());
        assertThrows(NoSuchMethodException.class,
                () -> AggregateContext.class.getMethod("contribute", ContextKey.class, Object.class),
                "22 §7.3: an aggregate cannot contribute — contribute exists only on a section-scoped draft");
    }

    @Test
    void thePhaseBelongsToTheComputationNotTheKey() throws NoSuchMethodException {
        assertEquals(StatePhase.class, Computation.class.getMethod("phase").getReturnType(),
                "22 §6.4: phase lives on the computation, so one key type serves both phases");
        assertNotNull(StatePhase.valueOf("SECTION"));
        assertNotNull(StatePhase.valueOf("AGGREGATE"));
    }
}
