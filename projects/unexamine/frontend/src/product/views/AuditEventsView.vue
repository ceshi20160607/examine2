<script setup lang="ts">
import { EyeOutlined, ReloadOutlined, SafetyCertificateOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { Empty } from 'ant-design-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { api, ApiError } from '../api'
import { systemTokens } from '../session'
import type { AuditEvent, AuditEventDetail, AuditEventList, AuditRetentionMarker, AuditRetentionPreflight } from '../types'

const loading = ref(false)
const detailLoading = ref(false)
const retentionLoading = ref(false)
const error = ref('')
const success = ref('')
const events = ref<AuditEvent[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const detailOpen = ref(false)
const detail = ref<AuditEventDetail>()
const preflight = ref<AuditRetentionPreflight>()
const marker = ref<AuditRetentionMarker>()
const filters = reactive({
  traceId: '', requestId: '', actorAccountId: undefined as number | undefined,
  memberId: undefined as number | undefined, eventCode: '', objectType: '', objectId: '', resultCode: '',
  occurredFrom: '', occurredTo: '',
})
const retention = reactive({ businessKey: '', reason: '', approvalReference: '', referenceImpactConfirmed: false })
const token = computed(() => systemTokens.value?.accessToken || '')

function readable(reason: unknown, fallback: string) {
  return reason instanceof ApiError
    ? (reason.traceId ? `${reason.message}（追踪号：${reason.traceId}）` : reason.message)
    : fallback
}

async function load() {
  loading.value = true
  error.value = ''
  const parameters = new URLSearchParams({ page: String(page.value), pageSize: String(pageSize.value) })
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim()) parameters.set(key, String(value).trim())
  })
  try {
    const result = await api<AuditEventList>(`/api/admin/audit-events?${parameters}`, {}, token.value)
    events.value = result.events
    total.value = result.total
  } catch (reason) {
    error.value = readable(reason, '审计记录加载失败')
  } finally { loading.value = false }
}

function search() { page.value = 1; void load() }

async function openDetail(event: AuditEvent) {
  detailLoading.value = true
  error.value = ''
  success.value = ''
  preflight.value = undefined
  marker.value = undefined
  Object.assign(retention, { businessKey: '', reason: '', approvalReference: '', referenceImpactConfirmed: false })
  try {
    detail.value = await api<AuditEventDetail>(`/api/admin/audit-events/${event.id}`, {}, token.value)
    detailOpen.value = true
  } catch (reason) {
    error.value = readable(reason, '审计详情加载失败')
  } finally { detailLoading.value = false }
}

async function runPreflight() {
  const event = detail.value?.event
  if (!event?.objectType || !event.objectId) return
  retentionLoading.value = true
  error.value = ''
  success.value = ''
  marker.value = undefined
  try {
    preflight.value = await api<AuditRetentionPreflight>('/api/admin/audit-events/retention/preflight', {
      method: 'POST', body: JSON.stringify({ objectType: event.objectType, objectId: event.objectId }),
    }, token.value)
  } catch (reason) {
    error.value = readable(reason, '永久保留影响检查失败')
  } finally { retentionLoading.value = false }
}

async function retainPermanently() {
  if (!preflight.value?.allowed || !retention.reason.trim() || !retention.approvalReference.trim()
      || !retention.referenceImpactConfirmed) return
  retentionLoading.value = true
  error.value = ''
  try {
    marker.value = await api<AuditRetentionMarker>('/api/admin/audit-events/retention', {
      method: 'POST', body: JSON.stringify({
        objectType: preflight.value.objectType, objectId: preflight.value.objectId,
        businessKey: retention.businessKey.trim() || null, reason: retention.reason.trim(),
        approvalReference: retention.approvalReference.trim(), referenceImpactConfirmed: true,
      }),
    }, token.value)
    success.value = `已生成不可变永久保留摘要 #${marker.value.id}；原审计正文未被普通业务删除。`
  } catch (reason) {
    error.value = readable(reason, '永久保留摘要创建失败')
  } finally { retentionLoading.value = false }
}

function formatJson(value?: string) {
  if (!value) return '—'
  try { return JSON.stringify(JSON.parse(value), null, 2) } catch { return value }
}

function fieldValue(value: string) {
  try { return JSON.parse(value) ?? 'null' } catch { return value }
}

onMounted(load)
</script>

<template>
  <div class="audit-page">
    <div class="page-heading"><div><p class="eyebrow">后台配置</p><h1>操作审计</h1><p>按当前系统与租户查询；字段前后值二次鉴权，永久保留只接受精确对象和审批引用。</p></div>
      <a-button :loading="loading" @click="load"><ReloadOutlined />刷新</a-button></div>
    <a-alert v-if="error" type="error" show-icon :message="error" class="section-alert" closable @close="error = ''" />
    <a-alert v-if="success" type="success" show-icon :message="success" class="section-alert" closable @close="success = ''" />
    <section class="audit-filters panel-card">
      <a-input v-model:value="filters.traceId" placeholder="traceId" allow-clear />
      <a-input v-model:value="filters.requestId" placeholder="requestId" allow-clear />
      <a-input-number v-model:value="filters.actorAccountId" placeholder="操作者账号 ID" :min="1" style="width:100%" />
      <a-input-number v-model:value="filters.memberId" placeholder="成员 ID" :min="1" style="width:100%" />
      <a-input v-model:value="filters.eventCode" placeholder="动作 / 事件编码" allow-clear />
      <a-input v-model:value="filters.objectType" placeholder="对象类型" allow-clear />
      <a-input v-model:value="filters.objectId" placeholder="对象 ID" allow-clear />
      <a-select v-model:value="filters.resultCode" placeholder="执行结果" allow-clear :options="[
        { value: 'SUCCESS', label: '成功' }, { value: 'PERMISSION_DENIED', label: '权限拒绝' },
        { value: 'FIELD_VALIDATION_FAILED', label: '字段校验失败' },
      ]" />
      <a-date-picker v-model:value="filters.occurredFrom" show-time value-format="YYYY-MM-DDTHH:mm:ss" placeholder="开始时间" style="width:100%" />
      <a-date-picker v-model:value="filters.occurredTo" show-time value-format="YYYY-MM-DDTHH:mm:ss" placeholder="结束时间" style="width:100%" />
      <a-button type="primary" @click="search"><SearchOutlined />查询</a-button>
    </section>
    <section class="audit-table panel-card">
      <a-table :data-source="events" :loading="loading" row-key="id" :pagination="false" :scroll="{ x: 1350 }">
        <a-table-column title="时间" data-index="occurredAt" :width="175" />
        <a-table-column title="事件" data-index="eventCode" :width="230" />
        <a-table-column title="结果" data-index="resultCode" :width="150"><template #default="{ text }"><a-tag :color="text === 'SUCCESS' ? 'green' : 'orange'">{{ text }}</a-tag></template></a-table-column>
        <a-table-column title="对象" :width="210"><template #default="{ record }">{{ record.objectType || '—' }} · {{ record.objectId || '—' }}</template></a-table-column>
        <a-table-column title="操作者" :width="125"><template #default="{ record }">账号 {{ record.actorAccountId || '—' }}<br><small>成员 {{ record.memberId || '—' }}</small></template></a-table-column>
        <a-table-column title="requestId / traceId" :width="300"><template #default="{ record }"><code>{{ record.requestId || '—' }}</code><br><code>{{ record.traceId }}</code></template></a-table-column>
        <a-table-column title="操作" fixed="right" :width="100"><template #default="{ record }"><a-button type="link" :loading="detailLoading" @click="openDetail(record)"><EyeOutlined />详情</a-button></template></a-table-column>
      </a-table>
      <div class="record-pagination"><a-pagination v-model:current="page" v-model:page-size="pageSize" :total="total" show-size-changer @change="load" /></div>
    </section>

    <a-drawer v-model:open="detailOpen" title="审计详情与永久保留" width="760" class="audit-detail-drawer">
      <template v-if="detail">
        <section class="audit-detail-summary panel-card">
          <div class="panel-title"><strong>{{ detail.event.eventCode }}</strong><a-tag :color="detail.event.resultCode === 'SUCCESS' ? 'green' : 'orange'">{{ detail.event.resultCode }}</a-tag></div>
          <a-descriptions :column="2" bordered size="small">
            <a-descriptions-item label="对象">{{ detail.event.objectType || '—' }} · {{ detail.event.objectId || '—' }}</a-descriptions-item>
            <a-descriptions-item label="操作者">账号 {{ detail.event.actorAccountId || '—' }} / 成员 {{ detail.event.memberId || '—' }}</a-descriptions-item>
            <a-descriptions-item label="requestId">{{ detail.event.requestId || '—' }}</a-descriptions-item>
            <a-descriptions-item label="traceId">{{ detail.event.traceId }}</a-descriptions-item>
          </a-descriptions>
          <div class="audit-json-grid"><div><strong>权限快照</strong><pre>{{ formatJson(detail.event.permissionSnapshot) }}</pre></div><div><strong>业务详情</strong><pre>{{ formatJson(detail.event.detailJson) }}</pre></div></div>
        </section>
        <section class="panel-card audit-field-changes">
          <div class="panel-title"><strong>字段前后值</strong><a-tag :color="detail.sensitiveValuesVisible ? 'green' : 'gold'">{{ detail.sensitiveValuesVisible ? '可看敏感值' : '敏感值已脱敏' }}</a-tag></div>
          <a-empty v-if="!detail.fieldChanges.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="该事件没有字段变化" />
          <article v-for="field in detail.fieldChanges" :key="field.id">
            <div><code>{{ field.fieldCode }}</code><a-tag>{{ field.valueType }}</a-tag><a-tag v-if="field.sensitivity === 'SENSITIVE'" color="red">敏感</a-tag><a-tag v-if="field.masked" color="gold">已脱敏</a-tag></div>
            <p><span>修改前</span><strong>{{ fieldValue(field.beforeValueJson) }}</strong><span>修改后</span><strong>{{ fieldValue(field.afterValueJson) }}</strong></p>
          </article>
        </section>
        <section class="panel-card audit-retention">
          <div class="panel-title"><strong>物理清理前的永久保留影响检查</strong><span>审计查询与清理分权</span></div>
          <a-alert v-if="!detail.event.objectType || !detail.event.objectId" type="warning" show-icon message="当前事件没有精确对象，不能提交永久保留。" />
          <a-button v-else :loading="retentionLoading" @click="runPreflight"><SafetyCertificateOutlined />检查当前精确对象</a-button>
          <template v-if="preflight">
            <div class="retention-impact"><div><span>审计正文</span><strong>{{ preflight.auditEventCount }}</strong></div><div><span>字段变化</span><strong>{{ preflight.fieldChangeCount }}</strong></div><div><span>已有标记</span><strong>{{ preflight.existingMarker ? '是' : '否' }}</strong></div></div>
            <a-alert :type="preflight.allowed ? 'success' : 'error'" show-icon :message="preflight.allowed ? '精确范围检查通过，可以提交审批引用和不可变摘要。' : preflight.blockers.join('；')" />
            <a-form v-if="preflight.allowed" layout="vertical" class="retention-form">
              <div class="form-grid"><a-form-item label="业务标识"><a-input v-model:value="retention.businessKey" placeholder="可选" /></a-form-item><a-form-item label="审批引用" required><a-input v-model:value="retention.approvalReference" placeholder="例如 APPROVAL-2026-001" /></a-form-item></div>
              <a-form-item label="永久保留原因" required><a-textarea v-model:value="retention.reason" :rows="2" /></a-form-item>
              <a-checkbox v-model:checked="retention.referenceImpactConfirmed">我已确认业务引用影响，摘要创建后不可通过普通业务修改或删除</a-checkbox>
              <a-popconfirm title="确认按当前精确对象生成永久保留摘要？" @confirm="retainPermanently"><a-button type="primary" danger :loading="retentionLoading" :disabled="!retention.reason.trim() || !retention.approvalReference.trim() || !retention.referenceImpactConfirmed">生成永久保留摘要</a-button></a-popconfirm>
            </a-form>
          </template>
          <a-result v-if="marker" status="success" :title="`不可变摘要 #${marker.id}`" :sub-title="`SHA-256：${marker.snapshotHash}`" />
        </section>
      </template>
    </a-drawer>
  </div>
</template>
