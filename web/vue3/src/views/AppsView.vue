<template>
  <AdminLayout>
    <h2>应用</h2>
    <p class="muted">选择应用进入管理控制台。</p>
    <p v-if="error" class="error">{{ error }}</p>
    <ul v-if="apps.length" class="list">
      <li v-for="a in apps" :key="a.id">
        <router-link :to="`/apps/${a.id}`" class="list__link">
          <strong>{{ a.appName || a.appCode }}</strong>
          <span class="muted">{{ a.appCode }}</span>
        </router-link>
      </li>
    </ul>
    <p v-else-if="!loading" class="muted">暂无应用</p>
  </AdminLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import AdminLayout from '../layouts/AdminLayout.vue'
import { listApps } from '../api/meta'

const apps = ref([])
const loading = ref(false)
const error = ref('')

onMounted(async () => {
  loading.value = true
  try {
    const r = await listApps()
    apps.value = r.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  } finally {
    loading.value = false
  }
})
</script>

<style src="./admin-shared.css"></style>
<style scoped>
.list {
  list-style: none;
  padding: 0;
}
.list__link {
  display: block;
  padding: 0.75rem 1rem;
  margin-bottom: 0.5rem;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  text-decoration: none;
  color: inherit;
  background: #fff;
}
.list__link:hover {
  border-color: #1677ff;
}
.list__link .muted {
  display: block;
  margin-top: 0.25rem;
}
</style>
