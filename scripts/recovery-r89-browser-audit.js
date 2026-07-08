const fs = require('fs');
const http = require('http');
const baseUrl = process.env.R89_BASE_URL;
const port = Number(process.env.R89_CDP_PORT);
const outPath = process.env.R89_BROWSER_OUT;
const systemId = process.env.R89_SYSTEM_ID;
const moduleId = process.env.R89_MODULE_ID;
const importFileId = process.env.R89_IMPORT_FILE_ID;
const role = {
  accessToken: process.env.R89_ACCESS_TOKEN,
  refreshToken: process.env.R89_REFRESH_TOKEN || '',
  accountId: process.env.R89_ACCOUNT_ID || 'r89',
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
async function setStorage(client) {
  await client.send('Page.navigate', { url: `${baseUrl}/#/` });
  await delay(500);
  await client.send('Runtime.evaluate', {
    expression: `localStorage.setItem('unexamine.accountId', ${JSON.stringify(role.accountId)}); localStorage.setItem('unexamine.accessToken', ${JSON.stringify(role.accessToken)}); localStorage.setItem('unexamine.refreshToken', ${JSON.stringify(role.refreshToken)});`,
    returnByValue: true,
  });
  await client.send('Page.navigate', { url: `${baseUrl}/?r89=${Date.now()}#/platform` });
  await delay(2500);
}
async function navigate(client, width, height) {
  await client.send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: width <= 640 });
  await client.send('Page.navigate', { url: `${baseUrl}/#/systems/${systemId}/modules?moduleId=${encodeURIComponent(moduleId)}&r89=${Date.now()}` });
  await delay(2200);
}
async function waitFor(client, selector, loops = 50) {
  for (let i = 0; i < loops; i += 1) {
    const found = await evaluate(client, `JSON.stringify({ ok: !!document.querySelector(${JSON.stringify(selector)}), textLength: (document.body.innerText || '').length })`);
    if (found.ok) return true;
    await delay(250);
  }
  return false;
}
async function clickSelector(client, selector) {
  return evaluate(client, `JSON.stringify((() => { const node = document.querySelector(${JSON.stringify(selector)}); if (node) node.click(); return { clicked: !!node }; })())`);
}
async function clickButtonText(client, text) {
  return evaluate(client, `JSON.stringify((() => { const node = Array.from(document.querySelectorAll('button')).find((button) => (button.textContent || '').includes(${JSON.stringify(text)})); if (node) node.click(); return { clicked: !!node, disabled: !!node?.disabled }; })())`);
}
async function collect(client, kind) {
  return evaluate(client, `JSON.stringify((() => {
    const overflowX = Math.max(0, document.documentElement.scrollWidth - document.documentElement.clientWidth);
    const body = document.body.innerText || '';
    const detail = document.querySelector('[data-runtime-recovery-r89][data-runtime-recovery-detail-state]');
    const exportPanel = document.querySelector('[data-runtime-export-panel="true"]');
    const importPanel = document.querySelector('[data-runtime-import-panel="true"]');
    return {
      kind: ${JSON.stringify(kind)},
      hash: location.hash,
      overflowX,
      blockerText: /閸旂姾娴囨径杈|undefined|null|NaN/.test(body),
      panel: !!document.querySelector('.runtime-recovery-r89-panel'),
      importPanel: !!importPanel,
      exportPanel: !!exportPanel,
      recoveryDetail: !!detail,
      recoveryState: detail?.dataset.runtimeRecoveryDetailState || '',
      taskId: detail?.dataset.runtimeAsyncTask || '',
      taskStatus: detail?.dataset.runtimeTaskStatus || '',
      resultFileId: detail?.dataset.runtimeTaskResultFileId || '',
      errorFileId: detail?.dataset.runtimeTaskErrorFileId || '',
      rollbackSupported: detail?.dataset.runtimeTaskRollbackSupported || '',
      rollbackReason: detail?.dataset.runtimeRollbackUnsupportedReason || '',
      selectedExportState: exportPanel?.dataset.runtimeSelectedExportState || document.querySelector('[data-runtime-selected-export-state]')?.dataset.runtimeSelectedExportState || '',
      selectedEmptyReason: !!document.querySelector('[data-runtime-selected-export-empty-reason="true"]'),
      resultDownloads: document.querySelectorAll('[data-runtime-task-result-download]').length,
      errorDownloads: document.querySelectorAll('[data-runtime-task-error-download]').length,
      resultPreviews: document.querySelectorAll('[data-runtime-task-result-preview]').length,
      errorPreviews: document.querySelectorAll('[data-runtime-task-error-preview]').length,
      fileActions: document.querySelectorAll('[data-runtime-task-file-action-r89]').length,
      precheckId: importPanel?.dataset.runtimeImportPrecheckId || '',
      precheckPassed: importPanel?.dataset.runtimeImportPrecheckPassed || '',
      precheckIssues: document.querySelector('[data-runtime-import-precheck-issue-summary]')?.dataset.runtimeImportPrecheckIssueSummary || '',
      textSampleLength: body.length,
    };
  })())`);
}
(async () => {
  const target = await newTarget();
  const client = await connect(target.webSocketDebuggerUrl);
  try {
    await client.send('Page.enable');
    await client.send('Runtime.enable');
    await setStorage(client);
    const results = [];

    await navigate(client, 1366, 900);
    await waitFor(client, '[data-runtime-export-open-r89="true"]');
    await clickSelector(client, '[data-runtime-export-open-r89="true"]');
    await waitFor(client, '[data-runtime-export-panel="true"]');
    results.push(await collect(client, 'export-empty-desktop'));
    await clickButtonText(client, '导出当前筛选');
    await delay(3000);
    await waitFor(client, '[data-runtime-recovery-r89="export"][data-runtime-recovery-detail-state="ready"]', 80);
    await delay(500);
    results.push(await collect(client, 'export-result-desktop'));

    await navigate(client, 1366, 900);
    await waitFor(client, '[data-runtime-import-open-r89="true"]');
    await clickSelector(client, '[data-runtime-import-open-r89="true"]');
    await waitFor(client, '[data-runtime-import-panel="true"]');
    await evaluate(client, `JSON.stringify((() => { const input = document.querySelector('input[aria-label="已有上传文件 fileId"]'); if (input) { input.value = ${JSON.stringify(importFileId)}; input.dispatchEvent(new Event('input', { bubbles: true })); } return { filled: !!input }; })())`);
    await clickSelector(client, '[data-runtime-import-precheck-action="true"]');
    await waitFor(client, '[data-runtime-import-panel="true"][data-runtime-import-precheck-passed="true"]', 80);
    await delay(700);
    results.push(await collect(client, 'import-precheck-desktop'));
    await clickSelector(client, '[data-runtime-import-confirm-action="true"]:not([disabled])');
    await waitFor(client, '[data-runtime-recovery-r89="import"][data-runtime-async-task*="confirm"]', 80);
    await delay(700);
    results.push(await collect(client, 'import-confirm-desktop'));

    await navigate(client, 390, 720);
    await waitFor(client, '[data-runtime-export-open-r89="true"]');
    await clickSelector(client, '[data-runtime-export-open-r89="true"]');
    await waitFor(client, '[data-runtime-export-panel="true"]');
    results.push(await collect(client, 'export-empty-mobile'));

    fs.writeFileSync(outPath, JSON.stringify({ results }, null, 2));
  } finally {
    client.close();
  }
})().catch((error) => { fs.writeFileSync(outPath, JSON.stringify({ error: error.stack || String(error) }, null, 2)); process.exit(1); });