const fs = require('fs');
const http = require('http');
const baseUrl = process.env.R88_BASE_URL;
const port = Number(process.env.R88_CDP_PORT);
const outPath = process.env.R88_BROWSER_OUT;
const systemId = process.env.R88_SYSTEM_ID;
const moduleId = process.env.R88_MODULE_ID;
const draftId = process.env.R88_DRAFT_ID;
const recordId = process.env.R88_RECORD_ID;
const draftTitle = process.env.R88_DRAFT_TITLE;
const recordTitle = process.env.R88_RECORD_TITLE;
const role = {
  accessToken: process.env.R88_ACCESS_TOKEN,
  refreshToken: process.env.R88_REFRESH_TOKEN || '',
  accountId: process.env.R88_ACCOUNT_ID || 'r88',
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
async function navigate(client, hash, width, height) {
  await client.send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: width <= 640 });
  await client.send('Page.navigate', { url: `${baseUrl}/#/${hash}` });
  await delay(1800);
}
async function setStorage(client) {
  const recent = [{ systemId, moduleId, moduleName: `module-${moduleId}`, recordId, title: recordTitle, updatedAt: new Date().toISOString() }];
  const drafts = [{ systemId, moduleId, moduleName: `module-${moduleId}`, draftId, recordId: '', title: draftTitle, fieldValues: { caseTitle: draftTitle, caseAmount: '88', caseOwner: 'R88 Browser' }, updatedAt: new Date().toISOString() }];
  await client.send('Page.navigate', { url: `${baseUrl}/#/` });
  await delay(500);
  await client.send('Runtime.evaluate', { expression: `localStorage.setItem('unexamine.accountId', ${JSON.stringify(role.accountId)}); localStorage.setItem('unexamine.accessToken', ${JSON.stringify(role.accessToken)}); localStorage.setItem('unexamine.refreshToken', ${JSON.stringify(role.refreshToken)}); localStorage.setItem('unexamine.runtime.recent.v1', ${JSON.stringify(JSON.stringify(recent))}); localStorage.setItem('unexamine.runtime.drafts.v1', ${JSON.stringify(JSON.stringify(drafts))});`, returnByValue: true });
  await client.send('Page.navigate', { url: `${baseUrl}/?r88=${Date.now()}#/platform` });
  await delay(2500);
}
async function waitForMarker(client, selector) {
  for (let i = 0; i < 50; i += 1) {
    const found = await evaluate(client, `JSON.stringify({ ok: !!document.querySelector(${JSON.stringify(selector)}), textLength: (document.body.innerText || '').length })`);
    if (found.ok) return found;
    await delay(250);
  }
  return { ok: false };
}
async function collect(client, kind, hash, width, height, selector) {
  await navigate(client, hash, width, height);
  await waitForMarker(client, selector);
  if (kind.includes('validation')) {
    await waitForMarker(client, '[data-runtime-form-field-r88="caseTitle"]');
    await evaluate(client, `JSON.stringify((() => { const button = document.querySelector('[data-runtime-save-record-r88="true"]'); if (button) button.click(); return { clicked: !!button }; })())`);
    await delay(600);
  }
  return evaluate(client, `JSON.stringify((() => {
    const overflowX = Math.max(0, document.documentElement.scrollWidth - document.documentElement.clientWidth);
    const draftField = document.querySelector('[data-runtime-form-field-r88="caseTitle"]');
    const formResult = document.querySelector('[data-runtime-form-result-r88="true"]');
    return {
      kind: ${JSON.stringify(kind)},
      viewport: ${JSON.stringify(`${width}x${height}`)},
      hash: location.hash,
      overflowX,
      blockerText: /鍔犺浇澶辫触|undefined|null|NaN/.test(document.body.innerText || ''),
      dashboardEfficiency: !!document.querySelector('[data-system-dashboard-runtime-efficiency-r88="true"]'),
      dashboardRecentItems: document.querySelectorAll('[data-dashboard-runtime-recent-item-r88]').length,
      dashboardDraftItems: document.querySelectorAll('[data-dashboard-runtime-draft-item-r88]').length,
      dashboardQuickModules: document.querySelectorAll('[data-dashboard-runtime-module-item-r88]').length,
      dashboardSearch: !!document.querySelector('[data-dashboard-runtime-search-r88="true"]'),
      runtimeEfficiency: !!document.querySelector('[data-runtime-efficiency-r88="true"]'),
      runtimeRecentItems: document.querySelectorAll('[data-runtime-efficiency-recent-item]').length,
      runtimeDraftItems: document.querySelectorAll('[data-runtime-efficiency-draft-item]').length,
      runtimeQuickCreate: !!document.querySelector('[data-runtime-efficiency-quick-create]'),
      draftRestored: draftField ? draftField.value : '',
      formResult: !!formResult,
      validationField: formResult?.dataset.runtimeValidationField || '',
      fieldErrors: document.querySelectorAll('[data-runtime-field-error-r88="true"]').length,
      textSampleLength: (document.body.innerText || '').length,
    };
  })())`);
}
(async () => {
  const target = await newTarget();
  const client = await connect(target.webSocketDebuggerUrl);
  try {
    await client.send('Page.enable');
    await client.send('Runtime.enable');
    await setStorage(client);
    const results = [];
    results.push(await collect(client, 'dashboard-desktop', `systems/${systemId}/dashboard`, 1366, 900, '[data-system-dashboard-runtime-efficiency-r88="true"]'));
    results.push(await collect(client, 'runtime-draft-desktop', `systems/${systemId}/modules?moduleId=${encodeURIComponent(moduleId)}&mode=draft&draftId=${encodeURIComponent(draftId)}`, 1366, 900, '[data-runtime-form-field-r88="caseTitle"]'));
    results.push(await collect(client, 'runtime-validation-desktop', `systems/${systemId}/modules?moduleId=${encodeURIComponent(moduleId)}&mode=create`, 1366, 900, '[data-runtime-form-field-r88="caseTitle"]'));
    results.push(await collect(client, 'dashboard-mobile', `systems/${systemId}/dashboard`, 390, 720, '[data-system-dashboard-runtime-efficiency-r88="true"]'));
    results.push(await collect(client, 'runtime-draft-mobile', `systems/${systemId}/modules?moduleId=${encodeURIComponent(moduleId)}&mode=draft&draftId=${encodeURIComponent(draftId)}`, 390, 720, '[data-runtime-form-field-r88="caseTitle"]'));
    fs.writeFileSync(outPath, JSON.stringify({ results }, null, 2));
  } finally {
    client.close();
  }
})().catch((error) => { fs.writeFileSync(outPath, JSON.stringify({ error: error.stack || String(error) }, null, 2)); process.exit(1); });
