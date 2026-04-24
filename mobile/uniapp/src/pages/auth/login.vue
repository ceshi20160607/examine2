<template>
  <Page title="登录" subtitle="登录后进入系统列表">
    <view class="u-card">
      <uni-forms :modelValue="form" labelPosition="top">
        <uni-forms-item label="用户名">
          <uni-easyinput v-model="form.username" placeholder="请输入用户名" />
        </uni-forms-item>
        <uni-forms-item label="密码">
          <uni-easyinput v-model="form.password" type="password" placeholder="请输入密码" />
        </uni-forms-item>
      </uni-forms>

      <view style="margin-top: 12px">
        <ActionBar>
          <uni-button type="primary" :disabled="submitting" @click="doLogin">登录</uni-button>
          <uni-button :disabled="submitting" @click="goHealth">健康检查</uni-button>
        </ActionBar>
      </view>

      <view v-if="error" style="margin-top: 12px; color: #d00">{{ error }}</view>
    </view>
  </Page>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { httpPost } from '@/api/http'
import { getSessionPayload } from '@/store/context'
import { hasToken } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import { useSessionStore } from '@/stores/session'

const submitting = ref(false)
const error = ref<string | null>(null)
const session = useSessionStore()

const form = reactive({
  username: '',
  password: ''
})

async function doLogin() {
  if (!form.username.trim() || !form.password) {
    error.value = '请输入用户名和密码'
    return
  }
  submitting.value = true
  error.value = null
  try {
    const r = await httpPost<{ token: string; account: any }>('/v1/platform/auth/login', {
      username: form.username.trim(),
      password: form.password
    })
    session.setToken(r.data.token)
    uni.showToast({ title: '登录成功', icon: 'success' })
    // 登录后先进入系统选择/创建
    uni.reLaunch({ url: '/pages/platform/systems' })
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  } finally {
    submitting.value = false
  }
}

function goHealth() {
  uni.navigateTo({ url: '/pages/boot/health' })
}

onMounted(() => {
  if (!hasToken()) return
  const p = getSessionPayload()
  if (p && p.systemId) {
    uni.reLaunch({ url: '/pages/tabs/workbench' })
  } else {
    uni.reLaunch({ url: '/pages/platform/systems' })
  }
})
</script>

