"""在隔离系统验证打印模板发布、普通成员 PDF 与权限撤销。"""

import importlib.util
import json
import secrets
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path


spec = importlib.util.spec_from_file_location("fixture", Path(__file__).with_name("verify-external-flow.py"))
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)


def denied(path, method, body, token, status, code):
    actual_status, response = fixture.request(path, method, body, token)
    if actual_status != status or response.get("code") != code:
        raise AssertionError(f"{method} {path}: expected {status}/{code}, got {actual_status}/{response}")


def pdf(path, token):
    request = urllib.request.Request(fixture.BASE + path, headers={"Authorization": "Bearer " + token})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status, response.headers, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.headers, error.read()


def main():
    suffix = secrets.token_hex(4)
    code = "g7_print_order"
    names = [f"g7_print_{kind}_{suffix}" for kind in ("owner", "member", "other")]
    accounts = [fixture.expected("/api/auth/register", "POST", {
        "username": name, "password": "correct-password", "displayName": f"G7 {kind}",
        "email": name + "@example.com", "systemName": f"G7 {kind}系统",
        "systemCode": f"g7_print_{index}_{suffix}"})
        for index, (name, kind) in enumerate(zip(names, ("管理员", "成员", "其他")))]
    owner = accounts[0]
    token = owner["tokens"]["accessToken"]
    system_id = owner["systemId"]

    def enter(name):
        platform = fixture.expected("/api/auth/login", "POST", {
            "username": name, "password": "correct-password"})
        return fixture.expected(f"/api/systems/{system_id}/enter", "POST", {},
                                platform["accessToken"])["tokens"]["accessToken"]

    group = fixture.expected("/api/admin/module-config/groups", "POST", {
        "code": "g7_order_group", "name": "订单管理", "sortOrder": 10}, token)
    module = fixture.expected("/api/admin/module-config/modules", "POST", {
        "groupId": group["id"], "code": code, "name": "订单"}, token)
    module_id = module["module"]["id"]
    for index, (field_code, label) in enumerate((("public_code", "公开编号"),
                                                  ("private_note", "内部备注"))):
        fixture.expected(f"/api/admin/module-config/modules/{module_id}/fields", "POST", {
            "code": field_code, "name": label, "fieldType": "TEXT", "required": False,
            "uniqueValue": False, "searchable": True, "sortOrder": index * 10 + 10,
            "config": {}}, token)
    # 打印沿用模块动作发布链路，模板单独发布不会自动开放记录打印。
    fixture.expected(f"/api/admin/module-config/modules/{module_id}/actions/print", "POST", token=token)
    draft = fixture.expected(f"/api/admin/module-config/modules/{module_id}/draft", token=token)
    fixture.expected(f"/api/admin/module-config/modules/{module_id}/publish", "POST", {
        "expectedDraftRevision": draft["module"]["draftRevision"]}, token)

    def grant(action):
        return {"resourceType": "MODULE", "resourceCode": code, "actionCode": action,
                "dataScopeType": "SELF", "dataScopeJson": "{}"}

    def policies(public_file=True):
        return [{"resourceCode": code, "fieldCode": field_code, "channel": channel,
                 "readable": field_code == "public_code" and (channel == "PAGE" or public_file)
                 or field_code == "private_note" and channel == "PAGE",
                 "writable": False, "maskStrategy": None}
                for field_code in ("public_code", "private_note") for channel in ("PAGE", "FILE")]

    def save_role(role_id=None, print_action=True, public_file=True, version=None):
        body = {"code": "g7_print_member", "name": "订单打印成员",
                "permissions": [grant(action) for action in
                                (["LIST", "DETAIL", "PRINT"] if print_action else ["LIST", "DETAIL"])],
                "fieldPolicies": policies(public_file)}
        if role_id:
            body.update({"id": role_id, "expectedVersion": version})
        saved = fixture.expected("/api/admin/system/authorization/roles", "POST", body, token)
        return fixture.expected(f"/api/admin/system/authorization/roles/{saved['id']}/publish", "POST", {
            "reason": "G7 打印权限范围验证", "expectedVersion": saved["version"]}, token)

    role = save_role()
    member = fixture.expected("/api/admin/system/authorization/members", "POST", {
        "account": names[1], "roleIds": [role["id"]]}, token)
    other = fixture.expected("/api/admin/system/authorization/members", "POST", {
        "account": names[2], "roleIds": [role["id"]]}, token)
    records = "/api/runtime/modules/" + code + "/records"
    first = fixture.expected(records, "POST", {
        "title": "成员订单", "status": "ACTIVE", "ownerMemberId": member["systemMemberId"],
        "participantMemberIds": [],
        "fields": {"public_code": "PUB-001", "private_note": "DO_NOT_PRINT_789"}}, token)
    second = fixture.expected(records, "POST", {
        "title": "其他成员订单", "status": "ACTIVE", "ownerMemberId": other["systemMemberId"],
        "participantMemberIds": [],
        "fields": {"public_code": "PUB-002", "private_note": "OTHER_PRIVATE_123"}}, token)
    member_token = enter(names[1])
    other_token = enter(names[2])
    print_base = f"/api/print/runtime/modules/{code}"
    layout = {"header": "G7 订单打印", "footer": "仅用于订单核对", "signatureLabel": "经办人",
              "fieldCodes": ["public_code", "private_note"], "detailFieldCodes": [],
              "rowsPerPage": 4, "offsetXmm": 0, "offsetYmm": 0}
    template = fixture.expected("/api/print/admin/templates", "POST", {
        "moduleId": module_id, "code": "g7_order_print", "name": "订单打印单",
        "pageSize": "A4", "orientation": "PORTRAIT", "layout": layout}, token)
    template_id = template["id"]
    if fixture.expected(print_base + "/templates", token=member_token):
        raise AssertionError("草稿出现在普通成员的已发布打印模板列表")
    print_path = f"{print_base}/records/{first['id']}/templates/{template_id}"
    denied(print_path, "POST", {}, member_token, 409, "PRINT_TEMPLATE_NOT_PUBLISHED")
    preview = fixture.expected(f"/api/print/admin/templates/{template_id}/preview", "POST", {
        "sampleRecordId": first["id"]}, token)
    if preview["visibleFieldCodes"] != ["public_code", "private_note"]:
        raise AssertionError(f"管理员草稿真实记录预览错误：{preview}")
    published = fixture.expected(f"/api/print/admin/templates/{template_id}/publish", "POST", {
        "expectedDraftRevision": template["draftRevision"]}, token)
    templates = fixture.expected(print_base + "/templates", token=member_token)
    if len(templates) != 1 or templates[0]["versionId"] != published["versionId"]:
        raise AssertionError(f"普通成员未取得已发布模板：{templates}")
    if templates[0]["layout"]["fieldCodes"] != ["public_code"]:
        raise AssertionError("打印模板目录泄露了文件渠道无权字段")
    denied(f"{print_base}/records/{second['id']}/templates/{template_id}",
           "POST", {}, member_token, 403, "PERMISSION_DENIED")
    job = fixture.expected(print_path, "POST", {}, member_token)
    if job["status"] != "SUCCEEDED" or job["pageCount"] != 1:
        raise AssertionError(f"普通成员打印未完成：{job}")
    visible = [field["code"] for page in job["pages"] for field in page["fields"]]
    if visible != ["public_code"] or "DO_NOT_PRINT_789" in json.dumps(job, ensure_ascii=False) \
            or "private_note" in json.dumps(job, ensure_ascii=False):
        raise AssertionError("打印快照泄露了文件渠道无权字段")
    for path, disposition in ((job["previewPath"], "inline"),
                              (job["downloadPath"], "attachment")):
        status, headers, content = pdf(path, member_token)
        if status != 200 or not content.startswith(b"%PDF-") or disposition not in headers["Content-Disposition"]:
            raise AssertionError(f"PDF 响应异常：{status}/{headers}/{content[:50]}")
        if "DO_NOT_PRINT_789".encode("utf-16be").hex().upper().encode() in content:
            raise AssertionError("PDF 输出了无权字段")
    denied(job["downloadPath"], "GET", None, other_token, 404, "PRINT_JOB_NOT_FOUND")

    # 已生成文件也必须服从当前字段权限；旧文件不能绕过后续撤权。
    role = save_role(role["id"], public_file=False, version=role["version"])
    revoked_token = enter(names[1])
    denied(job["previewPath"], "GET", None, revoked_token, 403,
           "PRINT_JOB_FIELD_ACCESS_REVOKED")
    denied(job["downloadPath"], "GET", None, revoked_token, 403,
           "PRINT_JOB_FIELD_ACCESS_REVOKED")
    denied(f"/api/print/jobs/{job['id']}", "GET", None, revoked_token, 403,
           "PRINT_JOB_FIELD_ACCESS_REVOKED")
    role = save_role(role["id"], print_action=False, public_file=True, version=role["version"])
    denied(print_base + "/templates", "GET", None, enter(names[1]), 403, "PERMISSION_DENIED")
    role = save_role(role["id"], public_file=True, version=role["version"])
    restored_token = enter(names[1])
    if pdf(job["downloadPath"], restored_token)[0] != 200:
        raise AssertionError("恢复字段与打印权限后历史 PDF 仍不可用")

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
            raise AssertionError("打印验收重启后服务未恢复")
        overview = fixture.expected("/api/print/admin", token=enter(names[0]))
        persisted = next(item for item in overview["templates"] if item["id"] == template_id)
        if persisted["currentVersionId"] != published["versionId"]:
            raise AssertionError("重启后发布版本指针丢失")
        if pdf(job["downloadPath"], enter(names[1]))[0] != 200:
            raise AssertionError("重启后历史 PDF 文件不可用")

    print(json.dumps({"systemId": system_id, "ownerUsername": names[0],
                      "memberUsername": names[1], "moduleId": module_id,
                      "templateId": template_id, "templateVersionId": published["versionId"],
                      "recordId": first["id"], "otherRecordId": second["id"],
                      "printJobId": job["id"], "visibleFieldCodes": visible,
                      "restartVerified": "--restart" in sys.argv}, ensure_ascii=False))


if __name__ == "__main__":
    main()
