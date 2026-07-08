const fs = require('fs');
const http = require('http');
const baseUrl = process.env.R91_BASE_URL;
const port = Number(process.env.R91_CDP_PORT);
const outPath = process.env.R91_BROWSER_OUT;
const systemId = process.env.R91_SYSTEM_ID;
const roleId = process.env.R91_ROLE_ID;
const moduleId = process.env.R91_MODULE_ID;
const adminRole = {
  accessToken: process.env.R91_ADMIN_ACCESS_TOKEN,
  refreshToken: process.env.R91_ADMIN_REFRESH_TOKEN || '',
  accountId: process.env.R91_ADMIN_ACCOUNT_ID || 'r91-admin',
};
const normalRole = {
  accessToken: process.env.R91_NORMAL_ACCESS_TOKEN,
  refreshToken: process.env.R91_NORMAL_REFRESH_TOKEN || '',
  accountId: process.env.R91_NORMAL_ACCOUNT_ID || 'r91-normal',
};
function delay(ms) { return new Promise((resolve) => setTimeout(resolve, ms)); }
async function cdpJson(pathname, method = 'GET') {
  return new Promise((resolve, reject) => {
    const req = http.request({ hostname: '127.0.0.1', port, path: pathname, method, timeout: 2500 }, (res) => {
      let body = '';
      res.setEncoding('utf8');
      res.on('data', (chunk) => { body += chunk; });
      res.on('end', () => {
        if (res.statusCode < 200 || res.statusCode >= 300) reject(new Error(`CDP HTTP ${res.statusCode}: ${body}`));
        else resolve(JSON.parse(body));
      });
    });
    req.on('timeout', () => req.destroy(new Error(`CDP timeout ${pathname}`)));
    req.on('error', reject);
    req.end();
  });
}
async function newTarget() {
  try { return await cdpJson(`/json/new?${encodeURIComponent('about:blank')}`, 'PUT'); }
  catch { const targets = await cdpJson('/json/list'); return targets[0]; }
}
function connect(wsUrl) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(wsUrl);
    const pending = new Map();
    let seq = 0;
    ws.onopen = () => resolve({
      send(method, params = {}) {
        const id = ++seq;
        ws.send(JSON.stringify({ id, method, params }));
        return new Promise((res, rej) => pending.set(id, { res, rej, method }));
      },
      close() { ws.close(); },
    });
    ws.onerror = reject;
    ws.onmessage = (event) => {
      const msg = JSON.parse(event.data);
      if (msg.id && pending.has(msg.id)) {
        const item = pending.get(msg.id);
        pending.delete(msg.id);
        if (msg.error) item.rej(new Error(`${item.method}: ${JSON.stringify(msg.error)}`));
        else item.res(msg.result);
      }
    };
  });
}
async function evaluate(client, expression) {
  const result = await client.send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
  if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
  const value = result.result.value;
  return typeof value === 'string' ? JSON.parse(value) : value;
}
async function setStorage(client, role) {
  await client.send('Page.navigate', { url: `${baseUrl}/#/` });
  await delay(500);
  await client.send('Runtime.evaluate', {
    expression: `localStorage.setItem('unexamine.accountId', ${JSON.stringify(role.accountId)}); localStorage.setItem('unexamine.accessToken', ${JSON.stringify(role.accessToken)}); localStorage.setItem('unexamine.refreshToken', ${JSON.stringify(role.refreshToken)});`,
    returnByValue: true,
  });
}
async function navigate(client, url, width, height) {
  await client.send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: width <= 640 });
  await client.send('Page.navigate', { url });
  await delay(2600);
}
async function waitFor(client, selector, loops = 60) {
  for (let i = 0; i < loops; i += 1) {
    const found = await evaluate(client, `JSON.stringify({ ok: !!document.querySelector(${JSON.stringify(selector)}), textLength: (document.body.innerText || '').length })`);
    if (found.ok) return true;
    await delay(250);
  }
  return false;
}
async function waitUntil(client, expression, loops = 60) {
  for (let i = 0; i < loops; i += 1) {
    const ok = await evaluate(client, `JSON.stringify({ ok: Boolean((() => { ${expression} })()) })`);
    if (ok.ok) return true;
    await delay(250);
  }
  return false;
}
async function clickSelector(client, selector) {
  return evaluate(client, `JSON.stringify((() => { const node = document.querySelector(${JSON.stringify(selector)}); if (node) node.click(); return { clicked: !!node }; })())`);
}
async function selectRoleModule(client) {
  return evaluate(client, `JSON.stringify((() => {
    const role = document.querySelector('[data-permission-role-select="true"]');
    const module = document.querySelector('[data-permission-module-select="true"]');
    if (role) { role.value = ${JSON.stringify(roleId)}; role.dispatchEvent(new Event('change', { bubbles: true })); }
    if (module) { module.value = ${JSON.stringify(moduleId)}; module.dispatchEvent(new Event('change', { bubbles: true })); }
    return { roleValue: role?.value || '', moduleValue: module?.value || '' };
  })())`);
}
async function collectPermission(client, kind) {
  return evaluate(client, `JSON.stringify((() => {
    const body = document.body.innerText || '';
    const decisions = Array.from(document.querySelectorAll('[data-r91-permission-decision="true"]')).map((node) => ({
      actionCode: node.dataset.r91ActionCode || '',
      allowed: node.dataset.r91Allowed || '',
      text: node.textContent || '',
    }));
    const audits = Array.from(document.querySelectorAll('[data-r91-preview-audit-row="true"]')).map((node) => ({
      actionCode: node.dataset.r91ActionCode || '',
      allowed: node.dataset.r91Allowed || '',
      id: node.dataset.r91PreviewLogId || '',
    }));
    return {
      kind: ${JSON.stringify(kind)},
      hash: location.hash,
      overflowX: Math.max(0, document.documentElement.scrollWidth - document.documentElement.clientWidth),
      blockerText: /undefined|null|NaN/.test(body),
      workbench: !!document.querySelector('[data-permission-impact-workbench-r91="true"]'),
      roleValue: document.querySelector('[data-permission-role-select="true"]')?.value || '',
      moduleValue: document.querySelector('[data-permission-module-select="true"]')?.value || '',
      batchButton: !!document.querySelector('[data-r91-batch-preview-button="true"]'),
      impactPanel: !!document.querySelector('[data-r91-permission-impact-panel="true"]'),
      decisionCount: decisions.length,
      deniedCreate: decisions.some((item) => item.actionCode === 'record.create' && item.allowed === 'false'),
      deniedDelete: decisions.some((item) => item.actionCode === 'record.delete' && item.allowed === 'false'),
      allowedEdit: decisions.some((item) => item.actionCode === 'record.edit' && item.allowed === 'true'),
      allowedSubmit: decisions.some((item) => item.actionCode === 'record.submitApproval' && item.allowed === 'true'),
      affectedMembers: document.querySelector('[data-r91-affected-members]')?.dataset.r91AffectedMembers || '',
      fieldMaskSummary: document.querySelector('[data-r91-field-mask-summary]')?.dataset.r91FieldMaskSummary || '',
      dataScopeSummary: document.querySelector('[data-r91-data-scope-summary]')?.dataset.r91DataScopeSummary || '',
      auditCount: audits.length,
      audits,
      deniedCopy: /权限|无权|拒绝|没有系统后台管理权限/.test(body),
      textLength: body.length,
    };
  })())`);
}
(async () => {
  const target = await newTarget();
  const client = await connect(target.webSocketDebuggerUrl);
  try {
    await client.send('Page.enable');
    await client.send('Runtime.enable');
    const results = [];

    await setStorage(client, adminRole);
    await navigate(client, `${baseUrl}/?r91=${Date.now()}#/systems/${systemId}/admin`, 1366, 900);
    await waitFor(client, '[data-admin-section="role-management"]');
    await clickSelector(client, '[data-admin-section="role-management"]');
    await waitFor(client, '[data-permission-impact-workbench-r91="true"]');
    await selectRoleModule(client);
    await delay(1500);
    await clickSelector(client, '[data-r91-batch-preview-button="true"]');
    await waitUntil(client, "return document.querySelectorAll('[data-r91-permission-decision=\"true\"]').length >= 4;", 80);
    results.push(await collectPermission(client, 'admin-role-desktop'));

    await navigate(client, `${baseUrl}/?r91m=${Date.now()}#/systems/${systemId}/admin`, 390, 720);
    await waitFor(client, '[data-admin-section="role-management"]');
    await clickSelector(client, '[data-admin-section="role-management"]');
    await waitFor(client, '[data-permission-impact-workbench-r91="true"]');
    await selectRoleModule(client);
    await delay(1000);
    results.push(await collectPermission(client, 'admin-role-mobile'));

    await setStorage(client, normalRole);
    await navigate(client, `${baseUrl}/?r91n=${Date.now()}#/systems/${systemId}/admin`, 1366, 900);
    results.push(await collectPermission(client, 'normal-admin-denied'));

    fs.writeFileSync(outPath, JSON.stringify({ results }, null, 2));
  } finally {
    client.close();
  }
})().catch((error) => { fs.writeFileSync(outPath, JSON.stringify({ error: error.stack || String(error) }, null, 2)); process.exit(1); });