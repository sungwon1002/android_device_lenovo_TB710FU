#!/vendor/bin/sh
vendor_cmd_tool -f /vendor/etc/wifi/sar-vendor-cmd.xml -i wlan0 --START_CMD --SAR_SET --ENABLE 7 --NUM_SPECS 2 --SAR_SPEC --NESTED_AUTO  --CHAIN 0  --POW_IDX 1 --END_ATTR --NESTED_AUTO --CHAIN 1 --POW_IDX 1 --END_ATTR  --END_ATTR --END_CMD

