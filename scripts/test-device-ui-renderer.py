"""Render the production menu/cards and embedded CJK font using real LVGL.

ESP/FreeRTOS declarations are mocked only for compilation; workers, network,
audio, camera, NVS and hardware are never started. Artifacts stay in .tools.
"""

import argparse
import importlib.util
from pathlib import Path
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--inspect-only", action="store_true")
    parser.add_argument("--color-format", choices=["RGB565", "XRGB8888"], default="RGB565")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    firmware = root / "firmware"
    cache = root / ".tools" / "eyes-qa"
    output = (args.output or root / ".tools" / "device-ui-qa").resolve()
    output.mkdir(parents=True, exist_ok=True)
    spec = importlib.util.spec_from_file_location("eyes_qa", root / "scripts" / "test-robot-eyes-renderer.py")
    eyes_qa = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(eyes_qa)
    if not (cache / "lvgl-host.o").is_file() and not args.inspect_only:
        subprocess.run([sys.executable, str(root / "scripts" / "test-robot-eyes-renderer.py")], check=True)
    flags = (
        "-O1 -ffunction-sections -fdata-sections -DLV_CONF_SKIP=1 -DLV_KCONFIG_IGNORE=1 "
        "-DLV_COLOR_DEPTH=32 -DLV_USE_STDLIB_MALLOC=1 -DLV_USE_OS=0 "
        "-Itest/host/ui_stubs -Imain -Imanaged_components/lvgl__lvgl "
        "-Imanaged_components/espressif__cjson/cJSON "
    )
    if args.color_format == "RGB565":
        flags += "-DHOST_RGB565 "
    command = (
        "gcc -std=gnu11 " + flags + "-Wall -Wextra -Werror -c main/device_ui_font.c -o /tmp/ui-font.o && "
        "gcc -std=gnu11 " + flags + "-Wall -Wextra -Werror -c main/device_ui_protocol.c -o /tmp/ui-protocol.o && "
        "gcc -c test/host/device_ui_font_blob.S -o /tmp/ui-font-blob.o && "
        "g++ -std=gnu++20 " + flags + "-DSTACKCHAN_DEVICE_UI_HOST_TEST -Wall -Wextra -Werror "
        "-c main/device_ui.cpp -o /tmp/ui.o && "
        "g++ -std=gnu++20 " + flags + "-Wall -Wextra -Werror -c test/host/device_ui_render_test.cpp -o /tmp/ui-test.o && "
        "g++ -Wl,--gc-sections /tmp/ui.o /tmp/ui-test.o /tmp/ui-font.o /tmp/ui-font-blob.o /tmp/ui-protocol.o "
        "/cache/lvgl-host.o -lm -o /tmp/ui-test && /tmp/ui-test /output"
    )
    if not args.inspect_only:
        subprocess.run([
            "docker", "run", "--rm", "--network", "none",
            "--mount", f"type=bind,source={firmware},target=/project,readonly",
            "--mount", f"type=bind,source={cache},target=/cache,readonly",
            "--mount", f"type=bind,source={output},target=/output",
            "--workdir", "/project", "gcc:14.2.0", "sh", "-c", command,
        ], check=True)
    images = {}
    for ppm in sorted(output.glob("*.ppm")):
        header = ppm.read_bytes().split(b"\n", 3)
        if header[:3] != [b"P6", b"320 240", b"255"] or len(header[3]) != 320 * 240 * 3:
            raise AssertionError(f"{ppm.stem}: invalid RGB fixture")
        pixels = header[3]
        colored = sum(max(pixels[index:index + 3]) > 45 for index in range(0, len(pixels), 3))
        if not 1000 <= colored < 60000:
            raise AssertionError(f"{ppm.stem}: expected readable bounded menu/card contents ({colored})")
        eyes_qa.write_png(ppm.with_suffix(".png"), pixels, 320, 240)
        images[ppm.stem] = pixels
    if len(images) < 22:
        raise AssertionError("Expected both icon pages, eight settings, long card scroll and disabled states")
    try:
        from PIL import Image, ImageDraw
    except ImportError:
        Image = ImageDraw = None
    for group in ["MENU", "CARD"]:
        names = [name for name in images if name.startswith(group)]
        width, cell_height, columns = 320, 262, 3
        rows = (len(names) + columns - 1) // columns
        raw = bytearray(bytes([20, 25, 31]) * (width * columns * cell_height * rows))
        for index, name in enumerate(names):
            x, y = index % columns * width, index // columns * cell_height
            for row in range(240):
                destination = ((y + 22 + row) * width * columns + x) * 3
                raw[destination:destination + width * 3] = images[name][row * width * 3:(row + 1) * width * 3]
        contact = output / f"{group.lower()}-contact.png"
        eyes_qa.write_png(contact, raw, width * columns, cell_height * rows)
        if Image is not None:
            sheet = Image.open(contact).convert("RGB")
            draw = ImageDraw.Draw(sheet)
            for index, name in enumerate(names):
                draw.text((index % columns * width + 8, index // columns * cell_height + 4), name, fill="white")
            sheet.save(contact)
    print(f"PASS {args.color_format} menu/card pixel bounds, {len(images)} fixtures; artifacts: {output}")


if __name__ == "__main__":
    main()
