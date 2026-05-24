<template>
  <AdminLayout>
    <h2>字典项 · {{ dictCode }}</h2>
    <div class="toolbar">
      <button type="button" @click="add">新增项</button>
      <button type="button" class="secondary" @click="load">刷新</button>
      <router-link class="btn secondary" :to="`/apps/${appId}/dicts`">返回</router-link>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <table v-if="rows.length" class="table">
      <thead><tr><th>值</th><th>标签</th><th>排序</th></tr></thead>
      <tbody>
        <tr v-for="it in rows" :key="it.id">
          <td>{{ it.itemValue }}</td>
          <td>{{ it.itemLabel }}</td>
          <td>{{ it.sortNo }}</td>
        </tr>
      </tbody>
    </table>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import { listDictItems, upsertDictItem } from '../api/module.js'

const route = useRoute()
const appId = computed(() => String(route.params.appId || ''))
const dictId = computed(() => String(route.params.dictId || ''))
const dictCode = computed(() => route.query.code || '')
const rows = ref([])
const error = ref('')

async function load() {
  error.value = ''
  try {
    const r = await listDictItems(dictId.value)
    rows.value = r.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function add() {
  const itemValue = prompt('itemValue')
  const itemLabel = prompt('itemLabel')
  if (!itemValue || !itemLabel) return
  await upsertDictItem(dictId.value, { itemValue, itemLabel })
  load()
}

onMounted(load)
</script>

<style src="./admin-shared.css"></style>
