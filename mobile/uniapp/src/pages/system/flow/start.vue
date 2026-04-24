<template>
  <Page title="发起流程" subtitle="推荐从模板下拉选择；发起成功可直接打开任务/待办">
    <view class="u-card">
      <uni-forms labelPosition="top">
        <uni-forms-item label="模板（推荐）">
          <uni-data-select
            v-model="selectedTempId"
            :localdata="tempOptions"
            placeholder="请选择可发起模板"
            @change="onTempChanged"
          />
        </uni-forms-item>
        <uni-forms-item label="defCode（FlowTemp.tempCode）">
          <uni-easyinput v-model="form.defCode" placeholder="例如 leave" />
        </uni-forms-item>
        <uni-forms-item label="bizType">
          <uni-easyinput v-model="form.bizType" placeholder="例如 record" />
        </uni-forms-item>
        <uni-forms-item label="bizId">
          <uni-easyinput v-model="form.bizId" placeholder="例如 123 / order-001" />
        </uni-forms-item>
        <uni-forms-item label="title">
          <uni-easyinput v-model="form.title" placeholder="流程标题" />
        </uni-forms-item>
        <uni-forms-item label="vars（JSON 对象，可选）">
          <uni-easyinput v-model="varsText" type="textarea" :autoHeight="true" placeholder="{}" />
        </uni-forms-item>
      </uni-forms>

      <ActionBar>
        <uni-button type="primary" :disabled="starting || !canStart" @click="start">发起</uni-button>
        <uni-button @click="goTemps">选择模板</uni-button>
        <uni-button @click="goTempManage">模板管理</uni-button>
        <uni-button @click="goByBiz">按 biz 查询</uni-button>
      </ActionBar>

      <view v-if="hint" class="u-subtitle">{{ hint }}</view>
      <ErrorBlock :text="error" />
      <view v-if="lastStart.instanceId || lastStart.taskId" style="margin-top: 12px">
        <ActionBar>
          <uni-button v-if="lastStart.instanceId && lastStart.taskId" type="primary" @click="goLastTask">打开任务</uni-button>
          <uni-button v-if="lastStart.instanceId" @click="goInbox">打开待办箱</uni-button>
        </ActionBar>
      </view>
      <view v-if="resultText" style="margin-top: 12px; font-family: monospace; white-space: pre-wrap;">{{ resultText }}</view>
    </view>
  </Page>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { httpGet, httpPost } from '@/api/http'
import { ensureSystemContext } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'

const starting = ref(false)
const error = ref<string | null>(null)
const resultText = ref<string>('')
const hint = ref<string>('')
const lastStart = reactive<{ instanceId: number; taskId: number }>({ instanceId: 0, taskId: 0 })

const form = reactive({
  defCode: '',
  bizType: 'record',
  bizId: '',
  title: ''
})

const tempName = ref<string>('')
const varsText = ref('{}')

type FlowTemp = {
  id: number | string
  tempCode?: string
  tempName?: string
  latestVerNo?: number
  status?: number
}

const temps = ref<FlowTemp[]>([])
const selectedTempId = ref<string>('')
const tempOptions = ref<{ value: string; text: string }[]>([])

function isStartable(t: FlowTemp): boolean {
  const st = Number(t.status || 0)
  const ver = Number(t.latestVerNo || 0)
  const code = String(t.tempCode || '').trim()
  return st === 1 && ver > 0 && !!code
}

const canStart = computed(() => {
  const codeOk = !!form.defCode.trim()
  if (!codeOk) return false
  if (!selectedTempId.value) return true // 手填 defCode 的情况
  const t = temps.value.find((x) => String(x.id) === String(selectedTempId.value))
  if (!t) return true
  return isStartable(t)
})

function rebuildOptions() {
  const list = [...temps.value]
  list.sort((a, b) => {
    const sa = isStartable(a) ? 0 : 1
    const sb = isStartable(b) ? 0 : 1
    if (sa !== sb) return sa - sb
    return String(a.tempName || a.tempCode || '').localeCompare(String(b.tempName || b.tempCode || ''), 'zh-Hans-CN')
  })
  tempOptions.value = [{ value: '', text: '（不选择，手填 defCode）' }].concat(
    list.map((t) => {
      const name = t.tempName || t.tempCode || `Temp#${t.id}`
      const tag = isStartable(t) ? '' : '（不可发起）'
      return { value: String(t.id), text: `${name}${tag}` }
    })
  )
}

async function loadTemps() {
  const r = await httpGet<any>('/v1/system/flow/temps/page?page=1&size=200')
  const d = r.data || {}
  temps.value = (d.records || []) as FlowTemp[]
  rebuildOptions()
}

function onTempChanged() {
  hint.value = ''
  const id = selectedTempId.value
  if (!id) return
  const t = temps.value.find((x) => String(x.id) === String(id))
  if (!t) return
  form.defCode = String(t.tempCode || '').trim()
  tempName.value = String(t.tempName || '').trim()
  if (!isStartable(t)) {
    const st = Number(t.status || 0)
    const ver = Number(t.latestVerNo || 0)
    hint.value = `该模板当前不可发起（status=${st} latestVerNo=${ver}）。请去“模板管理”发布版本后再发起。`
  }
}

onLoad((opts) => {
  const dc = decodeURIComponent(String((opts as any)?.defCode || ''))
  if (dc) form.defCode = dc
  const tn = decodeURIComponent(String((opts as any)?.tempName || ''))
  if (tn) tempName.value = tn
})

function goByBiz() {
  uni.navigateTo({ url: `/pages/system/flow/by_biz?bizType=${encodeURIComponent(form.bizType || '')}&bizId=${encodeURIComponent(form.bizId || '')}` })
}

function goInbox() {
  uni.navigateTo({ url: '/pages/system/flow/inbox' })
}

function goLastTask() {
  if (!lastStart.instanceId || !lastStart.taskId) return
  uni.navigateTo({ url: `/pages/system/flow/task?instanceId=${lastStart.instanceId}&taskId=${lastStart.taskId}` })
}

async function start() {
  error.value = null
  resultText.value = ''
  lastStart.instanceId = 0
  lastStart.taskId = 0

  if (!form.defCode.trim()) {
    error.value = 'defCode 不能为空'
    return
  }
  if (!form.bizType.trim() || !form.bizId.trim()) {
    error.value = 'bizType/bizId 不能为空'
    return
  }

  let vars: any = {}
  try {
    const obj = JSON.parse(varsText.value || '{}')
    if (!obj || Array.isArray(obj) || typeof obj !== 'object') throw new Error('vars 必须是 JSON 对象')
    vars = obj
  } catch (e: any) {
    error.value = e?.message ?? 'vars JSON 非法'
    return
  }

  starting.value = true
  try {
    if (!form.title.trim()) {
      const prefix = tempName.value?.trim() || form.defCode.trim()
      form.title = `${prefix}:${form.bizId.trim()}`
    }
    const r = await httpPost<any>('/v1/system/flow/instances/start', {
      defCode: form.defCode.trim(),
      bizType: form.bizType.trim(),
      bizId: form.bizId.trim(),
      title: form.title.trim(),
      vars
    })
    uni.showToast({ title: '已发起', icon: 'success' })
    lastStart.instanceId = Number(r?.data?.instanceId || 0) || 0
    lastStart.taskId = Number(r?.data?.taskId || 0) || 0
    try {
      resultText.value = JSON.stringify(r.data, null, 2)
    } catch {
      resultText.value = String(r.data ?? '')
    }
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  } finally {
    starting.value = false
  }
}

function goTemps() {
  uni.navigateTo({ url: '/pages/system/flow/temps' })
}

function goTempManage() {
  uni.navigateTo({ url: '/pages/system/flow/temp_list' })
}

onMounted(() => {
  if (!ensureSystemContext()) return
  loadTemps()
})
</script>
