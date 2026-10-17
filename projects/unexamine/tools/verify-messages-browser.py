"""真实 Edge 页面验证审批待办与结果消息进入目标后仍走原权限接口。"""

from __future__ import annotations

import json
import secrets
from pathlib import Path

from playwright.sync_api import sync_playwright

PROJECT = Path(__file__).resolve().parents[1]
EDGE = Path(r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe")
URL = "http://127.0.0.1:15173/"


def api(context, method, path, wanted, body=None):
    response = context.request.fetch(URL + "api" + path, method=method, data=body)
    if response.status != wanted:
        raise AssertionError(f"{method} {path}: wanted {wanted}, got {response.status}: {response.text()[:400]}")
    return response.json() if response.body() else None


def main():
    suffix = secrets.token_hex(4)
    names = ["ui_msg_owner_" + suffix, "ui_msg_app_" + suffix, "ui_msg_review_" + suffix]
    password = "UiMessageCheck9!" + suffix
    todo_image = PROJECT / "ai" / "evidence" / "r15-09-message-todo-browser.png"
    result_image = PROJECT / "ai" / "evidence" / "r15-09-message-result-browser.png"
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(executable_path=str(EDGE), headless=True)
        contexts = [browser.new_context(viewport={"width": 1540, "height": 1050}, locale="zh-CN") for _ in names]
        owner, applicant, reviewer = contexts
        for context, username in zip(contexts, names):
            api(context, "POST", "/auth/register", 201,
                {"username": username, "password": password, "displayName": username})
        system = api(owner, "POST", "/systems", 201,
                     {"code": "ui_message_" + suffix, "name": "消息浏览器系统"})
        sid = system["id"]
        base = f"/systems/{sid}"
        role = api(owner, "GET", base + "/roles", 200)[0]
        group = api(owner, "POST", base + "/module-groups", 201,
                    {"code": "business", "name": "业务"})
        module = api(owner, "POST", base + "/modules", 201,
                     {"groupId": group["id"], "code": "request", "name": "申请单"})
        mid = module["id"]
        path = base + f"/modules/{mid}"
        api(owner, "POST", path + "/fields", 201,
            {"key": "title", "label": "申请标题", "type": "TEXT", "required": True, "searchable": True})
        api(owner, "POST", path + "/publish", 200)
        api(owner, "POST", base + f"/roles/{role['id']}/grants", 200,
            {"moduleId": mid, "canView": True, "canCreate": True, "canUpdate": True,
             "canDelete": False, "canExport": False})
        for username in names[1:]:
            api(owner, "POST", base + "/members/invite", 201,
                {"username": username, "roleId": role["id"]})
        members = {item["username"]: item["id"] for item in api(owner, "GET", base + "/members", 200)}
        api(owner, "PUT", path + "/approval", 200,
            {"name": "申请审批", "nodeName": "负责人审批", "approverMemberId": members[names[2]]})
        api(owner, "POST", path + "/approval/publish", 200)
        record = api(applicant, "POST", path + "/records", 201,
                     {"values": {"title": "浏览器消息申请"}})
        task = api(applicant, "POST", path + f"/records/{record['id']}/submit-approval", 201)

        reviewer_page = reviewer.new_page()
        applicant_page = applicant.new_page()
        errors = []
        reviewer_page.on("pageerror", lambda error: errors.append("reviewer: " + str(error)))
        applicant_page.on("pageerror", lambda error: errors.append("applicant: " + str(error)))
        reviewer_page.goto(URL)
        reviewer_page.locator(".system-card", has_text="消息浏览器系统").click()
        reviewer_page.get_by_role("button", name="消息").click()
        reviewer_page.locator(".message-item", has_text="新的审批待办").wait_for()
        assert reviewer_page.locator(".message-item .field-tag").count() == 1
        reviewer_page.screenshot(path=str(todo_image), full_page=True)
        reviewer_page.locator(".message-item", has_text="新的审批待办").click()
        reviewer_page.locator(".access-card", has_text="TASK DETAIL").get_by_role("heading", name="浏览器消息申请").wait_for()
        reviewer_page.locator(".access-card", has_text="TASK DETAIL").get_by_placeholder("拒绝时必须填写原因").fill("材料不完整")
        reviewer_page.get_by_role("button", name="拒绝", exact=True).click()
        reviewer_page.get_by_text("已拒绝审批。").wait_for()
        assert api(reviewer, "GET", base + "/messages/unread-count", 200)["count"] == 0

        applicant_page.goto(URL)
        applicant_page.locator(".system-card", has_text="消息浏览器系统").click()
        applicant_page.get_by_role("button", name="消息").click()
        applicant_page.locator(".message-item", has_text="审批申请被拒绝").wait_for()
        applicant_page.locator(".message-item", has_text="审批申请被拒绝").click()
        applicant_page.locator(".record-detail").get_by_role("heading", name="浏览器消息申请").wait_for()
        assert api(applicant, "GET", base + "/messages/unread-count", 200)["count"] == 0
        assert api(applicant, "GET", path + f"/records/{record['id']}", 200)["recordStatus"] == "REJECTED"
        applicant_page.screenshot(path=str(result_image), full_page=True)
        assert task["taskId"] > 0 and not errors, errors
        browser.close()
    print(json.dumps({"status": "passed", "todo_screenshot": str(todo_image),
                      "result_screenshot": str(result_image),
                      "checks": ["todo_inbox", "unread_badge", "message_to_task", "reject",
                                 "result_inbox", "message_to_record", "read_count", "no_page_errors"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
