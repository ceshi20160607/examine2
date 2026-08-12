# P6 Work, Todo and message phase acceptance

- phase: `P6_WORK_EVENT`
- predecessor: `P5_PHASE_ACCEPTANCE`
- acceptedAt: `2026-08-07T17:16:00+08:00`
- verdict: `PASS`
- successor: `P7_PHASE_ACCEPTANCE`

## Frozen requirement audit

- Work project/task/daily-report runtime, views, reminders and configuration,
  Todo aggregation/actions, message inbox/templates/channels/preferences and
  durable job notifications are represented by completed project outcomes.
- The final configuration/platform-Todo gap is proved by
  `.cursor/session/evidence/cycle114-work-config/acceptance.md`: versioned
  custom fields, dictionary/permission checks, card/Kanban roles, immutable
  publish/history/rollback and platform-context Todo list/count/detail/action.
- Current integration is strengthened by Cycle116 Flow TASK/notification node
  owner calls and the final Work/Flow/Web reactor. No duplicate Work or Event
  persistence owner was introduced.

## Integration gates

The current package passed `1,786` backend tests, `687` frontend tests/build,
all `112` migrations, health/login/frontend cold start and the browser system
journey. Historical migration-number caveats in Cycle114 were resolved in the
current consecutive migration set through `8.91.0`.

This phase acceptance is not release acceptance or user sign-off. Formal
checkpoint attempts remain `0/3`.

