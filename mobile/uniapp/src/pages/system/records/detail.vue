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
            :title="e.title"
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
import { listDepartmentPickerOptions, listMemberPickerOptions } from '@/api/rbac'
import { deleteRecord, getRecord } from '@/api/records'
import {
  fieldTypeCode,
  isDepartmentField,
  isPersonField,
  isRatingSelectField,
  isRefField,
  isRefMultiField
} from '@/utils/fieldTypes'
import { resolveRefDisplay } from '@/utils/refPicker'
import { pageQuerySuffix } from '@/utils/pageRuntime'
import { hasId, idToString, uniqueIds, type IdValue } from '@/utils/id'
import { createModulePermState, MODULE_PERMS } from '@/utils/modulePerms'

const recordId = ref('')
const pageId = ref('')
const loading = ref(false)
const error = ref<string | null>(null)
const detail = ref<any>(null)
const showRaw = ref(false)
const fieldMetaByCode = ref<Record<string, ModuleField>>({})
const refLabelByKey = ref<Record<string, string>>({})
const memberLabelMap = ref<Record<string, string>>({})
const deptLabelMap = ref<Record<string, string>>({})
const { loadModulePerms, hasModulePerm } = createModulePermState()
const canManageRecords = computed(() => hasModulePerm(MODULE_PERMS.records))

onLoad((opts) => {
  recordId.value = idToString((opts as any)?.recordId)
  pageId.value = idToString((opts as any)?.pageId)
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
      const mf = fieldMetaByCode.value[k]
      const title = mf?.fieldName || k
      const fileId = resolveFileId(raw)
      const fileMeta = fileId ? files?.[String(fileId)] : undefined
      let v = refLabelByKey.value[k] || stringifyValue(raw)
      if (fileMeta && typeof fileMeta === 'object') {
        const name = fileMeta.originalName || fileMeta.name
        if (name) v = name
      }
      return { k, title, v, raw, meta: fileMeta }
    })
})

type DataEntry = { k: string; title: string; v: string; raw: any; meta?: any }

function parseRefIds(v: any): string[] {
  if (v == null || v === '') return []
  if (Array.isArray(v)) {
    return uniqueIds(v as IdValue[])
  }
  const s = String(v).trim()
  if (!s) return []
  if (s.startsWith('[')) {
    try {
      const arr = JSON.parse(s)
      if (Array.isArray(arr)) return uniqueIds(arr as IdValue[])
    } catch {
      return []
    }
  }
  return hasId(s) ? [s] : []
}

function onEntryClick(e: DataEntry) {
  const mf = fieldMetaByCode.value[e.k]
  if (mf && isRefField(mf)) {
    const ids = parseRefIds(e.raw)
    if (ids.length === 1) {
      uni.navigateTo({ url: `/pages/system/records/detail?recordId=${encodeURIComponent(ids[0])}` })
      return
    }
  }
  const fileId = resolveFileId(e.raw)
  if (fileId) {
    uni.navigateTo({
      url: `/pages/system/upload/view?fileId=${encodeURIComponent(fileId)}&name=${encodeURIComponent(e.meta?.originalName || '')}`
    })
    return
  }
  copyText(`${e.k}=${e.v}`)
}

function resolveFileId(v: any): string | null {
  if (v == null || v === '') return null
  const id = idToString(v)
  return hasId(id) ? id : null
}

async function load() {
  if (!hasId(recordId.value)) return
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
  if (!canManageRecords.value) {
    error.value = '当前账号没有记录维护权限'
    return
  }
  const rec = detail.value?.record
  const appId = idToString(rec?.appId)
  const modelId = idToString(rec?.modelId)
  const q = pageQuerySuffix(pageId.value)
  if (hasId(appId) && hasId(modelId)) {
    uni.navigateTo({
      url: `/pages/system/records/form?recordId=${encodeURIComponent(recordId.value)}&appId=${encodeURIComponent(appId)}&modelId=${encodeURIComponent(modelId)}${q}`
    })
    return
  }
  uni.navigateTo({ url: `/pages/system/records/form?recordId=${encodeURIComponent(recordId.value)}${q}` })
}

function goHistory() {
  uni.navigateTo({ url: `/pages/system/records/history?recordId=${encodeURIComponent(recordId.value)}` })
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
  memberLabelMap.value = {}
  deptLabelMap.value = {}
  const modelId = idToString(detail.value?.record?.modelId)
  const appId = idToString(detail.value?.record?.appId)
  if (!hasId(modelId)) return
  try {
    const fr = await listFieldsByModel(modelId)
    for (const mf of fr.data || []) {
      if (mf?.fieldCode) fieldMetaByCode.value[mf.fieldCode] = mf
    }
  } catch {
    return
  }
  if (hasId(appId)) {
    try {
      const [m, d] = await Promise.all([
        listMemberPickerOptions(appId),
        listDepartmentPickerOptions(appId)
      ])
      for (const o of m.data || []) memberLabelMap.value[String(o.value)] = o.text
      for (const o of d.data || []) deptLabelMap.value[String(o.value)] = o.text
    } catch {
      // ignore picker load errors
    }
  }
  const data = detail.value?.data || {}
  for (const k of Object.keys(data)) {
    const mf = fieldMetaByCode.value[k]
    if (!mf) continue
    const raw = data[k]
    if (isRefField(mf)) {
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
      continue
    }
    if (isPersonField(mf)) {
      const ids = parseRefIds(raw)
      if (ids.length) {
        refLabelByKey.value[k] = ids.map((id) => memberLabelMap.value[String(id)] || `#${id}`).join('、')
      }
      continue
    }
    if (isDepartmentField(mf)) {
      const ids = parseRefIds(raw)
      if (ids.length) {
        refLabelByKey.value[k] = ids.map((id) => deptLabelMap.value[String(id)] || `#${id}`).join('、')
      }
      continue
    }
    if (isRatingSelectField(mf)) {
      const n = Number(raw)
      if (Number.isFinite(n) && n > 0) refLabelByKey.value[k] = `${n} 星`
      continue
    }
    if (fieldTypeCode(mf) === 'BOOLEAN') {
      refLabelByKey.value[k] = raw === true || raw === 'true' || raw === 1 || raw === '1' ? '是' : '否'
      continue
    }
    if (fieldTypeCode(mf) === 'ADDRESS') {
      try {
        const o = typeof raw === 'string' ? JSON.parse(raw) : raw
        if (o && typeof o === 'object') {
          const parts = [o.region, o.detail].filter(Boolean)
          if (o.lat != null && o.lng != null) parts.push(`(${o.lat},${o.lng})`)
          refLabelByKey.value[k] = parts.join(' ') || stringifyValue(raw)
        }
      } catch {
        // keep raw
      }
      continue
    }
    if (fieldTypeCode(mf) === 'DATE_RANGE') {
      try {
        const o = typeof raw === 'string' ? JSON.parse(raw) : raw
        if (o && typeof o === 'object') {
          refLabelByKey.value[k] = [o.start, o.end].filter(Boolean).join(' ~ ')
        }
      } catch {
        // keep raw
      }
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
  if (!hasId(recordId.value)) return
  if (!canManageRecords.value) {
    error.value = '当前账号没有记录维护权限'
    return
  }
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
  loadModulePerms().then(() => load())
})
</script>

