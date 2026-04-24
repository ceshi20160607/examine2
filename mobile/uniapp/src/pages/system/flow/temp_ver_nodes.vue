<template>
  <Page :title="`版本节点（tempVerId=${tempVerId}）`" subtitle="节点元数据配置（弹窗编辑）">
    <view class="u-card u-section">
      <ActionBar>
        <uni-button type="primary" :disabled="loading" @click="openEdit()">新增</uni-button>
        <uni-button :disabled="loading" @click="reload">刷新</uni-button>
      </ActionBar>
    </view>

    <view class="u-card u-section">
      <view class="u-title">列表</view>
      <view style="margin-top: 12px">
        <uni-list v-if="rows.length">
          <uni-list-item
            v-for="n in rows"
            :key="String(n.id)"
            :title="`${n.nodeName || n.nodeKey || ('Node#' + n.id)} (${n.nodeType || ''})`"
            :note="`key=${n.nodeKey || ''} parent=${n.parentNodeKey || ''}`"
            clickable
            @click="openActions(n)"
          />
        </uni-list>
        <EmptyState v-else text="暂无节点" />
      </view>
    </view>

    <uni-popup ref="popupRef" type="bottom">
      <view class="u-card">
        <uni-forms labelPosition="top">
          <uni-forms-item label="nodeKey">
            <uni-easyinput v-model="form.nodeKey" />
          </uni-forms-item>
          <uni-forms-item label="nodeType">
            <uni-easyinput v-model="form.nodeType" placeholder="start/approve/condition/cc/end..." />
          </uni-forms-item>
          <uni-forms-item label="nodeName">
            <uni-easyinput v-model="form.nodeName" />
          </uni-forms-item>
          <uni-forms-item label="parentNodeKey（可选）">
            <uni-easyinput v-model="form.parentNodeKey" />
          </uni-forms-item>
          <uni-forms-item label="sortNo（可选）">
            <uni-easyinput v-model="form.sortNo" type="number" />
          </uni-forms-item>
          <uni-forms-item label="configJson（可选 JSON）">
            <uni-easyinput v-model="form.configJson" type="textarea" :autoHeight="true" />
          </uni-forms-item>
        </uni-forms>
        <ActionBar>
          <uni-button type="primary" :disabled="saving" @click="save">保存</uni-button>
          <uni-button @click="closePopup">取消</uni-button>
        </ActionBar>
        <ErrorBlock :text="error" />
      </view>
    </uni-popup>
  </Page>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { httpGet, httpPost } from '@/api/http'
import { ensureSystemContext } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'

type NodeRow = { id: number | string; nodeKey?: string; parentNodeKey?: string; nodeType?: string; nodeName?: string; configJson?: string; sortNo?: number }

const tempVerId = ref<number>(0)
const loading = ref(false)
const saving = ref(false)
const rows = ref<NodeRow[]>([])
const editingId = ref<any>(null)
const error = ref<string | null>(null)

const popupRef = ref<any>(null)
const form = reactive<{ nodeKey: string; parentNodeKey: string; nodeType: string; nodeName: string; sortNo: string; configJson: string }>({
  nodeKey: '',
  parentNodeKey: '',
  nodeType: '',
  nodeName: '',
  sortNo: '',
  configJson: ''
})

onLoad((opts) => {
  tempVerId.value = Number((opts as any)?.tempVerId || 0) || 0
})

async function load() {
  if (!tempVerId.value) return
  loading.value = true
  try {
    const r = await httpGet<any>(`/v1/system/flow/temp-ver-nodes/page?tempVerId=${tempVerId.value}&page=1&size=200`)
    rows.value = (r.data?.records || []) as NodeRow[]
  } finally {
    loading.value = false
  }
}

async function reload() {
  await load()
}

function openEdit(n?: NodeRow) {
  editingId.value = n?.id ?? null
  form.nodeKey = String(n?.nodeKey || '')
  form.parentNodeKey = String(n?.parentNodeKey || '')
  form.nodeType = String(n?.nodeType || '')
  form.nodeName = String(n?.nodeName || '')
  form.sortNo = n?.sortNo == null ? '' : String(n.sortNo)
  form.configJson = String(n?.configJson || '')
  error.value = null
  popupRef.value?.open()
}

function closePopup() {
  popupRef.value?.close()
}

function openActions(n: NodeRow) {
  if (!n?.id) return
  uni.showActionSheet({
    itemList: ['编辑', '删除'],
    success: (res) => {
      if (res.tapIndex === 0) return openEdit(n)
      if (res.tapIndex === 1) return del(n.id)
    }
  })
}

function del(id: any) {
  uni.showModal({
    title: '确认删除？',
    content: `将删除节点 #${id}`,
    success: async (m) => {
      if (!m.confirm) return
      await httpPost('/v1/system/flow/temp-ver-nodes/delete', { ids: [id] })
      uni.showToast({ title: '已删除', icon: 'success' })
      reload()
    }
  })
}

function normalizeJson(s: string): string | null {
  const t = (s || '').trim()
  if (!t) return null
  JSON.parse(t)
  return t
}

async function save() {
  if (!tempVerId.value) return
  error.value = null
  if (!form.nodeKey.trim() || !form.nodeType.trim()) {
    error.value = 'nodeKey/nodeType 不能为空'
    return
  }
  let configJson: string | null = null
  try {
    configJson = normalizeJson(form.configJson)
  } catch (e: any) {
    error.value = e?.message ?? 'configJson 非法'
    return
  }
  const sortNo = form.sortNo.trim() ? Number(form.sortNo.trim()) : null
  if (form.sortNo.trim() && Number.isNaN(sortNo as any)) {
    error.value = 'sortNo 非法'
    return
  }
  saving.value = true
  try {
    await httpPost('/v1/system/flow/temp-ver-nodes/upsert', {
      id: editingId.value,
      tempVerId: tempVerId.value,
      nodeKey: form.nodeKey.trim(),
      parentNodeKey: form.parentNodeKey.trim() ? form.parentNodeKey.trim() : null,
      nodeType: form.nodeType.trim(),
      nodeName: form.nodeName.trim() ? form.nodeName.trim() : null,
      sortNo,
      status: 1,
      configJson
    })
    uni.showToast({ title: '已保存', icon: 'success' })
    closePopup()
    reload()
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  if (!ensureSystemContext()) return
  if (!tempVerId.value) {
    uni.showToast({ title: '缺少 tempVerId', icon: 'none' })
    return
  }
  load()
})
</script>

