<script setup lang="ts">
import { BellOutlined, CheckSquareOutlined, LogoutOutlined, UserOutlined } from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import ProductMark from './ProductMark.vue'
import { api } from '../api'
import { clearSession, platformContext, platformTokens } from '../session'

const router = useRouter()
async function logout() {
  try {
    if (platformTokens.value?.accessToken) {
      await api('/api/auth/logout', { method: 'POST' }, platformTokens.value.accessToken)
    }
  } finally {
    clearSession()
    await router.replace('/login')
  }
}
</script>

<template>
  <div class="platform-shell">
    <header class="topbar">
      <ProductMark />
      <div class="topbar__actions">
        <a-button type="text" class="topbar-action"><CheckSquareOutlined /><span>待办</span></a-button>
        <a-button type="text" class="topbar-action"><BellOutlined /><span>消息</span></a-button>
        <a-dropdown>
          <a-button type="text" class="user-trigger"><a-avatar size="small"><UserOutlined /></a-avatar>{{ platformContext?.displayName || platformContext?.username }}</a-button>
          <template #overlay><a-menu><a-menu-item @click="logout"><LogoutOutlined /> 退出登录</a-menu-item></a-menu></template>
        </a-dropdown>
      </div>
    </header>
    <div class="platform-frame">
      <aside class="side-nav">
        <nav>
          <a class="side-nav__item side-nav__item--active"><span class="side-nav__icon">系</span><span>我的系统</span></a>
          <a class="side-nav__item side-nav__item--disabled"><span class="side-nav__icon">流</span><span>Flow</span><small>后续</small></a>
          <a class="side-nav__item side-nav__item--disabled"><span class="side-nav__icon">应</span><span>对外应用</span><small>后续</small></a>
          <a class="side-nav__item side-nav__item--disabled"><span class="side-nav__icon">志</span><span>日志</span><small>后续</small></a>
        </nav>
        <div class="side-nav__scope">
          <strong>平台工作区</strong>
          <span>进入系统后切换到系统权限和数据范围</span>
        </div>
      </aside>
      <main class="workspace"><slot /></main>
    </div>
  </div>
</template>
