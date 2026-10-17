// 在 S19 同一候选环境中核对普通成员的桌面和手机运行页面。
import { spawn } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'
import { setTimeout as delay } from 'node:timers/promises'

const password = process.env.S19_USER_PASSWORD
if (!password) throw new Error('S19_USER_PASSWORD is required')
const evidence = JSON.parse(readFileSync('projects/unexamine/ai/evidence/s19-member-record-readback.json', 'utf8'))
const base = process.env.S19_FRONTEND_URL ?? 'http://127.0.0.1:15180'
const debug = 'http://127.0.0.1:19237'
const edge = spawn('C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe', [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--remote-debugging-port=19237', '--remote-allow-origins=*',
  `--user-data-dir=${process.env.TEMP}\\unexamine-s19-member-browser`, 'about:blank',
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

  if (process.argv.includes('--secondary')) {
    const branch = JSON.parse(readFileSync('projects/unexamine/ai/evidence/s19-secondary-tenant-readback.json', 'utf8'))
    await waitFor(`document.querySelector('.tenant-switch-trigger') !== null`)
    await evaluate(`document.querySelector('.tenant-switch-trigger').click()`)
    await waitFor(`[...document.querySelectorAll('.ant-dropdown-menu-item')].some(item => item.textContent.includes('发布验收次空间'))`)
    await evaluate(`(() => {
      const item = [...document.querySelectorAll('.ant-dropdown-menu-item')]
        .find(node => node.textContent.includes('发布验收次空间'))
      if (!item) throw new Error('Secondary tenant switch option missing')
      item.click()
    })()`)
    await waitFor(`document.querySelector('.tenant-switch-label')?.textContent.includes('发布验收次空间')`)
    await send('Page.navigate', { url: `${base}/systems/1?workspace=runtime&module=${branch.moduleCode}` })
    await waitFor(`document.querySelector('.record-table .ant-table-row')?.textContent.includes('S19 次空间客户')`)
    const branchView = await evaluate(`({tenant:document.querySelector('.tenant-switch-label')?.textContent.trim(),
      text:document.body.innerText.slice(-850),
      branchRecordVisible:document.body.innerText.includes('S19 次空间客户'),
      mainRecordVisible:document.body.innerText.includes('S19 上海客户')})`)
    if (!branchView.branchRecordVisible || branchView.mainRecordVisible) throw new Error('Secondary tenant record isolation failed')
    const branchImage = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
    writeFileSync('projects/unexamine/ai/evidence/s19-secondary-member-desktop.png', Buffer.from(branchImage.data, 'base64'))
    await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true })
    await send('Page.navigate', { url: `${base}/systems/1?workspace=runtime&module=${branch.moduleCode}` })
    await waitFor(`document.querySelector('.mobile-record-item')?.textContent.includes('S19 次空间客户')`)
    const mobile = await evaluate(`({viewport:innerWidth, text:document.querySelector('.mobile-record-item')?.textContent.trim()})`)
    if (mobile.viewport > 400 || !mobile.text.includes('S19 次空间客户')) throw new Error('Secondary mobile card missing')
    const mobileImage = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
    writeFileSync('projects/unexamine/ai/evidence/s19-secondary-member-mobile.png', Buffer.from(mobileImage.data, 'base64'))
    writeFileSync('projects/unexamine/ai/evidence/s19-secondary-member-browser.json', JSON.stringify({
      step: 'S19.1', ...branchView, mobile,
    }, null, 2) + '\n')
    console.log(JSON.stringify({ branchView, mobile }))
  } else if (process.argv.includes('--search')) {
    const customer = evidence.records['客户记录']
    await send('Page.navigate', { url: base + '/systems/1?workspace=runtime&module=' + customer.moduleCode })
    await waitFor('[...document.querySelectorAll(".record-table .ant-table-row")].some(x=>x.textContent.includes("S19 上海客户"))')
    const menu = await evaluate('({customerGroupVisible:document.body.innerText.includes("业务运营"), orderGroupVisible:document.body.innerText.includes("订单运营"), customerVisible:document.body.innerText.includes("客户记录")})')
    if (!menu.customerGroupVisible || !menu.orderGroupVisible || !menu.customerVisible) throw new Error('Published group navigation is missing')
    await evaluate('(() => { const input=document.querySelector(".record-search input"); if (!input) throw new Error("Search input missing"); input.value="S19 上海客户"; input.dispatchEvent(new Event("input",{bubbles:true})); input.dispatchEvent(new Event("change",{bubbles:true})); const button=document.querySelector(".record-search button"); if (!button) throw new Error("Search button missing"); button.click() })()')
    await waitFor('document.body.innerText.includes("搜索“S19 上海客户”")')
    await waitFor('document.querySelectorAll(".record-table .ant-table-row").length === 1 && document.querySelector(".record-table .ant-table-row")?.textContent.includes("S19 上海客户")')
    const matching = await evaluate('({rows:[...document.querySelectorAll(".record-table .ant-table-row")].map(x=>x.textContent.trim())})')
    if (matching.rows.length !== 1 || !matching.rows[0].includes('S19 上海客户')) throw new Error('Member search did not narrow to the customer record')
    await evaluate('(() => { const input=document.querySelector(".record-search input"); input.value="不存在的客户S19"; input.dispatchEvent(new Event("input",{bubbles:true})); input.dispatchEvent(new Event("change",{bubbles:true})); document.querySelector(".record-search button").click() })()')
    await waitFor('document.body.innerText.includes("搜索“不存在的客户S19”")')
    await waitFor('document.querySelectorAll(".record-table .ant-table-row").length === 0')
    const excluded = await evaluate('({rows:document.querySelectorAll(".record-table .ant-table-row").length, empty:document.body.innerText.includes("当前条件下没有记录")})')
    if (excluded.rows !== 0 || !excluded.empty) throw new Error('Member search did not exclude unrelated records')
    const screenshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
    writeFileSync('projects/unexamine/ai/evidence/s19-member-search-browser.png', Buffer.from(screenshot.data, 'base64'))
    writeFileSync('projects/unexamine/ai/evidence/s19-member-search-browser.json', JSON.stringify({ status:'member_search_narrows_and_excludes', menu, matching, excluded }, null, 2) + '\n')
    console.log(JSON.stringify({ menu, matching, excluded }))
  } else if (process.argv.includes('--messages')) {
    await send('Page.navigate', { url: base + '/systems/1?workspace=messages' })
    await waitFor('document.querySelector(".message-card") !== null')
    const desktop = await evaluate('({url:location.href, total:document.querySelectorAll(".message-card").length, text:document.querySelector(".message-card")?.textContent.trim(), denied:document.body.innerText.includes("当前工作范围没有消息查看权限")})')
    if (desktop.denied || desktop.total < 1) throw new Error('Member message inbox is unavailable')
    const desktopImage = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
    writeFileSync('projects/unexamine/ai/evidence/s19-member-messages-desktop.png', Buffer.from(desktopImage.data, 'base64'))
    await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true })
    await send('Page.navigate', { url: base + '/systems/1?workspace=messages' })
    await waitFor('document.querySelector(".message-card") !== null')
    const mobile = await evaluate('({viewport:innerWidth, total:document.querySelectorAll(".message-card").length, text:document.querySelector(".message-card")?.textContent.trim()})')
    if (mobile.viewport !== 390 || mobile.total < 1) throw new Error('Mobile member message inbox is unavailable')
    const mobileImage = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
    writeFileSync('projects/unexamine/ai/evidence/s19-member-messages-mobile.png', Buffer.from(mobileImage.data, 'base64'))
    writeFileSync('projects/unexamine/ai/evidence/s19-member-messages-browser.json', JSON.stringify({ status: 'desktop_mobile_member_messages_visible', desktop, mobile }, null, 2) + '\n')
    console.log(JSON.stringify({ desktop, mobile }))
  } else {
  const views = []
  for (const [name, item] of Object.entries(evidence.records)) {
    await send('Page.navigate', { url: `${base}/systems/${evidence.systemId}?workspace=runtime&module=${item.moduleCode}` })
    await waitFor(`document.body.innerText.includes(${JSON.stringify(item.moduleCode === evidence.records['客户记录'].moduleCode ? 'S19 上海客户' : 'S19 首笔订单')})`)
    const title = name === '客户记录' ? 'S19 上海客户' : 'S19 首笔订单'
    const desktop = await evaluate(`({url:location.href, heading:document.querySelector('h1')?.textContent,
      recordVisible:document.body.innerText.includes(${JSON.stringify(title)}),
      rows:[...document.querySelectorAll('.record-table .ant-table-row')].map(row => row.textContent.trim())})`)
    if (!desktop.recordVisible || !desktop.rows.some(row => row.includes(title))) throw new Error(`${name}: desktop row missing`)
    const desktopImage = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
    writeFileSync(`projects/unexamine/ai/evidence/s19-member-${name === '客户记录' ? 'customer' : 'order'}-desktop.png`, Buffer.from(desktopImage.data, 'base64'))
    await evaluate(`(() => {
      const row = [...document.querySelectorAll('.record-table .ant-table-row')].find(item => item.textContent.includes(${JSON.stringify(title)}))
      row.click()
    })()`)
    await waitFor(`document.querySelector('.detail-drawer')?.textContent.includes(${JSON.stringify(title)})`)
    const detail = await evaluate(`({visible:document.querySelector('.detail-drawer')?.textContent.includes(${JSON.stringify(title)}),
      text:document.querySelector('.detail-drawer')?.textContent.slice(0,700)})`)
    const detailImage = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
    writeFileSync(`projects/unexamine/ai/evidence/s19-member-${name === '客户记录' ? 'customer' : 'order'}-detail.png`, Buffer.from(detailImage.data, 'base64'))
    views.push({ name, desktop, detail })
  }
  await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true })
  const customer = evidence.records['客户记录']
  await send('Page.navigate', { url: `${base}/systems/${evidence.systemId}?workspace=runtime&module=${customer.moduleCode}` })
  await waitFor(`document.querySelector('.mobile-record-item')?.textContent.includes('S19 上海客户')`)
  const mobile = await evaluate(`({viewport:innerWidth, cards:[...document.querySelectorAll('.mobile-record-item')].map(item=>item.textContent.trim())})`)
  const mobileImage = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  writeFileSync('projects/unexamine/ai/evidence/s19-member-customer-mobile.png', Buffer.from(mobileImage.data, 'base64'))
  if (mobile.viewport > 400 || !mobile.cards.some(card => card.includes('S19 上海客户'))) throw new Error('Mobile record card missing')
  await evaluate(`document.querySelector('.mobile-record-item button').click()`)
  await waitFor(`document.querySelector('.detail-drawer')?.textContent.includes('上海客户已跟进')`)
  const mobileDetail = await evaluate(`({visible:document.querySelector('.detail-drawer')?.textContent.includes('上海客户已跟进'),
    text:document.querySelector('.detail-drawer')?.textContent.slice(0,500)})`)
  const mobileDetailImage = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  writeFileSync('projects/unexamine/ai/evidence/s19-member-customer-mobile-detail.png', Buffer.from(mobileDetailImage.data, 'base64'))
  await evaluate(`(() => {
    const tab = [...document.querySelectorAll('.detail-drawer .ant-tabs-tab')].find(item => item.textContent.includes('附件'))
    if (!tab) throw new Error('Mobile attachment tab missing')
    tab.click()
  })()`)
  await waitFor(`document.querySelector('.detail-drawer')?.textContent.includes('s19-customer-note.txt')`)
  const mobileAttachment = await evaluate(`({text:document.querySelector('.detail-drawer')?.textContent.slice(-500)})`)
  const mobileAttachmentImage = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  writeFileSync('projects/unexamine/ai/evidence/s19-member-customer-mobile-attachment.png', Buffer.from(mobileAttachmentImage.data, 'base64'))
  await evaluate(`(() => {
    const button = [...document.querySelectorAll('.detail-drawer button')].find(item => item.textContent.replaceAll(' ', '').includes('打印'))
    if (!button) throw new Error('Mobile print action missing')
    button.click()
  })()`)
  await waitFor(`document.querySelector('.ant-modal')?.textContent.includes('记录打印与 PDF 预览')`)
  const mobilePrint = await evaluate(`({text:document.querySelector('.ant-modal')?.textContent.slice(0,800)})`)
  const mobilePrintImage = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  writeFileSync('projects/unexamine/ai/evidence/s19-member-customer-mobile-print.png', Buffer.from(mobilePrintImage.data, 'base64'))
  await send('Page.navigate', { url: `${base}/systems/${evidence.systemId}?workspace=runtime&module=${customer.moduleCode}` })
  await waitFor(`document.querySelector('.mobile-record-item')?.textContent.includes('S19 上海客户')`)
  await evaluate(`(() => {
    const button = [...document.querySelectorAll('button')].find(item => item.textContent.replaceAll(' ', '').trim() === '导出')
    if (!button) throw new Error('Mobile export action missing')
    button.click()
  })()`)
  await waitFor(`document.querySelector('.ant-drawer')?.textContent.includes('导出')`)
  const mobileExport = await evaluate(`({text:document.querySelector('.ant-drawer')?.textContent.slice(0,700)})`)
  const mobileExportImage = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  writeFileSync('projects/unexamine/ai/evidence/s19-member-customer-mobile-export.png', Buffer.from(mobileExportImage.data, 'base64'))
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 980, deviceScaleFactor: 1, mobile: false })
  await send('Page.navigate', { url: `${base}/systems/${evidence.systemId}?workspace=runtime&module=${customer.moduleCode}` })
  await waitFor(`document.querySelector('.record-table .ant-table-row')?.textContent.includes('S19 上海客户')`)
  await evaluate(`document.querySelector('.record-table .ant-table-row').click()`)
  await waitFor(`document.querySelector('.detail-drawer')?.textContent.includes('S19 上海客户')`)
  await evaluate(`(() => {
    const tab = [...document.querySelectorAll('.detail-drawer .ant-tabs-tab')].find(item => item.textContent.includes('附件'))
    if (!tab) throw new Error('Record attachment tab missing')
    tab.click()
  })()`)
  await waitFor(`document.querySelector('.detail-drawer')?.textContent.includes('s19-customer-note.txt')`)
  const attachment = await evaluate(`({text:document.querySelector('.detail-drawer')?.textContent.slice(-500)})`)
  const attachmentImage = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  writeFileSync('projects/unexamine/ai/evidence/s19-member-customer-attachment.png', Buffer.from(attachmentImage.data, 'base64'))
  await evaluate(`(() => {
    const button = [...document.querySelectorAll('.detail-drawer button')].find(item => item.textContent.replaceAll(' ', '').includes('打印'))
    if (!button) throw new Error('Published print action missing')
    button.click()
  })()`)
  await waitFor(`document.querySelector('.ant-modal')?.textContent.includes('记录打印与 PDF 预览')`)
  await evaluate(`(() => {
    const button = [...document.querySelectorAll('.ant-modal button')].find(item => item.textContent.replaceAll(' ', '').includes('按发布版本生成'))
    if (!button) throw new Error('Print generation button missing')
    button.click()
  })()`)
  await waitFor(`document.querySelector('.ant-modal')?.textContent.includes('打印文件已生成')`)
  const printed = await evaluate(`({text:document.querySelector('.ant-modal')?.textContent.slice(0,800)})`)
  const printImage = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  writeFileSync('projects/unexamine/ai/evidence/s19-member-customer-print.png', Buffer.from(printImage.data, 'base64'))
  await send('Page.navigate', { url: `${base}/systems/${evidence.systemId}?workspace=runtime&module=${customer.moduleCode}` })
  await waitFor(`document.querySelector('.record-table .ant-table-row')?.textContent.includes('S19 上海客户')`)
  await evaluate(`(() => {
    const button = [...document.querySelectorAll('button')].find(item => item.textContent.replaceAll(' ', '').trim() === '导出')
    if (!button) throw new Error('Published export action missing')
    button.click()
  })()`)
  await waitFor(`document.querySelector('.ant-drawer')?.textContent.includes('导出')`)
  const exporting = await evaluate(`({text:document.querySelector('.ant-drawer')?.textContent.slice(0,700)})`)
  const exportImage = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  writeFileSync('projects/unexamine/ai/evidence/s19-member-customer-export.png', Buffer.from(exportImage.data, 'base64'))
  evidence.status = 'api_and_browser_verified'
  evidence.browserViews = { desktopModules: views.map(item => item.name), desktopDetails: views.map(item => item.detail.visible),
    mobileCard: true, mobileDetail: mobileDetail.visible, mobileAttachment, mobilePrint, mobileExport,
    attachment, printed, exporting }
  writeFileSync('projects/unexamine/ai/evidence/s19-member-record-readback.json', JSON.stringify(evidence, null, 2) + '\n')
  console.log(JSON.stringify({ views, mobile, mobileDetail }))
  }
  socket.close()
} finally {
  edge.kill()
}
