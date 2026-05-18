<template>
  <div class="layout">
    <header class="layout__header">
      <router-link to="/systems" class="layout__brand">examine2 管理台</router-link>
      <nav class="layout__nav">
        <router-link to="/systems">系统</router-link>
        <router-link to="/apps">应用</router-link>
      </nav>
      <span v-if="session" class="layout__user">{{ session.username }} · sys={{ session.systemId }}</span>
    </header>
    <main class="layout__main">
      <slot />
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { getSession } from '../store/session'

const session = computed(() => getSession())
</script>

<script>
export default { name: 'AppLayout' }
</script>

<style scoped>
.layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.layout__header {
  display: flex;
  align-items: center;
  gap: 1.5rem;
  padding: 0.75rem 1.25rem;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
}
.layout__brand {
  font-weight: 600;
  color: #111;
  text-decoration: none;
}
.layout__nav {
  display: flex;
  gap: 1rem;
}
.layout__nav a {
  color: #555;
  text-decoration: none;
}
.layout__nav a.router-link-active {
  color: #1677ff;
  font-weight: 500;
}
.layout__user {
  margin-left: auto;
  font-size: 0.85rem;
  color: #666;
}
.layout__main {
  flex: 1;
  padding: 1.25rem;
  max-width: 960px;
  margin: 0 auto;
  width: 100%;
}
</style>
