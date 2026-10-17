"""在隔离系统验证 A5 横向长文本、明细分页和打印脱敏。"""

import importlib.util
import json
import re
import secrets
import subprocess
import sys
import time
import urllib.request
from pathlib import Path


spec = importlib.util.spec_from_file_location("fixture", Path(__file__).with_name("verify-external-flow.py"))
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)


def pdf(path, token):
    request = urllib.request.Request(fixture.BASE + path, headers={"Authorization": "Bearer " + token})
    with urllib.request.urlopen(request, timeout=30) as response:
        data = response.read()
        if response.status != 200 or not data.startswith(b"%PDF-"):
            raise AssertionError("PDF 文件下载失败")
        return data


def content_positions(data):
    return [(int(y), bytes.fromhex(value.decode()).decode("utf-16be"))
            for y, value in re.findall(rb"-?\d+ (-?\d+) Td <([0-9A-F]+)> Tj", data)]


def main():
    suffix = secrets.token_hex(4)
    username = "g7_layout_owner_" + suffix
    owner = fixture.expected("/api/auth/register", "POST", {
        "username": username, "password": "correct-password", "displayName": "G7 排版管理员",
        "email": username + "@example.com", "systemName": "G7 排版系统",
        "systemCode": "g7_layout_" + suffix})
    token = owner["tokens"]["accessToken"]
    group = fixture.expected("/api/admin/module-config/groups", "POST", {
        "code": "g7_layout_group", "name": "排版业务", "sortOrder": 10}, token)
    module = fixture.expected("/api/admin/module-config/modules", "POST", {
        "groupId": group["id"], "code": "g7_layout", "name": "排版订单"}, token)
    module_id = module["module"]["id"]

    def field(code, name, field_type, order, **extra):
        return fixture.expected(f"/api/admin/module-config/modules/{module_id}/fields", "POST", {
            "code": code, "name": name, "fieldType": field_type, "required": False,
            "uniqueValue": False, "searchable": field_type != "SUBTABLE", "sortOrder": order,
            "config": extra.pop("config", {}), **extra}, token)

    field("long_note", "说明", "TEXT", 10)
    field("secret_note", "敏感说明", "TEXT", 20)
    detail = field("line_items", "明细", "SUBTABLE", 30, config={"minRows": 0, "maxRows": 100})
    detail_id = detail.get("id") or detail["field"]["id"]
    for index, code in enumerate(("product_name", "description", "serial_code")):
        field(code, ("产品", "描述", "序列号")[index], "TEXT", 40 + index * 10,
              parentFieldId=detail_id)
    # 运行打印需要当前发布版本中的 PRINT 动作。
    fixture.expected(f"/api/admin/module-config/modules/{module_id}/actions/print", "POST", token=token)
    draft = fixture.expected(f"/api/admin/module-config/modules/{module_id}/draft", token=token)
    fixture.expected(f"/api/admin/module-config/modules/{module_id}/publish", "POST", {
        "expectedDraftRevision": draft["module"]["draftRevision"]}, token)
    long_value = "需要逐项核对交付清单与质保条款。" * 45 + "LONG_NOTE_TAIL_9387"
    rows = [{"rowKey": f"line-{index}", "fields": {
        "product_name": f"设备型号{index}详细规格" * 4,
        "description": "配件与安装说明需保留完整内容" * 3 + f"ROW_TAIL_{index:02d}",
        "serial_code": f"SERIAL-{index:02d}-XYZ"}} for index in range(1, 10)]
    order = fixture.expected("/api/runtime/modules/g7_layout/records", "POST", {
        "title": "小纸张长明细采购单", "status": "ACTIVE", "participantMemberIds": [],
        "fields": {"long_note": long_value, "secret_note": "SECRET_VALUE_7249", "line_items": rows}}, token)
    layout = {"header": "A5 横向长内容", "footer": "采购部存档", "signatureLabel": "签收人",
              "fieldCodes": ["long_note", "secret_note"], "detailFieldCodes": ["line_items"],
              "rowsPerPage": 24, "offsetXmm": 0, "offsetYmm": 0}
    template = fixture.expected("/api/print/admin/templates", "POST", {
        "moduleId": module_id, "code": "g7_layout_print", "name": "长内容采购单",
        "pageSize": "A5", "orientation": "LANDSCAPE", "layout": layout}, token)
    template_id = template["id"]
    preview = fixture.expected(f"/api/print/admin/templates/{template_id}/preview", "POST", {
        "sampleRecordId": order["id"]}, token)
    v1 = fixture.expected(f"/api/print/admin/templates/{template_id}/publish", "POST", {
        "expectedDraftRevision": template["draftRevision"]}, token)
    path = f"/api/print/runtime/modules/g7_layout/records/{order['id']}/templates/{template_id}"
    job = fixture.expected(path, "POST", {}, token)
    data = pdf(job["downloadPath"], token)
    if preview["pages"] != job["pages"] or job["pageCount"] < 2:
        raise AssertionError("A5 草稿预览与打印分页不一致或没有分页")
    if "".join(field["value"] for page in job["pages"] for field in page["fields"]
               if field["code"] == "long_note") != long_value:
        raise AssertionError("长字段跨页后页面预览丢失内容")
    if f"/Count {job['pageCount']}".encode() not in data:
        raise AssertionError("PDF 页数与页面预览不一致")
    positions = content_positions(data)
    rendered = "".join(value for _, value in positions)
    for marker in ("LONG_NOTE_TAIL_9387", "ROW_TAIL_01", "ROW_TAIL_09", "SERIAL-09-XYZ"):
        if marker not in rendered:
            raise AssertionError(f"PDF 截断了 {marker}")
    if any(y < 100 for y, value in positions if any(marker in value for marker in (
            "ROW_TAIL", "SERIAL-", "配件与安装", "设备型号", "需要逐项", "说明（"))):
        raise AssertionError("字段或明细行进入页脚区域")
    if min(y for y, _ in positions) < 20:
        raise AssertionError("PDF 存在页外文本")

    # 固定区域也要逐行排入 PDF，不能因标题、页眉、页脚较长而在右边界裁掉。
    verbose_layout = {**layout,
                      "header": "页眉说明" * 18 + "HEADER_TAIL",
                      "footer": "归档说明" * 22 + "FOOTER_TAIL",
                      "signatureLabel": "打印责任人" * 8 + "SIGN_TAIL"}
    verbose = fixture.expected("/api/print/admin/templates", "POST", {
        "moduleId": module_id, "code": "g7_verbose_print",
        "name": "特别长打印模板标题" * 3 + "TITLE_TAIL",
        "pageSize": "A5", "orientation": "LANDSCAPE", "layout": verbose_layout}, token)
    verbose_preview = fixture.expected(f"/api/print/admin/templates/{verbose['id']}/preview", "POST", {
        "sampleRecordId": order["id"]}, token)
    fixture.expected(f"/api/print/admin/templates/{verbose['id']}/publish", "POST", {
        "expectedDraftRevision": verbose["draftRevision"]}, token)
    verbose_job = fixture.expected(
        f"/api/print/runtime/modules/g7_layout/records/{order['id']}/templates/{verbose['id']}",
        "POST", {}, token)
    verbose_pdf = pdf(verbose_job["downloadPath"], token)
    verbose_text = "".join(value for _, value in content_positions(verbose_pdf))
    if verbose_preview["pages"] != verbose_job["pages"] or \
            f"/Count {verbose_job['pageCount']}".encode() not in verbose_pdf or \
            not all(marker in verbose_text for marker in (
                "TITLE_TAIL", "HEADER_TAIL", "FOOTER_TAIL", "SIGN_TAIL", "ROW_TAIL_09")):
        raise AssertionError("较长的标题、页眉、页脚或签名没有完整进入 PDF 与预览")

    current_template = next(item for item in fixture.expected("/api/print/admin", token=token)["templates"]
                            if item["id"] == template_id)
    revised_layout = {**layout, "header": "A5 横向长内容 v2", "offsetXmm": 7, "offsetYmm": 9}
    revised = fixture.expected(f"/api/print/admin/templates/{template_id}", "PUT", {
        "moduleId": module_id, "code": "g7_layout_print", "name": "长内容采购单新版",
        "pageSize": "A5", "orientation": "LANDSCAPE", "layout": revised_layout,
        "expectedVersion": current_template["version"]}, token)
    draft_preview = fixture.expected(f"/api/print/admin/templates/{template_id}/preview", "POST", {
        "sampleRecordId": order["id"]}, token)
    still_v1 = fixture.expected(path, "POST", {}, token)
    if still_v1["templateVersionId"] != v1["versionId"] or \
            still_v1["pages"][0]["header"] != layout["header"]:
        raise AssertionError("未发布 v2 草稿污染了运行态 v1")
    v2 = fixture.expected(f"/api/print/admin/templates/{template_id}/publish", "POST", {
        "expectedDraftRevision": revised["draftRevision"]}, token)
    current_v2 = fixture.expected(path, "POST", {}, token)
    old_v1 = fixture.expected(path, "POST", {"templateVersionId": v1["versionId"]}, token)
    if current_v2["templateVersionId"] != v2["versionId"] or \
            current_v2["pages"] != draft_preview["pages"] or \
            old_v1["templateVersionId"] != v1["versionId"] or \
            old_v1["pages"][0]["header"] != layout["header"] or \
            b"62 348 Td" not in pdf(current_v2["downloadPath"], token):
        raise AssertionError("同一记录 v1/v2 快照或 7/9 毫米套打偏移不正确")
    fixture.expected(f"/api/print/admin/templates/{template_id}/versions/{v1['versionId']}/activate",
                     "POST", {"expectedCurrentVersionId": v2["versionId"]}, token)
    restored = fixture.expected(path, "POST", {}, token)
    if restored["templateVersionId"] != v1["versionId"]:
        raise AssertionError("切换回 v1 后默认打印仍使用 v2")

    member_name = "g7_layout_member_" + suffix
    fixture.expected("/api/auth/register", "POST", {
        "username": member_name, "password": "correct-password", "displayName": "G7 排版成员",
        "email": member_name + "@example.com", "systemName": "G7 成员系统",
        "systemCode": "g7_layout_member_" + suffix})

    def member_token():
        platform = fixture.expected("/api/auth/login", "POST", {
            "username": member_name, "password": "correct-password"})
        return fixture.expected(f"/api/systems/{owner['systemId']}/enter", "POST", {},
                                platform["accessToken"])["tokens"]["accessToken"]

    def role(mask, role_id=None, version=None):
        body = {"code": "g7_layout_printer", "name": "排版打印员",
                "permissions": [{"resourceType": "MODULE", "resourceCode": "g7_layout", "actionCode": action,
                                 "dataScopeType": "ALL", "dataScopeJson": "{}"}
                                for action in ("LIST", "DETAIL", "PRINT")],
                "fieldPolicies": [{"resourceCode": "g7_layout", "fieldCode": code,
                                   "channel": channel, "readable": True, "writable": False,
                                   "maskStrategy": mask if code == "secret_note" and channel == "FILE" else None}
                                  for code in ("long_note", "secret_note", "line_items",
                                               "product_name", "description", "serial_code")
                                  for channel in ("PAGE", "FILE")]}
        if role_id:
            body.update({"id": role_id, "expectedVersion": version})
        saved = fixture.expected("/api/admin/system/authorization/roles", "POST", body, token)
        return fixture.expected(f"/api/admin/system/authorization/roles/{saved['id']}/publish", "POST", {
            "reason": "G7 打印脱敏回归", "expectedVersion": saved["version"]}, token)

    printer_role = role(None)
    fixture.expected("/api/admin/system/authorization/members", "POST", {
        "account": member_name, "roleIds": [printer_role["id"]]}, token)
    member_job = fixture.expected(path, "POST", {}, member_token())
    if "SECRET_VALUE_7249" not in json.dumps(member_job, ensure_ascii=False):
        raise AssertionError("初始打印范围未包含可见字段")
    printer_role = role("FULL", printer_role["id"], printer_role["version"])
    current_token = member_token()
    status, error = fixture.request(member_job["downloadPath"], "GET", None, current_token)
    if status != 403 or error.get("code") != "PRINT_JOB_FIELD_ACCESS_REVOKED":
        raise AssertionError(f"脱敏策略变化后历史 PDF 未拒绝：{status}/{error}")
    for old_path in (member_job["previewPath"],
                     f"/api/print/jobs/{member_job['id']}"):
        status, previous = fixture.request(old_path, "GET", None, current_token)
        if status != 403 or previous.get("code") != "PRINT_JOB_FIELD_ACCESS_REVOKED":
            raise AssertionError(f"脱敏策略变化后历史作业仍可读取：{old_path}: {status}/{previous}")
    masked_job = fixture.expected(path, "POST", {}, current_token)
    masked_data = pdf(masked_job["downloadPath"], current_token)
    if "SECRET_VALUE_7249" in json.dumps(masked_job, ensure_ascii=False) or \
            "SECRET_VALUE_7249".encode("utf-16be").hex().upper().encode() in masked_data or \
            "***" not in json.dumps(masked_job, ensure_ascii=False) or \
            "***".encode("utf-16be").hex().upper().encode() not in masked_data:
        raise AssertionError("新 PDF 未应用当前 FILE 脱敏策略")
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
            raise AssertionError("重启后打印服务未恢复")
        if fixture.expected(path, "POST", {}, token)["templateVersionId"] != v1["versionId"]:
            raise AssertionError("重启后模板发布指针没有保持 v1")
        if "***" not in json.dumps(fixture.expected(
                f"/api/print/jobs/{masked_job['id']}", token=current_token), ensure_ascii=False) or \
                "***".encode("utf-16be").hex().upper().encode() not in pdf(
                    masked_job["downloadPath"], current_token):
            raise AssertionError("重启后脱敏打印预览丢失")
    print(json.dumps({"systemId": owner["systemId"], "ownerUsername": username,
                      "moduleId": module_id, "recordId": order["id"], "templateId": template_id,
                      "version1": v1["versionId"], "version2": v2["versionId"],
                      "pageCount": job["pageCount"], "positions": len(positions),
                      "verbosePageCount": verbose_job["pageCount"],
                      "maskRevocation": error["code"], "restartVerified": "--restart" in sys.argv},
                     ensure_ascii=False))


if __name__ == "__main__":
    main()
