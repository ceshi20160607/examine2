<template>
  <AdminLayout>
    <h2>字典 · app {{ appId }}</h2>
    <div class="toolbar">
      <button type="button" @click="addDict">新建字典</button>
      <button type="button" class="secondary" @click="load">刷新</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <table v-if="dicts.length" class="table">
      <thead><tr><th>ID</th><th>编码</th><th>名称</th><th>项</th></tr></thead>
      <tbody>
        <tr v-for="d in dicts" :key="d.id">
          <td>{{ d.id }}</td>
          <td>{{ d.dictCode }}</td>
          <td>{{ d.dictName }}</td>
          <td><button type="button" @click="editItems(d)">管理项</button></td>
        </tr>
      </tbody>
    </table>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import { listDictsByApp, upsertDict } from '../api/module'

const route = useRoute()
const router = useRouter()
const appId = computed(() => Number(route.params.appId))
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
  const dictCode = prompt('dictCode')
  const dictName = prompt('dictName')
  if (!dictCode || !dictName) return
  await upsertDict(appId.value, { dictCode, dictName })
  load()
}

function editItems(d) {
  router.push({
    path: `/apps/${appId.value}/dicts/${d.id}/items`,
    query: { code: d.dictCode || '' }
  })
}

onMounted(load)
</script>

<style src="./admin-shared.css"></style>
