# examine2

`examine2` is the rebuild workspace for `unexamine`, a configurable business system platform with Flow, permissions, runtime business pages, controlled application access, AI assistance, and operations support.

The rebuild now has explicit boundaries:

- `workspace.json`: machine-checked requirements, template and active-project boundary
- `template_base/`: reusable deterministic development pipeline and generators
- `projects/unexamine/`: the only location for the new product implementation

Start here:

- `docs/user_requirement.md`: product requirement source
- `projects/unexamine/ai/requirements/requirement-intake.json`: deterministic requirement block index
- `projects/unexamine/ai/status/project-status.json`: computed project state and scheduling availability
- `workspace.json`: enforced active-project and template boundary

Important rule:

- product changes are allowed only under `projects/unexamine/`; reusable engineering changes belong in `template_base/`
- new implementation starts from clear requirements and database design
- base persistence code should be generated after schema planning
- business behavior is coded explicitly above the generated base layer
- progress comes from accepted outcome evidence, never file or commit counts
- stages and four-hour cycles are computed only after atomic tasks and dependencies are complete; they are never guessed from old code
- no historical implementation is part of this workspace; requirements come only from `docs/user_requirement.md`
