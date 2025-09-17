# Copilot TODO

- [x] Wire admin Users view to backend
	- Implement fetch to `/v1/admin/users` with search and render table with delete action.
- [ ] Deploy server updates to VPS
	- Pull latest from `github` remote on VPS for `/var/www/LWB` and ensure static admin UI updated.
- [ ] Smoke test Users view
	- Visit https://aparat.feezor.net/LWB/Admin/, login, verify list/search/delete work.

Notes:
- Admin UI static files are served from Express at `/admin/ui`; updating the repo on VPS is sufficient—no restart strictly required.
