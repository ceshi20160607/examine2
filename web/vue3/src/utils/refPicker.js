import { getRecord, queryRecords, queryRecordsByRelation } from "../api/records";
import { listRelationsByApp } from "../api/meta";
import { configFromMeta } from "./fieldTypes";
import { hasId, idToString, sameId, uniqueIds } from "./id";
function recordDisplayLabel(detail, displayField, inlineData) {
  const data = inlineData || detail?.data || {};
  const id = detail?.record?.id;
  if (displayField) {
    const v = data[displayField];
    if (v != null && String(v).trim()) return String(v);
  }
  for (const k of Object.keys(data)) {
    const v = data[k];
    if (v == null || v === "") continue;
    if (typeof v === "object") continue;
    return String(v);
  }
  return id ? `#${id}` : "-";
}
function fieldIncludeCodes(field) {
  const cfg = configFromMeta(field);
  const codes = [];
  const show = field.refDisplayField;
  if (show) codes.push(String(show));
  const listFields = cfg.listFields;
  if (Array.isArray(listFields)) {
    for (const c of listFields) {
      if (c && !codes.includes(c)) codes.push(String(c));
    }
  }
  return codes.slice(0, 30);
}
async function batchLoadRecordDataMap(appId, modelId, recordIds, fieldCodes) {
  const ids = uniqueIds(recordIds);
  const codes = fieldCodes.filter(Boolean).slice(0, 30);
  if (!appId || !modelId || !ids.length || !codes.length) return {};
  const r = await queryRecords({
    appId,
    modelId,
    page: 1,
    limit: Math.min(ids.length, 100),
    filters: [{ field: "id", op: "in", values: ids }],
    includeFieldCodes: codes
  });
  const map = {};
  for (const row of r.data?.list || []) {
    const id = idToString(row.id);
    if (hasId(id)) map[id] = row.data || {};
  }
  return map;
}
async function loadRefSelectOptions(params) {
  const refModelId = params.field.refModelId;
  if (!refModelId || !params.appId) return [];
  const includeFieldCodes = fieldIncludeCodes(params.field);
  const r = await queryRecords({
    appId: params.appId,
    modelId: refModelId,
    page: 1,
    limit: params.limit ?? 50,
    includeFieldCodes
  });
  const list = r.data?.list || [];
  const displayField = params.field.refDisplayField;
  return list.map((row) => {
    const id = idToString(row.id);
    if (!hasId(id)) return null;
    return {
      value: id,
      text: recordDisplayLabel(null, displayField, row.data)
    };
  }).filter((x) => x != null);
}
async function resolveRefDisplay(recordId, displayField) {
  const id = idToString(recordId);
  if (!hasId(id)) return "-";
  try {
    const d = await getRecord(id);
    return recordDisplayLabel(d.data, displayField);
  } catch {
    return `#${id}`;
  }
}
function formatCellValue(v) {
  if (v == null || v === "") return "-";
  if (typeof v === "object") return JSON.stringify(v);
  return String(v);
}
function relationIdFromField(field) {
  const cfg = configFromMeta(field);
  const rid = idToString(cfg.relationId);
  return hasId(rid) ? rid : "";
}
async function resolveRelationId(field, parentModelId, appId) {
  const explicit = relationIdFromField(field);
  if (hasId(explicit)) return explicit;
  const dstId = idToString(field.refModelId);
  const srcId = idToString(parentModelId);
  if (!appId || !dstId || !srcId) return "";
  try {
    const r = await listRelationsByApp(appId);
    const list = r.data || [];
    const hit = list.find((rel) => {
      const t = String(rel.relType || "").toLowerCase();
      if (!["1-n", "1-1", "1n", "11", "n-n", "nn"].includes(t)) return false;
      return sameId(rel.srcModelId, srcId) && sameId(rel.dstModelId, dstId);
    });
    return hit?.id ? idToString(hit.id) : "";
  } catch {
    return "";
  }
}
function fkFieldFromRelation(rel) {
  if (!rel?.configJson) return "";
  try {
    const cfg = typeof rel.configJson === "string" ? JSON.parse(rel.configJson) : rel.configJson;
    return cfg?.fkField ? String(cfg.fkField).trim() : "";
  } catch {
    return "";
  }
}
function rowMapFromQueryList(list, columns, field, options) {
  const optById = new Map(options.map((o) => [idToString(o.value), o.text]));
  const map = {};
  for (const row of list) {
    const id = idToString(row.id);
    if (!hasId(id)) continue;
    const data = row.data || {};
    const cells = {};
    for (const col of columns) {
      if (col.code === "_title") {
        cells._title = optById.get(id) || recordDisplayLabel(null, field.refDisplayField, data) || `#${id}`;
      } else {
        cells[col.code] = formatCellValue(data[col.code]);
      }
    }
    map[id] = { id, cells };
  }
  return map;
}
async function loadSubRowsByRelation(params) {
  const pid = idToString(params.parentRecordId);
  if (!hasId(pid) || !params.appId) return { ids: [], rowMap: {} };
  const relationId = await resolveRelationId(params.field, params.parentModelId, params.appId);
  if (!relationId) return { ids: [], rowMap: {} };
  const r = await listRelationsByApp(params.appId);
  const rel = (r.data || []).find((x) => sameId(x.id, relationId)) || null;
  const relType = String(rel?.relType || "1-n").toLowerCase();
  const includeFieldCodes = fieldIncludeCodes(params.field);
  const qr = await queryRecordsByRelation({
    relationId,
    parentRecordId: pid,
    query: { page: 1, limit: params.limit ?? 200, includeFieldCodes }
  });
  const list = qr.data?.list || [];
  const ids = uniqueIds(list.map((x) => x.id));
  return {
    ids,
    rowMap: rowMapFromQueryList(list, params.columns, params.field, params.options),
    relationId,
    relType: qr.data?.relType || relType,
    linkRecordIdByDstId: qr.data?.linkRecordIdByDstId || {},
    fkField: fkFieldFromRelation(rel)
  };
}
async function buildRowCellsMap(appId, modelId, recordIds, columns, displayField, options) {
  const codes = columns.map((c) => c.code).filter((code) => code && code !== "_title");
  if (displayField && !codes.includes(displayField)) codes.unshift(displayField);
  const dataMap = await batchLoadRecordDataMap(appId, modelId, recordIds, codes);
  const optById = new Map(options.map((o) => [idToString(o.value), o.text]));
  const out = {};
  for (const rawId of recordIds) {
    const id = idToString(rawId);
    if (!hasId(id)) continue;
    const data = dataMap[id] || {};
    const cells = {};
    for (const col of columns) {
      if (col.code === "_title") {
        cells._title = optById.get(id) || recordDisplayLabel(null, displayField, data) || `#${id}`;
      } else {
        cells[col.code] = formatCellValue(data[col.code]);
      }
    }
    out[id] = { id, cells };
  }
  return out;
}
export {
  batchLoadRecordDataMap,
  buildRowCellsMap,
  fieldIncludeCodes,
  loadRefSelectOptions,
  loadSubRowsByRelation,
  recordDisplayLabel,
  relationIdFromField,
  resolveRefDisplay,
  resolveRelationId,
  rowMapFromQueryList
};
