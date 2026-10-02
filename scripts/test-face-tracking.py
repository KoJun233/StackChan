"""Exercise local tracking policy without acquiring images or enabling robot hardware."""
import argparse
from pathlib import Path
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cc", help="Local C compiler (otherwise Docker gcc:14.2.0)")
    parser.add_argument("--cxx", help="Local C++ compiler with --cc (default g++)")
    args = parser.parse_args()
    firmware = Path(__file__).resolve().parents[1] / "firmware"
    inputs = ["-std=c11", "-Wall", "-Wextra", "-Werror", "-Imain",
              "main/face_tracking_policy.c", "test/host/face_tracking_test.c", "-lm"]
    if args.cc:
        with tempfile.TemporaryDirectory(prefix="stackchan-face-tracking-") as directory:
            binary = str(Path(directory) / "face-tracking")
            subprocess.run([args.cc, *inputs, "-o", binary], cwd=firmware, check=True)
            subprocess.run([binary], check=True)
            policy = str(Path(directory) / "policy.o")
            runtime = str(Path(directory) / "runtime")
            subprocess.run([args.cc, "-std=c11", "-Imain", "-c", "main/face_tracking_policy.c", "-o", policy], cwd=firmware, check=True)
            subprocess.run([args.cxx or "g++", "-std=c++17", "-Wall", "-Wextra", "-Werror",
                            "-Imain", "-Itest/host", "-Itest/host/stubs",
                            "test/host/face_tracking_runtime_test.cpp", policy, "-o", runtime], cwd=firmware, check=True)
            subprocess.run([runtime], check=True)
    else:
        command = (
            "gcc " + " ".join(inputs) + " -o /tmp/face-tracking && /tmp/face-tracking && "
            "gcc -std=c11 -Imain -c main/face_tracking_policy.c -o /tmp/policy.o && "
            "g++ -std=c++17 -Wall -Wextra -Werror -Imain -Itest/host -Itest/host/stubs "
            "test/host/face_tracking_runtime_test.cpp /tmp/policy.o -o /tmp/runtime && /tmp/runtime"
        )
        subprocess.run([
            "docker", "run", "--rm", "--network", "none",
            "--mount", f"type=bind,source={firmware},target=/project,readonly",
            "--workdir", "/project", "gcc:14.2.0", "sh", "-c", command,
        ], check=True)


if __name__ == "__main__":
    main()
