#!/bin/bash
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
rm -rf out/src
javac -nowarn -d out $(find src -name '*.java') 2>&1 | grep -v '^Note:'
if [ ${PIPESTATUS[0]} -ne 0 ]; then echo "FAIL: java did not compile"; exit 1; fi

work=$(mktemp -d)
failed=0
for test in testInput/*.mexplainerth testInput/*.mexpl; do
    [ -e "$test" ] || continue
    name=$(basename "$(basename "$test" .mexplainerth)" .mexpl)
    case $name in canvasTest|metalRayTracer) continue;; esac

    perl -e 'alarm 60; exec @ARGV' java -Djava.awt.headless=true -cp out src.C10H15N "$test" 2>&1 \
        | sed 's/\x1b\[[0-9;]*m//g' | grep -v '^Running' > "$work/$name.interp"
    perl -e 'alarm 120; exec @ARGV' riscv/run.sh "$test" 2>&1 \
        | sed 's/\x1b\[[0-9;]*m//g' > "$work/$name.riscv"

    for kind in interp riscv; do
        if [ "$1" = "--save" ]; then
            cp "$work/$name.$kind" "tests/expected/$name.$kind"
        elif ! diff -q "tests/expected/$name.$kind" "$work/$name.$kind" > /dev/null; then
            echo "FAIL: $name ($kind)"
            diff "tests/expected/$name.$kind" "$work/$name.$kind" | head -6
            failed=1
        fi
    done
done

if [ "$1" = "--save" ]; then echo "saved"; exit 0; fi
if [ $failed -eq 0 ]; then echo "ALL PASS"; fi
exit $failed
