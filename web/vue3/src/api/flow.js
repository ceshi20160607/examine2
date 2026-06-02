import { httpGet, httpPost } from "../api/http";
import { idToString } from "../utils/id.js";
function pageTemps(page = 1, size = 20) {
  return httpGet(`/v1/system/flow/temps/page?page=${q(page)}&size=${q(size)}`);
}
function getTemp(id) {
  return httpGet(`/v1/system/flow/temps/${pathId(id)}`);
}
function upsertTemp(cmd) {
  return httpPost("/v1/system/flow/temps/upsert", cmd);
}
function deleteTemps(ids) {
  return httpPost("/v1/system/flow/temps/delete", { ids });
}
function pageTempVers(tempId, page = 1, size = 20) {
  return httpGet(`/v1/system/flow/temp-vers/page?tempId=${pathId(tempId)}&page=${q(page)}&size=${q(size)}`);
}
function getTempVer(id) {
  return httpGet(`/v1/system/flow/temp-vers/${pathId(id)}`);
}
function upsertTempVer(cmd) {
  return httpPost("/v1/system/flow/temp-vers/upsert", cmd);
}
function deleteTempVers(ids) {
  return httpPost("/v1/system/flow/temp-vers/delete", { ids });
}
function publishTempVer(id) {
  return httpPost(`/v1/system/flow/temp-vers/${pathId(id)}/publish`);
}
function loadGraphDesigner(tempVerId) {
  return httpGet(
    `/v1/system/flow/temp-vers/${pathId(tempVerId)}/graph-designer`
  );
}
function saveGraphDesigner(tempVerId, body) {
  return httpPost(
    `/v1/system/flow/temp-vers/${pathId(tempVerId)}/graph-designer`,
    body
  );
}
function startInstance(cmd) {
  return httpPost("/v1/system/flow/instances/start", cmd);
}
function pageInstances(page = 1, size = 20, keyword, params = {}) {
  const kw = (keyword || "").trim();
  const query = [`page=${q(page)}`, `size=${q(size)}`];
  if (kw) {
    query.push(`keyword=${q(kw)}`);
  }
  for (const key of ["status", "bizType", "starterPlatId", "tempId", "rootOnly", "currentNodeKey", "startFrom", "startTo"]) {
    const value = params[key];
    if (value != null && String(value).trim()) {
      query.push(`${key}=${q(String(value).trim())}`);
    }
  }
  return httpGet(`/v1/system/flow/instances/page?${query.join("&")}`);
}
function pageMyInstances(page = 1, size = 20, keyword, params = {}) {
  const kw = (keyword || "").trim();
  const query = [`page=${q(page)}`, `size=${q(size)}`];
  if (kw) {
    query.push(`keyword=${q(kw)}`);
  }
  for (const key of ["onlyRunning", "tempId", "rootOnly", "currentNodeKey", "includeStarted", "includeTodo", "includeCc", "startFrom", "startTo"]) {
    const value = params[key];
    if (value != null && String(value).trim()) {
      query.push(`${key}=${q(String(value).trim())}`);
    }
  }
  return httpGet(`/v1/system/flow/instances/my/page?${query.join("&")}`);
}
function pageTasks(page = 1, size = 20, params = {}) {
  const query = [`page=${q(page)}`, `size=${q(size)}`];
  for (const key of ["status", "recordId", "taskType", "assigneePlatId", "nodeKey", "keyword"]) {
    const value = params[key];
    if (value != null && String(value).trim()) {
      query.push(`${key}=${q(String(value).trim())}`);
    }
  }
  return httpGet(`/v1/system/flow/tasks/page?${query.join("&")}`);
}
function getInstance(instanceId) {
  return httpGet(`/v1/system/flow/instances/${pathId(instanceId)}`);
}
function listInstanceTasks(instanceId) {
  return httpGet(`/v1/system/flow/instances/${pathId(instanceId)}/tasks`);
}
function listInstanceActions(instanceId) {
  return httpGet(`/v1/system/flow/instances/${pathId(instanceId)}/actions`);
}
function listInstanceTraces(instanceId) {
  return httpGet(`/v1/system/flow/instances/${pathId(instanceId)}/traces`);
}
function inboxPending(limit = 50) {
  return httpGet(`/v1/system/flow/inbox/tasks/pending?limit=${q(limit)}`);
}
function inboxCc(limit = 50, onlyUnread) {
  const query = [`limit=${q(limit)}`];
  if (onlyUnread != null) {
    query.push(`onlyUnread=${q(onlyUnread)}`);
  }
  return httpGet(`/v1/system/flow/inbox/cc?${query.join("&")}`);
}
function readInboxCc(taskId) {
  return httpPost(`/v1/system/flow/inbox/cc/${pathId(taskId)}/read`, {});
}
function byBiz(bizType, bizId) {
  return httpGet(`/v1/system/flow/instances/by-biz?bizType=${encodeURIComponent(bizType)}&bizId=${encodeURIComponent(bizId)}`);
}
function byBizWithPending(bizType, bizId) {
  return httpGet(
    `/v1/system/flow/instances/by-biz/with-pending-tasks?bizType=${encodeURIComponent(bizType)}&bizId=${encodeURIComponent(bizId)}`
  );
}
function byBizActionable(bizType, bizId) {
  return httpGet(
    `/v1/system/flow/instances/by-biz/actionable-tasks?bizType=${encodeURIComponent(bizType)}&bizId=${encodeURIComponent(bizId)}`
  );
}
function actTask(path, body) {
  return httpPost(path, body);
}
function pageTempVerNodes(tempVerId) {
  return httpGet(`/v1/system/flow/temp-ver-nodes/page?tempVerId=${pathId(tempVerId)}&page=1&size=200`);
}
function upsertTempVerNode(cmd) {
  return httpPost("/v1/system/flow/temp-ver-nodes/upsert", cmd);
}
function deleteTempVerNodes(ids) {
  return httpPost("/v1/system/flow/temp-ver-nodes/delete", { ids });
}
function pageTempVerLines(tempVerId) {
  return httpGet(`/v1/system/flow/temp-ver-lines/page?tempVerId=${pathId(tempVerId)}&page=1&size=200`);
}
function upsertTempVerLine(cmd) {
  return httpPost("/v1/system/flow/temp-ver-lines/upsert", cmd);
}
function deleteTempVerLines(ids) {
  return httpPost("/v1/system/flow/temp-ver-lines/delete", { ids });
}
function pageTempVerLineConds(lineId) {
  return httpGet(`/v1/system/flow/temp-ver-line-conds/page?lineId=${pathId(lineId)}&page=1&size=200`);
}
function upsertTempVerLineCond(cmd) {
  return httpPost("/v1/system/flow/temp-ver-line-conds/upsert", cmd);
}
function deleteTempVerLineConds(ids) {
  return httpPost("/v1/system/flow/temp-ver-line-conds/delete", { ids });
}
function pageTempVerSettings(tempVerId) {
  return httpGet(`/v1/system/flow/temp-ver-settings/page?tempVerId=${pathId(tempVerId)}&page=1&size=200`);
}
function upsertTempVerSetting(cmd) {
  return httpPost("/v1/system/flow/temp-ver-settings/upsert", cmd);
}
function deleteTempVerSettings(ids) {
  return httpPost("/v1/system/flow/temp-ver-settings/delete", { ids });
}
function pageTempVerNodeSettings(tempVerId) {
  return httpGet(`/v1/system/flow/temp-ver-node-settings/page?tempVerId=${pathId(tempVerId)}&page=1&size=200`);
}
function upsertTempVerNodeSetting(cmd) {
  return httpPost("/v1/system/flow/temp-ver-node-settings/upsert", cmd);
}
function deleteTempVerNodeSettings(ids) {
  return httpPost("/v1/system/flow/temp-ver-node-settings/delete", { ids });
}
function pathId(value) {
  return encodeURIComponent(idToString(value));
}
function q(value) {
  return encodeURIComponent(String(value));
}
export {
  actTask,
  byBiz,
  byBizActionable,
  byBizWithPending,
  deleteTempVerLineConds,
  deleteTempVerLines,
  deleteTempVerNodeSettings,
  deleteTempVerNodes,
  deleteTempVerSettings,
  deleteTempVers,
  deleteTemps,
  getInstance,
  getTemp,
  getTempVer,
  inboxCc,
  inboxPending,
  listInstanceActions,
  listInstanceTasks,
  listInstanceTraces,
  loadGraphDesigner,
  pageInstances,
  pageMyInstances,
  pageTasks,
  pageTempVerLineConds,
  pageTempVerLines,
  pageTempVerNodeSettings,
  pageTempVerNodes,
  pageTempVerSettings,
  pageTempVers,
  pageTemps,
  publishTempVer,
  readInboxCc,
  saveGraphDesigner,
  startInstance,
  upsertTemp,
  upsertTempVer,
  upsertTempVerLine,
  upsertTempVerLineCond,
  upsertTempVerNode,
  upsertTempVerNodeSetting,
  upsertTempVerSetting
};
