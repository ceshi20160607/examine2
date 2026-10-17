// 在真实 Edge 页面核对已发布模块动作进入当前租户的模块仪表盘。
import { spawn } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'
import { setTimeout as delay } from 'node:timers/promises'

const statePath = 'projects/unexamine/ai/evidence/s18-module-daily-journey-live.json'
const state = JSON.parse(readFileSync(statePath, 'utf8'))
const systemId = 143
const backend = 'http://127.0.0.1:18081'
const frontend = 'http://127.0.0.1:15179'
const debug = 'http://127.0.0.1:19235'
const evidence = 'projects/unexamine/ai/evidence'
const edgePath = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const profile = `${process.env.TEMP}\\unexamine-s18-daily-journey-browser`

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
  '--remote-debugging-port=19235', '--remote-allow-origins=*', `--user-data-dir=${profile}`,
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
    url: `${frontend}/systems/${systemId}?workspace=runtime&module=${state.moduleCode}`,
  })
  let runtime
  for (let attempt = 0; attempt < 50; attempt++) {
    await delay(350)
    runtime = await evaluate(`({url:location.href, text:document.body.innerText,
      importButton:[...document.querySelectorAll('[data-action-code="IMPORT"]')]
        .some(button => button.getBoundingClientRect().width > 0),
      exportButton:[...document.querySelectorAll('[data-action-code="EXPORT"]')]
        .some(button => button.getBoundingClientRect().width > 0)})`)
    if (runtime.importButton && runtime.exportButton && runtime.text.includes(state.importedTitle)) break
  }
  if (!runtime?.importButton || !runtime?.exportButton || !runtime.text.includes(state.importedTitle))
    throw new Error('Published import/export or imported record absent from runtime')
  const listPng = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  const listPath = `${evidence}/s18-module-daily-journey-list-browser.png`
  writeFileSync(listPath, Buffer.from(listPng.data, 'base64'))
  await send('Page.navigate', {
    url: `${frontend}/systems/${systemId}?workspace=runtime&module=${state.moduleCode}&recordId=${state.importedRecordId}`,
  })
  let record
  for (let attempt = 0; attempt < 45; attempt++) {
    await delay(350)
    record = await evaluate(`({title:document.body.innerText.includes(${JSON.stringify(state.importedTitle)}),
      menu:!!document.querySelector('.ant-drawer [data-action-location="DETAIL_MORE"] button')})`)
    if (record.title && record.menu) break
  }
  if (!record?.title || !record?.menu) throw new Error('Transferred record drawer did not load')
  const transfer = (await api(`/api/runtime/modules/${state.moduleCode}/records/${state.importedRecordId}`,
    systemTokens.accessToken)).actions.find(action => action.code === 'TRANSFER')
  if (!transfer) throw new Error('Published transfer action absent from record')
  await evaluate(`document.querySelector('.ant-drawer [data-action-location="DETAIL_MORE"] button').click()`)
  let transferMenu = false
  for (let attempt = 0; attempt < 20; attempt++) {
    await delay(200)
    transferMenu = await evaluate(`[...document.querySelectorAll('.ant-dropdown-menu-item')]
      .some(item => item.innerText.includes(${JSON.stringify(transfer.name)}))`)
    if (transferMenu) break
  }
  if (!transferMenu) throw new Error('Transfer is not available in the record action menu')
  const detailPng = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  const detailPath = `${evidence}/s18-module-daily-journey-transfer-browser.png`
  writeFileSync(detailPath, Buffer.from(detailPng.data, 'base64'))
  const receiverPlatform = await api('/api/auth/login', null,
    { username: state.receiverAccount, password: 'correct-password' })
  const receiverPlatformContext = await api('/api/auth/me', receiverPlatform.accessToken)
  const receiverEntry = await api(`/api/systems/${systemId}/enter`, receiverPlatform.accessToken, {})
  const receiverContext = await api('/api/auth/me', receiverEntry.tokens.accessToken)
  const receiverStorage = {
    'unexamine.platform.tokens': receiverPlatform,
    'unexamine.platform.context': receiverPlatformContext,
    'unexamine.system.tokens': receiverEntry.tokens,
    'unexamine.system.context': receiverContext,
  }
  await evaluate(`Object.entries(${JSON.stringify(receiverStorage)})
    .forEach(([key,value]) => sessionStorage.setItem(key, JSON.stringify(value)))`)
  await send('Page.navigate', {
    url: `${frontend}/systems/${systemId}?workspace=runtime&module=${state.moduleCode}&recordId=${state.importedRecordId}`,
  })
  let receiverVisible = false
  for (let attempt = 0; attempt < 50; attempt++) {
    await delay(350)
    receiverVisible = await evaluate(`document.body.innerText.includes(${JSON.stringify(state.importedTitle)})
      && !!document.querySelector('.ant-drawer')`)
    if (receiverVisible) break
  }
  if (!receiverVisible) throw new Error('New owner cannot open transferred record in browser')
  const receiverPng = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  const receiverPath = `${evidence}/s18-module-daily-journey-receiver-browser.png`
  writeFileSync(receiverPath, Buffer.from(receiverPng.data, 'base64'))
  const result = { moduleCode: state.moduleCode, recordId: state.importedRecordId,
    importedTitle: state.importedTitle, runtimeImportButton: true, runtimeExportButton: true,
    transferMenu: true, receiverVisible: true, listPath, detailPath, receiverPath }
  writeFileSync(`${evidence}/s18-module-daily-journey-browser-readback.json`,
    JSON.stringify(result, null, 2) + '\n')
  console.log(JSON.stringify(result))
  socket.close()
} finally {
  edge.kill()
}
