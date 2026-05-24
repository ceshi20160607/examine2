<template>
  <AdminLayout>
    <h2>模型关系 · app {{ appId }}</h2>
    <div class="toolbar">
      <button type="button" @click="add">新建关系</button>
      <button type="button" class="secondary" @click="load">刷新</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <table v-if="rows.length" class="table">
      <thead><tr><th>关系ID</th><th>源模型</th><th>目标模型</th><th>类型</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="r in rows" :key="r.id">
          <td>{{ r.id }}</td>
          <td>{{ modelLabel(r.srcModelId) }}</td>
          <td>{{ modelLabel(r.dstModelId) }}</td>
          <td>{{ r.relType }}</td>
          <td><button type="button" class="secondary" @click="remove(r)">删除</button></td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">暂无关系</p>
    <p class="muted hint">子表/关联字段的 <code>config_json.relationId</code> 可填上表「关系ID」；n-n 需在关系 <code>config_json</code> 中配置 linkModelId、srcFkField、dstFkField。</p>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import { deleteRelations, listModelsByApp, listRelationsByApp, upsertRelation } from '../api/meta'

const route = useRoute()
const appId = computed(() => String(route.params.appId || ''))
const rows = ref([])
const models = ref([])
const error = ref('')

function modelLabel(id) {
  const m = models.value.find((x) => x.id === id)
  return m ? `${m.modelName || m.modelCode} (#${id})` : id
}

async function load() {
  error.value = ''
  try {
    const [rr, mr] = await Promise.all([
      listRelationsByApp(appId.value),
      listModelsByApp(appId.value)
    ])
    rows.value = rr.data || []
    models.value = mr.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function add() {
  if (!models.value.length) {
    error.value = '请先创建模型'
    return
  }
  const opts = models.value.map((m, i) => `${i + 1}. ${m.modelCode} (#${m.id})`).join('\n')
  const srcIdx = Number(prompt(`源模型序号:\n${opts}`)) - 1
  const dstIdx = Number(prompt(`目标模型序号:\n${opts}`)) - 1
  const relType = prompt('relType (1-1 / 1-n / n-n)', '1-n')
  if (srcIdx < 0 || dstIdx < 0 || !relType) return
  const srcModelId = models.value[srcIdx]?.id
  const dstModelId = models.value[dstIdx]?.id
  if (!srcModelId || !dstModelId) return
  let configJson = null
  if (relType === 'n-n') {
    configJson = prompt(
      'n-n config_json',
      '{"linkModelId":0,"srcFkField":"srcId","dstFkField":"dstId"}'
    )
  } else {
    const fk = prompt('fkField（子表外键字段编码）', 'parentId')
    if (fk) configJson = JSON.stringify({ fkField: fk })
  }
  error.value = ''
  try {
    await upsertRelation({ appId: appId.value, srcModelId, dstModelId, relType, configJson })
    load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function remove(r) {
  if (!confirm(`删除关系 #${r.id}?`)) return
  error.value = ''
  try {
    await deleteRelations([r.id])
    load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

onMounted(load)
</script>

<style src="./admin-shared.css"></style>
