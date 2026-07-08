const fs = require('fs');
const path = require('path');
const http = require('http');

const baseUrl = process.env.R50_BASE_URL;
const outDir = process.env.R50_EVIDENCE_DIR;
const port = process.env.R50_CDP_PORT;
const systemId = process.env.R50_SYSTEM_ID;

function delay(ms) { return new Promise((resolve) => setTimeout(resolve, ms)); }
function assert(condition, message, details = {}) {
  if (!condition) throw new Error(`${message}: ${JSON.stringify(details)}`);
}
async function cdpJson(pathname, options = {}) {
  return new Promise((resolve, reject) => {
    const request = http.request({
      hostname: '127.0.0.1',
      port,
      path: pathname,
      method: options.method || 'GET',
    }, (response) => {
      let body = '';
      response.setEncoding('utf8');
      response.on('data', (chunk) => { body += chunk; });
      response.on('end', () => {
        if (response.statusCode < 200 || response.statusCode >= 300) {
          reject(new Error(`CDP HTTP ${response.statusCode} for ${pathname}`));
          return;
        }
        try {
          resolve(JSON.parse(body));
        } catch (error) {
          reject(error);
        }
      });
    });
    request.on('error', reject);
    request.end();
  });
}
async function newTarget() {
  try {
    return await cdpJson(`/json/new?${encodeURIComponent('about:blank')}`, { method: 'PUT' });
  } catch {
    const targets = await cdpJson('/json/list');
    return targets[0];
  }
}
function connect(wsUrl) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(wsUrl);
    const pending = new Map();
    const eventWaiters = new Map();
    let seq = 0;
    ws.onopen = () => resolve({
      send(method, params = {}) {
        const id = ++seq;
        ws.send(JSON.stringify({ id, method, params }));
        return new Promise((res, rej) => pending.set(id, { res, rej, method }));
      },
      waitFor(eventName, timeoutMs = 5000) {
        return new Promise((res) => {
          const timer = setTimeout(() => res(null), timeoutMs);
          eventWaiters.set(eventName, (payload) => {
            clearTimeout(timer);
            res(payload);
          });
        });
      },
      close() { ws.close(); },
    });
    ws.onerror = reject;
    ws.onmessage = (event) => {
      const message = JSON.parse(event.data);
      if (message.id && pending.has(message.id)) {
        const item = pending.get(message.id);
        pending.delete(message.id);
        message.error ? item.rej(new Error(`${item.method}: ${JSON.stringify(message.error)}`)) : item.res(message.result);
      }
      if (message.method && eventWaiters.has(message.method)) {
        const waiter = eventWaiters.get(message.method);
        eventWaiters.delete(message.method);
        waiter(message.params || {});
      }
    };
  });
}
async function navigate(client, url) {
  const waitLoad = client.waitFor('Page.loadEventFired', 8000);
  await client.send('Page.navigate', { url });
  await waitLoad;
  await delay(900);
}
async function evaluateJson(client, expression) {
  const result = await client.send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
  const value = result.result.value;
  return typeof value === 'string' ? JSON.parse(value) : value;
}
async function waitUntil(client, expression, timeoutMs = 12000) {
  const started = Date.now();
  let last = null;
  while (Date.now() - started < timeoutMs) {
    last = await evaluateJson(client, expression);
    if (last && last.ok) return last;
    await delay(300);
  }
  throw new Error(`waitUntil timed out. Last=${JSON.stringify(last)}`);
}
async function realLogin(client) {
  await navigate(client, `${baseUrl}/#/login`);
  await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('form.auth-form input[data-field-name="loginName"]') })`);
  await client.send('Runtime.evaluate', {
    expression: `
      (() => {
        const login = document.querySelector('form.auth-form input[data-field-name="loginName"]');
        const password = document.querySelector('form.auth-form input[data-field-name="password"]');
        const button = document.querySelector('form.auth-form button.button.primary');
        login.value = 'admin';
        login.dispatchEvent(new Event('input', { bubbles: true }));
        login.dispatchEvent(new Event('change', { bubbles: true }));
        password.value = '123123aa';
        password.dispatchEvent(new Event('input', { bubbles: true }));
        password.dispatchEvent(new Event('change', { bubbles: true }));
        button.click();
      })()
    `,
    returnByValue: true,
  });
  await waitUntil(client, `JSON.stringify({ ok: !!localStorage.getItem('unexamine.accessToken') && location.hash !== '#/login' })`);
}
async function capture(client, key, extra = {}) {
  const metrics = await evaluateJson(client, `
    JSON.stringify((() => {
      const overflowX = Math.max(0, document.documentElement.scrollWidth - window.innerWidth);
      const blockers = [];
      if (document.body.innerText.includes('undefined')) blockers.push('undefined-copy');
      if (document.body.innerText.includes('NaN')) blockers.push('nan-copy');
      return { overflowX, blockers, hash: location.hash, textSample: document.body.innerText.slice(0, 300) };
    })())
  `);
  const screenshot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  const fileName = `${key}.png`;
  fs.writeFileSync(path.join(outDir, fileName), Buffer.from(screenshot.data, 'base64'));
  return { key, screenshot: fileName, ...metrics, ...extra };
}

(async () => {
  fs.mkdirSync(outDir, { recursive: true });
  const target = await newTarget();
  const client = await connect(target.webSocketDebuggerUrl);
  await client.send('Page.enable');
  await client.send('Runtime.enable');
  await realLogin(client);
  const results = [];
  const viewports = [
    { name: 'desktop', width: 1440, height: 920, mobile: false },
    { name: 'mobile', width: 390, height: 720, mobile: true },
  ];
  for (const viewport of viewports) {
    await client.send('Emulation.setDeviceMetricsOverride', { width: viewport.width, height: viewport.height, deviceScaleFactor: 1, mobile: viewport.mobile });
    await client.send('Emulation.setVisibleSize', { width: viewport.width, height: viewport.height });
    await navigate(client, `${baseUrl}/#/platform/admin`);
    await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('.admin-sidebar') && !!document.querySelector('.admin-content') })`);
    await client.send('Runtime.evaluate', { expression: `(() => { const items = Array.from(document.querySelectorAll('.admin-sidebar .sidebar-item')); items[5]?.click(); })()`, returnByValue: true });
    await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('#platform-ops-governance') })`);
    results.push(await capture(client, `platform-ops-${viewport.name}`, { viewport: viewport.name }));
    await client.send('Runtime.evaluate', { expression: `(() => { const items = Array.from(document.querySelectorAll('.admin-sidebar .sidebar-item')); items[6]?.click(); })()`, returnByValue: true });
    await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('#platform-logs') })`);
    results.push(await capture(client, `platform-logs-${viewport.name}`, { viewport: viewport.name }));
    await navigate(client, `${baseUrl}/#/systems/${systemId}/admin/log-management`);
    await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('.admin-content') && !!document.querySelector('#log-management') })`);
    results.push(await capture(client, `system-logs-${viewport.name}`, { viewport: viewport.name }));
  }
  results.forEach((item) => assert(item.overflowX <= 2 && item.blockers.length === 0, 'Browser containment failed', item));
  fs.writeFileSync(path.join(outDir, 'operations-release-log-browser-audit.json'), JSON.stringify({ results }, null, 2));
  client.close();
  console.log(JSON.stringify({ results }, null, 2));
})().catch((error) => {
  console.error(error.stack || error.message);
  process.exit(1);
});
