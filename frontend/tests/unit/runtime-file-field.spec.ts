import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fileApi } from '@/services/file'
import type { RuntimeFieldCapability } from '@/types/config'
import RuntimeFileFieldInput from '@/views/system/RuntimeFileFieldInput.vue'

vi.mock('@/services/file', () => ({ fileApi: {
  upload: vi.fn(), get: vi.fn(), download: vi.fn(), thumbnailUrl: vi.fn(() => '/thumbnail'),
} }))

const field: RuntimeFieldCapability = {
  fieldCode: 'signature', fieldName: '签名', logicalFieldId: '8', type: 'SIGNATURE', mode: 'WRITABLE',
  readable: true, writable: true, sensitiveReadable: false, sensitiveQueryable: false, masked: false,
  operators: [], sortable: false, showInList: false, showInDetail: true, options: [],
  schema: { maxFiles: 1, imageOnly: true, allowedExtensions: ['png'] },
}

function render(modelValue: unknown = []) {
  return mount(RuntimeFileFieldInput, {
    props: { field, modelValue, inputId: 'signature-input', systemId: '10' },
    global: { plugins: [createPinia()], stubs: {
      'a-button': { emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
      'a-tooltip': { template: '<span><slot /></span>' },
      Camera: true, Upload: true, Download: true, FileImage: true, Paperclip: true, Trash2: true,
    } },
  })
}

describe('RuntimeFileFieldInput', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(fileApi.upload).mockResolvedValue({ id: '91', originalName: 'signature.png', mediaType: 'image/png',
      size: 128, sha256: 'a'.repeat(64), uploaderMemberId: '20', createdAt: '2026-08-07T01:00:00Z', version: 1, references: [] })
  })

  it('uploads an image into the canonical file-id array and enforces signature cardinality', async () => {
    const wrapper = render()
    const input = wrapper.get('input[type="file"]')
    const file = new File(['signature'], 'signature.png', { type: 'image/png' })
    Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })
    await input.trigger('change')
    await flushPromises()

    expect(fileApi.upload).toHaveBeenCalledWith('10', file)
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([['91']])
    expect(wrapper.text()).toContain('已绑定 0/1')
  })

  it('reads existing scoped metadata and can remove the binding without deleting the asset', async () => {
    vi.mocked(fileApi.get).mockResolvedValue({ id: '91', originalName: 'signature.png', mediaType: 'image/png',
      size: 128, sha256: 'a'.repeat(64), uploaderMemberId: '20', createdAt: '2026-08-07T01:00:00Z', version: 1, references: [] })
    const wrapper = render(['91'])
    await flushPromises()
    expect(wrapper.text()).toContain('signature.png')
    await wrapper.findAll('button').at(-1)!.trigger('click')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([[]])
    expect(fileApi.upload).not.toHaveBeenCalled()
  })

  it('offers a rear-camera capture input for image fields and uploads the captured photo', async () => {
    const wrapper = render()
    const input = wrapper.get('input[capture="environment"]')
    const photo = new File(['photo'], 'camera.jpg', { type: 'image/jpeg' })
    Object.defineProperty(input.element, 'files', { configurable: true, value: [photo] })
    await input.trigger('change')
    await flushPromises()

    expect(input.attributes('accept')).toBe('image/*')
    expect(fileApi.upload).toHaveBeenCalledWith('10', photo)
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([['91']])
  })
})
