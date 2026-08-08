import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

import { expect, test, type Page } from '@playwright/test'

test.use({ trace: 'off' })
test.setTimeout(90_000)

const restartFixture = JSON.parse(readFileSync(resolve(
  process.cwd(), '../.cursor/session/evidence/p4-c4/task-02-restart-fixture.json',
), 'utf8')) as {
  systemId: string
  username: string
  password: string
  moduleCode: string
  records: { order: string }
}
const memberFixture = JSON.parse(readFileSync(resolve(
  process.cwd(), '../.cursor/session/evidence/p4-c3/task-02-members.json',
), 'utf8')) as {
  positive: { username: string; password: string }
}

async function login(page: Page, account: { username: string; password: string }) {
  await page.goto('/auth/login')
  await page.getByLabel('账号').fill(account.username)
  await page.getByLabel('密码').fill(account.password)
  await Promise.all([
    page.waitForURL(/\/platform\/workbench$/),
    page.getByRole('button', { name: '登录', exact: true }).click(),
  ])
}

async function apiData<T>(page: Page, path: string): Promise<T> {
  return page.evaluate(async (requestPath) => {
    const response = await fetch(requestPath, { credentials: 'same-origin' })
    const payload = await response.json()
    if (!response.ok || payload.code !== 'OK') throw new Error(`${response.status}:${payload.code}`)
    return payload.data
  }, path)
}

test('administrator sees exact structured controls for all five derived field types', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', 'desktop configuration journey')
  await login(page, restartFixture)
  await page.goto(`/systems/${restartFixture.systemId}/admin/configuration`)
  await expect(page.getByRole('heading', { name: '模块配置工作台' })).toBeVisible()

  const modules = await apiData<Array<{ id: string; code: string; name: string }>>(
    page, `/api/v1/systems/${restartFixture.systemId}/admin/config/modules`,
  )
  const module = modules.find((item) => item.code === restartFixture.moduleCode)
  expect(module).toBeTruthy()
  await page.getByRole('button', { name: new RegExp(module!.name) }).click()

  const cases = [
    { code: 'derived_taxed', heading: '结构化表达式', controls: ['派生表达式运算符'] },
    { code: 'derived_total', heading: '结构化表达式', controls: ['派生表达式运算符'] },
    { code: 'derived_customer_count', heading: '关系汇总', controls: ['汇总来源关系', '汇总归并方式'] },
    { code: 'derived_customer_name', heading: '关系查找', controls: ['查找来源关系', '查找目标字段'] },
    { code: 'derived_line_total', heading: '子表聚合', controls: ['聚合子表字段', '已声明子表聚合'] },
  ]
  for (const expected of cases) {
    const row = page.locator('.resource-row').filter({ hasText: expected.code })
    await expect(row).toBeVisible()
    await row.getByTitle('编辑字段').click()
    const dialog = page.locator('.ant-modal:visible')
    await expect(dialog.getByText(expected.heading, { exact: true })).toBeVisible()
    await expect(dialog.getByText(/推导结果：(DECIMAL|INTEGER|STRING)/)).toBeVisible()
    await expect(dialog.getByText(/^依赖：/)).toBeVisible()
    for (const label of expected.controls) await expect(dialog.getByLabel(label)).toBeVisible()
    await expect(dialog.locator('textarea')).toHaveCount(0)
    await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
  }

  const overflow = await page.evaluate(() => ({
    documentWidth: document.documentElement.scrollWidth,
    viewportWidth: window.innerWidth,
  }))
  expect(overflow.documentWidth).toBeLessThanOrEqual(overflow.viewportWidth + 1)
  await page.screenshot({ path: '../.cursor/session/evidence/p4-c4/task-03-config-desktop.png' })
})

test('ordinary member sees ordered read-only READY derived results on desktop and mobile', async ({ page }, testInfo) => {
  await login(page, memberFixture.positive)
  await page.goto(`/systems/${restartFixture.systemId}/workbench?module=${restartFixture.moduleCode}&record=${restartFixture.records.order}`)
  const drawer = page.locator('.ant-drawer-content').filter({ has: page.locator('.detail-list') })
  await expect(drawer).toBeVisible()

  const schema = await apiData<{ fields: Array<{ type: string; writable: boolean }> }>(
    page, `/api/v1/systems/${restartFixture.systemId}/runtime/modules/${restartFixture.moduleCode}/record-schema`,
  )
  const derived = schema.fields.filter((field) => ['FORMULA', 'SUMMARY', 'CALCULATED', 'LOOKUP', 'AGGREGATE'].includes(field.type))
  expect(derived).toHaveLength(5)
  expect(derived.every((field) => !field.writable)).toBe(true)
  await expect(drawer.getByText('READY · 已计算', { exact: true })).toHaveCount(5)
  await expect(drawer.locator('.lookup-values .lookup-chip')).toHaveCount(1)
  await expect(drawer.getByRole('button', { name: '重试', exact: true })).toHaveCount(0)

  const metrics = await page.evaluate(() => ({
    documentWidth: document.documentElement.scrollWidth,
    viewportWidth: window.innerWidth,
    lookupWrapped: Array.from(document.querySelectorAll('.lookup-values')).every((element) =>
      element.scrollWidth <= element.clientWidth + 1),
  }))
  expect(metrics.documentWidth).toBeLessThanOrEqual(metrics.viewportWidth + 1)
  expect(metrics.lookupWrapped).toBe(true)
  await page.screenshot({
    path: `../.cursor/session/evidence/p4-c4/task-03-runtime-${testInfo.project.name}.png`,
  })
})
