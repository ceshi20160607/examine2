const fs = require('fs');
const path = require('path');

const baseUrl = process.env.R97_BASE_URL;
const port = Number(process.env.R97_CDP_PORT);
const outDir = process.env.R97_EVIDENCE_DIR;
const systemId = process.env.R97_SYSTEM_ID;
const adminRole = {
  accessToken: process.env.R97_ADMIN_ACCESS_TOKEN,
  refreshToken: process.env.R97_ADMIN_REFRESH_TOKEN || '',
  accountId: process.env.R97_ADMIN_ACCOUNT_ID || 'admin',
};

if (!baseUrl || !port || !outDir || !systemId || !adminRole.accessToken) {
  throw new Error('R97_BASE_URL, R97_CDP_PORT, R97_EVIDENCE_DIR, R97_SYSTEM_ID, and R97_ADMIN_ACCESS_TOKEN are required.');
}

const requiredC1Steps = [
  'system-info',
  'organization-members',
  'roles-permissions',
  'module-groups-modules',
  'fields-dictionaries',
  'page-list-detail-actions',
  'workflow-messages',
  'work-configuration',
  'integration-openapi-ai',
  'publish-runtime-preview',
];

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

async function setStorage(client) {
  await client.send('Page.navigate', { url: `${baseUrl}/#/` });
  await delay(500);
  await client.send('Runtime.evaluate', {
    expression: `
      localStorage.clear();
      sessionStorage.clear();
      localStorage.setItem('unexamine.accountId', ${JSON.stringify(adminRole.accountId)});
      localStorage.setItem('unexamine.accessToken', ${JSON.stringify(adminRole.accessToken)});
      localStorage.setItem('unexamine.refreshToken', ${JSON.stringify(adminRole.refreshToken)});
    `,
    returnByValue: true,
  });
}

async function navigate(client, route, viewport) {
  await setViewport(client, viewport);
  await client.send('Page.navigate', { url: `${baseUrl}/?r97=${Date.now()}#${route}` });
  await waitUntil(client, 'return !!document.body && document.readyState !== "loading";', 30000);
  await waitUntil(client, 'return !!document.body && (document.body.innerText || "").length > 20;', 30000);
  await delay(900);
}

async function captureScreenshot(client, name) {
  const shot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
  const data = (shot.result && shot.result.data) || shot.data;
  fs.writeFileSync(path.join(outDir, `${name}.png`), Buffer.from(data, 'base64'));
}

async function inspectEmptyDashboard(client, viewport) {
  await navigate(client, `/systems/${systemId}/dashboard`, viewport);
  await waitUntil(client, 'return !!document.querySelector("[data-r97-empty-system-dashboard=\\"true\\"]");', 35000);
  const result = await evaluate(client, `JSON.stringify((() => {
    const doc = document.documentElement;
    const body = document.body;
    const text = body ? body.innerText || '' : '';
    const panel = document.querySelector('[data-r97-empty-system-dashboard="true"]');
    const steps = Array.from(document.querySelectorAll('[data-r97-empty-dashboard-step]'));
    const controls = Array.from(document.querySelectorAll('main button, main [role="button"], main a, main input, main select, main textarea'));
    return {
      kind: 'empty-dashboard',
      viewport: ${JSON.stringify(viewport.name)},
      route: location.hash,
      textLength: text.length,
      hasHeading: !!document.querySelector('[data-r97-empty-dashboard-heading="true"]'),
      hasPanel: !!panel,
      primarySurface: panel?.dataset.emptyDashboardPrimarySurface || '',
      actionHubSuppressed: panel?.dataset.dashboardActionHubSuppressed || '',
      runtimePanelsSuppressed: panel?.dataset.dashboardRuntimePanelsSuppressed || '',
      stepCountMarker: Number(document.querySelector('[data-r97-empty-dashboard-steps]')?.dataset.r97EmptyDashboardSteps || '0'),
      stepCount: steps.length,
      primaryActionCount: document.querySelectorAll('[data-r97-empty-dashboard-primary-action="system-admin-first-use"]').length,
      secondaryActionCount: document.querySelectorAll('[data-r97-empty-dashboard-secondary-action]').length,
      dailyHubCount: document.querySelectorAll('[data-system-dashboard-daily-hub="true"]').length,
      runtimePanelCount: document.querySelectorAll('[data-system-dashboard-runtime-efficiency-r88="true"], .dashboard-runtime-efficiency').length,
      modulePreviewCount: document.querySelectorAll('[data-system-dashboard-module-preview="true"]').length,
      operationPanelCount: document.querySelectorAll('[data-home-operations-panel="true"]').length,
      adminCopyVisible: text.includes('系统初始化') && text.includes('先完成初始化'),
      mojibake: /�|鍚|绯|妯|涓|鏉|寰|骞|浠/.test(text),
      overflowX: Math.max(0, Math.ceil(Math.max(doc.scrollWidth, body.scrollWidth) - window.innerWidth)),
      controlOverflowCount: controls.filter((el) => Math.ceil(el.scrollWidth - el.clientWidth) > 2).length,
    };
  })())`);
  result.blockers = [];
  if (!result.hasHeading) result.blockers.push('missing empty dashboard heading');
  if (!result.hasPanel) result.blockers.push('missing empty initialization panel');
  if (result.primarySurface !== 'initialization') result.blockers.push(`primary surface ${result.primarySurface}`);
  if (result.actionHubSuppressed !== 'true') result.blockers.push('action hub suppression marker missing');
  if (result.runtimePanelsSuppressed !== 'true') result.blockers.push('runtime suppression marker missing');
  if (result.stepCountMarker !== 5 || result.stepCount !== 5) result.blockers.push(`empty guidance steps ${result.stepCountMarker}/${result.stepCount}`);
  if (result.primaryActionCount !== 1) result.blockers.push(`primary action count ${result.primaryActionCount}`);
  if (result.secondaryActionCount < 2) result.blockers.push(`secondary actions too few ${result.secondaryActionCount}`);
  if (result.dailyHubCount !== 0) result.blockers.push(`daily action hub leaked ${result.dailyHubCount}`);
  if (result.runtimePanelCount !== 0) result.blockers.push(`runtime efficiency panel leaked ${result.runtimePanelCount}`);
  if (result.modulePreviewCount !== 0) result.blockers.push(`module preview leaked ${result.modulePreviewCount}`);
  if (!result.adminCopyVisible) result.blockers.push('initialization copy is not visible');
  if (result.mojibake) result.blockers.push('visible mojibake detected');
  if (result.overflowX > 2) result.blockers.push(`horizontal overflow ${result.overflowX}`);
  if (result.controlOverflowCount > 0) result.blockers.push(`control overflow ${result.controlOverflowCount}`);
  await captureScreenshot(client, `empty-dashboard-${viewport.name}`);
  return result;
}

async function inspectC1Guide(client, viewport) {
  await navigate(client, `/systems/${systemId}/admin`, viewport);
  await waitUntil(client, 'return !!document.querySelector("[data-r97-c1-guide=\\"true\\"]");', 35000);
  const result = await evaluate(client, `JSON.stringify((() => {
    const doc = document.documentElement;
    const body = document.body;
    const text = body ? body.innerText || '' : '';
    const guide = document.querySelector('[data-r97-c1-guide="true"]');
    const steps = Array.from(document.querySelectorAll('[data-r97-c1-step]')).map((node) => ({
      key: node.dataset.r97C1Step || '',
      status: node.dataset.c1StepStatus || '',
      target: node.dataset.c1StepTarget || '',
      blocked: node.dataset.c1StepBlocked || '',
    }));
    const actions = Array.from(document.querySelectorAll('[data-r97-c1-step-action]')).map((node) => node.dataset.r97C1StepAction || '');
    const controls = Array.from(document.querySelectorAll('main button, main [role="button"], main a, main input, main select, main textarea'));
    return {
      kind: 'c1-guide',
      viewport: ${JSON.stringify(viewport.name)},
      route: location.hash,
      textLength: text.length,
      hasGuide: !!guide,
      stepCountMarker: Number(guide?.dataset.c1StepCount || '0'),
      blockingCount: Number(guide?.dataset.c1BlockingCount || '-1'),
      startedCount: Number(guide?.dataset.c1StartedCount || '-1'),
      progress: document.querySelector('[data-r97-c1-progress]')?.dataset.r97C1Progress || '',
      steps,
      actions,
      stepKeys: steps.map((step) => step.key),
      readbackCount: document.querySelectorAll('.step-readback').length,
      metricCount: document.querySelectorAll('.metric').length,
      adminCopyVisible: text.includes('系统初始化清单') && text.includes('按 C1 首用顺序'),
      mojibake: /�|鍚|绯|妯|涓|鏉|寰|骞|浠/.test(text),
      overflowX: Math.max(0, Math.ceil(Math.max(doc.scrollWidth, body.scrollWidth) - window.innerWidth)),
      controlOverflowCount: controls.filter((el) => Math.ceil(el.scrollWidth - el.clientWidth) > 2).length,
    };
  })())`);
  const keys = new Set(result.stepKeys);
  const actions = new Set(result.actions);
  result.missingSteps = requiredC1Steps.filter((step) => !keys.has(step));
  result.missingActions = requiredC1Steps.filter((step) => !actions.has(step));
  result.blockers = [];
  if (!result.hasGuide) result.blockers.push('missing C1 guide marker');
  if (result.stepCountMarker !== 10 || result.steps.length !== 10) result.blockers.push(`C1 steps ${result.stepCountMarker}/${result.steps.length}`);
  if (result.missingSteps.length) result.blockers.push(`missing steps ${result.missingSteps.join(',')}`);
  if (result.missingActions.length) result.blockers.push(`missing actions ${result.missingActions.join(',')}`);
  if (result.readbackCount < 10) result.blockers.push(`readback rows too few ${result.readbackCount}`);
  if (!result.progress.includes('/10')) result.blockers.push(`progress marker ${result.progress}`);
  if (!result.adminCopyVisible) result.blockers.push('C1 guide copy is not visible');
  if (result.mojibake) result.blockers.push('visible mojibake detected');
  if (result.overflowX > 2) result.blockers.push(`horizontal overflow ${result.overflowX}`);
  if (result.controlOverflowCount > 0) result.blockers.push(`control overflow ${result.controlOverflowCount}`);
  await captureScreenshot(client, `c1-guide-${viewport.name}`);
  return result;
}

(async () => {
  fs.mkdirSync(outDir, { recursive: true });
  const desktop = { name: 'desktop', width: 1440, height: 920, mobile: false };
  const mobile = { name: 'mobile', width: 390, height: 760, mobile: true };
  const target = await newTarget();
  const client = await connect(target.webSocketDebuggerUrl);
  try {
    await client.send('Page.enable');
    await client.send('Runtime.enable');
    await setStorage(client);
    const results = [];
    for (const viewport of [desktop, mobile]) {
      results.push(await inspectEmptyDashboard(client, viewport));
    }
    for (const viewport of [desktop, mobile]) {
      results.push(await inspectC1Guide(client, viewport));
    }
    const blockerResults = results.filter((item) => item.blockers.length > 0);
    const output = {
      status: blockerResults.length ? 'FAIL' : 'PASS',
      generatedAt: new Date().toISOString(),
      baseUrl,
      systemId,
      resultCount: results.length,
      blockerResultCount: blockerResults.length,
      emptyDashboardPassed: results.filter((item) => item.kind === 'empty-dashboard').every((item) => item.blockers.length === 0),
      c1GuidePassed: results.filter((item) => item.kind === 'c1-guide').every((item) => item.blockers.length === 0),
      maxOverflowX: Math.max(...results.map((item) => item.overflowX || 0)),
      maxControlOverflowCount: Math.max(...results.map((item) => item.controlOverflowCount || 0)),
      requiredC1Steps,
      results,
    };
    fs.writeFileSync(path.join(outDir, 'c1-fresh-system-initialization-path-browser-audit.json'), JSON.stringify(output, null, 2));
    console.log(JSON.stringify(output));
  } finally {
    client.close();
  }
})().catch((error) => {
  const output = { status: 'FAIL', error: error.stack || String(error) };
  fs.mkdirSync(outDir, { recursive: true });
  fs.writeFileSync(path.join(outDir, 'c1-fresh-system-initialization-path-browser-audit.json'), JSON.stringify(output, null, 2));
  console.error(error.stack || error.message);
  process.exit(1);
});
