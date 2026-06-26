export interface ElementOptions {
  id?: string;
  className?: string;
  ariaLabel?: string;
  title?: string;
  dataset?: Record<string, string>;
}

export type ChildNodeValue = HTMLElement | Text | string | number | null | undefined;

export function createElement<K extends keyof HTMLElementTagNameMap>(
  tagName: K,
  options: ElementOptions = {},
  ...children: ChildNodeValue[]
): HTMLElementTagNameMap[K] {
  const element = document.createElement(tagName);
  if (options.className) {
    element.className = options.className;
  }
  if (options.id) {
    element.id = options.id;
  }
  if (options.ariaLabel) {
    element.setAttribute('aria-label', options.ariaLabel);
  }
  if (options.title) {
    element.title = options.title;
  }
  if (options.dataset) {
    Object.entries(options.dataset).forEach(([key, value]) => {
      element.dataset[key] = value;
    });
  }
  appendChildren(element, children);
  return element;
}

export function appendChildren(parent: HTMLElement, children: ChildNodeValue[]): void {
  children.forEach((child) => {
    if (child === null || child === undefined) {
      return;
    }
    parent.append(child instanceof Node ? child : document.createTextNode(String(child)));
  });
}

export function createSection(title: string, ...children: ChildNodeValue[]): HTMLElement {
  return createElement(
    'section',
    { className: 'section' },
    createElement('h2', {}, title),
    createElement('div', { className: 'section-body' }, ...children),
  );
}

export function createButton(label: string, variant: 'primary' | 'secondary' | 'ghost', disabled: boolean, disabledReason?: string): HTMLButtonElement {
  const button = createElement('button', { className: `button ${variant}` }, label);
  button.disabled = disabled;
  if (disabledReason) {
    button.title = disabledReason;
    button.dataset.disabledReason = disabledReason;
  }
  return button;
}

export function createTraceLine(traceId: string, auditLogId?: string): HTMLElement {
  return createElement(
    'p',
    { className: 'trace-line' },
    createElement('span', {}, `traceId: ${traceId}`),
    auditLogId ? createElement('span', {}, `auditLogId: ${auditLogId}`) : null,
  );
}
