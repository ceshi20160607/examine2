"""真实 Edge 浏览器验证所属人配置、成员发起和审批人待办办理。"""

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
    password = "UiApproval9!" + suffix
    names = ["ui_flow_owner_" + suffix, "ui_flow_app_" + suffix, "ui_flow_review_" + suffix]
    screenshot = PROJECT / "ai" / "evidence" / "r15-05-approval-browser.png"
    task_screenshot = PROJECT / "ai" / "evidence" / "r15-05-approval-task-browser.png"
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(executable_path=str(EDGE), headless=True)
        contexts = [browser.new_context(viewport={"width": 1540, "height": 1050}, locale="zh-CN") for _ in names]
        owner, applicant, reviewer = [context.new_page() for context in contexts]
        errors = []
        for name, page in zip(names, [owner, applicant, reviewer]):
            page.on("pageerror", lambda error, name=name: errors.append(name + ": " + str(error)))
        for page, username in zip([owner, applicant, reviewer], names):
            register(page, username, password)

        owner.get_by_role("button", name="创建第一个系统").click()
        owner.get_by_placeholder("例如：采购管理").fill("审批浏览器系统")
        owner.get_by_placeholder("例如：purchase").fill("ui_flow_" + suffix)
        owner.get_by_role("button", name="创建并进入").click()
        owner.get_by_role("heading", name="模块组", exact=True).wait_for()
        owner.get_by_title("创建模块组").click()
        owner.get_by_placeholder("例如：销售业务").fill("服务业务")
        owner.get_by_placeholder("例如：sales").fill("service")
        owner.get_by_role("button", name="创建模块组", exact=True).click()
        owner.get_by_title("创建业务模块").click()
        owner.get_by_placeholder("例如：客户").fill("申请单")
        owner.get_by_placeholder("例如：customer").fill("request")
        owner.get_by_role("button", name="创建模块", exact=True).click()
        owner.get_by_role("button", name="添加字段").click()
        owner.get_by_placeholder("例如：客户名称").fill("申请标题")
        owner.get_by_placeholder("例如：customer_name").fill("title")
        owner.get_by_role("button", name="保存字段").click()
        owner.get_by_role("button", name="发布模块", exact=True).click()
        owner.get_by_text("发布成功，当前为第 1 版。").wait_for()
        owner.get_by_role("button", name="成员与权限").click()
        for index in range(3):
            owner.locator(".grant-options input").nth(index).check()
        owner.get_by_role("button", name="保存菜单权限").click()
        owner.get_by_text("角色菜单权限已保存，立即生效。").wait_for()
        for username in names[1:]:
            owner.get_by_placeholder("输入已有用户名").fill(username)
            owner.get_by_role("button", name="添加成员").click()
            owner.get_by_text("账号已加入系统主租户。").wait_for()

        owner.get_by_role("button", name="审批配置与待办").click()
        owner.get_by_label("流程名称").wait_for()
        owner.wait_for_function("() => document.querySelector('input') && [...document.querySelectorAll('input')].some(i => i.value.includes('审批'))")
        owner.get_by_label("审批人").select_option(label=f"{names[2]} · {names[2]}")
        owner.get_by_role("button", name="保存草稿").click()
        try:
            owner.get_by_text("审批草稿已保存，发布后新申请才会使用。").wait_for(timeout=8000)
        except Exception:
            diagnostic = owner.locator(".access-card:first-child").evaluate("el => ({text: el.innerText, fields: [...el.querySelectorAll('input,select')].map(i => ({value:i.value, valid:i.checkValidity()}))})")
            raise AssertionError("审批草稿页面反馈：" + repr(diagnostic))
        owner.get_by_role("button", name="发布审批", exact=True).click()
        owner.get_by_text("审批已发布第 1 版。").wait_for()

        applicant.reload()
        applicant.locator(".system-card").filter(has_text="审批浏览器系统").click()
        applicant.locator(".runtime-side button", has_text="申请单").click()
        applicant.get_by_role("button", name="新增记录").click()
        applicant.locator(".record-form-grid input[type=text]").fill("出差审批申请")
        applicant.get_by_role("button", name="保存记录").click()
        applicant.locator(".record-row", has_text="出差审批申请").click()
        applicant.locator(".record-detail").get_by_role("heading", name="出差审批申请").wait_for()
        applicant.get_by_role("button", name="发起审批").click()
        applicant.get_by_text("审批已发起，记录处于待审批状态。").wait_for()
        assert applicant.locator(".record-row", has_text="待审批").count() == 1
        assert applicant.locator(".record-detail").get_by_role("button", name="编辑").count() == 0

        reviewer.reload()
        reviewer.locator(".system-card").filter(has_text="审批浏览器系统").click()
        reviewer.get_by_role("button", name="我的审批待办").click()
        reviewer.locator(".access-row", has_text="出差审批申请").get_by_role("button", name="查看办理").click()
        reviewer.locator(".access-card", has_text="TASK DETAIL").get_by_role("heading", name="出差审批申请").wait_for()
        reviewer.screenshot(path=str(task_screenshot), full_page=True)
        reviewer.locator(".access-card", has_text="TASK DETAIL").get_by_placeholder("拒绝时必须填写原因").fill("材料需要补充")
        reviewer.get_by_role("button", name="拒绝", exact=True).click()
        reviewer.get_by_text("已拒绝审批。").wait_for()
        reviewer.locator(".access-row", has_text="出差审批申请").wait_for(state="detached")
        reviewer.screenshot(path=str(screenshot), full_page=True)

        applicant.reload()
        applicant.locator(".runtime-side button", has_text="申请单").click()
        applicant.locator(".record-row", has_text="已拒绝").wait_for()
        applicant.locator(".record-row", has_text="出差审批申请").click()
        applicant.locator(".record-detail").get_by_role("button", name="编辑").wait_for()
        assert not errors, errors
        browser.close()
    print(json.dumps({"status": "passed", "screenshot": str(screenshot), "task_screenshot": str(task_screenshot),
                      "checks": ["owner_config_publish", "member_submit", "pending_edit_hidden",
                                 "reviewer_task_detail", "reject", "applicant_status_readback", "no_page_errors"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
