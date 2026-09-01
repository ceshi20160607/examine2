<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError, api } from '../api'
import { observedStageCount, readinessLabel, safeRequestId } from '../operations-health'
import { systemTokens } from '../session'
import type { OperationsHealth, OperationsObservation } from '../types'

const basePath = '/api/admin/system/operations'
const token = computed(() => systemTokens.value?.accessToken)
const route = useRoute()
const router = useRouter()
const health = ref<OperationsHealth>()
const observation = ref<OperationsObservation>()
const requestId = ref(String(route.query.requestId || ''))
const loading = ref(false)
const checking = ref(false)
const searching = ref(false)
const error = ref('')
const success = ref('')

const passed = computed(() => health.value?.items.filter(item => item.status === 'PASSED').length ?? 0)
const failed = computed(() => health.value?.items.filter(item => item.status === 'FAILED').length ?? 0)
const jobsActive = computed(() => (health.value?.jobs.queued ?? 0) + (health.value?.jobs.running ?? 0))

function explain(cause: unknown, fallback: string) {
  return cause instanceof ApiError
    ? `${cause.message}（${cause.code}${cause.traceId ? ` · requestId ${cause.traceId}` : ''}）`
    : fallback
}

async function load() {
  if (!token.value) return
  loading.value = true
  error.value = ''
  try {
    const latest = await api<OperationsHealth | null>(`${basePath}/health`, {}, token.value)
    if (latest) health.value = latest
    else await recheck(false)
  } catch (cause) {
    error.value = explain(cause, '系统体检加载失败')
  } finally {
    loading.value = false
  }
}

async function recheck(showSuccess = true) {
  if (!token.value) return
  checking.value = true
  error.value = ''
  success.value = ''
  try {
    health.value = await api<OperationsHealth>(`${basePath}/health/recheck`, { method: 'POST' }, token.value)
    requestId.value = health.value.requestId
    if (showSuccess) {
      success.value = health.value.ready
        ? `体检 #${health.value.id} 已持久化读回，5 项关键依赖全部通过。`
        : `体检 #${health.value.id} 已持久化读回；关键依赖失败，当前禁止宣告就绪。`
    }
    await search(false)
  } catch (cause) {
    error.value = explain(cause, '依赖重检失败，不能确认环境就绪')
  } finally {
    checking.value = false
  }
}

async function search(updateRoute = true) {
  if (!token.value || !safeRequestId(requestId.value)) {
    if (requestId.value) error.value = 'requestId 必须是 8 到 64 位字母、数字、下划线或短横线。'
    return
  }
  searching.value = true
  error.value = ''
  try {
    observation.value = await api<OperationsObservation>(
      `${basePath}/observability?requestId=${encodeURIComponent(requestId.value.trim())}`, {}, token.value,
    )
    if (updateRoute) await router.replace({ query: { ...route.query, requestId: requestId.value.trim() } })
  } catch (cause) {
    error.value = explain(cause, 'requestId 观测查询失败')
  } finally {
    searching.value = false
  }
}

function searchError(value: string) {
  if (!value) return
  requestId.value = value
  void search()
}

function itemMetric(item: { metric: Record<string, unknown> }) {
  const entries = Object.entries(item.metric).filter(([key]) => key !== 'valueExposed')
  return entries.map(([key, value]) => `${key}: ${String(value)}`).join(' · ') || '已执行真实探测'
}

function metricLabel(key: string) {
  return ({
    requestCount: '请求日志', serverDurationMillis: '服务耗时(ms)', auditCount: '审计事件', jobCount: '关联作业',
    applicationCallCount: '应用调用', flowEventCount: '流程事件', healthCheckCount: '依赖检查',
  } as Record<string, string>)[key] || key
}

onMounted(async () => {
  await load()
  if (requestId.value && requestId.value !== health.value?.requestId) await search(false)
})
</script>

<template>
  <section class="operations-health" :class="{ 'is-loading': loading }">
    <a-alert v-if="error" type="error" show-icon :message="error" closable @close="error = ''" />
    <a-alert v-if="success" :type="health?.ready ? 'success' : 'warning'" show-icon :message="success" closable @close="success = ''" />

    <div class="operations-health__hero panel-card" :data-ready="health?.ready ? 'true' : 'false'">
      <div>
        <p class="eyebrow">就绪门禁 · 真实依赖</p>
        <h2>{{ readinessLabel(health) }}</h2>
        <p v-if="health">体检 #{{ health.id }} · {{ health.finishedAt }} · requestId {{ health.requestId }}</p>
        <p v-else>尚无持久化体检结果，页面会从管理 API 发起首次检查。</p>
      </div>
      <div class="operations-health__hero-actions">
        <a-tag :color="health?.ready ? 'green' : 'red'">{{ health?.status || 'UNKNOWN' }}</a-tag>
        <a-button type="primary" :danger="health && !health.ready" :loading="checking" @click="recheck()">立即重检并读回</a-button>
      </div>
    </div>

    <div class="operations-health__metrics">
      <article class="panel-card"><small>关键依赖</small><strong>{{ passed }}/{{ health?.items.length ?? 5 }}</strong><span>{{ failed ? `${failed} 项阻断` : '全部通过' }}</span></article>
      <article class="panel-card"><small>服务版本</small><strong>{{ health?.releaseVersion || '—' }}</strong><span>当前运行实例</span></article>
      <article class="panel-card"><small>数据库迁移</small><strong>V{{ health?.migrationVersion || '—' }}</strong><span>Flyway 实际版本</span></article>
      <article class="panel-card"><small>后台作业</small><strong>{{ jobsActive }}</strong><span>排队/运行 · {{ health?.jobs.failed ?? 0 }} 失败</span></article>
    </div>

    <section class="panel-card operations-dependencies">
      <div class="panel-title">
        <div><strong>数据库、Redis、文件存储、密钥与迁移</strong><small>每项结果、时间和指标均来自本次真实探测；页面只显示密钥配置状态，不读取或展示密钥值。</small></div>
      </div>
      <div class="operations-dependency-grid">
        <article v-for="item in health?.items" :key="item.id" :data-status="item.status">
          <header><span class="operations-status-dot" /><strong>{{ item.name }}</strong><a-tag :color="item.status === 'PASSED' ? 'green' : 'red'">{{ item.status === 'PASSED' ? '通过' : '失败' }}</a-tag></header>
          <p>{{ item.message }}</p>
          <small>{{ item.checkedAt }} · {{ itemMetric(item) }}</small>
        </article>
      </div>
    </section>

    <section class="panel-card operations-observation">
      <div class="panel-title operations-observation__heading">
        <div><strong>请求全链路观测</strong><small>从同一入口查看结构化请求日志、指标、作业、应用调用、流程和不可删除审计。</small></div>
        <div class="operations-request-search"><a-input v-model:value="requestId" placeholder="输入页面或错误 requestId" @press-enter="search()" /><a-button type="primary" :loading="searching" @click="search()">查询链路</a-button></div>
      </div>
      <template v-if="observation">
        <div class="operations-stage-strip">
          <article v-for="stage in observation.stages" :key="stage.code" :class="{ observed: stage.observed }">
            <span>{{ stage.observed ? '✓' : '—' }}</span><strong>{{ stage.name }}</strong><small>{{ stage.evidence }}</small>
          </article>
        </div>
        <a-alert
          :type="observation.observable ? 'info' : 'warning'"
          show-icon
          :message="observation.observable ? `已观测 ${observedStageCount(observation)}/${observation.stages.length} 个链路阶段` : '没有可读回的观测证据'"
          :description="`requestId ${observation.requestId} · system ${observation.systemId} · tenant ${observation.tenantId} · user ${observation.accountId || '—'}`"
        />
        <div class="operations-observation-grid">
          <div class="operations-log-list">
            <strong>结构化日志与追踪</strong>
            <article v-for="(item, index) in observation.logs" :key="`${item.source}-${index}`"><a-tag>{{ item.source }}</a-tag><span><b>{{ item.summary }}</b><small>{{ item.kind }} · {{ item.status }} · {{ item.occurredAt }}</small></span></article>
            <a-empty v-if="!observation.logs.length" description="该 requestId 没有匹配日志" />
          </div>
          <div class="operations-observation-metrics">
            <strong>关联指标</strong>
            <div><article v-for="(value, key) in observation.metrics" :key="key"><small>{{ metricLabel(String(key)) }}</small><b>{{ value }}</b></article></div>
          </div>
        </div>
        <div class="operations-related-counts">
          <span>后台作业 <b>{{ observation.jobs.length }}</b></span><span>应用调用 <b>{{ observation.applicationCalls.length }}</b></span><span>流程事件 <b>{{ observation.flowEvents.length }}</b></span><span>审计事件 <b>{{ observation.audits.length }}</b></span>
        </div>
      </template>
      <a-empty v-else description="执行体检或输入 requestId，查看真实链路证据。" />
    </section>

    <section class="panel-card operations-errors">
      <div class="panel-title"><div><strong>最近错误</strong><small>脱敏摘要按当前系统与租户隔离；点击 requestId 进入同一观测查询。</small></div></div>
      <div v-if="health?.recentErrors.length" class="operations-error-list">
        <button v-for="item in health.recentErrors" :key="`${item.source}-${item.targetId}-${item.occurredAt}`" type="button" @click="searchError(item.requestId)">
          <a-tag color="red">{{ item.source }}</a-tag><span><strong>{{ item.code }}</strong><small>{{ item.message }} · {{ item.occurredAt }}</small></span><code>{{ item.requestId || '无关联 requestId' }}</code>
        </button>
      </div>
      <a-empty v-else description="当前范围没有最近错误" />
    </section>
  </section>
</template>
