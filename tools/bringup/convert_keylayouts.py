#!/usr/bin/env python3
"""Rewrite the stock Lenovo keylayouts (ZUI-only keycodes) to AOSP keycodes.

usage: convert_keylayouts.py <stock system/usr/keylayout> <out dir> <InputEventLabels.cpp>

ZUI adds keycodes (PEN_*, TP_MUTE, APP1, ...) that AOSP does not know; a
keylayout with one unknown label fails to parse as a whole and the device
falls back to Generic.kl. The special keys are mapped to otherwise unused AOSP
keycodes that tb520fu-input (input/) handles.
"""
import os
import re
import sys

MAP = {
    "LENOVO_VOLUME_MUTE": "VOLUME_MUTE",
    "SCREEN_LOCK": "LOCK",
    "FULL_SCREEN": "FULLSCREEN",
    "GLOBAL_SEARCH": "SEARCH",
    "SPLIT_SCREEN": "RECENT_APPS",
    "ZUI_SETTING": "SETTINGS",
    "BACK_LIGHT": "KEYBOARD_BACKLIGHT_TOGGLE",
    # handled by input/KeyboardController.java
    "TP_MUTE": "F24",
    "APP1": "MACRO_1",
    "APP2": "MACRO_2",
    "FN_LOCK": "MACRO_3",
    # handled by input/PenKeys.java
    "PEN_ONE_CLICK": "F13",
    "PEN_TWO_CLICK": "F14",
    "PEN_THREE_CLICK": "F15",
    "PEN_LONG_CLICK": "F16",
    "PEN_PRESS_CLICK": "F17",
    "PEN_BT_DISCONNECT": "F18",
    "PEN_360_THREE_CLICK": "F19",
    "PEN_D_LONG_PRESS": "F20",
    "PEN_D_SLIDE_DOWN": "F21",
    "PEN_D_SLIDE_UP": "F22",
    "PEN_D_CLICK": "F23",
}
# Lenovo ecosystem / PC mode keys without an equivalent here.
DROP = {"SUPER_CONNECT", "PC_MODE", "KEYBOARD_RESTORE", "KEYBOARD_REVERSE",
        "HEADSET_PLUG", "HEADSET_UNPLUG"}
FLAGS = {"FUNCTION", "GESTURE", "VIRTUAL", "WAKE", "FALLBACK_USAGE_MAPPING"}


def main():
    src, out, labels = sys.argv[1:4]
    known = set(re.findall(r"DEFINE_KEYCODE\((\w+)\)", open(labels).read()))
    os.makedirs(out, exist_ok=True)
    for name in sorted(os.listdir(src)):
        if not name.lower().startswith("vendor_17ef_"):
            continue
        lines = []
        for line in open(os.path.join(src, name)):
            m = re.match(r"(\s*key\s+(?:usage\s+)?\S+\s+)(\w+)(.*)$", line.rstrip("\n"))
            if not m:
                lines.append(line.rstrip("\n"))
                continue
            head, code, rest = m.groups()
            flags = rest.split()
            if code in DROP:
                lines.append("# " + line.strip() + "  (no AOSP equivalent)")
                continue
            new = MAP.get(code, code)
            if new not in known or any(f not in FLAGS for f in flags):
                sys.exit(f"{name}: unknown label in: {line.strip()}")
            if new != code:
                lines.append(f"{head}{new}{rest}  # ZUI {code}")
            else:
                lines.append(line.rstrip("\n"))
        header = ["# Converted from the stock ZUI keylayout by",
                  "# tools/bringup/convert_keylayouts.py; ZUI-only keycodes remapped."]
        with open(os.path.join(out, name.replace("61A1", "61a1")), "w") as f:
            f.write("\n".join(header + lines) + "\n")
        print("wrote", name)


if __name__ == "__main__":
    main()
