<template>
  <view style="padding: 16px">
    <uni-card title="登录">
      <uni-forms :modelValue="form" labelPosition="top">
        <uni-forms-item label="用户名">
          <uni-easyinput v-model="form.username" placeholder="请输入用户名" />
        </uni-forms-item>
        <uni-forms-item label="密码">
          <uni-easyinput v-model="form.password" type="password" placeholder="请输入密码" />
        </uni-forms-item>
      </uni-forms>

      <view style="margin-top: 12px">
        <uni-button type="primary" :disabled="submitting" @click="doLogin">登录</uni-button>
      </view>

      <view v-if="error" style="margin-top: 12px; color: #d00">{{ error }}</view>
    </uni-card>

    <view style="margin-top: 12px">
      <uni-button @click="goHealth">Back to Health</uni-button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { httpPost } from '@/api/http'

const submitting = ref(false)
const error = ref<string | null>(null)

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
    uni.setStorageSync('token', r.data.token)
    uni.showToast({ title: '登录成功', icon: 'success' })
    // 下一步：系统列表/创建系统
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
</script>

