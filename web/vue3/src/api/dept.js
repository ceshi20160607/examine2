import { httpGet, httpPost } from "../api/http";
import { idToString } from "../utils/id.js";
function listDepts(appId) {
  return httpGet(`/v1/system/module/depts/apps/${pathId(appId)}`);
}
function listDeptPickerOptions(appId) {
  return httpGet(`/v1/system/module/depts/apps/${pathId(appId)}/picker`);
}
function upsertDept(appId, cmd) {
  return httpPost(`/v1/system/module/depts/apps/${pathId(appId)}/upsert`, cmd);
}
function deleteDepts(ids) {
  return httpPost("/v1/system/module/depts/delete", { ids });
}
function pathId(value) {
  return encodeURIComponent(idToString(value));
}
export {
  deleteDepts,
  listDeptPickerOptions,
  listDepts,
  upsertDept
};
