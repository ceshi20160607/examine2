const { chromium } = require('C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright')
const path = require('node:path')

const baseUrl = 'http://127.0.0.1:15174'
const evidenceRoot = path.resolve(__dirname, '../ai/evidence')
const password = 'Correct-c68-live-password!'
const viewports = {
  desktop: { width: 1440, height: 900 },
  tablet: { width: 1024, height: 768 },
  mobile: { width: 390, height: 844 },
}

async function login(page, username, userPassword) {
  await page.goto(`${baseUrl}/login`)
  await page.getByPlaceholder('请输入用户名').fill(username)
  await page.getByPlaceholder('请输入密码').fill(userPassword)
  await page.getByRole('button', { name: /登.*录/ }).click()
  await page.waitForLoadState('networkidle')
}

async function enterSystem(page) {
  if (!page.url().includes('/systems/44')) {
    await page.getByRole('button', { name: '进入北辰客户协作中心' }).click()
    await page.waitForURL('**/systems/44')
  }
}

async function settle(page) {
  await page.waitForLoadState('domcontentloaded')
  await page.waitForTimeout(450)
  await page.evaluate(() => document.fonts?.ready)
}

async function assertPage(page, label, forbidTechnical = true) {
  await settle(page)
  const report = await page.evaluate(() => {
    const root = document.documentElement
    const bodyText = document.body.innerText.replace(/\s+/g, ' ').trim()
    const visibleMain = [...document.querySelectorAll('main, [role="main"], .product-shell__main, .system-home-main')]
      .some(node => {
        const box = node.getBoundingClientRect()
        const style = getComputedStyle(node)
        return box.width > 0 && box.height > 0 && style.display !== 'none' && style.visibility !== 'hidden'
      })
    return {
      bodyText,
      title: document.title,
      scrollWidth: root.scrollWidth,
      innerWidth: window.innerWidth,
      visibleMain,
    }
  })
  if (!report.visibleMain || report.bodyText.length < 40) throw new Error(`${label}: page has no meaningful main content`)
  if (report.scrollWidth > report.innerWidth + 2) {
    throw new Error(`${label}: root horizontal overflow ${report.scrollWidth} > ${report.innerWidth}`)
  }
  if (/Internal Server Error|Cannot read properties|Failed to fetch|系统出现意外错误/.test(report.bodyText)) {
    throw new Error(`${label}: visible application error`)
  }
  if (forbidTechnical) {
    const technical = report.bodyText.match(/CYCLE[-_ ]?\d+|草稿\s*r\d+|发布版\s*v\d+|\b(?:PUBLISHED|DRAFT|READY|ACTIVE|DISABLED)\b/)
    if (technical) throw new Error(`${label}: technical text leaked into core content: ${technical[0]}`)
  }
  return { label, url: page.url(), width: report.innerWidth, scrollWidth: report.scrollWidth }
}

async function assertKeyboardFocus(page, label) {
  for (let attempt = 0; attempt < 8; attempt += 1) {
    await page.keyboard.press('Tab')
    const focus = await page.evaluate(() => {
      const active = document.activeElement
      if (!active || active === document.body) return null
      const box = active.getBoundingClientRect()
      return { tag: active.tagName, width: box.width, height: box.height }
    })
    if (focus && focus.width > 0 && focus.height > 0) return focus
  }
  throw new Error(`${label}: keyboard focus cannot reach a visible control`)
}

async function assertMobileTouchTargets(page, label) {
  const targets = await page.evaluate(() => [...document.querySelectorAll(
    '.side-nav button, .system-nav button, .system-admin-nav button, .platform-admin-nav button')]
    .map(node => node.getBoundingClientRect())
    .filter(box => box.width > 0 && box.height > 0)
    .map(box => ({ width: box.width, height: box.height })))
  if (!targets.length) throw new Error(`${label}: no visible mobile navigation targets`)
  const tooSmall = targets.find(box => box.width < 44 || box.height < 44)
  if (tooSmall) throw new Error(`${label}: mobile navigation target is ${tooSmall.width}x${tooSmall.height}`)
  return targets.length
}

async function capture(page, role, size, destination, forbidTechnical = true) {
  await page.setViewportSize(viewports[size])
  await page.goto(`${baseUrl}${destination}`)
  const result = await assertPage(page, `${role}/${size}`, forbidTechnical)
  result.keyboardFocus = await assertKeyboardFocus(page, `${role}/${size}`)
  if (size === 'mobile') result.mobileTouchTargets = await assertMobileTouchTargets(page, `${role}/${size}`)
  await page.screenshot({ path: path.join(evidenceRoot, `CYCLE-075-${role}-${size}.png`), fullPage: true })
  return result
}

async function main() {
  const browser = await chromium.launch({
    headless: true,
    executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  })
  const results = []
  try {
    const ordinaryContext = await browser.newContext({ viewport: viewports.desktop })
    const ordinary = await ordinaryContext.newPage()
    await login(ordinary, 'c68_seller_227384', password)
    await enterSystem(ordinary)
    results.push(await capture(ordinary, 'ordinary', 'desktop', '/systems/44'))
    results.push(await capture(ordinary, 'ordinary', 'tablet', '/systems/44?workspace=runtime&module=module_98af3d0ea2'))
    results.push(await capture(ordinary, 'ordinary', 'mobile', '/systems/44?workspace=todo'))
    await ordinaryContext.close()

    const systemContext = await browser.newContext({ viewport: viewports.desktop })
    const systemAdmin = await systemContext.newPage()
    await login(systemAdmin, 'c68_owner_227384', password)
    await enterSystem(systemAdmin)
    const systemSections = ['system-info', 'organization', 'roles', 'dictionary', 'templates', 'parameters', 'flow', 'applications', 'ai', 'audit', 'operations']
    for (const section of systemSections) {
      await systemAdmin.goto(`${baseUrl}/systems/44/admin?section=${section}`)
      results.push(await assertPage(systemAdmin, `system-admin/${section}`, !['audit', 'operations'].includes(section)))
    }
    results.push(await capture(systemAdmin, 'system-admin', 'desktop', '/systems/44/admin?section=templates'))
    results.push(await capture(systemAdmin, 'system-admin', 'tablet', '/systems/44/admin?section=roles'))
    results.push(await capture(systemAdmin, 'system-admin', 'mobile', '/systems/44/admin?section=templates'))
    await systemContext.close()

    const platformContext = await browser.newContext({ viewport: viewports.desktop })
    const platformAdmin = await platformContext.newPage()
    await login(platformAdmin, 'admin', '123123aa')
    const platformRoutes = [
      '/platform', '/platform/admin/configurations?category=PLATFORM_INFO', '/platform/admin/identity-providers',
      '/platform/admin/flows', '/platform/admin/applications', '/platform/admin/ai',
      '/platform/admin/dashboards', '/platform/admin/messages',
    ]
    for (const route of platformRoutes) {
      await platformAdmin.goto(`${baseUrl}${route}`)
      results.push(await assertPage(platformAdmin, `platform-admin/${route}`, !route.includes('configurations')))
    }
    results.push(await capture(platformAdmin, 'platform-admin', 'desktop', '/platform/admin/configurations?category=PLATFORM_INFO'))
    results.push(await capture(platformAdmin, 'platform-admin', 'tablet', '/platform/admin/identity-providers'))
    results.push(await capture(platformAdmin, 'platform-admin', 'mobile', '/platform/admin/flows'))
    await platformContext.close()

    process.stdout.write(JSON.stringify({ pagesChecked: results.length, screenshots: 9, results }, null, 2))
  } finally {
    await browser.close()
  }
}

main().catch(error => {
  console.error(error)
  process.exitCode = 1
})
