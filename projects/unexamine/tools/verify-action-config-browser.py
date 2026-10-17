"""真实 Edge 页面验证动作草稿、菜单改名与发布后运行页显示。"""

from __future__ import annotations

import json
import secrets
from pathlib import Path

from playwright.sync_api import sync_playwright

PROJECT = Path(__file__).resolve().parents[1]
EDGE = Path(r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe")
URL = "http://127.0.0.1:15173/"


def main():
    suffix = secrets.token_hex(4)
    screenshot = PROJECT / "ai" / "evidence" / "r15-08-action-config-browser.png"
    draft_screenshot = PROJECT / "ai" / "evidence" / "r15-08-action-config-draft.png"
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(executable_path=str(EDGE), headless=True)
        context = browser.new_context(viewport={"width": 1500, "height": 1050}, locale="zh-CN")
        page = context.new_page()
        errors = []
        page.on("pageerror", lambda error: errors.append(str(error)))
        page.goto(URL)
        page.get_by_role("button", name="还没有账号？立即注册").click()
        page.get_by_placeholder("例如 admin01").fill("ui_action_" + suffix)
        page.get_by_placeholder("你的姓名或团队名称").fill("动作验收")
        page.get_by_placeholder("至少 8 位").fill("UiActionCheck9!" + suffix)
        page.get_by_role("button", name="注册并进入").click()
        page.get_by_role("button", name="创建第一个系统").click()
        page.get_by_placeholder("例如：采购管理").fill("动作浏览器系统")
        page.get_by_placeholder("例如：purchase").fill("ui_action_" + suffix)
        page.get_by_role("button", name="创建并进入").click()
        page.get_by_role("heading", name="模块组", exact=True).wait_for()
        page.get_by_title("创建模块组").click()
        page.get_by_placeholder("例如：销售业务").fill("销售业务")
        page.get_by_placeholder("例如：sales").fill("sales")
        page.get_by_role("button", name="创建模块组", exact=True).click()
        page.get_by_title("创建业务模块").click()
        page.get_by_placeholder("例如：客户").fill("客户")
        page.get_by_placeholder("例如：customer").fill("customer")
        page.get_by_role("button", name="创建模块", exact=True).click()
        page.get_by_role("button", name="添加字段").click()
        page.get_by_placeholder("例如：客户名称").fill("客户名称")
        page.get_by_placeholder("例如：customer_name").fill("customer_name")
        page.get_by_role("button", name="保存字段").click()
        page.get_by_role("button", name="发布模块", exact=True).click()
        page.get_by_text("发布成功，当前为第 1 版。").wait_for()
        page.get_by_role("button", name="运行预览").click()
        page.locator(".record-toolbar").wait_for()
        page.get_by_role("button", name="导出数据").wait_for()
        page.get_by_role("button", name="业务建模").click()
        draft = page.locator(".module-draft")
        draft.locator("label", has_text="导出").locator("input").uncheck()
        page.get_by_text("动作草稿已保存，发布新版本后生效。").wait_for()
        draft.get_by_label("列表菜单名称").fill("客户办理")
        draft.get_by_role("button", name="保存菜单名称").click()
        page.get_by_text("菜单名称草稿已保存，发布新版本后生效。").wait_for()
        page.screenshot(path=str(draft_screenshot), full_page=True)
        page.get_by_role("button", name="运行预览").click()
        page.locator(".record-toolbar").wait_for()
        page.get_by_role("button", name="导出数据").wait_for()
        assert page.locator(".runtime-side button", has_text="客户办理").count() == 0
        page.get_by_role("button", name="业务建模").click()
        page.get_by_role("button", name="发布新版本").click()
        page.get_by_text("发布成功，当前为第 2 版。").wait_for()
        page.get_by_role("button", name="运行预览").click()
        page.locator(".runtime-side button", has_text="客户办理").wait_for()
        page.get_by_text("已发布 · 第 2 版").wait_for()
        page.locator(".record-toolbar").wait_for()
        assert page.get_by_role("button", name="导出数据").count() == 0
        assert page.get_by_role("button", name="导入 Excel").count() == 1
        page.screenshot(path=str(screenshot), full_page=True)
        assert not errors, errors
        browser.close()
    print(json.dumps({"status": "passed", "screenshot": str(screenshot), "draft_screenshot": str(draft_screenshot),
                      "checks": ["draft_action", "draft_menu", "old_publish_stable", "new_publish_effective",
                                 "runtime_action_visibility", "no_page_errors"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
