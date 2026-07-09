const apiBase = location.port === '9999' ? '' : 'http://127.0.0.1:9999';
const storageKey = 'unexamine.clean.session';

const state = {
  token: '',
  profile: null,
  currentContext: null,
  systems: [],
  adminConfigs: [],
  businessModules: [],
  moduleConfigsByCode: {},
  records: [],
  selectedRecord: null,
  activeNav: 'workbench',
  activeGroup: 0,
  activeModule: 0,
  detailTab: 'overview',
  snapshot: null,
  error: '',
  loading: false,
};

const app = document.querySelector('#app');

function restoreStoredSession() {
  try {
    const stored = JSON.parse(localStorage.getItem(storageKey) || 'null');
    if (stored?.token) {
      state.token = stored.token;
      state.profile = stored.profile;
      state.currentContext = stored.currentContext;
      state.systems = stored.systems || [];
    }
  } catch {
    localStorage.removeItem(storageKey);
  }
}

function persistSession() {
  localStorage.setItem(storageKey, JSON.stringify({
    token: state.token,
    profile: state.profile,
    currentContext: state.currentContext,
    systems: state.systems,
  }));
}

async function api(path, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (state.token) {
    headers.Authorization = `Bearer ${state.token}`;
  }
  if (options.body && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json';
  }
  const response = await fetch(`${apiBase}${path}`, { ...options, headers });
  if (response.status === 401) {
    clearSession();
    throw new Error('登录已失效，请重新登录');
  }
  if (!response.ok) {
    let message = `请求失败 ${response.status}`;
    try {
      const payload = await response.json();
      message = payload.message || payload.error || message;
    } catch {
      // Keep status message when body is not JSON.
    }
    throw new Error(message);
  }
  if (response.status === 204) {
    return null;
  }
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

async function boot() {
  restoreStoredSession();
  if (!state.token) {
    renderLogin();
    return;
  }
  try {
    state.loading = true;
    renderShellSkeleton();
    const profile = await api('/api/auth/profile');
    state.profile = profile.profile;
    state.currentContext = profile.currentContext;
    state.systems = profile.systems || [];
    persistSession();
    await loadShell();
  } catch (error) {
    state.error = error.message;
    renderLogin();
  } finally {
    state.loading = false;
  }
}

async function login(event) {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  const username = String(form.get('username') || '').trim();
  const password = String(form.get('password') || '');
  state.error = '';
  state.loading = true;
  renderLogin();
  try {
    const result = await api('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    });
    state.token = result.token;
    state.profile = result.profile;
    state.currentContext = result.currentContext;
    state.systems = result.systems || [];
    persistSession();
    await loadShell();
  } catch (error) {
    state.error = error.message;
    renderLogin();
  } finally {
    state.loading = false;
  }
}

async function switchContext(target, systemId) {
  state.error = '';
  try {
    const context = await api('/api/context/switch', {
      method: 'POST',
      body: JSON.stringify({ target, systemId }),
    });
    state.currentContext = context;
    state.activeNav = 'workbench';
    state.activeGroup = 0;
    state.activeModule = 0;
    persistSession();
    await loadShell();
  } catch (error) {
    state.error = error.message;
    render();
  }
}

async function loadShell() {
  state.error = '';
  renderShellSkeleton();
  const context = state.currentContext?.scope || 'platform';
  state.snapshot = await api(`/api/shell?context=${encodeURIComponent(context)}`);
  state.adminConfigs = await api('/api/admin/config');
  state.businessModules = await api('/api/business/modules');
  state.moduleConfigsByCode = Object.fromEntries(state.businessModules.map((item) => [item.moduleCode, item]));
  await loadRecordsForActiveModule();
  render();
}

async function loadRecordsForActiveModule() {
  const module = activeModule();
  if (!module?.code) {
    state.records = [];
    state.selectedRecord = null;
    return;
  }
  state.records = await api(`/api/business/modules/${encodeURIComponent(module.code)}/records`);
  state.selectedRecord = state.records[0] || null;
}

async function logout() {
  try {
    await api('/api/auth/logout', { method: 'POST' });
  } catch {
    // Local logout still wins when the server session is already gone.
  }
  clearSession();
  renderLogin();
}

function clearSession() {
  state.token = '';
  state.profile = null;
  state.currentContext = null;
  state.systems = [];
  state.snapshot = null;
  localStorage.removeItem(storageKey);
}

function renderLogin() {
  app.innerHTML = `
    <main class="login-shell">
      <section class="login-panel">
        <div>
          <span class="eyebrow">clean rebuild</span>
          <h1>unexamine</h1>
          <p>登录后进入平台工作台，再通过系统切换获得系统上下文。</p>
        </div>
        <form class="login-form">
          <label>
            <span>账号</span>
            <input name="username" value="admin" autocomplete="username" />
          </label>
          <label>
            <span>密码</span>
            <input name="password" type="password" value="123123aa" autocomplete="current-password" />
          </label>
          ${state.error ? `<div class="form-error">${escapeHtml(state.error)}</div>` : ''}
          <button type="submit" ${state.loading ? 'disabled' : ''}>${state.loading ? '登录中' : '登录'}</button>
        </form>
        <div class="login-hint">
          <span>admin / 123123aa</span>
          <span>che / 123123aa</span>
        </div>
      </section>
    </main>
  `;
  app.querySelector('form')?.addEventListener('submit', login);
}

function renderShellSkeleton() {
  app.innerHTML = `
    <main class="app-shell">
      <section class="empty-state">正在读取系统上下文...</section>
    </main>
  `;
}

function render() {
  if (state.error && !state.snapshot) {
    app.innerHTML = `
      <main class="app-shell">
        <section class="empty-state error">
          <strong>无法读取后台上下文</strong>
          <span>${escapeHtml(state.error)}</span>
          <button data-action="reload">重试</button>
          <button data-action="logout">退出登录</button>
        </section>
      </main>
    `;
    bindActions();
    return;
  }

  const snapshot = state.snapshot;
  const groups = businessGroups();
  const group = groups[state.activeGroup] ?? groups[0];
  const module = group?.modules[state.activeModule] ?? group?.modules[0];
  const rows = activeRows(snapshot, group);

  app.innerHTML = `
    <main class="app-shell">
      <header class="topbar">
        <div class="brand">
          <strong>unexamine</strong>
          <span>${escapeHtml(snapshot.activeSystemName)}</span>
        </div>
        <nav class="topnav">
          ${snapshot.navigation.map((item) => navButton(item)).join('')}
        </nav>
        <div class="user-zone">
          <span>${escapeHtml(state.profile?.displayName || '')}</span>
          <button data-action="logout">退出</button>
        </div>
      </header>

      <section class="context-bar">
        <div class="context-switch" aria-label="上下文切换">
          <button class="${state.currentContext?.scope === 'platform' ? 'active' : ''}" data-context-target="platform">平台</button>
          <button class="${state.currentContext?.scope === 'system' ? 'active' : ''}" data-context-target="system">系统</button>
        </div>
        <label class="system-select">
          <span>系统切换</span>
          <select data-system-select>
            ${state.systems.map((item) => `
              <option value="${escapeHtml(item.systemId)}" ${item.systemId === state.currentContext?.systemId ? 'selected' : ''}>
                ${escapeHtml(item.systemName)}
              </option>
            `).join('')}
          </select>
        </label>
        <span class="context-proof">${contextProof()}</span>
      </section>

      <section class="workspace">
        <aside class="left-tabs">
          <div class="rail-title">业务模块组</div>
          ${groups.map((item, index) => `
            <button class="${index === state.activeGroup ? 'active' : ''}" data-group="${index}">
              ${escapeHtml(item.label)}
            </button>
          `).join('')}
          <div class="rail-title second">组内模块</div>
          ${(group?.modules ?? []).map((item, index) => `
            <button class="${index === state.activeModule ? 'active' : ''}" data-module="${index}">
              ${escapeHtml(item.label)}
            </button>
          `).join('')}
        </aside>

        <section class="list-surface">
          <div class="section-head">
            <div>
              <span class="eyebrow">${escapeHtml(activeNavLabel())}</span>
              <h1>${escapeHtml(listTitle(module))}</h1>
            </div>
            <div class="toolbar">
              <button>新建</button>
              <button>筛选</button>
              <button>导出</button>
            </div>
          </div>
          ${state.error ? `<div class="inline-error">${escapeHtml(state.error)}</div>` : ''}
          <table>
            <thead>
              <tr>
                <th>对象</th>
                <th>说明</th>
                <th>归属/配置</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              ${rows.map((item, index) => `
                <tr class="${index === selectedRowIndex() ? 'selected' : ''}" data-row="${index}">
                  <td>${escapeHtml(item.name)}</td>
                  <td>${escapeHtml(item.description)}</td>
                  <td>${item.tags.map((name) => `<span class="tag">${escapeHtml(name)}</span>`).join('')}</td>
                  <td><span class="status">${escapeHtml(item.status)}</span></td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </section>

        <aside class="detail-surface">
          <div class="detail-head">
            <span class="eyebrow">右侧详情</span>
            <h2>${escapeHtml(detailTitle(module))}</h2>
          </div>
          <div class="detail-tabs">
            ${detailTab('overview', '基础')}
            ${detailTab('flow', 'Flow')}
            ${detailTab('app', '应用授权')}
            ${detailTab('admin', '后台配置')}
          </div>
          <div class="detail-body">
            ${renderDetail(snapshot, module)}
          </div>
        </aside>
      </section>
    </main>
  `;
  bindActions();
}

function navButton(item) {
  return `
    <button class="${state.activeNav === item.code ? 'active' : ''}" data-nav="${escapeHtml(item.code)}" title="${escapeHtml(item.description)}">
      ${escapeHtml(item.label)}
    </button>
  `;
}

function detailTab(code, label) {
  return `<button class="${state.detailTab === code ? 'active' : ''}" data-detail-tab="${code}">${label}</button>`;
}

function activeRows(snapshot, group) {
  if (state.activeNav === 'flow') {
    return snapshot.flowManagement.map((name) => row(name, 'Flow 管理能力', ['流程图', '节点', '发布检查'], '已纳入'));
  }
  if (state.activeNav === 'application') {
    return snapshot.applicationGateway.map((name) => row(name, '应用授权阀门', ['scope', 'SecretRef', '审计'], '已纳入'));
  }
  if (state.activeNav === 'admin') {
    return state.adminConfigs.map((item) => row(item.label, item.description, [item.group, ...item.fields], item.status));
  }
  if (state.activeNav === 'profile') {
    return state.systems.map((item) => row(item.systemName, item.accountMemberBindingId, item.effectiveRoles, '可切换'));
  }
  return state.records.map((item) => row(item.title, recordDescription(item), recordTags(item), item.status));
}

function renderDetail(snapshot, module) {
  const config = module ? state.moduleConfigsByCode[module.code] : null;
  if (state.activeNav === 'profile') {
    return `
      <section class="detail-block">
        <h3>个人信息</h3>
        <p>${escapeHtml(state.profile?.displayName)} / ${escapeHtml(state.profile?.username)}</p>
        <p>${escapeHtml(contextProof())}</p>
      </section>
      ${listBlock('可切换系统', state.systems.map((item) => `${item.systemName}：${item.effectiveRoles.join(', ')}`))}
    `;
  }
  if (state.detailTab === 'flow') {
    return listBlock('Flow 管理', snapshot.flowManagement);
  }
  if (state.detailTab === 'app') {
    return listBlock('应用授权阀门', snapshot.applicationGateway);
  }
  if (state.detailTab === 'admin') {
    return listBlock('后台配置读回', state.adminConfigs.map((item) => `${item.label} / ${item.status}：${item.description}`));
  }
  return `
    <section class="detail-block">
      <h3>${escapeHtml(state.selectedRecord?.title || config?.moduleName || '模块详情')}</h3>
      <p>${escapeHtml(config?.purpose || activeNavDescription())}</p>
      ${state.selectedRecord ? `<p>${escapeHtml(recordDescription(state.selectedRecord))}</p>` : ''}
    </section>
    ${state.selectedRecord ? listBlock('记录字段读回', Object.entries(state.selectedRecord.values || {}).map(([key, value]) => `${key}：${value}`)) : ''}
    ${config ? listBlock('字段配置', config.fields.map((item) => `${item.label} / ${item.type} / ${item.required ? '必填' : '可选'}`)) : ''}
    ${config ? listBlock('动作配置', config.actions.map((item) => `${item.label} / ${item.type}`)) : ''}
    ${config ? listBlock('页面配置', config.pages.map((item) => `${item.label} / ${item.layout}`)) : ''}
    ${config ? listBlock('打印模板', config.printTemplates.map((item) => item.label)) : ''}
    ${listBlock('详情布局规则', snapshot.layoutRules)}
  `;
}

function businessGroups() {
  if (!state.businessModules.length && state.snapshot?.moduleGroups?.length) {
    return state.snapshot.moduleGroups.map((group) => ({
      code: group.code,
      label: group.label,
      modules: group.modules.map((module) => ({
        code: module.code,
        label: module.label,
        purpose: module.purpose,
        ownedConfig: module.ownedConfig || [],
      })),
    }));
  }
  const map = new Map();
  state.businessModules.forEach((config) => {
    if (!map.has(config.groupCode)) {
      map.set(config.groupCode, { code: config.groupCode, label: config.groupName, modules: [] });
    }
    map.get(config.groupCode).modules.push({
      code: config.moduleCode,
      label: config.moduleName,
      purpose: config.purpose,
      ownedConfig: ['字段', '动作', '页面', '打印模板'],
    });
  });
  return Array.from(map.values());
}

function activeModule() {
  const groups = businessGroups();
  const group = groups[state.activeGroup] ?? groups[0];
  return group?.modules[state.activeModule] ?? group?.modules[0] ?? null;
}

function row(name, description, tags, status) {
  return { name, description, tags, status };
}

function recordDescription(recordItem) {
  return `${recordItem.recordId} / ${recordItem.updatedAt}`;
}

function recordTags(recordItem) {
  return Object.entries(recordItem.values || {}).slice(0, 4).map(([key, value]) => `${key}:${value}`);
}

function listBlock(title, items) {
  return `
    <section class="detail-block">
      <h3>${escapeHtml(title)}</h3>
      <ul>
        ${items.map((item) => `<li>${escapeHtml(item)}</li>`).join('')}
      </ul>
    </section>
  `;
}

function activeNavLabel() {
  return state.snapshot.navigation.find((item) => item.code === state.activeNav)?.label ?? '工作台';
}

function activeNavDescription() {
  return state.snapshot.navigation.find((item) => item.code === state.activeNav)?.description ?? '';
}

function listTitle(module) {
  if (state.activeNav === 'workbench') {
    return state.currentContext?.scope === 'system' ? `${module?.label || '模块'}记录` : '平台工作台统计';
  }
  if (state.activeNav === 'profile') {
    return '个人信息与系统切换';
  }
  return activeNavLabel() || module?.label || '列表';
}

function detailTitle(module) {
  if (state.activeNav === 'profile') {
    return '个人信息';
  }
  return state.selectedRecord?.title || module?.label || activeNavLabel();
}

function selectedRowIndex() {
  if (['flow', 'application', 'admin', 'profile'].includes(state.activeNav)) {
    return -1;
  }
  return state.records.findIndex((item) => item.recordId === state.selectedRecord?.recordId);
}

function contextProof() {
  const context = state.currentContext;
  if (!context) {
    return '未取得上下文';
  }
  if (context.scope === 'system') {
    return `系统上下文：${context.systemName} / ${context.accountMemberBindingId} / ${context.effectiveRoles.join(', ')}`;
  }
  return `平台上下文：${context.effectiveRoles.join(', ')}`;
}

function bindActions() {
  app.querySelector('[data-action="reload"]')?.addEventListener('click', loadShell);
  app.querySelector('[data-action="logout"]')?.addEventListener('click', logout);
  app.querySelectorAll('[data-context-target]').forEach((button) => {
    button.addEventListener('click', () => {
      const target = button.dataset.contextTarget;
      const systemId = target === 'system' ? (state.currentContext?.systemId || state.systems[0]?.systemId) : null;
      switchContext(target, systemId);
    });
  });
  app.querySelector('[data-system-select]')?.addEventListener('change', (event) => {
    switchContext('system', event.target.value);
  });
  app.querySelectorAll('[data-nav]').forEach((button) => {
    button.addEventListener('click', () => {
      state.activeNav = button.dataset.nav;
      render();
    });
  });
  app.querySelectorAll('[data-group]').forEach((button) => {
    button.addEventListener('click', async () => {
      state.activeGroup = Number(button.dataset.group);
      state.activeModule = 0;
      await loadRecordsForActiveModule();
      render();
    });
  });
  app.querySelectorAll('[data-module]').forEach((rowElement) => {
    rowElement.addEventListener('click', async () => {
      state.activeModule = Number(rowElement.dataset.module);
      await loadRecordsForActiveModule();
      render();
    });
  });
  app.querySelectorAll('[data-row]').forEach((rowElement) => {
    rowElement.addEventListener('click', () => {
      if (!['flow', 'application', 'admin', 'profile'].includes(state.activeNav)) {
        state.selectedRecord = state.records[Number(rowElement.dataset.row)] || null;
        render();
      }
    });
  });
  app.querySelectorAll('[data-detail-tab]').forEach((button) => {
    button.addEventListener('click', () => {
      state.detailTab = button.dataset.detailTab;
      render();
    });
  });
}

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

boot();