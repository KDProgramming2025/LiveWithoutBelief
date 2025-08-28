Title: chore: add Apache-2.0 LICENSE, NOTICE and archive backlog txt

Summary:
- Add Apache 2.0 LICENSE and NOTICE.
- Archive `docs/backlog-moscow-jira.txt` into `docs/archived/`.
- Remove references to CONTRIBUTING/Code of Conduct in `docs/backlog-moscow-jira.json`.

What changed:
- `LICENSE` (Apache-2.0)
- `NOTICE`
- `docs/backlog-moscow-jira.json` (text updates removing references)
- `docs/archived/backlog-moscow-jira.txt` (archived copy)

Why:
- Prepare the repository for open-source release and remove unused contributor docs.

Testing:
- Ran `python tools\validate_backlog.py` — result: ALL_OK

Notes / Manual steps for reviewer:
- LICENSE contains full Apache-2.0 text; replace year/owner in NOTICE if needed.
- CI pipelines untouched; GitHub/GitLab workflows will run on this branch.

Suggested reviewers: @KDProgramming2025

Merge checklist:
- [ ] Confirm LICENSE year/owner
- [ ] Confirm no further contributor docs need removal
- [ ] Approve and merge into `main`
