<template>
  <AdminLayout>
    <h2>字典 · app {{ appId }}</h2>
    <div class="toolbar">
      <button type="button" @click="addDict">新建字典</button>
      <button type="button" class="secondary" @click="load">刷新</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <table v-if="dicts.length" class="table">
      <thead><tr><th>ID</th><th>编码</th><th>名称</th><th>状态</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="d in dicts" :key="d.id">
          <td>{{ d.id }}</td>
          <td>{{ d.dictCode }}</td>
          <td>{{ d.dictName }}</td>
          <td>{{ d.status === 2 ? '停用' : '启用' }}</td>
          <td class="actions">
            <button type="button" @click="editItems(d)">管理项</button>
            <button type="button" class="secondary" @click="editDict(d)">编辑</button>
            <button type="button" class="danger" @click="removeDict(d)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">暂无字典</p>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import { deleteDicts, listDictsByApp, upsertDict } from '../api/module'
import { confirmDialog, promptText } from '../utils/dialog.js'
import { notify } from '../utils/notify.js'

const route = useRoute()
const router = useRouter()
const appId = computed(() => String(route.params.appId || ''))
const dicts = ref([])
const error = ref('')

async function load() {
  error.value = ''
  try {
    const r = await listDictsByApp(appId.value)
    dicts.value = r.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function addDict() {
  await saveDict()
}

async function editDict(d) {
  await saveDict(d)
}

async function saveDict(existing = null) {
  const dictCode = await promptText('字典编码', { defaultValue: existing?.dictCode || '' })
  const dictName = await promptText('字典名称', { defaultValue: existing?.dictName || '' })
  const status = await promptText('状态', { defaultValue: String(existing?.status ?? 1), message: '1=启用，2=停用' })
  if (!dictCode || !dictName) return
  error.value = ''
  try {
    await upsertDict(appId.value, {
      id: existing?.id ?? null,
      dictCode,
      dictName,
      status: String(status).trim() === '2' ? 2 : 1,
      remark: existing?.remark ?? null
    })
    notify.success('字典已保存')
    await load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function removeDict(d) {
  if (!d?.id || !(await confirmDialog(`删除字典 ${d.dictCode || d.id}？`, { danger: true, confirmText: '删除' }))) return
  error.value = ''
  try {
    await deleteDicts([d.id])
    notify.success('字典已删除')
    await load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

function editItems(d) {
  router.push({
    path: `/apps/${appId.value}/dicts/${d.id}/items`,
    query: { code: d.dictCode || '' }
  })
}

onMounted(load)
</script>

<style scoped>
.actions {
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
}
</style>
