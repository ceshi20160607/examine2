<template>
  <view style="padding: 16px">
    <uni-card :title="`字段 Fields（modelId=${modelId}）`">
      <view style="display:flex; flex-direction: column; gap: 8px;">
        <view style="display:flex; gap: 8px;">
          <uni-easyinput v-model="form.fieldCode" placeholder="fieldCode (如 title)" />
          <uni-easyinput v-model="form.fieldName" placeholder="fieldName" />
        </view>
        <view style="display:flex; gap: 8px;">
          <uni-easyinput v-model="form.fieldType" placeholder="fieldType (text/number/date...)" />
          <uni-button type="primary" :disabled="saving" @click="create">创建</uni-button>
        </view>
      </view>
    </uni-card>

    <uni-card title="列表" style="margin-top: 12px">
      <uni-list v-if="fields.length">
        <uni-list-item
          v-for="f in fields"
          :key="f.id"
          :title="(f.fieldName || f.fieldCode || ('Field#' + f.id))"
          :note="`${f.fieldCode || ''} / ${f.fieldType || ''}`"
        />
      </uni-list>
      <view v-else style="color:#666">暂无字段</view>
      <view style="margin-top: 12px">
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
      </view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { httpGet, httpPost } from '@/api/http'

type ModuleField = { id: number; fieldCode?: string; fieldName?: string; fieldType?: string; status?: number }

const appId = ref<number>(0)
const modelId = ref<number>(0)
const fields = ref<ModuleField[]>([])
const loading = ref(false)
const saving = ref(false)
const form = reactive({ fieldCode: '', fieldName: '', fieldType: 'text' })

onLoad((opts) => {
  appId.value = Number((opts as any)?.appId || 0) || 0
  modelId.value = Number((opts as any)?.modelId || 0) || 0
})

async function load() {
  if (!modelId.value) {
    uni.showToast({ title: '缺少 modelId', icon: 'none' })
    return
  }
  loading.value = true
  try {
    const r = await httpGet<ModuleField[]>(`/v1/system/module/meta/models/${modelId.value}/fields`)
    fields.value = r.data || []
  } finally {
    loading.value = false
  }
}

async function create() {
  if (!appId.value || !modelId.value) return
  if (!form.fieldCode.trim() || !form.fieldName.trim() || !form.fieldType.trim()) {
    uni.showToast({ title: '请输入 fieldCode/fieldName/fieldType', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await httpPost('/v1/system/module/meta/fields/upsert', {
      id: null,
      appId: appId.value,
      modelId: modelId.value,
      fieldCode: form.fieldCode.trim(),
      fieldName: form.fieldName.trim(),
      fieldType: form.fieldType.trim(),
      requiredFlag: 0,
      uniqueFlag: 0,
      hiddenFlag: 0,
      tips: null,
      maxLength: null,
      minLength: null,
      validateType: null,
      dateFormat: null,
      dictCode: null,
      multiFlag: 0,
      defaultValue: null,
      sortNo: 0,
      status: 1
    })
    form.fieldCode = ''
    form.fieldName = ''
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

