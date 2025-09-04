#!/usr/bin/env bash
set -euo pipefail
echo "[perf] Cold build timing..."
time ./gradlew clean :app:assembleDebug > /dev/null
echo "[perf] Jetifier / configuration cache warm build timing..."
time ./gradlew :app:assembleDebug > /dev/null
echo "[perf] Run unit tests timing..."
time ./gradlew testDebugUnitTest > /dev/null
