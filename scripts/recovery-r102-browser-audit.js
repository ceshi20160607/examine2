const fs = require('fs');
const path = require('path');

const baseUrl = process.env.R102_BASE_URL;
const port = Number(process.env.R102_CDP_PORT);
const outDir = process.env.R102_EVIDENCE_DIR;
const trialPack = JSON.parse(process.env.R102_TRIAL_PACK || '{}');
const flowCode = process.env.R102_FLOW_CODE || '';
const authRequestId = process.env.R102_AUTH_REQUEST_ID || '';
const authAppName = process.env.R102_AUTH_APP_NAME || '';

if (!baseUrl || !port || !outDir || !trialPack.admin || !trialPack.runtimeDailyUse || !trialPack.workflowTodoMessage) {
  throw new Error('R102_BASE_URL, R102_CDP_PORT, R102_EVIDENCE_DIR, and R102_TRIAL_PACK are required.');
}

function delay(ms) { return new Promise((resolve) => setTimeout(resolve, ms)); }

async function cdpJson(pathname, method = 'GET') {
  let lastError;
  for (let attempt = 0; attempt < 60; attempt += 1) {
    try {
      const response = await fetch(`http://127.0.0.1:${port}${pathname}`, { method });
      if (!response.ok) throw new Error(`CDP HTTP ${response.status}: ${await response.text()}`);
      return response.json();
    } catch (error) {
      lastError = error;
      await delay(250);
    }
  }
  throw lastError || new Error(`CDP unavailable for ${pathname}`);
}

async function newTarget() {
  try {
    return await cdpJson(`/json/new?${encodeURIComponent('about:blank')}`, 'PUT');
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
        else item.res(message.result || message);
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

async function waitUntil(client, expression, timeout = 30000) {
  const start = Date.now();
  let last = null;
  while (Date.now() - start < timeout) {
    last = await evaluate(client, `JSON.stringify({ ok: Boolean((() => { ${expression} })()) })`).catch((error) => ({ ok: false, error: error.message }));
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

async function navigate(client, route, viewport) {
  await setViewport(client, viewport);
  await client.send('Page.navigate', { url: `${baseUrl}/?r102=${Date.now()}#${route}` });
  await waitUntil(client, 'return !!document.body && document.readyState !== "loading";', 30000);
  await waitUntil(client, 'return !!document.body && (document.body.innerText || "").length > 20;', 30000);
  await delay(900);
}

async function loginViaForm(client, loginName, password, route, viewport) {
  await setViewport(client, viewport);
  await client.send('Storage.clearDataForOrigin', { origin: baseUrl, storageTypes: 'all' });
  await client.send('Page.navigate', { url: `${baseUrl}/?r102-login=${Date.now()}#/login` });
  await waitUntil(client, 'return !!document.querySelector("[data-auth-form=\\"login\\"]") && !!document.querySelector("[data-auth-action=\\"login\\"]");', 30000);
  const submitted = await evaluate(client, `JSON.stringify((() => {
    localStorage.clear();
    sessionStorage.clear();
    const name = document.querySelector('input[data-field-name="loginName"]');
    const pass = document.querySelector('input[data-field-name="password"]');
    const button = document.querySelector('[data-auth-action="login"]');
    if (!name || !pass || !button) return false;
    name.value = ${JSON.stringify(loginName)};
    pass.value = ${JSON.stringify(password)};
    name.dispatchEvent(new Event('input', { bubbles: true }));
    pass.dispatchEvent(new Event('input', { bubbles: true }));
    name.dispatchEvent(new Event('change', { bubbles: true }));
    pass.dispatchEvent(new Event('change', { bubbles: true }));
    button.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    return true;
  })())`);
  if (!submitted) throw new Error(`login form controls missing for ${loginName}`);
  await waitUntil(client, 'return !!localStorage.getItem("unexamine.accessToken") && !document.querySelector("[data-auth-form=\\"login\\"]");', 45000);
  await navigate(client, route, viewport);
}

async function captureScreenshot(client, name) {
  const shot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
  const data = (shot.result && shot.result.data) || shot.data;
  fs.writeFileSync(path.join(outDir, `${name}.png`), Buffer.from(data, 'base64'));
}

async function inspect(client, spec) {
  await loginViaForm(client, spec.loginName, spec.password, spec.route, spec.viewport);
  if (spec.selector) {
    const selector = spec.selector.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
    await waitUntil(client, `return !!document.querySelector("${selector}");`, spec.waitMs || 35000);
  }
  const result = await evaluate(client, `JSON.stringify((() => {
    const doc = document.documentElement;
    const body = document.body;
    const text = body ? body.innerText || '' : '';
    const controls = Array.from(document.querySelectorAll('main button, main [role="button"], main a, main input, main select, main textarea'));
    return {
      key: ${JSON.stringify(spec.key)},
      kind: ${JSON.stringify(spec.kind)},
      role: ${JSON.stringify(spec.role)},
      viewport: ${JSON.stringify(spec.viewport.name)},
      route: location.hash,
      textLength: text.length,
      h1: document.querySelector('h1')?.innerText || '',
      hasAuthForm: !!document.querySelector('[data-auth-form="login"]'),
      hasPlatformWorkbench: !!document.querySelector('[data-platform-workbench="true"]'),
      hasPlatformFlowPage: !!document.querySelector('[data-platform-flow-page="true"]'),
      hasPlatformAppsPage: !!document.querySelector('[data-platform-apps-page="true"]'),
      flowRowCount: document.querySelectorAll('[data-platform-flow-row]').length,
      flowRunBatchCount: document.querySelectorAll('[data-platform-flow-run-batch]').length,
      flowTraceCount: document.querySelectorAll('[data-platform-flow-trace-id]').length,
      authorizationRowCount: document.querySelectorAll('[data-platform-authorization-row]').length,
      requestIdMarkers: document.querySelectorAll('[data-request-id]').length,
      changeIdMarkers: document.querySelectorAll('[data-authorization-change-id]').length,
      appsSystemEntryCount: document.querySelector('[data-platform-apps-separated-from-system-entry="true"]')?.getAttribute('data-platform-apps-system-entry-count') || '',
      systemEntryPanelCount: document.querySelectorAll('[data-platform-system-entry-panel="true"]').length,
      systemCardCount: document.querySelectorAll('[data-platform-system-card]').length,
      hasRuntimeShell: !!document.querySelector('.runtime-shell'),
      hasRuntimeEmptyState: !!document.querySelector('.runtime-empty-state'),
      runtimeRecordRows: document.querySelectorAll('[data-runtime-record-row]').length,
      hasTodoWorkbench: !!document.querySelector('[data-system-todo-workbench="true"], [data-platform-todos-page="true"]'),
      hasMessageCenter: !!document.querySelector('[data-system-message-center="true"], [data-platform-messages-page="true"]'),
      expectedFlowVisible: ${JSON.stringify(flowCode)} ? text.includes(${JSON.stringify(flowCode)}) : true,
      expectedAuthorizationVisible: (${JSON.stringify(authRequestId)} ? text.includes(${JSON.stringify(authRequestId)}) : false) || (${JSON.stringify(authAppName)} ? text.includes(${JSON.stringify(authAppName)}) : false) || (!${JSON.stringify(authRequestId)} && !${JSON.stringify(authAppName)}),
      containsNoSystemWriteBoundary: text.includes('NO_SYSTEM_BUSINESS_WRITE') || text.includes('系统业务数据不在此页读写') || text.includes('不直接读写系统业务记录'),
      obviousBrokenText: ['undefined', 'NaN', 'Cannot read properties', 'TypeError', 'ReferenceError'].filter((word) => text.includes(word)),
      replacementCharCount: (text.match(/�/g) || []).length,
      mojibakeWarningCount: (text.match(/绯荤粺|璐﹀彿|鐧诲綍|鍔犺浇|鎿嶄綔/g) || []).length,
      overflowX: Math.max(0, Math.ceil(Math.max(doc.scrollWidth, body.scrollWidth) - window.innerWidth)),
      controlOverflowCount: controls.filter((el) => Math.ceil(el.scrollWidth - el.clientWidth) > 2).length,
    };
  })())`);
  result.blockers = [];
  result.warnings = [];
  if (result.hasAuthForm) result.blockers.push('still on login form');
  if (result.textLength < 20) result.blockers.push(`route text too short ${result.textLength}`);
  if (result.obviousBrokenText.length) result.blockers.push(`obvious broken text ${result.obviousBrokenText.join(',')}`);
  if (result.overflowX > 2) result.blockers.push(`horizontal overflow ${result.overflowX}`);
  if (result.controlOverflowCount > 0) result.blockers.push(`control overflow ${result.controlOverflowCount}`);
  if (result.replacementCharCount > 0 || result.mojibakeWarningCount > 0) result.warnings.push(`encoding warning replacement=${result.replacementCharCount} mojibake=${result.mojibakeWarningCount}`);
  if (spec.kind === 'platform-flow') {
    if (!result.hasPlatformFlowPage) result.blockers.push('platform Flow page marker missing');
    if (result.flowRowCount < 1) result.blockers.push(`flow row count ${result.flowRowCount}`);
    if (result.flowRunBatchCount < 1 || result.flowTraceCount < 1) result.blockers.push('flow run batch or trace markers missing');
    if (!result.expectedFlowVisible) result.blockers.push(`R101 flow code not visible: ${flowCode}`);
    if (result.systemEntryPanelCount !== 0 || result.systemCardCount !== 0) result.blockers.push('Flow page leaked system entry');
    if (!result.containsNoSystemWriteBoundary) result.blockers.push('Flow page missing platform/system boundary copy');
  }
  if (spec.kind === 'platform-apps') {
    if (!result.hasPlatformAppsPage) result.blockers.push('platform Application page marker missing');
    if (result.authorizationRowCount < 1) result.blockers.push(`authorization row count ${result.authorizationRowCount}`);
    if (result.requestIdMarkers < 1 || result.changeIdMarkers < 1) result.blockers.push('authorization id markers missing');
    if (result.appsSystemEntryCount !== '0') result.blockers.push(`apps system entry count ${result.appsSystemEntryCount}`);
    if (!result.expectedAuthorizationVisible) result.blockers.push('R101 authorization object not visible');
    if (result.systemEntryPanelCount !== 0 || result.systemCardCount !== 0) result.blockers.push('Application page leaked system entry');
    if (!result.containsNoSystemWriteBoundary) result.blockers.push('Application page missing platform/system boundary copy');
  }
  if (spec.kind === 'platform' && !result.hasPlatformWorkbench) result.blockers.push('platform workbench marker missing');
  if (spec.kind === 'runtime' && !result.hasRuntimeShell && !result.hasRuntimeEmptyState) result.blockers.push('runtime shell marker missing');
  if (spec.kind === 'todo' && !result.hasTodoWorkbench && !result.route.includes('/todos')) result.blockers.push('todo workbench marker missing');
  if (spec.kind === 'message' && !result.hasMessageCenter && !result.route.includes('/messages')) result.blockers.push('message center marker missing');
  await captureScreenshot(client, spec.key);
  return result;
}

(async () => {
  fs.mkdirSync(outDir, { recursive: true });
  const desktop = { name: 'desktop', width: 1440, height: 920, mobile: false };
  const mobile = { name: 'mobile', width: 390, height: 760, mobile: true };
  const admin = trialPack.admin;
  const runtime = trialPack.runtimeDailyUse;
  const workflow = trialPack.workflowTodoMessage;
  const cases = [
    { key: 'admin-platform-desktop', role: 'admin', loginName: admin.loginName, password: admin.password, route: '/platform', viewport: desktop, kind: 'platform', selector: '[data-platform-workbench="true"]' },
    { key: 'admin-flow-desktop', role: 'admin', loginName: admin.loginName, password: admin.password, route: '/platform/flow', viewport: desktop, kind: 'platform-flow', selector: '[data-platform-flow-page="true"]' },
    { key: 'admin-flow-mobile', role: 'admin', loginName: admin.loginName, password: admin.password, route: '/platform/flow', viewport: mobile, kind: 'platform-flow', selector: '[data-platform-flow-page="true"]' },
    { key: 'admin-apps-desktop', role: 'admin', loginName: admin.loginName, password: admin.password, route: '/platform/apps', viewport: desktop, kind: 'platform-apps', selector: '[data-platform-apps-page="true"]' },
    { key: 'admin-apps-mobile', role: 'admin', loginName: admin.loginName, password: admin.password, route: '/platform/apps', viewport: mobile, kind: 'platform-apps', selector: '[data-platform-apps-page="true"]' },
    { key: 'runtime-normal-desktop', role: 'runtime-normal', loginName: runtime.normalLoginName, password: runtime.password, route: `/systems/${runtime.systemId}/modules`, viewport: desktop, kind: 'runtime' },
    { key: 'runtime-readonly-mobile', role: 'runtime-readonly', loginName: runtime.readonlyLoginName, password: runtime.password, route: `/systems/${runtime.systemId}/modules`, viewport: mobile, kind: 'runtime' },
    { key: 'workflow-requester-desktop', role: 'workflow-requester', loginName: workflow.requesterLoginName, password: workflow.password, route: `/systems/${workflow.systemId}/modules`, viewport: desktop, kind: 'runtime' },
    { key: 'workflow-approver-todo-desktop', role: 'workflow-approver', loginName: workflow.approverLoginName, password: workflow.password, route: `/systems/${workflow.systemId}/todos`, viewport: desktop, kind: 'todo' },
    { key: 'workflow-approver-messages-mobile', role: 'workflow-approver', loginName: workflow.approverLoginName, password: workflow.password, route: `/systems/${workflow.systemId}/messages`, viewport: mobile, kind: 'message' },
  ];
  const target = await newTarget();
  const client = await connect(target.webSocketDebuggerUrl);
  try {
    await client.send('Page.enable');
    await client.send('Runtime.enable');
    const results = [];
    for (const item of cases) {
      results.push(await inspect(client, item));
    }
    const blockerResults = results.filter((item) => item.blockers.length > 0);
    const warningResults = results.filter((item) => item.warnings.length > 0);
    const output = {
      status: blockerResults.length ? 'FAIL' : 'PASS',
      generatedAt: new Date().toISOString(),
      baseUrl,
      resultCount: results.length,
      blockerResultCount: blockerResults.length,
      warningResultCount: warningResults.length,
      maxOverflowX: Math.max(...results.map((item) => item.overflowX || 0)),
      maxControlOverflowCount: Math.max(...results.map((item) => item.controlOverflowCount || 0)),
      flowPassed: results.filter((item) => item.kind === 'platform-flow').every((item) => item.blockers.length === 0),
      appsPassed: results.filter((item) => item.kind === 'platform-apps').every((item) => item.blockers.length === 0),
      trialRoutesPassed: results.filter((item) => ['runtime', 'todo', 'message'].includes(item.kind)).every((item) => item.blockers.length === 0),
      loginBoundary: 'Every smoke route starts from the deployed login page and submitted login form.',
      screenshotBoundary: 'Screenshots prove visible route, hierarchy, and containment only. Functional completion requires API/readback assertions and explicit user signoff.',
      expectedReadback: { flowCode, authRequestId, authAppName },
      results,
    };
    fs.writeFileSync(path.join(outDir, 'final-user-trial-refresh-browser-audit.json'), JSON.stringify(output, null, 2));
    console.log(JSON.stringify(output));
  } finally {
    client.close();
  }
})().catch((error) => {
  const output = { status: 'FAIL', error: error.stack || String(error) };
  fs.mkdirSync(outDir, { recursive: true });
  fs.writeFileSync(path.join(outDir, 'final-user-trial-refresh-browser-audit.json'), JSON.stringify(output, null, 2));
  console.error(error.stack || error.message);
  process.exit(1);
});
