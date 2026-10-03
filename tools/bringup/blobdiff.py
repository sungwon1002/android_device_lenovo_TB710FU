#!/usr/bin/env python3
"""Compare OnePlus sm8650 proprietary lists against the stock TB710FU dump.

Writes to $OUT:
  matched.txt    OnePlus entries (lines kept verbatim) whose file exists in stock
  missing.txt    OnePlus entries absent from stock
  unlisted.txt   stock vendor/odm/system_ext/product files no OnePlus list names
"""
import os
import re
import sys

STOCK = os.environ.get("TB710FU_STOCK", os.path.expanduser("~/tb520fu"))
REF = os.environ.get("TB710FU_REF", os.path.expanduser("~/ref"))
OUT = sys.argv[1]
LISTS = [
    "android_device_oneplus_sm8650-common/proprietary-files.txt",
    "android_device_oneplus_caihong/proprietary-files.txt",
]
PARTS = ["vendor", "odm", "system_ext", "product"]


def src_path(line):
    p = line.lstrip("-").split(";")[0].split(":")[0].split("|")[0].strip()
    return p


def exists(p):
    top = p.split("/")[0]
    if top not in PARTS:
        return os.path.exists(os.path.join(STOCK, "system", p))
    return os.path.exists(os.path.join(STOCK, p))


stock = set()
for part in PARTS:
    root = os.path.join(STOCK, part)
    for d, _, files in os.walk(root):
        for f in files:
            stock.add(os.path.relpath(os.path.join(d, f), STOCK))

matched, missing, listed = [], [], set()
for lst in LISTS:
    section = ""
    for line in open(os.path.join(REF, lst)):
        line = line.rstrip("\n")
        if not line or line.startswith("#"):
            if line.startswith("# ") and not line.startswith("# Current"):
                section = line
            continue
        p = src_path(line)
        listed.add(p)
        (matched if exists(p) else missing).append((section, line))

os.makedirs(OUT, exist_ok=True)


def dump(name, rows):
    with open(os.path.join(OUT, name), "w") as f:
        last = None
        for section, line in rows:
            if section != last:
                f.write("\n" + section + "\n")
                last = section
            f.write(line + "\n")


dump("matched.txt", matched)
dump("missing.txt", missing)
unlisted = sorted(p for p in stock if p not in listed)
with open(os.path.join(OUT, "unlisted.txt"), "w") as f:
    f.write("\n".join(unlisted) + "\n")
print(f"matched {len(matched)}  missing {len(missing)}  unlisted {len(unlisted)}")
