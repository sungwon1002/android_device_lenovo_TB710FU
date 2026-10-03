#!/usr/bin/env python3
"""Generate proprietary-files.txt for TB710FU (phase 1: boot + core hardware).

Sources:
  ~/tb520fu/blobdiff/matched.txt   OnePlus sm8650 entries that exist in stock
  Lenovo additions (EXTRA_* below) matched against the stock dump
Then pulls in init rc / VINTF fragments of included services and the NEEDED
closure of included ELFs, skipping libraries the source tree builds.
"""
import fnmatch
import os
import re
import subprocess
import sys

STOCK = os.environ.get("TB710FU_STOCK", os.path.expanduser("~/tb520fu"))
BLOBDIFF = os.path.join(STOCK, "blobdiff")
SRC_MODULES = set(open(os.path.join(STOCK, "src_modules.txt")).read().split())
OUT = sys.argv[1]
PARTS = ("vendor", "odm", "system_ext", "product")

SKIP_SECTIONS = {"# Dolby Vision", "# QVR", "# SXR", "# WiFi Display"}

# Lenovo-specific additions, grouped into output sections.
EXTRA = {
    "Audio (Lenovo configs, ACDB, awinic amplifier)": [
        "vendor/etc/audio/**",
        "vendor/etc/acdbdata/**",
        "vendor/etc/card-defs.xml",
        "vendor/etc/backend_conf.xml",
        "vendor/etc/microphone_characteristics.xml",
        "vendor/etc/audio_policy_configuration.xml",
        "vendor/etc/bluetooth_qti_hearing_aid_audio_policy_configuration.xml",
        "vendor/etc/audio_effects.xml",
        "vendor/etc/audio_effects.conf",
        "vendor/etc/default_volume_tables.xml",
        "vendor/etc/audio_policy_volumes.xml",
        "vendor/etc/Hapticsconfig.xml",
        "vendor/etc/usecaseKvManager.xml",
        "vendor/bin/aw882xx_cali",
    ],
    "Display (Lenovo panel configs)": [
        "vendor/etc/display/**",
    ],
    "Camera (Lenovo camx)": [
        "vendor/bin/hw/vendor.qti.camera.provider-service_64",
        "vendor/etc/init/vendor.qti.camera.provider-service_64.rc",
        "vendor/etc/vintf/manifest/vendor.qti.camera.*.xml",
        "vendor/etc/camera/**",
        "vendor/lib64/camera/**",
        "vendor/firmware/CAMERA_ICP*",
            "vendor/lib64/hw/camera.qcom*.so",
        "vendor/lib64/hw/com.qti.chi.*.so",
        "vendor/lib64/libcamx*.so",
        "vendor/lib64/libchi*.so",
        "vendor/lib64/com.qti.*.so",
        "vendor/lib64/libpandora*.so",
        "vendor/lib64/libarcsoft_hdr_detection.so",
        "vendor/lib64/libarcsoft_high_dynamic_range*.so",
        "vendor/lib64/libarcsoft_video_superportrait.so",
        "vendor/lib64/libarcsoft_qnnhtp.so",
        "vendor/lib64/libmpbase.so",
        "vendor/lib64/libcamera2ndk_vendor.so",
        "vendor/lib64/vendor.qti.hardware.camera.*.so",
    ],
    "Fingerprint (Goodix)": [
        "vendor/bin/hw/android.hardware.biometrics.fingerprint-service.gf",
        "vendor/etc/init/fingerprint-gf.rc",
        "vendor/etc/vintf/manifest/fingerprint-example.xml",
        "vendor/lib64/hw/gf_fingerprint.default.so",
    ],
    "Lights": [
        "vendor/bin/hw/vendor.qti.hardware.lights.service",
        "vendor/etc/init/vendor.qti.hardware.lights.service.rc",
        "vendor/etc/vintf/manifest/vendor.qti.hardware.lights.service.xml",
    ],
    "Firmware": [
        "vendor/firmware/*",
        "vendor/firmware/**",
    ],
    "Media (codec configs)": [
        "vendor/etc/media_codecs*.xml",
        "vendor/etc/media_profiles*.xml",
        "vendor/etc/video_system_specs.json",
    ],
    "Perf (boost configs, the OnePlus list ships its own)": [
        "vendor/etc/perf/perfboostsconfig.xml",
        "vendor/etc/perf/perfconfigstore.xml",
    ],
    "Widevine (OEMCrypto, dlopen'd by the widevine HAL)": [
        "vendor/lib64/liboemcrypto.so",
    ],
    "QESDK (security manager, its rc and policy were already in)": [
        "vendor/bin/qesdk-secmanager",
    ],
    "Sensors (Lenovo configs)": [
        "vendor/etc/sensors/**",
    ],
    "WiFi (configs)": [
        "vendor/etc/wifi/**",
    ],
}
# Never take these as blobs (camera/face/fingerprint come in phase 2, the rest
# is built from source or not wanted).
EXCLUDE = [
    "vendor/firmware/arcface*",
    "vendor/firmware/wlan/**",          # symlinks created by Android.bp
    "vendor/firmware/wlanmdsp.otaupdate",
    "vendor/etc/audio/audio-generic-modules.mk",
    "vendor/etc/audio/audio_vendor_product.mk",
    "vendor/etc/wifi/wpa_supplicant.conf",  # built from source
    "vendor/etc/vintf/manifest/android.hardware.drm-service.xml",  # WFD wfdhdcp only
]

# Files the device tree ships itself (init/) must not also come in as blobs.
TREE_INIT = os.path.join(os.path.dirname(os.path.abspath(OUT)), "init")
for f in os.listdir(TREE_INIT):
    if f.endswith(".sh"):
        EXCLUDE.append(f"vendor/bin/{f}")
    elif f.endswith(".rc") and f != "ueventd.qcom.rc":
        EXCLUDE.append(f"vendor/etc/init/hw/{f}")
EXCLUDE += ["vendor/etc/fstab.qcom", "vendor/etc/ueventd.rc"]

# ---------------------------------------------------------------------------

stock = set()
for part in PARTS:
    for d, _, files in os.walk(os.path.join(STOCK, part)):
        for f in files:
            p = os.path.relpath(os.path.join(d, f), STOCK)
            if not os.path.islink(os.path.join(STOCK, p)):
                stock.add(p)


def src_path(line):
    return line.lstrip("-").split(";")[0].split(":")[0].split("|")[0].strip()


def match(pattern):
    if pattern.endswith("/**"):
        base = pattern[:-3] + "/"
        return sorted(p for p in stock if p.startswith(base))
    return sorted(p for p in stock if fnmatch.fnmatchcase(p, pattern))


def excluded(p):
    return any(fnmatch.fnmatchcase(p, e) or (e.endswith("/**") and p.startswith(e[:-3] + "/"))
               for e in EXCLUDE)


sections = []  # (title, [lines])
included = set()

# 1. OnePlus matched entries
cur_title, cur = None, []
for line in open(os.path.join(BLOBDIFF, "matched.txt")):
    line = line.rstrip("\n")
    if not line:
        continue
    if line.startswith("# "):
        if cur_title and cur and cur_title not in SKIP_SECTIONS:
            sections.append((cur_title[2:], cur))
        cur_title, cur = line, []
        continue
    p = src_path(line)
    dst = line.lstrip("-").split(";")[0].split("|")[0].split(":")[-1]
    if excluded(p) or p in included:
        continue
    cur.append(line)
    included.add(p)
    included.add(dst)
if cur_title and cur and cur_title not in SKIP_SECTIONS:
    sections.append((cur_title[2:], cur))

# 2. Lenovo additions
_AIDL = re.compile(r"^(.*?)-V\d+-(ndk|cpp|ndk_platform)$")


def _src_lib(p):
    if not p.endswith(".so"):
        return False
    n = os.path.basename(p)[:-3]
    m = _AIDL.match(n)
    return n in SRC_MODULES or bool(m and m.group(1) in SRC_MODULES)


for title, pats in EXTRA.items():
    lines = []
    for pat in pats:
        for p in match(pat):
            if p in included or excluded(p) or _src_lib(p):
                continue
            lines.append(p)
            included.add(p)
    if lines:
        sections.append((title, sorted(lines)))

# Stock QTI power HAL: the open source power-service-qti gets -1 from every
# perf_hint() on this perf HAL, so no launch/touch boost is ever applied.
# power.xml is renamed because a source module already uses that name.
power = [
    "vendor/bin/hw/android.hardware.power-service",
    "vendor/etc/init/android.hardware.power-service.rc",
    "vendor/etc/powerhint.xml",
    "vendor/etc/vintf/manifest/power.xml:vendor/etc/vintf/manifest/android.hardware.power-service.xml",
]
sections.append(("Power (stock QTI power HAL)", power))
included.update(src_path(l) for l in power)

# Dolby Atmos (DAX3): effect libs + dms HAL + daxService. The UI is PixelOS'
# packages/apps/DolbyAtmos; audio_effects.xml (sku_pineapple) already lists
# the dap/dvl/gamedap libraries. The NEEDED closure below adds the rest.
dolby = [
    "vendor/bin/hw/vendor.dolby.hardware.dms@2.0-service",
    "vendor/etc/init/vendor.dolby.hardware.dms@2.0-service.rc",
    "vendor/etc/vintf/manifest/vendor.dolby.hardware.dms.xml",
    "vendor/etc/dolby/dax-default.xml",
    "vendor/lib64/soundfx/libdlbvol.so",
    "vendor/lib64/soundfx/libswdap.so",
    "vendor/lib64/soundfx/libswgamedap.so",
    "system_ext/etc/permissions/com.dolby.daxservice.xml",
    # Re-signed with platform: it needs INTERACT_ACROSS_USERS_FULL (stock is
    # signed with the Lenovo platform key).
    "system_ext/priv-app/daxService/daxService.apk",
]
sections.append(("Dolby Atmos (DAX3)", dolby))
included.update(src_path(l) for l in dolby)

# Lenovo HALs used by the pen/keyboard bridge (input/): battery (stylus Qi
# commands, charge protection), touchscreen (pen BLE mode, haptics) and the
# UART keyboard. The NEEDED closure adds their -ndk libraries.
lenovo_hals = []
for hal in ("battery", "touchscreen", "keyboard"):
    lenovo_hals += match(f"vendor/bin/hw/vendor.lenovo.hardware.{hal}-service")
    lenovo_hals += match(f"vendor/etc/init/vendor.lenovo.hardware.{hal}*.rc")
    lenovo_hals += match(f"vendor/etc/vintf/manifest/vendor.lenovo.hardware.{hal}*.xml")
sections.append(("Lenovo HALs (battery, touchscreen, keyboard)", lenovo_hals))
included.update(lenovo_hals)

# Lenovo pen / accessory apps, moved to system_ext and re-signed with the
# platform key (PenService runs as android.uid.system). PenService is only
# extracted: lenovo/PenService builds it with a compat dex prepended.
lenovo_apps = [
    "system/priv-app/PenService/PenService.apk:system_ext/priv-app/PenService/PenService.apk;EXTRACT_ONLY;FILEGROUP=PenService-stock",
    "system/priv-app/ZuiUDevice/ZuiUDevice.apk:system_ext/priv-app/ZuiUDevice/ZuiUDevice.apk",
    "system/etc/permissions/privapp-permissions-com.zui.udevice.xml:system_ext/etc/permissions/privapp-permissions-com.zui.udevice.xml",
    "system/etc/sysconfig/hiddenapi-whitelist-com.zui.udevice.xml:system_ext/etc/sysconfig/hiddenapi-whitelist-com.zui.udevice.xml",
    "system/etc/default-permissions/default-permissions-com.zui.udevice.xml:system_ext/etc/default-permissions/default-permissions-com.zui.udevice.xml",
]
sections.append(("Lenovo pen and accessory apps", lenovo_apps))
included.update(src_path(l) for l in lenovo_apps)


# 3. rc files + VINTF fragments for included executables
def text(p):
    try:
        return open(os.path.join(STOCK, p), errors="ignore").read()
    except OSError:
        return ""


bins = {p for p in included if "/bin/" in p}
rcs = [p for p in stock if p.startswith("vendor/etc/init/") and p.endswith(".rc")]
frags = [p for p in stock if p.startswith("vendor/etc/vintf/manifest/")]
auto = []
for rc in rcs:
    if rc in included or os.path.basename(rc) in SRC_MODULES:
        continue
    t = text(rc)
    if any(("/" + b) in t for b in bins):
        auto.append(rc)
if auto:
    sections.append(("Init / VINTF for included services", sorted(auto)))
    included.update(auto)

# 4. NEEDED closure
AIDL_RX = re.compile(r"^(.*?)-V\d+-(ndk|cpp|ndk_platform)$")


def built_from_source(lib):
    name = lib[:-3]
    if name in SRC_MODULES:
        return True
    m = AIDL_RX.match(name)
    if m and m.group(1) in SRC_MODULES:
        return True
    return False


def needed(p):
    out = subprocess.run(["readelf", "-dW", os.path.join(STOCK, p)],
                         capture_output=True, text=True).stdout
    return re.findall(r"\(NEEDED\)\s+Shared library: \[(.+?)\]", out)


def is_elf(p):
    try:
        with open(os.path.join(STOCK, p), "rb") as f:
            return f.read(4) == b"\x7fELF"
    except OSError:
        return False


libdirs = ["vendor/lib64", "odm/lib64", "vendor/lib64/hw", "system_ext/lib64", "product/lib64"]
# Libraries soong reported as undefined (filled in by fixdeps.sh)
FORCE = os.path.join(STOCK, "force_libs.txt")
forced = []
if os.path.exists(FORCE):
    for name in open(FORCE).read().split():
        for d in libdirs:
            c = f"{d}/{name}.so"
            if c in stock and c not in included:
                forced.append(c)
                included.add(c)
if forced:
    sections.append(("Dependencies (reported by soong)", sorted(forced)))
forced_names = set(open(FORCE).read().split()) if os.path.exists(FORCE) else set()
todo = [p for p in included if is_elf(p)]
deps, unresolved = [], {}
seen = set()
while todo:
    p = todo.pop()
    for lib in needed(p):
        if lib in seen:
            continue
        seen.add(lib)
        cand = next((f"{d}/{lib}" for d in libdirs
                     if f"{d}/{lib}" in stock and d.split("/")[0] == p.split("/")[0]), None) \
            or next((f"{d}/{lib}" for d in libdirs if f"{d}/{lib}" in stock), None)
        if cand is None or cand in included:
            continue
        if built_from_source(lib) and lib[:-3] not in forced_names:
            continue
        deps.append(cand)
        included.add(cand)
        todo.append(cand)
if deps:
    sections.append(("Dependencies (auto)", sorted(deps)))

with open(OUT, "w") as f:
    f.write("## All proprietary files from this list are from the stock\n")
    f.write("## TB710FU_ROW_OPEN_USER_Q00002.0_W_ZUI_17.5.10.362_ST_260719 firmware.\n")
    for title, lines in sections:
        f.write(f"\n# {title}\n")
        f.write("\n".join(lines) + "\n")
print(f"{len(included)} entries in {len(sections)} sections; {len(deps)} auto deps; {len(auto)} rc/vintf")
