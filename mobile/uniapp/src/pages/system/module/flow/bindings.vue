<template>
  <Page :title="`流程绑定 modelId=${modelId}`" subtitle="记录创建/更新时自动触发流程">
    <view class="u-card u-section">
      <ActionBar>
        <uni-button type="primary" :disabled="loading" @click="load">刷新</uni-button>
      </ActionBar>
      <ErrorBlock :text="error" />
    </view>

    <view class="u-card u-section">
      <view class="u-title">新增/更新绑定</view>
      <uni-forms labelPosition="top">
        <uni-forms-item label="触发动作">
          <uni-data-select v-model="form.triggerAction" :localdata="triggerOptions" />
        </uni-forms-item>
        <uni-forms-item label="流程模板">
          <uni-data-select v-model="form.tempId" :localdata="tempOptions" placeholder="选择模板" />
        </uni-forms-item>
      </uni-forms>
      <ActionBar>
        <uni-button type="primary" :disabled="saving" @click="save">保存</uni-button>
      </ActionBar>
    </view>

    <view class="u-card u-section">
      <view class="u-title">已配置</view>
      <uni-list v-if="rows.length">
        <uni-list-item
          v-for="row in rows"
          :key="row.binding.id"
          :title="`${row.binding.triggerAction || ''} → ${row.tempName || row.tempCode || row.binding.tempId}`"
          :note="`id=${row.binding.id} status=${row.binding.status ?? ''}`"
          clickable
          @click="editRow(row)"
        />
      </uni-list>
      <EmptyState v-else text="暂无绑定" />
    </view>
  </Page>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'
import { usePageRequest } from '@/composables/usePageRequest'
import { ensureSystemContext } from '@/utils/guard'
import {
  deleteModelFlowBinding,
  listFlowTempOptions,
  listModelFlowBindings,
  upsertModelFlowBinding,
  type FlowBindingRow,
  type FlowTempOption
} from '@/api/flowBinding'

const appId = ref(0)
const modelId = ref(0)
const rows = ref<FlowBindingRow[]>([])
const temps = ref<FlowTempOption[]>([])
const { loading, error, run, capture, clearError } = usePageRequest()
const saving = ref(false)

const triggerOptions = [
  { value: 'create', text: 'create（新建记录）' },
  { value: 'update', text: 'update（更新记录）' }
]

const form = reactive<{ id: number | null; triggerAction: string; tempId: number | string }>({
  id: null,
  triggerAction: 'create',
  tempId: ''
})

const tempOptions = ref<Array<{ value: number; text: string }>>([])

onLoad((opts) => {
  appId.value = Number((opts as any)?.appId || 0) || 0
  modelId.value = Number((opts as any)?.modelId || 0) || 0
})

async function loadTemps() {
  const r = await listFlowTempOptions()
  temps.value = r.data || []
  tempOptions.value = temps.value.map((t) => ({
    value: Number(t.id),
    text: `${t.tempName || t.tempCode || t.id}`
  }))
}

async function load() {
  if (!appId.value || !modelId.value) return
  await run(async () => {
    await loadTemps()
    const r = await listModelFlowBindings(appId.value, modelId.value)
    rows.value = r.data || []
  })
}

function editRow(row: FlowBindingRow) {
  const b = row.binding
  uni.showActionSheet({
    itemList: ['编辑', '删除'],
    success: async (res) => {
      if (res.tapIndex === 0) {
        form.id = b.id
        form.triggerAction = b.triggerAction || 'create'
        form.tempId = b.tempId || ''
        return
      }
      if (res.tapIndex === 1) {
        try {
          await deleteModelFlowBinding(b.id)
          uni.showToast({ title: '已删除', icon: 'success' })
          await load()
        } catch (e: unknown) {
          capture(e)
        }
      }
    }
  })
}

async function save() {
  if (!appId.value || !modelId.value) return
  const tempId = Number(form.tempId)
  if (!tempId) {
    uni.showToast({ title: '请选择流程模板', icon: 'none' })
    return
  }
  saving.value = true
  clearError()
  try {
    await upsertModelFlowBinding({
      id: form.id,
      appId: appId.value,
      modelId: modelId.value,
      triggerAction: form.triggerAction,
      tempId,
      status: 1
    })
    form.id = null
    form.triggerAction = 'create'
    form.tempId = ''
    uni.showToast({ title: '已保存', icon: 'success' })
    await load()
  } catch (e: unknown) {
    capture(e)
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  if (!ensureSystemContext()) return
  load()
})
</script>
