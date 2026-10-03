#!/usr/bin/env bash
#
# SPDX-FileCopyrightText: 2026 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#
# Apply the source patches and build, logging to <top>/build.log with the
# result in <top>/build.status (RUNNING / EXIT=n), so it can run unattended:
#
#   setsid nohup device/lenovo/TB710FU/tools/build.sh > /dev/null 2>&1 < /dev/null &
#   tail -f build.log
#
# usage: [JOBS=n] tools/build.sh [make target, default pixelos] [extra make args]
# JOBS defaults to 8, which builds faster here than all cores (less memory stall).
# SHOW_LOG=1 also prints the build output (it always goes to build.log).
set -uo pipefail
TREE=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
TOP=$(cd "$TREE/../../.." && pwd)
cd "$TOP"

bash "$TREE/patches/apply.sh" "$TOP" || exit 1

# common.mk uses ifdef, so even the nonempty value "false" disables ADB auth.
# Keep authentication enabled unless explicitly requested with "true".
if [ "${WITH_ADB_INSECURE:-false}" = true ]; then
    export WITH_ADB_INSECURE=true
else
    unset WITH_ADB_INSECURE
fi
source build/envsetup.sh > /dev/null
breakfast TB710FU user > /dev/null 2>&1 || { echo "breakfast failed" >&2; exit 1; }

echo RUNNING > build.status
if [ "${SHOW_LOG:-0}" = 1 ]; then
    # Show the output as well (interactive use); build.log is still written
    m "${1:-pixelos}" -j"${JOBS:-8}" -k 0 "${@:2}" 2>&1 | tee build.log
    status=${PIPESTATUS[0]}
else
    m "${1:-pixelos}" -j"${JOBS:-8}" -k 0 "${@:2}" > build.log 2>&1
    status=$?
fi
echo "EXIT=$status" > build.status
cat build.status
exit "$status"
