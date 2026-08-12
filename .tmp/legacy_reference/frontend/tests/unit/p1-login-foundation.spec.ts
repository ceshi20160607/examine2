import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { createMemoryHistory } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { createP1Router, p1Routes } from '@/router'
import { ApiRequestError, type ApiEnvelope } from '@/services/api'
import { useSessionStore } from '@/stores/session'
import type { AuthResult, SystemSummary } from '@/types/session'
import LoginView from '@/vnext/auth/LoginView.vue'
import MySystemsView from '@/vnext/platform-shell/MySystemsView.vue'

const replace = vi.fn()

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return { ...actual, useRouter: () => ({ replace }) }
})

const system: SystemSummary = {
  id: 'sys-1',
  code: 'CRM',
  name: '客户关系管理',
  status: 'ACTIVE',
  memberStatus: 'ACTIVE',
  defaultTenantId: 'tenant-1',
  roleNames: ['系统管理员'],
  recentEnteredAt: '2026-08-09T08:30:00+08:00',
}

function accountContext(systems: SystemSummary[] = [system]): AuthResult {
  const account = { id: '1', username: 'admin', displayName: '平台管理员' }
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

function envelope<T>(data: T, code = 'OK', message = ''): ApiEnvelope<T> {
  return {
    code,
    message,
    data,
    requestId: 'request-id',
    traceId: 'trace-id',
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

describe('P1 login foundation', () => {
  beforeEach(() => {
    replace.mockReset()
    vi.stubGlobal('fetch', vi.fn())
  })

  it('posts the login task and opens My Systems without retaining the password', async () => {
    const { pinia, session } = setupStore()
    const login = vi.spyOn(session, 'login').mockResolvedValue(undefined)
    const wrapper = mount(LoginView, { global: { plugins: [pinia] } })

    await wrapper.get('input[name="account"]').setValue('  admin  ')
    await wrapper.get('input[name="password"]').setValue('cycle-secret')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(login).toHaveBeenCalledWith({ account: 'admin', password: 'cycle-secret' })
    expect(replace).toHaveBeenCalledWith({ name: 'platform-systems' })
    expect((wrapper.get('input[name="password"]').element as HTMLInputElement).value).toBe('')
    expect(wrapper.text()).not.toMatch(/忘记密码|单点登录|多因素认证|调试/)
  })

  it.each([
    { code: 'AUTH_INVALID_CREDENTIALS', status: 401, expectedMessage: '账号或密码不正确，请重新输入。' },
    { code: 'AUTH_ACCOUNT_DISABLED', status: 401, expectedMessage: '当前账号已停用，请联系管理员。' },
    { code: 'AUTH_RATE_LIMITED', status: 429, expectedMessage: '尝试次数过多，请稍后再试。' },
    { code: 'DEPENDENCY_UNAVAILABLE', status: 503, expectedMessage: '登录服务暂时不可用，请稍后重试。' },
  ])('maps $code to a stable user-facing error', async ({ code, status, expectedMessage }) => {
    const { pinia, session } = setupStore()
    vi.spyOn(session, 'login').mockRejectedValue(new ApiRequestError(
      status,
      envelope(null, code, 'server detail that must stay hidden'),
    ))
    const wrapper = mount(LoginView, { global: { plugins: [pinia] } })

    await wrapper.get('input[name="account"]').setValue('admin')
    await wrapper.get('input[name="password"]').setValue('wrong')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toBe(expectedMessage)
    expect(wrapper.text()).not.toMatch(/request-id|trace-id|server detail/)
  })

  it('restores the account and systems from current context exactly once', async () => {
    const { session } = setupStore()
    const fetchMock = vi.mocked(fetch)
    fetchMock.mockImplementation(() => response(accountContext()))

    await session.bootstrap()
    await session.bootstrap()

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/me/context')
    expect(session.authenticated).toBe(true)
    expect(session.context?.account.username).toBe('admin')
    expect(session.systems).toEqual([system])
    expect(session.systemsState).toBe('ready')
  })

  it('tries refresh once after current context returns 401 and applies the recovered context', async () => {
    const { session } = setupStore()
    const fetchMock = vi.mocked(fetch)
    fetchMock
      .mockImplementationOnce(() => response(null, 401, 'AUTH_SESSION_REQUIRED'))
      .mockImplementationOnce(() => response(accountContext()))

    await session.bootstrap()

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/me/context',
      '/api/v1/auth/refresh',
    ])
    expect(fetchMock.mock.calls[1]?.[1]).toEqual(expect.objectContaining({
      method: 'POST',
      credentials: 'include',
    }))
    expect(session.authenticated).toBe(true)
    expect(session.systems).toEqual([system])
  })

  it('becomes anonymous after current context and the single refresh attempt both return 401', async () => {
    const { session } = setupStore()
    const fetchMock = vi.mocked(fetch)
    fetchMock.mockImplementation(() => response(null, 401, 'AUTH_SESSION_REQUIRED'))

    await session.bootstrap()
    await session.bootstrap()

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/me/context',
      '/api/v1/auth/refresh',
    ])
    expect(session.authenticated).toBe(false)
    expect(session.initialized).toBe(true)
    expect(session.systemsState).toBe('ready')
  })

  it('exposes only the four delivered P1 pages, root redirect and fallback', async () => {
    const { session } = setupStore()
    session.initialized = true
    session.systemsState = 'ready'
    const router = createP1Router(createMemoryHistory())

    expect(p1Routes.map((route) => route.path)).toEqual([
      '/login',
      '/register',
      '/platform/systems',
      '/systems/:systemId/admin/onboarding',
      '/',
      '/:pathMatch(.*)*',
    ])
    expect(router.hasRoute('register')).toBe(true)
    expect(router.hasRoute('system-onboarding')).toBe(true)
    expect(router.hasRoute('platform-workbench')).toBe(false)
    expect(router.hasRoute('system-reports')).toBe(false)

    await router.push('/operations')
    await router.isReady()
    expect(router.currentRoute.value.path).toBe('/login')

    session.applyAuth(accountContext())
    await router.push('/reports')
    expect(router.currentRoute.value.path).toBe('/platform/systems')
  })

  it('renders an explicit loading state while requesting the authorized systems once', async () => {
    const { pinia, session } = setupStore()
    session.applyAuth(accountContext())
    vi.mocked(fetch).mockImplementation(() => new Promise<Response>(() => undefined))
    const wrapper = mountSystems(pinia)
    await nextTick()

    expect(wrapper.get('[role="status"]').text()).toContain('正在恢复账号与系统信息')
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(vi.mocked(fetch).mock.calls[0]?.[0]).toBe('/api/v1/context/systems')
  })

  it('renders an explicit list error state', async () => {
    const { pinia, session } = setupStore()
    session.applyAuth(accountContext())
    vi.mocked(fetch).mockRejectedValue(new Error('network detail that must stay hidden'))
    const wrapper = mountSystems(pinia)
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('系统信息暂时无法加载')
    expect(wrapper.get('.vnext-state button').text()).toBe('重新加载')
    expect(wrapper.text()).not.toContain('network detail')
    expect(fetch).toHaveBeenCalledTimes(1)
  })

  it('renders empty guidance without an undelivered create-system action', async () => {
    const { pinia, session } = setupStore()
    session.applyAuth(accountContext())
    vi.mocked(fetch).mockImplementation(() => response([]))
    const wrapper = mountSystems(pinia)
    await flushPromises()

    expect(wrapper.text()).toContain('暂无可访问的系统')
    expect(wrapper.text()).toContain('请联系管理员')
    expect(wrapper.text()).not.toMatch(/创建系统|新建系统/)
    expect(fetch).toHaveBeenCalledTimes(1)
  })

  it('renders a compact authorized-system list using the authoritative list response', async () => {
    const { pinia, session } = setupStore()
    session.applyAuth(accountContext([]))
    vi.mocked(fetch).mockImplementation(() => response([system]))
    const wrapper = mountSystems(pinia)
    await flushPromises()

    expect(wrapper.findAll('nav a')).toHaveLength(1)
    expect(wrapper.text()).toContain('我的系统')
    expect(wrapper.text()).toContain('客户关系管理')
    expect(wrapper.text()).toContain('系统管理员')
    expect(wrapper.findAll('th').map((item) => item.text())).toEqual(['系统', '状态', '角色', '最近进入', '操作'])
    expect(wrapper.findAll('.vnext-system-list__enter')).toHaveLength(1)
    expect(wrapper.text()).not.toMatch(/运营|报表|收藏|关注|SSO|AI|新建系统|占位|调试/)
    expect(fetch).toHaveBeenCalledTimes(1)
  })

  it('logs out through the cookie session API, clears local context and returns to login', async () => {
    const { pinia, session } = setupStore()
    session.applyAuth(accountContext())
    const fetchMock = vi.mocked(fetch)
    fetchMock
      .mockImplementationOnce(() => response([system]))
      .mockImplementationOnce(() => response(null))
    const wrapper = mountSystems(pinia)
    await flushPromises()

    await wrapper.get('.vnext-shell__account button').trigger('click')
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/v1/auth/logout')
    expect(fetchMock.mock.calls[1]?.[1]).toEqual(expect.objectContaining({
      method: 'POST',
      credentials: 'include',
    }))
    expect(session.authenticated).toBe(false)
    expect(session.systems).toEqual([])
    expect(replace).toHaveBeenCalledWith({ name: 'login' })
  })
})
