"""真实 Edge 浏览器验证业务记录附件上传、缩略图和筛选导出下载。"""

from __future__ import annotations

import json
import secrets
from pathlib import Path

from playwright.sync_api import sync_playwright

PROJECT = Path(__file__).resolve().parents[1]
EDGE = Path(r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe")
URL = "http://127.0.0.1:15173/"
PNG = bytes.fromhex("89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c4890000000b49444154789c636000020000050001a5f645400000000049454e44ae426082")


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
    password = "UiFileCheck9!" + suffix
    owner_name, member_name = "ui_file_owner_" + suffix, "ui_file_member_" + suffix
    screenshot = PROJECT / "ai" / "evidence" / "r15-06-file-export-browser.png"
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(executable_path=str(EDGE), headless=True)
        owner_context = browser.new_context(viewport={"width": 1540, "height": 1050}, locale="zh-CN", accept_downloads=True)
        member_context = browser.new_context(viewport={"width": 1540, "height": 1050}, locale="zh-CN", accept_downloads=True)
        owner, member = owner_context.new_page(), member_context.new_page()
        errors = []
        owner.on("pageerror", lambda error: errors.append("owner: " + str(error)))
        member.on("pageerror", lambda error: errors.append("member: " + str(error)))
        register(member, member_name, password)
        register(owner, owner_name, password)
        owner.get_by_role("button", name="创建第一个系统").click()
        owner.get_by_placeholder("例如：采购管理").fill("文件导出浏览器系统")
        owner.get_by_placeholder("例如：purchase").fill("ui_file_" + suffix)
        owner.get_by_role("button", name="创建并进入").click()
        owner.get_by_role("heading", name="模块组", exact=True).wait_for()
        owner.get_by_title("创建模块组").click()
        owner.get_by_placeholder("例如：销售业务").fill("服务业务")
        owner.get_by_placeholder("例如：sales").fill("service")
        owner.get_by_role("button", name="创建模块组", exact=True).click()
        owner.get_by_title("创建业务模块").click()
        owner.get_by_placeholder("例如：客户").fill("报告")
        owner.get_by_placeholder("例如：customer").fill("report")
        owner.get_by_role("button", name="创建模块", exact=True).click()
        owner.get_by_role("button", name="添加字段").click()
        owner.get_by_placeholder("例如：客户名称").fill("报告标题")
        owner.get_by_placeholder("例如：customer_name").fill("title")
        owner.get_by_role("button", name="保存字段").click()
        owner.get_by_role("button", name="发布模块", exact=True).click()
        owner.get_by_text("发布成功，当前为第 1 版。").wait_for()
        owner.get_by_role("button", name="成员与权限").click()
        for index in range(4):
            owner.locator(".grant-options input").nth(index).check()
        owner.get_by_role("button", name="保存菜单权限").click()
        owner.get_by_text("角色菜单权限已保存，立即生效。").wait_for()
        owner.get_by_placeholder("输入已有用户名").fill(member_name)
        owner.get_by_role("button", name="添加成员").click()
        owner.get_by_text("账号已加入系统主租户。").wait_for()

        member.reload()
        member.locator(".system-card").filter(has_text="文件导出浏览器系统").click()
        member.locator(".runtime-side button", has_text="报告").click()
        member.get_by_role("button", name="新增记录").click()
        member.locator(".record-form-grid input[type=text]").fill("报修报告")
        member.get_by_role("button", name="保存记录").click()
        member.locator(".record-row", has_text="报修报告").click()
        member.locator(".record-detail").get_by_role("heading", name="报修报告").wait_for()
        member.locator(".file-upload input").set_input_files({"name": "凭证.png", "mimeType": "image/png", "buffer": PNG})
        member.get_by_text("附件已上传。").wait_for()
        file_row = member.locator(".record-files .file-row", has_text="凭证.png")
        file_row.wait_for()
        assert file_row.locator("img").count() == 1
        with member.expect_download() as download_info:
            file_row.get_by_role("link", name="下载").click()
        assert download_info.value.suggested_filename == "凭证.png"

        member.get_by_placeholder("搜索记录标题").fill("报修")
        member.get_by_role("button", name="查询").click()
        member.get_by_role("button", name="导出数据").click()
        member.locator(".export-panel").get_by_text("报告标题").wait_for()
        member.get_by_role("button", name="提交导出任务").click()
        member.get_by_text("导出任务已提交，可在下方刷新状态并下载。").wait_for()
        for _ in range(20):
            if member.locator(".export-panel .file-row", has_text="已完成").count():
                break
            member.get_by_role("button", name="刷新状态").click()
            member.wait_for_timeout(250)
        row = member.locator(".export-panel .file-row", has_text="已完成")
        row.wait_for()
        assert "1 行" in row.inner_text()
        with member.expect_download() as download_info:
            row.get_by_role("link", name="下载 CSV").click()
        assert download_info.value.suggested_filename.endswith(".csv")
        member.screenshot(path=str(screenshot), full_page=True)

        owner.get_by_role("button", name="运行预览").click()
        owner.locator(".runtime-side button", has_text="报告").click()
        owner.locator(".record-row", has_text="报修报告").click()
        owner.locator(".record-files .file-row", has_text="凭证.png").wait_for()
        assert not errors, errors
        browser.close()
    print(json.dumps({"status": "passed", "screenshot": str(screenshot),
                      "checks": ["member_upload", "thumbnail", "download", "filtered_export_job", "csv_download",
                                 "owner_attachment_readback", "no_page_errors"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
