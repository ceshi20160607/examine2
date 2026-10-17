// 在同一发布候选上验证管理员生成邀请和新成员通过页面接受邀请。
import { spawn } from 'node:child_process'
import { randomBytes } from 'node:crypto'
import { writeFileSync } from 'node:fs'
import { setTimeout as delay } from 'node:timers/promises'

const adminPassword = process.env.S19_ADMIN_PASSWORD
const userPassword = process.env.S19_USER_PASSWORD
if (!adminPassword || (!process.argv.includes('--verify-fresh-role-runtime')
  && !process.argv.includes('--verify-access-request')
  && !process.argv.includes('--inspect-access-review')
  && !process.argv.includes('--reject-pending-access') && !userPassword)) {
  throw new Error('S19 account passwords are required')
}
const base = process.env.S19_FRONTEND_URL ?? 'http://127.0.0.1:15173'
const debug = 'http://127.0.0.1:19240'
const edge = spawn('C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe', [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--remote-debugging-port=19240', '--remote-allow-origins=*',
  '--user-data-dir=' + process.env.TEMP + '\\unexamine-s19-invitation-browser', 'about:blank',
], { stdio: 'ignore', windowsHide: true })

try {
  let ready = false
  for (let i = 0; i < 60; i++) {
    try { if ((await fetch(debug + '/json/version')).ok) { ready = true; break } }
    catch { /* 等待浏览器调试入口。 */ }
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
    for (let i = 0; i < 80; i++) {
      const value = await evaluate(expression)
      if (value) return value
      await delay(350)
    }
    throw new Error('Browser condition did not arrive: ' + expression)
  }
  const login = async (username, password) => {
    await send('Page.navigate', { url: base + '/login' })
    await waitFor('document.querySelector(\'input[autocomplete="username"]\') !== null')
    await evaluate('(() => { const fill = (selector, value) => { const input = document.querySelector(selector); input.value = value; input.dispatchEvent(new Event("input", {bubbles:true})); input.dispatchEvent(new Event("change", {bubbles:true})) }; fill(\'input[autocomplete="username"]\', ' + JSON.stringify(username) + '); fill(\'input[autocomplete="current-password"]\', ' + JSON.stringify(password) + '); document.querySelector(\'form button[type="submit"]\').click() })()')
    await waitFor('location.pathname.startsWith("/platform")')
  }
  await send('Page.enable')
  await send('Runtime.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 980, deviceScaleFactor: 1, mobile: false })
  if (process.argv.includes('--reject-pending-access')) {
    await login('admin',adminPassword)
    await send('Page.navigate',{url:base+'/platform'})
    await waitFor('[...document.querySelectorAll(".system-row")].some(x=>x.textContent.includes("发布验收业务甲"))')
    await evaluate(`(() => {
      const row=[...document.querySelectorAll('.system-row')].find(x=>x.textContent.includes('发布验收业务甲'))
      row.querySelector('button')?.click()
    })()`)
    await waitFor('location.pathname === "/systems/1"')
    await send('Page.navigate',{url:base+'/systems/1/admin?section=system-info'})
    await waitFor('[...document.querySelectorAll(".settings-task-nav button")].some(x=>x.textContent.includes("访问审批"))')
    await evaluate(`([...document.querySelectorAll('.settings-task-nav button')]
      .find(x=>x.textContent.includes('访问审批'))).click()`)
    await waitFor('document.querySelector(".access-review-card") !== null')
    const pending=await evaluate(`(async () => {
      const token=JSON.parse(sessionStorage.getItem('unexamine.system.tokens')||'{}').accessToken
      const response=await fetch('/api/admin/system/access-requests?status=PENDING',
        {headers:{Authorization:'Bearer '+token}})
      return (await response.json()).data.filter(x=>x.accountDisplayName?.startsWith('s19_access_'))
        .map(x=>({id:x.id,name:x.accountDisplayName,status:x.status}))
    })()`)
    if(pending.length<1) throw new Error('No interrupted access test request remains pending')
    for(const request of pending) {
      await evaluate(`(() => {
        const row=[...document.querySelectorAll('.access-review-row')]
          .find(x=>x.textContent.includes(${JSON.stringify(request.name)}))
        if(!row) throw new Error('Pending access row missing')
        const comment=row.querySelector('input[placeholder="处理说明；拒绝时必填"]')
        if(!comment) throw new Error('Access decision reason input missing')
        comment.value='S19 验收脚本中断申请，关闭待办'
        comment.dispatchEvent(new Event('input',{bubbles:true}))
        const reject=[...row.querySelectorAll('button')]
          .find(x=>x.textContent.replaceAll(' ','').includes('拒绝'))
        if(!reject) throw new Error('Access reject button missing')
        reject.click()
      })()`)
      await waitFor(`[...document.querySelectorAll('.access-review-row')]
        .every(x=>!x.textContent.includes(${JSON.stringify(request.name)}))`)
    }
    const rejected=await evaluate(`(async () => {
      const token=JSON.parse(sessionStorage.getItem('unexamine.system.tokens')||'{}').accessToken
      const response=await fetch('/api/admin/system/access-requests?status=REJECTED',
        {headers:{Authorization:'Bearer '+token}})
      return (await response.json()).data.filter(x=>${JSON.stringify(pending.map(y=>y.id))}.includes(x.id))
        .map(x=>({id:x.id,status:x.status,comment:x.decisionComment}))
    })()`)
    if(rejected.length!==pending.length || rejected.some(x=>x.status!=='REJECTED' || !x.comment)) {
      throw new Error('Access request rejection readback mismatch: '+JSON.stringify({pending,rejected}))
    }
    const evidence={step:'S19.11',candidate:process.env.S19_CANDIDATE_VERSION,
      status:'interrupted_test_access_requests_rejected_by_admin_page',pending,rejected}
    writeFileSync('projects/unexamine/ai/evidence/s19-access-rejection-browser.json',JSON.stringify(evidence,null,2)+'\n')
    console.log(JSON.stringify(evidence))
    socket.close()
  } else if (process.argv.includes('--inspect-access-review')) {
    await login('admin',adminPassword)
    await send('Page.navigate',{url:base+'/platform'})
    await waitFor('[...document.querySelectorAll(".system-row")].some(x=>x.textContent.includes("发布验收业务甲"))')
    await evaluate(`(() => {
      const row=[...document.querySelectorAll('.system-row')].find(x=>x.textContent.includes('发布验收业务甲'))
      row.querySelector('button')?.click()
    })()`)
    await waitFor('location.pathname === "/systems/1"')
    await send('Page.navigate',{url:base+'/systems/1/admin?section=system-info'})
    await waitFor('[...document.querySelectorAll(".settings-task-nav button")].some(x=>x.textContent.includes("访问审批"))')
    await evaluate(`([...document.querySelectorAll('.settings-task-nav button')]
      .find(x=>x.textContent.includes('访问审批'))).click()`)
    await waitFor('document.querySelector(".access-review-card") !== null')
    const rows=await evaluate(`([...document.querySelectorAll('.access-review-row')]
      .map(x=>({text:x.innerText.slice(0,230),buttons:[...x.querySelectorAll('button')]
        .map(y=>({text:y.textContent.trim(),disabled:y.disabled}))})))`)
    console.log(JSON.stringify(rows))
    socket.close()
  } else if (process.argv.includes('--verify-access-request')) {
    const username='s19_access_'+Date.now()
    const password=randomBytes(18).toString('base64url')+'Aa1!'
    await send('Page.navigate',{url:base+'/login'})
    await waitFor('document.querySelector(\'input[autocomplete="username"]\') !== null')
    const registration=await evaluate(`(async () => {
      const response=await fetch('/api/auth/register',{method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify({username:${JSON.stringify(username)},password:${JSON.stringify(password)},
          displayName:${JSON.stringify(username)},email:${JSON.stringify(username+'@example.test')}})})
      return {status:response.status,code:(await response.json()).code}
    })()`)
    if(registration.status!==200) throw new Error('Access applicant registration failed: '+JSON.stringify(registration))
    await login(username,password)
    await send('Page.navigate',{url:base+'/platform'})
    await waitFor('[...document.querySelectorAll(".access-directory__row")].some(x=>x.textContent.includes("发布验收业务甲"))')
    const before=await evaluate(`(async () => {
      const token=JSON.parse(sessionStorage.getItem('unexamine.platform.tokens')||'{}').accessToken
      const headers={Authorization:'Bearer '+token}
      const systems=await fetch('/api/systems',{headers})
      const enter=await fetch('/api/systems/1/enter',{method:'POST',
        headers:{...headers,'Content-Type':'application/json'},body:'{}'})
      const enterResult=await enter.json()
      return {accessibleSystemIds:(await systems.json()).data.map(x=>x.systemId),
        targetVisible:[...document.querySelectorAll('.access-directory__row')]
          .some(x=>x.textContent.includes('发布验收业务甲')),
        directEnterStatus:enter.status,directEnterCode:enterResult.code}
    })()`)
    if(before.accessibleSystemIds.includes(1) || !before.targetVisible || before.directEnterStatus!==403) {
      throw new Error('Unapproved applicant system boundary failed: '+JSON.stringify(before))
    }
    await evaluate(`(() => {
      const row=[...document.querySelectorAll('.access-directory__row')]
        .find(x=>x.textContent.includes('发布验收业务甲'))
      const button=[...row.querySelectorAll('button')].find(x=>x.textContent.includes('申请访问'))
      if(!button) throw new Error('Access request button missing')
      button.click()
    })()`)
    await waitFor('[...document.querySelectorAll(".ant-modal-title")].some(x=>x.textContent.includes("申请系统访问"))')
    await evaluate(`(() => {
      const modal=[...document.querySelectorAll('.ant-modal')]
        .find(x=>x.querySelector('.ant-modal-title')?.textContent.includes('申请系统访问'))
      const reason=modal?.querySelector('textarea')
      if(!reason) throw new Error('Access request reason missing')
      reason.value='S19 当前候选主页面申请访问验证'
      reason.dispatchEvent(new Event('input',{bubbles:true}))
      modal.querySelector('.ant-modal-footer .ant-btn-primary')?.click()
    })()`)
    await waitFor(`[...document.querySelectorAll('.access-directory__row')]
      .some(x=>x.textContent.includes('发布验收业务甲') && x.textContent.includes('待处理'))`)
    const pending=await evaluate(`(async () => {
      const token=JSON.parse(sessionStorage.getItem('unexamine.platform.tokens')||'{}').accessToken
      const response=await fetch('/api/systems/access-requests/mine',
        {headers:{Authorization:'Bearer '+token}})
      const request=(await response.json()).data.find(x=>x.systemId===1)
      return {status:response.status,id:request?.id,requestStatus:request?.status}
    })()`)
    if(pending.status!==200 || pending.requestStatus!=='PENDING') {
      throw new Error('Access request not pending: '+JSON.stringify(pending))
    }
    await evaluate('sessionStorage.clear()')
    await login('admin',adminPassword)
    await send('Page.navigate',{url:base+'/platform'})
    await waitFor('[...document.querySelectorAll(".system-row")].some(x=>x.textContent.includes("发布验收业务甲"))')
    await evaluate(`(() => {
      const row=[...document.querySelectorAll('.system-row')].find(x=>x.textContent.includes('发布验收业务甲'))
      row.querySelector('button')?.click()
    })()`)
    await waitFor('location.pathname === "/systems/1"')
    await send('Page.navigate',{url:base+'/systems/1/admin?section=system-info'})
    await waitFor('[...document.querySelectorAll(".settings-task-nav button")].some(x=>x.textContent.includes("访问审批"))')
    await evaluate(`([...document.querySelectorAll('.settings-task-nav button')]
      .find(x=>x.textContent.includes('访问审批'))).click()`)
    await waitFor(`[...document.querySelectorAll('.access-review-row')]
      .some(x=>x.textContent.includes(${JSON.stringify(username)}))`)
    await evaluate(`(() => {
      const row=[...document.querySelectorAll('.access-review-row')]
        .find(x=>x.textContent.includes(${JSON.stringify(username)}))
      const selector=row?.querySelector('.ant-select-selector')
      if(!selector) throw new Error('Access approval role selector missing')
      selector.dispatchEvent(new MouseEvent('mousedown',{bubbles:true,cancelable:true}))
      selector.click()
    })()`)
    await waitFor('[...document.querySelectorAll(".ant-select-item-option")].some(x=>x.textContent.includes("S19 页面验收消息角色"))')
    await evaluate(`(() => {
      const option=[...document.querySelectorAll('.ant-select-item-option')]
        .find(x=>x.textContent.includes('S19 页面验收消息角色'))
      option.click()
      const row=[...document.querySelectorAll('.access-review-row')]
        .find(x=>x.textContent.includes(${JSON.stringify(username)}))
      const approve=[...row.querySelectorAll('button')]
        .find(x=>x.textContent.replaceAll(' ','').includes('批准'))
      if(!approve) throw new Error('Access approve button missing')
      approve.click()
    })()`)
    await waitFor(`[...document.querySelectorAll('.access-review-row')]
      .every(x=>!x.textContent.includes(${JSON.stringify(username)}))`)
    const approved=await evaluate(`(async () => {
      const token=JSON.parse(sessionStorage.getItem('unexamine.system.tokens')||'{}').accessToken
      const response=await fetch('/api/admin/system/access-requests?status=APPROVED',
        {headers:{Authorization:'Bearer '+token}})
      const item=(await response.json()).data.find(x=>x.id===${pending.id})
      return {status:response.status,id:item?.id,requestStatus:item?.status}
    })()`)
    if(approved.status!==200 || approved.requestStatus!=='APPROVED') {
      throw new Error('Access approval readback failed: '+JSON.stringify(approved))
    }
    await evaluate('sessionStorage.clear()')
    await login(username,password)
    await send('Page.navigate',{url:base+'/platform'})
    await waitFor('[...document.querySelectorAll(".system-row")].some(x=>x.textContent.includes("发布验收业务甲"))')
    await evaluate(`(() => {
      const row=[...document.querySelectorAll('.system-row')].find(x=>x.textContent.includes('发布验收业务甲'))
      const enter=[...row.querySelectorAll('button')].find(x=>x.textContent.includes('进入'))
      if(!enter) throw new Error('Approved system entry missing')
      enter.click()
    })()`)
    await waitFor('location.pathname === "/systems/1"')
    const after=await evaluate(`(async () => {
      const token=JSON.parse(sessionStorage.getItem('unexamine.system.tokens')||'{}').accessToken
      const response=await fetch('/api/messages?status=ACTIVE&sourceType=ALL&page=1&pageSize=10',
        {headers:{Authorization:'Bearer '+token}})
      return {path:location.pathname,messageStatus:response.status}
    })()`)
    if(after.messageStatus!==200) throw new Error('Approved account grant not effective: '+JSON.stringify(after))
    const evidence={step:'S19.11',candidate:process.env.S19_CANDIDATE_VERSION,
      status:'platform_access_request_admin_approval_member_entry',username,
      registration,before,pending,approved,after,
      credentialNote:'Generated test password stayed in process memory and was not persisted.'}
    writeFileSync('projects/unexamine/ai/evidence/s19-access-request-browser.json',JSON.stringify(evidence,null,2)+'\n')
    console.log(JSON.stringify(evidence))
    socket.close()
  } else if (process.argv.includes('--verify-fresh-role-runtime')) {
    const username = 's19_role_' + Date.now()
    const password = randomBytes(18).toString('base64url') + 'Aa1!'
    const roleName = 'S19 页面验收消息角色'
    await send('Page.navigate',{url:base+'/login'})
    await waitFor('document.querySelector(\'input[autocomplete="username"]\') !== null')
    const registration=await evaluate(`(async () => {
      const response=await fetch('/api/auth/register',{method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify({username:${JSON.stringify(username)},password:${JSON.stringify(password)},
          displayName:${JSON.stringify(username)},email:${JSON.stringify(username+'@example.test')}})})
      const result=await response.json()
      return {status:response.status,code:result.code}
    })()`)
    if(registration.status!==200) throw new Error('Fresh invited account registration failed: '+JSON.stringify(registration))
    await login('admin', adminPassword)
    await send('Page.navigate', {url:base+'/platform'})
    await waitFor('[...document.querySelectorAll(".system-row")].some(x=>x.textContent.includes("发布验收业务甲"))')
    await evaluate(`(() => {
      const row=[...document.querySelectorAll('.system-row')].find(x=>x.textContent.includes('发布验收业务甲'))
      const enter=[...row.querySelectorAll('button')].find(x=>x.textContent.includes('进入'))
      if(!enter) throw new Error('Source system entry missing')
      enter.click()
    })()`)
    await waitFor('location.pathname === "/systems/1"')
    await send('Page.navigate',{url:base+'/systems/1/admin?section=organization'})
    await waitFor('document.body.innerText.includes("邀请成员")')
    await evaluate('([...document.querySelectorAll("button")].find(x=>x.textContent.includes("邀请成员"))).click()')
    await waitFor('document.querySelector(".ant-modal")?.textContent.includes("生成邀请链接")')
    await evaluate(`(() => {
      const input=document.querySelector('.ant-modal input[placeholder="用户名、邮箱或手机号；留空允许新用户注册后接受"]')
      if(!input) throw new Error('Invitation target input missing')
      input.value=${JSON.stringify(username)}
      input.dispatchEvent(new Event('input',{bubbles:true}))
      input.dispatchEvent(new Event('change',{bubbles:true}))
      const item=[...document.querySelectorAll('.ant-modal .ant-form-item')]
        .find(x=>x.querySelector('.ant-form-item-label')?.textContent.includes('角色'))
      const selector=item?.querySelector('.ant-select-selector')
      if(!selector) throw new Error('Invitation role selector missing')
      selector.dispatchEvent(new MouseEvent('mousedown',{bubbles:true,cancelable:true}))
      selector.click()
    })()`)
    await waitFor(`[...document.querySelectorAll('.ant-select-item-option')]
      .some(x=>x.textContent.includes(${JSON.stringify(roleName)}))`)
    await evaluate(`(() => {
      const option=[...document.querySelectorAll('.ant-select-item-option')]
        .find(x=>x.textContent.includes(${JSON.stringify(roleName)}))
      option.click()
      const generate=[...document.querySelectorAll('.ant-modal button')]
        .find(x=>x.textContent.includes('生成邀请链接'))
      if(!generate) throw new Error('Generate invitation button missing')
      generate.click()
    })()`)
    await delay(500)
    const invitationForm=await evaluate(`({errors:[...document.querySelectorAll('.ant-modal .ant-alert-error')]
      .map(x=>x.textContent.trim()), role:[...document.querySelectorAll('.ant-modal .ant-form-item')]
      .find(x=>x.querySelector('.ant-form-item-label')?.textContent.includes('角色'))?.innerText,
      department:document.querySelector('.ant-modal .department-select')?.textContent,
      buttonDisabled:[...document.querySelectorAll('.ant-modal button')]
      .find(x=>x.textContent.includes('生成邀请链接'))?.disabled})`)
    if(invitationForm.errors.length) throw new Error('Invitation form rejected: '+JSON.stringify(invitationForm))
    const invitationLink=await waitFor('document.querySelector(".invitation-link input")?.value || null')
    if(!invitationLink.startsWith(base+'/invitations/')) throw new Error('Unexpected invitation origin')
    await evaluate('sessionStorage.clear()')
    await login(username,password)
    await send('Page.navigate',{url:invitationLink})
    await waitFor('document.querySelector(".invitation-card")?.textContent.includes("发布验收业务甲")')
    await evaluate('([...document.querySelectorAll(".invitation-card button")].find(x=>x.textContent.includes("接受邀请"))).click()')
    await waitFor('document.querySelector(".invitation-card h1")?.textContent.includes("已加入系统")')
    await evaluate('([...document.querySelectorAll(".invitation-card button")].find(x=>x.textContent.includes("进入系统"))).click()')
    await waitFor('location.pathname === "/systems/1"')
    const checkMember=async expected => {
      await send('Page.navigate',{url:base+'/systems/1?workspace=messages'})
      if(expected==='allowed') {
        await waitFor('document.querySelector(".message-filter-bar") !== null || document.body.innerText.includes("当前工作范围没有消息查看权限")')
      } else {
        await delay(900)
      }
      const result=await evaluate(`(async () => {
        const token=JSON.parse(sessionStorage.getItem('unexamine.system.tokens')||'{}').accessToken
        const response=await fetch('/api/messages?status=ACTIVE&sourceType=ALL&page=1&pageSize=10',
          {headers:{Authorization:'Bearer '+token}})
        const data=response.ok?(await response.json()).data:null
        return {path:location.pathname,workspace:new URLSearchParams(location.search).get('workspace'),
          denied:document.body.innerText.includes('当前工作范围没有消息查看权限'),
          filterVisible:document.querySelector('.message-filter-bar')!==null,
          status:response.status,total:data?.total??null}
      })()`)
      if(expected==='allowed' && (result.denied || !result.filterVisible || result.status!==200)) {
        throw new Error('Fresh member message grant did not take effect: '+JSON.stringify(result))
      }
      if(expected==='denied' && (result.filterVisible || result.status!==403)) {
        throw new Error('Fresh member message revocation did not take effect: '+JSON.stringify(result))
      }
      return result
    }
    const granted=await checkMember('allowed')
    const enterSystem=async () => {
      await send('Page.navigate',{url:base+'/platform'})
      await waitFor('[...document.querySelectorAll(".system-row")].some(x=>x.textContent.includes("发布验收业务甲"))')
      await evaluate(`(() => {
        const row=[...document.querySelectorAll('.system-row')].find(x=>x.textContent.includes('发布验收业务甲'))
        const enter=[...row.querySelectorAll('button')].find(x=>x.textContent.includes('进入'))
        if(!enter) throw new Error('Fresh role system entry missing')
        enter.click()
      })()`)
      await waitFor('location.pathname === "/systems/1"')
    }
    const administerRole=async assign => {
      await evaluate('sessionStorage.clear()')
      await login('admin',adminPassword)
      await enterSystem()
      await send('Page.navigate',{url:base+'/systems/1/admin?section=organization'})
      await waitFor(`[...document.querySelectorAll('.authorization-member-row')]
        .some(x=>x.textContent.includes(${JSON.stringify(username)}))`)
      const before=await evaluate(`(async () => {
        const token=JSON.parse(sessionStorage.getItem('unexamine.system.tokens')||'{}').accessToken
        const response=await fetch('/api/admin/system/authorization',{headers:{Authorization:'Bearer '+token}})
        const overview=(await response.json()).data
        const member=overview.members.find(x=>x.account===${JSON.stringify(username)}
          || x.displayName===${JSON.stringify(username)})
        const role=overview.roles.find(x=>x.name===${JSON.stringify(roleName)})
        return {roleId:role?.id,memberId:member?.tenantMemberId,
          roleIds:member?.roleIds,permissionVersion:overview.permissionVersion}
      })()`)
      if(!before.memberId || !before.roleId) throw new Error('Fresh role member assignment missing: '+JSON.stringify(before))
      if(assign) {
        await evaluate(`(() => {
          const row=[...document.querySelectorAll('.authorization-member-row')]
            .find(x=>x.textContent.includes(${JSON.stringify(username)}))
          const field=[...row.querySelectorAll('.authorization-member-field')]
            .find(x=>x.textContent.includes('角色'))
          const selector=field?.querySelector('.ant-select-selector')
          if(!selector) throw new Error('Fresh role selector missing')
          selector.dispatchEvent(new MouseEvent('mousedown',{bubbles:true,cancelable:true}))
          selector.click()
        })()`)
        await waitFor(`[...document.querySelectorAll('.ant-select-item-option')]
          .some(x=>x.textContent.includes(${JSON.stringify(roleName)}))`)
        await evaluate(`(() => {
          const option=[...document.querySelectorAll('.ant-select-item-option')]
            .find(x=>x.textContent.includes(${JSON.stringify(roleName)}))
          option.click()
        })()`)
      } else {
        await evaluate(`(() => {
          const row=[...document.querySelectorAll('.authorization-member-row')]
            .find(x=>x.textContent.includes(${JSON.stringify(username)}))
          const field=[...row.querySelectorAll('.authorization-member-field')]
            .find(x=>x.textContent.includes('角色'))
          const chip=[...field.querySelectorAll('.ant-select-selection-item')]
            .find(x=>x.textContent.includes(${JSON.stringify(roleName)}))
          const remove=chip?.querySelector('.ant-select-selection-item-remove')
          if(!remove) throw new Error('Fresh role remove control missing')
          remove.click()
        })()`)
      }
      await evaluate(`(() => {
        const row=[...document.querySelectorAll('.authorization-member-row')]
          .find(x=>x.textContent.includes(${JSON.stringify(username)}))
        const save=row?.querySelector('.authorization-member-save')
        if(!save || save.disabled) throw new Error('Fresh role save control missing')
        save.click()
      })()`)
      const after=await waitFor(`(async () => {
        const token=JSON.parse(sessionStorage.getItem('unexamine.system.tokens')||'{}').accessToken
        const response=await fetch('/api/admin/system/authorization',{headers:{Authorization:'Bearer '+token}})
        const overview=(await response.json()).data
        const member=overview.members.find(x=>x.account===${JSON.stringify(username)}
          || x.displayName===${JSON.stringify(username)})
        const hasRole=member?.roleIds.includes(${before.roleId})
        return overview.permissionVersion>${before.permissionVersion} && hasRole===${assign}
          ? {roleIds:member.roleIds,permissionVersion:overview.permissionVersion} : null
      })()`)
      return {before,after}
    }
    const removed=await administerRole(false)
    await evaluate('sessionStorage.clear()')
    await login(username,password)
    await enterSystem()
    const denied=await checkMember('denied')
    const restored=await administerRole(true)
    await evaluate('sessionStorage.clear()')
    await login(username,password)
    await enterSystem()
    const allowedAgain=await checkMember('allowed')
    const evidence={step:'S19.11',candidate:process.env.S19_CANDIDATE_VERSION,
      status:'fresh_invited_member_role_revoke_restore_runtime',username,registration,
      granted,removed,denied,restored,allowedAgain,
      credentialNote:'Generated test password stayed in process memory and was not persisted.'}
    writeFileSync('projects/unexamine/ai/evidence/s19-fresh-role-runtime-browser.json',JSON.stringify(evidence,null,2)+'\n')
    console.log(JSON.stringify(evidence))
    socket.close()
  } else if (process.argv.includes('--verify-invitee-role-runtime')) {
    await login('s19_invitee', userPassword)
    await send('Page.navigate', { url: base + '/platform' })
    await waitFor('[...document.querySelectorAll(".system-row")].some(x=>x.textContent.includes("发布验收业务甲"))')
    await evaluate('(() => { const row=[...document.querySelectorAll(".system-row")].find(x=>x.textContent.includes("发布验收业务甲")); const enter=[...row.querySelectorAll("button")].find(x=>x.textContent.includes("进入")); if(!enter) throw new Error("Invited system entry missing"); enter.click() })()')
    await waitFor('location.pathname === "/systems/1"')
    await send('Page.navigate', { url: base + '/systems/1?workspace=messages' })
    await waitFor('document.querySelector(".message-filter-bar") !== null || document.body.innerText.includes("当前工作范围没有消息查看权限")')
    const runtime = await evaluate(`(async () => {
      const token = JSON.parse(sessionStorage.getItem('unexamine.system.tokens') || '{}').accessToken
      const response = await fetch('/api/messages?status=ACTIVE&sourceType=ALL&page=1&pageSize=10',
        {headers:{Authorization:'Bearer '+token}})
      const body = response.ok ? (await response.json()).data : null
      return {path:location.pathname, denied:document.body.innerText.includes('当前工作范围没有消息查看权限'),
        filterVisible:document.querySelector('.message-filter-bar')!==null,
        inboxStatus:response.status, total:body?.total ?? null}
    })()`)
    if (runtime.path!=='/systems/1' || runtime.denied || !runtime.filterVisible || runtime.inboxStatus!==200) {
      throw new Error('Fresh invited-member session did not receive the restored message grant')
    }
    const receipt = {step:'S19.11', candidate:process.env.S19_CANDIDATE_VERSION,
      status:'fresh_invited_member_message_grant_effective', runtime}
    writeFileSync('projects/unexamine/ai/evidence/s19-invitee-role-runtime-browser.json', JSON.stringify(receipt,null,2)+'\n')
    console.log(JSON.stringify(receipt))
    socket.close()
  } else {
  await login('admin', adminPassword)
  await send('Page.navigate', { url: base + '/platform' })
  await waitFor('[...document.querySelectorAll(".system-row")].some(x => x.textContent.includes("发布验收业务甲"))')
  await evaluate('(() => { const row = [...document.querySelectorAll(".system-row")].find(x => x.textContent.includes("发布验收业务甲")); const button = [...row.querySelectorAll("button")].find(x => x.textContent.includes("进入")); if (!button) throw new Error("System entry missing"); button.click() })()')
  await waitFor('location.pathname === "/systems/1"')
  await send('Page.navigate', { url: base + '/systems/1/admin?section=organization' })
  await waitFor('document.body.innerText.includes("邀请成员")')
  await evaluate('([...document.querySelectorAll("button")].find(x => x.textContent.includes("邀请成员"))).click()')
  await waitFor('document.querySelector(".ant-modal")?.textContent.includes("生成邀请链接")')
  await evaluate('(() => { const input = document.querySelector(\'.ant-modal input[placeholder="用户名、邮箱或手机号；留空允许新用户注册后接受"]\'); if (!input) throw new Error("Invitation target account input missing"); input.value = "s19_invitee"; input.dispatchEvent(new Event("input", {bubbles:true})); input.dispatchEvent(new Event("change", {bubbles:true})); [...document.querySelectorAll(".ant-modal button")].find(x => x.textContent.includes("生成邀请链接")).click() })()')
  await delay(500)
  const formDebug = await evaluate('({errors:[...document.querySelectorAll(".ant-modal .ant-alert-error")].map(x=>x.textContent), department:document.querySelector(".ant-modal .department-select")?.textContent, buttonDisabled:[...document.querySelectorAll(".ant-modal button")].find(x=>x.textContent.includes("生成邀请链接"))?.disabled})')
  if (formDebug.errors.length) throw new Error('Invitation form rejected: ' + JSON.stringify(formDebug))
  const invitationLink = await waitFor('document.querySelector(".invitation-link input")?.value || null')
  if (!invitationLink.startsWith(base + '/invitations/')) throw new Error('Invitation link has an unexpected origin')
  const adminView = await evaluate('({url:location.href, invitationVisible:document.querySelector(".invitation-link") !== null, targetVisible:document.querySelector(".invitation-history")?.textContent.includes("s19_invitee")})')
  if (!adminView.invitationVisible || !adminView.targetVisible) throw new Error('Admin invitation page did not show the created invitation')
  const adminImage = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  writeFileSync('projects/unexamine/ai/evidence/s19-invitation-admin-browser.png', Buffer.from(adminImage.data, 'base64'))

  await evaluate('sessionStorage.clear()')
  await login('s19_invitee', userPassword)
  await send('Page.navigate', { url: invitationLink })
  await waitFor('document.querySelector(".invitation-card")?.textContent.includes("发布验收业务甲")')
  const preview = await evaluate('({invitationRoute:location.pathname.startsWith("/invitations/"), systemVisible:document.querySelector(".invitation-card")?.textContent.includes("发布验收业务甲"), accountVisible:document.querySelector(".invitation-card")?.textContent.includes("s19_invitee")})')
  if (!preview.systemVisible || !preview.accountVisible) throw new Error('Invitee preview is incomplete')
  await evaluate('([...document.querySelectorAll(".invitation-card button")].find(x => x.textContent.includes("接受邀请"))).click()')
  await waitFor('document.querySelector(".invitation-card h1")?.textContent.includes("已加入系统")')
  await evaluate('([...document.querySelectorAll(".invitation-card button")].find(x => x.textContent.includes("进入系统"))).click()')
  await waitFor('location.pathname === "/systems/1"')
  const accepted = await evaluate('({url:location.href, systemVisible:document.body.innerText.includes("发布验收业务甲")})')
  if (!accepted.systemVisible) throw new Error('Invitee could not enter the invited system')
  const acceptedImage = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  writeFileSync('projects/unexamine/ai/evidence/s19-invitation-accepted-browser.png', Buffer.from(acceptedImage.data, 'base64'))
  const evidence = { status: 'admin_created_invitee_accepted_and_entered', adminView, preview, accepted }
  writeFileSync('projects/unexamine/ai/evidence/s19-invitation-browser.json', JSON.stringify(evidence, null, 2) + '\n')
  console.log(JSON.stringify(evidence))
  socket.close()
  }
} finally {
  edge.kill()
}
