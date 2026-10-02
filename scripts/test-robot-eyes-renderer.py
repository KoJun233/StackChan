"""Render production robot eyes using the bundled LVGL, without SDL or hardware.

Default artifacts are under ignored .tools/eyes-qa. The container is offline,
mounts firmware read-only and writes only this output directory.
"""

import argparse
import hashlib
import json
from pathlib import Path
import shlex
import struct
import subprocess
import zlib


def write_png(path, pixels, width, height):
    """RGB PNG writer keeps ordinary Python sufficient for automated QA."""
    def chunk(kind, body):
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body))
    rows = b"".join(b"\0" + pixels[row * width * 3:(row + 1) * width * 3] for row in range(height))
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)) +
                     chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b""))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, help="PPM/PNG and contact sheet output directory")
    parser.add_argument("--inspect-only", action="store_true", help="Verify/convert already rendered PPM fixtures")
    parser.add_argument("--color-format", choices=["RGB565", "XRGB8888"], default="RGB565")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    firmware = root / "firmware"
    output = (args.output or root / ".tools" / "eyes-qa").resolve()
    output.mkdir(parents=True, exist_ok=True)
    lvgl = firmware / "managed_components" / "lvgl__lvgl"
    if not (lvgl / "lvgl.h").is_file():
        raise RuntimeError("Bundled firmware LVGL dependency is required; prepare the ordinary firmware build first")
    sources = [path.relative_to(firmware).as_posix() for path in sorted((lvgl / "src").rglob("*.c"))]
    # GCC response file avoids the Windows command length limit. Library paths
    # are repository-owned and quoted as compiler arguments, not shell text.
    (output / "lvgl-sources.rsp").write_text("\n".join(shlex.quote(path) for path in sources), encoding="utf-8")
    flags = (
        "-std=gnu11 -O1 -DLV_CONF_SKIP=1 -DLV_KCONFIG_IGNORE=1 -DLV_COLOR_DEPTH=32 "
        "-DLV_USE_STDLIB_MALLOC=1 -DLV_USE_OS=0 -Imain -Imanaged_components/lvgl__lvgl "
    )
    if args.color_format=="RGB565":
        flags+="-DHOST_RGB565 "
    library_key = hashlib.sha256((lvgl / "CHECKSUMS.json").read_bytes() + flags.encode()).hexdigest()
    key_file = output / "lvgl-host.key"
    cached = (output / "lvgl-host.o").is_file() and key_file.is_file() and key_file.read_text().strip() == library_key
    command = (
        "gcc " + flags + "-Wall -Wextra -Werror -c main/robot_eyes_renderer.c -o /tmp/renderer.o && " +
        ("" if cached else "gcc " + flags + "@/output/lvgl-sources.rsp -r -o /output/lvgl-host.o && ") +
        "gcc " + flags + "-Wall -Wextra -Werror main/expression_engine.c main/robot_eyes_renderer.c "
        "test/host/robot_eyes_render_test.c /output/lvgl-host.o -lm -o /tmp/eyes-render && /tmp/eyes-render /output"
    )
    if not args.inspect_only:
        subprocess.run([
            "docker", "run", "--rm", "--network", "none",
            "--mount", f"type=bind,source={firmware},target=/project,readonly",
            "--mount", f"type=bind,source={output},target=/output",
            "--workdir", "/project", "gcc:14.2.0", "sh", "-c", command,
        ], check=True)
        key_file.write_text(library_key, encoding="utf-8")
    emotions = ["NEUTRAL", "HAPPY", "LOVING", "SAD", "ANGRY", "SURPRISED",
                "CONFUSED", "SHY", "TIRED", "FOCUSED", "NERVOUS", "CONTENT"]
    hashes = {hashlib.sha256((output / f"{name}.ppm").read_bytes()).hexdigest() for name in emotions}
    if len(hashes) != 12:
        raise AssertionError("Every emotion must produce a distinct LVGL frame")
    try:
        from PIL import Image, ImageDraw
    except ImportError:
        Image = ImageDraw = None
    images = {}
    for ppm in sorted(output.glob("*.ppm")):
        header = ppm.read_bytes().split(b"\n", 3)
        if header[:3] != [b"P6", b"192 192", b"255"] or len(header[3]) != 192 * 192 * 3:
            raise AssertionError(f"{ppm.stem}: invalid fixture format")
        pixels = header[3]
        colored = sum(max(pixels[index:index + 3]) > 32 for index in range(0, len(pixels), 3))
        if not 25 <= colored < 14000:
            raise AssertionError(f"{ppm.stem}: missing eyes or excessive background fill ({colored} pixels)")
        if pixels[:3] != b"\0\0\0" or pixels[-3:] != b"\0\0\0":
            raise AssertionError(f"{ppm.stem}: black background boundary violated")
        write_png(ppm.with_suffix(".png"), pixels, 192, 192)
        images[ppm.stem] = pixels
    groups = {
        "emotions": emotions,
        "systems": [name for name in images if name.startswith("STATE_")],
        "behaviors": [name for name in images if name.startswith("BEHAVIOR_")] +
                     ["INDEPENDENT_BLINK_PTT", "INDEPENDENT_LIDS_DARK_THEME", "INVALID_POSE_BOUNDED"],
    }
    for group, names in groups.items():
        columns = 4
        rows = (len(names) + columns - 1) // columns
        raw_sheet = bytearray(bytes([20, 25, 31]) * (columns * 212 * rows * 222))
        for index, name in enumerate(names):
            x, y = index % columns * 212, index // columns * 222
            for row in range(192):
                destination = ((y + 24 + row) * columns * 212 + x + 10) * 3
                raw_sheet[destination:destination + 192 * 3] = images[name][row * 192 * 3:(row + 1) * 192 * 3]
        contact = output / f"{group}-contact.png"
        write_png(contact, raw_sheet, columns * 212, rows * 222)
        if Image is not None:
            sheet = Image.open(contact).convert("RGB")
            draw = ImageDraw.Draw(sheet)
            for index, name in enumerate(names):
                x, y = index % columns * 212, index // columns * 222
                draw.text((x + 10, y + 6), name.replace("STATE_", "").replace("BEHAVIOR_", ""), fill="white")
            sheet.save(contact)
        (output / f"{group}-contact.json").write_text(json.dumps(names), encoding="utf-8")
    # Each frame comes from the same production LVGL path as the still fixtures.
    # 25 fps is only the portable preview cadence, not a hardware measurement.
    for recording in sorted(output.glob("ANIMATION_*.rgb")):
        raw = recording.read_bytes()
        frame_bytes = 192 * 192 * 3
        if len(raw) != 80 * frame_bytes:
            raise AssertionError(f"{recording.stem}: expected 80 native RGB frames")
        if Image is not None:
            animation = [Image.frombytes("RGB", (192, 192), raw[i:i + frame_bytes])
                         for i in range(0, len(raw), frame_bytes)]
            animation[0].save(recording.with_suffix(".gif"), save_all=True,
                              append_images=animation[1:], duration=40, loop=0, disposal=2)
    print(f"PASS 12 distinct emotions; bounded drawing, black background; artifacts: {output}")


if __name__ == "__main__":
    main()
