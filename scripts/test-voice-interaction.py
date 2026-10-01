"""Run the production touch, capture and follow-up policies without a robot or cloud service."""

import argparse
from pathlib import Path
import subprocess
import tempfile
import re


def check_wake_model_lifecycle(firmware):
    source = (firmware / "main/voice_control.c").read_text(encoding="utf-8")
    if re.search(r"(?:->|\.)\s*clean\s*\(", source):
        raise RuntimeError("WakeNet clean must not return: real-device model_clean null dereference")
    for trigger in ("TOUCH_STARTED, true", "WAKE_DETECTED, false"):
        pattern = r"execute_voice_conversation\(DEVICE_VOICE_STAGE_" + re.escape(trigger) + r"\);\s*destroy_active_wakenet\(&active\);"
        if not re.search(pattern, source):
            raise RuntimeError("Both voice paths must release WakeNet after the turn")
    if not re.search(r"if \(!automatic_wake_enabled\(\)\) \{\s*/\*.*?\*/\s*destroy_active_wakenet\(&active\);", source, re.S):
        raise RuntimeError("Manual idle must release the model without calling clean")
    print("PASS WakeNet lifecycle guards: no unsafe clean; release on PTT idle and after either turn", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cc", help="Local POSIX C compiler (otherwise Docker gcc:14.2.0)")
    args = parser.parse_args()
    firmware = Path(__file__).resolve().parents[1] / "firmware"
    check_wake_model_lifecycle(firmware)
    inputs = [
        "-std=gnu11", "-Wall", "-Wextra", "-Werror", "-Imain", "-Itest/host/stubs",
        "-Imanaged_components/espressif__cjson/cJSON",
        "main/touch_interaction.c", "main/voice_capture_policy.c",
        "main/continuous_conversation.c", "test/host/voice_interaction_test.c",
        "main/body_touch_policy.c", "main/expression_engine.c", "main/interaction_state.c", "-lm",
        "main/voice_protocol.c", "main/audio_wav.c", "main/strict_json.c",
        "managed_components/espressif__cjson/cJSON/cJSON.c",
    ]
    if args.cc:
        with tempfile.TemporaryDirectory(prefix="stackchan-voice-interaction-") as directory:
            binary = str(Path(directory) / "voice-interaction")
            subprocess.run([args.cc, *inputs, "-o", binary], cwd=firmware, check=True)
            subprocess.run([binary], check=True)
    else:
        command = "gcc " + " ".join(inputs) + " -o /tmp/voice-interaction && /tmp/voice-interaction"
        subprocess.run([
            "docker", "run", "--rm", "--network", "none",
            "--mount", f"type=bind,source={firmware},target=/project,readonly",
            "--workdir", "/project", "gcc:14.2.0", "sh", "-c", command,
        ], check=True)


if __name__ == "__main__":
    main()
