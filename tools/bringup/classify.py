#!/usr/bin/env python3
"""Bucket unlisted stock vendor/odm files by feature keyword."""
import collections
import os
import re
import sys

D = os.path.join(os.environ.get("TB710FU_STOCK", os.path.expanduser("~/tb520fu")), "blobdiff")
RULES = [
    ("camera", r"camera|camx|chi|/cam|mfnr|arcsoft|mpbase|qvrcamera|eis|facedetect|libofflinecam"),
    ("dolby", r"dolby|dax|libdlb|dapparam|libstagefright_soft_ddpdec|libdeccfg"),
    ("lenovo", r"lenovo|zui|zuiperf|hyperschedule|fan_service|init_fan"),
    ("fingerprint", r"gf_|fingerprint|goodix|\bgf\b|libgf"),
    ("face", r"biometrics\.face|faceimport|libface"),
    ("audio", r"audio|acdb|agm|pal|aw88|awinic|sound|snd|mixer|soundfx|voiceui|sva|listen|tfa|smartpa"),
    ("display", r"display|sdm|demura|qdcm|snapdragoncolor|color|disp|hdr|ubwc|gralloc|composer|libgpu|adreno|libllvm|vulkan|egl|gles|libCB|libkgsl|libq3d"),
    ("media", r"media|c2|codec|video|vpp|libOmx|venus|iris|mm-|libmm"),
    ("sensors", r"sensor|ssc|sns|qsh|hall"),
    ("wifi/bt/fm", r"wlan|wifi|cnss|hostapd|wpa|bt_|bluetooth|btaudio|fm|qca|wcnss|sar"),
    ("security", r"keymint|keymaster|gatekeeper|weaver|spu|qsee|smcinvoke|tee|trusted|soter|drm|widevine|hdcp|secure|qwes|ssg|keybox|rpmb|cas"),
    ("data/ims/modem", r"ims|cne|cnd|dpm|data|ipa|qmi|rmnet|netmgr|embms|mdm|modem|diag|qcril|ril|port-bridge|rfs|tftp|rmt_storage|wds|dsi"),
    ("perf/power/thermal", r"perf|power|thermal|limits|iop|pasr|memtrack|uclamp|boost|post_boot|kernel"),
    ("xr", r"qvr|xr|sxr"),
    ("toybox/tools", r"^vendor/bin/(\[|[a-z0-9]{1,10})$"),
]
rows = [l.strip() for l in open(os.path.join(D, "unlisted.txt")) if l.startswith(("vendor/", "odm/"))]
buckets = collections.defaultdict(list)
for p in rows:
    for name, rx in RULES:
        if re.search(rx, p, re.I):
            buckets[name].append(p)
            break
    else:
        buckets["other"].append(p)
for name, items in sorted(buckets.items(), key=lambda kv: -len(kv[1])):
    print(f"{len(items):5d} {name}")
    with open(os.path.join(D, "cat_" + re.sub(r"\W", "_", name) + ".txt"), "w") as f:
        f.write("\n".join(items) + "\n")
