"""在已有隔离系统核对权限预览与成员实际模块访问，并恢复临时角色变更。"""

import copy
import json
import runpy
import subprocess
import sys
import time
import urllib.request
from pathlib import Path


health_fixture = runpy.run_path(str(Path(__file__).with_name("verify-operations-health.py")))
fixture = health_fixture["fixture"]
enter = health_fixture["token"]
BASE = "/api/admin/system/authorization"
MODULE = "g8_contract"


def preview(token, member_id, version, action):
    return fixture.expected(BASE + "/preview", "POST", {
        "tenantMemberId": member_id, "resourceCode": MODULE,
        "actionCode": action, "expectedPermissionVersion": version}, token)


def action(result, expected_scope, allowed):
    decisions = result["actions"]
    if len(decisions) != 1 or decisions[0]["allowed"] != allowed:
        raise AssertionError(f"预览动作结果与预期不一致：{decisions}")
    if allowed and [term["type"] for term in decisions[0]["dataScope"]["terms"]] != [expected_scope]:
        raise AssertionError(f"预览数据范围错误：{decisions[0]['dataScope']}")


def record_ids(token):
    result = fixture.expected(f"/api/runtime/modules/{MODULE}/records", token=token)
    return [item["id"] for item in result["records"]]


def assert_menus(preview_result, member_token):
    actual = fixture.expected("/api/runtime/modules", token=member_token)
    if preview_result["visibleMenus"] != actual:
        raise AssertionError(f"预览菜单与成员实际目录不同：{preview_result['visibleMenus']} / {actual}")


def role_now(token, role_id):
    overview = fixture.expected(BASE, token=token)
    return overview, next(item for item in overview["roles"] if item["id"] == role_id)


def save_and_publish(token, role, permissions, reason):
    draft = fixture.expected(BASE + "/roles", "POST", {
        "id": role["id"], "code": role["code"], "name": role["name"],
        "description": role.get("description"), "permissions": permissions,
        "fieldPolicies": role["fieldPolicies"], "expectedVersion": role["version"]}, token)
    if not draft["draftPending"]:
        raise AssertionError("已发布角色编辑未保存为草稿")
    return fixture.expected(f"{BASE}/roles/{role['id']}/publish", "POST", {
        "reason": reason, "expectedVersion": draft["version"]}, token)


def main():
    system_id = int(sys.argv[1]) if len(sys.argv) > 1 else 151
    owner = enter("g8_owner_75ca9359", system_id)
    viewer = enter("g8_viewer_75ca9359", system_id)
    outsider = enter("g8_outsider_75ca9359", system_id)
    overview = fixture.expected(BASE, token=owner)
    version = overview["permissionVersion"]
    role = next(item for item in overview["roles"] if item["code"] == "g8_metric_viewer")
    owner_id, viewer_id, outsider_id = 226, 228, 230

    owner_result = preview(owner, owner_id, version, "LIST")
    assert_menus(owner_result, owner)
    action(owner_result, "ALL", True)
    owner_field = owner_result["fields"]
    if not owner_result["systemOwner"] or owner_result["roles"] or len(owner_field) != 1 \
            or not owner_field[0]["readable"] or not owner_field[0]["writable"]:
        raise AssertionError("系统所属人预览没有体现隐式动作、字段和全部数据权限")
    viewer_result = preview(owner, viewer_id, version, "LIST")
    assert_menus(viewer_result, viewer)
    action(viewer_result, "SELF", True)
    if viewer_result["systemOwner"] or len(viewer_result["roles"]) != 1 \
            or len(viewer_result["fields"]) != 1 \
            or not viewer_result["fields"][0]["readable"] or viewer_result["fields"][0]["writable"]:
        raise AssertionError("查看员的角色与字段预览不符合已发布授权")
    action(preview(owner, viewer_id, version, "CREATE"), None, False)
    outsider_result = preview(owner, outsider_id, version, "LIST")
    action(outsider_result, None, False)
    assert_menus(outsider_result, outsider)
    if len(record_ids(owner)) < 3 or record_ids(viewer):
        raise AssertionError("所属人或 SELF 查看员的实际列表范围错误")
    if MODULE not in [item["moduleCode"] for item in fixture.expected("/api/runtime/modules", token=viewer)] \
            or fixture.expected("/api/runtime/modules", token=outsider):
        raise AssertionError("模块菜单与列表动作预览不一致")
    configuration = fixture.expected(f"/api/runtime/modules/{MODULE}/configuration", token=viewer)
    fields = configuration["configuration"]["fields"]
    actions = {item["code"] for item in configuration["configuration"]["actions"]}
    if len(fields) != 1 or not fields[0]["access"]["readable"] \
            or fields[0]["access"]["writable"] or actions != {"LIST", "DETAIL"}:
        raise AssertionError("查看员实际页面字段或按钮与预览不一致")
    for token, path, method, body in (
            (viewer, f"/api/runtime/modules/{MODULE}/records/130", "GET", None),
            (viewer, f"/api/runtime/modules/{MODULE}/records", "POST", {
                "title": "不应创建", "status": "ACTIVE", "participantMemberIds": [],
                "fields": {"amount": 1}}),
            (outsider, f"/api/runtime/modules/{MODULE}/records", "GET", None),
            (viewer, BASE + "/preview", "POST", {
                "tenantMemberId": outsider_id, "resourceCode": MODULE,
                "actionCode": "LIST", "expectedPermissionVersion": version})):
        status, _ = fixture.request(path, method, body, token)
        if status != 403:
            raise AssertionError(f"未授权动作或权限预览未拒绝：{method} {path} -> {status}")

    original_permissions = copy.deepcopy(role["permissions"])
    widened_version = None
    restored_version = None
    try:
        widened = copy.deepcopy(original_permissions)
        for item in widened:
            item["dataScopeType"] = "ALL"
        draft = fixture.expected(BASE + "/roles", "POST", {
            "id": role["id"], "code": role["code"], "name": role["name"],
            "description": role.get("description"), "permissions": widened,
            "fieldPolicies": role["fieldPolicies"], "expectedVersion": role["version"]}, owner)
        action(preview(owner, viewer_id, version, "LIST"), "SELF", True)
        if record_ids(viewer):
            raise AssertionError("未发布角色草稿提前扩张了成员数据权限")
        fixture.expected(f"{BASE}/roles/{role['id']}/publish", "POST", {
            "reason": "G10 临时验证权限预览即时更新", "expectedVersion": draft["version"]}, owner)
        widened_version = fixture.expected(BASE, token=owner)["permissionVersion"]
        action(preview(owner, viewer_id, widened_version, "LIST"), "ALL", True)
        assert_menus(preview(owner, viewer_id, widened_version, "LIST"), viewer)
        if len(record_ids(viewer)) < 3:
            raise AssertionError("发布 ALL 范围后已有成员会话未取得合同记录")
        status, error = fixture.request(BASE + "/preview", "POST", {
            "tenantMemberId": viewer_id, "resourceCode": MODULE,
            "actionCode": "LIST", "expectedPermissionVersion": version}, owner)
        if status != 409 or error.get("code") != "AUTHORIZATION_VERSION_EXPIRED":
            raise AssertionError(f"旧授权版本没有阻止误导性预览：{status}/{error}")
    finally:
        current_overview, current_role = role_now(owner, role["id"])
        if current_role["permissions"] != original_permissions or current_role["draftPending"]:
            save_and_publish(owner, current_role, original_permissions, "G10 恢复查看员 SELF 范围")
        restored_version = fixture.expected(BASE, token=owner)["permissionVersion"]
    restored_preview = preview(owner, viewer_id, restored_version, "LIST")
    action(restored_preview, "SELF", True)
    assert_menus(restored_preview, viewer)
    if record_ids(viewer):
        raise AssertionError("恢复 SELF 范围后仍可读其他负责人的记录")

    if "--restart" in sys.argv:
        subprocess.run(["docker", "restart", "unexamine-g1-preview"], check=True, capture_output=True)
        for _ in range(70):
            try:
                with urllib.request.urlopen(fixture.BASE + "/actuator/health", timeout=2) as response:
                    if json.loads(response.read()).get("status") == "UP":
                        break
            except OSError:
                pass
            time.sleep(0.5)
        else:
            raise AssertionError("重启后权限预览服务未恢复")
        owner = enter("g8_owner_75ca9359", system_id)
        viewer = enter("g8_viewer_75ca9359", system_id)
        restarted_preview = preview(owner, viewer_id, restored_version, "LIST")
        action(restarted_preview, "SELF", True)
        assert_menus(restarted_preview, viewer)
        if record_ids(viewer):
            raise AssertionError("重启后查看员 SELF 范围失效")
    print(json.dumps({"systemId": system_id, "moduleCode": MODULE,
                      "ownerMemberId": owner_id, "viewerMemberId": viewer_id,
                      "initialVersion": version, "widenedVersion": widened_version,
                      "restoredVersion": restored_version,
                      "restartVerified": "--restart" in sys.argv}, ensure_ascii=False))


if __name__ == "__main__":
    main()
