<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ApiError, api } from '../api'
import { systemTokens } from '../session'
import type { BackgroundJob, BackgroundJobDetail, BackgroundJobHandler } from '../types'

const basePath = '/api/admin/system/background-jobs'
const token = computed(() => systemTokens.value?.accessToken)
const handlers = ref<BackgroundJobHandler[]>([])
const jobs = ref<BackgroundJob[]>([])
const selected = ref<BackgroundJobDetail>()
const loading = ref(false)
const busy = ref('')
const error = ref('')
const success = ref('')
let timer: number | undefined

const activeCount = computed(() => jobs.value.filter(item => ['QUEUED', 'RUNNING', 'RETRY_WAIT'].includes(item.status)).length)
const statusMeta: Record<string, { text: string; color: string }> = {
  QUEUED: { text: '待执行', color: 'blue' }, RUNNING: { text: '运行中', color: 'processing' },
  RETRY_WAIT: { text: '等待重试', color: 'orange' }, SUCCEEDED: { text: '成功', color: 'green' },
  FAILED: { text: '失败', color: 'red' }, PERMANENT_FAILED: { text: '永久失败', color: 'red' },
  TIMED_OUT: { text: '心跳超时', color: 'volcano' },
}

function explain(cause: unknown, fallback: string) {
  return cause instanceof ApiError ? `${cause.message}（${cause.code}）` : fallback
}
function progress(item: BackgroundJob) {
  if (!item.progressTotal) return item.status === 'SUCCEEDED' ? 100 : 0
  return Math.min(100, Math.round(item.progressCurrent * 100 / item.progressTotal))
}
async function load(silent = false) {
  if (!token.value) return
  if (!silent) loading.value = true
  try {
    const [available, current] = await Promise.all([
      api<BackgroundJobHandler[]>(`${basePath}/handlers`, {}, token.value),
      api<BackgroundJob[]>(basePath, {}, token.value),
    ])
    handlers.value = available
    jobs.value = current
    if (selected.value) selected.value = await api<BackgroundJobDetail>(`${basePath}/${selected.value.job.id}`, {}, token.value)
  } catch (cause) {
    if (!silent) error.value = explain(cause, '后台作业加载失败')
  } finally { if (!silent) loading.value = false }
}
async function submit(jobType: string) {
  if (!token.value) return
  busy.value = jobType; error.value = ''; success.value = ''
  try {
    const detail = await api<BackgroundJobDetail>(basePath, {
      method: 'POST', body: JSON.stringify({ jobType, sourceType: 'SYSTEM_OPERATIONS',
        sourceId: `operations-${Date.now()}`, parameters: { requestedFrom: 'SYSTEM_OPERATIONS_PAGE' }, maxAttempts: 3 }),
    }, token.value)
    selected.value = detail
    success.value = `作业 #${detail.job.id} 已进入 Redis 队列，页面会持续读回真实进度。`
    await load(true)
  } catch (cause) { error.value = explain(cause, '后台作业提交失败') } finally { busy.value = '' }
}
async function open(item: BackgroundJob) {
  if (!token.value) return
  busy.value = `detail-${item.id}`; error.value = ''
  try { selected.value = await api<BackgroundJobDetail>(`${basePath}/${item.id}`, {}, token.value) }
  catch (cause) { error.value = explain(cause, '作业详情加载失败') } finally { busy.value = '' }
}
async function retry() {
  if (!token.value || !selected.value) return
  busy.value = `retry-${selected.value.job.id}`; error.value = ''; success.value = ''
  try {
    selected.value = await api<BackgroundJobDetail>(`${basePath}/${selected.value.job.id}/retry`, {
      method: 'POST', body: JSON.stringify({ reason: '运维页面检查失败项后请求立即重试' }),
    }, token.value)
    success.value = `作业 #${selected.value.job.id} 已重新入队；最大尝试次数仍为 ${selected.value.job.maxAttempts}，不会无限循环。`
    await load(true)
  } catch (cause) { error.value = explain(cause, '当前作业不能重试') } finally { busy.value = '' }
}

onMounted(async () => {
  await load()
  timer = window.setInterval(() => { if (activeCount.value || selected.value) void load(true) }, 1000)
})
onBeforeUnmount(() => { if (timer !== undefined) window.clearInterval(timer) })
</script>

<template>
  <section class="panel-card foundation-section background-jobs" :class="{ 'is-loading': loading }">
    <div class="panel-title"><div><strong>Redis 后台作业</strong><small>队列、工作锁、实时状态和有限重试由 Redis 协调；MySQL 保存作业、心跳、尝试和逐项结果。</small></div><div class="foundation-actions"><a-tag :color="activeCount ? 'processing' : 'default'">{{ activeCount }} 个活动作业</a-tag><a-button size="small" @click="load()">刷新</a-button></div></div>
    <a-alert v-if="error" type="error" show-icon :message="error" closable @close="error = ''" />
    <a-alert v-if="success" type="success" show-icon :message="success" closable @close="success = ''" />
    <div class="job-launchers"><article v-for="handler in handlers" :key="handler.jobType"><div><strong>{{ handler.name }}</strong><small>{{ handler.description }}</small></div><a-button type="primary" :loading="busy === handler.jobType" @click="submit(handler.jobType)">提交作业</a-button></article></div>
    <div class="job-workspace">
      <div class="job-list">
        <button v-for="item in jobs" :key="item.id" :class="{ active: selected?.job.id === item.id }" @click="open(item)"><span class="job-list-heading"><strong>#{{ item.id }} · {{ handlers.find(handler => handler.jobType === item.jobType)?.name || item.jobType }}</strong><a-tag :color="statusMeta[item.status]?.color || 'default'">{{ statusMeta[item.status]?.text || item.status }}</a-tag></span><a-progress :percent="progress(item)" size="small" :status="item.status === 'PERMANENT_FAILED' ? 'exception' : undefined" /><small>{{ item.progressCurrent }}/{{ item.progressTotal }} 项 · 第 {{ item.attemptCount }}/{{ item.maxAttempts }} 次 · {{ item.redisState.source }}</small></button>
        <a-empty v-if="!jobs.length" description="还没有后台作业，从上方真实入口提交。" />
      </div>
      <div v-if="selected" class="job-detail">
        <div class="job-detail-title"><div><strong>作业 #{{ selected.job.id }}</strong><small>{{ selected.job.jobType }} · {{ selected.job.sourceType }}</small></div><a-button v-if="selected.job.status === 'RETRY_WAIT'" danger :loading="busy === `retry-${selected.job.id}`" @click="retry">检查后立即重试</a-button><a-tag v-else-if="selected.job.status === 'PERMANENT_FAILED'" color="red">尝试次数已耗尽，禁止无限重试</a-tag></div>
        <a-descriptions size="small" bordered :column="2"><a-descriptions-item label="状态"><a-tag :color="statusMeta[selected.job.status]?.color">{{ statusMeta[selected.job.status]?.text }}</a-tag></a-descriptions-item><a-descriptions-item label="进度">{{ selected.job.progressCurrent }}/{{ selected.job.progressTotal }}</a-descriptions-item><a-descriptions-item label="Redis 队列">{{ selected.job.redisState.queued ? '已排队' : '未排队' }}</a-descriptions-item><a-descriptions-item label="工作锁">{{ selected.job.redisState.locked ? '已领取' : '未占用' }}</a-descriptions-item><a-descriptions-item label="心跳">{{ selected.job.heartbeatAt || '尚未开始' }}</a-descriptions-item><a-descriptions-item label="下次执行">{{ selected.job.nextRunAt || '—' }}</a-descriptions-item></a-descriptions>
        <a-alert v-if="selected.job.errorCode" type="error" show-icon :message="`${selected.job.errorCode}：${selected.job.errorMessage}`" />
        <div class="job-result-block"><strong>逐项结果</strong><div v-for="item in selected.items" :key="item.id"><span>{{ item.rowNumber ?? '—' }} · {{ item.itemKey }}</span><a-tag :color="statusMeta[item.status]?.color || (item.status === 'SUCCEEDED' ? 'green' : 'red')">{{ statusMeta[item.status]?.text || item.status }}</a-tag><small>{{ item.errorMessage || JSON.stringify(item.result) }}</small></div><a-empty v-if="!selected.items.length" description="处理器尚未写入逐项结果" /></div>
        <div class="job-result-block"><strong>尝试记录</strong><div v-for="attempt in selected.attempts" :key="attempt.id"><span>第 {{ attempt.attemptNumber }} 次 · {{ attempt.workerId }}</span><a-tag :color="statusMeta[attempt.status]?.color || 'default'">{{ statusMeta[attempt.status]?.text || attempt.status }}</a-tag><small>{{ attempt.errorMessage || `${attempt.startedAt} → ${attempt.finishedAt || '运行中'}` }}</small></div></div>
      </div>
      <a-empty v-else description="选择一个作业查看心跳、尝试次数和逐项结果。" class="job-detail-empty" />
    </div>
  </section>
</template>
