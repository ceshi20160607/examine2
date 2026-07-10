# examine2

`examine2` is the rebuild workspace for `unexamine`, a configurable business system platform with workflow, permissions, runtime business pages, OpenAPI integration, AI assistance, and operations support.

Current status: framework and requirements are being consolidated before implementation.

Start here:

- `docs/user_requirement.md`: product requirement source
- `docs/design/prototypes/index.html`: prototype reference
- `.base/README.md`: portable engineering template source with no current-project references
- `.cursor/INSTANCE.md`: self-contained current project engineering instance
- `.cursor/README.md`: current project collaboration/runtime rules

Important rule:

- `.base` is used for instantiation and explicit template maintenance; normal project work runs only from `.cursor`
- new implementation starts from clear requirements and database design
- base persistence code should be generated after schema planning
- business behavior is coded explicitly above the generated base layer
- old code is not patched or reused as the new project
