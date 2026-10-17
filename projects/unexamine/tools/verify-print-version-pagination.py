"""验证打印历史版本、关联展示和子表明细分页的真实业务路径。"""

import importlib.util
import json
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
        content = response.read()
        if response.status != 200 or not content.startswith(b"%PDF-"):
            raise AssertionError("PDF 文件下载失败或格式不正确")
        return content


def main():
    suffix = secrets.token_hex(4)
    name = "g7_version_owner_" + suffix
    owner = fixture.expected("/api/auth/register", "POST", {
        "username": name, "password": "correct-password", "displayName": "G7 版本管理员",
        "email": name + "@example.com", "systemName": "G7 明细打印系统",
        "systemCode": "g7_detail_" + suffix})
    token = owner["tokens"]["accessToken"]
    group = fixture.expected("/api/admin/module-config/groups", "POST", {
        "code": "g7_detail_group", "name": "采购管理", "sortOrder": 10}, token)

    def module(code, label):
        result = fixture.expected("/api/admin/module-config/modules", "POST", {
            "groupId": group["id"], "code": code, "name": label}, token)
        return result["module"]["id"]

    supplier_id = module("g7_supplier", "供应商")
    purchase_id = module("g7_purchase", "采购单")

    def field(module_id, code, label, field_type, order, **extra):
        return fixture.expected(f"/api/admin/module-config/modules/{module_id}/fields", "POST", {
            "code": code, "name": label, "fieldType": field_type, "required": False,
            "uniqueValue": False, "searchable": field_type != "SUBTABLE", "sortOrder": order,
            "config": extra.pop("config", {}), **extra}, token)

    def publish_module(module_id):
        draft = fixture.expected(f"/api/admin/module-config/modules/{module_id}/draft", token=token)
        return fixture.expected(f"/api/admin/module-config/modules/{module_id}/publish", "POST", {
            "expectedDraftRevision": draft["module"]["draftRevision"]}, token)

    field(supplier_id, "supplier_code", "供应商编码", "TEXT", 10)
    publish_module(supplier_id)
    supplier = fixture.expected("/api/runtime/modules/g7_supplier/records", "POST", {
        "title": "蓝海设备", "status": "ACTIVE", "participantMemberIds": [],
        "fields": {"supplier_code": "SUP-001"}}, token)
    field(purchase_id, "supplier_ref", "供应商", "REFERENCE", 10, referenceModuleId=supplier_id)
    detail = field(purchase_id, "line_items", "采购明细", "SUBTABLE", 20,
                   config={"minRows": 0, "maxRows": 100})
    detail_id = detail["id"] if "id" in detail else detail["field"]["id"]
    field(purchase_id, "product_name", "商品", "TEXT", 30, parentFieldId=detail_id)
    field(purchase_id, "quantity", "数量", "NUMBER", 40, parentFieldId=detail_id)
    # 采购单发布时一并发布可选打印动作。
    fixture.expected(f"/api/admin/module-config/modules/{purchase_id}/actions/print", "POST", token=token)
    publish_module(purchase_id)
    rows = [{"rowKey": f"line-{index}", "fields": {"product_name": f"设备-{index}", "quantity": index}}
            for index in range(1, 7)]
    order = fixture.expected("/api/runtime/modules/g7_purchase/records", "POST", {
        "title": "六行采购申请", "status": "ACTIVE", "participantMemberIds": [],
        "fields": {"supplier_ref": supplier["id"], "line_items": rows}}, token)
    print_path = f"/api/print/runtime/modules/g7_purchase/records/{order['id']}"
    layout = {"header": "采购明细 v1", "footer": "采购部", "signatureLabel": "审批人",
              "fieldCodes": ["supplier_ref"], "detailFieldCodes": ["line_items"],
              "rowsPerPage": 4, "offsetXmm": 7, "offsetYmm": 9}
    template = fixture.expected("/api/print/admin/templates", "POST", {
        "moduleId": purchase_id, "code": "g7_purchase_print", "name": "采购单",
        "pageSize": "A4", "orientation": "PORTRAIT", "layout": layout}, token)
    template_id = template["id"]
    preview = fixture.expected(f"/api/print/admin/templates/{template_id}/preview", "POST", {
        "sampleRecordId": order["id"]}, token)
    all_rows = [row for page in preview["pages"] for table in page.get("detailTables", [])
                for row in table["rows"]]
    if len(preview["pages"]) != 2 or len(all_rows) != 6:
        raise AssertionError(f"六行采购明细未按行自动分页：{json.dumps(preview, ensure_ascii=False)[:1000]}")
    if any(field["value"] == str(supplier["id"]) for page in preview["pages"]
           for field in page["fields"]):
        raise AssertionError("打印关联字段落回内部 ID")
    if preview["pages"][0]["fields"][0]["value"] != "蓝海设备":
        raise AssertionError("打印关联字段没有展示值")
    v1 = fixture.expected(f"/api/print/admin/templates/{template_id}/publish", "POST", {
        "expectedDraftRevision": template["draftRevision"]}, token)
    first_job = fixture.expected(f"{print_path}/templates/{template_id}", "POST", {}, token)
    if first_job["pageCount"] != 2 or first_job["templateVersionId"] != v1["versionId"]:
        raise AssertionError("版本 v1 打印不符合草稿预览")
    first_pdf = pdf(first_job["downloadPath"], token)
    if b"/Count 2" not in first_pdf or b"62 770 Td" not in first_pdf:
        raise AssertionError("PDF 没有两页或没有应用 7/9 毫米套打偏移")
    for item in ("蓝海设备", "设备-1", "设备-6"):
        if item.encode("utf-16be").hex().upper().encode() not in first_pdf:
            raise AssertionError(f"PDF 明细缺少 {item}")
    current_template = next(item for item in fixture.expected("/api/print/admin", token=token)["templates"]
                            if item["id"] == template_id)
    revised_layout = {**layout, "header": "采购明细 v2", "footer": "新版采购部"}
    revised = fixture.expected(f"/api/print/admin/templates/{template_id}", "PUT", {
        "moduleId": purchase_id, "code": "g7_purchase_print", "name": "采购单新版",
        "pageSize": "A4", "orientation": "PORTRAIT", "layout": revised_layout,
        "expectedVersion": current_template["version"]}, token)
    still_old = fixture.expected(f"{print_path}/templates/{template_id}", "POST", {}, token)
    if still_old["templateVersionId"] != v1["versionId"] or still_old["pages"][0]["header"] != "采购明细 v1":
        raise AssertionError("未发布草稿污染了当前版本")
    fixture.expected(f"/api/print/admin/templates/{template_id}/preview", "POST", {
        "sampleRecordId": order["id"]}, token)
    v2 = fixture.expected(f"/api/print/admin/templates/{template_id}/publish", "POST", {
        "expectedDraftRevision": revised["draftRevision"]}, token)
    current = fixture.expected(f"{print_path}/templates/{template_id}", "POST", {}, token)
    old = fixture.expected(f"{print_path}/templates/{template_id}", "POST", {
        "templateVersionId": v1["versionId"]}, token)
    if current["templateVersionId"] != v2["versionId"] or current["pages"][0]["header"] != "采购明细 v2" \
            or old["pages"][0]["header"] != "采购明细 v1":
        raise AssertionError("历史版本和新版本没有保持各自布局")
    if "采购明细 v2".encode("utf-16be").hex().upper().encode() not in pdf(current["downloadPath"], token):
        raise AssertionError("v2 PDF 仍使用旧版本页眉")
    fixture.expected(f"/api/print/admin/templates/{template_id}/versions/{v1['versionId']}/activate", "POST", {
        "expectedCurrentVersionId": v2["versionId"]}, token)
    restored = fixture.expected(f"{print_path}/templates/{template_id}", "POST", {}, token)
    if restored["templateVersionId"] != v1["versionId"]:
        raise AssertionError("历史版本切换未生效")
    duplicate_status, duplicate = fixture.request(
        f"/api/print/admin/templates/{template_id}/publish", "POST",
        {"expectedDraftRevision": revised["draftRevision"]}, token)
    if duplicate_status != 409 or duplicate["code"] != "PRINT_TEMPLATE_ALREADY_PUBLISHED":
        raise AssertionError("恢复旧版后重复发布已有草稿未明确拒绝")

    # 子表列也服从当前 FILE 权限；旧 PDF 在列撤权后不能继续下载。
    member_name = "g7_detail_member_" + suffix
    fixture.expected("/api/auth/register", "POST", {
        "username": member_name, "password": "correct-password", "displayName": "G7 采购成员",
        "email": member_name + "@example.com", "systemName": "G7 成员系统",
        "systemCode": "g7_detail_member_" + suffix})

    def member_token():
        platform = fixture.expected("/api/auth/login", "POST", {
            "username": member_name, "password": "correct-password"})
        return fixture.expected(f"/api/systems/{owner['systemId']}/enter", "POST", {},
                                platform["accessToken"])["tokens"]["accessToken"]

    def role(public_quantity, role_id=None, version=None):
        permissions = [{"resourceType": "MODULE", "resourceCode": "g7_purchase", "actionCode": action,
                        "dataScopeType": "ALL", "dataScopeJson": "{}"}
                       for action in ("LIST", "DETAIL", "PRINT")]
        permissions.append({"resourceType": "MODULE", "resourceCode": "g7_supplier",
                            "actionCode": "DETAIL", "dataScopeType": "ALL", "dataScopeJson": "{}"})
        policies = [{"resourceCode": "g7_purchase", "fieldCode": field_code, "channel": channel,
                     "readable": field_code != "quantity" or channel == "PAGE" or public_quantity,
                     "writable": False, "maskStrategy": None}
                    for field_code in ("supplier_ref", "line_items", "product_name", "quantity")
                    for channel in ("PAGE", "FILE")]
        body = {"code": "g7_detail_printer", "name": "采购明细打印员",
                "permissions": permissions, "fieldPolicies": policies}
        if role_id:
            body.update({"id": role_id, "expectedVersion": version})
        saved = fixture.expected("/api/admin/system/authorization/roles", "POST", body, token)
        return fixture.expected(f"/api/admin/system/authorization/roles/{saved['id']}/publish", "POST", {
            "reason": "G7 子表打印字段权限验证", "expectedVersion": saved["version"]}, token)

    printer_role = role(True)
    fixture.expected("/api/admin/system/authorization/members", "POST", {
        "account": member_name, "roleIds": [printer_role["id"]]}, token)
    member_job = fixture.expected(f"{print_path}/templates/{template_id}", "POST", {}, member_token())
    if [column["code"] for column in member_job["pages"][0]["detailTables"][0]["columns"]] \
            != ["product_name", "quantity"]:
        raise AssertionError("授权成员没有拿到完整明细列")
    printer_role = role(False, printer_role["id"], printer_role["version"])
    revoked_token = member_token()
    status, error = fixture.request(member_job["downloadPath"], "GET", None, revoked_token)
    if status != 403 or error["code"] != "PRINT_JOB_FIELD_ACCESS_REVOKED":
        raise AssertionError(f"撤销子表数量列后旧 PDF 未拒绝：{status}/{error}")
    reduced = fixture.expected(f"{print_path}/templates/{template_id}", "POST", {}, revoked_token)
    if [column["code"] for column in reduced["pages"][0]["detailTables"][0]["columns"]] \
            != ["product_name"] or "quantity" in json.dumps(reduced, ensure_ascii=False):
        raise AssertionError("新打印作业仍泄露已撤销的子表列")
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
            raise AssertionError("重启后打印预览服务未恢复")
        after_restart = fixture.expected(f"{print_path}/templates/{template_id}", "POST", {}, token)
        if after_restart["templateVersionId"] != v1["versionId"] or after_restart["pageCount"] != 2:
            raise AssertionError("重启后历史版本指针或分页丢失")
        if b"/Count 2" not in pdf(after_restart["downloadPath"], token):
            raise AssertionError("重启后 PDF 文件内容不一致")
    print(json.dumps({"systemId": owner["systemId"], "ownerUsername": name,
                      "supplierId": supplier["id"], "purchaseRecordId": order["id"],
                      "templateId": template_id, "version1": v1["versionId"],
                      "version2": v2["versionId"], "pageCount": len(preview["pages"]),
                      "detailRows": len(all_rows), "restoredVersion": restored["templateVersionId"],
                      "childFieldRevocation": error["code"],
                      "restartVerified": "--restart" in sys.argv},
                     ensure_ascii=False))


if __name__ == "__main__":
    main()
