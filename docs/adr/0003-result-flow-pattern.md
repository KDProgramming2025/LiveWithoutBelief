---
status: Accepted
date: 2025-09-04
issue: LWB-21
deciders: core-team
---

# Decision: Use Flow<Result<T>> for async data exposure

# Context
Need unified success/loading/error signalling without ad-hoc sealed hierarchies per feature.

# Rationale
- Familiar API for consumers.
- Compose-friendly collection.
- Natural cancellation + backpressure.

# Consequences
Positive: uniform handling in UI. Negative: potential nesting; mitigated by extension helpers later.

# Follow-up
- [ ] Provide mapper extensions (Result.mapSuccess).
