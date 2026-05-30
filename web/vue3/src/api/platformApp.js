import { httpGet, httpPost, httpPut, httpRequest } from "../api/http";
import { idToString } from "../utils/id.js";
function listOpenApps() {
  return httpGet("/v1/platform/apps");
}
function getOpenApp(id) {
  return httpGet(`/v1/platform/apps/${pathId(id)}`);
}
function createOpenApp(body) {
  return httpPost("/v1/platform/apps", body);
}
function updateOpenApp(id, body) {
  return httpPut(`/v1/platform/apps/${pathId(id)}`, body);
}
function setOpenAppStatus(id, status) {
  return httpPost(`/v1/platform/apps/${pathId(id)}/status`, { status });
}
function deleteOpenApp(id) {
  return httpRequest("DELETE", `/v1/platform/apps/${pathId(id)}`);
}
function rotateOpenAppSecret(id) {
  return httpPost(`/v1/platform/apps/${pathId(id)}/rotate-secret`, {});
}
function pathId(value) {
  return encodeURIComponent(idToString(value));
}
export {
  createOpenApp,
  deleteOpenApp,
  getOpenApp,
  listOpenApps,
  rotateOpenAppSecret,
  setOpenAppStatus,
  updateOpenApp
};
