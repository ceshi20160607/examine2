import type { ActionContract, BusinessDetailView, DynamicColumn, DynamicListSchema, FieldDefinitionVO, OperationRecord } from '../../../api/types';
import {
  confirmRuntimeImport,
  createRuntimeExport,
  deleteRuntimeRecord,
  executeRuntimeRecordAction,
  loadRuntimeLiveData,
  precheckRuntimeImport,
  saveRuntimeDraft,
  saveRuntimeRecord,
  saveRuntimeScene,
  uploadRuntimeImportFile,
  type RuntimeExportResult,
  type RuntimeImportConfirmResult,
  type RuntimeImportPrecheckResult,
  type RuntimeLiveData,
  type RuntimeSort,
} from '../../../api/liveData';
import { shellState } from '../../../app/state';
import { createButton, createElement, createTraceLine } from '../../../shared/components';
import { requestTextInput } from '../../../shared/dialogs';
import { createExportPanel, createImportPanel } from '../import-export/importExportPanel';
import {
  type RuntimeModuleItem,
  type RuntimeRecordRow,
} from './runtimeData';


export interface RuntimeRecentEntry {
  systemId: string;
  moduleId: string;
  moduleName: string;
  recordId?: string;
  title: string;
  updatedAt: string;
}

export interface RuntimeDraftEntry {
  systemId: string;
  moduleId: string;
  moduleName: string;
  draftId: string;
  recordId?: string;
  title: string;
  fieldValues: Record<string, unknown>;
  updatedAt: string;
}

const RUNTIME_RECENT_STORAGE_KEY = 'unexamine.runtime.recent.v1';
const RUNTIME_DRAFT_STORAGE_KEY = 'unexamine.runtime.drafts.v1';
const R88_RUNTIME_EFFICIENCY_MARKER = 'runtimeEfficiencyR88';
type RuntimePanel = 'detail' | 'create' | 'edit' | 'import' | 'export' | 'columns';
type DetailTab = 'base' | 'children' | 'attachments' | 'print' | 'logs';

interface RuntimePageState {
  selectedIds: Set<string>;
  activeRecordId: string;
  activeModuleId?: string;
  activePanel: RuntimePanel;
  activeTab: DetailTab;
  advancedOpen: boolean;
  loading: boolean;
  realEmpty: boolean;
  pageNo: number;
  pageSize: number;
  keyword: string;
  fieldFilters: Record<string, string>;
  sorts: RuntimeSort[];
  draftId?: string;
  restoredDraftValues?: Record<string, unknown>;
  validationFieldCode?: string;
  actionMessage?: string;
  importPrecheck?: RuntimeImportPrecheckResult;
  importConfirm?: RuntimeImportConfirmResult;
  importMessage?: string;
  importLoading: boolean;
  exportResult?: RuntimeExportResult;
  exportMessage?: string;
  exportLoading: boolean;
  loadError?: string;
}

const state: RuntimePageState = {
  selectedIds: new Set(),
  activeRecordId: '',
  activePanel: 'detail',
  activeTab: 'base',
  advancedOpen: false,
  loading: false,
  realEmpty: false,
  pageNo: 1,
  pageSize: 10,
  keyword: '',
  fieldFilters: {},
  sorts: [],
  importLoading: false,
  exportLoading: false,
};

let liveData: RuntimeLiveData | undefined;
let loadVersion = 0;
let appliedRuntimeRouteIntent = '';

export function renderRuntimeRecordPage(): HTMLElement {
  applyRuntimeRouteIntent();
  const root = createElement('section', { className: 'runtime-page' });
  const render = () => {
    root.replaceChildren(createRuntimeShell(render, () => refreshRuntimeData(render)));
  };
  render();
  void refreshRuntimeData(render);
  return root;
}

async function refreshRuntimeData(render: () => void): Promise<void> {
  const currentVersion = ++loadVersion;
  state.loading = true;
  state.loadError = undefined;
  render();
  try {
    const data = await loadRuntimeLiveData(activeSystemId(), state.activeModuleId, {
      pageNo: state.pageNo,
      pageSize: state.pageSize,
      keyword: state.keyword,
      fieldFilters: Object.entries(state.fieldFilters)
        .filter(([, value]) => value.trim())
        .map(([fieldCode, value]) => ({ fieldCode, operator: 'LIKE', value })),
      sorts: state.sorts,
    });
    if (currentVersion !== loadVersion) {
      return;
    }
    liveData = data;
    state.realEmpty = data.modules.length === 0;
    state.activeModuleId = data.activeModule.moduleId;
    state.activeRecordId = data.rows.some((row) => row.recordId === state.activeRecordId)
      ? state.activeRecordId
      : data.rows[0]?.recordId ?? '';
    state.selectedIds = new Set(Array.from(state.selectedIds).filter((id) => data.rows.some((row) => row.recordId === id)));
  } catch (error) {
    if (currentVersion === loadVersion) {
      liveData = undefined;
      state.realEmpty = true;
      state.activeRecordId = '';
      state.selectedIds.clear();
      state.loadError = error instanceof Error ? error.message : '真实业务数据加载失败。';
    }
  } finally {
    if (currentVersion === loadVersion) {
      state.loading = false;
      render();
    }
  }
}

function createRuntimeShell(render: () => void, reload: () => void): HTMLElement {
  const selectedRows = currentRows().filter((row) => state.selectedIds.has(row.recordId));
  if (state.realEmpty && !state.loading) {
    return createRuntimeEmptyState();
  }
  return createElement(
    'div',
    { className: 'runtime-shell' },
    createModuleSidebar(reload),
    createElement(
      'section',
      { className: 'runtime-main' },
      createRuntimeHeader(render),
      createRuntimeEfficiencyStrip(render),
      createLoadStatePanel(),
      createFilterArea(render),
      createBatchBar(selectedRows, render),
      createRecordTable(render),
    ),
    createRuntimePanel(selectedRows, render),
  );
}

function createRuntimeEmptyState(): HTMLElement {
  const adminLink = createElement('a', { className: 'button primary' }, '去系统后台配置模块');
  adminLink.href = `#/systems/${activeSystemId()}/admin`;
  return createElement(
    'section',
    { className: 'runtime-empty-state' },
    createElement('div', { className: 'page-heading' },
      createElement('h1', {}, '暂无可用业务模块'),
      createElement('p', {}, state.loadError ?? '当前系统还没有可访问的已发布模块。先完成模块、字段、列表和发布检查，再回到业务页处理数据。'),
    ),
    createElement(
      'section',
      { className: 'runtime-card' },
      createElement('strong', {}, '下一步'),
      createElement('p', {}, '系统管理员进入后台配置并发布模块后，这里才显示搜索、导入导出、批量操作和右侧详情。'),
      canOpenSystemAdmin() ? adminLink : createElement('p', {}, '当前账号没有系统后台入口，请联系系统管理员发布业务模块。'),
    ),
  );
}

function createModuleSidebar(reload: () => void): HTMLElement {
  return createElement(
    'aside',
    { className: 'runtime-module-sidebar' },
    createElement('div', { className: 'sidebar-title' }, currentModuleGroupTitle()),
    ...currentModules().map((module) => createModuleButton(module, reload)),
  );
}

function createModuleButton(module: RuntimeModuleItem, reload: () => void): HTMLElement {
  const button = createElement(
    'button',
    {
      className: `runtime-module-item${module.active ? ' active' : ''}`,
      ariaLabel: module.disabledReason ?? module.name,
    },
    createElement('span', {}, module.name),
    createElement('small', {}, module.disabledReason ?? `${module.count} 条 / ${module.publishVersion}`),
  );
  button.addEventListener('click', () => {
    if (module.disabledReason) {
      return;
    }
    state.activeModuleId = module.moduleId;
    state.activePanel = 'detail';
    state.pageNo = 1;
    state.selectedIds.clear();
    reload();
  });
  return button;
}

function createRuntimeHeader(render: () => void): HTMLElement {
  const moduleName = liveData?.activeModule.name ?? '业务模块';
  const createButtonAction = createButton('新建记录', 'primary', state.realEmpty, state.realEmpty ? '请先配置并发布业务模块。' : undefined);
  createButtonAction.addEventListener('click', () => {
    state.activePanel = 'create';
    render();
  });
  const importButton = createButton('导入', 'secondary', state.realEmpty);
  importButton.dataset.runtimeImportOpenR89 = 'true';
  importButton.addEventListener('click', () => {
    state.activePanel = 'import';
    render();
  });
  const exportButton = createButton('全部导出', 'secondary', state.realEmpty);
  exportButton.dataset.runtimeExportOpenR89 = 'true';
  exportButton.addEventListener('click', () => {
    state.activePanel = 'export';
    render();
  });
  const columnButton = createButton('列设置', 'ghost', state.realEmpty);
  columnButton.addEventListener('click', () => {
    state.activePanel = 'columns';
    render();
  });

  return createElement(
    'header',
    { className: 'runtime-page-head' },
    createElement(
      'div',
      {},
      createElement('p', { className: 'eyebrow' }, currentModuleGroupTitle()),
      createElement('h1', {}, moduleName),
      createElement('p', {}, '列表行点击打开右侧详情，操作列只保留编辑、删除、打印等差异动作。'),
    ),
    createElement('div', { className: 'inline-actions' }, createButtonAction, importButton, exportButton, columnButton),
  );
}

function createFilterArea(render: () => void): HTMLElement {
  const advancedButton = createButton(state.advancedOpen ? '收起高级筛选' : '高级筛选', 'ghost', state.realEmpty);
  advancedButton.addEventListener('click', () => {
    state.advancedOpen = !state.advancedOpen;
    render();
  });
  const searchButton = createButton('快速搜索', 'primary', state.realEmpty);
  searchButton.addEventListener('click', () => {
    applyRuntimeFilters();
    state.pageNo = 1;
    void refreshRuntimeData(render);
  });
  const resetButton = createButton('重置筛选', 'ghost', state.realEmpty);
  resetButton.addEventListener('click', () => {
    state.keyword = '';
    state.fieldFilters = {};
    state.sorts = [];
    state.pageNo = 1;
    void refreshRuntimeData(render);
  });
  const saveSceneButton = createButton('保存为场景', 'secondary', state.realEmpty);
  saveSceneButton.addEventListener('click', async () => {
    applyRuntimeFilters();
    await saveCurrentScene(render, saveSceneButton);
  });
  const quickFilters = currentSchema().filters.slice(0, 3);
  const advancedFilters = currentSchema().filters.slice(3);

  return createElement(
    'section',
    { className: 'runtime-filter-panel' },
    createElement(
      'div',
      { className: 'runtime-filter-row' },
      createFilterInput('搜索当前模块数据', 'keyword'),
      ...quickFilters.map((filter) => createFilterInput(filter.fieldCode, filter.fieldCode)),
      searchButton,
      advancedButton,
    ),
    state.advancedOpen
      ? createElement(
          'div',
          { className: 'runtime-advanced-filter' },
          ...(advancedFilters.length > 0 ? advancedFilters : currentSchema().filters).map((filter) => createFilterInput(filter.fieldCode, filter.fieldCode)),
          saveSceneButton,
          resetButton,
        )
      : null,
    createElement('p', { className: 'runtime-filter-meta' }, `权限快照：${currentSchema().permissionSnapshotId || '-'}`),
  );
}

function createBatchBar(selectedRows: RuntimeRecordRow[], render: () => void): HTMLElement {
  const deleteReason = disabledReasonFor(selectedRows, 'delete');
  const deleteButton = createButton('删除', 'secondary', selectedRows.length <= 0 || Boolean(deleteReason), selectedRows.length <= 0 ? '请先勾选记录。' : deleteReason);
  deleteButton.addEventListener('click', async () => {
    if (selectedRows.length <= 0 || deleteReason || !liveData) {
      return;
    }
    await Promise.all(selectedRows.map((row) => deleteRuntimeRecord(activeSystemId(), liveData!.activeModule.moduleId, row.recordId)));
    state.selectedIds.clear();
    state.actionMessage = `已删除 ${selectedRows.length} 条记录。`;
    await refreshRuntimeData(render);
  });
  const exportSelected = createButton('导出选中', 'secondary', selectedRows.length <= 0, '请先勾选记录。');
  exportSelected.dataset.runtimeExportSelectedOpenR89 = 'true';
  exportSelected.addEventListener('click', () => {
    state.activePanel = 'export';
    render();
  });

  return createElement(
    'section',
    { className: 'runtime-batch-bar' },
    createElement('strong', {}, `已选 ${selectedRows.length} 条`),
    deleteButton,
    exportSelected,
    createElement('span', {}, batchReason(selectedRows, deleteReason)),
    state.actionMessage ? createElement('span', {}, state.actionMessage) : null,
  );
}

function createRecordTable(render: () => void): HTMLElement {
  const rows = currentRows();
  const schema = currentSchema();
  const columns = schema.columns;
  if (rows.length === 0) {
    return createElement(
      'section',
      { className: 'runtime-card' },
      createElement('strong', {}, schema.emptyState.title),
      createElement('p', {}, state.realEmpty ? '请先进入系统后台配置并发布模块。' : '当前筛选条件下暂无记录。'),
    );
  }
  return createElement(
    'div',
    { className: 'runtime-table-shell' },
    createElement(
      'table',
      { className: 'data-table runtime-table' },
      createElement(
        'thead',
        {},
        createElement(
          'tr',
          {},
          createElement('th', { className: 'select-col' }, ''),
          createElement('th', { className: 'index-col' }, '序号'),
          ...columns.map((column) => createSortableHeader(column, render)),
          createElement('th', {}, '操作'),
        ),
      ),
      createElement('tbody', {}, ...rows.map((row) => createRecordRow(row, columns, render))),
    ),
    createElement(
      'footer',
      { className: 'pagination' },
      createElement('span', {}, `第 ${state.pageNo} 页，每页 ${state.pageSize} 条，共 ${currentTotal()} 条`),
      createPageButton('上一页', state.pageNo <= 1, () => {
        state.pageNo -= 1;
        void refreshRuntimeData(render);
      }),
      createPageButton('下一页', state.pageNo * state.pageSize >= currentTotal(), () => {
        state.pageNo += 1;
        void refreshRuntimeData(render);
      }),
    ),
  );
}

function createRecordRow(row: RuntimeRecordRow, columns: DynamicColumn[], render: () => void): HTMLElement {
  const tr = createElement('tr', {
    className: `clickable-row${state.activeRecordId === row.recordId ? ' selected-row' : ''}`,
    dataset: { rowClickTarget: currentSchema().rowClickTarget, runtimeRecordRow: row.recordId },
  });
  tr.addEventListener('click', () => {
    state.activeRecordId = row.recordId;
    saveRuntimeRecentEntry(runtimeRecentFromRow(row));
    state.activePanel = 'detail';
    render();
  });

  const checkbox = createElement('input', { ariaLabel: `选择 ${row.title}` });
  checkbox.type = 'checkbox';
  checkbox.checked = state.selectedIds.has(row.recordId);
  checkbox.addEventListener('click', (event) => {
    event.stopPropagation();
    if (checkbox.checked) {
      state.selectedIds.add(row.recordId);
    } else {
      state.selectedIds.delete(row.recordId);
    }
    render();
  });

  tr.append(
    createElement('td', { className: 'select-col' }, checkbox),
    createElement('td', { className: 'index-col' }, row.serialNo),
    ...columns.map((column, index) =>
      createElement(
        'td',
        {},
        index === 0
          ? createElement('span', { className: 'link-like' }, displayCell(row, column.fieldCode))
          : displayCell(row, column.fieldCode),
      ),
    ),
    createElement('td', { className: 'row-actions' }, ...row.actions.map((action) => createRowActionButton(action, row, render))),
  );

  return tr;
}

function createRowActionButton(action: ActionContract, row: RuntimeRecordRow, render: () => void): HTMLButtonElement {
  const button = createButton(action.name, 'ghost', !action.enabled, action.disabledReason);
  button.addEventListener('click', async (event) => {
    event.stopPropagation();
    state.activeRecordId = row.recordId;
    if (action.actionCode === 'record.edit') {
      state.activePanel = 'edit';
      render();
      return;
    }
    if (action.enabled && action.actionCode === 'record.delete') {
      try {
        await deleteRuntimeRecord(activeSystemId(), activeModuleId(), row.recordId);
        state.actionMessage = `记录 ${row.title} 已删除。`;
        state.activeRecordId = '';
        state.activePanel = 'detail';
        await refreshRuntimeData(render);
        return;
      } catch (error) {
        state.actionMessage = error instanceof Error ? error.message : '删除记录失败。';
      }
    }
    if (action.enabled && action.actionCode === 'record.submitApproval') {
      try {
        const result = await executeRuntimeRecordAction(activeSystemId(), activeModuleId(), row.recordId, action.actionCode, {
          reason: '前端行操作提交审批',
        });
        state.actionMessage = result.accepted ? `审批已提交：${result.result}` : result.disabledReason || '审批提交未被接受。';
        state.activePanel = 'detail';
        await refreshRuntimeData(render);
        return;
      } catch (error) {
        state.actionMessage = error instanceof Error ? error.message : '提交审批失败。';
      }
    }
    state.activePanel = 'detail';
    render();
  });
  return button;
}

function createRuntimePanel(selectedRows: RuntimeRecordRow[], render: () => void): HTMLElement {
  const closePanel = () => {
    state.activePanel = 'detail';
    render();
  };
  if (state.activePanel === 'import') {
    return createImportPanel(
      {
        precheck: state.importPrecheck,
        confirm: state.importConfirm,
        message: state.importMessage,
        loading: state.importLoading,
      },
      {
        onPrecheck: async (input) => {
          if (!input.file && !input.fileId) {
            state.importMessage = '请先选择导入文件，或填写已有上传文件 fileId。';
            render();
            return;
          }
          state.importLoading = true;
          state.importMessage = undefined;
          render();
          try {
            const uploadResult = input.file ? await uploadRuntimeImportFile(input.file) : undefined;
            const fileId = uploadResult?.file.fileId ?? input.fileId ?? '';
            state.importPrecheck = await precheckRuntimeImport(activeSystemId(), activeModuleId(), {
              fileId,
              templateCode: input.templateCode,
              duplicateStrategy: input.duplicateStrategy,
            });
            const fileName = uploadResult?.file.fileName ? `文件 ${uploadResult.file.fileName} ` : '';
            state.importMessage = state.importPrecheck.passed ? `${fileName}预检通过，可以确认导入。` : `${fileName}预检完成，请处理失败行后再确认导入。`;
          } catch (error) {
            state.importMessage = error instanceof Error ? error.message : '导入预检失败。';
          } finally {
            state.importLoading = false;
            render();
          }
        },
        onConfirm: async (precheckId, duplicateStrategy) => {
          state.importLoading = true;
          state.importMessage = undefined;
          render();
          try {
            state.importConfirm = await confirmRuntimeImport(activeSystemId(), activeModuleId(), { precheckId, duplicateStrategy });
            state.importMessage = `导入任务已创建：${state.importConfirm.task.taskId}`;
            await refreshRuntimeData(render);
          } catch (error) {
            state.importMessage = error instanceof Error ? error.message : '确认导入失败。';
          } finally {
            state.importLoading = false;
            render();
          }
        },
        onClose: closePanel,
      },
    );
  }
  if (state.activePanel === 'export') {
    return createExportPanel(
      {
        result: state.exportResult,
        message: state.exportMessage,
        loading: state.exportLoading,
      },
      selectedRows.length,
      currentTotal(),
      {
        onExportAll: () => runExport('ALL_MATCHED', selectedRows, render),
        onExportSelected: () => runExport('SELECTED', selectedRows, render),
        onExportTemplate: () => runExport('TEMPLATE_ONLY', selectedRows, render),
        onClose: closePanel,
      },
    );
  }
  if (state.activePanel === 'create' || state.activePanel === 'edit') {
    return createEditPanel(state.activePanel, closePanel, render);
  }
  if (state.activePanel === 'columns') {
    return createColumnPanel(closePanel, render);
  }
  const row = activeRow();
  if (!row) {
    return createElement(
      'aside',
      { className: 'runtime-side-panel detail-panel' },
      createElement('header', { className: 'detail-head' }, createElement('div', {}, createElement('span', { className: 'eyebrow' }, 'Runtime'), createElement('h2', {}, '暂无业务数据'))),
      createElement('section', { className: 'runtime-card' }, state.realEmpty ? '当前系统还没有可访问的已发布模块。' : '当前筛选条件下暂无记录。'),
    );
  }
  return createDetailPanel(row, render);
}

function createDetailPanel(row: RuntimeRecordRow, render: () => void): HTMLElement {
  return createElement(
    'aside',
    { className: 'runtime-side-panel detail-panel' },
    createElement(
      'header',
      { className: 'detail-head' },
      createElement('div', {}, createElement('span', { className: 'eyebrow' }, '业务详情'), createElement('h2', {}, row.title)),
      createElement(
        'div',
        { className: 'inline-actions' },
        createPanelAction('编辑', !row.editDisabledReason, row.editDisabledReason, () => {
          state.activePanel = 'edit';
          render();
        }),
        createPanelAction('删除', !row.deleteDisabledReason, row.deleteDisabledReason, async () => {
          await deleteRuntimeRecord(activeSystemId(), activeModuleId(), row.recordId);
          state.actionMessage = `记录 ${row.title} 已删除。`;
          await refreshRuntimeData(render);
        }),
        createPanelAction('打印', true, undefined, () => window.print()),
      ),
    ),
    createSummaryGrid(row),
    createDetailTabs(render),
    createDetailTabBody(row.detail),
    createApprovalSidebar(row, render),
    createTraceLine(row.traceId, row.auditLogId),
  );
}

function createEditPanel(panel: 'create' | 'edit', onClose: () => void, render: () => void): HTMLElement {
  const active = activeRow();
  const title = panel === 'create' ? `新建${liveData?.activeModule.name ?? '记录'}` : `编辑 ${active?.title ?? '记录'}`;
  const saveDraftButton = createButton('保存草稿', 'secondary', false);
  const saveButton = createButton(panel === 'create' ? '保存记录' : '保存修改', 'primary', false);
  const submitApprovalButton = createButton('提交审批', 'secondary', false);
  saveDraftButton.dataset.runtimeSaveDraftR88 = 'true';
  saveButton.dataset.runtimeSaveRecordR88 = 'true';
  submitApprovalButton.dataset.runtimeSubmitApprovalR88 = 'true';
  saveDraftButton.addEventListener('click', async () => {
    const fieldValues = collectFormValues();
    try {
      const draft = await saveRuntimeDraft(activeSystemId(), activeModuleId(), {
        recordId: panel === 'edit' ? active?.recordId : undefined,
        draftId: state.draftId,
        fieldValues,
        attachmentIds: collectAttachmentIds(),
      });
      state.draftId = draft.draftId;
      state.restoredDraftValues = fieldValues;      saveRuntimeDraftEntry({
        systemId: activeSystemId(),
        moduleId: activeModuleId(),
        moduleName: liveData?.activeModule.name ?? activeModuleId(),
        draftId: draft.draftId,
        recordId: panel === 'edit' ? active?.recordId : undefined,
        title: active?.title ?? `${liveData?.activeModule.name ?? 'record'} draft`,
        fieldValues,
        updatedAt: new Date().toISOString(),
      });
      state.actionMessage = `草稿已保存：${draft.draftId}`;
      render();
    } catch (error) {
      state.actionMessage = error instanceof Error ? error.message : '保存草稿失败。';
      render();
    }
  });
  saveButton.addEventListener('click', async () => {
    try {
      await saveCurrentRecord(panel, active, false);
      state.activePanel = 'detail';
      await refreshRuntimeData(render);
    } catch (error) {
      state.actionMessage = error instanceof Error ? error.message : '保存记录失败。';
      render();
    }
  });
  submitApprovalButton.addEventListener('click', async () => {
    try {
      await saveCurrentRecord(panel, active, true);
      state.activePanel = 'detail';
      await refreshRuntimeData(render);
    } catch (error) {
      state.actionMessage = error instanceof Error ? error.message : '提交审批失败。';
      render();
    }
  });
  return createElement(
    'aside',
    { className: 'runtime-side-panel edit-panel' },
    createElement(
      'header',
      { className: 'runtime-panel-head' },
      createElement('div', {}, createElement('span', { className: 'eyebrow' }, 'Record Form'), createElement('h2', {}, title)),
      createPanelAction('收起', true, undefined, onClose),
    ),
    createElement(
      'section',
      { className: 'runtime-card form-grid' },
      ...currentFields().map((field) => createFormField(field, panel === 'edit' ? active?.fields[field.fieldCode] : state.restoredDraftValues?.[field.fieldCode])),
      createElement('label', {}, createElement('span', {}, '附件'), createElement('input', { ariaLabel: '附件' })),
    ),
    createElement(
      'section',
      { className: 'runtime-card', dataset: { runtimeFormResultR88: 'true', runtimeDraftId: state.draftId ?? '', runtimeValidationField: state.validationFieldCode ?? '' } },
      createElement('h3', {}, '提交结果'),
      createElement('p', {}, state.actionMessage ?? '保存后返回字段错误、traceId、auditLogId；需要审批时生成流程实例并在详情右侧审批栏展示。'),
      state.draftId ? createElement('p', {}, `当前草稿：${state.draftId}`) : null,
    ),
    createElement('div', { className: 'inline-actions' },
      saveDraftButton,
      saveButton,
      submitApprovalButton,
      createPanelAction('取消', true, undefined, onClose),
    ),
  );
}

function createColumnPanel(onClose: () => void, render: () => void): HTMLElement {
  const saveButton = createButton('保存列设置', 'primary', false);
  const resetButton = createButton('恢复页面默认', 'ghost', false);
  saveButton.addEventListener('click', async () => {
    await saveCurrentScene(render, saveButton, selectedColumnFieldIds());
  });
  resetButton.addEventListener('click', () => {
    state.keyword = '';
    state.fieldFilters = {};
    state.sorts = [];
    onClose();
    void refreshRuntimeData(render);
  });
  return createElement(
    'aside',
    { className: 'runtime-side-panel' },
    createElement(
      'header',
      { className: 'runtime-panel-head' },
      createElement('div', {}, createElement('span', { className: 'eyebrow' }, 'Column Setting'), createElement('h2', {}, '列设置')),
      createPanelAction('收起', true, undefined, onClose),
    ),
    createElement(
      'section',
      { className: 'runtime-card column-list' },
      ...currentSchema().columns.map((column) => createColumnToggle(column)),
    ),
    createElement('div', { className: 'inline-actions' },
      saveButton,
      resetButton,
    ),
  );
}

function createSummaryGrid(row: RuntimeRecordRow): HTMLElement {
  const entries = Object.entries(row.detail.summary).slice(0, 4);
  return createElement(
    'section',
    { className: 'detail-summary-grid' },
    ...entries.map(([label, value]) => summaryItem(label, String(value ?? '-'))),
  );
}

function createDetailTabs(render: () => void): HTMLElement {
  const tabs: Array<[DetailTab, string]> = [
    ['base', '详细资料'],
    ['children', '关联数据'],
    ['attachments', '附件'],
    ['print', '打印记录'],
    ['logs', '操作记录'],
  ];
  return createElement(
    'nav',
    { className: 'detail-tabs' },
    ...tabs.map(([tab, label]) => {
      const button = createElement('button', { className: state.activeTab === tab ? 'active' : '' }, label);
      button.addEventListener('click', () => {
        state.activeTab = tab;
        render();
      });
      return button;
    }),
  );
}

function createDetailTabBody(detail: BusinessDetailView): HTMLElement {
  if (state.activeTab === 'children') {
    return createChildrenTables(detail.childRows);
  }
  if (state.activeTab === 'attachments') {
    return createElement(
      'section',
      { className: 'runtime-card simple-stack' },
      ...detail.attachments.map((file) =>
        createElement('div', { className: 'list-line' }, file.fileName, file.downloadUrl ? createDownloadLink(file.fileName, file.downloadUrl) : createButton('下载', 'ghost', true, '后端未返回附件下载地址。')),
      ),
    );
  }
  if (state.activeTab === 'print') {
    return createOperationList(detail.printRecords);
  }
  if (state.activeTab === 'logs') {
    return createOperationList(detail.operationLogs);
  }
  return createElement(
    'section',
    { className: 'runtime-card' },
    createElement('h3', {}, '基本信息'),
    createKeyValueGrid(Object.entries(detail.baseFields).map(([key, value]) => [key, String(value)])),
  );
}

function createApprovalSidebar(row: RuntimeRecordRow, render: () => void): HTMLElement {
  const detail = row.detail;
  const approval = detail.approvalSidebar;
  if (!approval) {
    return createElement('section', { className: 'runtime-card' }, createElement('h3', {}, '审批流程'), createElement('p', {}, '当前记录未发起审批。'));
  }
  return createElement(
    'section',
    { className: 'runtime-card approval-card' },
    createElement('div', { className: 'runtime-card-head' }, createElement('h3', {}, '审批流程信息'), createElement('span', {}, approval.currentNodeName)),
    createElement(
      'ol',
      { className: 'approval-timeline' },
      ...approval.timeline.map((item) =>
        createElement(
          'li',
          {},
          createElement('strong', {}, item.nodeName),
          createElement('span', {}, `${item.operator ?? '-'} / ${item.result ?? '-'} / ${item.operatedAt ?? '-'}`),
        ),
      ),
    ),
    createElement('div', { className: 'inline-actions' }, ...approval.availableActions.map((action) => createRuntimeActionButton(action, row.recordId, render))),
  );
}

function createChildrenTables(childRows: Record<string, unknown[]>): HTMLElement {
  const entries = Object.entries(childRows);
  if (entries.length === 0) {
    return createElement('section', { className: 'runtime-card' }, '暂无关联数据');
  }
  return createElement(
    'section',
    { className: 'runtime-card simple-stack' },
    ...entries.map(([name, rows]) => createElement('div', {}, createElement('h3', {}, name), createSimpleTable(asTableRows(rows)))),
  );
}

function createOperationList(records: OperationRecord[]): HTMLElement {
  if (records.length === 0) {
    return createElement('section', { className: 'runtime-card' }, '暂无记录');
  }
  return createElement(
    'section',
    { className: 'runtime-card simple-stack' },
    ...records.map((record) => createElement('div', { className: 'list-line' }, createElement('strong', {}, record.action), createElement('span', {}, `${record.operator} / ${record.operatedAt}`), createElement('code', {}, record.traceId))),
  );
}

function createSimpleTable(rows: Record<string, unknown>[]): HTMLElement {
  if (rows.length === 0) {
    return createElement('section', { className: 'runtime-card' }, '暂无数据');
  }
  const headers = Object.keys(rows[0]);
  return createElement(
    'table',
    { className: 'mini-data-table' },
    createElement('thead', {}, createElement('tr', {}, ...headers.map((header) => createElement('th', {}, header)))),
    createElement('tbody', {}, ...rows.map((row) => createElement('tr', {}, ...headers.map((header) => createElement('td', {}, String(row[header] ?? '')))))),
  );
}

function asTableRows(rows: unknown[] | undefined): Record<string, unknown>[] {
  return (rows ?? []).filter((row): row is Record<string, unknown> => Boolean(row) && typeof row === 'object' && !Array.isArray(row));
}

function createPanelAction(label: string, enabled: boolean, disabledReason: string | undefined, onClick: () => void | Promise<void>): HTMLButtonElement {
  const button = createButton(label, enabled ? 'secondary' : 'ghost', !enabled, disabledReason);
  button.addEventListener('click', () => {
    void onClick();
  });
  return button;
}

function createSortableHeader(column: DynamicColumn, render: () => void): HTMLElement {
  const activeSort = state.sorts.find((sort) => sort.fieldCode === column.fieldCode);
  const label = activeSort ? `${column.title} ${activeSort.direction === 'ASC' ? '↑' : '↓'}` : column.sortable ? `${column.title} ↕` : column.title;
  const th = createElement('th', { dataset: { sortable: column.sortable ? 'true' : 'false' } }, label);
  if (column.sortable) {
    th.addEventListener('click', () => {
      state.sorts = [{ fieldCode: column.fieldCode, direction: activeSort?.direction === 'ASC' ? 'DESC' : 'ASC' }];
      state.pageNo = 1;
      void refreshRuntimeData(render);
    });
  }
  return th;
}

function createFilterInput(label: string, fieldCode: string): HTMLElement {
  const input = createElement('input', { ariaLabel: label, dataset: { filterField: fieldCode } });
  input.placeholder = label;
  input.value = fieldCode === 'keyword' ? state.keyword : state.fieldFilters[fieldCode] ?? '';
  return createElement('label', {}, createElement('span', {}, label), input);
}

function createRuntimeEfficiencyStrip(render: () => void): HTMLElement {
  const systemId = activeSystemId();
  const moduleId = activeModuleId();
  const recent = readRuntimeRecentEntries(systemId).filter((entry) => entry.moduleId === moduleId).slice(0, 3);
  const drafts = readRuntimeDraftEntries(systemId).filter((entry) => entry.moduleId === moduleId).slice(0, 3);
  const searchInput = createElement('input', { ariaLabel: '搜索当前模块', dataset: { runtimeEfficiencySearchInput: 'true' } });
  searchInput.placeholder = '搜索当前模块数据';
  searchInput.value = state.keyword;
  const searchButton = createButton('搜索', 'primary', state.realEmpty);
  searchButton.dataset.runtimeEfficiencySearchAction = 'true';
  searchButton.addEventListener('click', () => {
    state.keyword = searchInput.value.trim();
    state.pageNo = 1;
    state.validationFieldCode = undefined;
    void refreshRuntimeData(render);
  });
  const createButtonAction = createButton('快捷新建', 'secondary', state.realEmpty);
  createButtonAction.dataset.runtimeEfficiencyQuickCreate = moduleId;
  createButtonAction.addEventListener('click', () => {
    state.activePanel = 'create';
    state.restoredDraftValues = undefined;
    state.validationFieldCode = undefined;
    render();
  });

  return createElement(
    'section',
    {
      className: 'runtime-efficiency-strip',
      dataset: {
        runtimeEfficiencyR88: 'true',
        runtimeEfficiencyModuleId: moduleId,
        runtimeEfficiencyRecentCount: String(recent.length),
        runtimeEfficiencyDraftCount: String(drafts.length),
        runtimeEfficiencyMarker: R88_RUNTIME_EFFICIENCY_MARKER,
      },
    },
    createElement('div', { className: 'runtime-efficiency-search' }, searchInput, searchButton, createButtonAction),
    createElement(
      'div',
      { className: 'runtime-efficiency-list', dataset: { runtimeEfficiencyRecentList: recent.length ? 'populated' : 'empty' } },
      createElement('strong', {}, '最近打开'),
      recent.length === 0
        ? createElement('span', {}, '当前模块暂无最近记录')
        : createElement('div', { className: 'simple-stack' }, ...recent.map((entry) => createEfficiencyEntryButton(entry.title, entry.updatedAt, () => {
            state.activeModuleId = entry.moduleId;
            state.activeRecordId = entry.recordId ?? '';
            state.activePanel = entry.recordId ? 'detail' : 'create';
            render();
          }, { runtimeEfficiencyRecentItem: entry.recordId ?? entry.moduleId }))),
    ),
    createElement(
      'div',
      { className: 'runtime-efficiency-list', dataset: { runtimeEfficiencyDraftList: drafts.length ? 'populated' : 'empty' } },
      createElement('strong', {}, '草稿'),
      drafts.length === 0
        ? createElement('span', {}, '保存草稿后可从这里继续')
        : createElement('div', { className: 'simple-stack' }, ...drafts.map((draft) => createEfficiencyEntryButton(draft.title, draft.draftId, () => {
            state.activeModuleId = draft.moduleId;
            state.draftId = draft.draftId;
            state.restoredDraftValues = draft.fieldValues;
            state.activeRecordId = draft.recordId ?? '';
            state.activePanel = 'create';
            state.actionMessage = `已恢复草稿：${draft.draftId}`;
            state.validationFieldCode = undefined;
            render();
          }, { runtimeEfficiencyDraftItem: draft.draftId }))),
    ),
  );
}

function createEfficiencyEntryButton(title: string, meta: string, onClick: () => void, dataset: Record<string, string>): HTMLButtonElement {
  const button = createElement('button', { className: 'list-line clickable-row', dataset }, createElement('span', {}, title), createElement('small', {}, meta));
  button.addEventListener('click', onClick);
  return button;
}

function createFormField(field: FieldDefinitionVO, value: unknown): HTMLElement {
  const isErrorField = state.validationFieldCode === field.fieldCode;
  const input = createElement('input', {
    ariaLabel: field.name,
    dataset: {
      fieldCode: field.fieldCode,
      runtimeFormFieldR88: field.fieldCode,
      runtimeFieldErrorR88: String(isErrorField),
    },
  });
  input.value = value === null || value === undefined ? '' : String(value);
  if (isErrorField) {
    input.classList.add('field-error');
  }
  return createElement(
    'label',
    { className: isErrorField ? 'field-error-label' : undefined, dataset: { runtimeValidationFieldR88: isErrorField ? field.fieldCode : '' } },
    createElement('span', {}, `${field.name}${field.required ? ' *' : ''}`),
    input,
  );
}

function createColumnToggle(column: DynamicColumn): HTMLElement {
  const input = createElement('input', {
    ariaLabel: column.title,
    dataset: { columnFieldId: column.fieldId },
  });
  input.type = 'checkbox';
  input.checked = true;
  return createElement(
    'label',
    {},
    input,
    createElement('span', {}, `${column.title}${column.masked ? ' / 脱敏' : ''}${column.sortable ? ' / 可排序' : ''}`),
  );
}

function applyRuntimeFilters(): void {
  const inputs = document.querySelectorAll<HTMLInputElement>('.runtime-filter-panel input[data-filter-field]');
  const fieldFilters: Record<string, string> = {};
  inputs.forEach((input) => {
    const fieldCode = input.dataset.filterField;
    if (!fieldCode) {
      return;
    }
    if (fieldCode === 'keyword') {
      state.keyword = input.value.trim();
    } else if (input.value.trim()) {
      fieldFilters[fieldCode] = input.value.trim();
    }
  });
  state.fieldFilters = fieldFilters;
}

function selectedColumnFieldIds(): string[] {
  return Array.from(document.querySelectorAll<HTMLInputElement>('.column-list input[data-column-field-id]'))
    .filter((input) => input.checked)
    .map((input) => input.dataset.columnFieldId)
    .filter((fieldId): fieldId is string => Boolean(fieldId));
}

function collectFormValues(): Record<string, unknown> {
  const values: Record<string, unknown> = {};
  document.querySelectorAll<HTMLInputElement>('.edit-panel input[data-field-code]').forEach((input) => {
    const fieldCode = input.dataset.fieldCode;
    if (fieldCode) {
      values[fieldCode] = input.value;
    }
  });
  return values;
}

function collectAttachmentIds(): string[] {
  const input = document.querySelector<HTMLInputElement>('.edit-panel label:last-child input');
  return (input?.value ?? '')
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean);
}

async function saveCurrentRecord(panel: 'create' | 'edit', active: RuntimeRecordRow | undefined, submitApproval: boolean): Promise<void> {
  const fieldValues = collectFormValues();
  const fields = currentFields();
  const missingField = fields.find((field) => field.required && !String(fieldValues[field.fieldCode] ?? '').trim())
    ?? (fields.length > 0 && fields.every((field) => !String(fieldValues[field.fieldCode] ?? '').trim()) ? fields[0] : undefined);
  if (missingField) {
    state.validationFieldCode = missingField.fieldCode;
    state.actionMessage = `请补充必填字段：${missingField.name}`;
    throw new Error(state.actionMessage);
  }
  state.validationFieldCode = undefined;
  const mutation = await saveRuntimeRecord(activeSystemId(), activeModuleId(), {
    recordId: panel === 'edit' ? active?.recordId : undefined,
    draftId: state.draftId,
    fieldValues,
    attachmentIds: collectAttachmentIds(),
  });
  state.draftId = undefined;
  state.restoredDraftValues = undefined;
  state.actionMessage = `记录已保存：${mutation.recordId}`;
  saveRuntimeRecentEntry({
    systemId: activeSystemId(),
    moduleId: activeModuleId(),
    moduleName: liveData?.activeModule.name ?? activeModuleId(),
    recordId: mutation.recordId,
    title: active?.title ?? `${liveData?.activeModule.name ?? 'record'} ${mutation.recordId}`,
    updatedAt: new Date().toISOString(),
  });
  if (submitApproval) {
    const action = await executeRuntimeRecordAction(activeSystemId(), activeModuleId(), mutation.recordId, 'record.submitApproval', {
      reason: '前端提交审批',
    });
    state.actionMessage = action.accepted ? `审批已提交：${action.result}` : action.disabledReason || '审批提交未被接受。';
  }
}

async function runExport(scope: 'ALL_MATCHED' | 'SELECTED' | 'TEMPLATE_ONLY', selectedRows: RuntimeRecordRow[], render: () => void): Promise<void> {
  if (scope === 'SELECTED' && selectedRows.length <= 0) {
    state.exportMessage = '请先勾选要导出的记录，或改用导出当前筛选。';
    render();
    return;
  }
  state.exportLoading = true;
  state.exportMessage = undefined;
  render();
  try {
    const fields = currentSchema().columns.map((column) => column.fieldCode);
    const filters = {
      keyword: state.keyword,
      fieldFilters: state.fieldFilters,
      sorts: state.sorts,
    };
    state.exportResult = await createRuntimeExport(activeSystemId(), activeModuleId(), {
      scope,
      selectedRecordIds: scope === 'SELECTED' ? selectedRows.map((row) => row.recordId) : [],
      fields,
      filters,
    });
    state.exportMessage = `导出任务已创建：${state.exportResult.task.taskId}`;
  } catch (error) {
    state.exportMessage = error instanceof Error ? error.message : '创建导出任务失败。';
  } finally {
    state.exportLoading = false;
    render();
  }
}

async function saveCurrentScene(render: () => void, button: HTMLButtonElement, columnFieldIds?: string[]): Promise<void> {
  const sceneName = await requestTextInput('保存场景', '场景名称', state.activePanel === 'columns' ? '自定义列设置' : '自定义筛选场景');
  if (!sceneName) {
    return;
  }
  button.disabled = true;
  button.textContent = '保存中...';
  try {
    const schema = currentSchema();
    const filterFieldIds = schema.filters
      .filter((filter) => Boolean(state.fieldFilters[filter.fieldCode]))
      .map((filter) => filter.fieldId);
    const sortFieldIds = state.sorts
      .map((sort) => schema.columns.find((column) => column.fieldCode === sort.fieldCode)?.fieldId)
      .filter((fieldId): fieldId is string => Boolean(fieldId));
    const scene = await saveRuntimeScene(activeSystemId(), activeModuleId(), {
      sceneCode: `scene_${Date.now()}`,
      sceneName,
      defaultScene: false,
      columnFieldIds: columnFieldIds ?? schema.columns.map((column) => column.fieldId),
      filterFieldIds,
      sortFieldIds,
    });
    state.actionMessage = `场景已保存：${scene.sceneName}`;
    state.activePanel = 'detail';
    await refreshRuntimeData(render);
  } catch (error) {
    button.disabled = false;
    button.textContent = '保存失败';
    state.actionMessage = error instanceof Error ? error.message : '保存场景失败。';
    render();
  }
}

function createRuntimeActionButton(action: ActionContract, recordId: string, render: () => void): HTMLButtonElement {
  const button = createButton(action.name, action.enabled ? 'secondary' : 'ghost', !action.enabled, action.disabledReason);
  button.addEventListener('click', async () => {
    try {
      const result = await executeRuntimeRecordAction(activeSystemId(), activeModuleId(), recordId, action.actionCode, {
        reason: `${action.name}操作`,
      });
      state.actionMessage = result.accepted ? `${action.name}已执行：${result.result}` : result.disabledReason || `${action.name}未被接受。`;
      await refreshRuntimeData(render);
    } catch (error) {
      state.actionMessage = error instanceof Error ? error.message : `${action.name}执行失败。`;
      render();
    }
  });
  return button;
}

function createPageButton(label: string, disabled: boolean, onClick: () => void): HTMLButtonElement {
  const button = createButton(label, 'ghost', disabled, disabled ? (label === '上一页' ? '已经是第一页。' : '没有更多数据。') : undefined);
  button.addEventListener('click', onClick);
  return button;
}

function createDownloadLink(label: string, href: string): HTMLAnchorElement {
  const link = document.createElement('a');
  link.className = 'button ghost';
  link.href = href;
  link.target = '_blank';
  link.rel = 'noreferrer';
  link.textContent = '下载';
  link.title = label;
  return link;
}

function activeModuleId(): string {
  return liveData?.activeModule.moduleId ?? state.activeModuleId ?? '';
}

function emptyRuntimeSchema(): DynamicListSchema {
  return {
    moduleId: '',
    moduleCode: '',
    sceneId: 'empty',
    columns: [],
    filters: [],
    sorters: [],
    page: { pageNo: state.pageNo, pageSize: state.pageSize, filters: [], sorts: [] },
    rowClickTarget: 'recordDetailDrawer',
    batchActions: [],
    toolbarActions: [],
    importExportConfig: {
      importEnabled: false,
      exportEnabled: false,
      exportAllEnabled: false,
      resultTaskRequired: true,
    },
    emptyState: { title: '暂无业务数据' },
    permissionSnapshotId: '-',
  };
}

function createKeyValueGrid(rows: Array<[string, string]>): HTMLElement {
  return createElement('dl', { className: 'kv-grid' }, ...rows.flatMap(([label, value]) => [createElement('dt', {}, label), createElement('dd', {}, value)]));
}

function summaryItem(label: string, value: string): HTMLElement {
  return createElement('div', {}, createElement('span', {}, label), createElement('strong', {}, value));
}

function disabledReasonFor(rows: RuntimeRecordRow[], actionCode: 'transfer' | 'delete' | 'bulkEdit'): string | undefined {
  if (rows.length === 0) {
    return undefined;
  }
  const row = rows.find((item) => item.disabledReasons?.[actionCode]);
  return row?.disabledReasons?.[actionCode] || undefined;
}

function batchReason(selectedRows: RuntimeRecordRow[], deleteReason?: string): string {
  if (selectedRows.length === 0) {
    return '勾选后启用删除和导出选中。';
  }
  return deleteReason ? `存在受限记录：${deleteReason}` : '当前选择可执行删除和导出。';
}

function createLoadStatePanel(): HTMLElement | null {
  if (state.loading) {
    return createElement('section', { className: 'runtime-card' }, createElement('strong', {}, '正在读取真实业务数据...'));
  }
  if (state.loadError) {
    return state.realEmpty
      ? createElement('section', { className: 'runtime-card' }, createElement('strong', {}, '暂无可访问业务模块'), createElement('p', {}, '请先进入系统后台配置并发布模块，再回到业务页面使用列表、筛选、导入导出和详情。'))
      : createElement('section', { className: 'runtime-card' }, createElement('strong', {}, '真实接口暂不可用'), createElement('p', {}, state.loadError));
  }
  return null;
}

function currentModules(): RuntimeModuleItem[] {
  return liveData?.modules ?? [];
}

function currentRows(): RuntimeRecordRow[] {
  return liveData?.rows ?? [];
}

function currentSchema(): DynamicListSchema {
  return liveData?.schema ?? emptyRuntimeSchema();
}

function currentFields(): FieldDefinitionVO[] {
  return liveData?.fields ?? [];
}

function currentTotal(): number {
  return liveData?.total ?? 0;
}

function currentModuleGroupTitle(): string {
  if (!liveData?.groups.length) {
    return '业务模块';
  }
  const activeGroup = liveData.groups.find((group) => group.groupId === liveData?.activeModule.groupId);
  return activeGroup?.name ?? '业务模块';
}

export function runtimeModulePath(systemId: string, moduleId?: string, options: { mode?: 'create' | 'draft'; draftId?: string; recordId?: string; keyword?: string } = {}): string {
  const params = new URLSearchParams();
  if (moduleId) params.set('moduleId', moduleId);
  if (options.mode) params.set('mode', options.mode);
  if (options.draftId) params.set('draftId', options.draftId);
  if (options.recordId) params.set('recordId', options.recordId);
  if (options.keyword) params.set('keyword', options.keyword);
  const query = params.toString();
  return `/systems/${systemId}/modules${query ? `?${query}` : ''}`;
}

export function readRuntimeRecentEntries(systemId?: string): RuntimeRecentEntry[] {
  return readRuntimeStorage<RuntimeRecentEntry>(RUNTIME_RECENT_STORAGE_KEY)
    .filter((entry) => !systemId || entry.systemId === systemId)
    .sort((left, right) => right.updatedAt.localeCompare(left.updatedAt));
}

export function readRuntimeDraftEntries(systemId?: string): RuntimeDraftEntry[] {
  return readRuntimeStorage<RuntimeDraftEntry>(RUNTIME_DRAFT_STORAGE_KEY)
    .filter((entry) => !systemId || entry.systemId === systemId)
    .sort((left, right) => right.updatedAt.localeCompare(left.updatedAt));
}

function saveRuntimeRecentEntry(entry: RuntimeRecentEntry): void {
  const scoped = readRuntimeStorage<RuntimeRecentEntry>(RUNTIME_RECENT_STORAGE_KEY)
    .filter((item) => !(item.systemId === entry.systemId && item.moduleId === entry.moduleId && (item.recordId ?? '') === (entry.recordId ?? '')));
  writeRuntimeStorage(RUNTIME_RECENT_STORAGE_KEY, [entry, ...scoped].slice(0, 12));
}

function saveRuntimeDraftEntry(entry: RuntimeDraftEntry): void {
  const scoped = readRuntimeStorage<RuntimeDraftEntry>(RUNTIME_DRAFT_STORAGE_KEY)
    .filter((item) => !(item.systemId === entry.systemId && item.moduleId === entry.moduleId && item.draftId === entry.draftId));
  writeRuntimeStorage(RUNTIME_DRAFT_STORAGE_KEY, [entry, ...scoped].slice(0, 12));
}

function readRuntimeStorage<T>(key: string): T[] {
  try {
    const raw = localStorage.getItem(key);
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed.filter((item) => item && typeof item === 'object') as T[] : [];
  } catch {
    return [];
  }
}

function writeRuntimeStorage<T>(key: string, entries: T[]): void {
  localStorage.setItem(key, JSON.stringify(entries));
}

function runtimeRecentFromRow(row: RuntimeRecordRow): RuntimeRecentEntry {
  return {
    systemId: activeSystemId(),
    moduleId: activeModuleId(),
    moduleName: liveData?.activeModule.name ?? activeModuleId(),
    recordId: row.recordId,
    title: row.title,
    updatedAt: new Date().toISOString(),
  };
}

function applyRuntimeRouteIntent(): void {
  const hash = window.location.hash;
  if (appliedRuntimeRouteIntent === hash) {
    return;
  }
  appliedRuntimeRouteIntent = hash;
  const queryIndex = hash.indexOf('?');
  if (queryIndex < 0) {
    return;
  }
  const params = new URLSearchParams(hash.slice(queryIndex + 1));
  const moduleId = params.get('moduleId') ?? undefined;
  const mode = params.get('mode') ?? undefined;
  const draftId = params.get('draftId') ?? undefined;
  const recordId = params.get('recordId') ?? undefined;
  const keyword = params.get('keyword') ?? undefined;
  if (moduleId) {
    state.activeModuleId = moduleId;
  }
  if (keyword) {
    state.keyword = keyword;
    state.pageNo = 1;
  }
  if (recordId) {
    state.activeRecordId = recordId;
    state.activePanel = 'detail';
  }
  if (mode === 'create') {
    state.activePanel = 'create';
    state.restoredDraftValues = undefined;
    state.validationFieldCode = undefined;
  }
  if (mode === 'draft' && draftId) {
    const draft = readRuntimeDraftEntries(activeSystemId()).find((item) => item.draftId === draftId && (!moduleId || item.moduleId === moduleId));
    state.activePanel = 'create';
    state.draftId = draftId;
    state.activeRecordId = draft?.recordId ?? recordId ?? '';
    state.restoredDraftValues = draft?.fieldValues ?? {};
    state.validationFieldCode = undefined;
    state.actionMessage = draft ? `已恢复草稿：${draft.draftId}` : `草稿入口已打开：${draftId}`;
  }
}
function activeSystemId(): string {
  return shellState.currentSystem?.systemId ?? shellState.availableSystems[0]?.systemId ?? '1';
}

function canOpenSystemAdmin(): boolean {
  return shellState.account.systemRoles.includes('SYSTEM_ADMIN') || shellState.account.systemRoles.includes('SYSTEM_SUPER_ADMIN');
}

function activeRow(): RuntimeRecordRow | undefined {
  return currentRows().find((row) => row.recordId === state.activeRecordId) ?? currentRows()[0];
}

function displayCell(row: RuntimeRecordRow, fieldCode: string): string {
  const value = row.fields[fieldCode];
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  if (typeof value === 'object') {
    return JSON.stringify(value);
  }
  return String(value);
}
