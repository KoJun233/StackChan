"""Validate the pinned graph reader and fail-closed compile requirement pruning."""
import importlib.util
from pathlib import Path
import tempfile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("requirements", ROOT / "scripts/generate-face-compile-requirements.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
models = ROOT / "firmware/managed_components/espressif__human_face_detect/models/s3"
paths = [models / "human_face_detect_msr_s8_v1.espdl", models / "human_face_detect_mnp_s8_v1.espdl"]
kernels = ROOT / "firmware/managed_components/espressif__esp-dl/spec/kernels.yml"
expected = [({"Conv", "Concat"}, 41, {"S8"}), ({"Conv", "PRelu"}, 23, {"S8"})]
for path, graph in zip(paths, expected):
    assert module.GraphReader(path).operators() == graph
with tempfile.TemporaryDirectory(prefix="stackchan-face-req-") as directory:
    output = Path(directory) / "requirements.yml"
    module.generate(paths, kernels, output)
    contents = output.read_text(encoding="utf-8")
    assert "  - LUT\n" in contents and "  - RequantizeLinear\n" in contents
    retained = [line for line in contents.splitlines() if line.startswith("  - dl_")]
    assert len(retained) == 36 and all("_s8_" in line for line in retained)
    original = paths[0].read_bytes()
    mutations = [b"BAD!" + original[4:], original[:31],
                 original[:16] + b"\xff" * 4 + original[20:], original.replace(b"S8", b"XX")]
    for index, data in enumerate(mutations):
        malformed = Path(directory) / f"malformed-{index}.espdl"
        malformed.write_bytes(data)
        try:
            module.GraphReader(malformed).operators()
        except (ValueError, UnicodeDecodeError):
            pass
        else:
            raise AssertionError(f"malformed graph {index} was accepted")
print("PASS pinned MSR/MNP graphs, S8 kernels/helper closure and four malformed containers")
