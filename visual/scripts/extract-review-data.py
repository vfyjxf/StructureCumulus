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


def write(path: Path, body: str) -> None:
    path.write_text(body)


def extract_codes() -> None:
    source = (ROOT / "src/kernel/java/dev/vfyjxf/cumulus/kernel/finding/Codes.java").read_text()
    producers = {}
    for line in (ROOT / "docs/modules/finding.md").read_text().splitlines():
        match = re.match(r"\|\s*`(SC-[A-Z]{2,6}-\d{4})`\s*\|.*\|\s*(.*?)\s*\|$", line)
        if match:
            producers[match.group(1)] = match.group(2).replace("`", "").strip()
    entries = []
    for match in re.finditer(r"public static final CodeEntry (\w+) = entry\((.*?)\);\n", source, re.S):
        name, body = match.group(1), match.group(2)
        head = re.search(r"FindingCode\.Family\.(\w+),\s*(\d+),\s*Severity\.(\w+)", body)
        if not head:
            continue
        strings = []
        for literal in re.findall(r'"((?:\\.|[^"\\])*)"', body):
            try:
                strings.append(json.loads('"' + literal + '"'))
            except Exception:
                strings.append(literal)
        code = f"SC-{head.group(1)}-{int(head.group(2)):04d}"
        entries.append({
            "id": name, "code": code, "family": head.group(1),
            "number": int(head.group(2)), "severity": head.group(3),
            "summary": strings[0] if strings else "",
            "body": "".join(strings[1:]),
            "producer": producers.get(code, ""),
        })
    entries = sorted({entry["code"]: entry for entry in entries}.values(), key=lambda entry: entry["code"])
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
