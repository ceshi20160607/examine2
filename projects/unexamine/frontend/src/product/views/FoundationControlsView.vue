<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ApiError, api } from '../api'
import { systemTokens } from '../session'
import type {
  FoundationCacheProbe,
  FoundationCommandResult,
  FoundationControlOverview,
  FoundationFeatureFlag,
  FoundationFeatureFlagResolution,
  FoundationQuota,
  FoundationSequence,
  FoundationSequenceValue,
} from '../types'
import BackgroundJobsView from './BackgroundJobsView.vue'
import OperationsDeploymentView from './OperationsDeploymentView.vue'
import OperationsHealthView from './OperationsHealthView.vue'
import OperationsContinuityView from './OperationsContinuityView.vue'
import OperationsSecurityPerformanceView from './OperationsSecurityPerformanceView.vue'

const basePath = '/api/admin/system/foundation-control'
const token = computed(() => systemTokens.value?.accessToken)
const overview = ref<FoundationControlOverview>()
const flags = ref<FoundationFeatureFlag[]>([])
const quotas = ref<FoundationQuota[]>([])
const sequences = ref<FoundationSequence[]>([])
const results = ref<FoundationCommandResult[]>([])
const sequenceResult = ref<FoundationSequenceValue>()
const flagResolution = ref<FoundationFeatureFlagResolution>()
const loading = ref(false)
const busy = ref('')
const error = ref('')
const success = ref('')
const retryAfter = ref<number>()
const command = reactive({
  idempotencyKey: `operation-${Date.now()}`,
  applicationCode: 'system-admin',
  operationCode: 'CONFIG_RELOAD',
  reason: '系统运维配置重载',
})
const flagForm = reactive({ flagKey: `feature.flag.${Date.now()}`, enabled: true, targetType: 'SYSTEM', targetCodes: '' })
const quotaForm = reactive({ quotaCode: `SYSTEM_QUOTA_${Date.now()}`, periodType: 'TOTAL', hardLimit: 3, warningThreshold: 2 })
const sequenceForm = reactive({ sequenceCode: `system_sequence_${Date.now()}`, pattern: 'SYS-{yyyyMMdd}-{seq:4}', resetPeriod: 'DAY', stepValue: 1 })

function explain(cause: unknown, fallback: string) {
  if (cause instanceof ApiError) {
    const seconds = Number(cause.details?.retryAfterSeconds)
    retryAfter.value = Number.isFinite(seconds) ? seconds : undefined
    return `${cause.message}（${cause.code}${retryAfter.value !== undefined ? `，${retryAfter.value} 秒后重试` : ''}）`
  }
  return fallback
}

async function load() {
  if (!token.value) return
  loading.value = true
  error.value = ''
  try {
    const [control, featureFlags, quotaPolicies, sequencePolicies] = await Promise.all([
      api<FoundationControlOverview>(basePath, {}, token.value),
      api<FoundationFeatureFlag[]>(`${basePath}/feature-flags`, {}, token.value),
      api<FoundationQuota[]>(`${basePath}/quotas`, {}, token.value),
      api<FoundationSequence[]>(`${basePath}/sequences`, {}, token.value),
    ])
    overview.value = control
    flags.value = featureFlags
    quotas.value = quotaPolicies
    sequences.value = sequencePolicies
  } catch (cause) {
    error.value = explain(cause, '运行控制数据加载失败')
  } finally {
    loading.value = false
  }
}

async function submit(repeat = false) {
  if (!token.value || !command.idempotencyKey.trim()) return
  busy.value = repeat ? 'repeat' : 'command'
  error.value = ''
  success.value = ''
  retryAfter.value = undefined
  try {
    const payload = {
      idempotencyKey: command.idempotencyKey.trim(),
      applicationCode: command.applicationCode.trim(),
      operationCode: command.operationCode,
      payload: { reason: command.reason.trim(), requestedFrom: 'SYSTEM_OPERATIONS_PAGE' },
    }
    const first = await api<FoundationCommandResult>(`${basePath}/commands`, {
      method: 'POST', body: JSON.stringify(payload),
    }, token.value)
    results.value.unshift(first)
    if (repeat) {
      const replayed = await api<FoundationCommandResult>(`${basePath}/commands`, {
        method: 'POST', body: JSON.stringify(payload),
      }, token.value)
      results.value.unshift(replayed)
      if (replayed.resultReference !== first.resultReference || !replayed.replayed) {
        throw new Error('idempotency verification failed')
      }
      success.value = `同一幂等键两次请求均返回 ${first.resultReference}，没有重复创建业务结果。`
    } else {
      success.value = `命令已提交，结果引用 ${first.resultReference}。`
    }
    await load()
  } catch (cause) {
    error.value = explain(cause, '幂等验证没有得到一致结果')
  } finally {
    busy.value = ''
  }
}

async function verifyRateLimit() {
  if (!token.value) return
  busy.value = 'rate'
  error.value = ''
  success.value = ''
  retryAfter.value = undefined
  let denied = false
  let deniedMessage = ''
  try {
    for (let index = 0; index < 7; index += 1) {
      try {
        await api<FoundationCommandResult>(`${basePath}/commands`, {
          method: 'POST',
          body: JSON.stringify({
            idempotencyKey: `rate-${Date.now()}-${index}`,
            applicationCode: command.applicationCode,
            operationCode: command.operationCode,
          payload: { reason: '系统运维限流边界验证', index },
          }),
        }, token.value)
      } catch (cause) {
        if (cause instanceof ApiError && cause.code === 'RATE_LIMIT_EXCEEDED') {
          denied = true
          deniedMessage = explain(cause, '已触发限流')
          break
        }
        throw cause
      }
    }
    if (!denied) throw new Error('rate limit was not reached')
    success.value = '组合维度限流已在页面请求中生效，拒绝结果包含稳定重试时间。'
    await load()
    error.value = deniedMessage
  } catch (cause) {
    error.value = explain(cause, '限流边界验证失败')
  } finally {
    busy.value = ''
  }
}

async function probeCache() {
  if (!token.value) return
  busy.value = 'cache'
  error.value = ''
  try {
    overview.value = { ...overview.value!, permissionCache: await api<FoundationCacheProbe>(`${basePath}/cache/permission`, {}, token.value) }
    success.value = `权限缓存读取来源：${overview.value.permissionCache.source}。`
  } catch (cause) {
    error.value = explain(cause, '权限缓存探测失败')
  } finally { busy.value = '' }
}

async function invalidateCache() {
  if (!token.value) return
  busy.value = 'invalidate'
  error.value = ''
  try {
    const cache = await api<FoundationCacheProbe>(`${basePath}/cache/permission/invalidate`, {
      method: 'POST', body: JSON.stringify({ reason: '系统运维主动刷新权限缓存' }),
    }, token.value)
    overview.value = { ...overview.value!, permissionCache: cache }
    success.value = `权限缓存版本已推进到 ${cache.epoch}，并从 ${cache.source} 安全重建。`
  } catch (cause) {
    error.value = explain(cause, '权限缓存失效失败')
  } finally { busy.value = '' }
}

async function saveFlag() {
  if (!token.value) return
  busy.value = 'flag'
  error.value = ''
  try {
    const saved = await api<FoundationFeatureFlag>(`${basePath}/feature-flags`, {
      method: 'POST', body: JSON.stringify({
        id: null, flagKey: flagForm.flagKey, enabled: flagForm.enabled,
        targetType: flagForm.targetType,
        targetCodes: flagForm.targetCodes.split(',').map(item => item.trim()).filter(Boolean),
        effectiveFrom: null, effectiveUntil: null, fallbackEnabled: false, stableVariant: 'stable', expectedVersion: null,
      }),
    }, token.value)
    flags.value = await api<FoundationFeatureFlag[]>(`${basePath}/feature-flags`, {}, token.value)
    await resolveFlag(saved)
    success.value = `特性开关 ${saved.flagKey} 已保存并读回 v${saved.version}；最终值已按当前范围重新解析。`
  } catch (cause) { error.value = explain(cause, '特性开关保存失败') } finally { busy.value = '' }
}

async function resolveFlag(item: FoundationFeatureFlag) {
  if (!token.value) return
  busy.value = `flag-resolve-${item.id}`
  error.value = ''
  try {
    const targetCode = item.targetCodes[0]
    const query = targetCode ? `?targetCode=${encodeURIComponent(targetCode)}` : ''
    flagResolution.value = await api<FoundationFeatureFlagResolution>(
      `${basePath}/feature-flags/${encodeURIComponent(item.flagKey)}/resolve${query}`,
      {}, token.value,
    )
  } catch (cause) {
    error.value = explain(cause, '特性开关最终值解析失败')
  } finally {
    busy.value = ''
  }
}

async function saveQuota() {
  if (!token.value) return
  busy.value = 'quota'
  error.value = ''
  try {
    const saved = await api<FoundationQuota>(`${basePath}/quotas`, {
      method: 'POST', body: JSON.stringify({ id: null, ...quotaForm, expectedVersion: null }),
    }, token.value)
    quotas.value = await api<FoundationQuota[]>(`${basePath}/quotas`, {}, token.value)
    success.value = `配额 ${saved.quotaCode} 已建立，当前剩余 ${saved.remaining}。`
  } catch (cause) { error.value = explain(cause, '配额保存失败') } finally { busy.value = '' }
}

async function consumeQuota(item: FoundationQuota) {
  if (!token.value) return
  busy.value = `quota-${item.id}`
  error.value = ''
  try {
    const saved = await api<FoundationQuota>(`${basePath}/quotas/${item.id}/consume`, {
      method: 'POST', body: JSON.stringify({ amount: 1, reference: 'SYSTEM_OPERATIONS_PAGE' }),
    }, token.value)
    quotas.value = await api<FoundationQuota[]>(`${basePath}/quotas`, {}, token.value)
    success.value = `原子占用成功：${saved.usedValue}/${saved.hardLimit}，剩余 ${saved.remaining}。`
  } catch (cause) { error.value = explain(cause, '配额占用被拒绝') } finally { busy.value = '' }
}

async function saveSequence() {
  if (!token.value) return
  busy.value = 'sequence'
  error.value = ''
  try {
    const saved = await api<FoundationSequence>(`${basePath}/sequences`, {
      method: 'POST', body: JSON.stringify({ id: null, ...sequenceForm, expectedVersion: null }),
    }, token.value)
    sequences.value = await api<FoundationSequence[]>(`${basePath}/sequences`, {}, token.value)
    success.value = `序列 ${saved.sequenceCode} 已建立。`
  } catch (cause) { error.value = explain(cause, '序列保存失败') } finally { busy.value = '' }
}

async function nextSequence(item: FoundationSequence) {
  if (!token.value) return
  busy.value = `sequence-${item.id}`
  error.value = ''
  try {
    sequenceResult.value = await api<FoundationSequenceValue>(`${basePath}/sequences/${item.id}/next`, { method: 'POST' }, token.value)
    sequences.value = await api<FoundationSequence[]>(`${basePath}/sequences`, {}, token.value)
    success.value = `已从数据库并发安全分配 ${sequenceResult.value.value}。`
  } catch (cause) { error.value = explain(cause, '序列分配失败') } finally { busy.value = '' }
}

onMounted(load)
</script>

<template>
  <div class="foundation-controls" :class="{ 'is-loading': loading }">
    <OperationsHealthView />
    <OperationsDeploymentView />
    <OperationsContinuityView />
    <OperationsSecurityPerformanceView />
    <a-alert v-if="error" type="error" show-icon :message="error" closable @close="error = ''" />
    <a-alert v-if="success" type="success" show-icon :message="success" closable @close="success = ''" />

    <section class="foundation-metrics">
      <article class="panel-card"><small>权限缓存来源</small><strong>{{ overview?.permissionCache.source || '—' }}</strong><span>epoch {{ overview?.permissionCache.epoch ?? '—' }}</span></article>
      <article class="panel-card"><small>当前权限项</small><strong>{{ overview?.permissionCache.permissionCount ?? '—' }}</strong><span>{{ overview?.permissionCache.roleIds.length ?? 0 }} 个角色合并</span></article>
      <article class="panel-card"><small>限流余量</small><strong>{{ overview ? Math.max(0, overview.rateLimit.limit - overview.rateLimit.current) : '—' }}/{{ overview?.rateLimit.limit ?? '—' }}</strong><span>{{ overview?.rateLimit.source === 'REDIS' ? 'Redis 原子计数正常' : '安全拒绝模式' }}</span></article>
      <article class="panel-card"><small>幂等记录</small><strong>{{ overview?.recentCommands.length ?? 0 }}</strong><span>当前账号/系统/租户/应用</span></article>
    </section>

    <section class="panel-card foundation-section">
      <div class="panel-title"><div><strong>幂等命令与组合限流</strong><small>相同主体、范围和键只产生一个结果；失败响应给出可执行的重试时间。</small></div></div>
      <a-form layout="vertical" class="foundation-form foundation-form--command">
        <a-form-item label="幂等键"><a-input v-model:value="command.idempotencyKey" /></a-form-item>
        <a-form-item label="应用编码"><a-input v-model:value="command.applicationCode" /></a-form-item>
        <a-form-item label="运行动作"><a-select v-model:value="command.operationCode" :options="[{ value: 'CONFIG_RELOAD', label: '配置重载' }, { value: 'PERMISSION_CONTEXT_REFRESH', label: '权限上下文刷新' }]" /></a-form-item>
        <a-form-item label="原因"><a-input v-model:value="command.reason" /></a-form-item>
        <div class="foundation-actions">
          <a-button :loading="busy === 'command'" @click="submit(false)">执行一次</a-button>
          <a-button type="primary" :loading="busy === 'repeat'" @click="submit(true)">同键执行两次并验收</a-button>
          <a-button danger :loading="busy === 'rate'" @click="verifyRateLimit">验证限流边界</a-button>
        </div>
      </a-form>
      <div v-if="results.length" class="foundation-result-list">
        <article v-for="(item, index) in results.slice(0, 4)" :key="`${item.outboxId}-${index}`">
          <a-tag :color="item.replayed ? 'blue' : 'green'">{{ item.replayed ? '复用首次结果' : '首次提交' }}</a-tag>
          <strong>{{ item.resultReference }}</strong><small>outbox #{{ item.outboxId }} · {{ item.extensionStatus }}</small>
        </article>
      </div>
    </section>

    <section class="panel-card foundation-section">
      <div class="panel-title"><div><strong>权限缓存失效</strong><small>版本存储在数据库；Redis 丢失或内容损坏时回源，不扩大任何权限。</small></div><div class="foundation-actions"><a-button :loading="busy === 'cache'" @click="probeCache">再次探测</a-button><a-button type="primary" :loading="busy === 'invalidate'" @click="invalidateCache">推进版本并重建</a-button></div></div>
      <div class="foundation-cache-key"><span>当前隔离键</span><code>{{ overview?.permissionCache.cacheKey || '—' }}</code></div>
    </section>

    <section class="foundation-policy-grid">
      <article class="panel-card foundation-section">
        <div class="panel-title"><div><strong>特性开关</strong><small>结构化目标、时间窗、回退和版本</small></div></div>
        <div class="foundation-mini-form"><a-input v-model:value="flagForm.flagKey" placeholder="flag key" /><a-select v-model:value="flagForm.targetType" :options="['SYSTEM','APPLICATION','MODULE','ROLE'].map(value => ({ value, label: value }))" /><a-input v-model:value="flagForm.targetCodes" placeholder="目标编码，逗号分隔；空为全部" /><a-switch v-model:checked="flagForm.enabled" /><a-button type="primary" :loading="busy === 'flag'" @click="saveFlag">保存</a-button></div>
        <a-alert
          v-if="flagResolution"
          type="info"
          show-icon
          :message="`${flagResolution.flagKey} 最终为${flagResolution.enabled ? '开启' : '关闭'} · 来源 ${flagResolution.source}`"
          :description="`${flagResolution.reason}；权限仍需独立校验：${flagResolution.permissionStillRequired ? '是' : '否'}；v${flagResolution.version}`"
        />
        <div class="foundation-policy-list"><div v-for="item in flags.slice(0, 5)" :key="item.id"><strong>{{ item.flagKey }}</strong><span>{{ item.targetType }} · v{{ item.version }}</span><a-tag :color="item.enabled ? 'green' : 'default'">{{ item.enabled ? '开启' : '关闭' }}</a-tag><a-button size="small" :loading="busy === `flag-resolve-${item.id}`" @click="resolveFlag(item)">解析最终值</a-button></div></div>
      </article>

      <article class="panel-card foundation-section">
        <div class="panel-title"><div><strong>系统业务配额</strong><small>硬边界原子占用，降低上限不会删除历史数据</small></div></div>
        <div class="foundation-mini-form"><a-input v-model:value="quotaForm.quotaCode" /><a-select v-model:value="quotaForm.periodType" :options="['TOTAL','DAY','MONTH'].map(value => ({ value, label: value }))" /><a-input-number v-model:value="quotaForm.hardLimit" :min="1" /><a-input-number v-model:value="quotaForm.warningThreshold" :min="0" /><a-button type="primary" :loading="busy === 'quota'" @click="saveQuota">建立配额</a-button></div>
        <div class="foundation-policy-list"><div v-for="item in quotas.slice(0, 5)" :key="item.id"><strong>{{ item.quotaCode }}</strong><span>{{ item.usedValue }}/{{ item.hardLimit }} · 剩余 {{ item.remaining }}</span><a-button size="small" :loading="busy === `quota-${item.id}`" @click="consumeQuota(item)">占用 1</a-button></div></div>
      </article>

      <article class="panel-card foundation-section foundation-section--wide">
        <div class="panel-title"><div><strong>业务序列</strong><small>独立序列表并发分配，不使用 max+1</small></div><a-tag v-if="sequenceResult" color="blue">最新：{{ sequenceResult.value }}</a-tag></div>
        <div class="foundation-mini-form foundation-mini-form--sequence"><a-input v-model:value="sequenceForm.sequenceCode" /><a-input v-model:value="sequenceForm.pattern" /><a-select v-model:value="sequenceForm.resetPeriod" :options="['NONE','DAY','MONTH','YEAR'].map(value => ({ value, label: value }))" /><a-input-number v-model:value="sequenceForm.stepValue" :min="1" /><a-button type="primary" :loading="busy === 'sequence'" @click="saveSequence">建立序列</a-button></div>
        <div class="foundation-policy-list"><div v-for="item in sequences.slice(0, 5)" :key="item.id"><strong>{{ item.sequenceCode }}</strong><span>{{ item.pattern }} · 当前 {{ item.currentValue }}</span><a-button size="small" :loading="busy === `sequence-${item.id}`" @click="nextSequence(item)">取下一个</a-button></div></div>
      </article>
    </section>
    <BackgroundJobsView />
  </div>
</template>
