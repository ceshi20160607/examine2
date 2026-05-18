import { httpGet, httpPost, httpRequest } from "./http";
function queryRecords(cmd) {
  return httpPost("/v1/system/records/query", cmd);
}
function getRecord(recordId) {
  return httpGet(`/v1/system/records/${recordId}`);
}
function createRecord(cmd) {
  return httpPost("/v1/system/records", cmd);
}
function updateRecord(recordId, data) {
  return httpPost(`/v1/system/records/${recordId}/update`, { data });
}
function deleteRecord(recordId) {
  return httpRequest("DELETE", `/v1/system/records/${recordId}`);
}
function listRecordHistory(recordId, limit = 50) {
  return httpGet(`/v1/system/records/${recordId}/history?limit=${limit}`);
}
function queryRecordsByRelation(cmd) {
  return httpPost("/v1/system/records/query-by-relation", cmd);
}
export {
  createRecord,
  deleteRecord,
  getRecord,
  listRecordHistory,
  queryRecords,
  queryRecordsByRelation,
  updateRecord
};
