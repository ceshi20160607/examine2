import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { createP1Router, p1Routes } from '@/router'
import type { ApiEnvelope, ApiErrorItem } from '@/services/api'
import { useSessionStore } from '@/stores/session'
import type { AuthResult, RegisterFirstSystemInput, SessionContext, SystemSummary } from '@/types/session'
import LoginView from '@/vnext/auth/LoginView.vue'
import SystemOnboardingView from '@/vnext/onboarding/SystemOnboardingView.vue'
import RegisterFirstSystemView from '@/vnext/register/RegisterFirstSystemView.vue'

const routerMock = vi.hoisted(() => ({
  replace: vi.fn(),
  params: { systemId: 'sys-1' },
}))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: () => ({ params: routerMock.params }),
    useRouter: () => ({ replace: routerMock.replace }),
  }
})

const system: SystemSummary = {
  id: 'sys-1',
  code: 'first_system',
  name: '首个业务系统',
  status: 'ACTIVE',
  memberStatus: 'ACTIVE',
  defaultTenantId: 'tenant-1',
  roleNames: ['系统超级管理员'],
  recentEnteredAt: null,
}

const validForm: RegisterFirstSystemInput = {
  username: 'owner_1',
  displayName: '首位管理员',
  password: 'safe-password-1',
  systemName: '首个业务系统',
  systemCode: 'first_system',
}

function systemAccountContext(overrides: Partial<SessionContext> = {}): AuthResult {
  const account = { id: 'account-1', username: 'owner_1', displayName: '首位管理员' }
  return {
    account,
    firstSystemId: 'sys-1',
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
      permissionVersion: '1',
      permissions: ['system.runtime.access', 'system.admin.access'],
      shells: ['SYSTEM_RUNTIME', 'SYSTEM_ADMIN'],
      restrictedMode: 'NONE',
      ...overrides,
    },
    systems: [system],
    tenants: [{
      id: 'tenant-1',
      code: 'default',
      name: '默认租户',
      status: 'ACTIVE',
      isDefault: true,
    }],
  }
}

function platformAccountContext(): AuthResult {
  const account = { id: 'platform-1', username: 'admin', displayName: '平台管理员' }
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
    systems: [],
    tenants: [],
    firstSystemId: null,
  }
}

function envelope<T>(data: T, code = 'OK', message = '', errors: ApiErrorItem[] = []): ApiEnvelope<T> {
  return {
    code,
    message,
    data,
    requestId: 'request-id-that-must-stay-hidden',
    traceId: 'trace-id-that-must-stay-hidden',
    errors,
  }
}

function response<T>(
  data: T,
  status = 200,
  code = 'OK',
  message = '',
  errors: ApiErrorItem[] = [],
) {
  return Promise.resolve(new Response(JSON.stringify(envelope(data, code, message, errors)), {
    status,
    headers: { 'Content-Type': 'application/json' },
  }))
}

function setupStore() {
  const pinia = createPinia()
  setActivePinia(pinia)
  return { pinia, session: useSessionStore() }
}

function mountRegister(pinia: ReturnType<typeof createPinia>) {
  return mount(RegisterFirstSystemView, { global: { plugins: [pinia] } })
}

function mountOnboarding(pinia: ReturnType<typeof createPinia>) {
  return mount(SystemOnboardingView, { global: { plugins: [pinia] } })
}

async function fillRegistration(wrapper: VueWrapper, values: RegisterFirstSystemInput = validForm) {
  for (const [name, value] of Object.entries(values)) {
    await wrapper.get(`input[name="${name}"]`).setValue(value)
  }
}

describe('P1 registration foundation', () => {
  beforeEach(() => {
    routerMock.replace.mockReset()
    routerMock.params.systemId = 'sys-1'
    vi.restoreAllMocks()
    vi.stubGlobal('fetch', vi.fn())
  })

  it.each([
    ['username', '1invalid'],
    ['displayName', ' '],
    ['password', 'too-short'],
    ['systemName', ' '],
    ['systemCode', 'Upper_case'],
  ] as const)('rejects an invalid %s before making a request', async (field, value) => {
    const { pinia, session } = setupStore()
    const register = vi.spyOn(session, 'register')
    const wrapper = mountRegister(pinia)
    await fillRegistration(wrapper, { ...validForm, [field]: value })

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(register).not.toHaveBeenCalled()
    expect(wrapper.get(`[data-error-for="${field}"]`).text()).not.toBe('')
    expect(wrapper.get('[data-state="validation_error"]')).toBeTruthy()
    expect((wrapper.get('input[name="password"]').element as HTMLInputElement).value).toBe('')
    expect(fetch).not.toHaveBeenCalled()
  })

  it.each([
    {
      username: 'Ab1',
      displayName: '名',
      password: '1234567890',
      systemName: '系',
      systemCode: 'abc',
    },
    {
      username: `A${'b'.repeat(31)}`,
      displayName: '名'.repeat(120),
      password: 'p'.repeat(200),
      systemName: '系'.repeat(160),
      systemCode: `a${'b'.repeat(31)}`,
    },
  ])('accepts the exact minimum and maximum field boundaries', async (input) => {
    const { pinia, session } = setupStore()
    const register = vi.spyOn(session, 'register').mockResolvedValue('sys-1')
    const wrapper = mountRegister(pinia)
    await fillRegistration(wrapper, input)

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(register).toHaveBeenCalledOnce()
    expect(register.mock.calls[0]?.[0]).toEqual(input)
    expect(register.mock.calls[0]?.[1]).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i)
  })

  it('posts one normalized submission with one UUID and enters onboarding without list or switch calls', async () => {
    const { pinia, session } = setupStore()
    const fetchMock = vi.mocked(fetch)
    fetchMock.mockImplementation(() => response(systemAccountContext()))
    vi.spyOn(globalThis.crypto, 'randomUUID')
      .mockReturnValueOnce('11111111-1111-4111-8111-111111111111')
      .mockReturnValue('22222222-2222-4222-8222-222222222222')
    const wrapper = mountRegister(pinia)
    await fillRegistration(wrapper, {
      ...validForm,
      username: '  Ａbc_1  ',
      displayName: '  首位管理员  ',
      systemName: '  首个业务系统  ',
    })

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledOnce()
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/auth/register')
    const options = fetchMock.mock.calls[0]?.[1]
    expect(options).toEqual(expect.objectContaining({ method: 'POST', credentials: 'include' }))
    expect(new Headers(options?.headers).get('Idempotency-Key')).toBe('11111111-1111-4111-8111-111111111111')
    expect(JSON.parse(String(options?.body))).toEqual({
      ...validForm,
      username: 'Abc_1',
      displayName: '首位管理员',
      systemName: '首个业务系统',
    })
    expect(session.context?.type).toBe('SYSTEM')
    expect(session.context?.shells).toContain('SYSTEM_ADMIN')
    expect(routerMock.replace).toHaveBeenCalledWith({
      name: 'system-onboarding',
      params: { systemId: 'sys-1' },
    })
    expect(fetchMock.mock.calls.map((call) => call[0])).not.toContain('/api/v1/context/systems')
    expect(fetchMock.mock.calls.some((call) => String(call[0]).includes(':switch'))).toBe(false)
  })

  it('suppresses a second submit while the first request is pending', async () => {
    const { pinia } = setupStore()
    let resolveRequest!: (value: Response) => void
    const pending = new Promise<Response>((resolve) => { resolveRequest = resolve })
    const fetchMock = vi.mocked(fetch)
    fetchMock.mockReturnValue(pending)
    const wrapper = mountRegister(pinia)
    await fillRegistration(wrapper)

    const first = wrapper.get('form').trigger('submit')
    const second = wrapper.get('form').trigger('submit')
    await Promise.resolve()

    expect(fetchMock).toHaveBeenCalledOnce()
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    resolveRequest(new Response(JSON.stringify(envelope(systemAccountContext())), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }))
    await Promise.all([first, second])
    await flushPromises()
  })

  it.each([
    ['REGISTER_USERNAME_CONFLICT', 'username', 'username_conflict'],
    ['REGISTER_SYSTEM_CODE_CONFLICT', 'systemCode', 'system_code_conflict'],
  ] as const)('anchors %s to %s and hides server diagnostics', async (code, field, expectedState) => {
    const { pinia } = setupStore()
    vi.mocked(fetch).mockImplementation(() => response(
      null,
      409,
      code,
      'raw persistence detail',
    ))
    const wrapper = mountRegister(pinia)
    await fillRegistration(wrapper)

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get(`[data-error-for="${field}"]`).text()).toContain('已被使用')
    expect(wrapper.get(`[data-state="${expectedState}"]`)).toBeTruthy()
    expect(wrapper.text()).not.toMatch(/request-id|trace-id|raw persistence detail/)
  })

  it('maps REGISTER_INVALID error paths to stable field messages', async () => {
    const { pinia } = setupStore()
    vi.mocked(fetch).mockImplementation(() => response(
      null,
      400,
      'REGISTER_INVALID',
      'raw validation detail',
      [{ code: 'INVALID', path: '/body/systemName', message: 'raw field detail' }],
    ))
    const wrapper = mountRegister(pinia)
    await fillRegistration(wrapper)

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[data-error-for="systemName"]').text()).toBe('系统名称长度应为 1 至 160 个字符。')
    expect(wrapper.text()).not.toMatch(/raw validation detail|raw field detail|request-id|trace-id/)
  })

  it('preserves four non-secret values, clears the password and shows the frozen rollback message', async () => {
    const { pinia } = setupStore()
    vi.mocked(fetch).mockImplementation(() => response(null, 500, 'REGISTER_FAILED', 'database detail'))
    const wrapper = mountRegister(pinia)
    await fillRegistration(wrapper)

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    for (const field of ['username', 'displayName', 'systemName', 'systemCode'] as const) {
      expect((wrapper.get(`input[name="${field}"]`).element as HTMLInputElement).value).toBe(validForm[field])
    }
    expect((wrapper.get('input[name="password"]').element as HTMLInputElement).value).toBe('')
    expect(wrapper.get('.vnext-register__error').text()).toBe('创建未完成，可重新输入密码后安全重试。')
    expect(wrapper.text()).not.toMatch(/database detail|request-id|trace-id/)
  })

  it.each([
    [409, 'UNKNOWN_CONFLICT', '用户名或系统编码已被使用，请检查后重试。'],
    [500, 'UNKNOWN_FAILURE', '创建未完成，可重新输入密码后安全重试。'],
  ])('uses HTTP %s only as a stable fallback when the business code is unknown', async (status, code, expected) => {
    const { pinia } = setupStore()
    vi.mocked(fetch).mockImplementation(() => response(null, status, code, 'raw server detail'))
    const wrapper = mountRegister(pinia)
    await fillRegistration(wrapper)

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('.vnext-register__error').text()).toBe(expected)
    expect((wrapper.get('input[name="password"]').element as HTMLInputElement).value).toBe('')
    expect(wrapper.text()).not.toMatch(/raw server detail|request-id|trace-id/)
  })

  it('does not authenticate or navigate when a success response lacks SYSTEM_ADMIN', async () => {
    const { pinia, session } = setupStore()
    const malformed = systemAccountContext({ shells: ['SYSTEM_RUNTIME'] })
    vi.mocked(fetch).mockImplementation(() => response(malformed))
    const wrapper = mountRegister(pinia)
    await fillRegistration(wrapper)

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(session.authenticated).toBe(false)
    expect(routerMock.replace).not.toHaveBeenCalled()
    expect(wrapper.get('.vnext-register__error').text()).toContain('无法确认新系统上下文')
    expect((wrapper.get('input[name="password"]').element as HTMLInputElement).value).toBe('')
  })

  it('adds only the frozen registration and exact onboarding runtime routes', async () => {
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
    expect(router.hasRoute('platform-workbench')).toBe(false)
    expect(router.hasRoute('system-reports')).toBe(false)
    expect(router.hasRoute('system-operations')).toBe(false)

    await router.push('/systems/sys-1/admin/onboarding')
    await router.isReady()
    expect(router.currentRoute.value.path).toBe('/login')

    await router.push('/not-delivered')
    expect(router.currentRoute.value.path).toBe('/login')

    session.applyAuth(platformAccountContext())
    await router.push('/register')
    expect(router.currentRoute.value.path).toBe('/platform/systems')

    session.applyAuth(systemAccountContext())
    await router.push('/')
    expect(router.currentRoute.value.path).toBe('/systems/sys-1/admin/onboarding')

    await router.push('/systems/another-system/admin/onboarding')
    expect(router.currentRoute.value.path).toBe('/systems/another-system/admin/onboarding')
    expect(fetch).not.toHaveBeenCalled()
  })

  it('exposes registration as the only login secondary action', () => {
    const { pinia } = setupStore()
    const wrapper = mount(LoginView, { global: { plugins: [pinia] } })

    expect(wrapper.get('.vnext-login__secondary a').attributes('href')).toBe('/register')
    expect(wrapper.get('.vnext-login__secondary').text()).toContain('注册')
    expect(wrapper.text()).not.toMatch(/忘记密码|单点登录|多因素认证/)
  })

  it.each([
    ['loading', null, 'sys-1', '正在恢复系统上下文'],
    ['error', null, 'sys-1', '系统上下文暂时无法加载'],
    ['permission_denied', platformAccountContext(), 'sys-1', '无法打开配置引导'],
    ['permission_denied', systemAccountContext(), 'wrong-system', '无法打开配置引导'],
    ['permission_denied', systemAccountContext({ shells: ['SYSTEM_RUNTIME'] }), 'sys-1', '无法打开配置引导'],
    ['admin_settings_only', systemAccountContext({
      restrictedMode: 'ADMIN_SETTINGS_ONLY',
      permissions: ['system.admin.access'],
      shells: ['SYSTEM_ADMIN'],
    }), 'sys-1', '当前仅开放管理设置'],
    ['ready', systemAccountContext(), 'sys-1', '系统创建完成'],
  ] as const)('renders the %s onboarding state without mounting a business request', (expectedState, context, routeSystemId, text) => {
    const { pinia, session } = setupStore()
    routerMock.params.systemId = routeSystemId
    if (context) session.applyAuth(context)
    if (expectedState === 'error') session.systemsState = 'error'
    const wrapper = mountOnboarding(pinia)

    expect(wrapper.get(`[data-state="${expectedState}"]`).text()).toContain(text)
    expect(wrapper.findAll('nav a')).toHaveLength(1)
    expect(wrapper.get('nav a').text()).toBe('配置引导')
    expect(fetch).not.toHaveBeenCalled()
    expect(wrapper.text()).not.toMatch(/模块|组织|角色|租户|SSO|运营|报表|收藏|关注|AI|创建另一系统|占位|调试/)
  })

  it('logs out from onboarding, clears the session and returns to login', async () => {
    const { pinia, session } = setupStore()
    session.applyAuth(systemAccountContext())
    vi.mocked(fetch).mockImplementation(() => response(null))
    const wrapper = mountOnboarding(pinia)

    await wrapper.get('.vnext-onboarding__account button').trigger('click')
    await flushPromises()

    expect(vi.mocked(fetch)).toHaveBeenCalledOnce()
    expect(vi.mocked(fetch).mock.calls[0]?.[0]).toBe('/api/v1/auth/logout')
    expect(session.authenticated).toBe(false)
    expect(routerMock.replace).toHaveBeenCalledWith({ name: 'login' })
  })
})
