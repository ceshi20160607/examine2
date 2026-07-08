const fs = require('fs');
const http = require('http');
const baseUrl = process.env.R90_BASE_URL;
const port = Number(process.env.R90_CDP_PORT);
const outPath = process.env.R90_BROWSER_OUT;
const systemId = process.env.R90_SYSTEM_ID;
const adminRole = {
  accessToken: process.env.R90_ADMIN_ACCESS_TOKEN,
  refreshToken: process.env.R90_ADMIN_REFRESH_TOKEN || '',
  accountId: process.env.R90_ADMIN_ACCOUNT_ID || 'r90-admin',
};
const normalRole = {
  accessToken: process.env.R90_NORMAL_ACCESS_TOKEN,
  refreshToken: process.env.R90_NORMAL_REFRESH_TOKEN || '',
  accountId: process.env.R90_NORMAL_ACCOUNT_ID || 'r90-normal',
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
async function clickSelector(client, selector) {
  return evaluate(client, `JSON.stringify((() => { const node = document.querySelector(${JSON.stringify(selector)}); if (node) node.click(); return { clicked: !!node }; })())`);
}
async function collectOrg(client, kind) {
  return evaluate(client, `JSON.stringify((() => {
    const body = document.body.innerText || '';
    return {
      kind: ${JSON.stringify(kind)},
      hash: location.hash,
      overflowX: Math.max(0, document.documentElement.scrollWidth - document.documentElement.clientWidth),
      blockerText: /閸旂姾娴囨径杈|undefined|null|NaN/.test(body),
      orgPanel: !!document.querySelector('[data-org-member-delivery-r90="true"]'),
      departmentTree: !!document.querySelector('[data-org-department-tree-r90="true"]'),
      departmentNodes: document.querySelectorAll('[data-org-department-node-r90]').length,
      memberRows: document.querySelectorAll('[data-org-member-r90="true"]').length,
      memberTableTotal: document.querySelector('[data-org-member-table-r90="true"]')?.dataset.orgMemberTotal || '',
      readiness: !!document.querySelector('[data-r90-binding-readiness="true"]'),
      selectedBinding: document.querySelector('[data-r90-binding-readiness="true"]')?.dataset.r90SelectedBinding || '',
      selectedRoles: document.querySelector('[data-r90-binding-readiness="true"]')?.dataset.r90SelectedRoles || '',
      bindButton: !!document.querySelector('[data-r90-bind-account="true"]'),
      assignButton: !!document.querySelector('[data-r90-assign-role="true"]'),
      previewButton: !!document.querySelector('[data-r90-preview-permission="true"]'),
      roleWorkbench: !!document.querySelector('[data-role-permission-workbench="true"]'),
      deniedCopy: /没有系统后台管理权限|没有后台管理权限|权限/.test(body),
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
    await navigate(client, `${baseUrl}/?r90=${Date.now()}#/systems/${systemId}/admin`, 1366, 900);
    await waitFor(client, '[data-admin-section="org-structure"]');
    await clickSelector(client, '[data-admin-section="org-structure"]');
    await waitFor(client, '[data-org-member-delivery-r90="true"]');
    results.push(await collectOrg(client, 'admin-org-desktop'));
    await clickSelector(client, '[data-admin-section="role-management"]');
    await waitFor(client, '[data-role-permission-workbench="true"]');
    results.push(await collectOrg(client, 'admin-role-desktop'));

    await navigate(client, `${baseUrl}/?r90m=${Date.now()}#/systems/${systemId}/admin`, 390, 720);
    await waitFor(client, '[data-admin-section="org-structure"]');
    await clickSelector(client, '[data-admin-section="org-structure"]');
    await waitFor(client, '[data-org-member-delivery-r90="true"]');
    results.push(await collectOrg(client, 'admin-org-mobile'));

    await setStorage(client, normalRole);
    await navigate(client, `${baseUrl}/?r90n=${Date.now()}#/systems/${systemId}/admin`, 1366, 900);
    results.push(await collectOrg(client, 'normal-admin-denied'));
    await navigate(client, `${baseUrl}/?r90d=${Date.now()}#/systems/${systemId}/dashboard`, 1366, 900);
    results.push(await collectOrg(client, 'normal-dashboard'));

    fs.writeFileSync(outPath, JSON.stringify({ results }, null, 2));
  } finally {
    client.close();
  }
})().catch((error) => { fs.writeFileSync(outPath, JSON.stringify({ error: error.stack || String(error) }, null, 2)); process.exit(1); });