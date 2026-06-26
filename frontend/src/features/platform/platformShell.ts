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
import { canEnterPlatformAdmin, shellState, switchToSystem } from '../../app/state';
import { renderPlatformAdmin } from '../platform-admin/platformAdmin';
import { createButton, createElement } from '../../shared/components';
import { createFilterBar } from '../../shared/filters';
import { renderStatusPill } from '../../shared/status';

export function renderPlatformShell(route: string, navigate: Navigate): HTMLElement {
  const isAdminRoute = route === '/platform/admin' && canEnterPlatformAdmin();
  return createElement(
    'div',
    { className: 'workspace-shell' },
    createPlatformHeader(navigate),
    createElement(
      'main',
      { className: 'workspace-main' },
      isAdminRoute ? renderPlatformAdmin(navigate) : createPlatformWorkbench(route, navigate),
    ),
  );
}

function createPlatformHeader(navigate: Navigate): HTMLElement {
  return createElement(
    'header',
    { className: 'workspace-header' },
    createElement('div', { className: 'brand' }, 'unexamine'),
    createElement(
      'nav',
      { className: 'main-nav' },
      createNavButton('仪表盘', '/platform', navigate),
      createNavButton('Flow', '/platform/flow', navigate),
      createNavButton('应用', '/platform/apps', navigate),
    ),
    createElement(
      'div',
      { className: 'header-actions' },
      createNavButton('代办', '/platform/todos', navigate),
      createNavButton('消息', '/platform/messages', navigate),
      createNavButton(shellState.account.displayName, '/platform/profile', navigate),
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
  if (route === '/platform/profile') {
    return createProfilePage(navigate);
  }

  const targetSystemId = platformTargetSystemId(route);
  const createSystemButton = createButton('创建系统', 'primary', false);
  const adminButton = canEnterPlatformAdmin() ? createButton('平台后台', 'secondary', false) : null;
  const createSystemPanel = createCreateSystemPanel(navigate);
  const todoPanel = createElement('section', { className: 'panel' }, createElement('strong', {}, '正在读取平台代办...'));
  const messagePanel = createElement('section', { className: 'panel message-stream' }, createElement('strong', {}, '正在读取平台消息...'));

  createSystemButton.addEventListener('click', () => {
    document.getElementById('create-system-panel')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    createSystemPanel.querySelector<HTMLInputElement>('input[data-field-name="systemName"]')?.focus();
  });
  adminButton?.addEventListener('click', () => navigate('/platform/admin'));

  void loadPlatformTodos()
    .then((todos) => todoPanel.replaceChildren(...createPlatformTodoPanel(todos, navigate)))
    .catch((error) => todoPanel.replaceChildren(createErrorBlock(error)));
  void loadPlatformMessages()
    .then((messages) => messagePanel.replaceChildren(...createPlatformMessagePanel(messages, navigate)))
    .catch((error) => messagePanel.replaceChildren(createErrorBlock(error)));

  return createElement(
    'section',
    { className: 'page-grid' },
    createElement(
      'div',
      { className: 'page-heading' },
      createElement('h1', {}, '平台工作台'),
      createElement('p', {}, '平台成员从这里进入授权内系统；平台管理员额外拥有平台后台入口。'),
      createElement('div', { className: 'inline-actions' }, createSystemButton, adminButton),
    ),
    createSystemPanel,
    createSystemSwitchPanel(navigate, targetSystemId),
    todoPanel,
    messagePanel,
  );
}

function createCreateSystemPanel(navigate: Navigate): HTMLElement {
  const submitButton = createButton('创建并进入系统', 'primary', false);
  const status = createElement('p', { className: 'field-error' }, '创建人会成为该系统超级管理员。');
  const panel = createElement(
    'section',
    { id: 'create-system-panel', className: 'panel create-system-panel' },
    createElement('h2', {}, '创建系统'),
    createElement(
      'div',
      { className: 'form-grid' },
      createField('系统名称', '例如：业务管理系统', 'systemName'),
      createField('系统编码', 'business_system', 'systemCode'),
      createElement('label', {}, createElement('span', {}, '租户模式'), createElement('select', { ariaLabel: '租户模式' }, createElement('option', {}, '多租户'), createElement('option', {}, '单租户'))),
    ),
    status,
    createElement('div', { className: 'inline-actions' }, submitButton),
  );
  submitButton.addEventListener('click', async () => {
    const systemName = valueOf(panel, 'systemName');
    const systemCode = valueOf(panel, 'systemCode');
    if (!systemName || !systemCode) {
      status.textContent = '系统名称和系统编码不能为空。';
      return;
    }
    submitButton.disabled = true;
    submitButton.textContent = '创建中...';
    status.textContent = '正在创建系统...';
    try {
      const system = await createPlatformSystem({ systemName, systemCode, tenantMode: 1 });
      status.textContent = '创建成功，正在进入系统。';
      await switchToSystem(system.systemId, undefined, 'platform create system enter');
      navigate(`/systems/${system.systemId}/dashboard`);
    } catch (error) {
      status.textContent = error instanceof Error ? error.message : '创建系统失败。';
      submitButton.disabled = false;
      submitButton.textContent = '创建并进入系统';
    }
  });
  return panel;
}

function createField(label: string, placeholder: string, fieldName: string): HTMLElement {
  const input = createElement('input', { ariaLabel: label, dataset: { fieldName } });
  input.placeholder = placeholder;
  return createElement('label', {}, createElement('span', {}, label), input);
}

function valueOf(root: HTMLElement, fieldName: string): string {
  return root.querySelector<HTMLInputElement>(`input[data-field-name="${fieldName}"]`)?.value.trim() ?? '';
}

function createPlatformTodoPage(navigate: Navigate): HTMLElement {
  const panel = createElement('section', { className: 'panel' }, createElement('strong', {}, '正在读取平台代办...'));
  void loadPlatformTodos()
    .then((todos) => panel.replaceChildren(...createPlatformTodoPanel(todos, navigate)))
    .catch((error) => panel.replaceChildren(createErrorBlock(error)));
  return createElement(
    'section',
    { className: 'page-grid' },
    createElement('div', { className: 'page-heading' }, createElement('h1', {}, '平台代办'), createElement('p', {}, '平台授权、系统切换、任务和审批类事项集中处理。')),
    panel,
  );
}

function createPlatformMessagePage(navigate: Navigate): HTMLElement {
  const panel = createElement('section', { className: 'panel message-stream' }, createElement('strong', {}, '正在读取平台消息...'));
  void loadPlatformMessages()
    .then((messages) => panel.replaceChildren(...createPlatformMessagePanel(messages, navigate)))
    .catch((error) => panel.replaceChildren(createErrorBlock(error)));
  return createElement(
    'section',
    { className: 'page-grid' },
    createElement('div', { className: 'page-heading' }, createElement('h1', {}, '平台消息'), createElement('p', {}, '消息按时间流展示，整条消息可进入对应平台对象或系统切换入口。')),
    panel,
  );
}

function createPlatformAppsPage(navigate: Navigate): HTMLElement {
  return createElement(
    'section',
    { className: 'page-grid' },
    createElement('div', { className: 'page-heading' }, createElement('h1', {}, '应用'), createElement('p', {}, '展示当前账号可进入的系统和租户。')),
    createSystemSwitchPanel(navigate),
  );
}

function createPlatformFlowPage(navigate: Navigate): HTMLElement {
  const resultPanel = createElement('section', { className: 'panel' }, createElement('p', {}, '运行体检后展示检查项、风险和 traceId。'));
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
    { className: 'page-grid' },
    createElement('div', { className: 'page-heading' }, createElement('h1', {}, 'Flow'), createElement('p', {}, '平台级流程运行、任务和审批健康情况。')),
    createElement(
      'section',
      { className: 'panel' },
      createElement('h2', {}, '平台 Flow 概览'),
      createElement('div', { className: 'metric-grid' }, createMetric('待办队列', '0'), createMetric('消息队列', '0'), createMetric('失败任务', '0')),
      createElement('div', { className: 'inline-actions' },
        healthButton,
        logButton,
      ),
    ),
    resultPanel,
  );
}

function createProfilePage(navigate: Navigate): HTMLElement {
  const logoutButton = createButton('退出登录', 'primary', false);
  const passwordButton = createButton('修改密码', 'secondary', false);
  logoutButton.addEventListener('click', () => {
    localStorage.removeItem('unexamine.accessToken');
    localStorage.removeItem('unexamine.refreshToken');
    navigate('/login');
  });
  passwordButton.addEventListener('click', async () => {
    const oldPassword = window.prompt('请输入当前密码');
    if (!oldPassword) {
      return;
    }
    const newPassword = window.prompt('请输入新密码');
    if (!newPassword) {
      return;
    }
    passwordButton.disabled = true;
    passwordButton.textContent = '修改中...';
    try {
      const result = await updateCurrentPassword(oldPassword, newPassword);
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
    { className: 'page-grid' },
    createElement('div', { className: 'page-heading' }, createElement('h1', {}, '个人信息'), createElement('p', {}, '账号资料、安全和当前权限摘要。')),
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
    { className: 'panel' },
    createElement('h2', {}, '系统切换'),
    targetSystemId ? createElement('p', {}, '平台消息或待办指向某个系统，必须先完成系统切换后再处理业务数据。') : null,
    createElement(
      'div',
      { className: 'system-list' },
      ...systems.map((system) => {
        const isTarget = system.systemId === targetSystemId;
        const button = createButton(system.enabled ? (isTarget ? '进入目标系统' : '进入系统') : '申请映射', system.enabled ? 'secondary' : 'ghost', false);
        button.addEventListener('click', async () => {
          if (system.enabled) {
            button.disabled = true;
            button.textContent = '切换中...';
            await switchToSystem(system.systemId, system.tenantId, 'platform workbench enter system');
            navigate(`/systems/${system.systemId}/dashboard`);
            return;
          }
          const tenant = system.tenantId ? `&tenantId=${encodeURIComponent(system.tenantId)}` : '';
          navigate(`/no-member?systemId=${encodeURIComponent(system.systemId)}${tenant}`);
        });
        return createElement(
          'article',
          { className: `${system.enabled ? 'system-card' : 'system-card disabled-card'}${isTarget ? ' highlighted-card' : ''}` },
          createElement('strong', {}, system.systemName),
          createElement('span', {}, `${system.tenantName} / ${system.accountMemberBindingId}`),
          system.disabledReason ? createElement('small', {}, system.disabledReason) : null,
          button,
        );
      }),
    ),
  );
}

function createPlatformTodoPanel(todos: TodoSearchResult, navigate: Navigate): HTMLElement[] {
  return [
    createElement('h2', {}, '平台代办'),
    createFilterBar(['类型', '系统', '租户', '状态', '时间范围']),
    todos.page.records.length === 0
      ? createElement('section', { className: 'runtime-card' }, '暂无平台代办')
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
    createPagination(todos.page),
  ];
}

function createPlatformTodoRow(todo: TodoRow, index: number, navigate: Navigate): HTMLElement {
  const row = createElement('tr', { className: 'clickable-row' });
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

function createPlatformMessagePanel(page: PageResult<MessageCard>, navigate: Navigate): HTMLElement[] {
  return [
    createElement('h2', {}, '平台消息'),
    createFilterBar(['系统', '租户', '模板', '类型', '时间']),
    page.records.length === 0
      ? createElement('section', { className: 'runtime-card' }, '暂无平台消息')
      : createElement('div', { className: 'message-stream' }, ...page.records.map((message) => createPlatformMessageItem(message, navigate))),
    createPagination(page),
  ];
}

function createPlatformMessageItem(message: MessageCard, navigate: Navigate): HTMLElement {
  const item = createElement(
    'article',
    { className: `message-item message-jump-card${message.readStatus === 'READ' ? ' read' : ''}` },
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

function createErrorBlock(error: unknown): HTMLElement {
  return createElement('section', { className: 'runtime-card' }, createElement('strong', {}, '加载失败'), createElement('p', {}, error instanceof Error ? error.message : '请稍后重试。'));
}

function createNavButton(label: string, path: string, navigate: Navigate): HTMLButtonElement {
  const button = createButton(label, 'ghost', false);
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
