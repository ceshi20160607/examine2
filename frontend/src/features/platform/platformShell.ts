import {
  createPlatformSystem,
  loadPlatformMessages,
  loadPlatformTodos,
  markPlatformMessageRead,
  platformMessageTargetToPath,
  platformTodoTargetToPath,
  runPlatformHealthCheck,
  updateCurrentPassword,
  type MessageCard,
  type TodoRow,
  type TodoSearchResult,
} from '../../api/liveData';
import type { PageResult } from '../../api/types';
import type { Navigate } from '../../app/app';
import { canEnterPlatformAdmin, canEnterSystemAdmin, shellState, switchToSystem } from '../../app/state';
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
  return createElement(
    'header',
    { className: 'workspace-header', dataset: { platformHeader: 'true' } },
    createElement('div', { className: 'brand' }, 'unexamine'),
    createElement(
      'nav',
      { className: 'main-nav', dataset: { platformNav: 'true' } },
      createNavButton('仪表盘', '/platform', navigate, { platformNavItem: 'dashboard' }),
      createNavButton('流程', '/platform/flow', navigate, { platformNavItem: 'flow' }),
      createNavButton('应用', '/platform/apps', navigate, { platformNavItem: 'apps' }),
      createNavButton('AI', '/platform/ai', navigate, { platformNavItem: 'ai' }),
    ),
    createElement(
      'div',
      { className: 'header-actions' },
      createCommandCenterButton(navigate),
      createNavButton('待办', '/platform/todos', navigate, { platformNavItem: 'todos' }),
      createNavButton('消息', '/platform/messages', navigate, { platformNavItem: 'messages' }),
      createNavButton(shellState.account.displayName, '/platform/profile', navigate, { platformNavItem: 'profile' }),
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
  if (route === '/platform/profile') {
    return createProfilePage(navigate);
  }

  const targetSystemId = platformTargetSystemId(route);
  const canManagePlatform = canEnterPlatformAdmin();
  const createSystemButton = canManagePlatform ? createButton('创建系统', 'primary', false) : null;
  const adminButton = canManagePlatform ? createButton('平台后台', 'secondary', false) : null;
  if (createSystemButton) {
    createSystemButton.dataset.platformCreateSystemAction = 'true';
  }
  if (adminButton) {
    adminButton.dataset.platformAdminEntry = 'true';
  }
  const headingCopy = canManagePlatform
    ? '平台管理员可以创建系统、进入平台后台，或切换到已有系统继续配置。'
    : '从这里进入你已授权的系统；没有系统入口时，请联系系统管理员分配成员映射。';

  if (createSystemButton) {
    createSystemButton.addEventListener('click', () => {
      void createSystemFromDialog(navigate, createSystemButton);
    });
  }
  adminButton?.addEventListener('click', () => navigate('/platform/admin'));

  return createElement(
    'section',
    {
      className: 'page-grid',
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
      createElement('h1', {}, '平台工作台'),
      createElement('p', {}, headingCopy),
      createElement('div', { className: 'inline-actions' }, createSystemButton, adminButton),
    ),
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

function createPlatformAppsPage(navigate: Navigate): HTMLElement {
  return createElement(
    'section',
    { className: 'page-grid', dataset: { platformAppsPage: 'true' } },
    createElement('div', { className: 'page-heading' }, createElement('h1', {}, '应用'), createElement('p', {}, '展示当前账号可进入的系统和租户。')),
    createSystemSwitchPanel(navigate),
  );
}

function createPlatformFlowPage(navigate: Navigate): HTMLElement {
  const resultPanel = createElement('section', { className: 'panel', dataset: { platformFlowResult: 'true' } }, createElement('p', {}, '运行体检后展示检查项、风险和 traceId。'));
  const healthButton = createButton('运行体检', 'secondary', false);
  healthButton.addEventListener('click', async () => {
    healthButton.disabled = true;
    healthButton.textContent = '体检中...';
    try {
      const result = await runPlatformHealthCheck('FLOW');
      resultPanel.replaceChildren(
        createElement('h2', {}, '体检结果'),
        createElement('div', { className: 'list-line' }, createElement('span', {}, '状态'), renderStatusPill(result.status, result.status === 'PASS' || result.status === 'UP' ? 'success' : 'warning')),
        createElement('div', { className: 'list-line' }, createElement('span', {}, '检查项'), createElement('strong', {}, String(result.checks.length))),
        createElement('div', { className: 'list-line' }, createElement('span', {}, '风险'), createElement('strong', {}, String(result.risks.length))),
        createElement('code', {}, `traceId=${result.traceId} auditLogId=${result.auditLogId}`),
      );
    } catch (error) {
      resultPanel.replaceChildren(createElement('h2', {}, '体检失败'), createElement('p', {}, error instanceof Error ? error.message : '请稍后重试。'));
    } finally {
      healthButton.disabled = false;
      healthButton.textContent = '运行体检';
    }
  });
  const logButton = createButton('查看任务日志', 'ghost', !canEnterPlatformAdmin(), canEnterPlatformAdmin() ? undefined : '当前账号没有平台后台权限。');
  logButton.addEventListener('click', () => navigate('/platform/admin'));
  return createElement(
    'section',
    { className: 'page-grid', dataset: { platformFlowPage: 'true' } },
    createElement('div', { className: 'page-heading' }, createElement('h1', {}, '流程'), createElement('p', {}, '平台级流程运行、任务和审批健康情况。')),
    createElement(
      'section',
      { className: 'panel' },
      createElement('h2', {}, '平台流程概览'),
      createElement('div', { className: 'metric-grid' }, createMetric('待办队列', '0'), createMetric('消息队列', '0'), createMetric('失败任务', '0')),
      createElement('div', { className: 'inline-actions' }, healthButton, logButton),
    ),
    resultPanel,
  );
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

function createSystemSwitchPanel(navigate: Navigate, targetSystemId?: string): HTMLElement {
  const systems = [...shellState.availableSystems].sort((first, second) => {
    if (first.systemId === targetSystemId) {
      return -1;
    }
    if (second.systemId === targetSystemId) {
      return 1;
    }
    return 0;
  });
  return createElement(
    'section',
    {
      className: 'panel',
      dataset: {
        systemSwitchPanel: 'true',
        platformSystemEntryPanel: 'true',
        productSurface: 'system-switch',
        systemSwitchCount: String(systems.length),
        systemSwitchTargetSystemId: targetSystemId ?? '',
      },
    },
    createElement('h2', {}, '系统入口'),
    targetSystemId ? createElement('p', {}, '平台消息或待办指向某个系统，必须先完成系统切换后再处理业务数据。') : null,
    systems.length === 0
      ? createElement(
          'section',
          { className: 'empty-guidance' },
          createElement('strong', {}, '暂无可进入系统'),
          createElement('p', {}, canEnterPlatformAdmin()
            ? '当前账号还没有系统成员上下文。创建系统后会进入系统后台初始化。'
            : '当前账号还没有可进入的系统，请联系系统管理员分配成员映射。'),
        )
      : createElement(
          'div',
          { className: 'system-list' },
          ...systems.map((system) => createSystemCard(system, navigate, targetSystemId)),
        ),
  );
}

function createSystemCard(system: { systemId: string; systemName: string; tenantName: string; tenantId?: string; accountMemberBindingId: string; enabled: boolean; disabledReason?: string }, navigate: Navigate, targetSystemId?: string): HTMLElement {
  const isTarget = system.systemId === targetSystemId;
  const actionText = system.enabled ? (isTarget ? '进入目标系统' : '进入系统') : '申请映射';
  const enterSystem = async () => {
    if (system.enabled) {
      await switchToSystem(system.systemId, system.tenantId, 'platform workbench enter system');
      navigate(`/systems/${system.systemId}/dashboard`);
      return;
    }
    const tenant = system.tenantId ? `&tenantId=${encodeURIComponent(system.tenantId)}` : '';
    navigate(`/no-member?systemId=${encodeURIComponent(system.systemId)}${tenant}`);
  };
  const card = createElement(
    'article',
    {
      className: `${system.enabled ? 'system-card clickable-card' : 'system-card disabled-card clickable-card'}${isTarget ? ' highlighted-card' : ''}`,
      title: actionText,
      dataset: {
        platformSystemCard: system.systemId,
        platformSystemEnabled: String(system.enabled),
        platformSystemTarget: String(isTarget),
        platformSystemBindingId: system.accountMemberBindingId ?? '',
        platformSystemDisabledReason: system.disabledReason ?? '',
      },
    },
    createElement('strong', {}, system.systemName),
    createElement('span', {}, `${system.tenantName} / ${system.accountMemberBindingId || '待切换'}`),
    system.disabledReason ? createElement('small', {}, system.disabledReason) : null,
    createElement('span', { className: 'system-card-action' }, actionText),
  );
  card.tabIndex = 0;
  card.setAttribute('role', 'button');
  card.addEventListener('click', () => {
    void enterSystem();
  });
  card.addEventListener('keydown', (event) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      void enterSystem();
    }
  });
  return card;
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
