const fs = require('fs');
const http = require('http');
const path = require('path');

const baseUrl = process.env.R86_BASE_URL;
const outDir = process.env.R86_EVIDENCE_DIR;
const port = process.env.R86_CDP_PORT;
const systemId = process.env.R86_SYSTEM_ID;
const moduleId = process.env.R86_MODULE_ID;
const moduleName = process.env.R86_MODULE_NAME;
const pageName = process.env.R86_PAGE_NAME;

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function cdpJson(pathname, options = {}) {
  let lastError;
  for (let attempt = 1; attempt <= 20; attempt += 1) {
    try {
      const method = options.method || 'GET';
      return await new Promise((resolve, reject) => {
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
    } catch (error) {
      lastError = error;
      await delay(250);
    }
  }
  throw new Error(`CDP request failed for ${pathname}: ${lastError && lastError.stack ? lastError.stack : String(lastError)}`);
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
  const result = await client.send('Runtime.evaluate', {
    expression,
    returnByValue: true,
    awaitPromise: true,
  });
  if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
  const value = result.result.value;
  return typeof value === 'string' ? JSON.parse(value) : value;
}

async function navigate(client, url) {
  await client.send('Page.navigate', { url });
  await delay(1600);
}

async function setViewport(client, width, height) {
  await client.send('Emulation.setDeviceMetricsOverride', {
    width,
    height,
    deviceScaleFactor: 1,
    mobile: width <= 640,
  });
  await client.send('Emulation.setVisibleSize', { width, height });
}

async function setStorage(client, role) {
  await navigate(client, `${baseUrl}/#/`);
  await client.send('Runtime.evaluate', {
    expression: `
      localStorage.setItem('unexamine.accountId', ${JSON.stringify(role.accountId)});
      localStorage.setItem('unexamine.accessToken', ${JSON.stringify(role.accessToken)});
      localStorage.setItem('unexamine.refreshToken', ${JSON.stringify(role.refreshToken || '')});
    `,
    returnByValue: true,
  });
  await delay(300);
}

async function clickByText(client, text) {
  return evaluate(client, `
    JSON.stringify((() => {
      const candidates = Array.from(document.querySelectorAll('button,a'));
      const target = candidates.find((el) => (el.innerText || '').trim() === ${JSON.stringify(text)});
      if (!target) return { clicked: false };
      if (target.disabled) return { clicked: false, disabled: true, text: target.innerText, title: target.title || '', ariaLabel: target.getAttribute('aria-label') || '' };
      target.click();
      return { clicked: true, disabled: false, text: target.innerText, title: target.title || '', ariaLabel: target.getAttribute('aria-label') || '' };
    })())
  `);
}

async function waitFor(client, expression, timeoutMs = 20000) {
  const deadline = Date.now() + timeoutMs;
  let last = null;
  while (Date.now() < deadline) {
    last = await evaluate(client, expression);
    if (last && last.ok) return last;
    await delay(300);
  }
  const pageState = await evaluate(client, `JSON.stringify({ hash: location.hash, text: (document.body.innerText || '').slice(0, 1200), create: !!document.querySelector('[data-runtime-create-record="true"]'), shell: !!document.querySelector('.runtime-shell') })`);
  throw new Error(`Condition timed out: ${expression}; last=${JSON.stringify(last)}; pageState=${JSON.stringify(pageState)}`);
}

async function clickSelector(client, selector) {
  return evaluate(client, `
    JSON.stringify((() => {
      const target = document.querySelector(${JSON.stringify(selector)});
      if (!target) return { clicked: false };
      if (target.disabled) return { clicked: false, disabled: true, text: target.innerText || '', title: target.title || '', ariaLabel: target.getAttribute('aria-label') || '' };
      target.click();
      return { clicked: true, disabled: false, text: target.innerText || '', title: target.title || '', ariaLabel: target.getAttribute('aria-label') || '' };
    })())
  `);
}

async function setInputValue(client, fieldCode, value) {
  return evaluate(client, `
    JSON.stringify((() => {
      const input = document.querySelector('input[data-field-code="' + ${JSON.stringify(fieldCode)} + '"]');
      if (!input) return { ok: false };
      input.value = ${JSON.stringify(value)};
      input.dispatchEvent(new Event('input', { bubbles: true }));
      input.dispatchEvent(new Event('change', { bubbles: true }));
      return { ok: true, fieldCode: input.dataset.fieldCode, value: input.value };
    })())
  `);
}

async function waitForText(client, text, timeoutMs = 8000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const found = await evaluate(client, `
      JSON.stringify({ found: (document.body.innerText || '').includes(${JSON.stringify(text)}), text: (document.body.innerText || '').slice(0, 800) })
    `);
    if (found.found) return found;
    await delay(400);
  }
  return evaluate(client, `JSON.stringify({ found: false, text: (document.body.innerText || '').slice(0, 1200) })`);
}

async function screenshot(client, fileName) {
  const image = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  fs.writeFileSync(path.join(outDir, fileName), Buffer.from(image.data, 'base64'));
}

async function inspect(client, key) {
  return evaluate(client, `
    JSON.stringify((() => {
      const text = document.body.innerText || '';
      const doc = document.documentElement;
      const body = document.body;
      const overflowX = Math.max(0, Math.ceil(Math.max(doc.scrollWidth, body.scrollWidth) - window.innerWidth));
      const fields = Array.from(document.querySelectorAll('.edit-panel input[data-field-code]')).map((input) => ({
        fieldCode: input.dataset.fieldCode,
        disabled: input.disabled,
        readonly: input.dataset.readonly === 'true',
        value: input.value || '',
      }));
      const modulePanels = Array.from(document.querySelectorAll('.module-work-panel,.module-page-designer-slot,.page-designer-panel')).map((el) => (el.innerText || '').slice(0, 400));
      const assetScripts = Array.from(document.scripts).map((script) => script.src).filter((src) => src.includes('/assets/'));
      const componentRows = Array.from(document.querySelectorAll('[data-page-component-row="true"]')).map((row) => ({
        code: row.dataset.componentCode,
        visible: row.dataset.componentVisible,
        draggable: row.dataset.componentDraggable,
        width: row.dataset.componentWidth,
        placement: row.dataset.componentPlacement,
        text: (row.innerText || '').slice(0, 240),
      }));
      const schemaComponents = Array.from(document.querySelectorAll('[data-schema-component]')).map((item) => ({
        code: item.dataset.schemaComponent,
        type: item.dataset.schemaComponentType,
        width: item.dataset.schemaComponentWidth,
        placement: item.dataset.schemaComponentPlacement,
        text: (item.innerText || '').slice(0, 160),
      }));
      const mobilePreviewComponents = Array.from(document.querySelectorAll('[data-mobile-preview-component]')).map((item) => ({
        code: item.dataset.mobilePreviewComponent,
        width: item.dataset.mobilePreviewComponentWidth,
        placement: item.dataset.mobilePreviewComponentPlacement,
      }));
      const componentButtons = Array.from(document.querySelectorAll('[data-page-component-workbench="true"] button'));
      const createButton = Array.from(document.querySelectorAll('button')).find((button) => (button.innerText || '').trim() === '\u65b0\u5efa\u8bb0\u5f55');
      const focusResults = componentButtons.map((button) => {
        button.focus();
        return {
          text: (button.innerText || '').trim(),
          ariaLabel: button.getAttribute('aria-label') || '',
          focused: document.activeElement === button,
          disabled: button.disabled,
        };
      });
      return {
        key: ${JSON.stringify(key)},
        hash: location.hash,
        overflowX,
        textPreview: text.slice(0, 1200),
        assetScripts,
        hasHomeOverviewMarker: !!document.querySelector('[data-home-overview="true"]'),
        hasHomeOperationsMarker: !!document.querySelector('[data-home-operations="true"]'),
        hasHomeConfigMarker: !!document.querySelector('[data-home-config-panel="true"]'),
        hasModulePageDesignerMarker: !!document.querySelector('[data-module-page-designer="true"]'),
        hasPageComponentWorkbench: !!document.querySelector('[data-page-component-workbench="true"]'),
        hasPageComponentDragCanvas: !!document.querySelector('[data-page-component-drag-canvas="true"]'),
        hasSchemaComponentStrip: !!document.querySelector('[data-page-schema-component-strip="true"]'),
        hasPageMobilePreview: !!document.querySelector('[data-page-mobile-preview="true"]'),
        componentRows,
        draggableComponentRowCount: componentRows.filter((row) => row.draggable === 'true').length,
        componentWidthValues: componentRows.map((row) => row.width).filter(Boolean),
        componentPlacementValues: componentRows.map((row) => row.placement).filter(Boolean),
        schemaComponents,
        schemaComponentCount: schemaComponents.length,
        schemaComponentWidthValues: schemaComponents.map((item) => item.width).filter(Boolean),
        schemaComponentPlacementValues: schemaComponents.map((item) => item.placement).filter(Boolean),
        mobilePreviewComponents,
        mobilePreviewComponentCount: mobilePreviewComponents.length,
        componentRowCount: componentRows.length,
        componentActionButtonCount: componentButtons.length,
        focusableComponentControlCount: focusResults.filter((item) => item.focused && !item.disabled && (item.ariaLabel || item.text)).length,
        focusResults,
        createButtonState: createButton ? {
          exists: true,
          disabled: createButton.disabled,
          title: createButton.title || '',
          ariaLabel: createButton.getAttribute('aria-label') || '',
          text: (createButton.innerText || '').trim(),
        } : {
          exists: false,
          disabled: false,
          title: '',
          ariaLabel: '',
          text: '',
        },
        hasRuntimeSchemaFormMarker: !!document.querySelector('[data-runtime-schema-form="true"]'),
        hasRuntimeValidationErrors: !!document.querySelector('[data-runtime-validation-errors="true"]'),
        invalidFieldCodes: Array.from(document.querySelectorAll('[data-field-error]')).map((el) => el.dataset.fieldError).filter(Boolean),
        hasEnglishReadonlyReason: text.includes('Current role can read this field but cannot write it.'),
        hasModulePageDesigner: text.includes('\u6a21\u5757\u9875\u9762\u8bbe\u8ba1\u5668'),
        hasHomeAndPageDesignerTitle: text.includes('\u9996\u9875\u4e0e\u9875\u9762\u8bbe\u8ba1'),
        hasHomeConfigTitle: text.includes('\u9996\u9875\u914d\u7f6e'),
        hasModuleName: text.includes(${JSON.stringify(moduleName)}),
        hasPageName: text.includes(${JSON.stringify(pageName)}),
        hasSchemaReadSuccess: text.includes('Schema \u8bfb\u53d6\u6210\u529f'),
        hasSchemaPreviewPanel: !!document.querySelector('.schema-preview'),
        hasSecretText: text.includes('Secret Note') || text.includes('secretNote') || text.includes('Hidden Secret'),
        fields,
        modulePanels,
      };
    })())
  `);
}

async function run() {
  fs.mkdirSync(outDir, { recursive: true });
  const target = await newTarget();
  const client = await connect(target.webSocketDebuggerUrl);
  await client.send('Page.enable');
  await client.send('Runtime.enable');

  const roles = {
    admin: {
      accessToken: process.env.R86_ADMIN_TOKEN,
      refreshToken: process.env.R86_ADMIN_REFRESH,
      accountId: process.env.R86_ADMIN_ACCOUNT_ID || 'admin',
    },
    normal: {
      accessToken: process.env.R86_NORMAL_TOKEN,
      refreshToken: process.env.R86_NORMAL_REFRESH,
      accountId: process.env.R86_NORMAL_ACCOUNT_ID || 'normal',
    },
  };

  const result = { results: [] };

  await setViewport(client, 1280, 720);
  await setStorage(client, roles.admin);
  await navigate(client, `${baseUrl}/?r86=${Date.now()}#/systems/${systemId}/dashboard`);
  await waitForText(client, '\u4eca\u65e5\u6982\u89c8');
  await screenshot(client, 'desktop-admin-system-home.png');
  result.results.push(await inspect(client, 'desktop-admin-system-home'));

  await navigate(client, `${baseUrl}/?r86=${Date.now()}#/systems/${systemId}/modules`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('.runtime-shell') && !!document.querySelector('[data-runtime-create-record="true"]') && (document.body.innerText || '').includes(${JSON.stringify(moduleName)}) })`);
  let clickedAdminCreate = await clickSelector(client, '[data-runtime-create-record="true"]');
  if (!clickedAdminCreate.clicked) clickedAdminCreate = await clickByText(client, '\u65b0\u5efa\u8bb0\u5f55');
  if (!clickedAdminCreate.clicked) throw new Error('Could not click admin runtime create button.');
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('[data-runtime-edit-panel="create"]') || (document.body.innerText || '').toLowerCase().includes('record form') })`);
  let clickedEmptySave = await clickSelector(client, '[data-runtime-save-record="create"]');
  if (!clickedEmptySave.clicked) clickedEmptySave = await clickByText(client, '\u4fdd\u5b58\u8bb0\u5f55');
  if (!clickedEmptySave.clicked) throw new Error('Could not click admin runtime save button.');
  await waitForText(client, '\u8868\u5355\u6821\u9a8c\u672a\u901a\u8fc7');
  await screenshot(client, 'desktop-admin-runtime-validation.png');
  result.results.push(await inspect(client, 'desktop-admin-runtime-validation'));
  const browserTitle = `R86 Browser Created ${Date.now()}`;
  const filledTitle = await setInputValue(client, 'title', browserTitle);
  const filledStatus = await setInputValue(client, 'status', 'DRAFT');
  const filledRequired = await setInputValue(client, 'publicName', browserTitle);
  if (!filledTitle.ok || !filledStatus.ok || !filledRequired.ok) throw new Error('Could not fill required runtime fields.');
  let clickedFilledSave = await clickSelector(client, '[data-runtime-save-record="create"]');
  if (!clickedFilledSave.clicked) clickedFilledSave = await clickByText(client, '\u4fdd\u5b58\u8bb0\u5f55');
  if (!clickedFilledSave.clicked) throw new Error('Could not click admin runtime save button after filling required field.');
  await waitForText(client, '\u8bb0\u5f55\u5df2\u4fdd\u5b58');
  await screenshot(client, 'desktop-admin-runtime-validation-saved.png');
  result.results.push(await inspect(client, 'desktop-admin-runtime-validation-saved'));

  await navigate(client, `${baseUrl}/?r86=${Date.now()}#/systems/${systemId}/admin`);
  await waitForText(client, '\u7cfb\u7edf\u521d\u59cb\u5316\u6e05\u5355');
  await delay(600);
  const clickedModule = await clickByText(client, '\u6a21\u5757\u7ba1\u7406');
  if (!clickedModule.clicked) throw new Error('Could not click module management sidebar.');
  const clickedPageTab = await clickByText(client, '\u9875\u9762');
  if (!clickedPageTab.clicked) throw new Error('Could not click page designer tab from module management.');
  const moduleDesignerVisible = await waitForText(client, '\u6a21\u5757\u9875\u9762\u8bbe\u8ba1\u5668');
  if (!moduleDesignerVisible.found) throw new Error(`Module page designer did not appear after module management click: ${moduleDesignerVisible.text}`);
  const componentWorkbenchVisible = await waitForText(client, '\u7ec4\u4ef6\u5de5\u4f5c\u53f0');
  if (!componentWorkbenchVisible.found) throw new Error(`Component workbench did not appear: ${componentWorkbenchVisible.text}`);
  const clickedSchema = await clickByText(client, 'Schema \u9884\u89c8');
  if (!clickedSchema.clicked) throw new Error('Could not click Schema preview from module management.');
  const schemaVisible = await waitForText(client, 'Schema \u8bfb\u53d6\u6210\u529f');
  if (!schemaVisible.found) throw new Error(`Schema preview result did not appear in module management: ${schemaVisible.text}`);
  const copiedComponent = await clickByText(client, '\u590d\u5236\u7ec4\u4ef6');
  if (!copiedComponent.clicked) throw new Error('Could not click copy component button.');
  await waitForText(client, '\u5df2\u590d\u5236\u7ec4\u4ef6');
  const movedComponent = await clickByText(client, '\u4e0b\u79fb');
  if (!movedComponent.clicked) throw new Error('Could not click move-down component button.');
  await waitForText(client, '\u5df2\u4e0b\u79fb\u7ec4\u4ef6');
  const hiddenComponent = await clickByText(client, '\u9690\u85cf');
  if (!hiddenComponent.clicked) throw new Error('Could not click hide component button.');
  await waitForText(client, '\u5df2\u9690\u85cf\u7ec4\u4ef6');
  const savedComponentLayout = await clickByText(client, '\u4fdd\u5b58\u7ec4\u4ef6\u5e03\u5c40');
  if (!savedComponentLayout.clicked) throw new Error('Could not click save component layout button.');
  await waitForText(client, '\u7ec4\u4ef6\u5e03\u5c40\u5df2\u4fdd\u5b58');
  await screenshot(client, 'desktop-admin-module-page-designer.png');
  result.results.push(await inspect(client, 'desktop-admin-module-page-designer'));

  const clickedDashboard = await clickByText(client, '\u4eea\u8868\u76d8\u7ba1\u7406');
  if (!clickedDashboard.clicked) throw new Error('Could not click dashboard config sidebar.');
  await waitForText(client, '\u9996\u9875\u914d\u7f6e');
  await screenshot(client, 'desktop-admin-dashboard-config.png');
  result.results.push(await inspect(client, 'desktop-admin-dashboard-config'));

  await setViewport(client, 390, 720);
  await navigate(client, `${baseUrl}/?r86=${Date.now()}#/systems/${systemId}/admin`);
  await waitForText(client, '\u7cfb\u7edf\u521d\u59cb\u5316\u6e05\u5355');
  await delay(600);
  const clickedMobileModule = await clickByText(client, '\u6a21\u5757\u7ba1\u7406');
  if (!clickedMobileModule.clicked) throw new Error('Could not click mobile module management sidebar.');
  const clickedMobilePageTab = await clickByText(client, '\u9875\u9762');
  if (!clickedMobilePageTab.clicked) throw new Error('Could not click mobile page designer tab from module management.');
  const mobileModuleDesignerVisible = await waitForText(client, '\u6a21\u5757\u9875\u9762\u8bbe\u8ba1\u5668');
  if (!mobileModuleDesignerVisible.found) throw new Error(`Mobile module page designer did not appear: ${mobileModuleDesignerVisible.text}`);
  await screenshot(client, 'mobile-admin-module-page-designer.png');
  result.results.push(await inspect(client, 'mobile-admin-module-page-designer'));

  await setStorage(client, roles.normal);
  await setViewport(client, 1280, 720);
  await navigate(client, `${baseUrl}/?r86=${Date.now()}#/systems/${systemId}/modules`);
  await waitForText(client, moduleName);
  const clickedCreate = await clickByText(client, '\u65b0\u5efa\u8bb0\u5f55');
  if (!clickedCreate.clicked && !clickedCreate.disabled) throw new Error('Could not find normal runtime create button.');
  if (clickedCreate.clicked) {
    await waitForText(client, '\u53ea\u8bfb');
  }
  await screenshot(client, 'desktop-normal-runtime-readonly-form.png');
  result.results.push(await inspect(client, 'desktop-normal-runtime-readonly-form'));

  await setViewport(client, 390, 720);
  await navigate(client, `${baseUrl}/?r86=${Date.now()}#/systems/${systemId}/modules`);
  await waitForText(client, moduleName);
  const clickedMobileCreate = await clickByText(client, '\u65b0\u5efa\u8bb0\u5f55');
  if (!clickedMobileCreate.clicked && !clickedMobileCreate.disabled) throw new Error('Could not find mobile normal runtime create button.');
  if (clickedMobileCreate.clicked) {
    await waitForText(client, '\u53ea\u8bfb');
  }
  await screenshot(client, 'mobile-normal-runtime-readonly-form.png');
  result.results.push(await inspect(client, 'mobile-normal-runtime-readonly-form'));

  client.close();
  return result;
}

run().then((result) => {
  console.log(JSON.stringify(result, null, 2));
}).catch((error) => {
  console.error(error && error.stack ? error.stack : String(error));
  process.exit(1);
});
