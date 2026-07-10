# Requirements Rebuild Architecture

## Current Stage

This project is not in coding mode now.

The current work is to rebuild the requirements, engineering architecture, task breakdown, review gates, and legacy reference boundary before any new implementation starts.

## Why This Stage Exists

Previous rebuild attempts did not satisfy the user's target. Continuing feature coding from the current P0 task list would repeat the same failure pattern: page/API slices move forward while the product boundary, engineering structure, and acceptance model remain unclear.

The current stage must finish the thinking work first:

- organize the whole product by engineering architecture, not by scattered pages;
- reconcile requirements, prototype, flow map, and project rules into one product contract;
- split tasks only after the contract is clear;
- identify legacy materials that are useful references;
- delete or ignore legacy materials only after inventory and impact assessment;
- define the code-generation-first backend workflow before backend work resumes.

## Hard Stop

- Do not create or modify active backend/frontend/sql implementation files in this stage.
- Do not resume `REBUILD-P0-006` until requirements and engineering gates are rebuilt.
- Do not treat old evidence or old scripts as proof of current completion.
- Do not fix discovered product/architecture problems one by one as ad hoc coding tasks.

## Engineering-First Work Order

1. Product boundary map.
   - Freeze the meaning of 工作台, Flow, 应用, 工作, AI, 待办, 消息, 后台, 个人信息, 业务模块, and flow 管理.
   - Distinguish platform context from system context.

2. Engineering module map.
   - Map product domains to backend modules, frontend shells/routes, database table groups, permissions, events, todo/message/log effects, and release evidence.

3. Legacy inventory.
   - Classify old materials as retained source, reference-only implementation, evidence archive, generated/cache noise, or deletion candidate.
   - For every deletion candidate, write impact before deleting.

4. Committee review.
   - Product, architecture, backend, frontend, DBA, and test perspectives review the same contract before coding.
   - Problems found here become requirement/task corrections, not immediate code patches.

5. Task breakdown.
   - Split tasks by engineering module and role journey.
   - Each task must state source requirement, affected modules, generated base scope, handwritten manage scope, data persistence, permission paths, states, readback, and evidence.

6. Coding gate.
   - Coding starts only after the above artifacts are accepted.
   - Backend coding starts with generator-produced base code, then handwritten manage logic.

## Committee Rule

The committee is not a meeting for every tiny defect. It is the gate used when a problem shows product boundary drift, engineering architecture drift, missing role journey, missing data model, missing permission model, or unclear acceptance.

Normal expectation: these problems should be found while reading requirements, checking the prototype, and splitting tasks. If a later coding task exposes one, stop the task and route the issue back through the committee gate.

## Legacy Rule

Old implementation and historical artifacts can be useful, but they are not current completion.

Legacy handling must use this classification:

| Class | Meaning | Action |
|---|---|---|
| retained source | current requirement/prototype/flow files | keep and use as source |
| reference implementation | old backend/frontend/sql patterns with reusable value | summarize, reference only |
| evidence archive | old screenshots, release logs, recovery scripts | keep only as history, not proof |
| generated/cache noise | browser profiles, build outputs, temporary caches | deletion candidate after impact check |
| unknown | cannot tell value yet | keep until reviewed |

No legacy deletion should happen silently. Write what is removed, why it has no reference value, and what impact it has.

## Backend Generation Rule

When backend coding resumes:

- database/schema design comes first;
- run the generator to produce `base` entity/mapper/service code;
- do not handwrite bulk base CRUD;
- handwritten logic goes under `manage`;
- controllers expose manage DTO/VO, not base entities;
- generated base is plumbing, not product completion.

Reference structure remains `.cursor/architecture/backend-structure.md`.
