const fs = require('fs');
const http = require('http');
const path = require('path');

const baseUrl = process.env.R46_BASE_URL;
const port = process.env.R46_CDP_PORT;
const outDir = process.env.R46_EVIDENCE_DIR;
const systemId = process.env.R46_SYSTEM_ID;
const moduleName = process.env.R46_MODULE_NAME;
const homeTitle = process.env.R46_HOME_TITLE;
const pageName = process.env.R46_PAGE_NAME;
const secretText = process.env.R46_SECRET_TEXT;

function delay(ms) { return new Promise((resolve) => setTimeout(resolve, ms)); }
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
      const message = JSON.parse(event.data);
      if (message.id && pending.has(message.id)) {
        const item = pending.get(message.id);
        pending.delete(message.id);
        if (message.error) item.rej(new Error(`${item.method}: ${JSON.stringify(message.error)}`));
        else item.res(message.result);
      }
    };
  });
}
async function evaluate(client, expression) {
  const result = await client.send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
  const value = result.result.value;
  return typeof value === 'string' ? JSON.parse(value) : value;
}
async function waitFor(client, expression, timeout = 30000) {
  const started = Date.now();
  while (Date.now() - started < timeout) {
    const value = await evaluate(client, expression);
    if (value && value.ok) return value;
    await delay(150);
  }
  throw new Error(`Timeout waiting for ${expression}`);
}
async function setViewport(client, viewport) {
  await client.send('Emulation.setDeviceMetricsOverride', {
    width: viewport.width,
    height: viewport.height,
    deviceScaleFactor: 1,
    mobile: viewport.mobile,
  });
  await client.send('Emulation.setVisibleSize', { width: viewport.width, height: viewport.height });
}
async function navigate(client, url) {
  await client.send('Page.navigate', { url });
  await waitFor(client, `JSON.stringify({ ok: document.readyState === 'complete' })`, 10000);
  await waitFor(client, `JSON.stringify({ ok: document.body && document.body.innerText.length > 20 })`, 30000);
}
async function clickByText(client, text) {
  return evaluate(client, `
    JSON.stringify((() => {
      const candidates = Array.from(document.querySelectorAll('button,a'));
      const target = candidates.find((el) => (el.innerText || '').trim() === ${JSON.stringify(text)});
      if (!target) return { clicked: false };
      if (target.disabled) return { clicked: false, disabled: true, text: target.innerText || '', title: target.title || '', ariaLabel: target.getAttribute('aria-label') || '' };
      target.click();
      return { clicked: true, disabled: false, text: target.innerText || '', title: target.title || '', ariaLabel: target.getAttribute('aria-label') || '' };
    })())
  `);
}
async function setStorage(client, role) {
  await navigate(client, `${baseUrl}/#/`);
  await evaluate(client, `(() => {
    localStorage.setItem('unexamine.accountId', ${JSON.stringify(role.accountId)});
    localStorage.setItem('unexamine.accessToken', ${JSON.stringify(role.accessToken)});
    localStorage.setItem('unexamine.refreshToken', ${JSON.stringify(role.refreshToken || '')});
    return JSON.stringify({ ok: true });
  })()`);
}
async function auditRoute(client, roleName, key, route, viewport, checks) {
  await setViewport(client, viewport);
  await navigate(client, `${baseUrl}/?r46=${Date.now()}#${route}`);
  if (key === 'module-config') {
    await waitFor(client, `JSON.stringify({ ok: (document.body.innerText || '').includes(${JSON.stringify(moduleName)}) && (document.body.innerText || '').includes('\u9875\u9762') })`, 30000);
    const clickedPageTab = await clickByText(client, '\u9875\u9762');
    if (!clickedPageTab.clicked) {
      throw new Error(`Could not click page designer tab on module-config route. text=${(await evaluate(client, `JSON.stringify({ text: (document.body.innerText || '').slice(0, 1200) })`)).text}`);
    }
  }
  if (checks.waitText) {
    await waitFor(client, `JSON.stringify({ ok: (document.body.innerText || '').includes(${JSON.stringify(checks.waitText)}) })`);
  }
  for (const selector of checks.selectors || []) {
    await waitFor(client, `JSON.stringify({ ok: !!document.querySelector(${JSON.stringify(selector)}) })`);
  }
  const raw = await evaluate(client, `(() => {
    const text = document.body.innerText || '';
    const selectors = ${JSON.stringify(checks.selectors || [])}.map((selector) => ({ selector, visible: !!document.querySelector(selector) }));
    const requiredTextsMissing = ${JSON.stringify(checks.requiredTexts || [])}.filter((item) => !text.includes(item));
    const forbiddenTexts = ${JSON.stringify(checks.forbiddenTexts || [])}.filter((item) => text.includes(item));
    const forbiddenSelectors = ${JSON.stringify(checks.forbiddenSelectors || [])}.filter((selector) => !!document.querySelector(selector));
    const placeholderTexts = ['TODO', 'coming soon', 'Coming soon', 'placeholder'].filter((item) => text.includes(item));
    const assetScripts = Array.from(document.scripts).map((script) => script.src).filter(Boolean);
    return JSON.stringify({
      role: ${JSON.stringify(roleName)},
      key: ${JSON.stringify(key)},
      viewport: ${JSON.stringify(viewport.name)},
      url: location.href,
      selectors,
      requiredTextsMissing,
      forbiddenTexts,
      forbiddenSelectors,
      placeholderTexts,
      assetScripts,
      overflowX: Math.max(0, document.documentElement.scrollWidth - document.documentElement.clientWidth),
      textSample: text.slice(0, 1200)
    });
  })()`);
  const result = typeof raw === 'string' ? JSON.parse(raw) : raw;
  result.blockers = [];
  if (result.selectors.some((item) => !item.visible)) result.blockers.push('expected selector missing');
  if (result.requiredTextsMissing.length) result.blockers.push('required text missing');
  if (result.forbiddenTexts.length) result.blockers.push('forbidden text visible');
  if (result.forbiddenSelectors.length) result.blockers.push('forbidden selector visible');
  if (result.placeholderTexts.length) result.blockers.push('placeholder copy visible');
  if (result.overflowX > 2) result.blockers.push('horizontal overflow');
  const screenshot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
  fs.writeFileSync(path.join(outDir, `${roleName}-${key}-${viewport.name}.png`), Buffer.from(screenshot.data, 'base64'));
  return result;
}
async function run() {
  fs.mkdirSync(outDir, { recursive: true });
  const target = await newTarget();
  const client = await connect(target.webSocketDebuggerUrl);
  await client.send('Page.enable');
  await client.send('Runtime.enable');
  const viewports = [
    { name: 'desktop', width: 1440, height: 920, mobile: false },
    { name: 'mobile', width: 390, height: 720, mobile: true },
  ];
  const roles = {
    admin: {
      accessToken: process.env.R46_ADMIN_TOKEN,
      refreshToken: process.env.R46_ADMIN_REFRESH,
      accountId: process.env.R46_ADMIN_ACCOUNT_ID || 'admin',
    },
    normal: {
      accessToken: process.env.R46_NORMAL_TOKEN,
      refreshToken: process.env.R46_NORMAL_REFRESH,
      accountId: process.env.R46_NORMAL_ACCOUNT_ID || 'normal',
    },
  };
  const results = [];
  await setStorage(client, roles.admin);
  for (const viewport of viewports) {
    results.push(await auditRoute(client, 'admin', 'dashboard', `/systems/${systemId}/dashboard`, viewport, {
      waitText: homeTitle,
      selectors: ['[data-home-overview="true"]', '[data-home-operations="true"]'],
      requiredTexts: [homeTitle],
      forbiddenSelectors: ['[data-module-page-designer="true"]', '[data-page-component-workbench="true"]'],
      forbiddenTexts: [secretText],
    }));
    results.push(await auditRoute(client, 'admin', 'dashboard-config', `/systems/${systemId}/admin/dashboard-config`, viewport, {
      waitText: homeTitle,
      selectors: ['[data-home-config-panel="true"]'],
      requiredTexts: [homeTitle],
      forbiddenSelectors: ['[data-module-page-designer="true"]'],
      forbiddenTexts: [secretText],
    }));
    results.push(await auditRoute(client, 'admin', 'module-config', `/systems/${systemId}/admin/module-config`, viewport, {
      waitText: pageName,
      selectors: ['[data-module-page-designer="true"]', '[data-page-component-workbench="true"]', '[data-page-mobile-preview="true"]'],
      requiredTexts: [moduleName, pageName, 'R46 Records', 'R46 Status Chart'],
      forbiddenTexts: [],
    }));
  }
  await setStorage(client, roles.normal);
  for (const viewport of viewports) {
    results.push(await auditRoute(client, 'normal', 'dashboard', `/systems/${systemId}/dashboard`, viewport, {
      waitText: homeTitle,
      selectors: ['[data-home-overview="true"]', '[data-home-operations="true"]'],
      requiredTexts: [homeTitle],
      forbiddenSelectors: ['[data-home-config-panel="true"]', '[data-module-page-designer="true"]'],
      forbiddenTexts: [secretText, 'System Admin', 'Platform Admin'],
    }));
    results.push(await auditRoute(client, 'normal', 'runtime-modules', `/systems/${systemId}/modules`, viewport, {
      waitText: moduleName,
      selectors: ['.runtime-shell', '.runtime-main'],
      requiredTexts: [moduleName],
      forbiddenSelectors: ['[data-home-config-panel="true"]', '[data-module-page-designer="true"]'],
      forbiddenTexts: [secretText, 'R46 Secret Note', 'R46 Secret Component'],
    }));
  }
  client.close();
  const output = { status: results.some((item) => item.blockers.length) ? 'FAIL' : 'PASS', results };
  fs.writeFileSync(path.join(outDir, 'page-surface-browser-audit.json'), JSON.stringify(output, null, 2));
  console.log(JSON.stringify(output));
  if (output.status !== 'PASS') process.exit(1);
}
run().catch((error) => {
  console.error(error && error.stack ? error.stack : String(error));
  process.exit(1);
});
