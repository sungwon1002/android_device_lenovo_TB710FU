#!/usr/bin/env python3
#
# SPDX-FileCopyrightText: 2026 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#
"""Put the classes.dex of a d8 output zip in front of an APK's dex files.

ART resolves a class from the first dex of the APK that defines it, so the
compat classes shadow the stock ones with the same name. Everything else is
copied unchanged (the APK is re-signed and aligned by android_app_import).
"""
import argparse
import re
import zipfile


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apk", required=True)
    ap.add_argument("--dex-zip", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    with zipfile.ZipFile(args.dex_zip) as dz:
        compat = dz.read("classes.dex")

    dex_re = re.compile(r"^classes(\d*)\.dex$")
    with zipfile.ZipFile(args.apk) as src, \
            zipfile.ZipFile(args.out, "w") as out:
        out.writestr(zipfile.ZipInfo("classes.dex", date_time=(2008, 1, 1, 0, 0, 0)), compat,
                     compress_type=zipfile.ZIP_DEFLATED)
        for info in src.infolist():
            if info.filename.startswith("META-INF/"):
                continue  # old signature
            data = src.read(info.filename)
            m = dex_re.match(info.filename)
            name = info.filename
            if m:
                n = int(m.group(1) or 1)
                name = f"classes{n + 1}.dex"
            zi = zipfile.ZipInfo(name, date_time=info.date_time)
            zi.external_attr = info.external_attr
            out.writestr(zi, data, compress_type=info.compress_type)


if __name__ == "__main__":
    main()
