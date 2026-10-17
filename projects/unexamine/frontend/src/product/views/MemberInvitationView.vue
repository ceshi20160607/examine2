<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, ApiError } from '../api'
import { platformTokens } from '../session'
import { establishSystemSession } from '../system-entry'

interface InvitationPreview {
  systemId: number
  systemName: string
  tenantName: string
  departmentName: string
  targetAccount?: string
  status: string
  expireTime: string
}

const route = useRoute()
const router = useRouter()
const token = typeof route.params.token === 'string' ? route.params.token : ''
const invitation = ref<InvitationPreview>()
const acceptedSystemId = ref<number>()
const loading = ref(false)
const error = ref('')
const errorCode = ref('')

async function load() {
  if (!platformTokens.value?.accessToken || !token) return
  loading.value = true
  error.value = ''
  errorCode.value = ''
  try {
    invitation.value = await api<InvitationPreview>('/api/invitations/preview', {
      method: 'POST', body: JSON.stringify({ token }),
    }, platformTokens.value.accessToken)
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '邀请加载失败'
    errorCode.value = reason instanceof ApiError ? reason.code : ''
  }
  finally { loading.value = false }
}

async function accept() {
  if (!platformTokens.value?.accessToken || !token) return
  loading.value = true
  error.value = ''
  try {
    const result = await api<{ systemId: number }>('/api/invitations/accept', {
      method: 'POST', body: JSON.stringify({ token }),
    }, platformTokens.value.accessToken)
    acceptedSystemId.value = result.systemId
  } catch (reason) { error.value = reason instanceof ApiError ? reason.message : '接受邀请失败' }
  finally { loading.value = false }
}

async function enter() {
  if (!acceptedSystemId.value) return
  try {
    await establishSystemSession(acceptedSystemId.value)
    await router.replace(`/systems/${acceptedSystemId.value}`)
  } catch (reason) { error.value = reason instanceof ApiError ? reason.message : '进入系统失败，请从系统列表重试' }
}

onMounted(load)
</script>

<template>
  <main class="invitation-page">
    <section class="panel-card invitation-card">
      <span class="eyebrow">系统成员邀请</span>
      <h1 v-if="acceptedSystemId">已加入系统</h1>
      <h1 v-else>接受邀请，开始协作</h1>
      <a-alert v-if="error" type="error" show-icon :message="error" />
      <template v-if="acceptedSystemId">
        <p>成员身份已经开通。现在可以进入系统，查看获授权的业务模块。</p>
        <a-button type="primary" @click="enter">进入系统</a-button>
      </template>
      <template v-else-if="!platformTokens?.accessToken">
        <p>请先登录平台账号；还没有账号可先注册，完成后会返回本邀请页。</p>
        <div class="invitation-actions"><a-button type="primary" @click="router.push({ path: '/login', query: { redirect: route.fullPath } })">登录</a-button><a-button @click="router.push({ path: '/register', query: { redirect: route.fullPath } })">注册账号</a-button></div>
      </template>
      <template v-else-if="invitation">
        <div class="invitation-details"><div><small>系统</small><strong>{{ invitation.systemName }}</strong></div><div><small>工作空间</small><strong>{{ invitation.tenantName }}</strong></div><div><small>所属部门</small><strong>{{ invitation.departmentName }}</strong></div><div><small>接收账号</small><strong>{{ invitation.targetAccount || '当前持链接账号' }}</strong></div></div>
        <p>邀请截止：{{ invitation.expireTime }}</p>
        <a-button type="primary" :loading="loading" :disabled="invitation.status !== 'PENDING'" @click="accept">{{ invitation.status === 'PENDING' ? '接受邀请' : '邀请已结束' }}</a-button>
      </template>
      <a-spin v-else-if="loading" />
      <div v-else class="invitation-actions"><a-button v-if="errorCode === 'INVITATION_ACCOUNT_MISMATCH'" type="primary" @click="router.push({ path: '/login', query: { redirect: route.fullPath } })">切换账号</a-button><a-button @click="load">重新加载邀请</a-button></div>
    </section>
  </main>
</template>
