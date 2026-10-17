"""Inspect a built APK/AAB without extracting personal data or changing the archive.

Usage: python tools/verify_play_artifact.py app.aab --require-jar-signature
Manifest and APK signing verification are separate Android SDK/bundletool checks.
"""
import argparse
import hashlib
import json
import struct
import zipfile
from pathlib import Path


def elf_load_alignments(data):
    if data[:4] != b"\x7fELF" or data[4] != 2 or data[5] != 1:
        raise ValueError("Expected a little-endian ELF64 library")
    offset = struct.unpack_from("<Q", data, 32)[0]
    size, count = struct.unpack_from("<HH", data, 54)
    return [struct.unpack_from("<Q", data, offset + i * size + 48)[0]
            for i in range(count)
            if struct.unpack_from("<I", data, offset + i * size)[0] == 1]


def inspect(path, require_signature=False):
    with zipfile.ZipFile(path) as archive:
        if archive.testzip():
            raise ValueError("Corrupt ZIP entry")
        names = archive.namelist()
        native = {}
        for name in names:
            if name.endswith(".so") and any(abi in name for abi in ("arm64-v8a/", "x86_64/")):
                aligns = elf_load_alignments(archive.read(name))
                if not aligns or any(value < 16384 for value in aligns):
                    raise ValueError(f"Not 16 KB aligned: {name}: {aligns}")
                native[name] = aligns
        if not any("arm64-v8a/" in name for name in native):
            raise ValueError("Missing arm64-v8a native libraries")
        for filename in ("NOTICE.txt", "CC-BY-NC-SA-4.0.txt", "onnxruntime-MIT.txt",
                         "onnxruntime-ThirdPartyNotices.txt", "Manrope-OFL-1.1.txt"):
            if not any(name.endswith("assets/licenses/" + filename) for name in names):
                raise ValueError(f"Missing bundled notice: {filename}")
        models = [name for name in names if name.endswith(".onnx")]
        if len(models) < 4:
            raise ValueError("Expected preprocessing and wake-word models")
        jar_signature = any(name.upper().startswith("META-INF/") and
                            name.upper().endswith((".RSA", ".EC", ".DSA")) for name in names)
        if require_signature and not jar_signature:
            raise ValueError("AAB is missing its JAR signing block")
        return {"file": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                "bytes": path.stat().st_size, "elf64_load_alignments": native,
                "models": models, "jar_signature_present": jar_signature,
                "limits": "Does not verify certificate trust, manifest, ZIP alignment or device inference."}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("--require-jar-signature", action="store_true")
    args = parser.parse_args()
    print(json.dumps(inspect(args.archive, args.require_jar_signature), indent=2))
