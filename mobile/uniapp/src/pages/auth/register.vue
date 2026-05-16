<template>
  <Page title="注册" subtitle="创建平台账号后登录使用">
    <view class="u-card">
      <uni-forms :modelValue="form" labelPosition="top">
        <uni-forms-item label="用户名">
          <uni-easyinput v-model="form.username" placeholder="请输入用户名" />
        </uni-forms-item>
        <uni-forms-item label="密码">
          <uni-easyinput v-model="form.password" type="password" placeholder="请输入密码" />
        </uni-forms-item>
        <uni-forms-item label="确认密码">
          <uni-easyinput v-model="form.password2" type="password" placeholder="再次输入密码" />
        </uni-forms-item>
      </uni-forms>

      <view style="margin-top: 12px">
        <ActionBar>
          <uni-button type="primary" :disabled="submitting" @click="doRegister">注册</uni-button>
          <uni-button :disabled="submitting" @click="goLogin">已有账号</uni-button>
        </ActionBar>
      </view>

      <ErrorBlock :text="error" />
    </view>
  </Page>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'
import { register } from '@/api/platformAuth'

const submitting = ref(false)
const error = ref<string | null>(null)
const form = reactive({ username: '', password: '', password2: '' })

async function doRegister() {
  const username = form.username.trim()
  const password = form.password
  if (!username || !password) {
    error.value = '请输入用户名和密码'
    return
  }
  if (password !== form.password2) {
    error.value = '两次密码不一致'
    return
  }
  submitting.value = true
  error.value = null
  try {
    await register(username, password)
    uni.showToast({ title: '注册成功，请登录', icon: 'success' })
    setTimeout(() => {
      uni.redirectTo({ url: '/pages/auth/login' })
    }, 500)
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  } finally {
    submitting.value = false
  }
}

function goLogin() {
  uni.navigateBack({
    fail: () => {
      uni.redirectTo({ url: '/pages/auth/login' })
    }
  })
}
</script>
