import { createButton, createElement } from './components';

export interface KeywordFilterOptions {
  label: string;
  value: string;
  onApply: (keyword: string) => void;
  onReset: () => void;
  disabled?: boolean;
  disabledReason?: string;
}

export function createFilterBar(filters: string[]): HTMLElement {
  const bar = createElement(
    'div',
    { className: 'filter-bar' },
    ...filters.map((filter) => createFilterField(filter)),
    createFilterResetButton(),
  );
  return bar;
}

export function createKeywordFilterBar(options: KeywordFilterOptions): HTMLElement {
  const input = createElement('input', { ariaLabel: options.label });
  input.placeholder = options.label;
  input.value = options.value;
  input.disabled = Boolean(options.disabled);

  const applyButton = createButton('应用筛选', 'primary', Boolean(options.disabled), options.disabledReason);
  const resetButton = createButton('重置', 'ghost', Boolean(options.disabled), options.disabledReason);

  const apply = () => {
    if (options.disabled) {
      return;
    }
    options.onApply(input.value.trim());
  };

  input.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      apply();
    }
  });
  applyButton.addEventListener('click', apply);
  resetButton.addEventListener('click', () => {
    input.value = '';
    options.onReset();
  });

  return createElement(
    'div',
    { className: 'filter-bar keyword-filter-bar' },
    createElement('label', {}, createElement('span', {}, options.label), input),
    applyButton,
    resetButton,
  );
}

function createFilterField(label: string): HTMLElement {
  const input = createElement('input', { ariaLabel: label });
  input.placeholder = label;
  return createElement('label', {}, createElement('span', {}, label), input);
}

function createFilterResetButton(): HTMLButtonElement {
  const button = createButton('重置', 'ghost', false);
  button.addEventListener('click', (event) => {
    const bar = (event.currentTarget as HTMLElement).closest('.filter-bar');
    bar?.querySelectorAll('input').forEach((input) => {
      input.value = '';
    });
  });
  return button;
}
