import { httpGet, httpPost } from "./http";
function listApps() {
  return httpGet("/v1/system/module/meta/apps");
}
function upsertApp(cmd) {
  return httpPost("/v1/system/module/meta/apps/upsert", {
    appCode: cmd.appCode,
    appName: cmd.appName,
    iconUrl: cmd.iconUrl ?? null,
    publishedFlag: cmd.publishedFlag ?? 0,
    remark: cmd.remark ?? null,
    status: cmd.status ?? 1
  });
}
function listModelsByApp(appId) {
  return httpGet(`/v1/system/module/meta/apps/${appId}/models`);
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
  return httpGet(`/v1/system/module/meta/models/${modelId}/fields`);
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
  return httpGet(`/v1/system/module/meta/apps/${appId}/relations`);
}
function upsertRelation(cmd) {
  return httpPost("/v1/system/module/meta/relations/upsert", cmd);
}
function deleteRelations(ids) {
  return httpPost("/v1/system/module/meta/relations/delete", { ids });
}
export {
  deleteFields,
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
