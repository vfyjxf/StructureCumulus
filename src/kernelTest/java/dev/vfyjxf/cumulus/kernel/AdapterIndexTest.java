package dev.vfyjxf.cumulus.kernel;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compares the {@code ## 15. Signatures} block of {@code docs/modules/adapter.md} with the adapter
 * sources under {@code src/main/java}: every documented member exists in the source with the same
 * arity, parameter and return type names and the same {@code @Nullable} positions, and every public
 * member declared in the source appears in the block. Type-name coverage of the uncovered batches
 * stays with {@code tools/spec-check.py} (D-74); this gate owns the documented members.
 */
class AdapterIndexTest {

    private static final Path doc = Path.of("docs", "modules", "adapter.md");
    private static final Path sources = Path.of("src", "main", "java");

    @Test
    void documentedAdapterSignaturesMatchTheSources() {
        Set<String> problems = new TreeSet<>();
        for (SpecIndexTest.DocType documented : documentedTypes(problems)) {
            SpecIndexTest.DocType source = sourceType(documented, problems);
            if (source != null) {
                compare(documented, source, documented.simpleName, problems);
            }
        }
        assertTrue(problems.isEmpty(), () -> "adapter index drift:\n  " + String.join("\n  ", problems));
    }

    private static List<SpecIndexTest.DocType> documentedTypes(Set<String> problems) {
        String text = read(doc);
        int start = text.indexOf("## 15. Signatures");
        if (start < 0) {
            throw new IllegalStateException("docs/modules/adapter.md has no '## 15. Signatures' block");
        }
        StringBuilder block = new StringBuilder();
        boolean inFence = false;
        for (String line : text.substring(start).split("\n")) {
            if (line.trim().startsWith("```")) {
                inFence = !inFence;
                continue;
            }
            if (inFence) {
                block.append(line).append('\n');
            }
        }
        return SpecIndexTest.DocParser.parse(block.toString(), problems);
    }

    /** The parsed type declaration of the adapter source file that declares the documented type. */
    private static SpecIndexTest.DocType sourceType(SpecIndexTest.DocType documented, Set<String> problems) {
        String topLevel = documented.fqn.split("\\$", 2)[0];
        Path file = sources.resolve(topLevel.replace('.', '/') + ".java");
        if (!Files.exists(file)) {
            problems.add(topLevel + ": documented but no adapter source file declares it");
            return null;
        }
        StringBuilder declaration = new StringBuilder();
        for (String line : read(file).split("\n")) {
            String stripped = line.strip();
            if (stripped.startsWith("package ") || stripped.startsWith("import ")) {
                continue;
            }
            declaration.append(line).append('\n');
        }
        List<SpecIndexTest.DocType> types = SpecIndexTest.DocParser.parse(declaration.toString(), problems);
        for (SpecIndexTest.DocType type : types) {
            if (type.simpleName.equals(documented.simpleName)) {
                return type;
            }
        }
        problems.add(topLevel + ": the source file declares no such type");
        return null;
    }

    private static void compare(
        SpecIndexTest.DocType documented,
        SpecIndexTest.DocType source,
        String where,
        Set<String> problems
    ) {
        if (documented.record != source.record
                || documented.enumType != source.enumType
                || documented.interfaceType != source.interfaceType) {
            problems.add(where + ": documented type kind differs from the source");
        }
        compareConstants(documented, source, where, problems);
        compareComponents(documented, source, where, problems);
        compareMembers(documented, source, where, problems);
        compareNested(documented, source, where, problems);
    }

    private static void compareConstants(
        SpecIndexTest.DocType documented,
        SpecIndexTest.DocType source,
        String where,
        Set<String> problems
    ) {
        if (!documented.enumType) {
            return;
        }
        if (!new TreeSet<>(documented.enumConstants).equals(new TreeSet<>(source.enumConstants))) {
            problems.add(
                where + ": documented constants " + documented.enumConstants + " differ from source "
                        + source.enumConstants
            );
        }
    }

    private static void compareComponents(
        SpecIndexTest.DocType documented,
        SpecIndexTest.DocType source,
        String where,
        Set<String> problems
    ) {
        if (!documented.record) {
            return;
        }
        if (!documented.componentNames.equals(source.componentNames)
                || !documented.componentNullable.equals(source.componentNullable)) {
            problems.add(
                where + ": documented record components "
                        + describe(documented.componentNames, documented.componentNullable) + " differ from source "
                        + describe(source.componentNames, source.componentNullable)
            );
        }
    }

    private static void compareMembers(
        SpecIndexTest.DocType documented,
        SpecIndexTest.DocType source,
        String where,
        Set<String> problems
    ) {
        Map<String, List<SpecIndexTest.DocMember>> bySignature = new LinkedHashMap<>();
        for (SpecIndexTest.DocMember member : source.members) {
            bySignature.computeIfAbsent(member.kind + " " + member.name + "/" + member.arity, key -> new ArrayList<>())
                    .add(member);
        }
        for (SpecIndexTest.DocMember member : documented.members) {
            List<SpecIndexTest.DocMember> candidates = bySignature
                    .get(member.kind + " " + member.name + "/" + member.arity);
            if (candidates == null) {
                problems.add(where + "." + member.name + ": documented but not in the source");
                continue;
            }
            if (match(member, candidates) == null) {
                problems.add(where + "." + member.name + ": documented signature differs from the source");
            }
        }
        for (SpecIndexTest.DocMember member : source.members) {
            if (!member.publicVisibility
                    || member.kind.equals(SpecIndexTest.DocMember.compactConstructor)
                    || member.name.equals("equals")
                    || member.name.equals("hashCode")
                    || member.name.equals("toString")) {
                continue;
            }
            boolean documentedAgain = documented.members.stream().anyMatch(
                other -> other.kind.equals(member.kind) && other.name.equals(member.name) && other.arity == member.arity
            );
            if (!documentedAgain) {
                problems.add(where + "." + member.name + ": public member missing from the block");
            }
        }
    }

    private static void compareNested(
        SpecIndexTest.DocType documented,
        SpecIndexTest.DocType source,
        String where,
        Set<String> problems
    ) {
        Map<String, SpecIndexTest.DocType> sourceNested = new LinkedHashMap<>();
        for (SpecIndexTest.DocType nested : source.nested) {
            sourceNested.put(nested.simpleName, nested);
        }
        for (SpecIndexTest.DocType nested : documented.nested) {
            SpecIndexTest.DocType match = sourceNested.get(nested.simpleName);
            if (match == null) {
                problems.add(where + "." + nested.simpleName + ": documented nested type not in the source");
                continue;
            }
            compare(nested, match, where + "." + nested.simpleName, problems);
        }
        for (SpecIndexTest.DocType nested : source.nested) {
            if (nested.publicVisibility
                    && documented.nested.stream().noneMatch(other -> other.simpleName.equals(nested.simpleName))) {
                problems.add(where + "." + nested.simpleName + ": public nested type missing from the block");
            }
        }
    }

    /** The first source candidate the documented member matches, or {@code null} when none does. */
    private static SpecIndexTest.DocMember match(
        SpecIndexTest.DocMember documented,
        List<SpecIndexTest.DocMember> candidates
    ) {
        for (SpecIndexTest.DocMember candidate : candidates) {
            if (!documented.parameterNullable.equals(candidate.parameterNullable)) {
                continue;
            }
            if (!typesEqual(documented.parameterTypes, candidate.parameterTypes)) {
                continue;
            }
            if (documented.returnNullable != candidate.returnNullable) {
                continue;
            }
            if (!nullableEquals(documented.returnType, candidate.returnType)) {
                continue;
            }
            if (!nullableEquals(documented.fieldType, candidate.fieldType)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private static boolean typesEqual(List<String> left, List<String> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (left.get(i) != null && !left.get(i).equals(right.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean nullableEquals(Object documented, Object source) {
        return documented == null || documented.equals(source);
    }

    private static String describe(List<String> names, List<Boolean> nullable) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(names.get(i));
            if (i < nullable.size() && nullable.get(i)) {
                out.append("?");
            }
        }
        return out.append(']').toString();
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException failure) {
            throw new IllegalStateException("cannot read " + file, failure);
        }
    }
}
