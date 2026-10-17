"""在隔离系统验证业务记录的归档、回收站和负责人范围。"""

import json
import runpy
import sys
from pathlib import Path


health = runpy.run_path(str(Path(__file__).with_name("verify-operations-health.py")))
fixture = health["fixture"]
enter = health["token"]
SYSTEM_ID = 151
MODULE_ID = 73
MODULE = "g8_contract"
ROLE_CODE = "g8_metric_viewer"
VIEWER_MEMBER_ID = 227


def role(token):
    rows = fixture.expected("/api/admin/system/authorization", token=token)["roles"]
    return next(item for item in rows if item["code"] == ROLE_CODE)


def publish_role(token, current, permissions, reason):
    draft = fixture.expected("/api/admin/system/authorization/roles", "POST", {
        "id": current["id"], "code": current["code"], "name": current["name"],
        "description": current.get("description"), "permissions": permissions,
        "fieldPolicies": current["fieldPolicies"], "expectedVersion": current["version"]}, token)
    fixture.expected(f"/api/admin/system/authorization/roles/{current['id']}/publish", "POST", {
        "reason": reason, "expectedVersion": draft["version"]}, token)


def setup():
    owner = enter("g8_owner_75ca9359", SYSTEM_ID)
    draft = fixture.expected(f"/api/admin/module-config/modules/{MODULE_ID}/draft", token=owner)
    archive = next(item for item in draft["actions"] if item["code"] == "ARCHIVE")
    if archive["location"] != "BATCH":
        fixture.expected(f"/api/admin/module-config/modules/{MODULE_ID}/actions/ARCHIVE", "PUT", {
            "location": "BATCH", "config": json.loads(archive["configJson"]), "version": archive["version"]}, owner)
        draft = fixture.expected(f"/api/admin/module-config/modules/{MODULE_ID}/draft", token=owner)
        fixture.expected(f"/api/admin/module-config/modules/{MODULE_ID}/publish", "POST", {
            "expectedDraftRevision": draft["module"]["draftRevision"]}, owner)
    current = role(owner)
    permissions = list(current["permissions"])
    for action in ("ARCHIVE", "DELETE", "RESTORE"):
        if not any(item["actionCode"] == action and item["resourceCode"] == MODULE for item in permissions):
            permissions.append({"resourceType": "MODULE", "resourceCode": MODULE,
                                "actionCode": action, "dataScopeType": "SELF", "dataScopeJson": "{}"})
    if permissions != current["permissions"]:
        publish_role(owner, current, permissions, "G11 隔离验证查看员的记录生命周期动作")
    records = []
    for index, amount in enumerate((411, 422, 433), 1):
        saved = fixture.expected(f"/api/runtime/modules/{MODULE}/records", "POST", {
            "title": f"G11 生命周期合同 {index}", "status": "ACTIVE",
            "ownerMemberId": VIEWER_MEMBER_ID, "participantMemberIds": [],
            "fields": {"amount": amount}}, owner)
        records.append({"id": saved["id"], "version": saved["version"], "title": saved["title"]})
    viewer = enter("g8_viewer_75ca9359", SYSTEM_ID)
    listed = fixture.expected(f"/api/runtime/modules/{MODULE}/records", token=viewer)["records"]
    if not set(item["id"] for item in records).issubset(item["id"] for item in listed):
        raise AssertionError("查看员本人负责的新记录未进入正常列表")
    print(json.dumps({"records": records, "roleId": current["id"],
                      "moduleId": MODULE_ID, "moduleCode": MODULE}, ensure_ascii=False))


def list_ids(token, lifecycle):
    rows = fixture.expected(f"/api/runtime/modules/{MODULE}/records?lifecycleState={lifecycle}", token=token)
    return {item["id"] for item in rows["records"]}


def verify():
    viewer = enter("g8_viewer_75ca9359", SYSTEM_ID)
    outsider = enter("g8_outsider_75ca9359", SYSTEM_ID)
    if not {133, 134}.issubset(list_ids(viewer, "ACTIVE")) or 135 not in list_ids(viewer, "ARCHIVED"):
        raise AssertionError("页面操作后的正常和归档列表与 API 不一致")
    if list_ids(viewer, "DELETED"):
        raise AssertionError("回收站恢复后仍有删除记录")
    for token, path, method, body in (
            (outsider, f"/api/runtime/modules/{MODULE}/records?lifecycleState=ARCHIVED", "GET", None),
            (outsider, f"/api/runtime/modules/{MODULE}/records/133/archive", "POST", {"version": 2, "reason": "无权"}),
            (viewer, f"/api/runtime/modules/{MODULE}/records/130/archive", "POST", {"version": 0, "reason": "越过负责人范围"})):
        status, _ = fixture.request(path, method, body, token)
        if status != 403:
            raise AssertionError(f"无权列表或归档未拒绝：{method} {path} -> {status}")
    status, error = fixture.request(f"/api/runtime/modules/{MODULE}/records/135/restore", "POST",
                                    {"version": 0, "reason": "旧版本"}, viewer)
    if status != 409 or error.get("code") != "RECORD_VERSION_CONFLICT":
        raise AssertionError(f"过期记录版本未拒绝：{status}/{error}")
    restored_archive = fixture.expected(f"/api/runtime/modules/{MODULE}/records/135/restore", "POST",
                                        {"version": 1, "reason": "G11 验证恢复清理归档时间"}, viewer)
    current = fixture.expected(f"/api/runtime/modules/{MODULE}/records/133", token=viewer)
    archived = fixture.expected(f"/api/runtime/modules/{MODULE}/records/133/archive", "POST",
                                {"version": current["version"], "reason": "G11 验证恢复清理删除时间"}, viewer)
    restored_delete = fixture.expected(f"/api/runtime/modules/{MODULE}/records/133/restore", "POST",
                                       {"version": archived["version"], "reason": "G11 清理历史生命周期时间"}, viewer)
    if any(item["id"] in list_ids(viewer, "ARCHIVED") for item in (restored_archive, restored_delete)):
        raise AssertionError("恢复后的记录仍在归档列表")
    if not {133, 134, 135}.issubset(list_ids(viewer, "ACTIVE")):
        raise AssertionError("恢复后正常列表缺少测试记录")
    print(json.dumps({"systemId": SYSTEM_ID, "recordIds": [133, 134, 135],
                      "archiveRestoreVersion": restored_archive["version"],
                      "deleteRestoreVersion": restored_delete["version"],
                      "outsiderDenied": True, "staleVersionDenied": True}, ensure_ascii=False))


def readback():
    viewer = enter("g8_viewer_75ca9359", SYSTEM_ID)
    outsider = enter("g8_outsider_75ca9359", SYSTEM_ID)
    active, archived, deleted = (list_ids(viewer, state) for state in ("ACTIVE", "ARCHIVED", "DELETED"))
    if not {133, 134, 135}.issubset(active) or not {136, 137}.issubset(archived) or deleted:
        raise AssertionError(f"重启后生命周期列表错误：{active}/{archived}/{deleted}")
    for record_id in (133, 134, 135):
        record = fixture.expected(f"/api/runtime/modules/{MODULE}/records/{record_id}", token=viewer)
        if record["archived"] or record["deleted"] or record.get("archivedAt") or record.get("deletedAt"):
            raise AssertionError(f"恢复后的状态或时间字段残留：{record_id}")
    status, _ = fixture.request(f"/api/runtime/modules/{MODULE}/records?lifecycleState=ARCHIVED", token=outsider)
    if status != 403:
        raise AssertionError(f"无角色成员重启后可读归档列表：{status}")
    print(json.dumps({"systemId": SYSTEM_ID, "activeTestRecords": sorted(active & {133, 134, 135}),
                      "archivedTestRecords": sorted(archived & {136, 137}),
                      "recycleTestRecords": sorted(deleted), "outsiderDenied": True}, ensure_ascii=False))


if __name__ == "__main__":
    if len(sys.argv) < 2 or sys.argv[1] not in ("setup", "verify", "readback"):
        raise SystemExit("usage: verify-record-archive.py setup|verify|readback")
    if sys.argv[1] == "setup":
        setup()
    elif sys.argv[1] == "verify":
        verify()
    else:
        readback()
