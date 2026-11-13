#!/bin/bash
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SITE="$ROOT/docs"
GEN="$ROOT/web/build/gen"

rm -rf "$GEN"
mkdir -p "$GEN"
cp -R "$ROOT/src" "$GEN/"
rm -rf "$GEN/src/Editor/EditorServer.java" "$GEN/src/Compiler"
cp -R "$ROOT/web/java/src" "$GEN/"
sed -i '' -E 's/System\.exit\(([0-9]+)\);/throw new IllegalStateException("exit \1");/' "$GEN/src/C10H15N.java"
(cd "$ROOT/web" && "${MVN:-mvn}" -q -B package)

STAMP="$(cat "$ROOT/web/build/js/mexpl.js" "$ROOT/web/static.js" "$ROOT/web/worker.js" "$ROOT/editor/index.html" | shasum | cut -c1-10)"
cp "$ROOT/web/build/js/mexpl.js" "$SITE/mexpl.js"
cp "$ROOT/web/worker.js" "$SITE/worker.js"
sed "s|worker.js|worker.js?v=$STAMP|" "$ROOT/web/static.js" > "$SITE/static.js"
rm -f "$SITE/mexpl.jar"

sed -e "s|</head>|<script src=\"static.js?v=$STAMP\"></script>\n</head>|" -e 's|</head>|<style>#recordButton, label:has(> input[value=riscv]), label:has(> #showAsm) { display: none; }</style>\n</head>|' \
    -e '/<option value="suzanne">/d' -e '/<option value="room">/d' \
    -e 's|<input type="radio" name="target" value="riscv" checked>|<input type="radio" name="target" value="riscv">|' \
    -e 's|<input type="radio" name="target" value="interp">|<input type="radio" name="target" value="interp" checked>|' \
    "$ROOT/editor/index.html" > "$SITE/index.html"
