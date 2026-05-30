<template>
  <Page :title="title" subtitle="详情 / 启停 / 轮换密钥 / 删除">
    <view class="u-card u-section">
      <view v-if="client" class="u-subtitle">
        code: {{ client.clientCode }} · {{ statusText(client.status) }}
      </view>
      <view v-if="activeAccessKey" class="u-subtitle">activeAccessKey: {{ activeAccessKey }}</view>

      <ActionBar>
        <uni-button type="primary" :disabled="loading" @click="load">刷新</uni-button>
        <uni-button :disabled="!client || client.status === 1" @click="setStatus(1)">启用</uni-button>
        <uni-button :disabled="!client || client.status === 2" @click="setStatus(2)">停用</uni-button>
        <uni-button @click="rotate">轮换 secret</uni-button>
        <uni-button type="warn" @click="remove">删除</uni-button>
      </ActionBar>
      <ErrorBlock :text="error" />
    </view>

    <view v-if="client" class="u-card u-section">
      <view class="u-title">编辑资料</view>
      <uni-forms labelPosition="top">
        <uni-forms-item label="clientName">
          <uni-easyinput v-model="editForm.clientName" />
        </uni-forms-item>
        <uni-forms-item label="contactName">
          <uni-easyinput v-model="editForm.contactName" />
        </uni-forms-item>
        <uni-forms-item label="contactMobile">
          <uni-easyinput v-model="editForm.contactMobile" />
        </uni-forms-item>
        <uni-forms-item label="contactEmail">
          <uni-easyinput v-model="editForm.contactEmail" />
        </uni-forms-item>
        <uni-forms-item label="remark">
          <uni-easyinput v-model="editForm.remark" />
        </uni-forms-item>
      </uni-forms>
      <ActionBar>
        <uni-button type="primary" :disabled="saving" @click="save">保存</uni-button>
      </ActionBar>
    </view>
  </Page>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { ensureLogin } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'
import { usePageRequest } from '@/composables/usePageRequest'
import {
  deleteOpenApp,
  getOpenApp,
  rotateOpenAppSecret,
  setOpenAppStatus,
  updateOpenApp,
  type OpenAppClient
} from '@/api/platformApp'
import { hasId, idToString } from '@/utils/id'

const id = ref('')
const client = ref<OpenAppClient | null>(null)
const activeAccessKey = ref('')
const saving = ref(false)
const { loading, error, run, capture, clearError } = usePageRequest()

const editForm = reactive({
  clientName: '',
  contactName: '',
  contactMobile: '',
  contactEmail: '',
  remark: ''
})

const title = computed(() => client.value?.clientName || `OpenApp #${id.value}`)

onLoad((opts) => {
  id.value = idToString((opts as any)?.id)
})

function statusText(st?: number) {
  if (st === 1) return '启用'
  if (st === 2) return '停用'
  return `status=${st ?? '-'}`
}

function fillEdit(c: OpenAppClient) {
  editForm.clientName = c.clientName || ''
  editForm.contactName = c.contactName || ''
  editForm.contactMobile = c.contactMobile || ''
  editForm.contactEmail = c.contactEmail || ''
  editForm.remark = c.remark || ''
}

async function load() {
  if (!hasId(id.value)) return
  await run(async () => {
    const r = await getOpenApp(id.value)
    client.value = r.data?.client || null
    activeAccessKey.value = r.data?.activeAccessKey || ''
    if (client.value) fillEdit(client.value)
  })
}

function showCredential(title: string, accessKey?: string, secret?: string) {
  const content = `accessKey:\n${accessKey || '-'}\n\nsecret:\n${secret || '-'}`
  uni.showModal({
    title,
    content,
    confirmText: '复制',
    success: (res) => {
      if (res.confirm && secret) {
        uni.setClipboardData({ data: `accessKey=${accessKey}\nsecret=${secret}` })
      }
    }
  })
}

async function setStatus(status: 1 | 2) {
  if (!hasId(id.value)) return
  clearError()
  try {
    await setOpenAppStatus(id.value, status)
    await load()
  } catch (e: unknown) {
    capture(e)
  }
}

async function rotate() {
  if (!hasId(id.value)) return
  uni.showModal({
    title: '轮换 secret？',
    content: '旧凭证将停用，新 secret 仅展示一次',
    success: async (m) => {
      if (!m.confirm) return
      clearError()
      try {
        const r = await rotateOpenAppSecret(id.value)
        showCredential('轮换成功', r.data?.accessKey, r.data?.secret)
        await load()
      } catch (e: unknown) {
        capture(e)
      }
    }
  })
}

async function save() {
  if (!hasId(id.value) || !editForm.clientName.trim()) {
    uni.showToast({ title: 'clientName 不能为空', icon: 'none' })
    return
  }
  saving.value = true
  clearError()
  try {
    await updateOpenApp(id.value, {
      clientName: editForm.clientName.trim(),
      contactName: editForm.contactName.trim() || null,
      contactMobile: editForm.contactMobile.trim() || null,
      contactEmail: editForm.contactEmail.trim() || null,
      remark: editForm.remark.trim() || null
    })
    uni.showToast({ title: '已保存', icon: 'success' })
    await load()
  } catch (e: unknown) {
    capture(e)
  } finally {
    saving.value = false
  }
}

function remove() {
  if (!hasId(id.value)) return
  uni.showModal({
    title: '确认删除？',
    success: async (m) => {
      if (!m.confirm) return
      try {
        await deleteOpenApp(id.value)
        uni.showToast({ title: '已删除', icon: 'success' })
        uni.navigateBack()
      } catch (e: unknown) {
        capture(e)
      }
    }
  })
}

onMounted(() => {
  if (!ensureLogin()) return
  load()
})
</script>
