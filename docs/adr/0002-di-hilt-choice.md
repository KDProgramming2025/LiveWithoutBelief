---
status: Accepted
date: 2025-09-04
issue: LWB-18
deciders: core-team
---

# Decision: Use Hilt for Dependency Injection

# Context
Need standardized DI across multi-module Android app with minimal boilerplate and good tooling.

# Rationale
- First-class Android support (ViewModel, WorkManager, Navigation entry points).
- Aggregating compilation for fast incremental builds vs manual Dagger setup overhead.
- Consistent hiring signal (industry norm).

# Alternatives
- Manual Service Locator: simple but unsafe at scale.
- Koin: runtime resolution, slower cold start.
- Plain Dagger: more boilerplate for components.

# Consequences
Positive: rapid module onboarding, scoping clarity. Negative: kapt overhead (mitigated later with KSP migration path if needed).

# Follow-up
- [ ] Evaluate switch to Dagger + Anvil or KSP when stable.
