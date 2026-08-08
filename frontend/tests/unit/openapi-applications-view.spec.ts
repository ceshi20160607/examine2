import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { openApiApplicationApi } from '@/services/openapi'
import type { OpenApiApplication, OpenApiCallLog, OpenApiCallLogQuery } from '@/types/openapi'
import OpenApiApplicationsView from '@/views/system/admin/OpenApiApplicationsView.vue'

const route = vi.hoisted(() => ({ params: { systemId: '10' } }))
const clipboardWrite = vi.fn()

vi.mock('vue-router', () => ({
  useRoute: () => route,
}))

vi.mock('@/services/openapi', () => ({
  openApiApplicationApi: {
    list: vi.fn(),
    detail: vi.fn(),
    callLogs: vi.fn(),
    create: vi.fn(),
    updatePolicy: vi.fn(),
    rotateSecretRef: vi.fn(),
    changeStatus: vi.fn(),
  },
}))

const application: OpenApiApplication = {
  id: 'application-8',
  appKey: 'app_01J_OPENAPI',
  name: 'ERP connector',
  status: 'ACTIVE',
  systemId: '10',
  tenantId: '20',
  serviceMemberId: '30',
  secretRef: 'vault://openapi/erp/v1',
  scopes: ['ping'],
  ipAllowlist: ['10.0.0.0/24'],
  rateLimitPerMinute: 120,
  credentialVersion: 2,
  version: 4,
  createdAt: '2026-07-29T01:00:00Z',
  updatedAt: '2026-07-29T02:00:00Z',
}

const callLog: OpenApiCallLog = {
  id: 'call-log-91',
  credentialVersion: 2,
  routeTemplate: '/openapi/v1/modules/{moduleCode}/records',
  requestMethod: 'GET',
  resultCategory: 'SUCCESS',
  httpStatus: 200,
  latencyMs: 37,
  requestId: 'request-call-log-91',
  traceId: 'trace-call-log-91',
  observedIp: '2001:db8::8',
  createdAt: '2026-08-05T03:00:00Z',
}

function render() {
  return mount(OpenApiApplicationsView, {
    global: {
      stubs: {
        AdminPageHeader: {
          props: ['title', 'description'],
          template: '<header><h1>{{ title }}</h1><p>{{ description }}</p><slot name="actions" /></header>',
        },
        Copy: true,
        Eye: true,
        KeyRound: true,
        Pencil: true,
        Plus: true,
        Power: true,
        RefreshCw: true,
        RotateCw: true,
        'a-alert': {
          props: ['message', 'description'],
          template: '<div class="alert">{{ message }} {{ description }}<slot /></div>',
        },
        'a-button': {
          inheritAttrs: false,
          props: ['disabled', 'loading'],
          emits: ['click'],
          template: '<button v-bind="$attrs" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
        },
        'a-drawer': {
          props: ['open'],
          emits: ['update:open'],
          template: '<div v-if="open" class="detail-drawer"><slot /></div>',
        },
        'a-empty': {
          props: ['description'],
          template: '<div>{{ description }}</div>',
        },
        'a-form': { template: '<form><slot /></form>' },
        'a-form-item': { template: '<div><slot /></div>' },
        'a-input': {
          inheritAttrs: false,
          props: ['value'],
          emits: ['update:value'],
          template: '<input v-bind="$attrs" :value="value" @input="$emit(\'update:value\', $event.target.value)" />',
        },
        'a-input-number': {
          inheritAttrs: false,
          props: ['value'],
          emits: ['update:value'],
          template: '<input v-bind="$attrs" type="number" :value="value" @input="$emit(\'update:value\', Number($event.target.value))" />',
        },
        'a-modal': {
          inheritAttrs: false,
          props: ['open'],
          emits: ['ok', 'update:open'],
          template: '<div v-if="open" v-bind="$attrs"><button class="modal-ok" @click="$emit(\'ok\')">ok</button><slot /></div>',
        },
        'a-spin': { template: '<div><slot /></div>' },
        'a-table': {
          props: ['dataSource'],
          template: `
            <div>
              <div v-for="record in dataSource" :key="record.id">
                <slot name="bodyCell" :column="{ key: 'application' }" :record="record" />
                <slot name="bodyCell" :column="{ key: 'actions' }" :record="record" />
              </div>
            </div>
          `,
        },
        'a-tag': { template: '<span><slot /></span>' },
        'a-textarea': {
          inheritAttrs: false,
          props: ['value'],
          emits: ['update:value'],
          template: '<textarea v-bind="$attrs" :value="value" @input="$emit(\'update:value\', $event.target.value)" />',
        },
      },
    },
  })
}

describe('OpenApiApplicationsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    clipboardWrite.mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText: clipboardWrite },
    })
    route.params.systemId = '10'
    vi.mocked(openApiApplicationApi.list).mockResolvedValue({
      items: [application],
      page: 1,
      size: 100,
      total: 1,
    })
    vi.mocked(openApiApplicationApi.detail).mockResolvedValue(application)
    vi.mocked(openApiApplicationApi.callLogs).mockResolvedValue({
      items: [callLog],
      page: 1,
      size: 20,
      total: 1,
    })
    vi.mocked(openApiApplicationApi.create).mockResolvedValue(application)
    vi.mocked(openApiApplicationApi.updatePolicy).mockResolvedValue(application)
    vi.mocked(openApiApplicationApi.rotateSecretRef).mockResolvedValue(application)
    vi.mocked(openApiApplicationApi.changeStatus).mockResolvedValue(application)
  })

  it('lists applications and shows the frozen identity, credential and binding detail', async () => {
    const wrapper = render()
    await flushPromises()

    expect(openApiApplicationApi.list).toHaveBeenCalledWith('10', 1, 100)
    expect(wrapper.text()).toContain('仅提交 SecretRef')
    expect(wrapper.text()).toContain('不接收、不生成、也不展示任何明文应用密钥')
    expect(wrapper.text()).toContain('app_01J_OPENAPI')

    await wrapper.get('.openapi-application-link').trigger('click')
    await flushPromises()

    expect(openApiApplicationApi.detail).toHaveBeenCalledWith('10', 'application-8')
    const detail = wrapper.get('.openapi-detail-list').text()
    expect(detail).toContain('app_01J_OPENAPI')
    expect(detail).toContain('v2')
    expect(detail).toContain('ACTIVE')
    expect(detail).toContain('30')
    expect(detail).toContain('20')
    expect(detail).toContain('vault://openapi/erp/v1')
  })

  it('loads call logs only after a real application detail is selected', async () => {
    let resolveDetail!: (value: OpenApiApplication) => void
    vi.mocked(openApiApplicationApi.detail).mockReturnValueOnce(new Promise(resolve => { resolveDetail = resolve }))
    const wrapper = render()
    await flushPromises()

    expect(openApiApplicationApi.callLogs).not.toHaveBeenCalled()
    await wrapper.get('.openapi-application-link').trigger('click')
    await flushPromises()
    expect(openApiApplicationApi.callLogs).not.toHaveBeenCalled()

    resolveDetail(application)
    await flushPromises()
    expect(openApiApplicationApi.callLogs).toHaveBeenCalledWith('10', 'application-8', {
      resultCategory: 'ALL',
      requestMethod: 'ALL',
      page: 1,
      size: 20,
    })
  })

  it('shows only the frozen safe call-log fields and no log mutation or export', async () => {
    vi.mocked(openApiApplicationApi.callLogs).mockResolvedValueOnce({
      items: [{
        ...callLog,
        appKeyHash: 'do-not-render-hash',
        secretRef: 'do-not-render-secret',
      } as OpenApiCallLog],
      page: 1,
      size: 20,
      total: 1,
    })
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.openapi-application-link').trigger('click')
    await flushPromises()

    const logs = wrapper.get('.openapi-call-logs').text()
    expect(logs).toContain('GET /openapi/v1/modules/{moduleCode}/records')
    expect(logs).toContain('SUCCESS')
    expect(logs).toContain('200')
    expect(logs).toContain('37 ms')
    expect(logs).toContain('v2')
    expect(logs).toContain('request-call-log-91')
    expect(logs).toContain('trace-call-log-91')
    expect(logs).toContain('2001:db8::8')
    expect(logs).toContain('call-log-91')
    expect(logs).not.toContain('do-not-render-hash')
    expect(logs).not.toContain('do-not-render-secret')
    expect(logs).not.toContain('导出')
    expect(logs).not.toContain('删除')
  })

  it('filters, pages and refreshes the current application log query', async () => {
    vi.mocked(openApiApplicationApi.callLogs).mockImplementation(async (
      _systemId: string,
      _applicationId: string,
      query: OpenApiCallLogQuery = {},
    ) => ({ items: [callLog], page: query.page ?? 1, size: query.size ?? 20, total: 41 }))
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.openapi-application-link').trigger('click')
    await flushPromises()

    await wrapper.get('.openapi-call-log-result').setValue('RATE_REJECTED')
    await flushPromises()
    expect(vi.mocked(openApiApplicationApi.callLogs).mock.calls.at(-1)?.[2]).toEqual({
      resultCategory: 'RATE_REJECTED', requestMethod: 'ALL', page: 1, size: 20,
    })

    await wrapper.get('.openapi-call-log-method').setValue('POST')
    await flushPromises()
    expect(vi.mocked(openApiApplicationApi.callLogs).mock.calls.at(-1)?.[2]).toEqual({
      resultCategory: 'RATE_REJECTED', requestMethod: 'POST', page: 1, size: 20,
    })

    await wrapper.get('.openapi-call-log-next').trigger('click')
    await flushPromises()
    expect(vi.mocked(openApiApplicationApi.callLogs).mock.calls.at(-1)?.[2]).toEqual({
      resultCategory: 'RATE_REJECTED', requestMethod: 'POST', page: 2, size: 20,
    })

    await wrapper.get('.openapi-call-log-refresh').trigger('click')
    await flushPromises()
    expect(vi.mocked(openApiApplicationApi.callLogs).mock.calls.at(-1)?.[2]).toEqual({
      resultCategory: 'RATE_REJECTED', requestMethod: 'POST', page: 2, size: 20,
    })
  })

  it('renders call-log loading, empty and error states', async () => {
    let resolveLogs!: (value: { items: OpenApiCallLog[], page: number, size: number, total: number }) => void
    vi.mocked(openApiApplicationApi.callLogs).mockReturnValueOnce(new Promise(resolve => { resolveLogs = resolve }))
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.openapi-application-link').trigger('click')
    await flushPromises()
    expect(wrapper.find('.openapi-call-log-loading').exists()).toBe(true)

    resolveLogs({ items: [], page: 1, size: 20, total: 0 })
    await flushPromises()
    expect(wrapper.get('.openapi-call-log-empty').text()).toContain('暂无调用日志')

    vi.mocked(openApiApplicationApi.callLogs).mockRejectedValueOnce(new Error('OPENAPI_CALL_LOG_READ_FAILED'))
    await wrapper.get('.openapi-call-log-refresh').trigger('click')
    await flushPromises()
    expect(wrapper.get('.openapi-call-log-error').text()).toContain('OPENAPI_CALL_LOG_READ_FAILED')
  })

  it('shows safe placeholder-only read and mutation examples and copies one signed request', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.openapi-application-link').trigger('click')
    await flushPromises()

    const examples = wrapper.findAll('.openapi-request-example')
    expect(examples).toHaveLength(17)

    const create = wrapper.get('[data-example="create"]').text()
    const list = wrapper.get('[data-example="list"]').text()
    const detail = wrapper.get('[data-example="detail"]').text()
    expect(create).toContain('POST')
    expect(create).toContain('/openapi/v1/modules/<moduleCode>/records')
    expect(create).toContain('"lifecycleState": "DRAFT"')
    expect(create).toContain('"values": { "fieldCode": "FIELD_VALUE" }')
    expect(list).toContain('GET')
    expect(list).toContain('/openapi/v1/modules/<moduleCode>/records?page=1&size=20')
    expect(detail).toContain('GET')
    expect(detail).toContain('/openapi/v1/modules/<moduleCode>/records/<recordId>')

    const update = wrapper.get('[data-example="update"]').text()
    expect(update).toContain('PUT')
    expect(update).toContain('/openapi/v1/modules/<moduleCode>/records/<recordId>')
    expect(update).toContain('"expectedVersion": <expectedVersion>')
    expect(update).toContain('"values": { "fieldCode": "UPDATED_FIELD_VALUE" }')

    for (const action of ['activate', 'archive', 'unarchive', 'trash', 'restore-from-trash']) {
      const lifecycle = wrapper.get(`[data-example="${action}"]`).text()
      expect(lifecycle).toContain('POST')
      expect(lifecycle).toContain(
        `/openapi/v1/modules/<moduleCode>/records/<recordId>:${action}`,
      )
      expect(lifecycle).toContain('"expectedVersion": <expectedVersion>')
    }

    const fileUpload = wrapper.get('[data-example="file-upload"]').text()
    expect(fileUpload).toContain('POST')
    expect(fileUpload).toContain('/records/<recordId>/files')
    expect(fileUpload).toContain('"originalName": "<originalName>"')
    expect(fileUpload).toContain('"mediaType": "<mediaType>"')
    expect(fileUpload).toContain('"contentBase64": "<contentBase64>"')

    const fileList = wrapper.get('[data-example="file-list"]').text()
    expect(fileList).toContain('GET')
    expect(fileList).toContain('/records/<recordId>/files?page=1&size=20')

    const fileDownload = wrapper.get('[data-example="file-download"]').text()
    expect(fileDownload).toContain('GET')
    expect(fileDownload).toContain('/records/<recordId>/files/<fileId>/content')
    expect(fileDownload).toContain("--output '<downloadFileName>'")

    const flowStatus = wrapper.get('[data-example="flow-status"]').text()
    expect(flowStatus).toContain('查询流程实例状态 · flow.read')
    expect(flowStatus).toContain('Required application scope: flow.read')
    expect(flowStatus).toContain('GET')
    expect(flowStatus).toContain('/openapi/v1/flow/instances/<instanceId>')

    const relationList = wrapper.get('[data-example="relation-list"]').text()
    expect(relationList).toContain('Required application scope: record.read')
    expect(relationList).toContain('GET')
    expect(relationList).toContain(
      '/records/<recordId>/relations/<fieldCode>?page=1&size=20',
    )

    const relationMutate = wrapper.get('[data-example="relation-mutate"]').text()
    expect(relationMutate).toContain('Required application scope: record.write')
    expect(relationMutate).toContain('POST')
    expect(relationMutate).toContain('/records/<recordId>/relations/<fieldCode>:mutate')
    expect(relationMutate).toContain('"expectedVersion": <expectedVersion>')
    expect(relationMutate).toContain('"targetRecordId": "<targetRecordId>"')
    expect(relationMutate).toContain('"targetExpectedVersion": <targetExpectedVersion>')
    expect(relationMutate).toContain('"remove": []')
    expect(relationMutate).toContain('"order": []')

    const subtableList = wrapper.get('[data-example="subtable-list"]').text()
    expect(subtableList).toContain('Required application scope: record.read')
    expect(subtableList).toContain('GET')
    expect(subtableList).toContain(
      '/records/<recordId>/subtables/<fieldCode>?page=1&size=20',
    )

    const subtableMutate = wrapper.get('[data-example="subtable-mutate"]').text()
    expect(subtableMutate).toContain('Required application scope: record.write')
    expect(subtableMutate).toContain('POST')
    expect(subtableMutate).toContain('/records/<recordId>/subtables/<fieldCode>:mutate')
    expect(subtableMutate).toContain('"clientRowKey": "<clientRowKey>"')
    expect(subtableMutate).toContain('"values": { "<subFieldCode>": "SUB_FIELD_VALUE" }')
    expect(subtableMutate).toContain('"update": []')
    expect(subtableMutate).toContain('"remove": []')
    expect(subtableMutate).toContain('"order": []')

    const exampleText = wrapper.get('.openapi-request-examples').text()
    for (const placeholder of [
      '<APP_KEY>', '<SECRET>', '<timestamp>', '<nonce>', '<Idempotency-Key>', '<moduleCode>', '<recordId>',
      '<expectedVersion>', '<originalName>', '<mediaType>', '<contentBase64>', '<fileId>', '<downloadFileName>',
      '<instanceId>', '<fieldCode>', '<targetRecordId>', '<targetExpectedVersion>', '<clientRowKey>',
      '<subFieldCode>',
    ]) expect(exampleText).toContain(placeholder)
    expect(exampleText).not.toContain(application.appKey)
    expect(exampleText).not.toContain(application.secretRef)
    expect(exampleText).toContain('不要把 SecretRef 当作密钥')

    await wrapper.get('[data-example="subtable-mutate"] .request-example-copy').trigger('click')
    await flushPromises()
    expect(clipboardWrite).toHaveBeenCalledTimes(1)
    expect(clipboardWrite.mock.calls[0]![0]).toContain(
      'POST \'<BASE_URL>/openapi/v1/modules/<moduleCode>/records/<recordId>/subtables/<fieldCode>:mutate\'',
    )
    expect(wrapper.get('[data-example="subtable-mutate"] .request-example-copy').text()).toContain('已复制')
  })

  it('keeps the same create key for an exact retry and rotates it when payload changes', async () => {
    vi.mocked(openApiApplicationApi.create).mockRejectedValue(new Error('provider unavailable'))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.openapi-create').trigger('click')
    await wrapper.get('.openapi-name').setValue('ERP connector')
    await wrapper.get('.openapi-tenant').setValue('20')
    await wrapper.get('.openapi-service-member').setValue('30')
    await wrapper.get('.openapi-secret-ref').setValue('vault://openapi/erp/v1')
    await wrapper.get('.openapi-scopes').setValue('ping')
    await wrapper.get('.openapi-ip-allowlist').setValue('10.0.0.0/24')
    await wrapper.get('.openapi-create-modal .modal-ok').trigger('click')
    await flushPromises()

    const firstKey = vi.mocked(openApiApplicationApi.create).mock.calls[0]![2]
    expect(firstKey).toEqual(expect.any(String))
    expect(vi.mocked(openApiApplicationApi.create).mock.calls[0]![1]).toEqual({
      name: 'ERP connector',
      tenantId: '20',
      serviceMemberId: '30',
      secretRef: 'vault://openapi/erp/v1',
      scopes: ['ping'],
      ipAllowlist: ['10.0.0.0/24'],
      rateLimitPerMinute: 60,
    })

    await wrapper.get('.openapi-create-modal .modal-ok').trigger('click')
    await flushPromises()
    expect(vi.mocked(openApiApplicationApi.create).mock.calls[1]![2]).toBe(firstKey)

    await wrapper.get('.openapi-scopes').setValue('ping\nflow.start')
    await wrapper.get('.openapi-create-modal .modal-ok').trigger('click')
    await flushPromises()
    expect(vi.mocked(openApiApplicationApi.create).mock.calls[2]![2]).not.toBe(firstKey)
    expect((wrapper.get('.openapi-secret-ref').element as HTMLInputElement).value)
      .toBe('vault://openapi/erp/v1')
    expect(wrapper.text()).toContain('provider unavailable')

    const submitted = vi.mocked(openApiApplicationApi.create).mock.calls[2]![1]
    expect(Object.keys(submitted)).toEqual([
      'name',
      'tenantId',
      'serviceMemberId',
      'secretRef',
      'scopes',
      'ipAllowlist',
      'rateLimitPerMinute',
    ])
  })

  it('passes optimistic versions through policy, SecretRef rotation and disable', async () => {
    vi.mocked(openApiApplicationApi.updatePolicy).mockResolvedValue({
      ...application,
      scopes: ['ping', 'flow.start'],
      ipAllowlist: ['10.0.0.8'],
      rateLimitPerMinute: 60,
      version: 5,
    })
    vi.mocked(openApiApplicationApi.rotateSecretRef).mockResolvedValue({
      ...application,
      secretRef: 'vault://openapi/erp/v2',
      credentialVersion: 3,
      version: 6,
    })
    vi.mocked(openApiApplicationApi.changeStatus).mockResolvedValue({
      ...application,
      secretRef: 'vault://openapi/erp/v2',
      credentialVersion: 3,
      status: 'DISABLED',
      version: 7,
    })
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.openapi-application-link').trigger('click')
    await flushPromises()

    await wrapper.get('.openapi-policy-edit').trigger('click')
    await wrapper.get('.policy-scopes').setValue('ping\nflow.start')
    await wrapper.get('.policy-ip-allowlist').setValue('10.0.0.8')
    await wrapper.get('.policy-rate').setValue('60')
    await wrapper.get('.openapi-policy-modal .modal-ok').trigger('click')
    await flushPromises()
    expect(openApiApplicationApi.updatePolicy).toHaveBeenCalledWith('10', 'application-8', {
      scopes: ['ping', 'flow.start'],
      ipAllowlist: ['10.0.0.8'],
      rateLimitPerMinute: 60,
      version: 4,
    }, expect.any(String))

    await wrapper.get('.openapi-secret-rotate').trigger('click')
    await wrapper.get('.rotate-secret-ref').setValue('vault://openapi/erp/v2')
    await wrapper.get('.openapi-rotate-modal .modal-ok').trigger('click')
    await flushPromises()
    expect(openApiApplicationApi.rotateSecretRef).toHaveBeenCalledWith('10', 'application-8', {
      secretRef: 'vault://openapi/erp/v2',
      version: 5,
    }, expect.any(String))

    await wrapper.get('.openapi-disable').trigger('click')
    await wrapper.get('.status-reason').setValue('incident response')
    await wrapper.get('.openapi-status-modal .modal-ok').trigger('click')
    await flushPromises()
    expect(openApiApplicationApi.changeStatus).toHaveBeenCalledWith(
      '10',
      'application-8',
      'disable',
      { version: 6, reason: 'incident response' },
      expect.any(String),
    )
    expect(wrapper.get('.openapi-detail-list').text()).toContain('DISABLED')
    expect(wrapper.get('.openapi-detail-list').text()).toContain('v3')
  })
})
