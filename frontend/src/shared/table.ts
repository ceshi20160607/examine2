import type { ActionContract } from '../api/types';
import { createButton, createElement } from './components';

export interface TableColumn<T> {
  key: keyof T & string;
  label: string;
  width?: string;
  render?: (row: T) => HTMLElement;
}

export interface TablePagination {
  pageNo: number;
  pageSize: number;
  total: number;
  hasNext: boolean;
}

export interface TableSelection {
  selectedCount: number;
  maxSelection: number;
}

export interface DataTableOptions<T> {
  columns: TableColumn<T>[];
  rows: T[];
  rowClickTarget?: string;
  pagination: TablePagination;
  selection?: TableSelection;
  actions?: ActionContract[];
}

export function createDataTable<T extends object>(options: DataTableOptions<T>): HTMLElement {
  const table = createElement('table', { className: 'data-table' });
  const head = createElement(
    'thead',
    {},
    createElement(
      'tr',
      {},
      createElement('th', { className: 'select-col' }, ''),
      createElement('th', { className: 'index-col' }, '序号'),
      ...options.columns.map((column) => createElement('th', { dataset: { sortable: 'true' } }, `${column.label} ↕`)),
      createElement('th', {}, '操作'),
    ),
  );
  const body = createElement(
    'tbody',
    {},
    ...options.rows.map((row, index) => createRow(row, index, options)),
  );
  table.append(head, body);

  return createElement(
    'div',
    { className: 'table-shell' },
    createToolbar(options),
    table,
    createPagination(options.pagination),
  );
}

function createRow<T extends object>(row: T, index: number, options: DataTableOptions<T>): HTMLTableRowElement {
  const tr = createElement('tr', {
    className: options.rowClickTarget ? 'clickable-row' : '',
    dataset: options.rowClickTarget ? { rowClickTarget: options.rowClickTarget } : undefined,
  });
  const checkbox = createElement('input', { ariaLabel: `选择第 ${index + 1} 行` });
  checkbox.type = 'checkbox';
  tr.append(createElement('td', { className: 'select-col' }, checkbox));
  tr.append(createElement('td', { className: 'index-col' }, String(index + 1)));
  options.columns.forEach((column) => {
    const value = column.render ? column.render(row) : String((row as Record<string, unknown>)[column.key] ?? '');
    tr.append(createElement('td', {}, value));
  });
  tr.append(
    createElement(
      'td',
      { className: 'row-actions' },
      ...(options.actions ?? []).map((action) => createButton(action.name, action.enabled ? 'ghost' : 'secondary', !action.enabled, action.disabledReason)),
    ),
  );
  return tr;
}

function createToolbar<T extends object>(options: DataTableOptions<T>): HTMLElement {
  const selectionText = options.selection
    ? `已选 ${options.selection.selectedCount} 条 / 最多 ${options.selection.maxSelection} 条`
    : '未启用批量选择';
  return createElement(
    'div',
    { className: 'table-toolbar' },
    createElement('span', {}, selectionText),
    ...(options.actions ?? []).map((action) => createButton(action.name, action.enabled ? 'secondary' : 'ghost', !action.enabled, action.disabledReason)),
  );
}

function createPagination(pagination: TablePagination): HTMLElement {
  return createElement(
    'footer',
    { className: 'pagination' },
    createElement('span', {}, `第 ${pagination.pageNo} 页，每页 ${pagination.pageSize} 条，共 ${pagination.total} 条`),
    createButton('上一页', 'ghost', pagination.pageNo <= 1, '已经是第一页'),
    createButton('下一页', 'ghost', !pagination.hasNext, '没有更多数据'),
  );
}
