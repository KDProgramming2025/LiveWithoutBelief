# Admin Web Module Architecture

This document describes the structure and responsibilities of the modular JavaScript and CSS code for the `admin/web` UI.

## High-Level Goals
- Keep concerns isolated (auth, state, UI behaviors, views, icons).
- Minimize coupling—modules communicate via small exported functions and shared state object.
- Preserve a framework‑agnostic, zero-build approach using native ES modules.
- Optimize for readability, testability (unit-test friendly pure helpers), and future feature growth.

## Directory Overview
```
admin/web/
  assets/
    css/              # Modular styles (tokens, base, forms, menu, progress, sidebar, animations)
    js/
      app.js          # Bootstrap entry: wires modules together
      core/
        state.js      # Global state + preference + token persistence
        api.js        # Auth-aware API wrapper (relative fetch helper)
        helpers.js    # Pure formatting utilities (bytes, ETA)
      auth/
        auth.js       # Login flow, token save/clear, ensureAuth gate
      ui/
        theme.js      # Theme initialization & toggle handling
        sidebar.js    # Sidebar collapse/expand + mobile outside click handling
      icons/
        lucide.js     # Lucide dynamic loader + refresh + theme icon switch
      router/
        router.js     # View registry + render orchestration
      views/
        menu.js       # CRUD UI for menu items (upload icon, reorder, edit)
        articles.js   # Article listing & progressive upload (XHR progress)
        users.js      # User search/list + delete action
```

## Module Responsibilities
### `core/state.js`
Centralizes mutable session state (`token`, current `view`) and persistence helpers:
- `savePref / loadPref` store UI preferences (theme, sidebar state) in `localStorage`.
- `saveToken / loadToken / clearToken` manage authentication token lifecycle.
- Exports a simple `state` object (no proxy/magic for ease of debugging).

### `core/api.js`
Thin fetch wrapper that injects `Authorization` header when a token exists and normalizes unauth handling (throws on 401 to let callers trigger re-login logic). Keeps networking concerns decoupled from views.

### `core/helpers.js`
Pure stateless helpers:
- `fmtBytes(bytes)` human readable size.
- `fmtEta(seconds)` HH:MM:SS or M:SS formatting.
Designed for unit testability and reuse (e.g., progress components).

### `auth/auth.js`
- Initializes login form submission.
- Calls `/login` endpoint, stores token, re-renders current view, and hides overlay.
- Provides `ensureAuth()` to gate rendering & show overlay when unauthenticated.
- Handles logout click: clears token and forces re-auth overlay.

### `ui/theme.js`
- Applies persisted theme (`dark` default, `light` if stored) to `<html>`.
- Updates `aria-pressed` on theme toggle for accessibility.
- Attaches click listener to toggle theme and persist preference.
- Delegates icon glyph switch to `setThemeIcon()` from icons module.

### `ui/sidebar.js`
- Reads persisted sidebar state and applies collapsed/expanded attributes & classes.
- Handles collapse/expand toggle with timed class markers (`is-expanding`, `is-collapsing`) aligned to CSS animation duration.
- Mobile behavior: auto-collapses when clicking outside on narrow viewports.

### `icons/lucide.js`
- Lazy loads Lucide icon set via ESM CDN import only once (idempotent flagging via `window.__LUCIDE_LOADED__`).
- Exposes `refreshIcons()` to re-scan DOM after dynamic content injection.
- `setThemeIcon()` swaps sun/moon glyph based on current theme.

### `router/router.js`
- Maintains view registry mapping (`menu`, `articles`, `users`) to async view factory functions.
- `render(view)` updates active nav item styling, clears content, inserts resolved view element.

### `views/*`
Each view module returns a fully wired DOM subtree:
- `menu.js`: Menu item CRUD (list, add, delete, reorder, edit text, replace icon) + modal editing pattern.
- `articles.js`: Lists uploaded articles; provides XHR upload with real-time progress (speed + ETA) & cancelation.
- `users.js`: Searchable user list (limit, search query) + user removal.
Views depend only on: `api`, `state`, minimal helpers, and direct DOM APIs.

### `app.js`
- Orchestrates initialization order: theme → sidebar → auth → nav binding → icons → initial render.
- Exposes `state` re-export for console debugging.
- Ensures `ensureAuth()` runs immediately after first render.

## CSS Architecture (Brief)
- `tokens.css`: Design tokens & theming variables (dark/light surfaces, accent, radii, spacing, animation duration).
- `base.css`: Resets and structural primitives.
- `forms.css`, `menu.css`, `progress.css`, `sidebar.css`: Component-scoped rules.
- `animations.css`: Transition timing (sidebar width/labels) + theme transition (with `prefers-reduced-motion` guards).
- The theme transition applies 1s `ease-in` fade for background, text, border, and shadow changes.

## Data & Control Flow
1. Page load → `DOMContentLoaded` → `app.js` `boot()`.
2. Theme + sidebar preferences restored before first paint of main content.
3. Auth token (if present) allows immediate data fetch inside initial view; otherwise overlay prompts login.
4. Views fetch data only when rendered; re-render triggered by nav clicks or after mutations (e.g., upload/delete).
5. Icon refresh invoked after dynamic DOM injection when necessary.

## Accessibility & UX Notes
- Aria attributes maintained on toggles (`aria-pressed`, `aria-expanded`).
- Theme & sidebar states persisted to reduce cognitive friction.
- `prefers-reduced-motion` respected for collapsing animation (partial) and theme transitions (full support). (Future improvement: extend to sidebar width animation as an instant state change.)

## Extension Points
- Add new view: create `views/<name>.js` exporting `async function view<Name>()`, register in `router.js` registry, add a nav button with `data-view` attribute.
- Add global preference: define key in `PREF_KEYS` and handle save/load within appropriate module.
- Swap icon set: isolate change to `icons/lucide.js` (or add fallback strategy if network import fails).

## Potential Future Enhancements
- Tooltip module for collapsed sidebar icons (focus + hover, aria-describedby approach).
- Full reduced-motion parity for width/gap transitions.
- Automated unused CSS reporting (DevTools Coverage + script / potential purge step if build pipeline adopted).
- Token documentation overlay or theme live preview inspector.

## Rationale for Decisions
- Native ES modules chosen to avoid extra build complexity for a small admin panel; network-friendly dynamic icon import defers cost.
- Single global `state` object vs. events/store library keeps mental model simple and avoids over-engineering.
- Modular CSS improves discoverability and future pruning while keeping cascade intentional.

---
_Last updated: synchronized with commit introducing 1s theme transitions._
