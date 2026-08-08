import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { workApi } from '@/services/work'
import { useSessionStore } from '@/stores/session'
import type {
  WorkDailyReport,
  WorkDailyReportSummary,
} from '@/types/work'
import WorkDailyReports from '@/views/system/WorkDailyReports.vue'

vi.mock('@/services/work', () => ({
  workApi: {
    listReports: vi.fn(),
    createReport: vi.fn(),
    report: vi.fn(),
    updateReport: vi.fn(),
    submitReport: vi.fn(),
    reopenReport: vi.fn(),
    reportSummary: vi.fn(),
  },
}))

const draft: WorkDailyReport = {
  id: '701',
  systemId: '10',
  tenantId: '20',
  authorMemberId: '200',
  workDate: '2026-08-05',
  completedWork: 'Completed initial work',
  plannedWork: 'Plan next work',
  blockers: null,
  status: 'DRAFT',
  createdAt: '2026-08-05T01:00:00Z',
  updatedAt: '2026-08-05T01:00:00Z',
  submittedAt: null,
  version: 1,
}

const summary: WorkDailyReportSummary = {
  memberId: '200',
  dateFrom: '2026-07-30',
  dateTo: '2026-08-05',
  submittedCount: 2,
  draftCount: 1,
  missingCount: 4,
  days: [
    { workDate: '2026-07-30', state: 'MISSING' },
    { workDate: '2026-07-31', state: 'SUBMITTED', reportId: '699', version: 2 },
    { workDate: '2026-08-01', state: 'MISSING' },
    { workDate: '2026-08-02', state: 'MISSING' },
    { workDate: '2026-08-03', state: 'SUBMITTED', reportId: '700', version: 3 },
    { workDate: '2026-08-04', state: 'MISSING' },
    { workDate: '2026-08-05', state: 'DRAFT', reportId: '701', version: 1 },
  ],
}

function applySession(permissions: string[]) {
  useSessionStore().applyAuth({
    account: { id: '1', username: 'member', displayName: 'Member' },
    context: {
      type: 'SYSTEM',
      account: { id: '1', username: 'member', displayName: 'Member' },
      systemId: '10',
      tenantId: '20',
      memberId: '200',
      permissionVersion: '1',
      permissions,
      shells: ['SYSTEM_RUNTIME'],
    },
    systems: [],
  })
}

function render() {
  return mount(WorkDailyReports, {
    props: { systemId: '10', tenantId: '20' },
    global: {
      stubs: {
        MemberPicker: {
          props: ['value'],
          emits: ['update:value'],
          template: '<input class="member-picker" :value="value" @input="$emit(\'update:value\', $event.target.value)" />',
        },
        'a-alert': {
          props: ['message'],
          template: '<div>{{ message }}<slot /></div>',
        },
        'a-spin': { template: '<div><slot /></div>' },
        'a-empty': {
          props: ['description'],
          template: '<div>{{ description }}</div>',
        },
        'a-tag': { template: '<span><slot /></span>' },
        'a-button': {
          props: ['disabled'],
          emits: ['click'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
        },
        'a-pagination': { template: '<div />' },
      },
    },
  })
}

describe('WorkDailyReports', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(workApi.listReports).mockResolvedValue({
      items: [], page: 1, size: 20, total: 0,
    })
    vi.mocked(workApi.reportSummary).mockResolvedValue(summary)
  })

  it('creates, revises and submits one author report while rendering seven-day states', async () => {
    applySession(['work.report.access', 'work.report.create'])
    let stored: WorkDailyReport | null = null
    vi.mocked(workApi.listReports).mockImplementation(async () => ({
      items: stored ? [stored] : [],
      page: 1,
      size: 20,
      total: stored ? 1 : 0,
    }))
    vi.mocked(workApi.createReport).mockImplementation(async (_systemId, input) => {
      stored = { ...draft, ...input }
      return stored
    })
    vi.mocked(workApi.updateReport).mockImplementation(async (_systemId, _id, input) => {
      stored = { ...(stored ?? draft), ...input, version: 2 }
      return stored
    })
    vi.mocked(workApi.submitReport).mockImplementation(async () => {
      stored = {
        ...(stored ?? draft),
        status: 'SUBMITTED',
        submittedAt: '2026-08-05T03:00:00Z',
        version: 3,
      }
      return stored
    })
    const wrapper = render()
    await flushPromises()

    expect(wrapper.findAll('.work-report-summary-day')).toHaveLength(7)
    expect(wrapper.find('[data-state="MISSING"]').exists()).toBe(true)
    const workDate = (wrapper.get('.work-report-work-date').element as HTMLInputElement).value
    await wrapper.get('.work-report-completed').setValue('Completed initial work')
    await wrapper.get('.work-report-planned').setValue('Plan next work')
    await wrapper.get('.work-report-blockers').setValue('')
    await wrapper.get('.work-report-save').trigger('click')
    await flushPromises()

    expect(workApi.createReport).toHaveBeenCalledWith('10', {
      workDate,
      completedWork: 'Completed initial work',
      plannedWork: 'Plan next work',
      blockers: null,
    })
    await wrapper.get('.work-report-completed').setValue('Completed revised work')
    await wrapper.get('.work-report-blockers').setValue('Waiting for approval')
    await wrapper.get('.work-report-save').trigger('click')
    await flushPromises()
    expect(workApi.updateReport).toHaveBeenCalledWith('10', '701', {
      completedWork: 'Completed revised work',
      plannedWork: 'Plan next work',
      blockers: 'Waiting for approval',
      version: 1,
    })

    await wrapper.get('.work-report-submit').trigger('click')
    await flushPromises()
    expect(workApi.submitReport).toHaveBeenCalledWith('10', '701', { version: 2 })
    expect(wrapper.get('.work-report-immutable').text()).toContain('内容不可修改')
    expect(wrapper.find('.work-report-save').exists()).toBe(false)
  })

  it('lets managers filter a member, read exact detail and reopen a submitted report', async () => {
    applySession(['work.report.access', 'work.report.manage'])
    const submitted: WorkDailyReport = {
      ...draft,
      authorMemberId: '300',
      status: 'SUBMITTED',
      submittedAt: '2026-08-05T02:00:00Z',
      version: 5,
    }
    vi.mocked(workApi.listReports).mockResolvedValue({
      items: [submitted], page: 1, size: 20, total: 1,
    })
    vi.mocked(workApi.report).mockResolvedValue(submitted)
    vi.mocked(workApi.reopenReport).mockResolvedValue({
      ...submitted, status: 'DRAFT', submittedAt: null, version: 6,
    })
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.work-report-scope').setValue('ALL')
    await wrapper.get('.member-picker').setValue('300')
    await flushPromises()
    expect(vi.mocked(workApi.listReports).mock.calls.at(-1)?.[1]).toEqual({
      scope: 'ALL',
      memberId: '300',
      dateFrom: expect.any(String),
      dateTo: expect.any(String),
      status: 'ALL',
      page: 1,
      size: 20,
    })
    expect(vi.mocked(workApi.reportSummary).mock.calls.at(-1)?.slice(0, 3))
      .toEqual(['10', expect.any(String), '300'])

    await wrapper.get('.work-report-select').trigger('click')
    await flushPromises()
    expect(workApi.report).toHaveBeenCalledWith('10', '701')
    expect(wrapper.get('.work-report-completed').element)
      .toHaveProperty('value', 'Completed initial work')
    await wrapper.get('.work-report-reopen').trigger('click')
    await flushPromises()
    expect(workApi.reopenReport).toHaveBeenCalledWith('10', '701', { version: 5 })
  })

  it('shows denied and stale mutation states explicitly', async () => {
    applySession([])
    const denied = render()
    await flushPromises()
    expect(denied.get('.work-report-denied').text()).toContain('没有日报访问权限')
    expect(workApi.listReports).not.toHaveBeenCalled()
    denied.unmount()

    setActivePinia(createPinia())
    applySession(['work.report.access', 'work.report.manage'])
    const submitted = { ...draft, status: 'SUBMITTED' as const, version: 5 }
    vi.mocked(workApi.listReports).mockResolvedValue({
      items: [submitted], page: 1, size: 20, total: 1,
    })
    vi.mocked(workApi.report).mockResolvedValue(submitted)
    vi.mocked(workApi.reopenReport).mockRejectedValue(new Error('stale report version'))
    const stale = render()
    await flushPromises()
    await stale.get('.work-report-select').trigger('click')
    await flushPromises()
    await stale.get('.work-report-reopen').trigger('click')
    await flushPromises()
    expect(stale.get('.work-report-error').text()).toContain('stale report version')
  })
})
