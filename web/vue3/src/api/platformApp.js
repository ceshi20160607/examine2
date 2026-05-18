import { httpGet, httpPost, httpPut, httpRequest } from "./http";
function listOpenApps() {
  return httpGet("/v1/platform/apps");
}
function getOpenApp(id) {
  return httpGet(`/v1/platform/apps/${id}`);
}
function createOpenApp(body) {
  return httpPost("/v1/platform/apps", body);
}
function updateOpenApp(id, body) {
  return httpPut(`/v1/platform/apps/${id}`, body);
}
function setOpenAppStatus(id, status) {
  return httpPost(`/v1/platform/apps/${id}/status`, { status });
}
function deleteOpenApp(id) {
  return httpRequest("DELETE", `/v1/platform/apps/${id}`);
}
function rotateOpenAppSecret(id) {
  return httpPost(`/v1/platform/apps/${id}/rotate-secret`, {});
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
