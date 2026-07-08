const fs = require('fs');
const path = require('path');

const baseUrl = process.env.R98_BASE_URL;
const port = Number(process.env.R98_CDP_PORT);
const outDir = process.env.R98_EVIDENCE_DIR;
const trialPack = JSON.parse(process.env.R98_TRIAL_PACK || '{}');
const r97SystemId = process.env.R98_R97_SYSTEM_ID || '';

if (!baseUrl || !port || !outDir || !trialPack.admin || !trialPack.runtimeDailyUse || !trialPack.workflowTodoMessage) {
  throw new Error('R98_BASE_URL, R98_CDP_PORT, R98_EVIDENCE_DIR, and R98_TRIAL_PACK are required.');
}

function delay(ms) { return new Promise((resolve) => setTimeout(resolve, ms)); }

async function cdpJson(pathname, method = 'GET') {
  const response = await fetch(`http://127.0.0.1:${port}${pathname}`, { method });
  if (!response.ok) {
    throw new Error(`CDP HTTP ${response.status}: ${await response.text()}`);
  }
  return response.json();
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

async function navigate(client, pathFragment, viewport) {
  await setViewport(client, viewport);
  await client.send('Page.navigate', { url: `${baseUrl}/?r98=${Date.now()}#${pathFragment}` });
  await waitUntil(client, 'return !!document.body && document.readyState !== "loading";', 30000);
  await waitUntil(client, 'return !!document.body && (document.body.innerText || "").length > 20;', 30000);
  await delay(900);
}

async function loginViaForm(client, loginName, password, targetPath, viewport) {
  await setViewport(client, viewport);
  await client.send('Storage.clearDataForOrigin', { origin: baseUrl, storageTypes: 'all' });
  await client.send('Page.navigate', { url: `${baseUrl}/?r98-login=${Date.now()}#/login` });
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
    button.click();
    return true;
  })())`);
  if (!submitted) throw new Error(`login form controls missing for ${loginName}`);
  await waitUntil(client, 'return !!localStorage.getItem("unexamine.accessToken") && !document.querySelector("[data-auth-form=\\"login\\"]");', 45000);
  if (targetPath) {
    await navigate(client, targetPath, viewport);
  }
}

async function captureScreenshot(client, name) {
  const shot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
  const data = (shot.result && shot.result.data) || shot.data;
  fs.writeFileSync(path.join(outDir, `${name}.png`), Buffer.from(data, 'base64'));
}

async function inspect(client, spec) {
  await loginViaForm(client, spec.loginName, spec.password, spec.path, spec.viewport);
  if (spec.selector) {
    const escaped = spec.selector.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
    await waitUntil(client, `return !!document.querySelector("${escaped}");`, spec.waitMs || 35000);
  }
  const result = await evaluate(client, `JSON.stringify((() => {
    const doc = document.documentElement;
    const body = document.body;
    const text = body ? body.innerText || '' : '';
    const controls = Array.from(document.querySelectorAll('main button, main [role="button"], main a, main input, main select, main textarea'));
    return {
      key: ${JSON.stringify(spec.key)},
      role: ${JSON.stringify(spec.role)},
      viewport: ${JSON.stringify(spec.viewport.name)},
      route: location.hash,
      textLength: text.length,
      h1: document.querySelector('h1')?.innerText || '',
      hasAuthForm: !!document.querySelector('[data-auth-form="login"]'),
      hasWorkspaceShell: !!document.querySelector('[data-platform-shell="workbench"], .workspace-shell'),
      hasPlatformWorkbench: !!document.querySelector('[data-platform-workbench="true"]'),
      hasPlatformAppsPage: !!document.querySelector('[data-platform-apps-page="true"]'),
      hasPlatformFlowPage: !!document.querySelector('[data-platform-flow-page="true"]'),
      platformAppsSystemEntryCount: document.querySelector('[data-platform-apps-page="true"]')?.dataset.platformAppsSystemEntryCount || '',
      systemEntryCardCount: document.querySelector('[data-platform-system-entry-panel="true"]')?.dataset.platformSystemEntryCardCount || '',
      systemEntryRowCount: document.querySelector('[data-platform-system-entry-list="true"]')?.dataset.platformSystemEntryRowCount || '',
      hasR97EmptyDashboard: !!document.querySelector('[data-r97-empty-system-dashboard="true"]'),
      hasR97C1Guide: !!document.querySelector('[data-r97-c1-guide="true"]'),
      r97C1StepCount: Number(document.querySelector('[data-r97-c1-guide="true"]')?.dataset.c1StepCount || '0'),
      hasRuntimeShell: !!document.querySelector('.runtime-shell'),
      hasRuntimeEmptyState: !!document.querySelector('.runtime-empty-state'),
      runtimeRecordRows: document.querySelectorAll('[data-runtime-record-row]').length,
      hasTodoWorkbench: !!document.querySelector('[data-system-todo-workbench="true"], [data-platform-todos-page="true"]'),
      hasMessageCenter: !!document.querySelector('[data-system-message-center="true"], [data-platform-messages-page="true"]'),
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
  if (spec.kind === 'platform' && !result.hasPlatformWorkbench) result.blockers.push('platform workbench marker missing');
  if (spec.kind === 'platform-apps') {
    if (!result.hasPlatformAppsPage) result.blockers.push('platform apps marker missing');
    if (result.platformAppsSystemEntryCount !== '0') result.blockers.push(`apps leaked system entry count ${result.platformAppsSystemEntryCount}`);
  }
  if (spec.kind === 'platform-flow' && !result.hasPlatformFlowPage) result.blockers.push('platform flow marker missing');
  if (spec.kind === 'empty-dashboard' && !result.hasR97EmptyDashboard) result.blockers.push('R97 empty dashboard marker missing');
  if (spec.kind === 'c1-guide') {
    if (!result.hasR97C1Guide) result.blockers.push('R97 C1 guide marker missing');
    if (result.r97C1StepCount !== 10) result.blockers.push(`R97 C1 step count ${result.r97C1StepCount}`);
  }
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
  const runtime = trialPack.runtimeDailyUse;
  const workflow = trialPack.workflowTodoMessage;
  const admin = trialPack.admin;
  const cases = [
    { key: 'admin-platform-desktop', role: 'admin', loginName: admin.loginName, password: admin.password, path: '/platform', viewport: desktop, kind: 'platform', selector: '[data-platform-workbench="true"]' },
    { key: 'admin-apps-desktop', role: 'admin', loginName: admin.loginName, password: admin.password, path: '/platform/apps', viewport: desktop, kind: 'platform-apps', selector: '[data-platform-apps-page="true"]' },
    { key: 'admin-flow-mobile', role: 'admin', loginName: admin.loginName, password: admin.password, path: '/platform/flow', viewport: mobile, kind: 'platform-flow', selector: '[data-platform-flow-page="true"]' },
    { key: 'r97-empty-dashboard-desktop', role: 'admin', loginName: admin.loginName, password: admin.password, path: `/systems/${r97SystemId}/dashboard`, viewport: desktop, kind: 'empty-dashboard', selector: '[data-r97-empty-system-dashboard="true"]' },
    { key: 'r97-c1-guide-mobile', role: 'admin', loginName: admin.loginName, password: admin.password, path: `/systems/${r97SystemId}/admin`, viewport: mobile, kind: 'c1-guide', selector: '[data-r97-c1-guide="true"]' },
    { key: 'runtime-normal-desktop', role: 'runtime-normal', loginName: runtime.normalLoginName, password: runtime.password, path: `/systems/${runtime.systemId}/modules`, viewport: desktop, kind: 'runtime' },
    { key: 'runtime-normal-mobile', role: 'runtime-normal', loginName: runtime.normalLoginName, password: runtime.password, path: `/systems/${runtime.systemId}/modules`, viewport: mobile, kind: 'runtime' },
    { key: 'runtime-readonly-desktop', role: 'runtime-readonly', loginName: runtime.readonlyLoginName, password: runtime.password, path: `/systems/${runtime.systemId}/modules`, viewport: desktop, kind: 'runtime' },
    { key: 'workflow-requester-desktop', role: 'workflow-requester', loginName: workflow.requesterLoginName, password: workflow.password, path: `/systems/${workflow.systemId}/modules`, viewport: desktop, kind: 'runtime' },
    { key: 'workflow-approver-todo-desktop', role: 'workflow-approver', loginName: workflow.approverLoginName, password: workflow.password, path: `/systems/${workflow.systemId}/todos`, viewport: desktop, kind: 'todo' },
    { key: 'workflow-approver-messages-mobile', role: 'workflow-approver', loginName: workflow.approverLoginName, password: workflow.password, path: `/systems/${workflow.systemId}/messages`, viewport: mobile, kind: 'message' },
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
      loginBoundary: 'Every smoke route starts from the deployed login page and submitted login form.',
      screenshotBoundary: 'Screenshots prove visible route, copy, and containment only. API/readback evidence proves behavior.',
      results,
    };
    fs.writeFileSync(path.join(outDir, 'final-user-trial-readiness-browser-audit.json'), JSON.stringify(output, null, 2));
    console.log(JSON.stringify(output));
  } finally {
    client.close();
  }
})().catch((error) => {
  const output = { status: 'FAIL', error: error.stack || String(error) };
  fs.mkdirSync(outDir, { recursive: true });
  fs.writeFileSync(path.join(outDir, 'final-user-trial-readiness-browser-audit.json'), JSON.stringify(output, null, 2));
  console.error(error.stack || error.message);
  process.exit(1);
});
