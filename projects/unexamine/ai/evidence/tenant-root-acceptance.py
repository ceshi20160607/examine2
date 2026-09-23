"""租户默认组织真实验收：新后端准备限定的遗留夹具，再验证迁移，凭证只留在进程。"""
import ast
import json
import re
import secrets
import subprocess
import sys
import traceback
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).resolve().parent
PROJECT = AREA.parents[1]
JAVA = PROJECT / "backend/src/main/java/com/unique/unexamine"
SYSTEM, MAIN_TENANT, RECIPIENT_TENANT, MANUAL_ROOT = 54, 56, 57, 75
FACTS = AREA / "tenant-root-facts.json"
MIGRATION = PROJECT / "db/update/V42__tenant_default_organization_repair.sql"


class AcceptanceError(Exception):
    """只携带可公开的验收错误，不携带请求正文或鉴权响应。"""
    def __init__(self, code, message):
        self.code, self.message = code, message
        super().__init__(message)


def require(condition, code, message):
    if not condition:
        raise AcceptanceError(code, message)


def load_tools():
    """只编译四个函数 AST；不 import 或执行共享验收脚本的顶层业务。"""
    source = AREA / "shared-actor-acceptance.py"
    tree = ast.parse(source.read_text(encoding="utf-8"), filename=str(source))
    names = {"call", "api", "sql", "enter"}
    selected = [node for node in tree.body if isinstance(node, ast.FunctionDef) and node.name in names]
    require({node.name for node in selected} == names, "TOOLS_MISSING", "共享验收工具函数不完整")
    namespace = {"json": json, "subprocess": subprocess, "urllib": urllib, "SYSTEM": SYSTEM}
    exec(compile(ast.Module(body=selected, type_ignores=[]), str(source), "exec"), namespace)
    return tuple(namespace[name] for name in ("call", "api", "sql", "enter"))


call, api, sql, enter = load_tools()


def safe_error(error):
    if isinstance(error, AcceptanceError):
        return {"code": error.code, "message": error.message}
    if isinstance(error, AssertionError) and error.args and isinstance(error.args[0], tuple):
        values = error.args[0]
        if len(values) == 4:
            return {"code": values[2], "message": values[3]}
    frames = traceback.extract_tb(error.__traceback__)
    return {"code": "LOCAL_ACCEPTANCE_FAILED", "message": "本地验收或收尾失败；不输出鉴权正文、命令或凭证",
            "error_type": type(error).__name__, "error_line": frames[-1].lineno if frames else None}


def save(facts):
    """仅写明确挑选的事实；禁止凭证、散列和动作请求标识进入文件。"""
    def check(value):
        if isinstance(value, dict):
            for key, child in value.items():
                require(not any(word in key.lower() for word in ("token", "password", "hash", "idempotency")),
                        "FACTS_SECRET_KEY", "事实包含禁止保存的字段")
                check(child)
        elif isinstance(value, list):
            for child in value:
                check(child)
    check(facts)
    facts["checked_time"] = datetime.now().astimezone().isoformat()
    FACTS.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def snapshot(tenants=None, nonempty_members=False):
    """中文业务列用 HEX 读回，防止 Windows/mysql 客户端编码改变比较结果。"""
    department_where = membership_where = ""
    if tenants is not None:
        ids = ",".join(str(int(value)) for value in sorted(set(tenants)))
        department_where = membership_where = " WHERE tenant_id IN (" + ids + ")"
    if nonempty_members:
        membership_where += (" AND" if membership_where else " WHERE") + " department_id IS NOT NULL"
    return {
        "departments": sql("SELECT id,system_id,tenant_id,IFNULL(parent_id,'NULL'),HEX(code),HEX(name),"
                           "HEX(path_code),sort_order,status,version,created_at,updated_at FROM sys_department"
                           + department_where + " ORDER BY id"),
        "memberships": sql("SELECT id,system_id,tenant_id,system_member_id,IFNULL(department_id,'NULL'),"
                           "tenant_admin,status,version,created_at,updated_at FROM sys_tenant_member"
                           + membership_where + " ORDER BY id")}


def roots(tenant):
    return sql("SELECT id,HEX(code),HEX(name),HEX(path_code),status,version FROM sys_department "
               f"WHERE system_id={SYSTEM} AND tenant_id={int(tenant)} AND parent_id IS NULL ORDER BY id")


def creator_membership(tenant, account):
    rows = sql("SELECT tm.id,IFNULL(tm.department_id,'NULL'),tm.version,tm.status FROM sys_tenant_member tm "
               "JOIN sys_member m ON m.id=tm.system_member_id AND m.system_id=tm.system_id "
               f"WHERE tm.system_id={SYSTEM} AND tm.tenant_id={int(tenant)} AND m.account_id={int(account)}")
    require(len(rows) == 1, "CREATOR_MEMBER_MISSING", "随机租户创建人成员读回不唯一")
    return rows[0]


def main():
    prior = json.loads(FACTS.read_text(encoding="utf-8")) if FACTS.exists() else None
    require(not prior or (not prior["fixtures"] and not prior["cases"]), "EXISTING_FACTS", "已有租户根夹具，禁止重复准备；保留原进程并发送 verify")
    facts = {"system_id": SYSTEM, "main_tenant_id": MAIN_TENANT, "recipient_tenant_id": RECIPIENT_TENANT,
             "manual_root_id": MANUAL_ROOT, "business_verification": "in_progress", "fixtures": {}, "cases": []}
    if prior: facts["prior_prepare_failures"] = prior.get("prior_prepare_failures", []) + [prior["error"]]
    nonce = secrets.token_hex(6)
    created, registration = [], None
    platform_admin = owner_admin = member = None
    new_tenant = None
    failed = False
    bootstrap = (JAVA / "platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
    username_match = re.search(r"default-admin\.username:([^}]+)", bootstrap)
    password_match = re.search(r"default-admin\.initial-password:([^}]+)", bootstrap)
    require(username_match and password_match, "ADMIN_DEFAULTS_MISSING", "无法在初始化源码读取默认管理员配置")
    admin_credentials = {"username": username_match.group(1), "password": password_match.group(1)}

    def login_admin():
        return api("POST", "/api/auth/login", admin_credentials)["accessToken"]

    def create_tenant(label):
        tenant = api("POST", "/api/admin/system/tenants", {
            "code": "root_accept_" + nonce + "_" + label, "name": "默认组织验收·" + label}, owner_admin)
        created.append(tenant)
        facts["fixtures"][label] = {"tenant_id": tenant["id"], "code": tenant["code"]}
        save(facts)
        return tenant["id"]

    def create_root(tenant, code):
        tenant_admin = enter(platform_admin, tenant)["tokens"]["accessToken"]
        return api("POST", "/api/admin/system/authorization/departments", {
            "code": code, "name": "默认组织验收根·" + code, "sortOrder": 0}, tenant_admin)

    try:
        require(bool(sql("SELECT version FROM flyway_schema_history WHERE version='42' AND success=1")),
                "PREPARE_REQUIRES_CURRENT_BACKEND", "先部署并确认当前后端完成 V42，再准备限定遗留夹具")
        platform_admin = login_admin()
        owner_entry = enter(platform_admin, MAIN_TENANT)
        owner_admin = owner_entry["tokens"]["accessToken"]
        creator_account = int(sql(f"SELECT account_id FROM sys_member WHERE system_id={SYSTEM} "
                                  f"AND id={int(owner_entry['systemMemberId'])}")[0][0])
        preserved = snapshot([MAIN_TENANT, RECIPIENT_TENANT], nonempty_members=True)
        require(any(int(row[0]) == MANUAL_ROOT and int(row[2]) == RECIPIENT_TENANT for row in preserved["departments"]),
                "MANUAL_ROOT_MISSING", "接收租户人工根75不存在，停止准备")
        facts["preserved_before"] = preserved
        before_deployment = json.loads((AREA / "tenant-root-predeployment-readback.json").read_text(encoding="utf-8"))
        after_deployment = snapshot()
        departments_now = {row[0]: row for row in after_deployment["departments"]}
        members_now = {row[0]: row for row in after_deployment["memberships"]}
        require(all(departments_now.get(row[0]) == row for row in before_deployment["departments"])
                and all(members_now.get(row[0]) == row for row in before_deployment["memberships"]),
                "DEPLOYMENT_CHANGED_EXISTING", "启动迁移改变了已有部门或非空归属")
        facts["deployment_preserved_existing"] = True
        normal = create_tenant("missing")
        original_root = roots(normal)
        require(len(original_root) == 1, "CURRENT_CREATE_ROOT_MISSING", "当前创建租户没有自动初始化根")
        # 只删除本次随机空租户的默认根来模拟已实测的遗留缺根；不删除真实组织或重建表。
        require(sql(f"SELECT COUNT(*) FROM biz_record WHERE system_id={SYSTEM} AND tenant_id={normal}") == [["0"]],
                "FIXTURE_NOT_EMPTY", "遗留模拟租户不为空，禁止修改")
        sql(f"UPDATE sys_tenant_member SET department_id=NULL,version=version+1 WHERE tenant_id={normal} AND system_id={SYSTEM}; "
            f"DELETE FROM sys_department WHERE id={int(original_root[0][0])} AND tenant_id={normal} AND system_id={SYSTEM}")
        old_creator = creator_membership(normal, creator_account)
        require(not roots(normal) and old_creator[1] == "NULL", "LEGACY_FIXTURE_INVALID", "遗留缺根夹具未准备好")
        facts["cases"].append({"case": "legacy_missing_root_fixture", "tenant_id": normal,
                               "no_root": True, "creator_department_empty": True,
                               "original_real_failure": "shared-actor-before-root-department.json"})
        multiple = create_tenant("multiple")
        create_root(multiple, "root_two")
        sql(f"UPDATE sys_tenant_member SET department_id=NULL,version=version+1 WHERE tenant_id={multiple} AND system_id={SYSTEM}")
        disabled = create_tenant("disabled")
        disabled_root = {"id": int(roots(disabled)[0][0])}
        # 唯一组织状态写入严格限定本次随机租户的具体根部门，不影响已有租户。
        sql("UPDATE sys_department d JOIN sys_tenant t ON t.id=d.tenant_id SET d.status='DISABLED',d.version=d.version+1 "
            f"WHERE d.id={int(disabled_root['id'])} AND d.tenant_id={int(disabled)} AND d.system_id={SYSTEM} "
            f"AND t.code='root_accept_{nonce}_disabled'")
        sql(f"UPDATE sys_tenant_member SET department_id=NULL,version=version+1 WHERE tenant_id={disabled} AND system_id={SYSTEM}")
        anomalies = snapshot([multiple, disabled])
        require(len(roots(multiple)) == 2 and creator_membership(multiple, creator_account)[1] == "NULL",
                "MULTIPLE_FIXTURE_INVALID", "多根夹具必须保留创建人空部门")
        require(roots(disabled)[0][4] == "DISABLED" and creator_membership(disabled, creator_account)[1] == "NULL",
                "DISABLED_FIXTURE_INVALID", "停用根夹具必须保留创建人空部门")
        facts["anomalies_before"] = anomalies
        facts["prepare_completed"] = True
        save(facts)
        print(json.dumps({"phase": "prepare_completed", "fixtures": facts["fixtures"]}, ensure_ascii=False), flush=True)
        print("WAITING: send verify for existing mysql migration fixture acceptance; finish only cleans fixtures.", flush=True)
        while True:
            command = input().strip()
            if command == "finish":
                facts["business_verification"] = "aborted_before_verify"
                break
            if command != "verify":
                print("WAITING: verify or finish", flush=True)
                continue
            status, health = call("GET", "/actuator/health")
            if not 200 <= status < 300:
                raise AcceptanceError(health.get("code", "BACKEND_NOT_READY"), health.get("message", "后端尚未就绪"))
            require(health.get("status") == "UP", "BACKEND_NOT_READY", "现有后端健康状态尚未 UP")
            require(bool(sql("SELECT version FROM flyway_schema_history WHERE version='42' AND success=1")),
                    "MIGRATION_NOT_APPLIED", "新后端尚未成功执行 V42")
            applied = subprocess.run(["docker", "exec", "-i", "unexamine-mysql-1", "sh", "-c",
                'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot unexamine'],
                input=MIGRATION.read_text(encoding="utf-8"), capture_output=True, text=True, encoding="utf-8")
            require(applied.returncode == 0, "LEGACY_MIGRATION_FAILED", "现有 mysql 遗留夹具迁移失败")
            platform_admin = login_admin()
            owner_admin = enter(platform_admin, MAIN_TENANT)["tokens"]["accessToken"]
            normal_roots = roots(normal)
            repaired_creator = creator_membership(normal, creator_account)
            require(len(normal_roots) == 1 and normal_roots[0][1:5] == ["726F6F74", "全公司".encode().hex().upper(), "2F726F6F74", "ACTIVE"],
                    "MISSING_ROOT_NOT_REPAIRED", "正常缺根租户未补齐唯一默认根")
            require(repaired_creator[1] == normal_roots[0][0] and int(repaired_creator[2]) == int(old_creator[2]) + 1,
                    "CREATOR_NOT_REPAIRED", "有效创建人空部门未补齐或版本未递增一次")
            current = snapshot([MAIN_TENANT, RECIPIENT_TENANT])
            current_nonempty = {row[0]: row for row in current["memberships"]}
            require(current["departments"] == preserved["departments"] and all(current_nonempty.get(row[0]) == row for row in preserved["memberships"]),
                    "EXISTING_ORGANIZATION_CHANGED", "人工根或已有非空成员归属发生变化")
            require(snapshot([multiple, disabled]) == anomalies, "ANOMALOUS_ROOT_ASSIGNED", "多根或停用根被修改或任意作为默认部门")
            facts["cases"].append({"case": "v42_incremental_repair", "root_department_id": int(normal_roots[0][0]),
                                   "creator_member_id": int(repaired_creator[0]), "version_increment_once": True,
                                   "manual_root_and_nonempty_assignments_preserved": True,
                                   "multiple_and_disabled_roots_unchanged": True})
            new_tenant = create_tenant("new")
            automatic = roots(new_tenant)
            require(len(automatic) == 1 and automatic[0][1:5] == normal_roots[0][1:5]
                    and creator_membership(new_tenant, creator_account)[1] == automatic[0][0],
                    "NEW_TENANT_DEFAULT_MISSING", "新公开创建租户未自动建根并绑定创建人")
            username = "tenant_root_actor_" + nonce
            registration = api("POST", "/api/auth/register", {"username": username,
                "password": secrets.token_urlsafe(24), "displayName": "默认组织普通成员验收",
                "systemName": "默认组织验收空注册系统"})
            facts["registration"] = {"account_id": registration["accountId"], "system_id": registration["systemId"],
                                     "tenant_id": registration["tenantId"], "email_or_sms_used": False}
            registration_snapshot = snapshot([registration["tenantId"]])
            registered_roots = [row for row in registration_snapshot["departments"] if row[3] == "NULL"]
            registered_members = registration_snapshot["memberships"]
            main_identity = sql("SELECT is_main,HEX(code),status FROM sys_tenant "
                                f"WHERE id={int(registration['tenantId'])} AND system_id={int(registration['systemId'])}")
            require(main_identity == [["1", "6D61696E", "ACTIVE"]]
                    and len(registered_roots) == 1 and registered_roots[0][4:7] == normal_roots[0][1:4]
                    and registered_roots[0][8] == "ACTIVE"
                    and len(registered_members) == 1 and registered_members[0][4] == registered_roots[0][0],
                    "MAIN_BOOTSTRAP_REGRESSION", "注册所得主租户根部门或创建人归属回归失败")
            new_admin = enter(platform_admin, new_tenant)["tokens"]["accessToken"]
            # 未传 departmentId/manager/条件 JSON，按真实接口的默认部门契约开通普通成员。
            member = api("POST", "/api/admin/system/authorization/members", {"account": username, "roleIds": []}, new_admin)
            require(member["accountId"] == registration["accountId"] and str(member["departmentId"]) == automatic[0][0],
                    "DEFAULT_MEMBER_DEPARTMENT_MISSING", "默认添加普通成员没有使用新租户根部门")
            persisted_member = creator_membership(new_tenant, registration["accountId"])
            require(str(member["tenantMemberId"]) == persisted_member[0] and persisted_member[1] == automatic[0][0],
                    "MEMBER_NOT_PERSISTED", "公开添加成员结果未持久化")
            actor_entry = enter(registration["platformTokens"]["accessToken"], new_tenant)
            require(actor_entry["tenantMemberId"] == member["tenantMemberId"], "ACTOR_ENTER_FAILED", "普通成员不能进入开通租户")
            facts["cases"].append({"case": "public_new_tenant_default_onboarding", "tenant_id": new_tenant,
                                   "root_department_id": int(automatic[0][0]), "ordinary_member_id": member["tenantMemberId"],
                                   "default_add_member_persisted": True, "ordinary_member_entered": True,
                                   "registration_main_bootstrap_preserved": True})
            before_repeat = snapshot()
            # 同一现有 mysql 客户端 stdin 再执行迁移；不创建容器、脚本临时 SQL 文件或暴露环境凭据。
            repeated = subprocess.run(["docker", "exec", "-i", "unexamine-mysql-1", "sh", "-c",
                'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot unexamine'],
                input=MIGRATION.read_text(encoding="utf-8"), capture_output=True, text=True, encoding="utf-8")
            require(repeated.returncode == 0, "MIGRATION_REPEAT_FAILED", "迁移 stdin 再次执行失败，不输出环境命令或客户端错误正文")
            require(snapshot() == before_repeat, "MIGRATION_NOT_IDEMPOTENT", "重复迁移改变了部门或成员快照")
            facts["cases"].append({"case": "migration_repeat_stdin", "all_department_and_membership_rows_unchanged": True})
            facts["verify_completed"] = True
            facts["business_verification"] = "verified"
            save(facts)
            print(json.dumps({"phase": "verify_completed", "cases": [value["case"] for value in facts["cases"]]}, ensure_ascii=False), flush=True)
            break
    except (Exception, KeyboardInterrupt) as error:
        failed = True
        facts["business_verification"] = "failed"
        facts["error"] = safe_error(error)
        print(json.dumps(facts["error"], ensure_ascii=False), flush=True)
    finally:
        cleanup = {"audit_preserved": True, "tenants": [], "errors": []}
        # 每类收尾独立执行；某个公共接口失败不阻断随机账号/会话/空系统的安全收尾。
        if registration:
            try:
                account, system = int(registration["accountId"]), int(registration["systemId"])
                sql("START TRANSACTION; DELETE mr FROM sys_member_role mr JOIN sys_tenant_member tm ON tm.id=mr.tenant_member_id "
                    "JOIN sys_member m ON m.id=tm.system_member_id JOIN plat_account a ON a.id=m.account_id "
                    f"WHERE a.id={account} AND a.username='{username}'; "
                    "UPDATE auth_session s JOIN plat_account a ON a.id=s.account_id "
                    "SET s.revoked=1,s.revoked_reason='tenant-root-acceptance-finished' "
                    f"WHERE a.id={account} AND a.username='{username}'; "
                    f"UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={account} AND username='{username}'; COMMIT;")
                cleanup["account_disabled"] = sql(f"SELECT status FROM plat_account WHERE id={account}") == [["DISABLED"]]
                cleanup["sessions_revoked"] = sql(f"SELECT COUNT(*) FROM auth_session WHERE account_id={account} AND revoked=0") == [["0"]]
                cleanup["random_account_roles_removed"] = sql("SELECT COUNT(*) FROM sys_member_role mr "
                    "JOIN sys_tenant_member tm ON tm.id=mr.tenant_member_id JOIN sys_member m ON m.id=tm.system_member_id "
                    f"WHERE m.account_id={account}") == [["0"]]
                require(all(cleanup[key] for key in ("account_disabled", "sessions_revoked", "random_account_roles_removed")),
                        "IDENTITY_CLEANUP_INCOMPLETE", "随机身份收尾读回不完整")
            except (Exception, KeyboardInterrupt) as error:
                cleanup["errors"].append(safe_error(error))
            try:
                system_code = registration["systemCode"]
                require(re.fullmatch(r"[a-z][a-z0-9_-]{1,99}", system_code), "CLEANUP_SCOPE_INVALID", "空注册系统编码不符合限定范围")
                require(int(sql(f"SELECT COUNT(*) FROM biz_record WHERE system_id={system}")[0][0]) == 0,
                        "REGISTRATION_SYSTEM_NOT_EMPTY", "注册系统存在业务数据，禁止自动禁用该系统")
                sql(f"UPDATE sys_system SET status='DISABLED',version=version+1 WHERE id={system} AND creator_account_id={account} "
                    f"AND code='{system_code}' AND NOT EXISTS (SELECT 1 FROM biz_record r WHERE r.system_id={system})")
                cleanup["registration_system_disabled"] = sql(f"SELECT status FROM sys_system WHERE id={system}") == [["DISABLED"]]
                require(cleanup["registration_system_disabled"], "SYSTEM_CLEANUP_INCOMPLETE", "随机空注册系统停用读回失败")
            except (Exception, KeyboardInterrupt) as error:
                cleanup["errors"].append(safe_error(error))
        for tenant in created:
            try:
                require(tenant["code"].startswith("root_accept_" + nonce + "_"), "TENANT_CLEANUP_SCOPE_INVALID", "仅允许禁用本次随机租户")
                if platform_admin:
                    owner_admin = enter(platform_admin, MAIN_TENANT)["tokens"]["accessToken"]
                result = api("PUT", f"/api/admin/system/tenants/{int(tenant['id'])}/status", {"status": "DISABLED"}, owner_admin)
                require(result["status"] == "DISABLED" and result["id"] == tenant["id"], "TENANT_CLEANUP_FAILED", "随机租户停用结果未读回")
                cleanup["tenants"].append({"tenant_id": tenant["id"], "disabled": True})
            except (Exception, KeyboardInterrupt) as error:
                cleanup["errors"].append(safe_error(error))
        facts["cleanup"] = cleanup
        if cleanup["errors"]:
            failed = True
            facts["business_verification"] = "failed_cleanup"
            for error in cleanup["errors"]:
                print(json.dumps(error, ensure_ascii=False), flush=True)
        save(facts)
        print(json.dumps({"phase": "cleanup_completed", "business_verification": facts["business_verification"],
                          "disabled_tenant_count": len(cleanup["tenants"]), "cleanup_error_count": len(cleanup["errors"])}, ensure_ascii=False), flush=True)
    return 1 if failed else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (Exception, KeyboardInterrupt) as error:
        print(json.dumps(safe_error(error), ensure_ascii=False), flush=True)
        sys.exit(1)
