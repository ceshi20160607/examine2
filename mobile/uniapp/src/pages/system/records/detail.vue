<template>
  <Page :title="`Record #${recordId}`" subtitle="查看/复制/删除">
    <view class="u-card">
      <ActionBar>
        <uni-button type="primary" :disabled="!recordId" @click="goEdit">编辑</uni-button>
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
        <uni-button type="warn" :disabled="!recordId || loading" @click="doDelete">删除</uni-button>
        <uni-button :disabled="!detail" @click="copyJson">复制 JSON</uni-button>
        <uni-button :disabled="!recordId" @click="goHistory">变更历史</uni-button>
        <uni-button @click="toggleRaw">{{ showRaw ? '结构化' : '原始 JSON' }}</uni-button>
      </ActionBar>

      <view v-if="showRaw" style="margin-top: 12px; font-family: monospace; white-space: pre-wrap;">
        {{ pretty }}
      </view>
      <view v-else style="margin-top: 12px">
        <uni-list v-if="dataEntries.length">
          <uni-list-item
            v-for="e in dataEntries"
            :key="e.k"
            :title="e.k"
            :note="e.v"
            clickable
            @click="onEntryClick(e)"
          />
        </uni-list>
        <EmptyState v-else text="无字段数据" />
      </view>
      <ErrorBlock :text="error" />
    </view>
  </Page>
</template>

<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { computed, onMounted, ref } from 'vue'
import { ensureSystemContext } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'
import { listFieldsByModel, type ModuleField } from '@/api/meta'
import { deleteRecord, getRecord } from '@/api/records'
import { fieldTypeCode, isRefField, isRefMultiField } from '@/utils/fieldTypes'
import { resolveRefDisplay } from '@/utils/refPicker'

const recordId = ref<number>(0)
const loading = ref(false)
const error = ref<string | null>(null)
const detail = ref<any>(null)
const showRaw = ref(false)
const fieldMetaByCode = ref<Record<string, ModuleField>>({})
const refLabelByKey = ref<Record<string, string>>({})

onLoad((opts) => {
  recordId.value = Number((opts as any)?.recordId || 0) || 0
})

const pretty = computed(() => {
  try {
    return JSON.stringify(detail.value, null, 2)
  } catch {
    return String(detail.value ?? '')
  }
})

const dataEntries = computed(() => {
  const data = detail.value?.data
  const files = detail.value?.files as Record<string, any> | undefined
  if (!data || typeof data !== 'object') return []
  return Object.keys(data)
    .slice()
    .sort()
    .map((k) => {
      const raw = data[k]
      const meta = files?.[k]
      let v = refLabelByKey.value[k] || stringifyValue(raw)
      if (meta && typeof meta === 'object') {
        const name = meta.originalName || meta.name
        if (name) v = `${v} (${name})`
      }
      return { k, v, raw, meta }
    })
})

type DataEntry = { k: string; v: string; raw: any; meta?: any }

function parseRefIds(v: any): number[] {
  if (v == null || v === '') return []
  if (Array.isArray(v)) {
    return v.map((x) => Number(x)).filter((n) => Number.isFinite(n) && n > 0)
  }
  const s = String(v).trim()
  if (!s) return []
  if (s.startsWith('[')) {
    try {
      const arr = JSON.parse(s)
      if (Array.isArray(arr)) return arr.map((x) => Number(x)).filter((n) => Number.isFinite(n) && n > 0)
    } catch {
      return []
    }
  }
  const n = Number(s)
  return Number.isFinite(n) && n > 0 ? [n] : []
}

function onEntryClick(e: DataEntry) {
  const mf = fieldMetaByCode.value[e.k]
  if (mf && isRefField(mf)) {
    const ids = parseRefIds(e.raw)
    if (ids.length === 1) {
      uni.navigateTo({ url: `/pages/system/records/detail?recordId=${ids[0]}` })
      return
    }
  }
  const fileId = resolveFileId(e.raw)
  if (fileId) {
    uni.navigateTo({
      url: `/pages/system/upload/view?fileId=${fileId}&name=${encodeURIComponent(e.meta?.originalName || '')}`
    })
    return
  }
  copyText(`${e.k}=${e.v}`)
}

function resolveFileId(v: any): number | null {
  if (v == null || v === '') return null
  const n = Number(v)
  return Number.isFinite(n) && n > 0 ? n : null
}

async function load() {
  if (!recordId.value) return
  loading.value = true
  error.value = null
  try {
    const r = await getRecord(recordId.value)
    detail.value = r.data
    await enrichRefLabels()
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  } finally {
    loading.value = false
  }
}

function goEdit() {
  uni.navigateTo({ url: `/pages/system/records/form?recordId=${recordId.value}` })
}

function goHistory() {
  uni.navigateTo({ url: `/pages/system/records/history?recordId=${recordId.value}` })
}

function toggleRaw() {
  showRaw.value = !showRaw.value
}

function copyText(s: string) {
  uni.setClipboardData({ data: s })
}

function copyJson() {
  copyText(pretty.value)
}

async function enrichRefLabels() {
  refLabelByKey.value = {}
  fieldMetaByCode.value = {}
  const modelId = Number(detail.value?.record?.modelId || 0)
  if (!modelId) return
  try {
    const fr = await listFieldsByModel(modelId)
    for (const mf of fr.data || []) {
      if (mf?.fieldCode) fieldMetaByCode.value[mf.fieldCode] = mf
    }
  } catch {
    return
  }
  const data = detail.value?.data || {}
  for (const k of Object.keys(data)) {
    const mf = fieldMetaByCode.value[k]
    if (!mf || !isRefField(mf)) continue
    const raw = data[k]
    if (isRefMultiField(mf)) {
      const ids = parseRefIds(raw)
      const parts: string[] = []
      for (const id of ids) {
        parts.push(await resolveRefDisplay(id, mf.refDisplayField))
      }
      refLabelByKey.value[k] = parts.join('、') || stringifyValue(raw)
    } else {
      refLabelByKey.value[k] = await resolveRefDisplay(raw, mf.refDisplayField)
    }
  }
}

function stringifyValue(v: any): string {
  if (v == null) return ''
  if (Array.isArray(v)) return v.slice(0, 20).join(',')
  if (typeof v === 'object') {
    try {
      return JSON.stringify(v)
    } catch {
      return String(v)
    }
  }
  return String(v)
}

function doDelete() {
  if (!recordId.value) return
  uni.showModal({
    title: '确认删除？',
    content: `将删除记录 #${recordId.value}`,
    success: async (m) => {
      if (!m.confirm) return
      try {
        await deleteRecord(recordId.value)
        uni.showToast({ title: '已删除', icon: 'success' })
        uni.navigateBack()
      } catch (e: any) {
        error.value = e?.message ?? String(e)
      }
    }
  })
}

onMounted(() => {
  if (!ensureSystemContext()) return
  load()
})
</script>

