import {
  autoDraftDailyReport,
  archiveSystemMessage,
  createDailyReport,
  createPlainTask,
  createProjectTask,
  createWorkProject,
  executeSystemTodoAction,
  loadDailyReports,
  loadPlainTasks,
  loadProjectTasks,
  loadSystemMessages,
  loadSystemModuleNavigation,
  loadSystemTodos,
  loadWorkDashboard,
  loadWorkProjects,
  markSystemMessageRead,
  markAllSystemMessagesRead,
  messageTargetToPath,
  todoTargetToPath,
  type DailyReport,
  type MessageCard,
  type RuntimeLiveData,
  type TodoRow,
  type TodoAction,
  type TodoSearchResult,
  type TodoTypeNode,
  type CalendarDay,
  type WorkDashboard,
  type WorkProject,
  type WorkWarning,
  type WorkTask,
} from '../../api/liveData';
import type { PageResult } from '../../api/types';
import type { Navigate } from '../../app/app';
import { canEnterSystemAdmin, loadSystemTenants, shellState, switchTenant as switchToTenant, switchToSystem, type TenantOption } from '../../app/state';
import { createButton, createElement, createTraceLine } from '../../shared/components';
import { requestFormInput, requestTextInput } from '../../shared/dialogs';
import { createFilterBar, createKeywordFilterBar } from '../../shared/filters';
import { renderStatusPill } from '../../shared/status';
import { renderRuntimeRecordPage } from '../runtime/records/runtimeRecords';
import { renderSystemAdmin } from '../system-admin/systemAdmin';

type WorkTab = 'dashboard' | 'project' | 'plain' | 'reports';
type TaskView = 'list' | 'kanban';

let activeTodoType: string | undefined;
let activeWorkTab: WorkTab = 'dashboard';
let projectTaskView: TaskView = 'list';
let plainTaskView: TaskView = 'list';
let activeWorkTask: WorkTask | undefined;
let activeWorkCreate: 'project' | 'project-task' | 'plain-task' | 'daily-report' | undefined;
let workActionMessage: string | undefined;
let headerNavigation: Pick<RuntimeLiveData, 'groups' | 'modules'> | undefined;
let headerTenants: TenantOption[] = [];
let todoPageNo = 1;
let todoKeyword = '';
let activeTodoAction: { todo: TodoRow; action: TodoAction } | undefined;
let messagePageNo = 1;
let messageKeyword = '';
let messageReadStatus: 'all' | 'unread' | 'read' = 'all';
let messageArchiveStatus: 'active' | 'archived' = 'active';
let messageActionResult: string | undefined;
let projectTaskPageNo = 1;
let plainTaskPageNo = 1;
let dailyReportPageNo = 1;

export function renderSystemShell(route: string, navigate: Navigate): HTMLElement {
  const root = createElement('div', { className: 'workspace-shell' });
  const requestedSystemId = routeSystemId(route);
  const render = () => {
    if (route.endsWith('/admin') && !canEnterSystemAdmin()) {
      root.replaceChildren(createSystemAccessDenied(requestedSystemId ?? activeSystemId(), navigate));
      return;
    }
    root.replaceChildren(
      createSystemHeader(navigate),
      createElement('main', { className: 'system-layout system-layout-full' }, createSystemContent(route, navigate)),
    );
  };
  if (!requestedSystemId) {
    root.replaceChildren(createSystemContextError('系统地址缺少 systemId。', navigate));
    return root;
  }
  if (shellState.currentSystem?.systemId !== requestedSystemId) {
    root.replaceChildren(createLoadingPanel('正在切换系统上下文...'));
    const option = shellState.availableSystems.find((system) => system.systemId === requestedSystemId);
    void switchToSystem(requestedSystemId, option?.tenantId, 'system route guard')
      .then(() => {
        headerNavigation = undefined;
        headerTenants = [];
        render();
        return Promise.all([loadSystemModuleNavigation(activeSystemId()), loadSystemTenants(activeSystemId())]);
      })
      .then(([navigation, tenants]) => {
        headerNavigation = navigation;
        headerTenants = tenants;
        render();
      })
      .catch((error) => root.replaceChildren(createSystemContextError(error, navigate, requestedSystemId)));
    return root;
  }
  render();
  void Promise.all([loadSystemModuleNavigation(activeSystemId()), loadSystemTenants(activeSystemId())])
    .then(([navigation, tenants]) => {
      headerNavigation = navigation;
      headerTenants = tenants;
      render();
    })
    .catch(() => undefined);
  return root;
}

function createSystemHeader(navigate: Navigate): HTMLElement {
  const system = shellState.currentSystem ?? {
    systemId: shellState.availableSystems[0]?.systemId ?? '1',
    systemName: shellState.availableSystems[0]?.systemName ?? '系统',
  };
  const adminButton = canEnterSystemAdmin() ? createButton('系统后台', 'secondary', false) : null;
  const messageButton = createButton('消息', 'ghost', false);
  const tenantSwitcher = createTenantSwitcher(system.systemId, navigate);
  const switchButton = createButton('系统切换', 'ghost', false);
  const profileButton = createButton(shellState.account.displayName, 'ghost', false);
  messageButton.addEventListener('click', () => navigate(`/systems/${system.systemId}/messages`));
  switchButton.addEventListener('click', () => navigate('/platform'));
  adminButton?.addEventListener('click', () => navigate(`/systems/${system.systemId}/admin`));
  profileButton.addEventListener('click', () => navigate(`/systems/${system.systemId}/profile`));

  const groupButtons = (headerNavigation?.groups ?? [])
    .filter((group) => group.visible)
    .map((group) => createNavButton(group.name, `/systems/${system.systemId}/modules`, navigate));

  return createElement(
    'header',
    { className: 'workspace-header' },
    createElement('div', { className: 'brand' }, system.systemName),
    createElement(
      'nav',
      { className: 'main-nav' },
      createNavButton('仪表盘', `/systems/${system.systemId}/dashboard`, navigate),
      ...groupButtons,
      createNavButton('工作', `/systems/${system.systemId}/work`, navigate),
      createNavButton('待办', `/systems/${system.systemId}/todos`, navigate),
    ),
    createElement(
      'div',
      { className: 'header-actions' },
      messageButton,
      tenantSwitcher,
      switchButton,
      adminButton,
      profileButton,
    ),
  );
}

function createSystemContent(route: string, navigate: Navigate): HTMLElement {
  if (route.endsWith('/admin')) {
    return renderSystemAdmin(navigate);
  }
  if (route.endsWith('/todos')) {
    return createTodoWorkbench(navigate);
  }
  if (route.endsWith('/messages')) {
    return createMessageCenter(navigate);
  }
  if (route.endsWith('/work')) {
    return createWorkPreview();
  }
  if (route.endsWith('/profile')) {
    return createSystemProfile(navigate);
  }
  if (route.endsWith('/dashboard')) {
    return createDashboard(navigate);
  }
  return renderRuntimeRecordPage();
}

function createSystemProfile(navigate: Navigate): HTMLElement {
  const logoutButton = createButton('退出登录', 'primary', false);
  logoutButton.addEventListener('click', () => {
    localStorage.removeItem('unexamine.accessToken');
    localStorage.removeItem('unexamine.refreshToken');
    navigate('/login');
  });
  return createElement(
    'section',
    { className: 'content-panel' },
    createElement('div', { className: 'page-heading' }, createElement('h1', {}, '个人信息'), createElement('p', {}, '当前系统成员、租户和权限上下文。')),
    createElement(
      'section',
      { className: 'panel' },
      createElement('div', { className: 'simple-stack' },
        createElement('div', { className: 'list-line' }, createElement('span', {}, '账号'), createElement('strong', {}, shellState.account.displayName)),
        createElement('div', { className: 'list-line' }, createElement('span', {}, '系统'), createElement('strong', {}, shellState.currentSystem?.systemName ?? activeSystemId())),
        createElement('div', { className: 'list-line' }, createElement('span', {}, '租户'), createElement('span', {}, shellState.currentSystem?.tenantId ?? '-')),
        createElement('div', { className: 'list-line' }, createElement('span', {}, '系统角色'), createElement('span', {}, shellState.account.systemRoles.join(' / ') || '未授权')),
      ),
      createElement('div', { className: 'inline-actions' }, logoutButton),
    ),
  );
}

function createDashboard(navigate: Navigate): HTMLElement {
  const root = createElement('section', { className: 'content-panel' });
  root.replaceChildren(createLoadingPanel('正在读取工作仪表盘...'));
  void loadWorkDashboard(activeSystemId())
    .then((dashboard) => {
      const hasRuntimeModules = (headerNavigation?.modules ?? []).length > 0;
      const openModuleButton = createButton(hasRuntimeModules ? '打开业务模块' : '配置业务模块', 'primary', false);
      openModuleButton.addEventListener('click', () => {
        navigate(hasRuntimeModules || !canEnterSystemAdmin()
          ? `/systems/${activeSystemId()}/modules`
          : `/systems/${activeSystemId()}/admin`);
      });
      const initializationPrompt = !hasRuntimeModules && canEnterSystemAdmin() ? [createSystemInitializationPrompt(navigate)] : [];
      root.replaceChildren(
        createElement('div', { className: 'page-heading' }, createElement('h1', {}, '系统仪表盘'), createElement('p', {}, '当前系统成员权限范围内的概览、预警、日历和待处理事项。')),
        ...initializationPrompt,
        createElement(
          'div',
          { className: 'metric-grid' },
          createMetric('项目任务', String(dashboard.overview?.projectTaskCount ?? 0)),
          createMetric('普通任务', String(dashboard.overview?.plainTaskCount ?? 0)),
          createMetric('逾期任务', String(dashboard.overview?.overdueTaskCount ?? 0)),
          createMetric('今日日报', dashboard.overview?.dailyReportSubmitted ? '已提交' : '待提交'),
        ),
        createWarningPanel(dashboard.todayWarnings ?? []),
        createCalendarPanel(dashboard.monthlyCalendar ?? []),
        openModuleButton,
        createTraceLine(dashboard.traceId ?? `trace_dashboard_${activeSystemId()}`),
      );
    })
    .catch((error) => root.replaceChildren(createErrorPanel(error)));
  return root;
}

function createSystemInitializationPrompt(navigate: Navigate): HTMLElement {
  const adminButton = createButton('进入系统初始化', 'primary', false);
  adminButton.addEventListener('click', () => navigate(`/systems/${activeSystemId()}/admin`));
  return createElement(
    'section',
    { className: 'onboarding-panel' },
    createElement('div', { className: 'runtime-card-head' },
      createElement('div', {}, createElement('h2', {}, '当前系统还没有可用业务模块'), createElement('p', {}, '先完成组织、角色、模块、流程和发布检查，再把系统交给普通成员使用。')),
      renderStatusPill('待初始化', 'warning'),
    ),
    createElement(
      'div',
      { className: 'onboarding-mini-grid' },
      createElement('span', {}, '1. 配置组织与成员'),
      createElement('span', {}, '2. 配置角色权限'),
      createElement('span', {}, '3. 创建并发布模块'),
      createElement('span', {}, '4. 绑定流程、字典和运行态动作'),
    ),
    createElement('div', { className: 'inline-actions' }, adminButton),
  );
}

function createTodoWorkbench(navigate: Navigate): HTMLElement {
  const root = createElement('section', { className: 'content-panel' });
  const load = () => {
    root.replaceChildren(createLoadingPanel('正在读取待办...'));
    void loadSystemTodos(activeSystemId(), {
      typeCode: activeTodoType,
      pageNo: todoPageNo,
      pageSize: 20,
      keyword: todoKeyword,
    })
      .then((todos) => {
        activeTodoAction = activeTodoAction && todos.page.records.some((row) => row.todoId === activeTodoAction?.todo.todoId)
          ? activeTodoAction
          : undefined;
        root.replaceChildren(createTodoContent(todos, navigate, load));
      })
      .catch((error) => root.replaceChildren(createErrorPanel(error)));
  };
  load();
  return root;
}

function createTodoContent(todos: TodoSearchResult, navigate: Navigate, reload: () => void): HTMLElement {
  return createElement(
    'section',
    { className: 'content-panel' },
    createElement('div', { className: 'page-heading' }, createElement('h1', {}, '待办'), createElement('p', {}, '左侧仅展示待办类型，右侧是当前类型的待办列表、筛选和分页。')),
    createElement(
      'div',
      { className: 'todo-layout' },
      createTodoTypeTree(todos.typeTree, reload),
      createElement(
        'section',
        { className: 'panel' },
        createKeywordFilterBar({
          label: '待办关键字',
          value: todoKeyword,
          onApply: (keyword) => {
            todoKeyword = keyword;
            todoPageNo = 1;
            activeTodoAction = undefined;
            reload();
          },
          onReset: () => {
            todoKeyword = '';
            todoPageNo = 1;
            activeTodoAction = undefined;
            reload();
          },
        }),
        createTodoTable(todos.page.records, navigate, reload),
        createPagination(todos.page, (nextPage) => {
          todoPageNo = nextPage;
          activeTodoAction = undefined;
          reload();
        }),
      ),
    ),
    createTraceLine(todos.traceId),
  );
}

function createTodoTypeTree(nodes: TodoTypeNode[], reload: () => void): HTMLElement {
  const buttons = flattenTodoTypes(nodes).map((node) => {
    const button = createElement('button', { className: `sidebar-item${activeTodoType === node.typeCode ? ' active' : ''}` }, `${node.typeName} (${node.count})`);
    button.addEventListener('click', () => {
      activeTodoType = node.typeCode;
      todoPageNo = 1;
      activeTodoAction = undefined;
      reload();
    });
    return button;
  });
  return createElement('aside', { className: 'module-sidebar compact' }, ...buttons);
}

function createTodoTable(rows: TodoRow[], navigate: Navigate, reload: () => void): HTMLElement {
  if (rows.length === 0) {
    return createElement('section', { className: 'runtime-card' }, '暂无待办');
  }
  return createElement(
    'div',
    { className: 'table-shell' },
    createElement(
      'table',
      { className: 'data-table' },
      createElement('thead', {}, createElement('tr', {}, createElement('th', {}, '序号'), createElement('th', {}, '标题'), createElement('th', {}, '来源'), createElement('th', {}, '优先级'), createElement('th', {}, '到期'), createElement('th', {}, '状态'), createElement('th', {}, '操作'))),
      createElement('tbody', {}, ...rows.map((row, index) => createTodoRow(row, index, navigate, reload))),
    ),
  );
}

function createTodoRow(row: TodoRow, index: number, navigate: Navigate, reload: () => void): HTMLElement {
  const tr = createElement('tr', { className: 'clickable-row' });
  tr.addEventListener('click', () => navigate(todoTargetToPath(row.target, activeSystemId())));
  const actionButtons = row.actionPermissions
    .filter((action) => action.actionCode !== 'open')
    .map((action) => {
      const button = createButton(action.actionName, 'ghost', !action.enabled, action.disabledReason);
      button.addEventListener('click', async (event) => {
        event.stopPropagation();
        const payload = await todoActionPayload(action.actionCode);
        if (payload === null) {
          return;
        }
        await executeSystemTodoAction(activeSystemId(), row.todoId, action.actionCode, payload);
        reload();
      });
      return button;
    });
  tr.append(
    createElement('td', {}, index + 1),
    createElement('td', {}, createElement('strong', {}, row.title), createElement('small', {}, row.objectTitle ?? row.moduleCode ?? '')),
    createElement('td', {}, row.sourceName),
    createElement('td', {}, row.priority ?? '-'),
    createElement('td', {}, row.dueAt ?? '-'),
    createElement('td', {}, renderStatusPill(row.status, row.status === 'DONE' ? 'success' : 'warning')),
    createElement('td', { className: 'row-actions' }, ...actionButtons),
  );
  return tr;
}

async function todoActionPayload(actionCode: string): Promise<{
  comment?: string;
  reason?: string;
  transferTargetId?: string;
  transferTargetName?: string;
} | null> {
  if (actionCode === 'approve') {
    const comment = await requestTextInput('审批通过', '审批意见', '同意', false);
    return {
      comment: comment || '同意',
    };
  }
  if (actionCode === 'reject') {
    const reason = await requestTextInput('审批拒绝', '拒绝原因', '不符合审批要求');
    return reason ? { reason } : null;
  }
  if (actionCode === 'transfer') {
    const values = await requestFormInput('转交审批', [
      { name: 'transferTargetId', label: '目标系统成员 ID' },
      { name: 'transferTargetName', label: '目标姓名', required: false },
      { name: 'reason', label: '转交原因', defaultValue: '转交给更合适的审批人' },
    ], '转交');
    if (!values) {
      return null;
    }
    return {
      transferTargetId: values.transferTargetId,
      transferTargetName: values.transferTargetName || undefined,
      reason: values.reason || '转交给更合适的审批人',
    };
  }
  return {};
}

function createMessageCenter(navigate: Navigate): HTMLElement {
  const root = createElement('section', { className: 'content-panel message-center' });
  const reload = () => {
    root.replaceChildren(createLoadingPanel('\u6b63\u5728\u8bfb\u53d6\u6d88\u606f...'));
    void loadSystemMessages(activeSystemId(), {
      pageNo: messagePageNo,
      pageSize: 20,
      keyword: messageKeyword,
      readStatus: messageReadStatus,
      archiveStatus: messageArchiveStatus,
    })
      .then((page) => root.replaceChildren(createMessageContent(page, navigate, reload)))
      .catch((error) => root.replaceChildren(createErrorPanel(error)));
  };
  reload();
  return root;
}

function createMessageContent(page: PageResult<MessageCard>, navigate: Navigate, reload: () => void): HTMLElement {
  return createElement(
    'section',
    { className: 'content-panel message-center' },
    createElement('div', { className: 'page-heading' }, createElement('h1', {}, '\u6d88\u606f'), createElement('p', {}, '\u6d88\u606f\u6309\u65f6\u95f4\u6d41\u5c55\u793a\uff0c\u6574\u6761\u6d88\u606f\u70b9\u51fb\u8df3\u8f6c\u5230\u5ba1\u6279\u3001\u4e1a\u52a1\u8be6\u60c5\u6216\u5de5\u4f5c\u5bf9\u8c61\uff0c\u8f85\u52a9\u64cd\u4f5c\u53ea\u5904\u7406\u9605\u8bfb\u548c\u5f52\u6863\u72b6\u6001\u3002')),
    createMessageToolbar(page, reload),
    messageActionResult ? createElement('section', { className: 'runtime-card' }, messageActionResult) : null,
    page.records.length === 0
      ? createElement('section', { className: 'runtime-card' }, messageArchiveStatus === 'archived' ? '\u6682\u65e0\u5f52\u6863\u6d88\u606f' : '\u6682\u65e0\u6d88\u606f')
      : createElement('div', { className: 'message-stream detail-message-stream' }, ...page.records.map((message) => createMessageItem(message, navigate, reload))),
    createPagination(page, (nextPage) => {
      messagePageNo = nextPage;
      reload();
    }),
  );
}

function createMessageToolbar(page: PageResult<MessageCard>, reload: () => void): HTMLElement {
  const markAllButton = createButton('\u5168\u90e8\u6807\u4e3a\u5df2\u8bfb', 'secondary', page.total === 0 || messageArchiveStatus === 'archived', page.total === 0 ? '\u5f53\u524d\u7b5b\u9009\u6ca1\u6709\u6d88\u606f\u3002' : '\u5f52\u6863\u6d88\u606f\u4e0d\u9700\u8981\u6279\u91cf\u6807\u8bb0\u3002');
  markAllButton.addEventListener('click', async () => {
    const result = await markAllSystemMessagesRead(activeSystemId(), {
      keyword: messageKeyword,
      readStatus: messageReadStatus,
      archiveStatus: messageArchiveStatus,
    });
    messageActionResult = `\u5df2\u8bfb\u66f4\u65b0\uff1a${result.affectedCount} \u6761\uff0ctraceId=${result.traceId}`;
    reload();
  });

  return createElement(
    'div',
    { className: 'simple-stack' },
    createKeywordFilterBar({
      label: '\u6d88\u606f\u5173\u952e\u5b57',
      value: messageKeyword,
      onApply: (keyword) => {
        messageKeyword = keyword;
        messagePageNo = 1;
        messageActionResult = undefined;
        reload();
      },
      onReset: () => {
        messageKeyword = '';
        messagePageNo = 1;
        messageActionResult = undefined;
        reload();
      },
    }),
    createElement(
      'div',
      { className: 'inline-actions' },
      createMessageStateButton('\u5168\u90e8', 'all', messageReadStatus, (value) => {
        messageReadStatus = value;
        messagePageNo = 1;
        messageActionResult = undefined;
        reload();
      }),
      createMessageStateButton('\u672a\u8bfb', 'unread', messageReadStatus, (value) => {
        messageReadStatus = value;
        messagePageNo = 1;
        messageActionResult = undefined;
        reload();
      }),
      createMessageStateButton('\u5df2\u8bfb', 'read', messageReadStatus, (value) => {
        messageReadStatus = value;
        messagePageNo = 1;
        messageActionResult = undefined;
        reload();
      }),
      createMessageArchiveButton('\u5f53\u524d\u6d88\u606f', 'active', reload),
      createMessageArchiveButton('\u5df2\u5f52\u6863', 'archived', reload),
      markAllButton,
    ),
  );
}

function createMessageStateButton<T extends string>(label: string, value: T, activeValue: T, onSelect: (value: T) => void): HTMLButtonElement {
  const button = createButton(label, value === activeValue ? 'primary' : 'ghost', false);
  button.addEventListener('click', () => onSelect(value));
  return button;
}

function createMessageArchiveButton(label: string, value: 'active' | 'archived', reload: () => void): HTMLButtonElement {
  const button = createButton(label, value === messageArchiveStatus ? 'primary' : 'ghost', false);
  button.addEventListener('click', () => {
    messageArchiveStatus = value;
    messagePageNo = 1;
    messageActionResult = undefined;
    reload();
  });
  return button;
}

function createMessageItem(message: MessageCard, navigate: Navigate, reload: () => void): HTMLElement {
  const archiveButton = createButton('\u5f52\u6863', 'ghost', message.archiveStatus === 'archived', message.archiveStatus === 'archived' ? '\u6d88\u606f\u5df2\u5f52\u6863\u3002' : undefined);
  archiveButton.addEventListener('click', async (event) => {
    event.stopPropagation();
    const result = await archiveSystemMessage(activeSystemId(), message.messageId);
    messageActionResult = `\u5df2\u5f52\u6863\uff1a${result.affectedCount} \u6761\uff0ctraceId=${result.traceId}`;
    reload();
  });
  const item = createElement(
    'article',
    { className: `message-item message-jump-card${message.readStatus === 'read' ? ' read' : ''}` },
    createElement('div', { className: 'message-title-row' }, createElement('strong', {}, message.title), createElement('time', {}, message.createdAt)),
    createElement('p', {}, message.content),
    createElement('div', { className: 'inline-actions' }, renderStatusPill(message.readStatus, message.readStatus === 'read' ? 'success' : 'warning'), renderStatusPill(message.archiveStatus, message.archiveStatus === 'archived' ? 'info' : 'success'), archiveButton),
    createElement('small', {}, `${message.templateCode} / ${message.type} / traceId=${message.traceId}`),
  );
  item.addEventListener('click', async () => {
    await markSystemMessageRead(activeSystemId(), message.messageId).catch(() => undefined);
    navigate(messageTargetToPath(message.target, activeSystemId()));
  });
  return item;
}

function createWorkPreview(): HTMLElement {
  const root = createElement('section', { className: 'content-panel workbench-page' });
  const render = (data?: WorkData, error?: unknown) => {
    if (error) {
      root.replaceChildren(createErrorPanel(error));
      return;
    }
    if (!data) {
      root.replaceChildren(createLoadingPanel('正在读取工作管理...'));
      return;
    }
    root.replaceChildren(createWorkContent(data, render, reload));
  };
  const reload = () => {
    render();
    void Promise.all([
      loadWorkDashboard(activeSystemId()),
      loadWorkProjects(activeSystemId()),
      loadProjectTasks(activeSystemId(), { pageNo: projectTaskPageNo, pageSize: 20 }),
      loadPlainTasks(activeSystemId(), { pageNo: plainTaskPageNo, pageSize: 20 }),
      loadDailyReports(activeSystemId(), { pageNo: dailyReportPageNo, pageSize: 20 }),
    ])
      .then(([dashboard, projects, projectTasks, plainTasks, reports]) => render({ dashboard, projects, projectTasks, plainTasks, reports }))
      .catch((error) => render(undefined, error));
  };
  reload();
  return root;
}

interface WorkData {
  dashboard: WorkDashboard;
  projects: PageResult<WorkProject>;
  projectTasks: PageResult<WorkTask>;
  plainTasks: PageResult<WorkTask>;
  reports: PageResult<DailyReport>;
}

function createWorkContent(data: WorkData, render: (data: WorkData) => void, reload: () => void): HTMLElement {
  return createElement(
    'section',
    { className: 'content-panel workbench-page' },
    createElement('div', { className: 'page-heading' }, createElement('h1', {}, '工作管理'), createElement('p', {}, '固定为仪表盘、项目任务、普通任务、日报四个标签；任务支持列表和看板互斥切换。')),
    createWorkTabs(data, render),
    activeWorkTab === 'dashboard'
      ? createWorkDashboard(data.dashboard)
      : activeWorkTab === 'project'
        ? createTaskSection('项目任务', data.projectTasks, projectTaskView, (view) => {
            projectTaskView = view;
            render(data);
          }, (task) => {
            activeWorkTask = task;
            render(data);
          }, (pageNo) => {
            projectTaskPageNo = pageNo;
            activeWorkTask = undefined;
            reload();
          }, render, data, reload)
        : activeWorkTab === 'plain'
          ? createTaskSection('普通任务', data.plainTasks, plainTaskView, (view) => {
              plainTaskView = view;
              render(data);
            }, (task) => {
              activeWorkTask = task;
              render(data);
            }, (pageNo) => {
              plainTaskPageNo = pageNo;
              activeWorkTask = undefined;
              reload();
            }, render, data, reload)
          : createDailyReportPanel(data.reports, render, data, reload, (pageNo) => {
              dailyReportPageNo = pageNo;
              reload();
            }),
    createTraceLine(data.dashboard.traceId ?? `frontend_work_${activeSystemId()}`),
  );
}

function createWorkTabs(data: WorkData, render: (data: WorkData) => void): HTMLElement {
  const tabs: Array<[WorkTab, string]> = [
    ['dashboard', '仪表盘'],
    ['project', '项目任务'],
    ['plain', '普通任务'],
    ['reports', '日报'],
  ];
  return createElement(
    'nav',
    { className: 'detail-tabs work-tabs' },
    ...tabs.map(([tab, label]) => {
      const button = createElement('button', { className: activeWorkTab === tab ? 'active' : '' }, label);
      button.addEventListener('click', () => {
        activeWorkTab = tab;
        activeWorkTask = undefined;
        render(data);
      });
      return button;
    }),
  );
}

function createWorkDashboard(dashboard: WorkDashboard): HTMLElement {
  return createElement(
    'section',
    { className: 'work-section' },
    createElement(
      'div',
      { className: 'metric-grid' },
      createMetric('进行中项目', String(dashboard.overview?.activeProjectCount ?? 0)),
      createMetric('项目任务', String(dashboard.overview?.projectTaskCount ?? 0)),
      createMetric('普通任务', String(dashboard.overview?.plainTaskCount ?? 0)),
      createMetric('逾期任务', String(dashboard.overview?.overdueTaskCount ?? 0)),
    ),
    createWarningPanel(dashboard.todayWarnings ?? []),
    createCalendarPanel(dashboard.monthlyCalendar ?? []),
  );
}

function createWarningPanel(warnings: WorkWarning[]): HTMLElement {
  return createElement(
    'article',
    { className: 'panel' },
    createElement('h3', {}, '今日预警'),
    warnings.length === 0 ? createElement('p', {}, '暂无今日预警') : createElement('div', { className: 'simple-stack' }, ...warnings.map((warning) => createElement('div', { className: 'list-line' }, createElement('span', {}, warning.title), renderStatusPill(warning.level ?? '预警', warning.level === 'HIGH' ? 'danger' : 'warning')))),
  );
}

function createCalendarPanel(days: CalendarDay[]): HTMLElement {
  const visibleDays = days.slice(0, 31);
  return createElement(
    'article',
    { className: 'panel' },
    createElement('h3', {}, '本月工作日历'),
    visibleDays.length === 0
      ? createElement('p', {}, '暂无日历事项')
      : createElement(
          'div',
          { className: 'calendar-month-grid' },
          ...visibleDays.map((day) => createElement('div', { className: 'calendar-day' }, createElement('strong', {}, day.date.slice(-2)), createElement('small', {}, day.items.map((item) => item.title).join(' / ') || '无'))),
        ),
  );
}

function createTaskSection(
  title: string,
  page: PageResult<WorkTask>,
  view: TaskView,
  setView: (view: TaskView) => void,
  selectTask: (task: WorkTask) => void,
  onPageChange: (pageNo: number) => void,
  render: (data: WorkData) => void,
  data: WorkData,
  reload: () => void,
): HTMLElement {
  const projectCreateButton = title === '项目任务' ? createWorkCreateButton('新建项目', 'project', data, render) : null;
  const taskCreateType = title === '项目任务' ? 'project-task' : 'plain-task';
  return createElement(
    'section',
    { className: 'work-section' },
    createElement(
      'div',
      { className: 'runtime-card-head' },
      createElement('h2', {}, title),
      createElement(
        'div',
        { className: 'inline-actions' },
        createViewButton('列表', view === 'list', () => setView('list')),
        createViewButton('看板', view === 'kanban', () => setView('kanban')),
        projectCreateButton,
        createWorkCreateButton(title === '项目任务' ? '新建项目任务' : '记录普通任务', taskCreateType, data, render),
      ),
    ),
    createFilterBar(['项目', '负责人', '状态', '标签', '完成时间']),
    activeWorkCreate === 'project' && title === '项目任务' ? createWorkCreatePanel('project', reload, data.projects) : null,
    activeWorkCreate === taskCreateType ? createWorkCreatePanel(taskCreateType, reload, data.projects) : null,
    workActionMessage ? createElement('section', { className: 'runtime-card' }, workActionMessage) : null,
    view === 'list' ? createTaskList(page, selectTask, onPageChange) : createTaskKanban(page.records, selectTask),
    activeWorkTask ? createTaskDetailCard(activeWorkTask) : null,
  );
}

function createViewButton(label: string, active: boolean, onClick: () => void): HTMLButtonElement {
  const button = createButton(label, active ? 'primary' : 'secondary', false);
  button.addEventListener('click', onClick);
  return button;
}

function createWorkCreateButton(label: string, createType: 'project' | 'project-task' | 'plain-task' | 'daily-report', data: WorkData, render: (data: WorkData) => void): HTMLButtonElement {
  const button = createButton(label, 'primary', false);
  button.addEventListener('click', () => {
    activeWorkCreate = activeWorkCreate === createType ? undefined : createType;
    workActionMessage = undefined;
    render(data);
  });
  return button;
}

function createWorkCreatePanel(createType: 'project' | 'project-task' | 'plain-task' | 'daily-report', reload: () => void, projects: PageResult<WorkProject>): HTMLElement {
  const titleInput = createElement('input', { ariaLabel: '标题' });
  titleInput.placeholder = createType === 'project' ? '项目名称' : createType === 'daily-report' ? '日报标题可不填' : '任务标题';
  const projectSelect = createProjectSelect(projects);
  const dueAtInput = createElement('input', { ariaLabel: '截止时间' });
  dueAtInput.type = 'datetime-local';
  const contentInput = createElement('textarea', { ariaLabel: '内容' });
  contentInput.placeholder = createType === 'daily-report' ? '填写今日完成、问题、明日计划' : '补充说明，可为空';
  const submitButton = createButton('保存', 'primary', false);
  submitButton.addEventListener('click', async () => {
    try {
      if (createType === 'project') {
        const project = await createWorkProject(activeSystemId(), { projectName: titleInput.value.trim() || `新项目 ${Date.now()}` });
        workActionMessage = `项目已创建：${project.projectName}`;
        projectTaskPageNo = 1;
      } else if (createType === 'project-task') {
        const task = await createProjectTask(activeSystemId(), {
          title: titleInput.value.trim() || `项目任务 ${Date.now()}`,
          projectId: projectSelect.value || undefined,
          dueAt: dueAtInput.value ? new Date(dueAtInput.value).toISOString() : undefined,
          fieldValues: contentInput.value.trim() ? { description: contentInput.value.trim() } : {},
        });
        workActionMessage = `项目任务已创建：${task.title}`;
        projectTaskPageNo = 1;
      } else if (createType === 'plain-task') {
        const task = await createPlainTask(activeSystemId(), {
          title: titleInput.value.trim() || `普通任务 ${Date.now()}`,
          dueAt: dueAtInput.value ? new Date(dueAtInput.value).toISOString() : undefined,
          fieldValues: contentInput.value.trim() ? { description: contentInput.value.trim() } : {},
        });
        workActionMessage = `普通任务已创建：${task.title}`;
        plainTaskPageNo = 1;
      } else {
        const today = new Date().toISOString().slice(0, 10);
        const report = await createDailyReport(activeSystemId(), {
          reportDate: today,
          content: contentInput.value.trim() || '今日工作待补充。',
          status: 'DRAFT',
          submitNow: false,
        });
        workActionMessage = `日报草稿已保存：${report.date}`;
        dailyReportPageNo = 1;
      }
      activeWorkCreate = undefined;
      reload();
    } catch (error) {
      workActionMessage = error instanceof Error ? error.message : '保存失败。';
      reload();
    }
  });
  return createElement(
    'section',
    { className: 'runtime-card form-grid' },
    createElement('label', {}, createElement('span', {}, createType === 'project' ? '项目名称' : '标题'), titleInput),
    createType === 'project-task'
      ? createElement('label', {}, createElement('span', {}, '关联项目'), projectSelect, projects.records.length === 0 ? createElement('small', {}, '暂无项目，可先点击“新建项目”。') : null)
      : null,
    createType === 'project-task' || createType === 'plain-task' ? createElement('label', {}, createElement('span', {}, '截止时间'), dueAtInput) : null,
    createType !== 'project' ? createElement('label', {}, createElement('span', {}, '内容'), contentInput) : null,
    createElement('div', { className: 'inline-actions' }, submitButton),
  );
}

function createProjectSelect(projects: PageResult<WorkProject>): HTMLSelectElement {
  const select = createElement('select', { ariaLabel: '关联项目' });
  const emptyOption = createElement('option', {}, '不关联项目');
  emptyOption.value = '';
  select.append(emptyOption);
  projects.records.forEach((project) => {
    const option = createElement('option', {}, `${project.projectName} / ${project.projectCode}`);
    option.value = project.projectId;
    select.append(option);
  });
  return select;
}

function createTaskList(page: PageResult<WorkTask>, selectTask: (task: WorkTask) => void, onPageChange: (pageNo: number) => void): HTMLElement {
  if (page.records.length === 0) {
    return createElement('section', { className: 'runtime-card' }, '暂无任务');
  }
  return createElement(
    'div',
    { className: 'table-shell' },
    createElement(
      'table',
      { className: 'data-table' },
      createElement('thead', {}, createElement('tr', {}, createElement('th', {}, '序号'), createElement('th', {}, '标题'), createElement('th', {}, '项目'), createElement('th', {}, '负责人'), createElement('th', {}, '进度'), createElement('th', {}, '状态'), createElement('th', {}, '到期'))),
      createElement('tbody', {}, ...page.records.map((task, index) => createTaskRow(task, index, selectTask))),
    ),
    createPagination(page, onPageChange),
  );
}

function createTaskRow(task: WorkTask, index: number, selectTask: (task: WorkTask) => void): HTMLElement {
  const row = createElement('tr', { className: 'clickable-row' });
  row.addEventListener('click', () => selectTask(task));
  row.append(
    createElement('td', {}, index + 1),
    createElement('td', {}, task.title),
    createElement('td', {}, task.projectName ?? '-'),
    createElement('td', {}, task.assignee?.memberName ?? '-'),
    createElement('td', {}, `${task.progress ?? 0}%`),
    createElement('td', {}, renderStatusPill(task.status?.itemName ?? task.status?.itemCode ?? '未设置', task.warningLevel === 'HIGH' ? 'danger' : 'info')),
    createElement('td', {}, task.dueAt ?? '-'),
  );
  return row;
}

function createTaskKanban(tasks: WorkTask[], selectTask: (task: WorkTask) => void): HTMLElement {
  const groups = new Map<string, WorkTask[]>();
  tasks.forEach((task) => {
    const key = task.status?.itemName ?? task.status?.itemCode ?? '未设置';
    groups.set(key, [...(groups.get(key) ?? []), task]);
  });
  return createElement(
    'div',
    { className: 'kanban-preview' },
    ...Array.from(groups.entries()).map(([status, groupTasks]) =>
      createElement(
        'article',
        { className: 'kanban-column' },
        createElement('strong', {}, status),
        ...groupTasks.map((task) => {
          const card = createElement('button', { className: 'kanban-card' }, task.title, createElement('small', {}, `${task.assignee?.memberName ?? '-'} / ${task.progress ?? 0}% / ${task.commentCount ?? 0} 评论`));
          card.addEventListener('click', () => selectTask(task));
          return card;
        }),
      ),
    ),
  );
}

function createTaskDetailCard(task: WorkTask): HTMLElement {
  return createElement(
    'section',
    { className: 'panel task-detail-card' },
    createElement('div', { className: 'runtime-card-head' }, createElement('h3', {}, task.title), renderStatusPill(task.status?.itemName ?? task.status?.itemCode ?? '未设置', task.warningLevel === 'HIGH' ? 'danger' : 'info')),
    createElement(
      'div',
      { className: 'kv-grid' },
      createElement('dt', {}, '项目'),
      createElement('dd', {}, task.projectName ?? '-'),
      createElement('dt', {}, '负责人'),
      createElement('dd', {}, task.assignee?.memberName ?? '-'),
      createElement('dt', {}, '完成度'),
      createElement('dd', {}, `${task.progress ?? 0}%`),
      createElement('dt', {}, '截止时间'),
      createElement('dd', {}, task.dueAt ?? '-'),
      createElement('dt', {}, '评论数'),
      createElement('dd', {}, String(task.commentCount ?? 0)),
    ),
  );
}

function createDailyReportPanel(page: PageResult<DailyReport>, render: (data: WorkData) => void, data: WorkData, reload: () => void, onPageChange: (pageNo: number) => void): HTMLElement {
  const manualButton = createWorkCreateButton('手动填写', 'daily-report', data, render);
  const autoDraftButton = createButton('自动生成今日日报', 'secondary', false);
  autoDraftButton.addEventListener('click', async () => {
    try {
      const draft = await autoDraftDailyReport(activeSystemId());
      workActionMessage = `自动草稿已生成：${draft.content}`;
    } catch (error) {
      workActionMessage = error instanceof Error ? error.message : '自动生成日报草稿失败。';
    }
    reload();
  });
  return createElement(
    'section',
    { className: 'work-section' },
    createElement(
      'div',
      { className: 'runtime-card-head' },
      createElement('h2', {}, '我的日报'),
      createElement('div', { className: 'inline-actions' },
        manualButton,
        autoDraftButton,
      ),
    ),
    createFilterBar(['日期', '状态', '项目', '关键字']),
    activeWorkCreate === 'daily-report' ? createWorkCreatePanel('daily-report', reload, data.projects) : null,
    workActionMessage ? createElement('section', { className: 'runtime-card' }, workActionMessage) : null,
    page.records.length === 0
      ? createElement('section', { className: 'runtime-card' }, '暂无日报')
      : createElement('div', { className: 'simple-stack' }, ...page.records.map((report) => createElement('div', { className: 'list-line clickable-row' }, createElement('span', {}, `${report.date}：${report.content}`), renderStatusPill(report.status, report.status === 'SUBMITTED' ? 'success' : 'warning')))),
    createPagination(page, onPageChange),
  );
}

function createPagination<T>(page: PageResult<T>, onPageChange?: (pageNo: number) => void): HTMLElement {
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

function createTenantSwitcher(systemId: string, navigate: Navigate): HTMLElement | null {
  if (headerTenants.length <= 1) {
    return null;
  }
  const select = createElement('select', { className: 'tenant-switcher', ariaLabel: '租户切换' });
  headerTenants.forEach((tenant) => {
    const option = createElement('option', {}, tenant.tenantName);
    option.value = tenant.tenantId;
    option.disabled = tenant.status !== 1;
    select.append(option);
  });
  select.value = shellState.currentSystem?.tenantId ?? shellState.currentTenant?.tenantId ?? headerTenants[0]?.tenantId ?? '';
  select.addEventListener('change', async () => {
    select.disabled = true;
    try {
      await switchToTenant(systemId, select.value, 'system header tenant switch');
      headerNavigation = undefined;
      headerTenants = [];
      navigate(`/systems/${systemId}/dashboard`);
    } catch (error) {
      select.title = error instanceof Error ? error.message : '租户切换失败。';
    } finally {
      select.disabled = false;
    }
  });
  return select;
}

function createMetric(label: string, value: string): HTMLElement {
  return createElement(
    'article',
    { className: 'metric-card' },
    createElement('span', {}, label),
    createElement('strong', {}, value),
    renderStatusPill('当前', 'info'),
  );
}

function createLoadingPanel(text: string): HTMLElement {
  return createElement('section', { className: 'panel' }, createElement('strong', {}, text));
}

function createErrorPanel(error: unknown): HTMLElement {
  return createElement('section', { className: 'panel' }, createElement('strong', {}, '加载失败'), createElement('p', {}, error instanceof Error ? error.message : '请稍后重试。'));
}

function createSystemAccessDenied(systemId: string, navigate: Navigate): HTMLElement {
  const backButton = createButton('返回系统首页', 'primary', false);
  backButton.addEventListener('click', () => navigate(`/systems/${systemId}/dashboard`));
  return createElement(
    'main',
    { className: 'auth-layout' },
    createElement(
      'section',
      { className: 'auth-card' },
      createElement('div', { className: 'auth-brand' }, shellState.currentSystem?.systemName ?? 'unexamine'),
      createElement('h1', {}, '无权限访问系统后台'),
      createElement('p', {}, '当前系统成员没有后台管理权限，后台入口由当前系统角色权限决定。'),
      backButton,
    ),
  );
}

function createSystemContextError(error: unknown, navigate: Navigate, systemId?: string): HTMLElement {
  const backButton = createButton('返回平台工作台', 'primary', false);
  const applyButton = systemId ? createButton('申请成员映射', 'secondary', false) : null;
  backButton.addEventListener('click', () => navigate('/platform'));
  applyButton?.addEventListener('click', () => navigate(`/no-member?systemId=${encodeURIComponent(systemId ?? '')}`));
  return createElement(
    'main',
    { className: 'auth-layout' },
    createElement(
      'section',
      { className: 'auth-card' },
      createElement('div', { className: 'auth-brand' }, 'unexamine'),
      createElement('h1', {}, '无法进入系统'),
      createElement('p', {}, error instanceof Error ? error.message : String(error)),
      createElement('div', { className: 'inline-actions' }, backButton, applyButton),
    ),
  );
}

function createNavButton(label: string, path: string, navigate: Navigate): HTMLButtonElement {
  const button = createButton(label, 'ghost', false);
  button.addEventListener('click', () => navigate(path));
  return button;
}

function flattenTodoTypes(nodes: TodoTypeNode[]): TodoTypeNode[] {
  return nodes.flatMap((node) => [node, ...flattenTodoTypes(node.children ?? [])]);
}

function activeSystemId(): string {
  if (!shellState.currentSystem?.systemId) {
    throw new Error('系统上下文尚未建立');
  }
  return shellState.currentSystem.systemId;
}

function routeSystemId(route: string): string | undefined {
  return route.match(/^\/systems\/([^/]+)/)?.[1];
}
