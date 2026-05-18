<template>
  <Page :title="`模型关系 appId=${appId}`" subtitle="定义模型间 1-1 / 1-n / n-n 关系（元数据）">
    <view class="u-card u-section">
      <uni-forms labelPosition="top">
        <uni-forms-item label="源模型 srcModel">
          <uni-data-select v-model="form.srcModelId" :localdata="modelOptions" placeholder="选择源模型" />
        </uni-forms-item>
        <uni-forms-item label="目标模型 dstModel">
          <uni-data-select v-model="form.dstModelId" :localdata="modelOptions" placeholder="选择目标模型" />
        </uni-forms-item>
        <uni-forms-item label="关系类型 relType">
          <uni-data-select v-model="form.relType" :localdata="relTypeOptions" />
        </uni-forms-item>
        <uni-forms-item label="configJson（可选）">
          <uni-easyinput
            v-model="form.configJson"
            type="textarea"
            :autoHeight="true"
            placeholder='1-n: {"fkField":"orderId"}；n-n: {"linkModelId":1,"srcFkField":"orderId","dstFkField":"productId"}'
          />
        </uni-forms-item>
      </uni-forms>
      <ActionBar>
        <uni-button type="primary" :disabled="saving" @click="save">保存</uni-button>
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
      </ActionBar>
      <ErrorBlock :text="error" />
    </view>

    <view class="u-card u-section">
      <view class="u-title">已配置关系</view>
      <uni-list v-if="rows.length">
        <uni-list-item
          v-for="r in rows"
          :key="r.id"
          :title="relationTitle(r)"
          :note="`relType=${r.relType || ''} id=${r.id}`"
          clickable
          @click="editRelation(r)"
        />
      </uni-list>
      <EmptyState v-else text="暂无关系" />
    </view>
  </Page>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'
import { usePageRequest } from '@/composables/usePageRequest'
import { ensureSystemContext } from '@/utils/guard'
import {
  deleteRelations,
  listModelsByApp,
  listRelationsByApp,
  type ModuleModel,
  type ModuleRelation,
  upsertRelation
} from '@/api/meta'

const appId = ref(0)
const models = ref<ModuleModel[]>([])
const rows = ref<ModuleRelation[]>([])
const saving = ref(false)
const { loading, error, run, capture, clearError } = usePageRequest()

const relTypeOptions = [
  { value: '1-1', text: '1-1 一对一' },
  { value: '1-n', text: '1-n 一对多' },
  { value: 'n-n', text: 'n-n 多对多' }
]

const form = reactive<{
  id: number | null
  srcModelId: number | string
  dstModelId: number | string
  relType: string
  configJson: string
}>({
  id: null,
  srcModelId: '',
  dstModelId: '',
  relType: '1-n',
  configJson: ''
})

const modelOptions = computed(() =>
  models.value.map((m) => ({
    value: m.id,
    text: `${m.modelName || m.modelCode || m.id} (#${m.id})`
  }))
)

const modelNameById = computed(() => {
  const map = new Map<number, string>()
  for (const m of models.value) {
    if (m.id) map.set(m.id, m.modelName || m.modelCode || String(m.id))
  }
  return map
})

onLoad((opts) => {
  appId.value = Number((opts as any)?.appId || 0) || 0
})

function relationTitle(r: ModuleRelation) {
  const src = modelNameById.value.get(Number(r.srcModelId)) || r.srcModelId
  const dst = modelNameById.value.get(Number(r.dstModelId)) || r.dstModelId
  return `${src} → ${dst}`
}

async function loadModels() {
  if (!appId.value) return
  const r = await listModelsByApp(appId.value)
  models.value = r.data || []
}

async function load() {
  if (!appId.value) return
  await run(async () => {
    await loadModels()
    const r = await listRelationsByApp(appId.value)
    rows.value = r.data || []
  })
}

function resetForm() {
  form.id = null
  form.srcModelId = ''
  form.dstModelId = ''
  form.relType = '1-n'
  form.configJson = ''
}

async function save() {
  if (!appId.value) return
  const srcModelId = Number(form.srcModelId)
  const dstModelId = Number(form.dstModelId)
  if (!srcModelId || !dstModelId) {
    uni.showToast({ title: '请选择源/目标模型', icon: 'none' })
    return
  }
  saving.value = true
  clearError()
  try {
    await upsertRelation({
      id: form.id,
      appId: appId.value,
      srcModelId,
      dstModelId,
      relType: form.relType,
      configJson: form.configJson.trim() || null
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

function editRelation(r: ModuleRelation) {
  uni.showActionSheet({
    itemList: ['编辑', '删除'],
    success: async (res) => {
      if (res.tapIndex === 0) {
        form.id = r.id
        form.srcModelId = r.srcModelId || ''
        form.dstModelId = r.dstModelId || ''
        form.relType = r.relType || '1-n'
        form.configJson = r.configJson || ''
        return
      }
      if (res.tapIndex === 1) {
        try {
          await deleteRelations([r.id])
          uni.showToast({ title: '已删除', icon: 'success' })
          await load()
        } catch (e: unknown) {
          capture(e)
        }
      }
    }
  })
}

onMounted(() => {
  if (!ensureSystemContext()) return
  load()
})
</script>
