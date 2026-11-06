#!/usr/bin/env python3
"""Check that every module's documented public types are in code and vice versa.

This is a name-level gate, not a signature gate: it compares type names, not members. Member-level
conformance is a review obligation (`07-regeneration.md` §4, row 5).
Examples inside a module file are excluded by reading only the block after '## 3. Signatures'.
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

TYPE_RE = re.compile(
    r"(?m)^[ \t]{0,12}(?:public\s+)?(?:sealed\s+)?(?:static\s+)?(?:final\s+)?(?:record|class|interface|enum)\s+([A-Z][A-Za-z0-9_]*)"
)
CODE_RE = re.compile(r"SC-([A-Z]{2,6})-(\d{4})")


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
                    "CellIterator"}
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
    for path in (ROOT / "src" / "kernel" / "java").rglob("*.java"):
        for match in CODE_RE.finditer(path.read_text()):
            code_codes.add(f"SC-{match.group(1)}-{match.group(2)}")
    unknown = sorted(code_codes - doc_codes)
    if unknown:
        print(f"codes in code but not in docs: {', '.join(unknown)}")
        problems += 1

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

    if problems:
        return 1
    print("spec-check: ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
