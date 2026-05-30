<template>
  <AdminLayout>
    <h2>模型 · app {{ appId }}</h2>
    <div class="toolbar">
      <button type="button" @click="add">新建模型</button>
      <button type="button" @click="load">刷新</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <table v-if="rows.length" class="table">
      <thead><tr><th>ID</th><th>编码</th><th>名称</th><th>状态</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="m in rows" :key="m.id">
          <td>{{ m.id }}</td>
          <td>{{ m.modelCode }}</td>
          <td>{{ m.modelName }}</td>
          <td>{{ m.status === 2 ? '停用' : '启用' }}</td>
          <td class="actions">
            <router-link :to="`/apps/${appId}/models/${m.id}/fields`">字段</router-link>
            <button type="button" class="secondary" @click="edit(m)">编辑</button>
            <button type="button" class="danger" @click="remove(m)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">暂无模型</p>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import { deleteModels, listModelsByApp, upsertModel } from '../api/meta'
import { confirmDialog, promptText } from '../utils/dialog.js'
import { notify } from '../utils/notify.js'

const route = useRoute()
const appId = computed(() => String(route.params.appId || ''))
const rows = ref([])
const error = ref('')

async function load() {
  error.value = ''
  try {
    const r = await listModelsByApp(appId.value)
    rows.value = r.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function add() {
  await saveModel()
}

async function edit(model) {
  await saveModel(model)
}

async function saveModel(existing = null) {
  const modelCode = await promptText('模型编码', { defaultValue: existing?.modelCode || '' })
  const modelName = await promptText('模型名称', { defaultValue: existing?.modelName || '' })
  const status = await promptText('状态', { defaultValue: String(existing?.status ?? 1), message: '1=启用，2=停用' })
  if (!modelCode || !modelName) return
  error.value = ''
  try {
    await upsertModel({
      id: existing?.id ?? null,
      appId: appId.value,
      modelCode,
      modelName,
      status: String(status).trim() === '2' ? 2 : 1,
      remark: existing?.remark ?? null
    })
    notify.success('模型已保存')
    await load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function remove(model) {
  if (!model?.id || !(await confirmDialog(`删除模型 ${model.modelCode || model.id}？`, { danger: true, confirmText: '删除' }))) return
  error.value = ''
  try {
    await deleteModels([model.id])
    notify.success('模型已删除')
    await load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

onMounted(load)
</script>

<style scoped>
.actions {
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
  align-items: center;
}
</style>
