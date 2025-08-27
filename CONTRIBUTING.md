# Contributing

Thanks for your interest in contributing.

## Branching & Workflow
- Main branch is protected. Use feature/*, fix/*, chore/* branches.
- Create small PRs (<400 lines diff when possible).
- All PRs must pass build, ktlint, detekt, and tests.

## Commit Messages
Use conventional style (not enforced yet but recommended):
```
feat(reader): add pagination toggle
fix(repo): handle null manifest
chore(ci): bump detekt version
```

## Code Style
- Kotlin: ktlint + detekt. Run:
```
./gradlew ktlintFormat
./gradlew detekt
```

## Tests
- Prefer fast, deterministic unit tests.
- For coroutines use runTest.
- Snapshot UI: Paparazzi (no device/emulator needed).

## Adding a Module
1. Add to settings.gradle.kts
2. Provide build.gradle.kts with consistent JVM/Android settings
3. Update README if conceptual.

## Security / Privacy
No user email stored; only hashed Google `sub` planned. Do not introduce PII without discussion.

## Reporting Issues
Open GitHub Issue with:
- Description
- Steps to reproduce
- Expected vs actual
- Logs / stack traces (trim sensitive info)

## Roadmap Changes
Propose via Issue + link to rationale (performance, correctness, user value).

## License
By contributing you agree your code is Apache-2.0 licensed.

