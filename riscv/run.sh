#!/bin/bash
set -e

if [ $# -ne 1 ]; then
    echo "usage: riscv/run.sh path/to/program.mexplainerth"
    exit 64
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NAME="$(basename "$1" .mexplainerth)"
BUILD="$ROOT/riscv/build"
ARCH="-march=rv64imafd_zicsr -mabi=lp64d"

mkdir -p "$BUILD" "$ROOT/out"
javac -nowarn -d "$ROOT/out" $(find "$ROOT/src" -name '*.java') 2>&1 | grep -v '^Note:' || true
java -cp "$ROOT/out" src.Compiler.Compile "$ROOT/riscv/runtime.mexplainerth" "$1" "$BUILD/$NAME.s"

riscv64-elf-as $ARCH -o "$BUILD/runtime.o" "$ROOT/riscv/runtime.s"
riscv64-elf-as $ARCH -o "$BUILD/$NAME.o" "$BUILD/$NAME.s"
riscv64-elf-ld --no-warn-rwx-segments -T "$ROOT/riscv/link.ld" -o "$BUILD/$NAME" "$BUILD/runtime.o" "$BUILD/$NAME.o"

qemu-system-riscv64 -machine virt -m 1G -bios none -nographic -kernel "$BUILD/$NAME"
