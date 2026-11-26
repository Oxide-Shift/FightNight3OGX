"""Validate interrupted ARM64 guest compiles and the previous release's size-only inventory format."""
import hashlib
import json
from pathlib import Path
import struct
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from compile_objects import object_inventory, unit_receipt, valid_guest_object


class GuestCache(unittest.TestCase):
    def test_reuse_and_repair(self):
        with tempfile.TemporaryDirectory() as root:
            out = Path(root)
            obj = out / "recomp_dispatch.o"
            blob = bytearray(192)
            blob[:7] = bytes.fromhex("7f454c46020101")
            struct.pack_into("<HHI", blob, 16, 1, 183, 1)
            struct.pack_into("<Q", blob, 40, 64)
            struct.pack_into("<6H", blob, 52, 64, 0, 0, 64, 2, 1)
            manifest = out / "inventory.json"
            manifest.write_text(json.dumps({obj.name: len(blob)}))
            obj.write_bytes(blob)
            self.assertIsNone(object_inventory(out))
            inventory = {obj.name: {"bytes":len(blob), "sha256":hashlib.sha256(blob).hexdigest()}}
            canonical = json.dumps(inventory, sort_keys=True, separators=(",", ":")).encode()
            manifest.write_text(json.dumps({"version":2, "objects":inventory,
                                            "inventory_sha256":hashlib.sha256(canonical).hexdigest()}))
            self.assertEqual(object_inventory(out), inventory)

            source = out / "recomp_dispatch.c"
            source.write_bytes(b"int guest;")
            original = unit_receipt(source,obj)
            changed = bytearray(blob)
            changed[-1] ^= 1
            obj.write_bytes(changed)
            self.assertTrue(valid_guest_object(obj))
            self.assertNotEqual(unit_receipt(source,obj),original)
            self.assertIsNone(object_inventory(out))
            obj.write_bytes(blob)
            source.write_bytes(b"int other;")
            self.assertNotEqual(unit_receipt(source,obj),original)

            for offset, data in [(0, b"BAD!"), (18, bytes([62, 0])), (40, bytes([200]))]:
                with self.subTest(offset=offset):
                    damaged = bytearray(blob)
                    damaged[offset:offset+len(data)] = data
                    obj.write_bytes(damaged)
                    self.assertIsNone(object_inventory(out))
                    self.assertFalse(valid_guest_object(obj))
            obj.write_bytes(blob[:100])
            self.assertFalse(valid_guest_object(obj))
            obj.write_bytes(blob)
            self.assertEqual(object_inventory(out), inventory)
            self.assertFalse(valid_guest_object(obj, len(blob)+1))


if __name__ == "__main__":
    unittest.main()
