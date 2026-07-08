const fs = require('fs');
const path = require('path');

const baseUrl = process.env.R95_BASE_URL;
const port = process.env.R95_CDP_PORT;
const outDir = process.env.R95_EVIDENCE_DIR;
if (!baseUrl || !port || !outDir) {
  throw new Error('R95_BASE_URL, R95_CDP_PORT, and R95_EVIDENCE_DIR are required.');
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
  const url = `${baseUrl}/?r95=${Date.now()}#${route}`;
  await client.send('Page.navigate', { url });
  await waitFor(client, 'JSON.stringify({ ok: !!document.body, state: document.readyState })', 30000);
  await waitFor(client, 'JSON.stringify({ ok: document.body && document.body.innerText.length > 20, length: document.body ? document.body.innerText.length : 0 })', 30000);
  await delay(650);
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
async function captureScreenshot(client, name) {
  const shot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
  const data = (shot.result && shot.result.data) || shot.data;
  fs.writeFileSync(path.join(outDir, `${name}.png`), Buffer.from(data, 'base64'));
}
async function inspectWorkbench(client, viewport) {
  await setViewport(client, viewport);
  await navigate(client, '/platform');
  await waitFor(client, 'JSON.stringify({ ok: !!document.querySelector(\'[data-platform-system-entry-panel="true"]\') })', 20000);
  const raw = await evaluate(client, `(() => {
    const doc = document.documentElement;
    const body = document.body;
    const text = body ? body.innerText || '' : '';
    const panel = document.querySelector('[data-platform-system-entry-panel="true"]');
    const rows = Array.from(document.querySelectorAll('[data-platform-system-entry-row]'));
    const rowRects = rows.map((row) => row.getBoundingClientRect());
    const search = document.querySelector('[data-platform-system-entry-search="true"]');
    const list = document.querySelector('[data-platform-system-entry-list="true"]');
    const empty = document.querySelector('[data-platform-system-entry-filtered-empty="true"]');
    let searchFilter = { checked: false, visibleRowsAfterMiss: null, emptyVisibleAfterMiss: null, restoredRows: null };
    if (search && rows.length) {
      search.value = '__r95_no_match__';
      search.dispatchEvent(new Event('input', { bubbles: true }));
      searchFilter = {
        checked: true,
        visibleRowsAfterMiss: rows.filter((row) => !row.hidden).length,
        emptyVisibleAfterMiss: empty ? !empty.hidden : false,
        restoredRows: null,
      };
      search.value = '';
      search.dispatchEvent(new Event('input', { bubbles: true }));
      searchFilter.restoredRows = rows.filter((row) => !row.hidden).length;
    }
    const controls = Array.from(document.querySelectorAll('main button, main [role="button"], main a, main input, main select, main textarea'));
    return JSON.stringify({
      route: '/platform',
      viewport: ${JSON.stringify(viewport.name)},
      textLength: text.length,
      hasWorkbench: !!document.querySelector('[data-platform-workbench="true"]'),
      hasEntryPanel: !!panel,
      entryMode: panel ? panel.getAttribute('data-platform-system-entry-mode') || '' : '',
      entryCardCountMarker: Number(panel ? panel.getAttribute('data-platform-system-entry-card-count') || '-1' : '-1'),
      systemSwitchCount: Number(panel ? panel.getAttribute('data-system-switch-count') || '-1' : '-1'),
      rowCount: rows.length,
      enabledRows: rows.filter((row) => row.getAttribute('data-platform-system-entry-enabled') === 'true').length,
      targetRows: rows.filter((row) => row.getAttribute('data-platform-system-entry-target') === 'true').length,
      currentRows: rows.filter((row) => row.getAttribute('data-platform-system-entry-current') === 'true').length,
      searchCount: document.querySelectorAll('[data-platform-system-entry-search="true"]').length,
      listCount: document.querySelectorAll('[data-platform-system-entry-list="true"]').length,
      systemCardCount: document.querySelectorAll('[data-platform-system-card]').length,
      rowRoleButtonCount: document.querySelectorAll('[data-platform-system-entry-row][role="button"]').length,
      panelCount: document.querySelectorAll('main .panel').length,
      mainButtonCount: document.querySelectorAll('main button, main [role="button"]').length,
      mainControlCount: controls.length,
      overflowX: Math.max(0, Math.ceil(Math.max(doc.scrollWidth, body.scrollWidth) - window.innerWidth)),
      rowOverflowCount: rowRects.filter((rect) => rect.width - window.innerWidth > 2).length,
      controlOverflowCount: controls.filter((el) => Math.ceil(el.scrollWidth - el.clientWidth) > 2).length,
      hasAppsPage: !!document.querySelector('[data-platform-apps-page="true"]'),
      hasFlowPage: !!document.querySelector('[data-platform-flow-page="true"]'),
      containsSystemEntryText: text.includes('系统入口'),
      searchFilter,
      listVisibleRows: list ? Number(list.getAttribute('data-platform-system-entry-visible-rows') || rows.length) : -1,
    });
  })()`);
  const result = typeof raw === 'string' ? JSON.parse(raw) : raw;
  result.blockers = [];
  if (!result.hasWorkbench) result.blockers.push('missing platform workbench marker');
  if (!result.hasEntryPanel) result.blockers.push('missing system entry panel');
  if (result.entryMode !== 'compact-list') result.blockers.push(`entry mode is ${result.entryMode || '(empty)'}`);
  if (result.entryCardCountMarker !== 0) result.blockers.push(`entry card count marker is ${result.entryCardCountMarker}`);
  if (result.rowCount !== result.systemSwitchCount) result.blockers.push(`row count mismatch rows=${result.rowCount} switch=${result.systemSwitchCount}`);
  if (result.rowCount <= 0) result.blockers.push('no system entry rows rendered');
  if (result.searchCount !== 1) result.blockers.push(`search count ${result.searchCount}`);
  if (result.listCount !== 1) result.blockers.push(`list count ${result.listCount}`);
  if (result.systemCardCount !== 0) result.blockers.push(`system card pile remains ${result.systemCardCount}`);
  if (result.rowRoleButtonCount !== 0) result.blockers.push(`entry rows still exposed as role=button ${result.rowRoleButtonCount}`);
  if (result.panelCount > 5) result.blockers.push(`too many panels ${result.panelCount}`);
  if (result.mainButtonCount > 10) result.blockers.push(`too many main buttons ${result.mainButtonCount}`);
  if (result.overflowX > 2) result.blockers.push(`horizontal overflow ${result.overflowX}`);
  if (result.rowOverflowCount > 0) result.blockers.push(`entry row overflow ${result.rowOverflowCount}`);
  if (result.controlOverflowCount > 0) result.blockers.push(`control overflow ${result.controlOverflowCount}`);
  if (result.hasAppsPage || result.hasFlowPage) result.blockers.push('workbench mixed with apps/flow page marker');
  if (!result.containsSystemEntryText) result.blockers.push('missing visible system-entry copy');
  if (!result.searchFilter.checked) result.blockers.push('search filter was not exercised');
  if (result.searchFilter.checked && result.searchFilter.visibleRowsAfterMiss !== 0) result.blockers.push(`search miss still shows ${result.searchFilter.visibleRowsAfterMiss} rows`);
  if (result.searchFilter.checked && !result.searchFilter.emptyVisibleAfterMiss) result.blockers.push('search miss empty state not visible');
  if (result.searchFilter.checked && result.searchFilter.restoredRows !== result.rowCount) result.blockers.push(`search clear restored ${result.searchFilter.restoredRows}/${result.rowCount}`);
  await captureScreenshot(client, `platform-workbench-${viewport.name}`);
  return result;
}
async function inspectBoundaryPage(client, page, viewport) {
  await setViewport(client, viewport);
  await navigate(client, page.route);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('${page.requiredSelector}') })`, 20000);
  const raw = await evaluate(client, `(() => {
    const doc = document.documentElement;
    const body = document.body;
    const text = body ? body.innerText || '' : '';
    return JSON.stringify({
      route: ${JSON.stringify(page.route)},
      viewport: ${JSON.stringify(viewport.name)},
      textLength: text.length,
      hasRequired: !!document.querySelector(${JSON.stringify(page.requiredSelector)}),
      systemCardCount: document.querySelectorAll('[data-platform-system-card]').length,
      entryPanelCount: document.querySelectorAll('[data-platform-system-entry-panel="true"]').length,
      appRowCount: document.querySelectorAll('[data-platform-application-row]').length,
      flowCapabilityCount: document.querySelectorAll('[data-platform-flow-capability]').length,
      overflowX: Math.max(0, Math.ceil(Math.max(doc.scrollWidth, body.scrollWidth) - window.innerWidth)),
      mainButtonCount: document.querySelectorAll('main button, main [role="button"]').length,
      containsExpectedText: text.includes(${JSON.stringify(page.expectedText)}),
    });
  })()`);
  const result = typeof raw === 'string' ? JSON.parse(raw) : raw;
  result.blockers = [];
  if (!result.hasRequired) result.blockers.push(`missing ${page.requiredSelector}`);
  if (result.systemCardCount !== 0) result.blockers.push(`system cards leaked ${result.systemCardCount}`);
  if (result.entryPanelCount !== 0) result.blockers.push(`system entry panel leaked ${result.entryPanelCount}`);
  if (result.overflowX > 2) result.blockers.push(`horizontal overflow ${result.overflowX}`);
  if (!result.containsExpectedText) result.blockers.push(`missing expected text ${page.expectedText}`);
  if (page.route === '/platform/apps' && result.appRowCount < 3) result.blockers.push(`application rows too few ${result.appRowCount}`);
  if (page.route === '/platform/flow' && result.flowCapabilityCount < 2) result.blockers.push(`flow capabilities too few ${result.flowCapabilityCount}`);
  if (result.mainButtonCount > page.maxButtons) result.blockers.push(`too many main buttons ${result.mainButtonCount}/${page.maxButtons}`);
  await captureScreenshot(client, `${page.key}-${viewport.name}`);
  return result;
}
async function run() {
  fs.mkdirSync(outDir, { recursive: true });
  const desktop = { name: 'desktop', width: 1440, height: 920, mobile: false };
  const mobile = { name: 'mobile', width: 390, height: 760, mobile: true };
  const target = await newTarget();
  const client = await connect(target.webSocketDebuggerUrl);
  await client.send('Page.enable');
  await client.send('Runtime.enable');
  const results = [];
  await loginViaForm(client);
  for (const viewport of [desktop, mobile]) {
    results.push(await inspectWorkbench(client, viewport));
  }
  for (const page of [
    { key: 'platform-apps', route: '/platform/apps', requiredSelector: '[data-platform-apps-page="true"]', expectedText: '不是进入系统的入口', maxButtons: 12 },
    { key: 'platform-flow', route: '/platform/flow', requiredSelector: '[data-platform-flow-page="true"]', expectedText: '不是系统入口', maxButtons: 8 },
  ]) {
    for (const viewport of [desktop, mobile]) {
      results.push(await inspectBoundaryPage(client, page, viewport));
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
    workbenchPassed: results.filter((item) => item.route === '/platform').every((item) => item.blockers.length === 0),
    boundaryPassed: results.filter((item) => item.route !== '/platform').every((item) => item.blockers.length === 0),
    maxMainButtonsOnWorkbench: Math.max(...results.filter((item) => item.route === '/platform').map((item) => item.mainButtonCount)),
    maxOverflowX: Math.max(...results.map((item) => item.overflowX || 0)),
    results,
    screenshotEvidenceBoundary: 'Screenshots prove the visible compact-list layout and responsive containment. Static and release checks are in the R95 PowerShell result.',
  };
  fs.writeFileSync(path.join(outDir, 'platform-workbench-system-entry-density-browser-audit.json'), JSON.stringify(output, null, 2));
  console.log(JSON.stringify(output));
}
run().catch((error) => {
  const output = { status: 'FAIL', error: error.stack || error.message };
  fs.mkdirSync(outDir, { recursive: true });
  fs.writeFileSync(path.join(outDir, 'platform-workbench-system-entry-density-browser-audit.json'), JSON.stringify(output, null, 2));
  console.error(error.stack || error.message);
  process.exit(1);
});
