<template>
  <AdminLayout>
    <h2>流程绑定 · app {{ appId }}</h2>
    <div class="toolbar">
      <select v-if="models.length" v-model="modelIdText" @change="load">
        <option value="">选择模型</option>
        <option v-for="m in models" :key="m.id" :value="String(m.id)">
          {{ m.modelName || m.modelCode }} (#{{ m.id }})
        </option>
      </select>
      <input v-else v-model="modelIdText" placeholder="modelId" />
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
          <td>{{ b.status === 1 ? '启用' : '停用' }}</td>
          <td class="actions">
            <button type="button" class="secondary" @click="edit(b)">编辑</button>
            <button type="button" class="secondary" @click="remove(b)">删除</button>
          </td>
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
import { listModelsByApp } from '../api/meta.js'
import {
  deleteModelFlowBinding,
  listFlowTempOptions,
  listModelFlowBindings,
  upsertModelFlowBinding
} from '../api/flowBinding'
import { confirmDialog, promptText } from '../utils/dialog.js'
import { notify } from '../utils/notify.js'

const route = useRoute()
const appId = computed(() => String(route.params.appId || ''))
const modelIdText = ref(String(route.query.modelId || ''))
const models = ref([])
const rows = ref([])
const tempOptions = ref([])
const error = ref('')

function tempLabel(id) {
  const sid = String(id ?? '')
  const t = tempOptions.value.find((x) => String(x.value ?? x.id) === sid)
  return t ? `${t.text || t.label || t.tempName || t.tempCode} (#${t.value ?? t.id})` : id
}

function tempOptionMessage() {
  if (!tempOptions.value.length) return '请先在流程模板页面创建并启用模板'
  return tempOptions.value
    .map((t, i) => `${i + 1}. ${t.text || t.label || t.tempName || t.tempCode} (#${t.value ?? t.id})`)
    .join('\n')
}

function statusText(value) {
  const s = String(value ?? '').trim()
  return s === '2' || s === '停用' ? 2 : 1
}

async function loadModels() {
  try {
    const r = await listModelsByApp(appId.value)
    models.value = r.data || []
    if (!modelIdText.value && models.value[0]?.id) {
      modelIdText.value = String(models.value[0].id)
    }
  } catch {
    models.value = []
  }
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
  const modelId = modelIdText.value.trim()
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
  await saveBinding()
}

async function edit(b) {
  await saveBinding(b)
}

async function saveBinding(existing = null) {
  const modelId = modelIdText.value.trim()
  if (!tempOptions.value.length) await loadTemps()
  const triggerAction = await promptText('触发动作', {
    defaultValue: existing?.triggerAction || 'create',
    message: '建议使用 create / update，与记录保存触发动作保持一致'
  })
  const existingIndex = tempOptions.value.findIndex((t) => String(t.value ?? t.id) === String(existing?.tempId ?? ''))
  const pickedIndex = await promptText('流程模板序号', {
    defaultValue: existingIndex >= 0 ? String(existingIndex + 1) : '1',
    message: tempOptionMessage()
  })
  const picked = tempOptions.value[Number(pickedIndex) - 1]
  const tempId = picked?.value ?? picked?.id ?? existing?.tempId
  const status = await promptText('状态', {
    defaultValue: String(existing?.status ?? 1),
    message: '1=启用，2=停用'
  })
  if (!modelId || !triggerAction || !tempId) return
  error.value = ''
  try {
    await upsertModelFlowBinding({
      id: existing?.id ?? null,
      appId: appId.value,
      modelId,
      triggerAction,
      tempId,
      status: statusText(status)
    })
    notify.success('流程绑定已保存')
    await load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function remove(b) {
  if (!(await confirmDialog(`删除绑定 #${b.id}?`, { danger: true, confirmText: '删除' }))) return
  error.value = ''
  try {
    await deleteModelFlowBinding(b.id)
    notify.success('流程绑定已删除')
    await load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

onMounted(async () => {
  await Promise.all([loadTemps(), loadModels()])
  if (modelIdText.value) await load()
})
</script>

<style scoped>
input,
select {
  padding: 0.35rem 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  min-width: 180px;
}
.actions {
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
}
</style>
