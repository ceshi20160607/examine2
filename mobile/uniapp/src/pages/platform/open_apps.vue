<template>
  <Page title="OpenAPI 应用" subtitle="对外 accessKey/secret（平台级）">
    <view class="u-card u-section">
      <uni-forms labelPosition="top">
        <uni-forms-item label="clientCode">
          <uni-easyinput v-model="form.clientCode" placeholder="唯一编码" />
        </uni-forms-item>
        <uni-forms-item label="clientName">
          <uni-easyinput v-model="form.clientName" placeholder="应用名称" />
        </uni-forms-item>
        <uni-forms-item label="remark(可选)">
          <uni-easyinput v-model="form.remark" placeholder="备注" />
        </uni-forms-item>
      </uni-forms>
      <ActionBar>
        <uni-button type="primary" :disabled="creating" @click="create">创建</uni-button>
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
      </ActionBar>
      <view class="u-subtitle">secret 仅在创建/轮换时展示一次，请立即保存。</view>
      <ErrorBlock :text="error" />
    </view>

    <view class="u-card u-section">
      <view class="u-title">应用列表</view>
      <view style="margin-top: 12px">
        <uni-list v-if="rows.length">
          <uni-list-item
            v-for="a in rows"
            :key="a.id"
            :title="a.clientName || a.clientCode || ('App#' + a.id)"
            :note="`${a.clientCode || ''} · ${statusText(a.status)}`"
            clickable
            @click="goDetail(a.id)"
          />
        </uni-list>
        <EmptyState v-else text="暂无 OpenAPI 应用" />
      </view>
    </view>
  </Page>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ensureLogin } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'
import { usePageRequest } from '@/composables/usePageRequest'
import { createOpenApp, listOpenApps, type OpenAppClient } from '@/api/platformApp'

const rows = ref<OpenAppClient[]>([])
const creating = ref(false)
const { loading, error, run, capture, clearError } = usePageRequest()
const form = reactive({ clientCode: '', clientName: '', remark: '' })

function statusText(st?: number) {
  if (st === 1) return '启用'
  if (st === 2) return '停用'
  return `status=${st ?? '-'}`
}

async function load() {
  await run(async () => {
    const r = await listOpenApps()
    rows.value = r.data || []
  })
}

function showCredential(title: string, accessKey?: string, secret?: string) {
  const content = `accessKey:\n${accessKey || '-'}\n\nsecret:\n${secret || '-'}`
  uni.showModal({
    title,
    content,
    confirmText: '复制 secret',
    success: (res) => {
      if (res.confirm && secret) {
        uni.setClipboardData({ data: `accessKey=${accessKey}\nsecret=${secret}` })
      }
    }
  })
}

async function create() {
  if (!form.clientCode.trim() || !form.clientName.trim()) {
    uni.showToast({ title: '请填写 clientCode/clientName', icon: 'none' })
    return
  }
  creating.value = true
  clearError()
  try {
    const r = await createOpenApp({
      clientCode: form.clientCode.trim(),
      clientName: form.clientName.trim(),
      remark: form.remark.trim() || null
    })
    form.clientCode = ''
    form.clientName = ''
    form.remark = ''
    const d = r.data
    showCredential('创建成功（请保存密钥）', d?.accessKey, d?.secret)
    await load()
  } catch (e: unknown) {
    capture(e)
  } finally {
    creating.value = false
  }
}

function goDetail(id: number) {
  uni.navigateTo({ url: `/pages/platform/open_app_detail?id=${id}` })
}

onMounted(() => {
  if (!ensureLogin()) return
  load()
})
</script>
