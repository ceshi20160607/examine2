<template>
  <Page :title="`字典 Dicts（appId=${appId}）`" subtitle="用于下拉/枚举：先建 dict，再建 items">
    <view class="u-card u-section">
      <view v-if="hintNoApp" class="u-subtitle">未找到应用：请先在「元数据」中创建 App，或从 Apps 列表进入本页（带 appId）。</view>
      <uni-forms labelPosition="top">
        <uni-forms-item label="dictCode">
          <uni-easyinput v-model="form.dictCode" placeholder="dictCode" />
        </uni-forms-item>
        <uni-forms-item label="dictName">
          <uni-easyinput v-model="form.dictName" placeholder="dictName" />
        </uni-forms-item>
      </uni-forms>
      <ActionBar>
        <uni-button type="primary" :disabled="saving" @click="upsert">保存</uni-button>
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
      </ActionBar>
    </view>

    <view class="u-card u-section">
      <view class="u-title">列表</view>
      <view style="margin-top: 12px">
        <uni-list v-if="rows.length">
          <uni-list-item
            v-for="d in rows"
            :key="d.id"
            :title="d.dictName || d.dictCode || ('Dict#' + d.id)"
            :note="d.dictCode || ''"
            clickable
            @click="goItems(d.id)"
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
import { listDictsByApp, type ModuleDictRow, upsertDict } from '@/api/module'
import { listApps } from '@/api/meta'

const appId = ref<number>(0)
const loading = ref(false)
const saving = ref(false)
const rows = ref<ModuleDictRow[]>([])

const form = reactive<{ dictCode: string; dictName: string }>({ dictCode: '', dictName: '' })
const hintNoApp = ref(false)

onLoad((opts) => {
  appId.value = Number((opts as any)?.appId || 0) || 0
})

async function ensureAppIdFromFirstApp() {
  if (appId.value) return
  hintNoApp.value = false
  try {
    const r = await listApps()
    const apps = r.data || []
    if (apps.length && apps[0]?.id) {
      appId.value = Number(apps[0].id)
    }
  } catch {
    /* ignore */
  }
  if (!appId.value) hintNoApp.value = true
}

async function load() {
  if (!appId.value) return
  loading.value = true
  try {
    const r = await listDictsByApp(appId.value)
    rows.value = r.data || []
  } finally {
    loading.value = false
  }
}

async function upsert() {
  if (!appId.value) return
  if (!form.dictCode.trim() || !form.dictName.trim()) {
    uni.showToast({ title: '请输入 dictCode/dictName', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await upsertDict(appId.value, { id: null, dictCode: form.dictCode.trim(), dictName: form.dictName.trim(), status: 1, remark: null })
    form.dictCode = ''
    form.dictName = ''
    await load()
  } finally {
    saving.value = false
  }
}

function goItems(dictId: number) {
  uni.navigateTo({ url: `/pages/system/module/dict/items?dictId=${dictId}` })
}

onMounted(async () => {
  if (!ensureSystemContext()) return
  await ensureAppIdFromFirstApp()
  await load()
})
</script>

