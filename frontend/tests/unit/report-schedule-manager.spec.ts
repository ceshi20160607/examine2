import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import ReportScheduleManager from '@/components/report/ReportScheduleManager.vue'
import { reportAdminApi } from '@/services/report'
import type { ReportSchedule } from '@/types/report'

vi.mock('@/services/report', () => ({ reportAdminApi: {
  schedules: vi.fn(), schedule: vi.fn(), createSchedule: vi.fn(), updateSchedule: vi.fn(),
  setScheduleEnabled: vi.fn(), previewSchedule: vi.fn(),
} }))

const schedule: ReportSchedule = {
  id: '40', reportId: '30', code: 'weekly_orders', name: '每周订单', enabled: true,
  timeZone: 'Asia/Shanghai', cadence: { kind: 'WEEKLY', localTime: '09:30', daysOfWeek: ['MONDAY', 'FRIDAY'] },
  recipientMemberIds: ['101', '102'], ownerMemberId: '9', nextFireAt: '2026-08-07T01:30:00Z',
  version: 3, createdAt: '2026-08-01T00:00:00Z', updatedAt: '2026-08-04T00:00:00Z',
}

function render() {
  return mount(ReportScheduleManager, {
    props: { systemId: '10', reportId: '30', reportCode: 'monthly_orders', reportActive: true },
    global: { stubs: {
      MemberPicker: {
        emits: ['update:value', 'select'],
        template: '<button class="pick-member" @click="$emit(\'update:value\', \'103\'); $emit(\'select\', { memberId: \'103\', memberCode: \'M103\', displayName: \'李四\' })">选择成员</button>',
      },
      'a-button': { props: ['disabled', 'loading'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
      'a-alert': { props: ['message'], template: '<div class="alert-stub">{{ message }}</div>' },
      'a-spin': { template: '<div><slot /></div>' }, 'a-tag': { template: '<span><slot /></span>' },
      'a-empty': { props: ['description'], template: '<div>{{ description }}</div>' },
    } },
  })
}

describe('ReportScheduleManager', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(reportAdminApi.schedules).mockResolvedValue([schedule])
    vi.mocked(reportAdminApi.schedule).mockResolvedValue(schedule)
    vi.mocked(reportAdminApi.updateSchedule).mockResolvedValue({ ...schedule, name: '每周经营订单', version: 4 })
    vi.mocked(reportAdminApi.createSchedule).mockResolvedValue({ ...schedule, id: '41', code: 'daily_orders', enabled: false, version: 1 })
    vi.mocked(reportAdminApi.setScheduleEnabled).mockResolvedValue({ ...schedule, enabled: false, version: 4 })
    vi.mocked(reportAdminApi.previewSchedule).mockResolvedValue({
      nextFireAt: '2026-08-10T01:30:00Z', localDateTime: '2026-08-10T09:30:00',
      offset: '+08:00', timeZone: 'Asia/Shanghai',
    })
  })

  it('loads nested cadence, previews next fire and updates without changing the stable code', async () => {
    const wrapper = render()
    await flushPromises()

    expect(reportAdminApi.schedules).toHaveBeenCalledWith('10', '30')
    expect((wrapper.get('.schedule-code').element as HTMLInputElement).disabled).toBe(true)
    expect((wrapper.get('.schedule-kind').element as HTMLSelectElement).value).toBe('WEEKLY')
    expect(wrapper.text()).toContain('成员 101')

    await wrapper.get('.schedule-preview-button').trigger('click')
    await flushPromises()
    expect(reportAdminApi.previewSchedule).toHaveBeenCalledWith('10', '30', {
      timeZone: 'Asia/Shanghai',
      cadence: { kind: 'WEEKLY', localTime: '09:30', daysOfWeek: ['MONDAY', 'FRIDAY'] },
    })
    expect(wrapper.text()).toContain('2026')

    await wrapper.get('.schedule-name').setValue('每周经营订单')
    await wrapper.get('.schedule-save').trigger('click')
    await flushPromises()
    expect(reportAdminApi.updateSchedule).toHaveBeenCalledWith('10', '30', '40', {
      expectedVersion: 3, name: '每周经营订单', timeZone: 'Asia/Shanghai',
      cadence: { kind: 'WEEKLY', localTime: '09:30', daysOfWeek: ['MONDAY', 'FRIDAY'] },
      recipientMemberIds: ['101', '102'],
    })
    await wrapper.get('.schedule-toggle').trigger('click')
    await flushPromises()
    expect(reportAdminApi.setScheduleEnabled).toHaveBeenCalledWith('10', '30', '40', false, 4)
  })

  it('creates a disabled daily schedule with a bounded current-tenant recipient set', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.schedule-create').trigger('click')
    await wrapper.get('.schedule-code').setValue('daily_orders')
    await wrapper.get('.schedule-name').setValue('每日订单')
    await wrapper.get('.pick-member').trigger('click')
    await wrapper.get('.schedule-recipient-add').trigger('click')
    await wrapper.get('.schedule-save').trigger('click')
    await flushPromises()

    expect(reportAdminApi.createSchedule).toHaveBeenCalledWith('10', '30', {
      code: 'daily_orders', name: '每日订单', enabled: false, timeZone: 'Asia/Shanghai',
      cadence: { kind: 'DAILY', localTime: '09:00', daysOfWeek: [] },
      recipientMemberIds: ['103'],
    })
  })

  it('enforces the shared 50-recipient boundary before mutation', async () => {
    vi.mocked(reportAdminApi.schedules).mockResolvedValueOnce([{
      ...schedule, recipientMemberIds: Array.from({ length: 50 }, (_, index) => String(index + 1)),
    }])
    const wrapper = render()
    await flushPromises()
    expect(wrapper.text()).toContain('50/50')
    expect((wrapper.get('.schedule-recipient-add').element as HTMLButtonElement).disabled).toBe(true)
  })
})
