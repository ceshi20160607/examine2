// 在 S19 同一候选环境中由审批员通过系统待办页面办理客户记录审批。
import { spawn } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'
import { setTimeout as delay } from 'node:timers/promises'

const password = process.env.S19_USER_PASSWORD
if (!password) throw new Error('S19_USER_PASSWORD is required')
const evidencePath = 'projects/unexamine/ai/evidence/s19-approval-readback.json'
const evidence = JSON.parse(readFileSync(evidencePath, 'utf8'))
if (evidence.status !== 'approval_pending') throw new Error('Approval must be pending before browser handling')
const base = process.env.S19_FRONTEND_URL ?? 'http://127.0.0.1:15180'
const debug = 'http://127.0.0.1:19238'
const edge = spawn('C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe', [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--remote-debugging-port=19238', '--remote-allow-origins=*',
  `--user-data-dir=${process.env.TEMP}\\unexamine-s19-approver-browser`, 'about:blank',
], { stdio: 'ignore', windowsHide: true })

try {
  let ready = false
  for (let i = 0; i < 60; i++) {
    try { if ((await fetch(debug + '/json/version')).ok) { ready = true; break } }
    catch { /* 等待浏览器调试入口。 */ }
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
    const item = JSON.parse(event.data)
    if (!item.id || !pending.has(item.id)) return
    const { resolve, reject } = pending.get(item.id)
    pending.delete(item.id)
    item.error ? reject(new Error(item.error.message)) : resolve(item.result)
  })
  const send = (method, params = {}) => new Promise((resolve, reject) => {
    const id = ++nextId
    pending.set(id, { resolve, reject })
    socket.send(JSON.stringify({ id, method, params }))
  })
  const evaluate = async expression => {
    const item = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
    if (item.exceptionDetails) throw new Error(item.exceptionDetails.exception?.description || item.exceptionDetails.text)
    return item.result.value
  }
  const waitFor = async expression => {
    for (let i = 0; i < 75; i++) {
      if (await evaluate(expression)) return
      await delay(350)
    }
    throw new Error(`Browser condition did not arrive: ${expression}`)
  }
  await send('Page.enable')
  await send('Runtime.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 980, deviceScaleFactor: 1, mobile: false })
  await send('Page.navigate', { url: base + '/login' })
  await waitFor(`document.querySelector('input[autocomplete="username"]') !== null`)
  await evaluate(`(() => {
    const fill = (selector, value) => {
      const input = document.querySelector(selector)
      input.value = value
      input.dispatchEvent(new Event('input', { bubbles: true }))
      input.dispatchEvent(new Event('change', { bubbles: true }))
    }
    fill('input[autocomplete="username"]', 's19_approver')
    fill('input[autocomplete="current-password"]', ${JSON.stringify(password)})
    document.querySelector('form button[type="submit"]').click()
  })()`)
  await waitFor(`location.pathname.startsWith('/platform')`)
  await waitFor(`document.body.innerText.includes('发布验收业务甲')`)
  await evaluate(`(() => {
    const button = [...document.querySelectorAll('button')].find(item => item.textContent.includes('进入发布验收业务甲'))
    if (!button) throw new Error('Approver system entry is missing')
    button.click()
  })()`)
  await waitFor(`location.pathname === '/systems/1'`)
  await send('Page.navigate', { url: base + '/systems/1?workspace=todos' })
  await waitFor(`document.querySelector('.todo-card')?.textContent.includes('S19 上海客户')`)
  const pendingView = await evaluate(`({url:location.href, title:document.querySelector('h1')?.textContent,
    card:document.querySelector('.todo-card')?.textContent.trim()})`)
  if (!pendingView.card.includes('s19_member') || !pendingView.card.replace(/\s/g, '').includes('同意')) {
    throw new Error(`Approver todo is incomplete: ${JSON.stringify(pendingView)}`)
  }
  const pendingImage = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  writeFileSync('projects/unexamine/ai/evidence/s19-approval-pending-browser.png', Buffer.from(pendingImage.data, 'base64'))
  await evaluate(`(() => {
    const button = [...document.querySelectorAll('.todo-card button')].find(item => item.textContent.replaceAll(' ', '').trim() === '同意')
    if (!button) throw new Error('Approve button missing')
    button.click()
  })()`)
  await waitFor(`location.search.includes('workspace=flow')`)
  await waitFor(`document.querySelector('.runtime-instance-hero')?.textContent.includes('S19 上海客户')`)
  const flowView = await evaluate(`({url:location.href, hero:document.querySelector('.runtime-instance-hero')?.textContent.trim()})`)
  await evaluate(`(() => {
    const button = [...document.querySelectorAll('.runtime-actions button')].find(item => item.textContent.replaceAll(' ', '').trim() === '同意')
    if (!button) throw new Error('Flow approve action missing')
    button.click()
  })()`)
  await waitFor(`document.querySelector('.ant-modal')?.textContent.includes('处理流程')`)
  await waitFor(`document.querySelector('.ant-modal-footer .ant-btn-primary:not([disabled])') !== null`)
  await evaluate(`(() => {
    const button = document.querySelector('.ant-modal-footer .ant-btn-primary')
    if (!button) throw new Error('Approval confirmation missing')
    button.click()
  })()`)
  await waitFor(`document.querySelector('.runtime-instance-hero')?.textContent.includes('已完成')`)
  const completedView = await evaluate(`({url:location.href,
    hero:document.querySelector('.runtime-instance-hero')?.textContent.trim()})`)
  const completedImage = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  writeFileSync('projects/unexamine/ai/evidence/s19-approval-completed-browser.png', Buffer.from(completedImage.data, 'base64'))
  evidence.status = 'approval_browser_completed'
  evidence.browser = { pendingView, flowView, completedView }
  writeFileSync(evidencePath, JSON.stringify(evidence, null, 2) + '\n')
  console.log(JSON.stringify(evidence.browser))
  socket.close()
} finally {
  edge.kill()
}
