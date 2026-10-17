"""真实 Edge 页面验证管理员字段类型配置和成员按类型填写、筛选业务记录。"""

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


def add_field(page, key, label, kind, required=False, searchable=False):
    page.get_by_role("button", name="添加字段").click()
    page.get_by_placeholder("例如：客户名称").fill(label)
    page.get_by_placeholder("例如：customer_name").fill(key)
    page.get_by_label("字段类型").select_option(kind)
    if required:
        page.locator(".check-row input").nth(0).check()
    if searchable:
        page.locator(".check-row input").nth(1).check()
    page.get_by_role("button", name="保存字段").click()
    page.locator(".modal-backdrop").wait_for(state="detached")


def main():
    suffix = secrets.token_hex(4)
    owner_name, member_name = "ui_fields_owner_" + suffix, "ui_fields_member_" + suffix
    password = "UiFieldsCheck9!" + suffix
    screenshot = PROJECT / "ai" / "evidence" / "r15-10-field-types-browser.png"
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(executable_path=str(EDGE), headless=True)
        owner_context = browser.new_context(viewport={"width": 1540, "height": 1050}, locale="zh-CN")
        member_context = browser.new_context(viewport={"width": 1540, "height": 1050}, locale="zh-CN")
        for context, username in ((owner_context, owner_name), (member_context, member_name)):
            api(context, "POST", "/auth/register", 201,
                {"username": username, "password": password, "displayName": username})
        system = api(owner_context, "POST", "/systems", 201,
                     {"code": "ui_fields_" + suffix, "name": "字段浏览器系统"})
        sid = system["id"]
        base = f"/systems/{sid}"
        role = api(owner_context, "GET", base + "/roles", 200)[0]
        group = api(owner_context, "POST", base + "/module-groups", 201,
                    {"code": "business", "name": "业务"})
        module = api(owner_context, "POST", base + "/modules", 201,
                     {"groupId": group["id"], "code": "customer", "name": "客户"})
        owner = owner_context.new_page()
        member = member_context.new_page()
        errors = []
        owner.on("pageerror", lambda error: errors.append("owner: " + str(error)))
        member.on("pageerror", lambda error: errors.append("member: " + str(error)))
        owner.goto(URL + f"systems/{sid}")
        owner.locator(".module-column .list-item", has_text="客户").click()
        add_field(owner, "title", "客户名称", "TEXT", required=True, searchable=True)
        add_field(owner, "description", "客户说明", "TEXTAREA")
        add_field(owner, "phone", "联系手机", "PHONE", searchable=True)
        add_field(owner, "email", "联系邮箱", "EMAIL")
        add_field(owner, "amount", "合同金额", "MONEY", searchable=True)
        add_field(owner, "occurred", "签约时间", "DATETIME", searchable=True)
        owner.get_by_role("button", name="发布模块", exact=True).click()
        owner.get_by_text("发布成功，当前为第 1 版。").wait_for()
        api(owner_context, "POST", base + f"/roles/{role['id']}/grants", 200,
            {"moduleId": module["id"], "canView": True, "canCreate": True,
             "canUpdate": True, "canDelete": False, "canExport": False})
        api(owner_context, "POST", base + "/members/invite", 201,
            {"username": member_name, "roleId": role["id"]})

        member.goto(URL + f"systems/{sid}")
        member.locator(".runtime-side button", has_text="客户").click()
        member.get_by_role("button", name="新增记录").click()
        form = member.locator(".record-form-grid")
        assert form.locator("textarea").count() == 1
        assert form.locator("input[type=tel]").count() == 1
        assert form.locator("input[type=email]").count() == 1
        assert form.locator("input[type=datetime-local]").count() == 1
        form.locator("label", has_text="客户名称").locator("input").fill("界面客户")
        form.locator("textarea").fill("第一行\n第二行")
        form.locator("input[type=tel]").fill("13800138000")
        form.locator("input[type=email]").fill("invalid")
        assert not form.locator("input[type=email]").evaluate("node => node.checkValidity()")
        form.locator("input[type=email]").fill("user@example.com")
        form.locator("label", has_text="合同金额").locator("input").fill("88.50")
        form.locator("input[type=datetime-local]").fill("2026-10-17T13:20")
        member.get_by_role("button", name="保存记录").click()
        member.get_by_text("记录已保存。").wait_for()
        member.locator(".record-detail").get_by_role("heading", name="界面客户").wait_for()
        assert "第一行" in member.locator(".record-detail-fields").inner_text()
        member.locator(".record-search select").select_option("amount")
        member.get_by_placeholder("字段精确值").fill("88.5")
        member.get_by_role("button", name="查询").click()
        member.locator(".record-row", has_text="界面客户").wait_for()
        member.screenshot(path=str(screenshot), full_page=True)
        assert not errors, errors
        browser.close()
    print(json.dumps({"status": "passed", "screenshot": str(screenshot),
                      "checks": ["owner_field_config", "published_form_types", "browser_email_validation",
                                 "typed_record_save", "detail_readback", "money_filter", "no_page_errors"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
