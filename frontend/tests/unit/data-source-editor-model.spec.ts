import { describe, expect, it } from 'vitest'

import type { DataSourceFieldCapability } from '@/types/dataSource'
import { fromFixedFilter, inputTypeForField, isTemporalField, normalizeJdbcTableConnection, toFixedFilter } from '@/views/system/admin/dataSourceEditorModel'

function field(type: string, operators: string[]): DataSourceFieldCapability {
  return { fieldCode: 'amount', fieldName: '金额', type, operators, sortable: true, temporal: false, available: true }
}

describe('data source visual editor model', () => {
  it('normalizes number, boolean, collection and range values without raw JSON', () => {
    expect(toFixedFilter({ key: '1', fieldCode: 'amount', operator: 'GTE', value: '12.5', secondValue: '' }, field('NUMBER', ['GTE']))).toEqual({ fieldCode: 'amount', operator: 'GTE', canonicalValue: 12.5 })
    expect(toFixedFilter({ key: '2', fieldCode: 'amount', operator: 'EQ', value: 'false', secondValue: '' }, field('SWITCH', ['EQ']))).toEqual({ fieldCode: 'amount', operator: 'EQ', canonicalValue: false })
    expect(toFixedFilter({ key: '3', fieldCode: 'amount', operator: 'IN', value: '1, 2', secondValue: '' }, field('INTEGER', ['IN']))).toEqual({ fieldCode: 'amount', operator: 'IN', canonicalValue: [1, 2] })
    expect(toFixedFilter({ key: '4', fieldCode: 'amount', operator: 'BETWEEN', value: '1', secondValue: '9' }, field('NUMBER', ['BETWEEN']))).toEqual({ fieldCode: 'amount', operator: 'BETWEEN', canonicalValue: [1, 9] })
    expect(toFixedFilter({ key: '5', fieldCode: 'amount', operator: 'EMPTY', value: '', secondValue: '' }, field('TEXT', ['EMPTY']))).toEqual({ fieldCode: 'amount', operator: 'EMPTY' })
  })

  it('round-trips persisted canonical filter values into typed controls', () => {
    expect(fromFixedFilter({ fieldCode: 'amount', operator: 'BETWEEN', canonicalValue: [3, 7] }, 'fixed')).toEqual({ key: 'fixed', fieldCode: 'amount', operator: 'BETWEEN', value: 3, secondValue: 7 })
    expect(inputTypeForField(field('DATETIME', ['EQ']))).toBe('datetime-local')
    expect(isTemporalField({ type: 'TEXT', temporal: true })).toBe(true)
    expect(() => toFixedFilter({ key: '1', fieldCode: 'amount', operator: 'LT', value: 'x', secondValue: '' }, field('NUMBER', ['GTE']))).toThrow('操作符无效')
  })

  it('normalizes only structured JDBC table settings and supports configured credential retention', () => {
    const connection = {
      host: ' mysql.example.internal ', port: 3306, databaseName: ' orders_db ', tableName: 'order_items',
      usernameSecretRef: 'env://EXAMINE_DS_S10_T40_MYSQL_USER_V1',
      passwordSecretRef: 'env://EXAMINE_DS_S10_T40_MYSQL_PASSWORD_V1',
      connectTimeoutSeconds: 5, queryTimeoutSeconds: 10,
    }
    expect(normalizeJdbcTableConnection(connection)).toEqual({
      ...connection, host: 'mysql.example.internal', databaseName: 'orders_db',
    })
    expect(normalizeJdbcTableConnection({
      ...connection, usernameSecretRef: '', passwordSecretRef: '',
    }, { username: true, password: true })).toMatchObject({
      usernameSecretRef: '', passwordSecretRef: '',
    })
    expect(() => normalizeJdbcTableConnection({ ...connection, host: 'jdbc:mysql://db/orders' }))
      .toThrow('不能填写 URL 或连接串')
    expect(() => normalizeJdbcTableConnection({ ...connection, tableName: 'orders;DROP TABLE x' }))
      .toThrow('安全标识符')
  })
})
