<template>
  <AdminLayout>
    <h2>流程实例 #{{ instanceId }}</h2>
    <div class="toolbar">
      <button type="button" @click="load">刷新</button>
      <router-link class="btn secondary" to="/flow/instances">返回</router-link>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <pre v-if="detail" class="json-pre">{{ JSON.stringify(detail, null, 2) }}</pre>
    <h3 v-if="tasks.length">待办/任务</h3>
    <ul v-if="tasks.length">
      <li v-for="t in tasks" :key="t.id">
        #{{ t.id }} {{ t.nodeName }}
        <router-link :to="`/flow/task?instanceId=${instanceId}&taskId=${t.id}`">办理</router-link>
      </li>
    </ul>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import { getInstance, listInstanceTasks, listInstanceTraces } from '../api/flow.js'

const route = useRoute()
const instanceId = computed(() => String(route.params.instanceId || ''))
const detail = ref(null)
const tasks = ref([])
const error = ref('')

async function load() {
  error.value = ''
  try {
    const [ir, tr] = await Promise.all([
      getInstance(instanceId.value),
      listInstanceTasks(instanceId.value)
    ])
    detail.value = ir.data
    tasks.value = tr.data || []
    await listInstanceTraces(instanceId.value)
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

onMounted(load)
</script>

<style scoped>
.json-pre {
  background: #f9fafb;
  padding: 0.75rem;
  border-radius: 6px;
  font-size: 0.8rem;
  overflow: auto;
}
</style>
<style src="./admin-shared.css"></style>
