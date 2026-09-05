<script setup lang="ts">
import { DeleteOutlined, DownloadOutlined, EyeOutlined, ReloadOutlined, UploadOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { computed, onMounted, ref } from 'vue'
import { api, ApiError } from '../api'
import { authorizedBlob, fileScanLabel, saveBlob, uploadBusinessAttachment } from '../file'
import { systemTokens } from '../session'
import ProductPageHeader from '../components/ProductPageHeader.vue'
import ProductPage from '../components/ProductPage.vue'
import ProductState from '../components/ProductState.vue'
import { productDateTime } from '../presentation'
import type { BusinessAttachmentView } from '../types'

const attachments = ref<BusinessAttachmentView[]>([])
const loading = ref(false)
const uploading = ref(false)
const error = ref('')
const token = computed(() => systemTokens.value?.accessToken || '')

function describeError(reason: unknown, fallback: string) {
  return reason instanceof ApiError ? `${reason.message}${reason.traceId ? `（追踪号 ${reason.traceId}）` : ''}` : fallback
}

async function load() {
  loading.value = true
  error.value = ''
  try { attachments.value = await api<BusinessAttachmentView[]>('/api/business-attachments/personal', {}, token.value) }
  catch (reason) { error.value = describeError(reason, '个人文档加载失败') }
  finally { loading.value = false }
}

async function selectFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploading.value = true
  try {
    const uploaded = await uploadBusinessAttachment(file, '/api/business-attachments/personal', token.value)
    if (uploaded.scanStatus === 'CLEAN') message.success('文档已通过安全扫描并保存')
    else message.warning('文件未通过安全扫描，未进入文档列表')
    await load()
  } catch (reason) { message.error(describeError(reason, '文档上传失败')) }
  finally { uploading.value = false; input.value = '' }
}

async function preview(item: BusinessAttachmentView) {
  if (!item.attachmentId) return
  try {
    const blob = await authorizedBlob(`/api/business-attachments/${item.attachmentId}/preview`, token.value)
    window.open(URL.createObjectURL(blob), '_blank', 'noopener,noreferrer')
  } catch (reason) { message.error(describeError(reason, '文档预览失败')) }
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
      <div><strong>个人文档</strong><span>在这里上传和管理</span></div><div><strong>业务附件</strong><span>回到记录、流程或任务内上传</span></div><div><strong>访问控制</strong><span>每次预览和下载都会复核对象权限</span></div>
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
  </ProductPage>
</template>
