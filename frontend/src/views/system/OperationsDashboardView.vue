<script setup lang="ts">
import { BarChart3, CalendarRange, ExternalLink, RefreshCw } from 'lucide-vue-next'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import OperationsSeriesChart from '@/components/analytics/OperationsSeriesChart.vue'
import { ApiRequestError } from '@/services/api'
import { analyticsApi } from '@/services/analytics'
import { useSessionStore } from '@/stores/session'
import type { OperationsAnalyticsSnapshot } from '@/types/analytics'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const systemId = computed(() => String(route.params.systemId))
const tenantId = computed(() => session.context?.tenantId ?? '')
const presetDays = ref<7 | 30>(7)
const from = ref('')
const to = ref('')
const snapshot = ref<OperationsAnalyticsSnapshot | null>(null)
const loading = ref(false)
const error = ref('')
let loadGeneration = 0

const workHasActivity = computed(() => {
  const section = snapshot.value?.work
  if (!section?.available) return false
  return [
    section.openCount,
    section.overdueOpenCount,
    section.dueInRangeOpenCount,
    section.completedInRangeCount,
  ].some(count => count > 0)
    || section.daily.some(day => day.created > 0 || day.completed > 0)
    || section.topAssignees.length > 0
})

const flowHasActivity = computed(() => {
  const section = snapshot.value?.flow
  if (!section?.available) return false
  return section.pendingCount > 0
    || section.daily.some(day => day.started > 0 || day.terminal > 0)
    || section.terminalBreakdown.some(item => item.count > 0)
})

const todoHasActivity = computed(() => {
  const section = snapshot.value?.todo
  return Boolean(section?.available && [
    section.openCount,
    section.taskCount,
    section.approvalCount,
    section.todayCount,
    section.overdueCount,
  ].some(count => count > 0))
})

const allUnavailable = computed(() => Boolean(snapshot.value
  && !snapshot.value.work.available
  && !snapshot.value.flow.available
  && !snapshot.value.todo.available))

function utcDate(value: Date) {
  return value.toISOString().slice(0, 10)
}

function presetRange(days: 7 | 30) {
  const now = new Date()
  const today = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()))
  const exclusiveTo = new Date(today)
  exclusiveTo.setUTCDate(exclusiveTo.getUTCDate() + 1)
  const inclusiveFrom = new Date(exclusiveTo)
  inclusiveFrom.setUTCDate(inclusiveFrom.getUTCDate() - days)
  return { from: utcDate(inclusiveFrom), to: utcDate(exclusiveTo) }
}

function selectPreset(days: 7 | 30) {
  presetDays.value = days
  const range = presetRange(days)
  from.value = range.from
  to.value = range.to
  void loadDashboard()
}

async function loadDashboard() {
  const generation = ++loadGeneration
  loading.value = true
  error.value = ''
  try {
    const result = await analyticsApi.operations(systemId.value, from.value, to.value)
    if (generation !== loadGeneration) return
    snapshot.value = result
  } catch (cause) {
    if (generation !== loadGeneration) return
    snapshot.value = null
    error.value = requestMessage(cause)
  } finally {
    if (generation === loadGeneration) loading.value = false
  }
}

async function drill(routeHint: string | null) {
  if (routeHint) await router.push(routeHint)
}

function requestMessage(cause: unknown) {
  if (cause instanceof ApiRequestError) return cause.message || cause.code
  return cause instanceof Error ? cause.message : '运营看板加载失败，请稍后重试'
}

function generatedTime(value: string) {
  const parsed = new Date(value)
  return Number.isNaN(parsed.valueOf()) ? value : parsed.toLocaleString('zh-CN')
}

function terminalLabel(status: string) {
  if (status === 'APPROVED') return '已通过'
  if (status === 'REJECTED') return '已驳回'
  if (status === 'WITHDRAWN') return '已撤回'
  return '已终止'
}

function metricLabel(count: number) {
  return count.toLocaleString('zh-CN')
}

function resetContext() {
  snapshot.value = null
  error.value = ''
  presetDays.value = 7
  const range = presetRange(7)
  from.value = range.from
  to.value = range.to
}

watch([systemId, tenantId], () => {
  resetContext()
  void loadDashboard()
}, { immediate: true, flush: 'sync' })
</script>

<template>
  <section class="operations-page">
    <header class="operations-heading">
      <div>
        <h1><BarChart3 :size="24" />运营看板</h1>
        <p>按当前系统、租户和成员权限汇总 Work、Flow 与个人待办的实时快照。</p>
      </div>
      <div class="operations-actions">
        <div class="range-presets" aria-label="统计周期">
          <a-button
            class="operations-range-7"
            :type="presetDays === 7 ? 'primary' : 'default'"
            @click="selectPreset(7)"
          >近 7 日</a-button>
          <a-button
            class="operations-range-30"
            :type="presetDays === 30 ? 'primary' : 'default'"
            @click="selectPreset(30)"
          >近 30 日</a-button>
        </div>
        <a-button class="operations-refresh" :loading="loading" @click="loadDashboard">
          <RefreshCw :size="16" />刷新
        </a-button>
      </div>
    </header>

    <div class="operations-range-summary">
      <CalendarRange :size="16" />
      <span>{{ snapshot?.range.from ?? from }} 至 {{ snapshot?.range.to ?? to }}（结束日期不包含）</span>
      <span v-if="snapshot">{{ snapshot.range.days }} 天 · 生成于 {{ generatedTime(snapshot.generatedAt) }}</span>
    </div>

    <a-alert
      v-if="error"
      class="operations-error"
      type="error"
      show-icon
      closable
      :message="error"
      @close="error = ''"
    />

    <a-spin :spinning="loading">
      <a-empty v-if="!snapshot && !loading && !error" description="暂无运营快照" />
      <a-alert
        v-else-if="allUnavailable"
        class="operations-unavailable"
        type="warning"
        show-icon
        message="当前身份无权读取 Work、Flow 和 Todo 运营指标，数据不可用而不是零。"
      />
      <div v-else-if="snapshot" class="operations-sections">
        <section class="operations-section work-operations" :data-available="snapshot.work.available">
          <header>
            <div><h2>Work 任务</h2><p>当前可见任务快照与所选区间活动。</p></div>
            <a-button v-if="snapshot.work.available && snapshot.work.route" @click="drill(snapshot.work.route)">查看全部</a-button>
          </header>
          <a-alert
            v-if="!snapshot.work.available"
            class="work-unavailable"
            type="warning"
            show-icon
            :message="snapshot.work.unavailableReason || '当前身份无权读取 Work 指标。'"
          />
          <a-empty v-else-if="!workHasActivity" class="work-empty" description="所选区间没有可见 Work 活动" />
          <template v-else>
            <div class="kpi-grid">
              <button class="kpi-card work-open" type="button" @click="drill(snapshot.work.openRoute)">
                <span>待处理</span><strong>{{ metricLabel(snapshot.work.openCount) }}</strong><small>查看任务 <ExternalLink :size="12" /></small>
              </button>
              <button class="kpi-card work-overdue" type="button" @click="drill(snapshot.work.overdueOpenRoute)">
                <span>逾期待处理</span><strong>{{ metricLabel(snapshot.work.overdueOpenCount) }}</strong><small>查看逾期 <ExternalLink :size="12" /></small>
              </button>
              <button class="kpi-card work-due-range" type="button" @click="drill(snapshot.work.dueInRangeOpenRoute)">
                <span>区间内到期</span><strong>{{ metricLabel(snapshot.work.dueInRangeOpenCount) }}</strong><small>查看到期任务 <ExternalLink :size="12" /></small>
              </button>
              <button class="kpi-card work-completed" type="button" @click="drill(snapshot.work.completedInRangeRoute)">
                <span>区间内完成</span><strong>{{ metricLabel(snapshot.work.completedInRangeCount) }}</strong><small>查看已完成 <ExternalLink :size="12" /></small>
              </button>
            </div>
            <div class="analytics-grid">
              <article class="chart-card work-daily-chart">
                <h3>任务每日趋势</h3>
                <OperationsSeriesChart
                  :categories="snapshot.work.daily.map(item => item.date)"
                  :series="[
                    { name: '创建', data: snapshot.work.daily.map(item => item.created) },
                    { name: '完成', data: snapshot.work.daily.map(item => item.completed) },
                  ]"
                />
              </article>
              <article class="breakdown-card work-assignees">
                <h3>待处理负责人 Top 20</h3>
                <a-empty v-if="!snapshot.work.topAssignees.length" description="没有负责人分布" />
                <button
                  v-for="item in snapshot.work.topAssignees"
                  :key="item.memberId"
                  class="breakdown-row"
                  type="button"
                  @click="drill(item.route)"
                ><span>成员 {{ item.memberId }}</span><strong>{{ item.openCount }}</strong></button>
              </article>
            </div>
          </template>
        </section>

        <section class="operations-section flow-operations" :data-available="snapshot.flow.available">
          <header>
            <div><h2>Flow 审批</h2><p>当前可见审批实例和区间内终态活动。</p></div>
            <a-button v-if="snapshot.flow.available && snapshot.flow.route" @click="drill(snapshot.flow.route)">查看全部</a-button>
          </header>
          <a-alert
            v-if="!snapshot.flow.available"
            class="flow-unavailable"
            type="warning"
            show-icon
            :message="snapshot.flow.unavailableReason || '当前身份无权读取 Flow 指标。'"
          />
          <a-empty v-else-if="!flowHasActivity" class="flow-empty" description="所选区间没有可见 Flow 活动" />
          <template v-else>
            <div class="kpi-grid flow-kpis">
              <button class="kpi-card flow-pending" type="button" @click="drill(snapshot.flow.pendingRoute)">
                <span>待审批</span><strong>{{ metricLabel(snapshot.flow.pendingCount) }}</strong><small>查看审批 <ExternalLink :size="12" /></small>
              </button>
              <button class="kpi-card flow-terminal" type="button" @click="drill(snapshot.flow.terminalInRangeRoute)">
                <span>区间内终态</span><strong>{{ metricLabel(snapshot.flow.terminalInRangeCount) }}</strong><small>查看区间终态 <ExternalLink :size="12" /></small>
              </button>
            </div>
            <div class="analytics-grid">
              <article class="chart-card flow-daily-chart">
                <h3>审批每日趋势</h3>
                <OperationsSeriesChart
                  :categories="snapshot.flow.daily.map(item => item.date)"
                  :series="[
                    { name: '发起', data: snapshot.flow.daily.map(item => item.started) },
                    { name: '进入终态', data: snapshot.flow.daily.map(item => item.terminal) },
                  ]"
                />
              </article>
              <article class="breakdown-card flow-terminal-breakdown">
                <h3>终态分布</h3>
                <a-empty v-if="!snapshot.flow.terminalBreakdown.length" description="没有终态分布" />
                <button
                  v-for="item in snapshot.flow.terminalBreakdown"
                  :key="item.status"
                  class="breakdown-row"
                  :data-status="item.status"
                  type="button"
                  @click="drill(item.route)"
                ><span>{{ terminalLabel(item.status) }}</span><strong>{{ item.count }}</strong></button>
              </article>
            </div>
          </template>
        </section>

        <section class="operations-section todo-operations" :data-available="snapshot.todo.available">
          <header><div><h2>个人待办</h2><p>当前成员的 Todo 动作中心开放项。</p></div></header>
          <a-alert
            v-if="!snapshot.todo.available"
            class="todo-analytics-unavailable"
            type="warning"
            show-icon
            :message="snapshot.todo.unavailableReason || '当前身份无权读取 Todo 指标。'"
          />
          <a-empty v-else-if="!todoHasActivity" class="todo-analytics-empty" description="当前没有开放待办" />
          <template v-else>
            <div class="kpi-grid todo-kpis">
              <button class="kpi-card todo-open" type="button" @click="drill(snapshot.todo.route)"><span>开放</span><strong>{{ snapshot.todo.openCount }}</strong></button>
              <button class="kpi-card todo-task" type="button" @click="drill(snapshot.todo.route)"><span>任务</span><strong>{{ snapshot.todo.taskCount }}</strong></button>
              <button class="kpi-card todo-approval" type="button" @click="drill(snapshot.todo.route)"><span>审批</span><strong>{{ snapshot.todo.approvalCount }}</strong></button>
              <button class="kpi-card todo-today" type="button" @click="drill(snapshot.todo.route)"><span>今日到期</span><strong>{{ snapshot.todo.todayCount }}</strong></button>
              <button class="kpi-card todo-overdue" type="button" @click="drill(snapshot.todo.route)"><span>已逾期</span><strong>{{ snapshot.todo.overdueCount }}</strong></button>
            </div>
          </template>
        </section>
      </div>
    </a-spin>
  </section>
</template>

<style scoped>
.operations-page{display:grid;gap:18px;max-width:1360px;min-width:0;margin:0 auto;padding:28px;background:#f4f6f8}.operations-heading,.operations-heading h1,.operations-actions,.range-presets,.operations-range-summary,.operations-section>header,.kpi-card small{display:flex;align-items:center}.operations-heading{justify-content:space-between;gap:20px}.operations-heading h1{gap:10px;margin:0}.operations-heading p,.operations-section header p{margin:6px 0 0;color:#64748b;overflow-wrap:anywhere}.operations-actions,.range-presets{gap:8px;flex-wrap:wrap}.operations-range-summary{gap:8px;color:#64748b;flex-wrap:wrap}.operations-range-summary span:last-child{margin-left:auto}.operations-sections{display:grid;gap:18px;min-width:0}.operations-section{display:grid;gap:16px;min-width:0;padding:20px;border:1px solid #dce3e8;border-radius:12px;background:#fff}.operations-section>header{justify-content:space-between;gap:14px}.operations-section h2,.operations-section h3{margin:0}.kpi-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}.kpi-card{display:grid;gap:6px;min-width:0;padding:15px;border:1px solid #dbe3e8;border-radius:9px;background:#f8fafc;text-align:left;cursor:pointer}.kpi-card:focus-visible,.breakdown-row:focus-visible{outline:3px solid rgb(15 118 110 / 24%);outline-offset:2px}.kpi-card strong{font-size:26px;color:#0f766e}.kpi-card small{gap:4px;color:#64748b}.analytics-grid{display:grid;grid-template-columns:minmax(0,1.7fr) minmax(300px,.8fr);gap:16px;min-width:0}.chart-card,.breakdown-card{display:grid;gap:14px;min-width:0;padding:16px;border:1px solid #e2e8f0;border-radius:9px}.breakdown-card{align-content:start}.breakdown-row{display:flex;justify-content:space-between;gap:12px;padding:11px 8px;border:0;border-bottom:1px solid #e7ecef;background:#fff;text-align:left;cursor:pointer}.flow-kpis{grid-template-columns:repeat(2,minmax(0,1fr))}.todo-kpis{grid-template-columns:repeat(5,minmax(0,1fr))}
@media(max-width:1024px){.operations-page{padding:20px}.operations-heading{align-items:flex-start}.kpi-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.todo-kpis{grid-template-columns:repeat(3,minmax(0,1fr))}.analytics-grid{grid-template-columns:minmax(0,1fr)}}
@media(max-width:560px){.operations-page{gap:14px;padding:14px}.operations-heading{align-items:stretch;flex-direction:column}.operations-actions{justify-content:flex-start}.operations-range-summary{align-items:flex-start}.operations-range-summary span:last-child{flex-basis:100%;margin-left:24px}.operations-section{padding:14px}.operations-section>header{align-items:flex-start;flex-direction:column}.kpi-grid,.flow-kpis,.todo-kpis{grid-template-columns:minmax(0,1fr)}.chart-card,.breakdown-card{padding:12px}}
</style>
