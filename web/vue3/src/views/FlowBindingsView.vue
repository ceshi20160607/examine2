<template>
  <AdminLayout>
    <h2>流程绑定 · app {{ appId }}</h2>
    <div class="toolbar">
      <input v-model="modelIdText" placeholder="modelId" />
      <button type="button" @click="load">加载绑定</button>
      <button type="button" @click="add">新建绑定</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <table v-if="rows.length" class="table">
      <thead><tr><th>ID</th><th>触发</th><th>模板ID</th><th>状态</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="b in rows" :key="b.id">
          <td>{{ b.id }}</td>
          <td>{{ b.triggerAction }}</td>
          <td>{{ tempLabel(b.tempId) }}</td>
          <td>{{ b.status }}</td>
          <td><button type="button" class="secondary" @click="remove(b)">删除</button></td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">选择 modelId 后加载</p>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import {
  deleteModelFlowBinding,
  listFlowTempOptions,
  listModelFlowBindings,
  upsertModelFlowBinding
} from '../api/flowBinding'

const route = useRoute()
const appId = computed(() => Number(route.params.appId))
const modelIdText = ref(String(route.query.modelId || ''))
const rows = ref([])
const tempOptions = ref([])
const error = ref('')

function tempLabel(id) {
  const t = tempOptions.value.find((x) => x.value === id || x.id === id)
  return t ? (t.text || t.label || t.tempName) : id
}

async function loadTemps() {
  try {
    const r = await listFlowTempOptions()
    tempOptions.value = r.data || []
  } catch {
    tempOptions.value = []
  }
}

async function load() {
  const modelId = Number(modelIdText.value)
  if (!modelId) {
    error.value = '请输入 modelId'
    return
  }
  error.value = ''
  try {
    const r = await listModelFlowBindings(appId.value, modelId)
    rows.value = r.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function add() {
  const modelId = Number(modelIdText.value)
  const triggerAction = prompt('triggerAction (如 on_create)', 'on_create')
  const opts = tempOptions.value.map((t, i) => `${i + 1}. ${t.text || t.label || t.tempCode} (#${t.value ?? t.id})`).join('\n')
  const idx = Number(prompt(`流程模板序号:\n${opts}`)) - 1
  const picked = tempOptions.value[idx]
  const tempId = picked?.value ?? picked?.id
  if (!modelId || !triggerAction || !tempId) return
  error.value = ''
  try {
    await upsertModelFlowBinding({ appId: appId.value, modelId, triggerAction, tempId })
    load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function remove(b) {
  if (!confirm(`删除绑定 #${b.id}?`)) return
  error.value = ''
  try {
    await deleteModelFlowBinding(b.id)
    load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

onMounted(loadTemps)
</script>

<style src="./admin-shared.css"></style>
<style scoped>
input { padding: 0.35rem 0.5rem; border: 1px solid #d1d5db; border-radius: 6px; width: 120px; }
</style>
