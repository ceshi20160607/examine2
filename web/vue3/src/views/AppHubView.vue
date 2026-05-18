<template>
  <AdminLayout>
    <h2>应用 #{{ appId }}</h2>
    <p class="muted">配置与运行时入口</p>
    <div class="hub">
      <router-link v-for="l in links" :key="l.to" :to="l.to" class="hub__card">{{ l.label }}</router-link>
    </div>
  </AdminLayout>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import { appHubLinks } from '../utils/appNav'

const route = useRoute()
const appId = computed(() => route.params.appId)
const links = computed(() => appHubLinks(appId.value).filter((l) => l.to !== `/apps/${appId.value}`))
</script>

<style scoped>
.hub {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 0.75rem;
  margin-top: 1rem;
}
.hub__card {
  padding: 1rem;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  text-decoration: none;
  color: #111;
  font-weight: 500;
}
.hub__card:hover {
  border-color: #1677ff;
}
.muted {
  color: #666;
}
</style>
