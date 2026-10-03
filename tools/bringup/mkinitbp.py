#!/usr/bin/env python3
"""Write init/Android.bp for the stock init files copied into the tree and
print the PRODUCT_PACKAGES list for device.mk."""
import os
import sys

I = sys.argv[1]
files = sorted(os.listdir(I))
out = ["//\n// Copyright (C) 2026 The LineageOS Project\n// SPDX-License-Identifier: Apache-2.0\n//\n"]
pkgs = []

out.append('''prebuilt_etc {
    name: "fstab.qcom",
    src: "fstab.qcom",
    vendor: true,
    vendor_ramdisk_available: true,
}
''')
pkgs += ["fstab.qcom", "fstab.qcom.vendor_ramdisk"]

out.append('''prebuilt_etc {
    name: "ueventd.qcom.rc",
    filename: "ueventd.rc",
    src: "ueventd.qcom.rc",
    vendor: true,
}
''')
pkgs.append("ueventd.qcom.rc")

for f in files:
    if f.endswith(".rc") and f != "ueventd.qcom.rc":
        out.append(f'''prebuilt_etc {{
    name: "{f}",
    src: "{f}",
    sub_dir: "init/hw",
    vendor: true,
}}
''')
        pkgs.append(f)
for f in files:
    if f.endswith(".sh"):
        out.append(f'''sh_binary {{
    name: "{f}",
    src: "{f}",
    vendor: true,
}}
''')
        pkgs.append(f)

open(os.path.join(I, "Android.bp"), "w").write("\n".join(out))
print("PRODUCT_PACKAGES += \\")
print(" \\\n".join("    " + p for p in pkgs))
