<template>
  <Page :title="`节点设置（tempVerId=${tempVerId}）`" subtitle="按 nodeKey 配置节点级异常兜底（弹窗编辑）">
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
            v-for="s in rows"
            :key="String(s.id)"
            :title="`${s.nodeKey || ''} mode=${s.exceptionMode || ''}`"
            :note="`admin=${s.exceptionAdminPlatId || ''} status=${s.status ?? ''}`"
            clickable
            @click="openActions(s)"
          />
        </uni-list>
        <EmptyState v-else text="暂无节点设置" />
      </view>
    </view>

    <uni-popup ref="popupRef" type="bottom">
      <view class="u-card">
        <uni-forms labelPosition="top">
          <uni-forms-item label="nodeKey">
            <uni-easyinput v-model="form.nodeKey" />
          </uni-forms-item>
          <uni-forms-item label="exceptionMode">
            <uni-easyinput v-model="form.exceptionMode" placeholder="fallback_admin/end_record" />
          </uni-forms-item>
          <uni-forms-item label="exceptionAdminPlatId(可选)">
            <uni-easyinput v-model="form.exceptionAdminPlatId" type="number" />
          </uni-forms-item>
          <uni-forms-item label="exceptionEndReason(可选)">
            <uni-easyinput v-model="form.exceptionEndReason" />
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

type NodeSettingRow = { id: number | string; nodeKey?: string; exceptionMode?: string; exceptionAdminPlatId?: number; exceptionEndReason?: string; status?: number }

const tempVerId = ref<number>(0)
const loading = ref(false)
const saving = ref(false)
const rows = ref<NodeSettingRow[]>([])
const editingId = ref<any>(null)
const error = ref<string | null>(null)

const popupRef = ref<any>(null)
const form = reactive<{ nodeKey: string; exceptionMode: string; exceptionAdminPlatId: string; exceptionEndReason: string }>({
  nodeKey: '',
  exceptionMode: 'fallback_admin',
  exceptionAdminPlatId: '',
  exceptionEndReason: ''
})

onLoad((opts) => {
  tempVerId.value = Number((opts as any)?.tempVerId || 0) || 0
})

async function load() {
  if (!tempVerId.value) return
  loading.value = true
  try {
    const r = await httpGet<any>(`/v1/system/flow/temp-ver-node-settings/page?tempVerId=${tempVerId.value}&page=1&size=200`)
    rows.value = (r.data?.records || []) as NodeSettingRow[]
  } finally {
    loading.value = false
  }
}

async function reload() {
  await load()
}

function openEdit(s?: NodeSettingRow) {
  editingId.value = s?.id ?? null
  form.nodeKey = String(s?.nodeKey || '')
  form.exceptionMode = String(s?.exceptionMode || 'fallback_admin')
  form.exceptionAdminPlatId = s?.exceptionAdminPlatId == null ? '' : String(s.exceptionAdminPlatId)
  form.exceptionEndReason = String(s?.exceptionEndReason || '')
  error.value = null
  popupRef.value?.open()
}

function closePopup() {
  popupRef.value?.close()
}

function openActions(s: NodeSettingRow) {
  if (!s?.id) return
  uni.showActionSheet({
    itemList: ['编辑', '删除'],
    success: (res) => {
      if (res.tapIndex === 0) return openEdit(s)
      if (res.tapIndex === 1) return del(s.id)
    }
  })
}

function del(id: any) {
  uni.showModal({
    title: '确认删除？',
    content: `将删除 nodeSetting #${id}`,
    success: async (m) => {
      if (!m.confirm) return
      await httpPost('/v1/system/flow/temp-ver-node-settings/delete', { ids: [id] })
      uni.showToast({ title: '已删除', icon: 'success' })
      reload()
    }
  })
}

async function save() {
  if (!tempVerId.value) return
  error.value = null
  if (!form.nodeKey.trim()) {
    error.value = 'nodeKey 不能为空'
    return
  }
  const adminRaw = form.exceptionAdminPlatId.trim()
  const admin = adminRaw ? Number(adminRaw) : null
  if (adminRaw && (!admin || Number.isNaN(admin))) {
    error.value = 'exceptionAdminPlatId 非法'
    return
  }
  saving.value = true
  try {
    await httpPost('/v1/system/flow/temp-ver-node-settings/upsert', {
      id: editingId.value,
      tempVerId: tempVerId.value,
      nodeKey: form.nodeKey.trim(),
      exceptionMode: form.exceptionMode.trim() || null,
      exceptionAdminPlatId: admin,
      exceptionEndReason: form.exceptionEndReason.trim() ? form.exceptionEndReason.trim() : null,
      status: 1
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

