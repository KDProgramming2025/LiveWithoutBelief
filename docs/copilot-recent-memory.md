Date: 2025-09-12
Branch: feature/LWB-80-annotations-discussions (active)

Current State Summary:
- Implemented annotations/discussions end-to-end: AnnotationRepositoryImpl, domain use cases (Get/AddThreadMessage), DiscussionThreadSheet UI with Hilt VM, and ReaderScreen integration to create annotation and open thread.
- Fixed Spotless (max line length) and Compose lint (collectAsState) issues; full clean build now passes locally with lint/detekt/spotless.
- Opened PR #22; CI just started.

Immediate Next Steps:
1. Monitor PR #22 CI to green and address any failures.
2. Add minimal unit tests: repo happy path for add/get, VM send() triggers use case. Snapshot for DiscussionThreadSheet optional.
3. After merge, local git cleanup and start next Jira item.

Deferred / Future:
- Improve message type detection and support attachments picker.
- Per-user private threads UI affordances and empty states.

Keep File Lean: prune after PR merges to main.
