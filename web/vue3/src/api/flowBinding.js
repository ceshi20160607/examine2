import { httpDelete, httpGet, httpPost } from "../api/http";
import { idToString } from "../utils/id.js";
function listModelFlowBindings(appId, modelId) {
  return httpGet(`/v1/system/module/flow-bindings/apps/${pathId(appId)}/models/${pathId(modelId)}`);
}
function listFlowTempOptions() {
  return httpGet("/v1/system/module/flow-bindings/flow-temps");
}
function upsertModelFlowBinding(cmd) {
  return httpPost("/v1/system/module/flow-bindings/upsert", {
    id: cmd.id ?? null,
    appId: cmd.appId,
    modelId: cmd.modelId,
    triggerAction: cmd.triggerAction,
    tempId: cmd.tempId,
    status: cmd.status ?? 1
  });
}
function deleteModelFlowBinding(id) {
  return httpDelete(`/v1/system/module/flow-bindings/${pathId(id)}`);
}
function pathId(value) {
  return encodeURIComponent(idToString(value));
}
export {
  deleteModelFlowBinding,
  listFlowTempOptions,
  listModelFlowBindings,
  upsertModelFlowBinding
};
