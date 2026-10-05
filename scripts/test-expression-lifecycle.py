"""Run production expression lifecycle math without robot, display or network."""

import argparse
from pathlib import Path
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cc", help="Local POSIX C compiler (otherwise Docker gcc:14.2.0)")
    args = parser.parse_args()
    firmware = Path(__file__).resolve().parents[1] / "firmware"
    inputs = [
        "-std=c11", "-Wall", "-Wextra", "-Werror", "-Imain",
        "main/expression_engine.c", "test/host/expression_lifecycle_test.c", "-lm",
    ]
    if args.cc:
        with tempfile.TemporaryDirectory(prefix="stackchan-expression-") as directory:
            binary = str(Path(directory) / "expression-lifecycle")
            subprocess.run([args.cc, *inputs, "-o", binary], cwd=firmware, check=True)
            subprocess.run([binary], check=True)
    else:
        subprocess.run([
            "docker", "run", "--rm", "--network", "none",
            "--mount", f"type=bind,source={firmware},target=/project,readonly",
            "--workdir", "/project", "gcc:14.2.0", "sh", "-c",
            "gcc " + " ".join(inputs) + " -o /tmp/expression-lifecycle && /tmp/expression-lifecycle",
        ], check=True)


if __name__ == "__main__":
    main()
