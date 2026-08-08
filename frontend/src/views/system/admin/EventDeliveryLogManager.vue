<script setup lang="ts">
import type { TableColumnsType } from 'ant-design-vue'
import { Eye, FilterX, RefreshCw, Search } from 'lucide-vue-next'
import { computed, onMounted, ref, watch } from 'vue'

import { ApiRequestError } from '@/services/api'
import { eventApi } from '@/services/event'
import type {
  DeliveryChannelFilter,
  DeliveryLog,
  DeliveryLogDetail,
  DeliveryLogStatus,
  DeliveryLogStatusFilter,
} from '@/types/event'

const props = defineProps<{ systemId: string }>()
const logs = ref<DeliveryLog[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const channel = ref<DeliveryChannelFilter>('ALL')
const status = ref<DeliveryLogStatusFilter>('ALL')
const templateCodeInput = ref('')
const templateCode = ref('')
const loading = ref(false)
const error = ref('')
const detailOpen = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detail = ref<DeliveryLogDetail | null>(null)
const selectedId = ref('')
const detailTab = ref('overview')
let listGeneration = 0
let detailGeneration = 0

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const columns: TableColumnsType = [
  { title: '投递记录', key: 'delivery', width: 230 },
  { title: '模板', dataIndex: 'templateCode', width: 220 },
  { title: '渠道', dataIndex: 'channel', width: 110 },
  { title: '状态', dataIndex: 'status', width: 130 },
  { title: '尝试', key: 'attempts', width: 100 },
  { title: '耗时', key: 'duration', width: 100 },
  { title: '脱敏接收方', dataIndex: 'recipientMasked', width: 190 },
  { title: '创建时间', key: 'createdAt', width: 180 },
  { title: '操作', key: 'actions', width: 90, fixed: 'right' },
]
const attemptColumns: TableColumnsType = [
  { title: '次数', dataIndex: 'attemptNo', width: 76 },
  { title: '状态', dataIndex: 'status', width: 110 },
  { title: '耗时', key: 'duration', width: 100 },
  { title: '错误码', key: 'failure', width: 180 },
  { title: '开始时间', key: 'startedAt', width: 180 },
]

function requestError(cause: unknown, fallback: string) {
  if (cause instanceof ApiRequestError) return cause.message || cause.code || fallback
  return cause instanceof Error ? cause.message : fallback
}

function formatTime(value: string | null) {
  if (!value) return '-'
  const parsed = new Date(value)
  return Number.isNaN(parsed.valueOf()) ? value : parsed.toLocaleString('zh-CN')
}

function statusColor(value: DeliveryLogStatus) {
  if (value === 'DELIVERED') return 'green'
  if (value === 'FAILED') return 'red'
  if (value === 'PENDING') return 'blue'
  return 'default'
}

async function load() {
  const generation = ++listGeneration
  loading.value = true
  error.value = ''
  try {
    let result = await eventApi.deliveryLogs(props.systemId, {
      page: page.value - 1,
      size: pageSize,
      channel: channel.value,
      status: status.value,
      templateCode: templateCode.value,
    })
    if (generation !== listGeneration) return
    const lastPage = Math.max(1, Math.ceil(result.total / pageSize))
    if (page.value > lastPage) {
      page.value = lastPage
      result = await eventApi.deliveryLogs(props.systemId, {
        page: page.value - 1,
        size: pageSize,
        channel: channel.value,
        status: status.value,
        templateCode: templateCode.value,
      })
      if (generation !== listGeneration) return
    }
    logs.value = result.items
    total.value = result.total
    page.value = result.page + 1
  } catch (cause) {
    if (generation === listGeneration) error.value = requestError(cause, '投递日志加载失败')
  } finally {
    if (generation === listGeneration) loading.value = false
  }
}

function search() {
  templateCode.value = templateCodeInput.value.trim()
  page.value = 1
  void load()
}

function resetFilters() {
  channel.value = 'ALL'
  status.value = 'ALL'
  templateCodeInput.value = ''
  templateCode.value = ''
  page.value = 1
  void load()
}

function changeFilter() {
  page.value = 1
  void load()
}

function changePage(value: number) {
  page.value = value
  void load()
}

async function showDetail(item: DeliveryLog) {
  selectedId.value = item.deliveryId
  detail.value = null
  detailError.value = ''
  detailTab.value = 'overview'
  detailOpen.value = true
  await loadDetail(item.deliveryId)
}

async function loadDetail(deliveryId = selectedId.value) {
  if (!deliveryId) return
  const generation = ++detailGeneration
  detailLoading.value = true
  detailError.value = ''
  try {
    const result = await eventApi.deliveryLog(props.systemId, deliveryId)
    if (generation !== detailGeneration || deliveryId !== selectedId.value) return
    detail.value = result
  } catch (cause) {
    if (generation === detailGeneration && deliveryId === selectedId.value) {
      detailError.value = requestError(cause, '投递详情加载失败')
    }
  } finally {
    if (generation === detailGeneration) detailLoading.value = false
  }
}

onMounted(load)
watch(() => props.systemId, () => {
  listGeneration += 1
  detailGeneration += 1
  logs.value = []
  total.value = 0
  page.value = 1
  channel.value = 'ALL'
  status.value = 'ALL'
  templateCodeInput.value = ''
  templateCode.value = ''
  detailOpen.value = false
  detail.value = null
  selectedId.value = ''
  void load()
})
</script>

<template>
  <section class="event-delivery-log-manager">
    <header class="event-section-heading">
      <div>
        <h2>逐渠道投递日志</h2>
        <p>每个渠道独立记录状态、尝试次数、耗时和脱敏失败原因。</p>
      </div>
      <a-button aria-label="刷新投递日志" :loading="loading" :disabled="loading" @click="load"><RefreshCw :size="15" />刷新</a-button>
    </header>

    <form class="delivery-log-filters" @submit.prevent="search">
      <label>渠道
        <select v-model="channel" class="delivery-log-channel" @change="changeFilter">
          <option value="ALL">全部渠道</option>
          <option value="INBOX">INBOX</option>
          <option value="EMAIL">EMAIL</option>
          <option value="WEBHOOK">WEBHOOK</option>
        </select>
      </label>
      <label>状态
        <select v-model="status" class="delivery-log-status" @change="changeFilter">
          <option value="ALL">全部状态</option>
          <option value="PENDING">PENDING</option>
          <option value="DELIVERED">DELIVERED</option>
          <option value="SKIPPED">SKIPPED</option>
          <option value="FAILED">FAILED</option>
        </select>
      </label>
      <label class="delivery-template-filter">模板编码
        <a-input v-model:value="templateCodeInput" class="delivery-log-template" allow-clear placeholder="精确匹配模板编码" />
      </label>
      <div class="delivery-filter-actions">
        <a-button html-type="submit" type="primary"><Search :size="15" />查询</a-button>
        <a-button @click="resetFilters"><FilterX :size="15" />重置</a-button>
      </div>
    </form>

    <a-alert
      class="delivery-log-boundary"
      type="info"
      show-icon
      message="日志只保留审计所需的脱敏信息"
      description="不会展示完整邮箱地址、Webhook Endpoint 查询参数、Secret、SMTP 密码或外部响应正文。"
    />
    <a-alert v-if="error" class="delivery-log-error" type="error" show-icon role="alert" aria-live="assertive" :message="error" />

    <div class="admin-table-region" :aria-busy="loading">
      <a-table
        :columns="columns"
        :data-source="logs"
        :loading="loading"
        :pagination="false"
        row-key="deliveryId"
        :scroll="{ x: 1400 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'delivery'">
            <strong>{{ record.deliveryId }}</strong>
            <code class="cell-secondary">Trace {{ record.traceId }}</code>
          </template>
          <template v-else-if="column.dataIndex === 'channel'"><a-tag>{{ record.channel }}</a-tag></template>
          <template v-else-if="column.dataIndex === 'status'">
            <a-tag :color="statusColor(record.status)">{{ record.status }}</a-tag>
            <span v-if="record.retryable" class="cell-secondary">可补偿重试</span>
          </template>
          <template v-else-if="column.key === 'attempts'">{{ record.attemptCount }} 次</template>
          <template v-else-if="column.key === 'duration'">{{ record.durationMs === null ? '-' : `${record.durationMs} ms` }}</template>
          <template v-else-if="column.key === 'createdAt'">{{ formatTime(record.createdAt) }}</template>
          <template v-else-if="column.key === 'actions'">
            <a-button class="delivery-log-detail" type="link" size="small" :aria-label="`查看投递${record.deliveryId}详情`" @click="showDetail(record as DeliveryLog)">
              <Eye :size="14" />详情
            </a-button>
          </template>
        </template>
      </a-table>
      <a-empty v-if="!loading && !logs.length" description="当前筛选条件下暂无投递日志" />
    </div>
    <footer class="delivery-log-pagination">
      <span>第 {{ page }} / {{ pageCount }} 页，共 {{ total }} 条</span>
      <a-pagination
        v-if="total > pageSize"
        :current="page"
        :page-size="pageSize"
        :total="total"
        :show-size-changer="false"
        @change="changePage"
      />
    </footer>

    <a-drawer v-model:open="detailOpen" :title="detail ? `投递 ${detail.deliveryId}` : '投递详情'" :width="700">
      <a-alert v-if="detailError" class="delivery-detail-error" type="error" show-icon :message="detailError">
        <template #action><a-button class="delivery-detail-retry" size="small" @click="loadDetail()">重试</a-button></template>
      </a-alert>
      <div v-if="detailLoading" class="delivery-detail-loading"><a-spin />正在加载投递详情…</div>
      <a-tabs v-else-if="detail" v-model:active-key="detailTab" class="delivery-detail-tabs">
        <a-tab-pane key="overview" tab="概览">
          <dl class="delivery-detail-list">
            <div><dt>模板</dt><dd>{{ detail.templateCode }} · 版本 {{ detail.templateVersionId ?? '-' }}</dd></div>
            <div><dt>渠道</dt><dd><a-tag>{{ detail.channel }}</a-tag></dd></div>
            <div><dt>状态</dt><dd><a-tag :color="statusColor(detail.status)">{{ detail.status }}</a-tag></dd></div>
            <div><dt>脱敏接收方</dt><dd>{{ detail.recipientMasked }}</dd></div>
            <div><dt>业务目标</dt><dd>{{ detail.targetType && detail.targetId ? `${detail.targetType} · ${detail.targetId}` : '无' }}</dd></div>
            <div><dt>尝试次数</dt><dd>{{ detail.attemptCount }}</dd></div>
            <div><dt>总耗时</dt><dd>{{ detail.durationMs === null ? '-' : `${detail.durationMs} ms` }}</dd></div>
            <div><dt>创建时间</dt><dd>{{ formatTime(detail.createdAt) }}</dd></div>
            <div><dt>完成时间</dt><dd>{{ formatTime(detail.completedAt) }}</dd></div>
            <div v-if="detail.failureCode"><dt>脱敏失败</dt><dd>{{ detail.failureCode }}<small>{{ detail.failureMessage }}</small></dd></div>
          </dl>
        </a-tab-pane>
        <a-tab-pane key="attempts" tab="尝试记录">
          <div class="admin-table-region">
            <a-table :columns="attemptColumns" :data-source="detail.attempts" :pagination="false" row-key="attemptNo" :scroll="{ x: 760 }">
              <template #bodyCell="{ column, record }">
                <template v-if="column.dataIndex === 'status'"><a-tag :color="statusColor(record.status)">{{ record.status }}</a-tag></template>
                <template v-else-if="column.key === 'duration'">{{ record.durationMs === null ? '-' : `${record.durationMs} ms` }}</template>
                <template v-else-if="column.key === 'failure'">
                  <span>{{ record.failureCode || '-' }}</span><small v-if="record.failureMessage" class="cell-secondary">{{ record.failureMessage }}</small>
                </template>
                <template v-else-if="column.key === 'startedAt'">{{ formatTime(record.startedAt) }}</template>
              </template>
            </a-table>
            <a-empty v-if="!detail.attempts.length" description="暂无尝试记录" />
          </div>
        </a-tab-pane>
        <a-tab-pane key="tracking" tab="追踪信息">
          <dl class="delivery-detail-list tracking-list">
            <div><dt>Delivery ID</dt><dd><code>{{ detail.deliveryId }}</code></dd></div>
            <div><dt>Trace ID</dt><dd><code>{{ detail.traceId }}</code></dd></div>
            <div><dt>去重指纹</dt><dd><code>{{ detail.dedupeFingerprint ?? '-' }}</code></dd></div>
            <div><dt>允许补偿</dt><dd>{{ detail.retryable ? '是' : '否' }}</dd></div>
          </dl>
        </a-tab-pane>
      </a-tabs>
    </a-drawer>
  </section>
</template>

<style scoped>
.event-delivery-log-manager{display:grid;min-width:0;gap:14px}.event-section-heading,.event-section-heading h2,.delivery-log-pagination,.delivery-filter-actions{display:flex;align-items:center}.event-section-heading{justify-content:space-between;gap:16px}.event-section-heading h2{margin:0;font-size:17px}.event-section-heading p{margin:4px 0 0;color:#657181}.delivery-log-filters{display:grid;grid-template-columns:150px 160px minmax(240px,1fr) auto;align-items:end;gap:12px;padding:14px;border:1px solid #d3dae1;border-radius:6px;background:#fff}.delivery-log-filters label{display:grid;min-width:0;gap:6px;color:#596576;font-size:12px}.delivery-log-filters select{width:100%;height:32px;padding:0 9px;border:1px solid #d9d9d9;border-radius:6px;background:#fff;color:#172033}.delivery-filter-actions{gap:8px}.delivery-log-pagination{justify-content:flex-end;gap:16px;color:#657181;font-size:13px}.delivery-detail-loading{display:flex;align-items:center;justify-content:center;gap:8px;min-height:180px;color:#657181}.delivery-detail-list{margin:0}.delivery-detail-list>div{display:grid;grid-template-columns:120px minmax(0,1fr);gap:14px;padding:12px 0;border-bottom:1px solid #e5eaed}.delivery-detail-list dt{color:#697781}.delivery-detail-list dd{min-width:0;margin:0;overflow-wrap:anywhere}.delivery-detail-list dd small{display:block;margin-top:4px;color:#b42318}.tracking-list code{font-size:12px}.delivery-detail-error{margin-bottom:12px}@media(max-width:900px){.delivery-log-filters{grid-template-columns:1fr 1fr}.delivery-template-filter{grid-column:1/-1}.delivery-filter-actions{grid-column:1/-1;justify-content:flex-end}}@media(max-width:720px){.event-section-heading{align-items:flex-start;flex-direction:column}.delivery-log-filters{grid-template-columns:minmax(0,1fr)}.delivery-template-filter,.delivery-filter-actions{grid-column:auto}.delivery-filter-actions,.delivery-log-pagination{justify-content:flex-start;flex-wrap:wrap}.delivery-detail-list>div{grid-template-columns:90px minmax(0,1fr)}}
</style>
