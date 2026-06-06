import type { AvailableAction, FieldDefinitionVO, FieldPermission } from "../../api";
import type { FieldPermissionState, FieldWritableRule } from "./types";
import { getDynamicFieldRenderer } from "./renderers";

export function resolveFieldPermission(
  field: FieldDefinitionVO,
  permissions: FieldPermission[] = [],
  writableRules: FieldWritableRule[] = [],
): FieldPermissionState {
  const fieldPermission = lastByCode(permissions, field.fieldCode);
  const definitionPermission = lastByCode(field.fieldPermissions ?? [], field.fieldCode);
  const writableRule = writableRules.find((item) => item.fieldCode === field.fieldCode);
  const renderer = getDynamicFieldRenderer(field.fieldType);
  const visible = (fieldPermission?.visible ?? definitionPermission?.visible ?? true) && field.status !== "DELETED";
  const writableByPermission = fieldPermission?.writable ?? definitionPermission?.writable ?? true;
  const writableByRule = writableRule?.writable ?? true;
  const writable = visible && renderer.writable && writableByPermission && writableByRule && field.status === "ENABLED";
  const readonlyReason =
    valueReason(fieldPermission?.readonlyReason) ??
    valueReason(definitionPermission?.readonlyReason) ??
    valueReason(writableRule?.readonlyReason) ??
    defaultReadonlyReason(field, writable, renderer.writable);

  return {
    fieldCode: field.fieldCode,
    visible,
    writable,
    readonly: !writable,
    readonlyReason: writable ? undefined : readonlyReason,
  };
}

export function resolveAvailableAction(
  actions: AvailableAction[] = [],
  actionCode: string,
): AvailableAction | undefined {
  return actions.find((action) => action.actionCode === actionCode);
}

export function actionDisabledReason(action?: AvailableAction): string | undefined {
  if (!action) {
    return undefined;
  }
  if (!action.visible) {
    return action.disabledReason ?? action.stateReason ?? "Action is hidden by permission or state.";
  }
  if (!action.enabled) {
    return action.disabledReason ?? action.stateReason ?? "Action is disabled by permission or state.";
  }
  return undefined;
}

function lastByCode(items: FieldPermission[], fieldCode: string): FieldPermission | undefined {
  return [...items].reverse().find((item) => item.fieldCode === fieldCode);
}

function valueReason(reason?: string): string | undefined {
  return reason && reason.trim().length > 0 ? reason : undefined;
}

function defaultReadonlyReason(
  field: FieldDefinitionVO,
  writable: boolean,
  rendererWritable: boolean,
): string | undefined {
  if (writable) {
    return undefined;
  }
  if (field.status !== "ENABLED") {
    return `Field status is ${field.status}.`;
  }
  if (!rendererWritable) {
    return "Field type is readonly by contract.";
  }
  return "Field is readonly by permission.";
}

