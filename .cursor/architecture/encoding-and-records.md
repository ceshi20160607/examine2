# Encoding And Records Rule

## Status

Status: active

Date: 2026-07-09

## UTF-8 Rule

- All active project text files must be UTF-8.
- `.editorconfig` is the project-level encoding source of truth.
- On Windows PowerShell, commands must use explicit `-Encoding UTF8` when reading or writing project text files.
- New files must be created as UTF-8 and must not introduce mojibake.
- If a file shows mojibake in terminal output, first verify the reader encoding before editing the content.
- Historical archives under `.oldbk/` are reference-only and are not normalized unless a cleanup task explicitly says so.

## Mojibake Guard

Before accepting a governance, requirement, design, or source edit, scan active project files for common mojibake markers:

```powershell
powershell -ExecutionPolicy Bypass -File .cursor/scripts/check-encoding.ps1
```

The expected result is no matches. If matches appear, confirm whether they are real content corruption or terminal decoding before proceeding.

The script scans existing active roots from `.cursor`, `docs`, `backend`, `frontend`, `sql`, `scripts`, root markdown/config files, and ignores archives/caches such as `.oldbk`, `.git`, `node_modules`, `dist`, `target`, `release`, `logs`, and `.tmp-browser`.

## Project Minutes Rule

Use one concise confirmed-decisions file:

- `.cursor/session/project-minutes.md`

Only confirmed outcomes go into the minutes. Unconfirmed options, open questions, and exploratory analysis stay in the relevant requirement, design, architecture, or task file.

Each minutes row should be short:

- date;
- topic;
- confirmed decision;
- durable source file;
- impact.

Do not create a new meeting-notes file for every discussion.

## Temporary File Rule

Temporary files are allowed only when they help finish the current task.

Rules:

- temporary files must not become source of truth;
- if a temporary file must survive the task, record it in `.cursor/session/project-minutes.md` under the temporary file ledger;
- each retained temporary entry must have path, purpose, disposal rule, and owner;
- obsolete temporary files must be deleted or moved to an approved archive/cleanup plan;
- root-level scratch files are forbidden unless an active task explicitly owns them.

## Source Of Truth Rule

When a conclusion matters later, update the durable contract file and add only the short confirmed summary to project minutes.

The project should have fewer authoritative files, not more ambiguous notes.
