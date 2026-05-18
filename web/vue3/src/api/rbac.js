import { httpGet } from "./http";
function listMemberPickerOptions(appId, scope, deptId) {
  const qs = [];
  if (scope) qs.push(`scope=${encodeURIComponent(scope)}`);
  if (deptId) qs.push(`deptId=${deptId}`);
  const q = qs.length ? `?${qs.join("&")}` : "";
  return httpGet(`/v1/system/module/rbac/apps/${appId}/picker/members${q}`);
}
function listDepartmentPickerOptions(appId) {
  return httpGet(`/v1/system/module/depts/apps/${appId}/picker`);
}
export {
  listDepartmentPickerOptions,
  listMemberPickerOptions
};
