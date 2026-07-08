const fs = require('fs');
const path = require('path');

const baseUrl = process.env.R100_BASE_URL;
const port = process.env.R100_CDP_PORT;
const outDir = process.env.R100_EVIDENCE_DIR;
if (!baseUrl || !port || !outDir) {
  throw new Error('R100_BASE_URL, R100_CDP_PORT, and R100_EVIDENCE_DIR are required.');
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
  const url = `${baseUrl}/?r100=${Date.now()}#${route}`;
  await client.send('Page.navigate', { url });
  await waitFor(client, 'JSON.stringify({ ok: !!document.body, state: document.readyState })', 30000);
  await waitFor(client, 'JSON.stringify({ ok: document.body && document.body.innerText.length > 20, length: document.body ? document.body.innerText.length : 0 })', 30000);
  await delay(700);
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
function basePageFacts() {
  const doc = document.documentElement;
  const body = document.body;
  const controls = Array.from(document.querySelectorAll('main button, main [role="button"], main a, main input, main select, main textarea'));
  const text = body ? body.innerText || '' : '';
  return {
    textLength: text.length,
    text,
    systemEntryPanelCount: document.querySelectorAll('[data-platform-system-entry-panel="true"]').length,
    systemCardCount: document.querySelectorAll('[data-platform-system-card]').length,
    overflowX: Math.max(0, Math.ceil(Math.max(doc.scrollWidth, body.scrollWidth) - window.innerWidth)),
    controlOverflowCount: controls.filter((el) => Math.ceil(el.scrollWidth - el.clientWidth) > 2).length,
    mainButtonCount: document.querySelectorAll('main button, main [role="button"]').length,
  };
}
async function inspectFlow(client, viewport) {
  await setViewport(client, viewport);
  await navigate(client, '/platform/flow');
  await waitFor(client, 'JSON.stringify({ ok: !!document.querySelector(\'[data-platform-flow-page="true"]\') && document.querySelectorAll(\'[data-platform-flow-row]\').length >= 3 })', 20000);
  const raw = await evaluate(client, `(() => {
    const retry = document.querySelector('[data-platform-flow-retry-action]:not(:disabled)');
    if (retry) retry.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    const compensation = document.querySelector('[data-platform-flow-compensation-action]');
    if (compensation) compensation.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    const facts = (${basePageFacts.toString()})();
    const detail = document.querySelector('[data-platform-flow-detail-panel]');
    const result = document.querySelector('[data-platform-flow-result]');
    return JSON.stringify({
      route: '/platform/flow',
      viewport: ${JSON.stringify(viewport.name)},
      ...facts,
      hasPage: !!document.querySelector('[data-platform-flow-page="true"]'),
      hasWorkbench: !!document.querySelector('[data-platform-flow-workbench="true"]'),
      rowCount: document.querySelectorAll('[data-platform-flow-row]').length,
      detailPanelCount: document.querySelectorAll('[data-platform-flow-detail-panel]').length,
      runBatchMarkers: document.querySelectorAll('[data-platform-flow-run-batch]').length,
      traceMarkers: document.querySelectorAll('[data-platform-flow-trace-id]').length,
      retryActions: document.querySelectorAll('[data-platform-flow-retry-action]').length,
      compensationActions: document.querySelectorAll('[data-platform-flow-compensation-action]').length,
      createActions: document.querySelectorAll('[data-platform-flow-create-action="true"]').length,
      resultState: result ? result.getAttribute('data-platform-flow-result') || '' : '',
      resultTaskId: result ? result.getAttribute('data-platform-flow-task-id') || '' : '',
      detailRunBatch: detail ? detail.getAttribute('data-platform-flow-run-batch') || '' : '',
      appPageLeak: document.querySelectorAll('[data-platform-apps-page="true"]').length,
      containsDirectBusinessWrite: /系统业务写入/.test(facts.text) || (facts.text.includes('直接写入系统业务') && !facts.text.includes('不直接写入系统业务')) || (facts.text.includes('业务记录直达') && !facts.text.includes('不提供系统业务记录直达')) ,
      containsFlowBoundaryCopy: facts.text.includes('Flow') && facts.text.includes('traceId'),
    });
  })()`);
  const result = typeof raw === 'string' ? JSON.parse(raw) : raw;
  result.blockers = [];
  if (!result.hasPage || !result.hasWorkbench) result.blockers.push('missing Flow workbench markers');
  if (result.rowCount < 3) result.blockers.push(`flow rows too few ${result.rowCount}`);
  if (result.detailPanelCount !== 1) result.blockers.push(`flow detail panel count ${result.detailPanelCount}`);
  if (result.runBatchMarkers < 4) result.blockers.push(`run batch markers too few ${result.runBatchMarkers}`);
  if (result.traceMarkers < 3) result.blockers.push(`trace markers too few ${result.traceMarkers}`);
  if (result.retryActions < 3) result.blockers.push(`retry actions too few ${result.retryActions}`);
  if (result.compensationActions < 3) result.blockers.push(`compensation actions too few ${result.compensationActions}`);
  if (result.createActions !== 1) result.blockers.push(`create action count ${result.createActions}`);
  if (!['compensation', 'retry'].includes(result.resultState)) result.blockers.push(`flow result state ${result.resultState}`);
  if (!result.resultTaskId) result.blockers.push('flow result task id missing');
  if (!result.detailRunBatch) result.blockers.push('flow detail run batch missing');
  if (result.systemEntryPanelCount !== 0) result.blockers.push(`system entry leaked ${result.systemEntryPanelCount}`);
  if (result.systemCardCount !== 0) result.blockers.push(`system cards leaked ${result.systemCardCount}`);
  if (result.appPageLeak !== 0) result.blockers.push(`apps page leaked ${result.appPageLeak}`);
  if (result.containsDirectBusinessWrite) result.blockers.push('copy implies direct system business write');
  if (!result.containsFlowBoundaryCopy) result.blockers.push('missing Flow boundary/trace copy');
  if (result.overflowX > 2) result.blockers.push(`horizontal overflow ${result.overflowX}`);
  if (result.controlOverflowCount > 0) result.blockers.push(`control overflow ${result.controlOverflowCount}`);
  await captureScreenshot(client, `platform-flow-${viewport.name}`);
  return result;
}
async function inspectApps(client, viewport) {
  await setViewport(client, viewport);
  await navigate(client, '/platform/apps');
  await waitFor(client, 'JSON.stringify({ ok: !!document.querySelector(\'[data-platform-apps-page="true"]\') && document.querySelectorAll(\'[data-platform-authorization-row]\').length >= 3 })', 20000);
  const raw = await evaluate(client, `(() => {
    const request = document.querySelector('[data-platform-authorization-request]:not(:disabled)');
    if (request) request.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    const facts = (${basePageFacts.toString()})();
    const result = document.querySelector('.platform-action-result[data-platform-authorization-request]');
    const detail = document.querySelector('[data-platform-authorization-detail-panel]');
    return JSON.stringify({
      route: '/platform/apps',
      viewport: ${JSON.stringify(viewport.name)},
      ...facts,
      hasPage: !!document.querySelector('[data-platform-apps-page="true"]'),
      hasWorkbench: !!document.querySelector('[data-platform-authorization-workbench="true"]'),
      separated: document.querySelector('[data-platform-apps-separated-from-system-entry="true"]')?.getAttribute('data-platform-apps-system-entry-count') === '0',
      authorizationRowCount: document.querySelectorAll('[data-platform-authorization-row]').length,
      applicationRowCount: document.querySelectorAll('[data-platform-application-row]').length,
      requestActionCount: document.querySelectorAll('[data-platform-authorization-request]').length,
      detailPanelCount: document.querySelectorAll('[data-platform-authorization-detail-panel]').length,
      requestIdMarkers: document.querySelectorAll('[data-request-id]').length,
      changeIdMarkers: document.querySelectorAll('[data-authorization-change-id]').length,
      resultState: result ? result.getAttribute('data-platform-authorization-request') || '' : '',
      resultRequestId: result ? result.getAttribute('data-request-id') || '' : '',
      resultChangeId: result ? result.getAttribute('data-authorization-change-id') || '' : '',
      detailRequestId: detail ? detail.getAttribute('data-request-id') || '' : '',
      flowPageLeak: document.querySelectorAll('[data-platform-flow-page="true"]').length,
      containsForbiddenEnterSystemText: facts.text.includes('进入系统'),
      containsBoundaryCopy: facts.text.includes('requestId') && facts.text.includes('authorizationChangeId'),
    });
  })()`);
  const result = typeof raw === 'string' ? JSON.parse(raw) : raw;
  result.blockers = [];
  if (!result.hasPage || !result.hasWorkbench) result.blockers.push('missing Application authorization workbench markers');
  if (!result.separated) result.blockers.push('apps page did not keep zero system-entry marker');
  if (result.authorizationRowCount < 3) result.blockers.push(`authorization rows too few ${result.authorizationRowCount}`);
  if (result.applicationRowCount < 3) result.blockers.push(`application rows too few ${result.applicationRowCount}`);
  if (result.requestActionCount < 3) result.blockers.push(`request actions too few ${result.requestActionCount}`);
  if (result.detailPanelCount !== 1) result.blockers.push(`authorization detail count ${result.detailPanelCount}`);
  if (result.requestIdMarkers < 4) result.blockers.push(`requestId markers too few ${result.requestIdMarkers}`);
  if (result.changeIdMarkers < 4) result.blockers.push(`authorizationChangeId markers too few ${result.changeIdMarkers}`);
  if (!['request', 'adjust', 'view', 'create'].includes(result.resultState)) result.blockers.push(`authorization result state ${result.resultState}`);
  if (!result.resultRequestId || !result.resultChangeId || !result.detailRequestId) result.blockers.push('authorization request/change/detail ids missing');
  if (result.systemEntryPanelCount !== 0) result.blockers.push(`system entry leaked ${result.systemEntryPanelCount}`);
  if (result.systemCardCount !== 0) result.blockers.push(`system cards leaked ${result.systemCardCount}`);
  if (result.flowPageLeak !== 0) result.blockers.push(`flow page leaked ${result.flowPageLeak}`);
  if (result.containsForbiddenEnterSystemText) result.blockers.push('Application page still contains forbidden enter-system copy');
  if (!result.containsBoundaryCopy) result.blockers.push('missing authorization id boundary copy');
  if (result.overflowX > 2) result.blockers.push(`horizontal overflow ${result.overflowX}`);
  if (result.controlOverflowCount > 0) result.blockers.push(`control overflow ${result.controlOverflowCount}`);
  await captureScreenshot(client, `platform-apps-${viewport.name}`);
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
    results.push(await inspectFlow(client, viewport));
    results.push(await inspectApps(client, viewport));
  }
  client.close();
  const blockerResults = results.filter((item) => item.blockers.length > 0);
  const output = {
    status: blockerResults.length ? 'FAIL' : 'PASS',
    generatedAt: new Date().toISOString(),
    baseUrl,
    resultCount: results.length,
    blockerResultCount: blockerResults.length,
    flowPassed: results.filter((item) => item.route === '/platform/flow').every((item) => item.blockers.length === 0),
    appsPassed: results.filter((item) => item.route === '/platform/apps').every((item) => item.blockers.length === 0),
    maxOverflowX: Math.max(...results.map((item) => item.overflowX || 0)),
    results,
    screenshotEvidenceBoundary: 'Screenshots prove visual hierarchy, density, selected detail visibility, and responsive containment only. Functional completion requires API/readback assertions and user signoff outside screenshots.',
  };
  fs.writeFileSync(path.join(outDir, 'platform-flow-application-depth-browser-audit.json'), JSON.stringify(output, null, 2));
  console.log(JSON.stringify(output));
}
run().catch((error) => {
  const output = { status: 'FAIL', error: error.stack || error.message };
  fs.mkdirSync(outDir, { recursive: true });
  fs.writeFileSync(path.join(outDir, 'platform-flow-application-depth-browser-audit.json'), JSON.stringify(output, null, 2));
  console.error(error.stack || error.message);
  process.exit(1);
});

