"""真实浏览器沿当前页面完成注册、建系统、进入系统与重新登录。"""

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
    username = "browser_" + suffix
    code = "web_" + suffix
    password = "BrowserCheck9!" + suffix
    screenshot = PROJECT / "ai" / "evidence" / "r15-02-admin-browser.png"
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(executable_path=str(EDGE), headless=True)
        page = browser.new_page(viewport={"width": 1440, "height": 900}, locale="zh-CN")
        errors: list[str] = []
        page.on("pageerror", lambda error: errors.append(str(error)))
        page.goto(URL)
        page.get_by_role("button", name="还没有账号？立即注册").click()
        page.get_by_placeholder("例如 admin01").fill(username)
        page.get_by_placeholder("你的姓名或团队名称").fill("浏览器验收员")
        page.get_by_placeholder("至少 8 位").fill(password)
        page.get_by_role("button", name="注册并进入").click()
        page.get_by_role("heading", name="我的系统").wait_for()
        page.get_by_role("button", name="创建第一个系统").click()
        page.get_by_placeholder("例如：采购管理").fill("浏览器验收系统")
        page.get_by_placeholder("例如：purchase").fill(code)
        page.get_by_placeholder("这个系统解决什么业务问题？").fill("验证管理员页面旅程")
        page.get_by_role("button", name="创建并进入").click()
        page.get_by_role("heading", name="浏览器验收系统", exact=True).first.wait_for()
        assert page.url.startswith(URL + "systems/"), page.url
        assert page.locator(".mode-tabs").get_by_text("业务建模").count() == 1
        page.screenshot(path=str(screenshot), full_page=True)
        page.get_by_role("button", name="退出登录").click()
        page.get_by_role("heading", name="登录工作空间").wait_for()
        page.get_by_placeholder("例如 admin01").fill(username)
        page.get_by_placeholder("至少 8 位").fill(password)
        page.locator(".auth-card .primary-button").click()
        page.get_by_role("heading", name="我的系统").wait_for()
        assert page.locator(".system-card").count() == 1
        assert not errors, errors
        browser.close()
    print(json.dumps({"status": "passed", "code": code, "screenshot": str(screenshot),
                      "checks": ["register_page", "create_system_page", "enter_system_page", "logout_page", "login_page", "no_page_errors"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
