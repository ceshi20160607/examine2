import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fileApi } from '@/services/file'
import { useSessionStore } from '@/stores/session'
import RecordFilePanel from '@/views/system/RecordFilePanel.vue'

vi.mock('@/services/file', () => ({
  fileApi: {
    recordFiles: vi.fn(),
    uploadRecordFile: vi.fn(),
    downloadRecordFile: vi.fn(),
    downloadRecordFileBundle: vi.fn(),
    detachRecordFile: vi.fn(),
  },
}))

function applySession(permissions: string[]) {
  useSessionStore().applyAuth({
    account: { id: '1', username: 'member', displayName: 'Member' },
    context: {
      type: 'SYSTEM',
      account: { id: '1', username: 'member', displayName: 'Member' },
      systemId: '10',
      tenantId: '20',
      memberId: '100',
      permissionVersion: '1',
      permissions,
      shells: ['SYSTEM_RUNTIME'],
    },
    systems: [],
  })
}

function render() {
  return mount(RecordFilePanel, {
    props: { systemId: '10', moduleCode: 'work_order', recordId: '30' },
    global: {
      stubs: {
        'a-alert': { props: ['message', 'description'], template: '<div>{{ message }} {{ description }}</div>' },
        'a-spin': { template: '<div><slot /></div>' },
        'a-empty': { props: ['description'], template: '<div>{{ description }}</div>' },
        'a-button': { template: '<button><slot /></button>' },
        'a-pagination': { template: '<div />' },
      },
    },
  })
}

describe('RecordFilePanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.unstubAllGlobals()
  })

  it('fails closed without file read permission', async () => {
    applySession(['system.runtime.access', 'module.work_order.view'])
    const wrapper = render()
    await flushPromises()

    expect(wrapper.text()).toContain('无权查看记录附件')
    expect(fileApi.recordFiles).not.toHaveBeenCalled()
  })

  it('loads the scoped record file page and exposes upload for permitted members', async () => {
    applySession([
      'system.runtime.access',
      'module.work_order.view',
      'file.read',
      'file.create',
      'file.reference',
    ])
    vi.mocked(fileApi.recordFiles).mockResolvedValue({
      items: [{
        fileId: '40',
        originalName: 'design.pdf',
        mediaType: 'application/pdf',
        sizeBytes: 2048,
        sha256: 'abc',
        uploaderMemberId: '100',
        createdAt: '2026-07-27T00:00:00Z',
        referencedByMemberId: '100',
        referencedAt: '2026-07-27T01:00:00Z',
        downloadUrl: '/content',
      }],
      page: 1,
      size: 20,
      total: 1,
    })

    const wrapper = render()
    await flushPromises()

    expect(fileApi.recordFiles).toHaveBeenCalledWith('10', 'work_order', '30', 1)
    expect(wrapper.text()).toContain('design.pdf')
    expect(wrapper.text()).toContain('2.0 KiB')
    expect(wrapper.text()).toContain('上传')
    expect(wrapper.text()).toContain('解除关联')
  })

  it('downloads the verified record attachment bundle', async () => {
    applySession(['system.runtime.access', 'module.work_order.view', 'file.read'])
    vi.mocked(fileApi.recordFiles).mockResolvedValue({
      items: [],
      page: 1,
      size: 20,
      total: 0,
    })
    vi.mocked(fileApi.downloadRecordFileBundle).mockResolvedValue({
      blob: new Blob(['zip'], { type: 'application/zip' }),
      filename: 'record-30-files.zip',
      mediaType: 'application/zip',
    })
    const createObjectURL = vi.fn(() => 'blob:record-bundle')
    const revokeObjectURL = vi.fn()
    vi.stubGlobal('URL', { createObjectURL, revokeObjectURL })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)

    const wrapper = render()
    await flushPromises()
    const button = wrapper.findAll('button').find((item) => item.text().includes('下载附件包'))
    expect(button).toBeTruthy()
    await button!.trigger('click')
    await flushPromises()

    expect(fileApi.downloadRecordFileBundle).toHaveBeenCalledWith('10', 'work_order', '30')
    expect(createObjectURL).toHaveBeenCalledOnce()
    expect(click).toHaveBeenCalledOnce()
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:record-bundle')
  })
})
