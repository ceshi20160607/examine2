# Cycle 118 accessibility and usability hardening

- Verdict: **PASS**
- status: `PASS` for the declared desktop/mobile accessibility slice
- captured_at: `2026-08-08T11:46:00+08:00`
- formal checkpoint impact: none; this is rolling evidence and does not consume CP3

## Implemented fixes

- Unified primary and secondary text tokens at WCAG AA-safe contrast values and corrected platform/system headings, cards, status tags, top-bar badges, onboarding actions and command-center secondary text.
- Added explicit accessible names to platform/system brand links when their visible labels are hidden at mobile breakpoints.
- Bound account profile and MFA labels to their inputs with stable `id`/`html-for` pairs.
- Added deterministic command-center focus management: click or `Ctrl/Cmd+K` focuses the search input; `Escape` returns focus to the launcher.
- Corrected the login footer and mobile record labels found by the real production scan.

## Verification

| Gate | Result |
|---|---|
| Focused frontend regression | `16/16` plus final command/navigation `4/4` PASS |
| Full frontend regression after fixes | `150` files / `722` tests PASS |
| Type check | PASS |
| Production build | PASS; only the existing chunk-size advisory |
| Desktop axe matrix | 7 surfaces, `0` critical/serious |
| Mobile axe matrix | 5 authenticated surfaces plus login, `0` critical/serious |
| Skip link | first keyboard focus is `跳到主要内容`; Enter focuses `MAIN#platform-main` |
| Command center | launcher/shortcut focuses search input; Escape restores launcher focus |

Machine-readable scan scope is in `axe-summary.json`. The broader all-role usability journey and explicit user sign-off remain release gates; this slice does not claim them.
