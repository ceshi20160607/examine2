<script setup lang="ts">
import { DeleteOutlined, DownloadOutlined, EyeOutlined, ReloadOutlined, UploadOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { api, ApiError } from '../api'
import { authorizedBlob, fileScanLabel, saveBlob, uploadBusinessAttachment } from '../file'
import { systemContext, systemTokens } from '../session'
import ProductPageHeader from '../components/ProductPageHeader.vue'
import ProductPage from '../components/ProductPage.vue'
import ProductState from '../components/ProductState.vue'
import { productDateTime } from '../presentation'
import type { BusinessAttachmentView, FileStorageBackendView, FileStoragePolicyView } from '../types'

const attachments = ref<BusinessAttachmentView[]>([])
const loading = ref(false)
const uploading = ref(false)
const error = ref('')
const policy = ref<FileStoragePolicyView>()
const backends = ref<FileStorageBackendView[]>([])
const savingStorage = ref(false)
const token = computed(() => systemTokens.value?.accessToken || '')
const canManageStorage = computed(() => Boolean(systemContext.value?.permissions.some(permission =>
  (permission.resourceType === 'FILE' || permission.resourceType === '*')
  && (permission.resourceCode === 'OBJECT' || permission.resourceCode === '*')
  && (permission.actionCode === 'MANAGE' || permission.actionCode === '*'))))
const objectStorage = computed(() => backends.value.find(item => item.code === 'OBJECT_DEFAULT'))
const storageForm = reactive({
  name: 'S3 兼容对象存储', endpoint: '', bucketName: '', region: 'us-east-1',
  accessKeyId: '', secretAccessKey: '', pathStyle: true,
})

function describeError(reason: unknown, fallback: string) {
  return reason instanceof ApiError ? `${reason.message}${reason.traceId ? `（追踪号 ${reason.traceId}）` : ''}` : fallback
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [items, currentPolicy] = await Promise.all([
      api<BusinessAttachmentView[]>('/api/business-attachments/personal', {}, token.value),
      api<FileStoragePolicyView>('/api/files/policy', {}, token.value),
    ])
    attachments.value = items
    policy.value = currentPolicy
    if (canManageStorage.value) {
      backends.value = await api<FileStorageBackendView[]>('/api/files/storage', {}, token.value)
      const object = backends.value.find(item => item.code === 'OBJECT_DEFAULT')
      if (object) Object.assign(storageForm, {
        name: object.name, endpoint: object.endpoint || '', bucketName: object.bucketName,
        region: object.region || 'us-east-1', pathStyle: object.pathStyle,
        accessKeyId: '', secretAccessKey: '',
      })
    }
  }
  catch (reason) { error.value = describeError(reason, '个人文档加载失败') }
  finally { loading.value = false }
}

async function selectFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (policy.value && file.size > policy.value.maximumFileSizeBytes) {
    message.error(`单个文件不能超过 ${size(policy.value.maximumFileSizeBytes)}`)
    input.value = ''
    return
  }
  uploading.value = true
  try {
    const uploaded = await uploadBusinessAttachment(file, '/api/business-attachments/personal', token.value)
    if (uploaded.scanStatus === 'CLEAN') message.success('文档已通过安全扫描并保存')
    else message.warning('文件未通过安全扫描，未进入文档列表')
    await load()
  } catch (reason) { message.error(describeError(reason, '文档上传失败')) }
  finally { uploading.value = false; input.value = '' }
}

async function saveObjectStorage() {
  savingStorage.value = true
  try {
    await api<FileStorageBackendView>('/api/files/storage/object', {
      method: 'PUT', body: JSON.stringify({ ...storageForm, activate: true }),
    }, token.value)
    message.success('对象存储已通过真实读写检查并启用')
    await load()
  } catch (reason) { message.error(describeError(reason, '对象存储配置失败')) }
  finally { savingStorage.value = false }
}

async function deactivateObjectStorage() {
  savingStorage.value = true
  try {
    await api<FileStorageBackendView>('/api/files/storage/object/deactivate', { method: 'POST' }, token.value)
    message.success('新上传已切回本地存储，已有对象存储文件仍可读取')
    await load()
  } catch (reason) { message.error(describeError(reason, '对象存储停用失败')) }
  finally { savingStorage.value = false }
}

async function preview(item: BusinessAttachmentView) {
  if (!item.attachmentId) return
  try {
    const blob = await authorizedBlob(`/api/business-attachments/${item.attachmentId}/preview`, token.value)
    window.open(URL.createObjectURL(blob), '_blank', 'noopener,noreferrer')
  } catch (reason) { message.warning(`${describeError(reason, '文档预览失败')}，请使用下载查看`) }
}

async function download(item: BusinessAttachmentView) {
  if (!item.attachmentId) return
  try { saveBlob(await authorizedBlob(`/api/business-attachments/${item.attachmentId}/download`, token.value), item.originalName) }
  catch (reason) { message.error(describeError(reason, '文档下载失败')) }
}

async function remove(item: BusinessAttachmentView) {
  if (!item.attachmentId) return
  try {
    await api(`/api/business-attachments/${item.attachmentId}`, { method: 'DELETE' }, token.value)
    message.success('文档已移除')
    await load()
  } catch (reason) { message.error(describeError(reason, '文档移除失败')) }
}

function size(value: number) {
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

onMounted(load)
</script>

<template>
  <ProductPage class="file-workspace">
    <ProductPageHeader kicker="当前系统" title="我的文档" description="集中查看你保存的文档；业务记录、流程和任务的附件在对应对象内管理。">
      <template #actions><a-button :loading="loading" @click="load"><ReloadOutlined />刷新</a-button></template>
      <template #primary><label class="ant-btn ant-btn-primary file-upload-button" for="personal-document-input"><UploadOutlined />上传文档</label><input id="personal-document-input" class="visually-hidden" type="file" @change="selectFile" /></template>
    </ProductPageHeader>
    <a-alert v-if="uploading" type="info" show-icon message="正在扫描并保存文档…" class="section-alert" />
    <section class="panel-card file-context-guide">
      <div><strong>个人文档</strong><span>在这里上传和管理</span></div><div><strong>业务附件</strong><span>回到记录、流程或任务内上传</span></div><div><strong>{{ policy?.backendName || '托管存储' }}</strong><span>单文件 {{ policy ? size(policy.maximumFileSizeBytes) : '50 MB' }}<template v-if="policy"> · 剩余 {{ size(policy.availableBytes) }}</template></span></div>
    </section>
    <ProductState v-if="error" state="error" title="文档加载失败" :description="error">
      <template #actions><a-button type="primary" @click="load">重新加载</a-button></template>
    </ProductState>
    <a-spin v-else-if="attachments.length" :spinning="loading">
      <div class="file-card-grid">
        <article v-for="item in attachments" :key="item.attachmentId" class="panel-card file-business-card">
          <header><span><strong>{{ item.originalName }}</strong><small>{{ size(item.sizeBytes) }} · {{ productDateTime(item.createdAt) }}</small></span><a-tag :color="item.scanStatus === 'CLEAN' ? 'green' : 'red'">{{ fileScanLabel(item.scanStatus) }}</a-tag></header>
          <p>{{ item.contentType }} · {{ item.previewable ? '可在线预览' : '可下载查看' }}</p>
          <footer><a-button v-if="item.previewable" @click="preview(item)"><EyeOutlined />预览</a-button><a-button v-if="item.downloadable" @click="download(item)"><DownloadOutlined />下载</a-button><a-button v-if="item.removable" danger @click="remove(item)"><DeleteOutlined />移除</a-button></footer>
        </article>
      </div>
    </a-spin>
    <ProductState v-else-if="loading" state="loading" />
    <ProductState v-else state="empty" title="还没有个人文档" description="可上传个人文档；业务附件请在对应记录、流程或任务中添加。">
      <template #actions><label class="ant-btn ant-btn-primary file-upload-button" for="personal-document-input">上传第一份文档</label></template>
    </ProductState>
    <section v-if="canManageStorage" class="panel-card storage-settings">
      <header>
        <span><strong>文件存储设置</strong><small>默认使用本地托管存储；配置 S3 兼容服务后，新文件写入对象存储，已有文件继续按原位置读取。</small></span>
        <a-tag :color="objectStorage?.status === 'ACTIVE' ? 'green' : 'default'">{{ objectStorage?.status === 'ACTIVE' ? '对象存储已启用' : '当前使用本地存储' }}</a-tag>
      </header>
      <a-form layout="vertical" class="storage-form">
        <a-form-item label="配置名称" required><a-input v-model:value="storageForm.name" placeholder="例如：生产文件存储" /></a-form-item>
        <a-form-item label="服务地址" required><a-input v-model:value="storageForm.endpoint" placeholder="https://s3.example.com" /></a-form-item>
        <a-form-item label="存储桶" required><a-input v-model:value="storageForm.bucketName" placeholder="business-files" /></a-form-item>
        <a-form-item label="区域" required><a-input v-model:value="storageForm.region" placeholder="cn-east-1" /></a-form-item>
        <a-form-item label="访问密钥 ID" :help="objectStorage?.credentialConfigured ? '留空则继续使用已保存密钥' : '首次配置必须填写'">
          <a-input v-model:value="storageForm.accessKeyId" autocomplete="off" />
        </a-form-item>
        <a-form-item label="访问密钥" :help="objectStorage?.credentialConfigured ? '留空则继续使用已保存密钥' : '首次配置必须填写'">
          <a-input-password v-model:value="storageForm.secretAccessKey" autocomplete="new-password" />
        </a-form-item>
        <a-form-item label="兼容模式"><a-switch v-model:checked="storageForm.pathStyle" /><span class="storage-switch-label">路径样式访问（MinIO 等服务通常需要）</span></a-form-item>
      </a-form>
      <footer>
        <a-button type="primary" :loading="savingStorage" @click="saveObjectStorage">保存、测试并启用</a-button>
        <a-popconfirm v-if="objectStorage?.status === 'ACTIVE'" title="停用后新文件将切回本地存储，是否继续？" @confirm="deactivateObjectStorage">
          <a-button :loading="savingStorage">停用对象存储</a-button>
        </a-popconfirm>
      </footer>
    </section>
  </ProductPage>
</template>

<style scoped>
.storage-settings { display:grid; gap:16px; margin-top:16px; }
.storage-settings > header { display:flex; align-items:flex-start; justify-content:space-between; gap:16px; }
.storage-settings > header span { display:grid; gap:4px; }
.storage-settings > header small { color:var(--app-text-secondary,#666); }
.storage-form { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:0 16px; }
.storage-settings > footer { display:flex; gap:8px; }
.storage-switch-label { margin-left:8px; color:var(--app-text-secondary,#666); }
@media (max-width:900px) { .storage-form { grid-template-columns:1fr; } }
</style>
