#!/usr/bin/env python3
"""Emit the two shipped relay-station documents; --check byte-compares a regeneration.

The module is the source of truth for the station's repetition: one pod function placed at four
turns, one tier function at three radii and heights, and one hand-written block table (no registry
read, no clock, no randomness, no hash-ordered iteration).
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/data/structurecumulus/cumulus/structure"

FULL_ID = "structurecumulus:demo_station"
COMPACT_ID = "structurecumulus:demo_station_compact"
GROUP = "demo_station"
FULL_BOUNDS = [0, 0, 0, 13, 11, 13]
COMPACT_BOUNDS = [0, 0, 0, 13, 8, 13]
CENTER = 6
FULL_CELL_BAND = (450, 512)
CELL_CAP = 512
MATERIAL_CAP = 64
DIRECTIONAL_FLOOR = 40
TURNS = ("none", "clockwise", "half", "counterclockwise")
POD_TURN = {"pod_north": 0, "pod_east": 1, "pod_south": 2, "pod_west": 3}

# The hand-written block table: the generator never touches the registry.
STONE = "minecraft:stone_bricks"
CHISELED = "minecraft:chiseled_stone_bricks"
ANDESITE = "minecraft:polished_andesite"
SLAB = "minecraft:stone_brick_slab"
DEEPSLATE = "minecraft:deepslate_tiles"
GLASS = "minecraft:glass"
SEA_LANTERN = "minecraft:sea_lantern"
COPPER = "minecraft:copper_block"
CUT_COPPER = "minecraft:cut_copper"
LANTERN = "minecraft:lantern"
QUARTZ = "minecraft:quartz_block"
CHISELED_QUARTZ = "minecraft:chiseled_quartz_block"
STAIRS = "minecraft:stone_brick_stairs"
END_ROD = "minecraft:end_rod"
SMOOTH_STONE = "minecraft:smooth_stone"
CHISELED_COPPER = "minecraft:chiseled_copper"
LIGHTNING_ROD = "minecraft:lightning_rod"

# The brief's orientation-bearing families, counted by the generator and the document test.
DIRECTIONAL = (SLAB, STAIRS, END_ROD, LANTERN, LIGHTNING_ROD)


def cell(x: int, y: int, z: int) -> tuple[int, int, int]:
    return (x, y, z)


def ring_cells(radius: int, y: int) -> list[tuple[int, int, int]]:
    """The Chebyshev ring of one radius in the station's square frame."""
    return [
        cell(x, y, z)
        for x in range(CENTER - radius, CENTER + radius + 1)
        for z in range(CENTER - radius, CENTER + radius + 1)
        if max(abs(x - CENTER), abs(z - CENTER)) == radius
    ]


def ring_boxes(radius: int, y: int) -> list[list[int]]:
    """The same ring as four axis-aligned bands, so no minus has to reach a later box."""
    outer = CENTER - radius
    inner = CENTER - radius + 1
    width = 2 * radius + 1
    return [
        [outer, y, outer, width, 1, 1],
        [outer, y, CENTER + radius, width, 1, 1],
        [outer, y, inner, 1, 1, width - 2],
        [CENTER + radius, y, inner, 1, 1, width - 2],
    ]


def boxes_cells(boxes: list[list[int]], minus: list[list[int]] = ()) -> set[tuple[int, int, int]]:
    cells = set()
    for x, y, z, sx, sy, sz in boxes:
        for cx in range(x, x + sx):
            for cy in range(y, y + sy):
                for cz in range(z, z + sz):
                    cells.add(cell(cx, cy, cz))
    for x, y, z, sx, sy, sz in minus:
        for cx in range(x, x + sx):
            for cy in range(y, y + sy):
                for cz in range(z, z + sz):
                    cells.discard(cell(cx, cy, cz))
    return cells


def turn_cell(point: tuple[int, int, int], turn: int) -> tuple[int, int, int]:
    """Rotate one cell about the station's vertical axis, north to east for turn one."""
    x, y, z = point
    dx, dz = x - CENTER, z - CENTER
    if turn == 0:
        rx, rz = dx, dz
    elif turn == 1:
        rx, rz = -dz, dx
    elif turn == 2:
        rx, rz = -dx, -dz
    else:
        rx, rz = dz, -dx
    return cell(CENTER + rx, y, CENTER + rz)


def turn_box(box: list[int], turn: int) -> list[int]:
    """Rotate an axis-aligned box about the station's vertical axis."""
    x, y, z, sx, sy, sz = box
    low = turn_cell((x, y, z), turn)
    high = turn_cell((x + sx - 1, y + sy - 1, z + sz - 1), turn)
    return [
        min(low[0], high[0]),
        min(low[1], high[1]),
        min(low[2], high[2]),
        abs(low[0] - high[0]) + 1,
        abs(low[1] - high[1]) + 1,
        abs(low[2] - high[2]) + 1,
    ]


def part(
    bay: str,
    boxes: list[list[int]],
    fill: str,
    specials: dict[tuple[int, int, int], str],
    description: str,
    hint: str,
    minus: list[list[int]] = (),
) -> dict:
    cells = boxes_cells(boxes, minus)
    if not cells:
        raise SystemExit(f"{bay}: a bay without cells is not a stage")
    for point in specials:
        if point not in cells:
            raise SystemExit(f"{bay}: special cell {point} is outside the bay")
    return {
        "id": bay,
        "boxes": boxes,
        "minus": list(minus),
        "fill": fill,
        "specials": dict(specials),
        "cells": cells,
        "description": description,
        "hint": hint,
    }


def turn_part(source: dict, turn: int, bay: str, description: str, hint: str) -> dict:
    boxes = [turn_box(box, turn) for box in source["boxes"]]
    minus = [turn_box(box, turn) for box in source["minus"]]
    specials = {turn_cell(point, turn): block for point, block in source["specials"].items()}
    return part(bay, boxes, source["fill"], specials, description, hint, minus)


def pod_shape(bottom: int, height: int) -> dict:
    """The canonical north pod: one hull with a door, a fin and a boom, the same at every turn."""
    top = bottom + height - 1
    hull = [5, bottom, 1, 3, height, 2]
    fin = [7, top - 1, 3, 1, 2, 1]
    boom = [6, bottom + 1, 0, 1, 2, 1]
    door = [6, bottom, 2, 1, 1, 1]
    specials = {
        cell(5, bottom + 1, 1): GLASS,
        cell(6, bottom + 1, 1): GLASS,
        cell(7, bottom + 1, 1): GLASS,
        cell(6, bottom + 2, 1): GLASS,
        cell(5, top, 1): END_ROD,
        cell(5, top, 2): SEA_LANTERN,
        cell(7, top - 1, 3): CHISELED_COPPER,
        cell(7, top, 3): CHISELED_COPPER,
        cell(6, bottom + 1, 0): LIGHTNING_ROD,
        cell(6, bottom + 2, 0): LIGHTNING_ROD,
    }
    return part("pod_north", [hull, fin, boom], SMOOTH_STONE, specials, "", "", [door])


def tier(
    bay: str,
    radius: int,
    y0: int,
    layers: int,
    fill: str,
    corner: str,
    light: str,
    window: str,
    cap: str,
    description: str,
    hint: str,
) -> dict:
    """One core tier: a hollow ring, lit at the foot, windowed up the faces and capped at the top."""
    width = 2 * radius + 1
    boxes = [[CENTER - radius, y, CENTER - radius, width, 1, width] for y in range(y0, y0 + layers)]
    minus = [[CENTER - radius + 1, y0, CENTER - radius + 1, width - 2, layers, width - 2]]
    near = CENTER - radius
    far = CENTER + radius
    top = y0 + layers - 1
    specials = {}
    corners = ((near, top, near), (far, top, near), (near, top, far), (far, top, far))
    faces = ((CENTER, top, near), (CENTER, top, far), (near, top, CENTER), (far, top, CENTER))
    if layers > 1:
        for point in ((near, y0, near), (far, y0, near), (near, y0, far), (far, y0, far)):
            specials[point] = light
    for y in range(y0, top):
        for point in ((CENTER, y, near), (CENTER, y, far), (near, y, CENTER), (far, y, CENTER)):
            specials[point] = window
    for point in corners:
        specials[point] = corner
    for point in faces:
        specials[point] = cap
    return part(bay, boxes, fill, specials, description, hint, minus)


def crown(y: int) -> dict:
    # the ring platform plus four spokes, both as bands so the union needs no minus
    boxes = ring_boxes(4, y) + [[CENTER - 3, y, CENTER, 7, 1, 1], [CENTER, y, CENTER - 3, 1, 1, 7]]
    outer = CENTER - 4
    far = CENTER + 4
    specials = {}
    for point in ((outer, y, outer), (far, y, outer), (outer, y, far), (far, y, far)):
        specials[point] = STAIRS
    for point in ((CENTER, y, outer), (CENTER, y, far), (outer, y, CENTER), (far, y, CENTER)):
        specials[point] = END_ROD
    for point in ((CENTER, y, CENTER - 2), (CENTER, y, CENTER + 2),
                  (CENTER - 2, y, CENTER), (CENTER + 2, y, CENTER)):
        specials[point] = LANTERN
    specials[cell(CENTER, y, CENTER)] = SEA_LANTERN
    return part(
        "crown",
        boxes,
        ANDESITE,
        specials,
        "the antenna crown",
        "close the ring, then stand the four masts",
    )


def plinth(full: bool) -> dict:
    boxes = ring_boxes(6, 0) + ring_boxes(5, 0)
    specials = {
        cell(0, 0, 0): CHISELED,
        cell(12, 0, 0): CHISELED,
        cell(0, 0, 12): CHISELED,
        cell(12, 0, 12): CHISELED,
        cell(6, 0, 0): ANDESITE,
        cell(6, 0, 12): ANDESITE,
        cell(0, 0, 6): ANDESITE,
        cell(12, 0, 6): ANDESITE,
    }
    for point in ring_cells(4, 1):
        specials[point] = SLAB
    boxes = boxes + ring_boxes(4, 1)
    if full:
        for point in ring_cells(3, 1):
            specials[point] = SLAB
        boxes = boxes + ring_boxes(3, 1)
    return part(
        "plinth",
        boxes,
        STONE,
        specials,
        "the stepped stone plinth, two rings wide with a slab landing",
        "lay the outer ring first, then the slab steps inside it"
        if full
        else "lay the outer ring first, then the slab landing inside it",
    )


def station(full: bool) -> tuple[dict, dict]:
    """Assemble one member and its expected-cell accounting."""
    parts = [plinth(full)]
    if full:
        parts.extend((
            tier(
                "core_low",
                3, 2, 4, DEEPSLATE, CHISELED, SEA_LANTERN, GLASS, CHISELED,
                "the deepslate lower shaft with its lantern corners",
                "raise the four lower walls, windows last",
            ),
            tier(
                "core_mid",
                2, 6, 3, COPPER, CUT_COPPER, SEA_LANTERN, GLASS, LANTERN,
                "the copper middle shaft",
                "stack the copper ring above the deepslate tier",
            ),
            tier(
                "core_high",
                1, 9, 1, QUARTZ, CHISELED_QUARTZ, SEA_LANTERN, GLASS, SEA_LANTERN,
                "the quartz upper shaft",
                "cap the shaft with its lantern crown",
            ),
        ))
    else:
        parts.extend((
            tier(
                "core_low",
                3, 2, 3, DEEPSLATE, CHISELED, SEA_LANTERN, GLASS, CHISELED,
                "the deepslate lower shaft with its lantern corners",
                "raise the four lower walls, windows last",
            ),
            tier(
                "core_mid",
                2, 5, 2, COPPER, CUT_COPPER, SEA_LANTERN, GLASS, LANTERN,
                "the copper middle shaft",
                "stack the copper ring above the deepslate tier",
            ),
        ))

    # arms: one bay, one batch, the same arm at every turn the member carries
    arm_turns = (0, 1, 2, 3) if full else (0, 2)
    arm_box = [5, 5, 1, 3, 1, 2] if full else [5, 3, 1, 3, 1, 2]
    parts.append(
        part(
            "arms",
            [turn_box(arm_box, turn) for turn in arm_turns],
            CUT_COPPER,
            {},
            "the cantilever arms that carry the pods",
            "run each arm outward in one batch, north and south first",
        )
    )

    # pods: one module, instantiated at the turns the member carries
    pod = pod_shape(6 if full else 4, 4 if full else 3)
    pod_turns = (0, 1, 2, 3) if full else (0, 2)
    names = ("pod_north", "pod_east", "pod_south", "pod_west")
    descriptions = (
        "the north satellite pod",
        "the east satellite pod",
        "the south satellite pod",
        "the west satellite pod",
    )
    hints = (
        "build the north pod on its arm, window row facing out",
        "build the east pod as the north pod turned once around the core",
        "build the south pod as the north pod turned twice around the core",
        "build the west pod as the north pod turned three times around the core",
    )
    for turn in pod_turns:
        parts.append(turn_part(pod, turn, names[turn], descriptions[turn], hints[turn]))

    parts.append(crown(10 if full else 7))
    description = (
        "a demonstration document: the relay station - a stepped plinth, a three-tier self-similar "
        "core, four cantilever arms, four satellite pods that are one module at four turns, and an "
        "antenna crown; ten bays, ten stages, 13x11x13"
        if full
        else "a demonstration document: the relay station in compact form - two core tiers and two "
        "satellite pods, so a variant switch changes the silhouette and not only the item list"
    )
    document = emit(
        FULL_ID if full else COMPACT_ID,
        description,
        FULL_BOUNDS if full else COMPACT_BOUNDS,
        parts,
    )
    return document, account(parts)


def checks_for(item: dict) -> list[dict]:
    """Every special cell first, the bay's fill last, so the fill cannot claim a detail."""
    counts: dict[str, int] = {}
    checks = []
    for point in sorted(item["specials"]):
        block = item["specials"][point]
        slug = block.split(":", 1)[1]
        counts[slug] = counts.get(slug, 0) + 1
        checks.append(
            {
                "id": f"{item['id']}_{slug}_{counts[slug]:02d}",
                "bay": item["id"],
                "focus": {"kind": "occupant", "cell": list(point)},
                "expect": {"kind": "block", "id": block},
            }
        )
    checks.append(
        {
            "id": f"{item['id']}_fill",
            "bay": item["id"],
            "focus": {"kind": "bay"},
            "expect": {"kind": "each_cell", "element": {"kind": "block", "id": item["fill"]}},
        }
    )
    return checks


def emit(document_id: str, description: str, bounds: list[int], parts: list[dict]) -> dict:
    bays = []
    checks = []
    for item in parts:
        bay = {"id": item["id"]}
        if len(item["boxes"]) == 1:
            bay["box"] = item["boxes"][0]
        else:
            bay["boxes"] = item["boxes"]
        if item["minus"]:
            bay["minus"] = item["minus"]
        bay["coverage"] = "every_cell"
        bay["footprint"] = "none"
        bay["sharing"] = "shared"
        bay["description"] = item["description"]
        bay["hint"] = item["hint"]
        bays.append(bay)
        checks.extend(checks_for(item))
    return {
        "schemaVersion": 1,
        "id": document_id,
        "description": description,
        "bounds": bounds,
        "variants": {
            "group": GROUP,
            "members": [
                {"id": FULL_ID, "label": "full"},
                {"id": COMPACT_ID, "label": "compact"},
            ],
        },
        "bays": bays,
        "checks": checks,
    }


def account(parts: list[dict]) -> dict:
    """The self-checks the design's test law repeats: cells, stages, materials, turn copies."""
    all_cells: dict[tuple[int, int, int], str] = {}
    for item in parts:
        for point in item["cells"]:
            if point in all_cells:
                raise SystemExit(f"{item['id']}: cell {point} is already in another bay")
            all_cells[point] = item["specials"].get(point, item["fill"])
    materials = set(all_cells.values())
    pods = [item for item in parts if item["id"].startswith("pod_")]
    canonical = None
    for item in pods:
        turn = POD_TURN[item["id"]]
        inverse = (4 - turn) % 4
        restored = {}
        for point in item["cells"]:
            restored[turn_cell(point, inverse)] = item["specials"].get(point, item["fill"])
        if canonical is None:
            canonical = restored
        elif canonical != restored:
            raise SystemExit(f"{item['id']}: the pod is not the north pod at a turn")
    directional = sum(1 for block in all_cells.values() if block in DIRECTIONAL)
    return {
        "cells": len(all_cells),
        "bays": len(parts),
        "stages": len(parts),
        "materials": len(materials),
        "directional": directional,
        "pods": len(pods),
        "turn_names": [TURNS[POD_TURN[item["id"]]] for item in pods],
    }


def bounds_of(parts: list[dict]) -> tuple[int, int, int, int, int, int]:
    cells = [point for item in parts for point in item["cells"]]
    return (
        min(point[0] for point in cells),
        min(point[1] for point in cells),
        min(point[2] for point in cells),
        max(point[0] for point in cells) + 1,
        max(point[1] for point in cells) + 1,
        max(point[2] for point in cells) + 1,
    )


def render(document: dict) -> str:
    """Serialise with scalar arrays on one line, so a box reads as a box."""
    return dump(document, 0) + "\n"


def dump(value, indent: int) -> str:
    pad = "  " * indent
    inner = "  " * (indent + 1)
    if isinstance(value, dict):
        if not value:
            return "{}"
        lines = [f"{inner}{json.dumps(name)}: {dump(item, indent + 1)}" for name, item in value.items()]
        return "{\n" + ",\n".join(lines) + "\n" + pad + "}"
    if isinstance(value, list):
        if not value:
            return "[]"
        if all(isinstance(item, (int, str, bool)) for item in value):
            return "[" + ", ".join(json.dumps(item) for item in value) + "]"
        rows = [inner + dump(item, indent + 1) for item in value]
        return "[\n" + ",\n".join(rows) + "\n" + pad + "]"
    return json.dumps(value, ensure_ascii=True)


def check(documents: list[tuple[str, dict]]) -> int:
    failures = 0
    for document_id, document in documents:
        path = OUT / (document_id.split(":", 1)[1] + ".json")
        expected = render(document).encode("utf-8")
        actual = path.read_bytes() if path.exists() else None
        if actual == expected:
            print(f"ok: {path.relative_to(ROOT)}")
            continue
        failures += 1
        if actual is None:
            print(f"drift: {path.relative_to(ROOT)} is missing; run tools/gen_demo_station.py")
            continue
        expected_lines = expected.decode("utf-8").splitlines()
        actual_lines = actual.decode("utf-8").splitlines()
        for number, (wanted, seen) in enumerate(zip(expected_lines, actual_lines), start=1):
            if wanted != seen:
                print(f"drift: {path.relative_to(ROOT)}:{number}: expected {wanted!r}, committed {seen!r}")
                break
        else:
            print(f"drift: {path.relative_to(ROOT)}: trailing bytes differ")
    return failures


def main(argv: list[str]) -> int:
    documents = []
    accounts = []
    for full in (True, False):
        document, numbers = station(full)
        label = "demo_station" if full else "demo_station_compact"
        bounds = FULL_BOUNDS if full else COMPACT_BOUNDS
        if list(bounds_of(station_parts(document))) != bounds:
            raise SystemExit(f"{label}: the emitted cells do not fill the declared bounds")
        if numbers["cells"] > CELL_CAP:
            raise SystemExit(f"{label}: {numbers['cells']} cells exceed the catalogue cap")
        if full and not FULL_CELL_BAND[0] <= numbers["cells"] <= FULL_CELL_BAND[1]:
            raise SystemExit(f"{label}: {numbers['cells']} cells leave the {FULL_CELL_BAND} band")
        if numbers["materials"] > MATERIAL_CAP:
            raise SystemExit(f"{label}: {numbers['materials']} materials exceed the {MATERIAL_CAP} cap")
        if numbers["directional"] < DIRECTIONAL_FLOOR:
            raise SystemExit(
                f"{label}: {numbers['directional']} orientation-bearing cells are below "
                f"the {DIRECTIONAL_FLOOR} floor"
            )
        ids = [check["id"] for check in document["checks"]]
        if len(ids) != len(set(ids)):
            raise SystemExit(f"{label}: duplicate check ids")
        documents.append((FULL_ID if full else COMPACT_ID, document))
        accounts.append((label, numbers))
    if "--check" in argv:
        return 1 if check(documents) else 0
    for document_id, document in documents:
        path = OUT / (document_id.split(":", 1)[1] + ".json")
        path.write_text(render(document), encoding="utf-8")
        print(f"wrote: {path.relative_to(ROOT)}")
    for label, numbers in accounts:
        print(
            f"{label}: {numbers['cells']} cells, {numbers['bays']} bays/stages, "
            f"{numbers['materials']} materials, {numbers['directional']} orientation-bearing cells, "
            f"{numbers['pods']} pods at {', '.join(numbers['turn_names'])}"
        )
    return 0


def station_parts(document: dict) -> list[dict]:
    """The emitted document's bays as cell sets, for the bounds self-check."""
    parts = []
    for bay in document["bays"]:
        boxes = bay["boxes"] if "boxes" in bay else [bay["box"]]
        parts.append({"cells": boxes_cells(boxes, bay.get("minus", []))})
    return parts


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
