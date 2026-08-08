import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { dataSourceAdminApi, runtimeDataSourceApi } from '@/services/dataSource'
import type { DataSourceCatalog, DataSourceDetail, DataSourceVersion, PublishedHttpDataSourceRows, RuntimeDataSourceMetadata, RuntimeDataSourceRows } from '@/types/dataSource'
import DataSourcesView from '@/views/system/admin/DataSourcesView.vue'

const route = vi.hoisted(() => ({ params: { systemId: '10' } }))
vi.mock('vue-router', () => ({ useRoute: () => route }))
vi.mock('@/services/dataSource', () => ({
  dataSourceAdminApi: {
    catalog: vi.fn(), list: vi.fn(), create: vi.fn(), detail: vi.fn(), saveDraft: vi.fn(),
    checkDraft: vi.fn(), checkDraftConnection: vi.fn(), discoverDraftSchema: vi.fn(), previewDraftRows: vi.fn(), publishDraft: vi.fn(), versions: vi.fn(),
  },
  runtimeDataSourceApi: { jdbcRows: vi.fn(), httpRows: vi.fn(), metadata: vi.fn(), rows: vi.fn(), statisticsCapabilities: vi.fn(), statistics: vi.fn() },
}))

const catalog: DataSourceCatalog = {
  modules: [{
    moduleId: '20', moduleCode: 'orders', moduleName: '订单', schemaVersionId: 'schema-1', available: true,
    fields: [
      { fieldCode: 'title', fieldName: '标题', type: 'TEXT', operators: ['EQ', 'CONTAINS'], sortable: true, temporal: false, available: true },
      { fieldCode: 'amount', fieldName: '金额', type: 'NUMBER', operators: ['GTE', 'BETWEEN'], sortable: true, temporal: false, available: true },
      { fieldCode: 'createdAt', fieldName: '创建时间', type: 'DATETIME', operators: ['GTE', 'BETWEEN'], sortable: true, temporal: true, available: true },
      { fieldCode: 'approved', fieldName: '已批准', type: 'SWITCH', operators: ['EQ'], sortable: false, temporal: false, available: true },
      { fieldCode: 'secret', fieldName: '密钥', type: 'SECRET', operators: [], sortable: false, temporal: false, available: false, unavailableReason: 'DEFERRED' },
    ],
  }],
}

const baseDetail: DataSourceDetail = {
  id: '30', systemId: '10', tenantId: '40', code: 'large_orders', moduleId: '20', moduleCode: 'orders',
  name: '大额订单', description: '已发布数据', draftVersion: 1, activeVersionId: '50', activeVersionNumber: 1,
  createdAt: '2026-08-01T01:00:00Z', updatedAt: '2026-08-01T02:00:00Z', version: 1,
  draft: {
    outputFields: [{ fieldCode: 'title' }, { fieldCode: 'amount' }],
    fixedFilters: [{ fieldCode: 'amount', operator: 'GTE', canonicalValue: 100 }],
    defaultSort: { fieldCode: 'amount', direction: 'DESC' }, defaultTimeFieldCode: null,
  },
}

const version: DataSourceVersion = {
  id: '50', dataSourceId: '30', versionNumber: 1, code: 'large_orders', moduleId: '20', moduleCode: 'orders',
  schemaVersionId: 'schema-1', name: '大额订单', description: '已发布数据', snapshot: baseDetail.draft,
  fingerprint: 'abcdef1234567890', publishedBy: '30', publishedAt: '2026-08-01T02:00:00Z', active: true,
}

const httpDetail: DataSourceDetail = {
  ...baseDetail,
  id: '31',
  code: 'external_orders',
  name: '外部订单',
  activeVersionId: null,
  activeVersionNumber: null,
  version: 4,
  draftVersion: 2,
  draft: {
    outputFields: [], fixedFilters: [], defaultSort: null, defaultTimeFieldCode: null,
    sourceKind: 'HTTP_JSON',
    httpJsonConnection: {
      endpoint: 'https://api.example.com/v1/orders',
      authSecretRef: 'env://EXAMINE_DS_S10_T40_ORDERS_V1',
      timeoutSeconds: 5,
    },
    httpFieldProjections: [],
  },
}

const projectedHttpDetail: DataSourceDetail = {
  ...httpDetail,
  draft: {
    ...httpDetail.draft,
    httpFieldProjections: [
      { sourceField: 'display_name', fieldCode: 'title', sourceType: 'STRING' },
      { sourceField: 'amount', fieldCode: 'amount', sourceType: 'DECIMAL' },
    ],
  },
}

const httpVersion: DataSourceVersion = {
  id: '70', dataSourceId: '31', versionNumber: 1, code: 'external_orders', moduleId: '20', moduleCode: 'orders',
  schemaVersionId: 'schema-1', name: '外部订单', description: null, snapshot: projectedHttpDetail.draft,
  fingerprint: 'httpabcdef1234567890', publishedBy: '30', publishedAt: '2026-08-05T07:00:00Z', active: true,
}

const activeHttpDetail: DataSourceDetail = {
  ...projectedHttpDetail,
  activeVersionId: httpVersion.id,
  activeVersionNumber: httpVersion.versionNumber,
}

const jdbcDetail: DataSourceDetail = {
  ...baseDetail,
  id: '33',
  code: 'mysql_orders',
  name: 'MySQL 订单',
  sourceKind: 'JDBC_TABLE',
  activeVersionId: '72',
  activeVersionNumber: 1,
  version: 2,
  draftVersion: 2,
  draft: {
    outputFields: [], fixedFilters: [], defaultSort: null, defaultTimeFieldCode: null,
    sourceKind: 'JDBC_TABLE',
    jdbcTableConnection: {
      host: 'mysql.example.internal', port: 3306, databaseName: 'orders_db', tableName: 'orders',
      usernameConfigured: true, passwordConfigured: true,
      connectTimeoutSeconds: 5, queryTimeoutSeconds: 10,
    },
    jdbcFieldProjections: [
      { sourceColumn: 'order_name', fieldCode: 'title', sourceType: 'STRING' },
      { sourceColumn: 'amount', fieldCode: 'amount', sourceType: 'DECIMAL' },
    ],
  },
}

const jdbcVersion: DataSourceVersion = {
  ...version,
  id: '72', dataSourceId: jdbcDetail.id, code: jdbcDetail.code, name: jdbcDetail.name,
  snapshot: jdbcDetail.draft, fingerprint: 'jdbcabcdef1234567890', active: true,
}

const publishedHttpRows: PublishedHttpDataSourceRows = {
  dataSourceId: activeHttpDetail.id,
  dataSourceCode: activeHttpDetail.code,
  dataSourceVersionId: httpVersion.id,
  dataSourceVersionNumber: httpVersion.versionNumber,
  fields: [
    { fieldCode: 'external_name', sourceType: 'STRING' },
    { fieldCode: 'external_count', sourceType: 'INTEGER' },
    { fieldCode: 'external_price', sourceType: 'DECIMAL' },
    { fieldCode: 'external_enabled', sourceType: 'BOOLEAN' },
    { fieldCode: 'external_note', sourceType: 'STRING' },
  ],
  rows: [{
    rowIndex: 1,
    values: {
      external_name: '<b>remote</b>',
      external_count: 7,
      external_price: 12.5,
      external_enabled: false,
      external_note: null,
    },
  }],
}

const metadata: RuntimeDataSourceMetadata = {
  id: '30', code: 'large_orders', name: '大额订单', moduleCode: 'orders', schemaVersionId: 'schema-1',
  versionId: '50', activeVersionId: '50', versionNumber: 1, activeVersionNumber: 1,
  outputFields: [
    { fieldCode: 'title', fieldName: '标题', type: 'TEXT' },
    { fieldCode: 'amount', fieldName: '金额', type: 'NUMBER' },
  ],
  fields: [
    { fieldCode: 'title', fieldName: '标题', type: 'TEXT' },
    { fieldCode: 'amount', fieldName: '金额', type: 'NUMBER' },
  ],
}

const rows: RuntimeDataSourceRows = {
  page: 1, size: 10, total: 1,
  rows: [{ recordId: '60', recordNo: 'ORD-1', version: 1, status: 'ACTIVE', values: { title: '采购单', amount: 260 } }],
  items: [{ recordId: '60', recordNo: 'ORD-1', version: 1, status: 'ACTIVE', values: { title: '采购单', amount: 260 } }],
}

function render() {
  return mount(DataSourcesView, {
    global: {
      stubs: {
        AdminPageHeader: { template: '<header><slot name="actions" /></header>' },
        'a-button': {
          props: ['disabled', 'loading'], emits: ['click'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
        },
        'a-alert': { props: ['message'], template: '<div class="alert-stub">{{ message }}</div>' },
        'a-tag': { template: '<span class="tag-stub"><slot /></span>' },
        'a-empty': { props: ['description'], template: '<div class="empty-stub">{{ description }}</div>' },
        'a-modal': { props: ['open'], template: '<div v-if="open"><slot /></div>' },
        'a-form': { template: '<form><slot /></form>' },
        'a-form-item': { template: '<label><slot /></label>' },
        'a-input': {
          props: ['value', 'type'], emits: ['update:value'],
          template: '<input :value="value" :type="type" @input="$emit(\'update:value\', $event.target.value)">',
        },
        'a-textarea': { template: '<textarea />' },
        'a-select': {
          props: ['value'], emits: ['update:value', 'change'],
          template: '<select :value="value" @change="$emit(\'update:value\', $event.target.value); $emit(\'change\', $event.target.value)"><slot /></select>',
        },
        'a-select-option': {
          props: ['value', 'disabled'], template: '<option :value="value" :disabled="disabled"><slot /></option>',
        },
      },
    },
  })
}

describe('DataSourcesView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(dataSourceAdminApi.catalog).mockResolvedValue(catalog)
    vi.mocked(dataSourceAdminApi.list).mockResolvedValue([baseDetail])
    vi.mocked(dataSourceAdminApi.detail).mockResolvedValue(baseDetail)
    vi.mocked(dataSourceAdminApi.versions).mockResolvedValue([version])
    vi.mocked(runtimeDataSourceApi.metadata).mockResolvedValue(metadata)
    vi.mocked(runtimeDataSourceApi.rows).mockResolvedValue(rows)
    vi.mocked(runtimeDataSourceApi.statisticsCapabilities).mockResolvedValue({
      dataSourceId: '30', dataSourceCode: 'large_orders', dataSourceVersionId: '50', dataSourceVersionNumber: 1,
      moduleCode: 'orders', schemaVersionId: 'schema-1', fields: [
        { code: 'title', name: '标题', type: 'TEXT', readable: true, numeric: false, temporal: false, groupable: true },
        { code: 'amount', name: '金额', type: 'NUMBER', readable: true, numeric: true, temporal: false, groupable: true },
      ],
    })
    vi.mocked(runtimeDataSourceApi.statistics).mockResolvedValue({
      queryId: 'stats-1', dataSourceId: '30', dataSourceCode: 'large_orders', dataSourceVersionId: '50',
      dataSourceVersionNumber: 1, moduleCode: 'orders', schemaVersionId: 'schema-1', aggregation: 'COUNT',
      measureFieldCode: null, value: '1', matchedRecordCount: 1, aggregateCount: 1, bucketCount: 0,
      groupBuckets: [], trendBuckets: [], totalBucketCount: 0, truncated: false,
    })
  })

  it('loads admin catalog and renders a visual draft plus the real published preview', async () => {
    const wrapper = render()
    await flushPromises()

    expect(dataSourceAdminApi.catalog).toHaveBeenCalledWith('10')
    expect(dataSourceAdminApi.detail).toHaveBeenCalledWith('10', '30')
    expect(runtimeDataSourceApi.metadata).toHaveBeenCalledWith('10', 'large_orders')
    expect(runtimeDataSourceApi.rows).toHaveBeenCalledWith('10', 'large_orders', 1, 10)
    expect(wrapper.findAll('.ordered-fields li')).toHaveLength(2)
    expect(wrapper.get('.filter-row').text()).toContain('金额')
    expect(wrapper.get('.published-preview').text()).toContain('采购单')
    expect(wrapper.get('.published-preview').text()).toContain('260')
    expect(wrapper.get('.version-history').text()).toContain('abcdef123456')
    expect(wrapper.find('[data-state="clean"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('密钥（secret）')
  })

  it('tracks dirty/saving/checked/published states and submits typed canonical filters', async () => {
    const nativeAfterPublish: DataSourceDetail = { ...baseDetail, version: 7, draftVersion: 1 }
    const saved: DataSourceDetail = { ...baseDetail, version: 2, draftVersion: 2, name: '大额订单（已调整）' }
    const published: DataSourceDetail = { ...saved, activeVersionId: '51', activeVersionNumber: 2, version: 3 }
    let resolveSave!: (value: DataSourceDetail) => void
    const savedAfterDivergence: DataSourceDetail = { ...saved, version: 8 }
    vi.mocked(dataSourceAdminApi.list).mockResolvedValue([nativeAfterPublish])
    vi.mocked(dataSourceAdminApi.detail).mockResolvedValue(nativeAfterPublish)
    vi.mocked(dataSourceAdminApi.saveDraft).mockReturnValue(new Promise(resolve => { resolveSave = resolve }))
    vi.mocked(dataSourceAdminApi.checkDraft).mockResolvedValue({ dataSourceId: '30', checkedDraftVersion: 2, schemaVersionId: 'schema-1', valid: true, blockerCount: 0, warningCount: 0, issues: [] })
    vi.mocked(dataSourceAdminApi.publishDraft).mockResolvedValue({ source: published, version: { ...version, id: '51', versionNumber: 2 } })

    const wrapper = render()
    await flushPromises()
    const name = wrapper.get('.source-basics input:not(:disabled)')
    await name.setValue('大额订单（已调整）')
    expect(wrapper.find('[data-state="dirty"]').exists()).toBe(true)

    await wrapper.get('.draft-save').trigger('click')
    expect(wrapper.find('[data-state="saving"]').exists()).toBe(true)
    resolveSave(savedAfterDivergence)
    await flushPromises()
    expect(dataSourceAdminApi.saveDraft).toHaveBeenCalledWith('10', '30', expect.objectContaining({
      expectedVersion: 1,
      name: '大额订单（已调整）',
      outputFields: [{ fieldCode: 'title' }, { fieldCode: 'amount' }],
      fixedFilters: [{ fieldCode: 'amount', operator: 'GTE', canonicalValue: 100 }],
      defaultSort: { fieldCode: 'amount', direction: 'DESC' },
    }))
    const nativeDraftInput = vi.mocked(dataSourceAdminApi.saveDraft).mock.calls[0]?.[2]
    expect(nativeDraftInput).not.toHaveProperty('sourceKind')
    expect(nativeDraftInput).not.toHaveProperty('httpJsonConnection')

    await wrapper.get('.draft-check').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-state="checked"]').exists()).toBe(true)

    await wrapper.get('.draft-publish').trigger('click')
    await flushPromises()
    expect(dataSourceAdminApi.checkDraft).toHaveBeenCalledTimes(1)
    expect(dataSourceAdminApi.publishDraft).toHaveBeenCalledWith('10', '30', { expectedVersion: 2 })
    expect(wrapper.find('[data-state="published"]').exists()).toBe(true)
    expect(runtimeDataSourceApi.metadata).toHaveBeenCalledTimes(2)
  })

  it('keeps blockers and request failures explicit instead of showing a false published state', async () => {
    vi.mocked(dataSourceAdminApi.checkDraft).mockResolvedValue({
      dataSourceId: '30', checkedDraftVersion: 1, schemaVersionId: 'schema-1',
      valid: false, blockerCount: 1, warningCount: 0,
      issues: [{ severity: 'BLOCKER', code: 'FIELD_STALE', path: '/outputFields/0', message: '字段已失效' }],
    })
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.draft-check').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-state="error"]').exists()).toBe(true)
    expect(wrapper.get('.check-result').text()).toContain('FIELD_STALE')
    await wrapper.get('.draft-publish').trigger('click')
    await flushPromises()
    expect(dataSourceAdminApi.publishDraft).not.toHaveBeenCalled()

    vi.mocked(runtimeDataSourceApi.metadata).mockRejectedValueOnce(new Error('published unavailable'))
    await wrapper.get('.preview-refresh').trigger('click')
    await flushPromises()
    expect(wrapper.get('.data-source-error').text()).toContain('published unavailable')
  })

  it('runs a real published-version statistic with the frozen COUNT request', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.statistics-run').trigger('click')
    await flushPromises()

    expect(runtimeDataSourceApi.statistics).toHaveBeenCalledWith('10', 'large_orders', {
      aggregation: 'COUNT', measureFieldCode: null, grouping: null, trend: null,
    })
    expect(wrapper.get('.statistics-tester-result').text()).toContain('1')
    expect(wrapper.get('.statistics-tester-result').text()).toContain('stats-1')
    expect(wrapper.get('.statistics-tester').text()).toContain('Schema schema-1')
  })

  it('renders one HTTP JSON draft with SecretRef-only configuration and no publish affordance', async () => {
    vi.mocked(dataSourceAdminApi.list).mockResolvedValue([httpDetail])
    vi.mocked(dataSourceAdminApi.detail).mockResolvedValue(httpDetail)
    vi.mocked(dataSourceAdminApi.versions).mockResolvedValue([])
    const wrapper = render()
    await flushPromises()

    expect((wrapper.get('.data-source-kind').element as HTMLSelectElement).value).toBe('HTTP_JSON')
    expect((wrapper.get('.http-json-endpoint').element as HTMLInputElement).value)
      .toBe('https://api.example.com/v1/orders')
    expect((wrapper.get('.http-json-secret-ref').element as HTMLInputElement).value)
      .toBe('env://EXAMINE_DS_S10_T40_ORDERS_V1')
    expect((wrapper.get('.http-json-timeout').element as HTMLInputElement).value).toBe('5')
    expect(wrapper.get('.http-publish-blocker').text()).toContain('发布后进入统一运行时')
    expect(wrapper.get('.draft-publish').attributes('disabled')).toBeDefined()
    expect(wrapper.find('.output-editor').exists()).toBe(false)
    expect(wrapper.find('.filter-editor').exists()).toBe(false)
    expect(wrapper.find('.query-defaults').exists()).toBe(false)
    expect(wrapper.find('.http-field-projection').exists()).toBe(true)
    expect(wrapper.find('.draft-schema-discover').exists()).toBe(true)
    expect(runtimeDataSourceApi.metadata).not.toHaveBeenCalled()
  })

  it('exposes HTTP JSON creation fields while retaining the required anchor module', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.data-source-create').trigger('click')
    await wrapper.get('.create-source-kind').setValue('HTTP_JSON')

    expect(wrapper.find('.create-source-module').exists()).toBe(true)
    expect(wrapper.find('.create-http-json-endpoint').exists()).toBe(true)
    expect(wrapper.find('.create-http-json-secret-ref').exists()).toBe(true)
    expect(wrapper.find('.create-http-json-timeout').exists()).toBe(true)
    expect(wrapper.get('.create-http-secret-boundary').text()).toContain('仅提交 SecretRef')
  })

  it('checks one persisted HTTP draft once, shows only its safe summary, and clears it after edits', async () => {
    vi.mocked(dataSourceAdminApi.list).mockResolvedValue([httpDetail])
    vi.mocked(dataSourceAdminApi.detail).mockResolvedValue(httpDetail)
    vi.mocked(dataSourceAdminApi.versions).mockResolvedValue([])
    let resolveConnection!: (value: {
      reachable: boolean
      contractValid: boolean
      httpStatus: number | null
      durationMillis: number
      code: 'SUCCESS'
      message: string
    }) => void
    vi.mocked(dataSourceAdminApi.checkDraftConnection).mockReturnValue(new Promise(resolve => {
      resolveConnection = resolve
    }))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.draft-connection-check').trigger('click')
    await wrapper.get('.draft-connection-check').trigger('click')
    expect(dataSourceAdminApi.checkDraftConnection).toHaveBeenCalledTimes(1)
    expect(dataSourceAdminApi.checkDraftConnection).toHaveBeenCalledWith('10', '31', {
      expectedVersion: 2,
    })
    expect(wrapper.get('.draft-connection-check').attributes('disabled')).toBeDefined()

    resolveConnection({
      reachable: true, contractValid: true, httpStatus: 200,
      durationMillis: 27, code: 'SUCCESS', message: 'HTTP JSON connection is valid',
    })
    await flushPromises()
    const result = wrapper.get('.connection-check-result')
    expect(result.attributes('data-code')).toBe('SUCCESS')
    expect(result.text()).toContain('200')
    expect(result.text()).toContain('27 ms')
    expect(result.text()).not.toContain('api.example.com')
    expect(result.text()).not.toContain('EXAMINE_DS')

    await wrapper.get('.http-json-endpoint').setValue('https://api.example.com/v2/orders')
    expect(wrapper.find('.connection-check-result').exists()).toBe(false)
  })

  it('shows the fixed HTTP publication blocker returned by normal draft check', async () => {
    vi.mocked(dataSourceAdminApi.list).mockResolvedValue([httpDetail])
    vi.mocked(dataSourceAdminApi.detail).mockResolvedValue(httpDetail)
    vi.mocked(dataSourceAdminApi.versions).mockResolvedValue([])
    vi.mocked(dataSourceAdminApi.checkDraft).mockResolvedValue({
      dataSourceId: '31', checkedDraftVersion: 2, schemaVersionId: 'schema-1',
      valid: false, blockerCount: 1, warningCount: 0,
      issues: [{
        severity: 'BLOCKER', code: 'SOURCE_RUNTIME_UNAVAILABLE', path: '/sourceKind',
        message: 'HTTP JSON runtime is not available',
      }],
    })
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.draft-check').trigger('click')
    await flushPromises()

    expect(wrapper.get('.check-result').text()).toContain('SOURCE_RUNTIME_UNAVAILABLE')
    expect(wrapper.get('.draft-publish').attributes('disabled')).toBeDefined()
    expect(dataSourceAdminApi.publishDraft).not.toHaveBeenCalled()
  })

  it('saves an edited HTTP draft with no output fields before checking its draft version', async () => {
    const emptyHttpDetail = {
      ...httpDetail,
      draft: { ...httpDetail.draft, outputFields: [] },
    }
    vi.mocked(dataSourceAdminApi.list).mockResolvedValue([emptyHttpDetail])
    vi.mocked(dataSourceAdminApi.detail).mockResolvedValue(emptyHttpDetail)
    vi.mocked(dataSourceAdminApi.versions).mockResolvedValue([])
    const saved = {
      ...emptyHttpDetail,
      version: 5,
      draftVersion: 3,
      draft: {
        ...emptyHttpDetail.draft,
        httpJsonConnection: {
          ...httpDetail.draft.httpJsonConnection!,
          endpoint: 'https://api.example.com/v2/orders',
        },
      },
    }
    vi.mocked(dataSourceAdminApi.saveDraft).mockResolvedValue(saved)
    vi.mocked(dataSourceAdminApi.checkDraftConnection).mockResolvedValue({
      reachable: false, contractValid: false, httpStatus: null,
      durationMillis: 10, code: 'TIMEOUT', message: 'HTTP JSON connection timed out',
    })
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.http-json-endpoint').setValue('https://api.example.com/v2/orders')
    await wrapper.get('.draft-connection-check').trigger('click')
    await flushPromises()

    expect(dataSourceAdminApi.saveDraft).toHaveBeenCalledWith('10', '31', expect.objectContaining({
      expectedVersion: 2,
      outputFields: [],
      sourceKind: 'HTTP_JSON',
      httpJsonConnection: {
        endpoint: 'https://api.example.com/v2/orders',
        authSecretRef: 'env://EXAMINE_DS_S10_T40_ORDERS_V1',
        timeoutSeconds: 5,
      },
    }))
    expect(dataSourceAdminApi.checkDraftConnection).toHaveBeenCalledWith('10', '31', {
      expectedVersion: 3,
    })
    expect(wrapper.get('.connection-check-result').attributes('data-code')).toBe('TIMEOUT')
  })

  it('discovers a saved HTTP schema once and edits only compatible explicit projections', async () => {
    const savedConnection: DataSourceDetail = {
      ...httpDetail,
      version: 5,
      draftVersion: 3,
      draft: {
        ...httpDetail.draft,
        httpJsonConnection: {
          ...httpDetail.draft.httpJsonConnection!,
          endpoint: 'https://api.example.com/v2/orders',
        },
      },
    }
    const savedProjection: DataSourceDetail = {
      ...savedConnection,
      version: 6,
      draftVersion: 4,
      draft: {
        ...savedConnection.draft,
        httpJsonConnection: { ...savedConnection.draft.httpJsonConnection!, timeoutSeconds: 6 },
        httpFieldProjections: [
          { sourceField: 'display_name', fieldCode: 'title', sourceType: 'STRING' },
          { sourceField: 'amount', fieldCode: 'amount', sourceType: 'DECIMAL' },
        ],
      },
    }
    vi.mocked(dataSourceAdminApi.list).mockResolvedValue([httpDetail])
    vi.mocked(dataSourceAdminApi.detail).mockResolvedValue(httpDetail)
    vi.mocked(dataSourceAdminApi.versions).mockResolvedValue([])
    vi.mocked(dataSourceAdminApi.saveDraft)
      .mockResolvedValueOnce(savedConnection)
      .mockResolvedValueOnce(savedProjection)
    let resolveDiscovery!: (value: any) => void
    vi.mocked(dataSourceAdminApi.discoverDraftSchema).mockReturnValue(new Promise(resolve => {
      resolveDiscovery = resolve
    }))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.http-json-endpoint').setValue('https://api.example.com/v2/orders')
    await wrapper.get('.draft-schema-discover').trigger('click')
    await wrapper.get('.draft-schema-discover').trigger('click')
    await flushPromises()
    expect(dataSourceAdminApi.saveDraft).toHaveBeenCalledWith('10', '31', expect.objectContaining({
      expectedVersion: 2,
      outputFields: [],
      fixedFilters: [],
      defaultSort: null,
      defaultTimeFieldCode: null,
      httpFieldProjections: [],
    }))
    expect(dataSourceAdminApi.discoverDraftSchema).toHaveBeenCalledTimes(1)
    expect(dataSourceAdminApi.discoverDraftSchema).toHaveBeenCalledWith('10', '31', {
      expectedVersion: 3,
    })
    expect(wrapper.get('.draft-schema-discover').attributes('disabled')).toBeDefined()

    resolveDiscovery({
      reachable: true, contractValid: true, httpStatus: 200, durationMillis: 31,
      code: 'SUCCESS', message: 'HTTP JSON schema discovery succeeded', checkedDraftVersion: 3,
      fields: [
        { sourceField: 'amount', suggestedFieldCode: 'amount', inferredType: 'DECIMAL', nullable: false, selectable: true, issueCode: null, sampleValue: 'RAW_AMOUNT_SENTINEL' },
        { sourceField: 'display_name', suggestedFieldCode: 'title', inferredType: 'STRING', nullable: true, selectable: true, issueCode: null },
        { sourceField: 'unknown', suggestedFieldCode: null, inferredType: 'UNKNOWN', nullable: true, selectable: false, issueCode: 'SCHEMA_REVIEW_REQUIRED' },
      ],
      rawBody: 'RAW_BODY_SECRET_TOKEN',
    })
    await flushPromises()

    const result = wrapper.get('.schema-discovery-result')
    expect(result.attributes('data-code')).toBe('SUCCESS')
    expect(result.attributes('data-draft-version')).toBe('3')
    expect(result.text()).toContain('amount')
    expect(result.text()).toContain('SCHEMA_REVIEW_REQUIRED')
    expect(wrapper.text()).not.toContain('RAW_AMOUNT_SENTINEL')
    expect(wrapper.text()).not.toContain('RAW_BODY_SECRET_TOKEN')
    expect(wrapper.get('.http-projection-field-code').text()).toContain('amount')
    expect(wrapper.get('.http-projection-field-code').text()).not.toContain('title')

    await wrapper.get('.http-projection-add').trigger('click')
    expect(wrapper.findAll('.http-projection-row')).toHaveLength(1)
    expect(wrapper.find('.schema-discovery-result').exists()).toBe(true)
    expect((wrapper.get('.http-projection-source').element as HTMLSelectElement).value).toBe('display_name')
    await wrapper.get('.http-projection-add').trigger('click')
    expect(wrapper.findAll('.http-projection-row')).toHaveLength(2)
    await wrapper.findAll('.http-projection-up')[1]!.trigger('click')
    expect(wrapper.findAll('.http-projection-source-field')[0]!.text()).toBe('display_name')
    expect(wrapper.find('.schema-discovery-result').exists()).toBe(true)

    await wrapper.get('.http-json-timeout').setValue('6')
    expect(wrapper.find('.schema-discovery-result').exists()).toBe(false)
    expect(wrapper.findAll('.http-projection-row')).toHaveLength(2)
    await wrapper.get('.draft-save').trigger('click')
    await flushPromises()
    expect(dataSourceAdminApi.saveDraft).toHaveBeenLastCalledWith('10', '31', expect.objectContaining({
      expectedVersion: 3,
      outputFields: [],
      fixedFilters: [],
      defaultSort: null,
      defaultTimeFieldCode: null,
      httpFieldProjections: [
        { sourceField: 'display_name', fieldCode: 'title', sourceType: 'STRING' },
        { sourceField: 'amount', fieldCode: 'amount', sourceType: 'DECIMAL' },
      ],
    }))
  })

  it('discards a late schema discovery response after selecting another source', async () => {
    vi.mocked(dataSourceAdminApi.list).mockResolvedValue([httpDetail, baseDetail])
    vi.mocked(dataSourceAdminApi.detail).mockImplementation(async (_systemId, id) =>
      id === httpDetail.id ? httpDetail : baseDetail)
    vi.mocked(dataSourceAdminApi.versions).mockResolvedValue([])
    let resolveDiscovery!: (value: any) => void
    vi.mocked(dataSourceAdminApi.discoverDraftSchema).mockReturnValue(new Promise(resolve => {
      resolveDiscovery = resolve
    }))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.draft-schema-discover').trigger('click')
    await flushPromises()
    await wrapper.findAll('.source-list-item')[1]!.trigger('click')
    await flushPromises()
    resolveDiscovery({
      reachable: true, contractValid: true, httpStatus: 200, durationMillis: 12,
      code: 'SUCCESS', message: 'HTTP JSON schema discovery succeeded', checkedDraftVersion: 2,
      fields: [{ sourceField: 'late_field', suggestedFieldCode: 'title', inferredType: 'STRING', nullable: false, selectable: true, issueCode: null }],
    })
    await flushPromises()

    expect(wrapper.find('.schema-discovery-result').exists()).toBe(false)
    expect(wrapper.find('.http-field-projection').exists()).toBe(false)
    expect((wrapper.get('.data-source-kind').element as HTMLSelectElement).value).toBe('NATIVE_MODULE')
  })

  it('saves a dirty projected HTTP draft once and renders only safe typed preview rows', async () => {
    const saved: DataSourceDetail = {
      ...projectedHttpDetail,
      version: 5,
      draftVersion: 3,
      draft: {
        ...projectedHttpDetail.draft,
        httpJsonConnection: { ...projectedHttpDetail.draft.httpJsonConnection!, timeoutSeconds: 6 },
      },
    }
    vi.mocked(dataSourceAdminApi.list).mockResolvedValue([projectedHttpDetail])
    vi.mocked(dataSourceAdminApi.detail).mockResolvedValue(projectedHttpDetail)
    vi.mocked(dataSourceAdminApi.versions).mockResolvedValue([])
    vi.mocked(dataSourceAdminApi.saveDraft).mockResolvedValue(saved)
    let resolvePreview!: (value: any) => void
    vi.mocked(dataSourceAdminApi.previewDraftRows).mockReturnValue(new Promise(resolve => {
      resolvePreview = resolve
    }))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.http-json-timeout').setValue('6')
    await wrapper.get('.draft-rows-preview').trigger('click')
    await wrapper.get('.draft-rows-preview').trigger('click')
    await flushPromises()

    expect(dataSourceAdminApi.saveDraft).toHaveBeenCalledTimes(1)
    expect(dataSourceAdminApi.saveDraft).toHaveBeenCalledWith('10', '31', expect.objectContaining({
      expectedVersion: 2,
      httpFieldProjections: [
        { sourceField: 'display_name', fieldCode: 'title', sourceType: 'STRING' },
        { sourceField: 'amount', fieldCode: 'amount', sourceType: 'DECIMAL' },
      ],
    }))
    expect(dataSourceAdminApi.previewDraftRows).toHaveBeenCalledTimes(1)
    expect(dataSourceAdminApi.previewDraftRows).toHaveBeenCalledWith('10', '31', {
      expectedVersion: 3,
    })
    expect(wrapper.get('.draft-rows-preview').attributes('disabled')).toBeDefined()

    resolvePreview({
      reachable: true, contractValid: true, httpStatus: 200, durationMillis: 24,
      code: 'SUCCESS', message: 'HTTP JSON draft rows preview succeeded', checkedDraftVersion: 3,
      fields: [
        { fieldCode: 'title', sourceType: 'STRING', sourceField: 'display_name' },
        { fieldCode: 'amount', sourceType: 'DECIMAL' },
      ],
      rows: [{
        rowIndex: 1,
        values: { title: '<b>remote</b>', amount: null, unprojected: 'UNPROJECTED_SECRET' },
      }],
      rawBody: 'RAW_BODY_SECRET',
      endpoint: 'https://api.example.com/private',
      secretRef: 'env://EXAMINE_DS_SECRET',
    })
    await flushPromises()

    const result = wrapper.get('.draft-preview-result')
    expect(result.attributes('data-code')).toBe('SUCCESS')
    expect(result.attributes('data-draft-version')).toBe('3')
    expect(result.findAll('.draft-preview-field')).toHaveLength(2)
    expect(result.findAll('.draft-preview-field')[0]!.text()).toContain('title')
    expect(result.get('.draft-preview-row').attributes('data-row-index')).toBe('1')
    expect(result.get('.draft-preview-row').text()).toContain('<b>remote</b>')
    expect(result.get('.draft-preview-row').text()).toContain('null')
    expect(result.find('.draft-preview-row b').exists()).toBe(false)
    expect(result.text()).not.toContain('display_name')
    expect(result.text()).not.toContain('UNPROJECTED_SECRET')
    expect(result.text()).not.toContain('RAW_BODY_SECRET')
    expect(result.text()).not.toContain('api.example.com')
    expect(result.text()).not.toContain('EXAMINE_DS_SECRET')
    expect(wrapper.find('.preview-pagination').exists()).toBe(false)
    expect(wrapper.find('.statistics-tester').exists()).toBe(false)
    expect(wrapper.find('.published-preview').exists()).toBe(false)

    await wrapper.get('.http-json-endpoint').setValue('https://api.example.com/v3/orders')
    expect(wrapper.find('.draft-preview-result').exists()).toBe(false)
  })

  it('clears a completed draft rows preview when its projection changes', async () => {
    vi.mocked(dataSourceAdminApi.list).mockResolvedValue([projectedHttpDetail])
    vi.mocked(dataSourceAdminApi.detail).mockResolvedValue(projectedHttpDetail)
    vi.mocked(dataSourceAdminApi.versions).mockResolvedValue([])
    vi.mocked(dataSourceAdminApi.previewDraftRows).mockResolvedValue({
      reachable: true, contractValid: true, httpStatus: 200, durationMillis: 9,
      code: 'SUCCESS', message: 'HTTP JSON draft rows preview succeeded', checkedDraftVersion: 2,
      fields: [{ fieldCode: 'title', sourceType: 'STRING' }],
      rows: [{ rowIndex: 1, values: { title: 'safe' } }],
    })
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.draft-rows-preview').trigger('click')
    await flushPromises()
    expect(wrapper.find('.draft-preview-result').exists()).toBe(true)
    await wrapper.findAll('.http-projection-remove')[1]!.trigger('click')

    expect(wrapper.find('.draft-preview-result').exists()).toBe(false)
    expect(wrapper.find('[data-state="dirty"]').exists()).toBe(true)
    expect(wrapper.findAll('.http-projection-row')).toHaveLength(1)
  })

  it('discards a late draft rows preview after selecting another source', async () => {
    vi.mocked(dataSourceAdminApi.list).mockResolvedValue([projectedHttpDetail, baseDetail])
    vi.mocked(dataSourceAdminApi.detail).mockImplementation(async (_systemId, id) =>
      id === projectedHttpDetail.id ? projectedHttpDetail : baseDetail)
    vi.mocked(dataSourceAdminApi.versions).mockResolvedValue([])
    let resolvePreview!: (value: any) => void
    vi.mocked(dataSourceAdminApi.previewDraftRows).mockReturnValue(new Promise(resolve => {
      resolvePreview = resolve
    }))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.draft-rows-preview').trigger('click')
    await flushPromises()
    await wrapper.findAll('.source-list-item')[1]!.trigger('click')
    await flushPromises()
    resolvePreview({
      reachable: true, contractValid: true, httpStatus: 200, durationMillis: 11,
      code: 'SUCCESS', message: 'HTTP JSON draft rows preview succeeded', checkedDraftVersion: 2,
      fields: [{ fieldCode: 'title', sourceType: 'STRING' }],
      rows: [{ rowIndex: 1, values: { title: 'LATE_ROW' } }],
    })
    await flushPromises()

    expect(wrapper.find('.draft-preview-result').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('LATE_ROW')
    expect(wrapper.find('.draft-rows-preview').exists()).toBe(false)
    expect((wrapper.get('.data-source-kind').element as HTMLSelectElement).value).toBe('NATIVE_MODULE')
  })

  it('saves a dirty mapped HTTP draft once and publishes it without ordinary check or runtime calls', async () => {
    const saved: DataSourceDetail = {
      ...projectedHttpDetail,
      version: 5,
      draftVersion: 3,
      draft: {
        ...projectedHttpDetail.draft,
        httpJsonConnection: { ...projectedHttpDetail.draft.httpJsonConnection!, timeoutSeconds: 6 },
      },
    }
    const published: DataSourceDetail = {
      ...saved,
      version: 6,
      activeVersionId: '70',
      activeVersionNumber: 1,
    }
    const publishedVersion: DataSourceVersion = { ...httpVersion, snapshot: saved.draft }
    vi.mocked(dataSourceAdminApi.list).mockResolvedValue([projectedHttpDetail])
    vi.mocked(dataSourceAdminApi.detail).mockResolvedValue(projectedHttpDetail)
    vi.mocked(dataSourceAdminApi.versions)
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([publishedVersion])
    vi.mocked(dataSourceAdminApi.saveDraft).mockResolvedValue(saved)
    let resolvePublish!: (value: { source: DataSourceDetail, version: DataSourceVersion }) => void
    vi.mocked(dataSourceAdminApi.publishDraft).mockReturnValue(new Promise(resolve => {
      resolvePublish = resolve
    }))
    const wrapper = render()
    await flushPromises()

    expect(wrapper.get('.draft-publish').attributes('disabled')).toBeUndefined()
    await wrapper.get('.http-json-timeout').setValue('6')
    await wrapper.get('.draft-publish').trigger('click')
    await wrapper.get('.draft-publish').trigger('click')
    await flushPromises()

    expect(dataSourceAdminApi.saveDraft).toHaveBeenCalledTimes(1)
    expect(dataSourceAdminApi.saveDraft).toHaveBeenCalledWith('10', '31', expect.objectContaining({
      expectedVersion: 2,
      httpFieldProjections: projectedHttpDetail.draft.httpFieldProjections,
    }))
    expect(dataSourceAdminApi.checkDraft).not.toHaveBeenCalled()
    expect(dataSourceAdminApi.publishDraft).toHaveBeenCalledTimes(1)
    expect(dataSourceAdminApi.publishDraft).toHaveBeenCalledWith('10', '31', { expectedVersion: 3 })
    expect(wrapper.get('.draft-publish').attributes('disabled')).toBeDefined()

    resolvePublish({ source: published, version: publishedVersion })
    await flushPromises()

    expect(wrapper.find('[data-state="published"]').exists()).toBe(true)
    expect(wrapper.get('.source-list-item').text()).toContain('v1')
    expect(wrapper.get('.version-history').text()).toContain('httpabcdef12')
    expect(wrapper.find('.published-preview').exists()).toBe(false)
    expect(wrapper.find('.statistics-tester').exists()).toBe(true)
    expect(runtimeDataSourceApi.metadata).not.toHaveBeenCalled()
    expect(runtimeDataSourceApi.rows).not.toHaveBeenCalled()
    expect(runtimeDataSourceApi.statisticsCapabilities).toHaveBeenCalledWith('10', 'external_orders')
    expect(runtimeDataSourceApi.statistics).not.toHaveBeenCalled()
  })

  it('discards a late HTTP publish response after selecting another source', async () => {
    const otherHttp: DataSourceDetail = {
      ...projectedHttpDetail,
      id: '32',
      code: 'external_orders_two',
      name: '外部订单二',
    }
    const latePublished: DataSourceDetail = {
      ...projectedHttpDetail,
      version: 5,
      activeVersionId: '70',
      activeVersionNumber: 1,
    }
    vi.mocked(dataSourceAdminApi.list).mockResolvedValue([projectedHttpDetail, otherHttp])
    vi.mocked(dataSourceAdminApi.detail).mockImplementation(async (_systemId, id) =>
      id === projectedHttpDetail.id ? projectedHttpDetail : otherHttp)
    vi.mocked(dataSourceAdminApi.versions).mockResolvedValue([])
    let resolvePublish!: (value: { source: DataSourceDetail, version: DataSourceVersion }) => void
    vi.mocked(dataSourceAdminApi.publishDraft).mockReturnValue(new Promise(resolve => {
      resolvePublish = resolve
    }))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.draft-publish').trigger('click')
    await wrapper.get('.draft-publish').trigger('click')
    expect(dataSourceAdminApi.publishDraft).toHaveBeenCalledTimes(1)
    await wrapper.findAll('.source-list-item')[1]!.trigger('click')
    await flushPromises()
    resolvePublish({ source: latePublished, version: httpVersion })
    await flushPromises()

    expect(wrapper.get('.editor-title-line code').text()).toBe('external_orders_two')
    expect(wrapper.find('[data-state="published"]').exists()).toBe(false)
    expect(wrapper.get('.version-history').text()).not.toContain('httpabcdef12')
    expect(dataSourceAdminApi.versions).toHaveBeenCalledTimes(2)
    expect(runtimeDataSourceApi.metadata).not.toHaveBeenCalled()
    expect(runtimeDataSourceApi.rows).not.toHaveBeenCalled()
  })

  it('shows a fixed HTTP publication failure without retrying preflight or CAS', async () => {
    vi.mocked(dataSourceAdminApi.list).mockResolvedValue([projectedHttpDetail])
    vi.mocked(dataSourceAdminApi.detail).mockResolvedValue(projectedHttpDetail)
    vi.mocked(dataSourceAdminApi.versions).mockResolvedValue([])
    vi.mocked(dataSourceAdminApi.publishDraft).mockRejectedValue(new Error('HTTP_PUBLISH_PREFLIGHT_FAILED'))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.draft-publish').trigger('click')
    await flushPromises()

    expect(dataSourceAdminApi.checkDraft).not.toHaveBeenCalled()
    expect(dataSourceAdminApi.publishDraft).toHaveBeenCalledTimes(1)
    expect(dataSourceAdminApi.publishDraft).toHaveBeenCalledWith('10', '31', { expectedVersion: 2 })
    expect(wrapper.get('.data-source-error').text()).toContain('HTTP_PUBLISH_PREFLIGHT_FAILED')
    expect(wrapper.find('[data-state="error"]').exists()).toBe(true)
    expect(dataSourceAdminApi.versions).toHaveBeenCalledTimes(1)
    expect(runtimeDataSourceApi.metadata).not.toHaveBeenCalled()
    expect(runtimeDataSourceApi.rows).not.toHaveBeenCalled()
  })

  it('keeps published HTTP row loading explicit while loading its statistics contract', async () => {
    vi.mocked(dataSourceAdminApi.list).mockResolvedValue([activeHttpDetail])
    vi.mocked(dataSourceAdminApi.detail).mockResolvedValue(activeHttpDetail)
    vi.mocked(dataSourceAdminApi.versions).mockResolvedValue([httpVersion])

    const wrapper = render()
    await flushPromises()

    expect(wrapper.find('.published-http-rows-load').exists()).toBe(true)
    expect(wrapper.find('.published-http-rows-result').exists()).toBe(false)
    expect(runtimeDataSourceApi.httpRows).not.toHaveBeenCalled()
    expect(runtimeDataSourceApi.metadata).not.toHaveBeenCalled()
    expect(runtimeDataSourceApi.rows).not.toHaveBeenCalled()
    expect(runtimeDataSourceApi.statisticsCapabilities).toHaveBeenCalledWith('10', 'external_orders')
  })

  it('loads one safe text-only published HTTP page and guards double submit', async () => {
    vi.mocked(dataSourceAdminApi.list).mockResolvedValue([activeHttpDetail])
    vi.mocked(dataSourceAdminApi.detail).mockResolvedValue(activeHttpDetail)
    vi.mocked(dataSourceAdminApi.versions).mockResolvedValue([httpVersion])
    let resolveRows!: (value: PublishedHttpDataSourceRows) => void
    vi.mocked(runtimeDataSourceApi.httpRows).mockReturnValue(new Promise(resolve => {
      resolveRows = resolve
    }))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.published-http-rows-load').trigger('click')
    await wrapper.get('.published-http-rows-load').trigger('click')
    expect(runtimeDataSourceApi.httpRows).toHaveBeenCalledTimes(1)
    expect(runtimeDataSourceApi.httpRows).toHaveBeenCalledWith('10', 'external_orders')
    expect(wrapper.get('.published-http-rows-load').attributes('disabled')).toBeDefined()

    resolveRows({
      ...publishedHttpRows,
      endpoint: 'FORBIDDEN_ENDPOINT',
      sourceField: 'FORBIDDEN_SOURCE_FIELD',
      secret: 'FORBIDDEN_SECRET',
      raw: 'FORBIDDEN_RAW',
      total: 999,
      queryHash: 'FORBIDDEN_QUERY_HASH',
      rows: [{
        ...publishedHttpRows.rows[0]!,
        recordId: 'FORBIDDEN_RECORD_ID',
        status: 'FORBIDDEN_STATUS',
        title: 'FORBIDDEN_RECORD_TITLE',
      }],
    } as unknown as PublishedHttpDataSourceRows)
    await flushPromises()

    const result = wrapper.get('.published-http-rows-result')
    expect(result.findAll('.published-http-field')).toHaveLength(5)
    expect(result.findAll('.published-http-field')[0]!.text()).toContain('external_name')
    expect(result.findAll('.published-http-field')[0]!.text()).toContain('STRING')
    expect(result.get('.published-http-row').attributes('data-row-index')).toBe('1')
    expect(result.get('.published-http-row').text()).toContain('<b>remote</b>')
    expect(result.get('.published-http-row').text()).toContain('7')
    expect(result.get('.published-http-row').text()).toContain('12.5')
    expect(result.get('.published-http-row').text()).toContain('false')
    expect(result.get('.published-http-row').text()).toContain('null')
    expect(result.find('b').exists()).toBe(false)
    expect(result.text()).not.toMatch(/FORBIDDEN_(ENDPOINT|SOURCE_FIELD|SECRET|RAW|QUERY_HASH|RECORD_ID|STATUS|RECORD_TITLE)/u)
    expect(wrapper.find('.preview-pagination').exists()).toBe(false)
    expect(wrapper.find('.statistics-tester').exists()).toBe(true)
    expect(wrapper.find('.published-preview').exists()).toBe(false)
  })

  it('discards a late published HTTP response after selecting another source', async () => {
    const otherVersion: DataSourceVersion = {
      ...httpVersion,
      id: '71',
      dataSourceId: '32',
      code: 'external_orders_two',
      versionNumber: 2,
    }
    const otherHttp: DataSourceDetail = {
      ...activeHttpDetail,
      id: '32',
      code: otherVersion.code,
      name: '外部订单二',
      activeVersionId: otherVersion.id,
      activeVersionNumber: otherVersion.versionNumber,
    }
    vi.mocked(dataSourceAdminApi.list).mockResolvedValue([activeHttpDetail, otherHttp])
    vi.mocked(dataSourceAdminApi.detail).mockImplementation(async (_systemId, id) =>
      id === activeHttpDetail.id ? activeHttpDetail : otherHttp)
    vi.mocked(dataSourceAdminApi.versions).mockImplementation(async (_systemId, id) =>
      id === activeHttpDetail.id ? [httpVersion] : [otherVersion])
    let resolveRows!: (value: PublishedHttpDataSourceRows) => void
    vi.mocked(runtimeDataSourceApi.httpRows).mockReturnValue(new Promise(resolve => {
      resolveRows = resolve
    }))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.published-http-rows-load').trigger('click')
    await wrapper.findAll('.source-list-item')[1]!.trigger('click')
    await flushPromises()
    resolveRows(publishedHttpRows)
    await flushPromises()

    expect(runtimeDataSourceApi.httpRows).toHaveBeenCalledTimes(1)
    expect(wrapper.get('.editor-title-line code').text()).toBe('external_orders_two')
    expect(wrapper.find('.published-http-rows-result').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('<b>remote</b>')
  })

  it('discards a published HTTP response for a stale active version', async () => {
    vi.mocked(dataSourceAdminApi.list).mockResolvedValue([activeHttpDetail])
    vi.mocked(dataSourceAdminApi.detail).mockResolvedValue(activeHttpDetail)
    vi.mocked(dataSourceAdminApi.versions).mockResolvedValue([httpVersion])
    vi.mocked(runtimeDataSourceApi.httpRows).mockResolvedValue({
      ...publishedHttpRows,
      dataSourceVersionId: '69',
      dataSourceVersionNumber: 0,
    })
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.published-http-rows-load').trigger('click')
    await flushPromises()

    expect(runtimeDataSourceApi.httpRows).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.published-http-rows-result').exists()).toBe(false)
    expect(wrapper.find('.published-http-rows-error').exists()).toBe(false)
  })

  it('shows a fixed safe published HTTP error without provider detail', async () => {
    vi.mocked(dataSourceAdminApi.list).mockResolvedValue([activeHttpDetail])
    vi.mocked(dataSourceAdminApi.detail).mockResolvedValue(activeHttpDetail)
    vi.mocked(dataSourceAdminApi.versions).mockResolvedValue([httpVersion])
    vi.mocked(runtimeDataSourceApi.httpRows).mockRejectedValue(
      new Error('PROVIDER_ENDPOINT_LEAK PROVIDER_SECRET_LEAK PROVIDER_RAW_LEAK'),
    )
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.published-http-rows-load').trigger('click')
    await flushPromises()

    const error = wrapper.get('.published-http-rows-error')
    expect(error.text()).toContain('HTTP_PUBLISHED_ROWS_LOAD_FAILED')
    expect(error.text()).not.toMatch(/PROVIDER_(ENDPOINT|SECRET|RAW)_LEAK/u)
    expect(runtimeDataSourceApi.httpRows).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.published-http-rows-result').exists()).toBe(false)
  })

  it('renders a successful empty published HTTP page without Native controls', async () => {
    vi.mocked(dataSourceAdminApi.list).mockResolvedValue([activeHttpDetail])
    vi.mocked(dataSourceAdminApi.detail).mockResolvedValue(activeHttpDetail)
    vi.mocked(dataSourceAdminApi.versions).mockResolvedValue([httpVersion])
    vi.mocked(runtimeDataSourceApi.httpRows).mockResolvedValue({
      ...publishedHttpRows,
      rows: [],
    })
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.published-http-rows-load').trigger('click')
    await flushPromises()

    expect(wrapper.find('.published-http-rows-result').exists()).toBe(true)
    expect(wrapper.findAll('.published-http-row')).toHaveLength(0)
    expect(wrapper.get('.published-http-rows-result').text()).toContain('第一页没有返回数据')
    expect(wrapper.find('.preview-pagination').exists()).toBe(false)
    expect(wrapper.find('.statistics-tester').exists()).toBe(true)
  })

  it('renders the frozen management table, filters and five detail tabs', async () => {
    vi.mocked(dataSourceAdminApi.list).mockResolvedValue([baseDetail, projectedHttpDetail, jdbcDetail])
    const wrapper = render()
    await flushPromises()

    expect(wrapper.findAll('.source-list-head th').map(item => item.text())).toEqual([
      '名称', '类型', '模块/目标', '版本', '状态', '操作',
    ])
    expect(wrapper.findAll('.source-list-item')).toHaveLength(3)
    expect(wrapper.findAll('.data-source-tabs button').map(item => item.text())).toEqual([
      '概览', '连接与字段', '预览与运行', '版本记录', '统计',
    ])
    await wrapper.get('.source-kind-filter').setValue('JDBC_TABLE')
    expect(wrapper.findAll('.source-list-item')).toHaveLength(1)
    expect(wrapper.get('.source-list-item').text()).toContain('MySQL 订单')
    await wrapper.get('.source-kind-filter').setValue('ALL')
    await wrapper.get('.source-search').setValue('external_orders')
    expect(wrapper.findAll('.source-list-item')).toHaveLength(1)
    expect(wrapper.get('.source-list-item').text()).toContain('外部订单')
  })

  it('saves only structured JDBC fields and keeps configured SecretRefs blank', async () => {
    const saved: DataSourceDetail = {
      ...jdbcDetail,
      draftVersion: 3,
      version: 3,
      draft: {
        ...jdbcDetail.draft,
        jdbcTableConnection: {
          ...jdbcDetail.draft.jdbcTableConnection!,
          host: 'mysql-ro.example.internal',
        },
      },
    }
    vi.mocked(dataSourceAdminApi.list).mockResolvedValue([jdbcDetail])
    vi.mocked(dataSourceAdminApi.detail).mockResolvedValue(jdbcDetail)
    vi.mocked(dataSourceAdminApi.versions).mockResolvedValue([jdbcVersion])
    vi.mocked(dataSourceAdminApi.saveDraft).mockResolvedValue(saved)
    const wrapper = render()
    await flushPromises()

    expect((wrapper.get('.jdbc-username-secret-ref').element as HTMLInputElement).value).toBe('')
    expect((wrapper.get('.jdbc-password-secret-ref').element as HTMLInputElement).value).toBe('')
    expect(wrapper.get('.jdbc-table-connection').text()).toContain('已配置，留空保留')
    await wrapper.get('.jdbc-host').setValue('mysql-ro.example.internal')
    await wrapper.get('.draft-save').trigger('click')
    await flushPromises()

    expect(dataSourceAdminApi.saveDraft).toHaveBeenCalledTimes(1)
    const input = vi.mocked(dataSourceAdminApi.saveDraft).mock.calls[0]![2]
    expect(input).toMatchObject({
      expectedVersion: 2,
      sourceKind: 'JDBC_TABLE',
      outputFields: [], fixedFilters: [], defaultSort: null, defaultTimeFieldCode: null,
      jdbcTableConnection: {
        host: 'mysql-ro.example.internal', port: 3306, databaseName: 'orders_db', tableName: 'orders',
        usernameSecretRef: '', passwordSecretRef: '', connectTimeoutSeconds: 5, queryTimeoutSeconds: 10,
      },
      jdbcFieldProjections: jdbcDetail.draft.jdbcFieldProjections,
    })
    expect(JSON.stringify(input)).not.toMatch(/jdbc:|driverClass|connectionProperties|\bsql\b/iu)
    expect(runtimeDataSourceApi.jdbcRows).not.toHaveBeenCalled()
    expect(runtimeDataSourceApi.metadata).not.toHaveBeenCalled()
  })

  it('never auto-loads JDBC rows and guards one explicit safe published read', async () => {
    vi.mocked(dataSourceAdminApi.list).mockResolvedValue([jdbcDetail])
    vi.mocked(dataSourceAdminApi.detail).mockResolvedValue(jdbcDetail)
    vi.mocked(dataSourceAdminApi.versions).mockResolvedValue([jdbcVersion])
    let resolveRows!: (value: any) => void
    vi.mocked(runtimeDataSourceApi.jdbcRows).mockReturnValue(new Promise(resolve => { resolveRows = resolve }))
    const wrapper = render()
    await flushPromises()

    expect(runtimeDataSourceApi.jdbcRows).not.toHaveBeenCalled()
    expect(runtimeDataSourceApi.metadata).not.toHaveBeenCalled()
    await wrapper.get('.published-jdbc-rows-load').trigger('click')
    await wrapper.get('.published-jdbc-rows-load').trigger('click')
    expect(runtimeDataSourceApi.jdbcRows).toHaveBeenCalledTimes(1)
    expect(runtimeDataSourceApi.jdbcRows).toHaveBeenCalledWith('10', 'mysql_orders')
    resolveRows({
      dataSourceId: '33', dataSourceCode: 'mysql_orders', dataSourceVersionId: '72', dataSourceVersionNumber: 1,
      fields: [{ fieldCode: 'title', sourceType: 'STRING' }, { fieldCode: 'amount', sourceType: 'DECIMAL' }],
      rows: [{ rowIndex: 1, values: { title: '<b>mysql</b>', amount: 18.5 } }],
    })
    await flushPromises()

    const result = wrapper.get('.published-jdbc-rows-result')
    expect(result.findAll('.published-jdbc-field')).toHaveLength(2)
    expect(result.get('.published-jdbc-row').attributes('data-row-index')).toBe('1')
    expect(result.text()).toContain('<b>mysql</b>')
    expect(result.find('b').exists()).toBe(false)
    expect(wrapper.find('.preview-pagination').exists()).toBe(false)
    expect(wrapper.find('.statistics-tester').exists()).toBe(true)
  })
})
