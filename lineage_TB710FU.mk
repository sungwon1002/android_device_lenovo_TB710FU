#
# Copyright (C) 2026 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

# Inherit from those products. Most specific first.
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit_only.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base.mk)

# Inherit from TB710FU device
$(call inherit-product, device/lenovo/TB710FU/device.mk)


# Inherit some common Evolution X stuff.
$(call inherit-product, vendor/lineage/config/common_full_tablet_wifionly.mk)

PRODUCT_NAME := lineage_TB710FU
PRODUCT_DEVICE := TB710FU
PRODUCT_MANUFACTURER := Lenovo
PRODUCT_BRAND := Lenovo
PRODUCT_MODEL := TB710FU
PRODUCT_CHARACTERISTICS := nosdcard,tablet

PRODUCT_GMS_CLIENTID_BASE := android-lenovo

PRODUCT_BUILD_PROP_OVERRIDES += \
    DeviceName=TB710FU \
    DeviceProduct=TB710FU \
    SystemDevice=TB710FU \
    SystemName=TB710FU
