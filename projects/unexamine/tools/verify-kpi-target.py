"""在隔离系统用真实模块金额验证 KPI 的三种责任对象与三种周期。"""

import importlib.util
import json
import secrets
import subprocess
import sys
import time
import urllib.request
from datetime import date
from decimal import Decimal
from pathlib import Path


spec = importlib.util.spec_from_file_location("fixture", Path(__file__).with_name("verify-external-flow.py"))
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)


def amount(value):
    return Decimal(str(value))


def assert_amount(result, expected):
    if amount(result["actualValue"]) != amount(expected):
        raise AssertionError(f"KPI 实际值不是模块金额之和：{result['actualValue']} != {expected}")


def main():
    suffix = secrets.token_hex(4)
    username = "g8_owner_" + suffix
    owner = fixture.expected("/api/auth/register", "POST", {
        "username": username, "password": "correct-password", "displayName": "G8 指标管理员",
        "email": username + "@example.com", "systemName": "G8 指标系统",
        "systemCode": "g8_kpi_" + suffix})
    token = owner["tokens"]["accessToken"]
    group = fixture.expected("/api/admin/module-config/groups", "POST", {
        "code": "g8_sales", "name": "销售", "sortOrder": 10}, token)
    module = fixture.expected("/api/admin/module-config/modules", "POST", {
        "groupId": group["id"], "code": "g8_contract", "name": "合同"}, token)
    module_id = module["module"]["id"]
    fixture.expected(f"/api/admin/module-config/modules/{module_id}/fields", "POST", {
        "code": "amount", "name": "合同金额", "fieldType": "NUMBER", "required": True,
        "searchable": True, "sortOrder": 10, "config": {}}, token)
    draft = fixture.expected(f"/api/admin/module-config/modules/{module_id}/draft", token=token)
    fixture.expected(f"/api/admin/module-config/modules/{module_id}/publish", "POST", {
        "expectedDraftRevision": draft["module"]["draftRevision"]}, token)
    record_ids = []
    for index, value in enumerate((120, 280), 1):
        record = fixture.expected("/api/runtime/modules/g8_contract/records", "POST", {
            "title": f"G8 合同 {index}", "status": "ACTIVE", "participantMemberIds": [],
            "fields": {"amount": value}}, token)
        record_ids.append(record["id"])
    definition = {
        "modules": [{"alias": "contract", "moduleCode": "g8_contract"}],
        "relations": [],
        "outputFields": [{"alias": "contract", "fieldCode": "title"},
                         {"alias": "contract", "fieldCode": "amount"}],
        "metric": {"operation": "SUM", "alias": "contract", "fieldCode": "amount"},
        "dimension": {}, "filters": [],
        "sort": {"alias": "contract", "fieldCode": "amount", "direction": "DESC"},
        "timeField": {"alias": "contract", "fieldCode": "createdAt"},
        "maxScanRows": 100, "limit": 20,
    }
    source = fixture.expected("/api/analytics/admin/data-sources", "POST", {
        "code": "g8_contract_amount", "name": "合同金额汇总", "sourceType": "MODULE_REPORT",
        "definition": definition,
        "permissionPolicy": {"resourceType": "MODULE", "resourceCode": "g8_contract",
                             "actionCode": "LIST"}}, token)
    source_id = source["id"]
    preview = fixture.expected(f"/api/analytics/admin/data-sources/{source_id}/report-preview", token=token)
    if not preview["valid"] or amount(preview["sampleResult"]["value"]) != 400:
        raise AssertionError(f"数据源草稿没有汇总两笔合同：{preview}")
    published = fixture.expected(f"/api/analytics/admin/data-sources/{source_id}/publish", "POST", {
        "expectedDraftRevision": source["draftRevision"]}, token)
    source_version = next(item["id"] for item in published["versions"] if item["versionNumber"] == 1)
    directory = fixture.expected("/api/system-directory", token=token)
    auth = fixture.expected("/api/admin/system/authorization", token=token)
    owner_person = next(item for item in directory["people"] if item.get("account") == username
                        or item.get("username") == username or item.get("displayName") == "G8 指标管理员")
    person_id = owner_person["tenantMemberId"]
    department_id = directory["departments"][0]["id"]
    role_id = next(item["id"] for item in auth["roles"] if item["status"] == "ACTIVE")
    today = date.today()
    periods = {
        "MONTH": f"{today.year}-{today.month:02d}",
        "QUARTER": f"{today.year}-Q{(today.month - 1) // 3 + 1}",
        "YEAR": str(today.year),
    }
    results = {}
    for subject, subject_id, period_type, target in (
            ("PERSON", person_id, "MONTH", 500),
            ("DEPARTMENT", department_id, "QUARTER", 300),
            ("ROLE", role_id, "YEAR", 500)):
        request = {"code": f"g8_{subject.lower()}_{suffix}", "name": f"{subject} 合同目标",
                   "dataSourceVersionId": source_version, "targetValue": target,
                   "targetOperator": "GTE", "periodType": period_type,
                   "responsibleType": subject, "responsibleIds": [subject_id],
                   "visibilityPermission": {"resourceType": "MODULE", "resourceCode": "g8_contract",
                                            "actionCode": "LIST"},
                   "drillPermission": {"resourceType": "MODULE",
                   "resourceCode": "g8_contract", "actionCode": "LIST"},
                   "reminderEnabled": subject == "PERSON",
                   "reminderRecipientTenantMemberIds": [person_id] if subject == "PERSON" else [],
                   "reminderBelowPercent": 100, "status": "ACTIVE"}
        kpi = fixture.expected("/api/analytics/admin/kpis", "POST", request, token)
        key = periods[period_type]
        calculation_preview = fixture.expected(
            f"/api/analytics/admin/kpis/{kpi['id']}/preview?periodKey={key}", token=token)
        assert_amount(calculation_preview, 400)
        calculated = fixture.expected(f"/api/analytics/admin/kpis/{kpi['id']}/calculate", "POST", {
            "periodKey": key}, token)
        assert_amount(calculated, 400)
        expected_status = "ACHIEVED" if target == 300 else "UNDER_TARGET"
        if calculated["status"] != expected_status or \
                abs(amount(calculated["achievementRate"]) - amount(400) / amount(target) * 100) > Decimal("0.000001"):
            raise AssertionError(f"{subject} {period_type} 达成率错误：{calculated}")
        if subject == "PERSON":
            if len(calculated["reminders"]) != 1 or calculated["reminders"][0]["status"] != "SENT":
                raise AssertionError("个人目标未达标没有形成消息提醒")
            repeated = fixture.expected(f"/api/analytics/admin/kpis/{kpi['id']}/calculate", "POST", {
                "periodKey": key}, token)
            if len(repeated["reminders"]) != 1:
                raise AssertionError("同一周期重算重复提醒")
            status, error = fixture.request(
                f"/api/analytics/admin/kpis/{kpi['id']}/preview?periodKey={periods['QUARTER']}",
                "GET", None, token)
            if status != 422 or error.get("code") != "KPI_PERIOD_INVALID":
                raise AssertionError(f"错误周期未明确拒绝：{status}/{error}")
        results[subject] = {"id": kpi["id"], "period": key,
                            "status": calculated["status"], "rate": str(calculated["achievementRate"])}
    board = fixture.expected("/api/analytics/kpis", token=token)
    if len([item for item in board["kpis"] if item["id"] in [value["id"] for value in results.values()]]) != 3:
        raise AssertionError("运行态 KPI 看板未读回三种责任目标")
    for label in ("viewer", "outsider"):
        account = f"g8_{label}_{suffix}"
        fixture.expected("/api/auth/register", "POST", {
            "username": account, "password": "correct-password", "displayName": f"G8 {label}",
            "email": account + "@example.com", "systemName": f"G8 {label} 独立系统",
            "systemCode": f"g8_{label}_{suffix}"})
        if label == "viewer":
            role = fixture.expected("/api/admin/system/authorization/roles", "POST", {
                "code": "g8_metric_viewer", "name": "指标查看员",
                "permissions": [{"resourceType": "MODULE", "resourceCode": "g8_contract",
                                 "actionCode": action, "dataScopeType": "SELF", "dataScopeJson": "{}"}
                                for action in ("LIST", "DETAIL")],
                "fieldPolicies": [{"resourceCode": "g8_contract", "fieldCode": "amount",
                                   "channel": "PAGE", "readable": True, "writable": False,
                                   "maskStrategy": None}]}, token)
            role = fixture.expected(f"/api/admin/system/authorization/roles/{role['id']}/publish",
                                    "POST", {"reason": "G8 指标范围验证",
                                             "expectedVersion": role["version"]}, token)
            role_ids = [role["id"]]
        else:
            role_ids = []
        fixture.expected("/api/admin/system/authorization/members", "POST", {
            "account": account, "roleIds": role_ids}, token)
        platform = fixture.expected("/api/auth/login", "POST", {
            "username": account, "password": "correct-password"})
        member_token = fixture.expected(f"/api/systems/{owner['systemId']}/enter", "POST", {},
                                        platform["accessToken"])["tokens"]["accessToken"]
        member_board = fixture.expected("/api/analytics/kpis", token=member_token)
        if label == "viewer":
            if len(member_board["kpis"]) != 3 or any(
                    item["latestResult"]["drillItems"] for item in member_board["kpis"]):
                raise AssertionError("SELF 范围成员未看到获权指标，或下钻越过本人记录")
        elif member_board["kpis"]:
            raise AssertionError("无模块动作授权成员看到了 KPI 指标")
        if label == "outsider":
            status, error = fixture.request(f"/api/analytics/kpis/{results['PERSON']['id']}",
                                            "GET", None, member_token)
            if status != 403 or error.get("code") != "KPI_VISIBILITY_DENIED":
                raise AssertionError(f"无权成员直读 KPI 未明确拒绝：{status}/{error}")
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
            raise AssertionError("重启后 KPI 服务未恢复")
        board = fixture.expected("/api/analytics/kpis", token=token)
        if len(board["kpis"]) != 3:
            raise AssertionError("重启后 KPI 看板目标丢失")
    print(json.dumps({"systemId": owner["systemId"], "ownerUsername": username,
                      "moduleId": module_id, "recordIds": record_ids,
                      "sourceId": source_id, "sourceVersionId": source_version,
                      "personId": person_id, "departmentId": department_id, "roleId": role_id,
                      "results": results, "restartVerified": "--restart" in sys.argv}, ensure_ascii=False))


if __name__ == "__main__":
    main()
