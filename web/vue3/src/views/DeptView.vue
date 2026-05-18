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
          <td>{{ d.parentId || 0 }}</td>
          <td><button type="button" class="secondary" @click="remove(d)">删除</button></td>
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

const route = useRoute()
const appId = computed(() => Number(route.params.appId))
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

async function add() {
  const deptCode = prompt('deptCode')
  const deptName = prompt('deptName')
  const parentId = Number(prompt('parentId (0=根)', '0') || '0')
  if (!deptCode || !deptName) return
  error.value = ''
  try {
    await upsertDept(appId.value, { deptCode, deptName, parentId: parentId || 0 })
    load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function remove(d) {
  if (!confirm(`删除部门 #${d.id}?`)) return
  error.value = ''
  try {
    await deleteDepts([d.id])
    load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

onMounted(load)
</script>

<style src="./admin-shared.css"></style>
