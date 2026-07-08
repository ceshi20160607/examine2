import { loadCommandCenter, type CommandCenterItem } from '../../api/liveData';
import type { Navigate } from '../../app/app';
import { shellState } from '../../app/state';
import { createButton, createElement, createTraceLine } from '../../shared/components';

let shortcutInstalled = false;
let activeOverlay: HTMLElement | undefined;

export function installCommandCenterShortcut(navigate: Navigate): void {
  if (shortcutInstalled) {
    return;
  }
  shortcutInstalled = true;
  document.addEventListener('keydown', (event) => {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      openCommandCenter(navigate);
    }
  });
}

export function createCommandCenterButton(navigate: Navigate, systemId?: string): HTMLButtonElement {
  const button = createButton('命令', 'ghost', false);
  button.title = 'Ctrl / Cmd + K';
  button.dataset.commandCenterEntry = 'true';
  button.dataset.commandCenterScope = systemId ? 'system' : 'platform';
  button.addEventListener('click', () => openCommandCenter(navigate, systemId));
  return button;
}

export function openCommandCenter(navigate: Navigate, systemId?: string): void {
  if (activeOverlay) {
    activeOverlay.remove();
    activeOverlay = undefined;
  }
  const resolvedSystemId = systemId ?? shellState.currentSystem?.systemId;
  const input = createElement('input', { ariaLabel: '搜索命令' });
  input.placeholder = '搜索入口、模块、后台、待办';
  const closeButton = createButton('关闭', 'ghost', false);
  const resultPanel = createElement('section', { className: 'command-results', dataset: { commandCenterResults: 'loading' } }, createElement('strong', {}, '正在读取命令...'));
  const dialog = createElement(
    'section',
    {
      className: 'command-dialog',
      dataset: {
        productSurface: 'command-center',
        commandCenterScope: resolvedSystemId ? 'system' : 'platform',
        commandCenterSystemId: resolvedSystemId ?? '',
      },
    },
    createElement(
      'header',
      { className: 'command-head' },
      createElement('div', {}, createElement('h2', {}, '命令中心'), createElement('small', {}, 'Ctrl / Cmd + K')),
      closeButton,
    ),
    input,
    resultPanel,
  );
  const overlay = createElement('div', { className: 'command-overlay', dataset: { commandCenterOverlay: 'true' } }, dialog);
  activeOverlay = overlay;

  const close = () => {
    overlay.remove();
    activeOverlay = undefined;
  };
  closeButton.addEventListener('click', close);
  overlay.addEventListener('click', (event) => {
    if (event.target === overlay) {
      close();
    }
  });
  dialog.addEventListener('click', (event) => event.stopPropagation());
  document.body.append(overlay);
  input.focus();

  let lastKeyword = '';
  const reload = () => {
    const keyword = input.value.trim();
    lastKeyword = keyword;
    resultPanel.replaceChildren(createElement('strong', {}, '正在读取命令...'));
    void loadCommandCenter({ keyword, systemId: resolvedSystemId })
      .then((response) => {
        if (keyword !== lastKeyword) {
          return;
        }
        resultPanel.replaceChildren(
          response.items.length === 0
            ? createElement('section', { className: 'runtime-card' }, '没有匹配入口')
            : createCommandGroups(response.items, (route) => {
                close();
                navigate(route);
              }),
          createTraceLine(response.traceId),
        );
        resultPanel.dataset.commandCenterResults = response.items.length === 0 ? 'empty' : 'loaded';
        resultPanel.dataset.commandCenterItemCount = String(response.items.length);
        resultPanel.dataset.commandCenterTraceId = response.traceId;
      })
      .catch((error) => {
        resultPanel.replaceChildren(createElement('section', { className: 'runtime-card' }, error instanceof Error ? error.message : '命令读取失败'));
        resultPanel.dataset.commandCenterResults = 'error';
      });
  };
  input.addEventListener('input', reload);
  input.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') {
      close();
    }
  });
  reload();
}

function createCommandGroups(items: CommandCenterItem[], onNavigate: (route: string) => void): HTMLElement {
  const groups = new Map<string, CommandCenterItem[]>();
  items.forEach((item) => {
    groups.set(item.groupName, [...(groups.get(item.groupName) ?? []), item]);
  });
  return createElement(
    'div',
    { className: 'command-group-list', dataset: { commandCenterGroupList: 'true' } },
    ...Array.from(groups.entries()).map(([groupName, groupItems]) =>
      createElement(
        'section',
        { className: 'command-group', dataset: { commandCenterGroup: groupName } },
        createElement('h3', {}, groupName),
        ...groupItems.map((item) => createCommandItem(item, onNavigate)),
      ),
    ),
  );
}

function createCommandItem(item: CommandCenterItem, onNavigate: (route: string) => void): HTMLButtonElement {
  const button = createElement(
    'button',
    {
      className: `command-item${item.disabled ? ' disabled' : ''}`,
      title: item.disabledReason,
      dataset: {
        commandCenterItem: item.commandId,
        commandCenterItemRoute: item.route,
        commandCenterItemDisabled: String(item.disabled),
      },
    },
    createElement('span', {}, item.label),
    createElement('small', {}, item.disabled ? item.disabledReason ?? '当前不可用' : item.description),
  );
  button.disabled = item.disabled;
  button.addEventListener('click', () => {
    if (!item.disabled) {
      onNavigate(item.route);
    }
  });
  return button;
}
