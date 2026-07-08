import {
  createOpenApiApp,
  createSystemDataSource,
  createSystemDictItem,
  createSystemDictType,
  createSystemFlow,
  createSystemAgentPolicy,
  assignSystemRoleMembers,
  bindSystemMemberAccount,
  createSystemDepartment,
  createSystemMember,
  createSystemModule,
  createSystemModuleAction,
  createSystemModuleField,
  createSystemRole,
  loadSystemRolePermissions,
  loadFlowCanvas,
  loadFlowNodeLibrary,
  loadModulePageSchema,
  loadRuntimeModulePage,
  loadSystemModuleImportExportConfig,
  loadSystemModuleListSchema,
  loadSystemDictImpact,
  listModulePageDesigns,
  listPrintTemplates,
  listSystemModuleActions,
  listSystemModuleFields,
  listSystemModuleScenes,
  loadSystemAdminData,
  precheckSystemSsoOrgSync,
  previewPrintTemplate,
  previewSystemEffectivePermission,
  previewSystemEffectivePermissions,
  listSystemPermissionPreviewLogs,
  publishSystemFlow,
  publishSystemModule,
  publishSystemDictType,
  rollbackSystemModule,
  publishModulePage,
  publishPrintTemplate,
  savePrintTemplate,
  saveFlowCanvas,
  saveModulePageDesign,
  saveSystemModuleImportExportConfig,
  saveSystemRolePermissions,
  saveSystemModuleScene,
  checkSystemDataSourceConnection,
  rotateOpenApiSecret,
  runHomePagePublishCheck,
  runModulePagePublishCheck,
  runFlowPublishCheck,
  runModulePublishCheck,
  runPrintTemplatePublishCheck,
  runSystemDataSourcePublishCheck,
  runSystemAgentPolicyPublishCheck,
  runWorkConfigPublishCheck,
  simulateSystemFlow,
  updateSystemHomePageConfig,
  updateSystemMember,
  updateSystemModule,
  updateSystemWorkConfig,
  updateSystemSsoPolicy,
  type AdminDynamicListSchema,
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
  type FlowSimulationResult,
  type FlowPublishCheckResult,
  type HomePageConfigView,
  type HomePageWidgetConfig,
  type MemberView,
  type ModuleActionConfigView,
  type ModuleImportExportConfigView,
  type ModuleListSceneView,
  type ModulePageDesignView,
  type PageComponentConfig,
  type ModulePublishCheckResult,
  type OpenApiAppView,
  type PrintTemplatePreview,
  type PrintTemplateView,
  type RoleView,
  type RolePermissionView,
  type PermissionBatchPreviewView,
  type PermissionPreviewAuditView,
  type SystemAdminData,
  type SystemAdminPageOptions,
  type SystemDataSourceCheckResult,
  type SystemDataSourceView,
} from '../../api/liveData';
import type { FieldDefinitionVO, PageResult, PermissionDecisionVO } from '../../api/types';
import type { Navigate } from '../../app/app';
import { shellState } from '../../app/state';
import { createButton, createElement, createTraceLine, type ChildNodeValue } from '../../shared/components';
import { requestConfirmation, requestFormInput, requestTextInput } from '../../shared/dialogs';
import { createFilterBar } from '../../shared/filters';
import { renderStatusPill, type StatusTone } from '../../shared/status';
import { createPageSchemaPreview } from '../schema/schemaRenderer';

export function renderSystemAdmin(navigate: Navigate, initialSection = 'system-info'): HTMLElement {
  const systemId = activeSystemId();
  let activeSection = initialSection;
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
    'org-structure': createOrgRolePanel(data.departments, data.members, data.roles, data.modules, systemId, reload, 'org-structure', (pageNo) => onPageChange('membersPageNo', pageNo), (pageNo) => onPageChange('rolesPageNo', pageNo)),
    'role-management': createOrgRolePanel(data.departments, data.members, data.roles, data.modules, systemId, reload, 'role-management', (pageNo) => onPageChange('membersPageNo', pageNo), (pageNo) => onPageChange('rolesPageNo', pageNo)),
    'module-config': createModuleConfigPanel(data.moduleGroups, data.modules, data.dictTypes.records, systemId, reload, (pageNo) => onPageChange('modulesPageNo', pageNo)),
    'flow-management': createFlowAndDictPanel(data.flows, data.dictTypes, systemId, reload, 'flow-management', (pageNo) => onPageChange('flowsPageNo', pageNo), (pageNo) => onPageChange('dictTypesPageNo', pageNo)),
    'dict-management': createFlowAndDictPanel(data.flows, data.dictTypes, systemId, reload, 'dict-management', (pageNo) => onPageChange('flowsPageNo', pageNo), (pageNo) => onPageChange('dictTypesPageNo', pageNo)),
    'dashboard-config': createDashboardConfigPanel(data, systemId, reload),
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
      { className: 'onboarding-checklist' },
      ...steps.map((step, index) => {
        const button = createButton(step.action, step.status === '待配置' ? 'primary' : 'secondary', false);
        button.addEventListener('click', () => onSelect(step.target));
        return createElement(
          'article',
          { className: 'onboarding-check-item' },
          createElement('span', { className: 'step-index' }, String(index + 1)),
          createElement('div', { className: 'onboarding-check-main' }, createElement('strong', {}, step.title)),
          renderStatusPill(step.status, step.status === '待配置' ? 'warning' : 'info'),
          button,
        );
      }),
    ),
  );
}

function createDashboardConfigPanel(data: SystemAdminData, systemId: string, reload: () => void): HTMLElement {
  const config = data.homePageConfig;
  const visibleWidgets = (config?.widgets ?? []).filter((widget) => widget.visible !== false);
  const result = createElement('p', { className: 'field-error' }, '首页设计会保存到系统后台配置，发布检查通过后由业务首页读取。');
  const saveButton = createButton('保存首页配置', 'primary', false);
  const checkButton = createButton('发布检查', 'secondary', false);
  saveButton.addEventListener('click', async () => {
    const values = await requestFormInput('首页设计配置', [
      { name: 'title', label: '首页标题', defaultValue: config?.title ?? '系统工作台' },
      { name: 'subtitle', label: '首页说明', defaultValue: config?.subtitle ?? '当前系统成员权限范围内的业务入口、待办、消息和工作概览。' },
      { name: 'visualTone', label: '视觉语义', defaultValue: config?.visualTone ?? 'calm-workbench' },
      { name: 'widgets', label: '显示组件编码（逗号分隔）', defaultValue: visibleWidgets.map((widget) => widget.widgetCode).join(',') || 'overview,warnings,calendar,modules' },
      { name: 'changeReason', label: '变更原因', defaultValue: '调整业务首页配置' },
    ], '保存');
    if (!values) {
      return;
    }
    saveButton.disabled = true;
    saveButton.textContent = '保存中...';
    try {
      const saved = await updateSystemHomePageConfig(systemId, {
        title: values.title,
        subtitle: values.subtitle,
        visualTone: values.visualTone,
        widgets: buildHomePageWidgets(csv(values.widgets), config),
        changeReason: values.changeReason,
      });
      result.textContent = `首页配置已保存：${saved.title}，traceId=${saved.traceId ?? '-'}`;
      reload();
    } catch (error) {
      saveButton.disabled = false;
      saveButton.textContent = '保存首页配置';
      result.textContent = error instanceof Error ? error.message : '保存首页配置失败。';
    }
  });
  checkButton.addEventListener('click', async () => {
    checkButton.disabled = true;
    checkButton.textContent = '检查中...';
    try {
      const check = await runHomePagePublishCheck(systemId);
      result.textContent = `首页发布检查${check.passed ? '通过' : '未通过'}：${check.targetVersion ?? '-'}，traceId=${check.traceId}`;
    } catch (error) {
      result.textContent = error instanceof Error ? error.message : '首页发布检查失败。';
    } finally {
      checkButton.disabled = false;
      checkButton.textContent = '发布检查';
    }
  });
  return createElement(
    'section',
    {
      id: 'dashboard-config',
      className: 'panel',
      dataset: {
        homeConfigPanel: 'true',
        productSurface: 'system-home-config',
        homeConfigStatus: String(config?.publishState?.status ?? 'DRAFT'),
        homeConfigWidgetCount: String(visibleWidgets.length),
        homeConfigTraceId: config?.traceId ?? '',
      },
    },
    createElement('h2', {}, '首页配置'),
    createElement(
      'div',
      { className: 'metric-grid' },
      createMetric('首页标题', config?.title ?? '系统工作台'),
      createMetric('视觉语义', config?.visualTone ?? 'calm-workbench'),
      createMetric('显示组件', String(visibleWidgets.length || 4)),
      createMetric('发布状态', String(config?.publishState?.status ?? 'DRAFT')),
    ),
    createElement(
      'div',
      { className: 'simple-stack' },
      createElement('div', { className: 'list-line' }, createElement('span', {}, '首页说明'), createElement('strong', {}, config?.subtitle ?? '当前系统成员权限范围内的业务入口、待办、消息和工作概览。')),
      createElement('div', { className: 'list-line' }, createElement('span', {}, '运行态读取'), createElement('strong', {}, '/work/home-page')),
      createElement('div', { className: 'list-line' }, createElement('span', {}, '页面组件'), createElement('strong', {}, widgetSummary(visibleWidgets))),
      createElement('div', { className: 'inline-actions' }, saveButton, checkButton),
      result,
      createElement(
        'div',
        { className: 'metric-grid' },
        ...buildHomePageWidgets(visibleWidgets.map((widget) => widget.widgetCode), config).map((widget) => createMetric(widget.widgetName, widget.sourceType)),
      ),
    ),
    config?.traceId ? createTraceLine(config.traceId) : null,
  );
}

function createModulePageDesigner(systemId: string, module: BackendModule, reload: () => void): HTMLElement {
  const host = createElement('section', {
    className: 'module-page-designer-slot',
    dataset: {
      productSurface: 'module-page-designer-slot',
      modulePageDesignerModuleId: module.moduleId,
    },
  }, createLoadingPanel('正在读取模块页面配置...'));
  void listModulePageDesigns(systemId, module.moduleId)
    .then((pages) => {
      host.replaceChildren(createPageDesignerPanel(module, pages, systemId, reload));
    })
    .catch((error) => {
      host.replaceChildren(createElement(
        'section',
        { className: 'result-panel page-designer-panel', dataset: { modulePageDesigner: 'true' } },
        createElement('h3', {}, '模块页面设计器'),
        createElement('p', { className: 'field-error' }, error instanceof Error ? error.message : '模块页面配置读取失败。'),
      ));
    });
  return host;
}

function createPageDesignerPanel(module: BackendModule | undefined, pages: ModulePageDesignView[], systemId: string, reload: () => void): HTMLElement {
  const result = createElement('p', { className: 'field-error' }, module
    ? '页面设计器保存模块页面草稿，发布后运行态才能读取。'
    : '请先创建业务模块，再配置模块页面。');
  const saveButton = createButton('保存模块页面', 'primary', !module, module ? undefined : '没有可配置的业务模块。');
  const checkButton = createButton('发布检查', 'secondary', !module, module ? undefined : '没有可检查的页面。');
  const publishButton = createButton('发布页面', 'secondary', !module, module ? undefined : '没有可发布的页面。');
  const runtimeButton = createButton('运行态读取', 'ghost', !module, module ? undefined : '没有可读取的页面。');
  const schemaButton = createButton('Schema 预览', 'ghost', !module, module ? undefined : '没有可预览的页面。');
  const schemaPreview = createElement('div', { className: 'schema-preview-slot' });
  const currentPage = pages[0];
  const componentDraft = normalizePageComponents(currentPage?.components);
  const saveComponentDraft = async (changeReason = '调整页面组件布局') => {
    if (!module) {
      return;
    }
    const saved = await saveModulePageDesign(systemId, module.moduleId, {
      pageCode: currentPage?.pageCode ?? 'main',
      pageName: currentPage?.pageName ?? `${module.name}主页面`,
      pageType: currentPage?.pageType ?? 'MODULE_LIST',
      route: currentPage?.route ?? `/systems/${systemId}/modules/${module.moduleId}`,
      layoutMode: currentPage?.layoutMode ?? 'left-list-right-detail',
      components: normalizePageComponents(componentDraft),
      visibleRoleIds: currentPage?.visibleRoleIds ?? [],
      changeReason,
    });
    result.textContent = `组件布局已保存：${saved.components.length} 个组件，traceId=${saved.traceId ?? '-'}`;
  };
  saveButton.addEventListener('click', async () => {
    if (!module) {
      return;
    }
    const values = await requestFormInput('模块页面设计', [
      { name: 'pageCode', label: '页面编码', defaultValue: currentPage?.pageCode ?? 'main' },
      { name: 'pageName', label: '页面名称', defaultValue: currentPage?.pageName ?? `${module.name}主页面` },
      { name: 'pageType', label: '页面类型', defaultValue: currentPage?.pageType ?? 'MODULE_LIST' },
      { name: 'layoutMode', label: '布局模式', defaultValue: currentPage?.layoutMode ?? 'left-list-right-detail' },
      { name: 'changeReason', label: '变更原因', defaultValue: '调整模块页面设计' },
    ], '保存');
    if (!values) {
      return;
    }
    saveButton.disabled = true;
    saveButton.textContent = '保存中...';
    try {
      const saved = await saveModulePageDesign(systemId, module.moduleId, {
        pageCode: values.pageCode,
        pageName: values.pageName,
        pageType: values.pageType,
        route: `/systems/${systemId}/modules/${module.moduleId}`,
        layoutMode: values.layoutMode,
        components: normalizePageComponents(componentDraft),
        visibleRoleIds: currentPage?.visibleRoleIds ?? [],
        changeReason: values.changeReason,
      });
      result.textContent = `页面草稿已保存：${saved.pageName}，traceId=${saved.traceId ?? '-'}`;
      reload();
    } catch (error) {
      saveButton.disabled = false;
      saveButton.textContent = '保存模块页面';
      result.textContent = error instanceof Error ? error.message : '页面草稿保存失败。';
    }
  });
  checkButton.addEventListener('click', async () => {
    if (!module) {
      return;
    }
    const pageCode = currentPage?.pageCode ?? 'main';
    checkButton.disabled = true;
    checkButton.textContent = '检查中...';
    try {
      const check = await runModulePagePublishCheck(systemId, module.moduleId, pageCode);
      result.textContent = `页面发布检查${check.passed ? '通过' : '未通过'}，${check.failureItems?.[0]?.message ?? check.warningItems?.[0]?.message ?? '-'}，traceId=${check.traceId}`;
    } catch (error) {
      result.textContent = error instanceof Error ? error.message : '页面发布检查失败。';
    } finally {
      checkButton.disabled = false;
      checkButton.textContent = '发布检查';
    }
  });
  publishButton.addEventListener('click', async () => {
    if (!module) {
      return;
    }
    const pageCode = currentPage?.pageCode ?? 'main';
    publishButton.disabled = true;
    publishButton.textContent = '发布中...';
    try {
      const published = await publishModulePage(systemId, module.moduleId, pageCode, '发布模块页面设计');
      result.textContent = `页面已发布：${published.version ?? '-'}，traceId=${published.traceId}`;
      reload();
    } catch (error) {
      result.textContent = error instanceof Error ? error.message : '页面发布失败。';
    } finally {
      publishButton.disabled = false;
      publishButton.textContent = '发布页面';
    }
  });
  runtimeButton.addEventListener('click', async () => {
    if (!module) {
      return;
    }
    const pageCode = currentPage?.pageCode ?? 'main';
    runtimeButton.disabled = true;
    runtimeButton.textContent = '读取中...';
    try {
      const runtimePage = await loadRuntimeModulePage(systemId, module.moduleId, pageCode);
      result.textContent = `运行态读取成功：${runtimePage.pageName}，组件 ${runtimePage.components.length} 个，版本 ${runtimePage.publishedVersion ?? '-'}`;
    } catch (error) {
      result.textContent = error instanceof Error ? error.message : '运行态页面读取失败。';
    } finally {
      runtimeButton.disabled = false;
      runtimeButton.textContent = '运行态读取';
    }
  });
  schemaButton.addEventListener('click', async () => {
    if (!module) {
      return;
    }
    const pageCode = currentPage?.pageCode ?? 'main';
    schemaButton.disabled = true;
    schemaButton.textContent = '读取中...';
    schemaPreview.replaceChildren(createElement('p', { className: 'empty-hint' }, '正在读取发布后的页面 schema...'));
    try {
      const schema = await loadModulePageSchema(systemId, module.moduleId, pageCode, 'published');
      schemaPreview.replaceChildren(createPageSchemaPreview(schema));
      result.textContent = `Schema 读取成功：${schema.schemaVersion}，字段 ${schema.fields.length} 个，组件 ${schema.components.length} 个。`;
    } catch (error) {
      schemaPreview.replaceChildren(createElement('p', { className: 'field-error' }, error instanceof Error ? error.message : 'Schema 读取失败。'));
    } finally {
      schemaButton.disabled = false;
      schemaButton.textContent = 'Schema 预览';
    }
  });
  return createElement(
    'div',
    {
      className: 'result-panel page-designer-panel',
      dataset: {
        modulePageDesigner: 'true',
        productSurface: 'module-page-designer',
        modulePageDesignerModuleId: module?.moduleId ?? '',
        modulePageDesignerPageId: currentPage?.pageId ?? '',
        modulePageDesignerPageCode: currentPage?.pageCode ?? 'main',
        modulePageDesignerStatus: currentPage?.publishStatus ?? 'EMPTY',
        modulePageDesignerVersion: currentPage?.publishedVersion ?? '',
        modulePageDesignerComponentCount: String(componentDraft.length),
        modulePageDesignerVisibleComponentCount: String(componentDraft.filter((component) => component.visible !== false).length),
        modulePageDesignerTraceId: currentPage?.traceId ?? '',
      },
    },
    createElement('h3', {}, '模块页面设计器'),
    createElement('div', { className: 'inline-actions' }, saveButton, checkButton, publishButton, runtimeButton, schemaButton),
    result,
    module
      ? createElement('div', { className: 'simple-stack' },
          createElement('div', { className: 'list-line' }, createElement('span', {}, '当前模块'), createElement('strong', {}, `${module.name} (${module.moduleCode})`)),
          createElement('div', { className: 'list-line' }, createElement('span', {}, '页面数量'), createElement('strong', {}, String(pages.length))),
          createElement('div', { className: 'list-line' }, createElement('span', {}, '当前页面'), createElement('strong', {}, currentPage ? `${currentPage.pageName} / ${currentPage.publishStatus}` : '未配置')),
          createElement('div', { className: 'list-line' }, createElement('span', {}, '组件预览'), createElement('strong', {}, componentSummary(componentDraft))),
        )
      : createElement('section', { className: 'runtime-card' }, '暂无业务模块，页面设计器等待模块配置。'),
    module ? createPageComponentWorkbench(componentDraft, saveComponentDraft, (message) => {
      result.textContent = message;
    }) : null,
    schemaPreview,
    currentPage?.traceId ? createTraceLine(currentPage.traceId) : null,
  );
}

function createPageComponentWorkbench(
  components: PageComponentConfig[],
  onSave: (changeReason?: string) => Promise<void>,
  onMessage: (message: string) => void,
): HTMLElement {
  const list = createElement('div', { className: 'page-component-list page-component-drag-canvas', dataset: { pageComponentDragCanvas: 'true' } });
  const preview = createElement('div', { className: 'page-mobile-preview-list' });
  const dragStatus = createElement('p', { className: 'empty-hint', dataset: { pageComponentDragStatus: 'ready' } }, '组件可拖动排序，也可用键盘方向键移动。');
  let dragIndex: number | null = null;
  const saveButton = createButton('保存组件布局', 'primary', false);
  saveButton.dataset.pageComponentSave = 'true';
  saveButton.setAttribute('aria-label', '保存页面组件布局');

  const moveComponent = (fromIndex: number, toIndex: number, message?: string) => {
    if (fromIndex === toIndex || fromIndex < 0 || toIndex < 0 || fromIndex >= components.length || toIndex >= components.length) {
      return;
    }
    const [item] = components.splice(fromIndex, 1);
    components.splice(toIndex, 0, item);
    item.props = { ...(item.props ?? {}), dragCanvas: 'page-designer', lastMovedBy: 'drag-or-keyboard' };
    sync(message ?? `组件已移动：${item.title || item.componentCode}`);
  };

  const sync = (message?: string) => {
    resortPageComponents(components);
    components.forEach((component, index) => ensurePageComponentProps(component, index));
    list.replaceChildren(...components.map((component, index) => createPageComponentRow(component, index, components, sync, moveComponent, {
      get: () => dragIndex,
      set: (value) => { dragIndex = value; },
    })));
    preview.replaceChildren(...components
      .filter((component) => component.visible !== false)
      .map((component) => createElement(
        'article',
        {
          className: 'page-mobile-preview-item',
          dataset: {
            mobilePreviewComponent: component.componentCode,
            mobilePreviewComponentWidth: componentLayoutWidth(component),
            mobilePreviewComponentPlacement: componentPlacement(component),
          },
        },
        createElement('span', {}, `${component.componentType} / ${componentLayoutWidth(component)}`),
        createElement('strong', {}, component.title || component.componentCode),
        createElement('small', {}, componentPropsSummary(component)),
      )));
    dragStatus.dataset.pageComponentDragStatus = message ? 'updated' : 'ready';
    if (message) {
      dragStatus.textContent = message;
      onMessage(message);
    }
  };

  saveButton.addEventListener('click', async () => {
    saveButton.disabled = true;
    saveButton.textContent = '保存中...';
    try {
      await onSave('保存拖拽画布组件布局');
      onMessage(`组件布局已保存：${components.length} 个组件，${components.filter((component) => component.visible !== false).length} 个可见。`);
    } catch (error) {
      onMessage(error instanceof Error ? error.message : '组件布局保存失败。');
    } finally {
      saveButton.disabled = false;
      saveButton.textContent = '保存组件布局';
    }
  });

  const addButton = createButton('添加表单组件', 'secondary', false);
  addButton.dataset.pageComponentAdd = 'true';
  addButton.setAttribute('aria-label', '添加一个表单组件');
  addButton.addEventListener('click', () => {
    const nextCode = uniqueComponentCode(components, 'form_extra');
    components.push({
      componentCode: nextCode,
      componentType: 'FORM',
      title: '补充表单',
      dataSource: 'MODULE_FIELDS',
      visible: true,
      sort: (components.length + 1) * 10,
      props: { createdBy: 'page-component-workbench', width: 'full', placement: 'main', dragCanvas: 'page-designer' },
    });
    sync(`已添加组件：${nextCode}`);
  });

  sync();

  return createElement(
    'section',
    {
      className: 'page-component-workbench',
      dataset: {
        pageComponentWorkbench: 'true',
        focusCheck: 'page-component-workbench',
      },
    },
    createElement(
      'div',
      { className: 'runtime-card-head' },
      createElement('div', {}, createElement('h4', {}, '组件工作台'), createElement('p', {}, '通过拖拽画布调整组件顺序，也可以用键盘方向键移动；宽度和位置会随页面设计一起保存并发布到运行态 schema。')),
      createElement('div', { className: 'inline-actions' }, addButton, saveButton),
    ),
    dragStatus,
    list,
    createElement(
      'div',
      {
        className: 'page-mobile-preview',
        dataset: {
          pageMobilePreview: 'true',
          focusCheck: 'page-mobile-preview',
        },
      },
      createElement('div', { className: 'module-config-head' }, createElement('strong', {}, '移动端预览'), createElement('span', {}, '按运行态顺序单列展示')),
      preview,
    ),
  );
}

function createPageComponentRow(
  component: PageComponentConfig,
  index: number,
  components: PageComponentConfig[],
  sync: (message?: string) => void,
  moveComponent: (fromIndex: number, toIndex: number, message?: string) => void,
  dragState: { get: () => number | null; set: (value: number | null) => void },
): HTMLElement {
  ensurePageComponentProps(component, index);
  const moveUpButton = createButton('上移', 'ghost', index === 0, index === 0 ? '已经是第一个组件。' : undefined);
  const moveDownButton = createButton('下移', 'ghost', index >= components.length - 1, index >= components.length - 1 ? '已经是最后一个组件。' : undefined);
  const widthButton = createButton('宽度', 'ghost', false);
  const placementButton = createButton('位置', 'ghost', false);
  const copyButton = createButton('复制组件', 'secondary', false);
  const toggleButton = createButton(component.visible === false ? '显示' : '隐藏', 'secondary', false);
  [moveUpButton, moveDownButton, widthButton, placementButton, copyButton, toggleButton].forEach((button) => {
    button.dataset.focusCheck = 'page-component-action';
  });
  moveUpButton.setAttribute('aria-label', `上移组件 ${component.title || component.componentCode}`);
  moveDownButton.setAttribute('aria-label', `下移组件 ${component.title || component.componentCode}`);
  widthButton.dataset.pageComponentPropAction = 'width';
  placementButton.dataset.pageComponentPropAction = 'placement';
  widthButton.setAttribute('aria-label', `切换组件宽度 ${component.title || component.componentCode}`);
  placementButton.setAttribute('aria-label', `切换组件位置 ${component.title || component.componentCode}`);
  copyButton.setAttribute('aria-label', `复制组件 ${component.title || component.componentCode}`);
  toggleButton.setAttribute('aria-label', `${component.visible === false ? '显示' : '隐藏'}组件 ${component.title || component.componentCode}`);

  moveUpButton.addEventListener('click', () => {
    moveComponent(index, index - 1, `组件已上移：${component.title || component.componentCode}`);
  });
  moveDownButton.addEventListener('click', () => {
    moveComponent(index, index + 1, `组件已下移：${component.title || component.componentCode}`);
  });
  widthButton.addEventListener('click', () => {
    component.props = { ...(component.props ?? {}), width: nextComponentWidth(component), dragCanvas: 'page-designer' };
    sync(`组件宽度已切换：${component.title || component.componentCode} / ${componentLayoutWidth(component)}`);
  });
  placementButton.addEventListener('click', () => {
    component.props = { ...(component.props ?? {}), placement: nextComponentPlacement(component), dragCanvas: 'page-designer' };
    sync(`组件位置已切换：${component.title || component.componentCode} / ${componentPlacement(component)}`);
  });
  copyButton.addEventListener('click', () => {
    const copiedCode = uniqueComponentCode(components, `${component.componentCode}_copy`);
    components.splice(index + 1, 0, {
      ...component,
      componentCode: copiedCode,
      title: `${component.title || component.componentCode} 副本`,
      visible: true,
      props: {
        ...(component.props ?? {}),
        copiedFrom: component.componentCode,
        dragCanvas: 'page-designer',
      },
    });
    sync(`已复制组件：${copiedCode}`);
  });
  toggleButton.addEventListener('click', () => {
    component.visible = component.visible === false;
    sync(`${component.visible === false ? '已隐藏' : '已显示'}组件：${component.title || component.componentCode}`);
  });

  const row = createElement(
    'article',
    {
      className: `page-component-row${component.visible === false ? ' is-hidden' : ''}`,
      dataset: {
        pageComponentRow: 'true',
        componentCode: component.componentCode,
        componentVisible: component.visible === false ? 'false' : 'true',
        componentSort: String(component.sort ?? (index + 1) * 10),
        componentWidth: componentLayoutWidth(component),
        componentPlacement: componentPlacement(component),
        componentDraggable: 'true',
        focusCheck: 'page-component-row',
      },
    },
    createElement(
      'div',
      { className: 'page-component-main' },
      createElement('span', { className: 'step-index' }, String(index + 1)),
      createElement(
        'div',
        {},
        createElement('strong', {}, component.title || component.componentCode),
        createElement('p', {}, `${component.componentType} / ${component.dataSource} / ${component.componentCode}`),
      ),
    ),
    createElement(
      'div',
      { className: 'page-component-meta' },
      renderStatusPill(component.visible === false ? '已隐藏' : '可见', component.visible === false ? 'warning' : 'success'),
      createElement('span', {}, `sort ${component.sort ?? (index + 1) * 10}`),
      createElement('span', { className: 'page-component-props' }, componentPropsSummary(component)),
    ),
    createElement('div', { className: 'row-actions' }, moveUpButton, moveDownButton, widthButton, placementButton, copyButton, toggleButton),
  );
  row.draggable = true;
  row.tabIndex = 0;
  row.setAttribute('aria-label', `可拖动页面组件 ${component.title || component.componentCode}`);
  row.addEventListener('dragstart', (event) => {
    dragState.set(index);
    row.classList.add('is-dragging');
    event.dataTransfer?.setData('text/plain', component.componentCode);
    event.dataTransfer?.setData('application/x-page-component-index', String(index));
  });
  row.addEventListener('dragover', (event) => {
    event.preventDefault();
  });
  row.addEventListener('drop', (event) => {
    event.preventDefault();
    const fromIndex = dragState.get();
    if (fromIndex !== null) {
      moveComponent(fromIndex, index, `组件已拖放到第 ${index + 1} 位：${component.title || component.componentCode}`);
    }
    dragState.set(null);
  });
  row.addEventListener('dragend', () => {
    row.classList.remove('is-dragging');
    dragState.set(null);
  });
  row.addEventListener('keydown', (event) => {
    if (event.key === 'ArrowUp' && index > 0) {
      event.preventDefault();
      moveComponent(index, index - 1, `键盘上移组件：${component.title || component.componentCode}`);
    }
    if (event.key === 'ArrowDown' && index < components.length - 1) {
      event.preventDefault();
      moveComponent(index, index + 1, `键盘下移组件：${component.title || component.componentCode}`);
    }
  });
  return row;
}

function createOrgRolePanel(
  departments: DepartmentNode[],
  members: PageResult<MemberView>,
  roles: PageResult<RoleView>,
  modules: PageResult<BackendModule>,
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
          createRolePermissionWorkbench(systemId, roles.records, modules.records, result),
        )
      : createElement(
          'div',
          { className: 'tree-table-layout' },
          createDepartmentTree(departments),
          createElement(
            'div',
            { className: 'admin-stack' },
            createFilterBar(['员工', '状态', '绑定状态']),
            createMemberTable(members, roles.records, onMembersPageChange),
            createOrgMemberDeliveryPanel(systemId, departments, members.records, roles.records, result, reload),
          ),
        ),
  );
}

function createDepartmentTree(departments: DepartmentNode[]): HTMLElement {
  const nodes = flattenDepartments(departments);
  return createElement(
    'aside',
    { className: 'mini-tree', dataset: { orgDepartmentTreeR90: 'true', orgDepartmentCount: String(nodes.length) } },
    createElement('strong', {}, '部门树'),
    nodes.length === 0
      ? createElement('p', { dataset: { orgDepartmentEmptyR90: 'true' } }, '暂无部门')
      : createElement(
          'div',
          { className: 'simple-stack' },
          ...nodes.map(({ node, depth }) => {
            const button = createTreeButton(`${'　'.repeat(depth)}${node.deptName}`, depth === 0);
            button.dataset.orgDepartmentNodeR90 = node.deptId;
            button.dataset.orgDepartmentParentR90 = node.parentId ?? '0';
            button.dataset.orgDepartmentStatusR90 = String(node.status);
            return button;
          }),
        ),
  );
}

function createMemberTable(page: PageResult<MemberView>, roles: RoleView[], onPageChange: (nextPage: number) => void): HTMLElement {
  return createElement(
    'section',
    { className: 'result-panel', dataset: { orgMemberTableR90: 'true', orgMemberTotal: String(page.total) } },
    createElement('h3', {}, '员工列表'),
    createTable(
      ['序号', '姓名', '工号', '部门', '角色', '绑定', '状态', '更新时间'],
      page.records.map((member, index) => [
        index + 1,
        createElement(
          'span',
          {
            dataset: {
              memberId: member.systemMemberId,
              memberTenantId: member.tenantId,
              memberBindingStatus: member.bindingStatus ?? '',
              memberRoleIds: (member.roleIds ?? []).join(','),
              orgMemberR90: 'true',
            },
          },
          member.memberName,
        ),
        member.employeeNo || '-',
        member.deptId || '-',
        formatRoleNames(member.roleIds ?? [], roles),
        createBindingPill(member.bindingStatus),
        renderStatusPill(enableStatusText(member.status), enableStatusTone(member.status)),
        formatTime(member.updatedAt),
      ]),
      '暂无员工。',
    ),
    createPagination(page, onPageChange),
  );
}

function createOrgMemberDeliveryPanel(
  systemId: string,
  departments: DepartmentNode[],
  members: MemberView[],
  roles: RoleView[],
  result: HTMLElement,
  reload: () => void,
): HTMLElement {
  const memberSelect = createElement('select', { ariaLabel: '成员交付对象', dataset: { r90MemberSelect: 'true' } });
  const roleSelect = createElement('select', { ariaLabel: '成员角色', dataset: { r90RoleSelect: 'true' } });
  const deptSelect = createElement('select', { ariaLabel: '成员部门', dataset: { r90DepartmentSelect: 'true' } });
  const loginInput = createElement('input', { ariaLabel: '绑定账号登录名', dataset: { r90LoginInput: 'true' } });
  loginInput.placeholder = '输入已有账号登录名 / 手机 / 邮箱';
  const readiness = createElement('div', { className: 'org-member-readiness', dataset: { r90BindingReadiness: 'true' } });
  const bindButton = createButton('绑定账号', 'primary', members.length === 0, '需要先创建成员。');
  const assignButton = createButton('分配角色', 'secondary', members.length === 0 || roles.length === 0, '需要先创建成员和角色。');
  const updateButton = createButton('更新部门', 'secondary', members.length === 0 || departments.length === 0, '需要先创建成员和部门。');
  const previewButton = createButton('预览权限', 'secondary', members.length === 0 || roles.length === 0, '需要先创建成员和角色。');
  bindButton.dataset.r90BindAccount = 'true';
  assignButton.dataset.r90AssignRole = 'true';
  updateButton.dataset.r90UpdateMember = 'true';
  previewButton.dataset.r90PreviewPermission = 'true';

  const departmentOptions = flattenDepartments(departments);
  memberSelect.replaceChildren(...members.map((member) => {
    const option = createElement('option', { dataset: { r90MemberOption: member.systemMemberId, r90MemberBinding: member.bindingStatus ?? 'UNBOUND' } }, `${member.memberName} / ${member.bindingStatus ?? 'UNBOUND'}`);
    option.value = member.systemMemberId;
    return option;
  }));
  roleSelect.replaceChildren(...roles.map((role) => {
    const option = createElement('option', { dataset: { r90RoleOption: role.roleId, r90RoleCode: role.roleCode } }, `${role.roleName} / ${role.roleCode}`);
    option.value = role.roleId;
    return option;
  }));
  deptSelect.replaceChildren(...departmentOptions.map(({ node, depth }) => {
    const option = createElement('option', { dataset: { r90DepartmentOption: node.deptId } }, `${'　'.repeat(depth)}${node.deptName}`);
    option.value = node.deptId;
    return option;
  }));

  const selectedMember = () => members.find((member) => member.systemMemberId === memberSelect.value) ?? members[0];
  const selectedRole = () => roles.find((role) => role.roleId === roleSelect.value) ?? roles[0];
  const refreshReadiness = () => {
    const member = selectedMember();
    const role = selectedRole();
    if (!member) {
      readiness.replaceChildren(createElement('p', {}, '还没有成员。先新建员工，再绑定账号和角色。'));
      return;
    }
    if (member.deptId && Array.from(deptSelect.options).some((option) => option.value === member.deptId)) {
      deptSelect.value = member.deptId;
    }
    readiness.dataset.r90SelectedMember = member.systemMemberId;
    readiness.dataset.r90SelectedBinding = member.bindingStatus ?? 'UNBOUND';
    readiness.dataset.r90SelectedRoles = (member.roleIds ?? []).join(',');
    readiness.replaceChildren(
      createElement('div', { className: 'list-line' }, createElement('span', {}, '成员'), createElement('strong', {}, member.memberName)),
      createElement('div', { className: 'list-line' }, createElement('span', {}, '绑定状态'), createBindingPill(member.bindingStatus)),
      createElement('div', { className: 'list-line' }, createElement('span', {}, '已分配角色'), createElement('strong', {}, formatRoleNames(member.roleIds ?? [], roles))),
      createElement('div', { className: 'list-line' }, createElement('span', {}, '待分配角色'), createElement('strong', {}, role ? `${role.roleName} / ${role.roleCode}` : '暂无角色')),
      createElement('p', {}, member.bindingStatus === 'BOUND' ? '该成员已绑定账号，可以通过系统切换获得成员上下文。' : '还未绑定账号，普通用户无法进入该系统。'),
    );
  };

  bindButton.addEventListener('click', async () => {
    const member = selectedMember();
    const loginName = loginInput.value.trim();
    if (!member || !loginName) {
      result.textContent = '请选择成员并填写已有账号登录名。';
      return;
    }
    bindButton.disabled = true;
    bindButton.textContent = '绑定中...';
    try {
      const binding = await bindSystemMemberAccount(systemId, member.systemMemberId, { loginName });
      result.textContent = `账号已绑定：binding=${binding.bindingId}，member=${binding.systemMemberId}，状态=${binding.bindingStatus}`;
      reload();
    } catch (error) {
      result.textContent = error instanceof Error ? error.message : '绑定账号失败。';
    } finally {
      bindButton.disabled = false;
      bindButton.textContent = '绑定账号';
    }
  });

  assignButton.addEventListener('click', async () => {
    const member = selectedMember();
    const role = selectedRole();
    if (!member || !role) {
      result.textContent = '请选择成员和角色。';
      return;
    }
    assignButton.disabled = true;
    assignButton.textContent = '分配中...';
    try {
      const assigned = await assignSystemRoleMembers(systemId, role.roleId, [member.systemMemberId]);
      result.textContent = `角色已分配：role=${assigned.roleId}，新增=${assigned.assignedCount}，traceId=${assigned.traceId}`;
      reload();
    } catch (error) {
      result.textContent = error instanceof Error ? error.message : '分配角色失败。';
    } finally {
      assignButton.disabled = false;
      assignButton.textContent = '分配角色';
    }
  });

  updateButton.addEventListener('click', async () => {
    const member = selectedMember();
    if (!member) {
      result.textContent = '请选择成员。';
      return;
    }
    updateButton.disabled = true;
    updateButton.textContent = '更新中...';
    try {
      const updated = await updateSystemMember(systemId, member.systemMemberId, {
        deptId: deptSelect.value || member.deptId,
        memberName: member.memberName,
        employeeNo: member.employeeNo,
        mobile: member.mobile,
        email: member.email,
        status: member.status,
        roleIds: member.roleIds ?? [],
      });
      result.textContent = `成员已更新：${updated.memberName}，部门=${updated.deptId ?? '-'}`;
      reload();
    } catch (error) {
      result.textContent = error instanceof Error ? error.message : '更新成员失败。';
    } finally {
      updateButton.disabled = false;
      updateButton.textContent = '更新部门';
    }
  });

  previewButton.addEventListener('click', async () => {
    const member = selectedMember();
    const role = selectedRole();
    if (!member || !role) {
      result.textContent = '请选择成员和角色。';
      return;
    }
    previewButton.disabled = true;
    previewButton.textContent = '预览中...';
    try {
      const decision = await previewSystemEffectivePermission(systemId, {
        systemMemberId: member.systemMemberId,
        roleIds: Array.from(new Set([...(member.roleIds ?? []), role.roleId])),
        actionCode: 'record.create',
      });
      result.textContent = `有效权限预览：${decision.allowed ? '允许' : '拒绝'}，原因=${decision.disabledReason ?? '-'}`;
    } catch (error) {
      result.textContent = error instanceof Error ? error.message : '预览权限失败。';
    } finally {
      previewButton.disabled = false;
      previewButton.textContent = '预览权限';
    }
  });

  memberSelect.addEventListener('change', refreshReadiness);
  roleSelect.addEventListener('change', refreshReadiness);
  refreshReadiness();

  return createElement(
    'section',
    { className: 'org-member-delivery-panel', dataset: { orgMemberDeliveryR90: 'true', r90MemberCount: String(members.length), r90RoleCount: String(roles.length) } },
    createElement('div', { className: 'module-config-head' }, createElement('strong', {}, '成员交付'), createElement('span', {}, '绑定账号、分配角色并确认可进入系统')),
    createElement('div', { className: 'module-config-grid compact-grid' },
      createElement('label', {}, createElement('span', {}, '成员'), memberSelect),
      createElement('label', {}, createElement('span', {}, '绑定账号'), loginInput),
      createElement('label', {}, createElement('span', {}, '部门'), deptSelect),
      createElement('label', {}, createElement('span', {}, '角色'), roleSelect),
    ),
    readiness,
    createElement('div', { className: 'module-config-actions compact-actions' }, bindButton, assignButton, updateButton, previewButton),
  );
}

function createBindingPill(bindingStatus?: string): HTMLElement {
  const normalized = bindingStatus === 'BOUND' ? 'BOUND' : 'UNBOUND';
  return renderStatusPill(normalized === 'BOUND' ? '已绑定' : '未绑定', normalized === 'BOUND' ? 'success' : 'warning');
}

function formatRoleNames(roleIds: string[], roles: RoleView[]): string {
  if (roleIds.length === 0) {
    return '未分配';
  }
  return roleIds.map((roleId) => {
    const role = roles.find((item) => item.roleId === roleId);
    return role ? role.roleName : roleId;
  }).join(' / ');
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
        createElement(
          'span',
          {
            dataset: {
              roleId: role.roleId,
              roleCode: role.roleCode,
              roleType: role.roleType,
              roleStatus: String(role.status),
              roleBuiltin: String(role.builtin),
            },
          },
          createElement('strong', {}, role.roleName),
          createElement('small', {}, ` ${role.roleCode}`),
        ),
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

function createRolePermissionWorkbench(systemId: string, roles: RoleView[], modules: BackendModule[], result: HTMLElement): HTMLElement {
  const roleSelect = createElement('select', { ariaLabel: '权限角色', dataset: { permissionRoleSelect: 'true' } });
  roles.forEach((role) => {
    const option = createElement('option', { dataset: { permissionRoleId: role.roleId, permissionRoleCode: role.roleCode } }, `${role.roleName} / ${role.roleCode}`);
    option.value = role.roleId;
    roleSelect.append(option);
  });

  const moduleSelect = createElement('select', { ariaLabel: '权限模块', dataset: { permissionModuleSelect: 'true' } });
  modules.forEach((module) => {
    const option = createElement('option', { dataset: { permissionModuleId: module.moduleId, permissionModuleCode: module.moduleCode } }, `${module.name} / ${module.moduleCode}`);
    option.value = module.moduleId;
    moduleSelect.append(option);
  });

  const dataScopeSelect = createElement('select', { ariaLabel: '数据范围', dataset: { permissionDataScopeSelect: 'true' } });
  [
    ['ALL', '全部数据'],
    ['SELF', '本人数据'],
    ['DEPARTMENT', '本部门数据'],
  ].forEach(([value, label]) => {
    const option = createElement('option', {}, label);
    option.value = value;
    dataScopeSelect.append(option);
  });

  const actionCodes = [
    { code: 'record.create', label: '新建' },
    { code: 'record.edit', label: '编辑' },
    { code: 'record.delete', label: '删除' },
    { code: 'record.submitApproval', label: '提交审批' },
  ];
  const actionHost = createElement('div', { className: 'permission-action-grid', dataset: { r91ActionMatrix: 'true' } });
  const fieldHost = createElement('div', { className: 'permission-field-grid', dataset: { r91FieldMatrix: 'true' } }, createElement('p', {}, '请选择模块读取字段。'));
  const previewHost = createElement('div', { className: 'permission-preview-panel', dataset: { permissionPreviewPanel: 'true', r91PermissionImpactPanel: 'true' } }, createElement('p', {}, '保存或预览后显示动作影响、字段掩码、数据范围和审计行。'));
  const auditHost = createElement('div', { className: 'permission-audit-list', dataset: { r91PreviewAuditList: 'true' } }, createElement('p', {}, '暂无预览审计。'));
  const saveButton = createButton('保存权限配置', 'primary', roles.length === 0 || modules.length === 0, '需要先创建角色和模块。');
  const previewButton = createButton('预览全部动作影响', 'secondary', roles.length === 0 || modules.length === 0, '需要先创建角色和模块。');
  const refreshAuditButton = createButton('刷新审计', 'secondary', roles.length === 0 || modules.length === 0, '需要先创建角色和模块。');
  saveButton.dataset.r91SavePermissionButton = 'true';
  previewButton.dataset.r91BatchPreviewButton = 'true';
  refreshAuditButton.dataset.r91RefreshAuditButton = 'true';
  let workbench: HTMLElement | null = null;

  const syncR91Dataset = () => {
    if (!workbench) return;
    workbench.dataset.r91RoleId = roleSelect.value;
    workbench.dataset.r91ModuleId = moduleSelect.value;
  };

  const renderActions = (permissions?: RolePermissionView) => {
    actionHost.replaceChildren(...actionCodes.map((action) => {
      const input = createElement('input', { ariaLabel: action.code, dataset: { r91ActionCode: action.code } });
      input.type = 'checkbox';
      input.value = action.code;
      input.checked = permissions?.actionPermissions?.[action.code] ?? true;
      return createElement('label', { className: 'check-row', dataset: { r91ActionPermissionRow: action.code } }, input, createElement('span', {}, `${action.label} / ${action.code}`));
    }));
  };

  const loadFields = async (permissions?: RolePermissionView) => {
    const fields = moduleSelect.value ? (await listSystemModuleFields(systemId, moduleSelect.value)).records : [];
    if (fields.length === 0) {
      fieldHost.replaceChildren(createElement('p', {}, '当前模块暂无字段。'));
      return;
    }
    fieldHost.replaceChildren(...fields.map((field) => createFieldPermissionRow(field, permissions?.fieldPermissions?.[field.fieldCode] ?? permissions?.fieldPermissions?.[`${moduleSelect.value}.${field.fieldCode}`] ?? 'WRITABLE')));
  };

  const collectActionPermissions = (): Record<string, boolean> => Object.fromEntries(
    Array.from(actionHost.querySelectorAll<HTMLInputElement>('input[type="checkbox"]')).map((input) => [input.value, input.checked]),
  );
  const collectFieldPermissions = (): Record<string, string> => Object.fromEntries(
    Array.from(fieldHost.querySelectorAll<HTMLSelectElement>('select[data-field-code]')).map((select) => [select.dataset.fieldCode ?? '', select.value]).filter(([code]) => code),
  );
  const dataScopeRules = () => [{
    type: dataScopeSelect.value,
    moduleId: moduleSelect.value,
    expression: dataScopeSelect.value === 'SELF'
      ? 'ownerMemberId == currentMember'
      : dataScopeSelect.value === 'DEPARTMENT'
        ? 'departmentId in currentMemberDepartments'
        : 'systemId == currentSystem && tenantId == currentTenant',
  }];

  const refreshAuditRows = async () => {
    syncR91Dataset();
    try {
      const rows = await listSystemPermissionPreviewLogs(systemId, { roleId: roleSelect.value, moduleId: moduleSelect.value, pageSize: 5 });
      auditHost.replaceChildren(createPermissionAuditList(rows));
    } catch (error) {
      auditHost.replaceChildren(createElement('p', { className: 'field-error' }, error instanceof Error ? error.message : '预览审计读取失败。'));
    }
  };

  const runBatchPreview = async () => {
    syncR91Dataset();
    const preview = await previewSystemEffectivePermissions(systemId, {
      roleIds: roleSelect.value ? [roleSelect.value] : [],
      moduleId: moduleSelect.value,
      actionCodes: actionCodes.map((item) => item.code),
    });
    previewHost.replaceChildren(createPermissionImpactSummary(preview));
    auditHost.replaceChildren(createPermissionAuditList(preview.recentAudits ?? []));
    return preview;
  };

  const loadCurrent = async () => {
    syncR91Dataset();
    if (!roleSelect.value) {
      return;
    }
    try {
      const permissions = await loadSystemRolePermissions(systemId, roleSelect.value);
      renderActions(permissions);
      await loadFields(permissions);
      dataScopeSelect.value = String(permissions.dataScopeRules?.[0]?.type ?? 'ALL');
      previewHost.replaceChildren(createPermissionPreviewSummary(null, permissions));
      await refreshAuditRows();
    } catch (error) {
      renderActions();
      await loadFields();
      previewHost.replaceChildren(createElement('p', { className: 'field-error' }, error instanceof Error ? error.message : '权限配置读取失败。'));
    }
  };

  saveButton.addEventListener('click', async () => {
    saveButton.disabled = true;
    saveButton.textContent = '保存中...';
    try {
      const saved = await saveSystemRolePermissions(systemId, roleSelect.value, {
        actionPermissions: collectActionPermissions(),
        fieldPermissions: collectFieldPermissions(),
        dataScopeRules: dataScopeRules(),
      });
      result.textContent = `角色权限已保存：version=${saved.permissionVersion}`;
      previewHost.replaceChildren(createPermissionPreviewSummary(null, saved));
      await refreshAuditRows();
    } catch (error) {
      result.textContent = error instanceof Error ? error.message : '保存角色权限失败。';
    } finally {
      saveButton.disabled = false;
      saveButton.textContent = '保存权限配置';
    }
  });

  previewButton.addEventListener('click', async () => {
    previewButton.disabled = true;
    previewButton.textContent = '预览中...';
    try {
      const preview = await runBatchPreview();
      result.textContent = `权限影响预览完成：version=${preview.permissionVersion}，影响成员=${preview.affectedMemberCount}`;
    } catch (error) {
      previewHost.replaceChildren(createElement('p', { className: 'field-error' }, error instanceof Error ? error.message : '有效权限预览失败。'));
    } finally {
      previewButton.disabled = false;
      previewButton.textContent = '预览全部动作影响';
    }
  });

  refreshAuditButton.addEventListener('click', () => void refreshAuditRows());
  roleSelect.addEventListener('change', () => void loadCurrent());
  moduleSelect.addEventListener('change', () => void loadCurrent());
  renderActions();

  workbench = createElement(
    'section',
    {
      className: 'role-permission-workbench',
      dataset: {
        rolePermissionWorkbench: 'true',
        permissionImpactWorkbenchR91: 'true',
        rolePermissionRoleIds: roles.map((role) => role.roleId).join(','),
        rolePermissionModuleIds: modules.map((module) => module.moduleId).join(','),
        r91RoleId: roleSelect.value,
        r91ModuleId: moduleSelect.value,
      },
    },
    createElement('div', { className: 'module-config-head' }, createElement('strong', {}, '角色权限配置'), createElement('span', {}, '动作、字段、数据范围写入真实权限接口，并支持批量影响预览。')),
    createElement('div', { className: 'module-config-grid' },
      createElement('label', {}, createElement('span', {}, '角色'), roleSelect),
      createElement('label', {}, createElement('span', {}, '模块'), moduleSelect),
      createElement('label', {}, createElement('span', {}, '数据范围'), dataScopeSelect),
    ),
    createElement('div', { className: 'module-config-two-column role-permission-matrix-r91' },
      createElement('section', {}, createElement('strong', {}, '动作权限'), actionHost),
      createElement('section', {}, createElement('strong', {}, '字段权限'), fieldHost),
    ),
    createElement('div', { className: 'module-config-actions' }, saveButton, previewButton, refreshAuditButton),
    previewHost,
    auditHost,
  );
  void loadCurrent();
  return workbench;
}

function createFieldPermissionRow(field: FieldDefinitionVO, mode: string): HTMLElement {
  const select = createElement('select', {
    ariaLabel: `${field.name} 字段权限`,
    dataset: {
      fieldCode: field.fieldCode,
      fieldId: field.fieldId,
      fieldPermissionMode: mode,
      r91FieldPermissionMode: mode,
    },
  });
  [
    ['WRITABLE', '可读写'],
    ['READABLE', '只读'],
    ['MASKED', '脱敏'],
    ['HIDDEN', '隐藏'],
  ].forEach(([value, label]) => {
    const option = createElement('option', {}, label);
    option.value = value;
    option.selected = value === mode;
    select.append(option);
  });
  select.addEventListener('change', () => {
    select.dataset.fieldPermissionMode = select.value;
    select.dataset.r91FieldPermissionMode = select.value;
  });
  return createElement('label', { className: 'permission-field-row', dataset: { r91FieldPermissionRow: field.fieldCode } },
    createElement('span', {}, `${field.name} / ${field.fieldCode}`),
    select,
  );
}

function createPermissionImpactSummary(preview: PermissionBatchPreviewView): HTMLElement {
  const dataScope = preview.dataScopeExpression ? JSON.stringify(preview.dataScopeExpression) : '-';
  const masked = Object.entries(preview.fieldMaskRules ?? {}).map(([field, mode]) => `${field}:${mode}`).join(' / ') || '-';
  const explain = (preview.explain ?? []).map((item) => `${item.type ?? 'RULE'}:${item.target ?? item.policyCode ?? '-'}`).join(' / ') || '-';
  return createElement('div', { className: 'permission-impact-summary', dataset: { r91PermissionImpactSummary: 'true', r91AffectedMembers: String(preview.affectedMemberCount) } },
    createElement('div', { className: 'permission-impact-stats' },
      createElement('span', { dataset: { r91PermissionVersion: preview.permissionVersion } }, `权限版本：${preview.permissionVersion}`),
      createElement('span', { dataset: { r91AffectedMembers: String(preview.affectedMemberCount) } }, `影响成员：${preview.affectedMemberCount}`),
      createElement('span', { dataset: { r91PreviewTraceId: preview.traceId ?? '' } }, `traceId：${preview.traceId ?? '-'}`),
    ),
    createElement('div', { className: 'permission-decision-list' },
      ...preview.decisions.map((decision) => createElement('div', {
        className: `permission-decision-row ${decision.allowed ? 'is-allowed' : 'is-denied'}`,
        dataset: { r91PermissionDecision: 'true', r91ActionCode: decision.actionCode, r91Allowed: String(decision.allowed) },
      },
        createElement('strong', {}, decision.actionCode),
        createElement('span', {}, decision.allowed ? '允许' : '拒绝'),
        createElement('small', {}, decision.disabledReason || decision.auditLogId || '满足当前角色权限。'),
      )),
    ),
    createElement('p', { dataset: { r91DataScopeSummary: dataScope } }, `数据范围：${dataScope}`),
    createElement('p', { dataset: { r91FieldMaskSummary: masked } }, `字段掩码：${masked}`),
    createElement('p', {}, `解释：${explain}`),
    createTraceLine(preview.traceId ?? '-', preview.permissionVersion),
  );
}

function createPermissionAuditList(rows: PermissionPreviewAuditView[]): HTMLElement {
  if (rows.length === 0) {
    return createElement('div', { className: 'permission-audit-list', dataset: { r91PreviewAuditList: 'true' } }, createElement('p', {}, '暂无预览审计。'));
  }
  return createElement('div', { className: 'permission-audit-list', dataset: { r91PreviewAuditList: 'true', r91PreviewAuditCount: String(rows.length) } },
    createElement('strong', {}, '最近预览审计'),
    ...rows.map((row) => createElement('div', {
      className: `permission-audit-row ${row.allowed ? 'is-allowed' : 'is-denied'}`,
      dataset: { r91PreviewAuditRow: 'true', r91ActionCode: row.actionCode, r91Allowed: String(row.allowed), r91PreviewLogId: row.previewLogId },
    },
      createElement('span', {}, row.actionCode),
      createElement('span', {}, row.allowed ? '允许' : '拒绝'),
      createElement('small', {}, row.disabledReason || row.traceId || row.previewLogId),
    )),
  );
}

function createPermissionPreviewSummary(decision?: PermissionDecisionVO | null, permissions?: RolePermissionView): HTMLElement {
  if (decision) {
    const dataScope = decision.dataScopeExpression ? JSON.stringify(decision.dataScopeExpression) : '-';
    const masked = Object.entries(decision.fieldMaskRules ?? {}).map(([field, mode]) => `${field}:${mode}`).join(' / ') || '-';
    const explain = (decision.explain ?? []).map((item) => `${item.type}:${item.target}`).join(' / ') || '-';
    return createElement('div', {},
      createElement('strong', {}, decision.allowed ? '预览结果：允许' : '预览结果：拒绝'),
      createElement('p', {}, decision.disabledReason || '当前角色满足所选动作权限。'),
      createElement('p', {}, `数据范围：${dataScope}`),
      createElement('p', {}, `字段规则：${masked}`),
      createElement('p', {}, `解释：${explain}`),
      createTraceLine(decision.traceId ?? '-', decision.auditLogId ?? decision.permissionVersion),
    );
  }
  if (permissions) {
    return createElement('div', {},
      createElement('strong', {}, `已读取权限版本：${permissions.permissionVersion}`),
      createElement('p', {}, `动作：${Object.entries(permissions.actionPermissions ?? {}).map(([code, allow]) => `${code}:${allow ? '允许' : '拒绝'}`).join(' / ') || '-'}`),
      createElement('p', {}, `字段：${Object.entries(permissions.fieldPermissions ?? {}).map(([code, mode]) => `${code}:${mode}`).join(' / ') || '-'}`),
    );
  }
  return createElement('p', {}, '暂无权限预览。');
}
type ModuleFilterState = {
  keyword: string;
  groupId: string;
  publishStatus: string;
  status: string;
};

type ModuleWorkspaceTab = 'lifecycle' | 'fields' | 'scene' | 'page' | 'print';

const FRC2C_FIELD_TYPES = [
  { code: 'TEXT', name: '文本', hint: '适合名称、编号、摘要等短文本' },
  { code: 'LONG_TEXT', name: '长文本', hint: '适合多行说明、备注和处理意见' },
  { code: 'NUMBER', name: '数字', hint: '适合金额、数量、评分等数值字段' },
  { code: 'DATE', name: '日期', hint: '适合计划日期、发生日期' },
  { code: 'DATETIME', name: '日期时间', hint: '适合包含时分秒的时间点' },
  { code: 'SELECT', name: '单选', hint: '从数据字典中选择一个选项' },
  { code: 'MULTI_SELECT', name: '多选', hint: '从数据字典中选择多个选项' },
  { code: 'USER', name: '成员', hint: '选择当前系统成员' },
  { code: 'DEPARTMENT', name: '部门', hint: '选择当前系统部门' },
  { code: 'ATTACHMENT', name: '附件', hint: '上传文件并随记录保存' },
  { code: 'IMAGE', name: '图片', hint: '上传图片并支持预览' },
  { code: 'RELATION', name: '关联记录', hint: '关联另一个模块中的业务记录' },
  { code: 'CHILD_TABLE', name: '子表', hint: '一条主记录下维护多行明细' },
  { code: 'AUTO_NUMBER', name: '自动编号', hint: '按模块规则生成连续业务编号' },
];

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
  dictTypes: DictTypeView[],
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
    workspaceHost.replaceChildren(createModuleWorkspace(systemId, selectedModule, groups, dictTypes, reload, result));
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
    { className: 'mini-tree', dataset: { moduleGroupTree: 'true', moduleGroupHierarchySupported: 'false' } },
    createElement('strong', {}, '模块分组'),
    groups.length === 0
      ? createElement('p', {}, '暂无模块分组')
      : createElement(
          'div',
          { className: 'simple-stack' },
          ...groups.map((group, index) => createTreeButton(group.name, index === 0, {
            moduleGroupId: group.groupId,
            moduleGroupPublishStatus: group.publishStatus ?? '',
            moduleGroupVisibleRoleIds: (group.visibleRoleIds ?? []).join(','),
            moduleGroupHierarchySupported: 'false',
          })),
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
    { className: 'module-list-panel admin-table-panel', dataset: { adminModuleTable: 'true' } },
    createModuleAdminTable(modules, selectedModuleId, onSelect),
    createElement('strong', {}, '模块列表'),
    ...modules.map((module) => {
      const button = createButton(`${module.name}`, module.moduleId === selectedModuleId ? 'primary' : 'ghost', false);
      button.classList.add('module-list-item');
      button.addEventListener('click', () => onSelect(module.moduleId));
      return createElement(
        'article',
        {
          className: module.moduleId === selectedModuleId ? 'module-list-card active' : 'module-list-card',
          dataset: {
            moduleListCardId: module.moduleId,
            moduleListCardGroupId: module.groupId,
            moduleListCardCode: module.moduleCode,
            moduleListCardStatus: String(module.status),
            moduleListCardPublishStatus: module.publishStatus ?? '',
            moduleListCardRuntimeVisible: String(module.navigation?.runtimeVisible === true),
            moduleListCardVisibleRoleIds: (module.navigation?.visibleRoleIds ?? []).join(','),
          },
        },
        createElement('div', {}, button, createElement('small', {}, module.moduleCode)),
        createElement('div', { className: 'module-list-meta' }, renderStatusPill(enableStatusText(Number(module.status)), enableStatusTone(Number(module.status))), createElement('span', {}, module.publishStatus || 'DRAFT')),
      );
    }),
  );
}

function createModuleAdminTable(modules: BackendModule[], selectedModuleId: string, onSelect: (moduleId: string) => void): HTMLElement {
  const rows = modules.map((module) => {
    const selectButton = createButton(module.moduleId === selectedModuleId ? '当前模块' : '选择', module.moduleId === selectedModuleId ? 'primary' : 'ghost', false);
    selectButton.addEventListener('click', (event) => {
      event.stopPropagation();
      onSelect(module.moduleId);
    });
    const row = createElement(
      'tr',
      {
        className: module.moduleId === selectedModuleId ? 'selected-row' : '',
        dataset: {
          moduleAdminRow: module.moduleId,
          moduleAdminGroupId: module.groupId,
          moduleAdminCode: module.moduleCode,
          moduleAdminStatus: String(module.status),
          moduleAdminPublishStatus: module.publishStatus ?? '',
          moduleAdminRuntimeVisible: String(module.navigation?.runtimeVisible === true),
          moduleAdminVisibleRoleIds: (module.navigation?.visibleRoleIds ?? []).join(','),
        },
      },
      createElement(
        'td',
        {},
        createElement('strong', {}, module.name),
        createElement('small', {}, module.moduleCode),
      ),
      createElement('td', {}, renderStatusPill(enableStatusText(Number(module.status)), enableStatusTone(Number(module.status)))),
      createElement('td', {}, module.publishStatus || 'DRAFT'),
      createElement('td', {}, module.currentVersion || '-'),
      createElement('td', {}, selectButton),
    );
    row.addEventListener('click', () => onSelect(module.moduleId));
    return row;
  });
  return createElement(
    'div',
    { className: 'table-shell module-admin-table-shell', dataset: { moduleAdminTableShell: 'true' } },
    createElement(
      'table',
      {},
      createElement(
        'thead',
        {},
        createElement(
          'tr',
          {},
          createElement('th', {}, '模块'),
          createElement('th', {}, '状态'),
          createElement('th', {}, '发布'),
          createElement('th', {}, '版本'),
          createElement('th', {}, '操作'),
        ),
      ),
      createElement('tbody', {}, ...rows),
    ),
  );
}

function createModuleWorkspace(systemId: string, module: BackendModule | undefined, groups: BackendModuleGroup[], dictTypes: DictTypeView[], reload: () => void, result: HTMLElement): HTMLElement {
  if (!module) {
    return createElement('section', { className: 'result-panel' }, createElement('strong', {}, '未选择模块'), createElement('p', {}, '请选择或新建一个模块。'));
  }
  let activeTab: ModuleWorkspaceTab = 'lifecycle';
  const tabHost = createElement('div', { className: 'module-work-tab-host', dataset: { moduleWorkTabHost: module.moduleId } });
  const actionButton = createButton('新增动作', 'secondary', false);
  const checkButton = createButton('发布检查', 'secondary', false);
  const publishButton = createButton('发布', 'primary', false);
  const rollbackButton = createButton('回滚', 'ghost', module.publishStatus !== 'PUBLISHED', module.publishStatus === 'PUBLISHED' ? undefined : '只有已发布模块可以回滚。');
  const tabs: Array<{ id: ModuleWorkspaceTab; label: string; description: string }> = [
    { id: 'lifecycle', label: '生命周期', description: '名称、编码、分组、启停和发布状态' },
    { id: 'fields', label: '字段', description: '字段类型、必填、字典、权限基础' },
    { id: 'scene', label: '列表/动作/导入导出', description: '运行态列表、筛选排序、行操作和数据进出' },
    { id: 'page', label: '页面', description: '运行态页面组件、移动端顺序和发布' },
    { id: 'print', label: '打印', description: '打印模板、字段绑定、预览和发布' },
  ];
  const renderActiveTab = () => {
    const active = tabs.find((tab) => tab.id === activeTab) ?? tabs[0];
    const tabButtons = tabs.map((tab) => {
      const button = createButton(tab.label, tab.id === activeTab ? 'primary' : 'ghost', false);
      button.dataset.moduleWorkTab = tab.id;
      button.setAttribute('aria-label', `模块配置任务：${tab.label}`);
      button.addEventListener('click', () => {
        activeTab = tab.id;
        renderActiveTab();
      });
      return button;
    });
    const activePanel = (() => {
      if (activeTab === 'fields') {
        return createFieldBuilder(systemId, module, dictTypes, result);
      }
      if (activeTab === 'scene') {
        return createElement(
          'div',
          { className: 'module-work-task-stack' },
          createSceneAndImportExportPanel(systemId, module, result),
          createElement(
            'div',
            { className: 'module-secondary-actions' },
            actionButton,
            createElement('span', {}, '字段保存后可继续配置行操作、发布检查和发布。'),
          ),
        );
      }
      if (activeTab === 'page') {
        return createModulePageDesigner(systemId, module, reload);
      }
      if (activeTab === 'print') {
        return createPrintTemplateDesigner(systemId, module, result);
      }
      return createModuleLifecyclePanel(systemId, module, groups, reload, result);
    })();
    tabHost.replaceChildren(
      createElement(
        'div',
        { className: 'module-work-tabs', dataset: { moduleWorkTabs: 'true' } },
        ...tabButtons,
      ),
      createElement(
        'section',
        { className: 'module-work-active-task', dataset: { moduleWorkActiveTab: active.id } },
        createElement(
          'div',
          { className: 'module-active-task-head' },
          createElement('strong', {}, `当前任务：${active.label}`),
          createElement('span', {}, active.description),
        ),
        activePanel,
      ),
    );
  };
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
  renderActiveTab();
  return createElement(
    'section',
    {
      className: 'module-work-panel',
      dataset: {
        moduleWorkPanelId: module.moduleId,
        moduleWorkPanelGroupId: module.groupId,
        moduleWorkPanelCode: module.moduleCode,
        moduleWorkPanelStatus: String(module.status),
        moduleWorkPanelPublishStatus: module.publishStatus ?? '',
        moduleWorkPanelRuntimeVisible: String(module.navigation?.runtimeVisible === true),
        moduleWorkPanelVisibleRoleIds: (module.navigation?.visibleRoleIds ?? []).join(','),
      },
    },
    createElement(
      'div',
      { className: 'module-work-head' },
      createElement('div', {}, createElement('h3', {}, module.name), createElement('p', {}, `${module.moduleCode} / ${module.currentVersion || '未发布版本'}`)),
      createElement('div', { className: 'module-work-actions' }, checkButton, publishButton, rollbackButton),
    ),
    createElement('div', { className: 'module-work-meta' }, renderStatusPill(enableStatusText(Number(module.status)), enableStatusTone(Number(module.status))), createElement('span', {}, `发布状态：${module.publishStatus || 'DRAFT'}`), createElement('span', {}, `分组：${module.groupId}`)),
    tabHost,
  );
}

function createModuleLifecyclePanel(systemId: string, module: BackendModule, groups: BackendModuleGroup[], reload: () => void, result: HTMLElement): HTMLElement {
  const nameInput = createElement('input', { ariaLabel: '模块名称' });
  nameInput.value = module.name;
  const codeInput = createElement('input', { ariaLabel: '模块编码' });
  codeInput.value = module.moduleCode;
  const groupSelect = createElement('select', { ariaLabel: '模块分组' });
  groups.forEach((group) => {
    const option = createElement('option', {}, group.name);
    option.value = group.groupId;
    option.selected = group.groupId === module.groupId;
    groupSelect.append(option);
  });
  const statusSelect = createElement('select', { ariaLabel: '启用状态' });
  [
    ['1', '启用'],
    ['0', '停用'],
  ].forEach(([value, label]) => {
    const option = createElement('option', {}, label);
    option.value = value;
    option.selected = String(module.status) === value;
    statusSelect.append(option);
  });
  const saveButton = createButton('保存模块基础信息', 'primary', false);
  saveButton.addEventListener('click', async () => {
    const name = nameInput.value.trim();
    const moduleCode = codeInput.value.trim();
    if (!name || !moduleCode) {
      result.textContent = '请填写模块名称和模块编码。';
      return;
    }
    saveButton.disabled = true;
    saveButton.textContent = '保存中...';
    try {
      const saved = await updateSystemModule(systemId, module.moduleId, {
        groupId: groupSelect.value,
        moduleCode,
        name,
        status: Number(statusSelect.value),
      });
      result.textContent = `模块基础信息已保存：${saved.name} / ${saved.moduleCode}，状态=${saved.status}。`;
      reload();
    } catch (error) {
      result.textContent = error instanceof Error ? error.message : '模块基础信息保存失败。';
    } finally {
      saveButton.disabled = false;
      saveButton.textContent = '保存模块基础信息';
    }
  });
  return createElement(
    'section',
    { className: 'module-lifecycle-panel', dataset: { moduleLifecyclePanel: module.moduleId } },
    createElement('div', { className: 'module-config-head' }, createElement('strong', {}, '模块生命周期'), createElement('span', {}, `当前发布状态：${module.publishStatus || 'DRAFT'}`)),
    createElement(
      'div',
      { className: 'module-config-grid' },
      createElement('label', {}, createElement('span', {}, '模块名称'), nameInput),
      createElement('label', {}, createElement('span', {}, '模块编码'), codeInput),
      createElement('label', {}, createElement('span', {}, '模块分组'), groupSelect),
      createElement('label', {}, createElement('span', {}, '启用状态'), statusSelect),
    ),
    createElement('div', { className: 'module-config-actions' }, saveButton),
  );
}

function createSceneAndImportExportPanel(systemId: string, module: BackendModule, result: HTMLElement): HTMLElement {
  const host = createElement('section', { className: 'scene-config-panel', dataset: { moduleSceneConfig: module.moduleId } }, createLoadingPanel('正在读取列表场景和导入导出配置...'));
  void Promise.all([
    listSystemModuleFields(systemId, module.moduleId),
    listSystemModuleScenes(systemId, module.moduleId),
    listSystemModuleActions(systemId, module.moduleId),
    loadSystemModuleImportExportConfig(systemId, module.moduleId).catch((): ModuleImportExportConfigView => ({
      moduleId: module.moduleId,
      importSupported: false,
      exportSupported: false,
      importTemplates: [],
      exportTemplates: [],
      fieldMappings: [],
      duplicateStrategies: [],
      supportedFormats: [],
    })),
  ])
    .then(([fieldsPage, scenes, actions, importExportConfig]) => {
      host.replaceChildren(createSceneImportExportBody(systemId, module, fieldsPage.records, scenes, actions, importExportConfig, result));
    })
    .catch((error) => {
      host.replaceChildren(createElement('p', { className: 'field-error' }, error instanceof Error ? error.message : '列表场景配置读取失败。'));
    });
  return host;
}

function createSceneImportExportBody(
  systemId: string,
  module: BackendModule,
  fields: FieldDefinitionVO[],
  scenes: ModuleListSceneView[],
  actions: ModuleActionConfigView[],
  importExportConfig: ModuleImportExportConfigView,
  result: HTMLElement,
): HTMLElement {
  const sceneCodeInput = createElement('input', { ariaLabel: '场景编码' });
  sceneCodeInput.value = `admin_scene_${Date.now()}`;
  const sceneNameInput = createElement('input', { ariaLabel: '场景名称' });
  sceneNameInput.value = `${module.name} 管理视图`;
  const defaultSceneInput = createElement('input', { ariaLabel: '默认场景' });
  defaultSceneInput.type = 'checkbox';
  defaultSceneInput.checked = scenes.length === 0;
  const schemaPreview = createElement('div', { className: 'module-config-preview' }, createSceneSummary(scenes, actions));
  const fieldPicker = createSceneFieldPicker(fields);
  const saveSceneButton = createButton('保存列表场景', 'primary', fields.length === 0, fields.length === 0 ? '请先保存字段，再配置列表场景。' : undefined);
  saveSceneButton.addEventListener('click', async () => {
    const sceneCode = sceneCodeInput.value.trim() || `admin_scene_${Date.now()}`;
    const sceneName = sceneNameInput.value.trim();
    if (!sceneName) {
      result.textContent = '请填写场景名称。';
      sceneNameInput.focus();
      return;
    }
    saveSceneButton.disabled = true;
    saveSceneButton.textContent = '保存中...';
    try {
      const savedScene = await saveSystemModuleScene(systemId, module.moduleId, {
        sceneCode,
        sceneName,
        defaultScene: defaultSceneInput.checked,
        columnFieldIds: selectedPickerValues(fieldPicker, 'column'),
        filterFieldIds: selectedPickerValues(fieldPicker, 'filter'),
        sortFieldIds: selectedPickerValues(fieldPicker, 'sort'),
      });
      const schema = await loadSystemModuleListSchema(systemId, module.moduleId, savedScene.sceneId);
      schemaPreview.replaceChildren(createListSchemaSummary(savedScene, schema));
      result.textContent = `列表场景已保存：${savedScene.sceneName}，列=${schema.columns.length}，筛选=${schema.filters.length}，排序=${schema.sorters.length}。`;
    } catch (error) {
      result.textContent = error instanceof Error ? error.message : '列表场景保存失败。';
    } finally {
      saveSceneButton.disabled = false;
      saveSceneButton.textContent = '保存列表场景';
    }
  });

  const importSupportedInput = createElement('input', { ariaLabel: '支持导入' });
  importSupportedInput.type = 'checkbox';
  importSupportedInput.checked = importExportConfig.importSupported;
  const exportSupportedInput = createElement('input', { ariaLabel: '支持导出' });
  exportSupportedInput.type = 'checkbox';
  exportSupportedInput.checked = importExportConfig.exportSupported;
  const importExportPreview = createElement('div', { className: 'module-config-preview' }, createImportExportSummary(importExportConfig));
  const saveImportExportButton = createButton('保存导入导出配置', 'secondary', fields.length === 0, fields.length === 0 ? '请先保存字段，再配置导入导出。' : undefined);
  saveImportExportButton.addEventListener('click', async () => {
    saveImportExportButton.disabled = true;
    saveImportExportButton.textContent = '保存中...';
    const selectedFields = fields.filter((field) => fieldPicker.querySelector<HTMLInputElement>(`input[data-picker-kind="column"][value="${field.fieldId}"]`)?.checked);
    const defaultCodes = selectedFields.map((field) => field.fieldCode);
    try {
      const savedConfig = await saveSystemModuleImportExportConfig(systemId, module.moduleId, {
        importSupported: importSupportedInput.checked,
        exportSupported: exportSupportedInput.checked,
        importTemplates: importSupportedInput.checked ? [{
          templateCode: `${module.moduleCode}_import_default`,
          templateName: `${module.name} 导入模板`,
          fileFormat: 'XLSX',
          defaultFieldCodes: defaultCodes,
          requiredFieldCodes: selectedFields.filter((field) => field.required).map((field) => field.fieldCode),
          desensitizeMode: 'PERMISSION',
        }] : [],
        exportTemplates: exportSupportedInput.checked ? [{
          templateCode: `${module.moduleCode}_export_default`,
          templateName: `${module.name} 导出模板`,
          fileFormat: 'XLSX',
          defaultFieldCodes: defaultCodes,
          desensitizeMode: 'PERMISSION',
        }] : [],
        fieldMappings: selectedFields.map((field) => ({
          sourceColumn: field.name,
          fieldCode: field.fieldCode,
          required: field.required,
          transformRule: 'DIRECT',
        })),
        duplicateStrategies: ['SKIP_DUPLICATE', 'UPDATE_EXISTING'],
        supportedFormats: ['XLSX', 'CSV'],
      });
      importExportPreview.replaceChildren(createImportExportSummary(savedConfig));
      result.textContent = `导入导出配置已保存：导入=${savedConfig.importSupported ? '开启' : '关闭'}，导出=${savedConfig.exportSupported ? '开启' : '关闭'}，映射=${savedConfig.fieldMappings.length}。`;
    } catch (error) {
      result.textContent = error instanceof Error ? error.message : '导入导出配置保存失败。';
    } finally {
      saveImportExportButton.disabled = false;
      saveImportExportButton.textContent = '保存导入导出配置';
    }
  });

  return createElement(
    'div',
    { className: 'module-config-two-column' },
    createElement(
      'section',
      { className: 'module-list-scene-card' },
      createElement('div', { className: 'module-config-head' }, createElement('strong', {}, '列表场景与后台表格'), createElement('span', {}, `${scenes.length} 个场景 / ${actions.length} 个动作`)),
      createElement('div', { className: 'module-config-grid' }, createElement('label', {}, createElement('span', {}, '场景编码'), sceneCodeInput), createElement('label', {}, createElement('span', {}, '场景名称'), sceneNameInput)),
      createElement('label', { className: 'check-row' }, defaultSceneInput, createElement('span', {}, '设为默认场景')),
      fieldPicker,
      createElement('div', { className: 'module-config-actions' }, saveSceneButton),
      schemaPreview,
    ),
    createElement(
      'section',
      { className: 'module-import-export-card', dataset: { moduleImportExport: module.moduleId } },
      createElement('div', { className: 'module-config-head' }, createElement('strong', {}, '导入导出'), createElement('span', {}, `${importExportConfig.supportedFormats.join(', ') || '未配置格式'}`)),
      createElement('label', { className: 'check-row' }, importSupportedInput, createElement('span', {}, '开启导入并生成模板映射')),
      createElement('label', { className: 'check-row' }, exportSupportedInput, createElement('span', {}, '开启导出并生成权限脱敏模板')),
      createElement('div', { className: 'module-config-actions' }, saveImportExportButton),
      importExportPreview,
    ),
  );
}

function createSceneFieldPicker(fields: FieldDefinitionVO[]): HTMLElement {
  if (fields.length === 0) {
    return createElement('div', { className: 'scene-field-picker empty' }, createElement('p', {}, '请先保存字段，再配置列、筛选和排序。'));
  }
  return createElement(
    'div',
    { className: 'scene-field-picker' },
    ...fields.map((field, index) => {
      const columnInput = createElement('input', { ariaLabel: `${field.name} 列` });
      columnInput.type = 'checkbox';
      columnInput.value = field.fieldId;
      columnInput.dataset.pickerKind = 'column';
      columnInput.checked = index < 5;
      const filterInput = createElement('input', { ariaLabel: `${field.name} 筛选` });
      filterInput.type = 'checkbox';
      filterInput.value = field.fieldId;
      filterInput.dataset.pickerKind = 'filter';
      filterInput.checked = index < 3;
      const sortInput = createElement('input', { ariaLabel: `${field.name} 排序` });
      sortInput.type = 'checkbox';
      sortInput.value = field.fieldId;
      sortInput.dataset.pickerKind = 'sort';
      sortInput.checked = index === 0 && field.sortable;
      sortInput.disabled = !field.sortable;
      return createElement(
        'div',
        { className: 'scene-field-row' },
        createElement('span', {}, `${field.name} / ${field.fieldCode}`),
        createElement('label', { className: 'mini-check' }, columnInput, createElement('span', {}, '列')),
        createElement('label', { className: 'mini-check' }, filterInput, createElement('span', {}, '筛选')),
        createElement('label', { className: 'mini-check' }, sortInput, createElement('span', {}, '排序')),
      );
    }),
  );
}

function selectedPickerValues(host: HTMLElement, kind: string): string[] {
  return Array.from(host.querySelectorAll<HTMLInputElement>(`input[data-picker-kind="${kind}"]`))
    .filter((input) => input.checked && !input.disabled)
    .map((input) => input.value);
}

function createSceneSummary(scenes: ModuleListSceneView[], actions: ModuleActionConfigView[]): HTMLElement {
  return createElement(
    'div',
    {},
    createElement('strong', {}, '当前配置'),
    createElement('p', {}, scenes.length === 0 ? '暂无列表场景。' : scenes.map((scene) => `${scene.sceneName}${scene.defaultScene ? '(默认)' : ''}`).join(' / ')),
    createElement('p', {}, actions.length === 0 ? '暂无后台/运行态动作。' : actions.map((action) => `${action.actionName}:${action.position}`).join(' / ')),
  );
}

function createListSchemaSummary(scene: ModuleListSceneView, schema: AdminDynamicListSchema): HTMLElement {
  return createElement(
    'div',
    {},
    createElement('strong', {}, `已生成 schema：${scene.sceneName}`),
    createElement('p', {}, `列：${schema.columns.map((column) => column.label).join(' / ') || '-'}`),
    createElement('p', {}, `筛选：${schema.filters.map((filter) => filter.label).join(' / ') || '-'}`),
    createElement('p', {}, `排序：${schema.sorters.map((sorter) => sorter.label).join(' / ') || '-'}`),
  );
}

function createImportExportSummary(config: ModuleImportExportConfigView): HTMLElement {
  return createElement(
    'div',
    {},
    createElement('strong', {}, '当前导入导出'),
    createElement('p', {}, `导入：${config.importSupported ? '开启' : '关闭'} / 导出：${config.exportSupported ? '开启' : '关闭'}`),
    createElement('p', {}, `模板：导入 ${config.importTemplates.length} 个，导出 ${config.exportTemplates.length} 个`),
    createElement('p', {}, `字段映射：${config.fieldMappings.length} 个 / 格式：${config.supportedFormats.join(', ') || '-'}`),
  );
}

function createFieldBuilder(systemId: string, module: BackendModule, dictTypes: DictTypeView[], result: HTMLElement): HTMLElement {
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
  let selectedType = FRC2C_FIELD_TYPES[0].code;
  const fieldTypes = FRC2C_FIELD_TYPES.concat(
    MODULE_FIELD_TYPES.filter((legacy) => !FRC2C_FIELD_TYPES.some((type) => type.code === legacy.code)),
  );
  const typeButtons = fieldTypes.map((type) => {
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
  const dictSelect = createElement('select', { ariaLabel: '绑定字典' });
  dictSelect.append(createElement('option', {}, '不绑定字典'));
  dictTypes.forEach((dict) => {
    const option = createElement('option', {}, `${dict.dictName} / ${dict.dictCode}`);
    option.value = dict.dictTypeId;
    dictSelect.append(option);
  });
  const defaultValueInput = createElement('input', { ariaLabel: '字段默认值' });
  defaultValueInput.placeholder = '可选：新建记录时自动带入';
  const validationInput = createElement('textarea', { ariaLabel: '字段校验规则 JSON' });
  validationInput.placeholder = '{"maxLength":120}';
  const typeConfigInput = createElement('textarea', { ariaLabel: '字段类型配置 JSON' });
  typeConfigInput.placeholder = '{"precision":2}';
  const permissionSelect = createElement('select', { ariaLabel: '默认字段权限' });
  [
    ['WRITABLE', '默认可读写'],
    ['READABLE', '默认只读'],
    ['MASKED', '默认脱敏'],
    ['HIDDEN', '默认隐藏'],
  ].forEach(([value, label]) => {
    const option = createElement('option', {}, label);
    option.value = value;
    permissionSelect.append(option);
  });
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
        dictTypeId: dictSelect.value || undefined,
        defaultValue: defaultValueInput.value.trim() || undefined,
        validationRules: parseJsonObjectInput(validationInput.value, '字段校验规则 JSON', result),
        typeConfig: parseJsonObjectInput(typeConfigInput.value, '字段类型配置 JSON', result),
        required: requiredInput.checked,
        sortable: sortableInput.checked,
        permissionMode: permissionSelect.value as 'WRITABLE' | 'READABLE' | 'MASKED' | 'HIDDEN',
      });
      result.textContent = `字段已保存：${field.name} / ${field.fieldCode} / ${field.fieldType}。`;
      fieldNameInput.value = '';
      fieldCodeInput.value = '';
      defaultValueInput.value = '';
      validationInput.value = '';
      typeConfigInput.value = '';
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
      createElement('label', {}, createElement('span', {}, '默认值'), defaultValueInput),
      createElement('label', {}, createElement('span', {}, '校验规则 JSON'), validationInput),
      createElement('label', {}, createElement('span', {}, '类型配置 JSON'), typeConfigInput),
    ),
    createElement(
      'section',
      { className: 'field-builder-props' },
      createElement('strong', {}, '字段属性'),
      createElement('label', { className: 'check-row' }, requiredInput, createElement('span', {}, '必填')),
      createElement('label', { className: 'check-row' }, sortableInput, createElement('span', {}, '可排序')),
      createElement('label', {}, createElement('span', {}, '绑定字典'), dictSelect),
      createElement('label', {}, createElement('span', {}, '默认权限'), permissionSelect),
      saveButton,
      createElement('p', {}, '字段类型、字典和默认权限会影响运行态表单、筛选和权限预览。'),
    ),
  );
}

function createPrintTemplateDesigner(systemId: string, module: BackendModule, result: HTMLElement): HTMLElement {
  const host = createElement('section', { className: 'print-designer', dataset: { printDesigner: 'true' } }, createLoadingPanel('正在读取打印模板配置...'));
  void Promise.all([listSystemModuleFields(systemId, module.moduleId), listPrintTemplates(systemId, module.moduleId)])
    .then(([fieldsPage, templates]) => {
      host.replaceChildren(createPrintDesignerBody(systemId, module, fieldsPage.records, templates, result));
    })
    .catch((error) => {
      host.replaceChildren(createElement('p', { className: 'field-error' }, error instanceof Error ? error.message : '打印模板读取失败。'));
    });
  return host;
}

function createPrintDesignerBody(
  systemId: string,
  module: BackendModule,
  fields: FieldDefinitionVO[],
  templates: PrintTemplateView[],
  result: HTMLElement,
): HTMLElement {
  const templateCodeInput = createElement('input', { ariaLabel: '打印模板编码' });
  templateCodeInput.value = templates[0]?.templateCode ?? `print_${Date.now()}`;
  const templateNameInput = createElement('input', { ariaLabel: '打印模板名称' });
  templateNameInput.value = templates[0]?.templateName ?? `${module.name} 打印模板`;
  const headerInput = createElement('input', { ariaLabel: '页眉' });
  headerInput.value = templates[0]?.headerText ?? `${module.name} 单据`;
  const footerInput = createElement('input', { ariaLabel: '页脚' });
  footerInput.value = templates[0]?.footerText ?? '由 unexamine 生成';
  const signatureInput = createElement('input', { ariaLabel: '签名区' });
  signatureInput.value = (templates[0]?.signatureLabels ?? ['制单人', '审核人']).join(',');
  const initialPageSetup = templates[0]?.pageSetup ?? {};
  const paperSelect = createElement('select', { ariaLabel: '打印纸张' });
  ['A4', 'A5', 'Letter'].forEach((value) => {
    const option = createElement('option', {}, value);
    option.value = value;
    paperSelect.append(option);
  });
  paperSelect.value = String(initialPageSetup.paper ?? 'A4');
  const orientationSelect = createElement('select', { ariaLabel: '打印方向' });
  [
    ['PORTRAIT', '纵向'],
    ['LANDSCAPE', '横向'],
  ].forEach(([value, label]) => {
    const option = createElement('option', {}, label);
    option.value = value;
    orientationSelect.append(option);
  });
  orientationSelect.value = String(initialPageSetup.orientation ?? 'PORTRAIT');
  const marginTopInput = createElement('input', { ariaLabel: 'print margin top' });
  marginTopInput.value = String(initialPageSetup.marginTop ?? '16mm');
  const marginRightInput = createElement('input', { ariaLabel: 'print margin right' });
  marginRightInput.value = String(initialPageSetup.marginRight ?? '14mm');
  const marginBottomInput = createElement('input', { ariaLabel: 'print margin bottom' });
  marginBottomInput.value = String(initialPageSetup.marginBottom ?? '16mm');
  const marginLeftInput = createElement('input', { ariaLabel: 'print margin left' });
  marginLeftInput.value = String(initialPageSetup.marginLeft ?? '14mm');
  const previewHost = createElement('div', { className: 'print-preview-surface' });
  const activeCodes = new Set<string>(templates[0]?.boundFieldCodes?.length ? templates[0].boundFieldCodes : fields.slice(0, 4).map((field) => field.fieldCode));
  const detailCodes = new Set<string>(templates[0]?.detailTableFieldCodes ?? []);
  const fieldChecks = fields.map((field) => {
    const checkbox = createElement('input', { ariaLabel: `bind_${field.fieldCode}` });
    checkbox.type = 'checkbox';
    checkbox.checked = activeCodes.has(field.fieldCode);
    checkbox.addEventListener('change', () => {
      if (checkbox.checked) {
        activeCodes.add(field.fieldCode);
      } else {
        activeCodes.delete(field.fieldCode);
      }
    });
    return createElement('label', { className: 'check-row' }, checkbox, createElement('span', {}, `${field.name} / ${field.fieldCode}`));
  });
  const detailChecks = fields.map((field) => {
    const checkbox = createElement('input', { ariaLabel: `detail_${field.fieldCode}` });
    checkbox.type = 'checkbox';
    checkbox.checked = detailCodes.has(field.fieldCode);
    checkbox.addEventListener('change', () => {
      if (checkbox.checked) {
        detailCodes.add(field.fieldCode);
      } else {
        detailCodes.delete(field.fieldCode);
      }
    });
    return createElement('label', { className: 'check-row' }, checkbox, createElement('span', {}, field.name));
  });

  const previewValues = () => Object.fromEntries(fields.map((field) => [field.fieldCode, `${field.name}预览值`]));
  const payload = () => ({
    templateCode: templateCodeInput.value.trim() || `print_${Date.now()}`,
    templateName: templateNameInput.value.trim() || `${module.name} 打印模板`,
    version: `draft_${Date.now()}`,
    status: 1,
    defaultTemplate: true,
    visibleRoleIds: [],
    boundFieldCodes: [...activeCodes],
    detailTableFieldCodes: [...detailCodes],
    signatureLabels: signatureInput.value.split(',').map((item) => item.trim()).filter(Boolean),
    headerText: headerInput.value.trim(),
    footerText: footerInput.value.trim(),
    previewFileId: `preview_${templateCodeInput.value.trim() || 'print'}`,
    pageSetup: {
      paper: paperSelect.value,
      orientation: orientationSelect.value,
      marginTop: marginTopInput.value.trim() || '16mm',
      marginRight: marginRightInput.value.trim() || '14mm',
      marginBottom: marginBottomInput.value.trim() || '16mm',
      marginLeft: marginLeftInput.value.trim() || '14mm',
      repeatHeader: true,
      repeatFooter: true,
      pageBreakPolicy: 'AVOID_SECTION_BREAK',
    },
  });
  const renderPreview = (preview: PrintTemplatePreview) => {
    previewHost.replaceChildren(createPrintPreview(preview));
  };
  const saveButton = createButton('保存模板草稿', 'primary', false);
  saveButton.addEventListener('click', async () => {
    saveButton.disabled = true;
    saveButton.textContent = '保存中...';
    try {
      const saved = await savePrintTemplate(systemId, module.moduleId, payload());
      result.textContent = `打印模板已保存：${saved.templateName} / ${saved.templateCode}`;
      saveButton.textContent = '保存模板草稿';
    } catch (error) {
      result.textContent = error instanceof Error ? error.message : '打印模板保存失败。';
    } finally {
      saveButton.disabled = false;
    }
  });
  const previewButton = createButton('预览模板', 'secondary', false);
  previewButton.addEventListener('click', async () => {
    await savePrintTemplate(systemId, module.moduleId, payload());
    renderPreview(await previewPrintTemplate(systemId, module.moduleId, payload().templateCode, previewValues()));
  });
  const checkButton = createButton('发布检查', 'secondary', false);
  checkButton.addEventListener('click', async () => {
    await savePrintTemplate(systemId, module.moduleId, payload());
    const check = await runPrintTemplatePublishCheck(systemId, module.moduleId, payload().templateCode);
    result.textContent = moduleCheckMessage(check);
  });
  const publishButton = createButton('发布模板', 'primary', false);
  publishButton.addEventListener('click', async () => {
    await savePrintTemplate(systemId, module.moduleId, payload());
    const check = await runPrintTemplatePublishCheck(systemId, module.moduleId, payload().templateCode);
    if (!check.passed) {
      result.textContent = moduleCheckMessage(check);
      return;
    }
    const published = await publishPrintTemplate(systemId, module.moduleId, payload().templateCode, 'system admin print template publish');
    result.textContent = `打印模板已发布：${published.version ?? published.result}，traceId=${published.traceId}`;
  });
  return createElement(
    'section',
    { className: 'print-designer-grid' },
    createElement('div', { className: 'print-field-picker' }, createElement('strong', {}, '字段选择'), ...fieldChecks),
    createElement(
      'div',
      { className: 'print-preview-panel' },
      createElement('div', { className: 'inline-actions' }, saveButton, previewButton, checkButton, publishButton),
      previewHost,
    ),
    createElement(
      'div',
      { className: 'print-props-panel' },
      createElement('strong', {}, '页面与签名'),
      createElement('label', {}, createElement('span', {}, '模板编码'), templateCodeInput),
      createElement('label', {}, createElement('span', {}, '模板名称'), templateNameInput),
      createElement('label', {}, createElement('span', {}, '页眉'), headerInput),
      createElement('label', {}, createElement('span', {}, '页脚'), footerInput),
      createElement('label', {}, createElement('span', {}, '签名区'), signatureInput),
      createElement('label', {}, createElement('span', {}, '纸张'), paperSelect),
      createElement('label', {}, createElement('span', {}, '方向'), orientationSelect),
      createElement(
        'div',
        { className: 'print-margin-grid' },
        createElement('label', {}, createElement('span', {}, '上边距'), marginTopInput),
        createElement('label', {}, createElement('span', {}, '右边距'), marginRightInput),
        createElement('label', {}, createElement('span', {}, '下边距'), marginBottomInput),
        createElement('label', {}, createElement('span', {}, '左边距'), marginLeftInput),
      ),
      createElement('div', { className: 'simple-stack' }, createElement('strong', {}, '明细表字段'), ...detailChecks),
      createElement('small', {}, `已发布模板：${templates.filter((item) => item.publishStatus === 'PUBLISHED').length}`),
    ),
  );
}

function createPrintPreview(preview: PrintTemplatePreview): HTMLElement {
  const setup = preview.pageSetup ?? {};
  return createElement(
    'article',
    { className: 'print-page-preview', dataset: { printPreview: 'true' } },
    createElement('header', {}, createElement('h3', {}, preview.templateName), createElement('small', {}, `${preview.templateCode} / ${preview.version}`)),
    createElement(
      'section',
      { className: 'print-export-meta' },
      createElement('span', {}, `Page: ${String(setup.paper ?? 'A4')} / ${String(setup.orientation ?? 'PORTRAIT')}`),
      createElement('span', {}, `Margins: ${String(setup.marginTop ?? '16mm')} ${String(setup.marginRight ?? '14mm')} ${String(setup.marginBottom ?? '16mm')} ${String(setup.marginLeft ?? '14mm')}`),
      preview.exportMeta ? createElement('span', {}, `Export: ${preview.exportMeta.format} / CSS ${preview.exportMeta.printCssReady ? 'ready' : 'missing'} / pages ${preview.exportMeta.estimatedPageCount}`) : null,
    ),
    ...preview.sections.map((section) => createElement(
      'section',
      {},
      createElement('strong', {}, section.title),
      section.rows.length === 0 ? createElement('p', {}, '暂无字段') : createTable(
        ['字段', '编码', '内容'],
        section.rows.map((row) => [row.label, row.fieldCode, row.value]),
        '暂无字段',
      ),
    )),
    createTraceLine(preview.traceId, preview.exportFileId ?? preview.publishStatus),
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
      {
        className: 'field-list-card',
        dataset: {
          moduleFieldId: field.fieldId,
          moduleFieldCode: field.fieldCode,
          moduleFieldType: field.fieldType,
          moduleFieldRequired: String(field.required),
          moduleFieldDictTypeId: field.dictTypeId ?? '',
          moduleFieldPermissionMode: field.permissionMode ?? '',
          moduleFieldRuntimeReadable: String(field.permissionMetadata?.runtimeReadable !== false),
          moduleFieldRuntimeWritable: String(field.permissionMetadata?.runtimeWritable !== false),
        },
      },
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
        createElement('span', {},
          createElement('strong', {}, dict.dictName),
          createElement('small', {}, ` ${dict.dictCode}`),
          createElement('small', {}, ` refs:${dict.fieldReferenceCount ?? 0} disabled:${dict.disabledItemCount ?? 0} version:${dict.publishedVersion ?? '-'}`),
        ),
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
  configureButton.dataset.r92ConfigureFlowButton = flow.flowId;
  configureButton.addEventListener('click', () => {
    renderFlowDesigner(systemId, flow, result, designerHost);
  });
  const checkButton = createButton('发布检查', 'secondary', false);
  const publishButton = createButton('发布', 'primary', false);
  checkButton.dataset.r92FlowRowCheckButton = flow.flowId;
  publishButton.dataset.r92FlowRowPublishButton = flow.flowId;
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
  const designer = createElement('section', { className: 'flow-designer', dataset: { r92WorkflowDesigner: 'true', r92FlowId: flow.flowId } });
  const status = createElement('p', { className: 'field-error' }, flowCanvasSummary(nodes, edges));
  const simulationAmountInput = createElement('input', { ariaLabel: '模拟金额' }) as HTMLInputElement;
  simulationAmountInput.type = 'number';
  simulationAmountInput.value = '120000';
  const simulationRecordInput = createElement('input', { ariaLabel: '模拟记录 ID' }) as HTMLInputElement;
  simulationRecordInput.value = 'preview_record';
  const simulationActorInput = createElement('input', { ariaLabel: '模拟发起人' }) as HTMLInputElement;
  simulationActorInput.value = 'current_member';
  const simulationResult = createElement('div', { className: 'flow-simulation-result', dataset: { flowSimulationResult: 'true' } });
  const publishCheckResult = createElement('div', { className: 'flow-publish-impact-result', dataset: { r92PublishImpactSummary: 'pending', r92PublishImpactCount: '0', r92PublishWarningCount: '0' } });

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
      createFlowSimulationPanel(simulationAmountInput, simulationRecordInput, simulationActorInput, simulationResult),
      publishCheckResult,
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
        }, () => {
          const preset = createAdvancedFlowPreset();
          nodes = preset.nodes;
          edges = preset.edges;
          selectedNodeKey = 'node_r92_external_api';
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
    button.dataset.r92FlowCanvasSaveButton = 'true';
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
    button.dataset.r92FlowCanvasCheckButton = 'true';
    button.addEventListener('click', async () => {
      button.disabled = true;
      button.textContent = '检查中...';
      try {
        await persistCanvas();
        const check = await runFlowPublishCheck(systemId, flow.flowId, '系统后台流程画布发布检查');
        publishCheckResult.replaceChildren(renderFlowPublishCheckResult(check));
        status.textContent = flowCheckMessage(check);
        result.textContent = status.textContent;      } catch (error) {
        status.textContent = error instanceof Error ? error.message : '流程发布检查失败。';
        publishCheckResult.replaceChildren(createElement('p', { className: 'field-error', dataset: { r92PublishImpactSummary: 'failed' } }, status.textContent));
      } finally {
        button.disabled = false;
        button.textContent = '发布检查';
      }
    });
    return button;
  };

  const createFlowSimulateButton = () => {
    const button = createButton('模拟运行', 'secondary', false);
    button.dataset.r92FlowSimulateButton = 'true';
    button.addEventListener('click', async () => {
      button.disabled = true;
      button.textContent = '模拟中...';
      try {
        await persistCanvas();
        const amount = Number(simulationAmountInput.value || '0');
        const simulation = await simulateSystemFlow(systemId, flow.flowId, {
          versionNo: 'DRAFT',
          recordId: simulationRecordInput.value.trim() || 'preview_record',
          actorMemberId: simulationActorInput.value.trim() || 'current_member',
          fieldValues: {
            amount,
          },
        });
        status.textContent = `模拟${simulation.passed ? '通过' : '失败'}：${simulation.stepTraces.length} 步，traceId=${simulation.traceId}`;
        simulationResult.replaceChildren(renderFlowSimulationResult(simulation));
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

function createFlowSimulationPanel(
  amountInput: HTMLInputElement,
  recordInput: HTMLInputElement,
  actorInput: HTMLInputElement,
  result: HTMLElement,
): HTMLElement {
  return createElement(
    'section',
    { className: 'flow-simulation-panel', dataset: { flowSimulationPanel: 'true' } },
    createElement('div', { className: 'runtime-card-head' }, createElement('h3', {}, '模拟运行'), createElement('small', {}, '只预测路径，不创建审批实例')),
    createElement(
      'div',
      { className: 'flow-simulation-form' },
      createElement('label', {}, createElement('span', {}, '样例金额'), amountInput),
      createElement('label', {}, createElement('span', {}, '记录 ID'), recordInput),
      createElement('label', {}, createElement('span', {}, '发起人'), actorInput),
    ),
    result,
  );
}

function renderFlowPublishCheckResult(check: FlowPublishCheckResult): HTMLElement {
  const impacts = check.impactRefs ?? [];
  return createElement(
    'div',
    {
      className: 'flow-publish-impact-output',
      dataset: {
        r92PublishImpactSummary: 'true',
        r92PublishPassed: String(check.passed),
        r92PublishImpactCount: String(impacts.length),
        r92PublishWarningCount: String(check.warningItems.length),
        r92PublishFailureCount: String(check.failureItems.length),
        r92PublishTraceId: check.traceId,
      },
    },
    createElement('div', { className: 'runtime-card-head' }, createElement('h3', {}, '发布影响'), createElement('small', {}, `traceId=${check.traceId}`)),
    createElement('p', {}, `检查${check.passed ? '通过' : '未通过'} / 阻断 ${check.failureItems.length} / 提醒 ${check.warningItems.length} / 影响 ${impacts.length}`),
    createElement(
      'div',
      { className: 'flow-simulation-grid' },
      createElement('div', {}, createElement('strong', {}, '阻断项'), check.failureItems.length === 0
        ? createElement('p', {}, '无阻断项。')
        : createElement('ul', {}, ...check.failureItems.map((item) => createElement('li', {}, `${item.itemName}: ${item.message}`)))),
      createElement('div', {}, createElement('strong', {}, '提醒项'), check.warningItems.length === 0
        ? createElement('p', {}, '无提醒项。')
        : createElement('ul', {}, ...check.warningItems.map((item) => createElement('li', {}, `${item.itemName}: ${item.message}`)))),
      createElement('div', {}, createElement('strong', {}, '影响对象'), impacts.length === 0
        ? createElement('p', {}, '暂无影响对象。')
        : createElement('ul', {}, ...impacts.map((item) => createElement('li', {}, impactRefText(item))))),
    ),
  );
}

function impactRefText(item: Record<string, unknown>): string {
  const type = String(item.refType ?? item.type ?? 'UNKNOWN');
  const name = String(item.name ?? item.refName ?? item.refId ?? '-');
  const impactType = String(item.impactType ?? item.action ?? '-');
  return `${type} / ${name}: ${impactType}`;
}
function renderFlowSimulationResult(simulation: FlowSimulationResult): HTMLElement {
  const blockers = simulation.blockerItems ?? simulation.failureItems ?? [];
  const approvers = simulation.predictedApprovers ?? [];
  const impacts = simulation.impactRefs ?? [];
  return createElement(
    'div',
    { className: 'flow-simulation-output', dataset: { r92SimulationOutput: 'true', r92SimulationPassed: String(simulation.passed), r92SimulationImpactCount: String(impacts.length), r92SimulationStepCount: String(simulation.stepTraces.length) } },
    createElement('p', {}, `结果：${simulation.passed ? '通过' : '存在阻断'} / ${simulation.stepTraces.length} 步 / traceId=${simulation.traceId}`),
    createElement('p', {}, `运行实例：${simulation.runtimeInstanceCreated ? '已创建' : '未创建，仅模拟'}`),
    createElement(
      'div',
      { className: 'flow-simulation-grid' },
      createElement('div', {}, createElement('strong', {}, '预测路径'), simulation.stepTraces.length === 0
        ? createElement('p', {}, '暂无路径。')
        : createElement('ol', {}, ...simulation.stepTraces.map((step) => createElement('li', {}, `${step.nodeName} -> ${step.outputSummary ?? ''}`)))),
      createElement('div', {}, createElement('strong', {}, '预计审批人'), approvers.length === 0
        ? createElement('p', {}, '当前路径没有人工审批节点。')
        : createElement('ul', {}, ...approvers.map((item) => createElement('li', {}, `${item.nodeName}: ${item.displayName}`)))),
      createElement('div', {}, createElement('strong', {}, '阻断项'), blockers.length === 0
        ? createElement('p', {}, '无阻断项。')
        : createElement('ul', {}, ...blockers.map((item) => createElement('li', {}, `${item.itemName}: ${item.message}`)))),
      createElement('div', {}, createElement('strong', {}, '发布影响'), impacts.length === 0
        ? createElement('p', {}, '暂无影响项。')
        : createElement('ul', {}, ...impacts.map((item) => createElement('li', {}, `${item.name}: ${item.impactType}`)))),
    ),
  );
}

function createFlowNodeLibraryPanel(library: FlowNodeLibraryItem[], onAdd: (item: FlowNodeLibraryItem) => void, onPreset: () => void, onAdvancedPreset: () => void): HTMLElement {
  const presetButton = createButton('插入审批-条件模板', 'secondary', false);
  presetButton.dataset.r92BasicPresetButton = 'true';
  presetButton.addEventListener('click', onPreset);
  const advancedPresetButton = createButton('插入高级节点模板', 'secondary', false);
  advancedPresetButton.dataset.r92AdvancedPresetButton = 'true';
  advancedPresetButton.addEventListener('click', onAdvancedPreset);
  return createElement(
    'aside',
    { className: 'flow-node-library', dataset: { r92AdvancedNodeLibrary: 'true', r92AdvancedNodeTypes: library.map((item) => item.nodeType).join(',') } },
    createElement('strong', {}, '节点库'),
    presetButton,
    advancedPresetButton,
    ...library.map((item) => {
      const button = createButton(item.nodeTypeName || item.nodeType, 'ghost', false, item.description);
      button.addEventListener('click', () => onAdd(item));
      return createElement('article', { className: 'flow-library-item', dataset: { r92AdvancedNode: item.nodeType, r92AdvancedNodeCategory: item.category || '' } }, button, createElement('small', {}, item.category || item.nodeType));
    }),
  );
}

function createFlowCanvasPanel(nodes: FlowNodeConfigView[], edges: FlowEdgeView[], selectedNodeKey: string, onSelect: (nodeKey: string) => void): HTMLElement {
  const canvasWidth = Math.max(980, ...nodes.map((node) => node.position.x + node.position.width + 40));
  const canvasHeight = Math.max(360, ...nodes.map((node) => node.position.y + node.position.height + 40));
  const canvas = createElement('div', { className: 'flow-canvas' });
  canvas.style.width = `${canvasWidth}px`;
  canvas.style.height = `${canvasHeight}px`;
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('class', 'flow-canvas-lines');
  svg.setAttribute('viewBox', `0 0 ${canvasWidth} ${canvasHeight}`);
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
  const branchInput = createElement('input', { ariaLabel: '分支标签' });
  branchInput.placeholder = '通过 / 默认 / 金额达到阈值';
  const conditionFieldInput = createElement('input', { ariaLabel: '条件字段' });
  conditionFieldInput.placeholder = 'amount';
  const conditionOperatorSelect = createElement('select', { ariaLabel: 'condition operator' });
  ['NONE', 'GTE', 'GT', 'LTE', 'LT', 'EQ', 'NE', 'CONTAINS'].forEach((operator) => {
    const option = createElement('option', {}, operator === 'NONE' ? '无条件/默认分支' : operator);
    option.setAttribute('value', operator);
    conditionOperatorSelect.append(option);
  });
  const conditionExpectedInput = createElement('input', { ariaLabel: 'condition expected value' });
  conditionExpectedInput.placeholder = '100000';
  const addButton = createButton('添加连线', 'primary', nodes.length < 2, nodes.length < 2 ? '至少需要两个节点' : undefined);
  addButton.addEventListener('click', () => {
    const source = sourceSelect.value;
    const target = targetSelect.value;
    if (!source || !target || source === target) {
      return;
    }
    const operator = conditionOperatorSelect.value;
    const expectedText = conditionExpectedInput.value.trim();
    const conditionPayload = operator === 'NONE' || !conditionFieldInput.value.trim()
      ? null
      : {
          expressionId: `expr_${conditionFieldInput.value.trim()}_${operator.toLowerCase()}_${Date.now()}`,
          fieldCode: conditionFieldInput.value.trim(),
          operator,
          expectedValue: numericOrText(expectedText),
          expressionText: `${conditionFieldInput.value.trim()} ${operator} ${expectedText}`,
        };
    onAdd({
      edgeKey: `edge_${source}_${target}_${Date.now()}`,
      sourceNodeKey: source,
      targetNodeKey: target,
      branchLabel: branchInput.value.trim() || 'default',
      conditionPayload,
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
      createElement('label', {}, createElement('span', {}, '条件字段'), conditionFieldInput),
      createElement('label', {}, createElement('span', {}, '条件操作'), conditionOperatorSelect),
      createElement('label', {}, createElement('span', {}, '期望值'), conditionExpectedInput),
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
          assigneeIds: ['role_flow_approver'],
          allowTransfer: true,
          allowReject: true,
          reasonRequired: false,
        },
        status: 1,
        propertyPanelCode: 'approvalPropertyPanel',
        runtimeExecutable: true,
      },
      {
        nodeKey: 'node_amount_condition',
        nodeType: 'condition',
        nodeName: '金额条件分支',
        position: { x: 380, y: 120, width: 180, height: 72 },
        propertyPayload: {
          ruleMode: 'FIRST_MATCH',
          defaultBranchLabel: '默认',
          unmatchedPolicy: 'FOLLOW_DEFAULT_BRANCH',
        },
        status: 1,
        propertyPanelCode: 'conditionPropertyPanel',
        runtimeExecutable: true,
      },
      {
        nodeKey: 'node_manager_review',
        nodeType: 'approval',
        nodeName: '主管复核',
        position: { x: 640, y: 60, width: 180, height: 72 },
        propertyPayload: {
          approvalType: 'OR_SIGN',
          assigneeType: 'ROLE',
          assigneeIds: ['role_manager_reviewer'],
          allowTransfer: true,
          allowReject: true,
          reasonRequired: true,
        },
        status: 1,
        propertyPanelCode: 'approvalPropertyPanel',
        runtimeExecutable: true,
      },
      {
        nodeKey: 'node_end_passed',
        nodeType: 'end',
        nodeName: '审批通过结束',
        position: { x: 900, y: 120, width: 180, height: 72 },
        propertyPayload: {},
        status: 1,
        propertyPanelCode: 'endPropertyPanel',
        runtimeExecutable: true,
      },
    ],
    edges: [
      {
        edgeKey: 'edge_submit_condition',
        sourceNodeKey: 'node_submit_review',
        targetNodeKey: 'node_amount_condition',
        branchLabel: 'approved',
        conditionPayload: null,
      },
      {
        edgeKey: 'edge_condition_manager',
        sourceNodeKey: 'node_amount_condition',
        targetNodeKey: 'node_manager_review',
        branchLabel: '金额大于等于 10 万',
        conditionPayload: {
          expressionId: 'expr_amount_gte_100000',
          fieldCode: 'amount',
          operator: 'GTE',
          expectedValue: 100000,
          expressionText: 'amount GTE 100000',
        },
      },
      {
        edgeKey: 'edge_condition_end',
        sourceNodeKey: 'node_amount_condition',
        targetNodeKey: 'node_end_passed',
        branchLabel: '金额小于 10 万',
        conditionPayload: {
          expressionId: 'expr_amount_lt_100000',
          fieldCode: 'amount',
          operator: 'LT',
          expectedValue: 100000,
          expressionText: 'amount LT 100000',
        },
      },
      {
        edgeKey: 'edge_manager_end',
        sourceNodeKey: 'node_manager_review',
        targetNodeKey: 'node_end_passed',
        branchLabel: '复核通过',
        conditionPayload: null,
      },
    ],
  };
}

function createAdvancedFlowPreset(): { nodes: FlowNodeConfigView[]; edges: FlowEdgeView[] } {
  return {
    nodes: [
      {
        nodeKey: 'node_r92_approval',
        nodeType: 'approval',
        nodeName: 'R92 审批节点',
        position: { x: 80, y: 150, width: 180, height: 72 },
        propertyPayload: {
          approvalType: 'OR_SIGN',
          assigneeType: 'ROLE',
          assigneeIds: ['role_flow_approver'],
          allowTransfer: true,
          allowReject: true,
          reasonRequired: true,
        },
        status: 1,
        propertyPanelCode: 'approvalPropertyPanel',
        runtimeExecutable: true,
      },
      {
        nodeKey: 'node_r92_condition',
        nodeType: 'condition',
        nodeName: 'R92 金额条件',
        position: { x: 320, y: 150, width: 180, height: 72 },
        propertyPayload: {
          ruleMode: 'FIRST_MATCH',
          defaultBranchLabel: '默认',
          unmatchedPolicy: 'FOLLOW_DEFAULT_BRANCH',
        },
        status: 1,
        propertyPanelCode: 'conditionPropertyPanel',
        runtimeExecutable: true,
      },
      {
        nodeKey: 'node_r92_external_api',
        nodeType: 'external_api',
        nodeName: 'R92 外部 API',
        position: { x: 560, y: 70, width: 180, height: 72 },
        propertyPayload: {
          appId: 'openapi_r92_app',
          endpointCode: 'notify_external_system',
          method: 'POST',
          timeoutMs: 3000,
          retryPolicy: { maxRetries: 2, intervalSeconds: 30 },
        },
        status: 1,
        propertyPanelCode: 'externalApiPropertyPanel',
        runtimeExecutable: true,
      },
      {
        nodeKey: 'node_r92_timer',
        nodeType: 'timer',
        nodeName: 'R92 定时器',
        position: { x: 800, y: 70, width: 180, height: 72 },
        propertyPayload: {
          timerMode: 'DELAY',
          delayMinutes: 30,
          businessCalendarCode: 'default_workday',
          maxTriggerCount: 1,
        },
        status: 1,
        propertyPanelCode: 'timerPropertyPanel',
        runtimeExecutable: true,
      },
      {
        nodeKey: 'node_r92_field_update',
        nodeType: 'field_update',
        nodeName: 'R92 字段回写',
        position: { x: 560, y: 230, width: 180, height: 72 },
        propertyPayload: {
          updateRules: [{ fieldCode: 'approvalStatus', valueExpression: 'APPROVED', overwritePolicy: 'ALWAYS' }],
          auditReason: 'R92 publish impact verification',
        },
        status: 1,
        propertyPanelCode: 'fieldUpdatePropertyPanel',
        runtimeExecutable: true,
      },
      {
        nodeKey: 'node_r92_timeout',
        nodeType: 'timeout_reminder',
        nodeName: 'R92 超时提醒',
        position: { x: 800, y: 230, width: 180, height: 72 },
        propertyPayload: {
          targetNodeKey: 'node_r92_approval',
          timeoutMinutes: 60,
          templateCode: 'tpl_flow_timeout_reminder',
          escalateToRoleIds: ['role_flow_admin'],
        },
        status: 1,
        propertyPanelCode: 'timeoutReminderPropertyPanel',
        runtimeExecutable: false,
      },
      {
        nodeKey: 'node_r92_end',
        nodeType: 'end',
        nodeName: 'R92 结束',
        position: { x: 1040, y: 150, width: 180, height: 72 },
        propertyPayload: {},
        status: 1,
        propertyPanelCode: 'endPropertyPanel',
        runtimeExecutable: true,
      },
    ],
    edges: [
      { edgeKey: 'edge_r92_approval_condition', sourceNodeKey: 'node_r92_approval', targetNodeKey: 'node_r92_condition', branchLabel: 'approved', conditionPayload: null },
      { edgeKey: 'edge_r92_condition_api', sourceNodeKey: 'node_r92_condition', targetNodeKey: 'node_r92_external_api', branchLabel: 'amount >= 100000', conditionPayload: { expressionId: 'expr_r92_amount_gte', fieldCode: 'amount', operator: 'GTE', expectedValue: 100000, expressionText: 'amount GTE 100000' } },
      { edgeKey: 'edge_r92_api_timer', sourceNodeKey: 'node_r92_external_api', targetNodeKey: 'node_r92_timer', branchLabel: 'api success', conditionPayload: null },
      { edgeKey: 'edge_r92_timer_end', sourceNodeKey: 'node_r92_timer', targetNodeKey: 'node_r92_end', branchLabel: 'timer reached', conditionPayload: null },
      { edgeKey: 'edge_r92_condition_field', sourceNodeKey: 'node_r92_condition', targetNodeKey: 'node_r92_field_update', branchLabel: 'amount < 100000', conditionPayload: { expressionId: 'expr_r92_amount_lt', fieldCode: 'amount', operator: 'LT', expectedValue: 100000, expressionText: 'amount LT 100000' } },
      { edgeKey: 'edge_r92_field_timeout', sourceNodeKey: 'node_r92_field_update', targetNodeKey: 'node_r92_timeout', branchLabel: 'write back', conditionPayload: null },
      { edgeKey: 'edge_r92_timeout_end', sourceNodeKey: 'node_r92_timeout', targetNodeKey: 'node_r92_end', branchLabel: 'reminded', conditionPayload: null },
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

function numericOrText(value: string): string | number {
  if (!value.trim()) {
    return '';
  }
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : value;
}

function parseJsonObjectInput(value: string, label: string, result: HTMLElement): Record<string, unknown> {
  const trimmed = value.trim();
  if (!trimmed) {
    return {};
  }
  const parsed = JSON.parse(trimmed);
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    result.textContent = `${label} must be a JSON object.`;
    throw new Error(`${label} must be a JSON object.`);
  }
  return parsed as Record<string, unknown>;
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
  const impactButton = createButton('影响分析', 'secondary', false);
  impactButton.addEventListener('click', async () => {
    impactButton.disabled = true;
    try {
      const impact = await loadSystemDictImpact(systemId, dict.dictTypeId);
      result.textContent = `字典影响分析：字段引用=${impact.fieldReferenceCount}，模块引用=${impact.moduleReferenceCount}，停用选项=${impact.disabledItemCount}，版本=${impact.publishedVersion ?? '-'}，traceId=${impact.traceId}`;
    } catch (error) {
      result.textContent = error instanceof Error ? error.message : '字典影响分析失败。';
    } finally {
      impactButton.disabled = false;
    }
  });
  const publishButton = createButton('发布', 'primary', false);
  publishButton.addEventListener('click', async () => {
    publishButton.disabled = true;
    publishButton.textContent = '发布中...';
    try {
      const published = await publishSystemDictType(systemId, dict.dictTypeId, 'system admin dictionary publish');
      result.textContent = `字典已发布：版本=${published.version ?? '-'}，traceId=${published.traceId}`;
      reload();
    } catch (error) {
      result.textContent = error instanceof Error ? error.message : '字典发布失败。';
    } finally {
      publishButton.disabled = false;
      publishButton.textContent = '发布';
    }
  });
  return createElement('div', { className: 'row-actions' }, itemButton, impactButton, publishButton);
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
    { className: 'result-panel', dataset: { systemLogsPanel: 'true', logCount: String(data.logs.records.length) } },
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
    { className: 'admin-stack', dataset: { systemLogTable: 'true', logCount: String(page.records.length) } },
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
  const visibleWarnings = warnings.slice(0, 3);
  const hiddenCount = Math.max(0, warnings.length - visibleWarnings.length);
  return createElement(
    'section',
    { className: 'panel warning-summary-panel' },
    createElement('div', { className: 'runtime-card-head' },
      createElement('div', {}, createElement('h2', {}, '加载提醒'), createElement('p', {}, hiddenCount > 0 ? `显示前 ${visibleWarnings.length} 条，其余 ${hiddenCount} 条请进入对应栏目处理。` : '当前后台数据加载存在需要处理的提醒。')),
      renderStatusPill(`${warnings.length} 条`, 'warning'),
    ),
    createElement('div', { className: 'warning-summary-list' }, ...visibleWarnings.map((warning) => createElement('p', {}, warning))),
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

function createTreeButton(label: string, active = false, dataset?: Record<string, string>): HTMLButtonElement {
  const button = createElement('button', { className: active ? 'sidebar-item active' : 'sidebar-item', dataset }, label);
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

function buildHomePageWidgets(codes: string[], current?: HomePageConfigView): HomePageWidgetConfig[] {
  const existing = new Map((current?.widgets ?? []).map((widget) => [widget.widgetCode, widget]));
  const catalog: Record<string, HomePageWidgetConfig> = {
    overview: { widgetCode: 'overview', widgetName: '工作概览', widgetType: 'metric', sourceType: 'WORK_DASHBOARD', sort: 10, visible: true },
    warnings: { widgetCode: 'warnings', widgetName: '今日预警', widgetType: 'list', sourceType: 'WORK_WARNING', sort: 20, visible: true },
    calendar: { widgetCode: 'calendar', widgetName: '当月日历', widgetType: 'calendar', sourceType: 'WORK_CALENDAR', sort: 30, visible: true },
    modules: { widgetCode: 'modules', widgetName: '业务模块入口', widgetType: 'shortcut', sourceType: 'RUNTIME_MODULES', sort: 40, visible: true },
  };
  const selected = codes.length ? codes : ['overview', 'warnings', 'calendar', 'modules'];
  return selected.map((code, index) => ({
    ...(existing.get(code) ?? catalog[code] ?? {
      widgetCode: code,
      widgetName: code,
      widgetType: 'custom',
      sourceType: 'CUSTOM',
      visible: true,
    }),
    sort: (index + 1) * 10,
    visible: true,
  }));
}

function widgetSummary(widgets: HomePageWidgetConfig[]): string {
  if (widgets.length === 0) {
    return '未配置';
  }
  return widgets.map((widget) => widget.widgetName || widget.widgetCode).join(' / ');
}

function buildPageComponents(codes: string[]): PageComponentConfig[] {
  const catalog: Record<string, PageComponentConfig> = {
    toolbar: { componentCode: 'toolbar', componentType: 'SHORTCUT', title: '页面动作', dataSource: 'MODULE_ACTIONS', sort: 10, visible: true },
    list: { componentCode: 'list', componentType: 'LIST', title: '数据列表', dataSource: 'RUNTIME_RECORDS', sort: 20, visible: true },
    detail: { componentCode: 'detail', componentType: 'DETAIL', title: '右侧详情', dataSource: 'RECORD_DETAIL', sort: 30, visible: true },
    form: { componentCode: 'form', componentType: 'FORM', title: '编辑表单', dataSource: 'MODULE_FIELDS', sort: 40, visible: true },
    chart: { componentCode: 'chart', componentType: 'CHART', title: '统计图表', dataSource: 'RUNTIME_STATISTICS', boundFieldCode: 'status', sort: 50, visible: true },
  };
  const selected = codes.length ? codes : ['toolbar', 'list', 'detail'];
  return selected.map((code, index) => ({
    ...(catalog[code] ?? {
      componentCode: code,
      componentType: 'CUSTOM',
      title: code,
      dataSource: 'CUSTOM',
      visible: true,
    }),
    sort: (index + 1) * 10,
  }));
}

function normalizePageComponents(components?: PageComponentConfig[]): PageComponentConfig[] {
  const source = components && components.length > 0 ? components : buildPageComponents(['toolbar', 'list', 'detail', 'form']);
  return source
    .map((component, index) => {
      const normalized = {
        ...component,
        componentCode: component.componentCode,
        componentType: component.componentType || 'CUSTOM',
        title: component.title || component.componentCode,
        dataSource: component.dataSource || 'CUSTOM',
        visible: component.visible !== false,
        sort: component.sort ?? (index + 1) * 10,
        props: component.props ?? {},
      };
      ensurePageComponentProps(normalized, index);
      return normalized;
    })
    .sort((left, right) => (left.sort ?? 0) - (right.sort ?? 0));
}


function ensurePageComponentProps(component: PageComponentConfig, index: number): void {
  component.props = {
    width: component.props?.width ?? defaultComponentWidth(component.componentType, index),
    placement: component.props?.placement ?? defaultComponentPlacement(component.componentType),
    dragCanvas: component.props?.dragCanvas ?? 'page-designer',
    ...component.props,
  };
}

function componentLayoutWidth(component: PageComponentConfig): string {
  return String(component.props?.width ?? defaultComponentWidth(component.componentType, 0));
}

function componentPlacement(component: PageComponentConfig): string {
  return String(component.props?.placement ?? defaultComponentPlacement(component.componentType));
}

function defaultComponentWidth(componentType: string | undefined, index: number): string {
  if (componentType === 'CHART' || componentType === 'SHORTCUT') {
    return 'half';
  }
  if (index >= 3) {
    return 'half';
  }
  return 'full';
}

function defaultComponentPlacement(componentType: string | undefined): string {
  if (componentType === 'DETAIL') {
    return 'right';
  }
  if (componentType === 'SHORTCUT') {
    return 'top';
  }
  return 'main';
}

function nextComponentWidth(component: PageComponentConfig): string {
  const widths = ['full', 'half', 'third'];
  const current = componentLayoutWidth(component);
  return widths[(widths.indexOf(current) + 1) % widths.length] ?? 'full';
}

function nextComponentPlacement(component: PageComponentConfig): string {
  const placements = ['top', 'main', 'right', 'bottom'];
  const current = componentPlacement(component);
  return placements[(placements.indexOf(current) + 1) % placements.length] ?? 'main';
}

function componentPropsSummary(component: PageComponentConfig): string {
  return `宽度 ${componentLayoutWidth(component)} / 位置 ${componentPlacement(component)}`;
}
function resortPageComponents(components: PageComponentConfig[]): void {
  components.forEach((component, index) => {
    component.sort = (index + 1) * 10;
  });
}

function uniqueComponentCode(components: PageComponentConfig[], baseCode: string): string {
  const existing = new Set(components.map((component) => component.componentCode));
  let candidate = baseCode;
  let index = 1;
  while (existing.has(candidate)) {
    index += 1;
    candidate = `${baseCode}_${index}`;
  }
  return candidate;
}

function componentSummary(components?: PageComponentConfig[]): string {
  if (!components || components.length === 0) {
    return '未配置';
  }
  return components.map((component) => component.title || component.componentCode).join(' / ');
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
