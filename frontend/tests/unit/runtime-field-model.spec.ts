import { describe, expect, it } from 'vitest'

import type { RuntimeFieldCapability } from '../../src/types/config'
import {
  buildRuntimeCascadeOptions,
  isBlankRuntimeValue,
  normalizeRuntimeMoneyAmount,
  normalizeRuntimeTime,
  readRuntimeFilterDraft,
  runtimeCurrencies,
  runtimePredicateValue,
  type RuntimeFilterValueDraft,
} from '../../src/views/system/runtimeFieldModel'

function field(type: string, schema: Record<string, unknown> = {}, options: RuntimeFieldCapability['options'] = []): RuntimeFieldCapability {
  return {
    fieldCode: type.toLowerCase(), fieldName: type, logicalFieldId: '1', type, mode: 'WRITABLE',
    readable: true, writable: true, sensitiveReadable: false, sensitiveQueryable: false,
    masked: false, operators: [], sortable: true,
    showInList: true, showInDetail: true, options, schema,
  }
}

function draft(overrides: Partial<RuntimeFilterValueDraft> = {}): RuntimeFilterValueDraft {
  return {
    value: '', secondValue: '', values: [], currency: '', empty: true, pathSnapshotId: '', params: {},
    ...overrides,
  }
}

describe('runtime P4-C1 field model', () => {
  it('builds a contiguous cascade tree from published parent ids', () => {
    const cascade = field('CASCADE', {}, [
      { value: 'root', label: '华东', parentValue: null },
      { value: 'child', label: '上海', parentValue: 'root' },
      { value: 'leaf', label: '浦东', parentValue: 'child' },
    ])
    expect(buildRuntimeCascadeOptions(cascade)).toEqual([{
      value: 'root', label: '华东', children: [{
        value: 'child', label: '上海', children: [{ value: 'leaf', label: '浦东' }],
      }],
    }])
  })

  it('preserves exact money scale and currency in scalar and range predicates', () => {
    const money = field('MONEY', { currencies: ['CNY', 'USD'] })
    expect(runtimeCurrencies(money)).toEqual(['CNY', 'USD'])
    expect(normalizeRuntimeMoneyAmount('10', 'CNY')).toBe('10.00')
    expect(runtimePredicateValue(money, 'EQ', draft({ value: '10', currency: 'USD' })))
      .toEqual({ amount: '10.00', currency: 'USD' })
    expect(runtimePredicateValue(money, 'BETWEEN', draft({ value: '9', secondValue: '10', currency: 'CNY' }))).toEqual([
      { amount: '9.00', currency: 'CNY' },
      { amount: '10.00', currency: 'CNY' },
    ])
  })

  it('serializes ranges, membership, booleans, boundaries and EMPTY=false', () => {
    const dateRange = field('DATE_RANGE')
    const tags = field('TAG')
    const enabled = field('SWITCH')
    const percent = field('PERCENT')
    expect(runtimePredicateValue(dateRange, 'OVERLAPS', draft({
      value: '2026-01-01', secondValue: '2026-01-31',
    }))).toEqual(['2026-01-01', '2026-01-31'])
    expect(runtimePredicateValue(tags, 'HAS_ALL', draft({ values: ['urgent', 'urgent', 'onsite'] })))
      .toEqual(['urgent', 'onsite'])
    expect(runtimePredicateValue(enabled, 'EQ', draft({ value: false }))).toBe(false)
    expect(runtimePredicateValue(percent, 'EQ', draft({ value: 100 }))).toBe(100)
    expect(() => runtimePredicateValue(percent, 'EQ', draft({ value: 101 }))).toThrow('0 到 100')
    expect(runtimePredicateValue(enabled, 'EMPTY', readRuntimeFilterDraft(enabled, 'EMPTY', false))).toBe(false)
  })

  it('serializes every P4-C2 structured query shape without flattening values', () => {
    expect(runtimePredicateValue(field('ADDRESS'), 'EQ_REGION', draft({ value: 'cn', secondValue: '110000' })))
      .toEqual({ countryCode: 'CN', regionCode: '110000' })
    expect(runtimePredicateValue(field('GEO'), 'WITHIN_BOX', draft({
      params: { south: 39, west: 116, north: 40, east: 117 },
    }))).toEqual({ south: 39, west: 116, north: 40, east: 117 })
    expect(runtimePredicateValue(field('GEO'), 'NEAR', draft({
      params: { lat: 39.9, lng: 116.4, radiusMeters: 5000 },
    }))).toEqual({ lat: 39.9, lng: 116.4, radiusMeters: 5000 })
    expect(runtimePredicateValue(field('BARCODE', { symbologies: ['CODE128'] }), 'EQ', draft({
      currency: 'CODE128', value: 'ABC123',
    }))).toBe('CODE128:ABC123')

    const json = field('JSON', { queryPaths: [
      { pathSnapshotId: '101', path: '$.count', type: 'INTEGER' },
      { pathSnapshotId: '102', path: '$.active', type: 'BOOLEAN' },
    ] })
    expect(runtimePredicateValue(json, 'DECLARED_PATH_EQ', draft({ pathSnapshotId: '101', value: '7' })))
      .toEqual({ pathSnapshotId: '101', value: 7 })
    expect(runtimePredicateValue(json, 'DECLARED_PATH_EXISTS', draft({ pathSnapshotId: '102' }))).toBe('102')
    expect(runtimePredicateValue(field('STATUS'), 'IN', draft({ values: ['NEW', 'ACTIVE', 'NEW'] })))
      .toEqual(['NEW', 'ACTIVE'])
  })

  it('normalizes time seconds and omits structurally empty values', () => {
    expect(normalizeRuntimeTime('09:30')).toBe('09:30:00')
    expect(isBlankRuntimeValue(field('TIME_RANGE'), ['', ''])).toBe(true)
    expect(isBlankRuntimeValue(field('TAG'), [])).toBe(true)
    expect(isBlankRuntimeValue(field('MONEY'), { amount: '', currency: 'CNY' })).toBe(true)
    expect(isBlankRuntimeValue(field('SWITCH'), false)).toBe(false)
  })
})
