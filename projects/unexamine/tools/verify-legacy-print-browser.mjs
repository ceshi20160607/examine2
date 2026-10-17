// 在真实 Edge 页面核对旧模块恢复按钮与迁移后的模块动作。
import { spawn } from 'node:child_process'
import { writeFileSync } from 'node:fs'
import { setTimeout as delay } from 'node:timers/promises'

const backend = 'http://127.0.0.1:18081'
const frontend = 'http://127.0.0.1:15179'
const debug = 'http://127.0.0.1:19229'
const edgePath = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const profile = `${process.env.TEMP}\\unexamine-s18-legacy-print-browser`
const evidence = 'projects/unexamine/ai/evidence'

async function api(path, token, body) {
  const response = await fetch(backend + path, {
    method: body ? 'POST' : 'GET',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  })
  const result = await response.json()
  if (!response.ok || result.code !== 'OK') throw new Error(`${path}: ${response.status}/${result.code}`)
  return result.data
}

async function session(username, systemId) {
  const platformTokens = await api('/api/auth/login', null,
    { username, password: 'correct-password' })
  const platformContext = await api('/api/auth/me', platformTokens.accessToken)
  const entry = await api(`/api/systems/${systemId}/enter`, platformTokens.accessToken, {})
  const systemTokens = entry.tokens
  const systemContext = await api('/api/auth/me', systemTokens.accessToken)
  return { platformTokens, platformContext, systemTokens, systemContext }
}

const edge = spawn(edgePath, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--remote-debugging-port=19229', '--remote-allow-origins=*', `--user-data-dir=${profile}`,
  'about:blank',
], { stdio: 'ignore', windowsHide: true })

try {
  let ready = false
  for (let attempt = 0; attempt < 60; attempt++) {
    try {
      const response = await fetch(debug + '/json/version')
      if (response.ok) { ready = true; break }
    } catch { /* Edge is starting. */ }
    await delay(250)
  }
  if (!ready) throw new Error('Edge DevTools did not start')
  const target = await (await fetch(debug + '/json/new?about:blank', { method: 'PUT' })).json()
  const socket = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((resolve, reject) => {
    socket.addEventListener('open', resolve, { once: true })
    socket.addEventListener('error', reject, { once: true })
  })
  let nextId = 0
  const pending = new Map()
  socket.addEventListener('message', event => {
    const message = JSON.parse(event.data)
    if (!message.id || !pending.has(message.id)) return
    const { resolve, reject } = pending.get(message.id)
    pending.delete(message.id)
    message.error ? reject(new Error(message.error.message)) : resolve(message.result)
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
  await send('Page.enable')
  await send('Runtime.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 980, deviceScaleFactor: 1, mobile: false })

  async function capture(username, systemId, moduleId, shouldRestore, suffix) {
    const auth = await session(username, systemId)
    await send('Page.navigate', { url: frontend + '/' })
    await delay(900)
    const entries = {
      'unexamine.platform.tokens': auth.platformTokens,
      'unexamine.platform.context': auth.platformContext,
      'unexamine.system.tokens': auth.systemTokens,
      'unexamine.system.context': auth.systemContext,
    }
    await evaluate(`Object.entries(${JSON.stringify(entries)}).forEach(([key,value]) => sessionStorage.setItem(key, JSON.stringify(value)))`)
    await send('Page.navigate', { url: `${frontend}/systems/${systemId}/admin?section=templates&module=g7_layout&moduleTask=ACTIONS` })
    let state
    for (let attempt = 0; attempt < 30; attempt++) {
      await delay(350)
      state = await evaluate(`({ url: location.href, text: document.body.innerText,
        restore: [...document.querySelectorAll('button')].some(b => b.innerText.includes('恢复旧模板打印')),
        add: [...document.querySelectorAll('button')].some(b => b.innerText.includes('添加打印动作')) })`)
      if (state.text.includes('模块功能') && state.text.includes('打印动作')) break
    }
    if (state.restore !== shouldRestore) {
      throw new Error(`module #${moduleId}: restore=${state.restore}, expected=${shouldRestore}; text=${state.text.slice(0, 450)}`)
    }
    const png = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
    const path = `${evidence}/s18-module-legacy-print-browser-${suffix}.png`
    writeFileSync(path, Buffer.from(png.data, 'base64'))
    return { moduleId, systemId, restoreButton: state.restore, addButton: state.add, path }
  }

  const before = await capture('g7_layout_owner_ea39f0f4', 141, 67, true, 'before')
  await send('Page.navigate', { url: `${frontend}/systems/141?workspace=runtime&module=g7_layout&recordId=120` })
  let runtimePrintButton = false
  for (let attempt = 0; attempt < 30; attempt++) {
    await delay(350)
    runtimePrintButton = await evaluate(`[...document.querySelectorAll('button')].some(b => b.innerText.trim() === '打印')`)
    if (runtimePrintButton) break
  }
  if (!runtimePrintButton) throw new Error('Legacy runtime detail lacks its print action')
  await evaluate(`[...document.querySelectorAll('button')].find(b => b.innerText.trim() === '打印').click()`)
  await delay(500)
  const runtimeDialog = await evaluate(`document.body.innerText.includes('打印模板')`)
  if (!runtimeDialog) throw new Error('Legacy runtime print dialog did not open')
  const runtimePng = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  const runtimePath = `${evidence}/s18-module-print-upgrade-runtime-browser.png`
  writeFileSync(runtimePath, Buffer.from(runtimePng.data, 'base64'))
  const runtime = { moduleId: 67, systemId: 141, runtimePrintButton, runtimeDialog, path: runtimePath }
  const after = await capture('g7_layout_owner_6b4dee80', 143, 68, false, 'after')
  const result = { ok: true, before, runtime, after }
  writeFileSync(`${evidence}/s18-module-legacy-print-browser-readback.json`, JSON.stringify(result, null, 2) + '\n')
  console.log(JSON.stringify(result))
  socket.close()
} finally {
  edge.kill()
}
