// Edge 通过发布包自带的前端进程登录并进入同一隔离库的系统。
import { spawn } from 'node:child_process'
import { writeFileSync } from 'node:fs'
import { setTimeout as delay } from 'node:timers/promises'

const base = 'http://127.0.0.1:15173'
const debug = 'http://127.0.0.1:19247'
const profile = `${process.env.TEMP}\\unexamine-s19-direct-browser`
const edge = spawn('C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe', [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--remote-debugging-port=19247', '--remote-allow-origins=*', `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore', windowsHide: true })
try {
  let ready = false
  for (let i = 0; i < 80; i++) {
    try { if ((await fetch(debug + '/json/version')).ok) { ready = true; break } } catch { /* 等待浏览器端口。 */ }
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
    for (let i = 0; i < 100; i++) {
      if (await evaluate(expression)) return
      await delay(300)
    }
    throw new Error(`Browser timed out: ${expression}; ${JSON.stringify(await evaluate('({url:location.href,text:document.body.innerText.slice(0,350)})'))}`)
  }
  await send('Page.enable')
  await send('Runtime.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false })
  await send('Page.navigate', { url: base + '/login' })
  await waitFor(`document.querySelector('input[autocomplete="username"]') !== null`)
  await evaluate(`(() => {
    const set = (selector, value) => {
      const input = document.querySelector(selector)
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, value)
      input.dispatchEvent(new Event('input', { bubbles: true }))
    }
    set('input[autocomplete="username"]', 'admin')
    set('input[autocomplete="current-password"]', '123123aa')
    document.querySelector('button[type="submit"]').click()
  })()`)
  await waitFor(`location.pathname.startsWith('/platform') && document.body.innerText.includes('S19 直连部署系统')`)
  const platformPage = await evaluate(`({url:location.href,systemVisible:document.body.innerText.includes('S19 直连部署系统')})`)
  await send('Page.navigate', { url: base + '/platform' })
  await waitFor(`document.querySelector('.system-row')?.innerText.includes('S19 直连部署系统')`)
  await evaluate(`(() => {
    const row = [...document.querySelectorAll('.system-row')].find(item => item.textContent.includes('S19 直连部署系统'))
    const button = [...row.querySelectorAll('button')].find(item => item.textContent.includes('进入'))
    button.click()
  })()`)
  await waitFor(`location.pathname.startsWith('/systems/1') && document.body.innerText.includes('S19 直连部署系统')`)
  await waitFor(`document.querySelector('.ant-skeleton') === null`)
  const systemPage = await evaluate(`({url:location.href,systemVisible:document.body.innerText.includes('S19 直连部署系统')})`)
  const screenshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
  writeFileSync('projects/unexamine/ai/evidence/s19-direct-deployment-browser.png', Buffer.from(screenshot.data, 'base64'))
  const evidence = { step: 'S19.7', candidate: '0.1.0-s19.7-dev5', platformPage, systemPage,
    screenshot: 'projects/unexamine/ai/evidence/s19-direct-deployment-browser.png' }
  writeFileSync('projects/unexamine/ai/evidence/s19-direct-deployment-browser.json', JSON.stringify(evidence, null, 2) + '\n')
  console.log(JSON.stringify(evidence, null, 2))
  socket.close()
} finally {
  edge.kill()
}
