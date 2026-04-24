<template>
  <Page :title="`字典 Dicts（appId=${appId}）`" subtitle="用于下拉/枚举：先建 dict，再建 items">
    <view class="u-card u-section">
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
import { httpGet, httpPost } from '@/api/http'
import { ensureSystemContext } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'

type DictRow = { id: number; dictCode?: string; dictName?: string; status?: number; remark?: string }

const appId = ref<number>(0)
const loading = ref(false)
const saving = ref(false)
const rows = ref<DictRow[]>([])

const form = reactive<{ dictCode: string; dictName: string }>({ dictCode: '', dictName: '' })

onLoad((opts) => {
  appId.value = Number((opts as any)?.appId || 0) || 0
})

async function load() {
  if (!appId.value) return
  loading.value = true
  try {
    const r = await httpGet<DictRow[]>(`/v1/system/module/dicts/apps/${appId.value}`)
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
    await httpPost(`/v1/system/module/dicts/apps/${appId.value}/upsert`, {
      id: null,
      dictCode: form.dictCode.trim(),
      dictName: form.dictName.trim(),
      status: 1,
      remark: null
    })
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

onMounted(() => {
  if (!ensureSystemContext()) return
  load()
})
</script>

