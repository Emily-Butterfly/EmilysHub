"""Tests of garmin-export-struktur.py (#136): the structure shows, the values never.

Run: python3 -I tools/test_garmin_export_struktur.py
"""

import importlib.util
import io
import json
import pathlib
import struct
import unittest
import zipfile

_PATH = pathlib.Path(__file__).with_name("garmin-export-struktur.py")
_SPEC = importlib.util.spec_from_file_location("garmin_export_struktur", _PATH)
script = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(script)


def fit_file(messages):
    """A FIT file of [messages], each (global number, [(field number, byte)]), one byte per field, no CRC checked."""
    body = b""
    for local, (number, fields) in enumerate(messages):
        local &= 0x0F
        body += bytes([0x40 | local, 0, 0]) + struct.pack("<H", number) + bytes([len(fields)])
        for field, _ in fields:
            body += bytes([field, 1, 2])
        body += bytes([local]) + bytes(value for _, value in fields)
    header = struct.pack("<BBHI4s", 12, 0x20, 2100, len(body), b".FIT")
    return header + body + b"\x00\x00"


def zipped(files):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        for name, data in files.items():
            archive.writestr(name, data)
    return buffer.getvalue()


class StructureTest(unittest.TestCase):
    def run_on(self, files, tmp):
        path = tmp / "export.zip"
        path.write_bytes(zipped(files))
        out = io.StringIO()
        self.assertEqual(0, script.main(["x", str(path)], out))
        return out.getvalue()

    def test_names_and_kinds_show_values_do_not(self):
        health = json.dumps([
            {"calendarDate": "2026-03-14", "metrics": [{"type": "HRV", "value": 87.25}, {"type": "SPO2", "value": 93.5}]},
        ])
        uds = json.dumps({"restingHeartRate": 6161, "note": "Ein ganz eigener Text", "allDayStress": {"aggregatorList": [{"type": "TOTAL", "averageStressLevel": 4242}]}})
        fit = fit_file([(0, [(0, 32)]), (370, [(0, 201), (1, 203)]), (371, [(0, 251)])])
        inner = zipped({"someone@example.org_123456789.fit": fit})
        files = {
            "1a2b3c4d-1111-2222-3333-444455556666_1/DI_CONNECT/DI-Connect-Wellness/2026-01-01_2026-04-01_98765_healthStatusData.json": health,
            "x_1/DI_CONNECT/DI-Connect-Aggregator/UDSFile_2026-01-01_2026-04-01.json": uds,
            "x_1/DI_CONNECT/DI-Connect-Uploaded-Files/UploadedFiles_0-_Part1.zip": inner,
        }
        import tempfile

        with tempfile.TemporaryDirectory() as directory:
            output = self.run_on(files, pathlib.Path(directory))
        for shown in ["calendarDate: Text", "metrics: Liste", "type: Text [HRV, SPO2]", "value: Zahl", "restingHeartRate: Zahl",
                      "averageStressLevel: Zahl", "[TOTAL]", "file_id.type: 32 (monitoring_b)", "hrv_status_summary (370): 1× Felder 0, 1",
                      "hrv_value (371): 1× Felder 0", "<uuid>", "<datum>", "<email>_<id>.fit"]:
            self.assertIn(shown, output)
        for hidden in ["87.25", "93.5", "6161", "4242", "2026-03-14", "Ein ganz eigener Text", "someone", "example.org", "98765", "123456789", "201", "203", "251"]:
            self.assertNotIn(hidden, output)

    def test_no_fit_and_broken_json(self):
        import tempfile

        with tempfile.TemporaryDirectory() as directory:
            output = self.run_on({"a.fit": b"nothing", "b.json": b"{kaputt"}, pathlib.Path(directory))
        self.assertIn("(kein FIT)", output)
        self.assertIn("(nicht lesbar)", output)

    def test_a_file_cut_short_is_marked_and_the_rest_listed(self):
        import tempfile

        fit = fit_file([(0, [(0, 32)]), (370, [(0, 201), (1, 203)])])
        cut = fit[:-6]
        with tempfile.TemporaryDirectory() as directory:
            output = self.run_on({"a.fit": cut, "b.json": "{}"}, pathlib.Path(directory))
        self.assertIn("(bricht vorzeitig ab)", output)
        self.assertIn("file_id (0): 1×", output)
        self.assertIn("JSON b.json", output)

    def test_not_a_zip(self):
        import tempfile

        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / "x.zip"
            path.write_bytes(b"no zip")
            self.assertEqual(1, script.main(["x", str(path)], io.StringIO()))


if __name__ == "__main__":
    unittest.main()
