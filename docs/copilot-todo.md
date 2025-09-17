# Copilot TODO

## Status Summary
Sidebar modernization (structure, animations, Lucide icons, persistence, timing refinement, stabilization) is complete on `feature/LWB-92-admin-ui`.

## Recently Completed (Light Theme Pass)
- Added semantic inset surface tokens: `--inset`, `--inset-border`, `--elevated-ring`.
- Replaced hard-coded dark background usages (#0b1220) with new tokens (inputs, badges, progress track, menu-card icon container, uploading badge).
- Softened and unified light theme palette (reduced glare, cohesive neutrals, consistent panel layering: `--panel`, `--panel-alt`, `--inset`).
- Refined hover/active nav-item states for light theme (subtler backgrounds, adjusted internal shadow/outline intensity).
- Added light-mode brand logo gradient and softened shadow ring.

## In Progress
- Visual QA pass (manual) to confirm contrast comfort across typical screen brightness settings.

## Backlog / Next Steps
1. Reduced-motion support: Wrap non-essential transitions in `@media (prefers-reduced-motion: no-preference)` and provide instant state changes otherwise.
2. Tooltips for collapsed sidebar icons (ARIA friendly, appear on focus + hover; hide when expanded to avoid redundancy).
3. Accessibility contrast audit (WCAG AA targets: normal text 4.5:1, large 3:1) for light theme muted + active gradient states.
4. Token consolidation / documentation: Inline comment block enumerating semantic layers and intended usage (panel vs inset vs alt).

## Stretch Ideas
- Live theming preview module (token inspector) within admin UI.
- User-selectable accent color persisted per account (post-auth integration).

## Changelog Anchor
Latest change: Light theme palette refactor & semantic inset surfaces introduced.