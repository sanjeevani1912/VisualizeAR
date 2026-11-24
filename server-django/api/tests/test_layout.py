"""Layout checking. The big test replays vectors produced by running the real server/src/layout.ts."""

import json
from pathlib import Path
from unittest import mock

from django.test import RequestFactory, SimpleTestCase

from api import layout
from api.http import MISSING, ApiError, json_body
from api.jsutil import db_text, js_len, js_mod, js_number_text, js_slice, js_stringify, js_trim

FIXTURES = Path(__file__).parent / "fixtures"


def _refuse(name):
    raise ValueError(name)


def parse_text(text):
    """Parses request text the way the API does (Python's NaN/Infinity extensions are refused)."""
    return json.loads(text, parse_constant=_refuse)


class JavaScriptNumberTests(SimpleTestCase):
    def test_numbers_print_like_javascript(self):
        # expected values were printed by Node: String(x)
        cases = {
            10.0: "10", 0.1 + 0.2: "0.30000000000000004", 1e-7: "1e-7", 1.5e-7: "1.5e-7", 0.000001: "0.000001",
            1e21: "1e+21", 1e20: "100000000000000000000", 123456789012345680000.0: "123456789012345680000",
            1.7976931348623157e308: "1.7976931348623157e+308", 5e-324: "5e-324", -0.0: "0", 0.0: "0", -1234.5: "-1234.5",
            4.199999809265137: "4.199999809265137", 100000.0: "100000", 1e16: "10000000000000000", 12345.6789: "12345.6789",
            -1e-7: "-1e-7", 0.5: "0.5", 123.456e-10: "1.23456e-8", 2.0**53: "9007199254740992",
        }
        for number, text in cases.items():
            self.assertEqual(js_number_text(number), text, number)

    def test_integers_print_like_javascript(self):
        self.assertEqual(js_stringify([1, 2**60, -5]), "[1,1152921504606847000,-5]")


class JavaScriptStringTests(SimpleTestCase):
    def test_json_text_escapes_like_javascript(self):
        # Node: JSON.stringify('é\u0001\u007f "\\\n\ud800')
        self.assertEqual(js_stringify("é\x01\x7f \"\\\n\ud800"), '"é\\u0001\x7f \\"\\\\\\n\\ud800"')
        self.assertEqual(js_stringify({"a": [True, False, None, "x"]}), '{"a":[true,false,null,"x"]}')

    def test_length_trim_and_slice_use_javascript_rules(self):
        self.assertEqual(js_len("a🚀"), 3)  # an emoji is two UTF-16 units
        self.assertEqual(js_trim("﻿  x  "), "x")
        self.assertEqual(js_trim("\x1cx\x1c"), "\x1cx\x1c")  # Python's strip() would remove these
        self.assertEqual(js_slice("ab🚀cd", 3), "ab\ud83d")  # cuts the emoji in half, like JavaScript
        self.assertEqual(js_mod(-90, 360), -90)  # Python's -90 % 360 would be 270
        self.assertEqual(db_text("a\ud800b"), "a�b")


class NodeVectorTests(SimpleTestCase):
    """Every vector is an input and the answer the Node server gave: either the exact error text, or the
    normalised layout (as JSON.stringify text) plus its content hash."""

    def vectors(self):
        return json.loads((FIXTURES / "layout_vectors.json").read_text(encoding="utf-8"))

    def test_every_vector_matches_node(self):
        vectors = self.vectors()
        self.assertGreater(len(vectors), 500)
        for vector in vectors:
            counter = iter(range(1, 100000))
            with mock.patch.object(layout, "new_uuid", lambda: f"gen-{next(counter):04d}"):
                try:
                    data = parse_text(vector["input_text"])
                except ValueError:
                    self.assertTrue(vector["result"].get("malformed"), vector["label"])
                    continue
                result, error = layout.parse_layout(data)
            expected = vector["result"]
            with self.subTest(vector["label"]):
                if "error" in expected:
                    self.assertEqual(error, expected["error"])
                else:
                    self.assertIsNone(error)
                    self.assertEqual(layout.layout_text(result), expected["layout_json"])
                    self.assertEqual(layout.content_hash(result), expected["hash"])

    def test_the_apps_own_export_gets_the_node_hash(self):
        fixture = next(v for v in self.vectors() if v["label"] == "fixture app-layout-v2")
        result, error = layout.parse_layout(parse_text(fixture["input_text"]))
        self.assertIsNone(error)
        self.assertEqual(layout.content_hash(result), fixture["result"]["hash"])


class LayoutBehaviourTests(SimpleTestCase):
    def good(self, **change):
        base = {
            "regionBounds": {"northLatitude": 28.62, "southLatitude": 28.6, "eastLongitude": 77.22, "westLongitude": 77.2},
            "anchor": {"latitude": 28.61, "longitude": 77.21},
            "objects": [],
        }
        base.update(change)
        return base

    def test_defaults_names_and_generated_ids(self):
        objects = [{"assetType": "K131_TAKEOFF", "x": 1, "z": 2}, {"assetType": "CAR", "x": 0, "z": 0, "name": "  "}]
        result, error = layout.parse_layout(self.good(objects=objects))
        self.assertIsNone(error)
        first, second = result["objects"]
        self.assertEqual((first["widthMeters"], first["lengthMeters"], first["heightMeters"]), (3.31, 6.24, 3.54))
        self.assertEqual((first["name"], second["name"]), ("K131_takeoff 1", "Car 2"))
        self.assertNotEqual(first["id"], second["id"])
        self.assertEqual(result["name"], "Untitled plan")
        self.assertEqual(result["anchor"]["headingDegrees"], 0)
        self.assertIsNone(result["anchor"]["horizontalAccuracyMeters"])

    def test_all_eleven_asset_types_are_known(self):
        self.assertEqual(len(layout.ASSET_DEFAULTS), 11)

    def test_duplicate_ids_get_fresh_ones(self):
        objects = [{"id": "same", "assetType": "CAR", "x": 0, "z": 0}] * 3
        result, _ = layout.parse_layout(self.good(objects=objects))
        ids = [o["id"] for o in result["objects"]]
        self.assertEqual(ids[0], "same")
        self.assertEqual(len(set(ids)), 3)

    def test_rotation_is_normalised_to_0_360(self):
        objects = [{"assetType": "CAR", "x": 0, "z": 0, "rotationDegrees": r} for r in (-90, 725.5, 360, -360, 3600)]
        result, _ = layout.parse_layout(self.good(objects=objects))
        self.assertEqual([o["rotationDegrees"] for o in result["objects"]], [270, 5.5, 0, 0, 0])

    def test_the_object_limit_is_500(self):
        many = lambda n: [{"assetType": "CAR", "x": 0, "z": 0}] * n
        self.assertIsNone(layout.parse_layout(self.good(objects=many(500)))[1])
        self.assertEqual(layout.parse_layout(self.good(objects=many(501)))[1], "Invalid layout file: objects — Too big: expected array to have <=500 items")

    def test_missing_layout_is_reported_as_layout(self):
        self.assertEqual(layout.parse_layout(MISSING)[1], "Invalid layout file: layout — Invalid input: expected object, received undefined")


class JsonBodyTests(SimpleTestCase):
    def test_nan_and_infinity_are_malformed_json(self):
        for text in ('{"a": NaN}', '{"a": Infinity}', '{"a": -Infinity}'):
            request = RequestFactory().post("/x", data=text, content_type="application/json")
            with self.assertRaises(ApiError) as caught:
                json_body(request)
            self.assertEqual((caught.exception.status, caught.exception.message), (400, "Malformed JSON"))
