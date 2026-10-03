# Lenovo Xiaoxin Pad Pro GT (TB710FU) — Evolution X device tree

Unofficial device tree for building Evolution X 12.x (Android 17, branch `cnb`)
for the Lenovo Xiaoxin Pad Pro GT / Yoga Tab 11.1 AI (TB710FU, Snapdragon 8 Gen 3).

> **Status: work in progress. Boot has not been verified yet.**
> Do not flash builds from this tree unless you can restore the stock firmware.

| | |
|---|---|
| SoC | Qualcomm SM8650 (pineapple) |
| Kernel | GKI `android14-6.1` (KMI 11), built from source |
| Display | NT36532 dual-DSI DSC, 3200×2000, `ORIENTATION_270` (reported as 2000×3200), density 400, 30/60/90/120/144 Hz |
| Touch | Novatek NVT-ts (SPI), pen support |
| Audio amp | Awinic aw882xx |
| Stock firmware | `TB710FU_CN_OPEN_USER_Q00019.0_A16_ZUXOS_1.5.04.470_ST_260625` (PRC) |
| Board API level | 34 |
| Super partition | 20401094656 bytes (group 19318964224) |

## Based on

This tree is a port of the TB520FU (Lenovo Yoga Tab Plus) tree by
[wnduddld0513](https://github.com/wnduddld0513/android_device_lenovo_TB520FU),
which is itself based on the LineageOS OnePlus Pad 2 (`caihong`) and
`oneplus/sm8650-common` trees. Copyright headers of the original files are kept.

Changes from the TB520FU tree:

- Display: density 400, boot animation size 2000×3200, `ORIENTATION_270`.
- Touch firmware: single `novatek_ts_fw.bin` instead of BOE/Tianma variants.
- Blobs: TB520FU-only panel, sensor, camera (kirby/lapis) and Goodix fingerprint
  files removed; TB710FU (topaz) display, sensor and camera files added.
- `init/`: replaced with the TB710FU stock versions; `fstab.qcom` without `lenovocust`.
- Super partition and group size set to the TB710FU values.
- Lenovo props: `project=topaz`, market name, `not_support_vibrator=true`.
- Product makefile `lineage_TB710FU.mk` for Evolution X; PixelOS-only `DolbyAtmos` removed.

## Repositories

| Path | Repository | Branch |
|---|---|---|
| `device/lenovo/TB710FU` | `sungwon1002/android_device_lenovo_TB710FU` | `cnb` |
| `vendor/lenovo/TB710FU` | `sungwon1002/android_vendor_lenovo_TB710FU` | `cnb` |
| `kernel/lenovo/TB710FU` | `wnduddld0513/android_kernel_lenovo_TB520FU` (used unchanged) | `seventeen` |

The vendor repository holds the proprietary blobs plus the stock dtb, dtbo and
vendor kernel modules in `kernel/`. Two files are stored with Git LFS.

## Kernel

The stock firmware runs `6.1.128-android14-11-g5c2cea985a84`. This tree builds
the TB520FU kernel repository (`android14-6.1` at `2ecae636cf9b`, 6.1.138).
Both are the same KMI generation, so the stock vendor modules are expected to
load (stable KMI). This is not verified on the device yet; if modules fail to
load, switch the kernel to `5c2cea985a84`.

## Getting the source

```bash
sudo apt install git-lfs && git lfs install
mkdir evox && cd evox
repo init -u https://github.com/Evolution-X/manifest -b cnb --git-lfs
mkdir -p .repo/local_manifests
curl -L https://raw.githubusercontent.com/sungwon1002/android_device_lenovo_TB710FU/cnb/tools/local_manifest.xml \
  -o .repo/local_manifests/TB710FU.xml
repo sync -c -j$(nproc) --force-sync --no-clone-bundle --no-tags
repo forall vendor/lenovo/TB710FU -c git lfs pull
```

## Building

```bash
source build/envsetup.sh
lunch lineage_TB710FU-cp2a-userdebug
m evolution
```

32 GB of RAM is recommended. The `patches/` directory contains PixelOS patches
from the TB520FU tree; they are not applied or tested on Evolution X, so the
Lenovo pen and keyboard features may not work.

## Extracting blobs

```bash
./extract-files.py <dump>
```

`<dump>` is an extracted stock firmware containing `vendor/`, `odm/`,
`system_ext/`, `product/` and `pvmfw.img`. A full extraction clears the vendor
repository, so restore the stock kernel files afterwards with
`git -C ../../../vendor/lenovo/TB710FU checkout -- kernel`.

## Verified boot

Inherited from the TB520FU tree: images are signed with the public AOSP
`testkey_rsa4096`, like the stock firmware. Not verified on TB710FU yet.
Do not relock the bootloader before a build is confirmed to boot.

## Credits

- [wnduddld0513](https://github.com/wnduddld0513) for the TB520FU tree and kernel
- The LineageOS project
- The Evolution X project
