<template>
  <AdminLayout>
    <h2>流程模板</h2>
    <div class="toolbar">
      <button type="button" @click="load">刷新</button>
      <button type="button" @click="add">新建模板</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <table v-if="rows.length" class="table">
      <thead><tr><th>ID</th><th>编码</th><th>名称</th><th>状态</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="t in rows" :key="t.id">
          <td>{{ t.id }}</td>
          <td>{{ t.tempCode }}</td>
          <td>{{ t.tempName }}</td>
          <td>{{ t.status === 2 ? '停用' : '启用' }}</td>
          <td class="actions">
            <router-link :to="`/flow/temps/${t.id}`">版本</router-link>
            <button type="button" class="secondary" @click="edit(t)">编辑</button>
            <button type="button" class="danger" @click="remove(t)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">暂无模板</p>
  </AdminLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import AdminLayout from '../layouts/AdminLayout.vue'
import { deleteTemps, pageTemps, upsertTemp } from '../api/flow'
import { confirmDialog, promptText } from '../utils/dialog.js'
import { notify } from '../utils/notify.js'

const rows = ref([])
const error = ref('')

async function load() {
  error.value = ''
  try {
    const r = await pageTemps(1, 50)
    rows.value = r.data?.list || r.data?.records || r.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function add() {
  await saveTemp()
}

async function edit(temp) {
  await saveTemp(temp)
}

async function saveTemp(existing = null) {
  const tempCode = await promptText('模板编码', { defaultValue: existing?.tempCode || '' })
  const tempName = await promptText('模板名称', { defaultValue: existing?.tempName || '' })
  const status = await promptText('状态', { defaultValue: String(existing?.status ?? 1), message: '1=启用，2=停用' })
  if (!tempCode || !tempName) return
  error.value = ''
  try {
    await upsertTemp({
      id: existing?.id ?? null,
      tempCode,
      tempName,
      status: String(status).trim() === '2' ? 2 : 1
    })
    notify.success('流程模板已保存')
    await load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function remove(temp) {
  if (!temp?.id || !(await confirmDialog(`删除流程模板 ${temp.tempCode || temp.id}？`, { danger: true, confirmText: '删除' }))) return
  error.value = ''
  try {
    await deleteTemps([temp.id])
    notify.success('流程模板已删除')
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
