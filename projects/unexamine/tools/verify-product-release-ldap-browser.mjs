// Edge 实际填写目录凭据，经发布登录入口进入平台和已确认的系统成员工作区。
import { spawn } from 'node:child_process'
import { writeFileSync } from 'node:fs'
import { setTimeout as delay } from 'node:timers/promises'

const frontend = 'http://127.0.0.1:15173'
const debug = 'http://127.0.0.1:19243'
const step = process.env.S19_LDAP_STEP || 'S19.5'
const candidate = process.env.S19_LDAP_CANDIDATE || '0.1.0-s19.5-dev1'
const evidencePrefix = process.env.S19_LDAP_EVIDENCE_PREFIX || 's19-ldap'
const evidencePath = name => `projects/unexamine/ai/evidence/${evidencePrefix}-${name}`
const profile = `${process.env.TEMP}\\unexamine-s19-ldap-browser`
const edge = spawn('C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe', [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--remote-debugging-port=19243', '--remote-allow-origins=*', `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore', windowsHide: true })

try {
  let ready = false
  for (let i = 0; i < 80; i++) {
    try { if ((await fetch(debug + '/json/version')).ok) { ready = true; break } } catch { /* 等待浏览器调试端口。 */ }
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
    if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails))
    return result.result.value
  }
  const waitFor = async expression => {
    for (let i = 0; i < 100; i++) {
      if (await evaluate(expression)) return
      await delay(300)
    }
    throw new Error(`Browser condition timed out: ${expression}; ${JSON.stringify(await evaluate('({url:location.href,text:document.body.innerText.slice(0,500)})'))}`)
  }
  await send('Page.enable')
  await send('Runtime.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 980, deviceScaleFactor: 1, mobile: false })
  await send('Page.navigate', { url: frontend + '/login' })
  await waitFor(`document.querySelector('.sso-provider-list button')?.innerText.includes('S19 LDAP 企业身份')`)
  const localLoginVisible = await evaluate(`document.querySelector('input[autocomplete="username"]') !== null`)
  await evaluate(`document.querySelector('.sso-provider-list button').click()`)
  await waitFor(`document.body.innerText.includes('目录用户名')`)
  await evaluate(`(() => {
    const set = (input, value) => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, value)
      input.dispatchEvent(new Event('input', { bubbles: true }))
    }
    set([...document.querySelectorAll('input[autocomplete="username"]')].at(-1), 'alice')
    set([...document.querySelectorAll('input[autocomplete="current-password"]')].at(-1), 'fixture-user-password')
    ;[...document.querySelectorAll('button')].find(button => button.textContent.includes('使用目录账号登录')).click()
  })()`)
  await waitFor(`location.pathname.startsWith('/platform') && document.body.innerText.includes('S19 LDAP 系统')`)
  const platformPage = await evaluate(`({url:location.href,accountVisible:document.body.innerText.includes('S19 LDAP 成员'),systemVisible:document.body.innerText.includes('S19 LDAP 系统')})`)
  await send('Page.navigate', { url: frontend + '/platform' })
  await waitFor(`document.querySelector('.system-row')?.innerText.includes('S19 LDAP 系统')`)
  await evaluate(`(() => {
    const row = [...document.querySelectorAll('.system-row')].find(item => item.textContent.includes('S19 LDAP 系统'))
    const button = [...(row?.querySelectorAll('button') || [])].find(item => item.textContent.includes('进入'))
    if (!button) throw new Error('Mapped system entry missing')
    button.click()
  })()`)
  await waitFor(`location.pathname.startsWith('/systems/1') && document.body.innerText.includes('S19 LDAP 系统')`)
  const systemPage = await evaluate(`({url:location.href,systemVisible:document.body.innerText.includes('S19 LDAP 系统'),memberVisible:document.body.innerText.includes('S19 LDAP 成员')})`)
  const screenshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
  writeFileSync(evidencePath('browser.png'), Buffer.from(screenshot.data, 'base64'))
  let systemAdminPage = null
  if (step === 'S19.6') {
    await send('Page.navigate', { url: frontend + '/systems/1/admin?section=system-info' })
    await waitFor(`document.body.innerText.includes('统一认证')`)
    await evaluate(`[...document.querySelectorAll('.admin-subnav button')].find(button => button.textContent.includes('统一认证')).click()`)
    await waitFor(`document.body.innerText.includes('登录映射日志') && document.body.innerText.includes('MATCHED')`)
    systemAdminPage = await evaluate(`({url:location.href, matched:document.body.innerText.includes('MATCHED'), denied:document.body.innerText.includes('DENIED'), traceVisible:document.body.innerText.includes('登录 ')})`)
    await evaluate(`document.querySelector('.identity-logs').scrollIntoView({ block: 'start' })`)
    await delay(300)
    const auditScreenshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
    writeFileSync(evidencePath('mapping-browser.png'), Buffer.from(auditScreenshot.data, 'base64'))
  }
  const passwordAbsentFromBrowserStorage = await evaluate(`!Object.values(sessionStorage).some(value => value.includes('fixture-user-password')) && !Object.values(localStorage).some(value => value.includes('fixture-user-password'))`)
  await evaluate(`sessionStorage.clear()`)
  await send('Page.navigate', { url: frontend + '/login' })
  await waitFor(`document.querySelector('input[autocomplete="username"]') !== null`)
  await evaluate(`(() => {
    const set = (input, value) => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, value)
      input.dispatchEvent(new Event('input', { bubbles: true }))
    }
    set(document.querySelector('input[autocomplete="username"]'), 'admin')
    set(document.querySelector('input[autocomplete="current-password"]'), '123123aa')
    document.querySelector('button[type="submit"]').click()
  })()`)
  await waitFor(`location.pathname.startsWith('/platform')`)
  await send('Page.navigate', { url: frontend + '/platform/admin/identity-providers' })
  await waitFor(`document.body.innerText.includes('S19 LDAP 企业身份')`)
  const adminPage = await evaluate(`({url:location.href,providerVisible:document.body.innerText.includes('S19 LDAP 企业身份'),publishedVisible:document.body.innerText.includes('已发布')})`)
  const adminScreenshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
  writeFileSync(evidencePath('admin-browser.png'), Buffer.from(adminScreenshot.data, 'base64'))
  const evidence = { step, candidate, frontend,
    localLoginVisible, directoryEntryVisible: true, passwordAbsentFromBrowserStorage,
    platformPage, systemPage, systemAdminPage, adminPage, screenshot: evidencePath('browser.png'),
    adminScreenshot: evidencePath('admin-browser.png'), mappingScreenshot: systemAdminPage ? evidencePath('mapping-browser.png') : null }
  if (!localLoginVisible || !platformPage.accountVisible || !systemPage.memberVisible || !passwordAbsentFromBrowserStorage
      || !adminPage.providerVisible || !adminPage.publishedVisible || (step === 'S19.6' && !systemAdminPage?.matched)) {
    throw new Error(`Browser acceptance incomplete: ${JSON.stringify(evidence)}`)
  }
  writeFileSync(evidencePath('browser.json'), JSON.stringify(evidence, null, 2) + '\n')
  console.log(JSON.stringify(evidence, null, 2))
  socket.close()
} finally {
  edge.kill()
}
