const fs = require('fs');
const http = require('http');

const baseUrl = process.env.R92_BASE_URL;
const port = Number(process.env.R92_CDP_PORT);
const outPath = process.env.R92_BROWSER_OUT;
const systemId = process.env.R92_SYSTEM_ID;
const flowId = process.env.R92_FLOW_ID;
const adminRole = {
  accessToken: process.env.R92_ADMIN_ACCESS_TOKEN,
  refreshToken: process.env.R92_ADMIN_REFRESH_TOKEN || '',
  accountId: process.env.R92_ADMIN_ACCOUNT_ID || 'r92-admin',
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

async function setStorage(client, role) {
  await client.send('Page.navigate', { url: `${baseUrl}/#/` });
  await delay(500);
  await client.send('Runtime.evaluate', {
    expression: `localStorage.setItem('unexamine.accountId', ${JSON.stringify(role.accountId)}); localStorage.setItem('unexamine.accessToken', ${JSON.stringify(role.accessToken)}); localStorage.setItem('unexamine.refreshToken', ${JSON.stringify(role.refreshToken)});`,
    returnByValue: true,
  });
}

async function navigate(client, url, width, height) {
  await client.send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: width <= 640 });
  await client.send('Page.navigate', { url });
  await delay(2600);
}

async function waitFor(client, selector, loops = 70) {
  for (let i = 0; i < loops; i += 1) {
    const found = await evaluate(client, `JSON.stringify({ ok: !!document.querySelector(${JSON.stringify(selector)}), textLength: (document.body.innerText || '').length })`);
    if (found.ok) return true;
    await delay(250);
  }
  return false;
}

async function waitUntil(client, expression, loops = 80) {
  for (let i = 0; i < loops; i += 1) {
    const ok = await evaluate(client, `JSON.stringify({ ok: Boolean((() => { ${expression} })()) })`);
    if (ok.ok) return true;
    await delay(250);
  }
  return false;
}

async function clickSelector(client, selector) {
  return evaluate(client, `JSON.stringify((() => { const node = document.querySelector(${JSON.stringify(selector)}); if (node) node.click(); return { clicked: !!node }; })())`);
}

async function openDesigner(client, width, height, kind) {
  await navigate(client, `${baseUrl}/?r92=${Date.now()}#/systems/${systemId}/admin`, width, height);
  await waitFor(client, '[data-admin-section="flow-management"]');
  await clickSelector(client, '[data-admin-section="flow-management"]');
  await waitFor(client, `[data-r92-configure-flow-button="${flowId}"]`);
  await clickSelector(client, `[data-r92-configure-flow-button="${flowId}"]`);
  await waitFor(client, '[data-r92-workflow-designer="true"]');
  return collect(client, kind);
}

async function exerciseDesigner(client) {
  await clickSelector(client, '[data-r92-advanced-preset-button="true"]');
  await waitUntil(client, "return document.querySelectorAll('[data-r92-canvas-node-type]').length >= 7;", 60);
  await clickSelector(client, '[data-r92-flow-canvas-save-button="true"]');
  await delay(1200);
  await clickSelector(client, '[data-r92-flow-canvas-check-button="true"]');
  await waitFor(client, '[data-r92-publish-impact-summary="true"]', 80);
  await clickSelector(client, '[data-r92-flow-simulate-button="true"]');
  await waitFor(client, '[data-r92-simulation-output="true"]', 80);
}

async function collect(client, kind) {
  return evaluate(client, `JSON.stringify((() => {
    const body = document.body.innerText || '';
    const nodeTypes = Array.from(document.querySelectorAll('[data-r92-canvas-node-type]')).map((node) => node.dataset.r92CanvasNodeType || '');
    const library = document.querySelector('[data-r92-advanced-node-library="true"]');
    const publish = document.querySelector('[data-r92-publish-impact-summary]');
    const simulation = document.querySelector('[data-r92-simulation-output]');
    return {
      kind: ${JSON.stringify(kind)},
      hash: location.hash,
      overflowX: Math.max(0, document.documentElement.scrollWidth - document.documentElement.clientWidth),
      blockerText: /undefined|null|NaN/.test(body),
      designer: !!document.querySelector('[data-r92-workflow-designer="true"]'),
      libraryTypes: library?.dataset.r92AdvancedNodeTypes || '',
      advancedPresetButton: !!document.querySelector('[data-r92-advanced-preset-button="true"]'),
      nodeTypes,
      selectedNodeType: document.querySelector('[data-r92-selected-node-type]')?.dataset.r92SelectedNodeType || '',
      propertyFieldCount: document.querySelectorAll('[data-r92-property-field]').length,
      publishSummary: publish?.dataset.r92PublishImpactSummary || '',
      publishPassed: publish?.dataset.r92PublishPassed || '',
      publishImpactCount: Number(publish?.dataset.r92PublishImpactCount || '0'),
      publishWarningCount: Number(publish?.dataset.r92PublishWarningCount || '0'),
      simulationPassed: simulation?.dataset.r92SimulationPassed || '',
      simulationImpactCount: Number(simulation?.dataset.r92SimulationImpactCount || '0'),
      simulationStepCount: Number(simulation?.dataset.r92SimulationStepCount || '0'),
      textLength: body.length,
    };
  })())`);
}

(async () => {
  const target = await newTarget();
  const client = await connect(target.webSocketDebuggerUrl);
  try {
    await client.send('Page.enable');
    await client.send('Runtime.enable');
    const results = [];
    await setStorage(client, adminRole);
    await openDesigner(client, 1366, 900, 'admin-desktop-before');
    await exerciseDesigner(client);
    results.push(await collect(client, 'admin-desktop-after'));
    await openDesigner(client, 390, 720, 'admin-mobile');
    results.push(await collect(client, 'admin-mobile'));
    fs.writeFileSync(outPath, JSON.stringify({ results }, null, 2));
  } finally {
    client.close();
  }
})().catch((error) => {
  fs.writeFileSync(outPath, JSON.stringify({ error: error.stack || String(error) }, null, 2));
  process.exit(1);
});
