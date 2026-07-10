# examine2

`examine2` is the rebuild workspace for `unexamine`, a configurable business system platform with workflow, permissions, runtime business pages, OpenAPI integration, AI assistance, and operations support.

Current status: framework and requirements are being consolidated before implementation.

Start here:

- `docs/user_requirement.md`: product requirement source
- `docs/design/prototypes/index.html`: prototype reference
- `docs/framework/current-engineering-framework.md`: active engineering framework
- `docs/framework/legacy-extract.md`: useful information extracted from the removed legacy archive

Important rule:

- new implementation starts from clear requirements and database design
- base persistence code should be generated after schema planning
- business behavior is coded explicitly above the generated base layer
- old code is not patched or reused as the new project

