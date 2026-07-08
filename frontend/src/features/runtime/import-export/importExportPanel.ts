import type { AsyncTask, FileRef } from '../../../api/types';
import type { RuntimeExportResult, RuntimeImportConfirmResult, RuntimeImportPrecheckResult } from '../../../api/liveData';
import { createButton, createElement, createTraceLine } from '../../../shared/components';
import { renderStatusPill, type StatusTone } from '../../../shared/status';

export interface ImportPanelState {
  precheck?: RuntimeImportPrecheckResult;
  confirm?: RuntimeImportConfirmResult;
  message?: string;
  loading?: boolean;
}

export interface ImportPanelHandlers {
  onPrecheck: (input: { file?: File; fileId?: string; templateCode: string; duplicateStrategy: string }) => Promise<void>;
  onConfirm: (precheckId: string, duplicateStrategy: string) => Promise<void>;
  onClose: () => void;
}

export interface ExportPanelState {
  result?: RuntimeExportResult;
  message?: string;
  loading?: boolean;
}

export interface ExportPanelHandlers {
  onExportAll: () => Promise<void>;
  onExportSelected: () => Promise<void>;
  onExportTemplate: () => Promise<void>;
  onClose: () => void;
}

export function createImportPanel(state: ImportPanelState, handlers: ImportPanelHandlers): HTMLElement {
  const fileInput = createElement('input', { ariaLabel: '选择导入文件' });
  fileInput.type = 'file';
  fileInput.accept = '.csv,.xlsx,.xls,text/csv,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

  const fileIdInput = createElement('input', { ariaLabel: '已有上传文件 fileId' });
  fileIdInput.placeholder = '可选：已有上传文件 fileId';

  const templateInput = createElement('input', { ariaLabel: '模板编码' });
  templateInput.placeholder = '默认 default';
  templateInput.value = 'default';

  const duplicateSelect = createElement('select', { ariaLabel: '重复策略' });
  duplicateSelect.append(
    createOption('SKIP', '跳过重复数据'),
    createOption('OVERWRITE', '覆盖重复数据'),
    createOption('REJECT', '遇到重复即失败'),
  );

  const precheckButton = createButton('上传并预检', 'primary', Boolean(state.loading));
  precheckButton.dataset.runtimeImportPrecheckAction = 'true';
  precheckButton.dataset.runtimeRecoveryR89 = 'precheck-action';
  precheckButton.addEventListener('click', async () => {
    await handlers.onPrecheck({
      file: fileInput.files?.[0],
      fileId: fileIdInput.value.trim(),
      templateCode: templateInput.value.trim() || 'default',
      duplicateStrategy: duplicateSelect.value,
    });
  });

  const confirmDisabled = Boolean(state.loading || !state.precheck?.precheckId || state.precheck.invalidRows > 0);
  const confirmReason = state.precheck?.invalidRows ? '预检仍有失败行，需要先修正文件或调整重复策略。' : undefined;
  const confirmButton = createButton('确认导入', 'primary', confirmDisabled, confirmReason);
  confirmButton.dataset.runtimeImportConfirmAction = 'true';
  confirmButton.dataset.runtimeImportConfirmDisabled = String(confirmButton.disabled);
  confirmButton.dataset.runtimeRecoveryR89 = 'confirm-action';
  confirmButton.addEventListener('click', async () => {
    if (state.precheck?.precheckId) {
      await handlers.onConfirm(state.precheck.precheckId, duplicateSelect.value);
    }
  });

  const task = state.confirm?.task ?? state.precheck?.task;
  const resultFile = state.confirm?.task.resultFile ?? state.precheck?.resultFile ?? state.precheck?.task.resultFile;
  const errorFile = state.confirm?.task.errorFile ?? state.precheck?.errorFile ?? state.precheck?.task.errorFile;

  return createElement(
    'aside',
    {
      className: 'runtime-side-panel import-export-panel runtime-recovery-r89-panel',
      dataset: {
        runtimeImportPanel: 'true',
        runtimeRecoveryR89: 'import-panel',
        runtimeImportPrecheckId: state.precheck?.precheckId ?? '',
        runtimeImportPrecheckPassed: String(state.precheck?.passed ?? false),
        runtimeImportInvalidRows: String(state.precheck?.invalidRows ?? 0),
        runtimeImportTaskStatus: task?.status ?? '',
        runtimeImportErrorFileId: errorFile?.fileId ?? '',
        runtimeImportResultFileId: resultFile?.fileId ?? '',
      },
    },
    createPanelHeader('导入业务数据', 'Import', handlers.onClose),
    createStepRail(['上传文件', '字段映射', '预检结果', '确认导入'], state.confirm ? 3 : state.precheck ? 2 : 0),
    createElement(
      'section',
      { className: 'runtime-card form-grid', dataset: { runtimeImportRecoveryDetail: 'input' } },
      createElement('label', {}, createElement('span', {}, '导入文件'), fileInput),
      createElement('label', {}, createElement('span', {}, '已有 fileId'), fileIdInput),
      createElement('label', {}, createElement('span', {}, '模板编码'), templateInput),
      createElement('label', {}, createElement('span', {}, '重复策略'), duplicateSelect),
    ),
    state.message ? createElement('section', { className: 'runtime-card runtime-recovery-message', dataset: { runtimeRecoveryR89: 'import-message' } }, state.message) : null,
    createPrecheckCard(state.precheck),
    createElement('div', { className: 'inline-actions' }, precheckButton, confirmButton, createButtonWithHandler('收起', 'ghost', false, handlers.onClose)),
    createRecoveryDetailCard({
      kind: 'import',
      title: '导入恢复详情',
      task,
      resultFile,
      errorFile,
      emptyText: '预检或确认导入后，这里会显示任务状态、结果文件、错误文件、traceId、auditLogId 和回滚边界。',
      contextRows: [
        ['预检编号', state.precheck?.precheckId ?? '-'],
        ['预检结果', state.precheck ? (state.precheck.passed ? '通过' : '未通过') : '-'],
        ['通过 / 失败', state.precheck ? `${state.precheck.validRows} / ${state.precheck.invalidRows}` : '-'],
      ],
    }),
  );
}

export function createExportPanel(state: ExportPanelState, selectedCount: number, totalCount: number, handlers: ExportPanelHandlers): HTMLElement {
  const resultFile = state.result?.expectedResultFile ?? state.result?.task.resultFile;
  const errorFile = state.result?.expectedErrorFile ?? state.result?.task.errorFile;
  return createElement(
    'aside',
    {
      className: 'runtime-side-panel import-export-panel runtime-recovery-r89-panel',
      dataset: {
        runtimeExportPanel: 'true',
        runtimeRecoveryR89: 'export-panel',
        runtimeExportTaskId: state.result?.task.taskId ?? '',
        runtimeExportTaskStatus: state.result?.task.status ?? '',
        runtimeExportResultFileId: resultFile?.fileId ?? '',
        runtimeExportErrorFileId: errorFile?.fileId ?? '',
        runtimeExportSelectedCount: String(selectedCount),
        runtimeExportTotalCount: String(totalCount),
        runtimeSelectedExportState: selectedCount > 0 ? 'ready' : 'empty',
      },
    },
    createPanelHeader('导出业务数据', 'Export', handlers.onClose),
    createExportScopeCard(selectedCount, totalCount, state.message, Boolean(state.loading), handlers),
    createRecoveryDetailCard({
      kind: 'export',
      title: '导出恢复详情',
      task: state.result?.task,
      resultFile,
      errorFile,
      emptyText: '发起导出后，这里会显示任务状态、导出范围、结果文件、错误文件、traceId、auditLogId 和回滚边界。',
      contextRows: [
        ['导出范围', state.result?.scope ?? '-'],
        ['导出字段', state.result?.fields?.length ? `${state.result.fields.length} 个字段` : '-'],
        ['脱敏策略', state.result?.desensitizeMode ?? '按权限脱敏'],
        ['已选记录', `${selectedCount} 条`],
      ],
    }),
  );
}

function createPanelHeader(title: string, eyebrow: string, onClose: () => void): HTMLElement {
  const closeButton = createButton('收起', 'ghost', false);
  closeButton.addEventListener('click', onClose);
  return createElement(
    'header',
    { className: 'runtime-panel-head' },
    createElement('div', {}, createElement('span', { className: 'eyebrow' }, eyebrow), createElement('h2', {}, title)),
    closeButton,
  );
}

function createStepRail(steps: string[], activeIndex: number): HTMLElement {
  return createElement(
    'ol',
    { className: 'step-rail' },
    ...steps.map((step, index) =>
      createElement(
        'li',
        { className: index === activeIndex ? 'active' : '' },
        createElement('span', {}, String(index + 1)),
        createElement('strong', {}, step),
      ),
    ),
  );
}

function createExportScopeCard(selectedCount: number, totalCount: number, message: string | undefined, loading: boolean, handlers: ExportPanelHandlers): HTMLElement {
  return createElement(
    'section',
    {
      className: 'runtime-card runtime-export-scope-r89',
      dataset: {
        runtimeExportScopeR89: 'true',
        runtimeSelectedExportState: selectedCount > 0 ? 'ready' : 'empty',
        runtimeExportSelectedCount: String(selectedCount),
        runtimeExportTotalCount: String(totalCount),
      },
    },
    createElement('h3', {}, '导出范围'),
    createKeyValueGrid([
      ['当前筛选', `全部匹配 ${totalCount} 条`],
      ['已选记录', `${selectedCount} 条`],
      ['导出字段', '当前列表可见字段'],
      ['脱敏规则', '按字段权限导出，敏感字段脱敏'],
      ['文件格式', 'XLSX'],
    ]),
    selectedCount <= 0
      ? createElement('p', { className: 'runtime-recovery-boundary', dataset: { runtimeSelectedExportEmptyReason: 'true' } }, '导出选中需要先勾选记录；仍可导出当前筛选的全部匹配记录或字段模板。')
      : null,
    message ? createElement('p', { className: 'runtime-recovery-message', dataset: { runtimeRecoveryR89: 'export-message' } }, message) : null,
    createElement(
      'div',
      { className: 'inline-actions' },
      createAsyncButton('导出当前筛选', 'primary', loading, handlers.onExportAll),
      createAsyncButton('导出选中', 'secondary', loading || selectedCount <= 0, handlers.onExportSelected, selectedCount <= 0 ? '请先选择要导出的记录。' : undefined),
      createAsyncButton('只导出字段模板', 'ghost', loading, handlers.onExportTemplate),
    ),
  );
}

function createPrecheckCard(precheck: RuntimeImportPrecheckResult | undefined): HTMLElement {
  if (!precheck) {
    return createElement(
      'section',
      { className: 'runtime-card', dataset: { runtimeImportPrecheck: 'empty', runtimeImportRecoveryDetail: 'precheck-empty' } },
      createElement('h3', {}, '预检结果'),
      createElement('p', {}, '上传文件后展示字段映射、必填、字典、权限和重复数据预检结果。'),
    );
  }
  return createElement(
    'section',
    {
      className: 'runtime-card runtime-import-precheck-r89',
      dataset: {
        runtimeImportPrecheck: precheck.precheckId,
        runtimeImportRecoveryDetail: 'precheck',
        runtimeImportPrecheckPassed: String(precheck.passed),
        runtimeImportInvalidRows: String(precheck.invalidRows),
        runtimeImportErrorFileId: precheck.errorFile?.fileId ?? '',
        runtimeImportResultFileId: precheck.resultFile?.fileId ?? '',
      },
    },
    createElement('h3', {}, '预检结果'),
    createKeyValueGrid([
      ['预检编号', precheck.precheckId],
      ['总行数', String(precheck.totalRows)],
      ['通过', String(precheck.validRows)],
      ['失败', String(precheck.invalidRows)],
      ['重复', String(precheck.duplicateRows)],
    ]),
    createElement(
      'div',
      { className: 'precheck-list', dataset: { runtimeImportPrecheckIssueSummary: String(precheck.issues.length) } },
      ...(precheck.issues.length > 0
        ? precheck.issues.map((issue) =>
            createElement(
              'div',
              { className: 'precheck-item', dataset: { runtimeImportPrecheckIssue: issue.level ?? 'INFO' } },
              createElement('strong', {}, `${issue.rowNo ?? '-'} / ${issue.fieldCode ?? '-'}`),
              renderStatusPill(issue.level ?? 'INFO', issue.level === 'ERROR' ? 'danger' : 'warning'),
              createElement('span', {}, `${issue.message}${issue.suggestion ? `，建议：${issue.suggestion}` : ''}`),
            ),
          )
        : [createElement('p', {}, precheck.passed ? '预检通过，可以确认导入。' : '预检未返回明细，请检查任务结果文件。')]),
    ),
    createTraceLine(precheck.traceId, precheck.auditLogId),
  );
}

function createRecoveryDetailCard(input: {
  kind: 'import' | 'export';
  title: string;
  task?: AsyncTask;
  resultFile?: FileRef;
  errorFile?: FileRef;
  emptyText: string;
  contextRows: Array<[string, string]>;
}): HTMLElement {
  const task = input.task;
  if (!task) {
    return createElement(
      'section',
      {
        className: 'runtime-card task-card-detail runtime-recovery-detail-r89',
        dataset: { runtimeRecoveryR89: input.kind, runtimeRecoveryDetailState: 'empty' },
      },
      createElement('h3', {}, input.title),
      createElement('p', {}, input.emptyText),
      createKeyValueGrid(input.contextRows),
    );
  }

  const rollbackText = task.rollbackSupported
    ? '支持按任务边界回滚，执行前仍需要管理员确认影响范围。'
    : '当前任务未声明可回滚；请使用结果文件、错误文件和审计记录定位需要人工修正的数据。';

  return createElement(
    'section',
    {
      className: 'runtime-card task-card-detail runtime-recovery-detail-r89',
      dataset: {
        runtimeRecoveryR89: input.kind,
        runtimeRecoveryDetailState: 'ready',
        runtimeAsyncTask: task.taskId,
        runtimeTaskStatus: task.status,
        runtimeTaskResultFileId: input.resultFile?.fileId ?? '',
        runtimeTaskErrorFileId: input.errorFile?.fileId ?? '',
        runtimeTaskRollbackSupported: String(task.rollbackSupported),
        runtimeRollbackUnsupportedReason: task.rollbackSupported ? '' : 'unsupported',
        runtimeTaskPartialSuccess: String(task.partialSuccessCount ?? 0),
        runtimeTaskPartialFailure: String(task.partialFailureCount ?? 0),
      },
    },
    createElement(
      'div',
      { className: 'runtime-card-head' },
      createElement('h3', {}, input.title),
      renderStatusPill(formatTaskStatus(task.status), taskTone(task.status)),
    ),
    createKeyValueGrid([
      ...input.contextRows,
      ['任务ID', task.taskId || '-'],
      ['业务类型', task.bizType || '-'],
      ['进度', `${task.progress}%`],
      ['可重试', task.retryable ? '是' : '否'],
      ['可取消', task.cancelable ? '是' : '否'],
      ['可回滚', task.rollbackSupported ? '是' : '否'],
      ['部分成功', String(task.partialSuccessCount ?? 0)],
      ['部分失败', String(task.partialFailureCount ?? 0)],
      ['失败原因', task.failureReason || '-'],
    ]),
    createElement('p', { className: 'runtime-recovery-boundary', dataset: { runtimeRollbackBoundaryR89: String(task.rollbackSupported) } }, rollbackText),
    createFileActionGroup(input.resultFile, 'result'),
    createFileActionGroup(input.errorFile, 'error'),
    createTraceLine(task.traceId || '-', task.auditLogId || '-'),
  );
}

function createFileActionGroup(file: FileRef | undefined, kind: 'result' | 'error'): HTMLElement {
  const label = kind === 'result' ? '结果文件' : '错误文件';
  if (!file) {
    return createElement(
      'div',
      {
        className: 'runtime-file-actions empty',
        dataset: {
          runtimeTaskFileActionR89: kind,
          runtimeTaskFileState: 'empty',
        },
      },
      createElement('strong', {}, label),
      createElement('span', {}, '暂无文件'),
    );
  }

  const preview = createFileLink(file, 'preview', kind);
  const download = createFileLink(file, 'download', kind);
  return createElement(
    'div',
    {
      className: 'runtime-file-actions',
      dataset: {
        runtimeTaskFileActionR89: kind,
        runtimeTaskFileState: 'ready',
        runtimeTaskFileId: file.fileId,
        runtimeTaskFilePermission: file.permissionMode ?? '',
      },
    },
    createElement('strong', {}, `${label}: ${file.fileName || file.fileId}`),
    createElement('span', {}, file.status ? `状态：${file.status}` : '状态：待读取'),
    createElement('div', { className: 'inline-actions compact-actions' }, preview, download),
  );
}

function createFileLink(file: FileRef, mode: 'preview' | 'download', kind: 'result' | 'error'): HTMLAnchorElement {
  const link = createElement('a',
    {
      className: mode === 'download' ? 'button secondary' : 'button ghost',
      dataset: {
        runtimeTaskResultDownload: kind === 'result' && mode === 'download' ? file.fileId : '',
        runtimeTaskErrorDownload: kind === 'error' && mode === 'download' ? file.fileId : '',
        runtimeTaskResultPreview: kind === 'result' && mode === 'preview' ? file.fileId : '',
        runtimeTaskErrorPreview: kind === 'error' && mode === 'preview' ? file.fileId : '',
      },
    },
    mode === 'download' ? '下载' : '预览',
  );
  const explicitUrl = mode === 'download' ? file.downloadUrl : file.previewUrl;
  link.href = explicitUrl || `/api/v1/uploads/files/${encodeURIComponent(file.fileId)}/${mode}`;
  link.target = '_blank';
  link.rel = 'noreferrer';
  if (mode === 'download') {
    link.download = file.fileName || file.fileId;
  }
  return link;
}

function createKeyValueGrid(rows: Array<[string, string]>): HTMLElement {
  return createElement('dl', { className: 'kv-grid' }, ...rows.flatMap(([label, value]) => [createElement('dt', {}, label), createElement('dd', {}, value)]));
}

function createOption(value: string, label: string): HTMLOptionElement {
  const option = document.createElement('option');
  option.value = value;
  option.textContent = label;
  return option;
}

function createButtonWithHandler(label: string, variant: 'primary' | 'secondary' | 'ghost', disabled: boolean, handler: () => void): HTMLButtonElement {
  const button = createButton(label, variant, disabled);
  button.addEventListener('click', handler);
  return button;
}

function createAsyncButton(label: string, variant: 'primary' | 'secondary' | 'ghost', disabled: boolean, handler: () => Promise<void>, disabledReason?: string): HTMLButtonElement {
  const button = createButton(label, variant, disabled, disabledReason);
  button.addEventListener('click', () => {
    void handler();
  });
  return button;
}

function formatTaskStatus(status: string): string {
  const labelMap: Record<string, string> = {
    QUEUED: '排队中',
    PENDING: '待处理',
    RUNNING: '处理中',
    SUCCESS: '完成',
    FAILED: '失败',
    PARTIAL_SUCCESS: '部分成功',
    CANCELED: '已取消',
  };
  return labelMap[status] ?? status;
}

function taskTone(status: string): StatusTone {
  if (status === 'SUCCESS') {
    return 'success';
  }
  if (status === 'FAILED' || status === 'CANCELED') {
    return 'danger';
  }
  if (status === 'PARTIAL_SUCCESS' || status === 'QUEUED' || status === 'PENDING') {
    return 'warning';
  }
  return 'info';
}