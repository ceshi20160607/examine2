const fs = require('fs');
const http = require('http');
const path = require('path');

const baseUrl = process.env.R75_BASE_URL;
const outDir = process.env.R75_EVIDENCE_DIR;
const port = process.env.R75_CDP_PORT;
const systemId = process.env.R75_SYSTEM_ID;
let stage = 'boot';

function delay(ms) { return new Promise((resolve) => setTimeout(resolve, ms)); }
function assert(condition, message, details = {}) {
  if (!condition) throw new Error(`${message}: ${JSON.stringify(details)}`);
}
async function cdpJson(pathname, options = {}) {
  const method = options.method || 'GET';
  return new Promise((resolve, reject) => {
    const request = http.request({
      hostname: '127.0.0.1',
      port,
      path: pathname,
      method,
      timeout: 2000,
    }, (response) => {
      let body = '';
      response.setEncoding('utf8');
      response.on('data', (chunk) => { body += chunk; });
      response.on('end', () => {
        if (response.statusCode < 200 || response.statusCode >= 300) {
          reject(new Error(`CDP HTTP ${response.statusCode} for ${pathname}: ${body}`));
          return;
        }
        try {
          resolve(JSON.parse(body));
        } catch (error) {
          reject(error);
        }
      });
    });
    request.on('timeout', () => request.destroy(new Error(`CDP timeout for ${pathname}`)));
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
    const waiters = new Map();
    let seq = 0;
    ws.onopen = () => resolve({
      send(method, params = {}) {
        const id = ++seq;
        ws.send(JSON.stringify({ id, method, params }));
        return new Promise((res, rej) => pending.set(id, { res, rej, method }));
      },
      waitFor(eventName, timeoutMs = 8000) {
        return new Promise((res) => {
          const timer = setTimeout(() => res(null), timeoutMs);
          waiters.set(eventName, (payload) => {
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
      if (message.method && waiters.has(message.method)) {
        const waiter = waiters.get(message.method);
        waiters.delete(message.method);
        waiter(message.params || {});
      }
    };
  });
}
async function evaluateJson(client, expression) {
  const result = await client.send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  if (result.exceptionDetails) throw new Error(`Runtime.evaluate failed at ${stage}: ${JSON.stringify(result.exceptionDetails)} expression=${expression}`);
  const value = result.result.value;
  return typeof value === 'string' ? JSON.parse(value) : value;
}
async function waitUntil(client, expression, timeoutMs = 20000) {
  const started = Date.now();
  let last = null;
  while (Date.now() - started < timeoutMs) {
    last = await evaluateJson(client, expression);
    if (last && last.ok) return last;
    await delay(350);
  }
  throw new Error(`waitUntil timed out at ${stage}. Last=${JSON.stringify(last)}`);
}
async function navigate(client, url) {
  stage = `navigate ${url}`;
  const waitLoad = client.waitFor('Page.loadEventFired', 10000);
  await client.send('Page.navigate', { url });
  await waitLoad;
  await delay(900);
}
async function login(client) {
  stage = 'login api session';
  await navigate(client, `${baseUrl}/#/login`);
  const loginResult = await evaluateJson(client, `JSON.stringify(await (async () => {
    localStorage.removeItem('unexamine.accessToken');
    localStorage.removeItem('unexamine.refreshToken');
    const response = await fetch('/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json; charset=utf-8' },
      body: JSON.stringify({ loginName: 'admin', password: '123123aa', loginTarget: 'PLATFORM' }),
    });
    const json = await response.json();
    const data = json.data || {};
    if (!response.ok || json.code !== 'SUCCESS' || !data.accessToken) {
      return { ok: false, status: response.status, json };
    }
    localStorage.setItem('unexamine.accessToken', data.accessToken);
    if (data.refreshToken) localStorage.setItem('unexamine.refreshToken', data.refreshToken);
    if (data.profile?.accountId) localStorage.setItem('unexamine.accountId', data.profile.accountId);
    return { ok: true, accountId: data.profile?.accountId || '' };
  })())`);
  assert(loginResult.ok, 'login api failed', loginResult);
}
async function clickSidebar(client, text, section) {
  stage = `click sidebar ${text}`;
  const selector = `[data-admin-section="${section}"]`;
  await client.send('Runtime.evaluate', { expression: `
    (() => {
      const selector = ${JSON.stringify(selector)};
      const label = ${JSON.stringify(text)};
      const button = document.querySelector(selector) || Array.from(document.querySelectorAll('.admin-sidebar button')).find((item) => item.textContent.includes(label));
      if (!button) throw new Error('sidebar button not found: ' + label);
      button.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    })()
  `, returnByValue: true, awaitPromise: true });
}
async function clickAction(client, action) {
  stage = `click action ${action}`;
  const selector = `[data-ops-action="${action}"]`;
  await client.send('Runtime.evaluate', { expression: `
    (() => {
      const selector = ${JSON.stringify(selector)};
      const action = ${JSON.stringify(action)};
      const button = document.querySelector(selector);
      if (!button) throw new Error('ops action not found: ' + action);
      button.click();
    })()
  `, returnByValue: true });
}
async function capture(client, key, extra = {}) {
  const metrics = await evaluateJson(client, `JSON.stringify((function(){var overflowX=Math.max(0,document.documentElement.scrollWidth-window.innerWidth);var blockers=[];var text=document.body.innerText||'';if(text.indexOf('undefined')>=0)blockers.push('undefined-copy');if(text.indexOf('NaN')>=0)blockers.push('nan-copy');return {overflowX:overflowX,blockers:blockers,hash:location.hash};})())`);
  const screenshot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  const screenshotFile = `${key}.png`;
  fs.writeFileSync(path.join(outDir, screenshotFile), Buffer.from(screenshot.data, 'base64'));
  return { key, screenshot: screenshotFile, ...metrics, ...extra };
}

(async () => {
  fs.mkdirSync(outDir, { recursive: true });
  const target = await newTarget();
  const client = await connect(target.webSocketDebuggerUrl);
  await client.send('Page.enable');
  await client.send('Runtime.enable');
  await login(client);
  const results = [];

  for (const viewport of [
    { name: 'desktop', width: 1440, height: 920, mobile: false },
    { name: 'mobile', width: 390, height: 760, mobile: true },
  ]) {
    await client.send('Emulation.setDeviceMetricsOverride', { width: viewport.width, height: viewport.height, deviceScaleFactor: 1, mobile: viewport.mobile });
    await client.send('Emulation.setVisibleSize', { width: viewport.width, height: viewport.height });
    await navigate(client, `${baseUrl}/#/platform/admin`);
    stage = `platform admin shell ${viewport.name}`;
    await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('[data-platform-admin-shell="true"]') || !!document.querySelector('[data-platform-admin-standalone="true"]') })`);
    await clickSidebar(client, '閰嶇疆绠＄悊', 'platform-config');
    stage = `platform ops panel ${viewport.name}`;
    await waitUntil(client, `JSON.stringify({
      ok: !!document.querySelector('#platform-ops-governance[data-platform-ops-governance="true"]') && document.querySelectorAll('[data-ops-action]').length >= 10,
      hash: location.hash,
      buttons: Array.from(document.querySelectorAll('.admin-sidebar button')).map((button) => button.textContent),
      content: document.querySelector('.admin-content')?.innerText?.slice(0, 800),
      hasOps: !!document.querySelector('#platform-ops-governance'),
      opsActionCount: document.querySelectorAll('[data-ops-action]').length
    })`);
    await clickAction(client, 'platform-health');
    stage = `platform health result ${viewport.name}`;
    await waitUntil(client, `JSON.stringify({ ok: document.querySelector('[data-ops-result="platform-health"]')?.dataset.opsTraceId?.length > 0 })`);
    const healthState = await evaluateJson(client, `JSON.stringify(document.querySelector('[data-ops-result="platform-health"]').dataset)`);
    await clickAction(client, 'backup');
    stage = `backup result ${viewport.name}`;
    await waitUntil(client, `JSON.stringify({ ok: document.querySelector('[data-ops-result="backup"]')?.dataset.opsTaskId?.length > 0 })`);
    const backupState = await evaluateJson(client, `JSON.stringify(document.querySelector('[data-ops-result="backup"]').dataset)`);
    await clickAction(client, 'restore-drill');
    stage = `restore drill result ${viewport.name}`;
    await waitUntil(client, `JSON.stringify({ ok: document.querySelector('[data-ops-result="restore-drill"]')?.dataset.opsDryRun === 'true' && document.querySelector('[data-ops-result="restore-drill"]')?.dataset.opsRollbackSupported === 'true' })`);
    const restoreState = await evaluateJson(client, `JSON.stringify(document.querySelector('[data-ops-result="restore-drill"]').dataset)`);
    await clickAction(client, 'deployments');
    stage = `deployment list ${viewport.name}`;
    await waitUntil(client, `JSON.stringify({ ok: Number(document.querySelector('[data-ops-deployment-list]')?.dataset.opsDeploymentCount || 0) >= 1 })`);
    await clickAction(client, 'cache-read');
    stage = `cache read ${viewport.name}`;
    await waitUntil(client, `JSON.stringify({ ok: Number(document.querySelector('[data-ops-cache-policy-list]')?.dataset.opsCachePolicyCount || 0) >= 1 })`);
    await clickAction(client, 'cache-update');
    stage = `cache update ${viewport.name}`;
    await waitUntil(client, `JSON.stringify({ ok: document.querySelector('[data-ops-result="cache-update"]')?.dataset.opsTraceId?.length > 0 })`);
    const cacheState = await evaluateJson(client, `JSON.stringify(document.querySelector('[data-ops-result="cache-update"]').dataset)`);
    results.push(await capture(client, `platform-ops-r75-${viewport.name}`, { viewport: viewport.name, healthState, backupState, restoreState, cacheState }));

    await clickSidebar(client, '鏃ュ織绠＄悊', 'platform-logs');
    stage = `platform logs panel ${viewport.name}`;
    await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('#platform-logs[data-platform-logs-panel="true"]') })`);
    const platformLogState = await evaluateJson(client, `JSON.stringify(document.querySelector('#platform-logs').dataset)`);
    results.push(await capture(client, `platform-logs-r75-${viewport.name}`, { viewport: viewport.name, platformLogState }));

    await navigate(client, `${baseUrl}/#/systems/${systemId}/admin/log-management`);
    stage = `system logs panel ${viewport.name}`;
    await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('[data-system-logs-panel="true"]') })`);
    const systemLogState = await evaluateJson(client, `JSON.stringify(document.querySelector('[data-system-logs-panel="true"]').dataset)`);
    results.push(await capture(client, `system-logs-r75-${viewport.name}`, { viewport: viewport.name, systemLogState }));
  }

  results.forEach((item) => assert(item.overflowX <= 2 && item.blockers.length === 0, 'Browser containment failed', item));
  fs.writeFileSync(path.join(outDir, 'operations-logs-release-error-browser-audit.json'), JSON.stringify({ status: 'PASS', results }, null, 2));
  client.close();
  console.log(JSON.stringify({ status: 'PASS', results }, null, 2));
})().catch((error) => {
  console.error(error.stack || error.message);
  process.exit(1);
});
