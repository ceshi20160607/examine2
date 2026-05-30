import { httpGet } from "../api/http";
import { idToString } from "../utils/id.js";
function listMemberPickerOptions(appId, scope, deptId) {
  const qs = [];
  if (scope) qs.push(`scope=${encodeURIComponent(scope)}`);
  const did = idToString(deptId);
  if (did) qs.push(`deptId=${encodeURIComponent(did)}`);
  const q = qs.length ? `?${qs.join("&")}` : "";
  return httpGet(`/v1/system/module/rbac/apps/${pathId(appId)}/picker/members${q}`);
}
function listDepartmentPickerOptions(appId) {
  return httpGet(`/v1/system/module/depts/apps/${pathId(appId)}/picker`);
}
function pathId(value) {
  return encodeURIComponent(idToString(value));
}
export {
  listDepartmentPickerOptions,
  listMemberPickerOptions
};
