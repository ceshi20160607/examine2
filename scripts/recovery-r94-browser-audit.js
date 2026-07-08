const fs = require('fs');
const path = require('path');

const baseUrl = process.env.R94_BASE_URL;
const port = process.env.R94_CDP_PORT;
const outDir = process.env.R94_EVIDENCE_DIR;
if (!baseUrl || !port || !outDir) {
  throw new Error('R94_BASE_URL, R94_CDP_PORT, and R94_EVIDENCE_DIR are required.');
}

function delay(ms) { return new Promise((resolve) => setTimeout(resolve, ms)); }
async function cdpJson(pathname, options = {}) {
  let lastError;
  for (let attempt = 0; attempt < 60; attempt += 1) {
    try {
      const response = await fetch(`http://127.0.0.1:${port}${pathname}`, options);
      if (!response.ok) throw new Error(`CDP HTTP ${response.status} for ${pathname}`);
      return await response.json();
    } catch (error) {
      lastError = error;
      await delay(250);
    }
  }
  throw lastError || new Error(`CDP unavailable for ${pathname}`);
}
async function newTarget() {
  try {
    return await cdpJson(`/json/new?${encodeURIComponent('about:blank')}`, { method: 'PUT' });
  } catch {
    const targets = await cdpJson('/json/list');
    if (!targets.length) throw new Error('No CDP targets available.');
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
        else item.res(message);
      }
    };
  });
}
async function evaluate(client, expression) {
  const message = await client.send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  const payload = message.result || message;
  if (payload.exceptionDetails || message.exceptionDetails) throw new Error(JSON.stringify(payload.exceptionDetails || message.exceptionDetails));
  const remote = payload.result || payload;
  const value = remote.value;
  return typeof value === 'string' ? JSON.parse(value) : value;
}
async function waitFor(client, expression, timeout = 30000) {
  const started = Date.now();
  let last = null;
  while (Date.now() - started < timeout) {
    last = await evaluate(client, expression).catch((error) => ({ ok: false, error: error.message }));
    if (last && last.ok) return last;
    await delay(250);
  }
  throw new Error(`wait timeout: ${expression} last=${JSON.stringify(last)}`);
}
async function setViewport(client, viewport) {
  await client.send('Emulation.setDeviceMetricsOverride', {
    width: viewport.width,
    height: viewport.height,
    deviceScaleFactor: 1,
    mobile: viewport.mobile,
  });
}
async function navigate(client, route) {
  const url = `${baseUrl}/?r94=${Date.now()}#${route}`;
  await client.send('Page.navigate', { url });
  await waitFor(client, 'JSON.stringify({ ok: !!document.body, state: document.readyState })', 30000);
  await waitFor(client, 'JSON.stringify({ ok: document.body && document.body.innerText.length > 20, length: document.body ? document.body.innerText.length : 0 })', 30000);
  await delay(600);
}
async function loginViaForm(client) {
  await client.send('Storage.clearDataForOrigin', { origin: baseUrl, storageTypes: 'all' });
  await navigate(client, '/login');
  await waitFor(client, 'JSON.stringify({ ok: !!document.querySelector(\'[data-auth-form="login"]\') && !!document.querySelector(\'[data-auth-action="login"]\') })', 30000);
  const result = await evaluate(client, `(() => {
    localStorage.clear();
    sessionStorage.clear();
    const name = document.querySelector('input[data-field-name="loginName"]');
    const pass = document.querySelector('input[data-field-name="password"]');
    const button = document.querySelector('[data-auth-action="login"]');
    if (!name || !pass || !button) throw new Error('login form controls missing');
    name.value = 'admin';
    pass.value = '123123aa';
    name.dispatchEvent(new Event('input', { bubbles: true }));
    pass.dispatchEvent(new Event('input', { bubbles: true }));
    name.dispatchEvent(new Event('change', { bubbles: true }));
    pass.dispatchEvent(new Event('change', { bubbles: true }));
    button.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    return JSON.stringify({ ok: true });
  })()`);
  if (!result.ok) throw new Error('login form was not submitted');
  await waitFor(client, 'JSON.stringify({ ok: !!localStorage.getItem(\'unexamine.accessToken\') && !document.querySelector(\'[data-auth-form="login"]\'), hash: location.hash })', 40000);
}
function pageDefinitions() {
  const desktop = { name: 'desktop', width: 1440, height: 920, mobile: false };
  const mobile = { name: 'mobile', width: 390, height: 760, mobile: true };
  return [
    {
      key: 'platform-workbench',
      route: '/platform',
      viewports: [desktop, mobile],
      expectedSelectors: ['[data-platform-workbench="true"]', '[data-platform-primary-nav="true"]', '[data-platform-aux-nav="true"]', '[data-platform-system-entry-panel="true"]'],
      forbiddenSelectors: ['[data-platform-apps-page="true"]', '[data-platform-flow-page="true"]'],
      expectedTexts: ['平台工作台', '系统入口'],
      forbiddenTexts: [],
      maxPanels: 5,
      maxMainButtons: 24,
      minTextLength: 180,
    },
    {
      key: 'platform-flow',
      route: '/platform/flow',
      viewports: [desktop, mobile],
      expectedSelectors: ['[data-platform-flow-page="true"]', '[data-platform-flow-separated-from-application="true"]', '[data-platform-flow-list="true"]'],
      forbiddenSelectors: ['[data-platform-application-list="true"]', '[data-platform-system-entry-panel="true"]', '[data-platform-system-card]'],
      expectedTexts: ['Flow', '不是系统入口'],
      forbiddenTexts: [],
      maxPanels: 5,
      maxMainButtons: 8,
      minTextLength: 180,
    },
    {
      key: 'platform-apps',
      route: '/platform/apps',
      viewports: [desktop, mobile],
      expectedSelectors: ['[data-platform-apps-page="true"]', '[data-platform-application-list="true"]', '[data-platform-application-boundary="true"]'],
      forbiddenSelectors: ['[data-platform-system-card]', '[data-platform-system-entry-panel="true"]', '[data-system-switch-panel="true"]'],
      expectedTexts: ['应用', '不是进入系统的入口', '应用列表'],
      forbiddenTexts: [],
      maxPanels: 5,
      maxMainButtons: 12,
      minTextLength: 240,
      appBoundary: true,
    },
    {
      key: 'platform-work',
      route: '/platform/work',
      viewports: [desktop, mobile],
      expectedSelectors: ['[data-platform-work-page="true"]', '[data-platform-work-boundary="true"]'],
      forbiddenSelectors: ['[data-platform-system-card]', '[data-platform-application-list="true"]'],
      expectedTexts: ['工作', '平台工作'],
      forbiddenTexts: [],
      maxPanels: 4,
      maxMainButtons: 8,
      minTextLength: 160,
    },
    {
      key: 'platform-admin',
      route: '/platform/admin',
      viewports: [desktop],
      expectedSelectors: ['[data-platform-admin-standalone="true"]'],
      forbiddenSelectors: ['[data-platform-workbench="true"]', '[data-platform-system-entry-panel="true"]'],
      expectedTexts: ['平台'],
      forbiddenTexts: [],
      maxPanels: 18,
      maxMainButtons: 80,
      minTextLength: 160,
    },
  ];
}
async function capture(client, page, viewport) {
  await setViewport(client, viewport);
  await navigate(client, page.route);
  if (page.expectedSelectors.length) {
    const expected = JSON.stringify(page.expectedSelectors);
    await waitFor(client, `(() => {
      const selectors = ${expected};
      return JSON.stringify({ ok: selectors.some((selector) => !!document.querySelector(selector)) });
    })()`, 12000).catch(() => undefined);
  }
  const expectedSelectors = JSON.stringify(page.expectedSelectors);
  const forbiddenSelectors = JSON.stringify(page.forbiddenSelectors);
  const expectedTexts = JSON.stringify(page.expectedTexts || []);
  const forbiddenTexts = JSON.stringify(page.forbiddenTexts || []);
  const raw = await evaluate(client, `(() => {
    const expectedSelectors = ${expectedSelectors};
    const forbiddenSelectors = ${forbiddenSelectors};
    const expectedTexts = ${expectedTexts};
    const forbiddenTexts = ${forbiddenTexts};
    const text = document.body ? document.body.innerText || '' : '';
    const doc = document.documentElement;
    const body = document.body;
    const overflowX = Math.max(0, Math.ceil(Math.max(doc.scrollWidth, body.scrollWidth) - window.innerWidth));
    const blockerTokens = ['undefined', 'null', 'NaN', 'coming soon', 'Coming soon', '未实现', '占位', '待接入', '敬请期待', '操作已响应', '成功响应'].filter((token) => text.includes(token));
    const mojibakeTokens = ['鍒涘缓', '绯荤粺', '骞冲彴', '寰呭姙', '娑堟伅', '鐧诲綍', '璐﹀彿', '杩涘叆', '鎿嶄綔', '鍔犺浇', '澶辫触', '璇风', '妯″潡', '鏃堕棿', '鐘舵', '涓嶆', '€?', '�'].filter((token) => text.includes(token));
    const expectedSelectorResults = expectedSelectors.map((selector) => ({ selector, visible: !!document.querySelector(selector) }));
    const forbiddenSelectorResults = forbiddenSelectors.map((selector) => ({ selector, visible: !!document.querySelector(selector) }));
    const missingTexts = expectedTexts.filter((token) => !text.includes(token));
    const forbiddenTextHits = forbiddenTexts.filter((token) => text.includes(token));
    const controls = Array.from(document.querySelectorAll('main button, main [role="button"], main a, main input, main select, main textarea'));
    const controlOverflow = controls.map((el) => ({ text: (el.innerText || el.value || el.getAttribute('aria-label') || el.title || '').trim().slice(0, 80), delta: Math.ceil(el.scrollWidth - el.clientWidth) })).filter((item) => item.delta > 2);
    const panelCount = document.querySelectorAll('main .panel').length;
    const mainButtonCount = document.querySelectorAll('main button, main [role="button"]').length;
    const appRoot = document.querySelector('[data-platform-apps-page="true"]');
    const appRowCount = Number(appRoot ? appRoot.getAttribute('data-platform-application-row-count') || '0' : '0');
    const appSystemEntryCount = Number(appRoot ? appRoot.getAttribute('data-platform-apps-system-entry-count') || '0' : '0');
    return JSON.stringify({
      key: ${JSON.stringify(page.key)},
      route: ${JSON.stringify(page.route)},
      viewport: ${JSON.stringify(viewport.name)},
      hash: location.hash,
      textLength: text.length,
      overflowX,
      panelCount,
      mainButtonCount,
      controlOverflow,
      blockerTokens,
      mojibakeTokens,
      expectedSelectorResults,
      forbiddenSelectorResults,
      missingTexts,
      forbiddenTextHits,
      hasAuthForm: !!document.querySelector('[data-auth-form="login"]'),
      hasPlatformWorkbench: !!document.querySelector('[data-platform-workbench="true"]'),
      hasSystemEntryPanel: !!document.querySelector('[data-platform-system-entry-panel="true"]'),
      systemCardCount: document.querySelectorAll('[data-platform-system-card]').length,
      hasApplicationList: !!document.querySelector('[data-platform-application-list="true"]'),
      appRowCount,
      appSystemEntryCount,
      hasFlowList: !!document.querySelector('[data-platform-flow-list="true"]'),
      flowCapabilityCount: document.querySelectorAll('[data-platform-flow-capability]').length,
    });
  })()`);
  const result = typeof raw === 'string' ? JSON.parse(raw) : raw;
  result.blockers = [];
  if (result.hasAuthForm) result.blockers.push('still on login form');
  if (result.textLength < page.minTextLength) result.blockers.push(`text too sparse ${result.textLength}/${page.minTextLength}`);
  if (result.overflowX > 2) result.blockers.push(`horizontal overflow ${result.overflowX}`);
  if (result.controlOverflow.length) result.blockers.push(`control overflow ${result.controlOverflow.length}`);
  if (result.blockerTokens.length) result.blockers.push(`placeholder/generic text ${result.blockerTokens.join(',')}`);
  if (result.mojibakeTokens.length) result.blockers.push(`mojibake text ${result.mojibakeTokens.join(',')}`);
  const missingSelectors = result.expectedSelectorResults.filter((item) => !item.visible);
  const forbiddenVisible = result.forbiddenSelectorResults.filter((item) => item.visible);
  if (missingSelectors.length) result.blockers.push(`missing selectors ${missingSelectors.map((item) => item.selector).join(',')}`);
  if (forbiddenVisible.length) result.blockers.push(`forbidden selectors ${forbiddenVisible.map((item) => item.selector).join(',')}`);
  if (result.missingTexts.length) result.blockers.push(`missing text ${result.missingTexts.join(',')}`);
  if (result.forbiddenTextHits.length) result.blockers.push(`forbidden text ${result.forbiddenTextHits.join(',')}`);
  if (result.panelCount > page.maxPanels) result.blockers.push(`too many panels ${result.panelCount}/${page.maxPanels}`);
  if (result.mainButtonCount > page.maxMainButtons) result.blockers.push(`too many main buttons ${result.mainButtonCount}/${page.maxMainButtons}`);
  if (page.key === 'platform-workbench' && result.systemCardCount > 8) result.blockers.push(`system entry card pile too large ${result.systemCardCount}/8`);
  if (page.appBoundary) {
    if (result.systemCardCount > 0 || result.hasSystemEntryPanel || result.appSystemEntryCount !== 0) result.blockers.push('application page still exposes system entry');
    if (result.appRowCount < 3) result.blockers.push(`application rows too few ${result.appRowCount}`);
  }
  if (page.key === 'platform-flow' && result.flowCapabilityCount < 2) result.blockers.push(`flow capabilities too few ${result.flowCapabilityCount}`);
  const shot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
  const shotData = (shot.result && shot.result.data) || shot.data;
  fs.writeFileSync(path.join(outDir, `${page.key}-${viewport.name}.png`), Buffer.from(shotData, 'base64'));
  return result;
}
async function run() {
  fs.mkdirSync(outDir, { recursive: true });
  const target = await newTarget();
  const client = await connect(target.webSocketDebuggerUrl);
  await client.send('Page.enable');
  await client.send('Runtime.enable');
  const results = [];
  await loginViaForm(client);
  for (const page of pageDefinitions()) {
    for (const viewport of page.viewports) {
      results.push(await capture(client, page, viewport));
    }
  }
  client.close();
  const blockerResults = results.filter((item) => item.blockers.length > 0);
  const output = {
    status: blockerResults.length ? 'FAIL' : 'PASS',
    generatedAt: new Date().toISOString(),
    baseUrl,
    resultCount: results.length,
    blockerResultCount: blockerResults.length,
    overflowCount: results.filter((item) => item.overflowX > 2).length,
    mojibakeResultCount: results.filter((item) => item.mojibakeTokens.length > 0).length,
    platformApplicationBoundaryPassed: results.filter((item) => item.key === 'platform-apps').every((item) => item.appSystemEntryCount === 0 && item.systemCardCount === 0 && !item.hasSystemEntryPanel && item.appRowCount >= 3),
    results,
    screenshotEvidenceBoundary: 'Screenshots prove visual hierarchy, containment, and visible copy only. API/readback/permission assertions live in the R94 PowerShell aggregation and child scripts.',
  };
  fs.writeFileSync(path.join(outDir, 'fresh-deployed-role-journey-browser-audit.json'), JSON.stringify(output, null, 2));
  console.log(JSON.stringify(output));
}
run().catch((error) => {
  const output = { status: 'FAIL', error: error.stack || error.message };
  fs.mkdirSync(outDir, { recursive: true });
  fs.writeFileSync(path.join(outDir, 'fresh-deployed-role-journey-browser-audit.json'), JSON.stringify(output, null, 2));
  console.error(error.stack || error.message);
  process.exit(1);
});



