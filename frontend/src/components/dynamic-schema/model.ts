import type { DynamicFieldValue, FieldDefinitionVO, JsonValue } from "../../api";
import { formatDynamicFieldValue, getDynamicFieldRenderer } from "./renderers";
import { normalizeDetailSchema, normalizeFormSchema, normalizeListSchema, normalizeValues } from "./normalizers";
import { resolveFieldPermission } from "./permissions";
import { validateDynamicForm } from "./validation";
import type {
  DynamicDetailRenderModel,
  DynamicFieldError,
  DynamicFieldRenderModel,
  DynamicFormRenderModel,
  DynamicHistorySnapshotModel,
  DynamicListRenderModel,
  DynamicSchemaFieldRef,
  DynamicSchemaInput,
} from "./types";

export function createDynamicListModel(
  input: DynamicSchemaInput,
  values: DynamicFieldValue[] = [],
  errors: DynamicFieldError[] = [],
): DynamicListRenderModel {
  const listSchema = normalizeListSchema(input);
  const valueMap = normalizeValues(values);
  const visibilityRules = new Map(listSchema.fieldVisibility?.map((rule) => [rule.fieldCode, rule]));
  const columns = listSchema.columns
    .map((ref) => buildFieldModel(input, ref, "list", valueMap, errors))
    .filter((field) => field.permission.visible && visibilityRules.get(field.field.fieldCode)?.visible !== false);
  const filters = (listSchema.filters ?? [])
    .map((ref) => buildFieldModel(input, ref, "filter", valueMap, errors))
    .filter((field) => field.permission.visible && field.renderer.filterable);
  return {
    columns,
    filters,
    sorters: (listSchema.sorters ?? [])
      .filter((sorter) => columns.some((field) => field.field.fieldCode === sorter.fieldCode && field.renderer.sortable))
      .map((sorter) => ({ field: sorter.fieldCode, direction: sorter.direction })),
    empty: columns.length === 0,
  };
}

export function createDynamicFormModel(
  input: DynamicSchemaInput,
  values: Record<string, unknown> = {},
  errors: DynamicFieldError[] = [],
): DynamicFormRenderModel {
  const formSchema = normalizeFormSchema(input);
  const valueMap = normalizeValues(toDynamicValues(values, input.fieldDefinitions));
  const validation = validateDynamicForm(input, values, errors);
  const combinedErrors = validation.errors;
  return {
    sections: formSchema.formSections.map((section) => ({
      sectionCode: section.sectionCode,
      title: section.title,
      fields: section.fields
        .map((ref) => buildFieldModel(input, ref, "form", valueMap, combinedErrors))
        .filter((field) => field.permission.visible),
    })),
    errors: combinedErrors,
    valid: validation.valid,
    values: validation.values,
  };
}

export function createDynamicDetailModel(
  input: DynamicSchemaInput,
  values: DynamicFieldValue[] = [],
  errors: DynamicFieldError[] = [],
): DynamicDetailRenderModel {
  const detailSchema = normalizeDetailSchema(input);
  const valueMap = normalizeValues(values);
  const visibilityRules = new Map(detailSchema.fieldVisibility?.map((rule) => [rule.fieldCode, rule]));
  const blocks = detailSchema.detailBlocks.map((block) => ({
    blockCode: block.blockCode,
    title: block.title,
    fields: block.fields
      .map((ref) => buildFieldModel(input, ref, "detail", valueMap, errors))
      .filter((field) => field.permission.visible && visibilityRules.get(field.field.fieldCode)?.visible !== false),
  }));
  return {
    blocks,
    empty: blocks.every((block) => block.fields.length === 0),
  };
}

export function createDynamicHistoryModel(
  input: DynamicSchemaInput,
  snapshots: Array<{ title: string; changedAt?: string; changedBy?: string; values: DynamicFieldValue[]; requestId?: string }>,
): DynamicHistorySnapshotModel[] {
  const detailSchema = normalizeDetailSchema(input);
  const refs = detailSchema.detailBlocks.flatMap((block) => block.fields);
  return snapshots.map((snapshot) => {
    const valueMap = normalizeValues(snapshot.values);
    return {
      title: snapshot.title,
      changedAt: snapshot.changedAt,
      changedBy: snapshot.changedBy,
      requestId: snapshot.requestId,
      fields: refs
        .map((ref) => buildFieldModel(input, ref, "history", valueMap, []))
        .filter((field) => field.permission.visible),
    };
  });
}

function buildFieldModel(
  input: DynamicSchemaInput,
  ref: DynamicSchemaFieldRef,
  mode: DynamicFieldRenderModel["mode"],
  values: Map<string, { value: unknown; displayValue?: JsonValue }>,
  errors: DynamicFieldError[],
): DynamicFieldRenderModel {
  const field = input.fieldDefinitions.find((item) => item.fieldCode === ref.fieldCode) ?? fallbackField(ref);
  const renderer = getDynamicFieldRenderer(field.fieldType);
  const permission = resolveFieldPermission(field, input.fieldPermissions, normalizeFormSchema(input).fieldWritable);
  const valueEntry = values.get(field.fieldCode);
  const value = valueEntry?.value ?? ref.defaultValue ?? field.defaultValue ?? null;
  const rawDisplayValue = valueEntry?.displayValue;
  return {
    field,
    ref: {
      ...ref,
      label: ref.label ?? field.fieldName,
    },
    renderer,
    mode,
    permission,
    value: value as DynamicFieldRenderModel["value"],
    displayValue: formatDynamicFieldValue(field.fieldType, value, rawDisplayValue),
    rawDisplayValue,
    required: Boolean(ref.required ?? field.required),
    errors: errors.filter((error) => error.fieldCode === field.fieldCode),
  };
}

function toDynamicValues(values: Record<string, unknown>, fields: FieldDefinitionVO[]): DynamicFieldValue[] {
  return fields.map((field) => ({
    fieldId: field.fieldId,
    fieldCode: field.fieldCode,
    fieldType: field.fieldType,
    value: (values[field.fieldCode] ?? null) as DynamicFieldValue["value"],
  }));
}

function fallbackField(ref: DynamicSchemaFieldRef): FieldDefinitionVO {
  return {
    fieldId: ref.fieldId ?? ref.fieldCode,
    fieldCode: ref.fieldCode,
    fieldName: ref.label ?? ref.fieldCode,
    fieldType: "TEXT",
    required: Boolean(ref.required),
    unique: false,
    status: "DISABLED",
    version: 0,
  };
}
