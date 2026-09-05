const { chromium } = require('C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright')
const path = require('node:path')

const baseUrl = 'http://127.0.0.1:15174'
const evidencePath = path.resolve(__dirname, '../ai/evidence/CYCLE-073-runtime-mobile.png')

async function waitForData(page) {
  await page.waitForLoadState('networkidle')
  await page.waitForTimeout(250)
}

async function archiveFirstMatching(page, text, reason) {
  const record = page.locator('.mobile-record-list > button').filter({ hasText: text }).first()
  if (!await record.count()) return false
  await record.click()
  await page.locator('.ant-drawer').getByRole('button', { name: /更多操作/ }).click()
  await page.getByRole('menuitem', { name: '归档' }).click()
  await page.locator('.ant-modal textarea').fill(reason)
  await page.getByRole('button', { name: '确认执行' }).click()
  await waitForData(page)
  return true
}

async function main() {
  const browser = await chromium.launch({
    headless: true,
    executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  })
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1 })
  const page = await context.newPage()

  try {
    await page.goto(`${baseUrl}/login`)
    await page.getByPlaceholder('请输入用户名').fill('c68_seller_227384')
    await page.getByPlaceholder('请输入密码').fill('Correct-c68-live-password!')
    await page.getByRole('button', { name: /登.*录/ }).click()
    await waitForData(page)
    await page.getByRole('button', { name: '进入北辰客户协作中心' }).click()
    await waitForData(page)
    await page.getByRole('button', { name: /客户 客户经营/ }).click()
    await waitForData(page)

    await archiveFirstMatching(page, '重复编号检查', '清理失败场景测试数据')
    while (await archiveFirstMatching(page, '海岳设备', '清理未完成的移动验收数据')) { /* remove prior interrupted runs */ }

    const search = page.getByPlaceholder('搜索标题、编号和业务字段')
    await search.fill('北辰')
    await search.press('Enter')
    await waitForData(page)
    if (!await page.locator('.mobile-record-list').getByText('北辰科技', { exact: true }).count()) {
      throw new Error('mobile query did not return the expected customer')
    }
    await search.fill('')
    await search.press('Enter')
    await waitForData(page)

    await page.getByRole('button', { name: /新建客户/ }).click()
    let formInputs = page.locator('.ant-drawer input')
    await formInputs.nth(0).fill('临时保存测试')
    await formInputs.nth(1).fill('KH-2026-TEMP')
    await formInputs.nth(2).fill('临时保存测试')
    const failNextSave = async route => {
      if (route.request().method() !== 'POST') return route.continue()
      await route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({ code: 'SERVICE_UNAVAILABLE', message: '当前网络繁忙，请稍后重试', data: null, traceId: 'mobile-save-check' }),
      })
    }
    await page.route('**/api/runtime/modules/*/records', failNextSave)
    await page.locator('.ant-drawer').getByRole('button', { name: /保.*存/ }).click()
    await page.waitForTimeout(350)
    await page.unroute('**/api/runtime/modules/*/records', failNextSave)
    if (await formInputs.nth(0).inputValue() !== '临时保存测试' || !await page.getByText(/稍后重试/).count()) {
      throw new Error('failed save did not preserve input or report the conflict')
    }
    await page.locator('.ant-drawer').getByRole('button', { name: /取.*消/ }).click()
    await page.getByRole('button', { name: '保留草稿并离开' }).click()
    await page.waitForTimeout(200)

    await page.getByRole('button', { name: /新建客户/ }).click()
    formInputs = page.locator('.ant-drawer input')
    const recordNumber = `KH-2026-M${String(Date.now()).slice(-6)}`
    await formInputs.nth(0).fill('海岳设备')
    await formInputs.nth(1).fill(recordNumber)
    await formInputs.nth(2).fill('海岳设备')
    await page.locator('.ant-drawer').getByRole('button', { name: /保.*存/ }).click()
    await waitForData(page)

    await page.locator('.mobile-record-list > button').filter({ hasText: recordNumber }).click()
    await page.waitForTimeout(250)
    await page.locator('.ant-drawer').getByRole('button', { name: /编.*辑/ }).click()
    formInputs = page.locator('.ant-drawer input')
    await formInputs.nth(0).fill('海岳设备（售后）')
    await formInputs.nth(2).fill('海岳设备（售后）')
    await page.locator('.ant-drawer').getByRole('button', { name: /保.*存/ }).click()
    await waitForData(page)

    const hasHorizontalOverflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)
    const topDrawer = page.locator('.ant-drawer:visible').last()
    const visiblePrimaryActions = await topDrawer.locator('.ant-btn-primary:visible').count()
    const drawerBox = await topDrawer.boundingBox()
    if (hasHorizontalOverflow) throw new Error('mobile runtime page has horizontal overflow')
    if (visiblePrimaryActions > 1) throw new Error(`mobile detail exposes ${visiblePrimaryActions} primary actions`)
    await page.waitForTimeout(3500)
    await page.screenshot({ path: evidencePath, fullPage: true })

    await page.locator('.ant-drawer').getByRole('button', { name: /更多操作/ }).click()
    await page.getByRole('menuitem', { name: '归档' }).click()
    await page.locator('.ant-modal textarea').fill('客户资料阶段性归档')
    await page.getByRole('button', { name: '确认执行' }).click()
    await waitForData(page)
    await page.getByText('已归档', { exact: true }).click()
    await waitForData(page)
    if (!await page.locator('.mobile-record-list > button').filter({ hasText: recordNumber }).count()) {
      throw new Error('archived customer was not available in the archived list')
    }

    process.stdout.write(JSON.stringify({
      viewport: '390x844',
      journey: ['query', 'failed-save-preserves-input', 'create', 'detail', 'edit', 'archive', 'archived-list'],
      horizontalOverflow: hasHorizontalOverflow,
      visiblePrimaryActions,
      drawerBox,
      evidencePath,
    }, null, 2))
  } finally {
    await browser.close()
  }
}

main().catch(error => {
  console.error(error)
  process.exitCode = 1
})
