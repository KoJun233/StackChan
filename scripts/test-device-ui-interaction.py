"""Exercise production UI pointer gestures and worker receipts under ASan/UBSan.

The complete bundled LVGL library is instrumented. Network, Flash, queues and
hardware are in-memory fixtures; no device or live service is contacted.
"""
import hashlib
from pathlib import Path
import shlex
import subprocess


def main():
    root=Path(__file__).resolve().parents[1]
    firmware=root/"firmware"
    output=root/".tools/device-menu-interaction-qa"
    output.mkdir(parents=True,exist_ok=True)
    lvgl=firmware/"managed_components/lvgl__lvgl"
    flags=("-O1 -g -fno-omit-frame-pointer -fsanitize=address,undefined "
           "-ffunction-sections -fdata-sections -DLV_CONF_SKIP=1 -DLV_KCONFIG_IGNORE=1 "
           "-DLV_COLOR_DEPTH=32 -DLV_USE_STDLIB_MALLOC=1 -DLV_USE_OS=0 "
           "-Itest/host/ui_stubs -Imain -Imanaged_components/lvgl__lvgl "
           "-Imanaged_components/espressif__cjson/cJSON ")
    library_key=hashlib.sha256((lvgl/"CHECKSUMS.json").read_bytes()+flags.encode()).hexdigest()
    key_file=output/"lvgl-sanitized.key"
    cached=(output/"lvgl-sanitized.o").is_file() and key_file.is_file() and key_file.read_text().strip()==library_key
    sources=[shlex.quote(p.relative_to(firmware).as_posix()) for p in sorted((lvgl/"src").rglob("*.c"))]
    (output/"lvgl-sources.rsp").write_text("\n".join(sources),encoding="utf-8")
    container=["docker","run","--rm","--network","none",
        "--mount",f"type=bind,source={firmware},target=/project,readonly",
        "--mount",f"type=bind,source={output},target=/output",
        "--workdir","/project","gcc:14.2.0","sh","-c"]
    if not cached:
        subprocess.run(container+["gcc -std=gnu11 "+flags+"@/output/lvgl-sources.rsp -r -o /output/lvgl-sanitized.o"],check=True)
        key_file.write_text(library_key,encoding="utf-8")
    commands=[]
    objects=[]
    for name in ["main/device_ui_font.c","main/device_ui_protocol.c","main/device_endpoint.c","main/strict_json.c",
                 "managed_components/espressif__cjson/cJSON/cJSON.c"]:
        obj="/tmp/"+Path(name).stem+".o";objects.append(obj)
        commands.append("gcc -std=gnu11 "+flags+"-Wall -Wextra -Werror -c "+name+" -o "+obj)
    commands.extend([
        "gcc -c test/host/device_ui_font_blob.S -o /tmp/font-blob.o",
        "g++ -std=gnu++20 "+flags+"-Wall -Wextra -Werror -c test/host/device_ui_interaction_test.cpp -o /tmp/ui-test.o",
        "g++ -fsanitize=address,undefined -Wl,--gc-sections /tmp/ui-test.o /tmp/font-blob.o "+
        " ".join(objects)+" /output/lvgl-sanitized.o -lm -o /tmp/ui-test",
        "ASAN_OPTIONS=detect_leaks=1:halt_on_error=1 UBSAN_OPTIONS=halt_on_error=1 /tmp/ui-test",
    ])
    subprocess.run(container+[" && ".join(commands)],check=True)


if __name__=="__main__":
    main()
