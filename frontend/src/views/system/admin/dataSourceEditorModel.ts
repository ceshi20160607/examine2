import type { DataSourceFieldCapability, DataSourceFixedFilter, JdbcTableConnectionInput } from '@/types/dataSource'

export interface DataSourceFilterDraft {
  key: string
  fieldCode: string
  operator: string
  value: unknown
  secondValue: unknown
}

const noValueOperators = new Set(['IS_NULL', 'IS_NOT_NULL', 'EMPTY', 'NOT_EMPTY'])
const collectionOperators = new Set(['IN', 'NOT_IN', 'HAS_ANY', 'HAS_ALL'])
const rangeOperators = new Set(['BETWEEN', 'NOT_BETWEEN'])
const numericTypes = new Set(['NUMBER', 'PERCENT', 'MONEY', 'RATING', 'PROGRESS', 'DECIMAL', 'INTEGER'])
const temporalTypes = new Set(['DATE', 'DATETIME', 'TIME', 'CREATED_AT', 'UPDATED_AT'])
const jdbcIdentifier = /^[A-Za-z_][A-Za-z0-9_]{0,63}$/u
const jdbcHost = /^(?=.{1,253}$)(?![.-])(?:[A-Za-z0-9-]+\.)*[A-Za-z0-9-]+$/u
const scopedSecretRef = /^env:\/\/EXAMINE_DS_S[1-9]\d*_T[1-9]\d*_[A-Z][A-Z0-9_]{0,127}_V[1-9]\d{0,8}$/u

export function isNoValueOperator(operator: string) {
  return noValueOperators.has(operator)
}

export function isCollectionOperator(operator: string) {
  return collectionOperators.has(operator)
}

export function isRangeOperator(operator: string) {
  return rangeOperators.has(operator)
}

export function isNumericField(field?: Pick<DataSourceFieldCapability, 'type'>) {
  return numericTypes.has(field?.type ?? '')
}

export function isBooleanField(field?: Pick<DataSourceFieldCapability, 'type'>) {
  return ['SWITCH', 'BOOLEAN'].includes(field?.type ?? '')
}

export function isTemporalField(field?: Pick<DataSourceFieldCapability, 'type' | 'temporal'>) {
  return Boolean(field?.temporal) || temporalTypes.has(field?.type ?? '')
}

export function inputTypeForField(field?: Pick<DataSourceFieldCapability, 'type'>) {
  if (!field) return 'text'
  if (numericTypes.has(field.type)) return 'number'
  if (field.type === 'DATE') return 'date'
  if (['DATETIME', 'CREATED_AT', 'UPDATED_AT'].includes(field.type)) return 'datetime-local'
  if (field.type === 'TIME') return 'time'
  return 'text'
}

export function normalizeJdbcTableConnection(
  value: JdbcTableConnectionInput,
  configured: { username: boolean, password: boolean } = { username: false, password: false },
): JdbcTableConnectionInput {
  const host = value.host.trim()
  const databaseName = value.databaseName.trim()
  const tableName = value.tableName.trim()
  const usernameSecretRef = value.usernameSecretRef.trim()
  const passwordSecretRef = value.passwordSecretRef.trim()
  const port = Number(value.port)
  const connectTimeoutSeconds = Number(value.connectTimeoutSeconds)
  const queryTimeoutSeconds = Number(value.queryTimeoutSeconds)
  if (!jdbcHost.test(host)) throw new Error('MySQL 主机必须是结构化主机名，不能填写 URL 或连接串')
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('MySQL 端口必须是 1 到 65535 的整数')
  if (!jdbcIdentifier.test(databaseName)) throw new Error('数据库名称必须使用安全标识符')
  if (!jdbcIdentifier.test(tableName)) throw new Error('表名称必须使用安全标识符')
  if ((!usernameSecretRef && !configured.username)
      || (!passwordSecretRef && !configured.password)
      || usernameSecretRef && !scopedSecretRef.test(usernameSecretRef)
      || passwordSecretRef && !scopedSecretRef.test(passwordSecretRef)) {
    throw new Error('MySQL 用户名和密码必须分别使用当前数据源命名空间的 SecretRef')
  }
  if (!Number.isInteger(connectTimeoutSeconds)
      || connectTimeoutSeconds < 1 || connectTimeoutSeconds > 10) {
    throw new Error('连接超时必须是 1 到 10 秒的整数')
  }
  if (!Number.isInteger(queryTimeoutSeconds)
      || queryTimeoutSeconds < 1 || queryTimeoutSeconds > 30) {
    throw new Error('查询超时必须是 1 到 30 秒的整数')
  }
  return {
    host,
    port,
    databaseName,
    tableName,
    usernameSecretRef,
    passwordSecretRef,
    connectTimeoutSeconds,
    queryTimeoutSeconds,
  }
}

function scalarValue(field: DataSourceFieldCapability, value: unknown) {
  if (isNumericField(field)) {
    const parsed = Number(value)
    if (!Number.isFinite(parsed)) throw new Error(`${field.fieldName} 必须是数字`)
    return parsed
  }
  if (isBooleanField(field)) {
    if (value === true || value === 'true') return true
    if (value === false || value === 'false') return false
    throw new Error(`${field.fieldName} 必须选择是或否`)
  }
  const result = String(value ?? '').trim()
  if (!result) throw new Error(`${field.fieldName} 的筛选值不能为空`)
  return result
}

export function toFixedFilter(row: DataSourceFilterDraft, field: DataSourceFieldCapability): DataSourceFixedFilter {
  if (!row.operator || !field.operators.includes(row.operator)) {
    throw new Error(`${field.fieldName} 的筛选操作符无效`)
  }
  if (isNoValueOperator(row.operator)) {
    return { fieldCode: field.fieldCode, operator: row.operator }
  }
  if (isCollectionOperator(row.operator)) {
    const source = Array.isArray(row.value) ? row.value : String(row.value ?? '').split(',')
    const values = source.map((item) => scalarValue(field, item))
    if (!values.length) throw new Error(`${field.fieldName} 至少需要一个筛选值`)
    return { fieldCode: field.fieldCode, operator: row.operator, canonicalValue: values }
  }
  if (isRangeOperator(row.operator)) {
    return {
      fieldCode: field.fieldCode,
      operator: row.operator,
      canonicalValue: [scalarValue(field, row.value), scalarValue(field, row.secondValue)],
    }
  }
  return {
    fieldCode: field.fieldCode,
    operator: row.operator,
    canonicalValue: scalarValue(field, row.value),
  }
}

export function fromFixedFilter(filter: DataSourceFixedFilter, key: string = crypto.randomUUID()): DataSourceFilterDraft {
  const values = Array.isArray(filter.canonicalValue) ? filter.canonicalValue : [filter.canonicalValue]
  return {
    key,
    fieldCode: filter.fieldCode,
    operator: filter.operator,
    value: isCollectionOperator(filter.operator) ? values.join(', ') : values[0] ?? '',
    secondValue: isRangeOperator(filter.operator) ? values[1] ?? '' : '',
  }
}
