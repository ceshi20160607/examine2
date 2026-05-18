import { listRelationsByApp } from "../api/meta.js";
import { getRecord, queryRecords, queryRecordsByRelation } from "../api/records.js";
import { configFromMeta } from "./fieldTypeEnum.js";

/** 关联字段列表/下拉需要批量拉取的 EAV 列 */
function fieldIncludeCodes(field) {
  const cfg = configFromMeta(field);
  const codes = [];
  const show = field?.refDisplayField;
  if (show) codes.push(String(show));
  const listFields = cfg.listFields;
  if (Array.isArray(listFields)) {
    for (const c of listFields) {
      if (c && !codes.includes(c)) codes.push(String(c));
    }
  }
  return codes.slice(0, 30);
}

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

function formatCellValue(v) {
  if (v == null || v === "") return "-";
  if (typeof v === "object") return JSON.stringify(v);
  return String(v);
}

/**
 * 按 recordId 批量拉 EAV（一次 query + includeFieldCodes），返回 id → data。
 */
async function batchLoadRecordDataMap(appId, modelId, recordIds, fieldCodes) {
  const ids = [...new Set(recordIds.map(Number).filter((n) => n > 0))];
  const codes = (fieldCodes || []).filter(Boolean).slice(0, 30);
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
    const id = Number(row.id);
    if (id > 0) map[id] = row.data || {};
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
  return list
    .map((row) => {
      const id = Number(row.id);
      if (!id) return null;
      const text = recordDisplayLabel(null, displayField, row.data);
      return { value: id, text };
    })
    .filter(Boolean);
}

async function resolveRefDisplay(recordId, displayField) {
  const id = Number(recordId);
  if (!id || !Number.isFinite(id)) return "-";
  try {
    const d = await getRecord(id);
    return recordDisplayLabel(d.data, displayField);
  } catch {
    return `#${id}`;
  }
}

/**
 * 子表行展示：批量填充 cells（避免每行 getRecord）。
 */
function relationIdFromField(field) {
  const cfg = configFromMeta(field);
  const rid = Number(cfg.relationId);
  return rid > 0 ? rid : 0;
}

/**
 * 解析子表用的关系 ID：优先 field.config_json.relationId，否则按 源模型→目标模型 自动匹配 1-n/1-1。
 */
async function resolveRelationId(field, parentModelId, appId) {
  const explicit = relationIdFromField(field);
  if (explicit > 0) return explicit;
  const dstId = Number(field?.refModelId);
  const srcId = Number(parentModelId);
  if (!appId || !dstId || !srcId) return 0;
  try {
    const r = await listRelationsByApp(appId);
    const list = r.data || [];
    const hit = list.find((rel) => {
      const t = String(rel.relType || "").toLowerCase();
      if (t !== "1-n" && t !== "1-1" && t !== "1n" && t !== "11") return false;
      return Number(rel.srcModelId) === srcId && Number(rel.dstModelId) === dstId;
    });
    return hit?.id ? Number(hit.id) : 0;
  } catch {
    return 0;
  }
}

async function getRelationMeta(relationId, appId) {
  if (!relationId || !appId) return null;
  const r = await listRelationsByApp(appId);
  return (r.data || []).find((x) => Number(x.id) === Number(relationId)) || null;
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
  const optById = new Map((options || []).map((o) => [Number(o.value), o.text]));
  const map = {};
  for (const row of list || []) {
    const id = Number(row.id);
    if (!id) continue;
    const data = row.data || {};
    const cells = {};
    for (const col of columns) {
      if (col.code === "_title") {
        cells._title =
          optById.get(id) || recordDisplayLabel(null, field?.refDisplayField, data) || `#${id}`;
      } else {
        cells[col.code] = formatCellValue(data[col.code]);
      }
    }
    map[id] = { id, cells };
  }
  return map;
}

/**
 * 1-n/1-1：按关系 + 父记录 ID 拉子表行（含 includeFieldCodes）。
 */
async function loadSubRowsByRelation(params) {
  const { field, appId, parentModelId, parentRecordId, columns, options, limit = 200 } = params;
  const pid = Number(parentRecordId);
  if (!pid || !appId) return { ids: [], rowMap: {} };

  const relationId = await resolveRelationId(field, parentModelId, appId);
  if (!relationId) return { ids: [], rowMap: {} };

  const rel = await getRelationMeta(relationId, appId);
  const relType = String(rel?.relType || "1-n").toLowerCase();
  if (relType === "n-n") {
    return { ids: [], rowMap: {}, relationId, relType };
  }

  const includeFieldCodes = fieldIncludeCodes(field);
  const r = await queryRecordsByRelation({
    relationId,
    parentRecordId: pid,
    query: {
      page: 1,
      limit,
      includeFieldCodes
    }
  });
  const list = r.data?.list || [];
  const ids = list.map((x) => Number(x.id)).filter((n) => n > 0);
  const rowMap = rowMapFromQueryList(list, columns, field, options);
  return { ids, rowMap, relationId, relType, fkField: fkFieldFromRelation(rel) };
}

async function buildRowCellsMap(appId, modelId, recordIds, columns, displayField, options) {
  const codes = columns
    .map((c) => c.code)
    .filter((code) => code && code !== "_title");
  if (displayField && !codes.includes(displayField)) codes.unshift(displayField);

  const dataMap = await batchLoadRecordDataMap(appId, modelId, recordIds, codes);
  const optById = new Map((options || []).map((o) => [Number(o.value), o.text]));
  const out = {};

  for (const id of recordIds) {
    const data = dataMap[id] || {};
    const cells = {};
    for (const col of columns) {
      if (col.code === "_title") {
        cells._title =
          optById.get(id) ||
          recordDisplayLabel(null, displayField, data) ||
          `#${id}`;
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
  fkFieldFromRelation,
  getRelationMeta,
  loadRefSelectOptions,
  loadSubRowsByRelation,
  recordDisplayLabel,
  relationIdFromField,
  resolveRefDisplay,
  resolveRelationId,
  rowMapFromQueryList
};
