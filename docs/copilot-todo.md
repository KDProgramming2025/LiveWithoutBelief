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

## Articles feature TODO

- [x] Backend: ArticleService with DOCX conversion
	- Ensure dirs under `/var/www/LWB/admin`; convert .docx with `mammoth`; generate `index.html`, `styles.css`, `script.js`; maintain `articles.json`.
- [x] Backend: Admin routes for articles
	- `GET /v1/admin/articles` list; `POST /v1/admin/articles` multipart (docx, cover, icon).
- [x] Server static mount for articles
	- Serve `/admin/ui/web/articles` from `/var/www/LWB/admin/web/articles`.
- [x] Admin UI: Articles list and upload form
	- Grid cards showing title/label/icon, Open link; client-side duplicate warning; uploading indicator.
- [ ] Add Nginx check for Admin web articles path
	- Should work via existing `/LWB/Admin/` proxy; verify `/LWB/Admin/web/articles/...` loads.
- [ ] Deploy Articles changes to VPS
	- Pull latest; install deps (mammoth) using isolated Node; build; restart service. (PARTIALLY DONE for later enhancements)
- [ ] Smoke test Articles upload
	- Upload a sample .docx; verify conversion, manifest update, and public index loads.
 - [x] OLE media extraction
	 - Extract MP4/MP3 from word/embeddings OLE .bin; inline placement; deploy and verify.
 - [x] Remove OLE placeholder icon images
	 - Strip image-only blocks that Mammoth renders for OLE icons immediately before inline media placeholders so they don't show above audio/video.

## YouTube Embeds

- [x] Detect YouTube embeds inside DOCX drawing hyperlinks (a:hlinkClick rels -> youtube)
- [x] Inject placeholders [[LWB_YT:videoId]] into document.xml
- [x] Post-conversion iframe replacement in ArticleService
- [x] Deploy YouTube embed backend changes (build + restart service)
- [ ] Smoke test YouTube embed end-to-end on VPS
  - Upload DOCX containing an embedded YouTube drawing; verify iframe renders in served article HTML
- [ ] Document authoring instructions for embedding YouTube (how to insert link icon in Word) in docs
- [ ] Add automated test for YouTube placeholder replacement (unit test around injector + HTML replacement)

## Follow-up / Nice-to-haves

- [ ] Extend OLE extraction to support additional containers (e.g., AVI, OGG) via header signatures
- [ ] Add configurable iframe dimensions & responsive wrapper (CSS aspect-ratio)
- [ ] Add language/i18n scaffold (resource bundle structure, locale switch) pre-feature freeze
- [ ] Add end-to-end upload test (using a small synthetic DOCX with media + YouTube) in tests/services
