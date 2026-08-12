import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { createMemoryHistory } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { createP1Router, p1Routes } from '@/router'
import { type ApiEnvelope } from '@/services/api'
import { InvalidSystemSwitchContextError, useSessionStore } from '@/stores/session'
import type { AuthResult, SessionContext, SystemSummary, TenantSummary } from '@/types/session'
import SystemOnboardingView from '@/vnext/onboarding/SystemOnboardingView.vue'
import MySystemsView from '@/vnext/platform-shell/MySystemsView.vue'

const routerHarness = vi.hoisted(() => ({
  replace: vi.fn(),
  params: { systemId: 'sys-1' } as Record<string, string>,
}))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: () => ({ params: routerHarness.params }),
    useRouter: () => ({ replace: routerHarness.replace }),
  }
})

const account = { id: 'account-1', username: 'owner_1', displayName: '首位管理员' }
const authorizedSystem: SystemSummary = {
  id: 'sys-1',
  code: 'first_system',
  name: '首个业务系统',
  status: 'ACTIVE',
  memberStatus: 'ACTIVE',
  defaultTenantId: 'tenant-1',
  roleNames: ['系统超级管理员'],
  recentEnteredAt: '2026-08-10T09:10:00+08:00',
}
const defaultTenant: TenantSummary = {
  id: 'tenant-1',
  code: 'default',
  name: '默认租户',
  status: 'ACTIVE',
  isDefault: true,
}

function platformContext(systems: SystemSummary[] = []): AuthResult {
  return {
    account,
    context: {
      type: 'PLATFORM',
      account,
      roleIds: [],
      dataScope: null,
      permissionVersion: '1',
      permissions: ['platform.runtime.access'],
      shells: ['PLATFORM_RUNTIME'],
      restrictedMode: 'NONE',
    },
    systems,
    tenants: [],
    firstSystemId: null,
  }
}

function enabledContext(overrides: Partial<SessionContext> = {}): AuthResult {
  return {
    account,
    context: {
      type: 'SYSTEM',
      account,
      systemId: 'sys-1',
      systemName: '首个业务系统',
      tenantId: 'tenant-1',
      tenantName: '默认租户',
      memberId: 'member-1',
      roleIds: ['role-1'],
      dataScope: { id: 'scope-1', code: 'all_data', kind: 'ALL' },
      permissionVersion: '3',
      permissions: ['system.admin.access', 'system.runtime.access'],
      shells: ['SYSTEM_RUNTIME', 'SYSTEM_ADMIN'],
      restrictedMode: 'NONE',
      ...overrides,
    },
    systems: [authorizedSystem],
    tenants: [defaultTenant],
    firstSystemId: null,
  }
}

function settingsOnlyContext(): AuthResult {
  return {
    ...enabledContext({
      permissions: ['system.admin.access'],
      shells: ['SYSTEM_ADMIN'],
      restrictedMode: 'ADMIN_SETTINGS_ONLY',
    }),
    systems: [{ ...authorizedSystem, status: 'DISABLED' }],
  }
}

function envelope<T>(data: T, code = 'OK', message = ''): ApiEnvelope<T> {
  return {
    code,
    message,
    data,
    requestId: 'raw-request-id',
    traceId: 'raw-trace-id',
    errors: [],
  }
}

function response<T>(data: T, status = 200, code = 'OK', message = '') {
  return Promise.resolve(new Response(JSON.stringify(envelope(data, code, message)), {
    status,
    headers: { 'Content-Type': 'application/json' },
  }))
}

function setupStore() {
  const pinia = createPinia()
  setActivePinia(pinia)
  return { pinia, session: useSessionStore() }
}

function mountSystems(pinia: ReturnType<typeof createPinia>) {
  return mount(MySystemsView, { global: { plugins: [pinia] } })
}

function mountOnboarding(pinia: ReturnType<typeof createPinia>, systemId = 'sys-1') {
  routerHarness.params.systemId = systemId
  return mount(SystemOnboardingView, { global: { plugins: [pinia] } })
}

describe('P1 system entry foundation', () => {
  beforeEach(() => {
    routerHarness.replace.mockReset()
    routerHarness.params.systemId = 'sys-1'
    vi.stubGlobal('fetch', vi.fn())
  })

  it('loads the authoritative system list exactly once and overwrites the login snapshot', async () => {
    const { pinia, session } = setupStore()
    const stale = { ...authorizedSystem, id: 'stale', name: '登录快照系统' }
    session.applyAuth(platformContext([stale]))
    let resolveList!: (value: Response) => void
    vi.mocked(fetch).mockImplementation(() => new Promise((resolve) => { resolveList = resolve }))

    const wrapper = mountSystems(pinia)
    await nextTick()

    expect(wrapper.get('.vnext-system-list').attributes('data-state')).toBe('loading')
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(vi.mocked(fetch).mock.calls[0]?.[0]).toBe('/api/v1/context/systems')

    resolveList(await response([authorizedSystem]))
    await flushPromises()

    expect(session.systems).toEqual([authorizedSystem])
    expect(wrapper.get('.vnext-system-list').attributes('data-state')).toBe('ready')
    expect(wrapper.text()).toContain('首个业务系统')
    expect(wrapper.text()).not.toContain('登录快照系统')
    expect(fetch).toHaveBeenCalledTimes(1)
  })

  it('renders the exact empty guidance state without a create action', async () => {
    const { pinia, session } = setupStore()
    session.applyAuth(platformContext([authorizedSystem]))
    vi.mocked(fetch).mockImplementation(() => response([]))

    const wrapper = mountSystems(pinia)
    await flushPromises()

    expect(wrapper.get('.vnext-system-list').attributes('data-state')).toBe('empty_create_first_system_guidance')
    expect(wrapper.text()).toContain('暂无可访问的系统')
    expect(wrapper.text()).not.toMatch(/创建系统|新建系统/)
  })

  it('adds one list request per explicit retry without an automatic loop', async () => {
    const { pinia, session } = setupStore()
    session.applyAuth(platformContext())
    vi.mocked(fetch)
      .mockRejectedValueOnce(new Error('private network detail'))
      .mockImplementationOnce(() => response([authorizedSystem]))

    const wrapper = mountSystems(pinia)
    await flushPromises()

    expect(wrapper.get('.vnext-system-list').attributes('data-state')).toBe('error')
    expect(wrapper.text()).not.toContain('private network detail')
    expect(fetch).toHaveBeenCalledTimes(1)

    await wrapper.get('.vnext-state button').trigger('click')
    await flushPromises()

    expect(fetch).toHaveBeenCalledTimes(2)
    expect(wrapper.get('.vnext-system-list').attributes('data-state')).toBe('ready')
  })

  it('shows a compact table with one row action and no future navigation', async () => {
    const { pinia, session } = setupStore()
    session.applyAuth(platformContext())
    vi.mocked(fetch).mockImplementation(() => response([authorizedSystem]))

    const wrapper = mountSystems(pinia)
    await flushPromises()

    expect(wrapper.findAll('nav a').map((item) => item.text())).toEqual(['我的系统'])
    expect(wrapper.findAll('th').map((item) => item.text())).toEqual(['系统', '状态', '角色', '最近进入', '操作'])
    expect(wrapper.findAll('tbody tr')).toHaveLength(1)
    expect(wrapper.findAll('.vnext-system-list__enter')).toHaveLength(1)
    expect(wrapper.text()).toContain('系统超级管理员')
    expect(wrapper.text()).not.toMatch(/运营|报表|收藏|关注|SSO|AI|模块|租户|新建系统|平台切换/)
  })

  it('issues one switch under double click, applies the enabled owner context and navigates exactly', async () => {
    const { pinia, session } = setupStore()
    session.applyAuth(platformContext())
    let resolveSwitch!: (value: Response) => void
    vi.mocked(fetch)
      .mockImplementationOnce(() => response([authorizedSystem]))
      .mockImplementationOnce(() => new Promise((resolve) => { resolveSwitch = resolve }))
    const wrapper = mountSystems(pinia)
    await flushPromises()

    const button = wrapper.get('.vnext-system-list__enter')
    const firstClick = button.trigger('click')
    const secondClick = button.trigger('click')
    await Promise.resolve()

    expect(wrapper.get('.vnext-system-list').attributes('data-state')).toBe('switching')
    expect(fetch).toHaveBeenCalledTimes(2)
    expect(vi.mocked(fetch).mock.calls[1]?.[0]).toBe('/api/v1/context/systems/sys-1:switch')

    resolveSwitch(await response(enabledContext()))
    await Promise.all([firstClick, secondClick])
    await flushPromises()

    expect(session.context?.type).toBe('SYSTEM')
    expect(session.context?.restrictedMode).toBe('NONE')
    expect(routerHarness.replace).toHaveBeenCalledTimes(1)
    expect(routerHarness.replace).toHaveBeenCalledWith({ name: 'system-onboarding', params: { systemId: 'sys-1' } })
  })

  it.each([
    ['SYSTEM_NOT_FOUND', 404, 'membership_denied', '无法进入该系统'],
    ['SYSTEM_MEMBER_REQUIRED', 403, 'membership_denied', '无法进入该系统'],
    ['SYSTEM_MEMBER_DISABLED', 403, 'membership_denied', '无法进入该系统'],
    ['SYSTEM_DISABLED', 403, 'system_disabled', '该系统当前已停用'],
    ['SYSTEM_NO_TENANT', 403, 'error', '系统入口暂时不可用'],
  ])('maps %s code first to %s without replacing the platform context', async (code, status, state, copy) => {
    const { pinia, session } = setupStore()
    session.applyAuth(platformContext())
    vi.mocked(fetch)
      .mockImplementationOnce(() => response([authorizedSystem]))
      .mockImplementationOnce(() => response(null, status, code, 'private raw backend message'))
    const wrapper = mountSystems(pinia)
    await flushPromises()

    await wrapper.get('.vnext-system-list__enter').trigger('click')
    await flushPromises()

    expect(wrapper.get('.vnext-system-list').attributes('data-state')).toBe(state)
    expect(wrapper.get('[data-entry-state]').text()).toContain(copy)
    expect(wrapper.text()).not.toMatch(/private raw backend message|raw-request-id|raw-trace-id/)
    expect(session.context?.type).toBe('PLATFORM')
    expect(routerHarness.replace).not.toHaveBeenCalled()
    expect(fetch).toHaveBeenCalledTimes(2)
  })

  it.each([
    ['different system', () => enabledContext({ systemId: 'sys-other' })],
    ['missing tenant', () => enabledContext({ tenantId: '' })],
    ['missing member', () => enabledContext({ memberId: '' })],
    ['empty roles', () => enabledContext({ roleIds: [] })],
    ['missing data scope', () => enabledContext({ dataScope: null })],
    ['missing permission version', () => enabledContext({ permissionVersion: '' })],
    ['missing permissions', () => enabledContext({ permissions: [] })],
    ['illegal restricted mode', () => enabledContext({ restrictedMode: undefined })],
    ['missing owner admin shell', () => enabledContext({ shells: ['SYSTEM_RUNTIME'] })],
    ['unexpected platform shell', () => enabledContext({ shells: ['SYSTEM_RUNTIME', 'SYSTEM_ADMIN', 'PLATFORM_RUNTIME'] })],
    ['non-null first system receipt', () => ({ ...enabledContext(), firstSystemId: 'sys-1' })],
    ['wrong system projection', () => ({ ...enabledContext(), systems: [{ ...authorizedSystem, id: 'sys-other' }] })],
    ['wrong tenant projection', () => ({ ...enabledContext(), tenants: [{ ...defaultTenant, id: 'tenant-other' }] })],
  ])('rejects malformed success with %s before applying it', async (_label, malformed) => {
    const { session } = setupStore()
    const before = platformContext([authorizedSystem])
    session.applyAuth(before)
    vi.mocked(fetch).mockImplementation(() => response(malformed()))

    await expect(session.switchSystem('sys-1')).rejects.toBeInstanceOf(InvalidSystemSwitchContextError)

    expect(session.context).toEqual(before.context)
    expect(session.systems).toEqual(before.systems)
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(vi.mocked(fetch).mock.calls[0]?.[0]).toBe('/api/v1/context/systems/sys-1:switch')
  })

  it('renders the enabled owner onboarding from the selected system projection without business requests', () => {
    const { pinia, session } = setupStore()
    session.applyAuth(enabledContext())

    const wrapper = mountOnboarding(pinia)

    expect(wrapper.attributes('data-state')).toBe('ready')
    expect(wrapper.findAll('nav a').map((item) => item.text())).toEqual(['配置引导'])
    expect(wrapper.text()).toContain('系统上下文已就绪')
    expect(wrapper.text()).toContain('首个业务系统')
    expect(wrapper.text()).toContain('首位管理员')
    expect(wrapper.text()).toContain('系统超级管理员')
    expect(wrapper.text()).not.toMatch(/SYSTEM_RUNTIME|运营|报表|模块|租户|业务数据/)
    expect(fetch).not.toHaveBeenCalled()
  })

  it('does not invent an administrator role when the current projection has no role names', () => {
    const { pinia, session } = setupStore()
    session.applyAuth({ ...enabledContext(), systems: [{ ...authorizedSystem, roleNames: [] }] })

    const wrapper = mountOnboarding(pinia)

    expect(wrapper.text()).toContain('身份信息暂不可用')
    expect(wrapper.text()).not.toContain('系统超级管理员')
  })

  it('renders the disabled administrator settings-only success without runtime entry', () => {
    const { pinia, session } = setupStore()
    session.applyAuth(settingsOnlyContext())

    const wrapper = mountOnboarding(pinia)

    expect(wrapper.attributes('data-state')).toBe('admin_settings_only')
    expect(wrapper.text()).toContain('系统已停用，仅可进行管理设置')
    expect(wrapper.findAll('nav a').map((item) => item.text())).toEqual(['配置引导'])
    expect(wrapper.text()).not.toMatch(/SYSTEM_RUNTIME|运行入口|运营|报表|模块|业务数据/)
    expect(fetch).not.toHaveBeenCalled()
  })

  it.each([
    ['platform context', platformContext(), 'sys-1'],
    ['wrong system id', enabledContext(), 'sys-other'],
    ['missing SYSTEM_ADMIN', enabledContext({ shells: ['SYSTEM_RUNTIME'] }), 'sys-1'],
  ])('renders permission_denied for %s without switching by URL', (_label, context, systemId) => {
    const { pinia, session } = setupStore()
    session.applyAuth(context)

    const wrapper = mountOnboarding(pinia, systemId)

    expect(wrapper.attributes('data-state')).toBe('permission_denied')
    expect(wrapper.text()).toContain('当前账号没有此系统的管理权限')
    expect(fetch).not.toHaveBeenCalled()
    expect(routerHarness.replace).not.toHaveBeenCalled()
  })

  it('keeps the six-route boundary and isolates PLATFORM and SYSTEM entries without legacy switching', async () => {
    const { session } = setupStore()
    session.applyAuth(enabledContext())
    const router = createP1Router(createMemoryHistory())

    expect(p1Routes.map((route) => route.path)).toEqual([
      '/login',
      '/register',
      '/platform/systems',
      '/systems/:systemId/admin/onboarding',
      '/',
      '/:pathMatch(.*)*',
    ])

    await router.push('/platform/systems')
    await router.isReady()
    expect(router.currentRoute.value.fullPath).toBe('/systems/sys-1/admin/onboarding')

    await router.push('/login')
    expect(router.currentRoute.value.fullPath).toBe('/systems/sys-1/admin/onboarding')

    session.applyAuth(platformContext())
    await router.push('/platform/systems')
    expect(router.currentRoute.value.fullPath).toBe('/platform/systems')
    expect(fetch).not.toHaveBeenCalled()
    expect(p1Routes.map((route) => String(route.path)).join(' ')).not.toMatch(/operations|reports|favorites|follow|sso|ai|modules|tenants/)
  })

  it('restores settings-only through current once, mounts without another request, then logs out', async () => {
    const { pinia, session } = setupStore()
    vi.mocked(fetch)
      .mockImplementationOnce(() => response(settingsOnlyContext()))
      .mockImplementationOnce(() => response(null))

    await session.bootstrap()
    const wrapper = mountOnboarding(pinia)

    expect(wrapper.attributes('data-state')).toBe('admin_settings_only')
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(vi.mocked(fetch).mock.calls[0]?.[0]).toBe('/api/v1/me/context')

    await wrapper.get('.vnext-onboarding__account button').trigger('click')
    await flushPromises()

    expect(fetch).toHaveBeenCalledTimes(2)
    expect(vi.mocked(fetch).mock.calls[1]?.[0]).toBe('/api/v1/auth/logout')
    expect(session.authenticated).toBe(false)
    expect(routerHarness.replace).toHaveBeenCalledWith({ name: 'login' })
    expect(vi.mocked(fetch).mock.calls.map((call) => String(call[0])).join(' ')).not.toMatch(/platform:switch|tenants|modules|operations|reports/)
  })
})
