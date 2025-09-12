# Contributing to Live Without Belief

Thanks for your interest in contributing!

## Development setup
- Android Studio Koala or newer
- JDK 17
- Run `./gradlew.bat build` on Windows PowerShell

## Workflow
- Use feature branches named `feature/<Jira-ID>-short-desc`
- Write tests first (TDD) when feasible
- Ensure `./gradlew.bat build` is green (tests, lint, detekt, spotless)
- Open a PR and link the Jira issue (Smart Commits optional)

## Coding standards
- Kotlin, Compose, MVVM, Clean Architecture
- Static analysis: Spotless (ktlint), Detekt; follow warnings
- Keep modules small and cohesive; prefer domain/use-case boundaries

## Commit messages
- Conventional style with Jira ID prefix, e.g. `LWB-87: add README`

## Code review
- Small, focused PRs
- Include screenshots for UI changes (or Paparazzi snapshots)

## Security and privacy
- Don’t commit secrets. Use env vars and CI secrets.
- Respect user privacy; avoid logging sensitive data.

## License
By contributing, you agree your changes are licensed under Apache-2.0.
