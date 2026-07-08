import {
  createPlatformArchiveRestoreRequest,
  createPlatformBackupTask,
  createPlatformIdentityProvider,
  createPlatformModelAuthorization,
  createPlatformRole,
  loadPlatformApiCachePolicy,
  loadPlatformDeployments,
  loadPlatformAdminData,
  publishPlatformIdentityProvider,
  rollbackPlatformDeployment,
  runPlatformHealthCheck,
  runPlatformRestoreDrill,
  runPlatformSystemLifecycle,
  runSystemHealthCheck,
  testPlatformIdentityProvider,
  updatePlatformApiCachePolicy,
  updatePlatformFeatureFlag,
  updatePlatformQuota,
  updatePlatformRateLimitPolicy,
  type AuditLogView,
  type IdentityProviderView,
  type ModelAuthorizationView,
  type OpsApiCachePolicyView,
  type OpsDeploymentView,
  type OpsFeatureFlagView,
  type OpsHealthCheck,
  type OpsQuotaView,
  type OpsRateLimitPolicyView,
  type PlatformAdminData,
  type PlatformAdminPageOptions,
  type PlatformSystem,
  type RoleView,
} from '../../api/liveData';
import type { AsyncTask, PageResult } from '../../api/types';
import type { Navigate } from '../../app/app';
import { shellState } from '../../app/state';
import { createButton, createElement, createTraceLine, type ChildNodeValue } from '../../shared/components';
import { requestConfirmation, requestFormInput, requestTextInput } from '../../shared/dialogs';
import { createFilterBar } from '../../shared/filters';
import { renderStatusPill, type StatusTone } from '../../shared/status';

export function renderPlatformAdmin(navigate: Navigate): HTMLElement {
  let activeSection = 'platform-info';
  let latestData: PlatformAdminData | null = null;
  const pageOptions: PlatformAdminPageOptions = {};
  const content = createElement('div', { className: 'admin-content' }, createLoadingPanel('正在读取平台后台数据...'));
  const root = createElement('section', { className: 'admin-layout', dataset: { platformAdminShell: 'true' } }, content);
  const changePage = (key: keyof PlatformAdminPageOptions, pageNo: number) => {
    pageOptions[key] = Math.max(1, pageNo);
    reload();
  };
  const showSection = (sectionId: string) => {
    activeSection = sectionId;
    root.replaceChildren(createPlatformAdminSidebar(navigate, activeSection, showSection), content);
    if (latestData) {
      content.replaceChildren(...createPlatformAdminContent(latestData, reload, activeSection, changePage));
    }
  };
  const reload = () => {
    content.replaceChildren(createLoadingPanel('正在读取平台后台数据...'));
    void loadPlatformAdminData(pageOptions)
      .then((data) => {
        latestData = data;
        content.replaceChildren(...createPlatformAdminContent(data, reload, activeSection, changePage));
      })
      .catch((error) => content.replaceChildren(createErrorPanel(error)));
  };

  root.replaceChildren(createPlatformAdminSidebar(navigate, activeSection, showSection), content);
  reload();

  return root;
}

function createPlatformAdminSidebar(navigate: Navigate, activeSection: string, onSelect: (targetId: string) => void): HTMLElement {
  const backButton = createButton('返回平台首页', 'secondary', false);
  backButton.addEventListener('click', () => navigate('/platform'));
  return createElement(
    'aside',
    { className: 'module-sidebar admin-sidebar' },
    createElement('strong', {}, '平台后台'),
    backButton,
    createSidebarButton('平台信息', 'platform-info', activeSection === 'platform-info', onSelect),
    createSidebarButton('组织架构', 'platform-org', activeSection === 'platform-org', onSelect),
    createSidebarButton('系统生命周期', 'platform-system', activeSection === 'platform-system', onSelect),
    createSidebarButton('角色管理', 'platform-role', activeSection === 'platform-role', onSelect),
    createSidebarButton('仪表盘管理', 'platform-dashboard', activeSection === 'platform-dashboard', onSelect),
    createSidebarButton('配置管理', 'platform-config', activeSection === 'platform-config', onSelect),
    createSidebarButton('日志管理', 'platform-logs', activeSection === 'platform-logs', onSelect),
  );
}

function createPlatformAdminContent(
  data: PlatformAdminData,
  reload: () => void,
  activeSection: string,
  onPageChange: (key: keyof PlatformAdminPageOptions, pageNo: number) => void,
): HTMLElement[] {
  const warningPanel = createWarningPanel(data.warnings);
  const panels: Record<string, HTMLElement> = {
    'platform-info': createPlatformInfoPanel(data),
    'platform-org': createPlatformOrgPanel(data),
    'platform-system': createSystemLifecyclePanel(data.systems, reload, (pageNo) => onPageChange('systemsPageNo', pageNo)),
    'platform-role': createPlatformRolePanel(data.roles, reload, (pageNo) => onPageChange('rolesPageNo', pageNo)),
    'platform-dashboard': createPlatformDashboardPanel(data),
    'platform-config': createPlatformConfigPanel(data.identityProviders, data.modelAuthorizations, data.health?.traceId, reload, (pageNo) => onPageChange('modelAuthorizationsPageNo', pageNo)),
    'platform-logs': createPlatformLogPanel(data.logs, (pageNo) => onPageChange('logsPageNo', pageNo)),
  };
  return [
    createElement(
      'div',
      { className: 'page-heading' },
      createElement('h1', {}, '平台后台'),
      createElement('p', {}, '平台后台只处理平台信息、平台组织、平台角色、系统生命周期、统一认证、AI Agent 授权和平台日志。'),
    ),
    ...(warningPanel ? [warningPanel] : []),
    panels[activeSection] ?? panels['platform-info'],
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

function createPlatformOrgPanel(data: PlatformAdminData): HTMLElement {
  return createElement(
    'section',
    { id: 'platform-org', className: 'panel' },
    createElement('h2', {}, '平台组织架构'),
    createElement(
      'div',
      { className: 'simple-stack' },
      createElement('div', { className: 'list-line' }, createElement('span', {}, '当前账号'), createElement('strong', {}, shellState.account.displayName)),
      createElement('div', { className: 'list-line' }, createElement('span', {}, '平台角色数'), createElement('strong', {}, String(data.roles.total))),
      createElement('div', { className: 'list-line' }, createElement('span', {}, '企业 SSO'), createElement('strong', {}, String(data.identityProviders.length))),
    ),
  );
}

function createPlatformDashboardPanel(data: PlatformAdminData): HTMLElement {
  return createElement(
    'section',
    { id: 'platform-dashboard', className: 'panel' },
    createElement('h2', {}, '平台仪表盘'),
    createElement(
      'div',
      { className: 'metric-grid' },
      createMetric('系统数量', String(data.systems.total)),
      createMetric('平台角色', String(data.roles.total)),
      createMetric('企业 SSO', String(data.identityProviders.length)),
      createMetric('模型授权', String(data.modelAuthorizations.total)),
    ),
    data.health ? createTraceLine(data.health.traceId) : null,
  );
}

function createSystemLifecyclePanel(page: PageResult<PlatformSystem>, reload: () => void, onPageChange: (nextPage: number) => void): HTMLElement {
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
    createPagination(page, onPageChange),
  );
}

function createSystemLifecycleButton(system: PlatformSystem, reload: () => void): HTMLButtonElement {
  const action = system.status === 1 ? 'disable' : 'enable';
  const label = system.status === 1 ? '禁用' : '启用';
  const button = createButton(label, action === 'disable' ? 'ghost' : 'secondary', false);
  button.addEventListener('click', async () => {
    const reason = await lifecycleReason(label, system);
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

async function lifecycleReason(label: string, system: PlatformSystem): Promise<string | undefined> {
  return requestTextInput(`系统${label}`, `${label}「${system.systemName}」的原因`, label === '禁用' ? system.disabledReason ?? '' : '恢复系统使用');
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

function createPlatformRolePanel(page: PageResult<RoleView>, reload: () => void, onPageChange: (nextPage: number) => void): HTMLElement {
  const result = createElement('p', { className: 'field-error' }, '平台角色只控制平台后台和平台工作台能力，不直接授权系统业务数据。');
  const createRoleButton = createButton('新建平台角色', 'primary', false);
  createRoleButton.addEventListener('click', async () => {
    const values = await requestFormInput('新建平台角色', [
      { name: 'roleName', label: '平台角色名称' },
      { name: 'roleCode', label: '平台角色编码', defaultValue: `platform_role_${Date.now()}` },
    ], '创建');
    if (!values) {
      return;
    }
    createRoleButton.disabled = true;
    createRoleButton.textContent = '创建中...';
    try {
      const role = await createPlatformRole({
        roleName: values.roleName,
        roleCode: values.roleCode,
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
    createPagination(page, onPageChange),
  );
}

function createPlatformConfigPanel(
  providers: IdentityProviderView[],
  authorizations: PageResult<ModelAuthorizationView>,
  traceId?: string,
  reload?: () => void,
  onAuthorizationPageChange?: (nextPage: number) => void,
): HTMLElement {
  const ssoResult = createElement('p', { className: 'field-error' }, 'SSO 身份源支持创建、测试和发布；密钥使用 SecretRef，不在页面展示明文。');
  const agentResult = createElement('p', { className: 'field-error' }, 'AI Agent 授权创建后可供系统策略选择，默认按不外发和脱敏策略保存。');
  const createProviderButton = createButton('新增身份源', 'primary', false);
  const createAuthorizationButton = createButton('新增模型授权', 'primary', false);
  createProviderButton.addEventListener('click', async () => {
    const values = await requestFormInput('新增身份源', [
      { name: 'name', label: '身份源名称', defaultValue: '企业 OIDC' },
      { name: 'issuer', label: 'Issuer 地址', type: 'url', required: false },
      { name: 'clientId', label: 'Client ID', required: false },
      { name: 'secretRefId', label: 'SecretRef ID', required: false },
    ], '创建');
    if (!values) {
      return;
    }
    createProviderButton.disabled = true;
    createProviderButton.textContent = '创建中...';
    try {
      const provider = await createPlatformIdentityProvider({
        name: values.name,
        protocol: 'OIDC',
        issuer: values.issuer || undefined,
        clientId: values.clientId || undefined,
        secretRefId: values.secretRefId || undefined,
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
    const values = await requestFormInput('新增模型授权', [
      { name: 'authorizationCode', label: '授权编码', defaultValue: `model_auth_${Date.now()}` },
      { name: 'modelProvider', label: '模型供应商', defaultValue: 'LOCAL' },
      { name: 'modelName', label: '模型名称', defaultValue: 'local-model' },
    ], '创建');
    if (!values) {
      return;
    }
    createAuthorizationButton.disabled = true;
    createAuthorizationButton.textContent = '创建中...';
    try {
      const authorization = await createPlatformModelAuthorization({
        authorizationCode: values.authorizationCode,
        modelProvider: values.modelProvider || 'LOCAL',
        modelName: values.modelName || 'local-model',
      });
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
    {
      id: 'platform-config',
      className: 'panel',
      dataset: {
        productSurface: 'platform-config',
        platformConfigProviderCount: String(providers.length),
        platformConfigAuthorizationCount: String(authorizations.total),
      },
    },
    createElement('h2', {}, '配置管理'),
    createPlatformConfigSummary(providers, authorizations),
    createElement(
      'div',
      { className: 'split-grid platform-config-grid' },
      createElement(
        'div',
        { className: 'result-panel compact-product-panel', dataset: { platformConfigSsoPanel: 'true' } },
        createElement('div', { className: 'runtime-card-head' },
          createElement('div', {}, createElement('strong', {}, '企业 SSO'), createElement('p', {}, '维护身份源、测试回调和发布状态；密钥只显示 SecretRef 边界。')),
          renderStatusPill(providers.some((provider) => provider.status === 'ENABLED') ? '已有可用身份源' : '待发布', providers.some((provider) => provider.status === 'ENABLED') ? 'success' : 'warning'),
        ),
        createElement('div', { className: 'inline-actions' }, createProviderButton),
        ssoResult,
        providers.length === 0
          ? createElement('p', {}, '暂无身份源，请配置 OIDC、SAML 或企业自建身份源后发布。')
          : createElement(
              'div',
              { className: 'simple-stack compact-stack' },
              ...providers.slice(0, 5).map((provider) => createProviderLine(provider, reload, ssoResult)),
              providers.length > 5 ? createElement('small', {}, `已收起 ${providers.length - 5} 个身份源，翻页和完整维护仍由后台数据接口承接。`) : null,
            ),
      ),
      createElement(
        'div',
        { className: 'result-panel compact-product-panel', dataset: { platformConfigAgentPanel: 'true' } },
        createElement('div', { className: 'runtime-card-head' },
          createElement('div', {}, createElement('strong', {}, 'AI Agent 授权'), createElement('p', {}, '平台只管理模型授权；系统级 Agent 读写仍受系统策略、字段和数据范围控制。')),
          renderStatusPill(authorizations.total > 0 ? '可供系统选择' : '待授权', authorizations.total > 0 ? 'success' : 'warning'),
        ),
        createElement('div', { className: 'inline-actions' }, createAuthorizationButton),
        agentResult,
        authorizations.records.length === 0
          ? createElement('p', {}, '暂无模型授权，系统可在授权后使用平台模型或外部模型。')
          : createElement(
              'div',
              { className: 'simple-stack compact-stack' },
              ...authorizations.records.slice(0, 5).map((authorization) => createAuthorizationLine(authorization)),
              authorizations.total > authorizations.records.length
                ? createElement('small', {}, `第 ${authorizations.pageNo} 页 / 共 ${authorizations.total} 条授权，使用分页查看其余记录。`)
                : null,
            ),
        createPagination(authorizations, onAuthorizationPageChange),
      ),
    ),
    createOpsGovernancePanel(),
    traceId ? createTraceLine(traceId) : null,
  );
}

function createPlatformConfigSummary(providers: IdentityProviderView[], authorizations: PageResult<ModelAuthorizationView>): HTMLElement {
  const enabledProviders = providers.filter((provider) => provider.status === 'ENABLED').length;
  const activeAuthorizations = authorizations.records.filter((authorization) => authorization.status === 1).length;
  return createElement(
    'div',
    { className: 'config-surface-summary', dataset: { platformConfigSummary: 'true' } },
    createMetric('身份源', `${enabledProviders}/${providers.length} 已发布`),
    createMetric('模型授权', `${activeAuthorizations}/${authorizations.total} 启用`),
    createMetric('安全边界', 'SecretRef / 脱敏'),
    createMetric('系统使用', '由系统策略二次授权'),
  );
}

function createOpsGovernancePanel(): HTMLElement {
  const result = createElement(
    'p',
    {
      className: 'field-error',
      dataset: {
        opsResult: 'idle',
        opsDryRunBoundary: 'dangerous actions require dry-run or explicit task evidence',
      },
    },
    '运维动作会返回体检、任务、traceId 或 dry-run 边界，危险操作不会直接破坏数据。',
  );
  const deploymentList = createElement('div', { className: 'simple-stack', dataset: { opsDeploymentList: 'empty' } }, createElement('p', {}, '点击读取部署记录。'));
  const cacheList = createElement('div', { className: 'simple-stack', dataset: { opsCachePolicyList: 'empty' } }, createElement('p', {}, '点击读取 API 缓存策略。'));

  const healthButton = createOpsButton('平台体检', async () => {
    const health = await runPlatformHealthCheck('FULL');
    setHealthOpsResult(result, 'platform-health', health);
    result.textContent = `平台体检：${health.status}，风险 ${health.risks.length} 项，traceId=${health.traceId}`;
  }, 'platform-health');
  const flagButton = createOpsButton('更新功能开关', async () => {
    const flag = await updatePlatformFeatureFlag('flag_gray_publish');
    setFeatureFlagOpsResult(result, 'feature-flag', flag);
    result.textContent = `功能开关已保存：${flag.flagCode}，回滚版本=${flag.rollbackVersion}，traceId=${flag.traceId}`;
  }, 'feature-flag');
  const quotaButton = createOpsButton('更新容量配额', async () => {
    const quota = await updatePlatformQuota('quota_openapi');
    setQuotaOpsResult(result, 'quota', quota);
    result.textContent = `容量配额已保存：${quota.quotaType} ${quota.quotaUsed}/${quota.quotaLimit}，traceId=${quota.traceId}`;
  }, 'quota');
  const rateLimitButton = createOpsButton('更新限流策略', async () => {
    const policy = await updatePlatformRateLimitPolicy('rl_openapi_app');
    setRateLimitOpsResult(result, 'rate-limit', policy);
    result.textContent = `限流策略已保存：${policy.policyCode}，traceId=${policy.traceId}`;
  }, 'rate-limit');
  const backupButton = createOpsButton('创建备份任务', async () => {
    const task = await createPlatformBackupTask();
    setTaskOpsResult(result, 'backup', task, 'false');
    result.textContent = `备份任务已创建：${task.taskId} / ${task.status}，traceId=${task.traceId}`;
  }, 'backup');
  const restoreButton = createOpsButton('恢复演练', async () => {
    const task = await runPlatformRestoreDrill();
    setTaskOpsResult(result, 'restore-drill', task, 'true');
    result.textContent = `恢复演练任务已创建：${task.taskId} / rollback=${task.rollbackSupported}，traceId=${task.traceId}`;
  }, 'restore-drill');
  const archiveButton = createOpsButton('归档恢复申请', async () => {
    const task = await createPlatformArchiveRestoreRequest();
    setTaskOpsResult(result, 'archive-restore', task, 'true');
    result.textContent = `归档恢复任务已创建：${task.taskId} / rollback=${task.rollbackSupported}，traceId=${task.traceId}`;
  }, 'archive-restore');
  const deploymentsButton = createOpsButton('读取部署记录', async () => {
    const deployments = await loadPlatformDeployments();
    deploymentList.replaceChildren(...deployments.records.map(createDeploymentLine));
    deploymentList.dataset.opsDeploymentList = 'loaded';
    deploymentList.dataset.opsDeploymentCount = String(deployments.records.length);
    result.dataset.opsResult = 'deployments';
    result.dataset.opsDeploymentCount = String(deployments.total);
    result.textContent = `部署记录已读取：${deployments.total} 条`;
  }, 'deployments');
  const rollbackButton = createOpsButton('部署回滚演练', async () => {
    const deployments = await loadPlatformDeployments();
    const deploymentId = deployments.records[0]?.deploymentId ?? 'deploy_20260623_001';
    const task = await rollbackPlatformDeployment(deploymentId);
    setTaskOpsResult(result, 'deployment-rollback', task, 'true');
    result.dataset.opsDeploymentId = deploymentId;
    result.textContent = `部署回滚演练已创建：${task.taskId} / rollback=${task.rollbackSupported}，traceId=${task.traceId}`;
  }, 'deployment-rollback');
  const cacheButton = createOpsButton('读取缓存策略', async () => {
    const policies = await loadPlatformApiCachePolicy();
    cacheList.replaceChildren(...policies.map(createCachePolicyLine));
    cacheList.dataset.opsCachePolicyList = 'loaded';
    cacheList.dataset.opsCachePolicyCount = String(policies.length);
    result.dataset.opsResult = 'cache-read';
    result.dataset.opsCachePolicyCount = String(policies.length);
    result.textContent = `API 缓存策略已读取：${policies.length} 条`;
  }, 'cache-read');
  const cacheUpdateButton = createOpsButton('更新缓存策略', async () => {
    const policies = await updatePlatformApiCachePolicy();
    cacheList.replaceChildren(...policies.map(createCachePolicyLine));
    cacheList.dataset.opsCachePolicyList = 'updated';
    cacheList.dataset.opsCachePolicyCount = String(policies.length);
    result.dataset.opsResult = 'cache-update';
    result.dataset.opsTraceId = policies[0]?.traceId ?? '';
    result.dataset.opsAuditLogId = policies[0]?.auditLogId ?? '';
    result.dataset.opsCachePolicyCount = String(policies.length);
    result.textContent = `API 缓存策略已保存：${policies[0]?.policyCode ?? '-'}，traceId=${policies[0]?.traceId ?? '-'}`;
  }, 'cache-update');

  return createElement(
    'div',
    { id: 'platform-ops-governance', className: 'result-panel', dataset: { platformOpsGovernance: 'true' } },
    createElement('strong', {}, '上线保障与运维治理'),
    createElement('div', { className: 'inline-actions' },
      healthButton,
      flagButton,
      quotaButton,
      rateLimitButton,
      backupButton,
      restoreButton,
      archiveButton,
      deploymentsButton,
      rollbackButton,
      cacheButton,
      cacheUpdateButton,
    ),
    result,
    createElement('div', { className: 'split-grid' }, deploymentList, cacheList),
  );
}

function createOpsButton(label: string, action: () => Promise<void>, actionCode: string): HTMLButtonElement {
  const button = createButton(label, 'secondary', false);
  button.dataset.opsAction = actionCode;
  button.addEventListener('click', async () => {
    button.disabled = true;
    const original = button.textContent ?? label;
    button.textContent = `${label}中...`;
    button.dataset.opsState = 'running';
    try {
      await action();
      button.dataset.opsState = 'succeeded';
      button.textContent = label;
    } catch (error) {
      button.dataset.opsState = 'failed';
      button.dataset.opsError = error instanceof Error ? error.message : '运维动作失败。';
      button.textContent = `${label}失败`;
      button.title = error instanceof Error ? error.message : '运维动作失败。';
    } finally {
      button.disabled = false;
      if (button.textContent === `${label}失败`) {
        window.setTimeout(() => {
          button.textContent = original;
        }, 1800);
      }
    }
  });
  return button;
}

function setHealthOpsResult(result: HTMLElement, resultCode: string, health: OpsHealthCheck): void {
  result.dataset.opsResult = resultCode;
  result.dataset.opsStatus = health.status;
  result.dataset.opsTraceId = health.traceId;
  result.dataset.opsAuditLogId = health.auditLogId ?? '';
  result.dataset.opsRiskCount = String(health.risks.length);
}

function setFeatureFlagOpsResult(result: HTMLElement, resultCode: string, flag: OpsFeatureFlagView): void {
  result.dataset.opsResult = resultCode;
  result.dataset.opsTraceId = flag.traceId;
  result.dataset.opsAuditLogId = flag.auditLogId;
  result.dataset.opsRollbackVersion = flag.rollbackVersion;
}

function setQuotaOpsResult(result: HTMLElement, resultCode: string, quota: OpsQuotaView): void {
  result.dataset.opsResult = resultCode;
  result.dataset.opsTraceId = quota.traceId;
  result.dataset.opsAuditLogId = quota.auditLogId;
  result.dataset.opsQuotaLimit = String(quota.quotaLimit);
  result.dataset.opsQuotaUsed = String(quota.quotaUsed);
}

function setRateLimitOpsResult(result: HTMLElement, resultCode: string, policy: OpsRateLimitPolicyView): void {
  result.dataset.opsResult = resultCode;
  result.dataset.opsTraceId = policy.traceId;
  result.dataset.opsAuditLogId = policy.auditLogId;
  result.dataset.opsPolicyCode = policy.policyCode;
}

function setTaskOpsResult(result: HTMLElement, resultCode: string, task: AsyncTask, dryRun: string): void {
  result.dataset.opsResult = resultCode;
  result.dataset.opsTaskId = task.taskId;
  result.dataset.opsTaskStatus = task.status;
  result.dataset.opsTraceId = task.traceId;
  result.dataset.opsAuditLogId = task.auditLogId ?? '';
  result.dataset.opsRollbackSupported = String(task.rollbackSupported);
  result.dataset.opsDryRun = dryRun;
}

function createDeploymentLine(deployment: OpsDeploymentView): HTMLElement {
  return createElement(
    'div',
    { className: 'list-line', dataset: { opsDeploymentId: deployment.deploymentId, opsDeploymentStatus: deployment.status } },
    createElement('span', {}, `${deployment.deploymentNo} / ${deployment.envCode}`),
    renderStatusPill(deployment.status, deployment.status === 'STABLE' ? 'success' : 'warning'),
    createElement('small', {}, deployment.rollbackPlan),
  );
}

function createCachePolicyLine(policy: OpsApiCachePolicyView): HTMLElement {
  return createElement(
    'div',
    { className: 'list-line', dataset: { opsCachePolicyCode: policy.policyCode, opsCachePolicyStatus: String(policy.status), opsTraceId: policy.traceId ?? '' } },
    createElement('span', {}, `${policy.policyCode} / ${policy.cacheDomain}`),
    renderStatusPill(enableStatusText(policy.status), enableStatusTone(policy.status)),
    createElement('small', {}, policy.keyRule),
  );
}

function createPlatformLogPanel(page: PageResult<AuditLogView>, onPageChange: (nextPage: number) => void): HTMLElement {
  return createElement(
    'section',
    {
      id: 'platform-logs',
      className: 'panel',
      dataset: {
        platformLogsPanel: 'true',
        productSurface: 'platform-logs',
        logCount: String(page.records.length),
        platformLogSuccessCount: String(page.records.filter((log) => log.result === 'SUCCESS').length),
        platformLogFailureCount: String(page.records.filter((log) => log.result !== 'SUCCESS').length),
      },
    },
    createElement('h2', {}, '日志管理'),
    createElement(
      'div',
      { className: 'config-surface-summary', dataset: { platformLogSummary: 'true' } },
      createMetric('本页日志', String(page.records.length)),
      createMetric('成功', String(page.records.filter((log) => log.result === 'SUCCESS').length)),
      createMetric('需要关注', String(page.records.filter((log) => log.result !== 'SUCCESS').length)),
      createMetric('总数', String(page.total)),
    ),
    createFilterBar(['日志类型', '账号', '系统', '结果', 'traceId', '时间范围']),
    createTable(
      ['序号', '类型', '操作人', '业务动作', '对象', '结果', '追踪', '时间'],
      page.records.map((log, index) => [
        index + 1,
        log.logType,
        log.operator || '-',
        createElement('span', {}, createElement('strong', {}, auditActionLabel(log.action)), createElement('small', {}, log.action)),
        createElement('span', {}, objectLabel(log.objectType, log.objectId)),
        renderStatusPill(log.result, log.result === 'SUCCESS' ? 'success' : 'warning'),
        createTraceChip(log.traceId),
        formatTime(log.createdAt),
      ]),
      '暂无平台日志。',
    ),
    createPagination(page, onPageChange),
  );
}

function createProviderLine(provider: IdentityProviderView, reload: (() => void) | undefined, result: HTMLElement): HTMLElement {
  const testButton = createButton('测试', 'secondary', false);
  const publishButton = createButton('发布', 'primary', provider.status === 'ENABLED', provider.status === 'ENABLED' ? '身份源已发布。' : undefined);
  testButton.addEventListener('click', async () => {
    const values = await requestFormInput('测试身份源', [
      { name: 'redirectUri', label: '测试回调地址', defaultValue: `${window.location.origin}/sso/callback`, type: 'url' },
      { name: 'testLoginName', label: '测试登录名', defaultValue: shellState.account.displayName },
    ], '测试');
    if (!values) {
      return;
    }
    testButton.disabled = true;
    testButton.textContent = '测试中...';
    try {
      const tested = await testPlatformIdentityProvider(provider.providerId, values.redirectUri, values.testLoginName || shellState.account.displayName);
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
    if (publishButton.disabled || !(await requestConfirmation('发布身份源', `确认发布身份源「${provider.name}」吗？`, '发布'))) {
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
    { className: 'list-line config-compact-line', dataset: { identityProviderId: provider.providerId, identityProviderStatus: provider.status } },
    createElement('span', {}, createElement('strong', {}, provider.name), createElement('small', {}, `${provider.protocol} / ${shortToken(provider.providerId)}`)),
    renderStatusPill(provider.status || 'UNKNOWN', provider.status === 'ENABLED' ? 'success' : 'warning'),
    createElement('div', { className: 'row-actions' }, testButton, publishButton),
  );
}

function createAuthorizationLine(authorization: ModelAuthorizationView): HTMLElement {
  return createElement(
    'div',
    { className: 'list-line config-compact-line', dataset: { modelAuthorizationId: authorization.authorizationId, modelAuthorizationStatus: String(authorization.status) } },
    createElement('span', {}, createElement('strong', {}, authorization.modelName), createElement('small', {}, `${authorization.modelProvider} / ${shortToken(authorization.authorizationCode)}`)),
    renderStatusPill(enableStatusText(authorization.status), enableStatusTone(authorization.status)),
  );
}

function auditActionLabel(action: string): string {
  if (action.includes('health-check')) {
    return '运行体检';
  }
  if (action.includes('logs.search')) {
    return '查询日志';
  }
  if (action.includes('ops.backup')) {
    return '创建备份任务';
  }
  if (action.includes('ops.restore')) {
    return '恢复演练';
  }
  if (action.includes('ops.rollback')) {
    return '部署回滚演练';
  }
  if (action.includes('systems') && action.startsWith('DELETE')) {
    return '清理测试系统';
  }
  if (action.startsWith('POST.')) {
    return '提交配置';
  }
  if (action.startsWith('PATCH.')) {
    return '更新配置';
  }
  if (action.startsWith('GET.')) {
    return '读取数据';
  }
  return action;
}

function objectLabel(objectType?: string, objectId?: string): string {
  if (!objectType && !objectId) {
    return '-';
  }
  return `${objectType || '-'} / ${shortToken(objectId || '-')}`;
}

function createTraceChip(traceId: string): HTMLElement {
  return createElement('code', { className: 'trace-chip', title: traceId }, shortToken(traceId));
}

function shortToken(value: string): string {
  if (value.length <= 24) {
    return value;
  }
  return `${value.slice(0, 10)}...${value.slice(-8)}`;
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

function createSidebarButton(label: string, targetId: string, active = false, onSelect?: (targetId: string) => void): HTMLButtonElement {
  const button = createElement('button', { className: active ? 'sidebar-item active' : 'sidebar-item', dataset: { adminSection: targetId } }, label);
  button.addEventListener('click', () => {
    onSelect?.(targetId);
  });
  return button;
}

function createPagination<T>(page: PageResult<T>, onPageChange?: (nextPage: number) => void): HTMLElement {
  const previousButton = createButton('上一页', 'ghost', page.pageNo <= 1 || !onPageChange, onPageChange ? '已经是第一页' : '当前后台聚合列表不支持翻页。');
  const nextButton = createButton('下一页', 'ghost', !page.hasNext || !onPageChange, onPageChange ? '没有更多数据' : '当前后台聚合列表不支持翻页。');
  previousButton.addEventListener('click', () => onPageChange?.(page.pageNo - 1));
  nextButton.addEventListener('click', () => onPageChange?.(page.pageNo + 1));
  return createElement(
    'footer',
    { className: 'pagination' },
    createElement('span', {}, `第 ${page.pageNo} 页，每页 ${page.pageSize} 条，共 ${page.total} 条`),
    previousButton,
    nextButton,
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
