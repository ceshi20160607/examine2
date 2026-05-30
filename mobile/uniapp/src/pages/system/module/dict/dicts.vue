<template>
  <Page :title="`字典 Dicts（appId=${appId}）`" subtitle="用于下拉/枚举：先建 dict，再建 items">
    <view class="u-card u-section">
      <view v-if="hintNoApp" class="u-subtitle">未找到应用：请先在「元数据」中创建 App，或从 Apps 列表进入本页（带 appId）。</view>
      <uni-forms labelPosition="top">
        <uni-forms-item :label="editingId ? '编辑字典' : '新增字典'">
          <uni-easyinput v-model="form.dictCode" placeholder="dictCode" />
          <uni-easyinput v-model="form.dictName" placeholder="dictName" />
        </uni-forms-item>
        <uni-forms-item label="状态">
          <uni-data-select v-model="form.status" :localdata="statusOptions" />
        </uni-forms-item>
      </uni-forms>
      <ActionBar>
        <uni-button type="primary" :disabled="saving" @click="upsert">{{ editingId ? '保存' : '新增' }}</uni-button>
        <uni-button v-if="editingId" @click="resetForm">取消编辑</uni-button>
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
      </ActionBar>
      <ErrorBlock :text="error" />
    </view>

    <view class="u-card u-section">
      <view class="u-title">列表</view>
      <view style="margin-top: 12px">
        <uni-list v-if="rows.length">
          <uni-list-item
            v-for="d in rows"
            :key="d.id"
            :title="d.dictName || d.dictCode || ('Dict#' + d.id)"
            :note="`${d.dictCode || ''} · ${d.status === 1 ? '启用' : '停用'}`"
            clickable
            @click="openActions(d)"
          />
        </uni-list>
        <EmptyState v-else text="暂无字典" />
      </view>
    </view>
  </Page>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { ensureSystemContext } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'
import { usePageRequest } from '@/composables/usePageRequest'
import { deleteDicts, listDictsByApp, type ModuleDictRow, upsertDict } from '@/api/module'
import { listApps } from '@/api/meta'
import { hasId, idToString, type IdValue } from '@/utils/id'

const appId = ref('')
const saving = ref(false)
const editingId = ref<string | null>(null)
const { loading, error, run, capture, clearError } = usePageRequest()
const rows = ref<ModuleDictRow[]>([])

const form = reactive<{ dictCode: string; dictName: string; status: number }>({ dictCode: '', dictName: '', status: 1 })
const hintNoApp = ref(false)
const statusOptions = [
  { value: 1, text: '启用' },
  { value: 2, text: '停用' }
]

onLoad((opts) => {
  appId.value = idToString((opts as any)?.appId)
})

async function ensureAppIdFromFirstApp() {
  if (hasId(appId.value)) return
  hintNoApp.value = false
  try {
    const r = await listApps()
    const apps = r.data || []
    if (apps.length && apps[0]?.id) {
      appId.value = idToString(apps[0].id as IdValue)
    }
  } catch {
    /* ignore */
  }
  if (!hasId(appId.value)) hintNoApp.value = true
}

async function load() {
  if (!hasId(appId.value)) return
  await run(async () => {
    const r = await listDictsByApp(appId.value)
    rows.value = r.data || []
  })
}

async function upsert() {
  if (!hasId(appId.value)) return
  if (!form.dictCode.trim() || !form.dictName.trim()) {
    uni.showToast({ title: '请输入 dictCode/dictName', icon: 'none' })
    return
  }
  saving.value = true
  clearError()
  try {
    await upsertDict(appId.value, {
      id: editingId.value,
      dictCode: form.dictCode.trim(),
      dictName: form.dictName.trim(),
      status: form.status,
      remark: null
    })
    resetForm()
    uni.showToast({ title: '已保存', icon: 'success' })
    await load()
  } catch (e: unknown) {
    capture(e)
  } finally {
    saving.value = false
  }
}

function resetForm() {
  editingId.value = null
  form.dictCode = ''
  form.dictName = ''
  form.status = 1
}

function fillForm(d: ModuleDictRow) {
  editingId.value = idToString(d.id as IdValue)
  form.dictCode = d.dictCode || ''
  form.dictName = d.dictName || ''
  form.status = d.status === 2 ? 2 : 1
}

function goItems(dictId: IdValue) {
  const id = idToString(dictId)
  if (!hasId(id)) return
  uni.navigateTo({ url: `/pages/system/module/dict/items?dictId=${encodeURIComponent(id)}` })
}

function remove(d: ModuleDictRow) {
  const id = idToString(d.id as IdValue)
  if (!hasId(id)) return
  uni.showModal({
    title: '删除字典？',
    content: d.dictName || d.dictCode || id,
    success: async (m) => {
      if (!m.confirm) return
      try {
        await deleteDicts([id])
        if (editingId.value === id) resetForm()
        uni.showToast({ title: '已删除', icon: 'success' })
        await load()
      } catch (e: unknown) {
        capture(e)
      }
    }
  })
}

function openActions(d: ModuleDictRow) {
  uni.showActionSheet({
    itemList: ['字典项', '编辑', '删除'],
    success: (res) => {
      if (res.tapIndex === 0) {
        goItems(d.id)
        return
      }
      if (res.tapIndex === 1) {
        fillForm(d)
        return
      }
      if (res.tapIndex === 2) {
        remove(d)
      }
    }
  })
}

onMounted(async () => {
  if (!ensureSystemContext()) return
  await ensureAppIdFromFirstApp()
  await load()
})
</script>

