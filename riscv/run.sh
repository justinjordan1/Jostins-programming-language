#!/bin/bash
set -e

if [ $# -ne 1 ]; then
    echo "usage: riscv/run.sh path/to/program.mexpl|program.s"
    exit 64
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NAME="$(basename "$(basename "$(basename "$1" .mexplainerth)" .mexpl)" .s)"
BUILD="$ROOT/riscv/build"
ARCH="-march=rv64imafd_zicsr -mabi=lp64d"

mkdir -p "$BUILD" "$ROOT/out"
STAMP="$ROOT/out/.javac-stamp"
if [ ! -f "$STAMP" ] || [ ! -f "$ROOT/out/src/Compiler/Compile.class" ] || [ -n "$(find "$ROOT/src" -name '*.java' -newer "$STAMP" | head -1)" ]; then
    javac -nowarn -d "$ROOT/out" $(find "$ROOT/src" -name '*.java') 2>&1 | grep -v '^Note:' || true
    touch "$STAMP"
fi

case "$1" in
    *.s) [ "$1" = "$BUILD/$NAME.s" ] || cp "$1" "$BUILD/$NAME.s" ;;
    *) java -cp "$ROOT/out" src.Compiler.Compile "$ROOT/riscv/runtime.mexplainerth" "$1" "$BUILD/$NAME.s" ;;
esac

if [ ! -f "$BUILD/runtime.o" ] || [ "$ROOT/riscv/runtime.s" -nt "$BUILD/runtime.o" ]; then
    riscv64-elf-as $ARCH -o "$BUILD/runtime.o.$$" "$ROOT/riscv/runtime.s"
    mv "$BUILD/runtime.o.$$" "$BUILD/runtime.o"
fi
riscv64-elf-as $ARCH -o "$BUILD/$NAME.o" "$BUILD/$NAME.s"
riscv64-elf-ld --no-warn-rwx-segments -T "$ROOT/riscv/link.ld" -o "$BUILD/$NAME" "$BUILD/runtime.o" "$BUILD/$NAME.o"

qemu-system-riscv64 -machine virt -m 1G -bios none -nographic -kernel "$BUILD/$NAME"
