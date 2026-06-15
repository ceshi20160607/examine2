import { DYNAMIC_FIELD_TYPES } from "../../api";
import type { DynamicFieldType, JsonValue } from "../../api";
import type { DynamicFieldRendererDefinition } from "./types";

export const DYNAMIC_FIELD_RENDERERS = {
  TEXT: renderer("TEXT", "Text", "text-input", "text-input", true, true, true, false, ["LIKE", "EQ", "NE"]),
  TEXTAREA: renderer("TEXTAREA", "Long text", "text-input", "text-input", false, true, true, false, ["LIKE", "EQ"]),
  NUMBER: renderer("NUMBER", "Number", "number-input", "number-input", true, true, true, false, [
    "EQ",
    "NE",
    "GT",
    "GE",
    "LT",
    "LE",
    "BETWEEN",
  ]),
  MONEY: renderer("MONEY", "Money", "money-input", "money-input", true, true, true, false, [
    "EQ",
    "NE",
    "GT",
    "GE",
    "LT",
    "LE",
    "BETWEEN",
  ]),
  DATE: renderer("DATE", "Date", "date-picker", "date-picker", true, true, true, false, ["EQ", "BETWEEN"]),
  DATETIME: renderer("DATETIME", "Datetime", "datetime-picker", "datetime-picker", true, true, true, false, [
    "EQ",
    "BETWEEN",
  ]),
  SELECT: renderer("SELECT", "Select", "select", "select", true, true, true, false, ["EQ", "NE", "IN"]),
  MULTI_SELECT: renderer("MULTI_SELECT", "Multi select", "multi-select", "multi-select", false, true, true, true, [
    "IN",
  ]),
  SWITCH: renderer("SWITCH", "Switch", "switch", "switch", true, true, true, false, ["EQ", "NE"]),
  MEMBER: renderer("MEMBER", "Member", "member-picker", "member-picker", true, true, true, false, ["EQ", "NE", "IN"]),
  DEPT: renderer("DEPT", "Department", "dept-picker", "dept-picker", true, true, true, false, ["EQ", "NE", "IN"]),
  ATTACHMENT: renderer("ATTACHMENT", "Attachment", "file-uploader", "readonly-text", false, false, true, true, []),
  IMAGE: renderer("IMAGE", "Image", "image-uploader", "readonly-text", false, false, true, true, []),
  AUTO_NO: renderer("AUTO_NO", "Auto number", "auto-number", "readonly-text", true, true, false, false, ["EQ", "LIKE"]),
  RELATION: renderer("RELATION", "Relation", "relation-picker", "relation-picker", true, true, true, true, [
    "EQ",
    "IN",
  ]),
  SUB_TABLE: renderer("SUB_TABLE", "Sub table", "sub-table", "readonly-text", false, false, true, true, []),
  ADDRESS: renderer("ADDRESS", "Address", "address-picker", "text-input", false, true, true, false, ["LIKE", "EQ"]),
  TAG: renderer("TAG", "Tag", "tag-input", "tag-input", false, true, true, true, ["IN"]),
  JSON: renderer("JSON", "JSON", "json-editor", "readonly-text", false, false, true, false, []),
} as const satisfies Record<DynamicFieldType, DynamicFieldRendererDefinition>;

export const SUPPORTED_DYNAMIC_FIELD_TYPES = DYNAMIC_FIELD_TYPES.filter(
  (fieldType) => fieldType in DYNAMIC_FIELD_RENDERERS,
);

export function getDynamicFieldRenderer(fieldType: DynamicFieldType): DynamicFieldRendererDefinition {
  return DYNAMIC_FIELD_RENDERERS[fieldType] ?? DYNAMIC_FIELD_RENDERERS.TEXT;
}

export function formatDynamicFieldValue(fieldType: DynamicFieldType, value: unknown, displayValue?: JsonValue): string {
  if (displayValue !== undefined && displayValue !== null && displayValue !== "") {
    return formatJsonDisplay(displayValue);
  }
  if (value === undefined || value === null || value === "") {
    return "-";
  }
  if (fieldType === "SWITCH") {
    return value === true ? "Yes" : "No";
  }
  if (fieldType === "MONEY" && isFiniteNumber(value)) {
    return Number(value).toFixed(2);
  }
  if ((fieldType === "ATTACHMENT" || fieldType === "IMAGE") && Array.isArray(value)) {
    return value
      .map((item) => (isRecord(item) ? String(item.displayName ?? item.fileName ?? item.fileId ?? "") : ""))
      .filter(Boolean)
      .join(", ");
  }
  return formatJsonDisplay(value as JsonValue);
}

function renderer(
  fieldType: DynamicFieldType,
  label: string,
  formComponent: DynamicFieldRendererDefinition["formComponent"],
  filterComponent: DynamicFieldRendererDefinition["filterComponent"],
  sortable: boolean,
  filterable: boolean,
  writable: boolean,
  acceptsMultipleValues: boolean,
  defaultOperators: DynamicFieldRendererDefinition["defaultOperators"],
): DynamicFieldRendererDefinition {
  return {
    fieldType,
    label,
    formComponent,
    filterComponent,
    detailComponent: "readonly-text",
    historyComponent: "readonly-text",
    sortable,
    filterable,
    writable,
    acceptsMultipleValues,
    defaultOperators,
  };
}

function formatJsonDisplay(value: JsonValue): string {
  if (Array.isArray(value)) {
    return value.map((item) => formatJsonDisplay(item)).join(", ");
  }
  if (isRecord(value)) {
    const label = value.label ?? value.name ?? value.displayName ?? value.title ?? value.value;
    return label === undefined ? JSON.stringify(value) : String(label);
  }
  return String(value);
}

function isFiniteNumber(value: unknown): value is number | string {
  return (typeof value === "number" || typeof value === "string") && Number.isFinite(Number(value));
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
