import fs from 'node:fs'
import path from 'node:path'
import { createHash } from 'node:crypto'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(scriptDir, '..', '..')
const frontendRequire = createRequire(path.join(root, 'frontend', 'package.json'))
const Ajv2020 = frontendRequire('ajv/dist/2020').default
const addFormats = frontendRequire('ajv-formats').default
const schemaPath = path.join(root, '.cursor', 'session', 'rebuild', 'vs4-api-contract.schema.json')
const schema = JSON.parse(fs.readFileSync(schemaPath, 'utf8'))
const fieldSchemaPath = path.join(root, '.cursor', 'session', 'rebuild', 'vs4-field-contract.schema.json')
const fieldContractPath = path.join(root, '.cursor', 'session', 'rebuild', 'vs4-field-contract.json')
const dbContractPath = path.join(root, '.cursor', 'session', 'rebuild', 'vs4-db-contract.json')
const verificationContractPath = path.join(root, '.cursor', 'session', 'rebuild', 'vs4-verification-contract.json')
const fieldSchema = JSON.parse(fs.readFileSync(fieldSchemaPath, 'utf8'))
const fieldContract = JSON.parse(fs.readFileSync(fieldContractPath, 'utf8'))
const dbContract = JSON.parse(fs.readFileSync(dbContractPath, 'utf8'))
const verificationContract = JSON.parse(fs.readFileSync(verificationContractPath, 'utf8'))

const ajv = new Ajv2020({ allErrors: true, strict: true, strictRequired: false, allowUnionTypes: true, validateFormats: true })
addFormats(ajv)
const runtimeSchema = structuredClone(schema)
delete runtimeSchema['x-endpointContracts']
ajv.addSchema(runtimeSchema)

function fail(message, errors = []) {
  const detail = errors.length ? `\n${ajv.errorsText(errors, { separator: '\n' })}` : ''
  throw new Error(`${message}${detail}`)
}

function dereference(node) {
  if (!node?.$ref) return node
  const prefix = '#/$defs/'
  if (!node.$ref.startsWith(prefix)) fail(`Unsupported local ref ${node.$ref}`)
  const target = schema.$defs[node.$ref.slice(prefix.length)]
  if (!target) fail(`Dangling ref ${node.$ref}`)
  return target
}

function stringSample(node) {
  if (node.pattern === '^[0-9]+$') return '1'
  if (node.pattern === '^[a-f0-9]{64}$') return 'a'.repeat(64)
  if (node.pattern?.includes('module\\.')) return 'module.demo.create'
  if (node.pattern?.includes('[A-Za-z]')) return 'field'
  if (node.format === 'date-time') return '2026-07-15T05:00:00Z'
  if (node.format === 'date') return '2026-07-15'
  if (node.format === 'time') return '09:30:00Z'
  const length = Math.max(node.minLength ?? 0, 2)
  return 'x'.repeat(length)
}

function overlayObject(target, branch, base, stack) {
  const resolved = dereference(branch)
  if (resolved.const !== undefined) return resolved.const
  if (resolved.properties) {
    for (const [key, property] of Object.entries(resolved.properties)) {
      if (resolved.required?.includes(key) || property.const !== undefined) {
        target[key] = sample(property, stack)
      }
    }
  }
  for (const key of resolved.required ?? []) {
    if (!(key in target)) target[key] = sample(resolved.properties?.[key] ?? base.properties?.[key] ?? {}, stack)
  }
  return target
}

function sample(input, stack = []) {
  const node = dereference(input)
  if (node !== input && input.$ref) {
    const name = input.$ref.slice('#/$defs/'.length)
    if (stack.includes(name)) return null
    return sample(node, [...stack, name])
  }
  if (node.const !== undefined) return node.const
  if (node.enum) return node.enum[0]

  const type = Array.isArray(node.type) ? node.type.find((candidate) => candidate !== 'null') : node.type
  let value
  if (type === 'object' || node.properties) {
    value = {}
    for (const key of node.required ?? []) value[key] = sample(node.properties?.[key] ?? {}, stack)
  } else if (type === 'array') {
    value = Array.from({ length: node.minItems ?? 0 }, () => sample(node.items ?? {}, stack))
  } else if (type === 'string') {
    value = stringSample(node)
  } else if (type === 'integer' || type === 'number') {
    value = node.minimum ?? 0
  } else if (type === 'boolean') {
    value = false
  } else if (type === 'null') {
    value = null
  } else if (node.oneOf?.length) {
    return sample(node.oneOf[0], stack)
  } else {
    value = null
  }

  for (const part of node.allOf ?? []) {
    const partValue = sample(part, stack)
    if (value && typeof value === 'object' && !Array.isArray(value) && partValue && typeof partValue === 'object') {
      Object.assign(value, partValue)
    }
  }
  if (node.oneOf?.length && value && typeof value === 'object' && !Array.isArray(value)) {
    value = overlayObject(value, node.oneOf[0], node, stack)
  }
  return value
}

function validatorFor(name) {
  if (!schema.$defs[name]) fail(`Missing DTO ${name}`)
  return ajv.compile({ $ref: `${schema.$id}#/$defs/${name}` })
}

function assertValid(name, value, label = name) {
  const validate = validatorFor(name)
  if (!validate(value)) fail(`Expected valid fixture: ${label}`, validate.errors ?? [])
}

function assertInvalid(name, value, label = name) {
  const validate = validatorFor(name)
  if (validate(value)) fail(`Expected invalid fixture: ${label}`)
}

const rootValidator = ajv.getSchema(schema.$id)
if (!rootValidator) fail('Draft 2020-12 root schema did not compile')
if (!rootValidator({ contractVersion: '1.0' })) fail('Root positive fixture failed', rootValidator.errors ?? [])
if (rootValidator({ contractVersion: '1.0', unknown: true })) fail('Root additional property was accepted')

const endpoints = schema['x-endpointContracts']
const expectedEndpointIds = [
  'record-schema', 'record-query', 'record-detail', 'record-create', 'record-update', 'record-autosave', 'record-activate',
  'record-archive', 'record-unarchive', 'record-trash', 'record-restore-trash', 'record-discard', 'record-recover',
  'record-duplicate', 'record-transfer', 'my-drafts-query', 'record-neighbors', 'batch-capabilities', 'batch-edit',
  'batch-transfer', 'batch-archive', 'batch-trash', 'relation-candidate-list', 'relation-list', 'relation-mutate', 'subtable-list', 'subtable-mutate',
  'team-list', 'team-mutate', 'comment-list', 'comment-create', 'comment-update', 'comment-delete', 'history-list',
  'saved-view-list', 'saved-view-create', 'saved-view-update', 'saved-view-delete', 'favorite-list', 'favorite-create',
  'favorite-delete', 'recent-list', 'global-search'
]
const expectedActions = new Map([
  ['record-create', ['CREATE', 'CREATE_DRAFT', 'module.{module}.create', 'NO_RECORD']],
  ['record-update', ['UPDATE', 'UPDATE_RECORD', 'module.{module}.update', 'DRAFT,ACTIVE']],
  ['record-autosave', ['UPDATE', 'AUTOSAVE_DRAFT', 'module.{module}.update', 'DRAFT']],
  ['record-activate', ['UPDATE', 'ACTIVATE_DRAFT', 'module.{module}.update', 'DRAFT']],
  ['record-archive', ['CUSTOM', 'ARCHIVE', 'module.{module}.action.archive', 'ACTIVE']],
  ['record-unarchive', ['CUSTOM', 'UNARCHIVE', 'module.{module}.action.unarchive', 'ARCHIVED']],
  ['record-trash', ['DELETE', 'TRASH_RECORD', 'module.{module}.delete', 'DRAFT,ACTIVE,ARCHIVED,EXPIRED']],
  ['record-restore-trash', ['CUSTOM', 'RESTORE_TRASH', 'module.{module}.action.restore_trash', 'TRASHED']],
  ['record-discard', ['DELETE', 'DISCARD_DRAFT', 'module.{module}.delete', 'DRAFT']],
  ['record-recover', ['CUSTOM', 'RECOVER_DRAFT', 'module.{module}.action.recover_draft', 'EXPIRED']],
  ['record-duplicate', ['CUSTOM', 'DUPLICATE', 'module.{module}.action.duplicate', 'ACTIVE,ARCHIVED']],
  ['record-transfer', ['CUSTOM', 'TRANSFER', 'module.{module}.action.transfer', 'ACTIVE']],
  ['batch-edit', ['UPDATE', 'BATCH_EDIT', 'module.{module}.update', 'ACTIVE']],
  ['batch-transfer', ['CUSTOM', 'TRANSFER', 'module.{module}.action.transfer', 'ACTIVE']],
  ['batch-archive', ['CUSTOM', 'ARCHIVE', 'module.{module}.action.archive', 'ACTIVE']],
  ['batch-trash', ['DELETE', 'TRASH_RECORD', 'module.{module}.delete', 'DRAFT,ACTIVE,ARCHIVED,EXPIRED']]
])
const nonMutationPosts = new Set(['record-query', 'my-drafts-query', 'record-neighbors', 'batch-capabilities', 'global-search'])
const recordConflictEndpoints = new Set([
  'record-update', 'record-autosave', 'record-activate', 'record-archive', 'record-unarchive', 'record-trash',
  'record-restore-trash', 'record-discard', 'record-recover', 'record-duplicate', 'record-transfer', 'relation-mutate',
  'subtable-mutate', 'team-mutate'
])
const batchConflictEndpoints = new Set(['batch-edit', 'batch-transfer', 'batch-archive', 'batch-trash'])
const dtoNames = new Set([
  'FilterNode', 'BatchChange', 'RelationInput', 'SubtableInput', 'TeamInput', 'SubRowCreateInput', 'SubRowUpdateInput',
  'TeamRoleChangeInput', 'MutationReceipt', 'FailureReceipt', 'RecordVersionConflictErrorResponse',
  'RateLimitErrorResponse', 'BatchPreconditionErrorResponse', 'RecordMutationReceipt', 'BatchMutationReceipt',
  'CommentMutationReceipt', 'SavedViewMutationReceipt', 'FavoriteMutationReceipt', 'DeleteMutationReceipt'
])

function refName(ref, label) {
  if (typeof ref !== 'string' || !ref.startsWith('#/$defs/')) fail(`Invalid ${label} ref ${ref}`)
  const name = ref.slice('#/$defs/'.length)
  if (!schema.$defs[name]) fail(`Dangling ${label} ref ${ref}`)
  return name
}

function validateEndpointManifest(manifest) {
  if (!Array.isArray(manifest) || manifest.length !== expectedEndpointIds.length) fail(`Expected exactly ${expectedEndpointIds.length} endpoint contracts, got ${manifest?.length}`)
  const endpointValidator = validatorFor('EndpointContract')
  const endpointIds = new Set()
  const endpointKeys = new Set()
  for (const endpoint of manifest) {
    if (!endpointValidator(endpoint)) fail(`Invalid EndpointContract ${endpoint.id ?? '<unknown>'}`, endpointValidator.errors ?? [])
    if (endpointIds.has(endpoint.id)) fail(`Duplicate endpoint id ${endpoint.id}`)
    endpointIds.add(endpoint.id)
    const key = `${endpoint.method} ${endpoint.path}`
    if (endpointKeys.has(key)) fail(`Duplicate endpoint ${key}`)
    endpointKeys.add(key)

    for (const [label, ref] of [['path', endpoint.pathParamsRef], ['query', endpoint.queryRef], ['body', endpoint.bodyRef], ['response', endpoint.responseRef]]) {
      if (ref === null) continue
      dtoNames.add(refName(ref, label))
    }
    for (const ref of Object.values(endpoint.errorRefs)) dtoNames.add(refName(ref, 'error'))

    const authName = refName(endpoint.authorizationRef, 'authorization')
    if (!authName.startsWith('Auth')) fail(`Endpoint ${endpoint.id} authorization must target an Auth definition`)
    const authConstants = schema.$defs[authName]?.allOf?.[1]?.properties
    if (!authConstants || Object.values(authConstants).some((property) => !Object.hasOwn(property, 'const'))) fail(`Authorization ${authName} must be a constant policy`)
    const authFixture = Object.fromEntries(Object.entries(authConstants).map(([name, property]) => [name, property.const]))
    assertValid(authName, authFixture, `${endpoint.id} authorization`)

    const pathParamName = refName(endpoint.pathParamsRef, 'path')
    const expectedPathParams = [...endpoint.path.matchAll(/\{([A-Za-z][A-Za-z0-9]*)\}/g)].map((match) => match[1]).sort()
    const actualPathParams = [...(schema.$defs[pathParamName].required ?? [])].sort()
    if (JSON.stringify(actualPathParams) !== JSON.stringify(expectedPathParams)) fail(`Path parameter mismatch in ${endpoint.id}`)
    if (endpoint.method === 'GET' && endpoint.bodyRef !== null) fail(`GET endpoint ${endpoint.id} cannot have a request body`)

    const isMutation = ['POST', 'PUT', 'DELETE'].includes(endpoint.method) && !nonMutationPosts.has(endpoint.id)
    if (isMutation && (!endpoint.csrf || !endpoint.idempotency)) fail(`Mutation ${endpoint.id} must require CSRF and idempotency`)
    if (!isMutation && (endpoint.csrf || endpoint.idempotency)) fail(`Read/query endpoint ${endpoint.id} cannot claim mutation controls`)
    if (endpoint.errorRefs['429'] !== '#/$defs/RateLimitErrorResponse') fail(`Endpoint ${endpoint.id} must bind the typed rate-limit error`)
    if (recordConflictEndpoints.has(endpoint.id) && endpoint.errorRefs['409'] !== '#/$defs/RecordVersionConflictErrorResponse') fail(`Endpoint ${endpoint.id} must bind the typed record conflict error`)
    if (batchConflictEndpoints.has(endpoint.id) && endpoint.errorRefs['409'] !== '#/$defs/BatchPreconditionErrorResponse') fail(`Endpoint ${endpoint.id} must bind the typed batch conflict error`)

    const expectedAction = expectedActions.get(endpoint.id)
    if (!expectedAction && endpoint.action !== null) fail(`Unexpected action mapping for ${endpoint.id}`)
    if (expectedAction) {
      if (!endpoint.action) fail(`Missing action mapping for ${endpoint.id}`)
      const actualAction = [endpoint.action.actionType, endpoint.action.handlerId, endpoint.action.permissionCode, endpoint.action.allowedStates.join(',')]
      if (JSON.stringify(actualAction) !== JSON.stringify(expectedAction)) fail(`Incorrect action mapping for ${endpoint.id}`)
    }
  }
  if (JSON.stringify([...endpointIds].sort()) !== JSON.stringify([...expectedEndpointIds].sort())) fail(`Endpoint id catalog does not match the approved ${expectedEndpointIds.length}-endpoint contract`)
}

validateEndpointManifest(endpoints)

const endpointMissingStatus = structuredClone(endpoints)
delete endpointMissingStatus[0].successStatus
try { validateEndpointManifest(endpointMissingStatus); fail('Endpoint manifest accepted a missing successStatus') } catch (error) { if (error.message === 'Endpoint manifest accepted a missing successStatus') throw error }
const endpointExtraProperty = structuredClone(endpoints)
endpointExtraProperty[0].unknown = true
try { validateEndpointManifest(endpointExtraProperty); fail('Endpoint manifest accepted an unknown property') } catch (error) { if (error.message === 'Endpoint manifest accepted an unknown property') throw error }
const endpointMissingAction = structuredClone(endpoints)
endpointMissingAction.find((endpoint) => endpoint.id === 'record-update').action = null
try { validateEndpointManifest(endpointMissingAction); fail('Endpoint manifest accepted a missing required action') } catch (error) { if (error.message === 'Endpoint manifest accepted a missing required action') throw error }

for (const name of [...dtoNames].sort()) {
  const fixture = sample({ $ref: `#/$defs/${name}` })
  assertValid(name, fixture, `${name} synthesized positive`)
  if (!fixture || typeof fixture !== 'object' || Array.isArray(fixture)) fail(`Public DTO ${name} is not a strict object`)
  assertInvalid(name, { ...fixture, __unexpected: true }, `${name} additional property negative`)
}

const predicate = { kind: 'PREDICATE', fieldCode: 'name', operator: 'EQ', value: 'A' }
assertValid('FilterNode', { kind: 'NOT', children: [predicate] }, 'unary NOT')
assertInvalid('FilterNode', { kind: 'NOT', children: [predicate, predicate] }, 'NOT with two children')

const emptyRecordInput = { schemaVersionId: '1', values: [], relations: [], subtables: [], team: [] }
assertValid('CreateRecordRequest', emptyRecordInput, 'strict create')
assertInvalid('CreateRecordRequest', { ...emptyRecordInput, relations: [{}] }, 'arbitrary relation object')
assertValid('RecordQuery', { schemaVersionId: '1', page: 1, size: 50, recordScope: 'active', q: null, filter: null, sort: [], columns: [] }, 'query without q')
assertInvalid('RecordQuery', { schemaVersionId: '1', page: 1, size: 50, recordScope: 'active', q: 'x', filter: null, sort: [], columns: [] }, 'one-character q')

assertValid('BatchChange', { fieldCode: 'name', operation: 'SET', value: 'A' }, 'batch SET with value')
assertValid('BatchChange', { fieldCode: 'name', operation: 'CLEAR' }, 'batch CLEAR without value')
assertInvalid('BatchChange', { fieldCode: 'name', operation: 'SET' }, 'batch SET missing value')
assertInvalid('BatchChange', { fieldCode: 'name', operation: 'CLEAR', value: null }, 'batch CLEAR with value')

assertValid('BatchResponse', { allApplied: true, items: [{ recordId: '1', resultCode: 'APPLIED', newVersion: 2, historyId: '3' }], correlationId: 'c' }, 'batch success response')
assertValid('BatchResponse', { allApplied: false, items: [{ recordId: '1', resultCode: 'VERSION_STALE' }], correlationId: 'c' }, 'batch rejected response')
assertInvalid('BatchResponse', { allApplied: false, items: [{ recordId: '1', resultCode: 'VERSION_STALE', newVersion: 2 }], correlationId: 'c' }, 'batch rejection leaks success fields')
assertInvalid('BatchResponse', { allApplied: true, items: [{ recordId: '1', resultCode: 'APPLIED' }], correlationId: 'c' }, 'batch success missing receipt fields')

for (const [format, validValue, invalidValues] of [
  ['date', '2026-07-15', ['2026-02-30', '15-07-2026']],
  ['date-time', '2026-07-15T05:00:00Z', ['2026-07-15T05:00:00', '2026-02-30T05:00:00Z']],
  ['time', '09:30:00Z', ['09:30:00', '25:00:00Z']],
  ['uri', 'https://example.com/runtime?q=1', ['not a uri', '://missing-scheme']]
]) {
  const validateFormat = ajv.compile({ type: 'string', format })
  if (!validateFormat(validValue)) fail(`Valid ${format} fixture was rejected`, validateFormat.errors ?? [])
  for (const invalidValue of invalidValues) {
    if (validateFormat(invalidValue)) fail(`Invalid ${format} fixture was accepted: ${invalidValue}`)
  }
}

function semanticHash(value) {
  return createHash('sha256').update(JSON.stringify(value)).digest('hex')
}

function assertJsonEqual(actual, expected, label) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) fail(`${label} does not match the approved contract`)
}

function assertExactKeys(value, expectedKeys, label) {
  assertJsonEqual(Object.keys(value).sort(), [...expectedKeys].sort(), `${label} keys`)
}

function expectRejected(label, source, mutate, validator) {
  const candidate = structuredClone(source)
  mutate(candidate)
  try {
    validator(candidate)
  } catch {
    return
  }
  fail(`${label} mutation was accepted`)
}

const fieldValidator = ajv.compile(fieldSchema)
function validateFieldContract(contract) {
  if (!fieldValidator(contract)) fail('VS4 field contract failed its Draft 2020-12 schema', fieldValidator.errors ?? [])
  const typeNames = contract.types.map((fieldType) => fieldType.type)
  const ordinals = contract.types.map((fieldType) => fieldType.ordinal)
  if (new Set(typeNames).size !== 49 || new Set(ordinals).size !== 49) fail('Field type names and ordinals must both be unique across all 49 types')
  assertJsonEqual([...ordinals].sort((a, b) => a - b), Array.from({ length: 49 }, (_, index) => index + 1), 'Field ordinals')

  for (const fieldType of contract.types) {
    const fixture = contract.fixtureProfiles[fieldType.fixtureProfile]
    if (!fixture) fail(`Missing fixture profile ${fieldType.fixtureProfile} for ${fieldType.type}`)
    if (fieldType.operatorPolicy === 'EXPLICIT') {
      const operatorProfile = contract.operatorVectorProfiles[fieldType.operatorVectorProfile]
      if (!operatorProfile) fail(`Missing operator vector profile for ${fieldType.type}`)
      const vectorKeys = Object.keys(operatorProfile.expectedByOperator)
      const missingOperators = fieldType.operators.filter((operator) => !vectorKeys.some((key) => key === operator || key.startsWith(`${operator}:`)))
      if (missingOperators.length) fail(`${fieldType.type} lacks vectors for operators: ${missingOperators.join(',')}`)
    }
  }

  const resultSchemas = ['STRING', 'DECIMAL', 'INTEGER', 'DATE', 'DATETIME', 'BOOLEAN']
  const derivedTypes = ['REFERENCE', 'FORMULA', 'SUMMARY', 'CALCULATED', 'LOOKUP']
  const executionIds = new Set(contract.derivedExecutionFixtures.map((fixture) => fixture.id))
  if (executionIds.size !== 30) fail('Derived execution fixture ids must be unique')
  for (const fieldType of derivedTypes) {
    const fixtures = contract.derivedExecutionFixtures.filter((fixture) => fixture.fieldType === fieldType)
    assertJsonEqual(fixtures.map((fixture) => fixture.resultSchema).sort(), [...resultSchemas].sort(), `${fieldType} execution result schemas`)
    const profile = contract.fixtureProfiles[contract.derivedFixtureByType[fieldType]]
    if (!profile?.resultVectors) fail(`Missing result vectors for ${fieldType}`)
    assertJsonEqual(profile.resultVectors.map((vector) => vector.resultSchema).sort(), [...resultSchemas].sort(), `${fieldType} result vectors`)
    for (const vector of profile.resultVectors) {
      if (!executionIds.has(vector.executionFixtureId)) fail(`Dangling execution fixture ${vector.executionFixtureId}`)
      const execution = contract.derivedExecutionFixtures.find((fixture) => fixture.id === vector.executionFixtureId)
      if (execution.fieldType !== fieldType || execution.resultSchema !== vector.resultSchema) fail(`Mismatched derived execution fixture ${vector.executionFixtureId}`)
    }
  }
  assertJsonEqual(contract.derivedCompatibilityMatrix.map((entry) => entry.fieldType).sort(), [...derivedTypes].sort(), 'Derived compatibility matrix')
  if (contract.migrationDefaults.rollbackPolicy !== 'CREATE_REVERSE_MIGRATION' || contract.migrationDefaults.forbidDirectGenerationReactivation !== true) fail('Rollback must create a reverse migration and forbid direct generation reactivation')
  for (const edge of contract.migrationEdges) {
    if (edge.reversible && !edge.reverseConverterId) fail(`Reversible migration ${edge.sourceType}->${edge.targetType} lacks a reverse converter`)
  }
}

validateFieldContract(fieldContract)
expectRejected('Missing field case', fieldContract, (candidate) => candidate.caseCatalog.pop(), validateFieldContract)
expectRejected('Malformed MONEY vector', fieldContract, (candidate) => { candidate.fixtureProfiles.money.legalVectors = [{}] }, validateFieldContract)
expectRejected('Incomplete derived execution fixture', fieldContract, (candidate) => { delete candidate.derivedExecutionFixtures[0].sourceRecords }, validateFieldContract)
expectRejected('Unsafe rollback policy', fieldContract, (candidate) => { candidate.migrationDefaults.rollbackPolicy = 'REACTIVATE_SUPERSEDED' }, validateFieldContract)

const expectedTables = [
  'un_module_runtime_schema_module', 'un_module_runtime_schema_field', 'un_module_runtime_index_plan',
  'un_module_runtime_migration_plan', 'un_module_runtime_migration_run', 'un_module_record', 'un_module_record_value',
  'un_module_record_index', 'un_module_record_search', 'un_module_record_unique', 'un_module_record_relation',
  'un_module_sub_record', 'un_module_sub_value', 'un_module_record_team', 'un_module_comment', 'un_module_record_history',
  'un_module_saved_view', 'un_module_favorite', 'un_module_recent', 'un_module_record_sequence'
]
const approvedDbContractHash = '1d08fd48d953eb17fceccfeb81e905aa2180f71b5cb2e21fa659551c86165e3f'
function validateDbContract(contract) {
  assertExactKeys(contract, ['schemaVersion', 'contractId', 'onDeletePolicy', 'externalTargetKeys', 'tables'], 'Database contract')
  if (contract.schemaVersion !== 1 || contract.contractId !== 'VS4-DB-V1' || contract.onDeletePolicy !== 'RESTRICT') fail('Database contract identity or delete policy changed')
  if (semanticHash(contract) !== approvedDbContractHash) fail('Database contract differs from the independently reviewed machine contract')
  assertJsonEqual(contract.tables.map((table) => table.name).sort(), [...expectedTables].sort(), 'Database table catalog')
  const tableByName = new Map(contract.tables.map((table) => [table.name, table]))
  const externalKeys = new Map(contract.externalTargetKeys.map((entry) => [`${entry.table}:${entry.key}`, entry]))
  for (const table of contract.tables) {
    assertExactKeys(table, ['name', 'primaryKey', 'uniqueKeys', 'foreignKeys', 'checks', 'indexes'], `Table ${table.name}`)
    if (!table.primaryKey?.name || !table.primaryKey.columns?.length || !table.uniqueKeys?.length || !table.foreignKeys?.length || !table.checks?.length || !table.indexes?.length) fail(`Incomplete keys/checks/indexes for ${table.name}`)
    const localKeys = [table.primaryKey, ...table.uniqueKeys]
    if (new Set(localKeys.map((key) => key.name)).size !== localKeys.length) fail(`Duplicate key name in ${table.name}`)
    for (const foreignKey of table.foreignKeys) {
      if (foreignKey.onDelete !== 'RESTRICT') fail(`Foreign key ${foreignKey.name} is not RESTRICT`)
      const targetTable = tableByName.get(foreignKey.targetTable)
      const targetKey = targetTable
        ? [targetTable.primaryKey, ...targetTable.uniqueKeys].find((key) => key.name === foreignKey.targetKey)
        : externalKeys.get(`${foreignKey.targetTable}:${foreignKey.targetKey}`)
      if (!targetKey) fail(`Foreign key ${foreignKey.name} targets an unknown candidate key`)
      if (targetKey.columns.length !== foreignKey.columns.length) fail(`Foreign key ${foreignKey.name} column count differs from ${foreignKey.targetKey}`)
    }
  }
  const favorite = tableByName.get('un_module_favorite')
  if (!favorite.foreignKeys.some((key) => key.name === 'fk_favorite_module') || !favorite.foreignKeys.some((key) => key.name === 'fk_favorite_record') || !favorite.foreignKeys.some((key) => key.name === 'fk_favorite_member')) fail('Favorite polymorphic anchors are incomplete')
  const savedView = tableByName.get('un_module_saved_view')
  if (!savedView.foreignKeys.some((key) => key.name === 'fk_saved_view_module') || !savedView.foreignKeys.some((key) => key.name === 'fk_saved_view_member')) fail('Saved view module/member anchors are incomplete')
  const recent = tableByName.get('un_module_recent')
  if (!recent.foreignKeys.some((key) => key.name === 'fk_recent_record') || !recent.foreignKeys.some((key) => key.name === 'fk_recent_member')) fail('Recent record/member anchors are incomplete')
}

validateDbContract(dbContract)
expectRejected('Missing database table', dbContract, (candidate) => candidate.tables.pop(), validateDbContract)
expectRejected('Cascading runtime foreign key', dbContract, (candidate) => { candidate.tables[0].foreignKeys[0].onDelete = 'CASCADE' }, validateDbContract)
expectRejected('Favorite without record anchor', dbContract, (candidate) => { candidate.tables.find((table) => table.name === 'un_module_favorite').foreignKeys.splice(1, 1) }, validateDbContract)

const approvedVerificationHash = '9d8699fe79654e034359fbe228f29e2c5ec8cea08df87fbaf66683f9a99b467e'
const expectedScopeIds = ['SC-01', 'SC-02', 'SC-03', 'SC-04', 'SC-05', 'SC-06', 'SC-07']
const expectedScopeNames = ['ALL', 'SELF', 'PRIMARY_DEPARTMENT', 'DEPARTMENT_TREE', 'SELECTED_DEPARTMENTS', 'SELECTED_MEMBERS', 'FIELD_RULE']
const expectedFailpoints = Array.from({ length: 15 }, (_, index) => `FP${String(index + 1).padStart(2, '0')}_${['RECORD', 'VALUE', 'UNIQUE', 'INDEX', 'SEARCH', 'RELATION', 'SUBTABLE', 'TEAM', 'COMMENT', 'HISTORY', 'SAVED_VIEW', 'FAVORITE', 'RECENT', 'SUCCESS_AUDIT', 'OUTBOX'][index]}`)
const expectedOperations = ['CREATE', 'UPDATE', 'AUTOSAVE', 'ACTIVATE', 'ARCHIVE', 'TRASH', 'UNARCHIVE', 'RESTORE_FROM_TRASH', 'DISCARD', 'RECOVER', 'DUPLICATE', 'RELATION_MUTATE', 'SUBTABLE_MUTATE', 'TEAM_MUTATE', 'TRANSFER', 'COMMENT_MUTATE', 'SAVED_VIEW_MUTATE', 'FAVORITE_MUTATE', 'RECENT_UPSERT', 'BATCH_EDIT', 'BATCH_TRANSFER', 'BATCH_ARCHIVE', 'BATCH_TRASH']
function validateVerificationContract(contract) {
  assertExactKeys(contract, ['schemaVersion', 'contractId', 'scopes', 'scopeOperations', 'scopeVariants', 'searchFixture', 'failpoints', 'performance', 'concurrency', 'databaseTargets', 'generator', 'release', 'capabilityEvidencePolicy'], 'Verification contract')
  if (contract.schemaVersion !== 1 || contract.contractId !== 'VS4-VERIFICATION-V1') fail('Verification contract identity changed')
  if (semanticHash(contract) !== approvedVerificationHash) fail('Verification contract differs from the independently reviewed machine contract')
  assertJsonEqual(contract.scopes.map((scope) => scope.id), expectedScopeIds, 'Scope ids')
  assertJsonEqual(contract.scopes.map((scope) => scope.scope), expectedScopeNames, 'Scope names')
  for (const scope of contract.scopes) {
    if (!scope.expectedIds.length || scope.batchItem.expectedNewVersion !== scope.batchItem.expectedVersion + 1) fail(`Scope ${scope.id} lacks exact read/batch expectations`)
  }
  assertJsonEqual(contract.failpoints.catalog, expectedFailpoints, 'Failpoint catalog')
  assertJsonEqual(contract.failpoints.operations.map((operation) => operation.name), expectedOperations, 'Failpoint operation catalog')
  if (contract.failpoints.roundsPerCombination !== 3) fail('Every failpoint combination must run three rounds')
  for (const operation of contract.failpoints.operations) {
    if (!operation.points.length || operation.points.some((point) => !contract.failpoints.catalog.includes(point))) fail(`Operation ${operation.name} has incomplete failpoint bindings`)
  }
  if (contract.performance.seedRecords !== 1_000_000 || contract.performance.read.vus !== 100 || contract.performance.write.vus !== 20) fail('Performance scale or concurrency changed')
  assertJsonEqual(contract.performance.queries, Array.from({ length: 12 }, (_, index) => `Q${String(index + 1).padStart(2, '0')}`), 'Performance query catalog')
  if (contract.performance.thresholdsMs.listP95 !== 1500 || contract.performance.thresholdsMs.detailP95 !== 800 || contract.performance.thresholdsMs.writeP95 !== 1500) fail('Performance thresholds changed')
  if (contract.concurrency.recordVersionRounds !== 20 || contract.concurrency.idempotencyRounds !== 20 || contract.concurrency.autoNumberConcurrency !== 100) fail('Concurrency repetitions changed')
  assertJsonEqual(contract.databaseTargets.map((target) => target.image), ['mysql:8.0.44', 'mysql:8.4.10'], 'MySQL target images')
  if (contract.databaseTargets.some((target) => !/^sha256:[a-f0-9]{64}$/.test(target.digest))) fail('MySQL target digest is not immutable')
  if (contract.generator.newTables !== 20 || contract.generator.expectedNewFiles !== 100 || contract.generator.expectedTotalFiles !== 340 || contract.generator.legacyDuplicates !== 0) fail('Generator exact-count contract changed')
  if (contract.release.backendPort !== 18080 || contract.release.frontendPort !== 4173 || contract.release.healthPaths.length !== 2 || !contract.release.evidenceRoot.endsWith('/vs4/')) fail('Release target or evidence root changed')
  if (contract.capabilityEvidencePolicy.testManifestPath !== '.cursor/session/evidence/vs4/test-manifest.json' || contract.capabilityEvidencePolicy.requiredManifestFields.length !== 8) fail('Capability evidence manifest policy changed')
}

validateVerificationContract(verificationContract)
expectRejected('Missing scope fixture', verificationContract, (candidate) => candidate.scopes.pop(), validateVerificationContract)
expectRejected('Missing mutation operation', verificationContract, (candidate) => candidate.failpoints.operations.pop(), validateVerificationContract)
expectRejected('Reduced performance seed', verificationContract, (candidate) => { candidate.performance.seedRecords = 1000 }, validateVerificationContract)
expectRejected('Missing release health check', verificationContract, (candidate) => candidate.release.healthPaths.pop(), validateVerificationContract)

process.stdout.write(`VS4 machine contracts passed: endpoints=${endpoints.length} DTOs=${dtoNames.size} fields=${fieldContract.types.length} derivedFixtures=${fieldContract.derivedExecutionFixtures.length} tables=${dbContract.tables.length} failpointOperations=${verificationContract.failpoints.operations.length}\n`)
