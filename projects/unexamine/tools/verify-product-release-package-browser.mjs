// 在独立安装的版本化包上验证管理员登录、建系统和重启读回。
import { spawn } from 'node:child_process'
import { writeFileSync } from 'node:fs'
import { setTimeout as delay } from 'node:timers/promises'

const password = process.env.S19_INITIAL_ADMIN_PASSWORD
if (!password) throw new Error('S19_INITIAL_ADMIN_PASSWORD is required')
const frontend = process.env.S19_PACKAGE_FRONTEND_URL || 'http://127.0.0.1:15182'
const debug = 'http://127.0.0.1:19240'
const profile = `${process.env.TEMP}\\unexamine-s19-package-browser`
const edge = spawn('C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe', [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--remote-debugging-port=19240', '--remote-allow-origins=*', `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore', windowsHide: true })

try {
  let ready = false
  for (let attempt = 0; attempt < 60; attempt++) {
    try { if ((await fetch(debug + '/json/version')).ok) { ready = true; break } }
    catch { /* 等待 Edge 调试入口。 */ }
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
    for (let attempt = 0; attempt < 60; attempt++) {
      const value = await evaluate(expression)
      if (value) return value
      await delay(350)
    }
    throw new Error(`Page condition did not arrive: ${expression.slice(0, 100)}`)
  }
  await send('Page.enable')
  await send('Runtime.enable')
  await send('Emulation.setDeviceMetricsOverride',
    { width: 1440, height: 980, deviceScaleFactor: 1, mobile: false })
  await send('Page.navigate', { url: frontend + '/login' })
  await waitFor(`document.querySelector('input[autocomplete="username"]') !== null`)
  const login = await evaluate(`(() => {
    const fill = (selector, value) => {
      const input = document.querySelector(selector)
      if (!input) throw new Error('Missing input ' + selector)
      input.value = value
      input.dispatchEvent(new Event('input', { bubbles: true }))
      input.dispatchEvent(new Event('change', { bubbles: true }))
    }
    fill('input[autocomplete="username"]', 'admin')
    fill('input[autocomplete="current-password"]', ${JSON.stringify(password)})
    const button = document.querySelector('form button[type="submit"]')
    if (!button) throw new Error('Missing login button')
    button.click()
    return true
  })()`)
  if (!login) throw new Error('Login form did not submit')
  await waitFor(`location.pathname.startsWith('/platform')`)

  const name = '发布包恢复演练'
  await send('Page.navigate', { url: frontend + '/platform' })
  await waitFor(`document.body.innerText.includes('我的系统')`)
  const exists = await evaluate(`document.body.innerText.includes(${JSON.stringify(name)})`)
  if (exists) {
    await evaluate(`(() => {
      const row = [...document.querySelectorAll('.system-row')].find(item => item.textContent.includes(${JSON.stringify(name)}))
      const button = [...(row?.querySelectorAll('button') || [])].find(item => item.textContent.includes('进入'))
      if (!button) throw new Error('Packaged frontend system entry missing')
      button.click()
    })()`)
  } else {
  await evaluate(`(() => {
    const button = [...document.querySelectorAll('button')].find(item => item.textContent.includes('新建系统'))
    if (!button) throw new Error('Packaged frontend create-system entry is missing')
    button.click()
  })()`)
  await waitFor(`document.querySelector('.ant-modal input') !== null`)
  await evaluate(`(() => {
    const input = document.querySelector('.ant-modal input')
    input.value = ${JSON.stringify(name)}
    input.dispatchEvent(new Event('input', { bubbles: true }))
    input.dispatchEvent(new Event('change', { bubbles: true }))
    const button = [...document.querySelectorAll('.ant-modal button')].find(item => item.textContent.includes('创建系统'))
    if (!button) throw new Error('Packaged frontend create-system confirmation is missing')
    button.click()
  })()`)
  }
  const path = await waitFor(`location.pathname.startsWith('/systems/') ? location.pathname : null`)
  if (!path.endsWith('/admin')) await send('Page.navigate', { url: `${frontend}/systems/${path.split('/')[2]}/admin` })
  await waitFor(`document.body.innerText.includes(${JSON.stringify(name)})`)
  const system = { id: Number(path.split('/')[2]), name }
  const view = await evaluate(`({url:location.href,title:document.querySelector('h1')?.textContent,text:document.body.innerText.slice(0,900)})`)
  const screenshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  writeFileSync('projects/unexamine/ai/evidence/s19-package-browser.png', Buffer.from(screenshot.data, 'base64'))
  writeFileSync('projects/unexamine/ai/evidence/s19-package-browser.json', JSON.stringify({step:'S19.2',status:'packaged_frontend_admin_browser_verified',entryUrl:frontend,system,view},null,2)+'\n')
  console.log(JSON.stringify({system,view}))
  socket.close()
} finally {
  edge.kill()
}
