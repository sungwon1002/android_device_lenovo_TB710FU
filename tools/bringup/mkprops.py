#!/usr/bin/env python3
"""Draft vendor.prop / odm.prop from the stock TB710FU build.prop files.

Drops properties the build system generates itself and ZUI/LGSI-only ones,
keeps everything else grouped by the stock comment sections.
"""
import os
import re
import sys

STOCK = os.environ.get("TB710FU_STOCK", os.path.expanduser("~/tb520fu"))
TREE = sys.argv[1]

DROP = re.compile(
    r"^(ro\.(build|vendor\.build|odm\.build|product|bootimage|board|virtual_ab|"
    r"vendor_dlkm\.build|system\.build|soc|boot\.dynamic_partitions|"
    r"treble|vndk|zygote|hwui|carrier|config\.(ringtone|notification_sound|alarm_alert)|"
    r"vendor\.config\.lgsi|config\.lgsi|lenovo|zui|com\.google|opa)\b"
    r"|ro\.vendor\.(product|lenovo|zui)"
    r"|dalvik\.vm\.(heap|dex2oat|image-dex2oat|appimageformat|usejit)"
    r"|persist\.sys\.(zui|lenovo|lgsi)"
    r"|ro\.recovery\."
    r"|import\b)"
)


def convert(src, dst, header):
    out, kept, dropped = [header], 0, []
    for line in open(src):
        line = line.rstrip("\n")
        if not line.strip():
            continue
        if line.startswith("#"):
            if line.startswith("# from ") or "BEGIN" in line or "END" in line:
                continue
            out.append(line)
            continue
        key = line.split("=", 1)[0].strip()
        if DROP.match(key) or "lenovo" in key.lower() or "zui" in key.lower():
            dropped.append(key)
            continue
        out.append(line)
        kept += 1
    # drop comment lines that are no longer followed by a property
    cleaned = []
    for i, line in enumerate(out):
        if line.startswith("#") and i > 0 and (i + 1 == len(out) or out[i + 1].startswith("#")):
            continue
        cleaned.append(line)
    open(dst, "w").write("\n".join(cleaned) + "\n")
    print(f"{dst}: kept {kept}, dropped {len(dropped)}")
    return dropped


d1 = convert(f"{STOCK}/vendor/build.prop", f"{TREE}/vendor.prop",
             "# Imported from stock TB710FU ZUI_17.5.10.362 vendor/build.prop")
d2 = convert(f"{STOCK}/odm/etc/build.prop", f"{TREE}/odm.prop",
             "# Imported from stock TB710FU ZUI_17.5.10.362 odm/etc/build.prop")
print("dropped:", " ".join(sorted(set(d1 + d2))))
