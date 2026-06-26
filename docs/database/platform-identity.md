# Platform Identity Schema

## Scope

Fragment: `sql/fragments/001-platform-identity.sql`

This fragment owns account, system, tenant, organization, system member, account-member binding, SSO binding, no-member access request, and login audit context.

## Tables

- `un_plat_account`: one platform account identity.
- `un_plat_system`: system lifecycle, tenant mode, owner, status, and publication state.
- `un_plat_tenant`: tenant records under a system.
- `un_plat_department`: department tree for each system and tenant.
- `un_plat_member`: employee/member identity inside a system.
- `un_plat_account_member_binding`: platform account to system member binding with `accountMemberBindingId`.
- `un_plat_sso_binding`: external identity to account/member binding.
- `un_plat_no_member_access_request`: lifecycle for SSO success without `systemMemberId`.
- `un_audit_login_log`: login/security audit with `traceId`.

## Constraints

- Platform account does not imply system business access.
- Business entry requires `SystemSwitchContext` with `systemId`, `tenantId`, `systemMemberId`, roles, data scope, and permission snapshot.
- No-member SSO access must create `NoMemberAccessRequest` before any business page access.
- Secrets are represented only by references in other fragments.

## Acceptance Notes

- Indexes cover system, tenant, account, member, SSO external identity, and login audit lookups.
- Soft delete and audit fields are present on mutable tables.
