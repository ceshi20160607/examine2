<script setup lang="ts">
import type { TableColumnsType } from 'ant-design-vue'
import { message } from 'ant-design-vue'
import { Check, RefreshCw, X } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'

import AdminMobileNotice from '@/components/admin/AdminMobileNotice.vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import { useAdminViewport } from '@/composables/useAdminViewport'
import { ApiRequestError } from '@/services/api'
import { systemAdminApi } from '@/services/admin'
import type { AccessRequest, AccessRequestStatus, Role, Tenant } from '@/types/admin'

const { isMobile } = useAdminViewport()
const route = useRoute()
const systemId = computed(() => String(route.params.systemId))
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const requests = ref<AccessRequest[]>([])
const tenants = ref<Tenant[]>([])
const roles = ref<Role[]>([])
const total = ref(0)
const statusFilter = ref<string>('SUBMITTED')
const reviewOpen = ref(false)
const decision = ref<'approve' | 'reject'>('approve')
const reviewForm = reactive({
  id: '',
  applicant: '',
  reason: '',
  version: '',
  targetTenantLabel: '',
  tenantIds: [] as string[],
  roleIds: [] as string[],
})
const tenantOptions = computed(() => {
  const options = tenants.value.map((tenant) => ({
    value: tenant.id,
    label: `${tenant.name}（${tenant.code}）${tenant.isDefault ? ' · 默认租户' : ''}`,
    disabled: tenant.status !== 'ACTIVE',
  }))
  const targetTenantId = reviewForm.tenantIds[0]
  if (targetTenantId && !options.some((option) => option.value === targetTenantId)) {
    options.unshift({
      value: targetTenantId,
      label: reviewForm.targetTenantLabel || `目标租户（${targetTenantId}）`,
      disabled: false,
    })
  }
  return options
})
const roleOptions = computed(() => roles.value.map((role) => ({
  value: role.id,
  label: `${role.name}（${role.code}）`,
  disabled: role.status !== 'ACTIVE',
})))
const columns: TableColumnsType = [
  { title: '申请人', key: 'applicant', width: 200 },
  { title: '目标租户', key: 'tenant', width: 180 },
  { title: '申请原因', dataIndex: 'reason' },
  { title: '提交时间', dataIndex: 'submittedAt', width: 180 },
  { title: '状态', dataIndex: 'status', width: 110 },
  { title: '操作', key: 'actions', width: 150 },
]
const statusText: Record<AccessRequestStatus, string> = {
  SUBMITTED: '待审核', APPROVED: '已批准', REJECTED: '已拒绝', CANCELLED: '已取消', EXPIRED: '已过期',
}

function reportError(error: unknown) {
  errorMessage.value = error instanceof ApiRequestError ? error.message : '访问申请与授权选项加载失败'
}

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [requestResult, tenantResult, roleResult] = await Promise.all([
      systemAdminApi.listAccessRequests(systemId.value, { page: 1, size: 100, status: statusFilter.value || undefined }),
      systemAdminApi.listTenants(systemId.value, { page: 1, size: 200 }),
      systemAdminApi.listRoles(systemId.value, { page: 1, size: 200 }),
    ])
    requests.value = requestResult.items
    total.value = requestResult.total
    tenants.value = tenantResult.items
    roles.value = roleResult.items
  } catch (error) {
    reportError(error)
  } finally {
    loading.value = false
  }
}

function openReview(request: AccessRequest, next: typeof decision.value) {
  decision.value = next
  Object.assign(reviewForm, {
    id: request.id,
    applicant: request.accountName,
    reason: '',
    version: request.version,
    targetTenantLabel: request.targetTenantName
      ? `${request.targetTenantName}${request.targetTenantId ? `（${request.targetTenantId}）` : ''}`
      : '',
    tenantIds: request.targetTenantId ? [request.targetTenantId] : [],
    roleIds: [],
  })
  reviewOpen.value = true
}

async function submitReview() {
  if (!reviewForm.reason.trim()) {
    message.warning('请填写审核意见')
    return
  }
  if (decision.value === 'approve' && !reviewForm.tenantIds.length) {
    message.warning('批准申请时请至少选择一个授权租户')
    return
  }
  if (decision.value === 'approve' && !reviewForm.roleIds.length) {
    message.warning('批准申请时请至少选择一个绑定角色')
    return
  }
  saving.value = true
  try {
    await systemAdminApi.reviewAccessRequest(systemId.value, reviewForm.id, decision.value, {
      reason: reviewForm.reason,
      version: reviewForm.version,
      tenantIds: decision.value === 'approve' ? reviewForm.tenantIds : undefined,
      roleIds: decision.value === 'approve' ? reviewForm.roleIds : undefined,
    })
    reviewOpen.value = false
    message.success(decision.value === 'approve' ? '申请已批准' : '申请已拒绝')
    await load()
  } catch (error) {
    reportError(error)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="admin-page">
    <AdminPageHeader title="访问申请" :description="`当前筛选共 ${total} 条申请，终态申请不可重复审核。`">
      <template #actions><a-select v-model:value="statusFilter" class="status-filter" @change="load"><a-select-option value="">全部状态</a-select-option><a-select-option value="SUBMITTED">待审核</a-select-option><a-select-option value="APPROVED">已批准</a-select-option><a-select-option value="REJECTED">已拒绝</a-select-option><a-select-option value="CANCELLED">已取消</a-select-option><a-select-option value="EXPIRED">已过期</a-select-option></a-select><a-button :loading="loading" @click="load"><RefreshCw :size="16" />刷新</a-button></template>
    </AdminPageHeader>
    <a-alert v-if="errorMessage" class="admin-alert" type="error" show-icon :message="errorMessage" />
    <template v-if="isMobile">
      <AdminMobileNotice />
      <div class="mobile-record-list"><article v-for="request in requests" :key="request.id" class="mobile-record"><div class="mobile-record-title"><strong>{{ request.accountName }}</strong><a-tag :color="request.status === 'SUBMITTED' ? 'blue' : request.status === 'APPROVED' ? 'green' : 'default'">{{ statusText[request.status] }}</a-tag></div><dl><dt>租户</dt><dd>{{ request.targetTenantName || request.targetTenantId || '系统默认' }}</dd><dt>原因</dt><dd>{{ request.reason }}</dd><dt>提交</dt><dd>{{ request.submittedAt }}</dd></dl></article></div>
      <a-empty v-if="!loading && !requests.length" description="暂无申请" />
    </template>
    <div v-else class="admin-table-region"><a-table :columns="columns" :data-source="requests" :loading="loading" :pagination="false" row-key="id" :scroll="{ x: 980 }"><template #bodyCell="{ column, record }"><template v-if="column.key === 'applicant'"><strong>{{ record.accountName }}</strong><span class="cell-secondary">{{ record.accountId }}</span></template><template v-else-if="column.key === 'tenant'">{{ record.targetTenantName || record.targetTenantId || '系统默认' }}</template><template v-else-if="column.dataIndex === 'status'"><a-tag :color="record.status === 'SUBMITTED' ? 'blue' : record.status === 'APPROVED' ? 'green' : 'default'">{{ statusText[record.status as AccessRequestStatus] }}</a-tag></template><template v-else-if="column.key === 'actions' && record.status === 'SUBMITTED'"><div class="table-actions"><a-button type="link" size="small" @click="openReview(record as AccessRequest, 'approve')"><Check :size="14" />批准</a-button><a-button type="link" danger size="small" @click="openReview(record as AccessRequest, 'reject')"><X :size="14" />拒绝</a-button></div></template></template></a-table></div>
    <a-modal v-model:open="reviewOpen" :title="decision === 'approve' ? '批准访问申请' : '拒绝访问申请'" :confirm-loading="saving" @ok="submitReview"><p>申请人：{{ reviewForm.applicant }}</p><a-form layout="vertical"><template v-if="decision === 'approve'"><a-form-item label="授权租户" required><a-select v-model:value="reviewForm.tenantIds" aria-label="授权租户" mode="multiple" show-search option-filter-prop="label" :options="tenantOptions" :loading="loading" placeholder="选择申请人可访问的租户" /></a-form-item><a-form-item label="绑定角色" required><a-select v-model:value="reviewForm.roleIds" aria-label="绑定角色" mode="multiple" show-search option-filter-prop="label" :options="roleOptions" :loading="loading" placeholder="选择申请人在系统中的角色" /></a-form-item></template><a-form-item label="审核意见" required><a-textarea v-model:value="reviewForm.reason" aria-label="审核意见" :rows="3" /></a-form-item></a-form></a-modal>
  </section>
</template>
