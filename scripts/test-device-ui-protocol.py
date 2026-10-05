"""Verify the production device UI JSON parser without hardware or networking."""
from pathlib import Path
import subprocess

firmware = Path(__file__).resolve().parents[1] / "firmware"
subprocess.run([
    "docker", "run", "--rm", "--network", "none",
    "--mount", f"type=bind,source={firmware},target=/project,readonly",
    "--workdir", "/project", "gcc:14.2.0", "sh", "-c",
    "gcc -std=gnu11 -Wall -Wextra -Werror -fsanitize=address,undefined "
    "-fno-omit-frame-pointer -Imain -Imanaged_components/espressif__cjson/cJSON "
    "main/device_ui_protocol.c main/strict_json.c "
    "managed_components/espressif__cjson/cJSON/cJSON.c "
    "test/host/device_ui_protocol_test.c -lm -o /tmp/device-ui-test && /tmp/device-ui-test",
], check=True)
