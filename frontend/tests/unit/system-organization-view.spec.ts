import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { systemAdminApi } from '@/services/admin'
import { useSessionStore } from '@/stores/session'
import SystemOrganizationView from '@/views/system/admin/SystemOrganizationView.vue'

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { systemId: '10' } }),
}))

vi.mock('ant-design-vue', () => ({
  message: { success: vi.fn() },
}))

vi.mock('@/services/admin', () => ({
  systemAdminApi: {
    listDepartments: vi.fn(),
    createDepartment: vi.fn(),
    updateDepartment: vi.fn(),
    updateDepartmentLeader: vi.fn(),
    listMembers: vi.fn(),
    updateMember: vi.fn(),
    updateMemberManager: vi.fn(),
  },
}))

const ASelect = defineComponent({
  name: 'ASelect',
  inheritAttrs: false,
  props: ['value', 'options'],
  emits: ['update:value'],
  template: `
    <select
      v-bind="$attrs"
      :value="value ?? ''"
      @change="$emit('update:value', $event.target.value || null)"
    >
      <option value=""></option>
      <option v-for="option in options ?? []" :key="option.value" :value="option.value">
        {{ option.label }}
      </option>
    </select>
  `,
})

function applySession() {
  useSessionStore().applyAuth({
    account: { id: '1', username: 'admin', displayName: 'Admin' },
    context: {
      type: 'SYSTEM',
      account: { id: '1', username: 'admin', displayName: 'Admin' },
      systemId: '10',
      tenantId: '20',
      memberId: 'member-1',
      permissionVersion: '1',
      permissions: [
        'system.organization.manage',
        'system.member.manage',
        'system.member.identity.view',
      ],
      shells: ['SYSTEM_ADMIN'],
    },
    systems: [],
  })
}

function render() {
  return mount(SystemOrganizationView, {
    global: {
      stubs: {
        AdminMobileNotice: true,
        AdminPageHeader: { template: '<header><slot name="actions" /></header>' },
        'a-alert': {
          props: ['message'],
          template: '<div class="alert-stub">{{ message }}</div>',
        },
        'a-button': {
          emits: ['click'],
          template: '<button @click="$emit(\'click\')"><slot /></button>',
        },
        'a-form': { template: '<form><slot /></form>' },
        'a-form-item': { template: '<label><slot /></label>' },
        'a-input': {
          props: ['value'],
          emits: ['update:value'],
          template: '<input :value="value" @input="$emit(\'update:value\', $event.target.value)">',
        },
        'a-modal': {
          props: ['open'],
          emits: ['ok', 'update:open'],
          template: '<section v-if="open" class="modal-stub"><slot /><button class="modal-ok" @click="$emit(\'ok\')">保存</button></section>',
        },
        'a-select': ASelect,
        'a-select-option': { template: '<option><slot /></option>' },
        'a-tab-pane': { template: '<section><slot /></section>' },
        'a-tabs': { template: '<div><slot /></div>' },
        'a-table': {
          props: ['dataSource'],
          template: `
            <div>
              <div v-for="record in dataSource" :key="record.id">
                <slot name="bodyCell" :column="{ key: 'actions' }" :record="record" />
              </div>
            </div>
          `,
        },
        'a-tag': { template: '<span><slot /></span>' },
      },
    },
  })
}

describe('SystemOrganizationView reporting relationships', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.stubGlobal('matchMedia', vi.fn(() => ({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })))
    applySession()
    vi.mocked(systemAdminApi.listDepartments).mockResolvedValue({
      items: [{
        id: 'department-1',
        name: '研发部',
        code: 'RD',
        status: 'ACTIVE',
        leaderMemberId: 'member-1',
        memberCount: 2,
        version: '1',
      }],
      page: 1,
      size: 200,
      total: 1,
    })
    vi.mocked(systemAdminApi.listMembers).mockResolvedValue({
      items: [
        {
          id: 'member-1',
          accountId: 'account-1',
          displayName: '张三',
          status: 'ACTIVE',
          managerMemberId: 'member-2',
          departmentIds: ['department-1'],
          tenantIds: ['20'],
          roleIds: [],
          version: '3',
        },
        {
          id: 'member-2',
          accountId: 'account-2',
          displayName: '李四',
          status: 'ACTIVE',
          managerMemberId: null,
          departmentIds: ['department-1'],
          tenantIds: ['20'],
          roleIds: [],
          version: '5',
        },
      ],
      page: 1,
      size: 200,
      total: 2,
    })
    vi.mocked(systemAdminApi.updateDepartment).mockResolvedValue({
      id: 'department-1',
      name: '研发部',
      code: 'RD',
      status: 'ACTIVE',
      leaderMemberId: 'member-1',
      memberCount: 2,
      version: '2',
    })
    vi.mocked(systemAdminApi.updateDepartmentLeader).mockResolvedValue({
      id: 'department-1',
      name: '研发部',
      code: 'RD',
      status: 'ACTIVE',
      leaderMemberId: 'member-2',
      memberCount: 2,
      version: '3',
    })
    vi.mocked(systemAdminApi.updateMember).mockResolvedValue({
      id: 'member-1',
      accountId: 'account-1',
      displayName: '张三',
      status: 'ACTIVE',
      managerMemberId: 'member-2',
      departmentIds: ['department-1'],
      tenantIds: ['20'],
      roleIds: [],
      version: '4',
    })
    vi.mocked(systemAdminApi.updateMemberManager).mockResolvedValue({
      id: 'member-1',
      accountId: 'account-1',
      displayName: '张三',
      status: 'ACTIVE',
      managerMemberId: null,
      departmentIds: ['department-1'],
      tenantIds: ['20'],
      roleIds: [],
      version: '5',
    })
  })

  it('assigns a department leader with the version returned by the base update', async () => {
    const wrapper = render()
    await flushPromises()

    await wrapper.findAll('button').find(button => button.text().includes('编辑'))!.trigger('click')
    await wrapper.get('.department-leader-select').setValue('member-2')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(systemAdminApi.updateDepartmentLeader).toHaveBeenCalledWith(
      '10',
      'department-1',
      { leaderMemberId: 'member-2', version: '2' },
    )
  })

  it('clears a direct manager explicitly and keeps a conflict visible in the modal', async () => {
    vi.mocked(systemAdminApi.updateMemberManager).mockRejectedValue(
      new Error('组织关系版本冲突'),
    )
    const wrapper = render()
    await flushPromises()

    const editButtons = wrapper.findAll('button').filter(button => button.text().includes('编辑'))
    await editButtons[1]!.trigger('click')
    await wrapper.get('.member-manager-select').setValue('')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(systemAdminApi.updateMemberManager).toHaveBeenCalledWith(
      '10',
      'member-1',
      { managerMemberId: null, version: '4' },
    )
    expect(wrapper.get('.modal-stub').text()).toContain('组织关系版本冲突')
  })
})
