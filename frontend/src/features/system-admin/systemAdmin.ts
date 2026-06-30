import {
  createOpenApiApp,
  createSystemDataSource,
  createSystemDictItem,
  createSystemDictType,
  createSystemFlow,
  createSystemAgentPolicy,
  createSystemDepartment,
  createSystemMember,
  createSystemModule,
  createSystemModuleAction,
  createSystemModuleField,
  createSystemRole,
  loadFlowCanvas,
  loadFlowNodeLibrary,
  listSystemModuleFields,
  loadSystemAdminData,
  precheckSystemSsoOrgSync,
  publishSystemFlow,
  publishSystemModule,
  rollbackSystemModule,
  saveFlowCanvas,
  checkSystemDataSourceConnection,
  rotateOpenApiSecret,
  runFlowPublishCheck,
  runModulePublishCheck,
  runSystemDataSourcePublishCheck,
  runSystemAgentPolicyPublishCheck,
  runWorkConfigPublishCheck,
  simulateSystemFlow,
  updateSystemWorkConfig,
  updateSystemSsoPolicy,
  type AgentPolicyView,
  type AuditLogView,
  type BackendModule,
  type BackendModuleGroup,
  type DictTypeView,
  type DepartmentNode,
  type FlowDefinitionView,
  type FlowCanvasView,
  type FlowEdgeView,
  type FlowNodeConfigView,
  type FlowNodeLibraryItem,
  type MemberView,
  type ModulePublishCheckResult,
  type OpenApiAppView,
  type RoleView,
  type SystemAdminData,
  type SystemAdminPageOptions,
  type SystemDataSourceCheckResult,
  type SystemDataSourceView,
} from '../../api/liveData';
import type { FieldDefinitionVO, PageResult } from '../../api/types';
import type { Navigate } from '../../app/app';
import { shellState } from '../../app/state';
import { createButton, createElement, createTraceLine, type ChildNodeValue } from '../../shared/components';
import { requestConfirmation, requestFormInput, requestTextInput } from '../../shared/dialogs';
import { createFilterBar } from '../../shared/filters';
import { renderStatusPill, type StatusTone } from '../../shared/status';

export function renderSystemAdmin(navigate: Navigate): HTMLElement {
  const systemId = activeSystemId();
  let activeSection = 'system-info';
  let latestData: SystemAdminData | null = null;
  const pageOptions: SystemAdminPageOptions = {};
  const content = createElement('div', { className: 'admin-content' }, createLoadingPanel('正在读取系统后台数据...'));
  const root = createElement('section', { className: 'admin-layout' }, content);
  const changePage = (key: keyof SystemAdminPageOptions, pageNo: number) => {
    pageOptions[key] = Math.max(1, pageNo);
    reload();
  };
  const showSection = (sectionId: string) => {
    activeSection = sectionId;
    root.replaceChildren(createSystemAdminSidebar(navigate, activeSection, showSection), content);
    if (latestData) {
      content.replaceChildren(...createSystemAdminContent(latestData, systemId, reload, activeSection, showSection, changePage));
    }
  };
  const reload = () => {
    content.replaceChildren(createLoadingPanel('正在读取系统后台数据...'));
    void loadSystemAdminData(systemId, pageOptions)
      .then((data) => {
        latestData = data;
        content.replaceChildren(...createSystemAdminContent(data, systemId, reload, activeSection, showSection, changePage));
      })
      .catch((error) => content.replaceChildren(createErrorPanel(error)));
  };

  root.replaceChildren(createSystemAdminSidebar(navigate, activeSection, showSection), content);
  reload();

  return root;
}

function createSystemAdminSidebar(navigate: Navigate, activeSection: string, onSelect: (targetId: string) => void): HTMLElement {
  const backButton = createButton('返回业务首页', 'secondary', false);
  backButton.addEventListener('click', () => navigate(`/systems/${activeSystemId()}/dashboard`));
  return createElement(
    'aside',
    { className: 'module-sidebar admin-sidebar' },
    createElement('strong', {}, '系统后台'),
    backButton,
    createSidebarButton('系统信息', 'system-info', activeSection === 'system-info', onSelect),
    createSidebarButton('组织架构', 'org-structure', activeSection === 'org-structure', onSelect),
    createSidebarButton('角色管理', 'role-management', activeSection === 'role-management', onSelect),
    createSidebarButton('模块管理', 'module-config', activeSection === 'module-config', onSelect),
    createSidebarButton('流程管理', 'flow-management', activeSection === 'flow-management', onSelect),
    createSidebarButton('字典管理', 'dict-management', activeSection === 'dict-management', onSelect),
    createSidebarButton('仪表盘管理', 'dashboard-config', activeSection === 'dashboard-config', onSelect),
    createSidebarButton('数据源', 'data-source', activeSection === 'data-source', onSelect),
    createSidebarButton('对外应用', 'openapi-apps', activeSection === 'openapi-apps', onSelect),
    createSidebarButton('工作配置', 'work-config', activeSection === 'work-config', onSelect),
    createSidebarButton('统一认证', 'sso-config', activeSection === 'sso-config', onSelect),
    createSidebarButton('AI Agent', 'agent-config', activeSection === 'agent-config', onSelect),
    createSidebarButton('日志管理', 'log-management', activeSection === 'log-management', onSelect),
  );
}

function createSystemAdminContent(
  data: SystemAdminData,
  systemId: string,
  reload: () => void,
  activeSection: string,
  onSelect: (targetId: string) => void,
  onPageChange: (key: keyof SystemAdminPageOptions, pageNo: number) => void,
): HTMLElement[] {
  const systemName = shellState.currentSystem?.systemName ?? shellState.availableSystems.find((system) => system.systemId === systemId)?.systemName ?? '当前系统';
  const warningPanel = createWarningPanel(data.warnings);
  const panels: Record<string, HTMLElement> = {
    'system-info': createSystemInfoPanel(data, systemName, onSelect),
    'org-structure': createOrgRolePanel(data.departments, data.members, data.roles, systemId, reload, 'org-structure', (pageNo) => onPageChange('membersPageNo', pageNo), (pageNo) => onPageChange('rolesPageNo', pageNo)),
    'role-management': createOrgRolePanel(data.departments, data.members, data.roles, systemId, reload, 'role-management', (pageNo) => onPageChange('membersPageNo', pageNo), (pageNo) => onPageChange('rolesPageNo', pageNo)),
    'module-config': createModuleConfigPanel(data.moduleGroups, data.modules, systemId, reload, (pageNo) => onPageChange('modulesPageNo', pageNo)),
    'flow-management': createFlowAndDictPanel(data.flows, data.dictTypes, systemId, reload, 'flow-management', (pageNo) => onPageChange('flowsPageNo', pageNo), (pageNo) => onPageChange('dictTypesPageNo', pageNo)),
    'dict-management': createFlowAndDictPanel(data.flows, data.dictTypes, systemId, reload, 'dict-management', (pageNo) => onPageChange('flowsPageNo', pageNo), (pageNo) => onPageChange('dictTypesPageNo', pageNo)),
    'dashboard-config': createDashboardConfigPanel(data),
    'data-source': createDataSourcePanel(data.dataSources, systemId, reload, (pageNo) => onPageChange('dataSourcesPageNo', pageNo)),
    'openapi-apps': createIntegrationPanel(data.openApiApps, systemId, reload, (pageNo) => onPageChange('openApiAppsPageNo', pageNo)),
    'work-config': createWorkAndAgentPanel(data, systemId, reload, 'work-config'),
    'sso-config': createSystemSsoLogPanel(data, systemId, reload, 'sso-config', (pageNo) => onPageChange('logsPageNo', pageNo)),
    'agent-config': createWorkAndAgentPanel(data, systemId, reload, 'agent-config', (pageNo) => onPageChange('agentPoliciesPageNo', pageNo)),
    'log-management': createSystemSsoLogPanel(data, systemId, reload, 'log-management', (pageNo) => onPageChange('logsPageNo', pageNo)),
  };
  return [
    createElement(
      'div',
      { className: 'page-heading' },
      createElement('h1', {}, '系统后台'),
      createElement('p', {}, `${systemName} 的后台配置，只影响当前系统内的组织、角色、模块、流程、字典、工作、SSO、Agent 和日志。`),
    ),
    ...(warningPanel ? [warningPanel] : []),
    panels[activeSection] ?? panels['system-info'],
  ];
}

function createSystemInfoPanel(data: SystemAdminData, systemName: string, onSelect: (targetId: string) => void): HTMLElement {
  return createElement(
    'section',
    { id: 'system-info', className: 'panel' },
    createElement('h2', {}, '系统信息'),
    createElement(
      'div',
      { className: 'metric-grid' },
      createMetric('当前系统', systemName),
      createMetric('成员数量', String(data.members.total)),
      createMetric('模块数量', String(data.modules.total)),
      createMetric('流程数量', String(data.flows.total)),
    ),
    createElement(
      'div',
      { className: 'simple-stack' },
      createElement('div', { className: 'list-line' }, createElement('span', {}, '当前租户'), createElement('strong', {}, shellState.currentSystem?.tenantId ?? shellState.availableSystems[0]?.tenantId ?? '-')),
      createElement('div', { className: 'list-line' }, createElement('span', {}, '系统角色'), createElement('span', {}, shellState.account.systemRoles.join(' / ') || '未授权')),
      createElement('div', { className: 'list-line' }, createElement('span', {}, '权限快照'), createElement('span', {}, shellState.currentSystem?.permissionSnapshotSummary?.permissionVersion ?? '-')),
    ),
    shellState.currentSystem?.permissionSnapshotSummary?.snapshotId
      ? createTraceLine(shellState.currentSystem.permissionSnapshotSummary.snapshotId)
      : null,
    createInitializationGuide(data, onSelect),
  );
}

function createInitializationGuide(data: SystemAdminData, onSelect: (targetId: string) => void): HTMLElement {
  const steps = [
    {
      title: '组织与成员',
      status: data.members.total > 1 || data.departments.length > 0 ? '已开始' : '待配置',
      target: 'org-structure',
      action: '配置组织',
      hint: '建立部门、员工和成员映射，普通成员才能进入系统业务页。',
    },
    {
      title: '角色权限',
      status: data.roles.total > 1 ? '已开始' : '待配置',
      target: 'role-management',
      action: '配置角色',
      hint: '把后台权限、业务模块权限和数据范围落到系统角色。',
    },
    {
      title: '业务模块',
      status: data.modules.total > 0 ? '已开始' : '待配置',
      target: 'module-config',
      action: '配置模块',
      hint: '创建模块、字段、列表场景、行操作和发布检查。',
    },
    {
      title: '流程与字典',
      status: data.flows.total > 0 || data.dictTypes.total > 0 ? '已开始' : '待配置',
      target: 'flow-management',
      action: '配置流程',
      hint: '发布审批流程和业务字典后，运行态才能形成记录、审批、待办和消息闭环。',
    },
    {
      title: '工作与集成',
      status: data.workConfig ? '已开始' : '待配置',
      target: 'work-config',
      action: '配置工作',
      hint: '配置任务、日报、Agent、SSO、OpenAPI 等长期使用能力。',
    },
  ];
  return createElement(
    'section',
    { className: 'onboarding-panel' },
    createElement('div', { className: 'runtime-card-head' },
      createElement('div', {}, createElement('h3', {}, '系统初始化清单'), createElement('p', {}, '按这个顺序把空系统配置到可以交给普通成员使用。')),
      renderStatusPill(data.modules.total > 0 ? '可继续完善' : '待初始化', data.modules.total > 0 ? 'info' : 'warning'),
    ),
    createElement(
      'div',
      { className: 'onboarding-steps' },
      ...steps.map((step, index) => {
        const button = createButton(step.action, step.status === '待配置' ? 'primary' : 'secondary', false);
        button.addEventListener('click', () => onSelect(step.target));
        return createElement(
          'article',
          { className: 'onboarding-step' },
          createElement('span', { className: 'step-index' }, String(index + 1)),
          createElement('div', {}, createElement('strong', {}, step.title), createElement('p', {}, step.hint)),
          renderStatusPill(step.status, step.status === '待配置' ? 'warning' : 'info'),
          button,
        );
      }),
    ),
  );
}

function createDashboardConfigPanel(data: SystemAdminData): HTMLElement {
  return createElement(
    'section',
    { id: 'dashboard-config', className: 'panel' },
    createElement('h2', {}, '仪表盘管理'),
    createElement(
      'div',
      { className: 'metric-grid' },
      createMetric('成员数量', String(data.members.total)),
      createMetric('模块数量', String(data.modules.total)),
      createMetric('流程数量', String(data.flows.total)),
      createMetric('日志数量', String(data.logs.total)),
    ),
    createElement(
      'div',
      { className: 'simple-stack' },
      createElement('div', { className: 'list-line' }, createElement('span', {}, '项目任务看板'), createElement('strong', {}, objectKeys(data.workConfig?.projectTaskKanban))),
      createElement('div', { className: 'list-line' }, createElement('span', {}, '普通任务看板'), createElement('strong', {}, objectKeys(data.workConfig?.plainTaskKanban))),
      createElement('div', { className: 'list-line' }, createElement('span', {}, '工作配置状态'), createElement('strong', {}, data.workConfig ? '已配置' : '-')),
    ),
    data.workConfig?.traceId ? createTraceLine(data.workConfig.traceId) : null,
  );
}

function createOrgRolePanel(
  departments: DepartmentNode[],
  members: PageResult<MemberView>,
  roles: PageResult<RoleView>,
  systemId: string,
  reload: () => void,
  activeSection: 'org-structure' | 'role-management',
  onMembersPageChange: (nextPage: number) => void,
  onRolesPageChange: (nextPage: number) => void,
): HTMLElement {
  const result = createElement('p', { className: 'field-error' }, '组织、员工和角色会写入当前系统，并受当前系统/租户上下文约束。');
  const departmentButton = createButton('新建部门', 'primary', false);
  const memberButton = createButton('新建员工', 'primary', false);
  const roleButton = createButton('新建角色', 'primary', false);
  departmentButton.addEventListener('click', async () => {
    const values = await requestFormInput('新建部门', [
      { name: 'deptName', label: '部门名称' },
      { name: 'deptCode', label: '部门编码', defaultValue: `dept_${Date.now()}` },
      { name: 'parentId', label: '父部门 ID', required: false },
    ], '创建');
    if (!values) {
      return;
    }
    departmentButton.disabled = true;
    departmentButton.textContent = '创建中...';
    try {
      const dept = await createSystemDepartment(systemId, {
        parentId: values.parentId || undefined,
        deptCode: values.deptCode,
        deptName: values.deptName,
      });
      result.textContent = `部门已创建：${dept.deptName}`;
      reload();
    } catch (error) {
      departmentButton.disabled = false;
      departmentButton.textContent = '新建部门';
      result.textContent = error instanceof Error ? error.message : '创建部门失败。';
    }
  });
  memberButton.addEventListener('click', async () => {
    const values = await requestFormInput('新建员工', [
      { name: 'memberName', label: '员工姓名' },
      { name: 'deptId', label: '所属部门 ID', defaultValue: firstDepartmentId(departments), required: false },
      { name: 'employeeNo', label: '工号', required: false },
      { name: 'mobile', label: '手机号', required: false },
    ], '创建');
    if (!values) {
      return;
    }
    memberButton.disabled = true;
    memberButton.textContent = '创建中...';
    try {
      const member = await createSystemMember(systemId, {
        deptId: values.deptId || undefined,
        memberName: values.memberName,
        employeeNo: values.employeeNo || undefined,
        mobile: values.mobile || undefined,
      });
      result.textContent = `员工已创建：${member.memberName}`;
      reload();
    } catch (error) {
      memberButton.disabled = false;
      memberButton.textContent = '新建员工';
      result.textContent = error instanceof Error ? error.message : '创建员工失败。';
    }
  });
  roleButton.addEventListener('click', async () => {
    const values = await requestFormInput('新建角色', [
      { name: 'roleName', label: '角色名称' },
      { name: 'roleCode', label: '角色编码', defaultValue: `role_${Date.now()}` },
    ], '创建');
    if (!values) {
      return;
    }
    roleButton.disabled = true;
    roleButton.textContent = '创建中...';
    try {
      const role = await createSystemRole(systemId, {
        roleName: values.roleName,
        roleCode: values.roleCode,
        roleType: 'CUSTOM',
      });
      result.textContent = `角色已创建：${role.roleName}`;
      reload();
    } catch (error) {
      roleButton.disabled = false;
      roleButton.textContent = '新建角色';
      result.textContent = error instanceof Error ? error.message : '创建角色失败。';
    }
  });
  const isRoleSection = activeSection === 'role-management';
  return createElement(
    'section',
    { id: activeSection, className: 'panel' },
    createElement('h2', {}, isRoleSection ? '角色管理' : '组织架构'),
    createElement('div', { className: 'inline-actions' }, ...(isRoleSection ? [roleButton] : [departmentButton, memberButton])),
    result,
    isRoleSection
      ? createElement(
          'div',
          { className: 'admin-stack' },
          createFilterBar(['角色', '类型', '状态']),
          createRoleTable(roles, onRolesPageChange),
        )
      : createElement(
          'div',
          { className: 'tree-table-layout' },
          createDepartmentTree(departments),
          createElement(
            'div',
            { className: 'admin-stack' },
            createFilterBar(['员工', '状态', '绑定状态']),
            createMemberTable(members, onMembersPageChange),
          ),
        ),
  );
}

function createDepartmentTree(departments: DepartmentNode[]): HTMLElement {
  const nodes = flattenDepartments(departments);
  return createElement(
    'aside',
    { className: 'mini-tree' },
    createElement('strong', {}, '部门树'),
    nodes.length === 0
      ? createElement('p', {}, '暂无部门')
      : createElement(
          'div',
          { className: 'simple-stack' },
          ...nodes.map(({ node, depth }) =>
            createTreeButton(`${'　'.repeat(depth)}${node.deptName}`, depth === 0),
          ),
        ),
  );
}

function createMemberTable(page: PageResult<MemberView>, onPageChange: (nextPage: number) => void): HTMLElement {
  return createElement(
    'section',
    { className: 'result-panel' },
    createElement('h3', {}, '员工列表'),
    createTable(
      ['序号', '姓名', '工号', '部门', '绑定', '状态', '更新时间'],
      page.records.map((member, index) => [
        index + 1,
        member.memberName,
        member.employeeNo || '-',
        member.deptId || '-',
        member.bindingStatus || '-',
        renderStatusPill(enableStatusText(member.status), enableStatusTone(member.status)),
        formatTime(member.updatedAt),
      ]),
      '暂无员工。',
    ),
    createPagination(page, onPageChange),
  );
}

function createRoleTable(page: PageResult<RoleView>, onPageChange: (nextPage: number) => void): HTMLElement {
  return createElement(
    'section',
    { className: 'result-panel' },
    createElement('h3', {}, '角色管理'),
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
      '暂无系统角色。',
    ),
    createPagination(page, onPageChange),
  );
}

type ModuleFilterState = {
  keyword: string;
  groupId: string;
  publishStatus: string;
  status: string;
};

const MODULE_FIELD_TYPES = [
  { code: 'TEXT', name: '文本', hint: '适合名称、说明、编号等短文本' },
  { code: 'DATE', name: '日期', hint: '适合计划日期、发生日期' },
  { code: 'SELECT', name: '单选', hint: '绑定数据字典后作为状态、分类' },
  { code: 'ATTACHMENT', name: '附件', hint: '上传文件并随记录保存' },
  { code: 'AUTO_NUMBER', name: '自动编号', hint: '按模块规则生成连续编号' },
];

function createModuleConfigPanel(
  groups: BackendModuleGroup[],
  modules: PageResult<BackendModule>,
  systemId: string,
  reload: () => void,
  onModulePageChange: (nextPage: number) => void,
): HTMLElement {
  const result = createElement('p', { className: 'field-error' }, '模块创建、发布检查、发布和回滚会调用真实后台接口并返回 traceId。');
  const createModuleButton = createButton('新建模块', 'primary', false);
  const filterState: ModuleFilterState = {
    keyword: '',
    groupId: '',
    publishStatus: '',
    status: '',
  };
  let selectedModuleId = modules.records[0]?.moduleId ?? '';
  const moduleListHost = createElement('div', { className: 'module-builder-list' });
  const workspaceHost = createElement('div', { className: 'module-builder-workspace' });

  const filteredModules = () => modules.records.filter((module) => {
    const keyword = filterState.keyword.trim().toLowerCase();
    const matchesKeyword = !keyword
      || module.name.toLowerCase().includes(keyword)
      || module.moduleCode.toLowerCase().includes(keyword);
    const matchesGroup = !filterState.groupId || module.groupId === filterState.groupId;
    const matchesPublish = !filterState.publishStatus || (module.publishStatus ?? '').toUpperCase() === filterState.publishStatus;
    const matchesStatus = !filterState.status || String(module.status) === filterState.status;
    return matchesKeyword && matchesGroup && matchesPublish && matchesStatus;
  });

  const renderModuleBuilder = () => {
    const visibleModules = filteredModules();
    if (!visibleModules.some((module) => module.moduleId === selectedModuleId)) {
      selectedModuleId = visibleModules[0]?.moduleId ?? '';
    }
    const selectedModule = visibleModules.find((module) => module.moduleId === selectedModuleId);
    moduleListHost.replaceChildren(createModuleListPanel(visibleModules, selectedModuleId, (moduleId) => {
      selectedModuleId = moduleId;
      renderModuleBuilder();
    }));
    workspaceHost.replaceChildren(createModuleWorkspace(systemId, selectedModule, reload, result));
  };

  createModuleButton.addEventListener('click', async () => {
    const values = await requestFormInput('新建模块', [
      { name: 'name', label: '模块名称' },
      { name: 'moduleCode', label: '模块编码', defaultValue: `module_${Date.now()}` },
    ], '创建');
    if (!values) {
      return;
    }
    createModuleButton.disabled = true;
    createModuleButton.textContent = '创建中...';
    result.textContent = '正在创建模块并初始化默认字段、动作和列表配置...';
    try {
      const module = await createSystemModule(systemId, {
        groupId: groups[0]?.groupId,
        moduleCode: values.moduleCode,
        name: values.name,
        status: 1,
      });
      result.textContent = `模块已创建：${module.name} / ${module.moduleCode}`;
      reload();
    } catch (error) {
      createModuleButton.disabled = false;
      createModuleButton.textContent = '新建模块';
      result.textContent = error instanceof Error ? error.message : '创建模块失败。';
    }
  });
  renderModuleBuilder();
  return createElement(
    'section',
    { id: 'module-config', className: 'panel' },
    createElement('h2', {}, '模块管理'),
    createElement(
      'div',
      { className: 'tree-table-layout module-config-layout' },
      createModuleTree(groups),
      createElement(
        'div',
        { className: 'admin-stack' },
        createElement(
          'div',
          { className: 'module-builder-toolbar' },
          createElement('div', {}, createElement('strong', {}, '模块配置'), createElement('p', {}, '先筛选/选择模块，再配置字段、动作和发布。')),
          createElement('div', { className: 'inline-actions' }, createModuleButton),
        ),
        result,
        createModuleFilterBar(groups, filterState, renderModuleBuilder),
        createElement('div', { className: 'module-builder' }, moduleListHost, workspaceHost),
        createPagination(modules, onModulePageChange),
      ),
    ),
  );
}

function createModuleTree(groups: BackendModuleGroup[]): HTMLElement {
  return createElement(
    'aside',
    { className: 'mini-tree' },
    createElement('strong', {}, '模块分组'),
    groups.length === 0
      ? createElement('p', {}, '暂无模块分组')
      : createElement(
          'div',
          { className: 'simple-stack' },
          ...groups.map((group, index) => createTreeButton(group.name, index === 0)),
        ),
  );
}

function createModuleFilterBar(groups: BackendModuleGroup[], state: ModuleFilterState, onApply: () => void): HTMLElement {
  const keywordInput = createElement('input', { ariaLabel: '模块名称或编码' });
  keywordInput.placeholder = '模块名称或编码';
  keywordInput.value = state.keyword;

  const groupSelect = createElement('select', { ariaLabel: '模块分组' });
  groupSelect.append(createElement('option', {}, '全部分组'));
  groupSelect.options[0].value = '';
  groups.forEach((group) => {
    const option = createElement('option', {}, group.name);
    option.value = group.groupId;
    option.selected = state.groupId === group.groupId;
    groupSelect.append(option);
  });

  const publishSelect = createElement('select', { ariaLabel: '发布状态' });
  [
    ['', '全部发布状态'],
    ['DRAFT', '草稿'],
    ['PUBLISHED', '已发布'],
  ].forEach(([value, label]) => {
    const option = createElement('option', {}, label);
    option.value = value;
    option.selected = state.publishStatus === value;
    publishSelect.append(option);
  });

  const statusSelect = createElement('select', { ariaLabel: '状态' });
  [
    ['', '全部状态'],
    ['1', '启用'],
    ['0', '停用'],
  ].forEach(([value, label]) => {
    const option = createElement('option', {}, label);
    option.value = value;
    option.selected = state.status === value;
    statusSelect.append(option);
  });

  const applyButton = createButton('应用筛选', 'primary', false);
  const resetButton = createButton('重置', 'ghost', false);
  const apply = () => {
    state.keyword = keywordInput.value.trim();
    state.groupId = groupSelect.value;
    state.publishStatus = publishSelect.value;
    state.status = statusSelect.value;
    onApply();
  };
  keywordInput.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      apply();
    }
  });
  applyButton.addEventListener('click', apply);
  resetButton.addEventListener('click', () => {
    state.keyword = '';
    state.groupId = '';
    state.publishStatus = '';
    state.status = '';
    onApply();
  });

  return createElement(
    'div',
    { className: 'filter-bar module-filter-bar' },
    createElement('label', {}, createElement('span', {}, '模块'), keywordInput),
    createElement('label', {}, createElement('span', {}, '分组'), groupSelect),
    createElement('label', {}, createElement('span', {}, '发布'), publishSelect),
    createElement('label', {}, createElement('span', {}, '状态'), statusSelect),
    applyButton,
    resetButton,
  );
}

function createModuleListPanel(modules: BackendModule[], selectedModuleId: string, onSelect: (moduleId: string) => void): HTMLElement {
  if (modules.length === 0) {
    return createElement('section', { className: 'result-panel' }, createElement('strong', {}, '暂无匹配模块'), createElement('p', {}, '调整筛选条件或新建模块后继续配置。'));
  }
  return createElement(
    'section',
    { className: 'module-list-panel' },
    createElement('strong', {}, '模块列表'),
    ...modules.map((module) => {
      const button = createButton(`${module.name}`, module.moduleId === selectedModuleId ? 'primary' : 'ghost', false);
      button.classList.add('module-list-item');
      button.addEventListener('click', () => onSelect(module.moduleId));
      return createElement(
        'article',
        { className: module.moduleId === selectedModuleId ? 'module-list-card active' : 'module-list-card' },
        createElement('div', {}, button, createElement('small', {}, module.moduleCode)),
        createElement('div', { className: 'module-list-meta' }, renderStatusPill(enableStatusText(Number(module.status)), enableStatusTone(Number(module.status))), createElement('span', {}, module.publishStatus || 'DRAFT')),
      );
    }),
  );
}

function createModuleWorkspace(systemId: string, module: BackendModule | undefined, reload: () => void, result: HTMLElement): HTMLElement {
  if (!module) {
    return createElement('section', { className: 'result-panel' }, createElement('strong', {}, '未选择模块'), createElement('p', {}, '请选择或新建一个模块。'));
  }
  const actionButton = createButton('新增动作', 'secondary', false);
  const checkButton = createButton('发布检查', 'secondary', false);
  const publishButton = createButton('发布', 'primary', false);
  const rollbackButton = createButton('回滚', 'ghost', module.publishStatus !== 'PUBLISHED', module.publishStatus === 'PUBLISHED' ? undefined : '只有已发布模块可以回滚。');
  actionButton.addEventListener('click', async () => {
    const values = await requestFormInput('新增动作', [
      { name: 'actionName', label: '动作名称' },
      { name: 'actionCode', label: '动作编码', defaultValue: `action_${Date.now()}` },
      { name: 'position', label: '动作位置', defaultValue: 'ROW' },
    ], '保存');
    if (!values) {
      return;
    }
    actionButton.disabled = true;
    actionButton.textContent = '保存中...';
    try {
      await createSystemModuleAction(systemId, module.moduleId, {
        actionCode: values.actionCode,
        actionName: values.actionName,
        position: values.position || 'ROW',
      });
      result.textContent = `动作已保存：${values.actionName} / ${values.actionCode}。请执行模块发布检查后发布。`;
      reload();
    } catch (error) {
      actionButton.disabled = false;
      actionButton.textContent = '新增动作';
      result.textContent = error instanceof Error ? error.message : '新增动作失败。';
    }
  });
  checkButton.addEventListener('click', async () => {
    await runModuleCheckButton(systemId, module, checkButton, result);
  });
  publishButton.addEventListener('click', async () => {
    if (!(await requestConfirmation('发布模块', `确认发布模块「${module.name}」吗？发布前会自动执行检查。`, '发布'))) {
      return;
    }
    publishButton.disabled = true;
    publishButton.textContent = '发布中...';
    try {
      const check = await runModulePublishCheck(systemId, module.moduleId, '发布前自动检查');
      if (!check.passed) {
        result.textContent = moduleCheckMessage(check);
        publishButton.disabled = false;
        publishButton.textContent = '发布';
        return;
      }
      const published = await publishSystemModule(systemId, module.moduleId, '系统后台发布模块');
      result.textContent = `模块已发布：${published.version ?? published.result}，traceId=${published.traceId}`;
      reload();
    } catch (error) {
      publishButton.disabled = false;
      publishButton.textContent = '发布失败';
      result.textContent = error instanceof Error ? error.message : '发布模块失败。';
    }
  });
  rollbackButton.addEventListener('click', async () => {
    if (rollbackButton.disabled || !(await requestConfirmation('回滚模块', `确认回滚模块「${module.name}」吗？`, '回滚'))) {
      return;
    }
    rollbackButton.disabled = true;
    rollbackButton.textContent = '回滚中...';
    try {
      const rollback = await rollbackSystemModule(systemId, module.moduleId, '系统后台回滚模块');
      result.textContent = `模块已回滚：${rollback.version ?? rollback.result}，traceId=${rollback.traceId}`;
      reload();
    } catch (error) {
      rollbackButton.disabled = false;
      rollbackButton.textContent = '回滚失败';
      result.textContent = error instanceof Error ? error.message : '回滚模块失败。';
    }
  });
  return createElement(
    'section',
    { className: 'module-work-panel' },
    createElement(
      'div',
      { className: 'module-work-head' },
      createElement('div', {}, createElement('h3', {}, module.name), createElement('p', {}, `${module.moduleCode} / ${module.currentVersion || '未发布版本'}`)),
      createElement('div', { className: 'module-work-actions' }, checkButton, publishButton, rollbackButton),
    ),
    createElement('div', { className: 'module-work-meta' }, renderStatusPill(enableStatusText(Number(module.status)), enableStatusTone(Number(module.status))), createElement('span', {}, `发布状态：${module.publishStatus || 'DRAFT'}`), createElement('span', {}, `分组：${module.groupId}`)),
    createFieldBuilder(systemId, module, result),
    createElement(
      'div',
      { className: 'module-secondary-actions' },
      actionButton,
      createElement('span', {}, '字段保存后可继续配置行操作、发布检查和发布。'),
    ),
  );
}

function createFieldBuilder(systemId: string, module: BackendModule, result: HTMLElement): HTMLElement {
  const fieldListHost = createElement('div', { className: 'field-builder-list' }, createElement('p', {}, '正在读取字段...'));
  void listSystemModuleFields(systemId, module.moduleId)
    .then((fields) => {
      fieldListHost.replaceChildren(createFieldList(fields.records));
    })
    .catch((error) => {
      fieldListHost.replaceChildren(createElement('p', { className: 'field-error' }, error instanceof Error ? error.message : '字段读取失败。'));
    });

  const fieldNameInput = createElement('input', { ariaLabel: '字段名称' });
  fieldNameInput.placeholder = '例如：客户名称';
  const fieldCodeInput = createElement('input', { ariaLabel: '字段编码' });
  fieldCodeInput.placeholder = `field_${Date.now()}`;
  let selectedType = MODULE_FIELD_TYPES[0].code;
  const typeButtons = MODULE_FIELD_TYPES.map((type) => {
    const button = createButton(type.name, type.code === selectedType ? 'primary' : 'ghost', false, type.hint);
    button.dataset.fieldType = type.code;
    button.addEventListener('click', () => {
      selectedType = type.code;
      typeButtons.forEach((item) => {
        item.className = `button ${item.dataset.fieldType === selectedType ? 'primary' : 'ghost'}`;
      });
    });
    return button;
  });
  const requiredInput = createElement('input', { ariaLabel: '必填' });
  requiredInput.type = 'checkbox';
  const sortableInput = createElement('input', { ariaLabel: '可排序' });
  sortableInput.type = 'checkbox';
  sortableInput.checked = true;
  const saveButton = createButton('保存字段', 'primary', false);
  saveButton.addEventListener('click', async () => {
    const name = fieldNameInput.value.trim();
    const fieldCode = fieldCodeInput.value.trim() || `field_${Date.now()}`;
    if (!name) {
      result.textContent = '请先填写字段名称。';
      fieldNameInput.focus();
      return;
    }
    saveButton.disabled = true;
    saveButton.textContent = '保存中...';
    try {
      const field = await createSystemModuleField(systemId, module.moduleId, {
        fieldCode,
        name,
        fieldType: selectedType,
        required: requiredInput.checked,
        sortable: sortableInput.checked,
      });
      result.textContent = `字段已保存：${field.name} / ${field.fieldCode} / ${field.fieldType}。`;
      fieldNameInput.value = '';
      fieldCodeInput.value = '';
      const fields = await listSystemModuleFields(systemId, module.moduleId);
      fieldListHost.replaceChildren(createFieldList(fields.records));
    } catch (error) {
      result.textContent = error instanceof Error ? error.message : '字段保存失败。';
    } finally {
      saveButton.disabled = false;
      saveButton.textContent = '保存字段';
    }
  });

  return createElement(
    'div',
    { className: 'field-builder' },
    createElement('section', {}, createElement('strong', {}, '字段列表'), fieldListHost),
    createElement(
      'section',
      { className: 'field-builder-form' },
      createElement('strong', {}, '表单预览'),
      createElement('label', {}, createElement('span', {}, '字段名称'), fieldNameInput),
      createElement('label', {}, createElement('span', {}, '字段编码'), fieldCodeInput),
      createElement('div', { className: 'field-type-grid' }, ...typeButtons),
    ),
    createElement(
      'section',
      { className: 'field-builder-props' },
      createElement('strong', {}, '字段属性'),
      createElement('label', { className: 'check-row' }, requiredInput, createElement('span', {}, '必填')),
      createElement('label', { className: 'check-row' }, sortableInput, createElement('span', {}, '可排序')),
      saveButton,
      createElement('p', {}, '字段类型固定选择，避免运行态出现不可解析的自由文本类型。'),
    ),
  );
}

function createFieldList(fields: FieldDefinitionVO[]): HTMLElement {
  if (fields.length === 0) {
    return createElement('p', {}, '暂无字段，先从中间表单新增字段。');
  }
  return createElement(
    'div',
    { className: 'simple-stack' },
    ...fields.map((field) => createElement(
      'article',
      { className: 'field-list-card' },
      createElement('strong', {}, field.name),
      createElement('small', {}, `${field.fieldCode} / ${field.fieldType}`),
      createElement('span', {}, field.required ? '必填' : '选填'),
    )),
  );
}

async function runModuleCheckButton(systemId: string, module: BackendModule, button: HTMLButtonElement, result: HTMLElement): Promise<void> {
  button.disabled = true;
  button.textContent = '检查中...';
  try {
    const check = await runModulePublishCheck(systemId, module.moduleId, '系统后台手动发布检查');
    button.disabled = false;
    button.textContent = check.passed ? '检查通过' : '检查失败';
    button.title = `traceId=${check.traceId}`;
    result.textContent = moduleCheckMessage(check);
  } catch (error) {
    button.disabled = false;
    button.textContent = '检查失败';
    result.textContent = error instanceof Error ? error.message : '发布检查失败。';
  }
}

function moduleCheckMessage(check: ModulePublishCheckResult): string {
  const failures = check.failureItems.map((item) => `${item.itemName}：${item.message}`).join('；');
  const warnings = check.warningItems.map((item) => `${item.itemName}：${item.message}`).join('；');
  if (!check.passed) {
    return `发布检查未通过：${failures || '存在阻断项'}，traceId=${check.traceId}`;
  }
  return `发布检查通过${warnings ? `，提醒：${warnings}` : ''}，traceId=${check.traceId}`;
}

function createFlowAndDictPanel(
  flows: PageResult<FlowDefinitionView>,
  dictTypes: PageResult<DictTypeView>,
  systemId: string,
  reload: () => void,
  activeSection: 'flow-management' | 'dict-management',
  onFlowsPageChange: (nextPage: number) => void,
  onDictTypesPageChange: (nextPage: number) => void,
): HTMLElement {
  const flowResult = createElement('p', { className: 'field-error' }, '流程创建后为草稿，必须保存真实节点和连线后才能发布。');
  const dictResult = createElement('p', { className: 'field-error' }, '字典项负责颜色、图标、语义、排序和看板可用性，供字段和工作看板复用。');
  const createFlowButton = createButton('新建流程', 'primary', false);
  const createDictButton = createButton('新建字典', 'primary', false);
  createFlowButton.addEventListener('click', async () => {
    const values = await requestFormInput('新建流程', [
      { name: 'flowName', label: '流程名称' },
      { name: 'flowCode', label: '流程编码', defaultValue: `flow_${Date.now()}` },
      { name: 'boundModuleId', label: '绑定模块 ID', required: false },
    ], '创建');
    if (!values) {
      return;
    }
    createFlowButton.disabled = true;
    createFlowButton.textContent = '创建中...';
    try {
      const flow = await createSystemFlow(systemId, {
        flowCode: values.flowCode,
        flowName: values.flowName,
        boundModuleId: values.boundModuleId || undefined,
      });
      flowResult.textContent = `流程已创建：${flow.flowName} / ${flow.flowCode}。发布前需要配置节点画布。`;
      reload();
    } catch (error) {
      createFlowButton.disabled = false;
      createFlowButton.textContent = '新建流程';
      flowResult.textContent = error instanceof Error ? error.message : '新建流程失败。';
    }
  });
  createDictButton.addEventListener('click', async () => {
    const values = await requestFormInput('新建字典', [
      { name: 'dictName', label: '字典名称' },
      { name: 'dictCode', label: '字典编码', defaultValue: `dict_${Date.now()}` },
    ], '创建');
    if (!values) {
      return;
    }
    createDictButton.disabled = true;
    createDictButton.textContent = '创建中...';
    try {
      const dict = await createSystemDictType(systemId, {
        dictCode: values.dictCode,
        dictName: values.dictName,
        dictKind: 'NORMAL',
      });
      dictResult.textContent = `字典已创建：${dict.dictName} / ${dict.dictCode}`;
      reload();
    } catch (error) {
      createDictButton.disabled = false;
      createDictButton.textContent = '新建字典';
      dictResult.textContent = error instanceof Error ? error.message : '新建字典失败。';
    }
  });
  const flowDesignerHost = createElement('div', { className: 'flow-designer-host' });
  const flowPanel = createElement(
    'div',
    { className: 'result-panel' },
    createElement('h3', {}, '流程管理'),
    createElement('div', { className: 'inline-actions' }, createFlowButton),
    flowResult,
    createFilterBar(['流程名称', '绑定模块', '发布状态']),
    createTable(
      ['序号', '流程', '绑定模块', '状态', '发布', '版本', '操作'],
      flows.records.map((flow, index) => [
        index + 1,
        createElement('span', {}, createElement('strong', {}, flow.flowName), createElement('small', {}, ` ${flow.flowCode}`)),
        flow.boundModuleId || '-',
        renderStatusPill(enableStatusText(flow.status), enableStatusTone(flow.status)),
        flow.publishStatus,
        flow.currentVersion || '-',
        createFlowActions(systemId, flow, reload, flowResult, flowDesignerHost),
      ]),
      '暂无流程。',
    ),
    createPagination(flows, onFlowsPageChange),
    flowDesignerHost,
  );
  const dictPanel = createElement(
    'div',
    { className: 'result-panel' },
    createElement('h3', {}, '数据字典'),
    createElement('div', { className: 'inline-actions' }, createDictButton),
    dictResult,
    createFilterBar(['字典名称', '类型', '状态']),
    createTable(
      ['序号', '字典', '类型', '状态', '预览项', '更新时间', '操作'],
      dictTypes.records.map((dict, index) => [
        index + 1,
        createElement('span', {}, createElement('strong', {}, dict.dictName), createElement('small', {}, ` ${dict.dictCode}`)),
        dict.dictKind || '-',
        renderStatusPill(enableStatusText(dict.status), enableStatusTone(dict.status)),
        String(dict.previewItems?.length ?? 0),
        formatTime(dict.updatedAt),
        createDictActions(systemId, dict, reload, dictResult),
      ]),
      '暂无字典。',
    ),
    createPagination(dictTypes, onDictTypesPageChange),
  );
  return createElement(
    'section',
    { id: activeSection, className: 'panel' },
    createElement('h2', {}, activeSection === 'flow-management' ? '流程管理' : '字典管理'),
    activeSection === 'flow-management' ? flowPanel : dictPanel,
  );
}

function createFlowActions(systemId: string, flow: FlowDefinitionView, reload: () => void, result: HTMLElement, designerHost: HTMLElement): HTMLElement {
  const configureButton = createButton('配置画布', 'secondary', false);
  configureButton.addEventListener('click', () => {
    renderFlowDesigner(systemId, flow, result, designerHost);
  });
  const checkButton = createButton('发布检查', 'secondary', false);
  const publishButton = createButton('发布', 'primary', false);
  checkButton.addEventListener('click', async () => {
    checkButton.disabled = true;
    checkButton.textContent = '检查中...';
    try {
      const check = await runFlowPublishCheck(systemId, flow.flowId, '系统后台流程发布检查');
      checkButton.disabled = false;
      checkButton.textContent = check.passed ? '检查通过' : '检查失败';
      result.textContent = flowCheckMessage(check);
    } catch (error) {
      checkButton.disabled = false;
      checkButton.textContent = '检查失败';
      result.textContent = error instanceof Error ? error.message : '流程发布检查失败。';
    }
  });
  publishButton.addEventListener('click', async () => {
    if (!(await requestConfirmation('发布流程', `确认发布流程「${flow.flowName}」吗？发布会先执行发布检查。`, '发布'))) {
      return;
    }
    publishButton.disabled = true;
    publishButton.textContent = '发布中...';
    try {
      const check = await runFlowPublishCheck(systemId, flow.flowId, '流程发布前自动检查');
      if (!check.passed) {
        result.textContent = flowCheckMessage(check);
        publishButton.disabled = false;
        publishButton.textContent = '发布';
        return;
      }
      const published = await publishSystemFlow(systemId, flow.flowId, '系统后台发布流程');
      result.textContent = `流程已发布：${published.version ?? published.result}，traceId=${published.traceId}`;
      reload();
    } catch (error) {
      publishButton.disabled = false;
      publishButton.textContent = '发布失败';
      result.textContent = error instanceof Error ? error.message : '发布流程失败。';
    }
  });
  return createElement('div', { className: 'row-actions' }, configureButton, checkButton, publishButton);
}

function renderFlowDesigner(systemId: string, flow: FlowDefinitionView, result: HTMLElement, host: HTMLElement): void {
  host.replaceChildren(createLoadingPanel('正在读取流程画布...'));
  void Promise.all([loadFlowCanvas(systemId, flow.flowId), loadFlowNodeLibrary(systemId)])
    .then(([canvas, library]) => {
      host.replaceChildren(createFlowDesigner(systemId, flow, normalizeFlowCanvas(canvas), library, result));
      host.scrollIntoView({ block: 'nearest' });
    })
    .catch((error) => {
      host.replaceChildren(createElement('p', { className: 'field-error' }, error instanceof Error ? error.message : '流程画布读取失败。'));
    });
}

function createFlowDesigner(
  systemId: string,
  flow: FlowDefinitionView,
  initialCanvas: FlowCanvasView,
  library: FlowNodeLibraryItem[],
  result: HTMLElement,
): HTMLElement {
  let nodes = initialCanvas.nodes.slice();
  let edges = initialCanvas.edges.slice();
  let selectedNodeKey = nodes[0]?.nodeKey ?? '';
  const designer = createElement('section', { className: 'flow-designer' });
  const status = createElement('p', { className: 'field-error' }, flowCanvasSummary(nodes, edges));

  const rerender = () => {
    const selectedNode = nodes.find((node) => node.nodeKey === selectedNodeKey) ?? nodes[0];
    selectedNodeKey = selectedNode?.nodeKey ?? '';
    designer.replaceChildren(
      createElement(
        'div',
        { className: 'flow-designer-head' },
        createElement('div', {}, createElement('h3', {}, `流程画布：${flow.flowName}`), createElement('p', {}, `${flow.flowCode} / ${flow.currentVersion ?? 'DRAFT'}`)),
        createElement('div', { className: 'inline-actions' }, createFlowCanvasSaveButton(), createFlowSimulateButton(), createFlowCanvasCheckButton()),
      ),
      status,
      createElement(
        'div',
        { className: 'flow-designer-grid' },
        createFlowNodeLibraryPanel(library, (item) => {
          const next = createNodeFromLibrary(item, nodes.length);
          nodes = [...nodes, next];
          selectedNodeKey = next.nodeKey;
          status.textContent = flowCanvasSummary(nodes, edges);
          rerender();
        }, () => {
          const preset = createApprovalEndPreset();
          nodes = preset.nodes;
          edges = preset.edges;
          selectedNodeKey = nodes[0]?.nodeKey ?? '';
          status.textContent = flowCanvasSummary(nodes, edges);
          rerender();
        }),
        createFlowCanvasPanel(nodes, edges, selectedNodeKey, (nodeKey) => {
          selectedNodeKey = nodeKey;
          rerender();
        }),
        createFlowPropertyPanel(selectedNode, library, (updated) => {
          nodes = nodes.map((node) => node.nodeKey === updated.nodeKey ? updated : node);
          status.textContent = flowCanvasSummary(nodes, edges);
          rerender();
        }, (nodeKey) => {
          nodes = nodes.filter((node) => node.nodeKey !== nodeKey);
          edges = edges.filter((edge) => edge.sourceNodeKey !== nodeKey && edge.targetNodeKey !== nodeKey);
          selectedNodeKey = nodes[0]?.nodeKey ?? '';
          status.textContent = flowCanvasSummary(nodes, edges);
          rerender();
        }),
      ),
      createFlowEdgeEditor(nodes, edges, (edge) => {
        edges = [...edges.filter((item) => item.edgeKey !== edge.edgeKey), edge];
        status.textContent = flowCanvasSummary(nodes, edges);
        rerender();
      }, (edgeKey) => {
        edges = edges.filter((edge) => edge.edgeKey !== edgeKey);
        status.textContent = flowCanvasSummary(nodes, edges);
        rerender();
      }),
    );
  };

  const persistCanvas = async (): Promise<FlowDefinitionView> => {
    const saved = await saveFlowCanvas(systemId, flow.flowId, { nodes, edges });
    if (saved.canvas) {
      const synced = normalizeFlowCanvas(saved.canvas);
      nodes = synced.nodes;
      edges = synced.edges;
      selectedNodeKey = nodes.find((node) => node.nodeKey === selectedNodeKey)?.nodeKey ?? nodes[0]?.nodeKey ?? '';
    }
    return saved;
  };

  const createFlowCanvasSaveButton = () => {
    const button = createButton('保存画布', 'primary', false);
    button.addEventListener('click', async () => {
      button.disabled = true;
      button.textContent = '保存中...';
      try {
        const saved = await persistCanvas();
        status.textContent = `画布已保存：${saved.canvas?.nodes.length ?? nodes.length} 个节点 / ${saved.canvas?.edges.length ?? edges.length} 条连线。`;
        result.textContent = status.textContent;
        rerender();
      } catch (error) {
        status.textContent = error instanceof Error ? error.message : '画布保存失败。';
      } finally {
        button.disabled = false;
        button.textContent = '保存画布';
      }
    });
    return button;
  };

  const createFlowCanvasCheckButton = () => {
    const button = createButton('发布检查', 'secondary', false);
    button.addEventListener('click', async () => {
      button.disabled = true;
      button.textContent = '检查中...';
      try {
        await persistCanvas();
        const check = await runFlowPublishCheck(systemId, flow.flowId, '系统后台流程画布发布检查');
        status.textContent = flowCheckMessage(check);
        result.textContent = status.textContent;
      } catch (error) {
        status.textContent = error instanceof Error ? error.message : '流程发布检查失败。';
      } finally {
        button.disabled = false;
        button.textContent = '发布检查';
      }
    });
    return button;
  };

  const createFlowSimulateButton = () => {
    const button = createButton('模拟运行', 'secondary', false);
    button.addEventListener('click', async () => {
      button.disabled = true;
      button.textContent = '模拟中...';
      try {
        await persistCanvas();
        const simulation = await simulateSystemFlow(systemId, flow.flowId, { amount: 120000 });
        status.textContent = `模拟${simulation.passed ? '通过' : '失败'}：${simulation.stepTraces.length} 步，traceId=${simulation.traceId}`;
        result.textContent = status.textContent;
      } catch (error) {
        status.textContent = error instanceof Error ? error.message : '流程模拟失败。';
      } finally {
        button.disabled = false;
        button.textContent = '模拟运行';
      }
    });
    return button;
  };

  rerender();
  return designer;
}

function createFlowNodeLibraryPanel(library: FlowNodeLibraryItem[], onAdd: (item: FlowNodeLibraryItem) => void, onPreset: () => void): HTMLElement {
  const presetButton = createButton('插入审批-结束模板', 'secondary', false);
  presetButton.addEventListener('click', onPreset);
  return createElement(
    'aside',
    { className: 'flow-node-library' },
    createElement('strong', {}, '节点库'),
    presetButton,
    ...library.map((item) => {
      const button = createButton(item.nodeTypeName || item.nodeType, 'ghost', false, item.description);
      button.addEventListener('click', () => onAdd(item));
      return createElement('article', { className: 'flow-library-item' }, button, createElement('small', {}, item.category || item.nodeType));
    }),
  );
}

function createFlowCanvasPanel(nodes: FlowNodeConfigView[], edges: FlowEdgeView[], selectedNodeKey: string, onSelect: (nodeKey: string) => void): HTMLElement {
  const canvas = createElement('div', { className: 'flow-canvas' });
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('class', 'flow-canvas-lines');
  svg.setAttribute('viewBox', '0 0 980 360');
  edges.forEach((edge) => {
    const source = nodes.find((node) => node.nodeKey === edge.sourceNodeKey);
    const target = nodes.find((node) => node.nodeKey === edge.targetNodeKey);
    if (!source || !target) {
      return;
    }
    const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
    line.setAttribute('x1', String(source.position.x + source.position.width));
    line.setAttribute('y1', String(source.position.y + source.position.height / 2));
    line.setAttribute('x2', String(target.position.x));
    line.setAttribute('y2', String(target.position.y + target.position.height / 2));
    line.setAttribute('marker-end', 'url(#flowArrow)');
    svg.append(line);
  });
  const defs = document.createElementNS('http://www.w3.org/2000/svg', 'defs');
  defs.innerHTML = '<marker id="flowArrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 Z"></path></marker>';
  svg.prepend(defs);
  canvas.append(svg);
  nodes.forEach((node) => {
    const button = createButton(node.nodeName, node.nodeKey === selectedNodeKey ? 'primary' : 'secondary', false);
    button.classList.add('flow-canvas-node');
    button.style.left = `${node.position.x}px`;
    button.style.top = `${node.position.y}px`;
    button.style.width = `${node.position.width}px`;
    button.style.height = `${node.position.height}px`;
    button.addEventListener('click', () => onSelect(node.nodeKey));
    button.append(createElement('small', {}, node.nodeType));
    canvas.append(button);
  });
  return createElement('section', { className: 'flow-canvas-panel' }, createElement('strong', {}, '画布连线'), canvas);
}

function createFlowPropertyPanel(
  node: FlowNodeConfigView | undefined,
  library: FlowNodeLibraryItem[],
  onUpdate: (node: FlowNodeConfigView) => void,
  onDelete: (nodeKey: string) => void,
): HTMLElement {
  if (!node) {
    return createElement('aside', { className: 'flow-property-panel' }, createElement('strong', {}, '属性面板'), createElement('p', {}, '先从节点库添加节点。'));
  }
  const libraryItem = library.find((item) => item.nodeType === node.nodeType);
  const nameInput = createElement('input', { ariaLabel: '节点名称' });
  nameInput.value = node.nodeName;
  const jsonInput = createElement('textarea', { ariaLabel: '节点属性 JSON' });
  jsonInput.value = JSON.stringify(node.propertyPayload ?? {}, null, 2);
  const saveButton = createButton('更新节点属性', 'primary', false);
  const deleteButton = createButton('删除节点', 'ghost', false);
  const errorLine = createElement('p', { className: 'field-error' });
  saveButton.addEventListener('click', () => {
    try {
      onUpdate({
        ...node,
        nodeName: nameInput.value.trim() || node.nodeName,
        propertyPayload: JSON.parse(jsonInput.value || '{}') as Record<string, unknown>,
      });
    } catch {
      errorLine.textContent = '节点属性必须是合法 JSON。';
    }
  });
  deleteButton.addEventListener('click', () => onDelete(node.nodeKey));
  return createElement(
    'aside',
    { className: 'flow-property-panel' },
    createElement('strong', {}, '属性面板'),
    createElement('label', {}, createElement('span', {}, '节点名称'), nameInput),
    createElement('div', { className: 'flow-property-schema' },
      createElement('span', {}, libraryItem?.nodeTypeName ?? node.nodeType),
      ...(libraryItem?.propertySchema ?? []).slice(0, 6).map((field) => createElement('small', {}, `${field.fieldName}${field.required ? ' *' : ''}`)),
    ),
    createElement('label', {}, createElement('span', {}, '属性 JSON'), jsonInput),
    createElement('div', { className: 'inline-actions' }, saveButton, deleteButton),
    errorLine,
  );
}

function createFlowEdgeEditor(
  nodes: FlowNodeConfigView[],
  edges: FlowEdgeView[],
  onAdd: (edge: FlowEdgeView) => void,
  onDelete: (edgeKey: string) => void,
): HTMLElement {
  const sourceSelect = createElement('select', { ariaLabel: 'source node' });
  const targetSelect = createElement('select', { ariaLabel: 'target node' });
  nodes.forEach((node) => {
    sourceSelect.append(createElement('option', {}, `${node.nodeName} (${node.nodeKey})`));
    sourceSelect.lastElementChild?.setAttribute('value', node.nodeKey);
    targetSelect.append(createElement('option', {}, `${node.nodeName} (${node.nodeKey})`));
    targetSelect.lastElementChild?.setAttribute('value', node.nodeKey);
  });
  if (nodes.length > 1) {
    targetSelect.value = nodes[1].nodeKey;
  }
  const branchInput = createElement('input', { ariaLabel: 'branch label' });
  branchInput.placeholder = 'approved / default / condition label';
  const addButton = createButton('添加连线', 'primary', nodes.length < 2, nodes.length < 2 ? '至少需要两个节点' : undefined);
  addButton.addEventListener('click', () => {
    const source = sourceSelect.value;
    const target = targetSelect.value;
    if (!source || !target || source === target) {
      return;
    }
    onAdd({
      edgeKey: `edge_${source}_${target}_${Date.now()}`,
      sourceNodeKey: source,
      targetNodeKey: target,
      branchLabel: branchInput.value.trim() || 'default',
      conditionPayload: null,
    });
  });
  return createElement(
    'section',
    { className: 'flow-edge-editor' },
    createElement('div', { className: 'runtime-card-head' }, createElement('h3', {}, '连线配置'), addButton),
    createElement('div', { className: 'flow-edge-form' },
      createElement('label', {}, createElement('span', {}, '来源节点'), sourceSelect),
      createElement('label', {}, createElement('span', {}, '目标节点'), targetSelect),
      createElement('label', {}, createElement('span', {}, '分支标签'), branchInput),
    ),
    edges.length === 0
      ? createElement('p', {}, '暂无连线。')
      : createElement('div', { className: 'simple-stack' }, ...edges.map((edge) => {
          const deleteButton = createButton('删除', 'ghost', false);
          deleteButton.addEventListener('click', () => onDelete(edge.edgeKey));
          return createElement('div', { className: 'list-line' }, createElement('span', {}, `${edge.sourceNodeKey} -> ${edge.targetNodeKey} / ${edge.branchLabel ?? '-'}`), deleteButton);
        })),
  );
}

function normalizeFlowCanvas(canvas: FlowCanvasView): FlowCanvasView {
  return {
    ...canvas,
    nodes: (canvas.nodes ?? []).map((node, index) => ({
      ...node,
      position: node.position ?? { x: 120 + index * 240, y: 120, width: 180, height: 72 },
      propertyPayload: asRecord(node.propertyPayload),
      status: node.status ?? 1,
    })),
    edges: canvas.edges ?? [],
  };
}

function createNodeFromLibrary(item: FlowNodeLibraryItem, index: number): FlowNodeConfigView {
  const nodeKey = `node_${item.nodeType}_${Date.now()}`;
  return {
    nodeKey,
    nodeType: item.nodeType,
    nodeName: item.nodeTypeName || item.nodeType,
    position: { x: 80 + (index % 4) * 220, y: 80 + Math.floor(index / 4) * 110, width: 180, height: 72 },
    propertyPayload: asRecord(item.defaultPropertyPayload),
    status: 1,
    propertyPanelCode: `${item.nodeType}PropertyPanel`,
    runtimeExecutable: item.nodeType !== 'timeout_reminder',
  };
}

function createApprovalEndPreset(): { nodes: FlowNodeConfigView[]; edges: FlowEdgeView[] } {
  return {
    nodes: [
      {
        nodeKey: 'node_submit_review',
        nodeType: 'approval',
        nodeName: '提交审批',
        position: { x: 120, y: 120, width: 180, height: 72 },
        propertyPayload: {
          approvalType: 'OR_SIGN',
          assigneeType: 'ROLE',
          assigneeIds: [],
          allowTransfer: true,
          allowReject: true,
          reasonRequired: false,
        },
        status: 1,
        propertyPanelCode: 'approvalPropertyPanel',
        runtimeExecutable: true,
      },
      {
        nodeKey: 'node_end_passed',
        nodeType: 'end',
        nodeName: '审批通过结束',
        position: { x: 380, y: 120, width: 180, height: 72 },
        propertyPayload: {},
        status: 1,
        propertyPanelCode: 'endPropertyPanel',
        runtimeExecutable: true,
      },
    ],
    edges: [
      {
        edgeKey: 'edge_submit_end',
        sourceNodeKey: 'node_submit_review',
        targetNodeKey: 'node_end_passed',
        branchLabel: 'approved',
        conditionPayload: null,
      },
    ],
  };
}

function flowCanvasSummary(nodes: FlowNodeConfigView[], edges: FlowEdgeView[]): string {
  const hasEnd = nodes.some((node) => node.nodeType === 'end');
  const connected = nodes.length > 0 && edges.length > 0;
  return `当前画布：${nodes.length} 个节点 / ${edges.length} 条连线；${hasEnd ? '包含结束节点' : '缺少结束节点'}；${connected ? '已形成连线' : '尚未形成可发布连线'}。`;
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function createDictActions(systemId: string, dict: DictTypeView, reload: () => void, result: HTMLElement): HTMLElement {
  const itemButton = createButton('新增选项', 'secondary', false);
  itemButton.addEventListener('click', async () => {
    const values = await requestFormInput('新增字典项', [
      { name: 'itemName', label: '字典项名称' },
      { name: 'itemCode', label: '字典项编码', defaultValue: `item_${Date.now()}` },
      { name: 'color', label: '颜色值', defaultValue: '#2563EB', required: false },
    ], '保存');
    if (!values) {
      return;
    }
    itemButton.disabled = true;
    itemButton.textContent = '保存中...';
    try {
      const item = await createSystemDictItem(systemId, dict.dictTypeId, {
        itemCode: values.itemCode,
        itemName: values.itemName,
        color: values.color || '#2563EB',
        icon: 'tag',
        semantic: values.itemCode,
        kanbanEnabled: true,
      });
      result.textContent = `字典项已保存：${item.itemName} / ${item.itemCode}`;
      reload();
    } catch (error) {
      itemButton.disabled = false;
      itemButton.textContent = '新增选项';
      result.textContent = error instanceof Error ? error.message : '新增字典项失败。';
    }
  });
  return createElement('div', { className: 'row-actions' }, itemButton);
}

function flowCheckMessage(check: { passed: boolean; failureItems: ModulePublishCheckResult['failureItems']; warningItems: ModulePublishCheckResult['warningItems']; traceId: string }): string {
  const failures = check.failureItems.map((item) => `${item.itemName}：${item.message}`).join('；');
  const warnings = check.warningItems.map((item) => `${item.itemName}：${item.message}`).join('；');
  if (!check.passed) {
    return `流程发布检查未通过：${failures || '存在阻断项'}，traceId=${check.traceId}`;
  }
  return `流程发布检查通过${warnings ? `，提醒：${warnings}` : ''}，traceId=${check.traceId}`;
}

function createDataSourcePanel(
  dataSources: PageResult<SystemDataSourceView>,
  systemId: string,
  reload: () => void,
  onPageChange: (nextPage: number) => void,
): HTMLElement {
  const result = createElement('p', { className: 'field-error' }, '数据源配置会写入当前系统后台，连通性检查只验证配置完整性并返回 traceId，不会私自访问外部数据库或外部 API。');
  const createButtonEl = createButton('新建数据源', 'primary', false);
  createButtonEl.addEventListener('click', async () => {
    const values = await requestFormInput('新建数据源', [
      { name: 'sourceName', label: '数据源名称' },
      { name: 'sourceCode', label: '数据源编码', defaultValue: `ds_${Date.now()}` },
      { name: 'sourceType', label: '类型', defaultValue: 'SYSTEM_INTERNAL' },
      { name: 'endpointOrSecret', label: 'Endpoint / SecretRef', required: false },
    ], '创建');
    if (!values) {
      return;
    }
    const sourceType = (values.sourceType || 'SYSTEM_INTERNAL').trim().toUpperCase();
    createButtonEl.disabled = true;
    createButtonEl.textContent = '创建中...';
    try {
      const dataSource = await createSystemDataSource(systemId, {
        sourceName: values.sourceName,
        sourceCode: values.sourceCode,
        sourceType,
        connectionConfig: dataSourceConnectionConfig(sourceType, values.endpointOrSecret),
        authConfig: {},
        syncConfig: { mode: 'MANUAL' },
        desensitizeConfig: { mode: 'PERMISSION' },
      });
      result.textContent = `数据源已创建：${dataSource.sourceName} / ${dataSource.sourceCode}，traceId=${dataSource.operation?.traceId ?? '-'}`;
      reload();
    } catch (error) {
      createButtonEl.disabled = false;
      createButtonEl.textContent = '新建数据源';
      result.textContent = error instanceof Error ? error.message : '新建数据源失败。';
    }
  });

  return createElement(
    'section',
    { id: 'data-source', className: 'panel' },
    createElement('h2', {}, '数据源'),
    createElement('div', { className: 'inline-actions' }, createButtonEl),
    result,
    createFilterBar(['数据源', '类型', '检查状态', '发布状态']),
    createTable(
      ['序号', '数据源', '类型', '状态', '检查', '发布', '更新时间', '操作'],
      dataSources.records.map((dataSource, index) => [
        index + 1,
        createElement('span', {}, createElement('strong', {}, dataSource.sourceName), createElement('small', {}, ` ${dataSource.sourceCode}`)),
        dataSourceTypeText(dataSource.sourceType),
        renderStatusPill(enableStatusText(dataSource.status), enableStatusTone(dataSource.status)),
        renderStatusPill(dataSource.lastCheckStatus ?? '未检查', dataSource.lastCheckStatus === 'PASSED' ? 'success' : 'warning'),
        dataSource.publishStatus ?? 'DRAFT',
        formatTime(dataSource.updatedAt),
        createDataSourceActions(systemId, dataSource, reload, result),
      ]),
      '暂无数据源。',
    ),
    createPagination(dataSources, onPageChange),
  );
}

function dataSourceConnectionConfig(sourceType: string, endpointOrSecret?: string): Record<string, unknown> {
  if (sourceType === 'EXTERNAL_API') {
    return { endpointUrl: endpointOrSecret || 'https://example.invalid/api' };
  }
  if (sourceType === 'DATABASE_DIRECT') {
    return { connectionSecretRef: endpointOrSecret || 'secret://datasource/manual' };
  }
  return { moduleScope: ['*'] };
}

function createDataSourceActions(
  systemId: string,
  dataSource: SystemDataSourceView,
  reload: () => void,
  result: HTMLElement,
): HTMLElement {
  const checkButton = createButton('连通性检查', 'secondary', false);
  const publishCheckButton = createButton('发布检查', 'secondary', false);
  checkButton.addEventListener('click', async () => {
    checkButton.disabled = true;
    checkButton.textContent = '检查中...';
    try {
      const check = await checkSystemDataSourceConnection(systemId, dataSource.dataSourceId);
      checkButton.disabled = false;
      checkButton.textContent = check.passed ? '检查通过' : '检查失败';
      result.textContent = dataSourceCheckMessage('连通性检查', check);
      reload();
    } catch (error) {
      checkButton.disabled = false;
      checkButton.textContent = '检查失败';
      result.textContent = error instanceof Error ? error.message : '数据源连通性检查失败。';
    }
  });
  publishCheckButton.addEventListener('click', async () => {
    publishCheckButton.disabled = true;
    publishCheckButton.textContent = '检查中...';
    try {
      const check = await runSystemDataSourcePublishCheck(systemId, dataSource.dataSourceId);
      publishCheckButton.disabled = false;
      publishCheckButton.textContent = check.passed ? '发布可用' : '发布阻断';
      result.textContent = dataSourceCheckMessage('发布检查', check);
    } catch (error) {
      publishCheckButton.disabled = false;
      publishCheckButton.textContent = '发布检查失败';
      result.textContent = error instanceof Error ? error.message : '数据源发布检查失败。';
    }
  });
  return createElement('div', { className: 'row-actions' }, checkButton, publishCheckButton);
}

function dataSourceCheckMessage(prefix: string, check: SystemDataSourceCheckResult): string {
  const failures = check.items.filter((item) => !item.passed).map((item) => item.message).join('；');
  if (!check.passed) {
    return `${prefix}未通过：${failures || '存在阻断项'}，traceId=${check.traceId}`;
  }
  return `${prefix}通过${check.targetVersion ? `，目标版本=${check.targetVersion}` : ''}，traceId=${check.traceId}`;
}

function dataSourceTypeText(sourceType: string): string {
  if (sourceType === 'SYSTEM_INTERNAL') {
    return '系统内数据源';
  }
  if (sourceType === 'EXTERNAL_API') {
    return '外部 API 数据源';
  }
  if (sourceType === 'DATABASE_DIRECT') {
    return '数据库直连';
  }
  return sourceType;
}

function createIntegrationPanel(
  apps: PageResult<OpenApiAppView>,
  systemId: string,
  reload: () => void,
  onPageChange: (nextPage: number) => void,
): HTMLElement {
  const appResult = createElement('p', { className: 'field-error' }, '对外应用使用 OpenApiSecretRef、scope、限流和调用日志，不在页面展示明文密钥。');
  const createAppButton = createButton('新建对外应用', 'primary', false);
  createAppButton.addEventListener('click', async () => {
    const values = await requestFormInput('新建对外应用', [
      { name: 'appName', label: '应用名称' },
      { name: 'externalAppCode', label: '应用编码', defaultValue: `app_${Date.now()}` },
      { name: 'callbackUrl', label: '回调地址', type: 'url', required: false },
    ], '创建');
    if (!values) {
      return;
    }
    createAppButton.disabled = true;
    createAppButton.textContent = '创建中...';
    try {
      const app = await createOpenApiApp(systemId, {
        appName: values.appName,
        externalAppCode: values.externalAppCode,
        callbackUrl: values.callbackUrl || undefined,
      });
      appResult.textContent = `对外应用已创建：${app.appName} / ${app.externalAppCode}，traceId=${app.operation?.traceId ?? '-'}`;
      reload();
    } catch (error) {
      createAppButton.disabled = false;
      createAppButton.textContent = '新建对外应用';
      appResult.textContent = error instanceof Error ? error.message : '新建对外应用失败。';
    }
  });

  return createElement(
    'section',
    { id: 'openapi-apps', className: 'panel' },
    createElement('h2', {}, '对外应用'),
    createElement('div', { className: 'inline-actions' }, createAppButton),
    appResult,
    createFilterBar(['应用名称', 'scope', '状态', 'traceId']),
    createTable(
      ['序号', '应用', 'scope', 'SecretRef', '状态', '更新时间', '操作'],
      apps.records.map((app, index) => [
        index + 1,
        createElement('span', {}, createElement('strong', {}, app.appName), createElement('small', {}, ` ${app.externalAppCode}`)),
        app.scopes?.filter((scope) => scope.enabled).map((scope) => scope.scopeCode).join(' / ') || '-',
        app.openApiSecretRef?.displayName ?? app.openApiSecretRef?.secretRefId ?? '-',
        renderStatusPill(enableStatusText(app.status), enableStatusTone(app.status)),
        formatTime(app.updatedAt),
        createOpenApiActions(systemId, app, appResult),
      ]),
      '暂无对外应用。',
    ),
    createPagination(apps, onPageChange),
  );
}

function createOpenApiActions(systemId: string, app: OpenApiAppView, result: HTMLElement): HTMLElement {
  const rotateButton = createButton('轮换密钥', 'secondary', false);
  rotateButton.addEventListener('click', async () => {
    if (!(await requestConfirmation('轮换 OpenAPI 密钥', `确认为「${app.appName}」创建密钥轮换任务吗？`, '创建任务'))) {
      return;
    }
    rotateButton.disabled = true;
    rotateButton.textContent = '轮换中...';
    try {
      const job = await rotateOpenApiSecret(systemId, app.externalAppId);
      rotateButton.textContent = '已创建任务';
      result.textContent = `密钥轮换任务已创建：${job.status} / ${job.newVersion ?? '-'}，traceId=${job.operation?.traceId ?? '-'}`;
    } catch (error) {
      rotateButton.disabled = false;
      rotateButton.textContent = '轮换失败';
      result.textContent = error instanceof Error ? error.message : '密钥轮换失败。';
    }
  });
  return createElement('div', { className: 'row-actions' }, rotateButton);
}

function createWorkAndAgentPanel(
  data: SystemAdminData,
  systemId: string,
  reload: () => void,
  activeSection: 'work-config' | 'agent-config',
  onAgentPolicyPageChange?: (nextPage: number) => void,
): HTMLElement {
  const agentResult = createElement('p', { className: 'field-error' }, '系统 Agent 策略控制模块、字段、动作、数据范围、外发限制和脱敏策略。');
  const workResult = createElement('p', { className: 'field-error' }, '工作配置保存项目任务、普通任务、日报字段和看板取数字段；状态/标签选项来自数据字典。');
  const saveWorkConfigButton = createButton('保存工作配置', 'primary', false);
  const checkWorkConfigButton = createButton('发布检查', 'secondary', false);
  saveWorkConfigButton.addEventListener('click', async () => {
    const reason = await requestTextInput('保存工作配置', '变更原因', '调整工作字段和看板配置');
    if (!reason) {
      return;
    }
    saveWorkConfigButton.disabled = true;
    saveWorkConfigButton.textContent = '保存中...';
    try {
      const config = await updateSystemWorkConfig(systemId, data.workConfig ?? {}, reason);
      workResult.textContent = `工作配置已保存，项目任务字段 ${config.projectTaskFields?.length ?? 0} 个，普通任务字段 ${config.plainTaskFields?.length ?? 0} 个，日报字段 ${config.dailyReportFields?.length ?? 0} 个。`;
      reload();
    } catch (error) {
      saveWorkConfigButton.disabled = false;
      saveWorkConfigButton.textContent = '保存工作配置';
      workResult.textContent = error instanceof Error ? error.message : '保存工作配置失败。';
    }
  });
  checkWorkConfigButton.addEventListener('click', async () => {
    checkWorkConfigButton.disabled = true;
    checkWorkConfigButton.textContent = '检查中...';
    try {
      const check = await runWorkConfigPublishCheck(systemId);
      checkWorkConfigButton.disabled = false;
      checkWorkConfigButton.textContent = check.passed ? '检查通过' : '检查失败';
      workResult.textContent = `工作配置发布检查${check.passed ? '通过' : '未通过'}：${check.items.map((item) => item.message).join('；') || '-'}，traceId=${check.traceId}`;
    } catch (error) {
      checkWorkConfigButton.disabled = false;
      checkWorkConfigButton.textContent = '检查失败';
      workResult.textContent = error instanceof Error ? error.message : '工作配置发布检查失败。';
    }
  });
  const workPanel = createElement(
    'div',
    { className: 'result-panel' },
    createElement('h3', {}, '工作配置'),
    createElement('div', { className: 'inline-actions' }, saveWorkConfigButton, checkWorkConfigButton),
    workResult,
    createElement('div', { className: 'simple-stack' },
      createElement('div', { className: 'list-line' }, createElement('span', {}, '项目任务字段'), createElement('strong', {}, fieldSummary(data.workConfig?.projectTaskFields))),
      createElement('div', { className: 'list-line' }, createElement('span', {}, '普通任务字段'), createElement('strong', {}, fieldSummary(data.workConfig?.plainTaskFields))),
      createElement('div', { className: 'list-line' }, createElement('span', {}, '日报字段'), createElement('strong', {}, fieldSummary(data.workConfig?.dailyReportFields))),
      createElement('div', { className: 'list-line' }, createElement('span', {}, '项目任务看板'), createElement('strong', {}, objectKeys(data.workConfig?.projectTaskKanban))),
      createElement('div', { className: 'list-line' }, createElement('span', {}, '普通任务看板'), createElement('strong', {}, objectKeys(data.workConfig?.plainTaskKanban))),
    ),
    data.workConfig?.traceId ? createTraceLine(data.workConfig.traceId) : null,
  );
  const agentPanel = createAgentPolicyPanel(data.agentPolicies, systemId, reload, agentResult, onAgentPolicyPageChange);
  return createElement(
    'section',
    { id: activeSection, className: 'panel' },
    createElement('h2', {}, activeSection === 'work-config' ? '工作配置' : 'AI Agent'),
    activeSection === 'work-config' ? workPanel : agentPanel,
  );
}

function createAgentPolicyPanel(
  page: PageResult<AgentPolicyView>,
  systemId: string,
  reload: () => void,
  result: HTMLElement,
  onPageChange?: (nextPage: number) => void,
): HTMLElement {
  const createPolicyButton = createButton('新建策略', 'primary', false);
  createPolicyButton.addEventListener('click', async () => {
    const policyCode = await requestTextInput('新建 Agent 策略', '策略编码', `agent_policy_${Date.now()}`);
    if (!policyCode) {
      return;
    }
    createPolicyButton.disabled = true;
    createPolicyButton.textContent = '创建中...';
    try {
      const policy = await createSystemAgentPolicy(systemId, policyCode);
      result.textContent = `Agent 策略已创建：${policy.policyCode}`;
      reload();
    } catch (error) {
      createPolicyButton.disabled = false;
      createPolicyButton.textContent = '新建策略';
      result.textContent = error instanceof Error ? error.message : '创建 Agent 策略失败。';
    }
  });
  return createElement(
    'div',
    { className: 'result-panel' },
    createElement('h3', {}, 'AI Agent 策略'),
    createElement('div', { className: 'inline-actions' }, createPolicyButton),
    result,
    createTable(
      ['序号', '策略', '状态', '发布状态', '版本', '操作'],
      page.records.map((policy, index) => [
        index + 1,
        policy.policyCode,
        renderStatusPill(enableStatusText(policy.status), enableStatusTone(policy.status)),
        policy.publishStatus?.status || '-',
        policy.publishStatus?.version || '-',
        createAgentPolicyActions(systemId, policy, result),
      ]),
      '暂无系统 Agent 策略。',
    ),
    createPagination(page, onPageChange),
  );
}

function createAgentPolicyActions(systemId: string, policy: AgentPolicyView, result: HTMLElement): HTMLElement {
  const checkButton = createButton('发布检查', 'secondary', false);
  checkButton.addEventListener('click', async () => {
    checkButton.disabled = true;
    checkButton.textContent = '检查中...';
    try {
      const check = await runSystemAgentPolicyPublishCheck(systemId, policy.policyId);
      checkButton.disabled = false;
      checkButton.textContent = check.passed ? '检查通过' : '检查失败';
      result.textContent = `Agent 策略发布检查${check.passed ? '通过' : '未通过'}：${check.items.map((item) => item.message).join('；') || '-'}，traceId=${check.traceId}`;
    } catch (error) {
      checkButton.disabled = false;
      checkButton.textContent = '检查失败';
      result.textContent = error instanceof Error ? error.message : 'Agent 策略发布检查失败。';
    }
  });
  return createElement('div', { className: 'row-actions' }, checkButton);
}

function createSystemSsoLogPanel(
  data: SystemAdminData,
  systemId: string,
  reload: () => void,
  activeSection: 'sso-config' | 'log-management',
  onLogsPageChange: (nextPage: number) => void,
): HTMLElement {
  const ssoResult = createElement('p', { className: 'field-error' }, '统一认证策略可保存身份源继承、租户域名、组织映射和无成员映射反馈。');
  const savePolicyButton = createButton('保存 SSO 策略', 'primary', false);
  const precheckButton = createButton('组织映射预检', 'secondary', false);
  savePolicyButton.addEventListener('click', async () => {
    const values = await requestFormInput('保存 SSO 策略', [
      { name: 'providerIds', label: '启用身份源 providerId，多个用逗号分隔', defaultValue: (data.ssoPolicy?.enabledProviderIds ?? []).join(','), required: false },
      { name: 'tenantDomains', label: '租户域名，多个用逗号分隔', defaultValue: (data.ssoPolicy?.tenantDomains ?? []).join(','), required: false },
    ], '保存');
    if (!values) {
      return;
    }
    savePolicyButton.disabled = true;
    savePolicyButton.textContent = '保存中...';
    try {
      const policy = await updateSystemSsoPolicy(systemId, {
        enabledProviderIds: csv(values.providerIds),
        tenantDomains: csv(values.tenantDomains),
        orgMapping: data.ssoPolicy?.orgMapping,
        employeeBinding: data.ssoPolicy?.employeeBinding,
        jitMemberPolicy: data.ssoPolicy?.jitMemberPolicy,
        noMemberFeedback: data.ssoPolicy?.noMemberFeedback,
        status: 'DRAFT',
      });
      ssoResult.textContent = `SSO 策略已保存：${policy.status ?? 'DRAFT'}，traceId=${policy.traceId ?? '-'}`;
      reload();
    } catch (error) {
      savePolicyButton.disabled = false;
      savePolicyButton.textContent = '保存 SSO 策略';
      ssoResult.textContent = error instanceof Error ? error.message : '保存 SSO 策略失败。';
    }
  });
  precheckButton.addEventListener('click', async () => {
    const identityProvider = await requestTextInput('组织映射预检', '身份源 providerId', data.ssoPolicy?.enabledProviderIds?.[0] ?? '');
    if (!identityProvider) {
      return;
    }
    precheckButton.disabled = true;
    precheckButton.textContent = '预检中...';
    try {
      const precheck = await precheckSystemSsoOrgSync(systemId, {
        identityProvider,
        tenantId: shellState.currentSystem?.tenantId,
      });
      precheckButton.disabled = false;
      precheckButton.textContent = '预检完成';
      ssoResult.textContent = `组织映射预检：部门匹配 ${precheck.matchedDepartmentCount}，未匹配 ${precheck.unmatchedDepartmentCount}，成员匹配 ${precheck.matchedMemberCount}，待绑定 ${precheck.unboundMemberCount}，traceId=${precheck.traceId}`;
    } catch (error) {
      precheckButton.disabled = false;
      precheckButton.textContent = '预检失败';
      ssoResult.textContent = error instanceof Error ? error.message : '组织映射预检失败。';
    }
  });
  const ssoPanel = createElement(
    'div',
    { className: 'result-panel' },
    createElement('h3', {}, '系统 SSO'),
    createElement('div', { className: 'inline-actions' }, savePolicyButton, precheckButton),
    ssoResult,
    createElement('div', { className: 'simple-stack' },
      createElement('div', { className: 'list-line' }, createElement('span', {}, '启用身份源'), createElement('strong', {}, listSummary(data.ssoPolicy?.enabledProviderIds))),
      createElement('div', { className: 'list-line' }, createElement('span', {}, '租户域名'), createElement('strong', {}, listSummary(data.ssoPolicy?.tenantDomains))),
      createElement('div', { className: 'list-line' }, createElement('span', {}, '组织映射'), createElement('span', {}, objectKeys(data.ssoPolicy?.orgMapping))),
      createElement('div', { className: 'list-line' }, createElement('span', {}, '员工绑定'), createElement('span', {}, objectKeys(data.ssoPolicy?.employeeBinding))),
      createElement('div', { className: 'list-line' }, createElement('span', {}, '策略状态'), renderStatusPill(data.ssoPolicy?.status ?? '未配置', data.ssoPolicy?.status === 'ENABLED' ? 'success' : 'warning')),
    ),
    data.ssoPolicy?.traceId ? createTraceLine(data.ssoPolicy.traceId) : null,
  );
  const logPanel = createElement(
    'div',
    { className: 'result-panel' },
    createElement('h3', {}, '日志管理'),
    createFilterBar(['日志类型', '账号', '模块', '结果', 'traceId', '时间范围']),
    createLogTable(data.logs, onLogsPageChange),
  );
  return createElement(
    'section',
    { id: activeSection, className: 'panel' },
    createElement('h2', {}, activeSection === 'sso-config' ? '统一认证' : '日志管理'),
    activeSection === 'sso-config' ? ssoPanel : logPanel,
  );
}

function createLogTable(page: PageResult<AuditLogView>, onPageChange: (nextPage: number) => void): HTMLElement {
  return createElement(
    'div',
    { className: 'admin-stack' },
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
      '暂无系统日志。',
    ),
    createPagination(page, onPageChange),
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

function createSidebarButton(label: string, targetId: string, active = false, onSelect?: (targetId: string) => void): HTMLButtonElement {
  const button = createElement('button', { className: active ? 'sidebar-item active' : 'sidebar-item' }, label);
  button.addEventListener('click', () => {
    onSelect?.(targetId);
  });
  return button;
}

function createTreeButton(label: string, active = false): HTMLButtonElement {
  const button = createElement('button', { className: active ? 'sidebar-item active' : 'sidebar-item' }, label);
  button.addEventListener('click', () => {
    button.closest('.mini-tree')?.querySelectorAll('.sidebar-item').forEach((item) => item.classList.remove('active'));
    button.classList.add('active');
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

function flattenDepartments(departments: DepartmentNode[], depth = 0): Array<{ node: DepartmentNode; depth: number }> {
  return departments.flatMap((node) => [{ node, depth }, ...flattenDepartments(node.children ?? [], depth + 1)]);
}

function firstDepartmentId(departments: DepartmentNode[]): string {
  return flattenDepartments(departments)[0]?.node.deptId ?? '';
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

function objectKeys(value?: Record<string, unknown>): string {
  const keys = Object.keys(value ?? {});
  return keys.length ? keys.join(' / ') : '未配置';
}

function listSummary(values?: string[]): string {
  return values && values.length > 0 ? values.join(' / ') : '未配置';
}

function fieldSummary(fields?: Array<{ fieldName?: string; fieldCode?: string }>): string {
  if (!fields || fields.length === 0) {
    return '未配置';
  }
  return fields.map((field) => {
    if (field.fieldName && field.fieldCode) {
      return `${field.fieldName} (${field.fieldCode})`;
    }
    return field.fieldName || field.fieldCode || '-';
  }).join(' / ');
}

function csv(value: string): string[] {
  return value.split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}

function activeSystemId(): string {
  return shellState.currentSystem?.systemId ?? shellState.availableSystems[0]?.systemId ?? '1';
}

function createLoadingPanel(text: string): HTMLElement {
  return createElement('section', { className: 'panel' }, createElement('strong', {}, text));
}

function createErrorPanel(error: unknown): HTMLElement {
  return createElement('section', { className: 'panel' }, createElement('strong', {}, '加载失败'), createElement('p', {}, error instanceof Error ? error.message : '请稍后重试。'));
}
