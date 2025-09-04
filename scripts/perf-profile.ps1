Write-Host "[perf] Cold build timing..."
Measure-Command { ./gradlew clean :app:assembleDebug | Out-Null } | ForEach-Object { Write-Host ("Cold build: {0}s" -f ($_.TotalSeconds.ToString("F2"))) }
Write-Host "[perf] Warm build timing..."
Measure-Command { ./gradlew :app:assembleDebug | Out-Null } | ForEach-Object { Write-Host ("Warm build: {0}s" -f ($_.TotalSeconds.ToString("F2"))) }
Write-Host "[perf] Unit tests timing..."
Measure-Command { ./gradlew testDebugUnitTest | Out-Null } | ForEach-Object { Write-Host ("Tests: {0}s" -f ($_.TotalSeconds.ToString("F2"))) }
