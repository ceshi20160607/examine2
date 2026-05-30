import { httpGet, httpPost, httpRequest } from "../api/http";
import { idToString } from "../utils/id.js";
function queryRecords(cmd) {
  return httpPost("/v1/system/records/query", cmd);
}
function getRecord(recordId) {
  return httpGet(`/v1/system/records/${pathId(recordId)}`);
}
function createRecord(cmd) {
  return httpPost("/v1/system/records", cmd);
}
function updateRecord(recordId, data) {
  return httpPost(`/v1/system/records/${pathId(recordId)}/update`, { data });
}
function deleteRecord(recordId) {
  return httpRequest("DELETE", `/v1/system/records/${pathId(recordId)}`);
}
function listRecordHistory(recordId, limit = 50) {
  return httpGet(`/v1/system/records/${pathId(recordId)}/history?limit=${encodeURIComponent(String(limit))}`);
}
function queryRecordsByRelation(cmd) {
  return httpPost("/v1/system/records/query-by-relation", cmd);
}
function pathId(value) {
  return encodeURIComponent(idToString(value));
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
