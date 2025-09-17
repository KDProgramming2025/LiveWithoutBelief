# Copilot TODO

- [x] Wire admin Users view to backend
	- Implement fetch to `/v1/admin/users` with search and render table with delete action.
- [ ] Provide config checklist
- [ ] Investigate Google Sign-In failure (ApiException 7)
- [ ] Migrate existing usernames to full email
	- The upsert path updates username on next login; consider a one-off SQL migration to update existing users where username != email and email is not null.
- [x] Deploy Menu feature to VPS
	- Ensure server/package.json includes multer and @types/multer
	- Install deps on VPS, build, restart systemd service
- [ ] Smoke test Admin Menu end-to-end
	- Verify GET/POST/DELETE /v1/admin/menu via UI; icon files visible under /admin/ui/uploads
 - [ ] Verify uploaded icons preserve extension and render inline
Notes:
- Admin UI static files are served from Express at `/admin/ui`; updating the repo on VPS is sufficient—no restart strictly required.
