# Engineering Framework

This directory is the active engineering control point for the refactor.

Read order:

1. `current-engineering-framework.md`
2. `legacy-extract.md`

Rules:

- `docs/user_requirement.md` is the primary product requirement source.
- `docs/design/prototypes/index.html` is a product interaction reference, not an implementation shortcut.
- `.oldbk/` has been extracted and removed. New work must not depend on it.
- Any development task must name its requirement area, role journey, boundary, expected result, and verification method before coding.
- A task is not complete because code exists. It is complete only when the role can finish the intended business action and the result is verified.
