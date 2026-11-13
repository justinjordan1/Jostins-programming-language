#!/bin/bash
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
mkdir -p out
javac -nowarn -d out $(find src -name '*.java') 2>&1 | grep -v '^Note:'
java -cp out src.Editor.EditorServer "$@"
