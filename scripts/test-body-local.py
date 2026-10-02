"""Verify firmware-only head targets against the production servo speed budget."""
from pathlib import Path
import subprocess

firmware = Path(__file__).resolve().parents[1] / "firmware"
subprocess.run([
    "docker", "run", "--rm", "--network", "none",
    "--mount", f"type=bind,source={firmware},target=/project,readonly",
    "--workdir", "/project", "gcc:14.2.0", "sh", "-c",
    "gcc -std=c11 -Wall -Wextra -Werror -Imain main/body_local_policy.c main/motion_planner.c "
    "test/host/body_local_policy_test.c -lm -o /tmp/body-local && /tmp/body-local",
], check=True)
