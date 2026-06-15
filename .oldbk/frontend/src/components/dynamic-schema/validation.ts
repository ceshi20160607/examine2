import type { ApiErrorDetail, DynamicFieldValue, FieldDefinitionVO, FileBindDTO, JsonValue } from "../../api";
import type { DynamicFieldError, DynamicSchemaValidationResult } from "./types";
import { resolveFieldPermission } from "./permissions";
import { normalizeFormSchema } from "./normalizers";
import type { DynamicSchemaInput } from "./types";

export function mapApiFieldErrors(errors: ApiErrorDetail[] = [], requestId?: string): DynamicFieldError[] {
  return errors
    .filter((item) => item.fieldCode)
    .map((item) => ({
      fieldCode: item.fieldCode as string,
      code: item.reason || "FIELD_VALUE_TYPE_INVALID",
      message: item.userMessage || item.reason,
      requestId,
      apiError: item,
    }));
}

export function validateDynamicForm(
  input: DynamicSchemaInput,
  values: Record<string, unknown>,
  apiErrors: DynamicFieldError[] = [],
): DynamicSchemaValidationResult {
  const formSchema = normalizeFormSchema(input);
  const fieldByCode = new Map(input.fieldDefinitions.map((field) => [field.fieldCode, field]));
  const errors = [...apiErrors];
  const resultValues: DynamicFieldValue[] = [];

  formSchema.formSections
    .flatMap((section) => section.fields)
    .forEach((ref) => {
      const field = fieldByCode.get(ref.fieldCode);
      if (!field) {
        return;
      }
      const permission = resolveFieldPermission(field, input.fieldPermissions, formSchema.fieldWritable);
      const value = values[field.fieldCode] ?? ref.defaultValue ?? field.defaultValue ?? null;
      const required = Boolean(ref.required ?? field.required);

      if (!permission.visible) {
        return;
      }
      if (!permission.writable) {
        if (values[field.fieldCode] !== undefined) {
          errors.push({
            fieldCode: field.fieldCode,
            code: "PERM_FIELD_WRITE_DENIED",
            message: permission.readonlyReason ?? "Field is readonly by permission.",
          });
        }
        return;
      }
      if (required && isEmptyValue(value)) {
        errors.push({
          fieldCode: field.fieldCode,
          code: "FIELD_REQUIRED_MISSING",
          message: `${field.fieldName} is required.`,
        });
        return;
      }
      if (!isEmptyValue(value) && !isValueCompatible(field, value)) {
        errors.push({
          fieldCode: field.fieldCode,
          code: "FIELD_VALUE_TYPE_INVALID",
          message: `${field.fieldName} value does not match ${field.fieldType}.`,
        });
        return;
      }
      resultValues.push({
        fieldId: field.fieldId,
        fieldCode: field.fieldCode,
        fieldType: field.fieldType,
        value: value as DynamicFieldValue["value"],
      });
    });

  return {
    valid: errors.length === 0,
    errors,
    values: resultValues,
  };
}

export function isValueCompatible(field: FieldDefinitionVO, value: unknown): boolean {
  switch (field.fieldType) {
    case "TEXT":
    case "TEXTAREA":
    case "AUTO_NO":
      return typeof value === "string";
    case "NUMBER":
    case "MONEY":
      return isFiniteNumber(value);
    case "DATE":
      return typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value);
    case "DATETIME":
      return typeof value === "string" && !Number.isNaN(Date.parse(value));
    case "SELECT":
    case "MEMBER":
    case "DEPT":
      return isPrimitive(value);
    case "SWITCH":
      return typeof value === "boolean";
    case "MULTI_SELECT":
    case "TAG":
      return Array.isArray(value) && value.every(isPrimitive);
    case "RELATION":
      return isPrimitive(value) || isRecord(value) || Array.isArray(value);
    case "ATTACHMENT":
    case "IMAGE":
      return Array.isArray(value) && value.every(isFileBind);
    case "SUB_TABLE":
      return Array.isArray(value);
    case "ADDRESS":
      return typeof value === "string" || isRecord(value);
    case "JSON":
      return isJsonValue(value);
    default:
      return false;
  }
}

function isEmptyValue(value: unknown): boolean {
  return value === undefined || value === null || value === "" || (Array.isArray(value) && value.length === 0);
}

function isFiniteNumber(value: unknown): boolean {
  return (typeof value === "number" || typeof value === "string") && Number.isFinite(Number(value));
}

function isPrimitive(value: unknown): boolean {
  return typeof value === "string" || typeof value === "number" || typeof value === "boolean";
}

function isFileBind(value: unknown): value is FileBindDTO {
  return isRecord(value) && typeof value.fileId === "string";
}

function isJsonValue(value: unknown): value is JsonValue {
  if (value === null || isPrimitive(value)) {
    return true;
  }
  if (Array.isArray(value)) {
    return value.every(isJsonValue);
  }
  if (isRecord(value)) {
    return Object.values(value).every(isJsonValue);
  }
  return false;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
