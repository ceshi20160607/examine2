# Real System Recovery G3 RBAC Org Evidence

Time: 2026-06-24 19:08 Asia/Shanghai

## Scope

Replace organization, system member and role management sample behavior with persisted data.

## Code Changes

- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/common/SystemMemberContextResolver.java`
  - Added shared resolver for current account, system, tenant and system member.
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/org/OrgService.java`
  - Replaced fixed department tree with persisted `un_plat_department` tree.
  - Department create now validates route context and unique department code.
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/role/RoleService.java`
  - Replaced sample platform/system roles with `un_plat_role`.
  - Platform/system role create and update now persist data.
  - System role member assignment now writes `un_plat_role_member` idempotently.
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/member/MemberService.java`
  - Replaced sample member list with `un_plat_member`.
  - Member create/update now persists member data and role-member rows.
  - Account binding now writes `un_plat_account_member_binding`.
  - Member list supports department, status, keyword, role and binding-status filters.

## Verification

Backend compile:

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn -f backend\pom.xml -pl examine-web -am -DskipTests compile
```

Result:

```text
BUILD SUCCESS
```

Backend package:

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn -f backend\pom.xml -pl examine-web -am -DskipTests package
```

Result:

```text
BUILD SUCCESS
```

Runtime jar started on `127.0.0.1:18114` against the configured `examine2` database.

Smoke result:

```json
{
  "health": "UP",
  "deptId": "1",
  "treeRoots": 1,
  "roleId": "6",
  "memberId": "6",
  "bindStatus": "BOUND",
  "assignCount": 0,
  "membersTotal": 1,
  "rolesTotal": 1,
  "bindingsCount": 2
}
```

`assignCount=0` is expected in this smoke because the member create request already assigned the role; the repeated assignment endpoint skipped the duplicate role-member row.

## Remaining Risk

Permission save/effective/preview still contains sample behavior and must be recovered next so backend enforcement is not only role/member persistence.
