<template>
  <Page :title="`版本连线（tempVerId=${tempVerId}）`" subtitle="连线元数据配置（可配置条件）">
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
            v-for="l in rows"
            :key="String(l.id)"
            :title="`${l.fromNodeKey || ''} -> ${l.toNodeKey || ''}${l.isDefault === 1 ? ' (default)' : ''}`"
            :note="`priority=${l.priority ?? ''} status=${l.status ?? ''}`"
            clickable
            @click="openActions(l)"
          />
        </uni-list>
        <EmptyState v-else text="暂无连线" />
      </view>
    </view>

    <uni-popup ref="popupRef" type="bottom">
      <view class="u-card">
        <uni-forms labelPosition="top">
          <uni-forms-item label="fromNodeKey">
            <uni-easyinput v-model="form.fromNodeKey" />
          </uni-forms-item>
          <uni-forms-item label="toNodeKey">
            <uni-easyinput v-model="form.toNodeKey" />
          </uni-forms-item>
          <uni-forms-item label="priority(可选)">
            <uni-easyinput v-model="form.priority" type="number" />
          </uni-forms-item>
          <uni-forms-item label="isDefault(0/1)">
            <uni-data-select v-model="form.isDefault" :localdata="defaultOptions" />
          </uni-forms-item>
          <uni-forms-item label="remark(可选)">
            <uni-easyinput v-model="form.remark" />
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

type LineRow = { id: number | string; fromNodeKey?: string; toNodeKey?: string; priority?: number; isDefault?: number; status?: number; remark?: string }

const tempVerId = ref<number>(0)
const loading = ref(false)
const saving = ref(false)
const rows = ref<LineRow[]>([])
const editingId = ref<any>(null)
const error = ref<string | null>(null)

const popupRef = ref<any>(null)
const defaultOptions = [
  { value: 0, text: '否' },
  { value: 1, text: '是' }
]
const form = reactive<{ fromNodeKey: string; toNodeKey: string; priority: string; isDefault: number; remark: string }>({
  fromNodeKey: '',
  toNodeKey: '',
  priority: '',
  isDefault: 0,
  remark: ''
})

onLoad((opts) => {
  tempVerId.value = Number((opts as any)?.tempVerId || 0) || 0
})

async function load() {
  if (!tempVerId.value) return
  loading.value = true
  try {
    const r = await httpGet<any>(`/v1/system/flow/temp-ver-lines/page?tempVerId=${tempVerId.value}&page=1&size=200`)
    rows.value = (r.data?.records || []) as LineRow[]
  } finally {
    loading.value = false
  }
}

async function reload() {
  await load()
}

function openEdit(l?: LineRow) {
  editingId.value = l?.id ?? null
  form.fromNodeKey = String(l?.fromNodeKey || '')
  form.toNodeKey = String(l?.toNodeKey || '')
  form.priority = l?.priority == null ? '' : String(l.priority)
  form.isDefault = Number(l?.isDefault || 0) || 0
  form.remark = String(l?.remark || '')
  error.value = null
  popupRef.value?.open()
}

function closePopup() {
  popupRef.value?.close()
}

function openActions(l: LineRow) {
  if (!l?.id) return
  uni.showActionSheet({
    itemList: ['编辑', '条件(conds)', '删除'],
    success: (res) => {
      if (res.tapIndex === 0) return openEdit(l)
      if (res.tapIndex === 1) return goConds(l.id)
      if (res.tapIndex === 2) return del(l.id)
    }
  })
}

function goConds(lineId: any) {
  uni.navigateTo({ url: `/pages/system/flow/temp_ver_line_conds?lineId=${encodeURIComponent(String(lineId))}` })
}

function del(id: any) {
  uni.showModal({
    title: '确认删除？',
    content: `将删除连线 #${id}`,
    success: async (m) => {
      if (!m.confirm) return
      await httpPost('/v1/system/flow/temp-ver-lines/delete', { ids: [id] })
      uni.showToast({ title: '已删除', icon: 'success' })
      reload()
    }
  })
}

async function save() {
  if (!tempVerId.value) return
  error.value = null
  if (!form.fromNodeKey.trim() || !form.toNodeKey.trim()) {
    error.value = 'from/to 不能为空'
    return
  }
  const pr = form.priority.trim() ? Number(form.priority.trim()) : null
  if (form.priority.trim() && Number.isNaN(pr as any)) {
    error.value = 'priority 非法'
    return
  }
  saving.value = true
  try {
    await httpPost('/v1/system/flow/temp-ver-lines/upsert', {
      id: editingId.value,
      tempVerId: tempVerId.value,
      fromNodeKey: form.fromNodeKey.trim(),
      toNodeKey: form.toNodeKey.trim(),
      priority: pr,
      isDefault: form.isDefault,
      status: 1,
      remark: form.remark.trim() ? form.remark.trim() : null
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

