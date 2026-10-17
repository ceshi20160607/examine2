// 在真实 Edge 页面核对已发布模块动作进入当前租户的模块仪表盘。
import { spawn } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'
import { setTimeout as delay } from 'node:timers/promises'

const statePath = 'projects/unexamine/ai/evidence/s18-module-conversion-live.json'
const state = JSON.parse(readFileSync(statePath, 'utf8'))
const systemId = 143
const sourceCode = 'dashboard_accept_1a4547cf'
const targetCode = 'approval_accept_5b69b5d6'
const backend = 'http://127.0.0.1:18081'
const frontend = 'http://127.0.0.1:15179'
const debug = 'http://127.0.0.1:19236'
const evidence = 'projects/unexamine/ai/evidence'
const edgePath = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const profile = `${process.env.TEMP}\\unexamine-s18-conversion-browser`

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
const entry = await api(`/api/systems/${systemId}/enter`, platformTokens.accessToken, {})
const systemTokens = entry.tokens
const systemContext = await api('/api/auth/me', systemTokens.accessToken)
const edge = spawn(edgePath, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--remote-debugging-port=19236', '--remote-allow-origins=*', `--user-data-dir=${profile}`,
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
    url: `${frontend}/systems/${systemId}?workspace=runtime&module=${sourceCode}`,
  })
  await send('Page.navigate', {
    url: `${frontend}/systems/${systemId}?workspace=runtime&module=${sourceCode}&recordId=${state.sourceRecordId}`,
  })
  let sourceReady = false
  for (let attempt = 0; attempt < 50; attempt++) {
    await delay(350)
    sourceReady = await evaluate(`document.body.innerText.includes(${JSON.stringify(state.sourceTitle)})
      && !!document.querySelector('.ant-drawer .ant-tabs-tab')`)
    if (sourceReady) break
  }
  if (!sourceReady) throw new Error('Converted source record did not load in browser')
  await evaluate(`[...document.querySelectorAll('.ant-drawer .ant-tabs-tab')]
    .find(item => item.innerText.trim() === '\u8f6c\u5316').click()`)
  const links = await api(`/api/runtime/modules/${sourceCode}/records/${state.sourceRecordId}/conversions`,
    systemTokens.accessToken)
  if (!links.links.some(link => link.recordId === state.targetRecordId && link.moduleCode === targetCode))
    throw new Error('Current conversion link missing from API')
  let relationVisible = false
  for (let attempt = 0; attempt < 30; attempt++) {
    await delay(300)
    relationVisible = await evaluate(`[...document.querySelectorAll('.ant-drawer .ant-list-item button')]
      .some(button => button.innerText.includes(${JSON.stringify(state.sourceTitle)}))`)
    if (relationVisible) break
  }
  if (!relationVisible) throw new Error('Source relation tab does not show converted target')
  const sourcePng = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  const sourcePath = `${evidence}/s18-module-conversion-source-browser.png`
  writeFileSync(sourcePath, Buffer.from(sourcePng.data, 'base64'))
  await evaluate(`[...document.querySelectorAll('.ant-drawer .ant-list-item button')]
    .find(button => button.innerText.includes(${JSON.stringify(state.sourceTitle)})).click()`)
  let targetReady = false
  for (let attempt = 0; attempt < 50; attempt++) {
    await delay(350)
    const url = new URL(await evaluate('location.href'))
    targetReady = url.searchParams.get('module') === targetCode
      && url.searchParams.get('recordId') === String(state.targetRecordId)
    if (targetReady) break
  }
  if (!targetReady) throw new Error('Conversion link did not open the target record')
  const targetRecord = await api(`/api/runtime/modules/${targetCode}/records/${state.targetRecordId}`,
    systemTokens.accessToken)
  if (targetRecord.fields.subject !== 'changed-target-after-conversion')
    throw new Error('Target page does not match current independent target record')
  let targetRendered = false
  for (let attempt = 0; attempt < 40; attempt++) {
    await delay(250)
    targetRendered = await evaluate(`!!document.querySelector('.ant-drawer')
      && document.body.innerText.includes('changed-target-after-conversion')`)
    if (targetRendered) break
  }
  if (!targetRendered) throw new Error('Converted target detail did not render after navigation')
  const targetPng = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  const targetPath = `${evidence}/s18-module-conversion-target-browser.png`
  writeFileSync(targetPath, Buffer.from(targetPng.data, 'base64'))
  const result = { sourceCode, sourceRecordId: state.sourceRecordId,
    targetCode, targetRecordId: state.targetRecordId,
    sourceRelationVisible: true, targetRouteOpened: true, sourcePath, targetPath }
  writeFileSync(`${evidence}/s18-module-conversion-browser-readback.json`,
    JSON.stringify(result, null, 2) + '\n')
  console.log(JSON.stringify(result))
  socket.close()
} finally {
  edge.kill()
}
