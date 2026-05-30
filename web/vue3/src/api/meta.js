import { httpGet, httpPost } from "../api/http";
import { idToString } from "../utils/id.js";
function listApps() {
  return httpGet("/v1/system/module/meta/apps");
}
function upsertApp(cmd) {
  return httpPost("/v1/system/module/meta/apps/upsert", {
    id: cmd.id ?? null,
    appCode: cmd.appCode,
    appName: cmd.appName,
    iconUrl: cmd.iconUrl ?? null,
    publishedFlag: cmd.publishedFlag ?? 0,
    remark: cmd.remark ?? null,
    status: cmd.status ?? 1
  });
}
function deleteApps(ids) {
  return httpPost("/v1/system/module/meta/apps/delete", { ids });
}
function listModelsByApp(appId) {
  return httpGet(`/v1/system/module/meta/apps/${pathId(appId)}/models`);
}
function upsertModel(cmd) {
  return httpPost("/v1/system/module/meta/models/upsert", {
    id: cmd.id ?? null,
    appId: cmd.appId,
    modelCode: cmd.modelCode,
    modelName: cmd.modelName,
    status: cmd.status ?? 1,
    remark: cmd.remark ?? null
  });
}
function listFieldsByModel(modelId) {
  return httpGet(`/v1/system/module/meta/models/${pathId(modelId)}/fields`);
}
function deleteModels(ids) {
  return httpPost("/v1/system/module/meta/models/delete", { ids });
}
function listFieldTypeDefinitions() {
  return httpGet("/v1/system/module/meta/field-types");
}
function upsertField(cmd) {
  return httpPost("/v1/system/module/meta/fields/upsert", cmd);
}
function deleteFields(ids) {
  return httpPost("/v1/system/module/meta/fields/delete", { ids });
}
function listRelationsByApp(appId) {
  return httpGet(`/v1/system/module/meta/apps/${pathId(appId)}/relations`);
}
function upsertRelation(cmd) {
  return httpPost("/v1/system/module/meta/relations/upsert", cmd);
}
function deleteRelations(ids) {
  return httpPost("/v1/system/module/meta/relations/delete", { ids });
}
function pathId(value) {
  return encodeURIComponent(idToString(value));
}
export {
  deleteApps,
  deleteFields,
  deleteModels,
  deleteRelations,
  listApps,
  listFieldTypeDefinitions,
  listFieldsByModel,
  listModelsByApp,
  listRelationsByApp,
  upsertApp,
  upsertField,
  upsertModel,
  upsertRelation
};
