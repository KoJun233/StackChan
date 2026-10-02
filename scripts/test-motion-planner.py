"""Run the production time-based motion planner without hardware or networking."""
from pathlib import Path
import subprocess

firmware = Path(__file__).resolve().parents[1] / "firmware"
subprocess.run([
    "docker", "run", "--rm", "--network", "none",
    "--mount", f"type=bind,source={firmware},target=/project,readonly",
    "--workdir", "/project", "gcc:14.2.0", "sh", "-c",
    "gcc -std=c11 -Wall -Wextra -Werror -Imain main/motion_planner.c "
    "test/host/motion_planner_test.c -lm -o /tmp/motion-test && /tmp/motion-test",
], check=True)
