<script setup lang="ts">
import { CalendarClock, CheckCircle2, Clock3, Plus, RefreshCw, Save } from 'lucide-vue-next'
import { computed, reactive, ref, watch } from 'vue'

import MemberPicker from '@/components/runtime/MemberPicker.vue'
import { ApiRequestError } from '@/services/api'
import { reportAdminApi } from '@/services/report'
import type {
  CreateReportScheduleInput,
  ReportSchedule,
  ReportScheduleCadenceKind,
  ReportSchedulePreview,
  UpdateReportScheduleInput,
} from '@/types/report'
import type { RuntimeMemberOption } from '@/types/memberDirectory'

const props = defineProps<{
  systemId: string
  reportId: string
  reportCode: string
  reportActive: boolean
}>()

const schedules = ref<ReportSchedule[]>([])
const selected = ref<ReportSchedule | null>(null)
const loading = ref(false)
const mutating = ref(false)
const errorMessage = ref('')
const preview = ref<ReportSchedulePreview | null>(null)
const recipientCandidate = ref('')
const recipientLabels = reactive<Record<string, string>>({})
let generation = 0

const form = reactive({
  code: '', name: '', enabled: false, timeZone: 'Asia/Shanghai',
  kind: 'DAILY' as ReportScheduleCadenceKind, localTime: '09:00',
  daysOfWeek: ['MONDAY'] as string[], recipientMemberIds: [] as string[],
})
const editing = computed(() => Boolean(selected.value))
const dayOptions = [
  ['MONDAY', '周一'], ['TUESDAY', '周二'], ['WEDNESDAY', '周三'], ['THURSDAY', '周四'],
  ['FRIDAY', '周五'], ['SATURDAY', '周六'], ['SUNDAY', '周日'],
] as const

function requestError(error: unknown, fallback: string) {
  return error instanceof ApiRequestError
    ? error.message || error.code
    : error instanceof Error ? error.message : fallback
}

function apply(schedule: ReportSchedule) {
  selected.value = schedule
  Object.assign(form, {
    code: schedule.code,
    name: schedule.name,
    enabled: schedule.enabled,
    timeZone: schedule.timeZone,
    kind: schedule.cadence.kind,
    localTime: schedule.cadence.localTime,
    daysOfWeek: [...schedule.cadence.daysOfWeek],
    recipientMemberIds: [...schedule.recipientMemberIds],
  })
  preview.value = null
  recipientCandidate.value = ''
}

function newSchedule() {
  selected.value = null
  Object.assign(form, {
    code: '', name: '', enabled: false, timeZone: 'Asia/Shanghai', kind: 'DAILY',
    localTime: '09:00', daysOfWeek: ['MONDAY'], recipientMemberIds: [],
  })
  recipientCandidate.value = ''
  preview.value = null
  errorMessage.value = ''
}

async function load() {
  const runGeneration = ++generation
  if (!props.reportId) return
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await reportAdminApi.schedules(props.systemId, props.reportId)
    if (runGeneration !== generation) return
    schedules.value = result
    if (selected.value) {
      const current = result.find(item => item.id === selected.value?.id)
      if (current) apply(current)
      else newSchedule()
    } else if (result[0]) apply(result[0])
  } catch (error) {
    if (runGeneration === generation) errorMessage.value = requestError(error, '调度列表加载失败')
  } finally {
    if (runGeneration === generation) loading.value = false
  }
}

async function selectSchedule(schedule: ReportSchedule) {
  errorMessage.value = ''
  try {
    apply(await reportAdminApi.schedule(props.systemId, props.reportId, schedule.id))
  } catch (error) {
    errorMessage.value = requestError(error, '调度详情加载失败')
  }
}

function cadence() {
  return {
    kind: form.kind,
    localTime: form.localTime,
    daysOfWeek: form.kind === 'WEEKLY' ? [...form.daysOfWeek] : [],
  }
}

function validate() {
  if (!props.reportActive) return '请先发布报表，再配置运行调度'
  if (!editing.value && !/^[a-z][a-z0-9_]{1,63}$/.test(form.code.trim())) return '调度编码应为 2–64 位小写字母、数字或下划线'
  if (!form.name.trim()) return '调度名称不能为空'
  if (!form.timeZone.trim()) return 'IANA 时区不能为空'
  if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(form.localTime)) return '本地时间格式必须为 HH:mm'
  if (form.kind === 'WEEKLY' && !form.daysOfWeek.length) return '每周调度至少选择一个星期'
  if (form.recipientMemberIds.length < 1 || form.recipientMemberIds.length > 50) return '收件人数量必须为 1–50 人'
  return ''
}

async function save() {
  const issue = validate()
  if (issue) {
    errorMessage.value = issue
    return
  }
  mutating.value = true
  errorMessage.value = ''
  try {
    let saved: ReportSchedule
    if (selected.value) {
      const input: UpdateReportScheduleInput = {
        expectedVersion: selected.value.version,
        name: form.name.trim(), timeZone: form.timeZone.trim(), cadence: cadence(),
        recipientMemberIds: [...form.recipientMemberIds],
      }
      saved = await reportAdminApi.updateSchedule(
        props.systemId, props.reportId, selected.value.id, input,
      )
    } else {
      const input: CreateReportScheduleInput = {
        code: form.code.trim(), name: form.name.trim(), enabled: form.enabled,
        timeZone: form.timeZone.trim(), cadence: cadence(),
        recipientMemberIds: [...form.recipientMemberIds],
      }
      saved = await reportAdminApi.createSchedule(props.systemId, props.reportId, input)
    }
    apply(saved)
    schedules.value = await reportAdminApi.schedules(props.systemId, props.reportId)
  } catch (error) {
    errorMessage.value = requestError(error, '调度保存失败')
  } finally {
    mutating.value = false
  }
}

async function previewNextFire() {
  if (!form.timeZone || !form.localTime || form.kind === 'WEEKLY' && !form.daysOfWeek.length) return
  mutating.value = true
  errorMessage.value = ''
  try {
    preview.value = await reportAdminApi.previewSchedule(props.systemId, props.reportId, {
      timeZone: form.timeZone.trim(), cadence: cadence(),
    })
  } catch (error) {
    errorMessage.value = requestError(error, '下次执行时间预览失败')
  } finally {
    mutating.value = false
  }
}

async function toggleEnabled(schedule = selected.value) {
  if (!schedule) return
  mutating.value = true
  errorMessage.value = ''
  try {
    const updated = await reportAdminApi.setScheduleEnabled(
      props.systemId, props.reportId, schedule.id, !schedule.enabled, schedule.version,
    )
    apply(updated)
    schedules.value = schedules.value.map(item => item.id === updated.id ? updated : item)
  } catch (error) {
    errorMessage.value = requestError(error, schedule.enabled ? '停用调度失败' : '启用调度失败')
  } finally {
    mutating.value = false
  }
}

function rememberRecipient(member: RuntimeMemberOption) {
  recipientLabels[member.memberId] = `${member.displayName}（${member.memberCode}）`
}

function addRecipient() {
  if (!recipientCandidate.value || form.recipientMemberIds.includes(recipientCandidate.value)
    || form.recipientMemberIds.length >= 50) return
  form.recipientMemberIds.push(recipientCandidate.value)
  recipientCandidate.value = ''
}

function removeRecipient(memberId: string) {
  form.recipientMemberIds = form.recipientMemberIds.filter(id => id !== memberId)
}

function formatTime(value?: string | null) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.valueOf()) ? value : date.toLocaleString('zh-CN')
}

watch(() => [props.systemId, props.reportId] as const, () => {
  selected.value = null
  schedules.value = []
  void load()
}, { immediate: true })
</script>

<template>
  <section class="report-schedule-manager">
    <header class="schedule-heading"><div><h2><CalendarClock :size="18" />运行调度</h2><p>按 IANA 时区的每日或每周本地时间生成 XLSX，并送达当前有效成员。</p></div><div><a-button :loading="loading" @click="load"><RefreshCw :size="15" />刷新</a-button><a-button class="schedule-create" :disabled="!reportActive" @click="newSchedule"><Plus :size="15" />新建调度</a-button></div></header>
    <a-alert v-if="errorMessage" class="schedule-error" type="error" show-icon :message="errorMessage" />
    <a-alert v-if="!reportActive" type="warning" show-icon message="报表发布后才能启用调度" />

    <a-spin :spinning="loading">
      <div class="schedule-layout">
        <aside class="schedule-list"><button v-for="schedule in schedules" :key="schedule.id" type="button" :class="{ active: selected?.id === schedule.id }" @click="selectSchedule(schedule)"><span><strong>{{ schedule.name }}</strong><small>{{ schedule.code }} · {{ schedule.enabled ? '已启用' : '已停用' }}</small></span><a-tag :color="schedule.enabled ? 'green' : 'default'">{{ schedule.enabled ? '启用' : '停用' }}</a-tag></button><a-empty v-if="!schedules.length" :image="false" description="暂无调度" /></aside>

        <div class="schedule-form">
          <div class="schedule-grid"><label>编码<input v-model="form.code" class="schedule-code" :disabled="editing" placeholder="daily_orders"></label><label>名称<input v-model="form.name" class="schedule-name" maxlength="160"></label><label>IANA 时区<input v-model="form.timeZone" class="schedule-time-zone" list="report-time-zones"><datalist id="report-time-zones"><option value="Asia/Shanghai" /><option value="UTC" /><option value="America/New_York" /><option value="Europe/London" /></datalist></label><label>周期<select v-model="form.kind" class="schedule-kind"><option value="DAILY">每日</option><option value="WEEKLY">每周</option></select></label><label>本地时间<input v-model="form.localTime" class="schedule-local-time" type="time"></label><label v-if="!editing" class="schedule-enabled-create"><span>创建后状态</span><span><input v-model="form.enabled" type="checkbox"> 立即启用</span></label></div>

          <div v-if="form.kind === 'WEEKLY'" class="schedule-days"><span>星期</span><label v-for="day in dayOptions" :key="day[0]"><input v-model="form.daysOfWeek" type="checkbox" :value="day[0]">{{ day[1] }}</label></div>

          <section class="schedule-recipients"><header><div><strong>收件人</strong><small>当前 {{ form.recipientMemberIds.length }}/50 人；失效成员在执行时自动省略。</small></div></header><div class="recipient-picker-row"><MemberPicker v-model:value="recipientCandidate" :system-id="systemId" placeholder="搜索当前租户成员" @select="rememberRecipient" /><a-button class="schedule-recipient-add" :disabled="!recipientCandidate || form.recipientMemberIds.length >= 50" @click="addRecipient">添加</a-button></div><div class="recipient-chips"><span v-for="memberId in form.recipientMemberIds" :key="memberId">{{ recipientLabels[memberId] || `成员 ${memberId}` }}<button type="button" title="移除" @click="removeRecipient(memberId)">×</button></span></div></section>

          <div class="schedule-preview"><div><Clock3 :size="17" /><span>下次执行：<strong>{{ formatTime(preview?.nextFireAt ?? selected?.nextFireAt) }}</strong></span><small>{{ form.timeZone }} · {{ form.kind === 'DAILY' ? '每日' : '每周' }} {{ form.localTime }}</small></div><a-button class="schedule-preview-button" :loading="mutating" @click="previewNextFire">预览</a-button></div>

          <footer class="schedule-actions"><a-button class="schedule-save" type="primary" :loading="mutating" @click="save"><Save :size="15" />{{ editing ? '保存修改' : '创建调度' }}</a-button><a-button v-if="selected" class="schedule-toggle" :danger="selected.enabled" :loading="mutating" @click="toggleEnabled()"><CheckCircle2 :size="15" />{{ selected.enabled ? '停用' : '启用' }}</a-button><span v-if="selected">版本 {{ selected.version }} · 所有者 {{ selected.ownerMemberId }}</span></footer>
        </div>
      </div>
    </a-spin>
  </section>
</template>

<style scoped>
.report-schedule-manager{display:grid;gap:12px;padding:16px;border:1px solid #dce5e9;border-radius:10px;background:#fff}.schedule-heading,.schedule-heading>div,.schedule-actions,.schedule-preview,.schedule-preview>div,.schedule-recipients header{display:flex;align-items:center}.schedule-heading,.schedule-preview{justify-content:space-between;gap:12px}.schedule-heading>div,.schedule-actions{gap:8px}.schedule-heading h2,.schedule-heading p{margin:0}.schedule-heading h2{display:flex;align-items:center;gap:7px;font-size:16px}.schedule-heading p{margin-top:4px;color:#6d7b82;font-size:12px}.schedule-layout{display:grid;grid-template-columns:210px minmax(0,1fr);gap:14px}.schedule-list{display:grid;align-content:start;gap:7px}.schedule-list>button{display:flex;justify-content:space-between;align-items:center;gap:8px;padding:10px;border:1px solid #dfe7e9;border-radius:7px;background:#fff;text-align:left}.schedule-list>button.active{border-color:#278f82;background:#eef8f6}.schedule-list>button>span{display:grid;gap:3px}.schedule-list small,.schedule-recipients small,.schedule-preview small,.schedule-actions>span{color:#708087;font-size:12px}.schedule-form{display:grid;gap:13px}.schedule-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}.schedule-grid label{display:grid;gap:5px;color:#56666d;font-size:12px}.schedule-grid input,.schedule-grid select{width:100%;padding:7px 9px;border:1px solid #cbd5d9;border-radius:6px;background:#fff}.schedule-enabled-create span:last-child{display:flex;align-items:center;gap:6px}.schedule-enabled-create input{width:auto}.schedule-days,.recipient-picker-row,.recipient-chips{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.schedule-days>span{color:#56666d;font-size:12px}.schedule-days label{display:flex;gap:4px}.schedule-recipients{display:grid;gap:9px;padding:12px;border:1px solid #e2e8ea;border-radius:7px}.schedule-recipients header{justify-content:space-between}.schedule-recipients header>div{display:grid;gap:2px}.recipient-picker-row :deep(.member-picker){flex:1}.recipient-chips>span{display:flex;align-items:center;gap:5px;padding:5px 8px;border-radius:12px;background:#eef4f4;font-size:12px}.recipient-chips button{border:0;background:transparent}.schedule-preview{padding:11px;border-radius:7px;background:#f5f8f8}.schedule-preview>div{gap:7px;flex-wrap:wrap}.schedule-actions{justify-content:flex-end;flex-wrap:wrap}
</style>
