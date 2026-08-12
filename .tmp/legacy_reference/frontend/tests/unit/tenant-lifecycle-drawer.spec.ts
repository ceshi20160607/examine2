import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { systemAdminApi } from '@/services/admin'
import TenantLifecycleDrawer from '@/components/admin/TenantLifecycleDrawer.vue'

vi.mock('@/services/admin', () => ({ systemAdminApi: {
  listTenantDomains: vi.fn(), listTenantQuotas: vi.fn(),
  addTenantDomain: vi.fn(), verifyTenantDomain: vi.fn(), makePrimaryTenantDomain: vi.fn(), disableTenantDomain: vi.fn(),
  setTenantQuota: vi.fn(), backupTenant: vi.fn(),
  previewTenantRecovery: vi.fn(), recoverTenant: vi.fn(),
  previewTenantMigration: vi.fn(), migrateTenant: vi.fn(),
} }))

const tenant = {
  id: '20', systemId: '10', name: '租户甲', code: 'tenant-a', mode: 'MULTI',
  status: 'DISABLED', isDefault: false, memberCount: 0, version: '4',
} as const
const plan = {
  id: '41', systemId: '10', sourceTenantId: '20', targetTenantId: null,
  operationType: 'RECOVERY_PREVIEW' as const, status: 'PREVIEWED', eligible: true,
  blockers: [], tableImpacts: { un_module_record: 3 }, quotaProjection: { STORAGE_BYTES: 128 },
  planFingerprint: 'fingerprint', confirmationToken: 'recover-once', expiresAt: '2026-08-07T04:00:00',
  rowCount: 3, estimatedBytes: 128, databaseMigrationVersion: '8.88.0',
}

const stubs = {
  'a-drawer': { props: ['open', 'title'], template: '<section v-if="open"><h1>{{ title }}</h1><slot /></section>' },
  'a-spin': { template: '<div><slot /></div>' },
  'a-tabs': { template: '<div><slot /></div>' },
  'a-tab-pane': { props: ['tab'], template: '<section><h2>{{ tab }}</h2><slot /></section>' },
  'a-alert': { props: ['message'], template: '<div>{{ message }}</div>' },
  'a-form': { template: '<form><slot /></form>' },
  'a-form-item': { props: ['label'], template: '<label>{{ label }}<slot /></label>' },
  'a-input': true, 'a-input-number': true, 'a-select': true, 'a-select-option': true,
  'a-textarea': true, 'a-radio-group': true, 'a-radio-button': true,
  'a-row': { template: '<div><slot /></div>' }, 'a-col': { template: '<div><slot /></div>' },
  'a-list': true, 'a-list-item': true, 'a-list-item-meta': true, 'a-table': true, 'a-table-column': true,
  'a-space': { template: '<div><slot /></div>' },
  'a-button': { props: ['disabled', 'loading'], emits: ['click'], template: '<button type="button" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
  'a-card': { props: ['title'], template: '<article><h3>{{ title }}</h3><slot /></article>' },
  'a-descriptions': { template: '<dl><slot /></dl>' },
  'a-descriptions-item': { props: ['label'], template: '<div><dt>{{ label }}</dt><dd><slot /></dd></div>' },
  'a-checkbox': { props: ['checked', 'disabled'], emits: ['update:checked'], template: '<label><input type="checkbox" :checked="checked" :disabled="disabled" @change="$emit(\'update:checked\', $event.target.checked)" /><slot /></label>' },
}

describe('TenantLifecycleDrawer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(systemAdminApi.listTenantDomains).mockResolvedValue([])
    vi.mocked(systemAdminApi.listTenantQuotas).mockResolvedValue([])
    vi.mocked(systemAdminApi.previewTenantRecovery).mockResolvedValue(plan)
    vi.mocked(systemAdminApi.recoverTenant).mockResolvedValue({
      id: '42', systemId: '10', sourceTenantId: '20', targetTenantId: null,
      operationType: 'RECOVERY', status: 'SUCCEEDED', reason: 'rollback', snapshotChecksum: 'sha',
      result: {}, requestedAt: '2026-08-07T03:00:00', finishedAt: '2026-08-07T03:00:01',
    })
  })

  it('requires a preview and explicit impact acknowledgement before recovery confirmation', async () => {
    const wrapper = mount(TenantLifecycleDrawer, { props: { open: true, systemId: '10', tenant }, global: { stubs } })
    await flushPromises()
    const setup = (wrapper.vm as unknown as { $: { setupState: Record<string, any> } }).$.setupState
    setup.lifecycle.action = 'RECOVERY'
    setup.lifecycle.backupOperationId = '40'
    setup.lifecycle.reason = 'rollback'
    await nextTick()

    expect(wrapper.findAll('button').find(button => button.text() === '确认执行')?.attributes('disabled')).toBeDefined()
    await wrapper.findAll('button').find(button => button.text() === '生成影响预览')!.trigger('click')
    await flushPromises()

    expect(systemAdminApi.previewTenantRecovery).toHaveBeenCalledWith('10', '20', {
      backupOperationId: '40', expectedTenantVersion: '4',
    })
    expect(wrapper.text()).toContain('un_module_record')
    expect(wrapper.text()).toContain('STORAGE_BYTES')
    expect(systemAdminApi.recoverTenant).not.toHaveBeenCalled()

    await wrapper.get('input[type="checkbox"]').setValue(true)
    await wrapper.findAll('button').find(button => button.text() === '确认执行')!.trigger('click')
    await flushPromises()
    expect(systemAdminApi.recoverTenant).toHaveBeenCalledWith('10', '20', {
      planOperationId: '41', confirmationToken: 'recover-once', reason: 'rollback',
      expectedTenantVersion: '4', impactConfirmed: true,
    })
  })
})
