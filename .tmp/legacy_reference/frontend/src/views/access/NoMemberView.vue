<script setup lang="ts">
import { message } from 'ant-design-vue'
import { Ban, Clock3, RefreshCw, Send, ShieldQuestion } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiRequestError } from '@/services/api'
import { contextAccessApi } from '@/services/access'
import { useSessionStore } from '@/stores/session'
import type { AccessRequest, AccessRequestStatus } from '@/types/admin'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const systemId = computed(() => String(route.params.systemId))
const loading = ref(false)
const submitting = ref(false)
const errorMessage = ref('')
const requests = ref<AccessRequest[]>([])
const form = reactive({ targetTenantId: typeof route.query.tenantId === 'string' ? route.query.tenantId : '', reason: '' })
const activeRequest = computed(() => requests.value.find((request) => request.status === 'SUBMITTED'))
const latestRequest = computed(() => activeRequest.value ?? requests.value[0])
const statusText: Record<AccessRequestStatus, string> = {
  SUBMITTED: '等待管理员审核', APPROVED: '申请已批准', REJECTED: '申请已拒绝', CANCELLED: '申请已取消', EXPIRED: '申请已过期',
}

function reportError(error: unknown) {
  errorMessage.value = error instanceof ApiRequestError ? error.message : '访问申请服务暂时不可用'
}

async function ensurePlatformContext() {
  if (!session.isPlatform) await session.switchPlatform()
}

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    await ensurePlatformContext()
    const result = await contextAccessApi.listOwnRequests(systemId.value)
    requests.value = result.items
  } catch (error) {
    reportError(error)
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!form.reason.trim()) return
  submitting.value = true
  errorMessage.value = ''
  try {
    await ensurePlatformContext()
    const request = await contextAccessApi.submitRequest(systemId.value, { targetTenantId: form.targetTenantId || undefined, reason: form.reason })
    requests.value = [request, ...requests.value.filter((item) => item.id !== request.id)]
    form.reason = ''
    message.success('访问申请已提交')
  } catch (error) {
    reportError(error)
  } finally {
    submitting.value = false
  }
}

async function cancel() {
  const request = activeRequest.value
  if (!request) return
  submitting.value = true
  try {
    await ensurePlatformContext()
    const updated = await contextAccessApi.cancelRequest(request.id, request.version)
    requests.value = requests.value.map((item) => item.id === updated.id ? updated : item)
    message.success('申请已取消')
  } catch (error) {
    reportError(error)
  } finally {
    submitting.value = false
  }
}

async function retryAccess() {
  await router.push(`/systems/${systemId.value}/workbench`)
}

onMounted(load)
</script>

<template>
  <main class="access-page">
    <header class="access-heading"><ShieldQuestion :size="32" /><div><h1>申请系统访问权限</h1><p>系统 {{ systemId }} 尚未向当前账号授予有效成员身份。</p></div></header>
    <a-alert v-if="errorMessage" type="error" show-icon :message="errorMessage" />
    <a-spin v-if="loading" class="access-loading" />
    <section v-else-if="latestRequest" class="request-status-panel">
      <div class="request-status-heading"><Clock3 :size="20" /><div><strong>{{ statusText[latestRequest.status] }}</strong><span>申请编号 {{ latestRequest.id }}</span></div><a-tag :color="latestRequest.status === 'SUBMITTED' ? 'blue' : latestRequest.status === 'APPROVED' ? 'green' : 'default'">{{ latestRequest.status }}</a-tag></div>
      <dl><dt>申请原因</dt><dd>{{ latestRequest.reason }}</dd><dt>目标租户</dt><dd>{{ latestRequest.targetTenantName || latestRequest.targetTenantId || '由管理员分配' }}</dd><dt>提交时间</dt><dd>{{ latestRequest.submittedAt }}</dd><template v-if="latestRequest.reviewReason"><dt>审核意见</dt><dd>{{ latestRequest.reviewReason }}</dd></template></dl>
      <div class="access-actions"><a-button :loading="loading" @click="load"><RefreshCw :size="16" />刷新状态</a-button><a-popconfirm v-if="activeRequest" title="确认取消当前申请？" @confirm="cancel"><a-button danger :loading="submitting"><Ban :size="16" />取消申请</a-button></a-popconfirm><a-button v-if="latestRequest.status === 'APPROVED'" type="primary" @click="retryAccess">重新进入系统</a-button></div>
    </section>
    <section v-if="!loading && !activeRequest && latestRequest?.status !== 'APPROVED'" class="request-form-panel">
      <h2>{{ latestRequest ? '重新提交申请' : '提交访问申请' }}</h2>
      <a-form layout="vertical" :model="form" @finish="submit"><a-form-item label="目标租户 ID（可选）"><a-input v-model:value="form.targetTenantId" aria-label="目标租户 ID（可选）" /></a-form-item><a-form-item label="申请原因" name="reason" :rules="[{ required: true, message: '请填写申请原因' }]"><a-textarea v-model:value="form.reason" :rows="4" :maxlength="500" /></a-form-item><a-button type="primary" html-type="submit" :loading="submitting"><Send :size="16" />提交申请</a-button></a-form>
    </section>
    <footer class="access-footer"><RouterLink to="/platform/workbench">返回平台工作台</RouterLink></footer>
  </main>
</template>
