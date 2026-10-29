#!/usr/bin/env python3
"""Regenerate the review app's code, decision and test indexes from the design set."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

VISUAL = Path(__file__).resolve().parent.parent
ROOT = VISUAL.parent
DATA = VISUAL / "src" / "data"

KERNEL_FAMILIES = ("SPC", "OCC", "EXP", "CLM", "BPT", "CHK", "DOC", "CTX", "ENG", "DIA", "RMD", "SPI", "SET", "MC")
SEVERITIES = ("ERROR", "WARNING", "INFO")
CODE_PATTERN = re.compile(r"SC-([A-Z]{2,6})-(\d{4})\Z")
ID_PATTERN = re.compile(r"[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*\Z")
ENTRY_PATTERN = re.compile(r"public static final CodeEntry (\w+) = entry\((.*?)\);\n", re.S)
ENTRY_DECLARATION = re.compile(r"public static final CodeEntry (\w+)\s*(?:=|;)")


def strip_comments(text: str) -> str:
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def write(path: Path, body: str) -> None:
    path.write_text(body)


def fail(message: str) -> None:
    raise SystemExit(f"review data: {message}")


def canonical_id(field: str) -> str:
    """A CodeEntry field name (lowerCamel after the rename) back to its UPPER_SNAKE data id."""
    if field.isupper():
        return field
    head = re.sub(r"([A-Z]+)([A-Z][a-z])", r"\1_\2", field)
    return re.sub(r"([a-z0-9])([A-Z])", r"\1_\2", head).upper()


def canonical_family(token: str) -> str:
    family = token.upper()
    if family not in KERNEL_FAMILIES:
        fail(f"FindingCode.Family.{token} is not a kernel family {KERNEL_FAMILIES}")
    return family


def canonical_severity(token: str) -> str:
    severity = token.upper()
    if severity not in SEVERITIES:
        fail(f"Severity.{token} is not a canonical severity {SEVERITIES}")
    return severity


def validate_entry(entry: dict) -> None:
    match = CODE_PATTERN.match(entry["code"])
    if not match:
        fail(f"{entry['code']!r} is not an SC-<FAMILY>-<NNNN> code")
    if match.group(1) != entry["family"]:
        fail(f"{entry['code']!r} disagrees with family {entry['family']!r}")
    if not ID_PATTERN.match(entry["id"]):
        fail(f"{entry['id']!r} is not an UPPER_SNAKE data id")
    if entry["severity"] not in SEVERITIES:
        fail(f"{entry['id']!r} has non-canonical severity {entry['severity']!r}")
    if not entry["producer"]:
        fail(f"{entry['code']!r} has no producer row in docs/modules/finding.md")


def extract_codes() -> None:
    source = (ROOT / "src/kernel/java/dev/vfyjxf/cumulus/kernel/finding/Codes.java").read_text()
    producers = {}
    for line in (ROOT / "docs/modules/finding.md").read_text().splitlines():
        match = re.match(r"\|\s*`(SC-[A-Z]{2,6}-\d{4})`\s*\|.*\|\s*(.*?)\s*\|$", line)
        if match:
            producers[match.group(1)] = match.group(2).replace("`", "").strip()
    entries = []
    parsed_names = []
    for match in ENTRY_PATTERN.finditer(source):
        name, body = match.group(1), match.group(2)
        head = re.search(r"FindingCode\.Family\.(\w+),\s*(\d+),\s*Severity\.(\w+)", body)
        if not head:
            fail(f"CodeEntry {name} does not match entry(FindingCode.Family.<tag>, <number>, Severity.<tag>)")
        parsed_names.append(name)
        strings = []
        for literal in re.findall(r'"((?:\\.|[^"\\])*)"', body):
            try:
                strings.append(json.loads('"' + literal + '"'))
            except Exception:
                strings.append(literal)
        family = canonical_family(head.group(1))
        number = int(head.group(2))
        code = f"SC-{family}-{number:04d}"
        entries.append({
            "id": canonical_id(name), "code": code, "family": family,
            "number": number, "severity": canonical_severity(head.group(3)),
            "summary": strings[0] if strings else "",
            "body": "".join(strings[1:]),
            "producer": producers.get(code, ""),
        })
    declared_names = [match.group(1) for match in ENTRY_DECLARATION.finditer(strip_comments(source))]
    unparsed = [name for name in declared_names if name not in set(parsed_names)]
    if unparsed:
        fail("CodeEntry declarations were not recognized as entry(...): " + ", ".join(unparsed))
    entries = sorted({entry["code"]: entry for entry in entries}.values(), key=lambda entry: entry["code"])
    for entry in entries:
        validate_entry(entry)
    lines = [
        "export type Code = {", "  id: string;", "  code: string;", "  family: string;",
        "  number: number;", '  severity: "ERROR" | "WARNING" | "INFO";',
        "  summary: string;", "  body: string;", "  producer: string;", "};", "",
        "export const codes: Code[] = [",
    ]
    lines += ["  " + json.dumps(entry, ensure_ascii=False) + "," for entry in entries]
    lines += ["];", "", f"export const CODE_COUNT = {len(entries)};", ""]
    write(DATA / "codes.ts", "\n".join(lines))


def extract_decisions() -> None:
    text = (ROOT / "docs/design/05-decisions.md").read_text()
    items = []
    for match in re.finditer(r"### (D-\d+) — (.*?)\n(.*?)(?=\n### |\n## |\Z)", text, re.S):
        ident, title, body = match.group(1), match.group(2).strip(), match.group(3).strip()
        body = re.sub(r"```.*?```", "", body, flags=re.S)
        body = body.replace("`", "").replace("**", "")
        paragraphs = []
        for part in re.split(r"\n\s*\n", body):
            lines = [line.strip() for line in part.splitlines() if line.strip()]
            prose = " ".join(line for line in lines if not line.startswith("|"))
            if prose:
                paragraphs.append(re.sub(r"\s+", " ", prose))
        paragraph = next((part for part in paragraphs if len(part) > 30), "")
        if ident == "D-20":
            paragraph = ("Twenty-four old questions are answered by deletion: no predicate/blueprint "
                         "cycle, no stage carrier, one ReadScope, one Coverage, one publication, one "
                         "conflict rule, one code table. The full table is in docs/design/05-decisions.md.")
        if ident == "D-36":
            paragraph = ("The nested-extent seam is now generic per holder: NestedExtentHolder<H> and "
                         "NestedExtentHolders.block/entity name the subject, NestedExtentProvider<H> is "
                         "one get(actual) provider, NestedExtentLookup is the immutable registry, and one "
                         "CapabilityNestedExtentProvider<H, C> covers both capability signatures through "
                         "NestedExtentAccesses.block()/entity(); NestedExtentProviders.block/entity are "
                         "the standard declarations. The old aggregate mc.NestedExtentLookup and "
                         "CapabilityLookup are deleted. See docs/design/05-decisions.md.")
        if not paragraph:
            paragraph = f"{title}. See docs/design/05-decisions.md."
        items.append({"id": ident, "title": title, "summary": paragraph})
    lines = [
        "export type Decision = {", "  id: string;", "  title: string;", "  summary: string;", "};", "",
        "export const decisions: Decision[] = [",
    ]
    lines += ["  " + json.dumps(entry, ensure_ascii=False) + "," for entry in items]
    lines += ["];", "", f"export const DECISION_COUNT = {len(items)};", ""]
    write(DATA / "decisions.ts", "\n".join(lines))


def extract_tests() -> None:
    classes = []
    for path in sorted((ROOT / "src/kernelTest/java").rglob("*.java")):
        text = path.read_text()
        count = len(re.findall(r"@Test", text))
        if count == 0:
            continue
        package = re.search(r"package\s+([\w.]+);", text).group(1).split(".")[-1]
        classes.append({"file": str(path.relative_to(ROOT / "src/kernelTest/java")),
                        "package": package, "className": path.stem, "count": count})
    groups = {}
    for entry in classes:
        groups[entry["package"]] = groups.get(entry["package"], 0) + entry["count"]
    lines = [
        "export type TestClass = {", "  file: string;", "  package: string;",
        "  className: string;", "  count: number;", "};", "",
        "export const testClasses: TestClass[] = [",
    ]
    lines += ["  " + json.dumps(entry, ensure_ascii=False) + "," for entry in classes]
    lines += ["];", "", "export const TEST_GROUPS = ["]
    lines += ["  " + json.dumps({"name": name, "count": count}, ensure_ascii=False) + ","
              for name, count in sorted(groups.items())]
    lines += ["];", "", f"export const TEST_COUNT = {sum(groups.values())};", ""]
    write(DATA / "tests.ts", "\n".join(lines))


def main() -> int:
    extract_codes()
    extract_decisions()
    extract_tests()
    print("review data: codes, decisions and tests regenerated")
    return 0


if __name__ == "__main__":
    sys.exit(main())
