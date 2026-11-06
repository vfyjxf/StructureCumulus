package dev.vfyjxf.cumulus.kernel;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compares the {@code ## 3. Signatures} block of every module file with the compiled kernel through
 * reflection: every documented public member exists in code with the same arity, parameter and
 * return type names and the same {@code @Nullable} positions, and every public member in code
 * appears in the block. Type-name coverage stays with {@code tools/spec-check.py}; this gate owns
 * the members.
 */
class SpecIndexTest {

    private static final String[] modules = {"core", "space", "finding", "world", "claim", "structure", "context",
            "document", "engine", "remedy", "spi"};

    @Test
    void documentedSignaturesMatchTheCompiledKernel() {
        Set<String> problems = new TreeSet<>();
        for (String module : modules) {
            List<DocType> types = DocParser.parse(signatureBlock(module), problems);
            for (DocType type : types) {
                compareType(type, problems);
            }
        }
        assertTrue(problems.isEmpty(), () -> "spec index drift:\n  " + String.join("\n  ", problems));
    }

    private static String signatureBlock(String module) {
        try {
            String text = Files.readString(Path.of("docs", "modules", module + ".md"));
            int start = text.indexOf("## 3. Signatures");
            if (start < 0) {
                throw new IllegalStateException("docs/modules/" + module + ".md has no '## 3. Signatures' block");
            }
            int end = text.indexOf("## 4.", start);
            StringBuilder block = new StringBuilder();
            boolean inFence = false;
            for (String line : text.substring(start, end < 0 ? text.length() : end).split("\n")) {
                if (line.trim().startsWith("```")) {
                    inFence = !inFence;
                    continue;
                }
                if (inFence) {
                    block.append(line).append('\n');
                }
            }
            return block.toString();
        } catch (Exception failure) {
            throw new IllegalStateException("cannot read docs/modules/" + module + ".md", failure);
        }
    }

    // ------------------------------------------------------------------ doc side

    static final class DocType {
        String fqn;
        String simpleName;
        boolean record;
        boolean enumType;
        boolean interfaceType;
        boolean publicVisibility;
        final List<String> enumConstants = new ArrayList<>();
        final List<String> componentNames = new ArrayList<>();
        final List<Boolean> componentNullable = new ArrayList<>();
        final List<DocMember> members = new ArrayList<>();
        final List<DocType> nested = new ArrayList<>();
    }

    static final class DocMember {
        static final String method = "method";
        static final String constructor = "constructor";
        static final String compactConstructor = "compact-constructor";
        static final String field = "field";

        String kind;
        String name;
        int arity;
        boolean explicitVisibility;
        boolean publicVisibility;
        @Nullable
        String fieldType;
        final List<String> parameterTypes = new ArrayList<>();
        final List<Boolean> parameterNullable = new ArrayList<>();
        @Nullable
        String returnType;
        boolean returnNullable;
    }

    static final class DocParser {

        private static final Pattern identifierPattern = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
        private static final Pattern primitiveTypes = Pattern
                .compile("(int|long|short|byte|char|boolean|float|double|void)");

        private final String text;
        private int pos;
        private String packageName = "";
        private final Set<String> problems;

        private DocParser(String block, Set<String> problems) {
            this.text = stripComments(block);
            this.problems = problems;
        }

        static List<DocType> parse(String block, Set<String> problems) {
            return new DocParser(block, problems).parseTopLevel();
        }

        private List<DocType> parseTopLevel() {
            List<DocType> types = new ArrayList<>();
            while (pos < text.length()) {
                String declaration = scanDeclaration();
                char terminator = eat();
                if (declaration.isBlank()) {
                    continue;
                }
                String normalized = normalize(declaration);
                if (terminator == ';' && normalized.startsWith("package ")) {
                    packageName = normalized.substring("package ".length()).strip();
                    continue;
                }
                DocType type = classifyType(declaration, null);
                if (type == null) {
                    problems.add("unparsable declaration: " + declaration.strip().split("\n")[0]);
                    continue;
                }
                types.add(type);
            }
            return types;
        }

        /** Reads up to the next {@code ;}, {@code {} or {@code }} and leaves the terminator unread. */
        private String scanDeclaration() {
            StringBuilder declaration = new StringBuilder();
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c == ';' || c == '{' || c == '}') {
                    break;
                }
                declaration.append(c);
                pos++;
            }
            return declaration.toString();
        }

        private char eat() {
            if (pos >= text.length()) {
                return 0;
            }
            char c = text.charAt(pos);
            pos++;
            return c;
        }

        private void skipBraces() {
            int depth = 1;
            while (pos < text.length() && depth > 0) {
                char c = text.charAt(pos);
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                }
                pos++;
            }
        }

        private @Nullable DocType classifyType(String declaration, @Nullable DocType enclosing) {
            String semiRaw = stripLeadingAnnotations(stripModifiers(declaration)).strip();
            String normalized = removeAnnotations(semiRaw);
            String keyword = null;
            for (String candidate : new String[]{"record ", "class ", "interface ", "enum "}) {
                if (normalized.startsWith(candidate)) {
                    keyword = candidate;
                    break;
                }
            }
            if (keyword == null) {
                return null;
            }
            String rest = normalized.substring(keyword.length());
            Matcher name = identifierPattern.matcher(rest);
            if (!name.find() || name.start() != 0) {
                return null;
            }
            DocType type = new DocType();
            type.simpleName = name.group();
            type.record = keyword.equals("record ");
            type.enumType = keyword.equals("enum ");
            type.interfaceType = keyword.equals("interface ");
            String typeVisibility = visibilityKeyword(declaration);
            type.publicVisibility = "public".equals(typeVisibility)
                    || (typeVisibility == null && enclosing != null && enclosing.interfaceType);
            type.fqn = enclosing == null ? packageName + "." + type.simpleName : enclosing.fqn + "$" + type.simpleName;
            if (type.record) {
                collectComponents(semiRaw.substring(keyword.length() + type.simpleName.length()), type);
            }
            parseBody(type);
            return type;
        }

        private void collectComponents(String header, DocType type) {
            int open = header.indexOf('(');
            if (open < 0) {
                return;
            }
            int depth = 0;
            int close = -1;
            for (int i = open; i < header.length(); i++) {
                char c = header.charAt(i);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth == 0) {
                        close = i;
                        break;
                    }
                }
            }
            if (close < 0) {
                return;
            }
            for (String component : splitParameters(header.substring(open + 1, close))) {
                String clean = removeAnnotations(component).strip();
                Matcher identifier = identifierPattern.matcher(clean);
                String componentName = "";
                int nameStart = -1;
                while (identifier.find()) {
                    componentName = identifier.group();
                    nameStart = identifier.start();
                }
                if (nameStart >= 0) {
                    type.componentNames.add(componentName);
                    type.componentNullable.add(stripGenerics(component).contains("@Nullable"));
                }
            }
        }

        private void parseBody(DocType type) {
            boolean constantsPending = type.enumType;
            while (pos < text.length()) {
                String declaration = scanDeclaration();
                char terminator = eat();
                if (declaration.isBlank() && terminator == '}') {
                    return;
                }
                if (constantsPending && terminator != '{') {
                    for (String constant : declaration.split(",")) {
                        Matcher identifier = identifierPattern.matcher(removeAnnotations(constant).strip());
                        if (identifier.find() && identifier.start() == 0) {
                            type.enumConstants.add(identifier.group());
                        }
                    }
                    constantsPending = false;
                    if (terminator == '}') {
                        return;
                    }
                    continue;
                }
                constantsPending = false;
                if (terminator == ';' || terminator == 0) {
                    type.members.addAll(classifyMember(declaration, type));
                    continue;
                }
                if (terminator == '}') {
                    type.members.addAll(classifyMember(declaration, type));
                    return;
                }
                DocType nested = classifyType(declaration, type);
                if (nested != null) {
                    type.nested.add(nested);
                    continue;
                }
                type.members.addAll(classifyMember(declaration, type));
                skipBraces();
            }
        }

        private List<DocMember> classifyMember(String declaration, DocType enclosing) {
            String visibility = visibilityKeyword(declaration);
            if ("private".equals(visibility)) {
                return List.of();
            }
            boolean explicitVisibility = visibility != null;
            boolean publicVisibility = "public".equals(visibility) || (!explicitVisibility && enclosing.interfaceType);
            String normalized = normalize(declaration);
            if (normalized.isBlank()) {
                return List.of();
            }
            int open = topLevelParen(normalized);
            boolean field = open < 0 || initializerBefore(normalized, open);
            if (field) {
                return classifyFields(normalized, enclosing, explicitVisibility, publicVisibility);
            }
            DocMember member = new DocMember();
            member.explicitVisibility = explicitVisibility;
            member.publicVisibility = publicVisibility;
            String before = normalized.substring(0, open).stripTrailing();
            member.name = trailingIdentifier(before);
            if (member.name.isEmpty()) {
                return List.of();
            }
            String returnType = stripTypeParameters(
                before.substring(0, before.length() - member.name.length()).strip()
            );
            String parameters = balancedGroup(rawParameters(declaration), topLevelParen(rawParameters(declaration)));
            if (parameters == null) {
                return List.of();
            }
            if (returnType.isEmpty() && member.name.equals(enclosing.simpleName)) {
                member.kind = DocMember.constructor;
            } else {
                member.kind = DocMember.method;
                member.returnType = simpleType(returnType);
                String head = headBeforeParameters(declaration);
                member.returnNullable = head.substring(head.lastIndexOf(')') + 1).contains("@Nullable");
            }
            for (String parameter : splitParameters(parameters)) {
                String clean = removeAnnotations(parameter).strip();
                String parameterName = trailingIdentifier(clean);
                member.parameterNullable.add(stripGenerics(parameter).contains("@Nullable"));
                member.parameterTypes
                        .add(simpleType(clean.substring(0, clean.length() - parameterName.length()).strip()));
            }
            member.arity = member.parameterTypes.size();
            return List.of(member);
        }

        private List<DocMember> classifyFields(
            String normalized,
            DocType enclosing,
            boolean explicitVisibility,
            boolean publicVisibility
        ) {
            List<DocMember> fields = new ArrayList<>();
            String sharedType = null;
            for (String declarator : splitTopLevel(normalized)) {
                String clean = declarator.strip();
                if (clean.isEmpty()) {
                    continue;
                }
                String head = (clean.contains("=") ? clean.substring(0, clean.indexOf('=')) : clean).strip();
                DocMember member = new DocMember();
                member.explicitVisibility = explicitVisibility;
                member.publicVisibility = publicVisibility;
                member.name = trailingIdentifier(head);
                if (member.name.isEmpty()) {
                    continue;
                }
                String type = head.substring(0, head.length() - member.name.length()).strip();
                sharedType = sharedType == null ? simpleType(type) : sharedType;
                member.fieldType = sharedType;
                if (member.name.equals(enclosing.simpleName)) {
                    member.kind = DocMember.compactConstructor;
                    member.arity = enclosing.componentNames.size();
                } else {
                    member.kind = DocMember.field;
                }
                fields.add(member);
            }
            return fields;
        }

        /** The declaration with annotations removed but modifiers kept, for reading nullness. */
        private static String rawParameters(String declaration) {
            return declaration.replaceAll("@[A-Za-z_$][A-Za-z0-9_$]*\\([^)]*\\)", " ");
        }

        /** Whether a top-level {@code =} precedes the first top-level {@code (}: an initializer. */
        private static boolean initializerBefore(String declaration, int open) {
            return declaration.substring(0, open).contains("=");
        }

        /** The declaration with annotations, modifiers and line breaks removed. */
        private static String normalize(String declaration) {
            return removeAnnotations(stripModifiers(declaration)).strip();
        }

        /** The declaration without the annotations that precede the type keyword. */
        private static String stripLeadingAnnotations(String text) {
            return text.replaceFirst("^\\s*(@[A-Za-z_$][A-Za-z0-9_$]*(\\([^)]*\\))?\\s*)+", "");
        }

        /** The declaration with modifiers and line breaks removed, annotations kept. */
        private static String stripModifiers(String declaration) {
            String out = declaration;
            for (String modifier : new String[]{"public", "private", "protected", "static", "final", "default",
                    "abstract", "sealed", "non-sealed", "synchronized", "native", "strictfp", "transient",
                    "volatile"}) {
                out = out.replaceFirst("(^|\\s)" + modifier + "(?=\\s|$)", " ");
            }
            return out.strip();
        }

        private static String removeAnnotations(String text) {
            return text.replaceAll("@[A-Za-z_$][A-Za-z0-9_$]*(\\([^)]*\\))?", " ");
        }

        private static @Nullable String visibilityKeyword(String declaration) {
            for (String keyword : new String[]{"public", "private", "protected"}) {
                if (Pattern.compile("(^|\\s)" + keyword + "(\\s|\\()").matcher(declaration).find()) {
                    return keyword;
                }
            }
            return null;
        }

        /** The declaration up to the first top-level {@code (}, keeping annotations. */
        private static String headBeforeParameters(String declaration) {
            String withoutAnnotationArguments = declaration.replaceAll("@[A-Za-z_$][A-Za-z0-9_$]*\\([^)]*\\)", " ");
            int depth = 0;
            for (int i = 0; i < withoutAnnotationArguments.length(); i++) {
                char c = withoutAnnotationArguments.charAt(i);
                if (c == '(' && depth == 0) {
                    return withoutAnnotationArguments.substring(0, i);
                }
                if (c == '<' || c == '[') {
                    depth++;
                } else if (c == '>' || c == ']') {
                    depth--;
                }
            }
            return withoutAnnotationArguments;
        }

        private static int topLevelParen(String declaration) {
            int depth = 0;
            for (int i = 0; i < declaration.length(); i++) {
                char c = declaration.charAt(i);
                if (c == '(' && depth == 0) {
                    return i;
                }
                if (c == '<' || c == '[') {
                    depth++;
                } else if (c == '>' || c == ']') {
                    depth--;
                }
            }
            return -1;
        }

        private static String stripTypeParameters(String type) {
            if (!type.startsWith("<")) {
                return type;
            }
            int depth = 0;
            for (int i = 0; i < type.length(); i++) {
                char c = type.charAt(i);
                if (c == '<') {
                    depth++;
                } else if (c == '>') {
                    depth--;
                    if (depth == 0) {
                        return type.substring(i + 1).strip();
                    }
                }
            }
            return type;
        }

        /** The simple type name, or {@code null} for a bare type variable such as {@code T}. */
        private static @Nullable String simpleType(String type) {
            String stripped = stripGenerics(type.strip());
            String name = stripped.substring(stripped.lastIndexOf('.') + 1).replace("...", "[]").strip();
            if (name.matches("[A-Z]") || name.isBlank()) {
                return null;
            }
            if (primitiveTypes.matcher(name).matches() || name.matches("[A-Z][A-Za-z0-9_$]*(\\[\\])*")) {
                return name;
            }
            return null;
        }

        private static String stripGenerics(String type) {
            StringBuilder out = new StringBuilder();
            int depth = 0;
            for (int i = 0; i < type.length(); i++) {
                char c = type.charAt(i);
                if (c == '<') {
                    depth++;
                } else if (c == '>') {
                    depth--;
                } else if (depth == 0) {
                    out.append(c);
                }
            }
            return out.toString();
        }

        private static String trailingIdentifier(String text) {
            Matcher identifier = identifierPattern.matcher(text);
            String found = "";
            int end = -1;
            int search = 0;
            while (identifier.find(search)) {
                if (identifier.end() == text.length()
                        || !Character.isJavaIdentifierPart(text.charAt(identifier.end()))) {
                    found = identifier.group();
                    end = identifier.end();
                }
                search = identifier.end();
            }
            return found;
        }

        private static @Nullable String balancedGroup(String text, int open) {
            int depth = 0;
            for (int i = open; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth == 0) {
                        return text.substring(open + 1, i);
                    }
                }
            }
            return null;
        }

        private static List<String> splitParameters(String parameters) {
            return splitTopLevel(parameters);
        }

        private static List<String> splitTopLevel(String text) {
            List<String> out = new ArrayList<>();
            int depth = 0;
            boolean quoted = false;
            StringBuilder current = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (quoted) {
                    current.append(c);
                    if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) {
                        quoted = false;
                    }
                    continue;
                }
                if (c == '"') {
                    quoted = true;
                } else if (c == '<' || c == '(' || c == '[' || c == '{') {
                    depth++;
                } else if (c == '>' || c == ')' || c == ']' || c == '}') {
                    depth--;
                } else if (c == ',' && depth == 0) {
                    out.add(current.toString());
                    current.setLength(0);
                    continue;
                }
                current.append(c);
            }
            if (!current.toString().strip().isEmpty()) {
                out.add(current.toString());
            }
            return out;
        }

        private static String stripComments(String block) {
            StringBuilder out = new StringBuilder();
            boolean line = false;
            boolean blockComment = false;
            boolean string = false;
            char previous = 0;
            for (int i = 0; i < block.length(); i++) {
                char c = block.charAt(i);
                char next = i + 1 < block.length() ? block.charAt(i + 1) : 0;
                if (line) {
                    if (c == '\n') {
                        line = false;
                        out.append(c);
                    }
                    continue;
                }
                if (blockComment) {
                    if (previous == '*' && c == '/') {
                        blockComment = false;
                    }
                    previous = c;
                    continue;
                }
                if (string) {
                    out.append(c);
                    if (c == '"' && previous != '\\') {
                        string = false;
                    }
                    previous = c;
                    continue;
                }
                if (c == '/' && next == '/') {
                    line = true;
                    i++;
                    continue;
                }
                if (c == '/' && next == '*') {
                    blockComment = true;
                    i++;
                    continue;
                }
                if (c == '"') {
                    string = true;
                }
                out.append(c);
                previous = c;
            }
            return out.toString();
        }
    }

    // ------------------------------------------------------------------ comparison

    private static void compareType(DocType type, Set<String> problems) {
        Class<?> code;
        try {
            code = Class.forName(type.fqn);
        } catch (ClassNotFoundException failure) {
            problems.add(type.fqn + ": documented but not compiled");
            return;
        }
        compareMembers(type, code, problems);
        compareNested(type, code, problems);
    }

    private static void compareNested(DocType type, Class<?> code, Set<String> problems) {
        Map<String, Class<?>> codeNested = new LinkedHashMap<>();
        for (Class<?> nested : code.getDeclaredClasses()) {
            if (Modifier.isPublic(nested.getModifiers())) {
                codeNested.put(nested.getSimpleName(), nested);
            }
        }
        for (DocType nested : type.nested) {
            Class<?> match = codeNested.remove(nested.simpleName);
            if (match == null) {
                problems.add(type.fqn + "." + nested.simpleName + ": documented nested type not in code");
                continue;
            }
            compareMembers(nested, match, problems);
            compareNested(nested, match, problems);
        }
        for (String missing : codeNested.keySet()) {
            problems.add(type.fqn + "." + missing + ": public nested type missing from the block");
        }
    }

    private static void compareMembers(DocType type, Class<?> code, Set<String> problems) {
        if (type.enumType) {
            Set<String> compiled = new TreeSet<>();
            for (Object constant : code.getEnumConstants()) {
                compiled.add(((Enum<?>) constant).name());
            }
            Set<String> documented = new TreeSet<>(type.enumConstants);
            for (String name : documented) {
                if (!compiled.contains(name)) {
                    problems.add(type.fqn + "." + name + ": documented enum constant not in code");
                }
            }
            for (String name : compiled) {
                if (!documented.contains(name)) {
                    problems.add(type.fqn + "." + name + ": enum constant missing from the block");
                }
            }
        }
        compareComponents(type, code, problems);
        compareExecutables(type, code, problems);
        compareFields(type, code, problems);
    }

    private static void compareComponents(DocType type, Class<?> code, Set<String> problems) {
        if (!type.record) {
            return;
        }
        Map<String, RecordComponent> compiled = new LinkedHashMap<>();
        for (RecordComponent component : code.getRecordComponents()) {
            compiled.put(component.getName(), component);
        }
        for (int i = 0; i < type.componentNames.size(); i++) {
            String name = type.componentNames.get(i);
            RecordComponent match = compiled.remove(name);
            if (match == null) {
                problems.add(type.fqn + "." + name + ": documented record component not in code");
            } else if (match.getAnnotatedType().isAnnotationPresent(Nullable.class) != type.componentNullable.get(i)) {
                problems.add(type.fqn + "." + name + ": record component nullness differs from the block");
            }
        }
        for (String name : compiled.keySet()) {
            problems.add(type.fqn + "." + name + ": record component missing from the block");
        }
    }

    private static void compareExecutables(DocType type, Class<?> code, Set<String> problems) {
        // A method that only re-declares a supertype signature is documented on that supertype.
        Set<String> inherited = inheritedSignatures(code);
        List<Method> codeMethods = new ArrayList<>();
        Map<String, List<Method>> declaredMethods = new LinkedHashMap<>();
        for (Method method : code.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic() || method.isBridge()) {
                continue;
            }
            declaredMethods
                    .computeIfAbsent(method.getName() + "/" + method.getParameterCount(), key -> new ArrayList<>())
                    .add(method);
            if (method.getName().equals("equals")
                    || method.getName().equals("hashCode")
                    || method.getName().equals("toString")) {
                continue;
            }
            if (code.isRecord() && method.getParameterCount() == 0 && hasComponent(code, method.getName())) {
                continue;
            }
            if (code.isEnum() && method.getParameterCount() == 0 && method.getName().equals("values")) {
                continue;
            }
            if (code.isEnum() && method.getParameterCount() == 1 && method.getName().equals("valueOf")) {
                continue;
            }
            if (inherited.contains(method.getName() + "/" + method.getParameterCount())) {
                continue;
            }
            codeMethods.add(method);
        }
        List<Constructor<?>> codeConstructors = new ArrayList<>();
        for (Constructor<?> constructor : code.getDeclaredConstructors()) {
            if (!Modifier.isPublic(constructor.getModifiers()) || constructor.isSynthetic()) {
                continue;
            }
            if (code.isRecord() && constructor.getParameterCount() == type.componentNames.size()) {
                continue;
            }
            codeConstructors.add(constructor);
        }
        List<DocMember> documented = new ArrayList<>();
        for (DocMember member : type.members) {
            if (member.kind.equals(DocMember.field) || member.kind.equals(DocMember.compactConstructor)) {
                continue;
            }
            if (member.name.equals("equals") || member.name.equals("hashCode") || member.name.equals("toString")) {
                continue;
            }
            if (member.publicVisibility || member.explicitVisibility) {
                documented.add(member);
            }
        }
        for (DocMember member : documented) {
            String signature = (member.kind.equals(DocMember.constructor) ? "<init>/" : member.name + "/")
                    + member.arity;
            boolean declared = member.kind.equals(DocMember.constructor)
                    ? anyConstructorOfArity(codeConstructors, member.arity)
                    : declaredMethods.containsKey(member.name + "/" + member.arity)
                            || inherited.contains(member.name + "/" + member.arity);
            if (!declared) {
                problems.add(type.fqn + "." + member.name + ": documented but not in code");
                continue;
            }
            List<? extends Executable> candidates = member.kind.equals(DocMember.constructor)
                    ? constructorsOfArity(codeConstructors, member.arity)
                    : declaredMethods.get(member.name + "/" + member.arity);
            String mismatch = candidates == null ? null : matchExecutable(member, candidates);
            if (mismatch != null) {
                problems.add(type.fqn + "." + member.name + ": " + mismatch);
            }
        }
        Set<String> documentedSignatures = new TreeSet<>();
        for (DocMember member : documented) {
            if (member.publicVisibility) {
                documentedSignatures.add(
                    (member.kind.equals(DocMember.constructor) ? "<init>/" : member.name + "/") + member.arity
                );
            }
        }
        for (Method method : codeMethods) {
            if (!documentedSignatures.contains(method.getName() + "/" + method.getParameterCount())) {
                problems.add(type.fqn + "." + method.getName() + ": public method missing from the block");
            }
        }
        for (Constructor<?> constructor : codeConstructors) {
            if (!documentedSignatures.contains("<init>/" + constructor.getParameterCount())) {
                problems.add(
                    type.fqn + ": public constructor of " + constructor.getParameterCount()
                            + " parameters missing from the block"
                );
            }
        }
    }

    private static boolean anyConstructorOfArity(List<Constructor<?>> constructors, int arity) {
        return constructors.stream().anyMatch(constructor -> constructor.getParameterCount() == arity);
    }

    private static List<Constructor<?>> constructorsOfArity(List<Constructor<?>> constructors, int arity) {
        List<Constructor<?>> out = new ArrayList<>();
        for (Constructor<?> constructor : constructors) {
            if (constructor.getParameterCount() == arity) {
                out.add(constructor);
            }
        }
        return out;
    }

    private static Set<String> inheritedSignatures(Class<?> code) {
        Set<Class<?>> supertypes = new TreeSet<>(Comparator.comparing(Class::getName));
        collectSupertypes(code, supertypes);
        Set<String> inherited = new HashSet<>();
        for (Class<?> supertype : supertypes) {
            for (Method method : supertype.getDeclaredMethods()) {
                inherited.add(method.getName() + "/" + method.getParameterCount());
            }
        }
        return inherited;
    }

    private static void collectSupertypes(Class<?> code, Set<Class<?>> supertypes) {
        if (code.getSuperclass() != null && supertypes.add(code.getSuperclass())) {
            collectSupertypes(code.getSuperclass(), supertypes);
        }
        for (Class<?> parent : code.getInterfaces()) {
            if (supertypes.add(parent)) {
                collectSupertypes(parent, supertypes);
            }
        }
    }

    private static @Nullable String matchExecutable(DocMember member, List<? extends Executable> code) {
        for (Executable executable : code) {
            String name = executable instanceof Constructor<?>
                    ? executable.getDeclaringClass().getSimpleName()
                    : executable.getName();
            if (!name.equals(member.name) || executable.getParameterCount() != member.arity) {
                continue;
            }
            boolean typesMatch = true;
            for (int i = 0; i < member.arity; i++) {
                String documented = member.parameterTypes.get(i);
                if (documented != null && !documented.equals(executable.getParameterTypes()[i].getSimpleName())) {
                    typesMatch = false;
                    break;
                }
            }
            if (typesMatch && member.kind.equals(DocMember.method) && member.returnType != null) {
                typesMatch = member.returnType.equals(((Method) executable).getReturnType().getSimpleName());
            }
            if (!typesMatch) {
                continue;
            }
            for (int i = 0; i < member.arity; i++) {
                if (executable.getAnnotatedParameterTypes()[i].isAnnotationPresent(Nullable.class)
                        != member.parameterNullable.get(i)) {
                    return "parameter " + (i + 1) + " nullness differs from the block";
                }
            }
            if (member.kind.equals(DocMember.method)
                    && ((Method) executable).getAnnotatedReturnType().isAnnotationPresent(Nullable.class)
                            != member.returnNullable) {
                return "return nullness differs from the block";
            }
            return null;
        }
        return "documented types differ from code";
    }

    private static void compareFields(DocType type, Class<?> code, Set<String> problems) {
        Map<String, DocMember> documented = new LinkedHashMap<>();
        for (DocMember member : type.members) {
            if (member.kind.equals(DocMember.field) && member.publicVisibility) {
                documented.put(member.name, member);
            }
        }
        Map<String, Field> compiled = new LinkedHashMap<>();
        for (Field field : code.getDeclaredFields()) {
            if (!Modifier.isPublic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            if (code.isEnum() && field.isEnumConstant()) {
                continue;
            }
            compiled.put(field.getName(), field);
        }
        for (Map.Entry<String, DocMember> entry : documented.entrySet()) {
            Field match = compiled.remove(entry.getKey());
            if (match == null) {
                problems.add(type.fqn + "." + entry.getKey() + ": documented field not in code");
            } else
                if (entry.getValue().fieldType != null
                        && !entry.getValue().fieldType.equals(match.getType().getSimpleName())) {
                            problems.add(
                                type.fqn + "." + entry.getKey() + ": documented field type "
                                        + entry.getValue().fieldType + " differs from code "
                                        + match.getType().getSimpleName()
                            );
                        }
        }
        for (String name : compiled.keySet()) {
            problems.add(type.fqn + "." + name + ": public field missing from the block");
        }
    }

    private static boolean hasComponent(Class<?> code, String name) {
        for (RecordComponent component : code.getRecordComponents()) {
            if (component.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
