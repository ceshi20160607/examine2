import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiRequestError } from '@/services/api'
import ForgotPasswordView from '@/views/auth/ForgotPasswordView.vue'
import LoginView from '@/views/auth/LoginView.vue'
import ResetPasswordView from '@/views/auth/ResetPasswordView.vue'

const requestRecovery = vi.hoisted(() => vi.fn())
const resetPassword = vi.hoisted(() => vi.fn())
const routerReplace = vi.hoisted(() => vi.fn())
const route = vi.hoisted(() => ({
  path: '/auth/password/reset',
  query: {} as Record<string, string>,
  hash: '',
}))

vi.mock('@/services/passwordRecovery', () => ({
  passwordRecoveryApi: {
    request: requestRecovery,
    reset: resetPassword,
  },
}))

vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ replace: routerReplace }),
}))

const stubs = {
  AlertTriangle: true,
  ArrowLeft: true,
  KeyRound: true,
  LockKeyhole: true,
  LogIn: true,
  Mail: true,
  Send: true,
  UserRound: true,
  RouterLink: {
    props: ['to'],
    template: '<a :href="typeof to === \'string\' ? to : \'\'"><slot /></a>',
  },
  'a-form': {
    emits: ['finish'],
    template: '<form @submit.prevent="$emit(\'finish\')"><slot /></form>',
  },
  'a-form-item': { template: '<label><slot /></label>' },
  'a-input': {
    inheritAttrs: false,
    props: ['value'],
    emits: ['update:value'],
    template: '<input v-bind="$attrs" :value="value" @input="$emit(\'update:value\', $event.target.value)" />',
  },
  'a-input-password': {
    inheritAttrs: false,
    props: ['value'],
    emits: ['update:value'],
    template: '<input v-bind="$attrs" :value="value" type="password" @input="$emit(\'update:value\', $event.target.value)" />',
  },
  'a-button': {
    inheritAttrs: false,
    props: ['htmlType', 'disabled', 'loading'],
    template: '<button v-bind="$attrs" :type="htmlType || \'button\'" :disabled="disabled"><slot /></button>',
  },
  'a-alert': { template: '<div><slot name="message" /><slot name="description" /></div>' },
}

describe('password recovery pages', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    requestRecovery.mockReset()
    resetPassword.mockReset()
    routerReplace.mockReset()
    route.path = '/auth/password/reset'
    route.query = {}
    route.hash = ''
  })

  it('shows the same bounded success response and clears the account identifier', async () => {
    requestRecovery.mockResolvedValue(undefined)
    const wrapper = mount(ForgotPasswordView, { global: { stubs } })

    await wrapper.get('.password-recovery-account').setValue(' owner@example.com ')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(requestRecovery).toHaveBeenCalledWith('owner@example.com')
    expect(wrapper.get('.password-recovery-requested').text()).toContain('如果账号有效')
    expect(wrapper.get('.password-recovery-requested').text()).not.toContain('owner@example.com')
    expect((wrapper.get('.password-recovery-account').element as HTMLInputElement).value).toBe('')
  })

  it('labels recovery controls and locks the form while the request is pending', async () => {
    let resolveRequest!: () => void
    requestRecovery.mockReturnValue(new Promise<void>((resolve) => { resolveRequest = resolve }))
    const wrapper = mount(ForgotPasswordView, { global: { stubs } })

    expect(wrapper.get('.password-recovery-account').attributes('aria-label')).toBe('账号')
    expect(wrapper.get('.password-recovery-submit').attributes('aria-label')).toBe('发送密码恢复信息')

    await wrapper.get('.password-recovery-account').setValue('owner@example.com')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.get('form').attributes('aria-busy')).toBe('true')
    expect(wrapper.get('.password-recovery-account').attributes('disabled')).toBeDefined()
    expect(wrapper.get('.password-recovery-submit').attributes('disabled')).toBeDefined()

    resolveRequest()
    await flushPromises()
    expect(wrapper.get('form').attributes('aria-busy')).toBe('false')
  })

  it('captures and trims the token, immediately scrubs it from the URL, then completes reset', async () => {
    route.query = { token: ' single-use-token ', source: 'email' }
    resetPassword.mockResolvedValue(undefined)
    const wrapper = mount(ResetPasswordView, { global: { stubs } })

    expect(routerReplace).toHaveBeenCalledWith({
      path: '/auth/password/reset', query: { source: 'email' }, hash: '',
    })
    expect(wrapper.html()).not.toContain('single-use-token')

    await wrapper.get('.password-reset-new').setValue('next-secret-value')
    await wrapper.get('.password-reset-repeat').setValue('different-secret')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.get('.password-reset-error').text()).toContain('两次输入的新密码不一致')
    expect(resetPassword).not.toHaveBeenCalled()

    await wrapper.get('.password-reset-new').setValue('next-secret-value')
    await wrapper.get('.password-reset-repeat').setValue('next-secret-value')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(resetPassword).toHaveBeenCalledWith('single-use-token', 'next-secret-value')
    expect(routerReplace).toHaveBeenLastCalledWith('/auth/password/reset/success')
  })

  it('renders an independent invalid-link state when no token is present', () => {
    const wrapper = mount(ResetPasswordView, { global: { stubs } })

    expect(wrapper.get('.password-reset-invalid').text()).toContain('恢复链接无效')
    expect(wrapper.find('.password-reset-form').exists()).toBe(false)
    expect(resetPassword).not.toHaveBeenCalled()
  })

  it('projects reset error metadata and clears password fields without restoring the URL token', async () => {
    route.query = { token: 'expired-token' }
    resetPassword.mockRejectedValue(new ApiRequestError(400, {
      code: 'PASSWORD_RECOVERY_TOKEN_EXPIRED',
      message: '恢复链接已过期',
      data: null,
      requestId: 'request-reset-1',
      traceId: 'trace-reset-1',
      errors: [],
    }))
    const wrapper = mount(ResetPasswordView, { global: { stubs } })
    await wrapper.get('.password-reset-new').setValue('next-secret-value')
    await wrapper.get('.password-reset-repeat').setValue('next-secret-value')

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('.password-reset-error').text()).toContain('恢复链接已过期')
    expect(wrapper.get('.password-reset-error').text()).toContain('request-reset-1')
    expect((wrapper.get('.password-reset-new').element as HTMLInputElement).value).toBe('')
    expect((wrapper.get('.password-reset-repeat').element as HTMLInputElement).value).toBe('')
    expect(routerReplace).toHaveBeenCalledTimes(1)
  })

  it('adds the recovery entry and maps only the bounded reset success notice on login', () => {
    route.query = { notice: 'password-reset' }
    const wrapper = mount(LoginView, { global: { plugins: [createPinia()], stubs } })

    expect(wrapper.get('.login-recovery-link a').attributes('href')).toBe('/auth/password/forgot')
    expect(wrapper.get('.password-success').text()).toContain('密码已重置，请使用新密码登录。')

    route.query = { notice: 'untrusted-message' }
    const bounded = mount(LoginView, { global: { plugins: [createPinia()], stubs } })
    expect(bounded.find('.password-success').exists()).toBe(false)
  })
})
