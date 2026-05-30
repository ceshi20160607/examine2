<template>
  <Page :title="`全局设置（tempVerId=${tempVerId}）`" subtitle="异常兜底策略等全局配置（弹窗编辑）">
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
            :title="`${s.exceptionMode || 'setting'} (status=${s.status ?? ''})`"
            :note="`admin=${s.exceptionAdminPlatId || ''}`"
            clickable
            @click="openActions(s)"
          />
        </uni-list>
        <EmptyState v-else text="暂无设置" />
      </view>
    </view>

    <uni-popup ref="popupRef" type="bottom">
      <view class="u-card">
        <uni-forms labelPosition="top">
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
import { ensureSystemContext } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'
import { deleteTempVerSettings, pageTempVerSettings, upsertTempVerSetting } from '@/api/flow'
import { hasId, idToString } from '@/utils/id'

type SettingRow = { id: number | string; exceptionMode?: string; exceptionAdminPlatId?: string | number; exceptionEndReason?: string; status?: number }

const tempVerId = ref('')
const loading = ref(false)
const saving = ref(false)
const rows = ref<SettingRow[]>([])
const editingId = ref<any>(null)
const error = ref<string | null>(null)

const popupRef = ref<any>(null)
const form = reactive<{ exceptionMode: string; exceptionAdminPlatId: string; exceptionEndReason: string }>({
  exceptionMode: 'fallback_admin',
  exceptionAdminPlatId: '',
  exceptionEndReason: ''
})

onLoad((opts) => {
  tempVerId.value = idToString((opts as any)?.tempVerId)
})

async function load() {
  if (!hasId(tempVerId.value)) return
  loading.value = true
  try {
    const r = await pageTempVerSettings(tempVerId.value)
    rows.value = (r.data?.records || []) as SettingRow[]
  } finally {
    loading.value = false
  }
}

async function reload() {
  await load()
}

function openEdit(s?: SettingRow) {
  editingId.value = s?.id ?? null
  form.exceptionMode = String(s?.exceptionMode || 'fallback_admin')
  form.exceptionAdminPlatId = s?.exceptionAdminPlatId == null ? '' : String(s.exceptionAdminPlatId)
  form.exceptionEndReason = String(s?.exceptionEndReason || '')
  error.value = null
  popupRef.value?.open()
}

function closePopup() {
  popupRef.value?.close()
}

function openActions(s: SettingRow) {
  if (!hasId(s?.id)) return
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
    content: `将删除 setting #${id}`,
    success: async (m) => {
      if (!m.confirm) return
      await deleteTempVerSettings([id])
      uni.showToast({ title: '已删除', icon: 'success' })
      reload()
    }
  })
}

async function save() {
  if (!hasId(tempVerId.value)) return
  error.value = null
  const adminRaw = form.exceptionAdminPlatId.trim()
  const admin = hasId(adminRaw) ? adminRaw : null
  if (adminRaw && !/^\d+$/.test(adminRaw)) {
    error.value = 'exceptionAdminPlatId 非法'
    return
  }
  saving.value = true
  try {
    await upsertTempVerSetting({
      id: editingId.value,
      tempVerId: tempVerId.value,
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
  if (!hasId(tempVerId.value)) {
    uni.showToast({ title: '缺少 tempVerId', icon: 'none' })
    return
  }
  load()
})
</script>

