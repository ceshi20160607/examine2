<script setup lang="ts">
import type { TableColumnsType } from 'ant-design-vue'
import { Eye, Pencil, PlugZap, RefreshCw, ShieldCheck } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref, watch } from 'vue'

import { ApiRequestError } from '@/services/api'
import { eventAdministrationApi } from '@/services/event'
import type {
  EventChannelCheckResult,
  EventChannelConfiguration,
  EventChannelUpdate,
  ExternalDeliveryChannel,
} from '@/types/event'

const props = defineProps<{ systemId: string }>()
type ExternalChannelConfiguration = EventChannelConfiguration & { channel: ExternalDeliveryChannel }
const channels = ref<ExternalChannelConfiguration[]>([])
const loading = ref(false)
const error = ref('')
const mutation = ref('')
const detailOpen = ref(false)
const detailTab = ref('overview')
const selectedChannel = ref<ExternalDeliveryChannel | null>(null)
const editOpen = ref(false)
const editError = ref('')
const checkResults = ref<Partial<Record<ExternalDeliveryChannel, EventChannelCheckResult>>>({})
let loadGeneration = 0

const selected = computed(() =>
  channels.value.find((item) => item.channel === selectedChannel.value) ?? null)

const form = reactive({
  enabled: false,
  endpoint: '',
  secretRef: '',
  timeoutMs: 5000,
  expectedVersion: 0,
})

const columns: TableColumnsType = [
  { title: '渠道', key: 'channel', width: 180 },
  { title: '配置状态', key: 'configuration', width: 160 },
  { title: '运行状态', key: 'enabled', width: 130 },
  { title: '脱敏目标', key: 'destination', width: 260 },
  { title: '最近检查', key: 'lastCheck', width: 180 },
  { title: '操作', key: 'actions', width: 260, fixed: 'right' },
]

function requestError(cause: unknown, fallback: string) {
  if (cause instanceof ApiRequestError) return cause.message || cause.code || fallback
  return cause instanceof Error ? cause.message : fallback
}

function formatTime(value: string | null) {
  if (!value) return '尚未检查'
  const parsed = new Date(value)
  return Number.isNaN(parsed.valueOf()) ? value : parsed.toLocaleString('zh-CN')
}

function checkColor(status: EventChannelConfiguration['lastCheckStatus']) {
  if (status === 'SENT') return 'green'
  if (status === 'PERMANENT_FAILURE') return 'red'
  if (status === 'TEMPORARY_FAILURE') return 'orange'
  return 'default'
}

function replaceChannel(updated: EventChannelConfiguration) {
  if (updated.channel === 'INBOX') return
  const externalUpdated: ExternalChannelConfiguration = { ...updated, channel: updated.channel }
  channels.value = channels.value.map((item) =>
    item.channel === externalUpdated.channel ? externalUpdated : item)
}

async function load() {
  const generation = ++loadGeneration
  loading.value = true
  error.value = ''
  try {
    const result = await eventAdministrationApi.channels(props.systemId)
    if (generation !== loadGeneration) return
    channels.value = result.filter((item): item is ExternalChannelConfiguration =>
      item.channel !== 'INBOX')
  } catch (cause) {
    if (generation === loadGeneration) error.value = requestError(cause, '投递渠道加载失败')
  } finally {
    if (generation === loadGeneration) loading.value = false
  }
}

function showDetail(item: ExternalChannelConfiguration) {
  selectedChannel.value = item.channel
  detailTab.value = 'overview'
  detailOpen.value = true
}

function openEditor(item: ExternalChannelConfiguration) {
  selectedChannel.value = item.channel
  Object.assign(form, {
    enabled: item.enabled,
    endpoint: '',
    secretRef: '',
    timeoutMs: item.timeoutMs ?? 5000,
    expectedVersion: item.version,
  })
  editError.value = ''
  editOpen.value = true
}

function updateInput(item: ExternalChannelConfiguration): EventChannelUpdate {
  const input: EventChannelUpdate = {
    enabled: form.enabled,
    expectedVersion: form.expectedVersion,
  }
  if (item.channel === 'WEBHOOK') {
    const endpoint = form.endpoint.trim()
    const secretRef = form.secretRef.trim()
    if (endpoint) input.endpoint = endpoint
    if (secretRef) input.secretRef = secretRef
    input.timeoutMs = Number(form.timeoutMs)
  }
  return input
}

async function save() {
  const item = selected.value
  if (!item) return
  editError.value = ''
  if (item.channel === 'WEBHOOK') {
    const endpoint = form.endpoint.trim()
    const secretRef = form.secretRef.trim()
    if (!Number.isInteger(Number(form.timeoutMs))
      || Number(form.timeoutMs) < 100 || Number(form.timeoutMs) > 30_000) {
      editError.value = '超时必须是 100 到 30000 毫秒的整数'
      return
    }
    if (endpoint) {
      try {
        const parsed = new URL(endpoint)
        if (parsed.protocol !== 'https:' || !parsed.hostname || parsed.username || parsed.password || parsed.hash) {
          throw new Error('invalid endpoint')
        }
      } catch {
        editError.value = 'Endpoint 必须是没有用户信息和片段的 HTTPS 地址'
        return
      }
    }
    if (secretRef && !/^env:\/\/[A-Z][A-Z0-9_]{1,126}$/u.test(secretRef)) {
      editError.value = 'SecretRef 必须使用 env:// 加大写环境变量名，不要填写明文 Secret'
      return
    }
    if (form.enabled && !item.configured && (!endpoint || !secretRef)) {
      editError.value = '首次启用 Webhook 时必须同时填写 HTTPS Endpoint 和 SecretRef'
      return
    }
  }
  mutation.value = `save:${item.channel}`
  try {
    const updated = await eventAdministrationApi.updateChannel(
      props.systemId,
      item.channel,
      updateInput(item),
    )
    replaceChannel(updated)
    editOpen.value = false
  } catch (cause) {
    editError.value = requestError(cause, '渠道配置保存失败')
    if (cause instanceof ApiRequestError && cause.status === 409) await load()
  } finally {
    mutation.value = ''
  }
}

async function changeEnabled(item: ExternalChannelConfiguration, enabled: boolean) {
  if (enabled === item.enabled || mutation.value) return
  mutation.value = `enabled:${item.channel}`
  error.value = ''
  try {
    replaceChannel(await eventAdministrationApi.updateChannel(props.systemId, item.channel, {
      enabled,
      expectedVersion: item.version,
    }))
  } catch (cause) {
    error.value = requestError(cause, '渠道状态更新失败')
    if (cause instanceof ApiRequestError && cause.status === 409) await load()
  } finally {
    mutation.value = ''
  }
}

async function check(item: ExternalChannelConfiguration) {
  if (mutation.value) return
  mutation.value = `check:${item.channel}`
  error.value = ''
  try {
    const result = await eventAdministrationApi.checkChannel(props.systemId, item.channel)
    checkResults.value = { ...checkResults.value, [item.channel]: result }
    await load()
  } catch (cause) {
    error.value = requestError(cause, '渠道连通性检查失败')
  } finally {
    mutation.value = ''
  }
}

onMounted(load)
watch(() => props.systemId, () => {
  loadGeneration += 1
  channels.value = []
  checkResults.value = {}
  selectedChannel.value = null
  detailOpen.value = false
  editOpen.value = false
  void load()
})
watch(editOpen, (open) => {
  if (open) return
  form.endpoint = ''
  form.secretRef = ''
})
</script>

<template>
  <section class="event-channel-manager">
    <header class="event-section-heading">
      <div>
        <h2>外部投递渠道</h2>
        <p>启停 EMAIL 与 WEBHOOK，并通过受控检查确认当前部署配置可用。</p>
      </div>
      <a-button aria-label="刷新投递渠道" :loading="loading" :disabled="loading" @click="load"><RefreshCw :size="15" />刷新</a-button>
    </header>

    <a-alert
      class="channel-security-notice"
      type="info"
      show-icon
      message="敏感配置不进入管理页面"
      description="SMTP 密码只由部署环境提供；Webhook Endpoint 保存后不回显，SecretRef 只显示掩码，检查结果不包含外部响应正文。"
    />
    <a-alert v-if="error" class="channel-error" type="error" show-icon role="alert" aria-live="assertive" :message="error" />

    <div class="admin-table-region" :aria-busy="loading">
      <a-table
        :columns="columns"
        :data-source="channels"
        :loading="loading"
        :pagination="false"
        row-key="channel"
        :scroll="{ x: 1140 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'channel'">
            <strong>{{ record.displayName }}</strong>
            <code class="cell-secondary">{{ record.channel }}</code>
          </template>
          <template v-else-if="column.key === 'configuration'">
            <a-tag :color="record.available && record.configured ? 'green' : 'orange'">
              {{ record.available && record.configured ? '可用' : '待配置' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'enabled'">
            <a-switch
              class="channel-enabled-toggle"
              :aria-label="`${record.displayName}启用状态`"
              :checked="record.enabled"
              :loading="mutation === `enabled:${record.channel}`"
              :disabled="Boolean(mutation) || !record.available || !record.configured"
              checked-children="已启用"
              un-checked-children="已停用"
              @change="changeEnabled(record as ExternalChannelConfiguration, Boolean($event))"
            />
          </template>
          <template v-else-if="column.key === 'destination'">
            <span class="masked-destination">{{ record.maskedDestination || '不在页面暴露' }}</span>
          </template>
          <template v-else-if="column.key === 'lastCheck'">
            <a-tag :color="checkColor(record.lastCheckStatus)">{{ record.lastCheckStatus || 'NOT_CHECKED' }}</a-tag>
            <span class="cell-secondary">{{ formatTime(record.lastCheckAt) }}</span>
          </template>
          <template v-else-if="column.key === 'actions'">
            <div class="table-actions">
              <a-button class="channel-detail" type="link" size="small" :aria-label="`查看${record.displayName}详情`" @click="showDetail(record as ExternalChannelConfiguration)">
                <Eye :size="14" />详情
              </a-button>
              <a-button class="channel-edit" type="link" size="small" :aria-label="`设置${record.displayName}`" @click="openEditor(record as ExternalChannelConfiguration)">
                <Pencil :size="14" />设置
              </a-button>
              <a-button
                class="channel-check"
                type="link"
                size="small"
                :aria-label="`检查${record.displayName}连通性`"
                :loading="mutation === `check:${record.channel}`"
                :disabled="Boolean(mutation) || !record.enabled || !record.available || !record.configured"
                @click="check(record as ExternalChannelConfiguration)"
              >
                <PlugZap :size="14" />检查
              </a-button>
            </div>
          </template>
        </template>
      </a-table>
      <a-empty v-if="!loading && !channels.length" description="暂无可管理的外部投递渠道" />
    </div>

    <a-drawer v-model:open="detailOpen" :title="selected?.displayName || '渠道详情'" :width="620">
      <a-tabs v-if="selected" v-model:active-key="detailTab" class="channel-detail-tabs">
        <a-tab-pane key="overview" tab="概览">
          <dl class="channel-detail-list">
            <div><dt>渠道</dt><dd>{{ selected.channel }}</dd></div>
            <div><dt>运行状态</dt><dd>{{ selected.enabled ? '已启用' : '已停用' }}</dd></div>
            <div><dt>配置状态</dt><dd>{{ selected.configured ? '已配置' : '待配置' }}</dd></div>
            <div><dt>脱敏目标</dt><dd>{{ selected.maskedDestination || '不在页面暴露' }}</dd></div>
            <div><dt>SecretRef</dt><dd>{{ selected.secretRefMasked || '不适用或未配置' }}</dd></div>
            <div><dt>固定超时</dt><dd>{{ selected.timeoutMs === null ? '由部署配置决定' : `${selected.timeoutMs} ms` }}</dd></div>
            <div><dt>更新时间</dt><dd>{{ formatTime(selected.updatedAt) }}</dd></div>
          </dl>
        </a-tab-pane>
        <a-tab-pane key="connectivity" tab="连通性">
          <section class="channel-connectivity">
            <div class="connectivity-summary">
              <div><span>最近结果</span><a-tag :color="checkColor(selected.lastCheckStatus)">{{ selected.lastCheckStatus || 'NOT_CHECKED' }}</a-tag></div>
              <div><span>最近检查</span><strong>{{ formatTime(selected.lastCheckAt) }}</strong></div>
            </div>
            <a-alert
              v-if="checkResults[selected.channel]"
              :type="checkResults[selected.channel]?.status === 'SENT' ? 'success' : 'error'"
              show-icon
              :message="checkResults[selected.channel]?.message"
              :description="`Trace ${checkResults[selected.channel]?.traceId} · ${checkResults[selected.channel]?.durationMs} ms`"
            />
            <a-button
              type="primary"
              :loading="mutation === `check:${selected.channel}`"
              :disabled="Boolean(mutation) || !selected.enabled || !selected.available || !selected.configured"
              @click="check(selected)"
            ><PlugZap :size="15" />执行连通性检查</a-button>
          </section>
        </a-tab-pane>
        <a-tab-pane key="security" tab="安全边界">
          <div class="channel-security-boundary">
            <ShieldCheck :size="28" />
            <div><strong>只展示运行所需的脱敏状态</strong><p>页面不会读取 SMTP 密码、Webhook Secret 解析值、完整收件地址、完整 Endpoint 查询参数或外部响应正文。</p></div>
          </div>
        </a-tab-pane>
      </a-tabs>
    </a-drawer>

    <a-modal
      v-model:open="editOpen"
      :title="selected ? `${selected.displayName}设置` : '渠道设置'"
      :confirm-loading="mutation.startsWith('save:')"
      @ok="save"
    >
      <a-form v-if="selected" layout="vertical">
        <a-form-item label="启用渠道"><a-switch v-model:checked="form.enabled" /></a-form-item>
        <template v-if="selected.channel === 'EMAIL'">
          <a-alert type="info" show-icon message="SMTP 连接由部署环境管理" description="本页面不接收主机凭据、用户名或密码；如需变更，请更新受控部署配置后执行连通性检查。" />
        </template>
        <template v-else>
          <a-form-item label="新的 HTTPS Endpoint">
            <a-input
              v-model:value="form.endpoint"
              class="channel-endpoint"
              type="url"
              autocomplete="off"
              :maxlength="2048"
              placeholder="留空保留现有地址；保存后不会回显"
            />
          </a-form-item>
          <a-form-item label="新的 SecretRef">
            <a-input
              v-model:value="form.secretRef"
              class="channel-secret-ref"
              autocomplete="off"
              :maxlength="500"
              placeholder="例如 env://EVENT_WEBHOOK_SECRET；不要输入明文 Secret"
            />
          </a-form-item>
          <a-form-item label="固定超时（毫秒）" required>
            <a-input-number v-model:value="form.timeoutMs" class="channel-timeout" :min="100" :max="30000" :precision="0" />
          </a-form-item>
        </template>
      </a-form>
      <a-alert v-if="editError" class="channel-edit-error" type="error" show-icon :message="editError" />
    </a-modal>
  </section>
</template>

<style scoped>
.event-channel-manager{display:grid;min-width:0;gap:14px}.event-section-heading,.event-section-heading h2,.channel-security-boundary,.connectivity-summary>div{display:flex;align-items:center}.event-section-heading{justify-content:space-between;gap:16px}.event-section-heading h2{margin:0;font-size:17px}.event-section-heading p{margin:4px 0 0;color:#657181}.masked-destination{display:block;max-width:240px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.channel-detail-list{margin:0}.channel-detail-list>div{display:grid;grid-template-columns:116px minmax(0,1fr);gap:14px;padding:12px 0;border-bottom:1px solid #e5eaed}.channel-detail-list dt{color:#697781}.channel-detail-list dd{min-width:0;margin:0;overflow-wrap:anywhere}.channel-connectivity{display:grid;gap:16px}.connectivity-summary{display:grid;grid-template-columns:1fr 1fr;gap:12px}.connectivity-summary>div{justify-content:space-between;gap:10px;padding:12px;border:1px solid #e1e6e9;border-radius:7px}.connectivity-summary span{color:#697781}.channel-security-boundary{align-items:flex-start;gap:14px;padding:16px;border:1px solid #cfe2dc;border-radius:8px;background:#f3faf8;color:#245b52}.channel-security-boundary p{margin:5px 0 0;color:#536d68;line-height:1.65}.channel-timeout{width:100%}.channel-edit-error{margin-top:12px}@media(max-width:720px){.event-section-heading{align-items:flex-start;flex-direction:column}.connectivity-summary{grid-template-columns:minmax(0,1fr)}.channel-detail-list>div{grid-template-columns:90px minmax(0,1fr)}.channel-security-boundary{flex-direction:column}}
</style>
