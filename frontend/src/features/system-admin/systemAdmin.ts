import {
  createNotificationTemplate,
  createOpenApiApp,
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
  loadSystemAdminData,
  precheckSystemSsoOrgSync,
  publishSystemFlow,
  publishSystemModule,
  rollbackSystemModule,
  rotateOpenApiSecret,
  runFlowPublishCheck,
  runModulePublishCheck,
  runNotificationTemplatePublishCheck,
  runSystemAgentPolicyPublishCheck,
  runWorkConfigPublishCheck,
  updateSystemWorkConfig,
  updateSystemSsoPolicy,
  type AgentPolicyView,
  type AuditLogView,
  type BackendModule,
  type BackendModuleGroup,
  type DictTypeView,
  type DepartmentNode,
  type FlowDefinitionView,
  type MemberView,
  type ModulePublishCheckResult,
  type NotificationTemplateView,
  type OpenApiAppView,
  type RoleView,
  type SystemAdminData,
} from '../../api/liveData';
import type { PageResult } from '../../api/types';
import type { Navigate } from '../../app/app';
import { shellState } from '../../app/state';
import { createButton, createElement, createTraceLine, type ChildNodeValue } from '../../shared/components';
import { createFilterBar } from '../../shared/filters';
import { renderStatusPill, type StatusTone } from '../../shared/status';

export function renderSystemAdmin(navigate: Navigate): HTMLElement {
  const systemId = activeSystemId();
  const content = createElement('div', { className: 'admin-content' }, createLoadingPanel('正在读取系统后台数据...'));
  const root = createElement('section', { className: 'admin-layout' }, createSystemAdminSidebar(navigate), content);
  const reload = () => {
    content.replaceChildren(createLoadingPanel('正在读取系统后台数据...'));
    void loadSystemAdminData(systemId)
      .then((data) => content.replaceChildren(...createSystemAdminContent(data, systemId, reload)))
      .catch((error) => content.replaceChildren(createErrorPanel(error)));
  };

  reload();

  return root;
}

function createSystemAdminSidebar(navigate: Navigate): HTMLElement {
  const backButton = createButton('返回业务首页', 'secondary', false);
  backButton.addEventListener('click', () => navigate(`/systems/${activeSystemId()}/dashboard`));
  return createElement(
    'aside',
    { className: 'module-sidebar admin-sidebar' },
    createElement('strong', {}, '系统后台'),
    backButton,
    createSidebarButton('系统信息', 'system-info', true),
    createSidebarButton('组织架构', 'org-role'),
    createSidebarButton('角色管理', 'org-role'),
    createSidebarButton('模块管理', 'module-config'),
    createSidebarButton('流程管理', 'flow-dict'),
    createSidebarButton('字典管理', 'flow-dict'),
    createSidebarButton('仪表盘管理', 'system-info'),
    createSidebarButton('数据源', 'integration-config'),
    createSidebarButton('对外应用', 'integration-config'),
    createSidebarButton('工作配置', 'work-agent'),
    createSidebarButton('统一认证', 'sso-log'),
    createSidebarButton('AI Agent', 'work-agent'),
    createSidebarButton('日志管理', 'sso-log'),
  );
}

function createSystemAdminContent(data: SystemAdminData, systemId: string, reload: () => void): HTMLElement[] {
  const systemName = shellState.currentSystem?.systemName ?? shellState.availableSystems.find((system) => system.systemId === systemId)?.systemName ?? '当前系统';
  const warningPanel = createWarningPanel(data.warnings);
  return [
    createElement(
      'div',
      { className: 'page-heading' },
      createElement('h1', {}, '系统后台'),
      createElement('p', {}, `${systemName} 的后台配置，只影响当前系统内的组织、角色、模块、流程、字典、工作、SSO、Agent 和日志。`),
    ),
    ...(warningPanel ? [warningPanel] : []),
    createSystemInfoPanel(data, systemName),
    createOrgRolePanel(data.departments, data.members, data.roles, systemId, reload),
    createModuleConfigPanel(data.moduleGroups, data.modules, systemId, reload),
    createFlowAndDictPanel(data.flows, data.dictTypes, systemId, reload),
    createIntegrationPanel(data.notificationTemplates, data.openApiApps, systemId, reload),
    createWorkAndAgentPanel(data, systemId, reload),
    createSystemSsoLogPanel(data, systemId, reload),
  ];
}

function createSystemInfoPanel(data: SystemAdminData, systemName: string): HTMLElement {
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
  );
}

function createOrgRolePanel(departments: DepartmentNode[], members: PageResult<MemberView>, roles: PageResult<RoleView>, systemId: string, reload: () => void): HTMLElement {
  const result = createElement('p', { className: 'field-error' }, '组织、员工和角色会写入当前系统，并受当前系统/租户上下文约束。');
  const departmentButton = createButton('新建部门', 'primary', false);
  const memberButton = createButton('新建员工', 'primary', false);
  const roleButton = createButton('新建角色', 'primary', false);
  departmentButton.addEventListener('click', async () => {
    const deptName = window.prompt('部门名称');
    if (!deptName?.trim()) {
      return;
    }
    const deptCode = window.prompt('部门编码', `dept_${Date.now()}`)?.trim();
    if (!deptCode) {
      return;
    }
    const parentId = window.prompt('父部门 ID，可为空', '')?.trim();
    departmentButton.disabled = true;
    departmentButton.textContent = '创建中...';
    try {
      const dept = await createSystemDepartment(systemId, {
        parentId: parentId || undefined,
        deptCode,
        deptName: deptName.trim(),
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
    const memberName = window.prompt('员工姓名');
    if (!memberName?.trim()) {
      return;
    }
    const deptId = window.prompt('所属部门 ID，可为空', firstDepartmentId(departments))?.trim();
    const employeeNo = window.prompt('工号，可为空', '')?.trim();
    const mobile = window.prompt('手机号，可为空', '')?.trim();
    memberButton.disabled = true;
    memberButton.textContent = '创建中...';
    try {
      const member = await createSystemMember(systemId, {
        deptId: deptId || undefined,
        memberName: memberName.trim(),
        employeeNo: employeeNo || undefined,
        mobile: mobile || undefined,
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
    const roleName = window.prompt('角色名称');
    if (!roleName?.trim()) {
      return;
    }
    const roleCode = window.prompt('角色编码', `role_${Date.now()}`)?.trim();
    if (!roleCode) {
      return;
    }
    roleButton.disabled = true;
    roleButton.textContent = '创建中...';
    try {
      const role = await createSystemRole(systemId, {
        roleName: roleName.trim(),
        roleCode,
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
  return createElement(
    'section',
    { id: 'org-role', className: 'panel' },
    createElement('h2', {}, '组织架构与角色'),
    createElement('div', { className: 'inline-actions' }, departmentButton, memberButton, roleButton),
    result,
    createElement(
      'div',
      { className: 'tree-table-layout' },
      createDepartmentTree(departments),
      createElement(
        'div',
        { className: 'admin-stack' },
        createFilterBar(['员工', '角色', '状态', '绑定状态']),
        createMemberTable(members),
        createRoleTable(roles),
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

function createMemberTable(page: PageResult<MemberView>): HTMLElement {
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
    createPagination(page),
  );
}

function createRoleTable(page: PageResult<RoleView>): HTMLElement {
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
    createPagination(page),
  );
}

function createModuleConfigPanel(groups: BackendModuleGroup[], modules: PageResult<BackendModule>, systemId: string, reload: () => void): HTMLElement {
  const result = createElement('p', { className: 'field-error' }, '模块创建、发布检查、发布和回滚会调用真实后台接口并返回 traceId。');
  const createModuleButton = createButton('新建模块', 'primary', false);
  createModuleButton.addEventListener('click', async () => {
    const name = window.prompt('请输入模块名称');
    if (!name?.trim()) {
      return;
    }
    const moduleCode = window.prompt('请输入模块编码', `module_${Date.now()}`)?.trim();
    if (!moduleCode) {
      return;
    }
    createModuleButton.disabled = true;
    createModuleButton.textContent = '创建中...';
    result.textContent = '正在创建模块并初始化默认字段、动作和列表配置...';
    try {
      const module = await createSystemModule(systemId, {
        groupId: groups[0]?.groupId,
        moduleCode,
        name: name.trim(),
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
  return createElement(
    'section',
    { id: 'module-config', className: 'panel' },
    createElement('h2', {}, '模块管理'),
    createElement(
      'div',
      { className: 'tree-table-layout' },
      createModuleTree(groups),
      createElement(
        'div',
        { className: 'admin-stack' },
        createElement('div', { className: 'inline-actions' }, createModuleButton),
        result,
        createFilterBar(['模块名称', '模块分组', '发布状态', '状态']),
        createModuleTable(modules, systemId, reload, result),
        createElement(
          'div',
          { className: 'split-grid' },
          createConfigTile('字段配置', '字段类型、字典绑定、权限、列表列、筛选项、导入导出、打印模板均归属于模块配置。'),
          createConfigTile('列表配置', '序号、复选、表头排序、场景、列设置、行点击详情、批量动作限制都从发布后的配置读取。'),
          createConfigTile('发布检查', '发布前检查字段索引、字典引用、权限快照、流程绑定、导入导出模板和影响范围。'),
        ),
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

function createModuleTable(page: PageResult<BackendModule>, systemId: string, reload: () => void, result: HTMLElement): HTMLElement {
  return createTable(
    ['序号', '模块', '分组', '状态', '发布状态', '版本', '更新时间', '操作'],
    page.records.map((module, index) => [
      index + 1,
      createElement('span', {}, createElement('strong', {}, module.name), createElement('small', {}, ` ${module.moduleCode}`)),
      module.groupId,
      renderStatusPill(enableStatusText(Number(module.status)), enableStatusTone(Number(module.status))),
      module.publishStatus || '-',
      module.currentVersion || '-',
      '-',
      createModuleRowActions(systemId, module, reload, result),
    ]),
    '暂无模块。',
  );
}

function createModuleRowActions(systemId: string, module: BackendModule, reload: () => void, result: HTMLElement): HTMLElement {
  const fieldButton = createButton('新增字段', 'secondary', false);
  const actionButton = createButton('新增动作', 'secondary', false);
  const checkButton = createButton('发布检查', 'secondary', false);
  const publishButton = createButton('发布', 'primary', false);
  const rollbackButton = createButton('回滚', 'ghost', module.publishStatus !== 'PUBLISHED', module.publishStatus === 'PUBLISHED' ? undefined : '只有已发布模块可以回滚。');
  fieldButton.addEventListener('click', async () => {
    const name = window.prompt('字段名称');
    if (!name?.trim()) {
      return;
    }
    const fieldCode = window.prompt('字段编码', `field_${Date.now()}`)?.trim();
    if (!fieldCode) {
      return;
    }
    const fieldType = window.prompt('字段类型：TEXT / NUMBER / DATE / DATETIME / SELECT / MULTI_SELECT / USER / DEPARTMENT', 'TEXT')?.trim() || 'TEXT';
    fieldButton.disabled = true;
    fieldButton.textContent = '保存中...';
    try {
      const field = await createSystemModuleField(systemId, module.moduleId, {
        fieldCode,
        name: name.trim(),
        fieldType,
        required: false,
        sortable: true,
      });
      result.textContent = `字段已保存：${field.name} / ${field.fieldCode}。请执行模块发布检查后发布。`;
      reload();
    } catch (error) {
      fieldButton.disabled = false;
      fieldButton.textContent = '新增字段';
      result.textContent = error instanceof Error ? error.message : '新增字段失败。';
    }
  });
  actionButton.addEventListener('click', async () => {
    const actionName = window.prompt('动作名称');
    if (!actionName?.trim()) {
      return;
    }
    const actionCode = window.prompt('动作编码', `action_${Date.now()}`)?.trim();
    if (!actionCode) {
      return;
    }
    const position = window.prompt('动作位置：ROW / TOOLBAR / BATCH_BAR', 'ROW')?.trim() || 'ROW';
    actionButton.disabled = true;
    actionButton.textContent = '保存中...';
    try {
      await createSystemModuleAction(systemId, module.moduleId, {
        actionCode,
        actionName: actionName.trim(),
        position,
      });
      result.textContent = `动作已保存：${actionName.trim()} / ${actionCode}。请执行模块发布检查后发布。`;
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
    if (!window.confirm(`确认发布模块「${module.name}」吗？`)) {
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
    if (rollbackButton.disabled || !window.confirm(`确认回滚模块「${module.name}」吗？`)) {
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
  return createElement('div', { className: 'row-actions' }, fieldButton, actionButton, checkButton, publishButton, rollbackButton);
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

function createFlowAndDictPanel(flows: PageResult<FlowDefinitionView>, dictTypes: PageResult<DictTypeView>, systemId: string, reload: () => void): HTMLElement {
  const flowResult = createElement('p', { className: 'field-error' }, '流程创建后为草稿，必须保存真实节点和连线后才能发布。');
  const dictResult = createElement('p', { className: 'field-error' }, '字典项负责颜色、图标、语义、排序和看板可用性，供字段和工作看板复用。');
  const createFlowButton = createButton('新建流程', 'primary', false);
  const createDictButton = createButton('新建字典', 'primary', false);
  createFlowButton.addEventListener('click', async () => {
    const flowName = window.prompt('流程名称');
    if (!flowName?.trim()) {
      return;
    }
    const flowCode = window.prompt('流程编码', `flow_${Date.now()}`)?.trim();
    if (!flowCode) {
      return;
    }
    const boundModuleId = window.prompt('绑定模块 ID，可为空', '')?.trim();
    createFlowButton.disabled = true;
    createFlowButton.textContent = '创建中...';
    try {
      const flow = await createSystemFlow(systemId, {
        flowCode,
        flowName: flowName.trim(),
        boundModuleId: boundModuleId || undefined,
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
    const dictName = window.prompt('字典名称');
    if (!dictName?.trim()) {
      return;
    }
    const dictCode = window.prompt('字典编码', `dict_${Date.now()}`)?.trim();
    if (!dictCode) {
      return;
    }
    createDictButton.disabled = true;
    createDictButton.textContent = '创建中...';
    try {
      const dict = await createSystemDictType(systemId, {
        dictCode,
        dictName: dictName.trim(),
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
  return createElement(
    'section',
    { id: 'flow-dict', className: 'panel' },
    createElement('h2', {}, '流程与字典'),
    createElement(
      'div',
      { className: 'split-grid' },
      createElement(
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
            createFlowActions(systemId, flow, reload, flowResult),
          ]),
          '暂无流程。',
        ),
        createPagination(flows),
      ),
      createElement(
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
        createPagination(dictTypes),
      ),
    ),
  );
}

function createFlowActions(systemId: string, flow: FlowDefinitionView, reload: () => void, result: HTMLElement): HTMLElement {
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
    if (!window.confirm(`确认发布流程「${flow.flowName}」吗？发布会先执行发布检查。`)) {
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
  return createElement('div', { className: 'row-actions' }, checkButton, publishButton);
}

function createDictActions(systemId: string, dict: DictTypeView, reload: () => void, result: HTMLElement): HTMLElement {
  const itemButton = createButton('新增选项', 'secondary', false);
  itemButton.addEventListener('click', async () => {
    const itemName = window.prompt('字典项名称');
    if (!itemName?.trim()) {
      return;
    }
    const itemCode = window.prompt('字典项编码', `item_${Date.now()}`)?.trim();
    if (!itemCode) {
      return;
    }
    const color = window.prompt('颜色值', '#2563EB')?.trim() || '#2563EB';
    itemButton.disabled = true;
    itemButton.textContent = '保存中...';
    try {
      const item = await createSystemDictItem(systemId, dict.dictTypeId, {
        itemCode,
        itemName: itemName.trim(),
        color,
        icon: 'tag',
        semantic: itemCode,
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

function createIntegrationPanel(
  templates: PageResult<NotificationTemplateView>,
  apps: PageResult<OpenApiAppView>,
  systemId: string,
  reload: () => void,
): HTMLElement {
  const templateResult = createElement('p', { className: 'field-error' }, '消息模板驱动消息、待办、审批、导入导出和 Agent 结果通知。');
  const appResult = createElement('p', { className: 'field-error' }, '对外应用使用 OpenApiSecretRef、scope、限流和调用日志，不在页面展示明文密钥。');
  const createTemplateButton = createButton('新建消息模板', 'primary', false);
  const createAppButton = createButton('新建对外应用', 'primary', false);
  createTemplateButton.addEventListener('click', async () => {
    const templateCode = window.prompt('模板编码', `tpl_${Date.now()}`)?.trim();
    if (!templateCode) {
      return;
    }
    const templateType = window.prompt('模板类型：APPROVAL / MESSAGE / IMPORT_EXPORT / AGENT', 'MESSAGE')?.trim() || 'MESSAGE';
    createTemplateButton.disabled = true;
    createTemplateButton.textContent = '创建中...';
    try {
      const template = await createNotificationTemplate(systemId, {
        templateCode,
        templateType,
        channels: ['MESSAGE', 'TODO'],
      });
      templateResult.textContent = `消息模板已创建：${template.templateCode}`;
      reload();
    } catch (error) {
      createTemplateButton.disabled = false;
      createTemplateButton.textContent = '新建消息模板';
      templateResult.textContent = error instanceof Error ? error.message : '新建消息模板失败。';
    }
  });
  createAppButton.addEventListener('click', async () => {
    const appName = window.prompt('应用名称');
    if (!appName?.trim()) {
      return;
    }
    const externalAppCode = window.prompt('应用编码', `app_${Date.now()}`)?.trim();
    if (!externalAppCode) {
      return;
    }
    const callbackUrl = window.prompt('回调地址，可为空', '')?.trim();
    createAppButton.disabled = true;
    createAppButton.textContent = '创建中...';
    try {
      const app = await createOpenApiApp(systemId, {
        appName: appName.trim(),
        externalAppCode,
        callbackUrl,
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
    { id: 'integration-config', className: 'panel' },
    createElement('h2', {}, '数据源与对外应用'),
    createElement(
      'div',
      { className: 'split-grid' },
      createElement(
        'div',
        { className: 'result-panel' },
        createElement('h3', {}, '消息模板'),
        createElement('div', { className: 'inline-actions' }, createTemplateButton),
        templateResult,
        createFilterBar(['模板编码', '模板类型', '渠道', '状态']),
        createTable(
          ['序号', '模板', '类型', '渠道', '状态', '更新时间', '操作'],
          templates.records.map((template, index) => [
            index + 1,
            createElement('span', {}, createElement('strong', {}, template.templateCode), createElement('small', {}, ` ${template.scope}`)),
            template.templateType,
            template.channels?.join(' / ') || '-',
            renderStatusPill(enableStatusText(template.status), enableStatusTone(template.status)),
            formatTime(template.updatedAt),
            createNotificationTemplateActions(systemId, template, templateResult),
          ]),
          '暂无消息模板。',
        ),
        createPagination(templates),
      ),
      createElement(
        'div',
        { className: 'result-panel' },
        createElement('h3', {}, 'OpenAPI 对外应用'),
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
        createPagination(apps),
      ),
    ),
  );
}

function createNotificationTemplateActions(systemId: string, template: NotificationTemplateView, result: HTMLElement): HTMLElement {
  const checkButton = createButton('发布检查', 'secondary', false);
  checkButton.addEventListener('click', async () => {
    checkButton.disabled = true;
    checkButton.textContent = '检查中...';
    try {
      const check = await runNotificationTemplatePublishCheck(systemId, template.templateCode);
      checkButton.disabled = false;
      checkButton.textContent = check.passed ? '检查通过' : '检查失败';
      const failures = check.failureItems.map((item) => item.message).join('；');
      const warnings = check.warningItems.map((item) => item.message).join('；');
      result.textContent = `模板发布检查${check.passed ? '通过' : '未通过'}：${failures || warnings || '-'}，traceId=${check.traceId}`;
    } catch (error) {
      checkButton.disabled = false;
      checkButton.textContent = '检查失败';
      result.textContent = error instanceof Error ? error.message : '消息模板发布检查失败。';
    }
  });
  return createElement('div', { className: 'row-actions' }, checkButton);
}

function createOpenApiActions(systemId: string, app: OpenApiAppView, result: HTMLElement): HTMLElement {
  const rotateButton = createButton('轮换密钥', 'secondary', false);
  rotateButton.addEventListener('click', async () => {
    if (!window.confirm(`确认为「${app.appName}」创建密钥轮换任务吗？`)) {
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

function createWorkAndAgentPanel(data: SystemAdminData, systemId: string, reload: () => void): HTMLElement {
  const agentResult = createElement('p', { className: 'field-error' }, '系统 Agent 策略控制模块、字段、动作、数据范围、外发限制和脱敏策略。');
  const workResult = createElement('p', { className: 'field-error' }, '工作配置保存项目任务、普通任务、日报字段和看板取数字段；状态/标签选项来自数据字典。');
  const saveWorkConfigButton = createButton('保存工作配置', 'primary', false);
  const checkWorkConfigButton = createButton('发布检查', 'secondary', false);
  saveWorkConfigButton.addEventListener('click', async () => {
    const reason = window.prompt('变更原因', '调整工作字段和看板配置')?.trim();
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
  return createElement(
    'section',
    { id: 'work-agent', className: 'panel' },
    createElement('h2', {}, '工作配置与 AI Agent'),
    createElement(
      'div',
      { className: 'split-grid' },
      createElement(
        'div',
        { className: 'result-panel' },
        createElement('h3', {}, '工作配置'),
        createElement('div', { className: 'inline-actions' }, saveWorkConfigButton, checkWorkConfigButton),
        workResult,
        createElement('div', { className: 'simple-stack' },
          createElement('div', { className: 'list-line' }, createElement('span', {}, '项目任务字段'), createElement('strong', {}, String(data.workConfig?.projectTaskFields?.length ?? 0))),
          createElement('div', { className: 'list-line' }, createElement('span', {}, '普通任务字段'), createElement('strong', {}, String(data.workConfig?.plainTaskFields?.length ?? 0))),
          createElement('div', { className: 'list-line' }, createElement('span', {}, '日报字段'), createElement('strong', {}, String(data.workConfig?.dailyReportFields?.length ?? 0))),
          createElement('div', { className: 'list-line' }, createElement('span', {}, '项目任务看板'), createElement('strong', {}, objectKeys(data.workConfig?.projectTaskKanban))),
          createElement('div', { className: 'list-line' }, createElement('span', {}, '普通任务看板'), createElement('strong', {}, objectKeys(data.workConfig?.plainTaskKanban))),
        ),
        data.workConfig?.traceId ? createTraceLine(data.workConfig.traceId) : null,
      ),
      createAgentPolicyPanel(data.agentPolicies, systemId, reload, agentResult),
    ),
  );
}

function createAgentPolicyPanel(page: PageResult<AgentPolicyView>, systemId: string, reload: () => void, result: HTMLElement): HTMLElement {
  const createPolicyButton = createButton('新建策略', 'primary', false);
  createPolicyButton.addEventListener('click', async () => {
    const policyCode = window.prompt('请输入 Agent 策略编码', `agent_policy_${Date.now()}`)?.trim();
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
    createPagination(page),
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

function createSystemSsoLogPanel(data: SystemAdminData, systemId: string, reload: () => void): HTMLElement {
  const ssoResult = createElement('p', { className: 'field-error' }, '统一认证策略可保存身份源继承、租户域名、组织映射和无成员映射反馈。');
  const savePolicyButton = createButton('保存 SSO 策略', 'primary', false);
  const precheckButton = createButton('组织映射预检', 'secondary', false);
  savePolicyButton.addEventListener('click', async () => {
    const providerIds = window.prompt('启用身份源 providerId，多个用逗号分隔', (data.ssoPolicy?.enabledProviderIds ?? []).join(',')) ?? '';
    const tenantDomains = window.prompt('租户域名，多个用逗号分隔', (data.ssoPolicy?.tenantDomains ?? []).join(',')) ?? '';
    savePolicyButton.disabled = true;
    savePolicyButton.textContent = '保存中...';
    try {
      const policy = await updateSystemSsoPolicy(systemId, {
        enabledProviderIds: csv(providerIds),
        tenantDomains: csv(tenantDomains),
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
    const identityProvider = window.prompt('用于组织同步预检的身份源 providerId', data.ssoPolicy?.enabledProviderIds?.[0] ?? '')?.trim();
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
  return createElement(
    'section',
    { id: 'sso-log', className: 'panel' },
    createElement('h2', {}, '统一认证与日志'),
    createElement(
      'div',
      { className: 'split-grid' },
      createElement(
        'div',
        { className: 'result-panel' },
        createElement('h3', {}, '系统 SSO'),
        createElement('div', { className: 'inline-actions' }, savePolicyButton, precheckButton),
        ssoResult,
        createElement('div', { className: 'simple-stack' },
          createElement('div', { className: 'list-line' }, createElement('span', {}, '启用身份源'), createElement('strong', {}, String(data.ssoPolicy?.enabledProviderIds?.length ?? 0))),
          createElement('div', { className: 'list-line' }, createElement('span', {}, '租户域名'), createElement('strong', {}, String(data.ssoPolicy?.tenantDomains?.length ?? 0))),
          createElement('div', { className: 'list-line' }, createElement('span', {}, '组织映射'), createElement('span', {}, objectKeys(data.ssoPolicy?.orgMapping))),
          createElement('div', { className: 'list-line' }, createElement('span', {}, '员工绑定'), createElement('span', {}, objectKeys(data.ssoPolicy?.employeeBinding))),
          createElement('div', { className: 'list-line' }, createElement('span', {}, '策略状态'), renderStatusPill(data.ssoPolicy?.status ?? '未配置', data.ssoPolicy?.status === 'ENABLED' ? 'success' : 'warning')),
        ),
        data.ssoPolicy?.traceId ? createTraceLine(data.ssoPolicy.traceId) : null,
      ),
      createElement(
        'div',
        { className: 'result-panel' },
        createElement('h3', {}, '日志管理'),
        createFilterBar(['日志类型', '账号', '模块', '结果', 'traceId', '时间范围']),
        createLogTable(data.logs),
      ),
    ),
  );
}

function createLogTable(page: PageResult<AuditLogView>): HTMLElement {
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
    createPagination(page),
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

function createConfigTile(title: string, body: string): HTMLElement {
  return createElement('div', { className: 'result-panel' }, createElement('strong', {}, title), createElement('p', {}, body));
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

function createTreeButton(label: string, active = false): HTMLButtonElement {
  const button = createElement('button', { className: active ? 'sidebar-item active' : 'sidebar-item' }, label);
  button.addEventListener('click', () => {
    button.closest('.mini-tree')?.querySelectorAll('.sidebar-item').forEach((item) => item.classList.remove('active'));
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
