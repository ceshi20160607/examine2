"""真实双浏览器从建模、授权走到受邀成员录入、搜索和编辑记录。"""

from __future__ import annotations

import json
import secrets
from pathlib import Path

from playwright.sync_api import sync_playwright

PROJECT = Path(__file__).resolve().parents[1]
EDGE = Path(r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe")
URL = "http://127.0.0.1:15173/"


def register(page, username, password):
    page.goto(URL)
    page.get_by_role("button", name="还没有账号？立即注册").click()
    page.get_by_placeholder("例如 admin01").fill(username)
    page.get_by_placeholder("你的姓名或团队名称").fill(username)
    page.get_by_placeholder("至少 8 位").fill(password)
    page.get_by_role("button", name="注册并进入").click()
    page.get_by_role("heading", name="我的系统").wait_for()


def main():
    suffix = secrets.token_hex(4)
    password = "UiRecordCheck9!" + suffix
    member_name = "ui_rec_member_" + suffix
    screenshot = PROJECT / "ai" / "evidence" / "r15-04-record-browser.png"
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(executable_path=str(EDGE), headless=True)
        owner_context = browser.new_context(viewport={"width": 1540, "height": 1050}, locale="zh-CN")
        member_context = browser.new_context(viewport={"width": 1540, "height": 1050}, locale="zh-CN")
        owner = owner_context.new_page()
        member = member_context.new_page()
        errors: list[str] = []
        owner.on("pageerror", lambda error: errors.append("owner: " + str(error)))
        member.on("pageerror", lambda error: errors.append("member: " + str(error)))
        register(member, member_name, password)
        register(owner, "ui_rec_owner_" + suffix, password)
        owner.get_by_role("button", name="创建第一个系统").click()
        owner.get_by_placeholder("例如：采购管理").fill("记录浏览器系统")
        owner.get_by_placeholder("例如：purchase").fill("ui_record_" + suffix)
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
        owner.locator(".grant-options input").nth(2).check()
        owner.get_by_role("button", name="保存菜单权限").click()
        owner.get_by_text("角色菜单权限已保存，立即生效。").wait_for()
        owner.get_by_placeholder("输入已有用户名").fill(member_name)
        owner.get_by_role("button", name="添加成员").click()
        owner.get_by_text("账号已加入系统主租户。").wait_for()

        member.reload()
        member.locator(".system-card").filter(has_text="记录浏览器系统").click()
        member.locator(".runtime-side button", has_text="服务工单").click()
        member.get_by_role("button", name="新增记录").click()
        member.locator(".record-form-grid input[type=text]").fill("首次报修工单")
        member.get_by_role("button", name="保存记录").click()
        member.locator(".record-row", has_text="首次报修工单").wait_for()
        member.locator(".record-row", has_text="首次报修工单").click()
        member.locator(".record-detail").get_by_role("heading", name="首次报修工单").wait_for()
        member.locator(".record-detail").get_by_role("button", name="编辑").click()
        member.wait_for_timeout(500)
        member.locator(".record-form-grid input[type=text]").fill("已处理报修工单")
        member.get_by_role("button", name="保存记录").click()
        member.locator(".record-row", has_text="已处理报修工单").wait_for()
        member.get_by_placeholder("搜索记录标题").fill("已处理")
        member.get_by_role("button", name="查询").click()
        assert member.locator(".record-row").count() == 1
        member.screenshot(path=str(screenshot), full_page=True)

        owner.locator(".mode-tabs button").filter(has_text="运行预览").click()
        owner.locator(".runtime-side button", has_text="服务工单").click()
        owner.locator(".record-row", has_text="已处理报修工单").wait_for()
        assert not errors, errors
        browser.close()
    print(json.dumps({"status": "passed", "screenshot": str(screenshot),
                      "checks": ["owner_model_publish", "role_grant_invite", "member_dynamic_form", "member_detail", "member_edit", "member_search", "owner_readback", "no_page_errors"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
