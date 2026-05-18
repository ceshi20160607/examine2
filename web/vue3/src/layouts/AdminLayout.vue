<template>
  <div class="admin">
    <aside class="admin__side">
      <router-link to="/systems" class="admin__brand">examine2</router-link>
      <nav class="admin__nav">
        <p class="admin__section">平台</p>
        <router-link v-for="l in platformLinks" :key="l.to" :to="l.to">{{ l.label }}</router-link>
        <p class="admin__section">应用</p>
        <router-link to="/apps">应用列表</router-link>
        <template v-if="appId">
          <p class="admin__section">应用 #{{ appId }}</p>
          <router-link v-for="l in appLinks" :key="l.to" :to="l.to">{{ l.label }}</router-link>
        </template>
        <p class="admin__section">流程</p>
        <router-link v-for="l in flowLinks" :key="l.to" :to="l.to">{{ l.label }}</router-link>
      </nav>
    </aside>
    <div class="admin__body">
      <header class="admin__header">
        <span v-if="session" class="admin__user">{{ session.username }} · sys={{ session.systemId }} · tenant={{ session.tenantId || 0 }}</span>
        <router-link v-if="session" to="/login" class="admin__logout" @click.prevent="logout">退出</router-link>
      </header>
      <main class="admin__main">
        <slot />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { setToken } from '../api/http'
import { getSession, setSession } from '../store/session'
import { appHubLinks, flowLinks, platformLinks } from '../utils/appNav'

const route = useRoute()
const router = useRouter()
const session = computed(() => getSession())
const appId = computed(() => {
  const m = route.path.match(/^\/apps\/(\d+)/)
  return m ? m[1] : ''
})
const appLinks = computed(() => (appId.value ? appHubLinks(appId.value) : []))

function logout() {
  setToken('')
  setSession(null)
  router.push('/login')
}
</script>

<script>
export default { name: 'AdminLayout' }
</script>

<style scoped>
.admin {
  display: flex;
  min-height: 100vh;
  background: #f5f6f8;
}
.admin__side {
  width: 220px;
  flex-shrink: 0;
  background: #1f2937;
  color: #e5e7eb;
  padding: 1rem 0;
}
.admin__brand {
  display: block;
  padding: 0 1rem 1rem;
  font-weight: 700;
  color: #fff;
  text-decoration: none;
  border-bottom: 1px solid #374151;
  margin-bottom: 0.75rem;
}
.admin__nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 0 0.5rem;
}
.admin__nav a {
  color: #d1d5db;
  text-decoration: none;
  padding: 0.4rem 0.6rem;
  border-radius: 6px;
  font-size: 0.88rem;
}
.admin__nav a.router-link-active {
  background: #374151;
  color: #fff;
}
.admin__section {
  margin: 0.75rem 0.35rem 0.25rem;
  font-size: 0.7rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: #9ca3af;
}
.admin__body {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.admin__header {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.6rem 1.25rem;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
}
.admin__user {
  font-size: 0.85rem;
  color: #555;
}
.admin__logout {
  margin-left: auto;
  font-size: 0.85rem;
  color: #1677ff;
}
.admin__main {
  flex: 1;
  padding: 1.25rem 1.5rem;
  max-width: 1100px;
}
</style>
