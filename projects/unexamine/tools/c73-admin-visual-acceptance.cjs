const { chromium } = require('C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright')
const path = require('node:path')

const baseUrl = 'http://127.0.0.1:15174'
const evidenceRoot = path.resolve(__dirname, '../ai/evidence')

async function login(page, username, password) {
  await page.goto(`${baseUrl}/login`)
  await page.getByPlaceholder('请输入用户名').fill(username)
  await page.getByPlaceholder('请输入密码').fill(password)
  await page.getByRole('button', { name: /登.*录/ }).click()
  await page.waitForLoadState('networkidle')
}

async function main() {
  const browser = await chromium.launch({
    headless: true,
    executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  })
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await context.newPage()

  try {
    await login(page, 'admin', '123123aa')
    await page.goto(`${baseUrl}/platform/admin/configurations?category=PLATFORM_INFO`)
    await page.waitForLoadState('networkidle')
    const platformItems = page.locator('[aria-label="平台管理导航"] > button')
    const platformItemCount = await platformItems.count()
    if (platformItemCount !== 11) throw new Error('platform admin does not expose the required eleven first-level entries')
    if (await page.locator('.ant-btn-primary:visible').count() !== 1) throw new Error('platform information page does not have one primary action')
    await page.screenshot({ path: path.join(evidenceRoot, 'CYCLE-073-platform-admin.png'), fullPage: true })

    await login(page, 'c68_owner_227384', 'Correct-c68-live-password!')
    await page.getByRole('button', { name: '进入北辰客户协作中心' }).click()
    await page.waitForURL('**/systems/44')
    await page.waitForTimeout(300)
    await page.goto(`${baseUrl}/systems/44/admin?section=roles`)
    await page.waitForLoadState('networkidle')
    const systemItems = page.locator('[aria-label="系统后台导航"] > button')
    const systemItemCount = await systemItems.count()
    if (systemItemCount !== 11) throw new Error(`system admin exposes ${systemItemCount} first-level entries instead of eleven at ${page.url()}`)
    const roleSteps = page.locator('[aria-label="角色配置步骤"] > button')
    if (await roleSteps.count() !== 3) throw new Error('role configuration is not organized as a three-step task')
    await roleSteps.nth(1).click()
    if (!await page.getByText('菜单、动作与数据范围', { exact: true }).count()) throw new Error('permission step did not open')
    await roleSteps.nth(2).click()
    if (!await page.getByText('受影响菜单预览', { exact: true }).count()) throw new Error('publish step did not open')
    await roleSteps.nth(0).click()
    await page.screenshot({ path: path.join(evidenceRoot, 'CYCLE-073-system-admin.png'), fullPage: true })

    process.stdout.write(JSON.stringify({
      platformFirstLevelEntries: platformItemCount,
      systemFirstLevelEntries: systemItemCount,
      roleTaskSteps: await roleSteps.count(),
      platformScope: 'all systems',
      systemScope: 'system 44 / current workspace',
    }, null, 2))
  } finally {
    await browser.close()
  }
}

main().catch(error => {
  console.error(error)
  process.exitCode = 1
})
