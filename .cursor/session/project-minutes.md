# Project Minutes

This file records confirmed project decisions only.

Open questions, alternatives, and exploratory notes are intentionally not recorded here. They belong in the relevant contract, design, task, or audit file.

## Confirmed Decisions

| Date | Topic | Confirmed Decision | Durable Source | Impact |
|---|---|---|---|---|
| 2026-07-09 | Requirements rebuild | Current work is requirements and engineering reconstruction, not coding. Old `REBUILD-P0-*` implementation queue remains historical only. | `.cursor/session/rebuild/task-plan.md`, `.cursor/session/state.json` | Backend/frontend/sql implementation stays closed until the requirements rebuild package is accepted. |
| 2026-07-09 | Old `19999` artifact | The old deployed page is failure evidence only, not the target product direction or completion evidence. | `.cursor/session/rebuild/leader-ui-function-review.md`, `.cursor/session/rebuild/user-review-package.md` | Future UI work follows the new UI system contract and reference dense enterprise style. |
| 2026-07-09 | Product boundaries | Application is an authorization/service exposure valve; business modules are organized by module groups and are not children of Application. | `.cursor/session/rebuild/product-boundary-contract.md` | Flow/Application/business-module tasks must keep ownership and routes separated. |
| 2026-07-09 | Engineering ownership | Work, Todo, Message, and AI are top-level product domains and should have explicit module/table ownership instead of being hidden in Flow or generic pages. | `.cursor/session/rebuild/engineering-architecture-map.md` | Future schema/API tasks must close module and prefix decisions before coding. |
| 2026-07-09 | Backend method | Future backend work is schema-first, then generated `base`, then handwritten `manage`. | `.cursor/session/rebuild/backend-codegen-manage-contract.md` | Generated CRUD is infrastructure only; business completion requires manage behavior, permission, state, readback, and side effects. |
| 2026-07-09 | UI system | Target UI is dense enterprise operations style: compact dark top navigation, table-first lists, right-side detail work areas, clear states and readback. | `.cursor/session/rebuild/ui-system-interaction-contract.md`, `.cursor/architecture/ui-system.md` | User-facing frontend tasks must name and prove the UI pattern. |
| 2026-07-09 | Encoding | Active project text files must be UTF-8; Windows PowerShell reads/writes must use explicit `-Encoding UTF8`. | `.editorconfig`, `.cursor/architecture/encoding-and-records.md` | Mojibake checks become part of governance/document acceptance. |
| 2026-07-09 | Records discipline | Use this single project minutes file for confirmed decisions; do not create scattered meeting-note files. | `.cursor/architecture/encoding-and-records.md` | Reduces drift and makes the latest confirmed conclusion easy to find. |
| 2026-07-09 | Leader three-cycle governance | Leader must organize role reviews, integrate decisions, supplement durable files, and re-check decisions through three internal cycles before reporting a package; ordinary continuation must not be pushed back to the user. | `.cursor/session/rebuild/leader-consistency-audit.md`, `.cursor/README.md`, `.cursor/session/state.json` | Future broad governance work follows review -> integrate -> verify loops, with user involvement only for final signoff or true user-only choices. |
| 2026-07-09 | UI implementation boundary | The reference style is contractized, not implemented. Old prototypes and `.oldbk/frontend` cannot be used as implementation baselines without a UI-system delta review. | `.cursor/session/rebuild/requirement-task-breakdown.md`, `.cursor/templates/task.md` | DESIGN/BUILD tasks must state what is retained from prototypes and what is rejected as old `19999` anti-pattern. |

## Temporary File Ledger

| Date | Path | Purpose | Disposal Rule | Owner |
|---|---|---|---|---|
| 2026-07-09 | none active | No retained temporary file is currently approved as source material. | Temporary files created during a task must be deleted, merged into a durable file, or recorded here before the task is closed. | leader |
