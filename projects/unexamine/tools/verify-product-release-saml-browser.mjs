// 用 Edge 验证发布包登录入口、身份源授权页、回调及目标系统成员进入。
import { spawn } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'
import { setTimeout as delay } from 'node:timers/promises'

const frontend = 'http://127.0.0.1:15185'
const candidate = process.env.S19_SAML_CANDIDATE_VERSION || '0.1.0-s19.4-dev1'
const adminPassword = process.env.S19_SAML_ADMIN_PASSWORD
if (!adminPassword) throw new Error('Isolated administrator credential is required')
const deniedSystemId = JSON.parse(readFileSync('projects/unexamine/ai/evidence/s19-saml-live-denials.json', 'utf8')).deniedSystemId
const debug = 'http://127.0.0.1:19242'
const profile = `${process.env.TEMP}\\unexamine-s19-saml-browser`
const edge = spawn('C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe', [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--remote-debugging-port=19242', '--remote-allow-origins=*', `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore', windowsHide: true })

try {
  let ready = false
  for (let i = 0; i < 60; i++) {
    try { if ((await fetch(debug + '/json/version')).ok) { ready = true; break } } catch { /* 等待调试端口。 */ }
    await delay(250)
  }
  if (!ready) throw new Error('Edge did not start')
  const target = await (await fetch(debug + '/json/new?about:blank', { method: 'PUT' })).json()
  const socket = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((resolve, reject) => {
    socket.addEventListener('open', resolve, { once: true })
    socket.addEventListener('error', reject, { once: true })
  })
  let nextId = 0
  const pending = new Map()
  socket.addEventListener('message', event => {
    const result = JSON.parse(event.data)
    if (!result.id || !pending.has(result.id)) return
    const { resolve, reject } = pending.get(result.id)
    pending.delete(result.id)
    result.error ? reject(new Error(result.error.message)) : resolve(result.result)
  })
  const send = (method, params = {}) => new Promise((resolve, reject) => {
    const id = ++nextId
    pending.set(id, { resolve, reject })
    socket.send(JSON.stringify({ id, method, params }))
  })
  const evaluate = async expression => {
    const result = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
    if (result.exceptionDetails) throw new Error(result.exceptionDetails.text)
    return result.result.value
  }
  const waitFor = async expression => {
    for (let i = 0; i < 90; i++) {
      if (await evaluate(expression)) return
      await delay(350)
    }
    const state = await evaluate('({ url: location.href, text: document.body.innerText.slice(0, 500) })')
    throw new Error(`Browser condition timed out: ${expression}; ${JSON.stringify(state)}`)
  }

  await send('Page.enable')
  await send('Runtime.enable')
  await send('Emulation.setDeviceMetricsOverride',
    { width: 1440, height: 980, deviceScaleFactor: 1, mobile: false })
  await send('Page.navigate', { url: frontend + '/login' })
  await waitFor(`document.querySelector('input[autocomplete="username"]') !== null`)
  await waitFor(`document.querySelector('.sso-provider-list button')?.innerText.includes('S19 SAML 企业身份')`)
  const localLoginVisible = await evaluate(`document.querySelector('input[autocomplete="username"]') !== null`)
  await evaluate(`document.querySelector('.sso-provider-list button').click()`)
  await waitFor(`location.port === '18213' && document.body.innerText.includes('隔离 SAML2 身份源')`)
  const authorizationPage = await evaluate(`({ url: location.origin + location.pathname, title: document.title })`)
  await evaluate(`document.querySelector('form button[type="submit"]').click()`)
  await waitFor(`location.pathname === '/platform' && document.body.innerText.includes('S19 SAML 系统')`)
  const platformPage = await evaluate(`({ url: location.href, title: document.title,
    accountVisible: document.body.innerText.includes('S19 SAML 成员'),
    systemVisible: document.body.innerText.includes('S19 SAML 系统') })`)
  const deniedSystemEntry = await evaluate(`(async () => {
    const tokens = JSON.parse(sessionStorage.getItem('unexamine.platform.tokens'))
    const response = await fetch('/api/systems/${deniedSystemId}/enter', {
      method: 'POST', headers: { authorization: 'Bearer ' + tokens.accessToken },
    })
    const body = await response.json()
    return { status: response.status, code: body.code }
  })()`)
  if (deniedSystemEntry.status !== 403) throw new Error(`Unauthorized system entry was not rejected: ${deniedSystemEntry.status}`)
  await evaluate(`(() => {
    const row = [...document.querySelectorAll('.system-row')].find(item => item.textContent.includes('S19 SAML 系统'))
    const button = [...(row?.querySelectorAll('button') || [])].find(item => item.textContent.includes('进入'))
    if (!button) throw new Error('Mapped system entry missing')
    button.click()
  })()`)
  await waitFor(`location.pathname.startsWith('/systems/1') && document.body.innerText.includes('S19 SAML 系统')`)
  await waitFor(`document.querySelector('.ant-skeleton') === null`)
  const systemPage = await evaluate(`({ url: location.href, title: document.title,
    systemVisible: document.body.innerText.includes('S19 SAML 系统'),
    memberVisible: document.body.innerText.includes('S19 SAML 成员') })`)
  const screenshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
  writeFileSync('projects/unexamine/ai/evidence/s19-saml-browser.png', Buffer.from(screenshot.data, 'base64'))
  await evaluate(`sessionStorage.clear()`)
  await send('Page.navigate', { url: frontend + '/login' })
  await waitFor(`document.querySelector('input[autocomplete="username"]') !== null`)
  await evaluate(`(() => {
    const fill = (selector, value) => {
      const input = document.querySelector(selector)
      input.value = value
      input.dispatchEvent(new Event('input', { bubbles: true }))
      input.dispatchEvent(new Event('change', { bubbles: true }))
    }
    fill('input[autocomplete="username"]', 'admin')
    fill('input[autocomplete="current-password"]', ${JSON.stringify(adminPassword)})
    document.querySelector('form button[type="submit"]').click()
  })()`)
  await waitFor(`location.pathname === '/platform/dashboard'`)
  await send('Page.navigate', { url: frontend + '/platform/admin/identity-providers' })
  await waitFor(`document.body.innerText.includes('S19 SAML 企业身份') && document.body.innerText.includes('预检报告')`)
  const adminPage = await evaluate(`({ url: location.href,
    providerVisible: document.body.innerText.includes('S19 SAML 企业身份'),
    publishedVisible: document.body.innerText.includes('已发布'),
    preflightVisible: document.body.innerText.includes('预检报告'),
    verifiedEmailVisible: document.body.innerText.includes('已验证邮箱映射') })`)
  const adminScreenshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
  writeFileSync('projects/unexamine/ai/evidence/s19-saml-admin-browser.png', Buffer.from(adminScreenshot.data, 'base64'))
  const evidence = {
    step: 'S19.4', candidate, frontend,
    localLoginVisible, samlEntryVisible: true, authorizationPage, platformPage, deniedSystemEntry, systemPage, adminPage,
    screenshot: 'projects/unexamine/ai/evidence/s19-saml-browser.png',
    adminScreenshot: 'projects/unexamine/ai/evidence/s19-saml-admin-browser.png',
  }
  writeFileSync('projects/unexamine/ai/evidence/s19-saml-browser.json', JSON.stringify(evidence, null, 2) + '\n')
  process.stdout.write(JSON.stringify(evidence) + '\n')
  socket.close()
} finally {
  edge.kill()
}
