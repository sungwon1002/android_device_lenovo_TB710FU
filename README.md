# Lenovo Yoga Tab Plus (TB710FU) — PixelOS device tree

Unofficial device tree for building PixelOS (Android 17, `seventeen`) for the
Lenovo Yoga Tab Plus / YOGA Pad Pro (TB710FU, Qualcomm Snapdragon 8 Gen 3).

| | |
|---|---|
| SoC | Qualcomm SM8650 (pineapple) |
| Kernel | GKI `6.1.138-android14-11`, built from source |
| Display | 2944×1840 dual-DSI, natively landscape, density 340 |
| Stock firmware | `ZUI_17.5.10.362_260719_ROW` |
| Shipping API | 34 |

Status: used daily. SELinux enforcing, dm-verity on, and the bootloader can be
relocked (see "Verified boot"). Widevine L1 (Netflix HD), Play Integrity
BASIC.

## Downloads

Latest build: see [Releases](https://github.com/wnduddld0513/android_device_lenovo_TB710FU/releases/),
installation steps in the release notes. Files are on
[SourceForge](https://sourceforge.net/projects/pixelos-unofficial-tb520fu/files/seventeen/).
Pick the region of your device: ROW and PRC only differ in the device tree
(dtb) and the signed images that carry it.

The `.zip` installs from TWRP or the PixelOS recovery; the `ltbox_*.7z` is a
firmware package for LTBox (EDL). Both keep the user data when updating an
installed build; coming from the stock firmware or another ROM, format data.
Installed builds that include the optional customizations also update
themselves (Settings > System > System update).

## Repositories

| Path | Repository | Contents |
|---|---|---|
| `device/lenovo/TB710FU` | `android_device_lenovo_TB710FU` | this tree |
| `kernel/lenovo/TB710FU` | `android_kernel_lenovo_TB710FU` | Android common kernel `android14-6.1` at `2ecae636cf9b` (the source of the stock GKI kernel) plus the Qualcomm UAPI headers |
| `vendor/lenovo/TB710FU` | `android_vendor_lenovo_TB710FU` | proprietary blobs, plus the stock vendor kernel modules, dtb and dtbo in `kernel/` (Git LFS for files over 50 MB) |
| `vendor/lenovo/TB710FU-custom` | `android_vendor_lenovo_TB710FU-custom` | optional customizations, see below |

`vendor/lenovo/TB710FU-custom` is optional. It holds the maintainer
additions on top of PixelOS - Lenovo Notes, the per-app game performance
profiles, the Play Integrity Fix switch, the OTA
updater with its publishing tools and the default live wallpaper - with
their app ("Custom features"), overlays, blobs, patches and a small
system_server extension. Without it this tree builds a plain PixelOS for
the device: no such app, no updater, no game performance enforcement and
the device tree's own TB710FUParts (Lenovo features) as the only settings
app. `patches/apply.sh` reverts the repository's patches automatically when
it is removed.

`tools/local_manifest.xml` lists them; `lineage.dependencies` does the same
for roomservice.

## Kernel

The stock firmware runs Google's GKI build of `android14-6.1`
(`6.1.138-android14-11-g2ecae636cf9b-ab14676408`). This tree builds the same
source with `gki_defconfig` (clang r547379; GKI uses r487747c, newer clang
fails on this kernel). The 60 GKI modules go to system_dlkm, signed with a
key generated for each build. Lenovo/Qualcomm only ship the vendor modules
(vendor_boot, vendor_dlkm, all unsigned) and the device trees; their source
is not published at this version, so they come from the stock firmware
(`vendor/lenovo/TB710FU/kernel/`). Only `android14-6.1` updates keep working
with them (stable KMI).

Source: [kernel/common](https://android.googlesource.com/kernel/common/+/2ecae636cf9be43fdfe04adb25b2c2987838955a),
Lenovo's release: https://support.lenovo.com/us/en/solutions/ht511330-lenovo-open-source-portal

## Getting the source

Git LFS is needed for two blobs in the vendor repository (and, with the
optional customizations, for the wallpaper APK and Lenovo Notes there).

```bash
sudo apt install git-lfs && git lfs install
mkdir pixelos && cd pixelos
repo init -u https://github.com/PixelOS-AOSP/android_manifest -b seventeen --git-lfs
mkdir -p .repo/local_manifests
# copy tools/local_manifest.xml to .repo/local_manifests/TB710FU.xml and
# set fetch= to the GitHub account hosting the four repositories
repo sync -c -j$(nproc)
repo forall device/lenovo/TB710FU vendor/lenovo/TB710FU -c git lfs pull
```

## Building

The PixelOS source needs a few patches (see `patches/apply.sh`). They are
applied with `git apply` only, nothing is committed, so `repo sync` keeps
working; run the script again after every sync.

```bash
bash device/lenovo/TB710FU/patches/apply.sh
source build/envsetup.sh
breakfast TB710FU user
m pixelos
```

The script also runs `vendor/lenovo/TB710FU-custom/patches/apply.sh` when
the customizations repository is synced, and reverts its patches when it is
gone.

The build helper selects the `user` variant and keeps ADB authentication
configured on by default. `WITH_ADB_INSECURE=true` explicitly requests the
insecure ADB setting; unset, empty and `false` values keep authentication on.
The AVB and app signing keys stay as configured below. `user` builds exclude
debug tools, ADB root and the OTA `addon.d` preservation path.

Or unattended, with the log in `build.log` and the result in `build.status`:

```bash
setsid nohup device/lenovo/TB710FU/tools/build.sh > /dev/null 2>&1 < /dev/null &
```

## OTA publishing

Belongs to the optional customizations repository
(`vendor/lenovo/TB710FU-custom`); see its README for the SourceForge folder
layout, its `tools/ota_json.py` and the incremental OTA steps.

## Installing

Sideload the OTA package (`out/target/product/TB710FU/PixelOS_TB710FU-*.zip`)
from the PixelOS recovery: Apply update > Apply from ADB, then
`adb sideload <zip>`. It installs to the other slot, like any A/B update.

If data has to be wiped (first install, or a change of signing keys), format
data **before** sideloading. Formatting after the sideload also wipes the
update snapshot in `/metadata`, and the new slot does not boot.

The pvmfw image is part of the package: with dm-verity on, the bootloader
checks it through vbmeta (stock `pvmfw.img` in the vendor repository, added
with `--include_descriptors_from_image`).

## Layout

Based on the LineageOS OnePlus Pad 2 (`caihong`) and `oneplus/sm8650-common`
trees, merged into one tree with the OnePlus-specific parts removed.

- `init/`, `vintf/`, `sepolicy/`, `overlay/` — from the stock firmware
  (`LapisRowFrameworksOverlay`, `LapisRowWifiResOverlay`,
  `manifest_pineapple.xml` without IMS/DPM, the device is Wi-Fi only).
- `health/` — QTI health HAL copy that ignores the pen charger (`wls_tx`).
- `parts/` — TB710FUParts, "Lenovo features" in Settings > System: charging
  modes, white balance strength, memory extension (zram writeback), pen
  settings, and the physical keyboard page of the stock settings (stock
  strings copied by `tools/lenovo_keyboard_strings.py`). The game performance
  page and the Play Store identity moved to "Custom features"
  (`vendor/lenovo/TB710FU-custom`).
- `input/` — `tb520fu-input.jar`, loaded into system_server as a
  DeviceKeyHandler: Lenovo pen (attach, pairing, battery, writing haptics,
  buttons), keyboard keys, charging modes and double tap to wake, ported from
  the stock ZUI services (see `input/NOTICE`). Also the converted Lenovo
  keylayouts.
- `lenovo/PenService/` — the stock PenService with a compat dex for APIs that
  changed in Android 17.
- `patches/` — PixelOS source patches, applied by `patches/apply.sh`.
- `tools/bringup/` — scripts used to generate `proprietary-files.txt` and the
  props from a stock dump (`TB710FU_STOCK`, default `~/tb520fu`).

## Verified boot

The stock firmware is signed with the public AOSP `testkey_rsa4096` (the flaw
LTBox uses), and this tree signs the same way: recovery is chained at
location 1, vbmeta_system at 2 and boot at 3. The device has no
`vbmeta_vendor`. dm-verity is on, so after checking that a build boots
unlocked, `fastboot flashing lock` (wipes data) gives a locked, green boot.

Apps are signed with the AOSP test keys, so anyone can build compatible
updates. Switching to other keys later needs a data wipe.

## Extracting blobs

```bash
./extract-files.py <dump>
```

`<dump>` is an extracted stock firmware containing `vendor/`, `odm/`,
`system_ext/` and `product/`. Not needed when the vendor repository is
synced.
