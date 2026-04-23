<template>
  <view style="padding: 16px">
    <uni-card :title="id ? `编辑模板 #${id}` : '新建模板'">
      <uni-forms labelPosition="top">
        <uni-forms-item label="tempCode">
          <uni-easyinput v-model="form.tempCode" placeholder="例如 leave" />
        </uni-forms-item>
        <uni-forms-item label="tempName">
          <uni-easyinput v-model="form.tempName" placeholder="例如 请假审批" />
        </uni-forms-item>
        <uni-forms-item label="status（1启用/2停用）">
          <uni-data-select v-model="form.status" :localdata="statusOptions" />
        </uni-forms-item>
        <uni-forms-item label="remark">
          <uni-easyinput v-model="form.remark" type="textarea" :autoHeight="true" placeholder="可选" />
        </uni-forms-item>
      </uni-forms>

      <view style="display:flex; gap: 8px; flex-wrap: wrap;">
        <uni-button type="primary" :disabled="saving" @click="save">保存</uni-button>
        <uni-button @click="back">返回</uni-button>
      </view>

      <view v-if="error" style="margin-top: 12px; color:#d00">{{ error }}</view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { httpGet, httpPost } from '@/api/http'
import { ensureSystemContext } from '@/utils/guard'

const id = ref<number>(0)
const saving = ref(false)
const error = ref<string | null>(null)

const statusOptions = [
  { value: 1, text: '启用' },
  { value: 2, text: '停用' }
]

const form = reactive<{ tempCode: string; tempName: string; status: number; remark: string }>({
  tempCode: '',
  tempName: '',
  status: 1,
  remark: ''
})

onLoad((opts) => {
  id.value = Number((opts as any)?.id || 0) || 0
})

function back() {
  uni.navigateBack()
}

async function loadDetail() {
  if (!id.value) return
  const r = await httpGet<any>(`/v1/system/flow/temps/${id.value}`)
  const t = r.data || {}
  form.tempCode = String(t.tempCode || '')
  form.tempName = String(t.tempName || '')
  form.status = Number(t.status || 1) || 1
  form.remark = String(t.remark || '')
}

async function save() {
  error.value = null
  if (!form.tempCode.trim() || !form.tempName.trim()) {
    error.value = 'tempCode/tempName 不能为空'
    return
  }
  saving.value = true
  try {
    await httpPost('/v1/system/flow/temps/upsert', {
      id: id.value || null,
      tempCode: form.tempCode.trim(),
      tempName: form.tempName.trim(),
      categoryCode: null,
      status: form.status,
      remark: form.remark.trim() ? form.remark.trim() : null
    })
    uni.showToast({ title: '已保存', icon: 'success' })
    back()
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  if (!ensureSystemContext()) return
  loadDetail()
})
</script>

