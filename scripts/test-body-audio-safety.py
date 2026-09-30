"""Execute production safety_state.c on the host; never access a robot or audio service.

Requires Docker and the official gcc:14.2.0 image, or --cc with a POSIX C compiler.
Only ESP logging and FreeRTOS critical sections are replaced by host equivalents.
"""

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
        "-std=c11", "-Wall", "-Wextra", "-Werror", "-pthread",
        "-Itest/host/stubs", "-Imain",
        "main/safety_state.c", "test/host/body_audio_safety_test.c",
    ]
    if args.cc:
        with tempfile.TemporaryDirectory(prefix="stackchan-audio-safety-") as directory:
            binary = str(Path(directory) / "body-audio-safety")
            subprocess.run([args.cc, *inputs, "-o", binary], cwd=firmware, check=True)
            subprocess.run([binary], check=True)
    else:
        # Shell text is fixed; paths are structured Docker arguments.
        command = "gcc " + " ".join(inputs) + " -o /tmp/body-audio-safety && /tmp/body-audio-safety"
        subprocess.run([
            "docker", "run", "--rm", "--network", "none",
            "--mount", f"type=bind,source={firmware},target=/project,readonly",
            "--workdir", "/project", "gcc:14.2.0", "sh", "-c", command,
        ], check=True)


if __name__ == "__main__":
    main()
