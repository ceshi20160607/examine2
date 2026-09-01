<script setup lang="ts">
import { DeleteOutlined, DownloadOutlined, EyeOutlined, LinkOutlined, ReloadOutlined, UploadOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { api, ApiError } from '../api'
import { authorizedBlob, fileScanLabel, referenceToAccount, saveBlob, uploadControlledFile } from '../file'
import { systemContext, systemTokens } from '../session'
import type { ControlledFileView } from '../types'

const files = ref<ControlledFileView[]>([])
const loading = ref(false)
const uploading = ref(false)
const uploadProgress = ref(0)
const error = ref('')
const token = computed(() => systemTokens.value?.accessToken || '')
const referenceOpen = ref(false)
const selected = ref<ControlledFileView>()
const referenceForm = reactive({ ownerType: 'ACCOUNT', ownerId: '', fieldCode: 'managed_files', referenceType: 'DOCUMENT' })

async function loadFiles() {
  loading.value = true
  error.value = ''
  try { files.value = await api<ControlledFileView[]>('/api/files', {}, token.value) }
  catch (reason) { error.value = reason instanceof ApiError ? reason.message : '文件列表加载失败' }
  finally { loading.value = false }
}

async function selectFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploading.value = true
  uploadProgress.value = 0
  try {
    let uploaded = await uploadControlledFile(file, token.value, progress => { uploadProgress.value = progress })
    if (uploaded.scanStatus === 'CLEAN' && systemContext.value?.accountId) {
      uploaded = await referenceToAccount(uploaded.id, systemContext.value.accountId, token.value)
    }
    message.success(uploaded.scanStatus === 'CLEAN' ? '文件已通过扫描并保存到当前账号' : '文件未通过扫描，已安全隔离')
    await loadFiles()
  } catch (reason) { message.error(reason instanceof ApiError ? reason.message : '文件上传失败') }
  finally { uploading.value = false; input.value = '' }
}

function openReference(file: ControlledFileView) {
  selected.value = file
  referenceForm.ownerType = 'ACCOUNT'
  referenceForm.ownerId = String(systemContext.value?.accountId || '')
  referenceForm.fieldCode = 'managed_files'
  referenceForm.referenceType = 'DOCUMENT'
  referenceOpen.value = true
}

async function addReference() {
  if (!selected.value) return
  try {
    await api(`/api/files/${selected.value.id}/references`, {
      method: 'POST', body: JSON.stringify(referenceForm),
    }, token.value)
    message.success('业务引用已保存')
    referenceOpen.value = false
    await loadFiles()
  } catch (reason) { message.error(reason instanceof ApiError ? reason.message : '引用保存失败') }
}

async function removeReference(file: ControlledFileView, referenceId: number) {
  try {
    await api(`/api/files/${file.id}/references/${referenceId}`, { method: 'DELETE' }, token.value)
    message.success('引用已解除')
    await loadFiles()
  } catch (reason) { message.error(reason instanceof ApiError ? reason.message : '引用解除失败') }
}

async function preview(file: ControlledFileView) {
  try {
    const blob = await authorizedBlob(`/api/files/${file.id}/preview`, token.value)
    window.open(URL.createObjectURL(blob), '_blank', 'noopener,noreferrer')
  } catch (reason) { message.error(reason instanceof ApiError ? reason.message : '文件预览失败') }
}

async function download(file: ControlledFileView) {
  try { saveBlob(await authorizedBlob(`/api/files/${file.id}/download`, token.value), file.originalName) }
  catch (reason) { message.error(reason instanceof ApiError ? reason.message : '文件下载失败') }
}

async function remove(file: ControlledFileView) {
  try {
    await api(`/api/files/${file.id}`, { method: 'DELETE' }, token.value)
    message.success('文件已删除')
    await loadFiles()
  } catch (reason) { message.error(reason instanceof ApiError ? reason.message : '文件删除失败') }
}

function size(value: number) {
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

onMounted(loadFiles)
</script>

<template>
  <div class="file-workspace">
    <div class="page-heading">
      <div><p class="eyebrow">受控文件</p><h1>文件与业务附件</h1><p>上传内容先进入安全扫描，只有通过扫描且保存引用的文件才能进入业务链路。</p></div>
      <div class="heading-actions">
        <a-button :loading="loading" @click="loadFiles"><ReloadOutlined />刷新</a-button>
        <label class="ant-btn ant-btn-primary file-upload-button" for="managed-file-input"><UploadOutlined />选择文件</label>
        <input id="managed-file-input" class="visually-hidden" type="file" @change="selectFile" />
      </div>
    </div>
    <a-progress v-if="uploading" :percent="uploadProgress" status="active" class="file-upload-progress" />
    <a-alert v-if="error" type="error" show-icon :message="error" class="section-alert" />
    <div class="file-policy-strip">
      <span><strong>上传</strong> 一次性凭证 + SHA-256</span><span><strong>扫描</strong> 未通过自动隔离</span>
      <span><strong>访问</strong> 每次重新校验引用目标</span><span><strong>删除</strong> 有引用时禁止</span>
    </div>
    <a-table :data-source="files" :loading="loading" row-key="id" :pagination="false" class="file-table">
      <a-table-column title="文件"><template #default="{ record }"><strong>{{ record.originalName }}</strong><small>{{ size(record.sizeBytes) }} · {{ record.contentType }}</small><code>{{ record.sha256.slice(0, 16) }}…</code></template></a-table-column>
      <a-table-column title="安全状态" width="150"><template #default="{ record }"><a-tag :color="record.scanStatus === 'CLEAN' ? 'green' : record.scanStatus === 'BLOCKED' ? 'red' : 'blue'">{{ fileScanLabel(record.scanStatus) }}</a-tag><small>{{ record.previewStatus === 'AVAILABLE' ? '可在线预览' : '仅允许下载' }}</small></template></a-table-column>
      <a-table-column title="业务引用" width="260"><template #default="{ record }"><div v-if="record.references.length" class="file-reference-list"><span v-for="item in record.references" :key="item.id"><LinkOutlined />{{ item.ownerType }}:{{ item.ownerId }}<button type="button" @click="removeReference(record, item.id)">解除</button></span></div><a-tag v-else>尚未引用</a-tag></template></a-table-column>
      <a-table-column title="上传时间" data-index="createdAt" width="190" />
      <a-table-column title="操作" width="310"><template #default="{ record }"><a-button v-if="record.scanStatus === 'CLEAN'" type="link" size="small" @click="openReference(record)"><LinkOutlined />引用</a-button><a-button v-if="record.previewStatus === 'AVAILABLE'" type="link" size="small" @click="preview(record)"><EyeOutlined />预览</a-button><a-button v-if="record.scanStatus === 'CLEAN'" type="link" size="small" @click="download(record)"><DownloadOutlined />下载</a-button><a-button danger type="link" size="small" @click="remove(record)"><DeleteOutlined />删除</a-button></template></a-table-column>
    </a-table>
    <section v-if="files.length" class="mobile-file-list" aria-label="文件附件卡片列表">
      <article v-for="file in files" :key="file.id">
        <header>
          <span><strong>{{ file.originalName }}</strong><small>{{ size(file.sizeBytes) }} · {{ file.contentType }}</small></span>
          <a-tag :color="file.scanStatus === 'CLEAN' ? 'green' : file.scanStatus === 'BLOCKED' ? 'red' : 'blue'">{{ fileScanLabel(file.scanStatus) }}</a-tag>
        </header>
        <code>{{ file.sha256.slice(0, 16) }}…</code>
        <dl>
          <div><dt>预览状态</dt><dd>{{ file.previewStatus === 'AVAILABLE' ? '可在线预览' : '仅允许下载' }}</dd></div>
          <div><dt>上传时间</dt><dd>{{ file.createdAt }}</dd></div>
          <div><dt>业务引用</dt><dd><template v-if="file.references.length"><span v-for="item in file.references" :key="item.id" class="mobile-file-reference"><LinkOutlined />{{ item.ownerType }}:{{ item.ownerId }}<button type="button" @click="removeReference(file, item.id)">解除</button></span></template><span v-else>尚未引用</span></dd></div>
        </dl>
        <footer>
          <a-button v-if="file.scanStatus === 'CLEAN'" type="link" @click="openReference(file)"><LinkOutlined />引用</a-button>
          <a-button v-if="file.previewStatus === 'AVAILABLE'" type="link" @click="preview(file)"><EyeOutlined />预览</a-button>
          <a-button v-if="file.scanStatus === 'CLEAN'" type="link" @click="download(file)"><DownloadOutlined />下载</a-button>
          <a-button danger type="link" @click="remove(file)"><DeleteOutlined />删除</a-button>
        </footer>
      </article>
    </section>
    <a-empty v-if="!files.length && !loading" description="尚未上传文件" />

    <a-modal v-model:open="referenceOpen" title="保存业务引用" ok-text="保存引用" @ok="addReference">
      <a-alert type="info" show-icon message="引用目标会在预览、下载和删除时重新校验权限" class="section-alert" />
      <a-form layout="vertical"><a-form-item label="目标类型"><a-select v-model:value="referenceForm.ownerType" :options="[{ value: 'ACCOUNT', label: '当前账号' }, { value: 'BUSINESS_RECORD', label: '业务记录' }, { value: 'FLOW_INSTANCE', label: '流程实例' }, { value: 'WORK_TASK', label: '工作任务' }]" /></a-form-item><a-form-item label="目标标识" required><a-input v-model:value="referenceForm.ownerId" placeholder="业务记录可使用模块编码:记录编号" /></a-form-item><a-form-item label="字段编码"><a-input v-model:value="referenceForm.fieldCode" /></a-form-item><a-form-item label="引用用途"><a-select v-model:value="referenceForm.referenceType" :options="[{ value: 'DOCUMENT', label: '文档' }, { value: 'ATTACHMENT', label: '附件' }, { value: 'IMAGE', label: '图片' }, { value: 'RESULT', label: '结果文件' }]" /></a-form-item></a-form>
    </a-modal>
  </div>
</template>
