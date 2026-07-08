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
  loadSystemHomePageConfig,
  loadSystemModuleNavigation,
  loadSystemTodos,
  loadWorkDashboard,
  loadWorkProjects,
  markSystemMessageRead,
  markAllSystemMessagesRead,
  messageTargetToPath,
  todoTargetToPath,
  type DailyReport,
  type DailyReportAutoDraft,
  type MessageCard,
  type RuntimeLiveData,
  type TodoRow,
  type TodoAction,
  type TodoSearchResult,
  type TodoTypeNode,
  type CalendarDay,
  type HomePageConfigView,
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
import {
  readRuntimeDraftEntries,
  readRuntimeRecentEntries,
  renderRuntimeRecordPage,
  runtimeModulePath,
} from '../runtime/records/runtimeRecords';
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
let activeDailyReportDraft: DailyReportAutoDraft | undefined;
let headerNavigation: Pick<RuntimeLiveData, 'groups' | 'modules'> | undefined;
let headerTenants: TenantOption[] = [];
let todoPageNo = 1;
let todoKeyword = '';
let activeTodoDetail: TodoRow | undefined;
let todoActionResult: string | undefined;
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
    if (isSystemAdminRoute(route) && !canEnterSystemAdmin()) {
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
  if (isSystemAdminRoute(route)) {
    return renderSystemAdmin(navigate, systemAdminSectionFromRoute(route));
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

function isSystemAdminRoute(route: string): boolean {
  return /^\/systems\/[^/]+\/admin(?:\/[^/]+)?$/.test(route);
}

function systemAdminSectionFromRoute(route: string): string {
  return route.match(/^\/systems\/[^/]+\/admin\/([^/?#]+)/)?.[1] ?? 'system-info';
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

interface SystemDashboardData {
  dashboard: WorkDashboard;
  homePage: HomePageConfigView;
  todos: TodoSearchResult;
  messages: PageResult<MessageCard>;
  projectTasks: PageResult<WorkTask>;
  plainTasks: PageResult<WorkTask>;
}

function createDashboard(navigate: Navigate): HTMLElement {
  const root = createElement('section', { className: 'content-panel', dataset: { systemDashboard: 'true' } });
  root.replaceChildren(createLoadingPanel('正在读取系统首页行动数据...'));
  void Promise.all([
    loadWorkDashboard(activeSystemId()),
    loadSystemHomePageConfig(activeSystemId()),
    loadSystemTodos(activeSystemId(), { pageNo: 1, pageSize: 5, status: 'PENDING' }),
    loadSystemMessages(activeSystemId(), { pageNo: 1, pageSize: 5, readStatus: 'unread', archiveStatus: 'active' }),
    loadProjectTasks(activeSystemId(), { pageNo: 1, pageSize: 5 }),
    loadPlainTasks(activeSystemId(), { pageNo: 1, pageSize: 5 }),
  ])
    .then(([dashboard, homePage, todos, messages, projectTasks, plainTasks]) => {
      const hasRuntimeModules = (headerNavigation?.modules ?? []).length > 0;
      const data: SystemDashboardData = { dashboard, homePage, todos, messages, projectTasks, plainTasks };
      const isEmptyAdminDashboard = !hasRuntimeModules && canEnterSystemAdmin();
      if (isEmptyAdminDashboard) {
        root.replaceChildren(
          createElement(
            'div',
            { className: 'page-heading', dataset: { systemDashboardHeading: 'true', r97EmptyDashboardHeading: 'true' } },
            createElement('h1', {}, '系统初始化'),
            createElement('p', {}, '当前系统还没有发布的业务模块。先完成初始化，再把运行态工作台交给普通成员使用。'),
          ),
          createSystemInitializationPrompt(navigate),
          createTraceLine(dashboard.traceId ?? `trace_dashboard_${activeSystemId()}`),
        );
        return;
      }
      root.replaceChildren(
        createElement('div', { className: 'page-heading', dataset: { systemDashboardHeading: 'true' } }, createElement('h1', {}, homePage.title || '系统工作台'), createElement('p', {}, homePage.subtitle || '从这里处理今天的工作、待办、消息和业务数据。')),
        createHomeOverviewPanel(homePage, dashboard, hasRuntimeModules),
        createDashboardDailyActionHub(data, navigate, hasRuntimeModules),
        createDashboardRuntimeEfficiencyPanel(navigate),
        createHomeOperationsPanel(dashboard),
        createTraceLine(dashboard.traceId ?? `trace_dashboard_${activeSystemId()}`),
      );
    })
    .catch((error) => root.replaceChildren(createErrorPanel(error)));
  return root;
}
function createSystemInitializationPrompt(navigate: Navigate): HTMLElement {
  const systemId = activeSystemId();
  const adminButton = createButton('进入系统初始化', 'primary', false);
  adminButton.dataset.r97EmptyDashboardPrimaryAction = 'system-admin-first-use';
  adminButton.addEventListener('click', () => navigate(`/systems/${systemId}/admin`));
  const moduleButton = createButton('配置业务模块', 'secondary', false);
  moduleButton.dataset.r97EmptyDashboardSecondaryAction = 'module-config';
  moduleButton.addEventListener('click', () => navigate(`/systems/${systemId}/admin/module-config`));
  const orgButton = createButton('配置组织成员', 'secondary', false);
  orgButton.dataset.r97EmptyDashboardSecondaryAction = 'org-structure';
  orgButton.addEventListener('click', () => navigate(`/systems/${systemId}/admin/org-structure`));
  const steps = [
    '确认系统信息与访问地址',
    '配置组织、成员和账号绑定',
    '配置角色、权限和数据范围',
    '创建模块分组、模块和字段',
    '发布检查后再开放运行态',
  ];
  return createElement(
    'section',
    {
      className: 'onboarding-panel system-first-use-dashboard',
      dataset: {
        r97EmptySystemDashboard: 'true',
        emptyDashboardPrimarySurface: 'initialization',
        dashboardActionHubSuppressed: 'true',
        dashboardRuntimePanelsSuppressed: 'true',
      },
    },
    createElement('div', { className: 'runtime-card-head' },
      createElement('div', {}, createElement('h2', {}, '先完成系统初始化'), createElement('p', {}, '这里暂不展示今日行动、业务搜索和运行态日历，避免把未配置系统误认为已经可用。')),
      renderStatusPill('待初始化', 'warning'),
    ),
    createElement(
      'div',
      { className: 'onboarding-mini-grid', dataset: { r97EmptyDashboardSteps: String(steps.length) } },
      ...steps.map((step, index) => createElement('span', { dataset: { r97EmptyDashboardStep: String(index + 1) } }, `${index + 1}. ${step}`)),
    ),
    createElement('div', { className: 'inline-actions' }, adminButton, orgButton, moduleButton),
  );
}

function createHomeOverviewPanel(homePage: HomePageConfigView, dashboard: WorkDashboard, hasRuntimeModules: boolean): HTMLElement {
  const widgets = (homePage.widgets ?? [])
    .filter((widget) => widget.visible !== false)
    .sort((left, right) => (left.sort ?? 0) - (right.sort ?? 0));
  const overviewItems = widgets.length > 0
    ? widgets.map((widget) => [widget.widgetName, homePageWidgetValue(widget.sourceType, dashboard, hasRuntimeModules)] as const)
    : [
        ['项目任务', String(dashboard.overview?.projectTaskCount ?? 0)] as const,
        ['普通任务', String(dashboard.overview?.plainTaskCount ?? 0)] as const,
        ['逾期任务', String(dashboard.overview?.overdueTaskCount ?? 0)] as const,
        ['今日日报', dashboard.overview?.dailyReportSubmitted ? '已提交' : '待提交'] as const,
      ];
  return createElement(
    'section',
    { className: 'home-overview-panel' },
    createElement(
      'div',
      { className: 'runtime-card-head' },
      createElement('div', {}, createElement('h2', {}, '今日概览'), createElement('p', {}, widgets.length > 0 ? '来自后台首页配置，运行态按当前成员权限读取。' : '首页组件未配置，先展示默认工作指标。')),
      renderStatusPill(hasRuntimeModules ? '业务可用' : '待配置模块', hasRuntimeModules ? 'success' : 'warning'),
    ),
    createElement(
      'div',
      { className: 'summary-chip-grid' },
      ...overviewItems.map(([label, value]) => createElement('div', { className: 'summary-chip' }, createElement('span', {}, label), createElement('strong', {}, value))),
    ),
  );
}

function createHomeOperationsPanel(dashboard: WorkDashboard): HTMLElement {
  const warnings = dashboard.todayWarnings ?? [];
  const days = (dashboard.monthlyCalendar ?? []).slice(0, 14);
  return createElement(
    'section',
    { className: 'home-operations-panel' },
    createElement(
      'article',
      { className: 'home-ops-block' },
      createElement('h3', {}, '今日预警'),
      warnings.length === 0
        ? createElement('p', {}, '暂无今日预警')
        : createElement('div', { className: 'simple-stack' }, ...warnings.slice(0, 4).map((warning) => createElement('div', { className: 'list-line' }, createElement('span', {}, warning.title), renderStatusPill(warning.level ?? '预警', warning.level === 'HIGH' ? 'danger' : 'warning')))),
    ),
    createElement(
      'article',
      { className: 'home-ops-block' },
      createElement('h3', {}, '近期日历'),
      days.length === 0
        ? createElement('p', {}, '暂无日历事项')
        : createElement(
            'div',
            { className: 'compact-calendar-grid' },
            ...days.map((day) => createElement('div', { className: 'compact-calendar-day' }, createElement('strong', {}, day.date.slice(-2)), createElement('small', {}, day.items.map((item) => item.title).join(' / ') || '无'))),
          ),
    ),
  );
}

function createDashboardDailyActionHub(data: SystemDashboardData, navigate: Navigate, hasRuntimeModules: boolean): HTMLElement {
  const workButton = createButton('进入工作管理', 'secondary', false);
  workButton.dataset.dashboardQuickAction = 'work';
  workButton.addEventListener('click', () => navigate(`/systems/${activeSystemId()}/work`));

  const quickTaskButton = createButton('记录普通任务', 'primary', false);
  quickTaskButton.dataset.dashboardQuickAction = 'plain-task';
  quickTaskButton.dataset.systemDashboardQuickCreate = 'plain-task';
  quickTaskButton.addEventListener('click', () => {
    activeWorkTab = 'plain';
    activeWorkCreate = 'plain-task';
    navigate(`/systems/${activeSystemId()}/work`);
  });

  const todoButton = createButton('查看待办', 'secondary', false);
  todoButton.dataset.dashboardQuickAction = 'todos';
  todoButton.addEventListener('click', () => navigate(`/systems/${activeSystemId()}/todos`));

  const messageButton = createButton('查看消息', 'secondary', false);
  messageButton.dataset.dashboardQuickAction = 'messages';
  messageButton.addEventListener('click', () => navigate(`/systems/${activeSystemId()}/messages`));

  const moduleButton = createButton(hasRuntimeModules ? '打开业务数据' : '配置业务模块', 'ghost', false);
  moduleButton.dataset.dashboardQuickAction = hasRuntimeModules ? 'modules' : 'admin-modules';
  moduleButton.addEventListener('click', () => {
    navigate(hasRuntimeModules || !canEnterSystemAdmin()
      ? `/systems/${activeSystemId()}/modules`
      : `/systems/${activeSystemId()}/admin`);
  });

  return createElement(
    'section',
    { className: 'dashboard-action-hub', dataset: { systemDashboardDailyHub: 'true', dashboardHasRuntimeModules: String(hasRuntimeModules) } },
    createElement(
      'div',
      { className: 'runtime-card-head' },
      createElement('div', {}, createElement('h2', {}, '今日行动'), createElement('p', {}, '首页只放当前成员马上要处理的工作、待办、消息和业务入口。')),
      renderStatusPill(data.dashboard.overview?.dailyReportSubmitted ? '日报已处理' : '日报待确认', data.dashboard.overview?.dailyReportSubmitted ? 'success' : 'warning'),
    ),
    createElement('div', { className: 'inline-actions', dataset: { systemDashboardQuickActions: 'true' } }, quickTaskButton, workButton, todoButton, messageButton, moduleButton),
    createElement(
      'div',
      { className: 'dashboard-action-grid' },
      createDashboardWorkPreview(data.projectTasks, data.plainTasks, data.dashboard, navigate),
      createDashboardTodoPreview(data.todos, navigate),
      createDashboardMessagePreview(data.messages, navigate),
      createDashboardModulePreview(navigate),
    ),
  );
}

function createDashboardRuntimeEfficiencyPanel(navigate: Navigate): HTMLElement {
  const systemId = activeSystemId();
  const modules = (headerNavigation?.modules ?? []).filter((module) => !module.disabledReason).slice(0, 4);
  const recent = readRuntimeRecentEntries(systemId).slice(0, 4);
  const drafts = readRuntimeDraftEntries(systemId).slice(0, 4);
  const firstModule = modules[0];
  const searchInput = createElement('input', { ariaLabel: '搜索业务数据', dataset: { dashboardRuntimeSearchInputR88: 'true' } });
  searchInput.placeholder = firstModule ? `搜索 ${firstModule.name}` : '暂无可搜索业务模块';
  const searchButton = createButton('搜索业务数据', 'primary', !firstModule, firstModule ? undefined : '当前没有可访问业务模块。');
  searchButton.dataset.dashboardRuntimeSearchR88 = 'true';
  searchButton.addEventListener('click', () => {
    if (firstModule) {
      navigate(runtimeModulePath(systemId, firstModule.moduleId, { keyword: searchInput.value.trim() }));
    }
  });
  const quickCreateButton = createButton('快捷新建业务记录', 'secondary', !firstModule, firstModule ? undefined : '当前没有可访问业务模块。');
  quickCreateButton.dataset.dashboardRuntimeQuickCreateR88 = firstModule?.moduleId ?? '';
  quickCreateButton.addEventListener('click', () => {
    if (firstModule) {
      navigate(runtimeModulePath(systemId, firstModule.moduleId, { mode: 'create' }));
    }
  });

  return createElement(
    'section',
    {
      className: 'dashboard-runtime-efficiency',
      dataset: {
        systemDashboardRuntimeEfficiencyR88: 'true',
        dashboardRuntimeModuleCount: String(modules.length),
        dashboardRuntimeRecentCount: String(recent.length),
        dashboardRuntimeDraftCount: String(drafts.length),
      },
    },
    createElement(
      'div',
      { className: 'runtime-card-head' },
      createElement('div', {}, createElement('h2', {}, '业务效率入口'), createElement('p', {}, '搜索、最近打开、草稿和快捷新建都回到真实运行态模块。')),
      renderStatusPill(firstModule ? '运行态可用' : '暂无模块', firstModule ? 'success' : 'warning'),
    ),
    createElement('div', { className: 'dashboard-runtime-search' }, searchInput, searchButton, quickCreateButton),
    createElement(
      'div',
      { className: 'dashboard-runtime-grid' },
      createElement(
        'article',
        { className: 'home-ops-block', dataset: { dashboardRuntimeRecentListR88: recent.length ? 'populated' : 'empty' } },
        createElement('h3', {}, '最近打开'),
        recent.length === 0
          ? createElement('p', {}, '打开业务记录后会显示在这里。')
          : createElement('div', { className: 'simple-stack' }, ...recent.map((entry) => {
              const row = createElement('button', { className: 'list-line clickable-row', dataset: { dashboardRuntimeRecentItemR88: entry.recordId ?? entry.moduleId } }, createElement('span', {}, entry.title), createElement('small', {}, entry.moduleName));
              row.addEventListener('click', () => navigate(runtimeModulePath(systemId, entry.moduleId, { recordId: entry.recordId })));
              return row;
            })),
      ),
      createElement(
        'article',
        { className: 'home-ops-block', dataset: { dashboardRuntimeDraftListR88: drafts.length ? 'populated' : 'empty' } },
        createElement('h3', {}, '继续草稿'),
        drafts.length === 0
          ? createElement('p', {}, '保存运行态草稿后可从这里继续。')
          : createElement('div', { className: 'simple-stack' }, ...drafts.map((draft) => {
              const row = createElement('button', { className: 'list-line clickable-row', dataset: { dashboardRuntimeDraftItemR88: draft.draftId } }, createElement('span', {}, draft.title), createElement('small', {}, draft.moduleName));
              row.addEventListener('click', () => navigate(runtimeModulePath(systemId, draft.moduleId, { mode: 'draft', draftId: draft.draftId, recordId: draft.recordId })));
              return row;
            })),
      ),
      createElement(
        'article',
        { className: 'home-ops-block', dataset: { dashboardRuntimeModuleQuickListR88: modules.length ? 'populated' : 'empty' } },
        createElement('h3', {}, '业务模块'),
        modules.length === 0
          ? createElement('p', {}, '暂无可访问业务模块。')
          : createElement('div', { className: 'simple-stack' }, ...modules.map((module) => {
              const row = createElement('button', { className: 'list-line clickable-row', dataset: { dashboardRuntimeModuleItemR88: module.moduleId } }, createElement('span', {}, module.name), createElement('small', {}, `${module.count ?? 0} 条`));
              row.addEventListener('click', () => navigate(runtimeModulePath(systemId, module.moduleId)));
              return row;
            })),
      ),
    ),
  );
}
function createDashboardWorkPreview(projectTasks: PageResult<WorkTask>, plainTasks: PageResult<WorkTask>, dashboard: WorkDashboard, navigate: Navigate): HTMLElement {
  const merged = new Map<string, WorkTask>();
  [...(dashboard.myTasks ?? []), ...projectTasks.records, ...plainTasks.records].forEach((task) => merged.set(task.taskId, task));
  const tasks = [...merged.values()].slice(0, 5);
  const openWork = () => navigate(`/systems/${activeSystemId()}/work`);
  return createElement(
    'article',
    { className: 'home-ops-block', dataset: { systemDashboardWorkPreview: 'true', previewCount: String(tasks.length) } },
    createElement('h3', {}, '我的工作'),
    tasks.length === 0
      ? createElement('p', {}, '暂无需要处理的工作任务。')
      : createElement('div', { className: 'simple-stack' }, ...tasks.map((task) => {
          const row = createElement('button', { className: 'list-line clickable-row', dataset: { dashboardWorkTask: task.taskId, taskType: task.taskType } }, createElement('span', {}, task.title), renderStatusPill(task.status?.itemName ?? '待处理', task.warningLevel === 'HIGH' ? 'danger' : 'info'));
          row.addEventListener('click', openWork);
          return row;
        })),
  );
}

function createDashboardTodoPreview(todos: TodoSearchResult, navigate: Navigate): HTMLElement {
  const rows = todos.page.records.slice(0, 5);
  return createElement(
    'article',
    { className: 'home-ops-block', dataset: { systemDashboardTodoPreview: 'true', previewCount: String(rows.length), totalCount: String(todos.page.total) } },
    createElement('h3', {}, `待办 ${todos.page.total}`),
    rows.length === 0
      ? createElement('p', {}, '当前没有待处理事项。')
      : createElement('div', { className: 'simple-stack' }, ...rows.map((todo) => {
          const row = createElement('button', { className: 'list-line clickable-row', dataset: { dashboardTodo: todo.todoId } }, createElement('span', {}, todo.title), renderStatusPill(todo.priority ?? '普通', todo.priority === 'HIGH' ? 'danger' : 'warning'));
          row.addEventListener('click', () => navigate(todoTargetToPath(todo.target, activeSystemId())));
          return row;
        })),
  );
}

function createDashboardMessagePreview(messages: PageResult<MessageCard>, navigate: Navigate): HTMLElement {
  const rows = messages.records.slice(0, 5);
  return createElement(
    'article',
    { className: 'home-ops-block', dataset: { systemDashboardMessagePreview: 'true', previewCount: String(rows.length), totalCount: String(messages.total) } },
    createElement('h3', {}, `未读消息 ${messages.total}`),
    rows.length === 0
      ? createElement('p', {}, '当前没有未读消息。')
      : createElement('div', { className: 'simple-stack' }, ...rows.map((message) => {
          const row = createElement('button', { className: 'list-line clickable-row', dataset: { dashboardMessage: message.messageId } }, createElement('span', {}, message.title), renderStatusPill(message.type, message.readStatus === 'read' ? 'success' : 'info'));
          row.addEventListener('click', async () => {
            await markSystemMessageRead(activeSystemId(), message.messageId).catch(() => undefined);
            navigate(messageTargetToPath(message.target, activeSystemId()));
          });
          return row;
        })),
  );
}

function createDashboardModulePreview(navigate: Navigate): HTMLElement {
  const modules = (headerNavigation?.modules ?? []).filter((module) => !module.disabledReason).slice(0, 5);
  return createElement(
    'article',
    { className: 'home-ops-block', dataset: { systemDashboardModulePreview: 'true', previewCount: String(modules.length) } },
    createElement('h3', {}, '业务入口'),
    modules.length === 0
      ? createElement('p', {}, '还没有可访问的业务模块。')
      : createElement('div', { className: 'simple-stack' }, ...modules.map((module) => {
          const row = createElement('button', { className: 'list-line clickable-row', dataset: { dashboardModule: module.moduleId } }, createElement('span', {}, module.name), createElement('small', {}, `${module.count ?? 0} 条`));
          row.addEventListener('click', () => navigate(`/systems/${activeSystemId()}/modules?moduleId=${encodeURIComponent(module.moduleId)}`));
          return row;
        })),
  );
}
function homePageWidgetValue(sourceType: string, dashboard: WorkDashboard, hasRuntimeModules: boolean): string {
  if (sourceType === 'WORK_DASHBOARD') {
    return `${dashboard.overview?.projectTaskCount ?? 0} 项目 / ${dashboard.overview?.plainTaskCount ?? 0} 普通`;
  }
  if (sourceType === 'WORK_WARNING') {
    return `${dashboard.todayWarnings?.length ?? 0} 条`;
  }
  if (sourceType === 'WORK_CALENDAR') {
    return `${dashboard.monthlyCalendar?.length ?? 0} 天`;
  }
  if (sourceType === 'RUNTIME_MODULES') {
    return hasRuntimeModules ? '已发布' : '待配置';
  }
  return '已启用';
}

function createTodoWorkbench(navigate: Navigate): HTMLElement {
  const root = createElement('section', { className: 'content-panel', dataset: { systemTodoWorkbench: 'true', systemTodoWorkbenchR87: 'true' } });
  const load = () => {
    root.replaceChildren(createLoadingPanel('正在读取待办...'));
    void loadSystemTodos(activeSystemId(), {
      typeCode: activeTodoType,
      pageNo: todoPageNo,
      pageSize: 20,
      keyword: todoKeyword,
    })
      .then((todos) => {
        const selected = activeTodoDetail ? todos.page.records.find((row) => row.todoId === activeTodoDetail?.todoId) : undefined;
        activeTodoDetail = selected ?? todos.page.records[0];
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
    { className: 'content-panel', dataset: { systemTodoWorkbench: 'true', systemTodoWorkbenchR87: 'true' } },
    createElement('div', { className: 'page-heading' }, createElement('h1', {}, '待办'), createElement('p', {}, '先查看待办上下文和可用动作，再进入关联业务或审批对象。')),
    createElement(
      'div',
      {
        className: 'todo-layout todo-workbench-layout',
        dataset: {
          systemTodoLayout: 'true',
          todoWorkbenchSelected: activeTodoDetail?.todoId ?? '',
          todoWorkbenchTotal: String(todos.page.total),
        },
      },
      createTodoTypeTree(todos.typeTree, reload),
      createElement(
        'section',
        { className: 'panel todo-list-panel', dataset: { systemTodoListPanel: 'true' } },
        createKeywordFilterBar({
          label: '待办关键字',
          value: todoKeyword,
          onApply: (keyword) => {
            todoKeyword = keyword;
            todoPageNo = 1;
            activeTodoDetail = undefined;
            todoActionResult = undefined;
            reload();
          },
          onReset: () => {
            todoKeyword = '';
            todoPageNo = 1;
            activeTodoDetail = undefined;
            todoActionResult = undefined;
            reload();
          },
        }),
        todoActionResult ? createElement('section', { className: 'runtime-card workbench-result-card', dataset: { systemTodoActionResult: 'true' } }, todoActionResult) : null,
        createTodoTable(todos.page.records, reload),
        createPagination(todos.page, (nextPage) => {
          todoPageNo = nextPage;
          activeTodoDetail = undefined;
          todoActionResult = undefined;
          reload();
        }),
      ),
      createTodoDetailPanel(activeTodoDetail, navigate, reload),
    ),
    createTraceLine(todos.traceId),
  );
}

function createTodoTypeTree(nodes: TodoTypeNode[], reload: () => void): HTMLElement {
  const buttons = flattenTodoTypes(nodes).map((node) => {
    const button = createElement('button', { className: `sidebar-item${activeTodoType === node.typeCode ? ' active' : ''}`, dataset: { systemTodoType: node.typeCode } }, `${node.typeName} (${node.count})`);
    button.addEventListener('click', () => {
      activeTodoType = node.typeCode;
      todoPageNo = 1;
      activeTodoDetail = undefined;
      todoActionResult = undefined;
      reload();
    });
    return button;
  });
  return createElement('aside', { className: 'module-sidebar compact', dataset: { systemTodoTypeTree: 'true' } }, ...buttons);
}

function createTodoTable(rows: TodoRow[], reload: () => void): HTMLElement {
  if (rows.length === 0) {
    return createElement('section', { className: 'runtime-card', dataset: { systemTodoEmptyState: 'true' } }, '当前筛选下没有待办。');
  }
  return createElement(
    'div',
    { className: 'table-shell', dataset: { systemTodoTable: 'true' } },
    createElement(
      'table',
      { className: 'data-table' },
      createElement('thead', {}, createElement('tr', {}, createElement('th', {}, '序号'), createElement('th', {}, '标题'), createElement('th', {}, '来源'), createElement('th', {}, '优先级'), createElement('th', {}, '到期'), createElement('th', {}, '状态'), createElement('th', {}, '操作'))),
      createElement('tbody', {}, ...rows.map((row, index) => createTodoRow(row, index, reload))),
    ),
  );
}

function createTodoRow(row: TodoRow, index: number, reload: () => void): HTMLElement {
  const selected = activeTodoDetail?.todoId === row.todoId;
  const tr = createElement('tr', {
    className: `clickable-row${selected ? ' selected-row' : ''}`,
    dataset: {
      systemTodoRow: row.todoId,
      todoSelected: String(selected),
      todoStatus: row.status,
      todoPriority: row.priority ?? '',
      todoTargetType: row.target?.targetType ?? '',
    },
  });
  tr.addEventListener('click', () => {
    activeTodoDetail = row;
    todoActionResult = undefined;
    reload();
  });
  tr.append(
    createElement('td', {}, index + 1),
    createElement('td', {}, createElement('strong', {}, row.title), createElement('small', {}, row.objectTitle ?? row.moduleCode ?? '')),
    createElement('td', {}, row.sourceName),
    createElement('td', {}, row.priority ?? '-'),
    createElement('td', {}, row.dueAt ?? '-'),
    createElement('td', {}, renderStatusPill(row.status, row.status === 'DONE' ? 'success' : 'warning')),
    createElement('td', { className: 'row-actions' }, ...createTodoActionButtons(row, reload)),
  );
  return tr;
}

function createTodoDetailPanel(row: TodoRow | undefined, navigate: Navigate, reload: () => void): HTMLElement {
  if (!row) {
    return createElement(
      'aside',
      { className: 'panel todo-detail-panel', dataset: { systemTodoDetailPanel: 'empty' } },
      createElement('h2', {}, '待办明细'),
      createElement('p', {}, '选择左侧待办后查看目标、状态、可用动作和处理回执。'),
    );
  }
  const openButton = createButton('打开关联', 'secondary', false);
  openButton.dataset.systemTodoOpenTarget = row.todoId;
  openButton.addEventListener('click', () => navigate(todoTargetToPath(row.target, activeSystemId())));
  const disabledActions = row.actionPermissions.filter((action) => !action.enabled);
  return createElement(
    'aside',
    {
      className: 'panel todo-detail-panel',
      dataset: {
        systemTodoDetailPanel: row.todoId,
        todoStatus: row.status,
        todoTargetType: row.target?.targetType ?? '',
      },
    },
    createElement('div', { className: 'detail-title-row' }, createElement('h2', {}, '待办明细'), renderStatusPill(row.status, row.status === 'DONE' ? 'success' : 'warning')),
    createElement('h3', {}, row.title),
    createElement('div', { className: 'todo-detail-grid' },
      createDetailMetric('来源', row.sourceName),
      createDetailMetric('业务对象', row.objectTitle ?? row.moduleCode ?? '-'),
      createDetailMetric('优先级', row.priority ?? '普通'),
      createDetailMetric('到期时间', row.dueAt ?? '未设置'),
      createDetailMetric('目标类型', row.target?.targetType ?? '无目标'),
      createDetailMetric('traceId', row.traceId),
    ),
    createElement('div', { className: 'inline-actions', dataset: { systemTodoDetailActions: row.todoId } }, openButton, ...createTodoActionButtons(row, reload)),
    disabledActions.length > 0
      ? createElement('section', { className: 'runtime-card disabled-card', dataset: { systemTodoDisabledReasons: row.todoId } }, createElement('strong', {}, '不可用动作'), ...disabledActions.map((action) => createElement('p', {}, `${action.actionName}: ${action.disabledReason ?? '当前状态不可执行。'}`)))
      : null,
  );
}

function createTodoActionButtons(row: TodoRow, reload: () => void): HTMLButtonElement[] {
  return row.actionPermissions
    .filter((action) => action.actionCode !== 'open')
    .map((action) => {
      const button = createButton(action.actionName, action.enabled ? 'ghost' : 'secondary', !action.enabled, action.disabledReason);
      button.dataset.systemTodoAction = action.actionCode;
      button.dataset.systemTodoId = row.todoId;
      button.dataset.todoId = row.todoId;
      button.addEventListener('click', async (event) => {
        event.stopPropagation();
        await runTodoAction(row, action, reload);
      });
      return button;
    });
}

async function runTodoAction(row: TodoRow, action: TodoAction, reload: () => void): Promise<void> {
  const payload = await todoActionPayload(action.actionCode);
  if (payload === null) {
    return;
  }
  try {
    const result = await executeSystemTodoAction(activeSystemId(), row.todoId, action.actionCode, payload);
    todoActionResult = `${action.actionName}：${result.status}${result.message ? `，${result.message}` : ''}${result.traceId ? `，traceId=${result.traceId}` : ''}`;
    activeTodoDetail = { ...row, status: result.status || row.status, traceId: result.traceId ?? row.traceId };
  } catch (error) {
    todoActionResult = `${action.actionName}失败：${error instanceof Error ? error.message : '请稍后重试。'}`;
  }
  reload();
}

function createDetailMetric(label: string, value: string): HTMLElement {
  return createElement('div', {}, createElement('span', {}, label), createElement('strong', {}, value));
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
  const root = createElement('section', { className: 'content-panel message-center', dataset: { systemMessageCenter: 'true', systemMessageWorkbenchR87: 'true' } });
  const reload = () => {
    root.replaceChildren(createLoadingPanel('正在读取消息...'));
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
    {
      className: 'content-panel message-center',
      dataset: {
        systemMessageCenter: 'true',
        systemMessageWorkbenchR87: 'true',
        messageReadFilter: messageReadStatus,
        messageArchiveFilter: messageArchiveStatus,
        messageTotal: String(page.total),
      },
    },
    createElement('div', { className: 'page-heading' }, createElement('h1', {}, '消息'), createElement('p', {}, '消息按时间流展示，可先标为已读、归档或打开关联目标；状态变化必须来自后台回读。')),
    createMessageToolbar(page, reload),
    messageActionResult ? createElement('section', { className: 'runtime-card workbench-result-card', dataset: { systemMessageActionResult: 'true' } }, messageActionResult) : null,
    page.records.length === 0
      ? createElement('section', { className: 'runtime-card', dataset: { systemMessageEmptyState: messageArchiveStatus } }, messageArchiveStatus === 'archived' ? '暂无归档消息。' : '当前筛选下没有消息。')
      : createElement('div', { className: 'message-stream detail-message-stream', dataset: { systemMessageStream: 'true' } }, ...page.records.map((message) => createMessageItem(message, navigate, reload))),
    createPagination(page, (nextPage) => {
      messagePageNo = nextPage;
      reload();
    }),
  );
}

function createMessageToolbar(page: PageResult<MessageCard>, reload: () => void): HTMLElement {
  const markAllButton = createButton('全部标为已读', 'secondary', page.total === 0 || messageArchiveStatus === 'archived', page.total === 0 ? '当前筛选没有消息。' : '归档消息不需要批量标记。');
  markAllButton.dataset.systemMessageMarkAll = 'true';
  markAllButton.addEventListener('click', async () => {
    const result = await markAllSystemMessagesRead(activeSystemId(), {
      keyword: messageKeyword,
      readStatus: messageReadStatus,
      archiveStatus: messageArchiveStatus,
    });
    messageActionResult = `已读更新：${result.affectedCount} 条，traceId=${result.traceId}`;
    reload();
  });

  return createElement(
    'div',
    { className: 'simple-stack message-toolbar', dataset: { systemMessageToolbar: 'true' } },
    createKeywordFilterBar({
      label: '消息关键字',
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
      createMessageStateButton('全部', 'all', messageReadStatus, (value) => {
        messageReadStatus = value;
        messagePageNo = 1;
        messageActionResult = undefined;
        reload();
      }),
      createMessageStateButton('未读', 'unread', messageReadStatus, (value) => {
        messageReadStatus = value;
        messagePageNo = 1;
        messageActionResult = undefined;
        reload();
      }),
      createMessageStateButton('已读', 'read', messageReadStatus, (value) => {
        messageReadStatus = value;
        messagePageNo = 1;
        messageActionResult = undefined;
        reload();
      }),
      createMessageArchiveButton('当前消息', 'active', reload),
      createMessageArchiveButton('已归档', 'archived', reload),
      markAllButton,
    ),
  );
}

function createMessageStateButton<T extends string>(label: string, value: T, activeValue: T, onSelect: (value: T) => void): HTMLButtonElement {
  const button = createButton(label, value === activeValue ? 'primary' : 'ghost', false);
  button.dataset.systemMessageReadFilterButton = String(value);
  button.addEventListener('click', () => onSelect(value));
  return button;
}

function createMessageArchiveButton(label: string, value: 'active' | 'archived', reload: () => void): HTMLButtonElement {
  const button = createButton(label, value === messageArchiveStatus ? 'primary' : 'ghost', false);
  button.dataset.systemMessageArchiveFilterButton = value;
  button.addEventListener('click', () => {
    messageArchiveStatus = value;
    messagePageNo = 1;
    messageActionResult = undefined;
    reload();
  });
  return button;
}

function createMessageItem(message: MessageCard, navigate: Navigate, reload: () => void): HTMLElement {
  const readButton = createButton('标为已读', 'ghost', message.readStatus === 'read', message.readStatus === 'read' ? '消息已经是已读状态。' : undefined);
  readButton.dataset.systemMessageMarkRead = message.messageId;
  readButton.addEventListener('click', async (event) => {
    event.stopPropagation();
    await markSystemMessageRead(activeSystemId(), message.messageId);
    messageActionResult = `已读更新：1 条，messageId=${message.messageId}`;
    reload();
  });

  const archiveButton = createButton('归档', 'ghost', message.archiveStatus === 'archived', message.archiveStatus === 'archived' ? '消息已归档。' : undefined);
  archiveButton.dataset.systemMessageArchive = message.messageId;
  archiveButton.addEventListener('click', async (event) => {
    event.stopPropagation();
    const result = await archiveSystemMessage(activeSystemId(), message.messageId);
    messageActionResult = `已归档：${result.affectedCount} 条，traceId=${result.traceId}`;
    reload();
  });

  const openButton = createButton('打开关联', 'secondary', false);
  openButton.dataset.systemMessageOpenTarget = message.messageId;
  openButton.addEventListener('click', async (event) => {
    event.stopPropagation();
    await markSystemMessageRead(activeSystemId(), message.messageId).catch(() => undefined);
    navigate(messageTargetToPath(message.target, activeSystemId()));
  });

  const item = createElement(
    'article',
    {
      className: `message-item message-jump-card${message.readStatus === 'read' ? ' read' : ''}`,
      dataset: {
        systemMessageItem: message.messageId,
        messageReadStatus: message.readStatus,
        messageArchiveStatus: message.archiveStatus,
        messageTargetType: message.target?.targetType ?? '',
      },
    },
    createElement('div', { className: 'message-title-row' }, createElement('strong', {}, message.title), createElement('time', {}, message.createdAt)),
    createElement('p', {}, message.content),
    createElement('div', { className: 'message-meta-grid' },
      createDetailMetric('模板', message.templateCode),
      createDetailMetric('类型', message.type),
      createDetailMetric('目标', message.target?.targetType ?? '无目标'),
      createDetailMetric('traceId', message.traceId),
    ),
    createElement('div', { className: 'inline-actions' }, renderStatusPill(message.readStatus, message.readStatus === 'read' ? 'success' : 'warning'), renderStatusPill(message.archiveStatus, message.archiveStatus === 'archived' ? 'info' : 'success'), readButton, archiveButton, openButton),
  );
  item.addEventListener('click', async () => {
    await markSystemMessageRead(activeSystemId(), message.messageId).catch(() => undefined);
    navigate(messageTargetToPath(message.target, activeSystemId()));
  });
  return item;
}
function createWorkPreview(): HTMLElement {
  const root = createElement('section', { className: 'content-panel workbench-page', dataset: { systemWorkbench: 'true' } });
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
    { className: 'content-panel workbench-page', dataset: { systemWorkbench: 'true', workActiveTab: activeWorkTab, workProjectTaskView: projectTaskView, workPlainTaskView: plainTaskView } },
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
    { className: 'detail-tabs work-tabs', dataset: { workTabs: 'true' } },
    ...tabs.map(([tab, label]) => {
      const button = createElement('button', { className: activeWorkTab === tab ? 'active' : '', dataset: { workTab: tab, active: String(activeWorkTab === tab) } }, label);
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
    { className: 'work-section', dataset: { workDashboard: 'true' } },
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
    { className: 'panel', dataset: { workWarnings: 'true' } },
    createElement('h3', {}, '今日预警'),
    warnings.length === 0 ? createElement('p', {}, '暂无今日预警') : createElement('div', { className: 'simple-stack' }, ...warnings.map((warning) => createElement('div', { className: 'list-line' }, createElement('span', {}, warning.title), renderStatusPill(warning.level ?? '预警', warning.level === 'HIGH' ? 'danger' : 'warning')))),
  );
}

function createCalendarPanel(days: CalendarDay[]): HTMLElement {
  const visibleDays = days.slice(0, 31);
  return createElement(
    'article',
    { className: 'panel', dataset: { workCalendar: 'true' } },
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
  const taskCreateType = title === '项目任务' ? 'project-task' : 'plain-task';
  const taskKind = taskCreateType === 'project-task' ? 'project' : 'plain';
  const projectCreateButton = title === '项目任务' ? createWorkCreateButton('新建项目', 'project', data, render) : null;
  return createElement(
    'section',
    { className: 'work-section', dataset: { workTaskSection: taskKind, workTaskView: view } },
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
    workActionMessage ? createElement('section', { className: 'runtime-card', dataset: { workActionResult: 'true', workTaskActionResult: 'true' } }, workActionMessage) : null,
    view === 'list' ? createTaskList(page, selectTask, onPageChange, taskKind) : createTaskKanban(page.records, selectTask, taskKind),
    activeWorkTask ? createTaskDetailCard(activeWorkTask) : null,
  );
}

function createViewButton(label: string, active: boolean, onClick: () => void): HTMLButtonElement {
  const button = createButton(label, active ? 'primary' : 'secondary', false);
  button.dataset.workView = label;
  button.dataset.workViewActive = String(active);
  button.addEventListener('click', onClick);
  return button;
}

function createWorkCreateButton(label: string, createType: 'project' | 'project-task' | 'plain-task' | 'daily-report', data: WorkData, render: (data: WorkData) => void): HTMLButtonElement {
  const button = createButton(label, 'primary', false);
  button.dataset.workCreate = createType;
  button.addEventListener('click', () => {
    activeWorkCreate = activeWorkCreate === createType ? undefined : createType;
    activeDailyReportDraft = undefined;
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
  submitButton.dataset.workCreateSubmit = createType;
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
          dueAt: dueAtInput.value ? normalizeLocalDateTimeForApi(dueAtInput.value) : undefined,
          fieldValues: contentInput.value.trim() ? { description: contentInput.value.trim() } : {},
        });
        workActionMessage = `项目任务已创建：${task.title}`;
        projectTaskPageNo = 1;
      } else if (createType === 'plain-task') {
        const task = await createPlainTask(activeSystemId(), {
          title: titleInput.value.trim() || `普通任务 ${Date.now()}`,
          dueAt: dueAtInput.value ? normalizeLocalDateTimeForApi(dueAtInput.value) : undefined,
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
      activeDailyReportDraft = undefined;
      reload();
    } catch (error) {
      workActionMessage = error instanceof Error ? error.message : '保存失败。';
      reload();
    }
  });
  return createElement(
    'section',
    { className: 'runtime-card form-grid', dataset: { workCreatePanel: createType } },
    createElement('label', {}, createElement('span', {}, createType === 'project' ? '项目名称' : '标题'), titleInput),
    createType === 'project-task'
      ? createElement('label', {}, createElement('span', {}, '关联项目'), projectSelect, projects.records.length === 0 ? createElement('small', {}, '暂无项目，可先点击“新建项目”。') : null)
      : null,
    createType === 'project-task' || createType === 'plain-task' ? createElement('label', {}, createElement('span', {}, '截止时间'), dueAtInput) : null,
    createType !== 'project' ? createElement('label', {}, createElement('span', {}, '内容'), contentInput) : null,
    createElement('div', { className: 'inline-actions' }, submitButton),
  );
}

function normalizeLocalDateTimeForApi(value: string): string {
  return value.length === 16 ? `${value}:00` : value;
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

function createTaskList(page: PageResult<WorkTask>, selectTask: (task: WorkTask) => void, onPageChange: (pageNo: number) => void, taskKind: string): HTMLElement {
  if (page.records.length === 0) {
    return createElement('section', { className: 'runtime-card', dataset: { workTaskEmpty: taskKind } }, '暂无任务');
  }
  return createElement(
    'div',
    { className: 'table-shell', dataset: { workTaskList: taskKind } },
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
  const row = createElement('tr', { className: 'clickable-row', dataset: { workTaskRow: task.taskId, workTaskType: task.taskType } });
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

function createTaskKanban(tasks: WorkTask[], selectTask: (task: WorkTask) => void, taskKind: string): HTMLElement {
  const groups = new Map<string, WorkTask[]>();
  tasks.forEach((task) => {
    const key = task.status?.itemName ?? task.status?.itemCode ?? '未设置';
    groups.set(key, [...(groups.get(key) ?? []), task]);
  });
  return createElement(
    'div',
    { className: 'kanban-preview', dataset: { workTaskKanban: taskKind } },
    ...Array.from(groups.entries()).map(([status, groupTasks]) =>
      createElement(
        'article',
        { className: 'kanban-column', dataset: { workKanbanColumn: status } },
        createElement('strong', {}, status),
        ...groupTasks.map((task) => {
          const card = createElement('button', { className: 'kanban-card', dataset: { workKanbanCard: task.taskId, workTaskType: task.taskType } }, task.title, createElement('small', {}, `${task.assignee?.memberName ?? '-'} / ${task.progress ?? 0}% / ${task.commentCount ?? 0} 评论`));
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
    { className: 'panel task-detail-card', dataset: { workTaskDetail: task.taskId, workTaskType: task.taskType } },
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
  autoDraftButton.dataset.workDailyAutoDraft = 'true';
  autoDraftButton.addEventListener('click', async () => {
    try {
      const draft = await autoDraftDailyReport(activeSystemId());
      activeDailyReportDraft = draft;
      workActionMessage = `自动草稿已生成，请确认后保存：${draft.content}`;
    } catch (error) {
      workActionMessage = error instanceof Error ? error.message : '自动生成日报草稿失败。';
    }
    reload();
  });
  return createElement(
    'section',
    { className: 'work-section', dataset: { workDailyReports: 'true' } },
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
    workActionMessage ? createElement('section', { className: 'runtime-card', dataset: { workActionResult: 'true', workDailyActionResult: 'true' } }, workActionMessage) : null,
    activeDailyReportDraft ? createDailyReportDraftPanel(activeDailyReportDraft, reload) : null,
    page.records.length === 0
      ? createElement('section', { className: 'runtime-card', dataset: { workDailyEmpty: 'true' } }, '暂无日报')
      : createElement('div', { className: 'simple-stack' }, ...page.records.map((report) => createElement('div', { className: 'list-line clickable-row' }, createElement('span', {}, `${report.date}：${report.content}`), renderStatusPill(report.status, report.status === 'SUBMITTED' ? 'success' : 'warning')))),
    createPagination(page, onPageChange),
  );
}

function createDailyReportDraftPanel(draft: DailyReportAutoDraft, reload: () => void): HTMLElement {
  const confirmButton = createButton('确认保存为草稿', 'primary', false);
  confirmButton.dataset.workDailyDraftConfirm = 'true';
  confirmButton.addEventListener('click', async () => {
    try {
      const report = await createDailyReport(activeSystemId(), {
        reportDate: draft.reportDate,
        content: draft.content,
        status: 'DRAFT',
        sourceIds: [draft.draftId],
        submitNow: false,
      });
      activeDailyReportDraft = undefined;
      dailyReportPageNo = 1;
      workActionMessage = `日报草稿已保存：${report.date}`;
      reload();
    } catch (error) {
      workActionMessage = error instanceof Error ? error.message : '保存日报草稿失败。';
      reload();
    }
  });
  return createElement(
    'section',
    { className: 'runtime-card', dataset: { workDailyDraft: draft.draftId, manualConfirmRequired: String(draft.manualConfirmRequired) } },
    createElement('strong', {}, '待确认日报草稿'),
    createElement('p', {}, draft.content),
    createElement('small', {}, `reportDate=${draft.reportDate} / traceId=${draft.traceId}`),
    createElement('div', { className: 'inline-actions' }, confirmButton),
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
