- 2025-09-18: Admin UI skeuomorphic redesign
	- Implemented textured matte surfaces, hidden light bands, bevel edges, and realistic raised/pressed shadows.
	- Updated files:
		- `admin/web/assets/css/tokens.css` (new tokens: noise texture, bevel colors, elevation presets; refined palettes for light/dark)
		- `admin/web/assets/css/base.css` (matte cards/tables)
		- `admin/web/assets/css/forms.css` (pressed inputs, raised buttons)
		- `admin/web/assets/css/menu.css` (inset icon wells, raised cards)
		- `admin/web/assets/css/sidebar.css` (matte sidebar with hidden lights; refined nav states)
		- `admin/web/assets/css/progress.css` (pressed track with glossy bar)
		- `admin/web/assets/styles.css` (minimized to avoid overrides)
	- Local smoke test: Opened `admin/web/index.html` successfully.
	- Git: committed and pushed to `feature/LWB-92-admin-ui`.
	- Server sync (one command per cycle via linux_commands.sh):
		1) `git fetch --all`
		2) `git checkout feature/LWB-92-admin-ui`
		3) `git pull --ff-only github feature/LWB-92-admin-ui`
	- Next visual refinements (optional):
		- Add subtle edge highlights on focusable components using ::before top edge.
		- Introduce micro-animations for press/release depth with scale/brightness.
		- Fine-tune noise intensity for ultra-low DPI devices.

	- 2025-09-18: Palette and LED updates
		- Lightened dark theme backgrounds and panels for better shadow visibility.
		- Increased matte texture intensity and added architectural LED accents to sidebar, cards, buttons, and progress.
		- Adjusted shadow and bevel tokens for more visible depth.
# Copilot TODO
