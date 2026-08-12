import { createHash } from 'node:crypto'
import { createWriteStream } from 'node:fs'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { once } from 'node:events'
import { CONTRACT_ID, CONTRACT_SHA256, fileSha256, sha256, stableJson } from './lib/evidence.mjs'

const arg = (name, fallback = null) => { const index = process.argv.indexOf(name); return index >= 0 ? process.argv[index + 1] : fallback }
const records = Number(arg('--records', '1000000'))
const output = path.resolve(arg('--output') ?? (() => { throw new Error('--output is required') })())
if (!Number.isSafeInteger(records) || records < 100 || records > 1_000_000) throw new Error('--records must be 100..1000000')
const root = path.resolve(import.meta.dirname)
const spec = JSON.parse(await readFile(path.join(root, 'seed/seed-spec.json'), 'utf8'))
if (spec.contractId !== CONTRACT_ID || spec.contractSha256 !== CONTRACT_SHA256 || sha256(CONTRACT_ID) !== CONTRACT_SHA256) throw new Error('Frozen seed contract is invalid')
await mkdir(output, { recursive: true })

const table = (name, columns, idColumn = 0) => ({ name: `${name}.tsv`, columns: columns.split(','), idColumn, rows: 0, minId: null, maxId: null })
const files = {
  accounts: table('un_plat_account', 'id,account_code,username,username_normalized,email,email_normalized,phone,display_name,locale,time_zone,status,last_login_at,created_at,created_by,updated_at,updated_by,deleted_at,deleted_by,version'),
  members: table('un_plat_member', 'id,system_id,account_id,member_code,display_name,default_tenant_id,status,joined_at,created_at,created_by,updated_at,updated_by,deleted_at,version'),
  memberTenants: table('un_plat_member_tenant', 'id,system_id,member_id,tenant_id,status,granted_at,granted_by,expires_at,created_at,created_by,updated_at,updated_by,deleted_at,deleted_by,version'),
  departments: table('un_plat_department', 'id,scope_type,scope_key,system_id,tenant_id,parent_id,department_code,name,sort_order,status,created_at,created_by,updated_at,updated_by,deleted_at,deleted_by,version'),
  departmentClosure: table('un_plat_department_closure', 'id,scope_type,scope_key,tenant_id,ancestor_id,descendant_id,depth,created_at,created_by'),
  memberDepartments: table('un_plat_member_department', 'id,scope_type,scope_key,system_id,tenant_id,account_id,member_id,department_id,is_primary,created_at,created_by,updated_at,updated_by,deleted_at,deleted_by,version'),
  records: table('un_module_record', 'id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,logical_module_id,record_no,title,status,prior_status,owner_member_id,owner_department_id,draft_expires_at,deleted_at,deleted_by,created_at,created_by,updated_at,updated_by,version'),
  values: table('un_module_record_value', 'id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,logical_module_id,field_snapshot_id,logical_field_id,field_version,field_type,ordinal,string_value,text_value,decimal_value,date_value,datetime_value,reference_value,encrypted_value,value_hash,display_value,created_at,created_by,updated_at,updated_by,version'),
  indexes: table('un_module_record_index', 'id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,logical_module_id,logical_field_id,index_generation_id,normalization_generation_id,path_snapshot_id,ordinal,record_status,value_kind,string_value,decimal_value,date_value,datetime_value,boolean_value,reference_value,hash_value,currency_code,created_at,updated_at'),
  search: table('un_module_record_search', 'id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,logical_module_id,logical_field_id,index_generation_id,record_status,field_type,token_ordinal,token,token_hash,created_at'),
  relations: table('un_module_record_relation', 'id,system_id,tenant_id,source_record_id,source_schema_version_id,source_module_snapshot_id,source_logical_module_id,source_field_snapshot_id,source_logical_field_id,source_field_type,source_field_scope,target_record_id,target_schema_version_id,target_module_snapshot_id,target_logical_module_id,ordinal,created_at,created_by,updated_at,updated_by,version'),
  subRecords: table('un_module_sub_record', 'id,system_id,tenant_id,parent_record_id,schema_version_id,module_snapshot_id,logical_module_id,parent_field_snapshot_id,parent_logical_field_id,parent_field_type,parent_field_scope,row_id,client_row_key,ordinal,status,created_at,created_by,updated_at,updated_by,version'),
  subValues: table('un_module_sub_value', 'id,system_id,tenant_id,parent_record_id,schema_version_id,module_snapshot_id,parent_field_snapshot_id,row_id,column_field_snapshot_id,source_field_id,logical_field_id,column_field_type,column_field_scope,ordinal,string_value,text_value,decimal_value,date_value,datetime_value,time_value,boolean_value,currency_code,reference_value,encrypted_value,encryption_key_version,value_hash,hash_key_version,display_value,created_at,created_by,updated_at,updated_by,version'),
  referenceStates: table('un_module_reference_state', 'id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,field_snapshot_id,source_record_id,source_record_version,recalculation_state,failure_correlation_id,updated_at,version'),
  teams: table('un_collab_record_team', 'system_id,tenant_id,record_id,version,created_at,created_by,updated_at,updated_by', 2),
  teamMembers: table('un_collab_record_team_member', 'system_id,tenant_id,record_id,member_id,team_role,row_version,created_at,created_by,updated_at,updated_by', 2),
  comments: table('un_collab_record_comment', 'comment_id,system_id,tenant_id,record_id,parent_comment_id,author_member_id,body,deleted,version,idempotency_key,request_hash,created_at,created_by,updated_at,updated_by,deleted_at,deleted_by'),
}
const streams = Object.fromEntries(Object.entries(files).map(([key, item]) => [key, createWriteStream(path.join(output, item.name), { encoding: 'utf8' })]))
const emit = (key, id, columns) => {
  const item = files[key]
  item.rows++; item.minId ??= id; item.maxId = id
  return streams[key].write(`${columns.join('\t')}\n`) ? null : once(streams[key], 'drain')
}
const iso = spec.fixedTimestamp.replace('T', ' ').replace('Z', '')
const dateOnly = (ordinal) => `2026-${String((ordinal % 12) + 1).padStart(2, '0')}-${String((ordinal % 28) + 1).padStart(2, '0')}`
const text = (ordinal) => `华东-${String((ordinal * 37) % 10000).padStart(4, '0')}-合同2026`
const ownerAccountId = spec.ownerAccountId
for (let ordinal = 1; ordinal <= 10_000; ordinal++) {
  const accountId = spec.accountIdBase + ordinal
  const memberId = spec.memberIdBase + ordinal
  const departmentId = spec.departmentIdBase + ((ordinal - 1) % 50) + 1
  let drain = emit('accounts', accountId, [accountId, `PERF-A${String(ordinal).padStart(5, '0')}`, `perf_member_${String(ordinal).padStart(5, '0')}`, `perf_member_${String(ordinal).padStart(5, '0')}`, `perf-${ordinal}@invalid.example`, `perf-${ordinal}@invalid.example`, '\\N', `性能成员 ${ordinal}`, 'zh-CN', 'Asia/Shanghai', 'ACTIVE', '\\N', iso, '\\N', iso, '\\N', '\\N', '\\N', 0]); if (drain) await drain
  drain = emit('members', memberId, [memberId, spec.systemId, accountId, `M${String(ordinal).padStart(5, '0')}`, `性能成员 ${ordinal}`, spec.tenantId, 'ACTIVE', iso, iso, ownerAccountId, iso, ownerAccountId, '\\N', 0]); if (drain) await drain
  drain = emit('memberTenants', 40_000_000 + ordinal, [40_000_000 + ordinal, spec.systemId, memberId, spec.tenantId, 'ACTIVE', iso, ownerAccountId, '\\N', iso, ownerAccountId, iso, ownerAccountId, '\\N', '\\N', 0]); if (drain) await drain
  drain = emit('memberDepartments', 50_000_000 + ordinal, [50_000_000 + ordinal, 'SYSTEM', spec.systemId, spec.systemId, spec.tenantId, '\\N', memberId, departmentId, 1, iso, ownerAccountId, iso, ownerAccountId, '\\N', '\\N', 0]); if (drain) await drain
}
const departmentParent = (ordinal) => ordinal === 1 ? null : ordinal <= 10 ? 1 : ordinal <= 15 ? 10 : 2 + ((ordinal - 16) % 8)
let closureId = 60_000_000
for (let ordinal = 1; ordinal <= 50; ordinal++) {
  const departmentId = spec.departmentIdBase + ordinal
  const parentOrdinal = departmentParent(ordinal)
  let drain = emit('departments', departmentId, [departmentId, 'SYSTEM', spec.systemId, spec.systemId, spec.tenantId, parentOrdinal == null ? '\\N' : spec.departmentIdBase + parentOrdinal, `D${String(ordinal).padStart(3, '0')}`, `性能部门 ${ordinal}`, ordinal, 'ACTIVE', iso, ownerAccountId, iso, ownerAccountId, '\\N', '\\N', 0]); if (drain) await drain
  let ancestor = ordinal; let depth = 0
  while (ancestor != null) {
    drain = emit('departmentClosure', ++closureId, [closureId, 'SYSTEM', spec.systemId, spec.tenantId, spec.departmentIdBase + ancestor, departmentId, depth++, iso, ownerAccountId]); if (drain) await drain
    ancestor = departmentParent(ancestor)
  }
}
let valueId = 10_000_000_000_000; let indexId = 20_000_000_000_000; let searchId = 30_000_000_000_000
let relationId = 31_000_000_000_000; let subRecordId = 32_000_000_000_000; let subValueId = 33_000_000_000_000; let referenceStateId = 34_000_000_000_000; let commentId = 35_000_000_000_000
for (let ordinal = 1; ordinal <= records; ordinal++) {
  const recordId = spec.recordIdBase + ordinal
  const status = ordinal % 10 === 9 ? 'ARCHIVED' : 'ACTIVE'
  const memberId = ordinal % 100 === 42 ? spec.memberIdBase + 42 : spec.memberIdBase + (ordinal % 10_000) + 1
  const departmentId = spec.departmentIdBase + (ordinal % 50) + 1
  let drain = emit('records', recordId, [recordId, spec.systemId, spec.tenantId, recordId, spec.schemaVersionId, spec.moduleSnapshotId, spec.logicalModuleId, `R-${String(ordinal).padStart(7, '0')}`, `性能记录 ${ordinal}`, status, '\\N', memberId, departmentId, '\\N', '\\N', '\\N', iso, memberId, iso, memberId, 1]); if (drain) await drain
  const typed = [
    ['TEXT', text(ordinal), '\\N', '\\N', '\\N', '\\N'],
    ['NUMBER', '\\N', '\\N', ordinal % 10000, '\\N', '\\N'],
    ['DATE', '\\N', '\\N', '\\N', dateOnly(ordinal), '\\N'],
    ['MEMBER', '\\N', '\\N', '\\N', '\\N', memberId],
    ['DEPARTMENT', '\\N', '\\N', '\\N', '\\N', departmentId],
  ]
  for (let field = 0; field < spec.ordinaryFields; field++) {
    const logicalFieldId = spec.fieldIdBase + field + 1
    const base = typed[field % typed.length]
    const display = base.find((value) => value !== '\\N')
    drain = emit('values', ++valueId, [valueId, spec.systemId, spec.tenantId, recordId, spec.schemaVersionId, spec.moduleSnapshotId, spec.logicalModuleId, logicalFieldId, logicalFieldId, 1, base[0], 0, base[1], base[2], base[3], base[4], '\\N', base[5], '\\N', '\\N', display, iso, memberId, iso, memberId, 0]); if (drain) await drain
  }
  const indexValues = [
    ['STRING', text(ordinal), '\\N', '\\N', '\\N'],
    ['DECIMAL', '\\N', ordinal % 10000, '\\N', '\\N'],
    ['DATE', '\\N', '\\N', dateOnly(ordinal), '\\N'],
    ['REFERENCE', '\\N', '\\N', '\\N', memberId],
    ['REFERENCE', '\\N', '\\N', '\\N', departmentId],
  ]
  for (let field = 0; field < indexValues.length; field++) {
    const value = indexValues[field]
    drain = emit('indexes', ++indexId, [indexId, spec.systemId, spec.tenantId, recordId, spec.schemaVersionId, spec.moduleSnapshotId, spec.logicalModuleId, spec.fieldIdBase + field + 1, 1, 1, 0, 0, status, value[0], value[1], value[2], value[3], '\\N', '\\N', value[4], '\\N', '\\N', iso, iso]); if (drain) await drain
  }
  for (let multi = 0; multi < 2; multi++) for (let item = 0; item < 2; item++) {
    const reference = spec.memberIdBase + ((ordinal + item * 97 + multi * 997) % 10_000) + 1
    drain = emit('indexes', ++indexId, [indexId, spec.systemId, spec.tenantId, recordId, spec.schemaVersionId, spec.moduleSnapshotId, spec.logicalModuleId, spec.fieldIdBase + 6 + multi, 1, 1, 0, item, status, 'REFERENCE', '\\N', '\\N', '\\N', '\\N', '\\N', reference, '\\N', '\\N', iso, iso]); if (drain) await drain
  }
  const token = ordinal % 100 === 0 ? '合同2026' : `记录${ordinal % 10000}`
  drain = emit('search', ++searchId, [searchId, spec.systemId, spec.tenantId, recordId, spec.schemaVersionId, spec.moduleSnapshotId, spec.logicalModuleId, spec.fieldIdBase + 1, 1, status, 'TEXT', 0, token, sha256(token), iso]); if (drain) await drain
  if (ordinal % 10 < 3) {
    for (let item = 1; item <= 2; item++) {
      const targetOrdinal = ((ordinal + item * 7919 - 1) % records) + 1
      const targetRecordId = spec.recordIdBase + targetOrdinal
      drain = emit('relations', ++relationId, [relationId, spec.systemId, spec.tenantId, recordId, spec.schemaVersionId, spec.moduleSnapshotId, spec.logicalModuleId, spec.relationFieldSnapshotId, spec.relationFieldSnapshotId, 'RELATION', 'RECORD', targetRecordId, spec.schemaVersionId, spec.moduleSnapshotId, spec.logicalModuleId, item - 1, iso, memberId, iso, memberId, 0]); if (drain) await drain
    }
    const targetOrdinal = ((ordinal + 104729 - 1) % records) + 1
    drain = emit('referenceStates', ++referenceStateId, [referenceStateId, spec.systemId, spec.tenantId, recordId, spec.schemaVersionId, spec.moduleSnapshotId, spec.referenceFieldSnapshotId, spec.recordIdBase + targetOrdinal, 1, 'READY', '\\N', iso, 0]); if (drain) await drain
  }
  if (ordinal % 5 === 0) for (let item = 0; item < 5; item++) {
    const rowId = ++subRecordId
    drain = emit('subRecords', rowId, [rowId, spec.systemId, spec.tenantId, recordId, spec.schemaVersionId, spec.moduleSnapshotId, spec.logicalModuleId, spec.subtableFieldSnapshotId, spec.subtableFieldSnapshotId, 'SUBTABLE', 'RECORD', rowId, `perf-${ordinal}-${item}`, item, 'ACTIVE', iso, memberId, iso, memberId, 0]); if (drain) await drain
    const subText = `子表 ${ordinal}-${item}`
    drain = emit('subValues', ++subValueId, [subValueId, spec.systemId, spec.tenantId, recordId, spec.schemaVersionId, spec.moduleSnapshotId, spec.subtableFieldSnapshotId, rowId, spec.subtableColumnFieldSnapshotId, spec.subtableColumnFieldSnapshotId, spec.subtableColumnFieldSnapshotId, 'TEXT', 'SUBTABLE_COLUMN', 0, subText, '\\N', '\\N', '\\N', '\\N', '\\N', '\\N', '\\N', '\\N', '\\N', '\\N', '\\N', '\\N', subText, iso, memberId, iso, memberId, 0]); if (drain) await drain
  }
  if (ordinal % 10 === 3) {
    drain = emit('teams', recordId, [spec.systemId, spec.tenantId, recordId, 1, iso, memberId, iso, memberId]); if (drain) await drain
    for (let item = 0; item < 3; item++) {
      const teamMemberId = item === 0 ? memberId : spec.memberIdBase + ((ordinal + item * 97 - 1) % 10_000) + 1
      drain = emit('teamMembers', recordId, [spec.systemId, spec.tenantId, recordId, teamMemberId, item === 0 ? 'OWNER' : 'COLLABORATOR', 1, iso, memberId, iso, memberId]); if (drain) await drain
    }
  }
  if (ordinal % 10 === 4) for (let item = 0; item < 5; item++) {
    const author = spec.memberIdBase + ((ordinal + item * 131 - 1) % 10_000) + 1
    const body = `性能评论 ${ordinal}-${item}`
    const idempotencyKey = `perf-seed-${ordinal}-${item}`
    drain = emit('comments', ++commentId, [commentId, spec.systemId, spec.tenantId, recordId, '\\N', author, body, 0, 1, idempotencyKey, sha256(body), iso, author, iso, author, '\\N', '\\N']); if (drain) await drain
  }
}
await Promise.all(Object.values(streams).map(async (stream) => { stream.end(); await once(stream, 'finish') }))
const scriptSha256 = await fileSha256(new URL(import.meta.url))
for (const item of Object.values(files)) item.sha256 = await fileSha256(path.join(output, item.name))
const manifest = {
  schemaVersion: 1, contractId: CONTRACT_ID, seedSha256: CONTRACT_SHA256,
  recordCount: records, releaseEligible: records === 1_000_000,
  fixedTimestamp: spec.fixedTimestamp, generatorSha256: scriptSha256,
  specSha256: await fileSha256(path.join(root, 'seed/seed-spec.json')),
  shape: { systems: 1, tenants: 1, modules: 1, records, ordinaryValuesPerRecord: 20, archivedRecords: Math.floor((records + 1) / 10), members: 10_000, departments: 50, singleValueIndexesPerRecord: 5, multiValueIndexFields: 2, multiValueEntriesPerField: 2, relationRecords: Math.floor(records / 10) * 3 + Math.min(records % 10, 2), relationsPerSelectedRecord: 2, subtableRecords: Math.floor(records / 5), subRowsPerSelectedRecord: 5, teamRecords: Math.floor((records + 7) / 10), teamMembersPerSelectedRecord: 3, commentRecords: Math.floor((records + 6) / 10), commentsPerSelectedRecord: 5 },
  prerequisites: ['system/tenant/module/schema snapshots including relation, subtable and reference fields', 'the system owner account identified by ownerAccountId'],
  tables: files,
}
manifest.manifestSha256 = sha256(stableJson(manifest))
await writeFile(path.join(output, 'seed-manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`, 'utf8')
await writeFile(path.join(output, 'fixture-fragment.json'), `${JSON.stringify({
  systemId: spec.systemId, tenantId: spec.tenantId, schemaVersionId: spec.schemaVersionId,
  moduleSnapshotId: spec.moduleSnapshotId, logicalModuleId: spec.logicalModuleId,
  moduleCode: 'performance_records', member42Id: spec.memberIdBase + 42,
  department10Id: spec.departmentIdBase + 10,
  detailRecordIds: [1, 2, 3, 4, 5].map((ordinal) => spec.recordIdBase + ordinal), seedManifest: manifest,
}, null, 2)}\n`, 'utf8')
const loadOrder = ['accounts', 'members', 'memberTenants', 'departments', 'departmentClosure', 'memberDepartments', 'records', 'values', 'indexes', 'search', 'relations', 'subRecords', 'subValues', 'referenceStates', 'teams', 'teamMembers', 'comments']
await writeFile(path.join(output, 'load-seed.mysql.sql'), loadOrder.map((key) => { const item = files[key]; return `LOAD DATA LOCAL INFILE '${item.name}' INTO TABLE ${path.basename(item.name, '.tsv')} CHARACTER SET utf8mb4 FIELDS TERMINATED BY '\\t' LINES TERMINATED BY '\\n' (${item.columns.join(',')});` }).join('\n') + '\n', 'utf8')
console.log(JSON.stringify({ output, manifest }, null, 2))
