# Clean Rebuild Task Plan

## Current Batch

`REBUILD-R0`

## Task Queue

1. `REBUILD-P0-001 Source Baseline And IA Contract`
   - Prove retained source files exist.
   - Freeze the clean rebuild taxonomy and layout rules in `.cursor`.
   - Keep `.oldbk` reference-only.

2. `REBUILD-P0-002 Clean Project Scaffold`
   - Recreate a minimal, runnable project structure.
   - Restore only the tech stack and useful patterns that support the clean architecture.
   - Add deterministic UTF-8, lint/build/test entry points, and a clean dev start path.

3. `REBUILD-P0-003 Auth And Context Shell`
   - Implement login/register/reset shell.
   - Implement platform/system context and system switch.
   - Render the context-aware top navigation and profile actions.

4. `REBUILD-P0-004 Admin Configuration Foundation`
   - Implement backend/admin IA for basic info, org, roles, modules, flow, applications, dashboards, dictionaries, work, AI, data sources, logs.
   - Persist configuration data and read it back in UI.

5. `REBUILD-P0-005 Business Module Configuration And Runtime`
   - Implement module groups, modules, fields, actions, pages, print templates.
   - Implement runtime list with left tabs and right-side detail tabs.

6. `REBUILD-P0-006 Flow And Application Gateway`
   - Implement Flow designer/runtime enough for internal approval and external binding.
   - Implement Application as authorization gateway for Flow/system service exposure.

7. `REBUILD-P0-007 Work Todo Message AI`
   - Implement work dashboard, project tasks, normal tasks, daily reports.
   - Implement configured todo workbench and message center.
   - Implement AI entry with admin-controlled config/policy placeholders and audited actions.

8. `REBUILD-P0-008 Release And Human Trial Readiness`
   - Package and run the system.
   - Verify role journeys, permissions, state transitions, responsive layout, and readback.
   - Produce user trial checklist while keeping `gates.user_script_passed=false` until user signoff.

## First Coding Task

After this plan is written, the next coding task is `REBUILD-P0-002 Clean Project Scaffold`.
