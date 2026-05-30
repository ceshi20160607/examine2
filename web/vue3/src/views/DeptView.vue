<template>
  <AdminLayout>
    <h2>部门 · app {{ appId }}</h2>
    <div class="toolbar">
      <button type="button" @click="add">新建部门</button>
      <button type="button" class="secondary" @click="load">刷新</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <table v-if="rows.length" class="table">
      <thead><tr><th>ID</th><th>编码</th><th>名称</th><th>父级</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="d in rows" :key="d.id">
          <td>{{ d.id }}</td>
          <td>{{ d.deptCode }}</td>
          <td>{{ d.deptName }}</td>
          <td>{{ deptLabel(d.parentId) }}</td>
          <td class="actions">
            <button type="button" class="secondary" @click="edit(d)">编辑</button>
            <button type="button" class="secondary" @click="remove(d)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">暂无部门</p>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import { deleteDepts, listDepts, upsertDept } from '../api/dept'
import { idToString } from '../utils/id'
import { confirmDialog, promptText } from '../utils/dialog.js'
import { notify } from '../utils/notify.js'

const route = useRoute()
const appId = computed(() => String(route.params.appId || ''))
const rows = ref([])
const error = ref('')

async function load() {
  error.value = ''
  try {
    const r = await listDepts(appId.value)
    rows.value = r.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

function deptLabel(id) {
  const sid = String(id || '0')
  if (sid === '0') return '根部门'
  const d = rows.value.find((x) => String(x.id) === sid)
  return d ? `${d.deptName || d.deptCode} (#${sid})` : `#${sid}`
}

function deptOptionMessage(currentId = null) {
  const opts = ['0 - 根部门']
  for (const d of rows.value) {
    if (currentId && String(d.id) === String(currentId)) continue
    opts.push(`${d.id} - ${d.deptName || d.deptCode}`)
  }
  return opts.join('\n')
}

async function add() {
  await saveDept()
}

async function edit(d) {
  await saveDept(d)
}

async function saveDept(existing = null) {
  const deptCode = await promptText('部门编码', { defaultValue: existing?.deptCode || '' })
  const deptName = await promptText('部门名称', { defaultValue: existing?.deptName || '' })
  const parentId = idToString(await promptText('上级部门 ID', {
    defaultValue: String(existing?.parentId || '0'),
    message: deptOptionMessage(existing?.id)
  })) || '0'
  const sortNo = await promptText('排序号', { defaultValue: String(existing?.sortNo ?? rows.value.length + 1) })
  if (!deptCode || !deptName) return
  error.value = ''
  try {
    await upsertDept(appId.value, {
      id: existing?.id ?? null,
      deptCode,
      deptName,
      parentId,
      sortNo: Number.isNaN(Number(sortNo)) ? 0 : Number(sortNo),
      status: existing?.status ?? 1,
      remark: existing?.remark ?? null
    })
    notify.success('部门已保存')
    await load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function remove(d) {
  if (!(await confirmDialog(`删除部门 #${d.id}?`, { danger: true, confirmText: '删除' }))) return
  error.value = ''
  try {
    await deleteDepts([d.id])
    notify.success('部门已删除')
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
}
</style>
