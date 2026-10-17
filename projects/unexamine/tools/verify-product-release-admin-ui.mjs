// 在隔离候选环境中通过管理员页面验证发布配置、授权、数据处理和运行态读回。
import { spawn } from 'node:child_process'
import { randomBytes } from 'node:crypto'
import { writeFileSync } from 'node:fs'
import { setTimeout as delay } from 'node:timers/promises'

const password = process.env.S19_ADMIN_PASSWORD
if (!password) throw new Error('S19_ADMIN_PASSWORD is required')
const base = process.env.S19_FRONTEND_URL ?? 'http://127.0.0.1:15173'
const debug = 'http://127.0.0.1:19241'
const edge = spawn('C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe', [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--remote-debugging-port=19241', '--remote-allow-origins=*',
  '--user-data-dir=' + process.env.TEMP + '\\unexamine-s19-admin-ui-browser', 'about:blank',
], { stdio: 'ignore', windowsHide: true })

try {
  for (let i = 0; i < 60; i++) {
    try { if ((await fetch(debug + '/json/version')).ok) break }
    catch { /* 等待 Edge 调试入口。 */ }
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
    for (let i = 0; i < 80; i++) {
      const value = await evaluate(expression)
      if (value) return value
      await delay(350)
    }
    throw new Error('Browser condition did not arrive: ' + expression)
  }
  const click = async (selector, label) => evaluate(`(() => {
    const button = [...document.querySelectorAll(${JSON.stringify(selector)})]
      .find(item => item.textContent.includes(${JSON.stringify(label)}))
    if (!button || button.disabled) throw new Error('Missing enabled button: ' + ${JSON.stringify(label)})
    button.click()
  })()`)
  const fill = async (selector, value) => evaluate(`(() => {
    const input = document.querySelector(${JSON.stringify(selector)})
    if (!input) throw new Error('Missing input: ' + ${JSON.stringify(selector)})
    input.value = ${JSON.stringify(value)}
    input.dispatchEvent(new Event('input', { bubbles: true }))
    input.dispatchEvent(new Event('change', { bubbles: true }))
  })()`)
  await send('Page.enable')
  await send('Runtime.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 980, deviceScaleFactor: 1, mobile: false })
  await send('Page.navigate', { url: base + '/login' })
  await waitFor('document.querySelector(\'input[autocomplete="username"]\') !== null')
  await fill('input[autocomplete="username"]', 'admin')
  await fill('input[autocomplete="current-password"]', password)
  await click('form button[type="submit"]', '')
  await waitFor('location.pathname.startsWith("/platform")')
  await send('Page.navigate', { url: base + '/platform' })
  await waitFor('[...document.querySelectorAll(".system-row")].some(x => x.textContent.includes("发布验收业务甲"))')
  await evaluate(`(() => {
    const row = [...document.querySelectorAll('.system-row')].find(x => x.textContent.includes('发布验收业务甲'))
    const button = [...row.querySelectorAll('button')].find(x => x.textContent.includes('进入'))
    if (!button) throw new Error('System entry missing')
    button.click()
  })()`)
  await waitFor('location.pathname === "/systems/1"')
  if (process.argv.includes('--verify-platform-authorization')) {
    const systemToken=await evaluate(`JSON.parse(sessionStorage.getItem('unexamine.system.tokens')||'{}').accessToken`)
    const adminToken=await evaluate(`JSON.parse(sessionStorage.getItem('unexamine.platform.tokens')||'{}').accessToken`)
    const username='s19_role_'+Date.now()
    const userPassword=randomBytes(18).toString('base64url')+'Aa1!'
    const registration=await evaluate(`(async()=>{
      const response=await fetch('/api/auth/register',{method:'POST',headers:{'Content-Type':'application/json'},
        body:JSON.stringify({username:${JSON.stringify(username)},password:${JSON.stringify(userPassword)},
          displayName:${JSON.stringify(username)},email:${JSON.stringify(username+'@example.test')}})})
      return {status:response.status,code:(await response.json()).code}
    })()`)
    if(registration.status!==200) throw new Error('Role test account registration failed: '+JSON.stringify(registration))
    await send('Page.navigate',{url:base+'/platform/admin/configurations?category=AUTHORIZATION'})
    await waitFor('document.querySelector(".platform-authorization-page .ant-table") !== null')
    if(await evaluate('!!document.querySelector(".fixed-setting-editor")')) throw new Error('Role page still uses generic key/value editor')
    const roleName='S19 平台角色 '+Date.now()
    await click('.platform-authorization-page button','新建角色')
    await waitFor('[...document.querySelectorAll(".ant-modal")].some(x=>x.getBoundingClientRect().height>0 && x.querySelector(".ant-modal-title")?.textContent.includes("新建平台角色"))')
    await fill('.ant-modal input[maxlength="200"]',roleName)
    await evaluate(`(() => {
      const modal=[...document.querySelectorAll('.ant-modal')].find(x=>x.getBoundingClientRect().height>0 && x.querySelector('.ant-modal-title')?.textContent.includes('新建平台角色'))
      modal.querySelector('.ant-modal-footer .ant-btn-primary').click()
    })()`)
    await waitFor(`[...document.querySelectorAll('.platform-authorization-page .ant-table')][0]?.textContent.includes(${JSON.stringify(roleName)})`)
    const readOverview=()=>evaluate(`(async()=>{
      const response=await fetch('/api/admin/platform/authorization',{headers:{Authorization:'Bearer '+${JSON.stringify(adminToken)}}})
      return {status:response.status,data:(await response.json()).data}
    })()`)
    let overview=await readOverview()
    let role=overview.data.roles.find(item=>item.name===roleName)
    let member=overview.data.members.find(item=>item.username===username)
    if(!role || !member || role.grants.length) throw new Error('Created role/member mismatch: '+JSON.stringify({role,member}))
    await evaluate(`(() => {
      const row=[...document.querySelectorAll('.platform-authorization-page .ant-table')][0]
        .querySelectorAll('tbody tr')
      const target=[...row].find(x=>x.textContent.includes(${JSON.stringify(roleName)}))
      ;[...target.querySelectorAll('button')].find(x=>x.textContent.includes('配置权限')).click()
    })()`)
    await waitFor('[...document.querySelectorAll(".ant-modal")].some(x=>x.getBoundingClientRect().height>0 && x.querySelector(".ant-modal-title")?.textContent.includes("配置"))')
    await evaluate(`(() => {
      const modal=[...document.querySelectorAll('.ant-modal')].find(x=>x.getBoundingClientRect().height>0 && x.querySelector('.ant-modal-title')?.textContent.includes('配置'))
      const choice=[...modal.querySelectorAll('.ant-checkbox-wrapper')].find(x=>x.textContent.includes('查看平台角色'))
      if(!choice) throw new Error('Missing role view capability')
      choice.click()
    })()`)
    await waitFor('[...document.querySelectorAll(".ant-modal .ant-checkbox-wrapper")].some(x=>x.textContent.includes("查看平台角色") && x.querySelector(".ant-checkbox-checked"))')
    await evaluate(`(() => {
      const modal=[...document.querySelectorAll('.ant-modal')].find(x=>x.getBoundingClientRect().height>0 && x.querySelector('.ant-modal-title')?.textContent.includes('配置'))
      const choice=[...modal.querySelectorAll('.ant-checkbox-wrapper')].find(x=>x.textContent.includes('查看平台组织'))
      if(!choice) throw new Error('Missing organization view capability')
      choice.click()
    })()`)
    await waitFor('[...document.querySelectorAll(".ant-modal .ant-checkbox-wrapper")].some(x=>x.textContent.includes("查看平台组织") && x.querySelector(".ant-checkbox-checked"))')
    await evaluate(`(() => {
      const modal=[...document.querySelectorAll('.ant-modal')].find(x=>x.getBoundingClientRect().height>0 && x.querySelector('.ant-modal-title')?.textContent.includes('配置'))
      modal.querySelector('.ant-modal-footer .ant-btn-primary').click()
    })()`)
    await waitFor('document.querySelector(".ant-modal-confirm .ant-btn-primary") !== null')
    await click('.ant-modal-confirm .ant-btn-primary','确认发布')
    await waitFor(`(async()=>{
      const response=await fetch('/api/admin/platform/authorization',{headers:{Authorization:'Bearer '+${JSON.stringify(adminToken)}}})
      const item=(await response.json()).data?.roles?.find(x=>x.id===${role.id})
      return item?.grants.includes('PLATFORM:ORGANIZATION:VIEW') && item?.grants.includes('PLATFORM:AUTHORIZATION:VIEW')
    })()`)
    overview=await readOverview(); role=overview.data.roles.find(item=>item.id===role.id)
    await fill('input[placeholder="搜索姓名或账号"]',username)
    await waitFor(`[...document.querySelectorAll('.platform-authorization-page .ant-table')][1]?.textContent.includes(${JSON.stringify(username)})`)
    await evaluate(`(() => {
      const table=document.querySelectorAll('.platform-authorization-page .ant-table')[1]
      const row=[...table.querySelectorAll('tbody tr')].find(x=>x.textContent.includes(${JSON.stringify(username)}))
      row.querySelector('button').click()
    })()`)
    await waitFor('[...document.querySelectorAll(".ant-modal")].some(x=>x.getBoundingClientRect().height>0 && x.querySelector(".ant-modal-title")?.textContent.includes("调整"))')
    await evaluate(`(() => {
      const modal=[...document.querySelectorAll('.ant-modal')].find(x=>x.getBoundingClientRect().height>0 && x.querySelector('.ant-modal-title')?.textContent.includes('调整'))
      modal.querySelector('.ant-select-selector').dispatchEvent(new MouseEvent('mousedown',{bubbles:true,cancelable:true}))
    })()`)
    await waitFor(`[...document.querySelectorAll('.ant-select-item-option')].some(x=>x.getBoundingClientRect().height>0 && x.textContent.includes(${JSON.stringify(roleName)}))`)
    await evaluate(`(() => {
      const option=[...document.querySelectorAll('.ant-select-item-option')].find(x=>x.getBoundingClientRect().height>0 && x.textContent.includes(${JSON.stringify(roleName)}))
      option.click()
      const modal=[...document.querySelectorAll('.ant-modal')].find(x=>x.getBoundingClientRect().height>0 && x.querySelector('.ant-modal-title')?.textContent.includes('调整'))
      modal.querySelector('.ant-modal-footer .ant-btn-primary').click()
    })()`)
    await waitFor('document.querySelector(".ant-modal-confirm .ant-btn-primary") !== null')
    await click('.ant-modal-confirm .ant-btn-primary','确认更新')
    await waitFor(`(async()=>{
      const response=await fetch('/api/admin/platform/authorization',{headers:{Authorization:'Bearer '+${JSON.stringify(adminToken)}}})
      const item=(await response.json()).data?.members?.find(x=>x.id===${member.id})
      return item?.roleIds.includes(${role.id})
    })()`)
    const stale=await evaluate(`(async()=>{
      const response=await fetch('/api/admin/platform/authorization/roles/${role.id}/grants',
        {method:'PUT',headers:{Authorization:'Bearer '+${JSON.stringify(adminToken)},'Content-Type':'application/json'},
          body:JSON.stringify({expectedVersion:${role.version-1},grants:[]})})
      return {status:response.status,code:(await response.json()).code}
    })()`)
    const wrongContext=await evaluate(`(async()=>{
      const response=await fetch('/api/admin/platform/authorization',
        {headers:{Authorization:'Bearer '+${JSON.stringify(systemToken)}}})
      return {status:response.status,code:(await response.json()).code}
    })()`)
    await evaluate('sessionStorage.clear()')
    await send('Page.navigate',{url:base+'/login'})
    await waitFor('document.querySelector(\'input[autocomplete="username"]\') !== null')
    await fill('input[autocomplete="username"]',username)
    await fill('input[autocomplete="current-password"]',userPassword)
    await click('form button[type="submit"]','')
    await waitFor('location.pathname.startsWith("/platform")')
    const memberToken=await evaluate(`JSON.parse(sessionStorage.getItem('unexamine.platform.tokens')||'{}').accessToken`)
    const limited=await evaluate(`(async()=>{
      const headers={Authorization:'Bearer '+${JSON.stringify(memberToken)},'Content-Type':'application/json'}
      const role=await fetch('/api/admin/platform/authorization',{headers})
      const org=await fetch('/api/admin/platform/organization',{headers})
      const denied=await fetch('/api/admin/platform/authorization/roles',{method:'POST',headers,
        body:JSON.stringify({name:'不可创建的平台角色'})})
      const system=await fetch('/api/systems/1/enter',{method:'POST',headers})
      return {role:role.status,organization:org.status,mutate:denied.status,system:system.status}
    })()`)
    if(stale.status!==409 || wrongContext.status<400 || limited.role!==200 || limited.organization!==200 ||
      limited.mutate!==403 || limited.system!==403) {
      throw new Error('Platform role grant result mismatch: '+JSON.stringify({stale,wrongContext,limited}))
    }
    const revocation=await evaluate(`(async()=>{
      const adminHeaders={Authorization:'Bearer '+${JSON.stringify(adminToken)},'Content-Type':'application/json'}
      const memberHeaders={Authorization:'Bearer '+${JSON.stringify(memberToken)}}
      const invalid=await fetch('/api/admin/platform/authorization/roles/${role.id}/grants',
        {method:'PUT',headers:adminHeaders,body:JSON.stringify({expectedVersion:${role.version},
          grants:['PLATFORM:*:*']})})
      const builtin=await fetch('/api/admin/platform/authorization/roles/1/grants',
        {method:'PUT',headers:adminHeaders,body:JSON.stringify({expectedVersion:0,grants:[]})})
      const revoke=await fetch('/api/admin/platform/authorization/roles/${role.id}/grants',
        {method:'PUT',headers:adminHeaders,body:JSON.stringify({expectedVersion:${role.version},
          grants:['PLATFORM:AUTHORIZATION:VIEW']})})
      const organization=await fetch('/api/admin/platform/organization',{headers:memberHeaders})
      const rolePage=await fetch('/api/admin/platform/authorization',{headers:memberHeaders})
      return {invalid:{status:invalid.status,code:(await invalid.json()).code},
        builtin:{status:builtin.status,code:(await builtin.json()).code},
        update:{status:revoke.status,code:(await revoke.json()).code},
        organization:organization.status,rolePage:rolePage.status}
    })()`)
    if(revocation.invalid.status!==400 || revocation.builtin.status!==409 ||
      revocation.update.status!==200 || revocation.organization!==403 || revocation.rolePage!==200) {
      throw new Error('Platform role revocation result mismatch: '+JSON.stringify(revocation))
    }
    await send('Page.navigate',{url:base+'/platform/admin/configurations?category=AUTHORIZATION'})
    await waitFor('document.querySelector(".platform-authorization-page .ant-table") !== null')
    const limitedPage=await evaluate(`({heading:document.querySelector('.platform-admin-workspace h1')?.textContent,
      hasRoleTable:!!document.querySelector('.platform-authorization-page .ant-table'),
      organizationNav:[...document.querySelectorAll('.platform-admin-nav button')].some(x=>x.textContent.includes('组织架构'))})`)
    await send('Page.navigate',{url:base+'/platform/admin/configurations?category=ORGANIZATION'})
    await waitFor('location.pathname==="/platform/dashboard" && new URLSearchParams(location.search).get("notice")==="access-denied"')
    if(!limitedPage.hasRoleTable || limitedPage.organizationNav) {
      throw new Error('Limited member route visibility mismatch: '+JSON.stringify(limitedPage))
    }
    const evidence={step:'S19.11',candidate:process.env.S19_CANDIDATE_VERSION,
      status:'platform_role_grant_and_member_assignment_verified',registration,
      role:{id:role.id,name:role.name,grants:role.grants},member:{id:member.id,username},
      stale,wrongContext,limited,revocation,limitedPage,
      credentialNote:'Fresh account password remained in process memory and was excluded from evidence.'}
    writeFileSync('projects/unexamine/ai/evidence/s19-admin-platform-authorization-browser.json',JSON.stringify(evidence,null,2)+'\n')
    console.log(JSON.stringify(evidence))
    socket.close()
  } else if (process.argv.includes('--inspect-platform-org-options')) {
    await send('Page.navigate',{url:base+'/platform/admin/configurations?category=ORGANIZATION'})
    await waitFor('document.querySelector(".platform-organization-page .ant-table") !== null')
    await fill('input[placeholder="搜索平台成员"]','s19_org_')
    await waitFor('[...document.querySelectorAll(".platform-organization-page .ant-table")][1]?.textContent.includes("s19_org_")')
    await evaluate(`(() => {
      const row=[...document.querySelectorAll('.platform-organization-page .ant-table')[1]
        .querySelectorAll('tbody tr')].find(x=>x.textContent.includes('s19_org_'))
      row?.querySelector('button')?.click()
    })()`)
    await waitFor('[...document.querySelectorAll(".ant-modal")].some(x=>x.getBoundingClientRect().height>0 && x.querySelector(".ant-modal-title")?.textContent.includes("调整"))')
    await evaluate(`(() => {
      const modal=[...document.querySelectorAll('.ant-modal')]
        .find(x=>x.getBoundingClientRect().height>0 && x.querySelector('.ant-modal-title')?.textContent.includes('调整'))
      const selector=modal.querySelector('.ant-select-selector')
      selector.dispatchEvent(new MouseEvent('mousedown',{bubbles:true,cancelable:true}))
      selector.click()
    })()`)
    const options=await evaluate(`({modal:[...document.querySelectorAll('.ant-modal')]
      .filter(x=>x.getBoundingClientRect().height>0).map(x=>x.innerText.slice(0,350)),
      selector:[...document.querySelectorAll('.ant-modal')]
        .find(x=>x.getBoundingClientRect().height>0 && x.querySelector('.ant-modal-title')?.textContent.includes('调整'))
        ?.querySelector('.ant-select')?.outerHTML.slice(0,750),
      options:[...document.querySelectorAll('.ant-select-item-option')]
        .map(x=>({text:x.innerText,html:x.outerHTML.slice(0,300),height:x.getBoundingClientRect().height})),
      dropdown:[...document.querySelectorAll('.ant-select-dropdown')]
        .map(x=>({text:x.innerText.slice(0,500),html:x.outerHTML.slice(0,250)}))})`)
    console.log(JSON.stringify(options))
    socket.close()
  } else if (process.argv.includes('--verify-platform-organization')) {
    const platformToken=await evaluate(`JSON.parse(sessionStorage.getItem('unexamine.platform.tokens')||'{}').accessToken`)
    const systemToken=await evaluate(`JSON.parse(sessionStorage.getItem('unexamine.system.tokens')||'{}').accessToken`)
    const username='s19_org_'+Date.now()
    const userPassword=randomBytes(18).toString('base64url')+'Aa1!'
    const registration=await evaluate(`(async () => {
      const response=await fetch('/api/auth/register',{method:'POST',headers:{'Content-Type':'application/json'},
        body:JSON.stringify({username:${JSON.stringify(username)},password:${JSON.stringify(userPassword)},
          displayName:${JSON.stringify(username)},email:${JSON.stringify(username+'@example.test')}})})
      return {status:response.status,code:(await response.json()).code}
    })()`)
    if(registration.status!==200) throw new Error('Organization test account registration failed')
    await send('Page.navigate',{url:base+'/platform/admin/configurations?category=ORGANIZATION'})
    await waitFor('document.querySelector(".platform-organization-page .ant-table") !== null')
    const departmentName='S19 平台组织 '+Date.now()
    await click('.platform-organization-page button','新建部门')
    await waitFor('[...document.querySelectorAll(".ant-modal")].some(x=>x.getBoundingClientRect().height>0 && x.querySelector(".ant-modal-title")?.textContent.includes("新建平台部门"))')
    await fill('input[placeholder="例如：客户服务部"]',departmentName)
    await evaluate(`(() => {
      const modal=[...document.querySelectorAll('.ant-modal')]
        .find(x=>x.getBoundingClientRect().height>0 && x.querySelector('.ant-modal-title')?.textContent.includes('新建平台部门'))
      modal.querySelector('.ant-modal-footer .ant-btn-primary').click()
    })()`)
    await waitFor(`[...document.querySelectorAll('.platform-organization-page .ant-table-tbody tr')]
      .some(x=>x.textContent.includes(${JSON.stringify(departmentName)}))`)
    const readOverview=()=>evaluate(`(async () => {
      const response=await fetch('/api/admin/platform/organization',
        {headers:{Authorization:'Bearer '+${JSON.stringify(platformToken)}}})
      return {status:response.status,data:(await response.json()).data}
    })()`)
    const created=await readOverview()
    const department=created.data?.departments?.find(x=>x.name===departmentName)
    const newMember=created.data?.members?.find(x=>x.username===username)
    if(!department || !newMember || newMember.departmentId) {
      throw new Error('Platform department/member creation readback mismatch: '+JSON.stringify({created:created.status,department,newMember}))
    }
    await fill('input[placeholder="搜索平台成员"]',username)
    await waitFor(`[...document.querySelectorAll('.platform-organization-page .ant-table')][1]
      ?.textContent.includes(${JSON.stringify(username)})`)
    await evaluate(`(() => {
      const table=document.querySelectorAll('.platform-organization-page .ant-table')[1]
      const row=[...table.querySelectorAll('tbody tr')].find(x=>x.textContent.includes(${JSON.stringify(username)}))
      if(!row) throw new Error('New platform member missing from page')
      const action=[...row.querySelectorAll('button')].find(x=>x.textContent.replaceAll(' ','').includes('调整归属'))
      if(!action) throw new Error('Member assignment button missing')
      action.click()
    })()`)
    await waitFor('[...document.querySelectorAll(".ant-modal")].some(x=>x.getBoundingClientRect().height>0 && x.querySelector(".ant-modal-title")?.textContent.includes("调整"))')
    await evaluate(`(() => {
      const modal=[...document.querySelectorAll('.ant-modal')]
        .find(x=>x.getBoundingClientRect().height>0 && x.querySelector('.ant-modal-title')?.textContent.includes('调整'))
      const selector=modal.querySelector('.ant-select-selector')
      selector.dispatchEvent(new MouseEvent('mousedown',{bubbles:true,cancelable:true}))
    })()`)
    await waitFor(`[...document.querySelectorAll('.ant-select-item-option')]
      .some(x=>x.getBoundingClientRect().height>0 && x.textContent.includes(${JSON.stringify(departmentName)}))`)
    await evaluate(`(() => {
      const option=[...document.querySelectorAll('.ant-select-item-option')]
        .find(x=>x.getBoundingClientRect().height>0 && x.textContent.includes(${JSON.stringify(departmentName)}))
      option.click()
    })()`)
    await fill('input[placeholder="可选"]','S19 平台组织验收')
    await evaluate(`(() => {
      const modal=[...document.querySelectorAll('.ant-modal')]
        .find(x=>x.getBoundingClientRect().height>0 && x.querySelector('.ant-modal-title')?.textContent.includes('调整'))
      modal.querySelector('.ant-modal-footer .ant-btn-primary').click()
    })()`)
    await waitFor(`(async () => {
      const response=await fetch('/api/admin/platform/organization',
        {headers:{Authorization:'Bearer '+${JSON.stringify(platformToken)}}})
      const member=(await response.json()).data?.members?.find(x=>x.username===${JSON.stringify(username)})
      return member?.departmentId===${department.id} && member.title==='S19 平台组织验收'
    })()`)
    const assigned=await readOverview()
    const assignedMember=assigned.data.members.find(x=>x.username===username)
    await evaluate(`(() => {
      const table=document.querySelectorAll('.platform-organization-page .ant-table')[0]
      const row=[...table.querySelectorAll('tbody tr')].find(x=>x.textContent.includes(${JSON.stringify(departmentName)}))
      if(!row) throw new Error('Created department missing from page')
      row.querySelector('button')?.click()
    })()`)
    await waitFor('[...document.querySelectorAll(".ant-modal")].some(x=>x.getBoundingClientRect().height>0 && x.querySelector(".ant-modal-title")?.textContent.includes("修改平台部门"))')
    const renamedName=departmentName+' 复核'
    await fill('input[placeholder="例如：客户服务部"]',renamedName)
    await evaluate(`(() => {
      const modal=[...document.querySelectorAll('.ant-modal')]
        .find(x=>x.getBoundingClientRect().height>0 && x.querySelector('.ant-modal-title')?.textContent.includes('修改平台部门'))
      modal.querySelector('.ant-modal-footer .ant-btn-primary').click()
    })()`)
    await waitFor(`[...document.querySelectorAll('.platform-organization-page .ant-table-tbody tr')]
      .some(x=>x.textContent.includes(${JSON.stringify(renamedName)}))`)
    const after=await readOverview()
    const renamed=after.data.departments.find(x=>x.id===department.id)
    const stale=await evaluate(`(async () => {
      const response=await fetch('/api/admin/platform/organization/departments/'+${department.id},
        {method:'PUT',headers:{Authorization:'Bearer '+${JSON.stringify(platformToken)},'Content-Type':'application/json'},
          body:JSON.stringify({name:'不能覆盖',expectedVersion:${department.version}})})
      return {status:response.status,code:(await response.json()).code}
    })()`)
    const invalidDepartment=await evaluate(`(async () => {
      const response=await fetch('/api/admin/platform/organization/members/'+${newMember.id}+'/assignment',
        {method:'PUT',headers:{Authorization:'Bearer '+${JSON.stringify(platformToken)},'Content-Type':'application/json'},
          body:JSON.stringify({departmentId:999999,expectedVersion:${assignedMember.version}})})
      return {status:response.status,code:(await response.json()).code}
    })()`)
    const wrongContext=await evaluate(`(async () => {
      const response=await fetch('/api/admin/platform/organization',
        {headers:{Authorization:'Bearer '+${JSON.stringify(systemToken)}}})
      return {status:response.status,code:(await response.json()).code}
    })()`)
    await evaluate('sessionStorage.clear()')
    await send('Page.navigate',{url:base+'/login'})
    await waitFor('document.querySelector(\'input[autocomplete="username"]\') !== null')
    await fill('input[autocomplete="username"]',username)
    await fill('input[autocomplete="current-password"]',userPassword)
    await click('form button[type="submit"]','')
    await waitFor('location.pathname.startsWith("/platform")')
    const unprivileged=await evaluate(`(async () => {
      const token=JSON.parse(sessionStorage.getItem('unexamine.platform.tokens')||'{}').accessToken
      const headers={Authorization:'Bearer '+token,'Content-Type':'application/json'}
      const view=await fetch('/api/admin/platform/organization',{headers})
      const edit=await fetch('/api/admin/platform/organization/members/'+${newMember.id}+'/assignment',
        {method:'PUT',headers,body:JSON.stringify({departmentId:null,expectedVersion:${assignedMember.version}})})
      const entry=await fetch('/api/systems/1/enter',{method:'POST',headers})
      return {viewStatus:view.status,editStatus:edit.status,systemEntryStatus:entry.status}
    })()`)
    if(!renamed || renamed.name!==renamedName || renamed.version<=department.version ||
      stale.status!==409 || invalidDepartment.status!==404 || wrongContext.status<400 ||
      unprivileged.viewStatus!==403 || unprivileged.editStatus!==403 ||
      unprivileged.systemEntryStatus!==403) {
      throw new Error('Platform organization permission/version result mismatch: '+JSON.stringify({renamed,stale,invalidDepartment,wrongContext,unprivileged}))
    }
    const evidence={step:'S19.11',candidate:process.env.S19_CANDIDATE_VERSION,
      status:'platform_department_member_page_and_permission_verified',
      registration,department:{id:department.id,name:renamed.name,version:renamed.version},
      member:{id:assignedMember.id,username,departmentId:assignedMember.departmentId,
        title:assignedMember.title,version:assignedMember.version},
      stale,invalidDepartment,wrongContext,unprivileged,
      credentialNote:'Fresh account password remained in process memory and was excluded from evidence.'}
    writeFileSync('projects/unexamine/ai/evidence/s19-admin-platform-organization-browser.json',JSON.stringify(evidence,null,2)+'\n')
    console.log(JSON.stringify(evidence))
    socket.close()
  } else if (process.argv.includes('--verify-platform-audit')) {
    const systemToken=await evaluate(`JSON.parse(sessionStorage.getItem('unexamine.system.tokens')||'{}').accessToken`)
    const platformToken=await evaluate(`JSON.parse(sessionStorage.getItem('unexamine.platform.tokens')||'{}').accessToken`)
    const systemEvent=await evaluate(`(async () => {
      const response=await fetch('/api/admin/audit-events?page=1&pageSize=1',
        {headers:{Authorization:'Bearer '+${JSON.stringify(systemToken)}}})
      const result=await response.json()
      return {status:response.status,id:result.data?.events?.[0]?.id}
    })()`)
    await send('Page.navigate',{url:base+'/platform/admin/configurations?category=AUDIT'})
    await waitFor('document.querySelector(".audit-table .ant-table-row") !== null')
    const first=await evaluate(`(async () => {
      const response=await fetch('/api/admin/platform/audit-events?page=1&pageSize=20',
        {headers:{Authorization:'Bearer '+${JSON.stringify(platformToken)}}})
      const result=await response.json()
      return {status:response.status,total:result.data?.total,
        events:result.data?.events?.map(x=>({id:x.id,platformId:x.platformId,
          systemId:x.systemId,tenantId:x.tenantId,eventCode:x.eventCode,occurTime:x.occurTime}))}
    })()`)
    if(first.status!==200 || first.total<1 || first.events.some(x=>x.platformId!==1 || x.systemId || x.tenantId || !x.occurTime)) {
      throw new Error('Platform audit list scope mismatch: '+JSON.stringify(first))
    }
    await fill('.audit-filters input[placeholder="动作 / 事件编码"]','APPLICATION_VERSION_PUBLISHED')
    await click('.audit-filters button','查询')
    await waitFor('document.querySelector(".audit-table .ant-table-row")?.textContent.includes("APPLICATION_VERSION_PUBLISHED")')
    const page=await evaluate(`({heading:document.querySelector('.audit-page h1')?.textContent,
      filteredRow:document.querySelector('.audit-table .ant-table-row')?.innerText,
      timeCell:document.querySelector('.audit-table .ant-table-row td')?.textContent.trim(),
      hasKeyValueEditor:!!document.querySelector('.fixed-setting-editor')})`)
    await click('.audit-table .ant-table-row button','详情')
    await waitFor('document.querySelector(".audit-detail-drawer")?.textContent.includes("APPLICATION_VERSION_PUBLISHED")')
    const detail=await evaluate(`({event:document.querySelector('.audit-detail-summary')?.innerText.slice(0,320),
      sensitiveTag:document.querySelector('.audit-field-changes .ant-tag')?.textContent.trim(),
      retentionVisible:!!document.querySelector('.audit-retention')})`)
    const crossScope=await evaluate(`(async () => {
      const response=await fetch('/api/admin/platform/audit-events/'+${systemEvent.id},
        {headers:{Authorization:'Bearer '+${JSON.stringify(platformToken)}}})
      return {status:response.status,code:(await response.json()).code}
    })()`)
    const wrongContext=await evaluate(`(async () => {
      const response=await fetch('/api/admin/platform/audit-events',
        {headers:{Authorization:'Bearer '+${JSON.stringify(systemToken)}}})
      return {status:response.status,code:(await response.json()).code}
    })()`)
    const username='s19_audit_'+Date.now()
    const userPassword=randomBytes(18).toString('base64url')+'Aa1!'
    const registration=await evaluate(`(async () => {
      const response=await fetch('/api/auth/register',{method:'POST',headers:{'Content-Type':'application/json'},
        body:JSON.stringify({username:${JSON.stringify(username)},password:${JSON.stringify(userPassword)},
          displayName:${JSON.stringify(username)},email:${JSON.stringify(username+'@example.test')}})})
      return {status:response.status,code:(await response.json()).code}
    })()`)
    await evaluate('sessionStorage.clear()')
    await send('Page.navigate',{url:base+'/login'})
    await waitFor('document.querySelector(\'input[autocomplete="username"]\') !== null')
    await fill('input[autocomplete="username"]',username)
    await fill('input[autocomplete="current-password"]',userPassword)
    await click('form button[type="submit"]','')
    await waitFor('location.pathname.startsWith("/platform")')
    const noPermission=await evaluate(`(async () => {
      const token=JSON.parse(sessionStorage.getItem('unexamine.platform.tokens')||'{}').accessToken
      const response=await fetch('/api/admin/platform/audit-events',
        {headers:{Authorization:'Bearer '+token}})
      return {status:response.status,code:(await response.json()).code}
    })()`)
    if(systemEvent.status!==200 || !systemEvent.id || !page.timeCell || page.hasKeyValueEditor ||
      detail.retentionVisible || crossScope.status!==404 || wrongContext.status<400 ||
      registration.status!==200 || noPermission.status!==403) {
      throw new Error('Platform audit browser/API/denial mismatch: '+JSON.stringify({systemEvent,page,detail,crossScope,wrongContext,registration,noPermission}))
    }
    const evidence={step:'S19.11',candidate:process.env.S19_CANDIDATE_VERSION,
      status:'platform_audit_page_scope_detail_and_denials_verified',
      platformEventTotal:first.total,firstEvent:first.events[0],page,detail,
      systemEventId:systemEvent.id,crossScope,wrongContext,registration,noPermission,
      credentialNote:'Fresh account password remained in process memory and was excluded from evidence.'}
    writeFileSync('projects/unexamine/ai/evidence/s19-admin-platform-audit-browser.json',JSON.stringify(evidence,null,2)+'\n')
    console.log(JSON.stringify(evidence))
    socket.close()
  } else if (process.argv.includes('--inspect-platform-backend')) {
    const categories=['DICTIONARY']
    const pages=[]
    for(const category of categories) {
      await send('Page.navigate',{url:base+'/platform/admin/configurations?category='+category})
      await waitFor('document.querySelector(".fixed-setting-browser button") !== null')
      await click('.fixed-setting-browser button','新建配置')
      await waitFor('document.querySelector(".fixed-setting-editor") !== null')
      pages.push(await evaluate(`({category:${JSON.stringify(category)},
        heading:document.querySelector('.platform-admin-workspace h1')?.textContent,
        editorLabels:[...document.querySelectorAll('.fixed-setting-editor .ant-form-item-label')]
          .map(x=>x.textContent.trim()),
        keyPlaceholder:document.querySelector('.fixed-setting-editor input')?.placeholder,
        hasBusinessManagement:!!document.querySelector('.authorization-page,.dictionary-page,.audit-page,.organization-page')})`))
    }
    if(pages.length!==1 || pages.some(x=>!x.editorLabels.includes('配置键') ||
      !x.editorLabels.includes('配置值') || x.hasBusinessManagement)) {
      throw new Error('Platform backend inspection incomplete: '+JSON.stringify(pages))
    }
    const evidence={step:'S19.11',candidate:process.env.S19_CANDIDATE_VERSION,
      status:'platform_dictionary_business_page_missing',pages,
      source:'PlatformConfigurationsView still routes DICTIONARY to FixedConfigurationsView; platform mode shows generic key/value editor.'}
    writeFileSync('projects/unexamine/ai/evidence/s19-admin-platform-backend-inspection.json',JSON.stringify(evidence,null,2)+'\n')
    console.log(JSON.stringify(evidence))
    socket.close()
  } else if (process.argv.includes('--verify-system-app-rollback')) {
    const sourceToken=await evaluate(`JSON.parse(sessionStorage.getItem('unexamine.system.tokens')||'{}').accessToken`)
    await send('Page.navigate',{url:base+'/platform'})
    await waitFor('[...document.querySelectorAll(".system-row")].some(x=>x.textContent.includes("发布验收业务乙"))')
    await evaluate(`(() => {
      const row=[...document.querySelectorAll('.system-row')].find(x=>x.textContent.includes('发布验收业务乙'))
      const enter=[...row.querySelectorAll('button')].find(x=>x.textContent.includes('进入'))
      if(!enter) throw new Error('Target system entry missing')
      enter.click()
    })()`)
    await waitFor('location.pathname === "/systems/2"')
    await send('Page.navigate',{url:base+'/systems/2/admin?section=applications'})
    await waitFor('[...document.querySelectorAll(".application-list__item")].some(x=>x.textContent.includes("S19 目标系统应用"))')
    await click('.application-list__item','S19 目标系统应用')
    const readApp=()=>evaluate(`(async () => {
      const token=JSON.parse(sessionStorage.getItem('unexamine.system.tokens')||'{}').accessToken
      const response=await fetch('/api/applications/1',{headers:{Authorization:'Bearer '+token}})
      if(!response.ok) throw new Error('Application detail unavailable: '+response.status)
      const app=(await response.json()).data
      return {id:app.id,status:app.status,currentVersionId:app.currentVersionId,
        publicationVersion:app.publicationVersion,
        versions:app.versions.map(x=>({id:x.id,number:x.versionNumber,current:x.current})),
        publishedGrants:app.publishedGrants.map(x=>({action:x.actionCode,
          channels:x.rateLimit?.accessChannels,readableFields:x.readableFields}))}
    })()`)
    const original=await readApp()
    if(!original.currentVersionId || original.publishedGrants.length!==3 ||
      original.publishedGrants.some(x=>!x.channels?.includes('EXTERNAL'))) {
      throw new Error('System application rollback preflight differs from candidate: '+JSON.stringify(original))
    }
    await click('.application-actions button','轮换凭证')
    await waitFor('[...document.querySelectorAll(".ant-modal")].some(x=>x.getBoundingClientRect().height>0 && x.querySelector(".ant-modal-title")?.textContent.includes("轮换访问凭证"))')
    await evaluate(`(() => {
      const modal=[...document.querySelectorAll('.ant-modal')]
        .find(x=>x.getBoundingClientRect().height>0 && x.querySelector('.ant-modal-title')?.textContent.includes('轮换访问凭证'))
      modal.querySelector('.ant-modal-footer .ant-btn-primary').click()
    })()`)
    await waitFor('[...document.querySelectorAll(".ant-modal")].some(x=>x.getBoundingClientRect().height>0 && x.querySelector(".application-secret"))')
    const credential=await evaluate(`(() => {
      const panel=[...document.querySelectorAll('.ant-modal')]
        .find(x=>x.getBoundingClientRect().height>0 && x.querySelector('.application-secret'))
        ?.querySelector('.application-secret')
      const value=label=>[...panel.querySelectorAll('.ant-descriptions-row')]
        .find(x=>x.querySelector('.ant-descriptions-item-label')?.textContent.trim()===label)
        ?.querySelector('.ant-descriptions-item-content code')?.textContent.trim()
      return {clientId:value('Client ID'),secret:value('Client Secret')}
    })()`)
    if(!credential.clientId || !credential.secret) throw new Error('One-time application credential unavailable')
    await evaluate(`(() => {
      const modal=[...document.querySelectorAll('.ant-modal')]
        .find(x=>x.getBoundingClientRect().height>0 && x.querySelector('.application-secret'))
      modal?.querySelector('.ant-modal-close')?.click()
    })()`)
    const signedList=()=>evaluate(`(async () => {
      const clientId=${JSON.stringify(credential.clientId)}
      const secret=${JSON.stringify(credential.secret)}
      const encoder=new TextEncoder()
      const hex=bytes=>[...new Uint8Array(bytes)].map(x=>x.toString(16).padStart(2,'0')).join('')
      const body=JSON.stringify({resourceType:'MODULE',resourceId:'s19_service_contract',
        actionCode:'LIST',targetSystemId:2,targetTenantId:2,
        requestedDataScope:{type:'RESPONSIBLE',memberIds:[2],departmentIds:[]},
        payload:{page:1,pageSize:20}})
      const timestamp=String(Date.now())
      const nonce='s19rollback'+Date.now()+Math.random().toString(36).slice(2)
      const idem='s19-rollback-'+Date.now()+Math.random().toString(36).slice(2)
      const digest=hex(await crypto.subtle.digest('SHA-256',encoder.encode(body)))
      const canonical=[clientId,timestamp,nonce,idem,digest].join('\\n')
      const key=await crypto.subtle.importKey('raw',encoder.encode(secret),
        {name:'HMAC',hash:'SHA-256'},false,['sign'])
      const signature=hex(await crypto.subtle.sign('HMAC',key,encoder.encode(canonical)))
      const response=await fetch('/api/application-access/v1/calls',{method:'POST',
        headers:{'Content-Type':'application/json','X-App-Client-Id':clientId,
          'X-App-Timestamp':timestamp,'X-App-Nonce':nonce,
          'X-App-Idempotency-Key':idem,'X-App-Signature':signature},body})
      const result=await response.json()
      const value=result.data?.result?.businessResult||result.data?.result
      return {status:response.status,code:result.code,
        recordIds:(value?.records||value?.items||[]).map(x=>x.id)}
    })()`)
    const internalList=()=>evaluate(`(async () => {
      const body={resourceType:'MODULE',resourceId:'s19_service_contract',actionCode:'LIST',
        targetSystemId:2,targetTenantId:2,
        requestedDataScope:{type:'RESPONSIBLE',memberIds:[2],departmentIds:[]},
        payload:{page:1,pageSize:20}}
      const response=await fetch('/api/application-internal-access/v1/applications/1/calls',
        {method:'POST',headers:{Authorization:'Bearer '+${JSON.stringify(sourceToken)},
          'Content-Type':'application/json','X-Idempotency-Key':'s19-rollback-internal-'+Date.now()},
          body:JSON.stringify(body)})
      const result=await response.json()
      const value=result.data?.result?.businessResult||result.data?.result
      return {status:response.status,code:result.code,
        recordIds:(value?.records||value?.items||[]).map(x=>x.id)}
    })()`)
    const rollbackByPage=async reason=>{
      const before=await readApp()
      await click('.application-actions button','版本回滚')
      await waitFor('[...document.querySelectorAll(".ant-modal")].some(x=>x.getBoundingClientRect().height>0 && x.querySelector(".ant-modal-title")?.textContent.includes("从历史版本恢复"))')
      await evaluate(`(() => {
        const modal=[...document.querySelectorAll('.ant-modal')]
          .find(x=>x.getBoundingClientRect().height>0 && x.querySelector('.ant-modal-title')?.textContent.includes('从历史版本恢复'))
        const textarea=modal.querySelector('textarea')
        textarea.value=${JSON.stringify(reason)}
        textarea.dispatchEvent(new Event('input',{bubbles:true}))
        modal.querySelector('.ant-modal-footer .ant-btn-primary').click()
      })()`)
      await waitFor(`(async () => {
        const token=JSON.parse(sessionStorage.getItem('unexamine.system.tokens')||'{}').accessToken
        const response=await fetch('/api/applications/1',{headers:{Authorization:'Bearer '+token}})
        return response.ok && (await response.json()).data.currentVersionId !== ${before.currentVersionId}
      })()`)
      await waitFor('![...document.querySelectorAll(".ant-modal")].some(x=>x.getBoundingClientRect().height>0 && x.querySelector(".ant-modal-title")?.textContent.includes("从历史版本恢复"))')
      return readApp()
    }
    let restored=false
    try {
      const before={external:await signedList(),internal:await internalList()}
      const old=await rollbackByPage('S19 发布候选验证上一版授权撤销')
      const during={external:await signedList(),internal:await internalList()}
      if(old.publishedGrants.some(x=>x.channels?.includes('EXTERNAL')) ||
        during.external.status!==403 || during.external.code!=='APPLICATION_ACCESS_CHANNEL_DENIED' ||
        during.internal.status!==200 || !during.internal.recordIds.includes(4)) {
        throw new Error('Rolled-back application authorization mismatch: '+JSON.stringify({old,during}))
      }
      const current=await rollbackByPage('S19 发布候选恢复内部和外部授权')
      const after={external:await signedList(),internal:await internalList()}
      restored=current.publishedGrants.length===original.publishedGrants.length &&
        current.publishedGrants.every(x=>x.channels?.includes('INTERNAL') && x.channels?.includes('EXTERNAL'))
      if(!restored || before.external.status!==200 || before.internal.status!==200 ||
        after.external.status!==200 || !after.external.recordIds.includes(4) ||
        after.internal.status!==200 || !after.internal.recordIds.includes(4)) {
        throw new Error('Restored application authorization mismatch: '+JSON.stringify({before,current,after}))
      }
      const evidence={step:'S19.11',candidate:process.env.S19_CANDIDATE_VERSION,
        status:'system_application_page_rollback_and_restore_runtime_verified',
        originalVersionId:original.currentVersionId,rolledVersionId:old.currentVersionId,
        restoredVersionId:current.currentVersionId,before,during,after,
        credentialNote:'Rotated one-time credential stayed in memory and was excluded from evidence.'}
      writeFileSync('projects/unexamine/ai/evidence/s19-admin-system-app-rollback-browser.json',JSON.stringify(evidence,null,2)+'\n')
      console.log(JSON.stringify(evidence))
    } finally {
      if(!restored) {
        const current=await readApp()
        if(current.currentVersionId!==original.currentVersionId &&
          !current.publishedGrants.every(x=>x.channels?.includes('EXTERNAL'))) {
          const token=await evaluate(`JSON.parse(sessionStorage.getItem('unexamine.system.tokens')||'{}').accessToken`)
          const response=await evaluate(`(async () => {
            const result=await fetch('/api/applications/1/rollback',{method:'POST',
              headers:{Authorization:'Bearer '+${JSON.stringify(token)},'Content-Type':'application/json'},
              body:JSON.stringify({targetVersionId:${original.currentVersionId},
                expectedPublicationVersion:${current.publicationVersion},
                reason:'S19 回滚验收异常后恢复原发布授权'})})
            return {status:result.status,code:(await result.json()).code}
          })()`)
          if(response.status!==200) throw new Error('Failed to restore application after interrupted rollback: '+JSON.stringify(response))
        }
      }
    }
    socket.close()
  } else if (process.argv.includes('--verify-system-internal-after-publish')) {
    const result=await evaluate(`(async () => {
      const token=JSON.parse(sessionStorage.getItem('unexamine.system.tokens')||'{}').accessToken
      const body={resourceType:'MODULE',resourceId:'s19_service_contract',actionCode:'LIST',
        targetSystemId:2,targetTenantId:2,
        requestedDataScope:{type:'RESPONSIBLE',memberIds:[2],departmentIds:[]},
        payload:{page:1,pageSize:20}}
      const response=await fetch('/api/application-internal-access/v1/applications/1/calls',
        {method:'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'application/json',
          'X-Idempotency-Key':'s19-internal-after-external-'+Date.now()},body:JSON.stringify(body)})
      const result=await response.json()
      const value=result.data?.result?.businessResult||result.data?.result
      return {status:response.status,code:result.code,
        recordIds:(value?.records||value?.items||[]).map(x=>x.id)}
    })()`)
    if(result.status!==200 || !result.recordIds.includes(4)) {
      throw new Error('Internal application channel regressed after external grant publication: '+JSON.stringify(result))
    }
    const evidence={step:'S19.11',candidate:process.env.S19_CANDIDATE_VERSION,
      status:'system_application_internal_channel_preserved',result}
    writeFileSync('projects/unexamine/ai/evidence/s19-admin-system-internal-after-publish-browser.json',JSON.stringify(evidence,null,2)+'\n')
    console.log(JSON.stringify(evidence))
    socket.close()
  } else if (process.argv.includes('--verify-system-signed-app')) {
    await send('Page.navigate',{url:base+'/platform'})
    await waitFor('[...document.querySelectorAll(".system-row")].some(x=>x.textContent.includes("发布验收业务乙"))')
    await evaluate(`(() => {
      const row=[...document.querySelectorAll('.system-row')].find(x=>x.textContent.includes('发布验收业务乙'))
      const enter=[...row.querySelectorAll('button')].find(x=>x.textContent.includes('进入'))
      if(!enter) throw new Error('Target system entry missing')
      enter.click()
    })()`)
    await waitFor('location.pathname === "/systems/2"')
    await send('Page.navigate',{url:base+'/systems/2/admin?section=applications'})
    await waitFor('[...document.querySelectorAll(".application-list__item")].some(x=>x.textContent.includes("S19 目标系统应用"))')
    await click('.application-list__item','S19 目标系统应用')
    const readApp=()=>evaluate(`(async () => {
      const token=JSON.parse(sessionStorage.getItem('unexamine.system.tokens')||'{}').accessToken
      const response=await fetch('/api/applications/1',{headers:{Authorization:'Bearer '+token}})
      return response.ok?(await response.json()).data:null
    })()`)
    const before=await readApp()
    if (!before?.currentVersionId || before.grants?.length!==3) throw new Error('Target application grant baseline missing')
    const configured=before.grants.every(x=>x.rateLimit?.accessChannels?.includes('EXTERNAL'))
    if (!configured) {
      await click('.application-actions button','配置草稿')
      await waitFor('[...document.querySelectorAll(".ant-modal")].some(x=>x.getBoundingClientRect().height>0 && x.querySelector(".application-draft-card"))')
      await evaluate(`(() => {
        const modal=[...document.querySelectorAll('.ant-modal')]
          .find(x=>x.getBoundingClientRect().height>0 && x.querySelector('.application-draft-card'))
        const cards=[...modal.querySelectorAll('.application-draft-card')]
        if(cards.length!==3) throw new Error('Expected three target application grant cards')
        for(const card of cards) {
          const checkbox=[...card.querySelectorAll('.ant-checkbox-wrapper')]
            .find(x=>x.textContent.includes('外部访问（凭证签名）'))
          if(!checkbox) throw new Error('External access checkbox missing')
          if(!checkbox.querySelector('input')?.checked) checkbox.click()
        }
        modal.querySelector('.ant-modal-footer .ant-btn-primary')?.click()
      })()`)
      await waitFor(`(async () => {
        const token=JSON.parse(sessionStorage.getItem('unexamine.system.tokens')||'{}').accessToken
        const response=await fetch('/api/applications/1',{headers:{Authorization:'Bearer '+token}})
        const app=response.ok?(await response.json()).data:null
        return app?.grants?.length===3 && app.grants.every(x=>x.rateLimit?.accessChannels?.includes('EXTERNAL'))
      })()`)
      await click('.application-actions button','检查并发布')
      await waitFor(`(async () => {
        const token=JSON.parse(sessionStorage.getItem('unexamine.system.tokens')||'{}').accessToken
        const response=await fetch('/api/applications/1',{headers:{Authorization:'Bearer '+token}})
        const app=response.ok?(await response.json()).data:null
        return app?.currentVersionId>${before.currentVersionId} ? app.currentVersionId : null
      })()`)
    }
    await click('.application-actions button','轮换凭证')
    await waitFor('[...document.querySelectorAll(".ant-modal")].some(x=>x.getBoundingClientRect().height>0 && x.querySelector(".ant-modal-title")?.textContent.includes("轮换访问凭证"))')
    await evaluate(`(() => {
      const modal=[...document.querySelectorAll('.ant-modal')]
        .find(x=>x.getBoundingClientRect().height>0 && x.querySelector('.ant-modal-title')?.textContent.includes('轮换访问凭证'))
      modal.querySelector('.ant-modal-footer .ant-btn-primary').click()
    })()`)
    await waitFor('[...document.querySelectorAll(".ant-modal")].some(x=>x.getBoundingClientRect().height>0 && x.querySelector(".application-secret"))')
    const credential=await evaluate(`(() => {
      const panel=[...document.querySelectorAll('.ant-modal')]
        .find(x=>x.getBoundingClientRect().height>0 && x.querySelector('.application-secret'))
        ?.querySelector('.application-secret')
      const value=label=>[...panel.querySelectorAll('.ant-descriptions-row')]
        .find(x=>x.querySelector('.ant-descriptions-item-label')?.textContent.trim()===label)
        ?.querySelector('.ant-descriptions-item-content code')?.textContent.trim()
      return {clientId:value('Client ID'),secret:value('Client Secret')}
    })()`)
    if(!credential.clientId || !credential.secret) throw new Error('Target application credential missing')
    const signed=await evaluate(`(async () => {
      const clientId=${JSON.stringify(credential.clientId)}
      const secret=${JSON.stringify(credential.secret)}
      const encoder=new TextEncoder()
      const hex=bytes=>[...new Uint8Array(bytes)].map(x=>x.toString(16).padStart(2,'0')).join('')
      const key=await crypto.subtle.importKey('raw',encoder.encode(secret),{name:'HMAC',hash:'SHA-256'},false,['sign'])
      async function call(resourceType,resourceId,actionCode,payload,targetSystemId=2,valid=true) {
        const body=JSON.stringify({resourceType,resourceId,actionCode,targetSystemId,targetTenantId:2,
          requestedDataScope:{type:'RESPONSIBLE',memberIds:[2],departmentIds:[]},payload})
        const timestamp=String(Date.now())
        const nonce='s19sys'+Date.now()+Math.random().toString(36).slice(2)
        const idem='s19-system-'+Date.now()+Math.random().toString(36).slice(2)
        const digest=hex(await crypto.subtle.digest('SHA-256',encoder.encode(body)))
        const canonical=[clientId,timestamp,nonce,idem,digest].join('\\n')
        const signature=valid?hex(await crypto.subtle.sign('HMAC',key,encoder.encode(canonical))):'0'.repeat(64)
        const response=await fetch('/api/application-access/v1/calls',{method:'POST',
          headers:{'Content-Type':'application/json','X-App-Client-Id':clientId,
            'X-App-Timestamp':timestamp,'X-App-Nonce':nonce,
            'X-App-Idempotency-Key':idem,'X-App-Signature':signature},body})
        const result=await response.json()
        return {status:response.status,code:result.code,
          value:result.data?.result?.businessResult||result.data?.result||null}
      }
      const list=await call('MODULE','s19_service_contract','LIST',{page:1,pageSize:20})
      const detail=await call('MODULE','s19_service_contract','DETAIL',{recordId:4})
      const deniedUpdate=await call('MODULE','s19_service_contract','UPDATE',{recordId:4})
      const deniedScope=await call('MODULE','s19_service_contract','LIST',{page:1,pageSize:20},999)
      const badSignature=await call('MODULE','s19_service_contract','LIST',{page:1,pageSize:20},2,false)
      return {list:{status:list.status,code:list.code,
          recordIds:(list.value?.records||list.value?.items||[]).map(x=>x.id)},
        detail:{status:detail.status,code:detail.code,fields:detail.value?.fields||null},
        deniedUpdate:{status:deniedUpdate.status,code:deniedUpdate.code},
        deniedScope:{status:deniedScope.status,code:deniedScope.code},
        badSignature:{status:badSignature.status,code:badSignature.code}}
    })()`)
    await evaluate(`(() => {
      const modal=[...document.querySelectorAll('.ant-modal')]
        .find(x=>x.getBoundingClientRect().height>0 && x.querySelector('.application-secret'))
      modal?.querySelector('.ant-modal-close')?.click()
    })()`)
    const after=await readApp()
    if(after?.status!=='ACTIVE' || !after.grants?.every(x=>x.rateLimit?.accessChannels?.includes('INTERNAL')
      && x.rateLimit?.accessChannels?.includes('EXTERNAL')) ||
      signed.list.status!==200 || !signed.list.recordIds.includes(4) ||
      signed.detail.status!==200 || signed.detail.fields?.public_note!=='授权给来源系统'
      || 'private_note' in (signed.detail.fields||{}) ||
      signed.deniedUpdate.status!==403 || signed.deniedUpdate.code!=='APPLICATION_GRANT_DENIED'
      || signed.deniedScope.status!==403 || signed.badSignature.status!==401) {
      throw new Error('System signed application access mismatch: '+JSON.stringify({after:{status:after?.status,version:after?.currentVersionId},signed}))
    }
    const evidence={step:'S19.11',candidate:process.env.S19_CANDIDATE_VERSION,
      status:'system_application_signed_scope_and_field_access',
      applicationId:after.id,publishedVersionId:after.currentVersionId,
      preservedInternalAndEnabledExternal:true,signed,
      credentialNote:'One-time credential stayed in browser memory and was excluded from evidence.'}
    writeFileSync('projects/unexamine/ai/evidence/s19-admin-system-signed-app-browser.json',JSON.stringify(evidence,null,2)+'\n')
    console.log(JSON.stringify(evidence))
    socket.close()
  } else if (process.argv.includes('--inspect-system-app') || process.argv.includes('--inspect-system-app-draft') || process.argv.includes('--inspect-system-app-rollback')) {
    await send('Page.navigate', {url:base+'/platform'})
    await waitFor('[...document.querySelectorAll(".system-row")].some(x=>x.textContent.includes("发布验收业务乙"))')
    await evaluate(`(() => {
      const row=[...document.querySelectorAll('.system-row')].find(x=>x.textContent.includes('发布验收业务乙'))
      const enter=[...row.querySelectorAll('button')].find(x=>x.textContent.includes('进入'))
      if(!enter) throw new Error('Target system entry missing')
      enter.click()
    })()`)
    await waitFor('location.pathname === "/systems/2"')
    await send('Page.navigate',{url:base+'/systems/2/admin?section=applications'})
    await waitFor('document.querySelector(".application-management-page") !== null')
    const inspect=await evaluate(`(async () => {
      const token=JSON.parse(sessionStorage.getItem('unexamine.system.tokens')||'{}').accessToken
      const headers={Authorization:'Bearer '+token}
      const response=await fetch('/api/applications/1',{headers})
      const app=response.ok?(await response.json()).data:null
      const catalogResponse=await fetch('/api/applications/1/scope-catalog',{headers})
      const catalog=catalogResponse.ok?(await catalogResponse.json()).data:null
      return {appStatus:response.status,scopeStatus:catalogResponse.status,
        app:app?{id:app.id,name:app.name,status:app.status,currentVersionId:app.currentVersionId,
          version:app.version,draftRevision:app.draftRevision,publicationVersion:app.publicationVersion,
          versions:app.versions?.map(x=>({id:x.id,versionNumber:x.versionNumber,current:x.current})),
          grants:app.grants?.map(x=>({targetType:x.targetType,targetSystemId:x.targetSystemId,
            targetTenantId:x.targetTenantId,resourceType:x.resourceType,resourceId:x.resourceId,
            actionCode:x.actionCode,channels:x.rateLimit?.accessChannels}))}:null,
        members:catalog?.members?.map(x=>({id:x.id,name:x.name,status:x.status})),
        page:document.querySelector('.application-management-page')?.innerText.slice(0,750)}
    })()`)
    if (process.argv.includes('--inspect-system-app-draft')) {
      await click('.application-list__item','S19 目标系统应用')
      await click('.application-actions button','配置草稿')
      await waitFor('[...document.querySelectorAll(".ant-modal")].some(x=>x.getBoundingClientRect().height>0 && x.querySelector(".application-draft-card"))')
      inspect.draft=await evaluate(`({cards:[...document.querySelectorAll('.application-draft-card')]
        .map(x=>({text:x.innerText.slice(0,450),checks:[...x.querySelectorAll('.ant-checkbox-wrapper')]
          .map(y=>({text:y.textContent.trim(),checked:y.querySelector('input')?.checked}))})),
        actions:[...document.querySelectorAll('.ant-modal-footer button')].map(x=>x.textContent.trim())})`)
    }
    if (process.argv.includes('--inspect-system-app-rollback')) {
      await click('.application-list__item','S19 目标系统应用')
      await click('.application-actions button','版本回滚')
      await waitFor('[...document.querySelectorAll(".ant-modal")].some(x=>x.getBoundingClientRect().height>0 && x.querySelector(".ant-modal-title")?.textContent.includes("从历史版本恢复"))')
      inspect.rollback=await evaluate(`(() => {
        const modal=[...document.querySelectorAll('.ant-modal')]
          .find(x=>x.getBoundingClientRect().height>0 && x.querySelector('.ant-modal-title')?.textContent.includes('从历史版本恢复'))
        return {text:modal.innerText,html:modal.querySelector('.ant-select')?.outerHTML.slice(0,900),
          textarea:modal.querySelector('textarea')?.outerHTML.slice(0,300)}
      })()`)
    }
    console.log(JSON.stringify(inspect))
    socket.close()
  } else if (process.argv.includes('--verify-platform-credential-revocation')) {
    await send('Page.navigate', { url: base + '/platform/admin/applications' })
    await waitFor('[...document.querySelectorAll(".application-list__item")].some(x=>x.textContent.includes("S19 平台流程接入应用"))')
    await click('.application-list__item', 'S19 平台流程接入应用')
    await waitFor('document.querySelector(".application-hero")?.textContent.includes("已发布")')
    const rotate = async () => {
      await click('.application-actions button', '轮换凭证')
      await waitFor('[...document.querySelectorAll(".ant-modal")].some(x=>x.getBoundingClientRect().height>0 && x.querySelector(".ant-modal-title")?.textContent.includes("轮换访问凭证"))')
      await evaluate(`(() => {
        const modal=[...document.querySelectorAll('.ant-modal')]
          .find(x=>x.getBoundingClientRect().height>0 && x.querySelector('.ant-modal-title')?.textContent.includes('轮换访问凭证'))
        modal.querySelector('.ant-modal-footer .ant-btn-primary').click()
      })()`)
      await waitFor('[...document.querySelectorAll(".ant-modal")].some(x=>x.getBoundingClientRect().height>0 && x.querySelector(".application-secret"))')
      const credential = await evaluate(`(() => {
        const panel=[...document.querySelectorAll('.ant-modal')]
          .find(x=>x.getBoundingClientRect().height>0 && x.querySelector('.application-secret'))
          ?.querySelector('.application-secret')
        const value=label=>[...panel.querySelectorAll('.ant-descriptions-row')]
          .find(x=>x.querySelector('.ant-descriptions-item-label')?.textContent.trim()===label)
          ?.querySelector('.ant-descriptions-item-content code')?.textContent.trim()
        return {clientId:value('Client ID'),secret:value('Client Secret')}
      })()`)
      if (!credential.clientId || !credential.secret) throw new Error('Rotated application credential missing')
      await evaluate(`(() => {
        const modal=[...document.querySelectorAll('.ant-modal')]
          .find(x=>x.getBoundingClientRect().height>0 && x.querySelector('.ant-modal-title')?.textContent.includes('一次性凭证明文'))
        modal?.querySelector('.ant-modal-close')?.click()
      })()`)
      await delay(350)
      return credential
    }
    const signedDeniedAction = async credential => evaluate(`(async () => {
      const token=JSON.parse(sessionStorage.getItem('unexamine.platform.tokens') || '{}').accessToken
      const scopeResponse=await fetch('/api/applications/2/scope-catalog',
        {headers:{Authorization:'Bearer '+token}})
      const scope=(await scopeResponse.json()).data
      const member=scope?.members?.find(x=>x.status==='ACTIVE' && x.name.includes('平台超级管理员'))
      if(!member) throw new Error('Platform app scope member unavailable')
      const clientId=${JSON.stringify(credential.clientId)}
      const secret=${JSON.stringify(credential.secret)}
      const encoder=new TextEncoder()
      const hex=bytes=>[...new Uint8Array(bytes)].map(x=>x.toString(16).padStart(2,'0')).join('')
      const body=JSON.stringify({resourceType:'FLOW',resourceId:'__flow_design__',actionCode:'PUBLISH',
        targetSystemId:null,targetTenantId:null,
        requestedDataScope:{type:'RESPONSIBLE',memberIds:[member.id],departmentIds:[]},payload:{}})
      const timestamp=String(Date.now())
      const nonce='s19revoke'+Date.now()+Math.random().toString(36).slice(2)
      const idem='s19-revoke-'+Date.now()+Math.random().toString(36).slice(2)
      const digest=hex(await crypto.subtle.digest('SHA-256',encoder.encode(body)))
      const canonical=[clientId,timestamp,nonce,idem,digest].join('\\n')
      const key=await crypto.subtle.importKey('raw',encoder.encode(secret),
        {name:'HMAC',hash:'SHA-256'},false,['sign'])
      const signature=hex(await crypto.subtle.sign('HMAC',key,encoder.encode(canonical)))
      const response=await fetch('/api/application-access/v1/calls', {method:'POST',
        headers:{'Content-Type':'application/json','X-App-Client-Id':clientId,
          'X-App-Timestamp':timestamp,'X-App-Nonce':nonce,
          'X-App-Idempotency-Key':idem,'X-App-Signature':signature},body})
      const result=await response.json()
      return {status:response.status,code:result.code}
    })()`)
    const first = await rotate()
    const before = await signedDeniedAction(first)
    const second = await rotate()
    const revoked = await signedDeniedAction(first)
    const current = await signedDeniedAction(second)
    if (before.status!==403 || before.code!=='APPLICATION_GRANT_DENIED'
      || revoked.status!==401 || current.status!==403 || current.code!=='APPLICATION_GRANT_DENIED') {
      throw new Error('Application credential revocation mismatch: '+JSON.stringify({before,revoked,current}))
    }
    const evidence={step:'S19.11',candidate:process.env.S19_CANDIDATE_VERSION,
      status:'platform_application_credential_revocation_effective',before,revoked,current,
      credentialNote:'Rotated credential values were held in memory only and excluded from evidence.'}
    writeFileSync('projects/unexamine/ai/evidence/s19-admin-platform-credential-revocation-browser.json',JSON.stringify(evidence,null,2)+'\n')
    console.log(JSON.stringify(evidence))
    socket.close()
  } else if (process.argv.includes('--verify-platform-signed-app')) {
    await send('Page.navigate', { url: base + '/platform/admin/applications' })
    await waitFor('[...document.querySelectorAll(".application-list__item")].some(x=>x.textContent.includes("S19 平台流程接入应用"))')
    await click('.application-list__item', 'S19 平台流程接入应用')
    await waitFor('document.querySelector(".application-hero")?.textContent.includes("已发布")')
    await click('.application-actions button', '轮换凭证')
    await waitFor('[...document.querySelectorAll(".ant-modal-title")].some(x=>x.textContent.includes("轮换访问凭证"))')
    await evaluate(`(() => {
      const modal = [...document.querySelectorAll('.ant-modal')].find(x=>x.querySelector('.ant-modal-title')?.textContent.includes('轮换访问凭证'))
      modal.querySelector('.ant-modal-footer .ant-btn-primary').click()
    })()`)
    await waitFor('document.querySelector(".application-secret") !== null')
    const signed = await evaluate(`(async () => {
      const secretPanel = document.querySelector('.application-secret')
      const secretValue = label => {
        const row = [...secretPanel.querySelectorAll('.ant-descriptions-row')]
          .find(x=>x.querySelector('.ant-descriptions-item-label')?.textContent.trim()===label)
        return row?.querySelector('.ant-descriptions-item-content code')?.textContent.trim()
      }
      const clientId = secretValue('Client ID')
      const clientSecret = secretValue('Client Secret')
      if (!clientId || !clientSecret) throw new Error('One-time credential fields are unavailable')
      const token = JSON.parse(sessionStorage.getItem('unexamine.platform.tokens') || '{}').accessToken
      const scopeResponse = await fetch('/api/applications/2/scope-catalog', { headers:{Authorization:'Bearer '+token} })
      const scope = (await scopeResponse.json()).data
      const member = scope?.members?.find(x=>x.status==='ACTIVE' && x.name.includes('平台超级管理员'))
      if (!member) throw new Error('Platform app scope member is unavailable')
      const encoder = new TextEncoder()
      const hex = bytes => [...new Uint8Array(bytes)].map(x=>x.toString(16).padStart(2,'0')).join('')
      const key = await crypto.subtle.importKey('raw', encoder.encode(clientSecret), {name:'HMAC',hash:'SHA-256'}, false, ['sign'])
      async function signedCall(actionCode, nonce, idem, validSignature=true) {
        const body = JSON.stringify({resourceType:'FLOW',resourceId:'__flow_design__',actionCode,
          targetSystemId:null,targetTenantId:null,
          requestedDataScope:{type:'RESPONSIBLE',memberIds:[member.id],departmentIds:[]},
          payload:{code:'s19_platform_signed_flow_'+Date.now(),name:'S19 平台签名流程'}})
        const timestamp=String(Date.now())
        const digest=hex(await crypto.subtle.digest('SHA-256', encoder.encode(body)))
        const canonical=[clientId,timestamp,nonce,idem,digest].join('\\n')
        const signature=validSignature ? hex(await crypto.subtle.sign('HMAC',key,encoder.encode(canonical))) : '0'.repeat(64)
        const response=await fetch('/api/application-access/v1/calls', {method:'POST',
          headers:{'Content-Type':'application/json','X-App-Client-Id':clientId,'X-App-Timestamp':timestamp,
            'X-App-Nonce':nonce,'X-App-Idempotency-Key':idem,'X-App-Signature':signature}, body})
        const result=await response.json()
        return {status:response.status,code:result.code,
          flowId:result.data?.result?.businessResult?.id || result.data?.result?.id || null}
      }
      const accepted=await signedCall('CREATE_DRAFT','s19signed'+Date.now(),'s19-signed-ok-'+Date.now())
      const denied=await signedCall('PUBLISH','s19denied'+Date.now(),'s19-signed-denied-'+Date.now())
      const badSignature=await signedCall('CREATE_DRAFT','s19bad'+Date.now(),'s19-signed-bad-'+Date.now(),false)
      return {accepted,denied,badSignature}
    })()`)
    await evaluate(`(() => {
      const modal = [...document.querySelectorAll('.ant-modal')].find(x=>x.querySelector('.ant-modal-title')?.textContent.includes('一次性凭证明文'))
      modal?.querySelector('.ant-modal-close')?.click()
    })()`)
    if (signed.accepted.status !== 200 || !signed.accepted.flowId ||
        signed.denied.status !== 403 || signed.denied.code !== 'APPLICATION_GRANT_DENIED' ||
        signed.badSignature.status !== 401) {
      throw new Error('Platform signed application access mismatch: ' + JSON.stringify(signed))
    }
    const evidence = {step:'S19.11',candidate:'0.1.0-s19.11-dev5',status:'platform_signed_app_authorized_and_denied',signed,
      credentialNote:'One-time secret remained in browser memory and was not persisted in evidence.'}
    writeFileSync('projects/unexamine/ai/evidence/s19-admin-platform-signed-app-browser.json', JSON.stringify(evidence,null,2)+'\n')
    console.log(JSON.stringify(evidence))
    socket.close()
  } else if (process.argv.includes('--verify-import-rollback')) {
    const moduleCode = 'module_ae3286711c'
    await send('Page.navigate', { url: base + '/systems/1?workspace=runtime&module=' + moduleCode })
    await waitFor('document.body.innerText.includes("S19 上海客户")')
    const inspect = () => evaluate(`(async () => {
      const token = JSON.parse(sessionStorage.getItem('unexamine.system.tokens') || '{}').accessToken
      const headers = { Authorization:'Bearer ' + token }
      const batchResponse = await fetch('/api/runtime/modules/${moduleCode}/imports', { headers })
      const recordsResponse = await fetch('/api/runtime/modules/${moduleCode}/records?page=1&pageSize=20', { headers })
      const dashboardResponse = await fetch('/api/analytics/runtime', { headers })
      return { batches:batchResponse.ok ? (await batchResponse.json()).data.map(x=>({id:x.id,status:x.status,
          successRows:x.successRows,failedRows:x.failedRows,rows:x.rows?.map(r=>({rowNumber:r.rowNumber,status:r.status,targetRecordId:r.targetRecordId,rollbackStatus:r.rollbackStatus}))})) : [],
        records:recordsResponse.ok ? (await recordsResponse.json()).data.total : null,
        dashboardValue:dashboardResponse.ok ? (await dashboardResponse.json()).data.components?.[0]?.value : null }
    })()`)
    const before = await inspect()
    const batch = before.batches.find(x => x.successRows === 1 && x.failedRows === 1)
    if (!batch) throw new Error('Expected partial import batch is missing')
    if (batch.status !== 'ROLLED_BACK') {
      await click('button', '导 入')
      await waitFor('document.querySelector(".import-drawer .import-summary") !== null')
      await click('.import-result-actions button', '安全回滚')
      await waitFor('document.querySelector(".ant-popover")?.textContent.includes("只撤销本批次")')
      await click('.ant-popconfirm-buttons button', '确 定')
      await waitFor('document.querySelector(".import-drawer .import-summary")?.textContent.includes("已回滚")')
    }
    const after = await inspect()
    const rolled = after.batches.find(x => x.id === batch.id)
    if (rolled?.status !== 'ROLLED_BACK' || after.records !== before.records - 1 ||
        after.dashboardValue !== before.dashboardValue - 1 ||
        !rolled.rows?.some(x => x.rollbackStatus === 'ROLLED_BACK')) {
      throw new Error('Import UI rollback did not preserve row-level results and dashboard: ' + JSON.stringify({ before, after }))
    }
    const image = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
    writeFileSync('projects/unexamine/ai/evidence/s19-admin-import-rollback-browser.png', Buffer.from(image.data, 'base64'))
    const evidence = { step:'S19.11', candidate:'0.1.0-s19.11-dev5', status:'import_ui_safe_rollback_and_dashboard_recalculation', before, after }
    writeFileSync('projects/unexamine/ai/evidence/s19-admin-import-rollback-browser.json', JSON.stringify(evidence, null, 2) + '\n')
    console.log(JSON.stringify(evidence))
    socket.close()
  } else if (process.argv.includes('--verify-file-denial')) {
    const attachmentId = 6
    await send('Page.navigate', { url: base + '/systems/1?workspace=files' })
    await waitFor('document.body.innerText.includes("我的文档")')
    const authorized = await evaluate(`(async () => {
      const token = JSON.parse(sessionStorage.getItem('unexamine.system.tokens') || '{}').accessToken
      const response = await fetch('/api/business-attachments/${attachmentId}/download',
        { headers:{ Authorization:'Bearer ' + token } })
      return { status:response.status, text:response.ok ? await response.text() : '' }
    })()`)
    const anonymous = await evaluate(`(async () => {
      const response = await fetch('/api/business-attachments/${attachmentId}/download')
      return { status:response.status, code:(await response.json()).code }
    })()`)
    await send('Page.navigate', { url: base + '/platform' })
    await waitFor('[...document.querySelectorAll(".system-row")].some(x=>x.textContent.includes("发布验收业务乙"))')
    await evaluate(`(() => {
      const row = [...document.querySelectorAll('.system-row')].find(x=>x.textContent.includes('发布验收业务乙'))
      const enter = [...row.querySelectorAll('button')].find(x=>x.textContent.includes('进入'))
      if (!enter) throw new Error('Target system entry missing')
      enter.click()
    })()`)
    await waitFor('location.pathname === "/systems/2"')
    const crossSystem = await evaluate(`(async () => {
      const token = JSON.parse(sessionStorage.getItem('unexamine.system.tokens') || '{}').accessToken
      const response = await fetch('/api/business-attachments/${attachmentId}/download',
        { headers:{ Authorization:'Bearer ' + token } })
      return { status:response.status, code:(await response.json()).code }
    })()`)
    if (authorized.status !== 200 || authorized.text !== 'S19 current-candidate personal file center\n' ||
        anonymous.status !== 401 || crossSystem.status !== 404 ||
        crossSystem.code !== 'BUSINESS_ATTACHMENT_NOT_FOUND') {
      throw new Error('File center download authorization mismatch: ' + JSON.stringify({ authorized, anonymous, crossSystem }))
    }
    const evidence = { step:'S19.11', candidate:'0.1.0-s19.11-dev5', status:'file_center_authorized_anonymous_cross_system_denial',
      attachmentId, authorized, anonymous, crossSystem }
    writeFileSync('projects/unexamine/ai/evidence/s19-admin-file-denial-browser.json', JSON.stringify(evidence, null, 2) + '\n')
    console.log(JSON.stringify(evidence))
    socket.close()
  } else if (process.argv.includes('--verify-platform-app')) {
    const applicationName = 'S19 平台流程接入应用'
    await send('Page.navigate', { url: base + '/platform/admin/applications' })
    await waitFor('document.querySelector(".application-management-page") !== null')
    const platformRead = expression => evaluate(`(async () => {
      const token = JSON.parse(sessionStorage.getItem('unexamine.platform.tokens') || '{}').accessToken
      ${expression}
    })()`)
    let applications = await platformRead(`const response = await fetch('/api/applications', { headers: { Authorization: 'Bearer ' + token } })
      return response.ok ? (await response.json()).data : []`)
    let application = applications.find(x => x.name === applicationName)
    if (!application) {
      await click('.application-management-page button', '新建应用')
      await waitFor('[...document.querySelectorAll(".ant-modal-title")].some(x=>x.textContent.includes("新建受控访问应用"))')
      await fill('input[placeholder="例如：客户服务连接器"]', applicationName)
      await evaluate(`(() => {
        const modal = [...document.querySelectorAll('.ant-modal')].find(x=>x.querySelector('.ant-modal-title')?.textContent.includes('新建受控访问应用'))
        modal.querySelector('.ant-modal-footer .ant-btn-primary').click()
      })()`)
      await waitFor('document.querySelector(".application-secret") !== null')
      await evaluate(`(() => {
        const modal = [...document.querySelectorAll('.ant-modal')].find(x=>x.querySelector('.ant-modal-title')?.textContent.includes('一次性凭证明文'))
        modal.querySelector('.ant-modal-close').click()
      })()`)
      await waitFor(`document.querySelector('.application-hero')?.textContent.includes(${JSON.stringify(applicationName)})`)
      applications = await platformRead(`const response = await fetch('/api/applications', { headers: { Authorization: 'Bearer ' + token } })
        return response.ok ? (await response.json()).data : []`)
      application = applications.find(x => x.name === applicationName)
    } else {
      await waitFor(`[...document.querySelectorAll('.application-list__item')].some(x=>x.textContent.includes(${JSON.stringify(applicationName)}))`)
      await click('.application-list__item', applicationName)
    }
    if (!application?.currentVersionId) {
      const draftConfigured = await platformRead(`const response = await fetch('/api/applications/${application.id}',
        { headers: { Authorization: 'Bearer ' + token } })
        const app = response.ok ? (await response.json()).data : null
        return app?.grants?.some(x=>x.resourceId==='__flow_design__' && x.actionCode==='CREATE_DRAFT' &&
          x.dataScope?.memberIds?.length && x.rateLimit?.accessChannels?.includes('INTERNAL'))`)
      if (!draftConfigured) {
      await click('.application-actions button', '配置草稿')
      await waitFor('[...document.querySelectorAll(".ant-modal-title")].some(x=>x.textContent.includes("配置应用草稿"))')
      await waitFor('document.querySelector(".application-draft-card") !== null')
      await click('.application-draft-card .ant-checkbox-wrapper', '系统内访问')
      await evaluate(`(() => {
        const target = [...document.querySelectorAll('.application-draft-card .ant-form-item')]
          .find(x=>x.querySelector('.ant-form-item-label')?.textContent.trim()==='负责人范围')?.querySelector('.ant-select-selector')
        if (!target) throw new Error('Application member selector missing')
        target.dispatchEvent(new MouseEvent('mousedown', { bubbles:true, cancelable:true }))
      })()`)
      await waitFor('[...document.querySelectorAll(".ant-select-item-option")].some(x=>x.textContent.includes("平台超级管理员"))')
      await click('.ant-select-item-option', '平台超级管理员')
      await evaluate(`(() => {
        const modal = [...document.querySelectorAll('.ant-modal')].find(x=>x.querySelector('.ant-modal-title')?.textContent.includes('配置应用草稿'))
        modal.querySelector('.ant-modal-footer .ant-btn-primary').click()
      })()`)
      await waitFor('document.querySelector(".application-detail")?.textContent.includes("负责人范围：平台超级管理员")')
      }
      await click('.application-actions button', '检查并发布')
      await waitFor('document.querySelector(".application-hero")?.textContent.includes("已发布")')
    }
    const appId = application.id
    const result = await platformRead(`const headers = { Authorization: 'Bearer ' + token }
      const appResponse = await fetch('/api/applications/${appId}', { headers })
      const app = appResponse.ok ? (await appResponse.json()).data : null
      const scopeResponse = await fetch('/api/applications/${appId}/scope-catalog', { headers })
      const scope = scopeResponse.ok ? (await scopeResponse.json()).data : null
      const member = scope?.members?.find(x=>x.status==='ACTIVE' && x.name.includes('平台超级管理员'))
      if (!member) return { appStatus:appResponse.status, scopeStatus:scopeResponse.status, memberFound:false }
      const body = { resourceType:'FLOW', resourceId:'__flow_design__', actionCode:'CREATE_DRAFT',
        targetSystemId:null, targetTenantId:null,
        requestedDataScope:{type:'RESPONSIBLE', memberIds:[member.id], departmentIds:[]},
        payload:{code:'s19_platform_app_flow_' + Date.now(), name:'S19 平台应用内部流程'} }
      const callHeaders = { ...headers, 'Content-Type':'application/json', 'X-Idempotency-Key':'s19-platform-' + Date.now() }
      const acceptedResponse = await fetch('/api/application-internal-access/v1/applications/${appId}/calls',
        { method:'POST', headers:callHeaders, body:JSON.stringify(body) })
      const accepted = await acceptedResponse.json()
      const deniedResponse = await fetch('/api/application-internal-access/v1/applications/${appId}/calls',
        { method:'POST', headers:{...callHeaders, 'X-Idempotency-Key':'s19-platform-deny-' + Date.now()},
          body:JSON.stringify({...body, actionCode:'PUBLISH'}) })
      const denied = await deniedResponse.json()
      return { appStatus:appResponse.status, scopeStatus:scopeResponse.status, app:{id:app?.id,name:app?.name,status:app?.status,
        currentVersionId:app?.currentVersionId, grants:app?.publishedGrants?.map(x=>({resourceType:x.resourceType,resourceId:x.resourceId,actionCode:x.actionCode,channels:x.rateLimit?.accessChannels}))},
        accepted:{status:acceptedResponse.status,code:accepted.code,result:accepted.data?.result?.businessResult || accepted.data?.result},
        denied:{status:deniedResponse.status,code:denied.code} }
    `)
    if (result.appStatus !== 200 || !result.app?.currentVersionId ||
        !result.app.grants?.some(x=>x.resourceId==='__flow_design__' && x.actionCode==='CREATE_DRAFT') ||
        result.accepted?.status !== 200 || result.denied?.status !== 403 ||
        result.denied?.code !== 'APPLICATION_GRANT_DENIED') {
      throw new Error('Platform application publish/call/denial failed: ' + JSON.stringify(result))
    }
    const image = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
    writeFileSync('projects/unexamine/ai/evidence/s19-admin-platform-app-browser.png', Buffer.from(image.data, 'base64'))
    const evidence = { step:'S19.11', candidate:'0.1.0-s19.11-dev5', status:'platform_application_ui_publish_internal_call_and_denial', result }
    writeFileSync('projects/unexamine/ai/evidence/s19-admin-platform-app-browser.json', JSON.stringify(evidence, null, 2) + '\n')
    console.log(JSON.stringify(evidence))
    socket.close()
  } else if (process.argv.includes('--inspect-platform-app')) {
    await send('Page.navigate', { url: base + '/platform/admin/applications' })
    await waitFor('document.querySelector(".application-management-page") !== null')
    const result = await evaluate(`(async () => {
      const token = JSON.parse(sessionStorage.getItem('unexamine.platform.tokens') || '{}').accessToken
      const headers = { Authorization: 'Bearer ' + token }
      const applications = await fetch('/api/applications', { headers })
      const resources = await fetch('/api/applications/resources', { headers })
      return { applications: { status:applications.status, names:applications.ok ? (await applications.json()).data.map(x=>({id:x.id,name:x.name,status:x.status,currentVersionId:x.currentVersionId})) : [] },
        resources: {status:resources.status, rows:resources.ok ? (await resources.json()).data.map(x=>({type:x.resourceType,id:x.resourceId,name:x.name,actions:x.actions.map(a=>a.code)})) : [] },
        page:document.querySelector('.application-management-page')?.innerText.slice(0,500) }
    })()`)
    console.log(JSON.stringify(result))
    socket.close()
  } else if (process.argv.includes('--verify-system-dashboard')) {
    const candidate = process.env.S19_CANDIDATE_VERSION ?? '0.1.0-s19.11-dev4'
    const evidenceName = candidate.endsWith('-dev5') ? 's19-admin-system-dashboard-dev5-browser' : 's19-admin-system-dashboard-browser'
    const sourceName = 'S19 客户记录来源'
    const dashboardName = 'S19 客户经营首页'
    const componentTitle = '客户记录数量'
    await send('Page.navigate', { url: base + '/systems/1/admin?section=parameters' })
    await waitFor('[...document.querySelectorAll(".admin-subnav button")].some(x=>x.textContent.includes("首页配置"))')
    await click('.admin-subnav button', '首页配置')
    await waitFor('document.querySelector(".dashboard-configuration") !== null')
    const readAdmin = () => evaluate(`(async () => {
      const token = JSON.parse(sessionStorage.getItem('unexamine.system.tokens') || '{}').accessToken
      const response = await fetch('/api/analytics/admin', { headers: { Authorization: 'Bearer ' + token } })
      return response.ok ? (await response.json()).data : null
    })()`)
    const before = await readAdmin()
    if (!before?.dashboards?.some(x => x.name === dashboardName)) {
      const existingSource = before?.dataSources?.find(x => x.name === sourceName)
      if (existingSource) {
        await click('.dashboard-config-list button', sourceName)
      } else {
        await fill('.dashboard-config-form input[placeholder="例如：客户阶段分布"]', sourceName)
        await evaluate(`(() => { const selector = document.querySelectorAll('.dashboard-config-form .ant-select-selector')[1]
          selector.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true })) })()`)
        await waitFor('[...document.querySelectorAll(".ant-select-item-option")].some(x=>x.textContent.includes("客户"))')
        await click('.ant-select-item-option', '客户')
        await click('.dashboard-config-form button', '保存草稿')
        await waitFor(`document.querySelector('.dashboard-configuration .ant-alert-success')?.textContent.includes(${JSON.stringify(sourceName)})`)
      }
      if (!existingSource?.versions?.length) {
        await click('.dashboard-config-form button', '发布数据源')
        await waitFor('document.querySelector(".dashboard-version-strip")?.textContent.includes("第 1 版")')
      }
      await click('.dashboard-config-tabs .ant-tabs-tab', '2. 布局与发布')
      await waitFor('document.querySelector(".dashboard-component-editor") !== null')
      await fill('input[placeholder="经营工作台"]', dashboardName)
      await fill('.dashboard-component-editor input[placeholder="例如：客户阶段分布"]', componentTitle)
      await evaluate(`(() => { const selector = document.querySelectorAll('.dashboard-component-editor .ant-select-selector')[1]
        selector.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true })) })()`)
      await waitFor(`[...document.querySelectorAll('.ant-select-item-option')].some(x=>x.textContent.includes(${JSON.stringify(sourceName)}))`)
      await click('.ant-select-item-option', sourceName)
      await click('.dashboard-component-editor button', '加入布局')
      await waitFor(`document.querySelector('.dashboard-draft-grid')?.textContent.includes(${JSON.stringify(componentTitle)})`)
      await click('.dashboard-publish-actions button', '保存草稿')
      await waitFor(`document.querySelector('.dashboard-configuration .ant-alert-success')?.textContent.includes(${JSON.stringify(dashboardName)})`)
      await click('.dashboard-publish-actions button', '执行预览')
      await waitFor('document.querySelector(".dashboard-preview-panel")?.textContent.includes("可发布")')
      await click('.dashboard-publish-actions button', '发布新版本')
      await waitFor('[...document.querySelectorAll(".dashboard-version-strip")].some(x=>x.textContent.includes("当前使用"))')
    } else {
      await click('.dashboard-config-tabs .ant-tabs-tab', '2. 布局与发布')
      await waitFor(`[...document.querySelectorAll('.dashboard-config-list button')].some(x=>x.textContent.includes(${JSON.stringify(dashboardName)}))`)
      await click('.dashboard-config-list button', dashboardName)
    }
    const admin = await readAdmin()
    await send('Page.navigate', { url: base + '/systems/1' })
    await waitFor(`document.querySelector('.dashboard-runtime')?.textContent.includes(${JSON.stringify(componentTitle)})`)
    const runtime = await evaluate(`({text:document.querySelector('.dashboard-runtime')?.innerText.slice(0,500),
      errors:[...document.querySelectorAll('.dashboard-runtime .ant-alert-error')].map(x=>x.innerText)})`)
    const apiReadback = await evaluate(`(async () => {
      const token = JSON.parse(sessionStorage.getItem('unexamine.system.tokens') || '{}').accessToken
      const response = await fetch('/api/analytics/runtime', { headers: { Authorization: 'Bearer ' + token } })
      return { status: response.status, body: response.ok ? (await response.json()).data : null }
    })()`)
    const dashboard = admin?.dashboards?.find(x => x.name === dashboardName)
    if (runtime.errors.length || !dashboard?.versions?.some(x => x.current) || apiReadback.status !== 200 ||
        !apiReadback.body?.configured || apiReadback.body.name !== dashboardName ||
        !apiReadback.body.components?.some(x => x.title === componentTitle && x.outcome === 'READY')) {
      throw new Error('System dashboard publication/runtime disagreement: ' + JSON.stringify({ dashboard, runtime, apiReadback }))
    }
    const image = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
    writeFileSync('projects/unexamine/ai/evidence/' + evidenceName + '.png', Buffer.from(image.data, 'base64'))
    const evidence = { step: 'S19.11', candidate, status: 'system_dashboard_ui_published_and_runtime_read_back',
      sourceName, dashboardName, componentTitle, dashboard: { id:dashboard.id, status:dashboard.status,
        versions:dashboard.versions.map(x=>({ version:x.versionNumber, current:x.current })) }, runtime, apiReadback }
    writeFileSync('projects/unexamine/ai/evidence/' + evidenceName + '.json', JSON.stringify(evidence, null, 2) + '\n')
    console.log(JSON.stringify(evidence))
    socket.close()
  } else if (process.argv.includes('--inspect-platform-dashboard')) {
    await send('Page.navigate', { url: base + '/platform/admin/dashboards' })
    await waitFor('document.querySelector(".dashboard-configuration") !== null')
    const result = await evaluate(`(async () => {
      const token = JSON.parse(sessionStorage.getItem('unexamine.platform.tokens') || '{}').accessToken
      const response = await fetch('/api/analytics/admin', { headers: { Authorization: 'Bearer ' + token } })
      const body = response.ok ? (await response.json()).data : null
      return { status: response.status, sources: body?.dataSources?.map(x=>({id:x.id,name:x.name,status:x.status,versions:x.versions?.length})),
        dashboards: body?.dashboards?.map(x=>({id:x.id,name:x.name,status:x.status,versions:x.versions?.map(v=>({version:v.versionNumber,current:v.current})),draftRevision:x.draftRevision,components:x.components?.map(c=>c.title)})),
        page: document.querySelector('.dashboard-configuration')?.innerText.slice(0,900) }
    })()`)
    console.log(JSON.stringify(result))
    socket.close()
  } else if (process.argv.includes('--verify-platform-dashboard')) {
    const candidate = process.env.S19_CANDIDATE_VERSION ?? '0.1.0-s19.11-dev4'
    const evidenceName = candidate.endsWith('-dev5') ? 's19-admin-platform-dashboard-dev5-browser' : 's19-admin-platform-dashboard-browser'
    const sourceName = 'S19 平台系统来源'
    const dashboardName = 'S19 平台交付首页'
    const componentTitle = '可访问系统数量'
    await send('Page.navigate', { url: base + '/platform/admin/dashboards' })
    await waitFor('document.querySelector(".dashboard-configuration") !== null')
    const existing = await evaluate(`(async () => {
      const token = JSON.parse(sessionStorage.getItem('unexamine.platform.tokens') || '{}').accessToken
      const response = await fetch('/api/analytics/admin', { headers: { Authorization: 'Bearer ' + token } })
      return response.ok && (await response.json()).data.dashboards.some(x => x.name === ${JSON.stringify(dashboardName)})
    })()`)
    if (!existing) {
      const sourceExists = await evaluate(`[...document.querySelectorAll('.dashboard-config-list button')].some(x=>x.textContent.includes(${JSON.stringify(sourceName)}))`)
      if (sourceExists) {
        await click('.dashboard-config-list button', sourceName)
      } else {
        await fill('input[placeholder="例如：客户阶段分布"]', sourceName)
        await click('.dashboard-config-form button', '保存草稿')
        await waitFor(`document.querySelector('.dashboard-configuration .ant-alert-success')?.textContent.includes(${JSON.stringify(sourceName)})`)
      }
      const sourcePublished = await evaluate('document.querySelector(".dashboard-version-strip")?.textContent.includes("第 1 版")')
      if (!sourcePublished) {
        await click('.dashboard-config-form button', '发布数据源')
        await waitFor('document.querySelector(".dashboard-version-strip")?.textContent.includes("第 1 版")')
      }
      await click('.dashboard-config-tabs .ant-tabs-tab', '2. 布局与发布')
      await waitFor('document.querySelector(".dashboard-component-editor") !== null')
      await fill('input[placeholder="经营工作台"]', dashboardName)
      await fill('.dashboard-component-editor input[placeholder="例如：客户阶段分布"]', componentTitle)
      await evaluate(`(() => { const selector = document.querySelectorAll('.dashboard-component-editor .ant-select-selector')[1]
        selector.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true }))
        selector.click() })()`)
      console.log(JSON.stringify({ stage: 'dashboard_select', ui: await evaluate(`({
        selectors:[...document.querySelectorAll('.dashboard-component-editor .ant-select-selector')].map(x=>x.parentElement?.innerText),
        sourceList:document.querySelector('.dashboard-config-list')?.innerText,
        options:[...document.querySelectorAll('.ant-select-item-option')].map(x=>x.innerText),
        alert:document.querySelector('.dashboard-configuration .ant-alert-error')?.innerText
      })`) }))
      await waitFor(`[...document.querySelectorAll('.ant-select-item-option')].some(x=>x.textContent.includes(${JSON.stringify(sourceName)}))`)
      await click('.ant-select-item-option', sourceName)
      await click('.dashboard-component-editor button', '加入布局')
      console.log(JSON.stringify({ stage: 'dashboard_add', ui: await evaluate(`({
        componentName:document.querySelector('.dashboard-component-editor input[placeholder="例如：客户阶段分布"]')?.value,
        selects:[...document.querySelectorAll('.dashboard-component-editor .ant-select-selection-item')].map(x=>x.innerText),
        alert:document.querySelector('.dashboard-configuration .ant-alert-error')?.innerText,
        draft:document.querySelector('.dashboard-draft-grid')?.innerText
      })`) }))
      await waitFor(`document.querySelector('.dashboard-draft-grid')?.textContent.includes(${JSON.stringify(componentTitle)})`)
      await click('.dashboard-publish-actions button', '保存草稿')
      await waitFor(`document.querySelector('.dashboard-configuration .ant-alert-success')?.textContent.includes(${JSON.stringify(dashboardName)})`)
      await click('.dashboard-publish-actions button', '执行预览')
      await waitFor('document.querySelector(".dashboard-preview-panel")?.textContent.includes("可发布")')
      await click('.dashboard-publish-actions button', '发布新版本')
      await waitFor('[...document.querySelectorAll(".dashboard-version-strip")].some(x=>x.textContent.includes("当前使用"))')
    } else {
      await click('.dashboard-config-tabs .ant-tabs-tab', '2. 布局与发布')
      await waitFor(`[...document.querySelectorAll('.dashboard-config-list button')].some(x=>x.textContent.includes(${JSON.stringify(dashboardName)}))`)
      await click('.dashboard-config-list button', dashboardName)
      const published = await evaluate('[...document.querySelectorAll(".dashboard-version-strip")].some(x=>x.textContent.includes("当前使用"))')
      if (!published) {
        await click('.dashboard-publish-actions button', '执行预览')
        await waitFor('document.querySelector(".dashboard-preview-panel")?.textContent.includes("可发布")')
        await click('.dashboard-publish-actions button', '发布新版本')
        await waitFor('[...document.querySelectorAll(".dashboard-version-strip")].some(x=>x.textContent.includes("当前使用"))')
      }
    }
    const admin = await evaluate(`({
      source: [...document.querySelectorAll('.dashboard-config-list button')].map(x=>x.innerText).filter(x=>x.includes(${JSON.stringify(dashboardName)})),
      version: document.querySelector('.dashboard-version-strip')?.innerText,
      preview: document.querySelector('.dashboard-preview-panel')?.innerText.slice(0,300),
      errors: [...document.querySelectorAll('.dashboard-configuration .ant-alert-error')].map(x=>x.innerText)
    })`)
    await send('Page.navigate', { url: base + '/platform/dashboard' })
    await waitFor(`document.querySelector('.dashboard-runtime')?.textContent.includes(${JSON.stringify(componentTitle)})`)
    const runtime = await evaluate(`({text:document.querySelector('.dashboard-runtime')?.innerText.slice(0,650),
      errors:[...document.querySelectorAll('.dashboard-runtime .ant-alert-error')].map(x=>x.innerText)})`)
    const apiReadback = await evaluate(`(async () => {
      const token = JSON.parse(sessionStorage.getItem('unexamine.platform.tokens') || '{}').accessToken
      const response = await fetch('/api/analytics/runtime', { headers: { Authorization: 'Bearer ' + token } })
      return { status: response.status, body: response.ok ? (await response.json()).data : null }
    })()`)
    if (admin.errors.length || runtime.errors.length || apiReadback.status !== 200 ||
        !apiReadback.body?.configured || apiReadback.body?.name !== dashboardName ||
        !apiReadback.body.components?.some(x => x.title === componentTitle && x.outcome === 'READY')) {
      throw new Error('Published platform dashboard did not match runtime readback: ' + JSON.stringify({ admin, runtime, apiReadback }))
    }
    const image = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
    writeFileSync('projects/unexamine/ai/evidence/' + evidenceName + '.png', Buffer.from(image.data, 'base64'))
    const evidence = { step: 'S19.11', candidate, status: 'platform_dashboard_ui_published_and_runtime_read_back',
      sourceName, dashboardName, componentTitle, admin, runtime, apiReadback }
    writeFileSync('projects/unexamine/ai/evidence/' + evidenceName + '.json', JSON.stringify(evidence, null, 2) + '\n')
    console.log(JSON.stringify(evidence))
    socket.close()
  } else if (process.argv.includes('--verify-operations')) {
    const candidate = process.env.S19_CANDIDATE_VERSION ?? '0.1.0-s19.11-dev3'
    const evidenceName = candidate.endsWith('-dev3') ? 's19-admin-operations-browser'
      : 's19-admin-operations-' + candidate.split('-').at(-1) + '-browser'
    await send('Page.navigate', { url: base + '/systems/1/admin?section=operations' })
    await waitFor('document.querySelector(".operations-health__hero") !== null')
    await click('.operations-health__hero button', '立即重检并读回')
    await waitFor('document.querySelector(".operations-health .ant-alert-success")?.textContent.includes("5 项关键依赖全部通过")')
    await waitFor('document.querySelector(".continuity-hero")?.textContent.includes("DB V23")')
    const page = await evaluate(`({
      health: document.querySelector('.operations-health__hero')?.innerText,
      dependencyStatuses: [...document.querySelectorAll('.operations-dependency-grid article')].map(x => x.getAttribute('data-status')),
      metrics: document.querySelector('.operations-health__metrics')?.innerText,
      deployment: document.querySelector('.deployment-manifest')?.innerText,
      continuity: document.querySelector('.continuity-hero')?.innerText,
      errors: [...document.querySelectorAll('.operations-health .ant-alert-error, .operations-deployment .ant-alert-error, .operations-continuity .ant-alert-error')].map(x => x.innerText)
    })`)
    const apiReadback = await evaluate(`(async () => {
      const token = JSON.parse(sessionStorage.getItem('unexamine.system.tokens') || '{}').accessToken
      const headers = { Authorization: 'Bearer ' + token }
      const paths = ['/api/admin/system/operations/health', '/api/admin/system/operations/deployments', '/api/admin/system/operations/continuity']
      return Promise.all(paths.map(async path => {
        const response = await fetch(path, { headers })
        return { path, status: response.status, body: response.ok ? (await response.json()).data : null }
      }))
    })()`)
    const [health, deployment, continuity] = apiReadback.map(x => x.body)
    if (page.dependencyStatuses.length !== 5 || page.dependencyStatuses.some(x => x !== 'PASSED') ||
        !health?.ready || health.items?.length !== 5 || health.items.some(x => x.status !== 'PASSED') ||
        !page.health.includes(String(health.requestId)) || !page.metrics.includes('V23') ||
        deployment?.runtime?.backendVersion !== candidate ||
        !page.deployment.includes(candidate) || continuity?.runtimeDatabaseVersion !== '23' ||
        page.errors.length || apiReadback.some(x => x.status !== 200)) {
      throw new Error('Operations UI, health probe and runtime API did not agree: ' + JSON.stringify({ page, apiStatuses: apiReadback.map(x => x.status) }))
    }
    const image = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
    writeFileSync('projects/unexamine/ai/evidence/' + evidenceName + '.png', Buffer.from(image.data, 'base64'))
    const evidence = { step: 'S19.11', candidate, status: 'operations_ui_health_readback_verified',
      page, apiStatuses: apiReadback.map(x => ({ path: x.path, status: x.status })),
      health: { id: health.id, ready: health.ready, requestId: health.requestId,
        checks: health.items.map(x => ({ name: x.name, status: x.status })), migrationVersion: health.migrationVersion },
      runtime: { backendVersion: deployment.runtime.backendVersion, databaseVersion: deployment.runtime.databaseVersion,
        frontendSmokeUrl: deployment.runtime.frontendSmokeUrl }, continuity: { runtimeDatabaseVersion: continuity.runtimeDatabaseVersion } }
    writeFileSync('projects/unexamine/ai/evidence/' + evidenceName + '.json', JSON.stringify(evidence, null, 2) + '\n')
    console.log(JSON.stringify(evidence))
    socket.close()
  } else if (process.argv.includes('--inspect-audit') || process.argv.includes('--verify-audit')) {
    await send('Page.navigate', { url: base + '/systems/1/admin?section=audit' })
    await waitFor('document.querySelector(".audit-table") !== null')
    await waitFor('document.querySelectorAll(".audit-table .ant-table-row").length > 0')
    const audit = await evaluate(`({rows:[...document.querySelectorAll('.audit-table .ant-table-row')].map(x=>x.innerText.slice(0,250)),
      filters:[...document.querySelectorAll('.audit-filters input')].map(x=>x.placeholder),
      errors:[...document.querySelectorAll('.audit-page .ant-alert-error')].map(x=>x.innerText)})`)
    console.log(JSON.stringify({ stage: 'audit_inspection', audit }))
    if (process.argv.includes('--verify-audit')) {
      await fill('input[placeholder="动作 / 事件编码"]', 'MODULE_IMPORT_COMPLETED')
      await click('.audit-filters button', '查询')
      await waitFor('document.querySelector(".audit-table .ant-table-row")?.textContent.includes("MODULE_IMPORT_COMPLETED")')
      const filtered = await evaluate(`({rows:[...document.querySelectorAll('.audit-table .ant-table-row')].map(x=>x.innerText),
        count:document.querySelectorAll('.audit-table .ant-table-row').length})`)
      if (filtered.rows.some(row => !row.includes('MODULE_IMPORT_COMPLETED')) || !filtered.rows[0].includes('IMPORT_BATCH')) {
        throw new Error('Import audit query returned an unexpected event')
      }
      await click('.audit-table .ant-table-row button', '详情')
      await waitFor('document.querySelector(".audit-detail-drawer")?.textContent.includes("MODULE_IMPORT_COMPLETED")')
      const detail = await evaluate(`({summary:document.querySelector('.audit-detail-summary')?.innerText.slice(0,650),
        fields:document.querySelector('.audit-field-changes')?.innerText.slice(0,400)})`)
      const image = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
      writeFileSync('projects/unexamine/ai/evidence/s19-admin-audit-browser.png', Buffer.from(image.data, 'base64'))
      const evidence = { step: 'S19.11', candidate: process.env.S19_CANDIDATE_VERSION, status: 'import_audit_ui_query_and_detail_verified', filtered, detail }
      writeFileSync('projects/unexamine/ai/evidence/s19-admin-audit-browser.json', JSON.stringify(evidence, null, 2) + '\n')
      console.log(JSON.stringify(evidence))
    }
    socket.close()
  } else if (process.argv.includes('--verify-file-center')) {
    await send('Page.navigate', { url: base + '/systems/1?workspace=files' })
    await waitFor('document.body.innerText.includes("我的文档")')
    const fileName = 's19-file-center-20261016.txt'
    const fileContent = 'S19 current-candidate personal file center\n'
    const filePath = process.env.TEMP + '\\' + fileName
    writeFileSync(filePath, fileContent)
    const existed = await evaluate(`document.body.innerText.includes(${JSON.stringify(fileName)})`)
    if (!existed) {
      const documentNode = await send('DOM.getDocument')
      const inputNode = await send('DOM.querySelector', { nodeId: documentNode.root.nodeId, selector: '#personal-document-input' })
      if (!inputNode.nodeId) throw new Error('Personal document input missing')
      await send('DOM.setFileInputFiles', { nodeId: inputNode.nodeId, files: [filePath] })
      await waitFor(`document.querySelector('.file-business-card')?.textContent.includes(${JSON.stringify(fileName)})`)
    }
    const fileReadback = await evaluate(`(async () => {
      const token = JSON.parse(sessionStorage.getItem('unexamine.system.tokens') || '{}').accessToken
      const response = await fetch('/api/business-attachments/personal', { headers: { Authorization: 'Bearer ' + token } })
      if (!response.ok) return { listStatus: response.status }
      const file = (await response.json()).data.find(x=>x.originalName===${JSON.stringify(fileName)})
      if (!file) return { listStatus: response.status, found: false }
      const download = await fetch('/api/business-attachments/' + file.attachmentId + '/download',
        { headers: { Authorization: 'Bearer ' + token } })
      return { listStatus: response.status, attachmentId:file.attachmentId,
        scanStatus:file.scanStatus, sizeBytes:file.sizeBytes,
        downloadStatus:download.status, content:download.ok ? await download.text() : '' }
    })()`)
    if (fileReadback.scanStatus !== 'CLEAN' || fileReadback.downloadStatus !== 200 || fileReadback.content !== fileContent) {
      throw new Error('Personal file center upload/download byte readback failed')
    }
    const image = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
    writeFileSync('projects/unexamine/ai/evidence/s19-admin-file-center-browser.png', Buffer.from(image.data, 'base64'))
    const evidence = { step: 'S19.11', candidate: '0.1.0-s19.11-dev3', status: 'personal_file_center_ui_and_byte_readback',
      page: '/systems/1?workspace=files', fileName, fileReadback }
    writeFileSync('projects/unexamine/ai/evidence/s19-admin-file-center-browser.json', JSON.stringify(evidence, null, 2) + '\n')
    console.log(JSON.stringify(evidence))
    socket.close()
  } else if (process.argv.includes('--verify-role-revocation')) {
    await send('Page.navigate', { url: base + '/systems/1/admin?section=organization' })
    await waitFor('[...document.querySelectorAll(".authorization-member-row")].some(x=>x.textContent.includes("s19_invitee"))')
    const roleName = 'S19 页面验收消息角色'
    const authorizationExpression = `(async () => {
      const token = JSON.parse(sessionStorage.getItem('unexamine.system.tokens') || '{}').accessToken
      const headers = { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' }
      const response = await fetch('/api/admin/system/authorization', { headers })
      if (!response.ok) return { overviewStatus: response.status }
      const overview = (await response.json()).data
      const role = overview.roles.find(x=>x.name===${JSON.stringify(roleName)})
      const member = overview.members.find(x=>x.account==='s19_invitee' || x.displayName==='s19_invitee')
      if (!role || !member) return { overviewStatus: response.status, roleFound:!!role, memberFound:!!member }
      const previewResponse = await fetch('/api/admin/system/authorization/preview', {
        method:'POST', headers, body:JSON.stringify({tenantMemberId:member.tenantMemberId,
          resourceCode:'SYSTEM', actionCode:'VIEW', expectedPermissionVersion:overview.permissionVersion}) })
      const preview = previewResponse.ok ? (await previewResponse.json()).data : null
      const messageView = preview?.actions.find(x=>x.resourceType==='MESSAGE'
        && x.resourceCode==='SYSTEM' && x.actionCode==='VIEW')
      return {overviewStatus:response.status, previewStatus:previewResponse.status,
        roleId:role.id, memberId:member.tenantMemberId, memberRoleIds:member.roleIds,
        permissionVersion:overview.permissionVersion, messageViewAllowed:messageView?.allowed ?? false,
        contributingRoleIds:messageView?.contributingRoleIds ?? []}
    })()`
    const readAuthorization = async () => evaluate(authorizationExpression)
    const before = await readAuthorization()
    if (before.overviewStatus!==200 || before.previewStatus!==200
      || !before.memberRoleIds.includes(before.roleId) || before.messageViewAllowed!==true) {
      throw new Error('Invited member initial message grant is unavailable')
    }
    await evaluate(`(() => {
      const row = [...document.querySelectorAll('.authorization-member-row')].find(x=>x.textContent.includes('s19_invitee'))
      const field = [...row.querySelectorAll('.authorization-member-field')].find(x=>x.textContent.includes('角色'))
      const chip = [...field.querySelectorAll('.ant-select-selection-item')]
        .find(x=>x.textContent.includes(${JSON.stringify(roleName)}))
      const remove = chip?.querySelector('.ant-select-selection-item-remove')
      if (!remove) throw new Error('Invited member role remove control missing')
      remove.click()
      row.querySelector('.authorization-member-save')?.click()
    })()`)
    const revoked = await waitFor(`(async () => {
      const result = await (${authorizationExpression})
      return result.overviewStatus===200 && !result.memberRoleIds.includes(${before.roleId}) ? result : null
    })()`)
    await evaluate(`(() => {
      const row = [...document.querySelectorAll('.authorization-member-row')].find(x=>x.textContent.includes('s19_invitee'))
      const field = [...row.querySelectorAll('.authorization-member-field')].find(x=>x.textContent.includes('角色'))
      const selector = field?.querySelector('.ant-select-selector')
      if (!selector) throw new Error('Invited member role selector missing for restoration')
      selector.dispatchEvent(new MouseEvent('mousedown', {bubbles:true,cancelable:true}))
      selector.dispatchEvent(new MouseEvent('mouseup', {bubbles:true,cancelable:true}))
      selector.click()
    })()`)
    await waitFor('[...document.querySelectorAll(".ant-select-item-option")].some(x=>x.textContent.includes("S19 页面验收消息角色"))')
    await click('.ant-select-item-option', roleName)
    await evaluate(`(() => {
      const row = [...document.querySelectorAll('.authorization-member-row')].find(x=>x.textContent.includes('s19_invitee'))
      const save = row?.querySelector('.authorization-member-save')
      if (!save || save.disabled) throw new Error('Invited member restore save control missing')
      save.click()
    })()`)
    const restored = await waitFor(`(async () => {
      const result = await (${authorizationExpression})
      return result.overviewStatus===200 && result.memberRoleIds.includes(${before.roleId})
        && result.permissionVersion>${revoked.permissionVersion} ? result : null
    })()`)
    const evidence = {step:'S19.11', candidate:process.env.S19_CANDIDATE_VERSION,
      status:'admin_role_revoked_and_restored', before, revoked, restored}
    writeFileSync('projects/unexamine/ai/evidence/s19-admin-role-revocation-browser.json', JSON.stringify(evidence,null,2)+'\n')
    if (revoked.previewStatus!==200 || revoked.messageViewAllowed!==false
      || restored.previewStatus!==200 || restored.messageViewAllowed!==true
      || !restored.contributingRoleIds.includes(before.roleId)) {
      throw new Error('Role revocation/restoration permission preview mismatch')
    }
    console.log(JSON.stringify(evidence))
    socket.close()
  } else if (process.argv.includes('--assign-role')) {
    await send('Page.navigate', { url: base + '/systems/1/admin?section=organization' })
    await waitFor('[...document.querySelectorAll(".authorization-member-row")].some(x=>x.textContent.includes("s19_invitee"))')
    const before = await evaluate(`(() => {
      const row = [...document.querySelectorAll('.authorization-member-row')].find(x=>x.textContent.includes('s19_invitee'))
      return {row:row?.innerText, selects:[...(row?.querySelectorAll('.ant-select')||[])].map(x=>x.innerText)}
    })()`)
    console.log(JSON.stringify({ stage: 'invitee_before', before }))
    if (!before.row.includes('S19 页面验收消息角色')) {
      await evaluate(`(() => {
      const row = [...document.querySelectorAll('.authorization-member-row')].find(x=>x.textContent.includes('s19_invitee'))
      const field = [...row.querySelectorAll('.authorization-member-field')].find(x=>x.textContent.includes('角色'))
      const selector = field?.querySelector('.ant-select-selector')
      if (!selector) throw new Error('Invitee role selector missing')
      selector.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true }))
      selector.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true }))
      selector.click()
    })()`)
      await waitFor('[...document.querySelectorAll(".ant-select-item-option")].some(x=>x.textContent.includes("S19 页面验收消息角色"))')
      await click('.ant-select-item-option', 'S19 页面验收消息角色')
      await evaluate(`(() => {
      const row = [...document.querySelectorAll('.authorization-member-row')].find(x=>x.textContent.includes('s19_invitee'))
      const button = row?.querySelector('.authorization-member-save')
      if (!button || button.disabled) throw new Error('Invitee save button missing')
      button.click()
    })()`)
    }
    const roleReadback = await waitFor(`(async () => {
      const token = JSON.parse(sessionStorage.getItem('unexamine.system.tokens') || '{}').accessToken
      const response = await fetch('/api/admin/system/authorization', { headers: { Authorization: 'Bearer ' + token } })
      if (!response.ok) return null
      const overview = (await response.json()).data
      const role = overview.roles.find(x=>x.name === 'S19 页面验收消息角色')
      const member = overview.members.find(x=>x.account === 's19_invitee' || x.displayName === 's19_invitee')
      const permission = role?.permissions.find(x=>x.resourceType==='MESSAGE' && x.actionCode==='VIEW')
      return role?.status === 'ACTIVE' && member?.roleIds.includes(role.id) && permission
        ? {roleId:role.id, roleStatus:role.status, memberId:member.tenantMemberId,
          memberRoles:member.roleIds, permission} : null
    })()`)
    console.log(JSON.stringify({ stage: 'role_readback', roleReadback }))
    if (!roleReadback.memberRoles?.includes(roleReadback.roleId) || roleReadback.roleStatus !== 'ACTIVE' || !roleReadback.permission) {
      throw new Error('Published role was not assigned to the invited member')
    }
    const permissionPreview = await evaluate(`(async () => {
      const token = JSON.parse(sessionStorage.getItem('unexamine.system.tokens') || '{}').accessToken
      const headers = { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' }
      const overview = (await (await fetch('/api/admin/system/authorization', { headers })).json()).data
      const response = await fetch('/api/admin/system/authorization/preview', {
        method: 'POST', headers, body: JSON.stringify({ tenantMemberId: ${roleReadback.memberId},
          resourceCode: 'SYSTEM', actionCode: 'VIEW', expectedPermissionVersion: overview.permissionVersion }) })
      if (!response.ok) return { status: response.status }
      const preview = (await response.json()).data
      return { status: response.status, target: preview.targetMember.displayName,
        roleIds: preview.roles.map(x=>x.roleId),
        messageView: preview.actions.find(x=>x.resourceType==='MESSAGE' && x.resourceCode==='SYSTEM' && x.actionCode==='VIEW') }
    })()`)
    if (permissionPreview.status !== 200 || !permissionPreview.roleIds.includes(roleReadback.roleId)
      || permissionPreview.messageView?.allowed !== true) {
      throw new Error('Effective invited-member permission preview did not include message view')
    }
    const image = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
    writeFileSync('projects/unexamine/ai/evidence/s19-admin-role-assignment-browser.png', Buffer.from(image.data, 'base64'))
    const evidence = { step: 'S19.11', candidate: '0.1.0-s19.11-dev3', status: 'admin_role_assigned_and_effective_permission_previewed', roleReadback, permissionPreview }
    writeFileSync('projects/unexamine/ai/evidence/s19-admin-role-assignment-browser.json', JSON.stringify(evidence, null, 2) + '\n')
    console.log(JSON.stringify(evidence))
    socket.close()
  } else if (process.argv.includes('--inspect-flow') || process.argv.includes('--publish-flow')) {
    await send('Page.navigate', { url: base + '/systems/1/admin?section=flow' })
    await waitFor('document.querySelectorAll(".flow-list-item").length > 0')
    const flows = await evaluate(`({list:[...document.querySelectorAll('.flow-list-item')].map(x=>x.innerText),
      nodes:[...document.querySelectorAll('.flow-visual-node')].map(x=>x.innerText),
      toolbar:document.querySelector('.flow-toolbar')?.innerText,
      versions:document.querySelector('.flow-version-panel')?.innerText,
      alerts:[...document.querySelectorAll('.flow-designer-page .ant-alert')].map(x=>x.innerText.slice(0,350)),
      issues:document.querySelector('.flow-issue-panel')?.innerText})`)
    console.log(JSON.stringify(flows))
    if (process.argv.includes('--publish-flow')) {
      const currentVersions = await evaluate('document.querySelector(".flow-version-panel")?.innerText || ""')
      if (!currentVersions.includes('第 2 版')) {
        await evaluate(`(() => {
          const node = [...document.querySelectorAll('.flow-visual-node')]
            .find(x => x.textContent.includes('客户审批') || x.textContent.includes('S19 页面复核审批'))
          if (!node) throw new Error('Approval node is missing')
          node.click()
        })()`)
        await waitFor('document.querySelector(".flow-inspector input") !== null')
        const inspector = await evaluate('document.querySelector(".flow-inspector")?.innerText.slice(0,600)')
        console.log(JSON.stringify({ stage: 'flow_inspector', inspector }))
        await fill('.flow-inspector input', 'S19 页面复核审批')
        await click('.flow-toolbar button', '保存草稿')
        await waitFor('[...document.querySelectorAll(".ant-message-notice")].some(x=>x.textContent.includes("保存"))')
        await click('.flow-toolbar button', '发布版本')
        await waitFor('[...document.querySelectorAll(".ant-modal input")].some(x=>x.placeholder.includes("首版合同审批"))')
        await fill('.ant-modal input[placeholder="例如：首版合同审批"]', 'S19 页面修改审批节点并发布')
        await click('.ant-modal button.ant-btn-primary', '')
        await waitFor('document.querySelector(".flow-version-panel")?.innerText.includes("第 2 版")')
      }
      const published = await evaluate(`({list:document.querySelector('.flow-list-item')?.innerText,
        nodes:[...document.querySelectorAll('.flow-visual-node')].map(x=>x.innerText),
        versions:document.querySelector('.flow-version-panel')?.innerText,
        issues:document.querySelector('.flow-issue-panel')?.innerText || ''})`)
      if (!published.nodes.some(node => node.includes('S19 页面复核审批')) || !published.versions.includes('第 2 版')) {
        throw new Error('Approval Flow version was not read back')
      }
      const image = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
      writeFileSync('projects/unexamine/ai/evidence/s19-admin-flow-browser.png', Buffer.from(image.data, 'base64'))
      const evidence = { step: 'S19.11', candidate: '0.1.0-s19.11-dev3', status: 'approval_flow_ui_published', published }
      writeFileSync('projects/unexamine/ai/evidence/s19-admin-flow-browser.json', JSON.stringify(evidence, null, 2) + '\n')
      console.log(JSON.stringify(evidence))
    }
    socket.close()
  } else if (process.argv.includes('--inspect-binding') || process.argv.includes('--publish-binding') || process.argv.includes('--capture-binding')) {
    await send('Page.navigate', { url: base + '/systems/1/admin?section=flow&flowSection=bindings&module=module_ae3286711c&moduleTask=FLOW&triggerEvent=MANUAL' })
    await waitFor('document.querySelector(".flow-binding-submit") !== null')
    await waitFor('document.body.innerText.includes("发起审批") && !document.querySelector(".flow-binding-submit")?.disabled')
    const binding = await evaluate(`({text:document.body.innerText.slice(-2600),
      submit:document.querySelector('.flow-binding-submit')?.innerText,
      disabled:document.querySelector('.flow-binding-submit')?.disabled,
      selects:[...document.querySelectorAll('.flow-binding-page .ant-select')].map(x=>x.innerText.slice(0,100))})`)
    console.log(JSON.stringify({ stage: 'binding_before', binding }))
    if (process.argv.includes('--publish-binding')) {
      await click('.flow-binding-submit', '发布动作配置')
      await waitFor('document.querySelector(".ant-modal-confirm") !== null')
      const impact = await evaluate('document.querySelector(".ant-modal-confirm")?.innerText')
      if (!impact.includes('旧绑定会保留')) throw new Error('Binding replacement impact is missing')
      await click('.ant-modal-confirm button', '停用旧绑定并发布')
      await waitFor('document.body.innerText.includes("第 2 版") && document.body.innerText.includes("系统公共已停用")')
      const published = await evaluate(`({text:document.body.innerText.slice(-1100),
        notices:[...document.querySelectorAll('.ant-message-notice')].map(x=>x.innerText)})`)
      const image = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
      writeFileSync('projects/unexamine/ai/evidence/s19-admin-binding-browser.png', Buffer.from(image.data, 'base64'))
      const evidence = { step: 'S19.11', candidate: '0.1.0-s19.11-dev3', status: 'approval_binding_ui_republished', impact, published }
      writeFileSync('projects/unexamine/ai/evidence/s19-admin-binding-browser.json', JSON.stringify(evidence, null, 2) + '\n')
      console.log(JSON.stringify(evidence))
    }
    if (process.argv.includes('--capture-binding')) {
      if (!binding.text.includes('系统公共启用') || !binding.text.includes('系统公共已停用') || !binding.text.includes('第 2 版')) {
        throw new Error('The current Flow binding and retained old binding were not read back')
      }
      const image = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
      writeFileSync('projects/unexamine/ai/evidence/s19-admin-binding-browser.png', Buffer.from(image.data, 'base64'))
      const evidence = { step: 'S19.11', candidate: '0.1.0-s19.11-dev3', status: 'approval_binding_ui_republished', activeVersion: 2,
        priorBindingRetainedDisabled: true, readback: binding.text.slice(-1000) }
      writeFileSync('projects/unexamine/ai/evidence/s19-admin-binding-browser.json', JSON.stringify(evidence, null, 2) + '\n')
      console.log(JSON.stringify(evidence))
    }
    socket.close()
  } else if (process.argv.includes('--inspect-action') || process.argv.includes('--publish-action') || process.argv.includes('--capture-action') || process.argv.includes('--inspect-runtime') || process.argv.includes('--execute-action') || process.argv.includes('--inspect-import') || process.argv.includes('--execute-import')) {
    await send('Page.navigate', { url: base + '/systems/1/admin?section=templates&module=module_ae3286711c&moduleTask=ACTIONS' })
    await waitFor('document.querySelectorAll(".action-config-row").length > 0')
    const actions = await evaluate(`({rows:[...document.querySelectorAll('.action-config-row')].map(x=>x.innerText),
      heading:document.querySelector('.designer-heading')?.innerText,
      tabs:document.querySelector('.config-task-tabs')?.innerText,
      page:location.pathname+location.search})`)
    console.log(JSON.stringify(actions))
    if (process.argv.includes('--capture-action')) {
      if (!actions.rows.some(row => row.includes('标记已联系'))) throw new Error('Published action is missing from module editor')
      await click('.config-task-tabs button', '模块信息')
      await click('.designer-subtabs button', '发布与版本')
      await waitFor('[...document.querySelectorAll(".module-version-row")].some(x=>x.textContent.includes("版本 4") && x.textContent.includes("当前运行版本"))')
      const publication = await evaluate(`({version:[...document.querySelectorAll('.module-version-row')].find(x=>x.textContent.includes('当前运行版本'))?.innerText,
        action:'标记已联系'})`)
      const image = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
      writeFileSync('projects/unexamine/ai/evidence/s19-admin-action-browser.png', Buffer.from(image.data, 'base64'))
      await send('Page.navigate', { url: base + '/systems/1?workspace=runtime&module=module_ae3286711c' })
      await waitFor('document.body.innerText.includes("S19 手机创建客户") && document.body.innerText.includes("标记已联系")')
      const runtime = await evaluate(`({row:[...document.querySelectorAll('tr')].find(x=>x.textContent.includes('S19 手机创建客户'))?.innerText,
        actionVisible:document.body.innerText.includes('标记已联系')})`)
      if (!runtime.row?.includes('已联系') || !runtime.actionVisible) throw new Error('Action runtime readback is incomplete')
      const evidence = { step: 'S19.11', candidate: '0.1.0-s19.11-dev3', status: 'module_action_ui_published_and_runtime_read_back', publication, runtime }
      writeFileSync('projects/unexamine/ai/evidence/s19-admin-action-browser.json', JSON.stringify(evidence, null, 2) + '\n')
      console.log(JSON.stringify(evidence))
    } else if (process.argv.includes('--inspect-runtime') || process.argv.includes('--execute-action') || process.argv.includes('--inspect-import') || process.argv.includes('--execute-import')) {
      await send('Page.navigate', { url: base + '/systems/1?workspace=runtime&module=module_ae3286711c' })
      await waitFor('document.body.innerText.includes("S19 上海客户")')
      const runtime = await evaluate(`({text:document.body.innerText.slice(-1800),
        buttons:[...document.querySelectorAll('button')].map(x=>x.textContent.trim()).filter(Boolean).slice(-80),
        actionVisible:document.body.innerText.includes('标记已联系')})`)
      console.log(JSON.stringify({ stage: 'runtime_inspection', runtime }))
      if (process.argv.includes('--inspect-import') || process.argv.includes('--execute-import')) {
        await click('button', '导 入')
        await delay(500)
        const importer = await evaluate(`({modals:[...document.querySelectorAll('.ant-modal')].map(x=>x.innerText.slice(0,1600)),
          drawers:[...document.querySelectorAll('.ant-drawer')].map(x=>x.innerText.slice(0,1600)),
          tail:document.body.innerText.slice(-1300),
          files:[...document.querySelectorAll('input[type=file]')].map(x=>x.outerHTML),
          buttons:[...document.querySelectorAll('.ant-modal button')].map(x=>({text:x.innerText,disabled:x.disabled}))})`)
        console.log(JSON.stringify({ stage: 'import_modal', importer }))
        if (process.argv.includes('--execute-import')) {
          const csvPath = process.env.TEMP + '\\s19-ui-customer-import.csv'
          writeFileSync(csvPath, '\ufefftitle,field_bc62d57501\r\nS19 页面导入客户,页面导入已验\r\n,无标题应拒绝\r\n')
          const countRecords = `(async () => {
            const token = JSON.parse(sessionStorage.getItem('unexamine.system.tokens') || '{}').accessToken
            const response = await fetch('/api/runtime/modules/module_ae3286711c/records?page=1&pageSize=20',
              { headers: { Authorization: 'Bearer ' + token } })
            if (!response.ok) throw new Error('Record count read failed: ' + response.status)
            return (await response.json()).data.total
          })()`
          const beforeCount = await evaluate(countRecords)
          const documentNode = await send('DOM.getDocument')
          const inputNode = await send('DOM.querySelector', { nodeId: documentNode.root.nodeId, selector: '#module-import-file' })
          if (!inputNode.nodeId) throw new Error('Import file input missing')
          await send('DOM.setFileInputFiles', { nodeId: inputNode.nodeId, files: [csvPath] })
          await waitFor('document.querySelector(".import-mapping-grid") !== null')
          const mapping = await evaluate(`({text:document.querySelector('.import-drawer')?.innerText.slice(0,1100),
            previewEnabled:[...document.querySelectorAll('.import-drawer button')].find(x=>x.textContent.includes('开始预演'))?.disabled===false})`)
          console.log(JSON.stringify({ stage: 'import_mapping', mapping }))
          if (!mapping.previewEnabled) throw new Error('Uploaded CSV did not produce a valid field mapping')
          await click('.import-drawer button', '开始预演')
          await waitFor('document.querySelector(".import-summary") !== null')
          const preview = await evaluate(`({summary:document.querySelector('.import-summary')?.innerText,
            rows:[...document.querySelectorAll('.import-drawer .ant-table-row')].map(x=>x.innerText)})`)
          const previewCount = await evaluate(countRecords)
          console.log(JSON.stringify({ stage: 'import_preview', preview, beforeCount, previewCount }))
          if (previewCount !== beforeCount || !preview.summary.includes('2') || !preview.summary.includes('1')) {
            throw new Error('Import preview wrote data or did not classify both rows')
          }
          await click('.import-drawer button', '确认后台执行')
          await waitFor('document.querySelector(".import-summary")?.innerText.includes("1成功")')
          const completed = await evaluate(`({summary:document.querySelector('.import-summary')?.innerText,
            rows:[...document.querySelectorAll('.import-drawer .ant-table-row')].map(x=>x.innerText)})`)
          const afterCount = await evaluate(countRecords)
          if (afterCount !== beforeCount + 1 || !completed.rows.some(row=>row.includes('无标题应拒绝'))) {
            throw new Error('Import did not write exactly one valid row and retain the rejected row')
          }
          const image = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
          writeFileSync('projects/unexamine/ai/evidence/s19-admin-import-browser.png', Buffer.from(image.data, 'base64'))
          const evidence = { step: 'S19.11', candidate: '0.1.0-s19.11-dev3', status: 'import_ui_preview_and_execute_verified',
            beforeCount, previewCount, afterCount, mapping, preview, completed }
          writeFileSync('projects/unexamine/ai/evidence/s19-admin-import-browser.json', JSON.stringify(evidence, null, 2) + '\n')
          console.log(JSON.stringify(evidence))
        }
      } else if (process.argv.includes('--execute-action')) {
        await evaluate(`(() => {
          const row = [...document.querySelectorAll('tr')].find(x=>x.textContent.includes('S19 手机创建客户'))
          const button = [...(row?.querySelectorAll('button') || [])].find(x=>x.textContent.includes('标记已联系'))
          if (!button) throw new Error('Published row action missing')
          button.click()
        })()`)
        await delay(500)
        const actionOpen = await evaluate(`({modals:[...document.querySelectorAll('.ant-modal')].map(x=>x.innerText.slice(0,600)),
          drawers:[...document.querySelectorAll('.ant-drawer')].map(x=>x.innerText.slice(0,600)),
          notices:[...document.querySelectorAll('.ant-message-notice')].map(x=>x.innerText),
          tail:document.body.innerText.slice(-800)})`)
        console.log(JSON.stringify({ stage: 'action_open', actionOpen }))
        if (!actionOpen.modals.some(text => text.includes('确认执行') && text.includes('已联系'))) {
          throw new Error('Published action confirmation is missing')
        }
        await click('.ant-modal button', '确认执行')
        await delay(700)
        const actionResult = await evaluate(`({modals:[...document.querySelectorAll('.ant-modal')].map(x=>x.innerText.slice(0,700)),
          notices:[...document.querySelectorAll('.ant-message-notice')].map(x=>x.innerText),
          alerts:[...document.querySelectorAll('.ant-alert')].map(x=>x.innerText.slice(0,500)),
          row:[...document.querySelectorAll('tr')].find(x=>x.textContent.includes('S19 手机创建客户'))?.innerText})`)
        console.log(JSON.stringify({ stage: 'action_result', actionResult }))
        if (!actionResult.modals.some(text => text.includes('确认将客户标记为已联系？'))) {
          throw new Error('Final business action confirmation is missing')
        }
        await click('.ant-modal-confirm button', '确认执行')
        const recordStatus = await waitFor(`(async () => {
          const token = JSON.parse(sessionStorage.getItem('unexamine.system.tokens') || '{}').accessToken
          const response = await fetch('/api/runtime/modules/module_ae3286711c/records?page=1&pageSize=20',
            { headers: { Authorization: 'Bearer ' + token } })
          if (!response.ok) return null
          const result = await response.json()
          const record = result.data?.records?.find(x => x.title === 'S19 手机创建客户')
          return record?.status === '已联系' ? { title: record.title, status: record.status, version: record.version } : null
        })()`)
        const image = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
        writeFileSync('projects/unexamine/ai/evidence/s19-admin-action-runtime-browser.png', Buffer.from(image.data, 'base64'))
        const evidence = { step: 'S19.11', candidate: '0.1.0-s19.11-dev3', status: 'published_action_executed', confirmation: actionOpen.modals[0], recordStatus }
        writeFileSync('projects/unexamine/ai/evidence/s19-admin-action-runtime-browser.json', JSON.stringify(evidence, null, 2) + '\n')
        console.log(JSON.stringify(evidence))
      }
    } else if (process.argv.includes('--publish-action')) {
      if (!actions.rows.some(row => row.includes('标记已联系'))) {
        await click('.page-editor-toolbar button', '新建手动动作')
        await waitFor('[...document.querySelectorAll(".ant-drawer input")].some(x=>x.placeholder.includes("填写复核结果"))')
        await fill('.ant-drawer input[placeholder="例如：填写复核结果"]', '标记已联系')
        await evaluate(`(() => {
          const item = [...document.querySelectorAll('.ant-drawer .ant-form-item')]
            .find(x=>x.querySelector('label')?.textContent.includes('动作效果'))
          const select = item?.querySelector('.ant-select-selector')
          if (!select) throw new Error('Action effect selector missing')
          select.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true }))
          select.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true }))
          select.click()
        })()`)
        const actionOptions = await evaluate(`({drawer:document.querySelector('.ant-drawer')?.innerText.slice(0,500),
          dropdowns:[...document.querySelectorAll('.ant-select-dropdown')].map(x=>x.innerText.slice(0,300)),
          options:[...document.querySelectorAll('.ant-select-item-option')].map(x=>x.innerText)})`)
        console.log(JSON.stringify({ stage: 'action_options', actionOptions }))
        await waitFor('[...document.querySelectorAll(".ant-select-item-option")].some(x=>x.textContent.includes("设置记录状态"))')
        await click('.ant-select-item-option', '设置记录状态')
        await fill('.ant-drawer input[placeholder="例如：已复核"]', '已联系')
        await fill('.ant-drawer input[placeholder="确认执行此操作？"]', '确认将客户标记为已联系？')
        await click('.ant-drawer .drawer-footer button', '保存动作')
        await waitFor('[...document.querySelectorAll(".action-config-row")].some(x=>x.textContent.includes("标记已联系"))')
      }
      const actionDraft = await evaluate(`({rows:[...document.querySelectorAll('.action-config-row')].filter(x=>x.textContent.includes('标记已联系')).map(x=>x.innerText),
        alerts:[...document.querySelectorAll('.ant-alert-error')].map(x=>x.innerText)})`)
      console.log(JSON.stringify({ stage: 'action_draft', actionDraft }))
      await click('.config-task-tabs button', '模块信息')
      await click('.designer-subtabs button', '发布与版本')
      await waitFor('document.querySelector(".publish-readiness") !== null')
      const publication = await evaluate(`({readiness:document.querySelector('.publish-readiness')?.innerText,
        issues:document.querySelector('.publication-issues')?.innerText,
        versions:[...document.querySelectorAll('.module-version-row')].map(x=>x.innerText)})`)
      console.log(JSON.stringify({ stage: 'publication', publication }))
      await waitFor('document.querySelector(".publish-readiness.ready") !== null')
      await click('.publish-actions button', '确认发布并进入运行页')
      await waitFor('location.pathname === "/systems/1" && location.search.includes("workspace=runtime")')
      const runtime = await evaluate(`({path:location.pathname+location.search,
        title:document.body.innerText.includes('客户记录'),
        actionText:document.body.innerText.includes('标记已联系'),
        text:document.body.innerText.slice(-1000)})`)
      const image = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
      writeFileSync('projects/unexamine/ai/evidence/s19-admin-action-browser.png', Buffer.from(image.data, 'base64'))
      const evidence = { step: 'S19.11', candidate: '0.1.0-s19.11-dev3', status: 'module_action_ui_published', actionDraft, publication, runtime }
      writeFileSync('projects/unexamine/ai/evidence/s19-admin-action-browser.json', JSON.stringify(evidence, null, 2) + '\n')
      console.log(JSON.stringify(evidence))
    }
    socket.close()
  } else {
  await send('Page.navigate', { url: base + '/systems/1/admin?section=roles' })
  await waitFor('document.querySelector(".authorization-role-layout") !== null')
  await waitFor('document.querySelectorAll(".authorization-role-item").length > 0')
  if (process.argv.includes('--inspect')) {
    const inspection = await evaluate(`({roles:[...document.querySelectorAll('.authorization-role-item')].map(x=>x.innerText),
      resources:document.body.innerText.slice(-2500), path:location.pathname})`)
    console.log(JSON.stringify(inspection))
    socket.close()
  } else {
    const existingRole = await evaluate(`(() => {
      const row = [...document.querySelectorAll('.authorization-role-item')]
        .find(x => x.textContent.includes('S19 页面验收消息角色'))
      if (row) row.click()
      return Boolean(row)
    })()`)
    let messageResource = '消息中心'
    if (!existingRole) {
      await click('.authorization-role-tools button', '新建')
      await fill('input[placeholder="例如：销售顾问、销售经理、客户只读"]', 'S19 页面验收消息角色')
      await click('.role-task-nav button', '2. 权限范围')
      await fill('input[placeholder="搜索菜单、模块、动作或字段"]', '消息')
      await waitFor('document.querySelectorAll(".permission-resource").length > 0')
      const resourceChoices = await evaluate('[...document.querySelectorAll(".permission-resource")].map(x=>x.innerText.slice(0,160))')
      console.log(JSON.stringify({ stage: 'resource_choices', resourceChoices }))
      messageResource = await evaluate(`(() => {
      const row = [...document.querySelectorAll('.permission-resource')]
        .find(x => x.querySelector('.permission-resource__name')?.textContent.includes('消息'))
      if (!row) throw new Error('Message permission resource missing')
      row.querySelector('.permission-resource__name').click()
      return row.querySelector('.permission-resource__name').textContent
    })()`)
      await waitFor('document.querySelector(".permission-resource .permission-actions") !== null')
      const actionChoices = await evaluate('[...document.querySelectorAll(".permission-resource .permission-actions .ant-checkbox-wrapper")].map(x=>x.innerText)')
      console.log(JSON.stringify({ stage: 'action_choices', actionChoices }))
      const wasChecked = await evaluate(`(() => {
      const row = [...document.querySelectorAll('.permission-resource')].find(x => x.querySelector('.permission-resource__name')?.textContent.includes('消息'))
      const option = [...row.querySelectorAll('.permission-actions .ant-checkbox-wrapper')]
        .find(x => x.textContent.includes('查看'))
      if (!option) throw new Error('Message view action missing')
      const checked = option.querySelector('input')?.checked === true
      if (!checked) option.click()
      return checked
    })()`)
      console.log(JSON.stringify({ stage: 'message_view_selection', wasChecked }))
      await click('.role-task-nav button', '3. 保存与发布')
      await click('.role-publish-panel button', '保存草稿')
      await waitFor('[...document.querySelectorAll(".authorization-role-item")].some(x=>x.textContent.includes("S19 页面验收消息角色"))')
    }
    await click('.role-task-nav button', '3. 保存与发布')
    const roleDraft = await evaluate(`({panel:document.querySelector('.role-publish-panel')?.innerText,
      alerts:[...document.querySelectorAll('.ant-alert')].map(x=>x.innerText.slice(0,500)),
      selected:[...document.querySelectorAll('.authorization-role-item.active')].map(x=>x.innerText)})`)
    console.log(JSON.stringify({ stage: 'role_draft', roleDraft }))
    if (!roleDraft.selected.some(text => text.includes('已发布'))) {
      await waitFor('document.querySelector(".role-publish-panel .ant-alert-warning") !== null')
      await fill('input[placeholder="发布原因（必填）"]', 'S19 同版管理员页面验收')
      await click('.role-publish-panel button', '发布授权版本')
      await waitFor('document.querySelector(".ant-modal-confirm") !== null')
      await click('.ant-modal-confirm button', '确认发布权限')
    }
    await waitFor('[...document.querySelectorAll(".authorization-role-item")].some(x=>x.textContent.includes("S19 页面验收消息角色") && x.textContent.includes("已发布"))')
    const role = await evaluate(`({name:'S19 页面验收消息角色', resource:${JSON.stringify(messageResource)},
      published:[...document.querySelectorAll('.authorization-role-item')]
        .find(x=>x.textContent.includes('S19 页面验收消息角色'))?.innerText,
      page:location.pathname + location.search})`)
    const roleImage = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
    writeFileSync('projects/unexamine/ai/evidence/s19-admin-role-browser.png', Buffer.from(roleImage.data, 'base64'))

    await send('Page.navigate', { url: base + '/systems/1/admin?section=templates' })
    await waitFor('document.body.innerText.includes("导出配置")')
    await click('button', '导出配置')
    await waitFor('document.querySelector(".export-admin-row") !== null')
    const exportBefore = await evaluate(`({text:document.querySelector('.export-admin')?.innerText.slice(0,1100),
      name:[...document.querySelectorAll('.export-admin label')].find(x=>x.textContent.trim()==='配置名称')?.querySelector('input')?.value})`)
    console.log(JSON.stringify({ stage: 'export_before', exportBefore }))
    const nameInput = await evaluate(`(() => {
      const label = [...document.querySelectorAll('.export-admin label')].find(x => x.textContent.includes('配置名称'))
      return label?.querySelector('input')?.outerHTML || null
    })()`)
    if (!nameInput) throw new Error('Export configuration name input missing')
    await evaluate(`(() => {
      const label = [...document.querySelectorAll('.export-admin label')]
        .find(x => x.textContent.trim() === '配置名称')
      const input = label?.querySelector('input')
      if (!input) throw new Error('Export configuration name input missing')
      input.value = 'S19 客户记录页面发布导出'
      input.dispatchEvent(new Event('input', { bubbles: true }))
      input.dispatchEvent(new Event('change', { bubbles: true }))
    })()`)
    const exportEdit = await evaluate(`({labels:[...document.querySelectorAll('.export-admin label')].map(x=>({text:x.textContent.slice(0,35),value:x.querySelector('input')?.value})),
      buttons:[...document.querySelectorAll('.export-admin-row button')].map(x=>({text:x.textContent,disabled:x.disabled})),
      errors:[...document.querySelectorAll('.export-admin .ant-alert-error')].map(x=>x.innerText)})`)
    console.log(JSON.stringify({ stage: 'export_edit', exportEdit }))
    if (exportEdit.buttons.some(button => button.text.includes('保存草稿') && !button.disabled)) {
      await click('.export-admin-row button', '保存草稿')
    }
    await delay(1200)
    const exportSaved = await evaluate(`({text:document.querySelector('.export-admin')?.innerText.slice(0,1000),
      buttons:[...document.querySelectorAll('.export-admin-row button')].map(x=>({text:x.textContent,disabled:x.disabled})),
      alerts:[...document.querySelectorAll('.export-admin .ant-alert')].map(x=>x.innerText)})`)
    console.log(JSON.stringify({ stage: 'export_saved', exportSaved }))
    const publishedVersion = Number(exportBefore.text.match(/第\s*(\d+)\s*版/)?.[1] || 0)
    if (publishedVersion < 2) {
      await waitFor('[...document.querySelectorAll(".export-admin-row button")].some(x=>x.textContent.includes("发布导出配置") && !x.disabled)')
      await click('.export-admin-row button', '发布导出配置')
      await waitFor('Number(document.querySelector(".export-admin-row .ant-tag")?.textContent.match(/第\\s*(\\d+)\\s*版/)?.[1] || 0) > ' + publishedVersion)
    }
    const exported = await evaluate(`({page:location.pathname+location.search,
      text:document.querySelector('.export-admin')?.innerText.slice(0,700)})`)
    const exportImage = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
    writeFileSync('projects/unexamine/ai/evidence/s19-admin-export-browser.png', Buffer.from(exportImage.data, 'base64'))
    const finalVersion = Number(exported.text.match(/第\s*(\d+)\s*版/)?.[1] || 0)
    if (finalVersion < 2 || exportBefore.name !== 'S19 客户记录页面发布导出') {
      throw new Error('Administrator export publication was not read back')
    }
    const evidence = { step: 'S19.11', candidate: '0.1.0-s19.11-dev3', status: 'role_and_export_admin_ui_published', role, exported, publishedVersion: finalVersion }
    writeFileSync('projects/unexamine/ai/evidence/s19-admin-ui-browser.json', JSON.stringify(evidence, null, 2) + '\n')
    console.log(JSON.stringify(evidence))
    socket.close()
  }
  }
} finally {
  edge.kill()
}
