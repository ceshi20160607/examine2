<template>
  <Page :title="`筛选模板（modelId=${modelId}）`" subtitle="为列表视图准备筛选配置模板">
    <view class="u-card u-section">
      <uni-forms labelPosition="top">
        <uni-forms-item label="tplCode">
          <uni-easyinput v-model="form.tplCode" placeholder="tplCode" />
        </uni-forms-item>
        <uni-forms-item label="tplName">
          <uni-easyinput v-model="form.tplName" placeholder="tplName" />
        </uni-forms-item>
        <uni-forms-item label="menuId(可选)">
          <uni-easyinput v-model="form.menuId" placeholder="menuId(可选)" />
        </uni-forms-item>
      </uni-forms>
      <ActionBar>
        <uni-button type="primary" :disabled="saving" @click="upsert">创建</uni-button>
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
      </ActionBar>
    </view>

    <view class="u-card u-section">
      <view class="u-title">列表</view>
      <view style="margin-top: 12px">
        <uni-list v-if="rows.length">
          <uni-list-item
            v-for="t in rows"
            :key="t.id"
            :title="t.tplName || t.tplCode || ('Tpl#' + t.id)"
            :note="t.tplCode || ''"
          />
        </uni-list>
        <EmptyState v-else text="暂无模板" />
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
import { listFilterTpls, type ModuleFilterTplRow, upsertFilterTpl } from '@/api/module'

const appId = ref<number>(0)
const modelId = ref<number>(0)
const loading = ref(false)
const saving = ref(false)
const rows = ref<ModuleFilterTplRow[]>([])

const form = reactive<{ tplCode: string; tplName: string; menuId: string }>({ tplCode: '', tplName: '', menuId: '' })

onLoad((opts) => {
  appId.value = Number((opts as any)?.appId || 0) || 0
  modelId.value = Number((opts as any)?.modelId || 0) || 0
})

async function load() {
  if (!modelId.value) return
  loading.value = true
  try {
    const r = await listFilterTpls(modelId.value)
    rows.value = r.data || []
  } finally {
    loading.value = false
  }
}

async function upsert() {
  if (!appId.value || !modelId.value) return
  if (!form.tplCode.trim() || !form.tplName.trim()) {
    uni.showToast({ title: '请输入 tplCode/tplName', icon: 'none' })
    return
  }
  const menuIdRaw = form.menuId.trim()
  const menuId = menuIdRaw ? Number(menuIdRaw) : null
  if (menuIdRaw && (!menuId || Number.isNaN(menuId))) {
    uni.showToast({ title: 'menuId 非法', icon: 'none' })
    return
  }

  saving.value = true
  try {
    await upsertFilterTpl({
      id: null,
      appId: appId.value,
      modelId: modelId.value,
      menuId,
      tplCode: form.tplCode.trim(),
      tplName: form.tplName.trim(),
      status: 1
    })
    form.tplCode = ''
    form.tplName = ''
    form.menuId = ''
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
