import {
  createPlatformAuthorizationRequest,
  createPlatformFlowDraft,
  createPlatformSystem,
  loadPlatformAuthorizations,
  loadPlatformFlows,
  loadPlatformMessages,
  loadPlatformTodos,
  markPlatformMessageRead,
  platformMessageTargetToPath,
  platformTodoTargetToPath,
  runPlatformAuthorizationAction,
  runPlatformFlowAction,
  runPlatformHealthCheck,
  updateCurrentPassword,
  type MessageCard,
  type PlatformAuthorizationActionFeedback,
  type PlatformAuthorizationView,
  type PlatformFlowRunFeedback,
  type PlatformFlowView,
  type TodoRow,
  type TodoSearchResult,
} from '../../api/liveData';
import type { PageResult } from '../../api/types';
import type { Navigate } from '../../app/app';
import { canEnterPlatformAdmin, shellState, switchToSystem } from '../../app/state';
import { renderPlatformAdmin } from '../platform-admin/platformAdmin';
import { createCommandCenterButton } from '../command-center/commandCenter';
import { createButton, createElement } from '../../shared/components';
import { requestFormInput } from '../../shared/dialogs';
import { createFilterBar } from '../../shared/filters';
import { renderStatusPill } from '../../shared/status';

export function renderPlatformShell(route: string, navigate: Navigate): HTMLElement {
  if (route === '/platform/admin' && canEnterPlatformAdmin()) {
    return createElement('div', { className: 'admin-shell admin-shell-standalone', dataset: { platformAdminStandalone: 'true' } }, renderPlatformAdmin(navigate));
  }
  return createElement(
    'div',
    { className: 'workspace-shell', dataset: { platformShell: 'workbench' } },
    createPlatformHeader(navigate),
    createElement('main', { className: 'workspace-main' }, createPlatformWorkbench(normalizePlatformRoute(route), navigate)),
  );
}

function normalizePlatformRoute(route: string): string {
  return route === '/platform/dashboard' ? '/platform' : route;
}

function createPlatformHeader(navigate: Navigate): HTMLElement {
  const canManagePlatform = canEnterPlatformAdmin();
  const adminButton = canManagePlatform ? createNavButton('\u5e73\u53f0\u540e\u53f0', '/platform/admin', navigate, { platformAuxItem: 'admin' }) : null;
  return createElement(
    'header',
    { className: 'workspace-header platform-workspace-header', dataset: { platformHeader: 'true', platformNavModel: 'temp-flow-p1-p2-p3-p5' } },
    createElement('div', { className: 'brand' }, 'unexamine'),
    createElement(
      'nav',
      { className: 'main-nav', dataset: { platformPrimaryNav: 'true' } },
      createNavButton('\u5de5\u4f5c\u53f0', '/platform', navigate, { platformNavItem: 'dashboard' }),
      createNavButton('Flow', '/platform/flow', navigate, { platformNavItem: 'flow' }),
      createNavButton('\u5e94\u7528', '/platform/apps', navigate, { platformNavItem: 'apps' }),
      createNavButton('\u5de5\u4f5c', '/platform/work', navigate, { platformNavItem: 'work' }),
    ),
    createElement(
      'div',
      { className: 'header-actions', dataset: { platformAuxNav: 'true' } },
      createCommandCenterButton(navigate),
      createNavButton('AI', '/platform/ai', navigate, { platformAuxItem: 'ai' }),
      createNavButton('\u7cfb\u7edf\u5165\u53e3', '/platform', navigate, { platformAuxItem: 'system-entry' }),
      createNavButton('\u5f85\u529e', '/platform/todos', navigate, { platformAuxItem: 'todos' }),
      createNavButton('\u6d88\u606f', '/platform/messages', navigate, { platformAuxItem: 'messages' }),
      adminButton,
      createNavButton(shellState.account.displayName, '/platform/profile', navigate, { platformAuxItem: 'profile' }),
    ),
  );
}

function createPlatformWorkbench(route: string, navigate: Navigate): HTMLElement {
  if (route === '/platform/todos') {
    return createPlatformTodoPage(navigate);
  }
  if (route === '/platform/messages') {
    return createPlatformMessagePage(navigate);
  }
  if (route === '/platform/apps') {
    return createPlatformAppsPage(navigate);
  }
  if (route === '/platform/flow') {
    return createPlatformFlowPage(navigate);
  }
  if (route === '/platform/ai') {
    return createPlatformAiPage(navigate);
  }
  if (route === '/platform/work') {
    return createPlatformWorkPage(navigate);
  }
  if (route === '/platform/profile') {
    return createProfilePage(navigate);
  }

  const targetSystemId = platformTargetSystemId(route);
  const canManagePlatform = canEnterPlatformAdmin();
  const createSystemButton = canManagePlatform ? createButton('\u521b\u5efa\u7cfb\u7edf', 'primary', false) : null;
  const adminButton = canManagePlatform ? createButton('\u5e73\u53f0\u540e\u53f0', 'secondary', false) : null;
  if (createSystemButton) {
    createSystemButton.dataset.platformCreateSystemAction = 'true';
  }
  if (adminButton) {
    adminButton.dataset.platformAdminEntry = 'true';
  }
  const headingCopy = canManagePlatform
    ? '\u5e73\u53f0\u7ba1\u7406\u5458\u5728\u8fd9\u91cc\u67e5\u770b\u5e73\u53f0\u6982\u89c8\u3001\u521b\u5efa\u7cfb\u7edf\uff0c\u5e76\u901a\u8fc7\u7cfb\u7edf\u5165\u53e3\u8fdb\u5165\u5177\u4f53\u7cfb\u7edf\u7ee7\u7eed\u914d\u7f6e\u3002'
    : '\u8fd9\u91cc\u662f\u5e73\u53f0\u5de5\u4f5c\u53f0\u3002\u8fdb\u5165\u7cfb\u7edf\u4e1a\u52a1\u524d\uff0c\u5fc5\u987b\u5148\u4f7f\u7528\u7cfb\u7edf\u5165\u53e3\u53d6\u5f97\u7cfb\u7edf\u6210\u5458\u548c\u6743\u9650\u4e0a\u4e0b\u6587\u3002';

  if (createSystemButton) {
    createSystemButton.addEventListener('click', () => {
      void createSystemFromDialog(navigate, createSystemButton);
    });
  }
  adminButton?.addEventListener('click', () => navigate('/platform/admin'));

  return createElement(
    'section',
    {
      className: 'page-grid platform-home-grid',
      dataset: {
        platformWorkbench: 'true',
        productSurface: 'platform-workbench',
        platformWorkbenchCanManage: String(canManagePlatform),
        platformWorkbenchSystemCount: String(shellState.availableSystems.length),
      },
    },
    createElement(
      'div',
      { className: 'page-heading' },
      createElement('h1', {}, '\u5e73\u53f0\u5de5\u4f5c\u53f0'),
      createElement('p', {}, headingCopy),
      createElement('div', { className: 'inline-actions' }, createSystemButton, adminButton),
    ),
    createPlatformOverviewPanel(navigate),
    createSystemSwitchPanel(navigate, targetSystemId),
  );
}

async function createSystemFromDialog(navigate: Navigate, trigger: HTMLButtonElement): Promise<void> {
  const defaultSystemCode = `sys_${Date.now().toString(36)}`;
  const values = await requestFormInput('创建系统', [
    { name: 'systemName', label: '系统名称' },
    { name: 'systemCode', label: '系统编码', defaultValue: defaultSystemCode },
    { name: 'tenantMode', label: '租户模式：1 多租户，0 单租户', defaultValue: '1' },
  ], '创建并进入系统后台');
  if (!values) {
    return;
  }
  trigger.disabled = true;
  trigger.textContent = '创建中...';
  try {
    const system = await createPlatformSystem({
      systemName: values.systemName,
      systemCode: values.systemCode,
      tenantMode: values.tenantMode === '0' ? 0 : 1,
    });
    await switchToSystem(system.systemId, undefined, 'platform create system enter');
    navigate(`/systems/${system.systemId}/admin`);
  } catch (error) {
    trigger.title = error instanceof Error ? error.message : '创建系统失败。';
  } finally {
    trigger.disabled = false;
    trigger.textContent = '创建系统';
  }
}

function createPlatformTodoPage(navigate: Navigate): HTMLElement {
  const panel = createElement('section', { className: 'panel', dataset: { platformTodoPanel: 'true' } }, createElement('strong', {}, '正在读取平台待办...'));
  loadPlatformTodoPanel(panel, navigate, 1);
  return createElement(
    'section',
    { className: 'page-grid', dataset: { platformTodosPage: 'true' } },
    createElement('div', { className: 'page-heading' }, createElement('h1', {}, '平台待办'), createElement('p', {}, '平台授权、系统切换、任务和审批类事项集中处理。')),
    panel,
  );
}

function createPlatformMessagePage(navigate: Navigate): HTMLElement {
  const panel = createElement('section', { className: 'panel message-stream', dataset: { platformMessagePanel: 'true' } }, createElement('strong', {}, '正在读取平台消息...'));
  loadPlatformMessagePanel(panel, navigate, 1);
  return createElement(
    'section',
    { className: 'page-grid', dataset: { platformMessagesPage: 'true' } },
    createElement('div', { className: 'page-heading' }, createElement('h1', {}, '平台消息'), createElement('p', {}, '消息按时间流展示。涉及系统业务的数据必须先完成系统切换。')),
    panel,
  );
}

function createPlatformAppsPage(_navigate: Navigate): HTMLElement {
  const canManagePlatform = canEnterPlatformAdmin();
  const actionPanel = createPlatformAuthorizationResultPanel();
  const detailPanel = createElement('section', {
    className: 'panel platform-workbench-detail',
    dataset: { platformAuthorizationDetailPanel: 'loading', requestId: 'loading', authorizationChangeId: 'loading' },
  }, createElement('h2', {}, '\u6388\u6743\u8be6\u60c5'), createElement('p', {}, '\u6b63\u5728\u8bfb\u53d6\u5e73\u53f0\u6388\u6743...'));
  const tableBody = createElement('tbody', {}, createLoadingTableRow(6, '\u6b63\u5728\u8bfb\u53d6\u5e73\u53f0\u6388\u6743...'));
  const countLabel = createElement('span', {}, '\u8bfb\u53d6\u4e2d');
  const reload = () => loadPlatformAuthorizationWorkbench(tableBody, detailPanel, countLabel, actionPanel);
  const requestButton = createButton(canManagePlatform ? '\u65b0\u589e\u5e73\u53f0\u6388\u6743' : '\u53d1\u8d77\u6388\u6743\u7533\u8bf7', 'primary', false);
  requestButton.dataset.platformAuthorizationRequest = 'create';
  requestButton.addEventListener('click', async () => {
    requestButton.disabled = true;
    requestButton.textContent = '\u63d0\u4ea4\u4e2d...';
    try {
      const created = await createPlatformAuthorizationRequest();
      const row = toPlatformAuthorizationRow(created);
      renderPlatformAuthorizationDetailPanel(detailPanel, row);
      renderPlatformAuthorizationViewResult(actionPanel, row, 'request');
      await reload();
    } catch (error) {
      renderPlatformActionError(actionPanel, 'authorization-error', error);
    } finally {
      requestButton.disabled = false;
      requestButton.textContent = canManagePlatform ? '\u65b0\u589e\u5e73\u53f0\u6388\u6743' : '\u53d1\u8d77\u6388\u6743\u7533\u8bf7';
    }
  });
  const page = createElement(
    'section',
    {
      className: 'page-grid platform-module-page platform-application-workbench',
      dataset: {
        platformAppsPage: 'true',
        platformAppsSeparatedFromSystemEntry: 'true',
        platformAppsSystemEntryCount: '0',
        platformApplicationRowCount: '0',
        platformAuthorizationWorkbench: 'true',
      },
    },
    createElement(
      'div',
      { className: 'page-heading' },
      createElement('h1', {}, '\u5e94\u7528'),
      createElement('p', {}, '\u5e94\u7528\u627f\u63a5\u5e73\u53f0\u6388\u6743\u3001OpenAPI\u3001Webhook\u3001AI \u548c Flow \u5f00\u653e\u80fd\u529b\uff1b\u5b83\u662f\u6388\u6743\u4e0e\u63a5\u5165\u5de5\u4f5c\u53f0\uff0c\u4e0d\u662f\u7cfb\u7edf\u4e1a\u52a1\u5165\u53e3\u3002'),
    ),
    createElement(
      'section',
      { className: 'panel platform-boundary-panel', dataset: { platformApplicationBoundary: 'true' } },
      createElement('h2', {}, '\u6388\u6743\u8fb9\u754c'),
      createElement('div', { className: 'metric-grid' },
        createMetric('\u6388\u6743\u5bf9\u8c61', countLabel.textContent ?? '0'),
        createMetric('\u5e73\u53f0\u5f85\u529e/\u6d88\u606f', '\u7533\u8bf7\u4e0e\u5ba1\u6279\u53cd\u9988'),
        createMetric('\u7cfb\u7edf\u4e1a\u52a1\u6570\u636e', '\u4e0d\u5728\u6b64\u9875\u8bfb\u5199'),
      ),
      createElement('p', {}, '\u5e73\u53f0\u6388\u6743\u53ea\u751f\u6210 requestId\u3001authorizationChangeId\u3001\u5e73\u53f0\u5f85\u529e\u3001\u5e73\u53f0\u6d88\u606f\u548c\u5e73\u53f0\u65e5\u5fd7\u3002'),
      createElement('div', { className: 'inline-actions' }, requestButton),
    ),
    createElement(
      'section',
      { className: 'panel platform-list-panel', dataset: { platformApplicationList: 'true', platformAuthorizationList: 'true' } },
      createElement('div', { className: 'runtime-card-head' },
        createElement('h2', {}, '\u5df2\u6388\u6743\u7cfb\u7edf\u4e0e\u5e94\u7528'),
        countLabel,
      ),
      createFilterBar(['\u7cfb\u7edf/\u79df\u6237', '\u6388\u6743\u53f7', '\u72b6\u6001', '\u5230\u671f\u65f6\u95f4']),
      createElement(
        'div',
        { className: 'table-shell platform-workbench-table-shell' },
        createElement(
          'table',
          { className: 'data-table platform-application-table platform-authorization-table' },
          createElement('thead', {}, createElement('tr', {},
            createElement('th', {}, '\u6388\u6743\u5bf9\u8c61'),
            createElement('th', {}, '\u79df\u6237/\u8bf7\u6c42\u4eba'),
            createElement('th', {}, '\u6388\u6743\u6a21\u5757'),
            createElement('th', {}, '\u5230\u671f/\u9694\u79bb'),
            createElement('th', {}, '\u72b6\u6001'),
            createElement('th', {}, '\u64cd\u4f5c'),
          )),
          tableBody,
        ),
      ),
    ),
    detailPanel,
    actionPanel,
  );
  void reload();
  return page;
}

function createPlatformFlowPage(navigate: Navigate): HTMLElement {
  const resultPanel = createPlatformFlowResultPanel();
  const detailPanel = createElement('section', {
    className: 'panel platform-workbench-detail',
    dataset: { platformFlowDetailPanel: 'loading', platformFlowRunBatch: 'loading', platformFlowTraceId: 'loading' },
  }, createElement('h2', {}, 'Flow \u8be6\u60c5'), createElement('p', {}, '\u6b63\u5728\u8bfb\u53d6\u5e73\u53f0 Flow...'));
  const tableBody = createElement('tbody', {}, createLoadingTableRow(7, '\u6b63\u5728\u8bfb\u53d6\u5e73\u53f0 Flow...'));
  const countLabel = createElement('span', {}, '\u8bfb\u53d6\u4e2d');
  const reload = () => loadPlatformFlowWorkbench(tableBody, detailPanel, countLabel, resultPanel);
  const healthButton = createButton('\u8fd0\u884c\u5e73\u53f0 Flow \u4f53\u68c0', 'secondary', false);
  healthButton.dataset.platformFlowRunBatch = 'health-check';
  healthButton.addEventListener('click', async () => {
    healthButton.disabled = true;
    healthButton.textContent = '\u4f53\u68c0\u4e2d...';
    try {
      const result = await runPlatformHealthCheck('FLOW');
      resultPanel.dataset.platformFlowResult = 'health-check';
      resultPanel.replaceChildren(
        createElement('h2', {}, '\u4f53\u68c0\u7ed3\u679c'),
        createElement('div', { className: 'list-line' }, createElement('span', {}, '\u72b6\u6001'), renderStatusPill(result.status, result.status === 'PASS' || result.status === 'UP' ? 'success' : 'warning')),
        createElement('div', { className: 'list-line' }, createElement('span', {}, '\u68c0\u67e5\u9879'), createElement('strong', {}, String(result.checks.length))),
        createElement('div', { className: 'list-line' }, createElement('span', {}, '\u98ce\u9669'), createElement('strong', {}, String(result.risks.length))),
        createElement('code', { dataset: { platformFlowTraceId: result.traceId, platformFlowAuditLogId: String(result.auditLogId ?? '') } }, 'traceId=' + result.traceId + ' auditLogId=' + result.auditLogId),
      );
    } catch (error) {
      renderPlatformActionError(resultPanel, 'health-error', error);
    } finally {
      healthButton.disabled = false;
      healthButton.textContent = '\u8fd0\u884c\u5e73\u53f0 Flow \u4f53\u68c0';
    }
  });
  const createFlowButton = createButton('\u65b0\u5efa\u5e73\u53f0 Flow', 'primary', !canEnterPlatformAdmin(), canEnterPlatformAdmin() ? undefined : '\u5f53\u524d\u8d26\u53f7\u53ea\u80fd\u67e5\u770b\u548c\u7533\u8bf7\uff0c\u65e0\u5e73\u53f0\u540e\u53f0\u914d\u7f6e\u6743\u9650\u3002');
  createFlowButton.dataset.platformFlowCreateAction = 'true';
  createFlowButton.addEventListener('click', async () => {
    if (createFlowButton.disabled) { return; }
    createFlowButton.disabled = true;
    createFlowButton.textContent = '\u4fdd\u5b58\u4e2d...';
    try {
      const created = await createPlatformFlowDraft();
      const row = toPlatformFlowRow(created);
      renderPlatformFlowDetailPanel(detailPanel, row);
      renderPlatformFlowViewResult(resultPanel, row, 'draft');
      await reload();
    } catch (error) {
      renderPlatformActionError(resultPanel, 'flow-create-error', error);
    } finally {
      createFlowButton.disabled = !canEnterPlatformAdmin();
      createFlowButton.textContent = '\u65b0\u5efa\u5e73\u53f0 Flow';
    }
  });
  const logButton = createButton('\u67e5\u770b\u5e73\u53f0\u65e5\u5fd7', 'ghost', !canEnterPlatformAdmin(), canEnterPlatformAdmin() ? undefined : '\u5f53\u524d\u8d26\u53f7\u6ca1\u6709\u5e73\u53f0\u540e\u53f0\u6743\u9650\u3002');
  logButton.addEventListener('click', () => navigate('/platform/admin'));
  const page = createElement(
    'section',
    { className: 'page-grid platform-module-page platform-flow-workbench', dataset: { platformFlowPage: 'true', platformFlowSeparatedFromApplication: 'true', platformFlowWorkbench: 'true', platformFlowRowCount: '0' } },
    createElement('div', { className: 'page-heading' }, createElement('h1', {}, 'Flow'), createElement('p', {}, '\u5e73\u53f0 Flow \u7528\u4e8e\u5e73\u53f0\u6388\u6743\u3001\u5e94\u7528\u63a5\u5165\u3001\u8fd0\u7ef4\u4efb\u52a1\u548c\u5931\u8d25\u8865\u507f\u7f16\u6392\uff1b\u5b83\u53ea\u751f\u6210\u5e73\u53f0\u4efb\u52a1\u3001\u5e73\u53f0\u6d88\u606f\u3001\u5e73\u53f0\u65e5\u5fd7\u548c traceId\u3002')),
    createElement(
      'section',
      { className: 'panel', dataset: { platformFlowOverview: 'true' } },
      createElement('h2', {}, '\u5e73\u53f0 Flow \u6982\u89c8'),
      createElement('div', { className: 'metric-grid' }, createMetric('\u89e6\u53d1\u6e90', '\u5e73\u53f0\u4efb\u52a1 / \u6388\u6743 / \u5e94\u7528\u56de\u8c03'), createMetric('\u8fd0\u884c\u7ed3\u679c', '\u6279\u6b21 / \u65e5\u5fd7 / traceId'), createMetric('\u7cfb\u7edf\u8fb9\u754c', '\u4e0d\u76f4\u63a5\u5199\u4e1a\u52a1\u6570\u636e')),
      createElement('div', { className: 'inline-actions' }, createFlowButton, healthButton, logButton),
    ),
    createElement(
      'section',
      { className: 'panel platform-list-panel', dataset: { platformFlowList: 'true' } },
      createElement('div', { className: 'runtime-card-head' }, createElement('h2', {}, 'Flow \u5217\u8868'), countLabel),
      createFilterBar(['\u89e6\u53d1\u6e90', '\u5f71\u54cd\u7cfb\u7edf', '\u8fd0\u884c\u72b6\u6001', '\u8d1f\u8d23\u4eba']),
      createElement(
        'div',
        { className: 'table-shell platform-workbench-table-shell' },
        createElement(
          'table',
          { className: 'data-table platform-flow-table' },
          createElement('thead', {}, createElement('tr', {},
            createElement('th', {}, 'Flow'),
            createElement('th', {}, '\u89e6\u53d1\u6e90'),
            createElement('th', {}, '\u5f71\u54cd\u7cfb\u7edf'),
            createElement('th', {}, '\u6700\u8fd1\u8fd0\u884c'),
            createElement('th', {}, '\u5931\u8d25\u53cd\u9988'),
            createElement('th', {}, '\u5ba1\u8ba1'),
            createElement('th', {}, '\u64cd\u4f5c'),
          )),
          tableBody,
        ),
      ),
    ),
    detailPanel,
    resultPanel,
  );
  void reload();
  return page;
}
function createPlatformAiPage(navigate: Navigate): HTMLElement {
  const canManagePlatform = canEnterPlatformAdmin();
  const healthPanel = createElement(
    'section',
    { className: 'panel', dataset: { platformAiHealthPanel: 'true' } },
    createElement('h2', {}, 'AI 能力体检'),
    createElement('p', {}, '从这里检查平台 AI、任务、消息、日志和模型授权链路；业务系统数据仍然通过系统工作台进入。'),
  );
  const healthButton = createButton('运行 AI 体检', 'secondary', false);
  const configButton = createButton('模型授权配置', 'ghost', !canManagePlatform, canManagePlatform ? undefined : '当前账号没有平台后台权限。');
  healthButton.addEventListener('click', async () => {
    healthButton.disabled = true;
    healthButton.textContent = '体检中...';
    try {
      const result = await runPlatformHealthCheck('AI');
      healthPanel.replaceChildren(
        createElement('h2', {}, 'AI 能力体检'),
        createElement('div', { className: 'list-line' }, createElement('span', {}, '状态'), renderStatusPill(result.status, result.status === 'PASS' || result.status === 'UP' ? 'success' : 'warning')),
        createElement('div', { className: 'list-line' }, createElement('span', {}, '检查项'), createElement('strong', {}, String(result.checks.length))),
        createElement('div', { className: 'list-line' }, createElement('span', {}, '风险'), createElement('strong', {}, String(result.risks.length))),
        createElement('code', {}, `traceId=${result.traceId} auditLogId=${result.auditLogId}`),
      );
    } catch (error) {
      healthPanel.replaceChildren(createElement('h2', {}, 'AI 能力体检'), createElement('p', {}, error instanceof Error ? error.message : 'AI 体检失败，请稍后重试。'));
    } finally {
      healthButton.disabled = false;
      healthButton.textContent = '运行 AI 体检';
    }
  });
  configButton.addEventListener('click', () => navigate('/platform/admin'));

  return createElement(
    'section',
    { className: 'page-grid', dataset: { platformAiPage: 'true' } },
    createElement(
      'div',
      { className: 'page-heading' },
      createElement('h1', {}, '平台 AI'),
      createElement('p', {}, '平台 AI 负责跨系统的授权、任务、消息、日志、健康检查和能力编排，不直接绕过系统权限读写业务数据。'),
      createElement('div', { className: 'inline-actions' }, healthButton, configButton),
    ),
    createElement(
      'section',
      { className: 'panel' },
      createElement('h2', {}, '当前账号边界'),
      createElement('div', { className: 'metric-grid' },
        createMetric('平台角色', shellState.account.platformRoles.join(' / ') || '未授权'),
        createMetric('可进入系统', String(shellState.availableSystems.filter((system) => system.enabled).length)),
        createMetric('后台权限', canManagePlatform ? '可配置' : '只读入口'),
      ),
      createElement('p', {}, canManagePlatform
        ? '你可以进入平台后台维护模型授权、系统成员、审计和全局配置。'
        : '你可以使用已授权的 AI 入口和系统工作台；模型授权和全局配置由平台管理员处理。'),
    ),
    healthPanel,
  );
}

function createPlatformWorkPage(navigate: Navigate): HTMLElement {
  const todoButton = createButton('\u67e5\u770b\u5e73\u53f0\u5f85\u529e', 'secondary', false);
  const messageButton = createButton('\u67e5\u770b\u5e73\u53f0\u6d88\u606f', 'secondary', false);
  const commandButton = createButton('\u6253\u5f00\u547d\u4ee4\u4e2d\u5fc3', 'ghost', false);
  todoButton.addEventListener('click', () => navigate('/platform/todos'));
  messageButton.addEventListener('click', () => navigate('/platform/messages'));
  commandButton.addEventListener('click', () => navigate('/platform'));
  return createElement(
    'section',
    { className: 'page-grid platform-module-page', dataset: { platformWorkPage: 'true' } },
    createElement('div', { className: 'page-heading' }, createElement('h1', {}, '\u5de5\u4f5c'), createElement('p', {}, '\u5e73\u53f0\u5de5\u4f5c\u627f\u63a5\u5e73\u53f0\u4efb\u52a1\u3001\u5f85\u529e\u3001\u6d88\u606f\u548c\u534f\u4f5c\u7ed3\u679c\uff1b\u7cfb\u7edf\u4e1a\u52a1\u4efb\u52a1\u4ecd\u5728\u8fdb\u5165\u7cfb\u7edf\u540e\u5904\u7406\u3002')),
    createElement(
      'section',
      { className: 'panel', dataset: { platformWorkBoundary: 'true' } },
      createElement('h2', {}, '\u5e73\u53f0\u5de5\u4f5c\u8303\u56f4'),
      createElement('div', { className: 'metric-grid' },
        createMetric('\u5e73\u53f0\u5f85\u529e', '\u6388\u6743 / \u4efb\u52a1 / \u5ba1\u6279'),
        createMetric('\u5e73\u53f0\u6d88\u606f', '\u901a\u77e5 / \u7ed3\u679c / \u65e5\u5fd7'),
        createMetric('\u4e1a\u52a1\u4efb\u52a1', '\u8fdb\u5165\u7cfb\u7edf\u540e\u5904\u7406'),
      ),
      createElement('div', { className: 'inline-actions' }, todoButton, messageButton, commandButton),
    ),
    createElement(
      'section',
      { className: 'panel' },
      createElement('h2', {}, '\u5de5\u4f5c\u53f0\u5173\u7cfb'),
      createElement('div', { className: 'simple-stack' },
        createElement('div', { className: 'list-line' }, createElement('span', {}, '\u5e73\u53f0\u4efb\u52a1'), createElement('strong', {}, '\u5e73\u53f0\u6388\u6743\u3001\u5e73\u53f0\u914d\u7f6e\u3001\u5e73\u53f0\u8fd0\u7ef4\u4ea7\u751f\u7684\u4e8b\u9879')),
        createElement('div', { className: 'list-line' }, createElement('span', {}, '\u7cfb\u7edf\u4efb\u52a1'), createElement('strong', {}, '\u5fc5\u987b\u8fdb\u5165\u7cfb\u7edf\u540e\u6309\u7cfb\u7edf\u6210\u5458\u6743\u9650\u5904\u7406')),
        createElement('div', { className: 'list-line' }, createElement('span', {}, '\u8df3\u8f6c\u89c4\u5219'), createElement('strong', {}, '\u6d89\u53ca\u7cfb\u7edf\u4e1a\u52a1\u65f6\u5148\u56de\u5230\u7cfb\u7edf\u5165\u53e3\u751f\u6210\u4e0a\u4e0b\u6587')),
      ),
    ),
  );
}

function createPlatformOverviewPanel(navigate: Navigate): HTMLElement {
  const flowButton = createButton('\u67e5\u770b Flow', 'secondary', false);
  const appsButton = createButton('\u67e5\u770b\u5e94\u7528', 'secondary', false);
  const workButton = createButton('\u67e5\u770b\u5de5\u4f5c', 'ghost', false);
  flowButton.addEventListener('click', () => navigate('/platform/flow'));
  appsButton.addEventListener('click', () => navigate('/platform/apps'));
  workButton.addEventListener('click', () => navigate('/platform/work'));
  return createElement(
    'section',
    { className: 'panel platform-overview-panel', dataset: { platformOverviewPanel: 'true' } },
    createElement('h2', {}, '\u5e73\u53f0\u6a21\u5757'),
    createElement('div', { className: 'metric-grid' },
      createMetric('Flow', '\u6d41\u7a0b\u7f16\u6392'),
      createMetric('\u5e94\u7528', '\u6388\u6743\u4e0e\u63a5\u5165'),
      createMetric('\u5de5\u4f5c', '\u4efb\u52a1\u4e0e\u534f\u4f5c'),
      createMetric('\u7cfb\u7edf\u5165\u53e3', String(shellState.availableSystems.filter((system) => system.enabled).length)),
    ),
    createElement('p', {}, '\u5e73\u53f0\u6a21\u5757\u5904\u7406\u5e73\u53f0\u5bf9\u8c61\uff1b\u9700\u8981\u67e5\u770b\u6216\u5904\u7406\u7cfb\u7edf\u4e1a\u52a1\u6570\u636e\u65f6\uff0c\u5148\u4f7f\u7528\u4e0b\u65b9\u7cfb\u7edf\u5165\u53e3\u751f\u6210\u7cfb\u7edf\u4e0a\u4e0b\u6587\u3002'),
    createElement('div', { className: 'inline-actions' }, flowButton, appsButton, workButton),
  );
}

type PlatformFlowRow = {
  flowId: string;
  flowCode: string;
  name: string;
  triggerSource: string;
  affectedSystems: string;
  owner: string;
  recentRun: string;
  failureFeedback: string;
  auditStatus: string;
  status: string;
  runBatchId: string;
  traceId: string;
  retryTaskId: string;
  compensationTaskId: string;
  idempotencyKey: string;
  latestTaskId: string;
  latestTodoId: string;
  latestMessageId: string;
  auditLogId: string;
  permissionMode: string;
  canManage: boolean;
};

type PlatformAuthorizationRow = {
  authorizationId: string;
  applicationName: string;
  applicationType: string;
  authorizedSystem: string;
  tenant: string;
  systemAdmin: string;
  authorizedModules: string;
  expiresAt: string;
  dataIsolation: string;
  status: string;
  requestId: string;
  authorizationChangeId: string;
  scope: string;
  recentFeedback: string;
  traceId: string;
  auditLogId: string;
  latestTodoId: string;
  latestMessageId: string;
  canManage: boolean;
};

type PlatformStatusTone = 'success' | 'warning' | 'danger' | 'info' | 'neutral';

function createLoadingTableRow(colSpan: number, label: string): HTMLTableRowElement {
  const cell = createElement('td', {}, label);
  cell.colSpan = colSpan;
  return createElement('tr', {}, cell);
}

function renderPlatformActionError(host: HTMLElement, marker: string, error: unknown): void {
  host.dataset.platformActionError = marker;
  host.replaceChildren(createElement('h2', {}, '\u64cd\u4f5c\u5931\u8d25'), createElement('p', {}, error instanceof Error ? error.message : '\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002'));
}

async function loadPlatformFlowWorkbench(tableBody: HTMLElement, detailPanel: HTMLElement, countLabel: HTMLElement, resultPanel: HTMLElement): Promise<void> {
  tableBody.replaceChildren(createLoadingTableRow(7, '\u6b63\u5728\u8bfb\u53d6\u5e73\u53f0 Flow...'));
  try {
    const page = await loadPlatformFlows({ pageNo: 1, pageSize: 20 });
    const rows = page.records.map(toPlatformFlowRow);
    const root = tableBody.closest('[data-platform-flow-page]') as HTMLElement | null;
    if (root) { root.dataset.platformFlowRowCount = String(rows.length); }
    countLabel.textContent = `${rows.length} \u4e2a\u5e73\u53f0\u7ea7\u6d41\u7a0b`;
    if (rows.length === 0) {
      tableBody.replaceChildren(createLoadingTableRow(7, '\u6682\u65e0\u5e73\u53f0 Flow'));
      detailPanel.dataset.platformFlowDetailPanel = 'empty';
      detailPanel.replaceChildren(createElement('h2', {}, 'Flow \u8be6\u60c5'), createElement('p', {}, '\u6682\u65e0\u53ef\u56de\u8bfb\u7684\u5e73\u53f0 Flow\u3002'));
      return;
    }
    const selectFlow = (row: PlatformFlowRow): void => renderPlatformFlowDetailPanel(detailPanel, row);
    renderPlatformFlowDetailPanel(detailPanel, rows[0]);
    tableBody.replaceChildren(...rows.map((row) => createPlatformFlowTableRow(row, selectFlow, resultPanel, () => loadPlatformFlowWorkbench(tableBody, detailPanel, countLabel, resultPanel))));
  } catch (error) {
    tableBody.replaceChildren(createLoadingTableRow(7, error instanceof Error ? error.message : '\u5e73\u53f0 Flow \u8bfb\u53d6\u5931\u8d25'));
  }
}

function toPlatformFlowRow(view: PlatformFlowView): PlatformFlowRow {
  const runBatchId = view.currentRunBatchId || 'not-run';
  const latestTaskId = view.latestTaskId || 'not-created';
  return {
    flowId: view.flowId,
    flowCode: view.flowCode,
    name: view.flowName,
    triggerSource: view.triggerSource,
    affectedSystems: view.affectedSystems.length > 0 ? view.affectedSystems.join(' / ') : 'platform',
    owner: view.canManage ? 'platform-admin' : 'view-request-only',
    recentRun: runBatchId,
    failureFeedback: view.latestTodoId ? `TODO ${view.latestTodoId}` : 'none',
    auditStatus: view.auditLogId ? 'RECORDED' : 'PENDING',
    status: view.status,
    runBatchId,
    traceId: view.traceId,
    retryTaskId: latestTaskId,
    compensationTaskId: view.latestTaskId || `COMP-${view.flowId}`,
    idempotencyKey: `${view.flowCode}+platform-action`,
    latestTaskId,
    latestTodoId: view.latestTodoId || '',
    latestMessageId: view.latestMessageId || '',
    auditLogId: view.auditLogId,
    permissionMode: view.permissionMode,
    canManage: view.canManage,
  };
}

function createPlatformFlowTableRow(row: PlatformFlowRow, onSelect: (row: PlatformFlowRow) => void, resultPanel: HTMLElement, onRefresh: () => Promise<void>): HTMLElement {
  const detailButton = createButton('\u67e5\u770b\u7ed3\u679c', 'secondary', false);
  detailButton.dataset.platformFlowDetailAction = row.flowId;
  detailButton.addEventListener('click', (event) => { event.stopPropagation(); onSelect(row); renderPlatformFlowViewResult(resultPanel, row, 'detail'); });
  const retryButton = createButton('\u5931\u8d25\u91cd\u8bd5', 'ghost', !row.canManage, row.canManage ? undefined : '\u5f53\u524d\u8d26\u53f7\u53ea\u80fd\u67e5\u770b\u548c\u7533\u8bf7\u3002');
  retryButton.dataset.platformFlowRetryAction = row.flowId;
  retryButton.addEventListener('click', (event) => { event.stopPropagation(); if (!retryButton.disabled) { void handlePlatformFlowAction(row, 'retry', retryButton, resultPanel, onRefresh); } });
  const compensationButton = createButton('\u8865\u507f\u4efb\u52a1', 'ghost', !row.canManage, row.canManage ? undefined : '\u5f53\u524d\u8d26\u53f7\u53ea\u80fd\u67e5\u770b\u548c\u7533\u8bf7\u3002');
  compensationButton.dataset.platformFlowCompensationAction = row.flowId;
  compensationButton.addEventListener('click', (event) => { event.stopPropagation(); if (!compensationButton.disabled) { void handlePlatformFlowAction(row, 'compensate', compensationButton, resultPanel, onRefresh); } });
  const tableRow = createElement('tr', { dataset: { platformFlowRow: row.flowId, platformFlowRunBatch: row.runBatchId, platformFlowTraceId: row.traceId, platformFlowAuditStatus: row.auditStatus, platformFlowFailureFeedback: row.failureFeedback } },
    createElement('td', {}, createElement('strong', {}, row.name), createElement('small', {}, row.flowCode)),
    createElement('td', {}, row.triggerSource),
    createElement('td', {}, row.affectedSystems),
    createElement('td', {}, row.recentRun),
    createElement('td', {}, row.failureFeedback),
    createElement('td', {}, renderStatusPill(row.auditStatus, row.auditStatus === 'PENDING' ? 'warning' : 'success')),
    createElement('td', {}, createElement('div', { className: 'inline-actions compact-actions' }, detailButton, retryButton, compensationButton)),
  );
  tableRow.classList.add('clickable-row');
  tableRow.tabIndex = 0;
  tableRow.addEventListener('click', () => onSelect(row));
  tableRow.addEventListener('keydown', (event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); onSelect(row); } });
  return tableRow;
}

async function handlePlatformFlowAction(row: PlatformFlowRow, action: 'retry' | 'compensate', button: HTMLButtonElement, resultPanel: HTMLElement, onRefresh: () => Promise<void>): Promise<void> {
  button.disabled = true;
  const originalText = button.textContent || '';
  button.textContent = '\u6267\u884c\u4e2d...';
  try {
    const feedback = await runPlatformFlowAction(row.flowId, action);
    renderPlatformFlowRunFeedback(resultPanel, row, feedback);
    await onRefresh();
  } catch (error) {
    renderPlatformActionError(resultPanel, `flow-${action}-error`, error);
  } finally {
    button.disabled = !row.canManage;
    button.textContent = originalText;
  }
}

function renderPlatformFlowDetailPanel(host: HTMLElement, row: PlatformFlowRow): void {
  host.dataset.platformFlowDetailPanel = row.flowId;
  host.dataset.platformFlowRunBatch = row.runBatchId;
  host.dataset.platformFlowTraceId = row.traceId;
  host.replaceChildren(
    createElement('div', { className: 'runtime-card-head' }, createElement('h2', {}, row.name), renderStatusPill(row.status, platformFlowStatusTone(row.status))),
    createElement('div', { className: 'simple-stack' },
      createElement('div', { className: 'list-line' }, createElement('span', {}, '\u89e6\u53d1\u6e90'), createElement('strong', {}, row.triggerSource)),
      createElement('div', { className: 'list-line' }, createElement('span', {}, '\u5f71\u54cd\u8303\u56f4'), createElement('strong', {}, row.affectedSystems)),
      createElement('div', { className: 'list-line' }, createElement('span', {}, '\u5e42\u7b49\u952e'), createElement('code', {}, row.idempotencyKey)),
      createElement('div', { className: 'list-line' }, createElement('span', {}, '\u6700\u8fd1\u6279\u6b21'), createElement('code', {}, row.runBatchId)),
      createElement('div', { className: 'list-line' }, createElement('span', {}, 'traceId'), createElement('code', {}, row.traceId)),
      createElement('div', { className: 'list-line' }, createElement('span', {}, '\u5f85\u529e/\u6d88\u606f'), createElement('strong', {}, `${row.latestTodoId || '-'} / ${row.latestMessageId || '-'}`)),
    ),
    createElement('p', {}, '\u8be5\u8be6\u60c5\u53ea\u5c55\u793a\u5e73\u53f0\u4efb\u52a1\u3001\u5e73\u53f0\u6d88\u606f\u548c\u5e73\u53f0\u65e5\u5fd7\u8fb9\u754c\uff0c\u4e0d\u76f4\u63a5\u8bfb\u5199\u7cfb\u7edf\u4e1a\u52a1\u8bb0\u5f55\u3002'),
  );
}

function createPlatformFlowResultPanel(): HTMLElement {
  return createElement('section', { className: 'panel platform-action-result', dataset: { platformFlowResult: 'idle', platformFlowRunBatch: 'none' } }, createElement('h2', {}, '\u8fd0\u884c\u53cd\u9988'), createElement('p', {}, '\u9009\u62e9 Flow \u884c\u6216\u6267\u884c\u91cd\u8bd5/\u8865\u507f\u540e\uff0c\u8fd9\u91cc\u663e\u793a\u5e73\u53f0\u4efb\u52a1\u3001\u5e73\u53f0\u6d88\u606f\u3001\u5e73\u53f0\u65e5\u5fd7\u548c traceId\u3002'));
}

function renderPlatformFlowViewResult(host: HTMLElement, row: PlatformFlowRow, action: 'detail' | 'draft'): void {
  host.dataset.platformFlowResult = action;
  host.dataset.platformFlowRunBatch = row.runBatchId;
  host.dataset.platformFlowTraceId = row.traceId;
  host.dataset.platformFlowTaskId = row.latestTaskId;
  host.dataset.platformFlowAuditLogId = row.auditLogId;
  host.dataset.platformFlowTodoId = row.latestTodoId;
  host.dataset.platformFlowMessageId = row.latestMessageId;
  host.replaceChildren(createElement('h2', {}, action === 'draft' ? '\u5e73\u53f0 Flow \u8349\u7a3f\u5df2\u4fdd\u5b58' : '\u8fd0\u884c\u7ed3\u679c'), createElement('div', { className: 'simple-stack' }, createElement('div', { className: 'list-line' }, createElement('span', {}, 'Flow'), createElement('strong', {}, row.name)), createElement('div', { className: 'list-line' }, createElement('span', {}, 'runBatchId'), createElement('code', {}, row.runBatchId)), createElement('div', { className: 'list-line' }, createElement('span', {}, 'taskId'), createElement('code', {}, row.latestTaskId)), createElement('div', { className: 'list-line' }, createElement('span', {}, 'traceId'), createElement('code', {}, row.traceId)), createElement('div', { className: 'list-line' }, createElement('span', {}, 'feedback'), createElement('strong', {}, `${row.latestTodoId || '-'} / ${row.latestMessageId || '-'} / ${row.auditLogId}`))), createElement('p', {}, '\u8fd9\u662f\u5e73\u53f0 Flow \u5de5\u4f5c\u53f0\u7684\u771f\u5b9e\u56de\u8bfb\u53cd\u9988\uff1b\u5b83\u4e0d\u76f4\u63a5\u5199\u5165\u7cfb\u7edf\u4e1a\u52a1\u6570\u636e\u3002'));
}

function renderPlatformFlowRunFeedback(host: HTMLElement, row: PlatformFlowRow, feedback: PlatformFlowRunFeedback): void {
  host.dataset.platformFlowResult = feedback.action;
  host.dataset.platformFlowRunBatch = feedback.runBatchId;
  host.dataset.platformFlowTraceId = feedback.traceId;
  host.dataset.platformFlowTaskId = feedback.taskId;
  host.dataset.platformFlowAuditLogId = feedback.auditLogId;
  host.dataset.platformFlowTodoId = feedback.todoId ?? '';
  host.dataset.platformFlowMessageId = feedback.messageId ?? '';
  host.dataset.platformFlowBoundary = feedback.boundary;
  host.replaceChildren(createElement('h2', {}, feedback.action === 'compensate' ? '\u8865\u507f\u4efb\u52a1\u5df2\u751f\u6210' : '\u91cd\u8bd5\u5df2\u8fdb\u5165\u5e73\u53f0\u4efb\u52a1'), createElement('div', { className: 'simple-stack' }, createElement('div', { className: 'list-line' }, createElement('span', {}, 'Flow'), createElement('strong', {}, row.name)), createElement('div', { className: 'list-line' }, createElement('span', {}, 'runBatchId'), createElement('code', {}, feedback.runBatchId)), createElement('div', { className: 'list-line' }, createElement('span', {}, 'taskId'), createElement('code', {}, feedback.taskId)), createElement('div', { className: 'list-line' }, createElement('span', {}, 'traceId'), createElement('code', {}, feedback.traceId)), createElement('div', { className: 'list-line' }, createElement('span', {}, 'feedback'), createElement('strong', {}, `${feedback.todoId ?? '-'} / ${feedback.messageId ?? '-'} / ${feedback.auditLogId}`)), createElement('div', { className: 'list-line' }, createElement('span', {}, 'boundary'), createElement('code', {}, feedback.boundary))));
}

async function loadPlatformAuthorizationWorkbench(tableBody: HTMLElement, detailPanel: HTMLElement, countLabel: HTMLElement, resultPanel: HTMLElement): Promise<void> {
  tableBody.replaceChildren(createLoadingTableRow(6, '\u6b63\u5728\u8bfb\u53d6\u5e73\u53f0\u6388\u6743...'));
  try {
    const page = await loadPlatformAuthorizations({ pageNo: 1, pageSize: 20 });
    const rows = page.records.map(toPlatformAuthorizationRow);
    const root = tableBody.closest('[data-platform-apps-page]') as HTMLElement | null;
    if (root) { root.dataset.platformApplicationRowCount = String(rows.length); }
    countLabel.textContent = `${rows.length} \u4e2a\u6388\u6743\u5bf9\u8c61`;
    if (rows.length === 0) {
      tableBody.replaceChildren(createLoadingTableRow(6, '\u6682\u65e0\u5e73\u53f0\u6388\u6743'));
      detailPanel.dataset.platformAuthorizationDetailPanel = 'empty';
      detailPanel.replaceChildren(createElement('h2', {}, '\u6388\u6743\u8be6\u60c5'), createElement('p', {}, '\u6682\u65e0\u53ef\u56de\u8bfb\u7684\u5e73\u53f0\u6388\u6743\u3002'));
      return;
    }
    const selectAuthorization = (row: PlatformAuthorizationRow): void => renderPlatformAuthorizationDetailPanel(detailPanel, row);
    renderPlatformAuthorizationDetailPanel(detailPanel, rows[0]);
    tableBody.replaceChildren(...rows.map((row) => createPlatformAuthorizationTableRow(row, selectAuthorization, resultPanel, () => loadPlatformAuthorizationWorkbench(tableBody, detailPanel, countLabel, resultPanel))));
  } catch (error) {
    tableBody.replaceChildren(createLoadingTableRow(6, error instanceof Error ? error.message : '\u5e73\u53f0\u6388\u6743\u8bfb\u53d6\u5931\u8d25'));
  }
}

function toPlatformAuthorizationRow(view: PlatformAuthorizationView): PlatformAuthorizationRow {
  return { authorizationId: view.authorizationId, applicationName: view.applicationName, applicationType: view.applicationType, authorizedSystem: view.targetSystemId, tenant: view.targetTenantId, systemAdmin: view.permissionMode, authorizedModules: view.moduleScope.length > 0 ? view.moduleScope.join(' / ') : '-', expiresAt: view.expiryAt, dataIsolation: view.dataIsolation, status: view.status, requestId: view.requestId, authorizationChangeId: view.authorizationChangeId, scope: view.scope.join(' '), recentFeedback: [view.latestTodoId, view.latestMessageId, view.auditLogId].filter(Boolean).join(' / '), traceId: view.traceId, auditLogId: view.auditLogId, latestTodoId: view.latestTodoId || '', latestMessageId: view.latestMessageId || '', canManage: view.canManage };
}

function createPlatformAuthorizationTableRow(row: PlatformAuthorizationRow, onSelect: (row: PlatformAuthorizationRow) => void, resultPanel: HTMLElement, onRefresh: () => Promise<void>): HTMLElement {
  const viewButton = createButton('\u67e5\u770b\u6388\u6743', 'secondary', false);
  viewButton.dataset.platformApplicationAction = row.authorizationId;
  viewButton.addEventListener('click', (event) => { event.stopPropagation(); onSelect(row); renderPlatformAuthorizationViewResult(resultPanel, row, 'view'); });
  const requestButton = createButton(row.canManage ? '\u8c03\u6574\u6388\u6743' : '\u7533\u8bf7\u8c03\u6574', 'ghost', false);
  requestButton.dataset.platformAuthorizationRequest = row.requestId;
  requestButton.addEventListener('click', (event) => { event.stopPropagation(); void handlePlatformAuthorizationAction(row, requestButton, resultPanel, onRefresh); });
  const disableButton = createButton('\u505c\u7528', 'ghost', !row.canManage, row.canManage ? undefined : '\u5f53\u524d\u8d26\u53f7\u4e0d\u80fd\u505c\u7528\u8be5\u6388\u6743\u3002');
  disableButton.dataset.platformAuthorizationDisableAction = row.authorizationId;
  disableButton.addEventListener('click', (event) => { event.stopPropagation(); if (!disableButton.disabled) { void handlePlatformAuthorizationDisable(row, disableButton, resultPanel, onRefresh); } });
  const tableRow = createElement('tr', { dataset: { platformAuthorizationRow: row.authorizationId, platformApplicationRow: row.applicationName, platformApplicationType: row.applicationType, requestId: row.requestId, authorizationChangeId: row.authorizationChangeId } }, createElement('td', {}, createElement('strong', {}, row.applicationName), createElement('small', {}, row.authorizationId + ' / ' + row.applicationType)), createElement('td', {}, row.tenant + ' / ' + row.systemAdmin), createElement('td', {}, row.authorizedModules), createElement('td', {}, row.expiresAt + ' / ' + row.dataIsolation), createElement('td', {}, renderStatusPill(row.status, platformAuthorizationStatusTone(row.status))), createElement('td', {}, createElement('div', { className: 'inline-actions compact-actions' }, viewButton, requestButton, disableButton)));
  tableRow.classList.add('clickable-row');
  tableRow.tabIndex = 0;
  tableRow.addEventListener('click', () => onSelect(row));
  tableRow.addEventListener('keydown', (event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); onSelect(row); } });
  return tableRow;
}

async function handlePlatformAuthorizationAction(row: PlatformAuthorizationRow, button: HTMLButtonElement, resultPanel: HTMLElement, onRefresh: () => Promise<void>): Promise<void> {
  button.disabled = true;
  const originalText = button.textContent || '';
  button.textContent = '\u63d0\u4ea4\u4e2d...';
  try {
    if (row.canManage) {
      const feedback = await runPlatformAuthorizationAction(row.authorizationId, 'adjust', { scope: row.scope.split(' ').filter(Boolean), expiryAt: row.expiresAt, approvalStatus: 'APPROVED' });
      renderPlatformAuthorizationActionFeedback(resultPanel, row, feedback);
    } else {
      const created = await createPlatformAuthorizationRequest({ applicationName: row.applicationName + ' adjust request', applicationType: row.applicationType, targetSystemId: row.authorizedSystem, targetTenantId: row.tenant, moduleScope: row.authorizedModules.split(' / ').filter(Boolean), scope: row.scope.split(' ').filter(Boolean), expiryAt: row.expiresAt, dataIsolation: row.dataIsolation, approvalStatus: 'PENDING' });
      renderPlatformAuthorizationViewResult(resultPanel, toPlatformAuthorizationRow(created), 'request');
    }
    await onRefresh();
  } catch (error) {
    renderPlatformActionError(resultPanel, 'authorization-action-error', error);
  } finally {
    button.disabled = false;
    button.textContent = originalText;
  }
}

async function handlePlatformAuthorizationDisable(row: PlatformAuthorizationRow, button: HTMLButtonElement, resultPanel: HTMLElement, onRefresh: () => Promise<void>): Promise<void> {
  button.disabled = true;
  const originalText = button.textContent || '';
  button.textContent = '\u505c\u7528\u4e2d...';
  try {
    const feedback = await runPlatformAuthorizationAction(row.authorizationId, 'disable');
    renderPlatformAuthorizationActionFeedback(resultPanel, row, feedback);
    await onRefresh();
  } catch (error) {
    renderPlatformActionError(resultPanel, 'authorization-disable-error', error);
  } finally {
    button.disabled = !row.canManage;
    button.textContent = originalText;
  }
}

function renderPlatformAuthorizationDetailPanel(host: HTMLElement, row: PlatformAuthorizationRow): void {
  host.dataset.platformAuthorizationDetailPanel = row.authorizationId;
  host.dataset.requestId = row.requestId;
  host.dataset.authorizationChangeId = row.authorizationChangeId;
  host.replaceChildren(createElement('div', { className: 'runtime-card-head' }, createElement('h2', {}, row.applicationName), renderStatusPill(row.status, platformAuthorizationStatusTone(row.status))), createElement('div', { className: 'simple-stack' }, createElement('div', { className: 'list-line' }, createElement('span', {}, 'requestId'), createElement('code', {}, row.requestId)), createElement('div', { className: 'list-line' }, createElement('span', {}, 'authorizationChangeId'), createElement('code', {}, row.authorizationChangeId)), createElement('div', { className: 'list-line' }, createElement('span', {}, '\u6388\u6743\u7cfb\u7edf'), createElement('strong', {}, row.authorizedSystem)), createElement('div', { className: 'list-line' }, createElement('span', {}, '\u6388\u6743\u8303\u56f4'), createElement('code', {}, row.scope)), createElement('div', { className: 'list-line' }, createElement('span', {}, '\u5e73\u53f0\u53cd\u9988'), createElement('strong', {}, row.recentFeedback || '-'))), createElement('p', {}, '\u6388\u6743\u8be6\u60c5\u53ea\u8fde\u5230\u5e73\u53f0\u5f85\u529e\u3001\u5e73\u53f0\u6d88\u606f\u548c\u5e73\u53f0\u65e5\u5fd7\uff1b\u7cfb\u7edf\u4e1a\u52a1\u8bb0\u5f55\u7531\u5bf9\u5e94\u7cfb\u7edf\u5185\u7684\u6743\u9650\u548c\u5bf9\u5916\u5e94\u7528\u63a7\u5236\u3002'));
}

function createPlatformAuthorizationResultPanel(): HTMLElement {
  return createElement('section', { className: 'panel platform-action-result', dataset: { platformAuthorizationRequest: 'idle', requestId: 'none', authorizationChangeId: 'none' } }, createElement('h2', {}, '\u6388\u6743\u53cd\u9988'), createElement('p', {}, '\u53d1\u8d77\u6216\u8c03\u6574\u6388\u6743\u540e\uff0c\u8fd9\u91cc\u663e\u793a requestId\u3001authorizationChangeId\u3001\u5e73\u53f0\u5f85\u529e\u3001\u5e73\u53f0\u6d88\u606f\u548c\u5e73\u53f0\u65e5\u5fd7\u53cd\u9988\u3002'));
}

function renderPlatformAuthorizationViewResult(host: HTMLElement, row: PlatformAuthorizationRow, action: 'view' | 'request'): void {
  host.dataset.platformAuthorizationRequest = action;
  host.dataset.requestId = row.requestId;
  host.dataset.authorizationChangeId = row.authorizationChangeId;
  host.dataset.platformAuthorizationTraceId = row.traceId;
  host.dataset.platformAuthorizationAuditLogId = row.auditLogId;
  host.dataset.platformAuthorizationTodoId = row.latestTodoId;
  host.dataset.platformAuthorizationMessageId = row.latestMessageId;
  host.replaceChildren(createElement('h2', {}, action === 'request' ? '\u6388\u6743\u7533\u8bf7\u5df2\u751f\u6210' : '\u6388\u6743\u8be6\u60c5\u5df2\u9009\u4e2d'), createElement('div', { className: 'simple-stack' }, createElement('div', { className: 'list-line' }, createElement('span', {}, '\u5e94\u7528'), createElement('strong', {}, row.applicationName)), createElement('div', { className: 'list-line' }, createElement('span', {}, 'requestId'), createElement('code', {}, row.requestId)), createElement('div', { className: 'list-line' }, createElement('span', {}, 'authorizationChangeId'), createElement('code', {}, row.authorizationChangeId)), createElement('div', { className: 'list-line' }, createElement('span', {}, '\u5e73\u53f0\u53cd\u9988'), createElement('strong', {}, `${row.latestTodoId || '-'} / ${row.latestMessageId || '-'} / ${row.auditLogId}`))), createElement('p', {}, '\u8be5\u7ed3\u679c\u662f\u5e73\u53f0\u6388\u6743\u5de5\u4f5c\u53f0\u7684\u771f\u5b9e\u56de\u8bfb\u53cd\u9988\uff1b\u5b83\u4e0d\u63d0\u4f9b\u7cfb\u7edf\u4e1a\u52a1\u8bb0\u5f55\u76f4\u8fbe\u3002'));
}

function renderPlatformAuthorizationActionFeedback(host: HTMLElement, row: PlatformAuthorizationRow, feedback: PlatformAuthorizationActionFeedback): void {
  host.dataset.platformAuthorizationRequest = feedback.action;
  host.dataset.requestId = feedback.requestId;
  host.dataset.authorizationChangeId = feedback.authorizationChangeId;
  host.dataset.platformAuthorizationTraceId = feedback.traceId;
  host.dataset.platformAuthorizationAuditLogId = feedback.auditLogId;
  host.dataset.platformAuthorizationTodoId = feedback.todoId ?? '';
  host.dataset.platformAuthorizationMessageId = feedback.messageId ?? '';
  host.dataset.platformAuthorizationBoundary = feedback.boundary;
  host.replaceChildren(createElement('h2', {}, feedback.action === 'disable' ? '\u505c\u7528\u7533\u8bf7\u5df2\u751f\u6210' : '\u8c03\u6574\u6388\u6743\u5df2\u751f\u6210'), createElement('div', { className: 'simple-stack' }, createElement('div', { className: 'list-line' }, createElement('span', {}, '\u5e94\u7528'), createElement('strong', {}, row.applicationName)), createElement('div', { className: 'list-line' }, createElement('span', {}, 'requestId'), createElement('code', {}, feedback.requestId)), createElement('div', { className: 'list-line' }, createElement('span', {}, 'authorizationChangeId'), createElement('code', {}, feedback.authorizationChangeId)), createElement('div', { className: 'list-line' }, createElement('span', {}, '\u5e73\u53f0\u53cd\u9988'), createElement('strong', {}, `${feedback.todoId ?? '-'} / ${feedback.messageId ?? '-'} / ${feedback.auditLogId}`)), createElement('div', { className: 'list-line' }, createElement('span', {}, 'boundary'), createElement('code', {}, feedback.boundary))));
}

function platformFlowStatusTone(status: string): PlatformStatusTone {
  if (/ENABLED|SUCCESS|APPROVED|RECORDED/.test(status)) { return 'success'; }
  if (/PENDING|QUEUED|COMP|OBSERVE|WAIT/.test(status)) { return 'warning'; }
  if (/DISABLED|FAILED|ERROR/.test(status)) { return 'danger'; }
  return 'info';
}

function platformAuthorizationStatusTone(status: string): PlatformStatusTone {
  if (/APPROVED|ENABLED|ACTIVE/.test(status)) { return 'success'; }
  if (/PENDING|WAIT|REVIEW/.test(status)) { return 'warning'; }
  if (/DISABLED|REJECTED|FAILED/.test(status)) { return 'danger'; }
  return 'info';
}
function createProfilePage(navigate: Navigate): HTMLElement {
  const logoutButton = createButton('退出登录', 'primary', false);
  const passwordButton = createButton('修改密码', 'secondary', false);
  logoutButton.addEventListener('click', () => {
    localStorage.removeItem('unexamine.accessToken');
    localStorage.removeItem('unexamine.refreshToken');
    localStorage.removeItem('unexamine.accountId');
    navigate('/login');
  });
  passwordButton.addEventListener('click', async () => {
    const values = await requestFormInput('修改密码', [
      { name: 'oldPassword', label: '当前密码', type: 'password' },
      { name: 'newPassword', label: '新密码', type: 'password' },
    ], '修改');
    if (!values) {
      return;
    }
    passwordButton.disabled = true;
    passwordButton.textContent = '修改中...';
    try {
      const result = await updateCurrentPassword(values.oldPassword, values.newPassword);
      passwordButton.textContent = '密码已修改';
      passwordButton.title = `traceId=${result.traceId}`;
    } catch (error) {
      passwordButton.disabled = false;
      passwordButton.textContent = '修改失败';
      passwordButton.title = error instanceof Error ? error.message : '修改密码失败。';
    }
  });
  return createElement(
    'section',
    { className: 'page-grid', dataset: { platformProfilePage: 'true' } },
    createElement('div', { className: 'page-heading' }, createElement('h1', {}, '登录人信息'), createElement('p', {}, '账号资料、安全和当前权限摘要。')),
    createElement(
      'section',
      { className: 'panel' },
      createElement('div', { className: 'simple-stack' },
        createElement('div', { className: 'list-line' }, createElement('span', {}, '账号'), createElement('strong', {}, shellState.account.displayName)),
        createElement('div', { className: 'list-line' }, createElement('span', {}, '平台角色'), createElement('span', {}, shellState.account.platformRoles.join(' / ') || '未授权')),
        createElement('div', { className: 'list-line' }, createElement('span', {}, '可进入系统'), createElement('span', {}, String(shellState.availableSystems.filter((system) => system.enabled).length))),
      ),
      createElement('div', { className: 'inline-actions' }, logoutButton, passwordButton),
    ),
  );
}

type PlatformSystemEntry = {
  systemId: string;
  systemName: string;
  tenantName: string;
  tenantId?: string;
  accountMemberBindingId: string;
  enabled: boolean;
  disabledReason?: string;
};

function createSystemSwitchPanel(navigate: Navigate, targetSystemId?: string): HTMLElement {
  const systems: PlatformSystemEntry[] = [...shellState.availableSystems].sort((first, second) => {
    if (first.systemId === targetSystemId) {
      return -1;
    }
    if (second.systemId === targetSystemId) {
      return 1;
    }
    return first.systemName.localeCompare(second.systemName, 'zh-Hans-CN');
  });
  const enabledCount = systems.filter((system) => system.enabled).length;
  const currentSystemId = shellState.currentSystem?.systemId ?? '';
  const currentSystemName = shellState.currentSystem?.systemName ?? '\u672a\u9009\u62e9';
  const targetSystemName = systems.find((system) => system.systemId === targetSystemId)?.systemName ?? '';
  const visibleCount = createElement('strong', { dataset: { platformSystemEntryVisibleCount: 'true' } }, String(systems.length));
  const searchInput = createElement('input', {
    className: 'system-entry-search',
    dataset: { platformSystemEntrySearch: 'true' },
  }) as HTMLInputElement;
  searchInput.type = 'search';
  searchInput.placeholder = '\u641c\u7d22\u7cfb\u7edf\u3001\u79df\u6237\u3001\u6210\u5458\u7ed1\u5b9a';
  searchInput.setAttribute('aria-label', '\u641c\u7d22\u7cfb\u7edf\u5165\u53e3');

  const rows = systems.map((system) => createSystemEntryRow(system, navigate, targetSystemId, currentSystemId));
  const filteredEmpty = createElement(
    'section',
    { className: 'system-entry-empty', dataset: { platformSystemEntryFilteredEmpty: 'true' } },
    createElement('strong', {}, '\u672a\u627e\u5230\u5339\u914d\u7cfb\u7edf'),
    createElement('p', {}, '\u8bf7\u8c03\u6574\u641c\u7d22\u6761\u4ef6\u6216\u8fd4\u56de\u5e73\u53f0\u540e\u53f0\u68c0\u67e5\u6210\u5458\u6620\u5c04\u3002'),
  );
  filteredEmpty.hidden = true;

  const list = createElement(
    'div',
    {
      className: 'system-entry-list',
      dataset: {
        platformSystemEntryList: 'true',
        platformSystemEntryRowCount: String(systems.length),
      },
    },
    ...rows,
    filteredEmpty,
  );

  const applyFilter = () => {
    const keyword = searchInput.value.trim().toLowerCase();
    let shown = 0;
    rows.forEach((row) => {
      const haystack = row.dataset.platformSystemEntrySearchText ?? '';
      const matched = keyword.length === 0 || haystack.includes(keyword);
      row.hidden = !matched;
      if (matched) {
        shown += 1;
      }
    });
    visibleCount.textContent = String(shown);
    filteredEmpty.hidden = shown > 0;
    list.dataset.platformSystemEntryVisibleRows = String(shown);
  };
  searchInput.addEventListener('input', applyFilter);

  return createElement(
    'section',
    {
      className: 'panel platform-system-entry-panel',
      dataset: {
        systemSwitchPanel: 'true',
        platformSystemEntryPanel: 'true',
        platformSystemEntryMode: 'compact-list',
        platformSystemEntryCardCount: '0',
        productSurface: 'system-switch',
        systemSwitchCount: String(systems.length),
        systemSwitchTargetSystemId: targetSystemId ?? '',
        platformSystemEntryCurrentSystemId: currentSystemId,
      },
    },
    createElement(
      'div',
      { className: 'system-entry-head' },
      createElement('div', {},
        createElement('h2', {}, '\u7cfb\u7edf\u5165\u53e3'),
        createElement('p', {}, targetSystemId
          ? '\u5e73\u53f0\u6d88\u606f\u6216\u5f85\u529e\u6307\u5411\u67d0\u4e2a\u7cfb\u7edf\uff0c\u5148\u8fdb\u5165\u76ee\u6807\u7cfb\u7edf\u518d\u5904\u7406\u4e1a\u52a1\u6570\u636e\u3002'
          : '\u4ece\u8fd9\u91cc\u83b7\u53d6\u7cfb\u7edf\u6210\u5458\u4e0a\u4e0b\u6587\uff0c\u518d\u8fdb\u5165\u5177\u4f53\u7cfb\u7edf\u5904\u7406\u4e1a\u52a1\u3002'),
      ),
      createElement('div', { className: 'system-entry-stats' },
        createMetric('\u5168\u90e8\u7cfb\u7edf', String(systems.length)),
        createMetric('\u53ef\u8fdb\u5165', String(enabledCount)),
        createMetric('\u5f53\u524d\u7cfb\u7edf', currentSystemName),
      ),
    ),
    systems.length === 0
      ? createElement(
          'section',
          { className: 'empty-guidance', dataset: { platformSystemEntryEmpty: 'true' } },
          createElement('strong', {}, '\u6682\u65e0\u53ef\u8fdb\u5165\u7cfb\u7edf'),
          createElement('p', {}, canEnterPlatformAdmin()
            ? '\u5f53\u524d\u8d26\u53f7\u8fd8\u6ca1\u6709\u7cfb\u7edf\u6210\u5458\u4e0a\u4e0b\u6587\u3002\u521b\u5efa\u7cfb\u7edf\u540e\u4f1a\u8fdb\u5165\u7cfb\u7edf\u540e\u53f0\u521d\u59cb\u5316\u3002'
            : '\u5f53\u524d\u8d26\u53f7\u8fd8\u6ca1\u6709\u53ef\u8fdb\u5165\u7684\u7cfb\u7edf\uff0c\u8bf7\u8054\u7cfb\u7cfb\u7edf\u7ba1\u7406\u5458\u5206\u914d\u6210\u5458\u6620\u5c04\u3002'),
        )
      : createElement(
          'div',
          { className: 'system-entry-body' },
          createElement('div', { className: 'system-entry-toolbar' },
            searchInput,
            createElement('span', { dataset: { platformSystemEntrySummary: 'true' } }, '\u663e\u793a ', visibleCount, '/', String(systems.length), targetSystemName ? ` · \u76ee\u6807 ${targetSystemName}` : ''),
          ),
          list,
        ),
  );
}

function createSystemEntryRow(system: PlatformSystemEntry, navigate: Navigate, targetSystemId?: string, currentSystemId?: string): HTMLElement {
  const isTarget = system.systemId === targetSystemId;
  const isCurrent = system.systemId === currentSystemId;
  const actionText = system.enabled ? (isTarget ? '\u8fdb\u5165\u76ee\u6807\u7cfb\u7edf' : '\u8fdb\u5165\u7cfb\u7edf') : '\u7533\u8bf7\u6620\u5c04';
  const statusText = system.enabled ? (isCurrent ? '\u5f53\u524d\u7cfb\u7edf' : isTarget ? '\u76ee\u6807\u7cfb\u7edf' : '\u53ef\u8fdb\u5165') : '\u9700\u6620\u5c04';
  const statusTone = system.enabled ? (isTarget || isCurrent ? 'info' : 'success') : 'warning';
  const searchText = [system.systemName, system.tenantName, system.tenantId ?? '', system.accountMemberBindingId ?? '', system.disabledReason ?? '', statusText]
    .join(' ')
    .toLowerCase();
  const enterSystem = async () => {
    if (system.enabled) {
      await switchToSystem(system.systemId, system.tenantId, 'platform workbench enter system');
      navigate(`/systems/${system.systemId}/dashboard`);
      return;
    }
    const tenant = system.tenantId ? `&tenantId=${encodeURIComponent(system.tenantId)}` : '';
    navigate(`/no-member?systemId=${encodeURIComponent(system.systemId)}${tenant}`);
  };
  const row = createElement(
    'article',
    {
      className: `system-entry-row clickable-row${system.enabled ? '' : ' disabled'}${isTarget ? ' target' : ''}${isCurrent ? ' current' : ''}`,
      title: actionText,
      dataset: {
        platformSystemEntryRow: system.systemId,
        platformSystemEntryEnabled: String(system.enabled),
        platformSystemEntryTarget: String(isTarget),
        platformSystemEntryCurrent: String(isCurrent),
        platformSystemEntryBindingId: system.accountMemberBindingId ?? '',
        platformSystemEntryDisabledReason: system.disabledReason ?? '',
        platformSystemEntrySearchText: searchText,
      },
    },
    createElement('div', { className: 'system-entry-main' },
      createElement('strong', {}, system.systemName),
      createElement('span', {}, system.tenantName),
    ),
    createElement('div', { className: 'system-entry-meta' },
      createElement('span', {}, system.accountMemberBindingId || '\u5f85\u5207\u6362'),
      system.disabledReason ? createElement('small', {}, system.disabledReason) : null,
    ),
    renderStatusPill(statusText, statusTone),
    createElement('span', { className: 'system-entry-action' }, actionText),
  );
  row.tabIndex = 0;
  row.addEventListener('click', () => {
    void enterSystem();
  });
  row.addEventListener('keydown', (event) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      void enterSystem();
    }
  });
  return row;
}
function loadPlatformTodoPanel(panel: HTMLElement, navigate: Navigate, pageNo: number): void {
  panel.replaceChildren(createElement('strong', {}, '正在读取平台待办...'));
  void loadPlatformTodos({ pageNo })
    .then((todos) => panel.replaceChildren(...createPlatformTodoPanel(todos, navigate, (nextPage) => loadPlatformTodoPanel(panel, navigate, nextPage))))
    .catch((error) => panel.replaceChildren(createErrorBlock(error)));
}

function createPlatformTodoPanel(todos: TodoSearchResult, navigate: Navigate, onPageChange: (pageNo: number) => void): HTMLElement[] {
  return [
    createElement('h2', {}, '平台待办'),
    createFilterBar(['类型', '系统', '租户', '状态', '时间范围']),
    todos.page.records.length === 0
      ? createElement('section', { className: 'runtime-card' }, '暂无平台待办')
      : createElement(
          'div',
          { className: 'table-shell' },
          createElement(
            'table',
            { className: 'data-table' },
            createElement('thead', {}, createElement('tr', {}, createElement('th', {}, '序号'), createElement('th', {}, '标题'), createElement('th', {}, '来源'), createElement('th', {}, '优先级'), createElement('th', {}, '状态'), createElement('th', {}, '到期'))),
            createElement('tbody', {}, ...todos.page.records.map((todo, index) => createPlatformTodoRow(todo, index, navigate))),
          ),
        ),
    createPagination(todos.page, onPageChange),
  ];
}

function createPlatformTodoRow(todo: TodoRow, index: number, navigate: Navigate): HTMLElement {
  const row = createElement('tr', { className: 'clickable-row', dataset: { platformTodoRow: todo.todoId } });
  row.addEventListener('click', () => navigate(platformTodoTargetToPath(todo.target)));
  row.append(
    createElement('td', {}, index + 1),
    createElement('td', {}, createElement('strong', {}, todo.title), createElement('small', {}, todo.objectTitle ?? todo.moduleCode ?? '')),
    createElement('td', {}, todo.sourceName),
    createElement('td', {}, todo.priority ?? '-'),
    createElement('td', {}, renderStatusPill(todo.status, todo.status === 'DONE' ? 'success' : 'warning')),
    createElement('td', {}, todo.dueAt ?? '-'),
  );
  return row;
}

function loadPlatformMessagePanel(panel: HTMLElement, navigate: Navigate, pageNo: number): void {
  panel.replaceChildren(createElement('strong', {}, '正在读取平台消息...'));
  void loadPlatformMessages({ pageNo })
    .then((messages) => panel.replaceChildren(...createPlatformMessagePanel(messages, navigate, (nextPage) => loadPlatformMessagePanel(panel, navigate, nextPage))))
    .catch((error) => panel.replaceChildren(createErrorBlock(error)));
}

function createPlatformMessagePanel(page: PageResult<MessageCard>, navigate: Navigate, onPageChange: (pageNo: number) => void): HTMLElement[] {
  return [
    createElement('h2', {}, '平台消息'),
    createFilterBar(['系统', '租户', '模板', '类型', '时间']),
    page.records.length === 0
      ? createElement('section', { className: 'runtime-card' }, '暂无平台消息')
      : createElement('div', { className: 'message-stream' }, ...page.records.map((message) => createPlatformMessageItem(message, navigate))),
    createPagination(page, onPageChange),
  ];
}

function createPlatformMessageItem(message: MessageCard, navigate: Navigate): HTMLElement {
  const item = createElement(
    'article',
    { className: `message-item message-jump-card${message.readStatus === 'READ' ? ' read' : ''}`, dataset: { platformMessageItem: message.messageId } },
    createElement('div', { className: 'message-title-row' }, createElement('strong', {}, message.title), createElement('time', {}, message.createdAt)),
    createElement('p', {}, message.content),
    createElement('small', {}, `${message.templateCode} / ${message.type} / traceId=${message.traceId}`),
  );
  item.addEventListener('click', async () => {
    await markPlatformMessageRead(message.messageId).catch(() => undefined);
    navigate(platformMessageTargetToPath(message.target));
  });
  return item;
}

function createPagination<T>(page: PageResult<T>, onPageChange?: (nextPage: number) => void): HTMLElement {
  const previousButton = createButton('上一页', 'ghost', page.pageNo <= 1 || !onPageChange, onPageChange ? '已经是第一页' : '当前列表不支持翻页。');
  const nextButton = createButton('下一页', 'ghost', !page.hasNext || !onPageChange, onPageChange ? '没有更多数据' : '当前列表不支持翻页。');
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

function createErrorBlock(error: unknown): HTMLElement {
  return createElement('section', { className: 'runtime-card' }, createElement('strong', {}, '加载失败'), createElement('p', {}, error instanceof Error ? error.message : '请稍后重试。'));
}

function createNavButton(label: string, path: string, navigate: Navigate, dataset?: Record<string, string>): HTMLButtonElement {
  const button = createButton(label, 'ghost', false);
  if (dataset) {
    Object.entries(dataset).forEach(([key, value]) => {
      button.dataset[key] = value;
    });
  }
  button.addEventListener('click', () => navigate(path));
  return button;
}

function platformTargetSystemId(route: string): string | undefined {
  const query = route.split('?')[1];
  if (!query) {
    return undefined;
  }
  return new URLSearchParams(query).get('targetSystemId') ?? undefined;
}
