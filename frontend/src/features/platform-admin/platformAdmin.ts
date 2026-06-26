import {
  createPlatformIdentityProvider,
  createPlatformModelAuthorization,
  createPlatformRole,
  loadPlatformAdminData,
  publishPlatformIdentityProvider,
  runPlatformSystemLifecycle,
  runSystemHealthCheck,
  testPlatformIdentityProvider,
  type AuditLogView,
  type IdentityProviderView,
  type ModelAuthorizationView,
  type PlatformAdminData,
  type PlatformSystem,
  type RoleView,
} from '../../api/liveData';
import type { PageResult } from '../../api/types';
import type { Navigate } from '../../app/app';
import { shellState } from '../../app/state';
import { createButton, createElement, createTraceLine, type ChildNodeValue } from '../../shared/components';
import { createFilterBar } from '../../shared/filters';
import { renderStatusPill, type StatusTone } from '../../shared/status';

export function renderPlatformAdmin(navigate: Navigate): HTMLElement {
  const content = createElement('div', { className: 'admin-content' }, createLoadingPanel('正在读取平台后台数据...'));
  const root = createElement('section', { className: 'admin-layout' }, createPlatformAdminSidebar(navigate), content);
  const reload = () => {
    content.replaceChildren(createLoadingPanel('正在读取平台后台数据...'));
    void loadPlatformAdminData()
      .then((data) => content.replaceChildren(...createPlatformAdminContent(data, reload)))
      .catch((error) => content.replaceChildren(createErrorPanel(error)));
  };

  reload();

  return root;
}

function createPlatformAdminSidebar(navigate: Navigate): HTMLElement {
  const backButton = createButton('返回平台首页', 'secondary', false);
  backButton.addEventListener('click', () => navigate('/platform'));
  return createElement(
    'aside',
    { className: 'module-sidebar admin-sidebar' },
    createElement('strong', {}, '平台后台'),
    backButton,
    createSidebarButton('平台信息', 'platform-info', true),
    createSidebarButton('组织架构', 'platform-role'),
    createSidebarButton('角色管理', 'platform-role'),
    createSidebarButton('仪表盘管理', 'platform-info'),
    createSidebarButton('配置管理', 'platform-config'),
    createSidebarButton('日志管理', 'platform-logs'),
  );
}

function createPlatformAdminContent(data: PlatformAdminData, reload: () => void): HTMLElement[] {
  const warningPanel = createWarningPanel(data.warnings);
  return [
    createElement(
      'div',
      { className: 'page-heading' },
      createElement('h1', {}, '平台后台'),
      createElement('p', {}, '平台后台只处理平台信息、平台组织、平台角色、系统生命周期、统一认证、AI Agent 授权和平台日志。'),
    ),
    ...(warningPanel ? [warningPanel] : []),
    createPlatformInfoPanel(data),
    createSystemLifecyclePanel(data.systems, reload),
    createPlatformRolePanel(data.roles, reload),
    createPlatformConfigPanel(data.identityProviders, data.modelAuthorizations, data.health?.traceId, reload),
    createPlatformLogPanel(data.logs),
  ];
}

function createPlatformInfoPanel(data: PlatformAdminData): HTMLElement {
  const healthTone = data.health?.status === 'UP' ? 'success' : 'warning';
  return createElement(
    'section',
    { id: 'platform-info', className: 'panel' },
    createElement('h2', {}, '平台信息'),
    createElement(
      'div',
      { className: 'metric-grid' },
      createMetric('系统数量', String(data.systems.total)),
      createMetric('平台角色', String(data.roles.total)),
      createMetric('企业 SSO', String(data.identityProviders.length)),
      createMetric('模型授权', String(data.modelAuthorizations.total)),
    ),
    createElement(
      'div',
      { className: 'simple-stack' },
      createElement('div', { className: 'list-line' }, createElement('span', {}, '平台健康'), renderStatusPill(data.health?.status ?? '未读取', healthTone)),
      createElement('div', { className: 'list-line' }, createElement('span', {}, '当前账号'), createElement('strong', {}, shellState.account.displayName)),
      createElement('div', { className: 'list-line' }, createElement('span', {}, '平台角色'), createElement('span', {}, shellState.account.platformRoles.join(' / ') || '未授权')),
    ),
    data.health ? createTraceLine(data.health.traceId) : null,
  );
}

function createSystemLifecyclePanel(page: PageResult<PlatformSystem>, reload: () => void): HTMLElement {
  return createElement(
    'section',
    { id: 'platform-system', className: 'panel' },
    createElement('h2', {}, '系统生命周期'),
    createFilterBar(['系统名称', '租户模式', '负责人', '状态', '时间范围']),
    createTable(
      ['序号', '系统', '租户模式', '负责人', '状态', '更新时间', '操作'],
      page.records.map((system, index) => [
        index + 1,
        createElement('span', {}, createElement('strong', {}, system.systemName), createElement('small', {}, ` ${system.systemCode}`)),
        tenantModeText(system.tenantMode),
        system.ownerAccountId,
        renderStatusPill(systemStatusText(system.status), systemStatusTone(system.status)),
        formatTime(system.updatedAt),
        createElement(
          'div',
          { className: 'row-actions' },
          createSystemHealthButton(system),
          createSystemLifecycleButton(system, reload),
        ),
      ]),
      '暂无系统，请在平台工作台创建系统。',
    ),
    createPagination(page),
  );
}

function createSystemLifecycleButton(system: PlatformSystem, reload: () => void): HTMLButtonElement {
  const action = system.status === 1 ? 'disable' : 'enable';
  const label = system.status === 1 ? '禁用' : '启用';
  const button = createButton(label, action === 'disable' ? 'ghost' : 'secondary', false);
  button.addEventListener('click', async () => {
    const reason = lifecycleReason(label, system);
    if (!reason) {
      return;
    }
    button.disabled = true;
    button.textContent = `${label}中...`;
    try {
      const result = await runPlatformSystemLifecycle(system.systemId, action, reason);
      button.textContent = `${label}完成`;
      button.title = `traceId=${result.traceId}`;
      reload();
    } catch (error) {
      button.disabled = false;
      button.textContent = `${label}失败`;
      button.title = error instanceof Error ? error.message : `${label}系统失败。`;
    }
  });
  return button;
}

function lifecycleReason(label: string, system: PlatformSystem): string | undefined {
  const reason = window.prompt(`请输入${label}「${system.systemName}」的原因`, label === '禁用' ? system.disabledReason ?? '' : '恢复系统使用');
  const normalized = reason?.trim();
  if (!normalized) {
    return undefined;
  }
  return normalized;
}

function createSystemHealthButton(system: PlatformSystem): HTMLButtonElement {
  const button = createButton('体检', 'secondary', false);
  button.addEventListener('click', async () => {
    button.disabled = true;
    button.textContent = '体检中...';
    try {
      const result = await runSystemHealthCheck(system.systemId, 'SYSTEM');
      button.textContent = result.status === 'PASS' || result.status === 'UP' ? '体检通过' : `体检${result.status}`;
      button.title = `traceId=${result.traceId}`;
    } catch (error) {
      button.textContent = '体检失败';
      button.title = error instanceof Error ? error.message : '系统体检失败。';
    } finally {
      button.disabled = false;
    }
  });
  return button;
}

function createPlatformRolePanel(page: PageResult<RoleView>, reload: () => void): HTMLElement {
  const result = createElement('p', { className: 'field-error' }, '平台角色只控制平台后台和平台工作台能力，不直接授权系统业务数据。');
  const createRoleButton = createButton('新建平台角色', 'primary', false);
  createRoleButton.addEventListener('click', async () => {
    const roleName = window.prompt('平台角色名称');
    if (!roleName?.trim()) {
      return;
    }
    const roleCode = window.prompt('平台角色编码', `platform_role_${Date.now()}`)?.trim();
    if (!roleCode) {
      return;
    }
    createRoleButton.disabled = true;
    createRoleButton.textContent = '创建中...';
    try {
      const role = await createPlatformRole({
        roleName: roleName.trim(),
        roleCode,
        roleType: 'CUSTOM',
      });
      result.textContent = `平台角色已创建：${role.roleName}`;
      reload();
    } catch (error) {
      createRoleButton.disabled = false;
      createRoleButton.textContent = '新建平台角色';
      result.textContent = error instanceof Error ? error.message : '创建平台角色失败。';
    }
  });
  return createElement(
    'section',
    { id: 'platform-role', className: 'panel' },
    createElement('h2', {}, '平台角色与权限'),
    createElement('div', { className: 'inline-actions' }, createRoleButton),
    result,
    createFilterBar(['角色名称', '角色类型', '状态']),
    createTable(
      ['序号', '角色', '类型', '内置', '状态', '说明'],
      page.records.map((role, index) => [
        index + 1,
        createElement('span', {}, createElement('strong', {}, role.roleName), createElement('small', {}, ` ${role.roleCode}`)),
        role.roleType,
        role.builtin ? '是' : '否',
        renderStatusPill(enableStatusText(role.status), enableStatusTone(role.status)),
        role.description || '-',
      ]),
      '暂无平台角色。',
    ),
    createPagination(page),
  );
}

function createPlatformConfigPanel(
  providers: IdentityProviderView[],
  authorizations: PageResult<ModelAuthorizationView>,
  traceId?: string,
  reload?: () => void,
): HTMLElement {
  const ssoResult = createElement('p', { className: 'field-error' }, 'SSO 身份源支持创建、测试和发布；密钥使用 SecretRef，不在页面展示明文。');
  const agentResult = createElement('p', { className: 'field-error' }, 'AI Agent 授权创建后可供系统策略选择，默认按不外发和脱敏策略保存。');
  const createProviderButton = createButton('新增身份源', 'primary', false);
  const createAuthorizationButton = createButton('新增模型授权', 'primary', false);
  createProviderButton.addEventListener('click', async () => {
    const name = window.prompt('身份源名称，例如：企业 OIDC');
    if (!name?.trim()) {
      return;
    }
    const issuer = window.prompt('Issuer 地址，例如：https://idp.example.com', '')?.trim();
    const clientId = window.prompt('Client ID', '')?.trim();
    const secretRefId = window.prompt('SecretRef ID，不填写则保存草稿但测试不会通过', '')?.trim();
    createProviderButton.disabled = true;
    createProviderButton.textContent = '创建中...';
    try {
      const provider = await createPlatformIdentityProvider({
        name: name.trim(),
        protocol: 'OIDC',
        issuer,
        clientId,
        secretRefId,
        domainWhitelist: [],
      });
      ssoResult.textContent = `身份源已创建：${provider.name} / ${provider.providerId}`;
      reload?.();
    } catch (error) {
      createProviderButton.disabled = false;
      createProviderButton.textContent = '新增身份源';
      ssoResult.textContent = error instanceof Error ? error.message : '创建身份源失败。';
    }
  });
  createAuthorizationButton.addEventListener('click', async () => {
    const authorizationCode = window.prompt('授权编码', `model_auth_${Date.now()}`)?.trim();
    if (!authorizationCode) {
      return;
    }
    const modelProvider = window.prompt('模型供应商', 'LOCAL')?.trim() || 'LOCAL';
    const modelName = window.prompt('模型名称', 'local-model')?.trim() || 'local-model';
    createAuthorizationButton.disabled = true;
    createAuthorizationButton.textContent = '创建中...';
    try {
      const authorization = await createPlatformModelAuthorization({ authorizationCode, modelProvider, modelName });
      agentResult.textContent = `模型授权已创建：${authorization.authorizationCode} / ${authorization.modelProvider}`;
      reload?.();
    } catch (error) {
      createAuthorizationButton.disabled = false;
      createAuthorizationButton.textContent = '新增模型授权';
      agentResult.textContent = error instanceof Error ? error.message : '创建模型授权失败。';
    }
  });
  return createElement(
    'section',
    { id: 'platform-config', className: 'panel' },
    createElement('h2', {}, '配置管理'),
    createElement(
      'div',
      { className: 'split-grid' },
      createElement(
        'div',
        { className: 'result-panel' },
        createElement('strong', {}, '企业 SSO'),
        createElement('div', { className: 'inline-actions' }, createProviderButton),
        ssoResult,
        providers.length === 0
          ? createElement('p', {}, '暂无身份源，请配置 OIDC、SAML 或企业自建身份源后发布。')
          : createElement('div', { className: 'simple-stack' }, ...providers.map((provider) => createProviderLine(provider, reload, ssoResult))),
      ),
      createElement(
        'div',
        { className: 'result-panel' },
        createElement('strong', {}, 'AI Agent 授权'),
        createElement('div', { className: 'inline-actions' }, createAuthorizationButton),
        agentResult,
        authorizations.records.length === 0
          ? createElement('p', {}, '暂无模型授权，系统可在授权后使用平台模型或外部模型。')
          : createElement('div', { className: 'simple-stack' }, ...authorizations.records.map((authorization) => createAuthorizationLine(authorization))),
      ),
    ),
    traceId ? createTraceLine(traceId) : null,
  );
}

function createPlatformLogPanel(page: PageResult<AuditLogView>): HTMLElement {
  return createElement(
    'section',
    { id: 'platform-logs', className: 'panel' },
    createElement('h2', {}, '日志管理'),
    createFilterBar(['日志类型', '账号', '系统', '结果', 'traceId', '时间范围']),
    createTable(
      ['序号', '类型', '操作人', '动作', '对象', '结果', 'traceId', '时间'],
      page.records.map((log, index) => [
        index + 1,
        log.logType,
        log.operator || '-',
        log.action,
        `${log.objectType || '-'} / ${log.objectId || '-'}`,
        renderStatusPill(log.result, log.result === 'SUCCESS' ? 'success' : 'warning'),
        log.traceId,
        formatTime(log.createdAt),
      ]),
      '暂无平台日志。',
    ),
    createPagination(page),
  );
}

function createProviderLine(provider: IdentityProviderView, reload: (() => void) | undefined, result: HTMLElement): HTMLElement {
  const testButton = createButton('测试', 'secondary', false);
  const publishButton = createButton('发布', 'primary', provider.status === 'ENABLED', provider.status === 'ENABLED' ? '身份源已发布。' : undefined);
  testButton.addEventListener('click', async () => {
    const redirectUri = window.prompt('测试回调地址', `${window.location.origin}/sso/callback`)?.trim();
    if (!redirectUri) {
      return;
    }
    const testLoginName = window.prompt('测试登录名', shellState.account.displayName)?.trim() || shellState.account.displayName;
    testButton.disabled = true;
    testButton.textContent = '测试中...';
    try {
      const tested = await testPlatformIdentityProvider(provider.providerId, redirectUri, testLoginName);
      testButton.disabled = false;
      testButton.textContent = tested.passed ? '测试通过' : '测试未过';
      testButton.title = `traceId=${tested.traceId}`;
      result.textContent = tested.passed ? `身份源测试通过：${provider.name}` : `身份源测试未通过：${tested.disabledReason ?? '配置不完整'}，traceId=${tested.traceId}`;
    } catch (error) {
      testButton.disabled = false;
      testButton.textContent = '测试失败';
      result.textContent = error instanceof Error ? error.message : '测试身份源失败。';
    }
  });
  publishButton.addEventListener('click', async () => {
    if (publishButton.disabled || !window.confirm(`确认发布身份源「${provider.name}」吗？`)) {
      return;
    }
    publishButton.disabled = true;
    publishButton.textContent = '发布中...';
    try {
      const published = await publishPlatformIdentityProvider(provider.providerId);
      result.textContent = `身份源已发布：${published.publishStatus}，traceId=${published.traceId}`;
      reload?.();
    } catch (error) {
      publishButton.disabled = false;
      publishButton.textContent = '发布失败';
      result.textContent = error instanceof Error ? error.message : '发布身份源失败。';
    }
  });
  return createElement(
    'div',
    { className: 'list-line' },
    createElement('span', {}, `${provider.name} / ${provider.protocol}`),
    renderStatusPill(provider.status || 'UNKNOWN', provider.status === 'ENABLED' ? 'success' : 'warning'),
    createElement('div', { className: 'row-actions' }, testButton, publishButton),
  );
}

function createAuthorizationLine(authorization: ModelAuthorizationView): HTMLElement {
  return createElement(
    'div',
    { className: 'list-line' },
    createElement('span', {}, `${authorization.authorizationCode} / ${authorization.modelProvider} / ${authorization.modelName}`),
    renderStatusPill(enableStatusText(authorization.status), enableStatusTone(authorization.status)),
  );
}

function createWarningPanel(warnings: string[]): HTMLElement | null {
  if (warnings.length === 0) {
    return null;
  }
  return createElement(
    'section',
    { className: 'panel' },
    createElement('h2', {}, '加载提醒'),
    createElement('div', { className: 'simple-stack' }, ...warnings.map((warning) => createElement('p', {}, warning))),
  );
}

function createTable(headers: string[], rows: ChildNodeValue[][], emptyText: string): HTMLElement {
  if (rows.length === 0) {
    return createElement('section', { className: 'runtime-card' }, emptyText);
  }
  return createElement(
    'div',
    { className: 'table-shell' },
    createElement(
      'table',
      { className: 'data-table' },
      createElement('thead', {}, createElement('tr', {}, ...headers.map((header) => createElement('th', {}, header)))),
      createElement(
        'tbody',
        {},
        ...rows.map((row) => createElement('tr', {}, ...row.map((cell) => createElement('td', {}, cell)))),
      ),
    ),
  );
}

function createSidebarButton(label: string, targetId: string, active = false): HTMLButtonElement {
  const button = createElement('button', { className: active ? 'sidebar-item active' : 'sidebar-item' }, label);
  button.addEventListener('click', () => {
    document.getElementById(targetId)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    button.closest('.admin-sidebar')?.querySelectorAll('.sidebar-item').forEach((item) => item.classList.remove('active'));
    button.classList.add('active');
  });
  return button;
}

function createPagination<T>(page: PageResult<T>): HTMLElement {
  return createElement(
    'footer',
    { className: 'pagination' },
    createElement('span', {}, `第 ${page.pageNo} 页，每页 ${page.pageSize} 条，共 ${page.total} 条`),
    createButton('上一页', 'ghost', page.pageNo <= 1, '已经是第一页'),
    createButton('下一页', 'ghost', !page.hasNext, '没有更多数据'),
  );
}

function createMetric(label: string, value: string): HTMLElement {
  return createElement('article', { className: 'metric-card' }, createElement('span', {}, label), createElement('strong', {}, value));
}

function tenantModeText(value: number): string {
  return value === 1 ? '多租户' : '单租户';
}

function systemStatusText(value: number): string {
  return value === 1 ? '运行中' : '已停用';
}

function systemStatusTone(value: number): StatusTone {
  return value === 1 ? 'success' : 'warning';
}

function enableStatusText(value: number): string {
  return value === 1 ? '启用' : '停用';
}

function enableStatusTone(value: number): StatusTone {
  return value === 1 ? 'success' : 'warning';
}

function formatTime(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '-';
}

function createLoadingPanel(text: string): HTMLElement {
  return createElement('section', { className: 'panel' }, createElement('strong', {}, text));
}

function createErrorPanel(error: unknown): HTMLElement {
  return createElement('section', { className: 'panel' }, createElement('strong', {}, '加载失败'), createElement('p', {}, error instanceof Error ? error.message : '请稍后重试。'));
}
