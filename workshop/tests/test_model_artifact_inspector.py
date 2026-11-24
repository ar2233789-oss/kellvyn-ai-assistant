from __future__ import annotations
import importlib.util
import json
from pathlib import Path
import struct
import tempfile
import unittest

MODULE = Path(__file__).parents[1] / "tools" / "model_artifact_inspector.py"
spec = importlib.util.spec_from_file_location("model_artifact_inspector", MODULE)
inspector = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(inspector)


class ModelArtifactInspectorTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def write_safetensors(self, name="model.safetensors", header=None, data=b"\0" * 16):
        if header is None:
            header = {"weight": {"dtype": "F32", "shape": [2, 2], "data_offsets": [0, 16]}}
        encoded = json.dumps(header, separators=(",", ":")).encode("utf-8")
        path = self.root / name
        path.write_bytes(struct.pack("<Q", len(encoded)) + encoded + data)
        return path

    @staticmethod
    def gguf_string(value):
        encoded = value.encode("utf-8")
        return struct.pack("<Q", len(encoded)) + encoded

    def write_gguf(self, name="model.gguf", version=3, alignment=32, offset=0, data=b"\0" * 12):
        parts = [
            b"GGUF", struct.pack("<IQQ", version, 1, 2),
            self.gguf_string("general.architecture"), struct.pack("<I", 8),
            self.gguf_string("llama"),
            self.gguf_string("general.alignment"), struct.pack("<II", 4, alignment),
            self.gguf_string("token_embd.weight"), struct.pack("<IQQI Q", 2, 2, 3, 1, offset),
        ]
        header = b"".join(parts)
        header += b"\0" * ((-len(header)) % alignment)
        path = self.root / name
        path.write_bytes(header + data)
        return path

    def test_safetensors_valid_header_and_counts(self):
        path = self.write_safetensors()
        result = inspector.inspect_safetensors(path)
        self.assertEqual("safetensors", result["format"])
        self.assertEqual(1, result["tensor_count"])
        self.assertEqual(4, result["parameter_count"])
        self.assertEqual({"F32": 1}, result["dtype_tensor_counts"])

    def test_safetensors_metadata_is_preserved(self):
        path = self.write_safetensors(header={
            "__metadata__": {"format": "pt", "source": "unit-test"},
            "weight": {"dtype": "F32", "shape": [2, 2], "data_offsets": [0, 16]},
        })
        result = inspector.inspect_safetensors(path)
        self.assertEqual("pt", result["metadata"]["format"])

    def test_safetensors_duplicate_json_keys_rejected(self):
        encoded = b'{"weight":{"dtype":"F32","dtype":"F16","shape":[2],"data_offsets":[0,4]}}'
        path = self.root / "duplicate.safetensors"
        path.write_bytes(struct.pack("<Q", len(encoded)) + encoded + b"\0" * 4)
        with self.assertRaises(inspector.ModelArtifactError):
            inspector.inspect_safetensors(path)

    def test_safetensors_wrong_tensor_byte_size_rejected(self):
        path = self.write_safetensors(header={
            "weight": {"dtype": "F32", "shape": [2, 2], "data_offsets": [0, 8]}
        }, data=b"\0" * 8)
        with self.assertRaises(inspector.ModelArtifactError):
            inspector.inspect_safetensors(path)

    def test_safetensors_overlapping_offsets_rejected(self):
        path = self.write_safetensors(header={
            "a": {"dtype": "F32", "shape": [2], "data_offsets": [0, 8]},
            "b": {"dtype": "F32", "shape": [2], "data_offsets": [4, 12]},
        }, data=b"\0" * 16)
        with self.assertRaises(inspector.ModelArtifactError):
            inspector.inspect_safetensors(path)

    def test_safetensors_data_holes_rejected(self):
        path = self.write_safetensors(header={
            "weight": {"dtype": "F32", "shape": [2], "data_offsets": [4, 12]}
        }, data=b"\0" * 16)
        with self.assertRaises(inspector.ModelArtifactError):
            inspector.inspect_safetensors(path)

    def test_safetensors_unindexed_trailing_bytes_rejected(self):
        path = self.write_safetensors(data=b"\0" * 20)
        with self.assertRaises(inspector.ModelArtifactError):
            inspector.inspect_safetensors(path)

    def test_safetensors_zero_rank_scalar_allowed(self):
        path = self.write_safetensors(header={
            "scalar": {"dtype": "F32", "shape": [], "data_offsets": [0, 4]}
        }, data=b"\0" * 4)
        result = inspector.inspect_safetensors(path)
        self.assertEqual(1, result["parameter_count"])

    def test_safetensors_empty_tensor_allowed(self):
        path = self.write_safetensors(header={
            "empty": {"dtype": "F32", "shape": [0, 4], "data_offsets": [0, 0]}
        }, data=b"")
        result = inspector.inspect_safetensors(path)
        self.assertEqual(0, result["parameter_count"])

    def test_safetensors_invalid_metadata_value_rejected(self):
        path = self.write_safetensors(header={
            "__metadata__": {"bad": 42},
            "weight": {"dtype": "F32", "shape": [2, 2], "data_offsets": [0, 16]},
        })
        with self.assertRaises(inspector.ModelArtifactError):
            inspector.inspect_safetensors(path)

    def test_gguf_valid_metadata_and_tensor_descriptors(self):
        path = self.write_gguf()
        result = inspector.inspect_gguf(path)
        self.assertEqual("gguf", result["format"])
        self.assertEqual("llama", result["architecture"])
        self.assertEqual(1, result["tensor_count"])
        self.assertEqual(6, result["parameter_count"])
        self.assertEqual(12, result["tensor_data_bytes"])

    def test_gguf_custom_alignment_is_used(self):
        path = self.write_gguf(alignment=64)
        result = inspector.inspect_gguf(path)
        self.assertEqual(12, result["tensor_data_bytes"])

    def test_gguf_invalid_magic_rejected(self):
        path = self.write_gguf()
        data = bytearray(path.read_bytes())
        data[:4] = b"NOPE"
        path.write_bytes(data)
        with self.assertRaises(inspector.ModelArtifactError):
            inspector.inspect_gguf(path)

    def test_gguf_unsupported_version_rejected(self):
        path = self.write_gguf(version=99)
        with self.assertRaises(inspector.ModelArtifactError):
            inspector.inspect_gguf(path)

    def test_gguf_offset_outside_tensor_buffer_rejected(self):
        path = self.write_gguf(offset=32)
        with self.assertRaises(inspector.ModelArtifactError):
            inspector.inspect_gguf(path)

    def test_gguf_duplicate_metadata_key_rejected(self):
        parts = [
            b"GGUF", struct.pack("<IQQ", 3, 0, 2),
            self.gguf_string("general.name"), struct.pack("<I", 8), self.gguf_string("one"),
            self.gguf_string("general.name"), struct.pack("<I", 8), self.gguf_string("two"),
        ]
        path = self.root / "duplicate.gguf"
        path.write_bytes(b"".join(parts))
        with self.assertRaises(inspector.ModelArtifactError):
            inspector.inspect_gguf(path)

    def test_unsupported_extension_rejected(self):
        path = self.root / "model.bin"
        path.write_bytes(b"model")
        with self.assertRaises(inspector.ModelArtifactError):
            inspector.inspect_model(path)

    def test_symlink_model_rejected(self):
        real = self.write_safetensors()
        link = self.root / "link.safetensors"
        try:
            link.symlink_to(real)
        except (OSError, NotImplementedError):
            self.skipTest("symlinks unavailable")
        with self.assertRaises(inspector.ModelArtifactError):
            inspector.inspect_safetensors(link)


if __name__ == "__main__":
    unittest.main()
