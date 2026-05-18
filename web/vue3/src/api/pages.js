import { httpGet, httpPost } from "./http";
function listPagesByApp(appId) {
  return httpGet(`/v1/system/module/pages/apps/${appId}`);
}
function listPagePickerOptions(appId) {
  return httpGet(`/v1/system/module/pages/apps/${appId}/picker`);
}
function getPageDetail(pageId) {
  return httpGet(`/v1/system/module/pages/${pageId}/detail`);
}
function getPageRuntime(pageId) {
  return httpGet(`/v1/system/module/pages/${pageId}/runtime`);
}
function upsertPage(cmd) {
  return httpPost("/v1/system/module/pages/upsert", {
    id: cmd.id ?? null,
    appId: cmd.appId,
    pageCode: cmd.pageCode,
    pageName: cmd.pageName,
    pageType: cmd.pageType,
    routePath: cmd.routePath ?? null,
    configJson: cmd.configJson ?? null,
    formFieldsJson: cmd.formFieldsJson ?? null,
    status: cmd.status ?? 1
  });
}
function deletePages(ids) {
  return httpPost("/v1/system/module/pages/delete", { ids });
}
function upsertPageBlock(cmd) {
  return httpPost("/v1/system/module/pages/blocks/upsert", {
    id: cmd.id ?? null,
    appId: cmd.appId,
    pageId: cmd.pageId,
    blockType: cmd.blockType,
    sortNo: cmd.sortNo ?? 0,
    configJson: cmd.configJson ?? null
  });
}
function deletePageBlocks(ids) {
  return httpPost("/v1/system/module/pages/blocks/delete", { ids });
}
export {
  deletePageBlocks,
  deletePages,
  getPageDetail,
  getPageRuntime,
  listPagePickerOptions,
  listPagesByApp,
  upsertPage,
  upsertPageBlock
};
