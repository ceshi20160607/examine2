# examine2

`examine2` is the rebuild workspace for `unexamine`, a configurable business system platform with workflow, permissions, runtime business pages, OpenAPI integration, AI assistance, and operations support.

The rebuild now has explicit boundaries:

- `workspace.json`: machine-checked requirements, template, active-project and legacy-reference boundary
- `template_base/`: reusable deterministic development pipeline and generators
- `projects/unexamine/`: the only location for the new product implementation
- `.tmp/legacy_reference/`: hidden, reference-only legacy material; normal repository searches do not enter it

Start here:

- `docs/user_requirement.md`: product requirement source
- `projects/unexamine/architecture-baseline.json`: source-bound architecture and first vertical slice
- `projects/unexamine/requirements/requirement-intake.json`: deterministic requirement block index
- `workspace.json`: enforced active/legacy directory boundary
- `.tmp/legacy_reference/README.md`: legacy reference rules and explicit lookup entry

Important rule:

- new implementation changes are allowed only under `projects/unexamine/` or reusable `template_base/`
- `.tmp/legacy_reference/` is evidence, not a product requirement source or implementation destination
- new implementation starts from clear requirements and database design
- base persistence code should be generated after schema planning
- business behavior is coded explicitly above the generated base layer
- progress comes from accepted outcome evidence, never file or commit counts
- the project has exactly three package checkpoints: functional baseline,
  feature complete and release candidate
- historical code is never a requirement source; behavior is reused only after it is traced to current requirements and independently verified
