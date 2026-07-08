import type { FieldDefinitionVO } from '../../api/types';
import type { PageSchemaView } from '../../api/liveData';
import { createElement } from '../../shared/components';

export function createSchemaFormField(field: FieldDefinitionVO, value: unknown, error?: string): HTMLElement {
  const readonly = field.readonly === true || field.writable === false;
  const input = createElement('input', {
    ariaLabel: field.name,
    dataset: {
      fieldCode: field.fieldCode,
      readonly: readonly ? 'true' : 'false',
      required: field.required ? 'true' : 'false',
    },
  });
  input.type = inputType(field.fieldType);
  input.value = value === null || value === undefined ? '' : String(value);
  input.required = field.required;
  input.disabled = readonly;
  if (error) {
    input.setAttribute('aria-invalid', 'true');
  }

  const badges = [
    field.required ? '必填' : '',
    readonly ? '只读' : '',
    field.permissionMode === 'MASKED' ? '脱敏' : '',
  ].filter(Boolean);

  return createElement(
    'label',
    {
      className: `schema-field${readonly ? ' is-readonly' : ''}${error ? ' is-invalid' : ''}`,
      dataset: {
        schemaField: 'true',
        fieldCode: field.fieldCode,
      },
    },
    createElement(
      'span',
      { className: 'schema-field-label' },
      createElement('strong', {}, field.name),
      badges.length ? createElement('em', {}, badges.join(' / ')) : null,
    ),
    input,
    error ? createElement('small', { className: 'field-error', dataset: { fieldError: field.fieldCode } }, error) : null,
    field.disabledReason ? createElement('small', {}, field.disabledReason) : null,
  );
}

export function createPageSchemaPreview(schema: PageSchemaView): HTMLElement {
  return createElement(
    'section',
    { className: 'schema-preview' },
    createElement(
      'header',
      { className: 'schema-preview-head' },
      createElement('div', {}, createElement('span', { className: 'eyebrow' }, 'Schema Runtime'), createElement('h4', {}, schema.pageName)),
      createElement('span', { className: 'schema-version' }, schema.schemaVersion),
    ),
    createElement(
      'div',
      { className: 'schema-component-strip', dataset: { pageSchemaComponentStrip: 'true' } },
      ...schema.components.map((component) => createElement(
        'article',
        {
          dataset: {
            schemaComponent: component.componentCode,
            schemaComponentType: component.componentType,
            schemaComponentWidth: componentProp(component, 'width'),
            schemaComponentPlacement: componentProp(component, 'placement'),
          },
        },
        createElement('span', {}, component.componentType),
        createElement('strong', {}, component.title || component.componentCode),
        createElement('small', {}, `宽度 ${componentProp(component, 'width')} / 位置 ${componentProp(component, 'placement')}`),
      )),
    ),
    createElement(
      'div',
      { className: 'schema-preview-grid' },
      createElement(
        'section',
        {},
        createElement('h5', {}, '表单'),
        schema.fields.length
          ? createElement('div', { className: 'form-grid' }, ...schema.fields.map((field) => createSchemaFormField(field, undefined)))
          : createElement('p', { className: 'empty-hint' }, '当前角色没有可见字段。'),
      ),
      createElement(
        'section',
        {},
        createElement('h5', {}, '列表'),
        createElement(
          'div',
          { className: 'schema-column-list' },
          ...schema.fields.map((field) => createElement('span', {}, `${field.name}${field.sortable ? ' / 可排序' : ''}`)),
        ),
      ),
      createElement(
        'section',
        {},
        createElement('h5', {}, '权限'),
        ...schema.fields.map((field) => createElement(
          'div',
          { className: 'list-line' },
          createElement('span', {}, field.name),
          createElement('strong', {}, field.permissionMode ?? 'READABLE'),
        )),
      ),
    ),
  );
}

function componentProp(component: PageSchemaView['components'][number], key: string): string {
  const value = component.props?.[key];
  return value === null || value === undefined ? '-' : String(value);
}

function inputType(fieldType: string | undefined): string {
  if (fieldType === 'NUMBER' || fieldType === 'AMOUNT') {
    return 'number';
  }
  if (fieldType === 'DATE') {
    return 'date';
  }
  if (fieldType === 'DATETIME') {
    return 'datetime-local';
  }
  return 'text';
}
