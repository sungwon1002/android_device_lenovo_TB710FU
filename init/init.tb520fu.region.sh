#!/vendor/bin/sh
#
# SPDX-FileCopyrightText: 2026 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#
# Region string of the running dtb: the stock ROW dtb says ROW, the LTBox one
# for PRC boards says PRC. The builds come in both variants (vendor_boot dtb),
# so the updater uses this to pick the matching package.

if grep -q -a PRC /sys/firmware/fdt; then
    region=PRC
elif grep -q -a ROW /sys/firmware/fdt; then
    region=ROW
else
    region=unknown
fi
setprop ro.vendor.tb520fu.dtb_region "$region"
