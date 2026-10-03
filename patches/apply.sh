#!/usr/bin/env bash
# Source patches for PixelOS (seventeen) that cannot live in the device tree.
# Safe to re-run; re-run after every `repo sync`.
#
# usage: bash device/lenovo/TB710FU/patches/apply.sh [pixelos-source-root]
#
# Patch files are named <project path with / -> _>-NNNN-<description>.patch
# and are applied with `git apply` to the matching project. Nothing is
# committed in the PixelOS projects, so `repo sync` keeps working. A patch
# for a project the ROM does not have is skipped; a patch that no longer
# applies is reported and left out, the others are still applied, and the
# script ends with a list of them (the feature is then missing from the
# build; TB710FUParts hides the settings of missing framework patches).
#
# The optional customizations in vendor/lenovo/TB710FU-custom have their own
# patches/apply.sh, run at the end. It records what it applied in
# .tb520fu-custom-applied/ at the source root; when that repository is
# removed, this script reverts those patches first.
set -euo pipefail
PATCHES=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
TOP=${1:-$(cd "$PATCHES/../../../.." && pwd)}
cd "$TOP"
[ -f build/envsetup.sh ] || { echo "ERROR: $TOP is not a source root" >&2; exit 1; }

FAILED=()

apply_patch() { # repo-dir patch-file
    local dir=$1 patch=$2
    # Projects some ROMs do not have (e.g. packages/apps/ParanoidSense)
    if [ ! -d "$dir" ]; then
        echo "skipped (no $dir): $(basename "$patch")"
        return 0
    fi
    if git -C "$dir" apply --check "$patch" 2>/dev/null; then
        git -C "$dir" apply "$patch" && echo "applied $(basename "$patch")"
    elif git -C "$dir" apply --reverse --check "$patch" 2>/dev/null; then
        echo "already applied: $(basename "$patch")"
    else
        echo "ERROR: $(basename "$patch") does not apply to $dir, left out" >&2
        FAILED+=("$dir: $(basename "$patch")")
    fi
}

# Customizations removed: revert what vendor/lenovo/TB710FU-custom applied
# (the state is written by its patches/apply.sh), newest first.
CUSTOM="$TOP/vendor/lenovo/TB710FU-custom"
STATE="$TOP/.tb520fu-custom-applied"
if [ ! -f "$CUSTOM/patches/apply.sh" ] && [ -f "$STATE/applied.list" ]; then
    echo "vendor/lenovo/TB710FU-custom is gone, reverting its patches"
    tac "$STATE/applied.list" | while read -r dir name; do
        [ -n "$name" ] || continue
        if git -C "$dir" apply --reverse --check "$STATE/$name" 2>/dev/null; then
            git -C "$dir" apply --reverse "$STATE/$name" && echo "reverted $name"
        fi
    done
    rm -rf "$STATE"
fi

# frameworks/base
# 0001: Lenovo PenService support. Adds android.app.haptic.ZuiPenHapticManager
#       (+ IZuiPenHapticManager) registered as "zui_pen_haptic", which the
#       stock PenService uses for its haptic settings/test, and the
#       config_stylus_pen_haptic_packages array it reads the selectable
#       apps from (the service itself lives in the device jar, input/).
#       Also restores the pre-caller RotationPolicy.setRotationLockAtAngle
#       overload that PenService's EasyJot still calls.
apply_patch frameworks/base \
    "$PATCHES/frameworks_base-0001-lenovo-pen-haptic-manager.patch"
# 0002: Settings.Secure display_white_balance_strength (0-100, default 100),
#       how far white balance follows the ambient light (TB710FUParts slider).
#       Defines config_tb520fu_patch_wb_strength so TB710FUParts can detect it.
apply_patch frameworks/base \
    "$PATCHES/frameworks_base-0002-display-white-balance-strength.patch"
# 0003: android.app.keyboard.LenovoKeyboardManager (+ ILenovoKeyboardService)
#       registered as "lenovokeyboard". The stock keyboard firmware updater
#       (ZuiKeyboardUpdate) reaches the keyboard only through it; the service
#       itself lives in the device jar (input/KeyboardServiceBinder).
#       Defines config_tb520fu_patch_keyboard_manager so TB710FUParts can detect it.
apply_patch frameworks/base \
    "$PATCHES/frameworks_base-0003-lenovo-keyboard-manager.patch"
# 0004: WM Shell turns the display into desktop windowing whenever a keyboard
#       and a touchpad are attached, with no way out. Let the device turn it
#       off (stock setting enter_work_mode_from_keyboard = 0, Lenovo keyboard
#       settings) or leave it until detach (tb520fu_keyboard_desktop_mode_exited,
#       notification button from input/KeyboardDesktopMode). Also enter it
#       on request without a keyboard (tb520fu_pc_mode, PC mode tile in Parts).
#       Defines config_tb520fu_patch_desktop_opt_out so TB710FUParts can detect it.
apply_patch frameworks/base \
    "$PATCHES/frameworks_base-0004-keyboard-desktop-first-opt-out.patch"
# frameworks/native
# 0001: RefreshRateSelector: a static screen (all layers vote Min) goes to the
#       lowest mode of at least 60 Hz, not the policy minimum; the idle timer
#       still drops to 30 Hz (30 -> 60 -> 120 Hz, like stock ZUI).
apply_patch frameworks/native \
    "$PATCHES/frameworks_native-0001-static-screen-60hz-floor.patch"

# packages/apps/DolbyAtmos
# 0001: the default profile was hardcoded to Dynamic; move it into an
#       overlayable string (overlay/DolbyAtmosResTB710FU keeps Dynamic).
apply_patch packages/apps/DolbyAtmos \
    "$PATCHES/packages_apps_DolbyAtmos-0001-overlayable-default-profile.patch"
# 0002: the equalizer labelled its sliders 32 Hz-16 kHz and treated the gains
#       as 1/10 dB. The DAX bands are 47 Hz-19.7 kHz (the sliders set every
#       other one) and the gains are 1/16 dB, as in the stock Lenovo equalizer.
apply_patch packages/apps/DolbyAtmos \
    "$PATCHES/packages_apps_DolbyAtmos-0002-geq-dax-bands-and-scale.patch"

# packages/apps/ParanoidSense
# 0001: the face enrollment preview surface is a fixed portrait 240x320dp
#       box. On this landscape tablet (front camera mounted at 270) the
#       preview stays 4:3 landscape and the face was stretched; swap the
#       surface size when the display rotation cancels the sensor rotation.
apply_patch packages/apps/ParanoidSense \
    "$PATCHES/packages_apps_ParanoidSense-0001-fit-enroll-preview-to-landscape.patch"

# packages/apps/Aperture
# 0001: Aperture asks for nosensor (natural = portrait here) and rotates its
#       buttons by the device orientation. Android 17 ignores that request on
#       large screens and shows it landscape, so every icon ended up rotated
#       by 90 degrees. Compensate only what the display rotation does not.
apply_patch packages/apps/Aperture \
    "$PATCHES/packages_apps_Aperture-0001-compensate-ui-for-display-rotation.patch"

# packages/apps/Settings
# 0001: config_show_display_white_balance, so the device can hide the Display
#       white balance switch (it lives in TB710FUParts with a strength slider).
apply_patch packages/apps/Settings \
    "$PATCHES/packages_apps_Settings-0001-optional-display-white-balance-switch.patch"
# 0002: com.android.settings.PLACE_HOLDER, the stock Lenovo settings action the
#       Lenovo PenService uses to open its pen settings (stylus toolbox button).
#       On a large screen the target is opened through the Settings embedded
#       deep link, so Settings embeds it next to its two-pane home (as the
#       stock Lenovo settings app did); otherwise it opens directly.
apply_patch packages/apps/Settings \
    "$PATCHES/packages_apps_Settings-0002-lenovo-place-holder-activity.patch"

# vendor/lineage
# 0001: kernel.mk installs every kernel module it builds that is not in
#       SYSTEM_KERNEL_MODULES to vendor_dlkm, even an empty set, which
#       overwrote the modules.dep/modules.load of the prebuilt vendor modules
#       and failed on their load list. Skip that step when nothing is left
#       (TB710FU builds only the GKI modules; vendor modules are prebuilt).
apply_patch vendor/lineage \
    "$PATCHES/vendor_lineage-0001-kernel-skip-empty-vendor-module-install.patch"
# 0002: android14-6.1 now exports struct sched_param in linux/sched/types.h,
#       which clashes with bionic's <sched.h> in vendor code using the
#       generated kernel headers. Drop it when cleaning the headers.
apply_patch vendor/lineage \
    "$PATCHES/vendor_lineage-0002-clean-sched-param-from-kernel-headers.patch"

# Optional customizations (vendor/lenovo/TB710FU-custom), when present
if [ -f "$CUSTOM/patches/apply.sh" ]; then
    bash "$CUSTOM/patches/apply.sh" "$TOP" \
        || FAILED+=("vendor/lenovo/TB710FU-custom/patches/apply.sh")
fi

if [ ${#FAILED[@]} -gt 0 ]; then
    echo
    echo "WARNING: these patches were left out:" >&2
    printf '  %s\n' "${FAILED[@]}" >&2
fi
exit 0
