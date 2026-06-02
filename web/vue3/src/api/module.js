import { buildApiUrl, httpGet, httpPost } from "../api/http";
import { idToString } from "../utils/id.js";
function listDictsByApp(appId) {
  return httpGet(`/v1/system/module/dicts/apps/${pathId(appId)}`);
}
function upsertDict(appId, cmd) {
  return httpPost(`/v1/system/module/dicts/apps/${pathId(appId)}/upsert`, {
    id: cmd.id ?? null,
    dictCode: cmd.dictCode,
    dictName: cmd.dictName,
    status: cmd.status ?? 1,
    remark: cmd.remark ?? null
  });
}
function deleteDicts(ids) {
  return httpPost("/v1/system/module/dicts/delete", { ids });
}
function listDictItems(dictId) {
  return httpGet(`/v1/system/module/dicts/${pathId(dictId)}/items`);
}
function upsertDictItem(dictId, cmd) {
  return httpPost(`/v1/system/module/dicts/${pathId(dictId)}/items/upsert`, {
    id: cmd.id ?? null,
    itemValue: cmd.itemValue,
    itemLabel: cmd.itemLabel,
    sortNo: cmd.sortNo ?? 0,
    status: cmd.status ?? 1
  });
}
function deleteDictItems(ids) {
  return httpPost("/v1/system/module/dicts/items/delete", { ids });
}
function listViewsByModel(modelId) {
  return httpGet(`/v1/system/module/list-views/models/${pathId(modelId)}`);
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
function deleteListViews(ids) {
  return httpPost("/v1/system/module/list-views/delete", { ids });
}
function listViewCols(viewId) {
  return httpGet(`/v1/system/module/list-views/${pathId(viewId)}/cols`);
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
function deleteViewCols(ids) {
  return httpPost("/v1/system/module/list-views/cols/delete", { ids });
}
function listFilterTpls(modelId) {
  return httpGet(`/v1/system/module/list-views/models/${pathId(modelId)}/filter-tpls`);
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
function deleteFilterTpls(ids) {
  return httpPost("/v1/system/module/list-views/filter-tpls/delete", { ids });
}
function listFilterFields(tplId) {
  return httpGet(`/v1/system/module/list-views/filter-tpls/${pathId(tplId)}/fields`);
}
function upsertFilterField(cmd) {
  return httpPost("/v1/system/module/list-views/filter-fields/upsert", {
    id: cmd.id ?? null,
    tplId: cmd.tplId,
    fieldId: cmd.fieldId,
    opCode: cmd.opCode ?? "eq",
    defaultValue: cmd.defaultValue ?? null,
    requiredFlag: cmd.requiredFlag ?? 0,
    sortNo: cmd.sortNo ?? 0
  });
}
function deleteFilterFields(ids) {
  return httpPost("/v1/system/module/list-views/filter-fields/delete", { ids });
}
function listExportTplsByModel(modelId) {
  return httpGet(`/v1/system/module/exports/models/${pathId(modelId)}/tpls`);
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
  return httpGet(`/v1/system/module/exports/tpls/${pathId(tplId)}/fields`);
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
  const query = [];
  if (params.tplId?.trim()) query.push(`tplId=${q(params.tplId.trim())}`);
  if (params.modelId?.trim()) query.push(`modelId=${q(params.modelId.trim())}`);
  if (params.status?.trim()) query.push(`status=${q(params.status.trim())}`);
  const ext = query.length ? "&" + query.join("&") : "";
  return httpGet(`/v1/system/module/export-jobs/page?page=${q(params.page)}&size=${q(params.size)}${ext}`);
}
function getExportJobDetail(jobId) {
  return httpGet(`/v1/system/module/export-jobs/${pathId(jobId)}`);
}
function createExportJob(tplId, query) {
  return httpPost(`/v1/system/module/export-jobs/tpls/${pathId(tplId)}`, query);
}
function buildExportTplUrl(tplId, limit = 200, fileType) {
  const suffix = fileType === "csv" || fileType === "xlsx" ? `/${fileType}` : "";
  return buildApiUrl(`/v1/system/module/exports/tpls/${pathId(tplId)}/export${suffix}?limit=${q(limit)}`);
}
function buildExportTplCsvUrl(tplId, limit = 200) {
  return buildExportTplUrl(tplId, limit, "csv");
}
function listRbacRoles(appId) {
  return httpGet(`/v1/system/module/rbac/apps/${pathId(appId)}/roles`);
}
function upsertRbacRole(appId, cmd) {
  return httpPost(`/v1/system/module/rbac/apps/${pathId(appId)}/roles/upsert`, {
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
  return httpGet(`/v1/system/module/rbac/apps/${pathId(appId)}/menus`);
}
function listRuntimeMenus(appId) {
  return httpGet(`/v1/system/module/rbac/apps/${pathId(appId)}/runtime-menus`);
}
function upsertRbacMenu(appId, cmd) {
  return httpPost(`/v1/system/module/rbac/apps/${pathId(appId)}/menus/upsert`, {
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
  return httpGet(`/v1/system/module/rbac/apps/${pathId(appId)}/members`);
}
function assignRbacMemberRole(cmd) {
  return httpPost("/v1/system/module/rbac/members/assign-role", cmd);
}
function listRoleMenuPerms(roleId) {
  return httpGet(`/v1/system/module/rbac/roles/${pathId(roleId)}/menu-perms`);
}
function setRoleMenuPerms(cmd) {
  return httpPost("/v1/system/module/rbac/roles/menu-perms/set", cmd);
}
function listRolePagePerms(roleId) {
  return httpGet(`/v1/system/module/rbac/roles/${pathId(roleId)}/page-perms`);
}
function setRolePagePerms(cmd) {
  return httpPost("/v1/system/module/rbac/roles/page-perms/set", cmd);
}
function permPreview(uri) {
  return httpGet(`/v1/system/auth/perm-preview?uri=${encodeURIComponent(uri)}`);
}
function listModulePermissions() {
  return httpGet("/v1/system/auth/permissions");
}
function pathId(value) {
  return encodeURIComponent(idToString(value));
}
function q(value) {
  return encodeURIComponent(String(value));
}
export {
  assignRbacMemberRole,
  buildExportTplCsvUrl,
  buildExportTplUrl,
  createExportJob,
  deleteDictItems,
  deleteDicts,
  deleteExportTpl,
  deleteExportTplField,
  deleteFilterFields,
  deleteFilterTpls,
  deleteListViews,
  deleteViewCols,
  getExportJobDetail,
  listDictItems,
  listDictsByApp,
  listExportTplFields,
  listExportTplsByModel,
  listFilterFields,
  listFilterTpls,
  listModulePermissions,
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
  upsertFilterField,
  upsertFilterTpl,
  upsertListView,
  upsertRbacMenu,
  upsertRbacRole,
  upsertViewCol
};
