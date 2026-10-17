// 在真实 Edge 页面核对已发布模块动作进入当前租户的模块仪表盘。
import { spawn } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'
import { setTimeout as delay } from 'node:timers/promises'

const statePath = 'projects/unexamine/ai/evidence/s18-module-dashboard-action-live.json'
const state = JSON.parse(readFileSync(statePath, 'utf8'))
const backend = 'http://127.0.0.1:18081'
const frontend = 'http://127.0.0.1:15179'
const debug = 'http://127.0.0.1:19231'
const evidence = 'projects/unexamine/ai/evidence'
const edgePath = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const profile = `${process.env.TEMP}\\unexamine-s18-dashboard-browser`

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

const platformTokens = await api('/api/auth/login', null,
  { username: 'g7_layout_owner_6b4dee80', password: 'correct-password' })
const platformContext = await api('/api/auth/me', platformTokens.accessToken)
const entry = await api(`/api/systems/${state.systemId}/enter`, platformTokens.accessToken, {})
const systemTokens = entry.tokens
const systemContext = await api('/api/auth/me', systemTokens.accessToken)
const edge = spawn(edgePath, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--remote-debugging-port=19231', '--remote-allow-origins=*', `--user-data-dir=${profile}`,
  'about:blank',
], { stdio: 'ignore', windowsHide: true })

try {
  let ready = false
  for (let attempt = 0; attempt < 60; attempt++) {
    try { if ((await fetch(debug + '/json/version')).ok) { ready = true; break } }
    catch { /* Edge 尚在启动。 */ }
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
    const response = JSON.parse(event.data)
    if (!response.id || !pending.has(response.id)) return
    const { resolve, reject } = pending.get(response.id)
    pending.delete(response.id)
    response.error ? reject(new Error(response.error.message)) : resolve(response.result)
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
  await send('Emulation.setDeviceMetricsOverride',
    { width: 1440, height: 980, deviceScaleFactor: 1, mobile: false })
  await send('Page.navigate', { url: frontend + '/' })
  await delay(800)
  const entries = {
    'unexamine.platform.tokens': platformTokens,
    'unexamine.platform.context': platformContext,
    'unexamine.system.tokens': systemTokens,
    'unexamine.system.context': systemContext,
  }
  await evaluate(`Object.entries(${JSON.stringify(entries)}).forEach(([key,value]) => sessionStorage.setItem(key, JSON.stringify(value)))`)
  await send('Page.navigate', {
    url: `${frontend}/systems/${state.systemId}?workspace=runtime&module=${state.existingModuleCode}`,
  })
  let runtime
  for (let attempt = 0; attempt < 50; attempt++) {
    await delay(350)
    runtime = await evaluate(`({url:location.href, text:document.body.innerText,
      buttons:[...document.querySelectorAll('[data-action-code="DASHBOARD"]')].map(button => ({
        width:button.getBoundingClientRect().width, height:button.getBoundingClientRect().height,
        text:button.innerText}))})`)
    if (runtime.buttons.some(button => button.width > 0 && button.height > 0)) break
  }
  if (!runtime?.buttons.some(button => button.width > 0 && button.height > 0))
    throw new Error('Runtime does not show published dashboard action: ' + JSON.stringify(runtime?.buttons))
  const runtimePng = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  const runtimePath = `${evidence}/s18-module-dashboard-runtime-browser.png`
  writeFileSync(runtimePath, Buffer.from(runtimePng.data, 'base64'))
  await evaluate(`document.querySelector('[data-action-code="DASHBOARD"]').click()`)
  let dashboard
  for (let attempt = 0; attempt < 50; attempt++) {
    await delay(350)
    dashboard = await evaluate(`({url:location.href, text:document.body.innerText})`)
    if (dashboard.url.includes('workspace=module-home') && dashboard.text.includes('审批业务首页')) break
  }
  if (!dashboard?.url.includes('workspace=module-home') || !dashboard.text.includes('审批业务首页'))
    throw new Error('Dashboard action did not open the current module page: ' + JSON.stringify({
      url: dashboard?.url, text: dashboard?.text.slice(0, 1600), runtimeButton: runtime.buttons[0],
    }))
  const dashboardPng = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  const dashboardPath = `${evidence}/s18-module-dashboard-page-browser.png`
  writeFileSync(dashboardPath, Buffer.from(dashboardPng.data, 'base64'))
  const url = new URL(dashboard.url)
  if (url.searchParams.get('module') !== state.existingModuleCode)
    throw new Error('Dashboard route selected a different module')
  const current = await api(`/api/analytics/runtime?moduleCode=${state.existingModuleCode}`,
    systemTokens.accessToken)
  if (current.dashboardId !== state.existingDashboardId)
    throw new Error('Dashboard page does not match the current tenant publication')
  const browser = { moduleCode: state.existingModuleCode, dashboardId: current.dashboardId,
    dashboardVersionId: current.versionId, runtimeButton: runtime.buttons[0],
    route: url.pathname + url.search, runtimeScreenshot: runtimePath, dashboardScreenshot: dashboardPath }
  const browserPath = `${evidence}/s18-module-dashboard-browser-readback.json`
  writeFileSync(browserPath, JSON.stringify(browser, null, 2) + '\n')
  console.log(JSON.stringify(browser))
  socket.close()
} finally {
  edge.kill()
}
