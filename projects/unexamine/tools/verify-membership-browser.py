"""真实双浏览器页面验收角色授权、添加成员及受邀者运行态。"""

from __future__ import annotations

import json
import secrets
from pathlib import Path

from playwright.sync_api import sync_playwright

PROJECT = Path(__file__).resolve().parents[1]
EDGE = Path(r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe")
URL = "http://127.0.0.1:15173/"


def register(page, username, password, display_name):
    page.goto(URL)
    page.get_by_role("button", name="还没有账号？立即注册").click()
    page.get_by_placeholder("例如 admin01").fill(username)
    page.get_by_placeholder("你的姓名或团队名称").fill(display_name)
    page.get_by_placeholder("至少 8 位").fill(password)
    page.get_by_role("button", name="注册并进入").click()
    page.get_by_role("heading", name="我的系统").wait_for()


def main():
    suffix = secrets.token_hex(4)
    password = "UiMemberCheck9!" + suffix
    member_name = "ui_member_" + suffix
    screenshot = PROJECT / "ai" / "evidence" / "r15-04-member-browser.png"
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(executable_path=str(EDGE), headless=True)
        owner_context = browser.new_context(viewport={"width": 1540, "height": 1000}, locale="zh-CN")
        member_context = browser.new_context(viewport={"width": 1540, "height": 1000}, locale="zh-CN")
        owner = owner_context.new_page()
        member = member_context.new_page()
        errors: list[str] = []
        owner.on("pageerror", lambda error: errors.append("owner: " + str(error)))
        member.on("pageerror", lambda error: errors.append("member: " + str(error)))
        register(member, member_name, password, "受邀成员")
        register(owner, "ui_owner_" + suffix, password, "系统所属人")
        owner.get_by_role("button", name="创建第一个系统").click()
        owner.get_by_placeholder("例如：采购管理").fill("成员验收系统")
        owner.get_by_placeholder("例如：purchase").fill("ui_access_" + suffix)
        owner.get_by_role("button", name="创建并进入").click()
        owner.get_by_role("heading", name="模块组", exact=True).wait_for()
        owner.get_by_title("创建模块组").click()
        owner.get_by_placeholder("例如：销售业务").fill("服务业务")
        owner.get_by_placeholder("例如：sales").fill("service")
        owner.get_by_role("button", name="创建模块组", exact=True).click()
        owner.get_by_title("创建业务模块").click()
        owner.get_by_placeholder("例如：客户").fill("服务工单")
        owner.get_by_placeholder("例如：customer").fill("ticket")
        owner.get_by_role("button", name="创建模块", exact=True).click()
        owner.get_by_role("button", name="添加字段").click()
        owner.get_by_placeholder("例如：客户名称").fill("工单标题")
        owner.get_by_placeholder("例如：customer_name").fill("title")
        owner.get_by_role("button", name="保存字段").click()
        owner.get_by_role("button", name="发布模块", exact=True).click()
        owner.get_by_text("发布成功，当前为第 1 版。").wait_for()
        owner.get_by_role("button", name="成员与权限").click()
        owner.locator(".grant-options input").nth(0).check()
        owner.locator(".grant-options input").nth(1).check()
        owner.get_by_role("button", name="保存菜单权限").click()
        owner.get_by_text("角色菜单权限已保存，立即生效。").wait_for()
        owner.get_by_placeholder("输入已有用户名").fill(member_name)
        owner.get_by_role("button", name="添加成员").click()
        owner.get_by_text("账号已加入系统主租户。").wait_for()
        member.reload()
        member.locator(".system-card").filter(has_text="成员验收系统").click()
        member.locator(".runtime-top button", has_text="服务业务").wait_for()
        assert member.get_by_role("button", name="业务建模").count() == 0
        assert member.get_by_role("button", name="成员与权限").count() == 0
        member.locator(".runtime-side button", has_text="服务工单").click()
        member.locator(".runtime-fields").get_by_text("工单标题").wait_for()
        member.screenshot(path=str(screenshot), full_page=True)
        assert not errors, errors
        browser.close()
    print(json.dumps({"status": "passed", "screenshot": str(screenshot),
                      "checks": ["owner_role_grant_page", "owner_invite_page", "member_system_card", "member_runtime_group", "member_runtime_module", "member_no_admin_tabs", "no_page_errors"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
