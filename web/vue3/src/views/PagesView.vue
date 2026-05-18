<template>
  <AdminLayout>
    <h2>页面设计 · app {{ appId }}</h2>
    <div class="toolbar">
      <button type="button" @click="add">新建页面</button>
      <button type="button" class="secondary" @click="load">刷新</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <table v-if="rows.length" class="table">
      <thead><tr><th>ID</th><th>编码</th><th>名称</th><th>类型</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="p in rows" :key="p.id">
          <td>{{ p.id }}</td>
          <td>{{ p.pageCode }}</td>
          <td>{{ p.pageName }}</td>
          <td>{{ p.pageType }}</td>
          <td>
            <router-link :to="`/apps/${appId}/pages/${p.id}/edit`">编辑</router-link>
            ·
            <button type="button" class="secondary" @click="remove(p)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">暂无页面</p>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import { deletePages, listPagesByApp, upsertPage } from '../api/pages'

const route = useRoute()
const appId = computed(() => Number(route.params.appId))
const rows = ref([])
const error = ref('')

async function load() {
  error.value = ''
  try {
    const r = await listPagesByApp(appId.value)
    rows.value = r.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function add() {
  const pageCode = prompt('pageCode')
  const pageName = prompt('pageName')
  const pageType = prompt('pageType (list/form/detail)', 'list')
  if (!pageCode || !pageName || !pageType) return
  error.value = ''
  try {
    await upsertPage({ appId: appId.value, pageCode, pageName, pageType })
    load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function remove(p) {
  if (!confirm(`删除页面 #${p.id}?`)) return
  error.value = ''
  try {
    await deletePages([p.id])
    load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

onMounted(load)
</script>

<style src="./admin-shared.css"></style>
