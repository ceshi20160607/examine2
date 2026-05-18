import { httpGet, httpPost } from "./http";
function listDepts(appId) {
  return httpGet(`/v1/system/module/depts/apps/${appId}`);
}
function listDeptPickerOptions(appId) {
  return httpGet(`/v1/system/module/depts/apps/${appId}/picker`);
}
function upsertDept(appId, cmd) {
  return httpPost(`/v1/system/module/depts/apps/${appId}/upsert`, cmd);
}
function deleteDepts(ids) {
  return httpPost("/v1/system/module/depts/delete", { ids });
}
export {
  deleteDepts,
  listDeptPickerOptions,
  listDepts,
  upsertDept
};
