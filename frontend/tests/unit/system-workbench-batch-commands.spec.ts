import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { runtimeApi } from '@/services/config'
import { favoriteApi } from '@/services/favorites'
import { globalSearchApi } from '@/services/globalSearch'
import { quickCreateApi } from '@/services/quickCreate'
import { recentRecordApi } from '@/services/recentRecords'
import { useSessionStore } from '@/stores/session'
import type { RuntimeFieldCapability, RuntimeRecordPage, RuntimeRecordQuery } from '@/types/config'
import SystemWorkbenchView from '@/views/system/SystemWorkbenchView.vue'

const route = vi.hoisted(() => ({
  params: { systemId: '10' },
  query: { module: 'work_order' } as Record<string, string>,
  fullPath: '/systems/10/workbench?module=work_order',
}))
const router = vi.hoisted(() => ({
  replace: vi.fn(() => Promise.resolve()),
  push: vi.fn(() => Promise.resolve()),
}))
const modalConfirm = vi.hoisted(() => vi.fn())

vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => router,
  onBeforeRouteLeave: vi.fn(),
  onBeforeRouteUpdate: vi.fn(),
}))

vi.mock('ant-design-vue', () => ({
  Modal: { confirm: modalConfirm },
}))

vi.mock('@/services/config', () => ({
  runtimeApi: {
    navigation: vi.fn(),
    definition: vi.fn(),
    recordSchema: vi.fn(),
    queryRecords: vi.fn(),
    myDrafts: vi.fn(),
    recordNeighbor: vi.fn(),
    batchArchiveRecords: vi.fn(),
    batchTrashRecords: vi.fn(),
    batchTransferRecords: vi.fn(),
    batchEditRecords: vi.fn(),
    savedViews: vi.fn(),
    createSavedView: vi.fn(),
    updateSavedView: vi.fn(),
    deleteSavedView: vi.fn(),
    record: vi.fn(),
    flowState: vi.fn(),
    flowStates: vi.fn(),
    createRecord: vi.fn(),
    updateRecord: vi.fn(),
    autosaveRecord: vi.fn(),
    activateRecord: vi.fn(),
    lifecycleRecord: vi.fn(),
  },
}))

vi.mock('@/services/favorites', () => ({
  favoriteApi: {
    list: vi.fn(),
    create: vi.fn(),
    remove: vi.fn(),
  },
}))

vi.mock('@/services/recentRecords', () => ({
  recentRecordApi: {
    list: vi.fn(),
    touch: vi.fn(),
  },
}))

vi.mock('@/services/globalSearch', () => ({
  globalSearchApi: {
    search: vi.fn(),
  },
}))

vi.mock('@/services/quickCreate', () => ({
  quickCreateApi: {
    list: vi.fn(),
  },
}))

const activeRows = [
  { recordId: '101', recordNo: 'R-101', version: 3, status: 'ACTIVE', title: 'Active one', values: [] },
  { recordId: '102', recordNo: 'R-102', version: 7, status: 'ACTIVE', title: 'Active two', values: [] },
]
const archivedRow = {
  recordId: '201',
  recordNo: 'R-201',
  version: 5,
  status: 'ARCHIVED',
  title: 'Archived one',
  values: [],
}
const draftRow = {
  recordId: '301',
  recordNo: 'R-301',
  version: 2,
  status: 'DRAFT',
  title: 'My draft',
  values: [],
}

function page(rows: RuntimeRecordPage['rows'], current: number, total = rows.length): RuntimeRecordPage {
  return {
    rows,
    page: current,
    size: 50,
    total,
    queryHash: 'query-hash',
    querySnapshotToken: 'snapshot-token',
    invalidNodes: [],
  }
}

function runtimeField(
  fieldCode: string,
  fieldName: string,
  type: string,
  overrides: Partial<RuntimeFieldCapability> = {},
): RuntimeFieldCapability {
  return {
    fieldCode,
    fieldName,
    logicalFieldId: fieldCode,
    type,
    mode: 'WRITABLE',
    readable: true,
    writable: true,
    sensitiveReadable: false,
    sensitiveQueryable: false,
    masked: false,
    operators: [],
    sortable: false,
    showInList: false,
    showInDetail: true,
    options: [],
    schema: {},
    ...overrides,
  }
}

function applySession(extraPermissions: string[] = [], includeTransfer = true, includeUpdate = true) {
  useSessionStore().applyAuth({
    account: { id: '1', username: 'member', displayName: 'Member' },
    context: {
      type: 'SYSTEM',
      account: { id: '1', username: 'member', displayName: 'Member' },
      systemId: '10',
      tenantId: '20',
      memberId: '30',
      permissionVersion: '1',
      permissions: [
        'module.work_order.view',
        'module.work_order.action.archive',
        'module.work_order.delete',
        ...(includeUpdate ? ['module.work_order.update'] : []),
        ...(includeTransfer ? ['module.work_order.action.transfer'] : []),
        ...extraPermissions,
      ],
      shells: ['SYSTEM_RUNTIME'],
    },
    systems: [],
  })
}

function render() {
  return mount(SystemWorkbenchView, {
    global: {
      stubs: {
        AlertCircle: true,
        Archive: true,
        ArchiveRestore: true,
        BookmarkPlus: true,
        Blocks: true,
        Check: true,
        ChevronLeft: true,
        ChevronRight: true,
        Clock3: true,
        Copy: true,
        Eye: true,
        FileDown: true,
        FileUp: true,
        ListFilter: true,
        Pencil: true,
        Plus: true,
        RefreshCw: true,
        Rocket: true,
        RotateCcw: true,
        Save: true,
        Search: true,
        Send: true,
        Star: true,
        Trash2: true,
        UserRoundCheck: true,
        X: true,
        RuntimeFavoriteToggle: {
          props: ['favorite', 'label', 'loading'],
          emits: ['toggle'],
          template: '<button :class="label === \'当前模块\' ? \'favorite-toggle-module\' : \'favorite-toggle-record\'" @click="$emit(\'toggle\')">{{ favorite ? \'已收藏\' : \'收藏\' }}</button>',
        },
        RuntimeExportDrawer: {
          props: ['open', 'query', 'fields', 'defaultFieldCodes'],
          emits: ['close'],
          template: '<div v-if="open" class="export-drawer-stub">export</div>',
        },
        RuntimeFavoritesPanel: {
          props: ['open', 'items'],
          emits: ['openFavorite', 'remove', 'close', 'refresh', 'pageChange'],
          template: '<div v-if="open" class="favorites-panel-stub"><button v-if="items[0]" class="open-first-favorite" @click="$emit(\'openFavorite\', items[items.length - 1])">open</button><button v-if="items[0]" class="remove-first-favorite" @click="$emit(\'remove\', items[0])">remove</button></div>',
        },
        RuntimeRecentRecordsPanel: {
          props: ['open', 'items'],
          emits: ['openRecord', 'close', 'refresh', 'pageChange'],
          template: '<div v-if="open" class="recent-panel-stub"><button v-if="items[0]" class="open-first-recent" @click="$emit(\'openRecord\', items[0])">open recent</button></div>',
        },
        RuntimeGlobalSearchPanel: {
          props: ['open', 'keyword', 'items', 'page', 'total', 'error'],
          emits: ['update:keyword', 'search', 'openRecord', 'close', 'pageChange'],
          template: '<div v-if="open" class="global-search-panel-stub"><button class="set-global-search-keyword" @click="$emit(\'update:keyword\', \'pump\')">set q</button><button class="run-global-search" @click="$emit(\'search\')">search</button><button class="global-search-page-two" @click="$emit(\'pageChange\', 2)">page 2</button><span class="global-search-context">{{ keyword }}|{{ items.length }}|{{ page }}|{{ total }}|{{ error }}</span><button v-if="items[0]" class="open-first-global-search" @click="$emit(\'openRecord\', items[0])">{{ items[0].displayLabel }}</button></div>',
        },
        RuntimeQuickCreatePanel: {
          props: ['open', 'items', 'error'],
          emits: ['select', 'close', 'refresh'],
          template: '<div v-if="open" class="quick-create-panel-stub"><button class="refresh-quick-create" @click="$emit(\'refresh\')">refresh</button><span class="quick-create-context">{{ items.map((item) => item.moduleCode).join(\'|\') }}|{{ error }}</span><button v-if="items[0]" class="open-first-quick-create" @click="$emit(\'select\', items[0])">{{ items[0].moduleName }}</button><button v-if="items[1]" class="open-second-quick-create" @click="$emit(\'select\', items[1])">{{ items[1].moduleName }}</button></div>',
        },
        MemberPicker: {
          emits: ['update:value'],
          template: '<button class="select-transfer-member" @click="$emit(\'update:value\', \'456\')">member</button>',
        },
        RecordCommentPanel: true,
        RecordFilePanel: true,
        RecordHistoryPanel: true,
        RecordTeamPanel: true,
        RuntimeDerivedField: true,
        RuntimeFieldDisplay: true,
        RuntimeFieldInput: {
          emits: ['update:modelValue', 'clear'],
          template: '<button class="set-batch-edit-value" @click="$emit(\'update:modelValue\', \'HIGH\')">set value</button>',
        },
        RuntimeReferenceField: true,
        RuntimeRelationField: true,
        RuntimeSubtableField: true,
        'a-alert': {
          props: ['message'],
          template: '<div class="alert">{{ message }}<slot /></div>',
        },
        'a-button': {
          inheritAttrs: false,
          props: ['disabled', 'loading'],
          emits: ['click'],
          template: '<button v-bind="$attrs" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
        },
        'a-checkbox': true,
        'a-checkbox-group': true,
        'a-drawer': {
          props: ['open'],
          template: '<div v-if="open"><slot /></div>',
        },
        'a-empty': {
          props: ['description'],
          template: '<div>{{ description }}</div>',
        },
        'a-input': true,
        'a-input-number': true,
        'a-input-password': true,
        'a-input-search': true,
        'a-modal': {
          props: ['open'],
          emits: ['ok', 'cancel'],
          template: '<div v-if="open"><slot /><button class="modal-ok" @click="$emit(\'ok\')">ok</button></div>',
        },
        'a-pagination': {
          props: ['current'],
          emits: ['change', 'showSizeChange'],
          template: '<button class="next-page" @click="$emit(\'change\', 2, 50)">{{ current }}</button>',
        },
        'a-segmented': {
          props: ['value'],
          emits: ['change'],
          template: '<div><button class="scope-active" @click="$emit(\'change\', \'active\')">active</button><button class="scope-draft" @click="$emit(\'change\', \'draft\')">draft</button><button class="scope-archived" @click="$emit(\'change\', \'archived\')">archived</button></div>',
        },
        'a-select': {
          props: ['value', 'disabled'],
          emits: ['change'],
          template: '<select :value="value" :data-value="value" :disabled="disabled" @change="$emit(\'change\', $event.target.value)"><slot /></select>',
        },
        'a-select-option': {
          props: ['value'],
          template: '<option :value="value"><slot /></option>',
        },
        'a-spin': {
          template: '<div><slot /></div>',
        },
        'a-tag': {
          template: '<span><slot /></span>',
        },
        'a-tooltip': {
          template: '<span><slot /></span>',
        },
      },
    },
  })
}

describe('SystemWorkbenchView batch commands', () => {
  let confirmation: { onOk?: () => Promise<void> } | undefined

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    route.query = { module: 'work_order' }
    route.fullPath = '/systems/10/workbench?module=work_order'
    applySession()
    modalConfirm.mockImplementation((options: { onOk?: () => Promise<void> }) => {
      confirmation = options
      return { destroy: vi.fn() }
    })
    vi.mocked(runtimeApi.navigation).mockResolvedValue({
      activeVersionId: '1',
      versionNo: '1',
      groups: [{
        id: '11',
        code: 'operations',
        name: 'Operations',
        sortOrder: 1,
        modules: [{
          id: '12',
          code: 'work_order',
          name: 'Work orders',
          sortOrder: 1,
          permissionCode: 'module.work_order.view',
        }],
      }],
    })
    vi.mocked(runtimeApi.definition).mockResolvedValue({
      activeVersionId: '1',
      versionNo: '1',
      module: { module_name: 'Work orders', module_code: 'work_order' },
      fields: [],
      pages: [],
      components: [],
      actions: [],
      rules: [],
      dictionaries: [],
      dictionaryItems: [],
      recordsAvailable: true,
    })
    vi.mocked(runtimeApi.recordSchema).mockResolvedValue({
      schemaVersionId: '1',
      moduleSnapshotId: '12',
      logicalModuleId: '12',
      checksum: 'checksum',
      runtimeState: 'READY',
      authzEpoch: 1,
      fields: [],
      actions: ['VIEW_ARCHIVE'],
      queryLimits: { defaultSize: 50, maxSize: 200, maxSorts: 3 },
    })
    vi.mocked(runtimeApi.savedViews).mockResolvedValue({ items: [], correlationId: 'saved-views' })
    vi.mocked(runtimeApi.queryRecords).mockImplementation(
      async (_systemId: string, _moduleCode: string, query: RuntimeRecordQuery) => {
        if (query.recordScope === 'archived') return page([archivedRow], query.page)
        return page([activeRows[query.page - 1] ?? activeRows[0]!], query.page, 2)
      },
    )
    vi.mocked(runtimeApi.myDrafts).mockImplementation(
      async (_systemId, _moduleCode, query) => page([draftRow], query.page),
    )
    vi.mocked(runtimeApi.flowState).mockResolvedValue(null)
    vi.mocked(runtimeApi.flowStates).mockResolvedValue([])
    vi.mocked(runtimeApi.batchArchiveRecords).mockResolvedValue({ allApplied: true, items: [] })
    vi.mocked(runtimeApi.batchTrashRecords).mockResolvedValue({ allApplied: true, items: [] })
    vi.mocked(runtimeApi.batchTransferRecords).mockResolvedValue({ allApplied: true, items: [] })
    vi.mocked(runtimeApi.batchEditRecords).mockResolvedValue({ allApplied: true, items: [] })
    vi.mocked(favoriteApi.list).mockResolvedValue({ items: [], page: 1, size: 20, total: 0 })
    vi.mocked(favoriteApi.remove).mockResolvedValue({ favoriteId: '91', version: 1, deleted: true })
    vi.mocked(recentRecordApi.list).mockResolvedValue({ items: [], page: 1, size: 20, total: 0 })
    vi.mocked(recentRecordApi.touch).mockResolvedValue({
      recentId: '81',
      moduleCode: 'work_order',
      recordId: '101',
      displayLabel: 'R-101 · Active one',
      status: 'ACTIVE',
      accessCount: 1,
      lastAccessedAt: '2026-07-27T12:00:00',
    })
    vi.mocked(globalSearchApi.search).mockResolvedValue({
      items: [],
      page: 1,
      size: 20,
      total: 0,
    })
    vi.mocked(quickCreateApi.list).mockResolvedValue({ items: [] })
  })

  it('exposes the current-query export drawer only with the independent export permission', async () => {
    const hidden = render()
    await flushPromises()
    expect(hidden.find('.export-command').exists()).toBe(false)
    hidden.unmount()

    applySession(['module.work_order.export'])
    const visible = render()
    await flushPromises()
    expect(visible.find('.export-command').exists()).toBe(true)
    await visible.find('.export-command').trigger('click')
    expect(visible.find('.export-drawer-stub').exists()).toBe(true)
  })

  it('applies the configured default and lets a shared scenario detach a saved view at page one', async () => {
    const urgentFilter = { kind: 'PREDICATE' as const, fieldCode: 'priority', operator: 'EQ', value: 'HIGH' }
    vi.mocked(runtimeApi.definition).mockResolvedValue({
      activeVersionId: '1',
      versionNo: '1',
      module: { module_name: 'Work orders', module_code: 'work_order' },
      fields: [],
      pages: [{
        page_type: 'LIST', desired_status: 'ENABLED', is_default: true,
        layout_json: {
          filterScenarios: [
            { code: 'urgent_first', name: 'Urgent first', filter: urgentFilter, sort: [{ fieldCode: 'created_at', direction: 'DESC', nulls: 'LAST' }] },
            { code: 'oldest_first', name: 'Oldest first', filter: null, sort: [{ fieldCode: 'created_at', direction: 'ASC', nulls: 'LAST' }] },
          ],
          defaultFilterScenarioCode: 'urgent_first',
        },
      }],
      components: [], actions: [], rules: [], dictionaries: [], dictionaryItems: [], recordsAvailable: true,
    })
    vi.mocked(runtimeApi.recordSchema).mockResolvedValue({
      schemaVersionId: '1', moduleSnapshotId: '12', logicalModuleId: '12', checksum: 'checksum', runtimeState: 'READY', authzEpoch: 1,
      fields: [
        runtimeField('priority', 'Priority', 'TEXT', { operators: ['EQ'] }),
        runtimeField('created_at', 'Created at', 'DATETIME', { sortable: true }),
      ],
      actions: [], queryLimits: { defaultSize: 50, maxSize: 200, maxSorts: 3 },
    })
    vi.mocked(runtimeApi.savedViews).mockResolvedValue({
      correlationId: 'saved-views',
      items: [{
        viewId: '91', version: 1, moduleCode: 'work_order', name: 'Mine', columns: [], correlationId: 'view',
        query: { schemaVersionId: '1', page: 4, size: 50, recordScope: 'active', q: null, filter: null, sort: [], columns: [], viewId: '91' },
      }],
    })

    const wrapper = render()
    await flushPromises()

    expect(vi.mocked(runtimeApi.queryRecords).mock.calls.at(-1)?.[2]).toMatchObject({
      page: 1, filter: urgentFilter, sort: [{ fieldCode: 'created_at', direction: 'DESC', nulls: 'LAST' }], viewId: null,
    })
    expect(wrapper.get('[aria-label="共享筛选方案"]').attributes('data-value')).toBe('urgent_first')

    await wrapper.get('.saved-view-select').setValue('91')
    await flushPromises()
    expect(vi.mocked(runtimeApi.queryRecords).mock.calls.at(-1)?.[2].viewId).toBe('91')

    await wrapper.get('[aria-label="共享筛选方案"]').setValue('oldest_first')
    await flushPromises()
    expect(vi.mocked(runtimeApi.queryRecords).mock.calls.at(-1)?.[2]).toMatchObject({
      page: 1, filter: null, sort: [{ fieldCode: 'created_at', direction: 'ASC', nulls: 'LAST' }], viewId: null,
    })
    expect(router.push).toHaveBeenCalled()
  })

  it('does not apply the configured default when the module route has an explicit filter', async () => {
    route.query = { module: 'work_order', filter: 'eyJmaWVsZENvZGUiOiJwcmlvcml0eSIsImtpbmQiOiJQUkVESUNBVEUiLCJvcGVyYXRvciI6IkVRIiwidmFsdWUiOiJMT1cifQ' }
    const explicitFilter = { kind: 'PREDICATE' as const, fieldCode: 'priority', operator: 'EQ', value: 'LOW' }
    vi.mocked(runtimeApi.definition).mockResolvedValue({
      activeVersionId: '1', versionNo: '1', module: { module_name: 'Work orders', module_code: 'work_order' }, fields: [],
      pages: [{ page_type: 'LIST', desired_status: 'ENABLED', is_default: true, layout_json: {
        filterScenarios: [{ code: 'urgent_first', name: 'Urgent first', filter: { ...explicitFilter, value: 'HIGH' }, sort: [] }],
        defaultFilterScenarioCode: 'urgent_first',
      } }],
      components: [], actions: [], rules: [], dictionaries: [], dictionaryItems: [], recordsAvailable: true,
    })
    vi.mocked(runtimeApi.recordSchema).mockResolvedValue({
      schemaVersionId: '1', moduleSnapshotId: '12', logicalModuleId: '12', checksum: 'checksum', runtimeState: 'READY', authzEpoch: 1,
      fields: [runtimeField('priority', 'Priority', 'TEXT', { operators: ['EQ'] })], actions: [],
      queryLimits: { defaultSize: 50, maxSize: 200, maxSorts: 3 },
    })

    const wrapper = render()
    await flushPromises()

    expect(wrapper.get('[aria-label="共享筛选方案"]').attributes('data-value')).toBeUndefined()
    expect(vi.mocked(runtimeApi.queryRecords).mock.calls.at(-1)?.[2].filter).toEqual(explicitFilter)
  })

  it('keeps selection across pages and trashes the explicit record versions', async () => {
    const wrapper = render()
    await flushPromises()

    await wrapper.get('input[aria-label="选择记录 R-101"]').setValue(true)
    await wrapper.get('.next-page').trigger('click')
    await flushPromises()
    await wrapper.get('input[aria-label="选择记录 R-102"]').setValue(true)

    expect(wrapper.get('.batch-trash-command').text()).toContain('(2)')
    await wrapper.get('.batch-trash-command').trigger('click')
    expect(modalConfirm).toHaveBeenCalledOnce()
    await confirmation?.onOk?.()
    await flushPromises()

    expect(runtimeApi.batchTrashRecords).toHaveBeenCalledWith('10', 'work_order', {
      items: [
        { recordId: '101', expectedVersion: 3 },
        { recordId: '102', expectedVersion: 7 },
      ],
    })
    expect(vi.mocked(runtimeApi.queryRecords).mock.calls.at(-1)?.[2].page).toBe(2)
    expect(wrapper.get('.batch-trash-command').attributes('disabled')).toBeDefined()
  })

  it('clears selection when scope changes and only offers trash for archived rows', async () => {
    const wrapper = render()
    await flushPromises()

    await wrapper.get('input[aria-label="选择记录 R-101"]').setValue(true)
    expect(wrapper.get('.batch-archive-command').attributes('disabled')).toBeUndefined()
    await wrapper.get('.scope-archived').trigger('click')
    await flushPromises()

    expect(wrapper.find('.batch-archive-command').exists()).toBe(false)
    expect(wrapper.get('.batch-trash-command').attributes('disabled')).toBeDefined()
    await wrapper.get('input[aria-label="选择记录 R-201"]').setValue(true)
    expect(wrapper.get('.batch-trash-command').attributes('disabled')).toBeUndefined()
  })

  it('keeps the selection and shows an error when batch trash fails', async () => {
    vi.mocked(runtimeApi.batchTrashRecords).mockRejectedValue(new Error('network unavailable'))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('input[aria-label="选择记录 R-101"]').setValue(true)
    await wrapper.get('.batch-trash-command').trigger('click')
    await confirmation?.onOk?.()
    await flushPromises()

    expect(wrapper.get('.batch-trash-command').text()).toContain('(1)')
    expect(wrapper.text()).toContain('批量移入回收站失败，整批记录均未修改。')
  })

  it('selects a tenant member, asks for a second confirmation and transfers the explicit selection', async () => {
    const wrapper = render()
    await flushPromises()

    await wrapper.get('input[aria-label="选择记录 R-101"]').setValue(true)
    await wrapper.get('.batch-transfer-command').trigger('click')
    expect(wrapper.text()).toContain('请选择新的记录负责人')

    await wrapper.get('.select-transfer-member').trigger('click')
    await wrapper.get('.modal-ok').trigger('click')
    expect(modalConfirm).toHaveBeenCalledOnce()
    await confirmation?.onOk?.()
    await flushPromises()

    expect(runtimeApi.batchTransferRecords).toHaveBeenCalledWith('10', 'work_order', {
      items: [{ recordId: '101', expectedVersion: 3 }],
      targetMemberId: '456',
    })
    expect(wrapper.find('.batch-transfer-form').exists()).toBe(false)
    expect(wrapper.get('.batch-transfer-command').attributes('disabled')).toBeDefined()
  })

  it('does not expose transfer without the dynamic module action permission', async () => {
    applySession([], false)
    const wrapper = render()
    await flushPromises()

    expect(wrapper.find('.batch-transfer-command').exists()).toBe(false)
  })

  it('edits one eligible ordinary field across the explicit cross-page selection', async () => {
    vi.mocked(runtimeApi.recordSchema).mockResolvedValueOnce({
      schemaVersionId: '1',
      moduleSnapshotId: '12',
      logicalModuleId: '12',
      checksum: 'checksum',
      runtimeState: 'READY',
      authzEpoch: 1,
      fields: [
        runtimeField('priority', 'Priority', 'TEXT'),
        runtimeField('identity', 'Identity', 'IDENTITY', { mode: 'SENSITIVE_WRITABLE' }),
        runtimeField('formula', 'Formula', 'FORMULA'),
        runtimeField('relation', 'Relation', 'RELATION'),
        runtimeField('readonly', 'Readonly', 'TEXT', { writable: false }),
      ],
      actions: ['VIEW_ARCHIVE'],
      queryLimits: { defaultSize: 50, maxSize: 200, maxSorts: 3 },
    })
    const wrapper = render()
    await flushPromises()

    await wrapper.get('input[aria-label="选择记录 R-101"]').setValue(true)
    await wrapper.get('.next-page').trigger('click')
    await flushPromises()
    await wrapper.get('input[aria-label="选择记录 R-102"]').setValue(true)
    await wrapper.get('.batch-edit-command').trigger('click')

    expect(wrapper.get('.batch-edit-form').text()).toContain('Priority')
    expect(wrapper.get('.batch-edit-form').text()).not.toContain('Identity')
    expect(wrapper.get('.batch-edit-form').text()).not.toContain('Formula')
    expect(wrapper.get('.batch-edit-form').text()).not.toContain('Relation')
    expect(wrapper.get('.batch-edit-form').text()).not.toContain('Readonly')

    await wrapper.get('.set-batch-edit-value').trigger('click')
    await wrapper.get('.modal-ok').trigger('click')
    expect(modalConfirm).toHaveBeenCalledOnce()
    expect(modalConfirm.mock.calls[0]![0].content).toContain('2 条记录')
    await confirmation?.onOk?.()
    await flushPromises()

    expect(runtimeApi.batchEditRecords).toHaveBeenCalledWith('10', 'work_order', {
      items: [
        { recordId: '101', expectedVersion: 3 },
        { recordId: '102', expectedVersion: 7 },
      ],
      changes: [{ fieldCode: 'priority', operation: 'SET', value: 'HIGH' }],
    })
    expect(wrapper.find('.batch-edit-form').exists()).toBe(false)
    expect(wrapper.get('.batch-edit-command').attributes('disabled')).toBeDefined()
  })

  it('submits CLEAR without a value and retains the dialog and selection after failure', async () => {
    vi.mocked(runtimeApi.recordSchema).mockResolvedValueOnce({
      schemaVersionId: '1',
      moduleSnapshotId: '12',
      logicalModuleId: '12',
      checksum: 'checksum',
      runtimeState: 'READY',
      authzEpoch: 1,
      fields: [runtimeField('note', 'Note', 'TEXT')],
      actions: [],
      queryLimits: { defaultSize: 50, maxSize: 200, maxSorts: 3 },
    })
    vi.mocked(runtimeApi.batchEditRecords).mockRejectedValue(new Error('unique changed'))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('input[aria-label="选择记录 R-101"]').setValue(true)
    await wrapper.get('.batch-edit-command').trigger('click')
    await wrapper.get('select[aria-label="批量编辑操作"]').setValue('CLEAR')
    await wrapper.get('.modal-ok').trigger('click')
    await confirmation?.onOk?.()
    await flushPromises()

    expect(runtimeApi.batchEditRecords).toHaveBeenCalledWith('10', 'work_order', {
      items: [{ recordId: '101', expectedVersion: 3 }],
      changes: [{ fieldCode: 'note', operation: 'CLEAR' }],
    })
    expect(wrapper.get('.batch-edit-command').text()).toContain('(1)')
    expect(wrapper.find('.batch-edit-form').exists()).toBe(true)
    expect((wrapper.get('select[aria-label="批量编辑操作"]').element as HTMLSelectElement).value).toBe('CLEAR')
    expect(wrapper.text()).toContain('批量编辑失败，整批记录均未修改。')
  })

  it('does not expose batch edit without update permission', async () => {
    applySession([], true, false)
    const wrapper = render()
    await flushPromises()

    expect(wrapper.find('.batch-edit-command').exists()).toBe(false)
  })

  it('keeps the transfer target and selection when the atomic command fails', async () => {
    vi.mocked(runtimeApi.batchTransferRecords).mockRejectedValue(new Error('team changed'))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('input[aria-label="选择记录 R-101"]').setValue(true)
    await wrapper.get('.batch-transfer-command').trigger('click')
    await wrapper.get('.select-transfer-member').trigger('click')
    await wrapper.get('.modal-ok').trigger('click')
    await confirmation?.onOk?.()
    await flushPromises()

    expect(wrapper.get('.batch-transfer-command').text()).toContain('(1)')
    expect(wrapper.find('.batch-transfer-form').exists()).toBe(true)
    expect(wrapper.text()).toContain('批量转交失败，整批记录及协作团队均未修改。')
  })

  it('loads the dedicated draft endpoint, hides advanced controls and permits batch trash', async () => {
    route.query = { module: 'work_order', recordScope: 'draft', q: '我的草稿' }
    route.fullPath = '/systems/10/workbench?module=work_order&recordScope=draft&q=%E6%88%91%E7%9A%84%E8%8D%89%E7%A8%BF'
    const wrapper = render()
    await flushPromises()

    expect(runtimeApi.myDrafts).toHaveBeenCalledWith('10', 'work_order', {
      page: 1,
      size: 50,
      q: '我的草稿',
    })
    expect(wrapper.find('.batch-archive-command').exists()).toBe(false)
    expect(wrapper.find('button[aria-label="筛选、排序和显示列"]').exists()).toBe(false)
    expect(wrapper.find('[aria-label="保存视图"]').exists()).toBe(false)

    await wrapper.get('input[aria-label="选择记录 R-301"]').setValue(true)
    await wrapper.get('.batch-trash-command').trigger('click')
    await confirmation?.onOk?.()
    await flushPromises()

    expect(runtimeApi.batchTrashRecords).toHaveBeenCalledWith('10', 'work_order', {
      items: [{ recordId: '301', expectedVersion: 2 }],
    })
    expect(runtimeApi.myDrafts).toHaveBeenCalledTimes(2)
  })

  it('favorites the current module and opens a record target from the personal panel', async () => {
    const recordFavorite = {
      favoriteId: '91',
      version: 0,
      type: 'RECORD' as const,
      moduleCode: 'work_order',
      recordId: '102',
      displayLabel: 'R-102 · Active two',
      status: 'ACTIVE',
      updatedAt: '2026-07-27T10:00:00',
    }
    applySession(['runtime.favorite.manage'])
    vi.mocked(favoriteApi.list).mockResolvedValue({
      items: [recordFavorite],
      page: 1,
      size: 20,
      total: 1,
    })
    vi.mocked(favoriteApi.create).mockResolvedValue({
      favoriteId: '92',
      version: 0,
      type: 'MODULE',
      moduleCode: 'work_order',
      displayLabel: 'work_order',
      updatedAt: '2026-07-27T10:01:00',
    })

    const wrapper = render()
    await flushPromises()

    await wrapper.get('.favorite-toggle-module').trigger('click')
    await flushPromises()
    expect(favoriteApi.create).toHaveBeenCalledWith('10', {
      type: 'MODULE',
      moduleCode: 'work_order',
    })
    expect(wrapper.get('.favorite-toggle-module').text()).toBe('已收藏')

    await wrapper.get('.favorites-panel-command').trigger('click')
    await wrapper.get('.open-first-favorite').trigger('click')
    await flushPromises()
    expect(router.push).toHaveBeenCalledWith(expect.objectContaining({
      query: expect.objectContaining({ module: 'work_order', mode: 'view', record: '102' }),
    }))
  })

  it('favorites the opened record and removes it through the same toggle', async () => {
    applySession(['runtime.favorite.manage'])
    route.query = { module: 'work_order', mode: 'view', record: '101' }
    route.fullPath = '/systems/10/workbench?module=work_order&mode=view&record=101'
    vi.mocked(runtimeApi.record).mockResolvedValue({
      ...activeRows[0]!,
      schemaVersionId: '1',
      actions: ['UPDATE'],
    })
    const created = {
      favoriteId: '93',
      version: 0,
      type: 'RECORD' as const,
      moduleCode: 'work_order',
      recordId: '101',
      displayLabel: 'R-101 · Active one',
      status: 'ACTIVE',
      updatedAt: '2026-07-27T10:02:00',
    }
    vi.mocked(favoriteApi.create).mockResolvedValue(created)

    const wrapper = render()
    await flushPromises()

    await wrapper.get('.favorite-toggle-record').trigger('click')
    await flushPromises()
    expect(favoriteApi.create).toHaveBeenCalledWith('10', {
      type: 'RECORD',
      moduleCode: 'work_order',
      recordId: '101',
    })
    expect(wrapper.get('.favorite-toggle-record').text()).toBe('已收藏')

    await wrapper.get('.favorite-toggle-record').trigger('click')
    await flushPromises()
    expect(favoriteApi.remove).toHaveBeenCalledWith('10', '93', 0)
    expect(wrapper.get('.favorite-toggle-record').text()).toBe('收藏')
  })

  it('touches each successfully loaded detail without blocking the visible record', async () => {
    route.query = { module: 'work_order', mode: 'view', record: '101' }
    route.fullPath = '/systems/10/workbench?module=work_order&mode=view&record=101'
    vi.mocked(runtimeApi.record).mockResolvedValue({
      ...activeRows[0]!,
      schemaVersionId: '1',
      actions: ['UPDATE'],
    })
    vi.mocked(recentRecordApi.touch).mockRejectedValue(new Error('telemetry unavailable'))

    const wrapper = render()
    await flushPromises()

    expect(recentRecordApi.touch).toHaveBeenCalledWith('10', {
      moduleCode: 'work_order',
      recordId: '101',
    })
    expect(wrapper.text()).toContain('Active one')
    expect(wrapper.text()).not.toContain('记录详情加载失败')
  })

  it('loads and renders the real flow state for the current record detail', async () => {
    route.query = { module: 'work_order', mode: 'view', record: '101' }
    route.fullPath = '/systems/10/workbench?module=work_order&mode=view&record=101'
    vi.mocked(runtimeApi.record).mockResolvedValue({
      ...activeRows[0]!,
      schemaVersionId: '1',
      actions: ['UPDATE'],
    })
    vi.mocked(runtimeApi.flowStates).mockResolvedValue([
      {
        instanceId: '701',
        status: 'PENDING',
        version: 0,
        updatedAt: '2026-07-27T20:30:00Z',
      },
      {
        instanceId: '702',
        status: 'APPROVED',
        version: 1,
        updatedAt: '2026-07-27T20:31:00Z',
      },
    ])

    const wrapper = render()
    await flushPromises()

    expect(runtimeApi.flowStates).toHaveBeenCalledWith('10', 'work_order', '101')
    expect(runtimeApi.flowState).not.toHaveBeenCalled()
    expect(wrapper.findAll('.record-flow-state')).toHaveLength(2)
    expect(wrapper.findAll('.record-flow-state')[0]!.text()).toContain('审批 1 状态')
    expect(wrapper.findAll('.record-flow-state')[0]!.text()).toContain('PENDING')
    expect(wrapper.findAll('.record-flow-state')[1]!.text()).toContain('审批 2 状态')
    expect(wrapper.findAll('.record-flow-state')[1]!.text()).toContain('APPROVED')
    expect(wrapper.text()).toContain('审批 1 实例')
    expect(wrapper.text()).toContain('701')
    expect(wrapper.text()).toContain('702')
    expect(wrapper.text()).toContain('2026-07-27T20:30:00Z')
  })

  it('renders an unbound flow state without hiding the current record', async () => {
    route.query = { module: 'work_order', mode: 'view', record: '101' }
    route.fullPath = '/systems/10/workbench?module=work_order&mode=view&record=101'
    vi.mocked(runtimeApi.record).mockResolvedValue({
      ...activeRows[0]!,
      schemaVersionId: '1',
      actions: ['UPDATE'],
    })
    vi.mocked(runtimeApi.flowStates).mockResolvedValue([])

    const wrapper = render()
    await flushPromises()

    expect(wrapper.get('.record-flow-state-empty').text()).toContain('未绑定审批流程')
    expect(wrapper.text()).toContain('Active one')
  })

  it('deep-links an active record into the flow start form', async () => {
    route.query = { module: 'work_order', mode: 'view', record: '101' }
    route.fullPath = '/systems/10/workbench?module=work_order&mode=view&record=101'
    applySession(['flow.instance.start'])
    vi.mocked(runtimeApi.record).mockResolvedValue({
      ...activeRows[0]!,
      schemaVersionId: '1',
      actions: ['UPDATE'],
    })
    vi.mocked(runtimeApi.flowStates).mockResolvedValue([])

    const wrapper = render()
    await flushPromises()

    const start = wrapper.get('.record-flow-start')
    expect((start.element as HTMLButtonElement).disabled).toBe(false)
    await start.trigger('click')
    expect(router.push).toHaveBeenCalledWith({
      name: 'system-flows',
      params: { systemId: '10' },
      query: {
        flowStart: '1',
        moduleCode: 'work_order',
        recordId: '101',
        businessKey: 'R-101',
      },
    })
  })

  it('disables record flow start while any bound approval is pending', async () => {
    route.query = { module: 'work_order', mode: 'view', record: '101' }
    route.fullPath = '/systems/10/workbench?module=work_order&mode=view&record=101'
    applySession(['flow.instance.start'])
    vi.mocked(runtimeApi.record).mockResolvedValue({
      ...activeRows[0]!,
      schemaVersionId: '1',
      actions: ['UPDATE'],
    })
    vi.mocked(runtimeApi.flowStates).mockResolvedValue([{
      instanceId: '701',
      status: 'PENDING',
      version: 0,
      updatedAt: '2026-07-27T20:30:00Z',
    }])

    const wrapper = render()
    await flushPromises()

    const start = wrapper.get('.record-flow-start')
    expect((start.element as HTMLButtonElement).disabled).toBe(true)
    await start.trigger('click')
    expect(router.push).not.toHaveBeenCalled()
  })

  it('loads the recent page and navigates a visible item through the existing detail route', async () => {
    vi.mocked(recentRecordApi.list).mockResolvedValue({
      items: [{
        recentId: '82',
        moduleCode: 'work_order',
        recordId: '102',
        displayLabel: 'R-102 · Active two',
        status: 'ACTIVE',
        accessCount: 3,
        lastAccessedAt: '2026-07-27T12:30:00',
      }],
      page: 1,
      size: 20,
      total: 1,
    })
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.recent-panel-command').trigger('click')
    await flushPromises()
    expect(recentRecordApi.list).toHaveBeenCalledWith('10', 1, 20)

    await wrapper.get('.open-first-recent').trigger('click')
    await flushPromises()
    expect(router.push).toHaveBeenCalledWith(expect.objectContaining({
      query: expect.objectContaining({ module: 'work_order', mode: 'view', record: '102' }),
    }))
  })

  it('searches globally and opens a cross-module result through the existing detail route', async () => {
    vi.mocked(globalSearchApi.search).mockResolvedValue({
      items: [{
        moduleCode: 'asset',
        moduleName: 'Assets',
        recordId: '501',
        recordNo: 'A-501',
        displayLabel: 'Pump station',
        status: 'ACTIVE',
        matchedFieldCodes: ['name'],
      }],
      page: 1,
      size: 20,
      total: 1,
    })
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.global-search-command').trigger('click')
    await wrapper.get('.set-global-search-keyword').trigger('click')
    await wrapper.get('.run-global-search').trigger('click')
    await flushPromises()

    expect(globalSearchApi.search).toHaveBeenCalledWith('10', {
      q: 'pump',
      page: 1,
      size: 20,
    })
    expect(wrapper.get('.open-first-global-search').text()).toBe('Pump station')

    await wrapper.get('.open-first-global-search').trigger('click')
    await flushPromises()
    expect(router.replace).toHaveBeenCalledWith({
      query: { module: 'asset', mode: 'view', record: '501' },
    })
    expect(runtimeApi.definition).toHaveBeenCalledWith('10', 'asset')
    expect(runtimeApi.record).toHaveBeenCalledWith('10', 'asset', '501')
  })

  it('preserves the keyword and current results when a later global-search page fails', async () => {
    vi.mocked(globalSearchApi.search)
      .mockResolvedValueOnce({
        items: [{
          moduleCode: 'work_order',
          moduleName: 'Work orders',
          recordId: '101',
          recordNo: 'R-101',
          displayLabel: 'Pump inspection',
          status: 'ACTIVE',
          matchedFieldCodes: ['title'],
        }],
        page: 1,
        size: 20,
        total: 21,
      })
      .mockRejectedValueOnce(new Error('network unavailable'))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.global-search-command').trigger('click')
    await wrapper.get('.set-global-search-keyword').trigger('click')
    await wrapper.get('.run-global-search').trigger('click')
    await flushPromises()
    await wrapper.get('.global-search-page-two').trigger('click')
    await flushPromises()

    expect(globalSearchApi.search).toHaveBeenLastCalledWith('10', {
      q: 'pump',
      page: 2,
      size: 20,
    })
    expect(wrapper.get('.global-search-context').text()).toContain('pump|1|1|21')
    expect(wrapper.get('.global-search-context').text()).toContain('全局搜索失败，请稍后重试。')
    expect(wrapper.get('.open-first-global-search').text()).toBe('Pump inspection')
  })

  it('keeps server order and opens a quick-create module in the real create route', async () => {
    vi.mocked(quickCreateApi.list).mockResolvedValue({
      items: [
        {
          moduleCode: 'work_order',
          moduleName: 'Work orders',
          schemaVersionId: '1',
        },
        {
          moduleCode: 'asset',
          moduleName: 'Assets',
          schemaVersionId: '2',
        },
      ],
    })
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.quick-create-command').trigger('click')
    await flushPromises()
    expect(quickCreateApi.list).toHaveBeenCalledWith('10')
    expect(wrapper.get('.quick-create-context').text()).toContain('work_order|asset')

    await wrapper.get('.open-second-quick-create').trigger('click')
    await flushPromises()
    expect(router.replace).toHaveBeenCalledWith({
      query: { module: 'asset', mode: 'create' },
    })
    expect(runtimeApi.definition).toHaveBeenCalledWith('10', 'asset')
    expect(runtimeApi.recordSchema).toHaveBeenCalledWith('10', 'asset')
    expect(wrapper.find('.quick-create-panel-stub').exists()).toBe(false)
  })

  it('keeps the quick-create panel and current modules when refresh fails or returns empty', async () => {
    vi.mocked(quickCreateApi.list)
      .mockResolvedValueOnce({
        items: [{
          moduleCode: 'work_order',
          moduleName: 'Work orders',
          schemaVersionId: '1',
        }],
      })
      .mockRejectedValueOnce(new Error('network unavailable'))
      .mockResolvedValueOnce({ items: [] })
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.quick-create-command').trigger('click')
    await flushPromises()
    await wrapper.get('.refresh-quick-create').trigger('click')
    await flushPromises()
    expect(wrapper.get('.quick-create-context').text()).toContain('work_order')
    expect(wrapper.get('.quick-create-context').text())
      .toContain('可新建模块加载失败，请稍后重试。')
    expect(runtimeApi.definition).toHaveBeenLastCalledWith('10', 'work_order')

    await wrapper.get('.refresh-quick-create').trigger('click')
    await flushPromises()
    expect(wrapper.find('.quick-create-panel-stub').exists()).toBe(true)
    expect(wrapper.get('.quick-create-context').text()).toContain('|')
    expect(runtimeApi.definition).toHaveBeenLastCalledWith('10', 'work_order')
  })

  it('keeps the existing dirty-form confirmation before quick-create module switching', async () => {
    route.query = { module: 'work_order', mode: 'create' }
    route.fullPath = '/systems/10/workbench?module=work_order&mode=create'
    vi.mocked(runtimeApi.recordSchema).mockResolvedValueOnce({
      schemaVersionId: '1',
      moduleSnapshotId: '12',
      logicalModuleId: '12',
      checksum: 'checksum',
      runtimeState: 'READY',
      authzEpoch: 1,
      fields: [runtimeField('priority', 'Priority', 'TEXT')],
      actions: ['CREATE'],
      queryLimits: { defaultSize: 50, maxSize: 200, maxSorts: 3 },
    })
    vi.mocked(quickCreateApi.list).mockResolvedValue({
      items: [{
        moduleCode: 'asset',
        moduleName: 'Assets',
        schemaVersionId: '2',
      }],
    })
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.set-batch-edit-value').trigger('click')
    await flushPromises()
    router.replace.mockClear()
    await wrapper.get('.quick-create-command').trigger('click')
    await flushPromises()
    await wrapper.get('.open-first-quick-create').trigger('click')
    await flushPromises()

    expect(confirm).toHaveBeenCalledWith('当前有尚未保存的修改，确定离开吗？')
    expect(router.replace).not.toHaveBeenCalled()
    expect(wrapper.find('.quick-create-panel-stub').exists()).toBe(true)
  })
})
