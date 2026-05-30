<template>
  <div class="admin">
    <aside class="admin__side">
      <router-link to="/systems" class="admin__brand">
        <span class="admin__brand-mark">E2</span>
        <span>
          <strong>examine2</strong>
          <small>低代码审批平台</small>
        </span>
      </router-link>
      <nav class="admin__nav">
        <p class="admin__section">平台</p>
        <router-link v-for="l in platformLinks" :key="l.to" :to="l.to" :class="{ 'is-active': isNavActive(l.to) }">{{ l.label }}</router-link>
        <p class="admin__section">应用</p>
        <router-link to="/apps" :class="{ 'is-active': isNavActive('/apps') }">应用列表</router-link>
        <template v-if="appId">
          <p class="admin__section">应用 #{{ appId }}</p>
          <router-link v-for="l in appLinks" :key="l.to" :to="l.to" :class="{ 'is-active': isNavActive(l.to) }">{{ l.label }}</router-link>
        </template>
        <p class="admin__section">流程</p>
        <router-link v-for="l in flowLinks" :key="l.to" :to="l.to" :class="{ 'is-active': isNavActive(l.to) }">{{ l.label }}</router-link>
      </nav>
    </aside>
    <div class="admin__body">
      <header class="admin__header">
        <div>
          <p class="admin__eyebrow">管理控制台</p>
          <span v-if="session" class="admin__user">
            {{ session.username }} · system {{ session.systemId }} · tenant {{ session.tenantId || 0 }}
          </span>
          <span v-else class="admin__user">未读取到会话信息</span>
        </div>
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
  const m = route.path.match(/^\/apps\/([^/]+)/)
  if (m) return m[1]
  const q = route.query.appId
  if (Array.isArray(q)) return q[0] ? String(q[0]) : ''
  return q ? String(q) : ''
})
const appLinks = computed(() => (appId.value ? appHubLinks(appId.value) : []))

function isNavActive(to) {
  const path = route.path
  if (to === '/apps') {
    return path === '/apps'
  }
  if (appId.value && to === `/apps/${appId.value}`) {
    return path === to
  }
  return path === to || path.startsWith(`${to}/`)
}

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
  background: transparent;
}
.admin__side {
  width: 244px;
  flex-shrink: 0;
  background: #202521;
  color: #edf3ef;
  padding: 1rem 0.85rem;
  border-right: 1px solid rgba(255, 255, 255, 0.08);
}
.admin__brand {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.25rem 0.35rem 1rem;
  color: #fff;
  text-decoration: none;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  margin-bottom: 0.85rem;
}
.admin__brand-mark {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: #f0b84b;
  color: #1f2933;
  font-weight: 800;
}
.admin__brand strong,
.admin__brand small {
  display: block;
  letter-spacing: 0;
}
.admin__brand small {
  margin-top: 0.1rem;
  color: #aebdb5;
  font-size: 0.72rem;
  font-weight: 500;
}
.admin__nav {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.admin__nav a {
  color: #dfe8e3;
  text-decoration: none;
  padding: 0.5rem 0.65rem;
  border-radius: 7px;
  font-size: 0.88rem;
  line-height: 1.25;
  border: 1px solid transparent;
  transition: background 0.16s ease, color 0.16s ease, border-color 0.16s ease;
}
.admin__nav a:hover {
  background: rgba(255, 255, 255, 0.08);
}
.admin__nav a.is-active {
  background: #e4f3f0;
  border-color: rgba(228, 243, 240, 0.65);
  color: #0f3f3c;
  font-weight: 700;
}
.admin__nav a.is-active::before {
  content: '';
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 0.45rem;
  border-radius: 50%;
  background: #16736f;
  vertical-align: 0.08rem;
}
.admin__nav a:not(.is-active)::before {
  content: '';
  display: inline-block;
  width: 6px;
  margin-right: 0.45rem;
}
.admin__section {
  margin: 0.9rem 0.35rem 0.3rem;
  font-size: 0.68rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #9fb0a7;
  font-weight: 700;
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
  justify-content: space-between;
  min-height: 64px;
  padding: 0.75rem 1.5rem;
  background: rgba(255, 255, 255, 0.86);
  border-bottom: 1px solid var(--color-border);
  backdrop-filter: blur(10px);
  position: sticky;
  top: 0;
  z-index: 5;
}
.admin__eyebrow {
  margin: 0;
  font-size: 0.78rem;
  color: #79867f;
  font-weight: 700;
}
.admin__user {
  font-size: 0.85rem;
  color: var(--color-muted);
}
.admin__logout {
  flex-shrink: 0;
  padding: 0.45rem 0.75rem;
  border: 1px solid var(--color-border-strong);
  border-radius: 7px;
  background: #fff;
  color: var(--color-primary);
  text-decoration: none;
  font-size: 0.85rem;
  font-weight: 700;
}
.admin__main {
  flex: 1;
  width: 100%;
  max-width: 1480px;
  padding: 1.35rem 1.5rem 2.5rem;
}

@media (max-width: 900px) {
  .admin {
    flex-direction: column;
  }
  .admin__side {
    width: 100%;
    padding: 0.8rem;
  }
  .admin__brand {
    padding-bottom: 0.75rem;
  }
  .admin__nav {
    display: grid;
    grid-auto-flow: column;
    grid-auto-columns: minmax(128px, max-content);
    overflow-x: auto;
    padding-bottom: 0.25rem;
  }
  .admin__section {
    display: none;
  }
  .admin__header {
    position: static;
    padding: 0.8rem 1rem;
  }
  .admin__main {
    padding: 1rem;
  }
}
</style>
