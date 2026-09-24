#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=${1:-"$ROOT/build/offline-evidence"}
GSON_JAR=${GSON_JAR:-/usr/share/java/gson.jar}
if [[ ! -f "$GSON_JAR" ]]; then
  echo 'Set GSON_JAR to an actual Gson jar (project dependency: 2.10.1). No dependency is downloaded by this script.' >&2
  exit 2
fi
mkdir -p "$OUT/classes"
java -version 2>"$OUT/java-version.txt"
find "$ROOT/core-planner/src/main/java" "$ROOT/llm-bridge/src/main/java" \
     "$ROOT/core-planner/src/test/java/org/mcsettlement/planner/baseline" \
     "$ROOT/core-planner/src/test/java/org/mcsettlement/planner/regression" \
     "$ROOT/llm-bridge/src/test/java" -name '*.java' ! -name '*JUnitTest.java' > "$OUT/sources.txt"
javac -proc:none --release 21 -cp "$GSON_JAR" -d "$OUT/classes" @"$OUT/sources.txt" 2>&1 | tee "$OUT/compile.log"
cp -r "$ROOT/core-planner/src/main/resources/"* "$OUT/classes/"
java -Xmx1g -cp "$OUT/classes:$GSON_JAR" org.mcsettlement.planner.regression.RegressionMain "$OUT" 2>&1 | tee "$OUT/tests.log"
java -cp "$OUT/classes:$GSON_JAR" org.mcsettlement.llm.BridgeRegressionMain 2>&1 | tee "$OUT/bridge-tests.log"
