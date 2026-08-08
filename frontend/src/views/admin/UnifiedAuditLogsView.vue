<script setup lang="ts">
import { Eye, FilterX, RefreshCw, Search } from 'lucide-vue-next'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import { ApiRequestError } from '@/services/api'
import { auditApi } from '@/services/audit'
import type {
  AuditCategory,
  AuditLogItem,
  AuditLogQuery,
  AuditResult,
  OperationsHealthStatus,
  OperationsHealthSummary,
} from '@/types/audit'

const props = defineProps<{ zone: 'platform' | 'system' }>()
const route = useRoute()
const systemId = computed(() => String(route.params.systemId ?? ''))
const items = ref<AuditLogItem[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)
const healthLoading = ref(false)
const error = ref('')
const health = ref<OperationsHealthSummary | null>(null)
const selected = ref<AuditLogItem | null>(null)
const detailOpen = ref(false)
const detailTab = ref('summary')
const filters = ref({
  requestId: '', traceId: '', actor: '', object: '',
  result: 'ALL' as AuditResult, category: 'ALL' as AuditCategory,
  from: '', to: '',
})

const title = computed(() => props.zone === 'platform' ? '平台日志与运维体检' : '系统日志与运维体检')

function message(cause: unknown, fallback: string) {
  if (cause instanceof ApiRequestError) return cause.message || cause.code || fallback
  return cause instanceof Error ? cause.message : fallback
}

function iso(value: string) {
  if (!value) return undefined
  const parsed = new Date(value)
  return Number.isNaN(parsed.valueOf()) ? undefined : parsed.toISOString()
}

function query(): AuditLogQuery {
  return {
    requestId: filters.value.requestId.trim() || undefined,
    traceId: filters.value.traceId.trim() || undefined,
    actor: filters.value.actor.trim() || undefined,
    object: filters.value.object.trim() || undefined,
    result: filters.value.result,
    category: filters.value.category,
    from: iso(filters.value.from),
    to: iso(filters.value.to),
    page: page.value,
    size,
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = props.zone === 'platform'
      ? await auditApi.platformLogs(query())
      : await auditApi.systemLogs(systemId.value, query())
    items.value = result.items
    total.value = result.total
    page.value = result.page
  } catch (cause) {
    error.value = message(cause, '日志加载失败')
  } finally {
    loading.value = false
  }
}

async function loadHealth() {
  healthLoading.value = true
  try {
    health.value = props.zone === 'platform'
      ? await auditApi.platformHealth()
      : await auditApi.systemHealth(systemId.value)
  } catch (cause) {
    error.value = message(cause, '运维体检加载失败')
  } finally {
    healthLoading.value = false
  }
}

function search() {
  page.value = 1
  void load()
}

function reset() {
  filters.value = {
    requestId: '', traceId: '', actor: '', object: '',
    result: 'ALL', category: 'ALL', from: '', to: '',
  }
  page.value = 1
  void load()
}

function changePage(value: number) {
  page.value = value
  void load()
}

function refresh() {
  void load()
  void loadHealth()
}

function showDetail(item: AuditLogItem) {
  selected.value = item
  detailTab.value = 'summary'
  detailOpen.value = true
}

function formatTime(value?: string) {
  if (!value) return '-'
  const parsed = new Date(value)
  return Number.isNaN(parsed.valueOf()) ? value : parsed.toLocaleString('zh-CN')
}

function resultColor(value: string) {
  if (value === 'SUCCESS' || value === 'AVAILABLE') return 'green'
  if (value === 'FAILED' || value === 'DEGRADED') return 'red'
  if (value === 'DENIED') return 'orange'
  if (value === 'PENDING') return 'blue'
  return 'default'
}

function healthLabel(value: OperationsHealthStatus) {
  return value === 'AVAILABLE' ? '可用' : value === 'DEGRADED' ? '需处理' : '未配置'
}

onMounted(() => {
  void load()
  void loadHealth()
})

watch(() => [props.zone, systemId.value], () => {
  page.value = 1
  items.value = []
  health.value = null
  void load()
  void loadHealth()
})
</script>

<template>
  <section class="unified-audit-page">
    <AdminPageHeader :title="title" description="统一检索认证、操作、配置、数据、流程、文件、任务、开放接口、消息与 AI 审计记录，并在同一入口检查关键运行依赖。">
      <template #actions>
        <a-button :loading="loading || healthLoading" @click="refresh"><RefreshCw :size="15" />刷新</a-button>
      </template>
    </AdminPageHeader>

    <section class="health-panel" :aria-busy="healthLoading">
      <header>
        <div><h2>授权运维体检</h2><p>版本 {{ health?.version ?? '-' }} · Flyway {{ health?.flywayVersion ?? '不可读取' }}</p></div>
        <a-tag :color="resultColor(health?.overallStatus ?? 'DEGRADED')">{{ health?.overallStatus === 'AVAILABLE' ? '整体可用' : '存在需处理项' }}</a-tag>
      </header>
      <div v-if="health" class="health-grid">
        <article v-for="component in health.components" :key="component.code" class="health-card">
          <div><strong>{{ component.label }}</strong><a-tag :color="resultColor(component.status)">{{ healthLabel(component.status) }}</a-tag></div>
          <p>{{ component.summary }}</p>
          <small>{{ component.hint }}</small>
        </article>
      </div>
      <a-skeleton v-else :loading="healthLoading" active />
    </section>

    <form class="audit-filters" @submit.prevent="search">
      <label>Request ID<a-input v-model:value="filters.requestId" allow-clear /></label>
      <label>Trace ID<a-input v-model:value="filters.traceId" allow-clear /></label>
      <label>操作者<a-input v-model:value="filters.actor" allow-clear placeholder="账号 ID 或名称" /></label>
      <label>对象<a-input v-model:value="filters.object" allow-clear placeholder="类型、ID 或事件" /></label>
      <label>分类<select v-model="filters.category">
        <option v-for="value in ['ALL','AUTH','CONFIG','DATA','FLOW','FILE','TASK','OPENAPI','MESSAGE','AI','OPERATION']" :key="value" :value="value">{{ value }}</option>
      </select></label>
      <label>结果<select v-model="filters.result">
        <option v-for="value in ['ALL','SUCCESS','DENIED','FAILED','PENDING']" :key="value" :value="value">{{ value }}</option>
      </select></label>
      <label>开始时间<input v-model="filters.from" type="datetime-local"></label>
      <label>结束时间<input v-model="filters.to" type="datetime-local"></label>
      <div class="filter-actions"><a-button html-type="submit" type="primary"><Search :size="15" />查询</a-button><a-button @click="reset"><FilterX :size="15" />重置</a-button></div>
    </form>

    <a-alert v-if="error" type="error" show-icon role="alert" :message="error" />
    <a-alert type="info" show-icon message="仅展示脱敏审计字段" description="页面不会返回请求正文、变更前后原文、连接串、SecretRef 值、凭据、完整地址或内部异常正文。" />

    <div class="audit-table-wrap" :aria-busy="loading">
      <table class="audit-table">
        <thead><tr><th>发生时间</th><th>分类 / 事件</th><th>操作者</th><th>对象</th><th>结果</th><th>关联标识</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="item in items" :key="item.id">
            <td>{{ formatTime(item.occurredAt) }}</td>
            <td><a-tag>{{ item.category }}</a-tag><strong>{{ item.event }}</strong><small>{{ item.source }}</small></td>
            <td>{{ item.actorName || item.actorId || '-' }}<small v-if="item.actorName && item.actorId">{{ item.actorId }}</small></td>
            <td>{{ item.objectType || '-' }}<small>{{ item.objectId || '-' }}</small></td>
            <td><a-tag :color="resultColor(item.result)">{{ item.result }}</a-tag><small v-if="item.failureCode">{{ item.failureCode }}</small></td>
            <td><small>Req {{ item.requestId || '-' }}</small><small>Trace {{ item.traceId || '-' }}</small></td>
            <td><a-button type="link" @click="showDetail(item)"><Eye :size="15" />详情</a-button></td>
          </tr>
          <tr v-if="!loading && items.length === 0"><td colspan="7" class="empty">当前筛选条件下没有日志</td></tr>
        </tbody>
      </table>
    </div>
    <footer class="audit-pagination"><span>共 {{ total }} 条</span><a-pagination :current="page" :page-size="size" :total="total" :show-size-changer="false" @change="changePage" /></footer>

    <a-drawer v-model:open="detailOpen" title="审计详情" width="620">
      <a-alert type="info" show-icon message="敏感内容已在服务端裁剪" />
      <a-tabs v-if="selected" v-model:active-key="detailTab">
        <a-tab-pane key="summary" tab="摘要">
          <dl><dt>日志标识</dt><dd>{{ selected.id }}</dd><dt>事件</dt><dd>{{ selected.category }} / {{ selected.event }}</dd><dt>结果</dt><dd>{{ selected.result }} {{ selected.failureCode || '' }}</dd><dt>时间</dt><dd>{{ formatTime(selected.occurredAt) }}</dd></dl>
        </a-tab-pane>
        <a-tab-pane key="subject" tab="主体与对象">
          <dl><dt>操作者</dt><dd>{{ selected.actorName || '-' }}（{{ selected.actorId || '-' }}）</dd><dt>对象</dt><dd>{{ selected.objectType || '-' }} / {{ selected.objectId || '-' }}</dd><template v-for="(value, key) in selected.details" :key="key"><dt>{{ key }}</dt><dd>{{ value }}</dd></template></dl>
        </a-tab-pane>
        <a-tab-pane key="correlation" tab="关联与上下文">
          <dl><dt>Request ID</dt><dd>{{ selected.requestId || '-' }}</dd><dt>Trace ID</dt><dd>{{ selected.traceId || '-' }}</dd><dt>System ID</dt><dd>{{ selected.systemId || '-' }}</dd><dt>Tenant ID</dt><dd>{{ selected.tenantId || '-' }}</dd></dl>
        </a-tab-pane>
      </a-tabs>
    </a-drawer>
  </section>
</template>

<style scoped>
.unified-audit-page{display:grid;gap:18px}.health-panel{border:1px solid #e5e7eb;border-radius:14px;background:#fff;padding:18px}.health-panel>header,.health-card>div,.audit-pagination{display:flex;align-items:center;justify-content:space-between;gap:12px}.health-panel h2{margin:0;font-size:18px}.health-panel header p{margin:4px 0 0;color:#64748b}.health-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(230px,1fr));gap:10px;margin-top:14px}.health-card{border:1px solid #edf0f4;border-radius:10px;padding:12px}.health-card p{margin:9px 0 5px}.health-card small{color:#64748b}.audit-filters{display:grid;grid-template-columns:repeat(4,minmax(150px,1fr));gap:12px;padding:16px;border:1px solid #e5e7eb;border-radius:14px;background:#fff}.audit-filters label{display:grid;gap:6px;color:#475569;font-size:13px}.audit-filters select,.audit-filters input{height:32px;border:1px solid #d9d9d9;border-radius:6px;padding:0 10px;background:#fff}.filter-actions{display:flex;gap:8px;align-items:end}.audit-table-wrap{overflow:auto;border:1px solid #e5e7eb;border-radius:12px;background:#fff}.audit-table{width:100%;min-width:1160px;border-collapse:collapse}.audit-table th,.audit-table td{padding:12px;border-bottom:1px solid #edf0f4;text-align:left;vertical-align:top}.audit-table th{background:#f8fafc;color:#475569;font-size:13px}.audit-table td strong,.audit-table td small{display:block;margin-top:4px}.audit-table td small{color:#64748b}.empty{text-align:center!important;color:#64748b;padding:38px!important}.audit-pagination{padding:0 4px}.unified-audit-page dl{display:grid;grid-template-columns:130px 1fr;gap:12px;margin-top:18px}.unified-audit-page dt{color:#64748b}.unified-audit-page dd{margin:0;word-break:break-all}@media(max-width:980px){.audit-filters{grid-template-columns:repeat(2,minmax(150px,1fr))}}@media(max-width:640px){.audit-filters{grid-template-columns:1fr}.health-grid{grid-template-columns:1fr}}
</style>
