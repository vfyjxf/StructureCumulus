package dev.vfyjxf.cumulus.kernel.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreTest {

    @Test
    void namespacedIdRoundTrip() {
        NamespacedId id = NamespacedId.of("mymod", "huge_furnace");
        assertEquals("mymod:huge_furnace", id.text());
        assertEquals(id, NamespacedId.parse("mymod:huge_furnace"));
        assertEquals(NamespacedId.of("structurecumulus", "hatch"), NamespacedId.parse("hatch"));
        assertNull(NamespacedId.parse("Mymod:hatch"));
        assertNull(NamespacedId.parse("mymod:"));
        assertNull(NamespacedId.parse("mymod:a:b"));
        assertNull(NamespacedId.parse("mymod:/a"));
        assertNull(NamespacedId.parse("mymod:a//b"));
        assertNull(NamespacedId.parse("mymod:a/"));
        assertNull(NamespacedId.parse(":a"));
        assertNull(NamespacedId.parse("mymod:" + "a".repeat(257)));
        assertNull(NamespacedId.parse("n".repeat(65) + ":a"));
        assertThrows(IllegalArgumentException.class, () -> NamespacedId.of("bad namespace", "x"));
    }

    @Test
    void namespacedIdOrderIsTotalAndLocaleIndependent() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.of("tr", "TR"));
            List<NamespacedId> ids = List.of(
                NamespacedId.parse("b:a"),
                NamespacedId.parse("a:z"),
                NamespacedId.parse("a:i"),
                NamespacedId.parse("a:aa"),
                NamespacedId.parse("a:0")
            );
            assertEquals(
                List.of(
                    NamespacedId.parse("a:0"),
                    NamespacedId.parse("a:aa"),
                    NamespacedId.parse("a:i"),
                    NamespacedId.parse("a:z"),
                    NamespacedId.parse("b:a")
                ),
                ids.stream().sorted().toList()
            );
            for (NamespacedId left : ids) {
                assertEquals(0, left.compareTo(left));
                for (NamespacedId right : ids) {
                    assertEquals(Integer.signum(left.compareTo(right)), -Integer.signum(right.compareTo(left)));
                }
            }
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void namespacedIdOrderIsNamespaceThenPath() {
        List<NamespacedId> ids = List.of(
            NamespacedId.parse("b:a"),
            NamespacedId.parse("a:z"),
            NamespacedId.parse("a:a"),
            NamespacedId.parse("a:aa")
        );
        List<NamespacedId> sorted = ids.stream().sorted().toList();
        assertEquals(
            List.of(
                NamespacedId.parse("a:a"),
                NamespacedId.parse("a:aa"),
                NamespacedId.parse("a:z"),
                NamespacedId.parse("b:a")
            ),
            sorted
        );
    }

    @Test
    void catalogFreezesAndRefusesDifferentDuplicate() {
        Catalog<String> catalog = Catalog.<String>builder(NamespacedId.of("test")).put(NamespacedId.of("a"), "a")
                .put(NamespacedId.of("b"), "b").build();
        assertEquals(List.of(NamespacedId.of("a"), NamespacedId.of("b")), catalog.ids());
        assertEquals("a", catalog.require(NamespacedId.of("a")));
        assertNull(catalog.get(NamespacedId.of("c")));
        Catalog<String> rePut = Catalog.<String>builder(NamespacedId.of("test")).put(NamespacedId.of("a"), "a")
                .put(NamespacedId.of("b"), "b").put(NamespacedId.of("a"), "a").build();
        assertEquals(catalog.ids(), rePut.ids());
        assertEquals(List.of(NamespacedId.of("a"), NamespacedId.of("b")), List.copyOf(rePut.asMap().keySet()));
        IllegalArgumentException duplicate = assertThrows(
            IllegalArgumentException.class,
            () -> Catalog.<String>builder(NamespacedId.of("test")).put(NamespacedId.of("a"), "a")
                    .put(NamespacedId.of("a"), "b")
        );
        assertTrue(Objects.requireNonNull(duplicate.getMessage(), "message").startsWith("SC-SPI-0003: "));
        assertThrows(UnsupportedOperationException.class, () -> catalog.asMap().put(NamespacedId.of("c"), "c"));
    }

    @Test
    void valueEqualityIgnoresSpan() {
        SourceSpan span = new SourceSpan(NamespacedId.of("doc"), 1, 3);
        Value a = Value.object(Map.of("b", Value.number(2), "a", Value.string("x")));
        Value b = Value.withSpan(Value.object(Map.of("a", Value.string("x"), "b", Value.number(2))), span);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertTrue(Values.contentEquals(a, b));
        assertEquals(List.of("a", "b"), List.copyOf(a.members().keySet()));
        assertEquals(2, Objects.requireNonNull(a.get("b"), "b").asNumber(0));
    }

    @Test
    void objectMembersSortByCodePointEvenBeyondTheBmp() {
        Value members = Value
                .object(Map.of("a", Value.number(1), "\uffff", Value.number(2), "\ud800\udc00", Value.number(3)));
        assertEquals(List.of("a", "\uffff", "\ud800\udc00"), List.copyOf(members.members().keySet()));
    }

    @Test
    void arraysKeepTheirOrderAndNumbersAreLongs() {
        Value first = Value.string("z");
        Value second = Value.string("a");
        Value number = Value.number(Long.MAX_VALUE);
        assertEquals(List.of(first, second, number), Value.array(List.of(first, second, number)).elements());
        assertEquals(Long.MAX_VALUE, number.asNumber(0));
    }

    @Test
    void outcomeFold() {
        Outcome<String> found = Outcome.found("x");
        assertEquals("x", found.fold(v -> v, () -> "missing"));
        Outcome<String> missing = Outcome.missing();
        assertEquals("missing", missing.fold(v -> v, () -> "missing"));
        assertTrue(found.isPresent());
        assertFalse(missing.isPresent());
    }

    @Test
    void findingCodeParsesAndOrders() {
        FindingCode code = FindingCode.of(FindingCode.Family.exp, 1);
        assertEquals("SC-EXP-0001", code.text());
        assertEquals(code, FindingCode.parse("SC-EXP-0001"));
        assertNull(FindingCode.parse("SC-EXP-1"));
        assertNull(FindingCode.parse("SC-NOPE-0001"));
        assertNull(FindingCode.parse("SC-EXP-0000"));
        assertThrows(IllegalArgumentException.class, () -> FindingCode.of(FindingCode.Family.exp, 10000));
        assertTrue(FindingCode.of(FindingCode.Family.exp, 1).compareTo(FindingCode.of(FindingCode.Family.exp, 2)) < 0);
    }

    @Test
    void everyFamilyEmitsItsUppercaseWireTag() {
        Set<String> tags = new HashSet<>();
        for (FindingCode.Family family : FindingCode.Family.values()) {
            String text = FindingCode.of(family, 7).text();
            assertTrue(text.matches("SC-[A-Z]{2,6}-0007"), text);
            assertFalse(text.contains(family.name()), text);
            assertEquals(new FindingCode(family, 7), FindingCode.parse(text));
            assertTrue(tags.add(text.substring(3, text.length() - 5)), "duplicate family tag: " + text);
        }
        assertEquals(FindingCode.Family.values().length, tags.size());
    }

    @Test
    void budgetAndSpend() {
        Budget budget = Budget.of(10, 2);
        assertTrue(budget.admits(Spend.zero));
        assertTrue(budget.admits(new Spend(10, 2)));
        assertFalse(budget.admits(new Spend(11, 2)));
        assertEquals(new Spend(3, 1), new Spend(1, 1).plus(new Spend(2, 0)));
        assertThrows(IllegalArgumentException.class, () -> new Spend(-1, 0));
        assertTrue(Budget.zero.admits(Spend.zero));
        assertFalse(Budget.zero.admits(new Spend(1, 0)));
        assertTrue(Budget.unbounded().admits(new Spend(Integer.MAX_VALUE, Integer.MAX_VALUE)));
        assertEquals(
            new Spend(Integer.MAX_VALUE, Integer.MAX_VALUE),
            new Spend(Integer.MAX_VALUE, Integer.MAX_VALUE).plus(new Spend(1, 1))
        );
    }

    @Test
    void phaseWireNamesAreUppercaseOverLowerCamelConstants() {
        for (Phase phase : Phase.values()) {
            assertEquals(phase.name().toLowerCase(Locale.ROOT), phase.name());
            assertEquals(phase.name().toUpperCase(Locale.ROOT), phase.wireName());
        }
    }

    @Test
    void twoKeysWithTheSameSlotHashAlike() {
        ContextKey<Long> a = ContextKeys
                .of(NamespacedId.of("k"), Long.class, Retention.instance, (left, right) -> left + right);
        ContextKey<Long> b = ContextKeys
                .of(NamespacedId.of("k"), Long.class, Retention.instance, (left, right) -> left * right);
        assertNotEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void theOrderIsIdThenTypeName() {
        ContextKey<Long> bx = ContextKeys.of(NamespacedId.of("b", "x"), Long.class, Retention.instance);
        ContextKey<String> az = ContextKeys.of(NamespacedId.of("a", "z"), String.class, Retention.match);
        ContextKey<Long> ay = ContextKeys.of(NamespacedId.of("a", "y"), Long.class, Retention.instance);
        ContextKey<Integer> ayInt = ContextKeys.of(NamespacedId.of("a", "y"), Integer.class, Retention.match);
        List<ContextKey<?>> shuffled = List.of(bx, ay, ayInt, az);
        List<ContextKey<?>> expected = List.of(ayInt, ay, az, bx);
        assertEquals(expected, shuffled.stream().sorted(ContextKeys.order()).toList());
        TreeSet<ContextKey<?>> tree = new TreeSet<>(ContextKeys.order());
        tree.addAll(shuffled);
        assertEquals(expected, List.copyOf(tree));
    }

    @Test
    void hashDoesNotDependOnIdentityHash() {
        List<ContextKey<?>> first = List.of(
            ContextKeys.of(NamespacedId.of("a"), Long.class, Retention.instance, (left, right) -> left + right),
            ContextKeys.of(NamespacedId.of("b"), String.class, Retention.match)
        );
        List<ContextKey<?>> second = List.of(
            ContextKeys.of(NamespacedId.of("a"), Long.class, Retention.instance, (left, right) -> left + right),
            ContextKeys.of(NamespacedId.of("b"), String.class, Retention.match)
        );
        for (int index = 0; index < first.size(); index++) {
            assertEquals(first.get(index).hashCode(), second.get(index).hashCode());
        }
    }

    @Test
    void contextKeyEqualityAndFactory() {
        ContextKey<Long> a = ContextKeys.of(NamespacedId.of("k"), Long.class, Retention.instance);
        ContextKey<Long> b = ContextKeys.of(NamespacedId.of("k"), Long.class, Retention.instance);
        assertEquals(a, b);
        assertFalse(a.contributed());
        assertNotEquals(a, ContextKeys.of(NamespacedId.of("other"), Long.class, Retention.instance));
        assertNotEquals(a, ContextKeys.of(NamespacedId.of("k"), String.class, Retention.instance));
        assertNotEquals(a, ContextKeys.of(NamespacedId.of("k"), Long.class, Retention.match));
        ContextKey<Long> c = ContextKeys
                .of(NamespacedId.of("k"), Long.class, Retention.instance, (left, right) -> left + right);
        assertTrue(c.contributed());
        assertNotEquals(
            c,
            ContextKeys.of(NamespacedId.of("k"), Long.class, Retention.instance, (left, right) -> right)
        );
    }
}
