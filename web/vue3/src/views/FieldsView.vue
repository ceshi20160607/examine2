<template>
  <AdminLayout>
    <h2>字段 · model {{ modelId }}</h2>
    <div class="toolbar">
      <button type="button" @click="add">新建字段</button>
      <button type="button" class="secondary" @click="load">刷新</button>
      <router-link class="btn secondary" :to="`/apps/${appId}`">返回应用</router-link>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <table v-if="rows.length" class="table">
      <thead><tr><th>ID</th><th>编码</th><th>名称</th><th>类型</th><th></th></tr></thead>
      <tbody>
        <tr v-for="f in rows" :key="f.id">
          <td>{{ f.id }}</td>
          <td>{{ f.fieldCode }}</td>
          <td>{{ f.fieldName }}</td>
          <td>{{ f.fieldType }}</td>
          <td><button type="button" class="secondary" @click="del(f.id)">删</button></td>
        </tr>
      </tbody>
    </table>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import { deleteFields, listFieldsByModel, upsertField } from '../api/meta'

const route = useRoute()
const appId = computed(() => String(route.params.appId || ''))
const modelId = computed(() => String(route.params.modelId || ''))
const rows = ref([])
const error = ref('')

async function load() {
  error.value = ''
  try {
    const r = await listFieldsByModel(modelId.value)
    rows.value = r.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function add() {
  const fieldCode = prompt('fieldCode')
  const fieldName = prompt('fieldName')
  const fieldType = prompt('fieldType (TEXT/NUMBER/...)', 'TEXT')
  if (!fieldCode || !fieldName || !fieldType) return
  await upsertField({ appId: appId.value, modelId: modelId.value, fieldCode, fieldName, fieldType })
  load()
}

async function del(id) {
  if (!confirm('删除字段?')) return
  await deleteFields([id])
  load()
}

onMounted(load)
</script>

<style src="./admin-shared.css"></style>
