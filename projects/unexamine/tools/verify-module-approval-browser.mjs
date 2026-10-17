// 在真实 Edge 页面核对模块审批按钮及发起后的 Flow 实例。
import { spawn } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'
import { setTimeout as delay } from 'node:timers/promises'

const state = JSON.parse(readFileSync('projects/unexamine/ai/evidence/s18-module-approval-action-live.json', 'utf8'))
const backend = 'http://127.0.0.1:18081'
const frontend = 'http://127.0.0.1:15179'
const debug = 'http://127.0.0.1:19230'
const evidence = 'projects/unexamine/ai/evidence'
const edgePath = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const profile = `${process.env.TEMP}\\unexamine-s18-approval-browser`

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
  '--remote-debugging-port=19230', '--remote-allow-origins=*', `--user-data-dir=${profile}`,
  'about:blank',
], { stdio: 'ignore', windowsHide: true })

try {
  let ready = false
  for (let attempt = 0; attempt < 60; attempt++) {
    try {
      if ((await fetch(debug + '/json/version')).ok) { ready = true; break }
    } catch { /* Edge 尚在启动。 */ }
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
    url: `${frontend}/systems/${state.systemId}/admin?section=templates&module=${state.moduleCode}&moduleTask=FLOW&triggerEvent=MANUAL`,
  })
  for (let attempt = 0; attempt < 25; attempt++) {
    await delay(300)
    const opened = await evaluate(`(() => { const button = [...document.querySelectorAll('button')]
      .find(item => item.innerText.includes('配置模块 Flow')); if (!button) return false; button.click(); return true })()`)
    if (opened) break
  }
  const bindingUrl = new URL(await evaluate('location.href'))
  bindingUrl.searchParams.set('triggerEvent', 'MANUAL')
  await send('Page.navigate', { url: bindingUrl.toString() })
  let bindingPage
  for (let attempt = 0; attempt < 35; attempt++) {
    await delay(350)
    bindingPage = await evaluate(`({text:document.body.innerText})`)
    if (bindingPage.text.includes('发起审批') && bindingPage.text.includes('仅启动流程')) break
  }
  if (!bindingPage?.text.includes('发起审批') || !bindingPage.text.includes('仅启动流程'))
    throw new Error('Flow binding page does not show the published approval action: '
      + bindingPage?.text.slice(0, 1200))
  await delay(500)
  const bindingPng = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  const bindingPath = `${evidence}/s18-module-approval-binding-browser.png`
  writeFileSync(bindingPath, Buffer.from(bindingPng.data, 'base64'))
  await send('Page.navigate', {
    url: `${frontend}/systems/${state.systemId}?workspace=runtime&module=${state.moduleCode}&recordId=${state.recordId}`,
  })
  let runtime
  for (let attempt = 0; attempt < 35; attempt++) {
    await delay(350)
    runtime = await evaluate(`({url:location.href, text:document.body.innerText,
      approvalButtons:[...document.querySelectorAll('[data-action-code="APPROVAL"]')].map(button => ({
        inDrawer:!!button.closest('.ant-drawer'), width:button.getBoundingClientRect().width,
        height:button.getBoundingClientRect().height, text:button.innerText}))})`)
    if (runtime.approvalButtons.some(button => button.inDrawer && button.width > 0 && button.height > 0)) break
  }
  if (!runtime?.approvalButtons.some(button => button.inDrawer && button.width > 0 && button.height > 0))
    throw new Error('Runtime drawer does not show published approval action: ' + JSON.stringify(runtime?.approvalButtons))
  await delay(700)
  const runtimePng = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  const runtimePath = `${evidence}/s18-module-approval-runtime-browser.png`
  writeFileSync(runtimePath, Buffer.from(runtimePng.data, 'base64'))
  await evaluate(`document.querySelector('.ant-drawer [data-action-code="APPROVAL"]').click()`)
  let flow
  for (let attempt = 0; attempt < 45; attempt++) {
    await delay(350)
    flow = await evaluate(`({url:location.href, text:document.body.innerText})`)
    if (flow.url.includes('workspace=flow') && flow.url.includes('instanceId=')
      && flow.text.includes('隔离库审批申请')) break
  }
  if (!flow?.url.includes('instanceId=') || !flow.text.includes('隔离库审批申请'))
    throw new Error('Approval button did not open a Flow instance: ' + JSON.stringify(flow?.url))
  const browserInstanceId = Number(new URL(flow.url).searchParams.get('instanceId'))
  const browserInstance = await api(`/api/flow-runtime/instances/${browserInstanceId}`, systemTokens.accessToken)
  if (browserInstance.businessId !== String(state.recordId))
    throw new Error('Browser-started Flow does not belong to the selected record')
  await delay(700)
  const flowPng = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  const flowPath = `${evidence}/s18-module-approval-flow-browser.png`
  writeFileSync(flowPath, Buffer.from(flowPng.data, 'base64'))
  const result = { ok: true, systemId: state.systemId, moduleId: state.moduleId,
    recordId: state.recordId, instanceId: browserInstanceId, runtimeApprovalButton: true,
    flowVisible: true, bindingVisible: true, bindingPath, runtimePath, flowPath }
  writeFileSync(`${evidence}/s18-module-approval-browser-readback.json`, JSON.stringify(result, null, 2) + '\n')
  console.log(JSON.stringify(result))
  socket.close()
} finally {
  edge.kill()
}
