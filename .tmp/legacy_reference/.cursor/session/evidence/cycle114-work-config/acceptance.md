# WK114-B Work configuration and platform Todo acceptance

- cycle: `CYCLE-PHASE-GAP-CLOSURE-114`
- task: `WK114-B`
- verdict: `PASS_FULL_INTEGRATION`
- integration evidence: `.cursor/session/evidence/cycle-phase-gap-closure-114/acceptance.md`
- final migration: `V8_84_0__work_configuration_platform_todo.sql`
- formal checkpoint attempts consumed: `0`

## Requirement closure

| frozen requirement | executable result | verdict |
|---|---|---|
| system administrator creates versioned Work configuration | tenant-scoped immutable draft revisions, history, active revision and `work.config.manage` permission | PASS |
| publication check, publish and rollback | dictionary-bound preflight, optimistic publish, one active revision, retirement history, rollback creates a new auditable published revision | PASS |
| project-task, ordinary-task and daily-report fields | create/update/list/detail endpoints validate and persist published custom fields; object type selects the correct field list | PASS |
| field permissions | unauthorized writes fail; unreadable fields and card values are omitted from runtime responses | PASS |
| dictionary-backed fields | publish requires an enabled system dictionary; writes require enabled dictionary item codes | PASS |
| card and Kanban configuration | runtime response exposes readable card values plus configured column/group/numeric field codes and validated values | PASS |
| platform-context Todo | `/api/v1/platform/todos` provides list, counts, detail and idempotent action; actions execute the native platform task complete/cancel/reopen lifecycle | PASS |
| Work configuration administration UI | system-admin page supports project-task/ordinary-task/daily-report field design, dictionary and permission bindings, card/Kanban roles, immutable draft creation, preflight, publish, history and rollback | PASS |
| platform Todo UI | permission-gated platform page shows counts and paged rows, tabbed detail drawer, available lifecycle actions and replay result feedback | PASS |

## Verification

1. Java 21 full Work reactor test:
   - command: `mvn -f backend/pom.xml -pl examine-work -am test`
   - `examine-core`: 83 passed
   - `examine-work`: 97 passed
   - failures/errors/skips: 0/0/0
2. New focused Work evidence:
   - `WorkConfigurationServiceTest`: 3 passed
   - `WorkConfigurationMigrationContractTest`: 1 passed
3. Full affected reactor compile through `examine-web`: 14/14 modules `BUILD SUCCESS`.
4. Real MySQL platform Todo journey:
   - `PlatformTodoJourneyIntegrationTest`: 1 passed
   - proves owner-scoped list/count/detail, native completion transition, replay-safe `Idempotency-Key`, and one durable action row.
5. Frontend focused verification:
   - command: `npm.cmd test --prefix frontend -- --run tests/unit/work-configuration-api.spec.ts tests/unit/platform-todo-api.spec.ts tests/unit/work-configuration-view.spec.ts tests/unit/platform-todo-view.spec.ts tests/unit/work-todo-navigation.spec.ts`
   - result: 5 files, 9 tests passed; failures: 0
6. Frontend affected regression verification:
   - command: `npm.cmd test --prefix frontend -- --run tests/unit/work-api.spec.ts tests/unit/platform-task-api.spec.ts tests/unit/platform-workbench.spec.ts tests/unit/platform-ai-navigation.spec.ts tests/unit/data-source-navigation.spec.ts tests/unit/work-message-template-navigation.spec.ts tests/unit/todo-navigation.spec.ts`
   - result: 7 files, 28 tests passed; failures: 0
7. Frontend production package verification:
   - command: `npm.cmd run build --prefix frontend`
   - result: `vue-tsc --build --force` and Vite production build passed; only the existing large-chunk advisory remains.

## Honest boundary

- This closes the frozen configurable field lists for project tasks, ordinary tasks and daily reports. The project aggregate itself remains the fixed project schema because `REQ-WORK-001` names the configurable object as project-task, not project metadata.
- Dictionary definitions/options remain owned by the existing system dictionary administration; Work configuration only binds and consumes them.
- The frontend cycle intentionally added only the minimum desktop management-system structure required for functional acceptance. Responsive and visual polishing remain deferred until the feature set is complete.
- The migration version is intentionally temporary. The integration owner must re-number all Cycle114 migrations, run the complete migration chain/full backend suite, and package only after all parallel modules are merged.
