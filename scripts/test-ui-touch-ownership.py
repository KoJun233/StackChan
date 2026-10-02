"""Exercise production modal touch ownership without LVGL, hardware or network."""
from pathlib import Path
import subprocess

firmware = Path(__file__).resolve().parents[1] / "firmware"
subprocess.run([
    "docker", "run", "--rm", "--network", "none",
    "--mount", f"type=bind,source={firmware},target=/project,readonly",
    "--workdir", "/project", "gcc:14.2.0", "sh", "-c",
    "gcc -std=c11 -Wall -Wextra -Werror -Imain test/host/ui_touch_ownership_test.c "
    "-o /tmp/ui-touch-test && /tmp/ui-touch-test",
], check=True)
