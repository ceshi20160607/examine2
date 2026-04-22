<template>
  <view style="padding: 16px">
    <uni-card :title="title">
      <uni-forms labelPosition="top">
        <uni-forms-item label="JSON data（对象）">
          <uni-easyinput v-model="jsonText" type="textarea" :autoHeight="true" placeholder="请输入 JSON 对象" />
        </uni-forms-item>
      </uni-forms>

      <view style="display:flex; gap: 8px;">
        <uni-button type="primary" :disabled="saving" @click="submit">{{ recordId ? '更新' : '创建' }}</uni-button>
        <uni-button @click="back">返回</uni-button>
      </view>

      <view v-if="error" style="margin-top: 12px; color:#d00">{{ error }}</view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { httpPost } from '@/api/http'

const appId = ref(0)
const modelId = ref(0)
const recordId = ref(0)

const jsonText = ref('{}')
const saving = ref(false)
const error = ref<string | null>(null)

const title = computed(() => (recordId.value ? `编辑 Record #${recordId.value}` : '新建 Record'))

onLoad((opts) => {
  appId.value = Number((opts as any)?.appId || 0) || 0
  modelId.value = Number((opts as any)?.modelId || 0) || 0
  recordId.value = Number((opts as any)?.recordId || 0) || 0
})

function back() {
  uni.navigateBack()
}

async function submit() {
  error.value = null
  let dataObj: any
  try {
    dataObj = JSON.parse(jsonText.value || '{}')
    if (!dataObj || Array.isArray(dataObj) || typeof dataObj !== 'object') {
      throw new Error('data 必须是 JSON 对象')
    }
  } catch (e: any) {
    error.value = e?.message ?? 'JSON 非法'
    return
  }

  saving.value = true
  try {
    if (recordId.value) {
      await httpPost(`/v1/system/records/${recordId.value}/update`, { data: dataObj })
      uni.showToast({ title: '更新成功', icon: 'success' })
      back()
      return
    }
    const r = await httpPost<any>('/v1/system/records', { appId: appId.value, modelId: modelId.value, data: dataObj })
    const id = r.data?.recordId
    uni.showToast({ title: '创建成功', icon: 'success' })
    if (id) {
      uni.redirectTo({ url: `/pages/system/records/detail?recordId=${id}` })
    } else {
      back()
    }
  } finally {
    saving.value = false
  }
}
</script>

