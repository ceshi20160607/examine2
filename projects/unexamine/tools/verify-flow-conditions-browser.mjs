// 在真实 Edge 页面从已发布模块动作发起 V2，核对路由、待办节点和旧实例。
import { spawn } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'
import { setTimeout as delay } from 'node:timers/promises'

const state = JSON.parse(readFileSync('projects/unexamine/ai/evidence/s18-flow-conditions-live.json', 'utf8'))
const backend = 'http://127.0.0.1:18081'
const frontend = 'http://127.0.0.1:15179'
const debug = 'http://127.0.0.1:19237'
const evidence = 'projects/unexamine/ai/evidence'
const edgePath = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const profile = `${process.env.TEMP}\\unexamine-s18-flow-conditions-browser`

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
const entry = await api('/api/systems/143/enter', platformTokens.accessToken, {})
const systemTokens = entry.tokens
const systemContext = await api('/api/auth/me', systemTokens.accessToken)
const edge = spawn(edgePath, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--remote-debugging-port=19237', '--remote-allow-origins=*', `--user-data-dir=${profile}`,
  'about:blank',
], { stdio: 'ignore', windowsHide: true })

try {
  let ready = false
  for (let attempt = 0; attempt < 60; attempt++) {
    try { if ((await fetch(debug + '/json/version')).ok) { ready = true; break } }
    catch { /* Edge 正在启动。 */ }
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
  const screenshot = async name => {
    const shot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
    const path = `${evidence}/${name}`
    writeFileSync(path, Buffer.from(shot.data, 'base64'))
    return path
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
    url: `${frontend}/systems/143?workspace=runtime&module=dashboard_accept_1a4547cf&recordId=${state.v2RecordId}`,
  })
  let runtime
  for (let attempt = 0; attempt < 40; attempt++) {
    await delay(350)
    runtime = await evaluate(`({url:location.href, text:document.body.innerText,
      buttons:[...document.querySelectorAll('.ant-drawer [data-action-code="APPROVAL"]')].map(button => ({
        width:button.getBoundingClientRect().width,height:button.getBoundingClientRect().height}))})`)
    if (runtime.buttons.some(button => button.width > 0 && button.height > 0)) break
  }
  if (!runtime?.buttons.some(button => button.width > 0 && button.height > 0))
    throw new Error('Published approval action is not visible: ' + JSON.stringify(runtime))
  const runtimePath = await screenshot('s18-flow-conditions-runtime-browser.png')
  await evaluate(`document.querySelector('.ant-drawer [data-action-code="APPROVAL"]').click()`)
  let flow
  for (let attempt = 0; attempt < 50; attempt++) {
    await delay(350)
    flow = await evaluate('({url:location.href,text:document.body.innerText})')
    if (flow.url.includes('workspace=flow') && flow.url.includes('instanceId=')) break
  }
  if (!flow?.url.includes('instanceId=')) throw new Error('Approval button did not open Flow')
  const browserInstanceId = Number(new URL(flow.url).searchParams.get('instanceId'))
  const browserInstance = await api(`/api/flow-runtime/instances/${browserInstanceId}`, systemTokens.accessToken)
  if (browserInstance.flowVersionId !== state.v2VersionId ||
      browserInstance.businessId !== String(state.v2RecordId) ||
      !browserInstance.tasks.some(task => task.nodeKey === 'high_review_v2' && task.status === 'PENDING'))
    throw new Error('Browser-started instance did not use the V2 true branch')
  await delay(500)
  const flowPath = await screenshot('s18-flow-conditions-instance-browser.png')
  const oldInstance = await api(`/api/flow-runtime/instances/${state.highV1InstanceId}`, systemTokens.accessToken)
  if (oldInstance.flowVersionId !== state.v1VersionId || oldInstance.status !== 'COMPLETED')
    throw new Error('Old V1 instance changed after browser initiation')
  const result = { ok: true, recordId: state.v2RecordId, browserInstanceId,
    v2VersionId: browserInstance.flowVersionId, pendingNode: 'high_review_v2',
    oldV1InstanceId: state.highV1InstanceId, runtimePath, flowPath, flowUrl: flow.url }
  writeFileSync(`${evidence}/s18-flow-conditions-browser-readback.json`, JSON.stringify(result, null, 2) + '\n')
  console.log(JSON.stringify(result))
  socket.close()
} finally {
  edge.kill()
}
