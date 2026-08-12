import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import AccountSecurityDialog from '@/components/AccountSecurityDialog.vue'
import { useSessionStore } from '@/stores/session'
import LoginView from '@/views/auth/LoginView.vue'

import { expectAccessibleName } from './ui-hardening-testkit'

const route = vi.hoisted(() => ({ query: {} as Record<string, string> }))
const replace = vi.hoisted(() => vi.fn())

vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ replace }),
}))

const FormStub = {
  emits: ['finish'],
  template: '<form @submit.prevent="$emit(\'finish\')"><slot /></form>',
}
const FormItemStub = {
  props: ['label', 'name', 'htmlFor'],
  template: '<div><label :data-field="name" :for="htmlFor"><span>{{ label }}</span></label><slot /></div>',
}
const InputStub = {
  inheritAttrs: false,
  props: ['value'],
  emits: ['update:value'],
  template: '<input v-bind="$attrs" :value="value" @input="$emit(\'update:value\', $event.target.value)" />',
}
const PasswordStub = {
  inheritAttrs: false,
  props: ['value'],
  emits: ['update:value'],
  template: '<input v-bind="$attrs" type="password" :value="value" @input="$emit(\'update:value\', $event.target.value)" />',
}
const ButtonStub = {
  inheritAttrs: false,
  props: ['htmlType', 'loading', 'disabled'],
  emits: ['click'],
  template: '<button v-bind="$attrs" :type="htmlType || \'button\'" :disabled="disabled" :aria-busy="String(Boolean(loading))" @click="$emit(\'click\')"><slot /></button>',
}
const AlertStub = {
  props: ['message', 'description'],
  template: '<div role="alert">{{ message }} {{ description }}<slot name="message" /><slot name="description" /></div>',
}
const ModalStub = {
  props: ['open', 'title'],
  emits: ['cancel'],
  template: '<section v-if="open" role="dialog" aria-modal="true" :aria-label="title"><slot /></section>',
}

const stubs = {
  RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
  'a-alert': AlertStub,
  'a-button': ButtonStub,
  'a-form': FormStub,
  'a-form-item': FormItemStub,
  'a-input': InputStub,
  'a-input-password': PasswordStub,
  'a-modal': ModalStub,
}

function freshSession() {
  const pinia = createPinia()
  setActivePinia(pinia)
  return { pinia, session: useSessionStore() }
}

describe('UI hardening: authentication and dialog accessibility', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    route.query = {}
  })

  it('gives every login control a form-derived accessible name and announces request failures', async () => {
    const { pinia, session } = freshSession()
    vi.spyOn(session, 'login').mockRejectedValue(new Error('network unavailable'))
    const wrapper = mount(LoginView, { global: { plugins: [pinia], stubs } })

    const controls = wrapper.findAll('input')
    expect(controls).toHaveLength(2)
    expect(wrapper.get('label[data-field="account"]').attributes('data-field')).toBe('account')
    expect(wrapper.get('label[data-field="password"]').attributes('data-field')).toBe('password')
    expect(controls[0]!.attributes('aria-label')).toBe('账号')
    expect(controls[1]!.attributes('aria-label')).toBe('密码')
    expectAccessibleName(controls[0]!.element, '账号')
    expectAccessibleName(controls[1]!.element, '密码')
    expectAccessibleName(wrapper.get('button[type="submit"]').element, /登录/u)

    await controls[0]!.setValue('member@example.com')
    await controls[1]!.setValue('secret-value')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    const alert = wrapper.get('[role="alert"]')
    expectAccessibleName(alert.element, /暂时无法连接服务/u)
    expect((controls[0]!.element as HTMLInputElement).value).toBe('member@example.com')
    expect((controls[1]!.element as HTMLInputElement).value).toBe('secret-value')
  })

  it('marks the login form busy and locks every credential submit control during authentication', () => {
    const { pinia, session } = freshSession()
    session.loading = true
    const wrapper = mount(LoginView, { global: { plugins: [pinia], stubs } })

    expect(wrapper.get('form').attributes('aria-busy')).toBe('true')
    for (const control of wrapper.findAll('input, button[type="submit"]')) {
      expect(control.attributes('disabled')).toBeDefined()
    }
    expect(wrapper.get('button[type="submit"]').attributes('aria-busy')).toBe('true')
  })

  it('exposes the account-security modal name, labelled password fields and inline validation alert', async () => {
    const { pinia } = freshSession()
    const wrapper = mount(AccountSecurityDialog, {
      props: { open: true },
      global: { plugins: [pinia], stubs },
    })

    const dialog = wrapper.get('[role="dialog"]')
    expectAccessibleName(dialog.element, '账户安全')

    const fields = wrapper.findAll('input')
    expect(fields).toHaveLength(3)
    const fieldContracts = [
      ['currentPassword', 'account-security-current-password', '当前密码'],
      ['newPassword', 'account-security-new-password', '新密码'],
      ['repeatPassword', 'account-security-repeat-password', '再次输入新密码'],
    ] as const
    fieldContracts.forEach(([name, id, label], index) => {
      expect(wrapper.get(`label[data-field="${name}"]`).attributes('for')).toBe(id)
      expect(fields[index]!.attributes('id')).toBe(id)
      expect(fields[index]!.attributes('aria-label')).toBe(label)
      expect(fields[index]!.attributes('aria-invalid')).toBe('false')
      expect(fields[index]!.attributes('aria-describedby')).toBeUndefined()
    })
    expectAccessibleName(fields[0]!.element, '当前密码')
    expectAccessibleName(fields[1]!.element, '新密码')
    expectAccessibleName(fields[2]!.element, '再次输入新密码')

    await fields[0]!.setValue('current-password')
    await fields[1]!.setValue('short')
    await fields[2]!.setValue('short')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    const alert = wrapper.get('[role="alert"]')
    expect(alert.attributes('id')).toBe('account-security-error')
    expectAccessibleName(alert.element, /10 至 200/u)
    for (const field of fields) {
      expect(field.attributes('aria-invalid')).toBe('true')
      expect(field.attributes('aria-describedby')).toBe('account-security-error')
    }
    expectAccessibleName(wrapper.get('.account-security-submit').element, '更新密码')
  })
})
