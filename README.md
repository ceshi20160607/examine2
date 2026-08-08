# examine2

`examine2` is the rebuild workspace for `unexamine`, a configurable business system platform with workflow, permissions, runtime business pages, OpenAPI integration, AI assistance, and operations support.

Whole-project progress is shown in `.cursor/session/PROJECT_PROGRESS.md` and is
backed by the machine-readable `.cursor/session/project-progress.json`. Runtime
execution status remains in `.cursor/session/state.json`; the current slice is
explained in `.cursor/session/current-node.md`, and the complete delivery path
is tracked in `.cursor/session/delivery-roadmap.md`.

Start here:

- `docs/user_requirement.md`: product requirement source
- `docs/design/prototypes/index.html`: prototype reference
- `.base/README.md`: portable engineering template source with no current-project references
- `.cursor/INSTANCE.md`: self-contained current project engineering instance
- `.cursor/LEADER.md`: overall orchestration and node-acceptance rules
- `.cursor/README.md`: current project collaboration/runtime entry
- `.cursor/session/PROJECT_PROGRESS.md`: concise completed/remaining progress and packaging status
- `.cursor/session/project-progress.json`: authoritative evidence-backed progress ledger
- `.cursor/session/state.json`: current phase, Gate, task and completion boundary
- `.cursor/session/delivery-roadmap.md`: staged delivery plan and remaining final-goal scope

Important rule:

- `.base` is used for instantiation and explicit template maintenance; normal project work runs only from `.cursor`
- new implementation starts from clear requirements and database design
- base persistence code should be generated after schema planning
- business behavior is coded explicitly above the generated base layer
- progress comes from accepted outcome evidence, never file or commit counts
- the project has exactly three package checkpoints: functional baseline,
  feature complete and release candidate
- deleted historical code is not a requirement source; the current clean implementation is retained and extended only through accepted slices
