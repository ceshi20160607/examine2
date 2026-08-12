import { expect, test } from '@playwright/test'

test('registers the first system and completes the shell journey', async ({ page }) => {
  const suffix = `${test.info().project.name}_${Date.now()}`.replace(/[^a-z0-9_]/g, '_')
  const consoleProblems: string[] = []
  const unexpectedHttpResponses: string[] = []
  page.on('console', (message) => {
    const expectedAnonymousProbe =
      message.location().url.endsWith('/api/v1/me/context') &&
      message.text().includes('401 (Unauthorized)')
    const expectedUnpublishedNavigation =
      message.text() === 'Failed to load resource: the server responded with a status of 404 (Not Found)'
    if (['error', 'warning'].includes(message.type()) && !expectedAnonymousProbe && !expectedUnpublishedNavigation) {
      consoleProblems.push(message.text())
    }
  })
  page.on('response', (response) => {
    const expectedAnonymousProbe =
      response.status() === 401 && response.url().endsWith('/api/v1/me/context')
    const expectedUnpublishedNavigation =
      response.status() === 404 && /\/api\/v1\/systems\/\d+\/runtime\/navigation$/.test(new URL(response.url()).pathname)
    if (response.status() >= 400 && !expectedAnonymousProbe && !expectedUnpublishedNavigation) {
      unexpectedHttpResponses.push(`${response.status()} ${response.url()}`)
    }
  })

  await page.goto('/auth/register')
  await page.getByLabel('用户名').fill(`browser_${suffix}`)
  await page.getByLabel('姓名').fill('浏览器测试用户')
  await page.getByLabel('密码').fill('Browser-Test-Password-42!')
  await page.getByLabel('系统名称').fill('浏览器验收系统')
  await page.getByLabel('系统编码').fill(`browser_${suffix}`)

  await Promise.all([
    page.waitForURL(/\/systems\/\d+\/workbench$/),
    page.getByRole('button', { name: '创建并进入系统' }).click(),
  ])
  await expect(page.getByRole('heading', { name: '浏览器验收系统' })).toBeVisible()
  await expect(page.getByText('系统管理员尚未发布模块配置。')).toBeVisible()

  const layout = await page.evaluate(() => {
    const topbar = document.querySelector('.topbar')
    const tools = document.querySelector('.topbar-tools')
    return {
      documentOverflow: document.documentElement.scrollWidth > window.innerWidth,
      topbarOverflow: topbar ? topbar.scrollWidth > topbar.clientWidth : true,
      toolsRight: tools?.getBoundingClientRect().right ?? Number.POSITIVE_INFINITY,
      viewportWidth: window.innerWidth,
    }
  })
  expect(layout.documentOverflow).toBe(false)
  expect(layout.topbarOverflow).toBe(false)
  expect(layout.toolsRight).toBeLessThanOrEqual(layout.viewportWidth)

  await page.locator('button[title="个人菜单"]').click()
  await page.getByRole('menuitem', { name: '返回平台' }).click()
  await page.waitForURL(/\/platform\/workbench$/)
  await expect(page.getByRole('button', { name: /浏览器验收系统/ })).toBeVisible()

  await page.getByRole('button', { name: /浏览器验收系统/ }).click()
  await page.waitForURL(/\/systems\/\d+\/workbench$/)
  await page.locator('button[title="个人菜单"]').click()
  await page.getByRole('menuitem', { name: '退出登录' }).click()
  await page.waitForURL(/\/auth\/login$/)
  await expect(page.getByRole('heading', { name: '登录' })).toBeVisible()
  expect(consoleProblems).toEqual([])
  expect(unexpectedHttpResponses).toEqual([])
})
