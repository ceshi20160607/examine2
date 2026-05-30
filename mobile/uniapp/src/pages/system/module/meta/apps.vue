<template>
  <Page title="应用 Apps" subtitle="创建应用后可继续创建模型、字段与权限">
    <view class="u-card u-section">
      <uni-forms labelPosition="top">
        <uni-forms-item :label="editingId ? '编辑应用' : '创建应用'">
          <view style="display:flex; gap: 8px; flex-wrap: wrap; align-items: center;">
            <uni-easyinput v-model="form.appCode" placeholder="appCode（如 default）" style="flex:1; min-width: 160px" />
            <uni-easyinput v-model="form.appName" placeholder="appName" style="flex:1; min-width: 160px" />
          </view>
        </uni-forms-item>
        <uni-forms-item label="状态">
          <uni-data-select v-model="form.status" :localdata="statusOptions" />
        </uni-forms-item>
      </uni-forms>
      <ActionBar>
        <uni-button type="primary" :disabled="saving" @click="save">{{ editingId ? '保存' : '创建' }}</uni-button>
        <uni-button v-if="editingId" @click="resetForm">取消编辑</uni-button>
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
      </ActionBar>
      <ErrorBlock :text="error" />
    </view>

    <view class="u-card u-section">
      <view class="u-title">列表</view>
      <view style="margin-top: 12px">
        <uni-list v-if="apps.length">
          <uni-list-item
            v-for="a in apps"
            :key="a.id"
            :title="(a.appName || a.appCode || ('App#' + a.id))"
            :note="`${a.appCode || ''} · ${a.status === 1 ? '启用' : '停用'}`"
            clickable
            @click="openActions(a)"
          />
        </uni-list>
        <EmptyState v-else text="暂无应用" />
      </view>
    </view>
  </Page>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ensureSystemContext } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'
import { usePageRequest } from '@/composables/usePageRequest'
import { deleteApps, listApps, type ModuleApp, upsertApp } from '@/api/meta'
import { hasId, idToString, type IdValue } from '@/utils/id'

const apps = ref<ModuleApp[]>([])
const saving = ref(false)
const editingId = ref<string | null>(null)
const { loading, error, run, capture, clearError } = usePageRequest()
const form = reactive<{ appCode: string; appName: string; status: number }>({ appCode: '', appName: '', status: 1 })

const statusOptions = [
  { value: 1, text: '启用' },
  { value: 2, text: '停用' }
]

async function load() {
  await run(async () => {
    const r = await listApps()
    apps.value = r.data || []
  })
}

function resetForm() {
  editingId.value = null
  form.appCode = ''
  form.appName = ''
  form.status = 1
}

function fillForm(a: ModuleApp) {
  editingId.value = idToString(a.id as IdValue)
  form.appCode = a.appCode || ''
  form.appName = a.appName || ''
  form.status = a.status === 2 ? 2 : 1
}

async function save() {
  if (!form.appCode.trim() || !form.appName.trim()) {
    uni.showToast({ title: '请输入 appCode/appName', icon: 'none' })
    return
  }
  saving.value = true
  clearError()
  try {
    await upsertApp({
      id: editingId.value,
      appCode: form.appCode.trim(),
      appName: form.appName.trim(),
      iconUrl: null,
      publishedFlag: 0,
      remark: null,
      status: form.status
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

function goModels(a: ModuleApp) {
  uni.navigateTo({ url: `/pages/system/module/meta/models?appId=${encodeURIComponent(idToString(a.id as IdValue))}` })
}

function remove(a: ModuleApp) {
  const id = idToString(a.id as IdValue)
  if (!hasId(id)) return
  uni.showModal({
    title: '删除应用？',
    content: a.appName || a.appCode || id,
    success: async (m) => {
      if (!m.confirm) return
      try {
        await deleteApps([id])
        if (editingId.value === id) resetForm()
        uni.showToast({ title: '已删除', icon: 'success' })
        await load()
      } catch (e: unknown) {
        capture(e)
      }
    }
  })
}

function openActions(a: ModuleApp) {
  const appId = idToString(a?.id as IdValue)
  if (!hasId(appId)) return
  uni.showActionSheet({
    itemList: ['运行时菜单', 'Models', 'Pages', 'Relations', 'Dicts', 'Depts', 'RBAC', '编辑', '删除'],
    success: (res) => {
      if (res.tapIndex === 0) {
        uni.navigateTo({ url: `/pages/system/runtime/menus?appId=${encodeURIComponent(appId)}` })
        return
      }
      if (res.tapIndex === 1) {
        goModels(a)
        return
      }
      if (res.tapIndex === 2) {
        uni.navigateTo({ url: `/pages/system/module/pages/index?appId=${encodeURIComponent(appId)}` })
        return
      }
      if (res.tapIndex === 3) {
        uni.navigateTo({ url: `/pages/system/module/meta/relations?appId=${encodeURIComponent(appId)}` })
        return
      }
      if (res.tapIndex === 4) {
        uni.navigateTo({ url: `/pages/system/module/dict/dicts?appId=${encodeURIComponent(appId)}` })
        return
      }
      if (res.tapIndex === 5) {
        uni.navigateTo({ url: `/pages/system/module/dept/depts?appId=${encodeURIComponent(appId)}` })
        return
      }
      if (res.tapIndex === 6) {
        uni.navigateTo({ url: `/pages/system/module/rbac/index?appId=${encodeURIComponent(appId)}` })
        return
      }
      if (res.tapIndex === 7) {
        fillForm(a)
        return
      }
      if (res.tapIndex === 8) {
        remove(a)
      }
    }
  })
}

onMounted(() => {
  if (!ensureSystemContext()) return
  load()
})
</script>
