# Workflow Approval Schema

## Scope

Fragment: `sql/fragments/005-workflow-approval.sql`

This fragment owns flow definition, node config, edge config, publish snapshots, workflow instances, approval tasks, approval actions, and workflow simulation logs.

## Tables

- `un_flow_definition`: configurable workflow header.
- `un_flow_node`: node library instance and node-specific properties.
- `un_flow_edge`: canvas links and branch labels.
- `un_flow_snapshot`: immutable published workflow snapshot.
- `un_flow_instance`: runtime workflow instance bound to a business object.
- `un_flow_approval_task`: pending and completed approval tasks.
- `un_flow_approval_action_log`: approve, reject, transfer, withdraw, and terminate records.
- `un_flow_simulation_log`: simulation input, output, and failed checks.

## Constraints

- Approval, condition, field update, external API, timer, and timeout nodes carry dedicated properties.
- Published snapshots are immutable and used by running instances.
- Approval actions are idempotent and must record reason, operator, trace ID, and audit ID.

## Acceptance Notes

- Indexes cover flow, version, instance, business target, assignee, status, and due-time lookups.
- Publish check and simulation must report blockers before activation.
