#!/bin/bash
# Loop: soong analysis -> collect undefined modules -> add stock libs -> re-extract.
S=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
TREE=$(cd "$S/../.." && pwd)
TOP=$(cd "$TREE/../../.." && pwd)
STOCK=${TB710FU_STOCK:-$HOME/tb520fu}
F=$STOCK/force_libs.txt
touch $F
cd $TOP && source build/envsetup.sh >/dev/null 2>&1 && breakfast TB710FU user >/dev/null 2>&1
python3 $S/mkblobs.py $TREE/proprietary-files.txt >/dev/null && (cd $TREE && ./extract-files.py $STOCK >/dev/null 2>&1)
for i in $(seq 1 25); do
    m nothing > $TOP/analysis.log 2>&1 && { echo "iter $i: analysis OK"; exit 0; }
    missing=$(grep -oE 'depends on undefined module "[^"]+"' $TOP/analysis.log | sed -E 's/.*"(.*)"/\1/' | sort -u)
    if [ -z "$missing" ]; then echo "iter $i: other error"; grep -E "^error|error:" $TOP/analysis.log | head -5; exit 1; fi
    new=0
    for mdl in $missing; do
        if grep -qxF "$mdl" $F; then continue; fi
        if ls $STOCK/{vendor,system_ext,odm,product}/lib64/$mdl.so >/dev/null 2>&1; then echo "$mdl" >> $F; new=1; echo "iter $i: + $mdl"
        else echo "iter $i: NOT IN STOCK $mdl"; grep -m3 "undefined module \"$mdl\"" $TOP/analysis.log; fi
    done
    [ $new = 0 ] && { echo "iter $i: no progress"; exit 1; }
    python3 $S/mkblobs.py $TREE/proprietary-files.txt >/dev/null && (cd $TREE && ./extract-files.py $STOCK >/dev/null 2>&1)
done
