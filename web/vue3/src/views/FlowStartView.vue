<template>
  <AdminLayout>
    <h2>发起流程</h2>
    <div class="toolbar">
      <button type="button" @click="load">刷新模板</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <table v-if="temps.length" class="table">
      <thead><tr><th>编码</th><th>名称</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="t in temps" :key="t.id || t.tempCode">
          <td>{{ t.tempCode }}</td>
          <td>{{ t.tempName }}</td>
          <td><button type="button" @click="start(t)">发起</button></td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">暂无模板</p>
    <pre v-if="resultText" class="pre">{{ resultText }}</pre>
  </AdminLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import AdminLayout from '../layouts/AdminLayout.vue'
import { pageTemps, startInstance } from '../api/flow'

const temps = ref([])
const error = ref('')
const resultText = ref('')

async function load() {
  error.value = ''
  try {
    const r = await pageTemps(1, 50)
    temps.value = r.data?.list || r.data?.records || r.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function start(t) {
  const title = prompt('流程标题', t.tempName || '')
  if (!title) return
  error.value = ''
  try {
    const r = await startInstance({
      defCode: t.tempCode,
      title: title.trim(),
      bizType: 'ui_flow',
      bizId: `ui-${Date.now()}`
    })
    resultText.value = JSON.stringify(r.data || null, null, 2)
    alert('已发起')
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

onMounted(load)
</script>

<style src="./admin-shared.css"></style>
<style scoped>
.pre { margin-top: 1rem; background: #fff; border: 1px solid #e5e7eb; padding: 0.75rem; border-radius: 6px; font-size: 0.85rem; }
</style>
