import { buildApiUrl, httpGet, httpPost } from "./http";
function listDictsByApp(appId) {
  return httpGet(`/v1/system/module/dicts/apps/${appId}`);
}
function upsertDict(appId, cmd) {
  return httpPost(`/v1/system/module/dicts/apps/${appId}/upsert`, {
    id: cmd.id ?? null,
    dictCode: cmd.dictCode,
    dictName: cmd.dictName,
    status: cmd.status ?? 1,
    remark: cmd.remark ?? null
  });
}
function listDictItems(dictId) {
  return httpGet(`/v1/system/module/dicts/${dictId}/items`);
}
function upsertDictItem(dictId, cmd) {
  return httpPost(`/v1/system/module/dicts/${dictId}/items/upsert`, {
    id: cmd.id ?? null,
    itemValue: cmd.itemValue,
    itemLabel: cmd.itemLabel,
    sortNo: cmd.sortNo ?? 0,
    status: cmd.status ?? 1
  });
}
function listViewsByModel(modelId) {
  return httpGet(`/v1/system/module/list-views/models/${modelId}`);
}
function upsertListView(cmd) {
  return httpPost("/v1/system/module/list-views/upsert", {
    id: cmd.id ?? null,
    appId: cmd.appId,
    modelId: cmd.modelId,
    platId: cmd.platId ?? null,
    viewCode: cmd.viewCode,
    viewName: cmd.viewName,
    defaultFlag: cmd.defaultFlag ?? 0,
    status: cmd.status ?? 1
  });
}
function listViewCols(viewId) {
  return httpGet(`/v1/system/module/list-views/${viewId}/cols`);
}
function upsertViewCol(cmd) {
  return httpPost("/v1/system/module/list-views/cols/upsert", {
    id: cmd.id ?? null,
    viewId: cmd.viewId,
    fieldId: cmd.fieldId,
    colTitle: cmd.colTitle,
    width: cmd.width ?? null,
    sortNo: cmd.sortNo ?? 0,
    visibleFlag: cmd.visibleFlag ?? 1,
    fixedType: cmd.fixedType ?? null,
    formatJson: cmd.formatJson ?? null
  });
}
function listFilterTpls(modelId) {
  return httpGet(`/v1/system/module/list-views/models/${modelId}/filter-tpls`);
}
function upsertFilterTpl(cmd) {
  return httpPost("/v1/system/module/list-views/filter-tpls/upsert", {
    id: cmd.id ?? null,
    appId: cmd.appId,
    modelId: cmd.modelId,
    menuId: cmd.menuId ?? null,
    tplCode: cmd.tplCode,
    tplName: cmd.tplName,
    status: cmd.status ?? 1
  });
}
function listExportTplsByModel(modelId) {
  return httpGet(`/v1/system/module/exports/models/${modelId}/tpls`);
}
function upsertExportTpl(cmd) {
  return httpPost("/v1/system/module/exports/tpls/upsert", {
    id: cmd.id ?? null,
    appId: cmd.appId,
    modelId: cmd.modelId,
    menuId: cmd.menuId ?? null,
    tplCode: cmd.tplCode,
    tplName: cmd.tplName,
    fileType: cmd.fileType ?? "csv",
    status: cmd.status ?? 1
  });
}
function deleteExportTpl(ids) {
  return httpPost("/v1/system/module/exports/tpls/delete", { ids });
}
function listExportTplFields(tplId) {
  return httpGet(`/v1/system/module/exports/tpls/${tplId}/fields`);
}
function upsertExportTplField(cmd) {
  return httpPost("/v1/system/module/exports/fields/upsert", {
    id: cmd.id ?? null,
    tplId: cmd.tplId,
    fieldId: cmd.fieldId,
    colTitle: cmd.colTitle,
    sortNo: cmd.sortNo ?? 0,
    formatJson: cmd.formatJson ?? null
  });
}
function deleteExportTplField(ids) {
  return httpPost("/v1/system/module/exports/fields/delete", { ids });
}
function pageExportJobs(params) {
  const q = [];
  if (params.tplId?.trim()) q.push(`tplId=${encodeURIComponent(params.tplId.trim())}`);
  if (params.modelId?.trim()) q.push(`modelId=${encodeURIComponent(params.modelId.trim())}`);
  if (params.status?.trim()) q.push(`status=${encodeURIComponent(params.status.trim())}`);
  const ext = q.length ? "&" + q.join("&") : "";
  return httpGet(`/v1/system/module/export-jobs/page?page=${params.page}&size=${params.size}${ext}`);
}
function getExportJobDetail(jobId) {
  return httpGet(`/v1/system/module/export-jobs/${jobId}`);
}
function createExportJob(tplId, query) {
  return httpPost(`/v1/system/module/export-jobs/tpls/${tplId}`, query);
}
function buildExportTplCsvUrl(tplId, limit = 200) {
  return buildApiUrl(`/v1/system/module/exports/tpls/${tplId}/export/csv?limit=${limit}`);
}
function listRbacRoles(appId) {
  return httpGet(`/v1/system/module/rbac/apps/${appId}/roles`);
}
function upsertRbacRole(appId, cmd) {
  return httpPost(`/v1/system/module/rbac/apps/${appId}/roles/upsert`, {
    id: cmd.id ?? null,
    roleCode: cmd.roleCode,
    roleName: cmd.roleName,
    status: cmd.status ?? 1,
    dataScope: cmd.dataScope ?? 1
  });
}
function searchRbacAccounts(keyword) {
  return httpGet(`/v1/system/module/rbac/account-search?keyword=${encodeURIComponent(keyword)}`);
}
function listRbacMenus(appId) {
  return httpGet(`/v1/system/module/rbac/apps/${appId}/menus`);
}
function listRuntimeMenus(appId) {
  return httpGet(`/v1/system/module/rbac/apps/${appId}/runtime-menus`);
}
function upsertRbacMenu(appId, cmd) {
  return httpPost(`/v1/system/module/rbac/apps/${appId}/menus/upsert`, {
    id: cmd.id ?? null,
    parentId: cmd.parentId,
    menuName: cmd.menuName,
    pageId: cmd.pageId ?? null,
    sortNo: cmd.sortNo ?? 0,
    visibleFlag: cmd.visibleFlag ?? 1,
    permKey: cmd.permKey ?? null,
    apiPattern: cmd.apiPattern ?? null
  });
}
function listRbacMembers(appId) {
  return httpGet(`/v1/system/module/rbac/apps/${appId}/members`);
}
function assignRbacMemberRole(cmd) {
  return httpPost("/v1/system/module/rbac/members/assign-role", cmd);
}
function listRoleMenuPerms(roleId) {
  return httpGet(`/v1/system/module/rbac/roles/${roleId}/menu-perms`);
}
function setRoleMenuPerms(cmd) {
  return httpPost("/v1/system/module/rbac/roles/menu-perms/set", cmd);
}
function listRolePagePerms(roleId) {
  return httpGet(`/v1/system/module/rbac/roles/${roleId}/page-perms`);
}
function setRolePagePerms(cmd) {
  return httpPost("/v1/system/module/rbac/roles/page-perms/set", cmd);
}
function permPreview(uri) {
  return httpGet(`/v1/system/module/auth/perm-preview?uri=${encodeURIComponent(uri)}`);
}
export {
  assignRbacMemberRole,
  buildExportTplCsvUrl,
  createExportJob,
  deleteExportTpl,
  deleteExportTplField,
  getExportJobDetail,
  listDictItems,
  listDictsByApp,
  listExportTplFields,
  listExportTplsByModel,
  listFilterTpls,
  listRbacMembers,
  listRbacMenus,
  listRbacRoles,
  listRoleMenuPerms,
  listRolePagePerms,
  listRuntimeMenus,
  listViewCols,
  listViewsByModel,
  pageExportJobs,
  permPreview,
  searchRbacAccounts,
  setRoleMenuPerms,
  setRolePagePerms,
  upsertDict,
  upsertDictItem,
  upsertExportTpl,
  upsertExportTplField,
  upsertFilterTpl,
  upsertListView,
  upsertRbacMenu,
  upsertRbacRole,
  upsertViewCol
};
