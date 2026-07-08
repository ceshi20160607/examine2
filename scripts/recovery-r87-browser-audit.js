const fs = require('fs');
const http = require('http');
const baseUrl = process.env.R87_BASE_URL;
const port = Number(process.env.R87_CDP_PORT);
const outPath = process.env.R87_BROWSER_OUT;
const systemId = process.env.R87_SYSTEM_ID;
const role = {
  accessToken: process.env.R87_ACCESS_TOKEN,
  refreshToken: process.env.R87_REFRESH_TOKEN || '',
  accountId: process.env.R87_ACCOUNT_ID || 'r87',
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
  await client.send('Page.navigate', { url: `${baseUrl}/#/` });
  await delay(500);
  await client.send('Runtime.evaluate', { expression: `localStorage.setItem('unexamine.accountId', ${JSON.stringify(role.accountId)}); localStorage.setItem('unexamine.accessToken', ${JSON.stringify(role.accessToken)}); localStorage.setItem('unexamine.refreshToken', ${JSON.stringify(role.refreshToken)});`, returnByValue: true });
  await client.send('Page.navigate', { url: `${baseUrl}/?r87=${Date.now()}#/platform` });
  await delay(2500);
}
async function waitForMarker(client, selector) {
  for (let i = 0; i < 50; i += 1) {
    const found = await evaluate(client, `JSON.stringify({ ok: !!document.querySelector(${JSON.stringify(selector)}), text: document.body.innerText.slice(0, 200) })`);
    if (found.ok) return found;
    await delay(250);
  }
  return { ok: false };
}
async function collect(client, kind, hash, width, height, selector) {
  await navigate(client, hash, width, height);
  await waitForMarker(client, selector);
  return evaluate(client, `JSON.stringify((() => {
    const overflowX = Math.max(0, document.documentElement.scrollWidth - document.documentElement.clientWidth);
    return {
      kind: ${JSON.stringify(kind)},
      viewport: ${JSON.stringify(`${width}x${height}`)},
      hash: location.hash,
      overflowX,
      blockerText: /加载失败|undefined|null|NaN/.test(document.body.innerText || ''),
      todoWorkbench: !!document.querySelector('[data-system-todo-workbench-r87="true"]'),
      todoLayout: !!document.querySelector('[data-system-todo-layout="true"]'),
      todoDetailPanel: !!document.querySelector('[data-system-todo-detail-panel]'),
      todoEmptyState: !!document.querySelector('[data-system-todo-empty-state="true"]'),
      todoRows: document.querySelectorAll('[data-system-todo-row]').length,
      messageCenter: !!document.querySelector('[data-system-message-workbench-r87="true"]'),
      messageToolbar: !!document.querySelector('[data-system-message-toolbar="true"]'),
      messageItems: document.querySelectorAll('[data-system-message-item]').length,
      messageMarkReadButtons: document.querySelectorAll('[data-system-message-mark-read]').length,
      messageArchiveButtons: document.querySelectorAll('[data-system-message-archive]').length,
      messageEmptyState: !!document.querySelector('[data-system-message-empty-state]'),
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
    results.push(await collect(client, 'todo-desktop', `systems/${systemId}/todos`, 1366, 900, '[data-system-todo-workbench-r87="true"]'));
    results.push(await collect(client, 'message-desktop', `systems/${systemId}/messages`, 1366, 900, '[data-system-message-workbench-r87="true"]'));
    results.push(await collect(client, 'todo-mobile', `systems/${systemId}/todos`, 390, 720, '[data-system-todo-workbench-r87="true"]'));
    results.push(await collect(client, 'message-mobile', `systems/${systemId}/messages`, 390, 720, '[data-system-message-workbench-r87="true"]'));
    fs.writeFileSync(outPath, JSON.stringify({ results }, null, 2));
  } finally {
    client.close();
  }
})().catch((error) => { fs.writeFileSync(outPath, JSON.stringify({ error: error.stack || String(error) }, null, 2)); process.exit(1); });
