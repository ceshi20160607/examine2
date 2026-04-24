<template>
  <Page :title="`字典项 Items（dictId=${dictId}）`" subtitle="为 dictCode 添加可选项">
    <view class="u-card u-section">
      <uni-forms labelPosition="top">
        <uni-forms-item label="value">
          <uni-easyinput v-model="form.itemValue" placeholder="value" />
        </uni-forms-item>
        <uni-forms-item label="label">
          <uni-easyinput v-model="form.itemLabel" placeholder="label" />
        </uni-forms-item>
      </uni-forms>
      <ActionBar>
        <uni-button type="primary" :disabled="saving" @click="upsert">新增</uni-button>
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
      </ActionBar>
    </view>

    <view class="u-card u-section">
      <view class="u-title">列表</view>
      <view style="margin-top: 12px">
        <uni-list v-if="rows.length">
          <uni-list-item
            v-for="it in rows"
            :key="it.id"
            :title="it.itemLabel || it.itemValue || ('Item#' + it.id)"
            :note="it.itemValue || ''"
          />
        </uni-list>
        <EmptyState v-else text="暂无字典项" />
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

type DictItemRow = { id: number; dictId?: number; itemValue?: string; itemLabel?: string; sortNo?: number; status?: number }

const dictId = ref<number>(0)
const loading = ref(false)
const saving = ref(false)
const rows = ref<DictItemRow[]>([])

const form = reactive<{ itemValue: string; itemLabel: string }>({ itemValue: '', itemLabel: '' })

onLoad((opts) => {
  dictId.value = Number((opts as any)?.dictId || 0) || 0
})

async function load() {
  if (!dictId.value) return
  loading.value = true
  try {
    const r = await httpGet<DictItemRow[]>(`/v1/system/module/dicts/${dictId.value}/items`)
    rows.value = r.data || []
  } finally {
    loading.value = false
  }
}

async function upsert() {
  if (!dictId.value) return
  if (!form.itemValue.trim() || !form.itemLabel.trim()) {
    uni.showToast({ title: '请输入 value/label', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await httpPost(`/v1/system/module/dicts/${dictId.value}/items/upsert`, {
      id: null,
      itemValue: form.itemValue.trim(),
      itemLabel: form.itemLabel.trim(),
      sortNo: 0,
      status: 1
    })
    form.itemValue = ''
    form.itemLabel = ''
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  if (!ensureSystemContext()) return
  load()
})
</script>

