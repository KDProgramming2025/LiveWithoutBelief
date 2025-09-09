# LWB-41: Failure triage workflow

Scope
- Configure workflow for triaging test failures with hermetic environment utilities.

Changes
- Hermetic tests: force UTC timezone and UTF-8 encoding for all Test tasks across subprojects; run in headless mode.
- Gradle Test Retry: enabled via org.gradle.test-retry plugin; active only on CI with maxRetries=1 and failOnPassedAfterRetry=false.
- Triage script: scripts/triage_junit.py parses JUnit XML into build/reports/triage/summary.md and failures.txt.
- CI wiring: Android workflow sets CI=true, runs unit tests, executes triage step, and uploads triage artifacts per JDK matrix.
- Docs: expanded Failure triage section in docs/test-strategy.md.

How to verify (local)
1. Run tests to generate JUnit XML:
   - Windows: ./gradlew.bat :app:testDebugUnitTest
2. Generate summary:
   - Windows: python scripts/triage_junit.py app\\build\\test-results\\testDebugUnitTest\\*.xml
3. Open build/reports/triage/summary.md and confirm totals/failing tests list.

CI expectations
- On PRs, the job uploads triage-report-jdkXX with summary.md and failures.txt.
- If a test passes on retry, CI remains green but the test will be listed as flaky in the summary.

Notes
- This implements the “hermetic” part by standardizing timezone/encoding. Additional hermetic utilities (fake clock, IO) are already noted in docs/test-strategy.md and used where applicable.