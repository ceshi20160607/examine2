import { httpGet, httpPost } from "../api/http";
import { idToString } from "../utils/id.js";
function listPagesByApp(appId) {
  return httpGet(`/v1/system/module/pages/apps/${pathId(appId)}`);
}
function listPagePickerOptions(appId) {
  return httpGet(`/v1/system/module/pages/apps/${pathId(appId)}/picker`);
}
function getPageDetail(pageId) {
  return httpGet(`/v1/system/module/pages/${pathId(pageId)}/detail`);
}
function getPageRuntime(pageId) {
  return httpGet(`/v1/system/module/pages/${pathId(pageId)}/runtime`);
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
function pathId(value) {
  return encodeURIComponent(idToString(value));
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
