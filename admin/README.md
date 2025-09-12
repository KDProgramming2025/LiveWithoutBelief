# Admin Panel Deployment

Public URL: https://aparat.feezor.net/LWB/Admin
Static root on server: /var/www/LWB/Admin
API base: defaults to same-origin; set VITE_API_URL at build-time if proxying elsewhere.

Build locally:
- In `admin/web`: `npm ci && npm run build` (dist/ is the artifact)
- In `admin/api`: `npm ci && npm run build` (optional for static hosting)

Deploy:
- Copy `admin/web/dist/*` to `/var/www/LWB/Admin/` on the server.

Note: Nginx is already serving the main site; we only add static files under /LWB/Admin.