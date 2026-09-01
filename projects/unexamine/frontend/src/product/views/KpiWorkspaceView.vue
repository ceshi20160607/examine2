<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError, api } from '../api'
import { kpiStatusLabel } from '../kpi'
import { productDateTime } from '../presentation'
import { systemTokens } from '../session'
import type { KpiDefinition, KpiOverview } from '../types'

const route = useRoute()
const router = useRouter()
const overview = ref<KpiOverview>({ kpis: [] })
const loading = ref(false)
const error = ref('')
const selectedId = computed(() => Number(route.query.kpiId) || undefined)
const selected = computed(() => overview.value.kpis.find(item => item.id === selectedId.value)
  || overview.value.kpis[0])

async function load() {
  if (!systemTokens.value?.accessToken) return
  loading.value = true; error.value = ''
  try { overview.value = await api<KpiOverview>('/api/analytics/kpis', {}, systemTokens.value.accessToken) }
  catch (cause) { error.value = cause instanceof ApiError ? cause.message : '关键指标看板加载失败' }
  finally { loading.value = false }
}

function choose(kpi: KpiDefinition) {
  void router.replace({ path: route.path, query: { ...route.query, workspace: 'kpi', kpiId: kpi.id } })
}

function itemText(item: Record<string, unknown>) {
  return Object.entries(item).filter(([, value]) => value !== null && typeof value !== 'object')
    .slice(0, 4).map(([key, value]) => `${key}: ${String(value)}`).join(' · ')
}

function responsibilityLabel(type: string, ids: number[]) {
  if (!ids.length) return '未指定负责人'
  return type === 'DEPARTMENT' ? `${ids.length} 个负责部门` : `${ids.length} 位负责人`
}

function reminderStatusLabel(status: string) {
  return ({ SENT: '已发送', ACKNOWLEDGED: '已确认', FAILED: '发送失败' } as Record<string, string>)[status] || '处理中'
}

async function acknowledge(reminderId: number) {
  if (!systemTokens.value?.accessToken) return
  try {
    await api(`/api/analytics/kpis/reminders/${reminderId}/acknowledge`, { method: 'POST' }, systemTokens.value.accessToken)
    await load()
  } catch (cause) { error.value = cause instanceof ApiError ? `${cause.code}：${cause.message}` : '确认提醒失败' }
}

onMounted(load)
</script>

<template>
  <section class="kpi-workspace">
    <div class="page-heading"><div><p class="eyebrow">经营责任 · 关键指标</p><h1>关键指标达成看板</h1><p>实际值来自已发布报表；指标汇总和明细查看分别按权限控制。</p></div><a-button :loading="loading" @click="load">刷新</a-button></div>
    <a-alert v-if="error" type="error" show-icon :message="error" />
    <a-empty v-if="!loading && !overview.kpis.length" description="当前没有可查看的关键指标" />
    <div v-else class="kpi-workspace__layout">
      <aside class="panel-card kpi-workspace__list">
        <button v-for="kpi in overview.kpis" :key="kpi.id" :class="{ active: selected?.id === kpi.id }" @click="choose(kpi)">
          <span><strong>{{ kpi.name }}</strong><small>{{ kpi.latestResult?.periodKey || '待计算' }}</small></span>
          <a-tag :color="kpi.latestResult?.stale ? 'orange' : kpi.latestResult?.status === 'ACHIEVED' ? 'green' : 'red'">{{ kpiStatusLabel(kpi.latestResult?.status || '') }}</a-tag>
        </button>
      </aside>

      <main v-if="selected" class="kpi-workspace__detail">
        <a-alert v-if="selected.latestResult?.stale" type="warning" show-icon message="数据源本次不可用，当前展示上次成功快照" description="系统没有将失败计算伪装成零值或已达标，也没有发送新的达标/未达标提醒。" />
        <section class="panel-card kpi-score-card">
          <div class="panel-title"><strong>{{ selected.name }}</strong><span>{{ responsibilityLabel(selected.responsibleType, selected.responsibleIds) }}</span></div>
          <div v-if="selected.latestResult" class="kpi-preview__metrics">
            <span><small>实际值</small><strong>{{ selected.latestResult.actualValue }}</strong></span>
            <span><small>目标值</small><strong>{{ selected.targetOperator === 'GTE' ? '≥' : '≤' }} {{ selected.latestResult.targetValue }}</strong></span>
            <span><small>达成率</small><strong>{{ Number(selected.latestResult.achievementRate).toFixed(2) }}%</strong></span>
            <span><small>状态</small><strong>{{ kpiStatusLabel(selected.latestResult.status) }}</strong></span>
          </div>
          <a-empty v-else :image="false" description="尚无计算结果，需由管理员预览并执行计算" />
          <p v-if="selected.latestResult" class="kpi-source-line">周期 {{ selected.latestResult.periodKey }} · 计算于 {{ productDateTime(selected.latestResult.calculatedAt) }}</p>
        </section>

        <section v-if="selected.latestResult?.reminders.length" class="panel-card kpi-reminders">
          <div class="panel-title"><strong>责任提醒</strong><span>未达标消息可从消息中心跳回本页</span></div>
          <div v-for="reminder in selected.latestResult.reminders" :key="reminder.id" class="kpi-reminder-row"><span>责任人提醒 · {{ reminderStatusLabel(reminder.status) }}</span><a-button v-if="reminder.status === 'SENT'" size="small" @click="acknowledge(reminder.id)">确认收到</a-button></div>
        </section>

        <section v-if="selected.latestResult" class="panel-card kpi-drill-panel">
          <div class="panel-title"><strong>授权明细</strong><a-tag :color="selected.latestResult.drillAvailable ? 'blue' : 'default'">{{ selected.latestResult.drillAvailable ? '下钻已授权' : '仅看板可见' }}</a-tag></div>
          <a-alert v-if="!selected.latestResult.drillAvailable" type="info" show-icon message="你可以查看指标汇总，但没有明细下钻权限。" />
          <div v-else-if="selected.latestResult.drillItems.length" class="kpi-drill-list"><article v-for="(item, index) in selected.latestResult.drillItems" :key="String(item.id || index)"><strong>明细 {{ index + 1 }}</strong><span>{{ itemText(item) }}</span></article></div>
          <a-empty v-else :image="false" description="当前授权范围没有明细记录" />
        </section>
      </main>
    </div>
  </section>
</template>
