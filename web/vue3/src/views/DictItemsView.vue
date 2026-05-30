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
      <thead><tr><th>值</th><th>标签</th><th>排序</th><th>状态</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="it in rows" :key="it.id">
          <td>{{ it.itemValue }}</td>
          <td>{{ it.itemLabel }}</td>
          <td>{{ it.sortNo }}</td>
          <td>{{ it.status === 2 ? '停用' : '启用' }}</td>
          <td class="actions">
            <button type="button" class="secondary" @click="edit(it)">编辑</button>
            <button type="button" class="danger" @click="remove(it)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import { deleteDictItems, listDictItems, upsertDictItem } from '../api/module.js'
import { confirmDialog, promptText } from '../utils/dialog.js'
import { notify } from '../utils/notify.js'

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
  await saveItem()
}

async function edit(item) {
  await saveItem(item)
}

async function saveItem(existing = null) {
  const itemValue = await promptText('字典项值', { defaultValue: existing?.itemValue || '' })
  const itemLabel = await promptText('字典项标签', { defaultValue: existing?.itemLabel || '' })
  const sortNo = await promptText('排序号', { defaultValue: String(existing?.sortNo ?? rows.value.length + 1) })
  const status = await promptText('状态', { defaultValue: String(existing?.status ?? 1), message: '1=启用，2=停用' })
  if (!itemValue || !itemLabel) return
  error.value = ''
  try {
    await upsertDictItem(dictId.value, {
      id: existing?.id ?? null,
      itemValue,
      itemLabel,
      sortNo: Number.isNaN(Number(sortNo)) ? 0 : Number(sortNo),
      status: String(status).trim() === '2' ? 2 : 1
    })
    notify.success('字典项已保存')
    await load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function remove(item) {
  if (!item?.id || !(await confirmDialog(`删除字典项 ${item.itemLabel || item.itemValue}？`, { danger: true, confirmText: '删除' }))) return
  error.value = ''
  try {
    await deleteDictItems([item.id])
    notify.success('字典项已删除')
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
