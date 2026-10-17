// 在空库候选制品上用真实页面验证管理员登录和两个系统的建立。
import { spawn } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'
import { setTimeout as delay } from 'node:timers/promises'

const password = process.env.S19_ADMIN_PASSWORD
if (!password) throw new Error('S19_ADMIN_PASSWORD is required')
const frontend = process.env.S19_FRONTEND_URL ?? 'http://127.0.0.1:15180'
const debug = 'http://127.0.0.1:19236'
const profile = `${process.env.TEMP}\\unexamine-s19-release-browser`
const edge = spawn('C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe', [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--remote-debugging-port=19236', '--remote-allow-origins=*', `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore', windowsHide: true })

try {
  let ready = false
  for (let attempt = 0; attempt < 60; attempt++) {
    try { if ((await fetch(debug + '/json/version')).ok) { ready = true; break } }
    catch { /* 等待 Edge 调试入口。 */ }
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
    if (result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description ?? result.exceptionDetails.text)
    return result.result.value
  }
  const waitFor = async expression => {
    for (let attempt = 0; attempt < 60; attempt++) {
      const value = await evaluate(expression)
      if (value) return value
      await delay(350)
    }
    throw new Error(`Page condition did not arrive: ${expression.slice(0, 100)}`)
  }
  await send('Page.enable')
  await send('Runtime.enable')
  await send('Emulation.setDeviceMetricsOverride',
    { width: 1440, height: 980, deviceScaleFactor: 1, mobile: false })
  await send('Page.navigate', { url: frontend + '/login' })
  await waitFor(`document.querySelector('input[autocomplete="username"]') !== null`)
  const login = await evaluate(`(() => {
    const fill = (selector, value) => {
      const input = document.querySelector(selector)
      if (!input) throw new Error('Missing input ' + selector)
      input.value = value
      input.dispatchEvent(new Event('input', { bubbles: true }))
      input.dispatchEvent(new Event('change', { bubbles: true }))
    }
    fill('input[autocomplete="username"]', 'admin')
    fill('input[autocomplete="current-password"]', ${JSON.stringify(password)})
    const button = document.querySelector('form button[type="submit"]')
    if (!button) throw new Error('Missing login button')
    button.click()
    return true
  })()`)
  if (!login) throw new Error('Login form did not submit')
  await waitFor(`location.pathname.startsWith('/platform')`)

  const resume = process.argv.includes('--resume')
  const systems = resume
    ? JSON.parse(readFileSync('projects/unexamine/ai/evidence/s19-two-systems-browser-readback.json', 'utf8')).systems
    : []
  for (const name of resume ? [] : ['发布验收业务甲', '发布验收业务乙']) {
    await send('Page.navigate', { url: frontend + '/platform' })
    await waitFor(`document.body.innerText.includes('我的系统')`)
    await evaluate(`(() => {
      const button = [...document.querySelectorAll('button')].find(item => item.textContent.includes('新建系统'))
      if (!button) throw new Error('Create-system entry is missing')
      button.click()
    })()`)
    await waitFor(`document.querySelector('.ant-modal input') !== null`)
    await evaluate(`(() => {
      const input = document.querySelector('.ant-modal input')
      input.value = ${JSON.stringify(name)}
      input.dispatchEvent(new Event('input', { bubbles: true }))
      input.dispatchEvent(new Event('change', { bubbles: true }))
      const button = [...document.querySelectorAll('.ant-modal button')]
        .find(item => item.textContent.includes('创建系统'))
      if (!button) throw new Error('Create-system confirmation is missing')
      button.click()
    })()`)
    const path = await waitFor(`location.pathname.match(/^\\/systems\\/\\d+\\/admin$/)?.[0] || null`)
    systems.push({ name, id: Number(path.split('/')[2]) })
  }
  if (process.argv.includes('--target')) {
    await send('Page.navigate', { url: frontend + '/platform' })
    await waitFor(`document.body.innerText.includes('发布验收业务乙')`)
    await evaluate(`(() => {
      const row = [...document.querySelectorAll('.system-row')]
        .find(item => item.textContent.includes('发布验收业务乙'))
      const button = [...(row?.querySelectorAll('button') || [])].find(item => item.textContent.includes('进入'))
      if (!button) throw new Error('Target system entry button is missing')
      button.click()
    })()`)
    await waitFor(`location.pathname === '/systems/${systems[1].id}'`)
    await send('Page.navigate', { url: `${frontend}/systems/${systems[1].id}/admin?section=applications` })
    await waitFor(`document.body.innerText.includes('S19 目标系统应用')`)
    const application = await evaluate(`({url:location.href, visible:document.body.innerText.includes('S19 目标系统应用'),
      text:document.body.innerText.slice(-1100)})`)
    const applicationImage = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
    writeFileSync('projects/unexamine/ai/evidence/s19-target-application-browser.png', Buffer.from(applicationImage.data, 'base64'))
    await send('Page.navigate', { url: `${frontend}/systems/${systems[1].id}?workspace=runtime&module=s19_service_contract` })
    await waitFor(`document.body.innerText.includes('S19 目标系统合同')`)
    const runtime = await evaluate(`({url:location.href, visible:document.body.innerText.includes('S19 目标系统合同'),
      text:document.body.innerText.slice(-900)})`)
    const runtimeImage = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
    writeFileSync('projects/unexamine/ai/evidence/s19-target-module-browser.png', Buffer.from(runtimeImage.data, 'base64'))
    writeFileSync('projects/unexamine/ai/evidence/s19-target-browser-readback.json', JSON.stringify({
      step: 'S19.1', application, runtime,
    }, null, 2) + '\n')
    console.log(JSON.stringify({ application, runtime }))
  } else if (resume) {
    await send('Page.navigate', { url: frontend + '/platform' })
    await waitFor(`document.body.innerText.includes('发布验收业务甲')`)
    await evaluate(`(() => {
      const row = [...document.querySelectorAll('.system-row')]
        .find(item => item.textContent.includes('发布验收业务甲'))
      const button = [...(row?.querySelectorAll('button') || [])]
        .find(item => item.textContent.includes('进入'))
      if (!button) throw new Error('System entry button is missing')
      button.click()
    })()`)
    await waitFor(`location.pathname === '/systems/${systems[0].id}'`)
    await send('Page.navigate', { url: `${frontend}/systems/${systems[0].id}/admin` })
    await waitFor(`document.body.innerText.includes('模板配置')`)
    if (process.argv.includes('--module')) {
      await evaluate(`(() => {
        const button = [...document.querySelectorAll('.system-admin-nav__item')]
          .find(item => item.textContent.includes('模板配置'))
        if (!button) throw new Error('Template configuration entry is missing')
        button.click()
      })()`)
      await waitFor(`document.body.innerText.includes('新建模块')`)
      const moduleExists = `(async () => {
        const token = JSON.parse(sessionStorage.getItem('unexamine.system.tokens')).accessToken
        const response = await fetch('/api/admin/module-config', { headers: { Authorization: 'Bearer ' + token } })
        const result = await response.json()
        return result.data?.modules?.some(item => item.name === '客户记录') || false
      })()`
      const exists = await evaluate(moduleExists)
      if (!exists) {
        await evaluate(`(() => {
          const button = [...document.querySelectorAll('.module-builder-toolbar button')]
            .find(item => item.textContent.includes('新建模块'))
          if (!button) throw new Error('New module button is missing')
          button.click()
        })()`)
        await waitFor(`document.querySelector('.ant-modal input[placeholder="例如：客户、订单、巡检记录"]') !== null`)
        await evaluate(`(() => {
          const fill = (selector, value) => {
            const input = document.querySelector(selector)
            if (!input) throw new Error('Missing module input ' + selector)
            input.value = value
            input.dispatchEvent(new Event('input', { bubbles: true }))
            input.dispatchEvent(new Event('change', { bubbles: true }))
          }
          fill('.ant-modal input[placeholder="例如：客户、订单、巡检记录"]', '客户记录')
          fill('.ant-modal input[placeholder="例如：客户运营"]', '业务运营')
          const button = document.querySelector('.ant-modal-footer .ant-btn-primary')
          if (!button) throw new Error('Module confirmation is missing')
          button.click()
        })()`)
        await waitFor(moduleExists)
        await send('Page.reload', { ignoreCache: true })
      }
      await waitFor(`document.body.innerText.includes('业务运营') && document.body.innerText.includes('客户记录')`)
      const module = await evaluate(`({text:document.body.innerText.slice(-1800),
        group:document.body.innerText.includes('业务运营'),
        module:document.body.innerText.includes('客户记录')})`)
      if (!module.group || !module.module) throw new Error('Module/group were not visible')
      await waitFor(`document.querySelector('.module-builder-toolbar .ant-btn-loading') === null`)
      await waitFor(`document.querySelector('.designer-heading') !== null || document.querySelector('.section-alert') !== null`)
      await delay(800)
      if (process.argv.includes('--configure')) {
        await evaluate(`(() => {
          window.__s19Calls = []
          const originalFetch = window.fetch
          window.fetch = (...args) => originalFetch(...args).then(async response => {
            if (String(args[0]).includes('/fields')) window.__s19Calls.push({url:String(args[0]), status:response.status, body:(await response.clone().text()).slice(0,500)})
            return response
          })
        })()`)
        const orderExists = await evaluate(`[...document.querySelectorAll('.module-tree-item')].some(item => item.textContent.includes('订单记录'))`)
        if (!orderExists) {
          await evaluate(`(() => {
            const button = [...document.querySelectorAll('.module-builder-toolbar button')]
              .find(item => item.textContent.includes('新建模块'))
            if (!button) throw new Error('New order module button is missing')
            button.click()
          })()`)
          await waitFor(`document.querySelector('.ant-modal input[placeholder="例如：客户、订单、巡检记录"]') !== null`)
          await evaluate(`(() => {
            const newGroup = [...document.querySelectorAll('.ant-modal .ant-radio-button-wrapper')]
              .find(item => item.textContent.includes('新建分组'))
            if (!newGroup) throw new Error('New group choice is missing')
            newGroup.click()
          })()`)
          await waitFor(`document.querySelector('.ant-modal input[placeholder="例如：客户运营"]') !== null`)
          await evaluate(`(() => {
            const fill = (selector, value) => {
              const input = document.querySelector(selector)
              if (!input) throw new Error('Missing order module input ' + selector)
              input.value = value
              input.dispatchEvent(new Event('input', { bubbles: true }))
              input.dispatchEvent(new Event('change', { bubbles: true }))
            }
            fill('.ant-modal input[placeholder="例如：客户、订单、巡检记录"]', '订单记录')
            fill('.ant-modal input[placeholder="例如：客户运营"]', '订单运营')
            document.querySelector('.ant-modal-footer .ant-btn-primary').click()
          })()`)
          await waitFor(`(async () => {
            const token = JSON.parse(sessionStorage.getItem('unexamine.system.tokens')).accessToken
            const result = await (await fetch('/api/admin/module-config', { headers: { Authorization: 'Bearer ' + token } })).json()
            return result.data.modules.some(item => item.name === '订单记录')
          })()`)
          await send('Page.reload', { ignoreCache: true })
          await waitFor(`[...document.querySelectorAll('.module-tree-item')].some(item => item.textContent.includes('订单记录'))`)
        }
        const customerFieldExists = await evaluate(`(async () => {
          const token = JSON.parse(sessionStorage.getItem('unexamine.system.tokens')).accessToken
          const overview = await (await fetch('/api/admin/module-config', { headers: { Authorization: 'Bearer ' + token } })).json()
          const module = overview.data.modules.find(item => item.name === '客户记录')
          const draft = await (await fetch('/api/admin/module-config/modules/' + module.id + '/draft', { headers: { Authorization: 'Bearer ' + token } })).json()
          return draft.data.fields.some(item => item.name === '客户名称')
        })()`)
        if (!customerFieldExists) {
        await evaluate(`(() => {
          const button = [...document.querySelectorAll('.module-tree-item')].find(item => item.textContent.includes('客户记录'))
          if (!button) throw new Error('Customer module is missing')
          button.click()
        })()`)
        await waitFor(`document.querySelector('.designer-heading')?.textContent.includes('客户记录')`)
        await evaluate(`(() => {
          const button = [...document.querySelectorAll('.config-task-tabs button')].find(item => item.textContent.includes('模块字段'))
          if (!button) throw new Error('Module field task is missing')
          button.click()
        })()`)
        await waitFor(`document.querySelector('.form-type-button[data-field-type="TEXT"]') !== null`)
        await evaluate(`document.querySelector('.form-type-button[data-field-type="TEXT"]').click()`)
        await waitFor(`document.querySelector('.form-field-properties input[placeholder="例如：客户名称、联系电话、跟进结果"]') !== null`)
        await evaluate(`(() => {
          const input = document.querySelector('.form-field-properties input[placeholder="例如：客户名称、联系电话、跟进结果"]')
          input.value = '客户名称'
          input.dispatchEvent(new Event('input', { bubbles: true }))
          input.dispatchEvent(new Event('change', { bubbles: true }))
        })()`)
        await delay(150)
        await evaluate(`(() => {
          const button = [...document.querySelectorAll('.form-field-properties button')].find(item => item.textContent.includes('保存字段'))
          if (!button) throw new Error('Save field button is missing')
          button.click()
        })()`)
        await delay(1200)
        const fieldDebug = await evaluate(`({input:document.querySelector('.form-field-properties input[placeholder="例如：客户名称、联系电话、跟进结果"]')?.value,
          alerts:[...document.querySelectorAll('.ant-alert,.ant-message-notice')].map(item=>item.textContent.trim()),
          calls:window.__s19Calls,
          saveButtons:[...document.querySelectorAll('.form-field-properties button')].filter(item=>item.textContent.includes('保存字段')).map(item=>({text:item.textContent,disabled:item.disabled})),
          text:document.body.innerText.slice(-1000)})`)
        console.log(JSON.stringify({ fieldDebug }))
        const fieldScreenshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
        writeFileSync('projects/unexamine/ai/evidence/s19-field-config-browser.png', Buffer.from(fieldScreenshot.data, 'base64'))
        }
      }
      if (process.argv.includes('--group')) {
        const alreadyNamed = await evaluate(`(async () => {
          const token = JSON.parse(sessionStorage.getItem('unexamine.system.tokens')).accessToken
          const response = await fetch('/api/admin/module-config', { headers: { Authorization: 'Bearer ' + token } })
          const result = await response.json()
          const group = result.data.groups.find(item => item.name === '订单运营')
          const module = result.data.modules.find(item => item.name === '订单记录')
          return !!group && module?.groupId === group.id
        })()`)
        if (!alreadyNamed) {
          await evaluate(`(() => {
            const group = [...document.querySelectorAll('.module-group')].find(item => item.textContent.includes('订单记录'))
            const button = [...(group?.querySelectorAll('button') || [])].find(item => item.textContent.trim() === '编辑')
            if (!button) throw new Error('Order group edit button is missing')
            button.click()
          })()`)
          await waitFor(`document.querySelector('.ant-modal input') !== null`)
          await evaluate(`(() => {
            const input = document.querySelector('.ant-modal input')
            input.value = '订单运营'
            input.dispatchEvent(new Event('input', { bubbles: true }))
            input.dispatchEvent(new Event('change', { bubbles: true }))
          })()`)
          await delay(150)
          await evaluate(`document.querySelector('.ant-modal-footer .ant-btn-primary').click()`)
          await waitFor(`[...document.querySelectorAll('.module-group')]
            .some(item => item.textContent.includes('订单运营') && item.textContent.includes('订单记录'))`)
        }
      }
      const moduleReadback = await evaluate(`(async () => {
        const token = JSON.parse(sessionStorage.getItem('unexamine.system.tokens')).accessToken
        const response = await fetch('/api/admin/module-config', { headers: { Authorization: 'Bearer ' + token } })
        const result = await response.json()
        const drafts = await Promise.all((result.data?.modules || []).map(async item => {
          const response = await fetch('/api/admin/module-config/modules/' + item.id + '/draft', { headers: { Authorization: 'Bearer ' + token } })
          const result = await response.json()
          return { id: item.id, name: item.name, fields: result.data?.fields?.map(field => field.name),
            draftRevision: result.data?.module?.draftRevision }
        }))
        return { apiGroups: result.data?.groups?.map(item => item.id),
          apiModules: drafts,
          visibleGroups: [...document.querySelectorAll('.module-group')].map(item => item.textContent.trim()),
          visibleModules: [...document.querySelectorAll('.module-tree-item')].map(item => item.textContent.trim()),
          designer: document.querySelector('.designer-heading')?.textContent?.trim(),
          error: document.querySelector('.section-alert')?.textContent?.trim() }
      })()`)
      if (process.argv.includes('--order') && !moduleReadback.apiModules.find(item => item.name === '订单记录')?.fields.includes('订单金额')) {
        await evaluate(`(() => {
          const button = [...document.querySelectorAll('.module-tree-item')].find(item => item.textContent.includes('订单记录'))
          if (!button) throw new Error('Order module is missing')
          button.click()
        })()`)
        await waitFor(`document.querySelector('.designer-heading')?.textContent.includes('订单记录')`)
        await evaluate(`(() => {
          const button = [...document.querySelectorAll('.config-task-tabs button')].find(item => item.textContent.includes('模块字段'))
          button.click()
        })()`)
        await waitFor(`document.querySelector('.form-type-button[data-field-type="MONEY"]') !== null`)
        await evaluate(`document.querySelector('.form-type-button[data-field-type="MONEY"]').click()`)
        await waitFor(`document.querySelector('.form-field-properties input[placeholder="例如：客户名称、联系电话、跟进结果"]') !== null`)
        await evaluate(`(() => {
          const input = document.querySelector('.form-field-properties input[placeholder="例如：客户名称、联系电话、跟进结果"]')
          input.value = '订单金额'
          input.dispatchEvent(new Event('input', { bubbles: true }))
          input.dispatchEvent(new Event('change', { bubbles: true }))
        })()`)
        await delay(150)
        await evaluate(`(() => {
          const button = [...document.querySelectorAll('.form-field-properties button')].find(item => item.textContent.includes('保存字段'))
          if (!button || button.disabled) throw new Error('Save order field button is unavailable')
          button.click()
        })()`)
        await waitFor(`document.body.innerText.includes('字段已加入草稿')`)
        const fieldScreenshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
        writeFileSync('projects/unexamine/ai/evidence/s19-order-field-browser.png', Buffer.from(fieldScreenshot.data, 'base64'))
      }
      if (process.argv.includes('--publish-check')) {
        const targetName = process.argv.includes('--order') ? '订单记录' : '客户记录'
        await evaluate(`(() => {
          if (document.querySelector('.designer-heading')?.textContent.includes(${JSON.stringify(targetName)})) return
          const button = [...document.querySelectorAll('.module-tree-item')].find(item => item.textContent.includes(${JSON.stringify(targetName)}))
          if (!button) throw new Error('Target module is missing')
          button.click()
        })()`)
        await waitFor(`document.querySelector('.designer-heading')?.textContent.includes(${JSON.stringify(targetName)})`)
        await evaluate(`(() => {
          const button = [...document.querySelectorAll('.config-task-tabs button')].find(item => item.textContent.includes('模块字段'))
          button.click()
        })()`)
        await waitFor(`document.querySelector('.form-designer-toolbar') !== null`)
        await evaluate(`(() => {
          const button = [...document.querySelectorAll('.form-designer-toolbar button')].find(item => item.textContent.trim() === '保存草稿')
          if (!button) throw new Error('Save form layout button is missing')
          button.click()
        })()`)
        await delay(900)
        await evaluate(`(() => {
          const button = [...document.querySelectorAll('.config-task-tabs button')].find(item => item.textContent.includes('模块信息'))
          button.click()
        })()`)
        await waitFor(`document.querySelector('.designer-subtabs') !== null`)
        await evaluate(`(() => {
          const button = [...document.querySelectorAll('.designer-subtabs button')].find(item => item.textContent.includes('发布与版本'))
          if (!button) throw new Error('Publication tab is missing')
          button.click()
        })()`)
        await waitFor(`document.querySelector('.publish-readiness') !== null`)
        await delay(900)
        const publication = await evaluate(`({readiness:document.querySelector('.publish-readiness')?.textContent.trim(),
          issues:[...document.querySelectorAll('.publication-issues li')].map(item => item.textContent.trim()),
          publishDisabled:document.querySelector('.publish-actions .ant-btn-primary')?.disabled,
          alerts:[...document.querySelectorAll('.ant-alert,.ant-message-notice')].map(item => item.textContent.trim())})`)
        const publicationScreenshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
        writeFileSync(`projects/unexamine/ai/evidence/s19-${process.argv.includes('--order') ? 'order' : 'first'}-module-publication-check.png`, Buffer.from(publicationScreenshot.data, 'base64'))
        console.log(JSON.stringify({ publication }))
        const alreadyPublished = await evaluate(`(async () => {
          const token = JSON.parse(sessionStorage.getItem('unexamine.system.tokens')).accessToken
          const overview = await (await fetch('/api/admin/module-config', { headers: { Authorization: 'Bearer ' + token } })).json()
          const module = overview.data.modules.find(item => item.name === ${JSON.stringify(targetName)})
          const response = await fetch('/api/admin/module-config/modules/' + module.id + '/published', { headers: { Authorization: 'Bearer ' + token } })
          return response.ok
        })()`)
        if (process.argv.includes('--publish') && !publication.publishDisabled && !alreadyPublished) {
          await evaluate(`document.querySelector('.publish-actions .ant-btn-primary').click()`)
          await waitFor(`location.pathname === '/systems/${systems[0].id}'`)
          await delay(650)
          const runtime = await evaluate(`({url:location.href, text:document.body.innerText.slice(-1300)})`)
          const runtimeScreenshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
          writeFileSync(`projects/unexamine/ai/evidence/s19-${process.argv.includes('--order') ? 'order' : 'first'}-module-runtime.png`, Buffer.from(runtimeScreenshot.data, 'base64'))
          console.log(JSON.stringify({ runtime }))
        }
      }
      const screenshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
      writeFileSync('projects/unexamine/ai/evidence/s19-first-module-browser.png', Buffer.from(screenshot.data, 'base64'))
      console.log(JSON.stringify({ systems, module, moduleReadback }))
    } else if (process.argv.includes('--tenant')) {
      const mode = await evaluate(`document.querySelector('input[type="radio"][value="MULTI"]')?.checked || false`)
      if (!mode) {
        await evaluate(`(() => {
          document.querySelector('input[type="radio"][value="MULTI"]')?.closest('label')?.click()
          const button = [...document.querySelectorAll('button')]
            .find(item => item.textContent.trim() === '保存设置')
          if (!button) throw new Error('System settings save button is missing')
          button.click()
        })()`)
        await waitFor(`document.body.innerText.includes('多租户已开启')`)
      }
      await evaluate(`(() => {
        const button = [...document.querySelectorAll('.settings-task-nav button')]
          .find(item => item.textContent.includes('工作空间'))
        if (!button) throw new Error('Workspace task is missing')
        button.click()
      })()`)
      await waitFor(`document.body.innerText.includes('新建工作空间')`)
      const exists = await evaluate(`document.body.innerText.includes('发布验收次空间')`)
      if (!exists) {
        await evaluate(`(() => {
          const button = [...document.querySelectorAll('button')]
            .find(item => item.textContent.includes('新建工作空间'))
          if (!button) throw new Error('New workspace button is missing')
          button.click()
        })()`)
        await waitFor(`document.querySelector('.ant-drawer input[placeholder="east_branch"]') !== null`)
        await evaluate(`(() => {
          const fill = (selector, value) => {
            const input = document.querySelector(selector)
            if (!input) throw new Error('Missing workspace input ' + selector)
            input.value = value
            input.dispatchEvent(new Event('input', { bubbles: true }))
            input.dispatchEvent(new Event('change', { bubbles: true }))
          }
          fill('.ant-drawer input[placeholder="例如：华东分公司"]', '发布验收次空间')
          fill('.ant-drawer input[placeholder="east_branch"]', 'release_branch')
          const button = [...document.querySelectorAll('.ant-drawer button')]
            .find(item => item.textContent.includes('创建工作空间'))
          if (!button) throw new Error('Workspace confirmation is missing')
          button.click()
        })()`)
        await waitFor(`document.body.innerText.includes('工作空间已创建')`)
      }
      const workspace = await evaluate(`({text:document.body.innerText.slice(-1500),
        visible:document.body.innerText.includes('发布验收次空间')})`)
      if (!workspace.visible) throw new Error('Secondary workspace was not visible after creation')
      const screenshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
      writeFileSync('projects/unexamine/ai/evidence/s19-secondary-tenant-browser.png', Buffer.from(screenshot.data, 'base64'))
      console.log(JSON.stringify({ systems, workspace }))
    } else {
      const admin = await evaluate(`({url:location.href, headings:[...document.querySelectorAll('nav button')]
        .map(item => item.textContent.trim()).filter(Boolean), text:document.body.innerText.slice(0, 2500)})`)
      const screenshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
      writeFileSync('projects/unexamine/ai/evidence/s19-system-admin-browser.png', Buffer.from(screenshot.data, 'base64'))
      console.log(JSON.stringify({ systems, admin }))
    }
  } else {
    await send('Page.navigate', { url: frontend + '/platform' })
    await waitFor(`document.body.innerText.includes('发布验收业务甲') && document.body.innerText.includes('发布验收业务乙')`)
    const screenshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
    writeFileSync('projects/unexamine/ai/evidence/s19-two-systems-browser.png', Buffer.from(screenshot.data, 'base64'))
    writeFileSync('projects/unexamine/ai/evidence/s19-two-systems-browser-readback.json', JSON.stringify({
      step: 'S19.1', systems, browser: 'Edge', frontend,
      result: 'Administrator login, two create-system modal submissions, admin entry and both system cards passed',
    }, null, 2) + '\n')
    console.log(JSON.stringify({ systems, screenshot: 's19-two-systems-browser.png' }))
  }
  socket.close()
} finally {
  edge.kill()
}
