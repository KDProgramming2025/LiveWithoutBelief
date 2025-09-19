# Admin Web — Direction B (Modern Analog Studio)# Copilot TODO


This iteration resets the admin UI to a skeuomorphic, tactile look inspired by analog studio gear. The default theme is dark with warm, low-saturation neutrals and brass-accent hints. A light mode is included via `[data-theme="light"]`.

## What changed
- New tokens in `admin/web/assets/css/tokens.css` for palette, elevation/shadows, radii, spacing, typography, focus, and textures.
- Base surfaces and layout in `base.css` with subtle hidden-lights and hairline noise.
- Tactile form controls in `forms.css` (inset inputs, raised buttons, select arrow, checkbox/switch).
- Menu cards in `menu.css` with fine paper texture and depth hover.
- Sidebar/navigation in `sidebar.css` with compact controls and theme-fade support.
- Progress bar in `progress.css` inspired by analog VU, using semantic colors (no flashy gradients).
- Tileable SVG textures added: `paper-fiber.svg`, `brushed-metal.svg`, `hairline-noise.svg`.

## Design tokens (highlights)
- Palette (dark default):
  - bg: `#161718`, surfaces: `#1c1e20`/`#232529`/`#2a2d31`, inset `#131415`.
  - text: primary/muted/subdued tuned for warm neutral contrast.
  - accent: warm brass `--accent-500: #cfae67` used sparingly.
- Shadows: `--shadow-raised-1/2`, `--shadow-inset-1`, `--shadow-press-1`.
- Focus: layered neutral + brass halo via `--focus`.
- Textures: paper for cards, brushed-metal for accents if needed, hairline-noise on page.

## Theming
- Dark is default. Light mode overrides palette/shadows for proper contrast.
- Theme toggle logic is unchanged (`assets/js/ui/theme.js`). The sidebar fade layer is supported in CSS.

## Next ideas (optional)
- Add a subtle “VU tick” animation class for progress milestones (reduced-motion aware).
- Small embossed section headers (micro-bevel) for data tables.
- Accessibility audit: ensure ≥ 4.5:1 for interactive text in both themes.

## Deploy notes
- Use the one-command Linux runner to fetch then fast-forward pull on `/var/www/LWB`.
- If CSS purging/minification is added later, thread it through CI and verify `index.html` includes the right outputs.
