"""Capture a bounded, redacted subset of CoreS3 startup diagnostics.

Open COM3 only after the device owner has authorized serial access. This tool
never writes serial bytes or stores a raw capture on disk. The optional
--reset-on-open pulses the USB reset control line before read-only capture.
"""

import argparse
import re
import time
from datetime import datetime, timezone

import serial


ALLOWED = re.compile(
    r"ESP-ROM:|rst:0x|boot:0x|Guru Meditation|Backtrace:|stack overflow|"
    r"abort\(\)|assert failed|ESP_ERROR_CHECK failed|Task watchdog got triggered|"
    r"brownout|Brownout detector|Rebooting\.\.\.|"
    r"Core [01] register dump:|EXCCAUSE\s*:|EXCVADDR\s*:|"
    r"Voice upload (?:started|opened|progress|completed|failed|stopped):|"
    r"Voice live upload (?:opened|completed|failed):|"
    r"Voice stream playback:|Voice playback worker stopped:|"
    r"Voice stream response(?::| failed:)|"
    r"Live upload worker stopped:|Voice conversation cancelled|Voice turn failed safely:|"
    r"Firmware health confirmation|Pending firmware did not remain active|"
    r"StackChan foundation started|Startup headroom|"
    r"Device UI operation (?:started|finished):|Device UI render diagnostics:|"
    r"Expression refresh:|Expression frame diagnostics:|"
    r"CoreS3 hardware did not initialize|Encrypted NVS initialization failed|"
    r"K151 body hardware unavailable|K151 body:|"
    r"Servo pitch calibration reference:|LTR553 unavailable:|"
    r"LTR553 StackChan proximity profile:|LTR553 proximity band:|"
    r"LTR553 presence changed:|"
    r"Firmware OTA state initialization failed|Firmware OTA health guard did not start|"
    r"Wake model OTA state initialization failed|Voice control task did not start|"
    r"Transport task did not start|Transport task reservation|"
    r"Wi-Fi initialized after transport reservation|Wi-Fi monitor initialization failed|"
    r"Provisioning task reservation|"
    r"Provisioning task did not start|USB provisioning ready|"
    r"Wi-Fi reconnect|Wi-Fi connected;|Wi-Fi disconnected;|"
    r"Wi-Fi credentials are unavailable|Waiting for Wi-Fi connection|"
    r"Device WebSocket connected:|Device WebSocket unavailable:|"
    r"Device WebSocket configuration is invalid|WebSocket client allocation failed|"
    r"WebSocket stopped;|Valid device identity unavailable;|"
    r"Firmware OTA health confirmation failed|Firmware verified; restarting|"
    r"Servo response rejected: stage=|Servo prior write ACK skipped:|"
    r"Servo motion unavailable: stage=|Servo power source: state=|Servo start pose:|"
    r"Servo top touch confirmed:|Servo pitch diagnostic:|Servo pitch recovery complete:|"
    r"Servo shutdown could not be fully verified",
    re.IGNORECASE,
)
SENSITIVE = re.compile(
    r"password|token|bearer|authorization|api.?key|secret|ssid|https?://",
    re.IGNORECASE,
)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", default="COM3")
    parser.add_argument("--seconds", type=int, default=150)
    parser.add_argument("--reset-on-open", action="store_true")
    args = parser.parse_args()
    if not 1 <= args.seconds <= 3600:
        parser.error("--seconds must be within 1..3600")

    device = serial.Serial(port=None, baudrate=115200, timeout=0.2)
    device.dtr = False
    device.rts = False
    device.port = args.port
    device.open()
    if args.reset_on_open:
        device.rts = True
        device.dtr = device.dtr  # Windows usbser.sys needs DTR reasserted to apply RTS.
        time.sleep(0.2)
        device.rts = False
        device.dtr = device.dtr
        print("USB reset control lines pulsed; no serial command or motion was sent", flush=True)
    print(f"Serial read-only capture started: {args.port}, {args.seconds}s", flush=True)
    deadline = time.monotonic() + args.seconds
    partial = bytearray()
    observed = 0
    try:
        while time.monotonic() < deadline:
            chunk = device.read(256)
            if not chunk:
                continue
            for byte in chunk:
                if byte == 10:
                    line = partial.decode("utf-8", errors="replace").strip()
                    partial.clear()
                    if not line or not ALLOWED.search(line) or SENSITIVE.search(line):
                        continue
                    line = re.sub(r"\b(?:\d{1,3}\.){3}\d{1,3}\b", "<ip>", line)
                    if len(line) > 480:
                        line = line[:480] + "..."
                    print(f"{datetime.now(timezone.utc).isoformat()} {line}", flush=True)
                    observed += 1
                elif len(partial) < 2048:
                    partial.append(byte)
                else:
                    partial.clear()
    finally:
        device.close()
        print(f"Serial read-only capture ended: relevant_lines={observed}", flush=True)


if __name__ == "__main__":
    main()
