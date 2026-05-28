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
  background: transparent;
}
.layout__header {
  display: flex;
  align-items: center;
  gap: 1.5rem;
  min-height: 64px;
  padding: 0.85rem 1.5rem;
  border-bottom: 1px solid var(--color-border);
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(10px);
}
.layout__brand {
  font-weight: 800;
  color: var(--color-text);
  text-decoration: none;
}
.layout__nav {
  display: flex;
  gap: 1rem;
}
.layout__nav a {
  color: var(--color-muted);
  text-decoration: none;
  font-size: 0.92rem;
  font-weight: 600;
}
.layout__nav a.router-link-active {
  color: var(--color-primary);
}
.layout__user {
  margin-left: auto;
  font-size: 0.85rem;
  color: var(--color-muted);
}
.layout__main {
  flex: 1;
  padding: 1.5rem;
  max-width: 1080px;
  margin: 0 auto;
  width: 100%;
}

@media (max-width: 720px) {
  .layout__header {
    gap: 0.9rem;
    padding: 0.8rem 1rem;
    flex-wrap: wrap;
  }
  .layout__user {
    width: 100%;
    margin-left: 0;
  }
  .layout__main {
    padding: 1rem;
  }
}
</style>
