export type DataSourceSortDirection = 'ASC' | 'DESC'
export type DataSourceCheckSeverity = 'BLOCKER' | 'WARNING'
export type DataSourceSourceKind = 'NATIVE_MODULE' | 'HTTP_JSON' | 'JDBC_TABLE' | 'MULTI_MODULE_JOIN'
export type DataSourceJoinType = 'INNER' | 'LEFT'
export type DataSourceJoinCardinality = 'ONE_TO_ONE' | 'ONE_TO_MANY' | 'MANY_TO_ONE' | 'MANY_TO_MANY'
export type DataSourceJoinFailureMode = 'FAIL_FAST' | 'ALLOW_PARTIAL_LEFT'
export type DataSourceConnectionCheckCode =
  | 'SUCCESS'
  | 'SAFE_TARGET'
  | 'SECRET_UNAVAILABLE'
  | 'TIMEOUT'
  | 'TLS'
  | 'IO'
  | 'RESPONSE_TOO_LARGE'
  | 'HTTP_STATUS'
  | 'JSON_INVALID'
  | 'CONTRACT_INVALID'
export type HttpJsonSelectableSourceType = 'STRING' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN'
export type JdbcTableSelectableSourceType = HttpJsonSelectableSourceType | 'DATE' | 'TIME' | 'DATETIME'
export type DataSourceProjectionSourceType = JdbcTableSelectableSourceType
export type HttpJsonInferredType = HttpJsonSelectableSourceType | 'UNKNOWN' | 'MIXED'
export type DataSourceSchemaDiscoveryCode = DataSourceConnectionCheckCode
  | 'SCHEMA_EMPTY'
  | 'SCHEMA_TOO_WIDE'
  | 'SCHEMA_FIELD_INVALID'
  | 'SCHEMA_REVIEW_REQUIRED'
export type DataSourceDraftRowsPreviewCode = DataSourceConnectionCheckCode | 'SCHEMA_STALE'

export interface HttpJsonConnection {
  endpoint: string
  authSecretRef?: string | null
  timeoutSeconds: number
}

export interface HttpJsonFieldProjection {
  sourceField: string
  fieldCode: string
  sourceType: HttpJsonSelectableSourceType
}

export interface JdbcTableConnection {
  host: string
  port: number
  databaseName: string
  tableName: string
  usernameConfigured?: boolean
  passwordConfigured?: boolean
  connectTimeoutSeconds: number
  queryTimeoutSeconds: number
}

export interface JdbcTableConnectionInput extends Omit<JdbcTableConnection, 'usernameConfigured' | 'passwordConfigured'> {
  usernameSecretRef: string
  passwordSecretRef: string
}

export interface JdbcTableFieldProjection {
  sourceColumn: string
  fieldCode: string
  sourceType: JdbcTableSelectableSourceType
}

export interface HttpJsonDiscoveredField {
  sourceField: string
  suggestedFieldCode: string | null
  inferredType: HttpJsonInferredType
  nullable: boolean
  selectable: boolean
  issueCode: string | null
}

export interface JdbcTableDiscoveredField {
  sourceColumn: string
  suggestedFieldCode: string | null
  inferredType: JdbcTableSelectableSourceType | 'UNKNOWN'
  nullable: boolean
  selectable: boolean
  issueCode: string | null
}

export interface DataSourceOutputField {
  fieldCode: string
}

export interface DataSourceFixedFilter {
  fieldCode: string
  operator: string
  canonicalValue?: unknown
}

export interface DataSourceDefaultSort {
  fieldCode: string
  direction: DataSourceSortDirection
}

export interface DataSourceJoinInput {
  alias: string
  dataSourceId: string
  dataSourceVersionId: string
}

export interface DataSourceJoinEdge {
  leftAlias: string
  leftFieldCode: string
  rightAlias: string
  rightFieldCode: string
  joinType: DataSourceJoinType
  cardinality: DataSourceJoinCardinality
}

export interface DataSourceJoinProjection {
  sourceAlias: string
  sourceFieldCode: string
  fieldCode: string
  logicalFieldId?: string
  fieldName?: string
  type?: string
  queryType?: string
  numeric?: boolean
  temporal?: boolean
  groupable?: boolean
}

export interface DataSourceMultiModuleJoin {
  inputs: DataSourceJoinInput[]
  edges: DataSourceJoinEdge[]
  projections: DataSourceJoinProjection[]
  failureMode: DataSourceJoinFailureMode
  timeoutSeconds: number
  rowLimit: number
}

export interface DataSourceDraft {
  outputFields: DataSourceOutputField[]
  fixedFilters: DataSourceFixedFilter[]
  defaultSort?: DataSourceDefaultSort | null
  defaultTimeFieldCode?: string | null
  sourceKind?: DataSourceSourceKind | null
  httpJsonConnection?: HttpJsonConnection | null
  httpFieldProjections?: HttpJsonFieldProjection[] | null
  jdbcTableConnection?: JdbcTableConnection | null
  jdbcFieldProjections?: JdbcTableFieldProjection[] | null
  multiModuleJoin?: DataSourceMultiModuleJoin | null
}

export interface DataSourceSummary {
  id: string
  systemId: string
  tenantId: string
  code: string
  moduleId: string
  moduleCode?: string | null
  name: string
  description?: string | null
  draftVersion: number
  activeVersionId?: string | null
  activeVersionNumber?: number | null
  createdAt: string
  updatedAt: string
  version: number
  sourceKind?: DataSourceSourceKind | null
  draft?: DataSourceDraft
}

export interface DataSourceDetail extends DataSourceSummary {
  draft: DataSourceDraft
}

export interface CreateDataSourceInput {
  code: string
  moduleId: string
  name: string
  description?: string | null
  sourceKind?: DataSourceSourceKind
  httpJsonConnection?: HttpJsonConnection | null
  jdbcTableConnection?: JdbcTableConnectionInput | null
  multiModuleJoin?: DataSourceMultiModuleJoin | null
}

export interface SaveDataSourceDraftInput extends Omit<DataSourceDraft, 'jdbcTableConnection'> {
  expectedVersion: number
  name: string
  description?: string | null
  jdbcTableConnection?: JdbcTableConnectionInput | null
}

export interface DataSourceCheckIssue {
  severity: DataSourceCheckSeverity
  code: string
  path: string
  message: string
}

export interface DataSourceCheckResult {
  dataSourceId: string
  checkedDraftVersion: number
  schemaVersionId: string | null
  valid: boolean
  blockerCount: number
  warningCount: number
  issues: DataSourceCheckIssue[]
}

export interface PublishDataSourceInput {
  expectedVersion: number
}

export interface DataSourceConnectionCheckInput {
  expectedVersion: number
}

export interface DataSourceConnectionCheckResult {
  reachable: boolean
  contractValid: boolean
  httpStatus: number | null
  durationMillis: number
  code: DataSourceConnectionCheckCode
  message: string
}

export interface DataSourceSchemaDiscoveryInput {
  expectedVersion: number
}

export interface DataSourceSchemaDiscoveryResult extends Omit<DataSourceConnectionCheckResult, 'code'> {
  checkedDraftVersion: number
  code: DataSourceSchemaDiscoveryCode
  fields: Array<HttpJsonDiscoveredField | JdbcTableDiscoveredField>
}

export interface DataSourceDraftRowsPreviewInput {
  expectedVersion: number
}

export interface DataSourceDraftPreviewField {
  fieldCode: string
  sourceType: DataSourceProjectionSourceType
}

export type DataSourceDraftPreviewScalar = string | number | boolean | null

export interface DataSourceDraftPreviewRow {
  rowIndex: number
  values: Record<string, DataSourceDraftPreviewScalar>
}

export interface DataSourceDraftRowsPreviewResult extends Omit<DataSourceConnectionCheckResult, 'code'> {
  checkedDraftVersion: number
  code: DataSourceDraftRowsPreviewCode
  fields: DataSourceDraftPreviewField[]
  rows: DataSourceDraftPreviewRow[]
}

export interface DataSourceVersion {
  id: string
  dataSourceId: string
  versionNumber: number
  code: string
  moduleId: string
  moduleCode: string
  schemaVersionId: string
  name: string
  description?: string | null
  fingerprint: string
  snapshot: DataSourceDraft
  publishedAt: string
  publishedBy: string
  active: boolean
}

export interface DataSourcePublishResult {
  source: DataSourceDetail
  version: DataSourceVersion
}

export interface PublishedHttpDataSourceField {
  fieldCode: string
  sourceType: HttpJsonSelectableSourceType
}

export type PublishedHttpDataSourceScalar = string | number | boolean | null

export interface PublishedHttpDataSourceRow {
  rowIndex: number
  values: Record<string, PublishedHttpDataSourceScalar>
}

export interface PublishedHttpDataSourceRows {
  dataSourceId: string
  dataSourceCode: string
  dataSourceVersionId: string
  dataSourceVersionNumber: number
  fields: PublishedHttpDataSourceField[]
  rows: PublishedHttpDataSourceRow[]
}

export interface PublishedJdbcDataSourceField {
  fieldCode: string
  sourceType: JdbcTableSelectableSourceType
}

export interface PublishedJdbcDataSourceRow {
  rowIndex: number
  values: Record<string, DataSourceDraftPreviewScalar>
}

export interface PublishedJdbcDataSourceRows {
  dataSourceId: string
  dataSourceCode: string
  dataSourceVersionId: string
  dataSourceVersionNumber: number
  fields: PublishedJdbcDataSourceField[]
  rows: PublishedJdbcDataSourceRow[]
}

export interface RuntimeDataSourceField {
  fieldCode: string
  fieldName: string
  type: string
}

export interface RuntimeDataSourceMetadata {
  id: string
  code: string
  name: string
  description?: string | null
  moduleCode: string
  versionId: string
  activeVersionId: string
  versionNumber: number
  activeVersionNumber: number
  schemaVersionId: string
  outputFields: RuntimeDataSourceField[]
  fields: RuntimeDataSourceField[]
  defaultSort?: DataSourceDefaultSort | null
  defaultTimeFieldCode?: string | null
}

export interface RuntimeDataSourceRow {
  recordId: string
  recordNo?: string
  version: number
  status: string
  title?: string | null
  values: Record<string, unknown> | Array<{
    fieldCode: string
    value?: unknown
    displayValue?: string
  }>
}

export interface RuntimeDataSourceRows {
  rows: RuntimeDataSourceRow[]
  items: RuntimeDataSourceRow[]
  page: number
  size: number
  total: number
  queryHash?: string | null
  partial?: boolean
  sourceKind?: DataSourceSourceKind | null
  sourceRowLimit?: number | null
  failedSourceAliases?: string[]
}

export interface DataSourceFieldCapability {
  fieldCode: string
  fieldName: string
  type: string
  operators: string[]
  sortable: boolean
  temporal: boolean
  available: boolean
  unavailableReason?: string | null
  options?: Array<{ value: string; label: string }>
}

export interface DataSourceModuleCapability {
  moduleId: string
  moduleCode: string
  moduleName: string
  schemaVersionId: string
  available: boolean
  unavailableReason?: string | null
  fields: DataSourceFieldCapability[]
}

export interface DataSourceCatalog {
  modules: DataSourceModuleCapability[]
}
