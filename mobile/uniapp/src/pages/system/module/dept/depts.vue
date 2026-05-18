<template>
  <Page :title="`部门 Depts（appId=${appId}）`" subtitle="DEPARTMENT 字段的数据来源">
    <view class="u-card u-section">
      <uni-forms labelPosition="top">
        <uni-forms-item label="deptCode">
          <uni-easyinput v-model="form.deptCode" placeholder="如 sales" />
        </uni-forms-item>
        <uni-forms-item label="deptName">
          <uni-easyinput v-model="form.deptName" placeholder="部门名称" />
        </uni-forms-item>
        <uni-forms-item label="parentId">
          <uni-easyinput v-model="form.parentId" type="number" placeholder="0=根部门" />
        </uni-forms-item>
      </uni-forms>
      <ActionBar>
        <uni-button type="primary" :disabled="saving" @click="save">保存</uni-button>
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
      </ActionBar>
      <ErrorBlock :text="error" />
    </view>

    <view class="u-card u-section">
      <view class="u-title">部门列表</view>
      <view style="margin-top: 12px">
        <uni-list v-if="rows.length">
          <uni-list-item
            v-for="d in rows"
            :key="d.id"
            :title="d.deptName || d.deptCode"
            :note="deptNote(d)"
            clickable
            @click="openActions(d)"
          />
        </uni-list>
        <EmptyState v-else text="暂无部门" />
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
import { deleteDepts, listDepts, type ModuleDept, upsertDept } from '@/api/dept'

const appId = ref(0)
const saving = ref(false)
const editingId = ref<number | null>(null)
const { loading, error, run, capture, clearError } = usePageRequest()
const rows = ref<ModuleDept[]>([])

const form = reactive({ deptCode: '', deptName: '', parentId: '0' })

function deptNote(d: ModuleDept) {
  const parts = [`code=${d.deptCode}`, `parent=${d.parentId ?? 0}`]
  if (d.depth) parts.push(`depth=${d.depth}`)
  return parts.join(' · ')
}

onLoad((opts) => {
  appId.value = Number((opts as any)?.appId || 0) || 0
})

async function load() {
  if (!appId.value) return
  await run(async () => {
    rows.value = (await listDepts(appId.value)).data || []
  })
}

function resetForm() {
  editingId.value = null
  form.deptCode = ''
  form.deptName = ''
  form.parentId = '0'
}

function fillForm(d: ModuleDept) {
  editingId.value = d.id
  form.deptCode = d.deptCode || ''
  form.deptName = d.deptName || ''
  form.parentId = String(d.parentId ?? 0)
}

async function save() {
  if (!appId.value || !form.deptCode.trim() || !form.deptName.trim()) {
    uni.showToast({ title: '请填写 deptCode/deptName', icon: 'none' })
    return
  }
  saving.value = true
  clearError()
  try {
    await upsertDept(appId.value, {
      id: editingId.value,
      parentId: Number(form.parentId) || 0,
      deptCode: form.deptCode.trim(),
      deptName: form.deptName.trim(),
      sortNo: 0,
      status: 1
    })
    resetForm()
    await load()
  } catch (e: unknown) {
    capture(e)
  } finally {
    saving.value = false
  }
}

function openActions(d: ModuleDept) {
  uni.showActionSheet({
    itemList: ['编辑', '删除'],
    success: async (res) => {
      if (res.tapIndex === 0) {
        fillForm(d)
        return
      }
      if (res.tapIndex === 1) {
        uni.showModal({
          title: '删除部门？',
          content: d.deptName || d.deptCode,
          success: async (m) => {
            if (!m.confirm) return
            await deleteDepts([d.id])
            if (editingId.value === d.id) resetForm()
            await load()
          }
        })
      }
    }
  })
}

onMounted(() => {
  if (!ensureSystemContext()) return
  load()
})
</script>
