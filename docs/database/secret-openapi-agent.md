# Secret OpenAPI Agent Schema

## Scope

Fragment: `sql/fragments/008-secret-openapi-agent.sql`

This fragment owns identity providers, SSO policies, secret references, secret rotation jobs, OpenAPI apps, OpenAPI call logs, platform/system Agent model authorization, Agent policies, sessions, confirmations, and Agent audit logs.

## Tables

- `un_sys_secret_ref`: reference-only secret metadata.
- `un_sys_secret_rotation_job`: rotation lifecycle and rollback plan.
- `un_plat_identity_provider`: platform identity source configuration.
- `un_plat_system_sso_policy`: system inheritance, tenant domains, org mapping, member binding, and JIT policy.
- `un_openapi_app`: external app and scope configuration.
- `un_openapi_call_log`: external call audit.
- `un_agent_model_authorization`: platform/model authorization and quota.
- `un_agent_policy`: system Agent scope, field/action/data rules, outbound limit, and desensitize policy.
- `un_agent_session`: platform/system Agent conversation session.
- `un_agent_audit_log`: conversation, tool, prompt, model, permission, and confirmation audit.
- `un_agent_confirmation`: platform, system write, and work draft confirmation.

## Constraints

- Secrets are never stored or returned as plaintext in config, logs, exports, or screenshots.
- Platform Agent can only handle platform authorization, tasks, messages, logs, health, quotas, and system-switch guidance.
- System Agent runs only inside system/member/tenant/permission context.
- Agent write and work draft confirmations are explicit, human-confirmed, idempotent, and audited.

## Acceptance Notes

- Indexes cover provider, system, policy, secret ref, external app, session, confirmation, model authorization, and trace lookups.
- Secret rotation uses versioned ref records plus async task state.
