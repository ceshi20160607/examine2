<script setup lang="ts">
import { message } from 'ant-design-vue'
import { GlobalOutlined } from '@ant-design/icons-vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, ApiError } from '../api'
import { establishTenantSession } from '../system-entry'
import ProductMark from '../components/ProductMark.vue'
import { clearSession, clearSystemSession, platformContext, platformTokens, setPlatformSession, setSystemSession, type CurrentContext, type SessionTokens } from '../session'

const props = defineProps<{ initialMode: 'login' | 'register' | 'forgot' | 'reset' }>()
const router = useRouter()
const route = useRoute()
const mode = ref(props.initialMode)
const registrationGoal = ref<'join' | 'create'>('join')
const loading = ref(false)
const error = ref('')
const providers = ref<Array<{ code: string; name: string; protocol: string }>>([])
const passwordResetAvailability = ref<'loading' | 'available' | 'unavailable' | 'error'>('loading')
const startingProvider = ref('')
const directoryProvider = ref<{ code: string; name: string } | null>(null)
const directoryLogin = reactive({ username: '', password: '' })
const login = reactive({ username: '', password: '' })
const forgot = reactive({ account: '' })
const reset = reactive({ newPassword: '', confirmation: '' })
const resetToken = computed(() => typeof route.query.token === 'string' ? route.query.token : '')
const accessTarget = computed(() => {
  if (typeof route.params.systemId === 'string' && /^\d+$/.test(route.params.systemId)
      && typeof route.params.tenantId === 'string' && /^\d+$/.test(route.params.tenantId)) {
    return `/api/auth/access/id/${route.params.systemId}/${route.params.tenantId}`
  }
  return typeof route.params.systemCode === 'string' && typeof route.params.tenantCode === 'string'
    ? `/api/auth/access/${encodeURIComponent(route.params.systemCode)}/${encodeURIComponent(route.params.tenantCode)}` : null
})
const accessEntry = ref<{ systemId: number; systemName: string; tenantMode: string; tenantId: number; tenantName: string;
  loginTitle?: string | null; loginSubtitle?: string | null } | null>(null)
const accessLoading = ref(false)
const accessInvalid = ref(false)
// 只允许返回当前产品的邀请页，避免登录后跳往任意外部地址。
const invitationRedirect = computed(() => typeof route.query.redirect === 'string'
  && /^\/invitations\/[A-Za-z0-9_-]{20,100}$/.test(route.query.redirect) ? route.query.redirect : '')
const submittedRecovery = ref('')
const register = reactive({
  username: '', password: '', displayName: '', email: '', systemName: '',
})
const title = computed(() => mode.value === 'login' ? (accessEntry.value
  ? accessEntry.value.loginTitle || `登录${accessEntry.value.tenantName}` : accessInvalid.value ? '工作空间不可用' : '欢迎回来')
  : mode.value === 'forgot' ? '找回密码'
    : mode.value === 'reset' ? '设置新密码'
      : registrationGoal.value === 'create' ? '创建你的业务系统' : '注册平台账号')
const subtitle = computed(() => mode.value === 'login' ? (accessEntry.value
  ? accessEntry.value.loginSubtitle || `进入${accessEntry.value.systemName}，继续处理当前工作空间的业务`
  : accessInvalid.value ? '请联系系统管理员确认访问地址和工作空间状态' : '登录并继续处理你的业务工作')
  : mode.value === 'forgot' ? (passwordResetAvailability.value === 'available'
    ? '输入用户名或绑定邮箱，获取一次性重置链接' : '当前无法申请密码找回链接')
    : mode.value === 'reset' ? '新密码生效后，所有旧设备会话都会退出'
      : registrationGoal.value === 'create' ? '注册账号，同时创建并配置业务系统'
        : '注册后可申请进入已有系统，或由系统管理员为你开通')

watch(() => props.initialMode, (value) => {
  mode.value = value
  error.value = ''
  submittedRecovery.value = ''
})

// 专属路径的展示信息来自服务端，停用或不存在的空间不允许回落到平台通用登录。
watch(accessTarget, async target => {
  accessEntry.value = null
  accessInvalid.value = false
  if (!target) return
  accessLoading.value = true
  try {
    accessEntry.value = await api(target)
    if (platformTokens.value?.accessToken && platformContext.value && accessEntry.value) {
      try {
        const entry = await establishTenantSession(accessEntry.value.systemId,
          accessEntry.value.tenantId, accessEntry.value.tenantMode)
        await router.replace(`/systems/${entry.systemId}`)
      } catch {
        error.value = '当前账号没有该工作空间的有效成员身份；可使用其他账号登录。'
      }
    }
  } catch {
    accessInvalid.value = true
    error.value = '该工作空间访问地址不存在或已停用'
  } finally {
    accessLoading.value = false
  }
}, { immediate: true })

async function loadContext(token: string) {
  return api<CurrentContext>('/api/auth/me', {}, token)
}

async function submitLogin() {
  if (accessTarget.value && !accessEntry.value) {
    error.value = '该工作空间访问地址尚未就绪，请刷新后重试'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const tokens = await api<SessionTokens>('/api/auth/login', {
      method: 'POST', body: JSON.stringify(login),
    })
    const context = await loadContext(tokens.accessToken)
    setPlatformSession(tokens, context)
    clearSystemSession()
    if (accessEntry.value) {
      const entry = await establishTenantSession(accessEntry.value.systemId,
        accessEntry.value.tenantId, accessEntry.value.tenantMode)
      await router.replace(`/systems/${entry.systemId}`)
    } else {
      await router.replace(invitationRedirect.value || '/platform/dashboard')
    }
  } catch (reason) {
    error.value = readableError(reason)
  } finally {
    loading.value = false
  }
}

async function submitRegister() {
  loading.value = true
  error.value = ''
  try {
    const result = await api<{
      systemId: number | null
      tenantId: number | null
      accountId: number
      systemName: string | null
      tenantName: string | null
      tokens: SessionTokens | null
      platformTokens: SessionTokens
    }>('/api/auth/register', { method: 'POST', body: JSON.stringify({
      ...register, systemName: registrationGoal.value === 'create' ? register.systemName : null,
    }) })
    const platform = await loadContext(result.platformTokens.accessToken)
    setPlatformSession(result.platformTokens, platform)
    if (!result.systemId || !result.tokens) {
      clearSystemSession()
      message.success('平台账号已创建，可新建系统，也可申请访问已有系统')
      await router.replace(invitationRedirect.value || '/platform')
      return
    }
    const system = await loadContext(result.tokens.accessToken)
    setSystemSession(result.tokens, { ...system,
      systemName: result.systemName ?? undefined, tenantName: result.tenantName ?? undefined })
    message.success('系统已创建，正在进入系统')
    await router.replace(`/systems/${result.systemId}/admin`)
  } catch (reason) {
    error.value = readableError(reason)
  } finally {
    loading.value = false
  }
}

async function submitForgot() {
  if (passwordResetAvailability.value !== 'available') {
    error.value = '密码找回投递尚未配置，请联系平台管理员'
    return
  }
  loading.value = true
  error.value = ''
  submittedRecovery.value = ''
  try {
    const result = await api<{ deliveryMode: string }>('/api/auth/password-reset/request', {
      method: 'POST', body: JSON.stringify({ account: forgot.account }),
    })
    submittedRecovery.value = result.deliveryMode === 'LOCAL_OUTBOX'
      ? '开发环境已受理。请由服务器管理员从本地测试投递箱取得重置链接。'
      : '若账号已绑定邮箱，重置链接将发送到该邮箱。链接 30 分钟内有效。'
  } catch (reason) {
    error.value = readableError(reason)
  } finally {
    loading.value = false
  }
}

async function submitReset() {
  error.value = ''
  submittedRecovery.value = ''
  if (!resetToken.value) { error.value = '找回链接缺少令牌，请重新申请'; return }
  if (reset.newPassword !== reset.confirmation) { error.value = '两次输入的新密码不一致'; return }
  loading.value = true
  try {
    await api<void>('/api/auth/password-reset/confirm', {
      method: 'POST', body: JSON.stringify({ token: resetToken.value, newPassword: reset.newPassword }),
    })
    reset.newPassword = ''
    reset.confirmation = ''
    clearSession()
    submittedRecovery.value = '密码已重置，旧设备会话已退出。请返回登录。'
  } catch (reason) {
    error.value = readableError(reason)
  } finally {
    loading.value = false
  }
}

async function loadProviders() {
  try {
    const available = await api<Array<{ code: string; name: string; protocol: string }>>('/api/auth/sso/providers')
    providers.value = available
  } catch {
    providers.value = []
  }
}

async function loadPasswordResetAvailability() {
  try {
    const result = await api<{ available: boolean }>('/api/auth/password-reset/availability')
    passwordResetAvailability.value = result.available ? 'available' : 'unavailable'
  } catch {
    passwordResetAvailability.value = 'error'
  }
}

async function startSso(provider: { code: string; name: string }) {
  startingProvider.value = provider.code
  error.value = ''
  try {
    const result = await api<{ authorizationUrl: string; expireTime: string }>(`/api/auth/sso/${provider.code}/start`, { method: 'POST' })
    sessionStorage.setItem('unexamine.sso.provider', provider.code)
    window.location.assign(result.authorizationUrl)
  } catch (reason) {
    error.value = readableError(reason)
    startingProvider.value = ''
  }
}

// 目录账号密码只发送给当前已发布身份源的后端 Bind 入口，不写入浏览器存储。
async function submitDirectoryLogin() {
  if (!directoryProvider.value) return
  if (!directoryLogin.username.trim() || !directoryLogin.password) {
    error.value = '请输入目录用户名和密码'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const tokens = await api<SessionTokens>(`/api/auth/sso/${directoryProvider.value.code}/directory/login`, {
      method: 'POST', body: JSON.stringify(directoryLogin),
    })
    directoryLogin.password = ''
    const context = await loadContext(tokens.accessToken)
    setPlatformSession(tokens, context)
    clearSystemSession()
    if (accessEntry.value) {
      const entry = await establishTenantSession(accessEntry.value.systemId,
        accessEntry.value.tenantId, accessEntry.value.tenantMode)
      await router.replace(`/systems/${entry.systemId}`)
    } else {
      await router.replace(invitationRedirect.value || '/platform/dashboard')
    }
  } catch (reason) {
    directoryLogin.password = ''
    error.value = readableError(reason)
  } finally {
    loading.value = false
  }
}

function readableError(reason: unknown) {
  if (reason instanceof ApiError) {
    return reason.traceId ? `${reason.message}（追踪号：${reason.traceId}）` : reason.message
  }
  return '无法连接服务，请稍后重试'
}

function switchMode(next: 'login' | 'register' | 'forgot') {
  error.value = ''
  submittedRecovery.value = ''
  void router.push({ path: next === 'login' ? '/login' : next === 'register' ? '/register' : '/forgot-password',
    query: invitationRedirect.value ? { redirect: invitationRedirect.value } : {} })
}

onMounted(() => {
  void loadProviders()
  void loadPasswordResetAvailability()
})
</script>

<template>
  <main class="auth-page">
    <section class="auth-story">
      <ProductMark />
      <div class="auth-story__copy">
        <p class="eyebrow">可配置业务系统平台</p>
        <h1>把复杂的管理流程，<br>变成团队真正会用的系统。</h1>
        <p>从日常业务处理到系统配置，都在清晰一致的工作区中完成。</p>
      </div>
      <div class="auth-story__status">
        <span class="status-dot" /> 工作空间相互隔离，关键操作全程可追溯
      </div>
    </section>

    <section class="auth-panel">
      <div class="auth-mobile-brand"><ProductMark /></div>
      <div class="auth-card">
        <div class="auth-card__heading">
          <p class="eyebrow">{{ mode === 'login' ? '账号登录' : mode === 'register' ? '开始使用' : '账号安全' }}</p>
          <h2>{{ title }}</h2>
          <p>{{ subtitle }}</p>
        </div>

        <a-alert v-if="error" type="error" show-icon :message="error" class="auth-error" />
        <a-alert v-if="submittedRecovery" type="success" show-icon :message="submittedRecovery" class="auth-error" />
        <a-alert v-if="mode === 'reset' && !resetToken" type="warning" show-icon message="找回链接缺少令牌，请重新申请。" class="auth-error" />

        <a-form v-if="mode === 'login'" :model="login" layout="vertical" @finish="submitLogin">
          <a-form-item label="用户名" name="username" :rules="[{ required: true, message: '请输入用户名' }]">
            <a-input v-model:value="login.username" size="large" autocomplete="username" placeholder="请输入用户名" />
          </a-form-item>
          <a-form-item label="密码" name="password" :rules="[{ required: true, message: '请输入密码' }]">
            <a-input-password v-model:value="login.password" size="large" autocomplete="current-password" placeholder="请输入密码" />
          </a-form-item>
          <a-button type="primary" size="large" html-type="submit" block :loading="loading || accessLoading" :disabled="accessInvalid">登录</a-button>
          <p v-if="passwordResetAvailability === 'available'" class="auth-switch"><a @click="switchMode('forgot')">忘记密码？</a></p>
          <template v-if="providers.length && !accessTarget">
            <a-divider plain>或使用企业身份源</a-divider>
            <div class="sso-provider-list">
              <a-button v-for="provider in providers" :key="provider.code" size="large" block :loading="startingProvider === provider.code" @click="provider.protocol === 'LDAP' ? directoryProvider = provider : startSso(provider)">
                <GlobalOutlined />{{ provider.name }}<small>{{ provider.protocol }}</small>
              </a-button>
            </div>
          </template>
          <div v-if="directoryProvider" @keydown.enter.prevent="submitDirectoryLogin">
            <a-divider plain>{{ directoryProvider.name }}目录登录</a-divider>
            <a-form-item label="目录用户名">
              <a-input v-model:value="directoryLogin.username" size="large" autocomplete="username" />
            </a-form-item>
            <a-form-item label="目录密码">
              <a-input-password v-model:value="directoryLogin.password" size="large" autocomplete="current-password" />
            </a-form-item>
            <a-button type="primary" html-type="button" block :loading="loading" @click="submitDirectoryLogin">使用目录账号登录</a-button>
            <a-button block @click="directoryProvider = null; directoryLogin.password = ''">返回本地登录</a-button>
          </div>
          <p class="auth-switch">还没有账号？<a @click="switchMode('register')">注册平台账号</a></p>
        </a-form>

        <a-form v-else-if="mode === 'forgot'" :model="forgot" layout="vertical" @finish="submitForgot">
          <a-alert v-if="passwordResetAvailability === 'unavailable'" type="info" show-icon message="密码找回投递尚未配置，请联系平台管理员。" class="auth-error" />
          <a-alert v-else-if="passwordResetAvailability === 'error'" type="warning" show-icon message="暂时无法读取密码找回配置，请稍后重试。" class="auth-error" />
          <a-form-item label="用户名或绑定邮箱" name="account" :rules="[{ required: true, message: '请输入用户名或绑定邮箱' }]">
            <a-input v-model:value="forgot.account" size="large" autocomplete="username" placeholder="请输入用户名或绑定邮箱" />
          </a-form-item>
          <a-button v-if="passwordResetAvailability === 'available'" type="primary" size="large" html-type="submit" block :loading="loading">获取重置链接</a-button>
          <p class="auth-switch"><a @click="switchMode('login')">返回登录</a></p>
        </a-form>

        <a-form v-else-if="mode === 'reset'" :model="reset" layout="vertical" @finish="submitReset">
          <a-form-item label="新密码" name="newPassword" :rules="[{ required: true, message: '请输入新密码' }, { min: 12, message: '密码至少 12 位' }]">
            <a-input-password v-model:value="reset.newPassword" size="large" autocomplete="new-password" />
          </a-form-item>
          <a-form-item label="确认新密码" name="confirmation" :rules="[{ required: true, message: '请再次输入新密码' }]">
            <a-input-password v-model:value="reset.confirmation" size="large" autocomplete="new-password" />
          </a-form-item>
          <p class="auth-form-note">至少 12 位，包含大小写字母、数字和符号。</p>
          <a-button type="primary" size="large" html-type="submit" block :loading="loading" :disabled="!resetToken">重置密码</a-button>
          <p class="auth-switch"><a @click="switchMode('forgot')">链接失效？重新申请</a> · <a @click="switchMode('login')">返回登录</a></p>
        </a-form>

        <a-form v-else :model="register" layout="vertical" @finish="submitRegister">
          <a-segmented v-model:value="registrationGoal" block :options="[{ label: '加入已有系统', value: 'join' }, { label: '创建新系统', value: 'create' }]" class="auth-registration-goal" />
          <div class="form-grid">
            <a-form-item label="显示名称" name="displayName" :rules="[{ required: true, message: '请输入显示名称' }]"><a-input v-model:value="register.displayName" size="large" /></a-form-item>
            <a-form-item label="用户名" name="username" :rules="[{ required: true, message: '请输入用户名' }]"><a-input v-model:value="register.username" size="large" autocomplete="username" /></a-form-item>
          </div>
          <a-form-item label="邮箱" name="email" :rules="[{ type: 'email', message: '请输入正确的邮箱格式' }]"><a-input v-model:value="register.email" size="large" type="email" /></a-form-item>
          <a-form-item label="密码" name="password" :rules="[{ required: true, message: '请输入密码' }, { min: 8, message: '密码至少 8 位' }]"><a-input-password v-model:value="register.password" size="large" autocomplete="new-password" /></a-form-item>
          <a-form-item v-if="registrationGoal === 'create'" label="系统名称" name="systemName" :rules="[{ required: true, message: '请输入系统名称' }]">
            <a-input v-model:value="register.systemName" size="large" placeholder="例如：客户运营中心" />
          </a-form-item>
          <p class="auth-form-note">{{ registrationGoal === 'create'
            ? '主工作空间、根组织和管理员权限会自动准备，创建后进入系统后台。'
            : '账号创建后进入平台系统目录；已有系统的管理员可按你的账号开通成员，或由你申请访问。' }}</p>
          <a-button type="primary" size="large" html-type="submit" block :loading="loading">{{ registrationGoal === 'create' ? '创建并进入系统' : '注册并查看系统' }}</a-button>
          <p class="auth-switch">已有账号？<a @click="switchMode('login')">直接登录</a></p>
        </a-form>
      </div>
    </section>
  </main>
</template>
