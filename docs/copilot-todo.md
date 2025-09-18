# Copilot TODO

- [x] Remove `#menu-uploading` element and references in `admin/web/assets/js/views/menu.js`.
- [x] Redesign `#menu-form` layout and styles; add `.menu-form` CSS in `admin/web/assets/css/forms.css` and update form markup in `menu.js`.
- [ ] Commit changes and push to `github` remote on branch `feature/LWB-92-admin-ui`.
- [ ] Sync server repo at `/var/www/www/LWB/` using one-command-per-run flow, pulling latest from Git and ensuring admin web reflects changes.

Notes:
- Loading state now uses the submit button (`#menu-submit.loading`) instead of the removed uploading badge.
- Icon preview and clear behavior preserved with updated classes.
