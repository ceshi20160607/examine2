import type { AsyncTask } from '../../../api/types';
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
  precheckButton.addEventListener('click', async () => {
    await handlers.onPrecheck({
      file: fileInput.files?.[0],
      fileId: fileIdInput.value.trim(),
      templateCode: templateInput.value.trim() || 'default',
      duplicateStrategy: duplicateSelect.value,
    });
  });

  const confirmButton = createButton(
    '确认导入',
    'primary',
    Boolean(state.loading || !state.precheck?.precheckId || state.precheck.invalidRows > 0),
    state.precheck?.invalidRows ? '预检仍有失败行，需要先修正或选择可处理失败行的策略。' : undefined,
  );
  confirmButton.addEventListener('click', async () => {
    if (state.precheck?.precheckId) {
      await handlers.onConfirm(state.precheck.precheckId, duplicateSelect.value);
    }
  });

  return createElement(
    'aside',
    { className: 'runtime-side-panel import-export-panel' },
    createPanelHeader('导入业务数据', 'Import', handlers.onClose),
    createStepRail(['上传文件', '字段匹配', '预检结果', '确认导入'], state.confirm ? 3 : state.precheck ? 2 : 0),
    createElement(
      'section',
      { className: 'runtime-card form-grid' },
      createElement('label', {}, createElement('span', {}, '导入文件'), fileInput),
      createElement('label', {}, createElement('span', {}, '已有 fileId'), fileIdInput),
      createElement('label', {}, createElement('span', {}, '模板编码'), templateInput),
      createElement('label', {}, createElement('span', {}, '重复策略'), duplicateSelect),
    ),
    state.message ? createElement('section', { className: 'runtime-card' }, state.message) : null,
    createPrecheckCard(state.precheck),
    createElement('div', { className: 'inline-actions' }, precheckButton, confirmButton, createButtonWithHandler('收起', 'ghost', false, handlers.onClose)),
    createTaskCard('导入任务', state.confirm?.task ?? state.precheck?.task),
  );
}

export function createExportPanel(state: ExportPanelState, selectedCount: number, totalCount: number, handlers: ExportPanelHandlers): HTMLElement {
  return createElement(
    'aside',
    { className: 'runtime-side-panel import-export-panel' },
    createPanelHeader('导出业务数据', 'Export', handlers.onClose),
    createElement(
      'section',
      { className: 'runtime-card' },
      createElement('h3', {}, '导出范围'),
      createKeyValueGrid([
        ['当前筛选', `全部匹配 ${totalCount} 条`],
        ['已选记录', `${selectedCount} 条`],
        ['导出字段', '当前列表可见字段'],
        ['脱敏规则', '按字段权限导出，敏感字段脱敏'],
        ['文件格式', 'XLSX'],
      ]),
      state.message ? createElement('p', {}, state.message) : null,
      createElement(
        'div',
        { className: 'inline-actions' },
        createAsyncButton('导出当前筛选', 'primary', Boolean(state.loading), handlers.onExportAll),
        createAsyncButton('导出选中', 'secondary', Boolean(state.loading || selectedCount <= 0), handlers.onExportSelected, selectedCount <= 0 ? '请先选择要导出的记录。' : undefined),
        createAsyncButton('只导出字段模板', 'ghost', Boolean(state.loading), handlers.onExportTemplate),
      ),
    ),
    createTaskCard('导出任务', state.result?.task),
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

function createPrecheckCard(precheck: RuntimeImportPrecheckResult | undefined): HTMLElement {
  if (!precheck) {
    return createElement('section', { className: 'runtime-card' }, createElement('h3', {}, '预检结果'), createElement('p', {}, '上传文件后展示字段映射、必填、字典、权限和重复数据预检结果。'));
  }
  return createElement(
    'section',
    { className: 'runtime-card' },
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
      { className: 'precheck-list' },
      ...(precheck.issues.length > 0
        ? precheck.issues.map((issue) =>
            createElement(
              'div',
              { className: 'precheck-item' },
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

function createTaskCard(title: string, task: AsyncTask | undefined): HTMLElement {
  if (!task) {
    return createElement('section', { className: 'runtime-card task-card-detail' }, createElement('h3', {}, title), createElement('p', {}, '任务创建后在这里展示进度、结果文件、错误文件和 traceId。'));
  }
  return createElement(
    'section',
    { className: 'runtime-card task-card-detail' },
    createElement(
      'div',
      { className: 'runtime-card-head' },
      createElement('h3', {}, title),
      renderStatusPill(formatTaskStatus(task.status), taskTone(task.status)),
    ),
    createKeyValueGrid([
      ['任务ID', task.taskId || '-'],
      ['业务类型', task.bizType || '-'],
      ['进度', `${task.progress}%`],
      ['可重试', task.retryable ? '是' : '否'],
      ['可取消', task.cancelable ? '是' : '否'],
      ['可回滚', task.rollbackSupported ? '是' : '否'],
      ['结果文件', task.resultFile?.fileName ?? '-'],
      ['错误文件', task.errorFile?.fileName ?? '-'],
    ]),
    createTraceLine(task.traceId || '-', task.auditLogId || '-'),
  );
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
