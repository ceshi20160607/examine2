<template>
  <Page :title="id ? `编辑模板 #${id}` : '新建模板'" subtitle="保存后可进入版本管理并发布">
    <view class="u-card">
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

      <ActionBar>
        <uni-button type="primary" :disabled="saving" @click="save">保存</uni-button>
        <uni-button @click="back">返回</uni-button>
        <uni-button v-if="id" @click="goVers">版本管理</uni-button>
      </ActionBar>

      <ErrorBlock :text="error" />
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
import ErrorBlock from '@/ui/ErrorBlock.vue'

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

function goVers() {
  if (!id.value) return
  uni.navigateTo({ url: `/pages/system/flow/temp_ver_list?tempId=${id.value}` })
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

