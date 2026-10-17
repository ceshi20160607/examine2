// 在 S19 同一候选环境中核对普通成员的桌面和手机运行页面。
import { spawn } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'
import { setTimeout as delay } from 'node:timers/promises'

const password = process.env.S19_USER_PASSWORD
if (!password) throw new Error('S19_USER_PASSWORD is required')
const evidence = JSON.parse(readFileSync('projects/unexamine/ai/evidence/s19-member-record-readback.json', 'utf8'))
const base = process.env.S19_FRONTEND_URL ?? 'http://127.0.0.1:15180'
const debug = 'http://127.0.0.1:19239'
const edge = spawn('C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe', [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--remote-debugging-port=19239', '--remote-allow-origins=*',
  `--user-data-dir=${process.env.TEMP}\\unexamine-s19-mobile-crud-browser`, 'about:blank',
], { stdio: 'ignore', windowsHide: true })

try {
  for (let i = 0; i < 60; i++) {
    try { if ((await fetch(debug + '/json/version')).ok) break }
    catch { /* 等待浏览器调试入口。 */ }
    await delay(250)
  }
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
    for (let i = 0; i < 65; i++) {
      const value = await evaluate(expression)
      if (value) return value
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
    fill('input[autocomplete="username"]', 's19_member')
    fill('input[autocomplete="current-password"]', ${JSON.stringify(password)})
  })()`)
  await delay(100)
  await evaluate(`document.querySelector('form button[type="submit"]').click()`)
  await waitFor(`location.pathname.startsWith('/platform')`)
  await waitFor(`document.body.innerText.includes('发布验收业务甲')`)
  const platformDebug = await evaluate(`({text:document.body.innerText.slice(-1100), rows:[...document.querySelectorAll('.system-row')].map(item=>item.textContent.trim())})`)
  console.log(JSON.stringify({ platformDebug }))
  await evaluate(`(() => {
    const button = [...document.querySelectorAll('button')].find(item => item.textContent.includes('进入发布验收业务甲'))
    if (!button) throw new Error('Member system entry is missing')
    button.click()
  })()`)
  await waitFor(`location.pathname === '/systems/${evidence.systemId}'`)

  await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true })
  const customer = Object.values(evidence.records)[0]
  await send('Page.navigate', { url: `${base}/systems/${evidence.systemId}?workspace=runtime&module=${customer.moduleCode}` })
  await waitFor(`document.querySelector('.mobile-record-item')?.textContent.includes('S19 ')`)
  const title = 'S19 手机创建客户'
  if (!(await evaluate(`document.body.innerText.includes(${JSON.stringify(title)})`))) {
    await evaluate(`(() => {
      const button = [...document.querySelectorAll('button')].find(item => item.textContent.replaceAll(' ', '').includes('新建客户记录'))
      if (!button) throw new Error('Mobile create action missing')
      button.click()
    })()`)
    await waitFor(`document.querySelector('.product-form-drawer')?.textContent.includes('新建客户记录')`)
    await evaluate(`(() => {
      const fill = (selector, value) => {
        const input = document.querySelector(selector)
        if (!input) throw new Error('Mobile form input missing: ' + selector)
        input.value = value
        input.dispatchEvent(new Event('input', { bubbles: true }))
        input.dispatchEvent(new Event('change', { bubbles: true }))
      }
      fill('#runtime-record-title', ${JSON.stringify(title)})
      fill('[data-runtime-field] input[type="text"]', '手机录入')
      const save = [...document.querySelectorAll('.product-form-drawer button')].find(item => item.textContent.replaceAll(' ', '').trim() === '保存')
      if (!save) throw new Error('Mobile save action missing')
      save.click()
    })()`)
    await waitFor(`document.querySelector('.mobile-record-item')?.textContent.includes(${JSON.stringify(title)})`)
  }
  const created = await evaluate(`({viewport:innerWidth, card:[...document.querySelectorAll('.mobile-record-item')].find(x=>x.textContent.includes(${JSON.stringify(title)}))?.textContent.trim()})`)
  const createImage = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  writeFileSync('projects/unexamine/ai/evidence/s19-mobile-customer-created.png', Buffer.from(createImage.data, 'base64'))
  await evaluate(`(() => {
    const card = [...document.querySelectorAll('.mobile-record-item')].find(item => item.textContent.includes(${JSON.stringify(title)}))
    if (!card) throw new Error('Created mobile record missing')
    card.querySelector('button').click()
  })()`)
  await waitFor(`document.querySelector('.detail-drawer')?.textContent.includes(${JSON.stringify(title)})`)
  await evaluate(`(() => {
    const button = [...document.querySelectorAll('.detail-drawer button')].find(item => item.textContent.replaceAll(' ', '').trim() === '编辑')
    if (!button) throw new Error('Mobile edit action missing')
    button.click()
  })()`)
  await waitFor(`document.querySelector('.product-form-drawer')?.textContent.includes('编辑客户记录')`)
  await evaluate(`(() => {
    const input = document.querySelector('[data-runtime-field] input[type="text"]')
    if (!input) throw new Error('Mobile editable customer field missing')
    input.value = '手机录入后更新'
    input.dispatchEvent(new Event('input', { bubbles: true }))
    input.dispatchEvent(new Event('change', { bubbles: true }))
    const save = [...document.querySelectorAll('.product-form-drawer button')].find(item => item.textContent.replaceAll(' ', '').trim() === '保存')
    if (!save) throw new Error('Mobile edit save action missing')
    save.click()
  })()`)
  await waitFor(`document.querySelector('.mobile-record-item')?.textContent.includes('手机录入后更新')`)
  const edited = await evaluate(`({viewport:innerWidth,card:[...document.querySelectorAll('.mobile-record-item')].find(x=>x.textContent.includes(${JSON.stringify(title)}))?.textContent.trim()})`)
  const editImage = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  writeFileSync('projects/unexamine/ai/evidence/s19-mobile-customer-edited.png', Buffer.from(editImage.data, 'base64'))
  if (created.viewport !== 390 || edited.viewport !== 390 || !edited.card.includes('手机录入后更新')) throw new Error('Mobile CRUD readback failed')
  writeFileSync('projects/unexamine/ai/evidence/s19-mobile-crud-browser.json', JSON.stringify({ step: 'S19.1', status: 'mobile_create_edit_browser_verified', created, edited }, null, 2) + '\n')
  console.log(JSON.stringify({ created, edited }))
  socket.close()
} finally {
  edge.kill()
}
