import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import AccountSecurityDialog from '@/components/AccountSecurityDialog.vue'
import { ApiRequestError } from '@/services/api'
import { useSessionStore } from '@/stores/session'
import LoginView from '@/views/auth/LoginView.vue'

const routerReplace = vi.hoisted(() => vi.fn())
const route = vi.hoisted(() => ({ query: { notice: 'password-changed' } as Record<string, string> }))
vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ replace: routerReplace }),
}))

const stubs = {
  LockKeyhole: true,
  ShieldCheck: true,
  UserRound: true,
  LogIn: true,
  RouterLink: { template: '<a><slot /></a>' },
  'a-modal': {
    props: ['open'],
    emits: ['cancel'],
    template: '<section v-if="open" class="modal-stub"><slot /></section>',
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
    emits: ['click'],
    template: '<button v-bind="$attrs" :type="htmlType || \'button\'" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
  },
  'a-alert': { template: '<div><slot name="message" /><slot name="description" /></div>' },
}

function renderDialog() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const session = useSessionStore()
  const wrapper = mount(AccountSecurityDialog, {
    props: { open: true },
    global: { plugins: [pinia], stubs },
  })
  return { session, wrapper }
}

async function fillPasswords(wrapper: ReturnType<typeof mount>, current: string, next: string, repeat = next) {
  await wrapper.get('.account-security-current-password').setValue(current)
  await wrapper.get('.account-security-new-password').setValue(next)
  await wrapper.get('.account-security-repeat-password').setValue(repeat)
}

describe('AccountSecurityDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('validates length, difference and matching locally before submitting', async () => {
    const { session, wrapper } = renderDialog()
    const changePassword = vi.spyOn(session, 'changePassword').mockResolvedValue(undefined)

    await fillPasswords(wrapper, 'current-secret', 'short')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.get('.account-security-error').text()).toContain('10 至 200')

    await fillPasswords(wrapper, 'same-secret-value', 'same-secret-value')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.get('.account-security-error').text()).toContain('不能与当前密码相同')

    await fillPasswords(wrapper, 'current-secret', 'next-secret-value', 'different-secret')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.get('.account-security-error').text()).toContain('两次输入的新密码不一致')
    expect(changePassword).not.toHaveBeenCalled()
  })

  it('prevents double submit, clears the dialog and replaces login on success', async () => {
    const { session, wrapper } = renderDialog()
    let resolveRequest!: () => void
    const changePassword = vi.spyOn(session, 'changePassword').mockImplementation(() => new Promise<void>((resolve) => {
      resolveRequest = resolve
    }))
    await fillPasswords(wrapper, 'current-secret', 'next-secret-value')

    await wrapper.get('form').trigger('submit')
    await wrapper.get('form').trigger('submit')
    expect(changePassword).toHaveBeenCalledTimes(1)
    expect(changePassword).toHaveBeenCalledWith({
      currentPassword: 'current-secret',
      newPassword: 'next-secret-value',
    })

    resolveRequest()
    await flushPromises()
    expect(wrapper.emitted('update:open')).toEqual([[false]])
    expect(routerReplace).toHaveBeenCalledWith({ path: '/auth/login', query: { notice: 'password-changed' } })
    expect((wrapper.get('.account-security-current-password').element as HTMLInputElement).value).toBe('')
    expect((wrapper.get('.account-security-new-password').element as HTMLInputElement).value).toBe('')
    expect((wrapper.get('.account-security-repeat-password').element as HTMLInputElement).value).toBe('')
  })

  it('keeps the dialog open, projects API metadata and clears every password on failure', async () => {
    const { session, wrapper } = renderDialog()
    vi.spyOn(session, 'changePassword').mockRejectedValue(new ApiRequestError(400, {
      code: 'CURRENT_PASSWORD_INVALID',
      message: '当前密码不正确',
      data: null,
      requestId: 'request-password-1',
      traceId: 'trace-password-1',
      errors: [],
    }))
    await fillPasswords(wrapper, 'wrong-current', 'next-secret-value')

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('.account-security-error').text()).toContain('当前密码不正确')
    expect(wrapper.get('.account-security-error').text()).toContain('request-password-1')
    expect(wrapper.emitted('update:open')).toBeUndefined()
    expect(wrapper.find('.account-security-dialog').exists()).toBe(true)
    expect((wrapper.get('.account-security-current-password').element as HTMLInputElement).value).toBe('')
    expect((wrapper.get('.account-security-new-password').element as HTMLInputElement).value).toBe('')
    expect((wrapper.get('.account-security-repeat-password').element as HTMLInputElement).value).toBe('')
  })

  it('maps only the bounded password-changed notice code on the login page', () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const wrapper = mount(LoginView, { global: { plugins: [pinia], stubs } })
    expect(wrapper.get('.password-change-success').text()).toContain('密码已更新，所有会话已退出，请使用新密码重新登录。')
  })
})
