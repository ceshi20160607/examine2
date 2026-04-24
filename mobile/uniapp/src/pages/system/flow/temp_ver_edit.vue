<template>
  <Page :title="id ? `编辑版本 #${id}` : `新建版本（tempId=${tempId}）`" subtitle="编辑 graphJson/formJson；发布后模板可发起">
    <view class="u-card">
      <uni-forms labelPosition="top">
        <uni-forms-item label="verNo（可选）">
          <uni-easyinput v-model="form.verNo" placeholder="例如 1" />
        </uni-forms-item>
        <uni-forms-item label="publishStatus（1草稿/2已发布/3已废弃）">
          <uni-data-select v-model="form.publishStatus" :localdata="pubOptions" />
        </uni-forms-item>
        <uni-forms-item label="graphJson（JSON，可选）">
          <uni-easyinput v-model="form.graphJson" type="textarea" :autoHeight="true" placeholder="{}" />
        </uni-forms-item>
        <uni-forms-item label="formJson（JSON，可选）">
          <uni-easyinput v-model="form.formJson" type="textarea" :autoHeight="true" placeholder="{}" />
        </uni-forms-item>
      </uni-forms>

      <ActionBar>
        <uni-button type="primary" :disabled="saving" @click="save">保存</uni-button>
        <uni-button v-if="id" type="warn" :disabled="saving || publishing" @click="publish">发布</uni-button>
        <uni-button v-if="id" :disabled="saving" @click="fillGraphMvp">填充 graphJson(MVP)</uni-button>
        <uni-button @click="back">返回</uni-button>
        <uni-button v-if="id" @click="goNodes">节点</uni-button>
        <uni-button v-if="id" @click="goLines">连线</uni-button>
        <uni-button v-if="id" @click="goSettings">全局设置</uni-button>
        <uni-button v-if="id" @click="goNodeSettings">节点设置</uni-button>
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
const tempId = ref<number>(0)
const saving = ref(false)
const publishing = ref(false)
const error = ref<string | null>(null)

const pubOptions = [
  { value: 1, text: '草稿' },
  { value: 2, text: '已发布' },
  { value: 3, text: '已废弃' }
]

const form = reactive<{ verNo: string; publishStatus: number; graphJson: string; formJson: string }>({
  verNo: '',
  publishStatus: 1,
  graphJson: '',
  formJson: ''
})

onLoad((opts) => {
  id.value = Number((opts as any)?.id || 0) || 0
  tempId.value = Number((opts as any)?.tempId || 0) || 0
})

function back() {
  uni.navigateBack()
}

function goNodes() {
  if (!id.value) return
  uni.navigateTo({ url: `/pages/system/flow/temp_ver_nodes?tempVerId=${id.value}` })
}
function goLines() {
  if (!id.value) return
  uni.navigateTo({ url: `/pages/system/flow/temp_ver_lines?tempVerId=${id.value}` })
}
function goSettings() {
  if (!id.value) return
  uni.navigateTo({ url: `/pages/system/flow/temp_ver_settings?tempVerId=${id.value}` })
}
function goNodeSettings() {
  if (!id.value) return
  uni.navigateTo({ url: `/pages/system/flow/temp_ver_node_settings?tempVerId=${id.value}` })
}

async function loadDetail() {
  if (!id.value) return
  const r = await httpGet<any>(`/v1/system/flow/temp-vers/${id.value}`)
  const v = r.data || {}
  tempId.value = Number(v.tempId || tempId.value || 0) || 0
  form.verNo = v.verNo == null ? '' : String(v.verNo)
  form.publishStatus = Number(v.publishStatus || 1) || 1
  form.graphJson = v.graphJson == null ? '' : String(v.graphJson)
  form.formJson = v.formJson == null ? '' : String(v.formJson)
}

function normalizeJson(s: string): string | null {
  const t = (s || '').trim()
  if (!t) return null
  try {
    JSON.parse(t)
    return t
  } catch {
    throw new Error('JSON 非法')
  }
}

function fillGraphMvp() {
  const t = (form.graphJson || '').trim()
  if (t) {
    uni.showToast({ title: 'graphJson 已有内容', icon: 'none' })
    return
  }
  const mvp = {
    nodes: [
      { id: 'approve-1', name: '审批', type: 'approve' }
    ],
    edges: []
  }
  form.graphJson = JSON.stringify(mvp, null, 2)
  if (!(form.formJson || '').trim()) {
    form.formJson = JSON.stringify({ fields: [] }, null, 2)
  }
}

async function save() {
  error.value = null
  if (!tempId.value) {
    error.value = 'tempId 不能为空'
    return
  }
  let verNo: number | null = null
  const vn = form.verNo.trim()
  if (vn) {
    const n = Number(vn)
    if (!n || Number.isNaN(n)) {
      error.value = 'verNo 非法'
      return
    }
    verNo = n
  }

  let graphJson: string | null = null
  let formJson: string | null = null
  try {
    graphJson = normalizeJson(form.graphJson)
    formJson = normalizeJson(form.formJson)
  } catch (e: any) {
    error.value = e?.message ?? String(e)
    return
  }

  saving.value = true
  try {
    await httpPost('/v1/system/flow/temp-vers/upsert', {
      id: id.value || null,
      tempId: tempId.value,
      verNo,
      publishStatus: form.publishStatus,
      graphJson,
      formJson
    })
    uni.showToast({ title: '已保存', icon: 'success' })
    back()
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  } finally {
    saving.value = false
  }
}

async function publish() {
  if (!id.value) return
  error.value = null
  publishing.value = true
  try {
    await httpPost(`/v1/system/flow/temp-vers/${id.value}/publish`)
    uni.showToast({ title: '已发布', icon: 'success' })
    await loadDetail()
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  } finally {
    publishing.value = false
  }
}

onMounted(() => {
  if (!ensureSystemContext()) return
  loadDetail()
})
</script>

