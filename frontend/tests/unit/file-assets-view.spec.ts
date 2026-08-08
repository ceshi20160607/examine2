import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import FileAssetsView from '@/views/system/FileAssetsView.vue'
import type { FileAsset } from '@/types/file'

const routerReplace = vi.hoisted(() => vi.fn())
const route = vi.hoisted(() => ({
  params: { systemId: '10' },
  query: {} as Record<string, string>,
}))
const session = vi.hoisted(() => ({
  context: { tenantId: '30', memberId: '40' },
  hasPermission: vi.fn(() => true),
}))
const modalConfirm = vi.hoisted(() => vi.fn())
const api = vi.hoisted(() => ({
  list: vi.fn(),
  storageStatus: vi.fn(),
  upload: vi.fn(),
  get: vi.fn(),
  download: vi.fn(),
  preview: vi.fn(),
  thumbnail: vi.fn(),
  thumbnailUrl: vi.fn((systemId: string, fileId: string) => '/api/v1/systems/' + systemId + '/files/' + fileId + '/thumbnail?maxWidth=56&maxHeight=56'),
  initMultipart: vi.fn(),
  uploadMultipartPart: vi.fn(),
  completeMultipart: vi.fn(),
  abortMultipart: vi.fn(),
  addReference: vi.fn(),
  removeReference: vi.fn(),
  delete: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ replace: routerReplace }),
}))

vi.mock('@/stores/session', () => ({ useSessionStore: () => session }))
vi.mock('@/services/file', () => ({ fileApi: api }))
vi.mock('ant-design-vue', () => ({ Modal: { confirm: modalConfirm } }))

const imageAsset: FileAsset = {
  id: '101',
  originalName: 'diagram.png',
  mediaType: 'image/png',
  size: 4096,
  sha256: 'a'.repeat(64),
  uploaderMemberId: '40',
  createdAt: '2026-08-06T05:00:00Z',
  version: 1,
  references: [],
}

const textAsset: FileAsset = {
  ...imageAsset,
  id: '102',
  originalName: 'notes.txt',
  mediaType: 'text/plain',
  size: 1024,
  sha256: 'b'.repeat(64),
  references: [{
    targetType: 'MODULE_RECORD', targetId: 'record-1', createdByMemberId: '40', createdAt: '2026-08-06T05:10:00Z',
  }],
}

const stubs = {
  Copy: true,
  Download: true,
  Eye: true,
  FileImage: true,
  FileText: true,
  FileUp: true,
  HardDrive: true,
  Link2: true,
  RefreshCw: true,
  RotateCcw: true,
  Search: true,
  Trash2: true,
  Unlink: true,
  X: true,
  'a-alert': { template: '<div><slot />{{ $attrs.message }}<slot name="message" /><slot name="description" /></div>' },
  'a-button': {
    inheritAttrs: false,
    emits: ['click'],
    template: '<button v-bind="$attrs" type="button" @click="$emit(\'click\')"><slot /></button>',
  },
  'a-empty': { template: '<div class="empty-stub">{{ $attrs.description }}</div>' },
  'a-input': {
    inheritAttrs: false,
    props: ['value'],
    emits: ['update:value', 'pressEnter'],
    template: '<input v-bind="$attrs" :value="value" @input="$emit(\'update:value\', $event.target.value)" @keyup.enter="$emit(\'pressEnter\')" />',
  },
  'a-pagination': { template: '<nav class="pagination-stub" />' },
  'a-select': {
    props: ['value', 'options'],
    emits: ['update:value', 'change'],
    template: '<select :value="value" @change="$emit(\'update:value\', $event.target.value); $emit(\'change\', $event.target.value)"><option v-for="option in options" :key="option.value" :value="option.value">{{ option.label }}</option></select>',
  },
  'a-spin': { template: '<div><slot /></div>' },
  'a-tab-pane': { template: '<section><slot /></section>' },
  'a-tabs': {
    props: ['activeKey'],
    emits: ['update:activeKey'],
    template: '<div><button class="tab-overview" @click="$emit(\'update:activeKey\', \'overview\')">概览</button><button class="tab-preview" @click="$emit(\'update:activeKey\', \'preview\')">预览</button><button class="tab-references" @click="$emit(\'update:activeKey\', \'references\')">引用关系</button><slot /></div>',
  },
  'a-table': {
    props: ['columns', 'dataSource'],
    template: '<table><thead><tr><th v-for="column in columns" :key="column.key">{{ column.title }}</th></tr></thead><tbody><tr v-for="record in dataSource" :key="record.id"><td v-for="column in columns" :key="column.key"><slot name="bodyCell" :column="column" :record="record" /></td></tr></tbody></table>',
  },
  'a-tag': { template: '<span><slot /></span>' },
}

function render() {
  return mount(FileAssetsView, { global: { stubs } })
}

function responseDefaults() {
  api.list.mockResolvedValue({ items: [imageAsset, textAsset], page: 1, size: 20, total: 2, totalPages: 1 })
  api.storageStatus.mockResolvedValue({
    mode: 'LOCAL',
    location: 'C:\\secret\\file-root',
    maxSingleUploadBytes: 20 * 1024 * 1024,
    maxMultipartUploadBytes: 100 * 1024 * 1024,
    maxPartBytes: 5 * 1024 * 1024,
    available: true,
  })
  api.upload.mockResolvedValue(imageAsset)
  api.get.mockImplementation(async (_systemId: string, fileId: string) => fileId === '102' ? textAsset : imageAsset)
  api.download.mockResolvedValue({ blob: new Blob(['file']), filename: 'download.bin', mediaType: 'application/octet-stream' })
  api.preview.mockResolvedValue({ blob: new Blob(['preview'], { type: 'image/png' }), filename: 'diagram.png', mediaType: 'image/png' })
  api.initMultipart.mockResolvedValue({
    uploadId: 'upload-1', originalName: 'large.bin', mediaType: 'application/octet-stream',
    sizeBytes: 21 * 1024 * 1024, sha256: '0'.repeat(64), partSizeBytes: 5 * 1024 * 1024,
    partCount: 5, expiresAt: '2026-08-06T06:00:00Z', status: 'OPEN',
  })
  api.uploadMultipartPart.mockResolvedValue({ uploadId: 'upload-1', partNumber: 1, sizeBytes: 1, sha256: '0'.repeat(64), replay: false, uploadedPartCount: 1, partCount: 5 })
  api.completeMultipart.mockResolvedValue({ ...imageAsset, id: '103', originalName: 'large.bin', mediaType: 'application/octet-stream' })
  api.abortMultipart.mockResolvedValue({ uploadId: 'upload-1', status: 'ABORTED' })
  api.addReference.mockResolvedValue(imageAsset)
  api.removeReference.mockResolvedValue(imageAsset)
  api.delete.mockResolvedValue(imageAsset)
}

function digestibleFile(name = 'large.bin', size = 21 * 1024 * 1024) {
  const file = new File(['x'], name, { type: 'application/octet-stream' })
  const buffer = new Uint8Array([1]).buffer
  Object.defineProperty(file, 'size', { value: size })
  Object.defineProperty(file, 'arrayBuffer', { value: vi.fn(async () => buffer) })
  Object.defineProperty(file, 'slice', { value: vi.fn(() => {
    const chunk = new Blob(['x'], { type: 'application/octet-stream' })
    Object.defineProperty(chunk, 'arrayBuffer', { value: vi.fn(async () => buffer) })
    return chunk
  }) })
  return file
}

async function chooseFile(wrapper: ReturnType<typeof mount>, file: File) {
  const input = wrapper.get('.upload-command input')
  Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
  await input.trigger('change')
}

describe('FileAssetsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    route.params.systemId = '10'
    route.query = {}
    session.context = { tenantId: '30', memberId: '40' }
    session.hasPermission.mockReturnValue(true)
    responseDefaults()
    vi.stubGlobal('crypto', {
      randomUUID: vi.fn(() => 'request-key'),
      subtle: { digest: vi.fn(async () => new Uint8Array(32).buffer) },
    })
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:preview'),
      revokeObjectURL: vi.fn(),
    })
  })

  it('renders the standard searchable table and hides a storage path from the status band', async () => {
    const wrapper = render()
    await vi.waitFor(() => expect(api.list).toHaveBeenCalled())
    await flushPromises()

    expect(wrapper.text()).toContain('文件中心')
    expect(wrapper.text()).toContain('媒体类型')
    expect(wrapper.text()).toContain('上传时间')
    expect(wrapper.text()).toContain('diagram.png')
    expect(wrapper.text()).toContain('notes.txt')
    expect(wrapper.text()).toContain('路径已隐藏')
    expect(wrapper.text()).not.toContain('C:\\secret\\file-root')

    await wrapper.get('.file-name-link').trigger('click')
    expect(routerReplace).toHaveBeenCalledWith({ query: { file: '101' } })
  })

  it('shows the backend-sanitized S3 bucket prefix while keeping credentials out of the page', async () => {
    api.storageStatus.mockResolvedValue({
      mode: 'S3',
      location: 'archive-bucket/examine-files',
      maxSingleUploadBytes: 20 * 1024 * 1024,
      maxMultipartUploadBytes: 100 * 1024 * 1024,
      maxPartBytes: 5 * 1024 * 1024,
      available: true,
    })
    const wrapper = render()
    await vi.waitFor(() => expect(wrapper.text()).toContain('archive-bucket/examine-files'))

    expect(wrapper.text()).toContain('S3 兼容存储')
    expect(wrapper.text()).not.toContain('accessKey')
    expect(wrapper.text()).not.toContain('secretKey')
    expect(wrapper.text()).not.toContain('endpoint')
  })

  it('loads an image through the dedicated preview endpoint and falls back for unsupported content', async () => {
    route.query = { file: '101' }
    const image = render()
    await vi.waitFor(() => expect(api.get).toHaveBeenCalledWith('10', '101'))
    await image.get('.tab-preview').trigger('click')
    await vi.waitFor(() => expect(api.preview).toHaveBeenCalledWith('10', '101'))
    await flushPromises()
    expect(image.get('.file-preview > img').attributes('src')).toBe('blob:preview')
    image.unmount()

    vi.clearAllMocks()
    responseDefaults()
    route.query = { file: '102' }
    const unsupported = render()
    await vi.waitFor(() => expect(api.get).toHaveBeenCalledWith('10', '102'))
    await vi.waitFor(() => expect(unsupported.find('.tab-preview').exists()).toBe(true))
    await unsupported.get('.tab-preview').trigger('click')
    await flushPromises()
    expect(unsupported.get('.file-preview-fallback').text()).toContain('不支持在线预览')
    expect(api.preview).not.toHaveBeenCalled()
  })

  it('keeps files at or below 20 MiB on the existing single-upload path', async () => {
    const wrapper = render()
    await flushPromises()
    const file = digestibleFile('small.bin', 20 * 1024 * 1024)

    await chooseFile(wrapper, file)
    await vi.waitFor(() => expect(api.upload).toHaveBeenCalledWith('10', file))

    expect(api.initMultipart).not.toHaveBeenCalled()
    expect(routerReplace).toHaveBeenCalledWith({ query: { file: '101' } })
  })

  it('retries the failed 5 MiB part and completes a 21 MiB upload without restarting confirmed parts', async () => {
    api.uploadMultipartPart.mockRejectedValueOnce(new Error('temporary part failure'))
      .mockResolvedValue({ uploadId: 'upload-1', partNumber: 1, sizeBytes: 1, sha256: '0'.repeat(64), replay: false, uploadedPartCount: 1, partCount: 5 })
    const wrapper = render()
    await flushPromises()
    const file = digestibleFile()

    await chooseFile(wrapper, file)
    await vi.waitFor(() => expect(wrapper.text()).toContain('上传中断，可从失败分片重试'))
    expect(api.upload).not.toHaveBeenCalled()
    expect(api.initMultipart).toHaveBeenCalledWith('10', expect.objectContaining({
      originalName: 'large.bin', sizeBytes: 21 * 1024 * 1024, sha256: '0'.repeat(64),
    }))

    const retry = wrapper.findAll('.multipart-actions button').find(button => button.text().includes('从失败处重试'))
    expect(retry).toBeTruthy()
    await retry!.trigger('click')
    await vi.waitFor(() => expect(api.completeMultipart).toHaveBeenCalledWith('10', 'upload-1', '0'.repeat(64)))

    expect(api.uploadMultipartPart.mock.calls.map(call => call[2])).toEqual([1, 1, 2, 3, 4, 5])
    expect(wrapper.text()).toContain('分片上传完成')
    expect(routerReplace).toHaveBeenCalledWith({ query: { file: '103' } })
  })

  it('aborts the active part and cleans the server session when the user cancels', async () => {
    api.uploadMultipartPart.mockImplementation((
      _systemId: string,
      _uploadId: string,
      _partNumber: number,
      _content: Blob,
      _sha256: string,
      signal?: AbortSignal,
    ) => new Promise((_resolve, reject) => {
      signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')))
    }))
    const wrapper = render()
    await flushPromises()

    await chooseFile(wrapper, digestibleFile())
    await vi.waitFor(() => expect(api.uploadMultipartPart).toHaveBeenCalledTimes(1))
    const cancel = wrapper.findAll('.multipart-actions button').find(button => button.text().includes('取消上传'))
    expect(cancel).toBeTruthy()
    await cancel!.trigger('click')
    await vi.waitFor(() => expect(api.abortMultipart).toHaveBeenCalledWith('10', 'upload-1'))
    await vi.waitFor(() => expect(wrapper.text()).toContain('上传已取消，暂存内容已清理'))

    expect(api.completeMultipart).not.toHaveBeenCalled()
  })
})
