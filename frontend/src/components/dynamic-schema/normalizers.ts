import type {
  DynamicDetailSchema,
  DynamicSchemaFieldRef,
  DynamicFormSchema,
  DynamicListSchema,
  DynamicSchemaSorterRef,
  FieldVisibilityRule,
  FieldWritableRule,
  DynamicSchemaInput,
} from "./types";
import type { FieldDefinitionVO, JsonValue } from "../../api";

export function normalizeListSchema(input: DynamicSchemaInput): DynamicListSchema {
  const raw = asRecord(input.listSchema);
  const columns = normalizeRefs(raw.columns, input.fieldDefinitions);
  return {
    columns: columns.length > 0 ? columns : defaultRefs(input.fieldDefinitions),
    filters: normalizeRefs(raw.filters, input.fieldDefinitions),
    sorters: normalizeSorters(raw.sorters),
    fieldVisibility: normalizeVisibility(raw.fieldVisibility),
    schemaVersion: normalizeNumber(raw.schemaVersion),
  };
}

export function normalizeFormSchema(input: DynamicSchemaInput): DynamicFormSchema {
  const raw = asRecord(input.formSchema);
  const sections = Array.isArray(raw.formSections) ? raw.formSections : [];
  const normalizedSections = sections.map((section, index) => {
    const sectionRecord = asRecord(section);
    return {
      sectionCode: normalizeString(sectionRecord.sectionCode) ?? `section_${index + 1}`,
      title: normalizeString(sectionRecord.title) ?? `Section ${index + 1}`,
      fields: normalizeRefs(sectionRecord.fields, input.fieldDefinitions),
    };
  }).filter((section) => section.fields.length > 0);
  return {
    formSections: normalizedSections.length > 0
      ? normalizedSections
      : [{
          sectionCode: "default",
          title: "基础信息",
          fields: defaultRefs(input.fieldDefinitions),
        }],
    fieldWritable: normalizeWritable(raw.fieldWritable),
    schemaVersion: normalizeNumber(raw.schemaVersion),
  };
}

export function normalizeDetailSchema(input: DynamicSchemaInput): DynamicDetailSchema {
  const raw = asRecord(input.detailSchema);
  const blocks = Array.isArray(raw.detailBlocks) ? raw.detailBlocks : [];
  const normalizedBlocks = blocks.map((block, index) => {
    const blockRecord = asRecord(block);
    return {
      blockCode: normalizeString(blockRecord.blockCode) ?? `block_${index + 1}`,
      title: normalizeString(blockRecord.title) ?? `Block ${index + 1}`,
      fields: normalizeRefs(blockRecord.fields, input.fieldDefinitions),
    };
  }).filter((block) => block.fields.length > 0);
  return {
    detailBlocks: normalizedBlocks.length > 0
      ? normalizedBlocks
      : [{
          blockCode: "default",
          title: "详情",
          fields: defaultRefs(input.fieldDefinitions),
        }],
    fieldVisibility: normalizeVisibility(raw.fieldVisibility),
    schemaVersion: normalizeNumber(raw.schemaVersion),
  };
}

export function normalizeValues(values: unknown): Map<string, { value: unknown; displayValue?: JsonValue }> {
  const map = new Map<string, { value: unknown; displayValue?: JsonValue }>();
  if (!Array.isArray(values)) {
    return map;
  }
  values.forEach((item) => {
    const record = asRecord(item);
    const fieldCode = normalizeString(record.fieldCode);
    if (!fieldCode) {
      return;
    }
    map.set(fieldCode, {
      value: record.value,
      displayValue: record.displayValue as JsonValue | undefined,
    });
  });
  return map;
}

function normalizeRefs(value: unknown, fields: FieldDefinitionVO[]): DynamicSchemaFieldRef[] {
  if (!Array.isArray(value)) {
    return [];
  }
  const fieldByCode = new Map(fields.map((field) => [field.fieldCode, field]));
  const refs: DynamicSchemaFieldRef[] = [];
  value.forEach((item) => {
    const record = asRecord(item);
    const fieldCode = normalizeString(record.fieldCode);
    if (!fieldCode || !fieldByCode.has(fieldCode)) {
      return;
    }
    const field = fieldByCode.get(fieldCode);
    refs.push({
      fieldCode,
      fieldId: normalizeString(record.fieldId) ?? field?.fieldId,
      label: normalizeString(record.label) ?? field?.fieldName,
      visible: normalizeBoolean(record.visible),
      required: normalizeBoolean(record.required),
      width: normalizeNumber(record.width),
      component: normalizeString(record.component),
      defaultValue: record.defaultValue as JsonValue | undefined,
      sortable: normalizeBoolean(record.sortable),
      filterable: normalizeBoolean(record.filterable),
    });
  });
  return refs;
}

function defaultRefs(fields: FieldDefinitionVO[]): DynamicSchemaFieldRef[] {
  return fields
    .filter((field) => field.status !== "DELETED")
    .map((field) => ({
      fieldCode: field.fieldCode,
      fieldId: field.fieldId,
      label: field.fieldName,
      visible: true,
      required: field.required,
      sortable: true,
      filterable: true,
    }));
}

function normalizeSorters(value: unknown): DynamicSchemaSorterRef[] {
  if (!Array.isArray(value)) {
    return [];
  }
  const sorters: DynamicSchemaSorterRef[] = [];
  value.forEach((item) => {
    const record = asRecord(item);
    const fieldCode = normalizeString(record.fieldCode) ?? normalizeString(record.field);
    if (!fieldCode) {
      return;
    }
    sorters.push({
      fieldCode,
      direction: record.direction === "DESC" ? "DESC" : "ASC",
    });
  });
  return sorters;
}

function normalizeVisibility(value: unknown): FieldVisibilityRule[] {
  if (!Array.isArray(value)) {
    return [];
  }
  const rules: FieldVisibilityRule[] = [];
  value.forEach((item) => {
    const record = asRecord(item);
    const fieldCode = normalizeString(record.fieldCode);
    if (!fieldCode) {
      return;
    }
    const reason = normalizeString(record.reason);
    rules.push({
      fieldCode,
      visible: record.visible !== false,
      ...(reason ? { reason } : {}),
    });
  });
  return rules;
}

function normalizeWritable(value: unknown): FieldWritableRule[] {
  if (!Array.isArray(value)) {
    return [];
  }
  const rules: FieldWritableRule[] = [];
  value.forEach((item) => {
    const record = asRecord(item);
    const fieldCode = normalizeString(record.fieldCode);
    if (!fieldCode) {
      return;
    }
    const readonlyReason = normalizeString(record.readonlyReason);
    rules.push({
      fieldCode,
      writable: record.writable !== false,
      ...(readonlyReason ? { readonlyReason } : {}),
    });
  });
  return rules;
}

function asRecord(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function normalizeString(value: unknown): string | undefined {
  return typeof value === "string" && value.trim().length > 0 ? value : undefined;
}

function normalizeBoolean(value: unknown): boolean | undefined {
  return typeof value === "boolean" ? value : undefined;
}

function normalizeNumber(value: unknown): number | undefined {
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}
