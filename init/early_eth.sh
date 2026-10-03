#! /vendor/bin/sh
#=============================================================================
# Copyright (c) 2024 Qualcomm Technologies, Inc.
# All Rights Reserved.
# Confidential and Proprietary - Qualcomm Technologies, Inc.
#=============================================================================

DIR_THIN=/sys/devices/platform/soc/23049000.qcom,ethernet/net/eth1
DIR_EMAC1_PASSTRU=/sys/devices/platform/soc/23000000.qcom,ethernet/net/eth2

Thin_driver () {
	echo "Thin_driver start" > /dev/kmsg
	while true
	do
		if [ -d "$DIR_THIN" ]
		then
			echo 1 > /proc/sys/net/ipv4/conf/all/arp_ignore
			echo 1 > /proc/sys/net/ipv4/conf/default/arp_ignore
			echo "Thin driver /sys/devices/.../eth1 is created." > /dev/kmsg
			ifconfig eth1 192.168.6.3 up
			break
		fi

		sleep 0.05
	done
	echo "Thin_driver end" > /dev/kmsg
}

Passthru_driver () {
	echo "Passthru_driver start" > /dev/kmsg
	while true
	do
		if [ -d "$DIR_EMAC1_PASSTRU" ]
		then
			echo "Pass through driver /sys/devices/.../eth2 is created." > /dev/kmsg
			ifconfig eth2 192.168.7.3 up
			break
		fi

		sleep 0.05
	done
	echo "Passthru_driver end" > /dev/kmsg
}

Thin_driver & Passthru_driver

