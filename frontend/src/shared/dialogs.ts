import { createButton, createElement } from './components';

export interface DialogField {
  name: string;
  label: string;
  defaultValue?: string;
  required?: boolean;
  multiline?: boolean;
  type?: 'text' | 'password' | 'url';
}

export async function requestTextInput(title: string, label: string, defaultValue = '', required = true): Promise<string | undefined> {
  const values = await requestFormInput(title, [
    {
      name: 'value',
      label,
      defaultValue,
      required,
    },
  ]);
  return values?.value;
}

export function requestConfirmation(title: string, message: string, confirmLabel = '确认'): Promise<boolean> {
  return new Promise((resolve) => {
    const cancelButton = createButton('取消', 'ghost', false);
    const confirmButton = createButton(confirmLabel, 'primary', false);
    const cleanup = (value: boolean) => {
      overlay.remove();
      resolve(value);
    };
    const overlay = createElement(
      'div',
      { className: 'modal-overlay' },
      createElement(
        'section',
        { className: 'modal-panel', ariaLabel: title },
        createElement('div', { className: 'modal-head' }, createElement('h2', {}, title)),
        createElement('p', { className: 'modal-message' }, message),
        createElement('div', { className: 'modal-actions' }, cancelButton, confirmButton),
      ),
    );
    cancelButton.addEventListener('click', () => cleanup(false));
    confirmButton.addEventListener('click', () => cleanup(true));
    overlay.addEventListener('click', (event) => {
      if (event.target === overlay) {
        cleanup(false);
      }
    });
    document.body.append(overlay);
    confirmButton.focus();
  });
}

export function requestFormInput(title: string, fields: DialogField[], submitLabel = '确认'): Promise<Record<string, string> | undefined> {
  return new Promise((resolve) => {
    const errorLine = createElement('p', { className: 'field-error' }, '');
    const controls = new Map<string, HTMLInputElement | HTMLTextAreaElement>();
    const cancelButton = createButton('取消', 'ghost', false);
    const submitButton = createButton(submitLabel, 'primary', false);
    const cleanup = (value: Record<string, string> | undefined) => {
      overlay.remove();
      resolve(value);
    };
    const fieldElements = fields.map((field) => {
      const control: HTMLInputElement | HTMLTextAreaElement = field.multiline
        ? createElement('textarea', { ariaLabel: field.label })
        : createElement('input', { ariaLabel: field.label });
      control.value = field.defaultValue ?? '';
      if (control instanceof HTMLInputElement) {
        control.type = field.type ?? 'text';
      }
      controls.set(field.name, control);
      return createElement(
        'label',
        { className: 'modal-field' },
        createElement('span', {}, field.required === false ? field.label : `${field.label} *`),
        control,
      );
    });
    const submit = () => {
      const values = Object.fromEntries(Array.from(controls.entries()).map(([name, control]) => [name, control.value.trim()]));
      const missing = fields.find((field) => field.required !== false && !values[field.name]);
      if (missing) {
        errorLine.textContent = `请填写${missing.label}。`;
        controls.get(missing.name)?.focus();
        return;
      }
      cleanup(values);
    };
    const overlay = createElement(
      'div',
      { className: 'modal-overlay' },
      createElement(
        'section',
        { className: 'modal-panel', ariaLabel: title },
        createElement('div', { className: 'modal-head' }, createElement('h2', {}, title)),
        createElement('div', { className: 'modal-form' }, ...fieldElements),
        errorLine,
        createElement('div', { className: 'modal-actions' }, cancelButton, submitButton),
      ),
    );
    cancelButton.addEventListener('click', () => cleanup(undefined));
    submitButton.addEventListener('click', submit);
    overlay.addEventListener('keydown', (event) => {
      if (event.key === 'Escape') {
        cleanup(undefined);
      }
      if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
        submit();
      }
    });
    overlay.addEventListener('click', (event) => {
      if (event.target === overlay) {
        cleanup(undefined);
      }
    });
    document.body.append(overlay);
    controls.values().next().value?.focus();
  });
}
