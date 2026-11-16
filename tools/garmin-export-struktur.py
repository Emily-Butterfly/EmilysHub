#!/usr/bin/env python3
"""Shows the structure of a Garmin export, never its values (#136, SPEC.md §17 "Garmin-Export").

Usage: python3 -I tools/garmin-export-struktur.py <export.zip>

Takes the whole export from Garmin Connect ("Export Your Data") or the export of one day, and the ZIPs nested in them.
For each JSON file it prints the path, the names of the fields as a tree with their type and, for fields that name a
kind (such as "type"), the kinds that occur; for each FIT file its file type and its messages with how many there are and
the numbers of their fields. Numbers, times and free text never appear; dates, ids and e-mail addresses in paths are
replaced. Reads only; writes nothing but to standard output. No library beyond Python's own.
"""

import io
import json
import re
import struct
import sys
import zipfile

# Fields whose text names a kind, not something personal: their values are shown.
KIND_FIELDS = {"type", "metricType", "metricsType", "aggregatorType"}

# The names of the FIT messages Tracking cares about, by global number (FIT SDK, Profile.xlsx); others show as a number.
MESSAGES = {
    0: "file_id",
    49: "file_creator",
    23: "device_info",
    55: "monitoring",
    78: "hrv",
    103: "monitoring_info",
    211: "monitoring_hr_data",
    227: "stress_level",
    269: "spo2_data",
    275: "sleep_level",
    297: "respiration_rate",
    346: "sleep_assessment",
    370: "hrv_status_summary",
    371: "hrv_value",
}

FILE_TYPES = {4: "activity", 15: "monitoring_a", 28: "monitoring_daily", 32: "monitoring_b"}

MAX_NESTING = 3


def clean_path(path):
    """[path] with what could name a person or a day replaced."""
    path = re.sub(r"[\w.+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}", "<email>", path)
    path = re.sub(r"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "<uuid>", path)
    path = re.sub(r"\d{4}-\d{2}-\d{2}", "<datum>", path)
    path = re.sub(r"\d{4,}", "<id>", path)
    return path


def kind_of(value):
    if isinstance(value, bool):
        return "Wahrheitswert"
    if isinstance(value, (int, float)):
        return "Zahl"
    if isinstance(value, str):
        return "Text"
    if isinstance(value, list):
        return "Liste"
    if isinstance(value, dict):
        return "Objekt"
    return "leer"


def merge(shape, value):
    """[shape] grown by [value]: per key its kinds, children and, for kind fields, the kinds named."""
    if isinstance(value, dict):
        fields = shape.setdefault("fields", {})
        for key, child in value.items():
            entry = fields.setdefault(key, {"kinds": set()})
            entry["kinds"].add(kind_of(child))
            if key in KIND_FIELDS and isinstance(child, str) and re.fullmatch(r"[A-Za-z][A-Za-z0-9_]{0,39}", child):
                entry.setdefault("names", set()).add(child)
            merge(entry, child)
    elif isinstance(value, list):
        items = shape.setdefault("items", {"kinds": set()})
        for child in value:
            items["kinds"].add(kind_of(child))
            merge(items, child)
    return shape


def print_shape(shape, indent, out):
    for key in sorted(shape.get("fields", {})):
        entry = shape["fields"][key]
        names = " [" + ", ".join(sorted(entry["names"])) + "]" if entry.get("names") else ""
        print(f"{indent}{key}: {' | '.join(sorted(entry['kinds']))}{names}", file=out)
        print_shape(entry, indent + "  ", out)
    if "items" in shape:
        print(f"{indent}[]: {' | '.join(sorted(shape['items']['kinds'])) or 'leer'}", file=out)
        print_shape(shape["items"], indent + "  ", out)


def show_json(path, data, out):
    print(f"\nJSON {clean_path(path)}", file=out)
    try:
        value = json.loads(data.decode("utf-8-sig"))
    except (ValueError, UnicodeDecodeError):
        print("  (nicht lesbar)", file=out)
        return
    print(f"  (oben): {kind_of(value)}", file=out)
    print_shape(merge({}, value), "  ", out)


def fit_structure(data):
    """The file type, per message its count and field numbers, and whether the file broke off early; None if [data] is no FIT file."""
    if len(data) < 12:
        return None
    header_size = data[0]
    if header_size not in (12, 14) or data[8:12] != b".FIT":
        return None
    data_size = struct.unpack_from("<I", data, 4)[0]
    end = min(header_size + data_size, len(data))
    pos = header_size
    definitions = {}
    messages = {}
    try:
        broken = _read_records(data, pos, end, definitions, messages)
    except (IndexError, struct.error):
        broken = True
    file_id = messages.get(0, {}).get("type")
    return file_id, messages, broken


def _read_records(data, pos, end, definitions, messages):
    """Reads the records from [pos] to [end] into [messages]; True if one runs past the end."""
    while pos < end:
        record = data[pos]
        pos += 1
        if record & 0x80:  # compressed time stamp: a data message of local type 0–3
            local = (record >> 5) & 0x03
            is_definition = False
        else:
            local = record & 0x0F
            is_definition = bool(record & 0x40)
        if is_definition:
            has_developer = bool(record & 0x20)
            if pos + 5 > end:
                return True
            little = data[pos + 1] == 0
            number = struct.unpack_from("<H" if little else ">H", data, pos + 2)[0]
            count = data[pos + 4]
            pos += 5
            if pos + 3 * count > end:
                return True
            fields = []
            for _ in range(count):
                fields.append((data[pos], data[pos + 1], data[pos + 2]))
                pos += 3
            developer_size = 0
            if has_developer:
                if pos + 1 > end:
                    return True
                developer_count = data[pos]
                pos += 1
                for _ in range(developer_count):
                    developer_size += data[pos + 1]
                    pos += 3
            size = sum(f[1] for f in fields) + developer_size
            definitions[local] = (number, fields, size, little)
        else:
            if local not in definitions:
                return True
            number, fields, size, little = definitions[local]
            if pos + size > end:
                return True
            entry = messages.setdefault(number, {"count": 0, "fields": set()})
            entry["count"] += 1
            entry["fields"].update(f[0] for f in fields)
            if number == 0 and "type" not in entry:
                offset = pos
                for field, length, _ in fields:
                    if field == 0 and length >= 1:
                        entry["type"] = data[offset]
                    offset += length
            pos += size
    return False


def show_fit(path, data, out):
    structure = fit_structure(data)
    print(f"\nFIT {clean_path(path)}", file=out)
    if structure is None:
        print("  (kein FIT)", file=out)
        return
    file_type, messages, broken = structure
    name = FILE_TYPES.get(file_type, "?")
    print(f"  file_id.type: {file_type} ({name})", file=out)
    if broken:
        print("  (bricht vorzeitig ab)", file=out)
    for number in sorted(messages):
        entry = messages[number]
        fields = ", ".join(str(f) for f in sorted(entry["fields"]))
        print(f"  {MESSAGES.get(number, '?')} ({number}): {entry['count']}× Felder {fields}", file=out)


def walk(archive, prefix, depth, out):
    for info in archive.infolist():
        if info.is_dir():
            continue
        path = prefix + info.filename
        data = archive.read(info)
        lower = info.filename.lower()
        if depth < MAX_NESTING and data[:2] == b"PK" and zipfile.is_zipfile(io.BytesIO(data)):
            print(f"\nZIP {clean_path(path)}", file=out)
            with zipfile.ZipFile(io.BytesIO(data)) as nested:
                walk(nested, path + "!/", depth + 1, out)
        elif lower.endswith(".json"):
            show_json(path, data, out)
        elif lower.endswith(".fit") or data[8:12] == b".FIT":
            show_fit(path, data, out)
        else:
            print(f"\nANDERE {clean_path(path)}", file=out)


def main(argv, out=sys.stdout):
    if len(argv) != 2:
        print("Aufruf: python3 -I tools/garmin-export-struktur.py <export.zip>", file=sys.stderr)
        return 2
    if not zipfile.is_zipfile(argv[1]):
        print("Keine ZIP-Datei.", file=sys.stderr)
        return 1
    with zipfile.ZipFile(argv[1]) as archive:
        walk(archive, "", 0, out)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
