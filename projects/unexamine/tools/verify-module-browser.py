"""真实浏览器完成系统、模块组、模块、字段、发布及运行导航旅程。"""

from __future__ import annotations

import json
import secrets
from pathlib import Path

from playwright.sync_api import sync_playwright


PROJECT = Path(__file__).resolve().parents[1]
EDGE = Path(r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe")
URL = "http://127.0.0.1:15173/"


def main() -> None:
    suffix = secrets.token_hex(4)
    screenshot = PROJECT / "ai" / "evidence" / "r15-03-module-browser.png"
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(executable_path=str(EDGE), headless=True)
        page = browser.new_page(viewport={"width": 1540, "height": 1000}, locale="zh-CN")
        errors: list[str] = []
        page.on("pageerror", lambda error: errors.append(str(error)))
        page.goto(URL)
        page.get_by_role("button", name="还没有账号？立即注册").click()
        page.get_by_placeholder("例如 admin01").fill("builder_" + suffix)
        page.get_by_placeholder("你的姓名或团队名称").fill("建模验收员")
        page.get_by_placeholder("至少 8 位").fill("BuilderCheck9!" + suffix)
        page.get_by_role("button", name="注册并进入").click()
        page.get_by_role("button", name="创建第一个系统").click()
        page.get_by_placeholder("例如：采购管理").fill("建模验收系统")
        page.get_by_placeholder("例如：purchase").fill("builder_sys_" + suffix)
        page.get_by_role("button", name="创建并进入").click()
        page.get_by_role("heading", name="模块组", exact=True).wait_for()
        page.get_by_title("创建模块组").click()
        page.get_by_placeholder("例如：销售业务").fill("客户业务")
        page.get_by_placeholder("例如：sales").fill("customers_" + suffix)
        page.get_by_role("button", name="创建模块组", exact=True).click()
        page.get_by_title("创建业务模块").click()
        page.get_by_placeholder("例如：客户").fill("客户档案")
        page.get_by_placeholder("例如：customer").fill("customer_" + suffix)
        page.get_by_role("button", name="创建模块", exact=True).click()
        page.get_by_role("button", name="添加字段").click()
        page.get_by_placeholder("例如：客户名称").fill("客户名称")
        page.get_by_placeholder("例如：customer_name").fill("customer_name")
        page.get_by_role("button", name="保存字段").click()
        page.get_by_role("button", name="发布模块", exact=True).click()
        page.get_by_text("发布成功，当前为第 1 版。").wait_for()
        page.locator(".mode-tabs button").nth(1).click()
        page.locator(".runtime-top button", has_text="客户业务").wait_for()
        page.locator(".runtime-side button", has_text="客户档案").click()
        page.locator(".runtime-fields").get_by_text("客户名称").wait_for()
        page.screenshot(path=str(screenshot), full_page=True)
        assert not errors, errors
        browser.close()
    print(json.dumps({"status": "passed", "screenshot": str(screenshot),
                      "checks": ["system_page", "group_page", "module_page", "field_page", "publish_page", "runtime_top_group", "runtime_side_module", "published_field", "no_page_errors"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
