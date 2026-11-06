#!/usr/bin/env python3
"""Check that every module's documented public types are in code and vice versa.

The kernel side is a name-level gate over the module files; member-level conformance of the kernel
is proven by SpecIndexTest and of the adapter by AdapterIndexTest, both in kernelTest. The adapter
side (D-74) reads src/main/java as a second source root: every public top-level adapter type is
either documented in adapter.md §15 or listed in tools/spec-uncovered.txt with a reason, and the
list can only shrink. Examples inside a module file are excluded by reading only the block after
'## 3. Signatures'.
The producer table in `finding.md` §6 is compared with `Codes.java`, and every registered code must
have at least one reference in the kernel or adapter sources (D-69). Production forms are resolved
both ways (D-04/X03-D04): `Codes.<field>`, `AdapterCodes.<field>`, `FindingCode.of(Family.x, n)`,
per-family `code(n)` helpers and `SC-` literals in `src/main` (including `SC-MC-*`) must name a
registered or catalogued code that the docs mention; the `CodesTest` mirror and every
"`Codes.all` has N entries" claim are checked against the registry.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODULES = {
    "core": "core", "space": "space", "finding": "finding", "world": "world",
    "claim": "claim", "structure": "structure", "context": "context",
    "document": "document", "engine": "engine", "remedy": "remedy", "spi": "spi",
}
ADAPTER_ROOT = ROOT / "src" / "main" / "java"
UNCOVERED = ROOT / "tools" / "spec-uncovered.txt"
ADAPTER_SECTION = "## 15. Signatures"
OWNERS = set(MODULES) | {"adapter"}

TYPE_RE = re.compile(
    r"(?m)^[ \t]{0,12}(?:public\s+)?(?:sealed\s+)?(?:static\s+)?(?:final\s+)?(?:record|class|interface|enum)\s+([A-Z][A-Za-z0-9_]*)"
)
CODE_RE = re.compile(r"SC-([A-Z]{2,6})-(\d{4})")
ADAPTER_PUBLIC_RE = re.compile(
    r"(?m)^\s*public\s+(?:final\s+|abstract\s+|sealed\s+|non-sealed\s+)*(?:class|interface|record|enum)\s+([A-Za-z0-9_]*)"
)
ADAPTER_ANY_RE = re.compile(
    r"(?m)^\s*(?:(?:public|private|protected|static|final|abstract|sealed|non-sealed)\s+)*(?:class|interface|record|enum)\s+([A-Za-z0-9_]*)"
)
TOP_LEVEL_RE = re.compile(
    r"(?m)^(?:public\s+)?(?:final\s+|abstract\s+|sealed\s+|non-sealed\s+)*(?:record|class|interface|enum)\s+([A-Z][A-Za-z0-9_]*)"
)

KERNEL_SOURCES = ROOT / "src" / "kernel" / "java"
ADAPTER_SOURCES = ROOT / "src" / "main" / "java"
PRODUCER_ROW_RE = re.compile(r"^\|\s*`(SC-[A-Z]{2,6}-\d{4})`\s*\|\s*(\S+)\s*\|\s*(\S+)\s*\|(.*?)\|\s*(\S+)\s*\|")
ENTRY_RE = re.compile(
    r"public static final CodeEntry (\w+) = entry\(\s*FindingCode\.Family\.(\w+),\s*(\d+),\s*Severity\.(\w+)"
)
FAMILY_TAG_RE = re.compile(r"(\w+)\(\s*\"([A-Z]+)\"\s*\)")
CODE_HELPER_RE = re.compile(
    r"private static FindingCode code\(int \w+\)\s*\{\s*return FindingCode\.of\(FindingCode\.Family\.(\w+), \w+\);",
    re.S,
)
CODES_FIELD_RE = re.compile(r"\bCodes\.(\w+)")
ADAPTER_FIELD_RE = re.compile(r"\bAdapterCodes\.(\w+)")
ADAPTER_CODE_RE = re.compile(r"static final FindingCode (\w+) = code\((\d+)\);")
ADAPTER_ENTRY_RE = re.compile(r"static final CodeEntry (\w+) = entry\(")
OF_FAMILY_RE = re.compile(r"FindingCode\.of\(\s*(?:FindingCode\.)?Family\.(\w+)\s*,\s*(\d+)\s*\)")
HELPER_CALL_RE = re.compile(r"\bcode\((\d+)\)")
COUNT_CLAIM_RE = re.compile(r"`Codes\.all` has (\d+) entries")


def docs_block(module: str) -> str:
    text = (ROOT / "docs" / "modules" / f"{module}.md").read_text()
    start = text.find("## 3. Signatures")
    if start < 0:
        start = text.find("## 3. API")
    end = text.find("## 4.", start)
    if start < 0 or end < 0:
        return ""
    return text[start:end]


def code_types(package: str) -> set[str]:
    directory = ROOT / "src" / "kernel" / "java" / "dev" / "vfyjxf" / "cumulus" / "kernel" / package
    names: set[str] = set()
    for path in directory.rglob("*.java"):
        if path.name == "package-info.java":
            continue
        text = path.read_text()
        # top-level and nested declarations; package-private helpers are not in the design
        for match in TYPE_RE.finditer(text):
            names.add(match.group(1))
    return names


def documented_types(module: str) -> set[str]:
    return {m.group(1) for m in TYPE_RE.finditer(docs_block(module))}


def adapter_files() -> list[Path]:
    return [p for p in sorted(ADAPTER_ROOT.rglob("*.java")) if p.name != "package-info.java"]


def adapter_public_types() -> set[str]:
    out: set[str] = set()
    for path in adapter_files():
        match = ADAPTER_PUBLIC_RE.search(path.read_text())
        if match:
            package = ".".join(path.relative_to(ADAPTER_ROOT).parts[:-1])
            out.add(f"{package}.{match.group(1)}")
    return out


def adapter_declared_names() -> set[str]:
    out: set[str] = set()
    for path in adapter_files():
        out.update(m.group(1) for m in ADAPTER_ANY_RE.finditer(path.read_text()))
    return out


def adapter_documented_names() -> tuple[set[str], set[str]]:
    text = (ROOT / "docs" / "modules" / "adapter.md").read_text()
    start = text.find(ADAPTER_SECTION)
    if start < 0:
        raise SystemExit(f"adapter: docs/modules/adapter.md has no '{ADAPTER_SECTION}' block")
    block: list[str] = []
    in_fence = False
    for line in text[start:].split("\n"):
        if line.strip().startswith("```"):
            in_fence = not in_fence
            continue
        if in_fence:
            block.append(line)
    text_block = "\n".join(block)
    all_names = {m.group(1) for m in TYPE_RE.finditer(text_block)}
    top_level = {m.group(1) for m in TOP_LEVEL_RE.finditer(text_block)}
    return all_names, top_level


def uncovered_types() -> dict[str, str]:
    out: dict[str, str] = {}
    for line in UNCOVERED.read_text().split("\n"):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        entry, _, reason = line.partition("#")
        out[entry.strip()] = reason.strip()
    return out


def check_adapter() -> int:
    problems = 0
    public_types = adapter_public_types()
    declared = adapter_declared_names()
    documented, documented_top_level = adapter_documented_names()
    listed = uncovered_types()
    missing_in_code = sorted(name for name in documented if name not in declared)
    if missing_in_code:
        print(f"adapter: documented but not in code: {', '.join(missing_in_code)}")
        problems += 1
    unregistered = sorted(t for t in public_types
                          if t.rsplit(".", 1)[1] not in documented_top_level and t not in listed)
    if unregistered:
        print(f"adapter: public type in neither adapter.md §15 nor spec-uncovered.txt: "
              f"{', '.join(unregistered)}")
        problems += 1
    gone = sorted(t for t in listed if t not in public_types)
    if gone:
        print(f"adapter: listed in spec-uncovered.txt but no longer public, remove the line: "
              f"{', '.join(gone)}")
        problems += 1
    both = sorted(t for t in listed if t.rsplit(".", 1)[1] in documented_top_level)
    if both:
        print(f"adapter: documented in adapter.md §15 while still listed, remove the line: "
              f"{', '.join(both)}")
        problems += 1
    return problems


def registry_entries() -> list[tuple[str, str, int, str]]:
    """Codes.java entries as (field, family, number, severity) in declaration order."""
    text = (KERNEL_SOURCES / "dev/vfyjxf/cumulus/kernel/finding/Codes.java").read_text()
    return [(field, family, int(number), severity)
            for field, family, number, severity in ENTRY_RE.findall(text)]


def family_tags() -> dict[str, str]:
    text = (KERNEL_SOURCES / "dev/vfyjxf/cumulus/kernel/core/FindingCode.java").read_text()
    return dict(FAMILY_TAG_RE.findall(text))


def producer_table_rows() -> dict[str, tuple[str, str, str]]:
    """finding.md §6 rows as code -> (severity, label, owner); SC-MC-* rows are adapter-only."""
    text = (ROOT / "docs" / "modules" / "finding.md").read_text()
    start = text.find("## 6. Code producers")
    end = text.find("## 7.", start)
    rows: dict[str, tuple[str, str, str]] = {}
    for line in text[start:end].splitlines():
        match = PRODUCER_ROW_RE.match(line)
        if match and not match.group(1).startswith("SC-MC-"):
            rows[match.group(1)] = (match.group(2), match.group(3), match.group(5))
    return rows


def check_producer_table() -> int:
    problems = 0
    table = producer_table_rows()
    tags = family_tags()
    registry = {f"SC-{tags[family]}-{number:04d}": severity.upper()
                for _, family, number, severity in registry_entries()}
    for code in sorted(set(table) | set(registry)):
        row, severity = table.get(code), registry.get(code)
        if severity is None:
            print(f"producer table: {code} is in finding.md but not in Codes.all")
            problems += 1
        elif row is None:
            print(f"producer table: {code} has no row in finding.md §6")
            problems += 1
        elif row[0] != severity:
            print(f"producer table: {code} severity {row[0]} in finding.md, {severity} in Codes.all")
            problems += 1
        elif row[1] not in ("P", "\u2013", "T"):
            print(f"producer table: {code} label {row[1]} is neither P, \u2013 nor T")
            problems += 1
        elif row[2] not in OWNERS:
            print(f"producer table: {code} owner {row[2]} names no module")
            problems += 1
    return problems


def production_sources() -> list[tuple[Path, str]]:
    """Kernel and adapter sources that may produce a finding code; tests are not producers."""
    out: list[tuple[Path, str]] = []
    for root in (KERNEL_SOURCES, ADAPTER_SOURCES):
        for path in sorted(root.rglob("*.java")):
            if path.name == "Codes.java" or path.name.endswith("GameTest.java"):
                continue
            out.append((path, path.read_text()))
    return out


def count_unproduced_codes() -> int:
    """A registered code with no reference outside Codes.java and the GameTests is dead."""
    blob = "\n".join(text for _, text in production_sources())
    helper_texts: dict[str, str] = {}
    for _, text in production_sources():
        helper = CODE_HELPER_RE.search(text)
        if helper:
            helper_texts.setdefault(helper.group(1), text)
    tags = family_tags()
    problems = 0
    for field, family, number, _ in registry_entries():
        code = f"SC-{tags[family]}-{number:04d}"
        referenced = (
            f"Codes.{field}" in blob
            or f'"{code}' in blob
            or f"Family.{family}, {number})" in blob
            or f"code({number})" in helper_texts.get(family, "")
        )
        if not referenced:
            print(f"unproduced code: {code} ({field}) has no emit site")
            problems += 1
    return problems


def adapter_catalog() -> dict[str, str]:
    """AdapterCodes' own codes as field -> SC-MC-NNNN."""
    text = (ADAPTER_SOURCES / "dev" / "vfyjxf" / "cumulus" / "mc" / "AdapterCodes.java").read_text()
    tags = family_tags()
    return {field: f"SC-{tags['mc']}-{int(number):04d}"
            for field, number in ADAPTER_CODE_RE.findall(text)}


def check_produced_codes(doc_codes: set[str]) -> int:
    """Every resolved production form names a known code that the docs mention."""
    tags = family_tags()
    registry = {f"SC-{tags[family]}-{number:04d}" for _, family, number, _ in registry_entries()}
    field_codes = {field: f"SC-{tags[family]}-{number:04d}"
                   for field, family, number, _ in registry_entries()}
    catalog = adapter_catalog()
    catalog_entry_fields = set()
    adapter_path = ADAPTER_SOURCES / "dev" / "vfyjxf" / "cumulus" / "mc" / "AdapterCodes.java"
    catalog_entry_fields.update(ADAPTER_ENTRY_RE.findall(adapter_path.read_text()))
    problems = 0
    for path, text in production_sources():
        where = path.relative_to(ROOT)
        produced: set[str] = {f"{family}-{number}" for family, number in CODE_RE.findall(text)}
        for field in CODES_FIELD_RE.findall(text):
            if field in field_codes:
                produced.add(field_codes[field][3:])
        for field in ADAPTER_FIELD_RE.findall(text):
            if field in catalog:
                produced.add(catalog[field][3:])
        for family, number in OF_FAMILY_RE.findall(text):
            tag = tags.get(family)
            if tag:
                produced.add(f"{tag}-{int(number):04d}")
        helper = CODE_HELPER_RE.search(text)
        if helper:
            tag = tags[helper.group(1)]
            produced.update(f"{tag}-{int(number):04d}" for number in HELPER_CALL_RE.findall(text))
        for short in sorted(produced):
            code = f"SC-{short}"
            if short.startswith("MC-"):
                if code not in catalog.values():
                    print(f"produced code: {code} in {where} is not declared in AdapterCodes")
                    problems += 1
            elif code not in registry:
                print(f"produced code: {code} in {where} is not in Codes.all")
                problems += 1
            if code not in doc_codes:
                print(f"produced code: {code} in {where} is absent from the docs")
                problems += 1
    return problems


def check_producer_mirror() -> int:
    """The CodesTest mirror must agree with the registry and the finding.md §6 table."""
    text = (ROOT / "src" / "kernelTest" / "java" / "dev" / "vfyjxf" / "cumulus" / "kernel"
            / "finding" / "CodesTest.java").read_text()
    start = text.find('producers = """') + len('producers = """')
    mirror: dict[str, tuple[str, str, str]] = {}
    for line in text[start:text.find('"""', start)].splitlines():
        parts = line.split()
        if len(parts) == 4:
            mirror[parts[0]] = (parts[1], "\u2013" if parts[2] == "DASH" else parts[2], parts[3])
    tags = family_tags()
    registry = {f"SC-{tags[family]}-{number:04d}": severity.upper()
                for _, family, number, severity in registry_entries()}
    table = producer_table_rows()
    problems = 0
    for code in sorted(set(mirror) | set(registry)):
        row, entry = mirror.get(code), registry.get(code)
        if entry is None:
            print(f"producer mirror: {code} is in CodesTest but not in Codes.all")
            problems += 1
        elif row is None:
            print(f"producer mirror: {code} is in Codes.all but not in CodesTest")
            problems += 1
        elif row[0] != entry:
            print(f"producer mirror: {code} severity {row[0]} in CodesTest, {entry} in Codes.all")
            problems += 1
        elif row[1] != table.get(code, ("", "", ""))[1]:
            print(f"producer mirror: {code} label {row[1]} differs from finding.md §6")
            problems += 1
        elif row[2] != table.get(code, ("", "", ""))[2]:
            print(f"producer mirror: {code} owner {row[2]} differs from finding.md §6")
            problems += 1
    return problems


def check_code_count_claims() -> int:
    """Every documented `Codes.all` count equals the registry size."""
    expected = len(registry_entries())
    problems = 0
    for path in sorted((ROOT / "docs").rglob("*.md")):
        for match in COUNT_CLAIM_RE.finditer(path.read_text()):
            if int(match.group(1)) != expected:
                print(f"code count: {path.relative_to(ROOT)} claims {match.group(1)} "
                      f"entries, Codes.all has {expected}")
                problems += 1
    return problems


def main() -> int:
    problems = 0
    for module, package in MODULES.items():
        doc = documented_types(module)
        code = code_types(package)
        # Code names may be nested; the doc must contain each public top-level name.
        missing_in_code = sorted(doc - code)
        internal = {"BasicExpectationContext", "InMemoryStore", "BuiltinCodes",
                    "BuiltinComputations", "BuiltinExpectations", "BuiltinFocuses",
                    "BuiltinSupport", "DefaultCatalogs", "DefaultCatalogRegistrar",
                    "BuilderImpl", "InMemoryClaimLedger",
                    "CellIterator", "BlueprintRejected", "PlanRejected"}
        missing_in_doc = sorted((code - doc) - internal)
        if missing_in_code:
            print(f"{module}: documented but not in code: {', '.join(missing_in_code)}")
            problems += 1
        if missing_in_doc:
            print(f"{module}: in code but not documented: {', '.join(missing_in_doc)}")
            problems += 1

    doc_codes = set()
    for path in (ROOT / "docs").rglob("*.md"):
        for match in CODE_RE.finditer(path.read_text()):
            doc_codes.add(f"SC-{match.group(1)}-{match.group(2)}")
    code_codes = set()
    for root in (ROOT / "src" / "kernel" / "java", ROOT / "src" / "main" / "java"):
        for path in root.rglob("*.java"):
            if path.name.endswith("GameTest.java"):
                continue
            for match in CODE_RE.finditer(path.read_text()):
                code_codes.add(f"SC-{match.group(1)}-{match.group(2)}")
    unknown = sorted(code_codes - doc_codes)
    if unknown:
        print(f"codes in code but not in docs: {', '.join(unknown)}")
        problems += 1

    problems += check_adapter()

    # Package edges: every kernel import in code must be allowed by the 03-modules table.
    table = (ROOT / "docs" / "design" / "03-modules.md").read_text()
    table = table[table.find("The edges, exactly:"):table.find("Notes:", table.find("The edges, exactly:"))]
    allowed: dict[str, set[str]] = {}
    row = re.compile(r"^\|\s*`([a-z]+)`\s*\|\s*(.*?)\s*\|$", re.M)
    for match in row.finditer(table):
        package = match.group(1)
        allowed[package] = set(re.findall(r"`([a-z]+)`", match.group(2)))
    allowed.setdefault("spi", set(MODULES))
    base = ROOT / "src" / "kernel" / "java" / "dev" / "vfyjxf" / "cumulus" / "kernel"
    for package in MODULES:
        for path in (base / package).rglob("*.java"):
            for imported in re.findall(r"^import dev\.vfyjxf\.cumulus\.kernel\.([a-z]+)\.",
                                       path.read_text(), re.M):
                if imported == package or imported not in MODULES or package == "spi":
                    continue
                if imported not in allowed.get(package, set()):
                    print(f"{package}: imports {imported}, absent from 03-modules")
                    problems += 1

    problems += check_producer_table()
    problems += count_unproduced_codes()
    problems += check_produced_codes(doc_codes)
    problems += check_producer_mirror()
    problems += check_code_count_claims()

    if problems:
        return 1
    print("spec-check: ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
