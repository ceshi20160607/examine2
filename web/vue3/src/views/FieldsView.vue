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
      <thead><tr><th>ID</th><th>编码</th><th>名称</th><th>类型</th><th>必填</th><th>排序</th><th></th></tr></thead>
      <tbody>
        <tr v-for="f in rows" :key="f.id">
          <td>{{ f.id }}</td>
          <td>{{ f.fieldCode }}</td>
          <td>{{ f.fieldName }}</td>
          <td>{{ typeLabel(f.fieldType) }}</td>
          <td>{{ f.requiredFlag === 1 ? '是' : '否' }}</td>
          <td>{{ f.sortNo ?? 0 }}</td>
          <td class="actions">
            <button type="button" class="secondary" @click="edit(f)">编辑</button>
            <button type="button" class="secondary" @click="del(f.id)">删</button>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">暂无字段</p>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import { deleteFields, listFieldTypeDefinitions, listFieldsByModel, upsertField } from '../api/meta'
import { confirmDialog, promptText } from '../utils/dialog.js'
import { notify } from '../utils/notify.js'

const route = useRoute()
const appId = computed(() => String(route.params.appId || ''))
const modelId = computed(() => String(route.params.modelId || ''))
const rows = ref([])
const fieldTypes = ref([])
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

async function loadFieldTypes() {
  try {
    const r = await listFieldTypeDefinitions()
    fieldTypes.value = r.data || []
  } catch {
    fieldTypes.value = []
  }
}

function typeLabel(code) {
  const t = fieldTypes.value.find((x) => x.code === code)
  return t ? `${t.label || t.code} (${t.code})` : code
}

function typeOptionMessage() {
  if (!fieldTypes.value.length) return '例如 TEXT / NUMBER / DATE / SELECT'
  return fieldTypes.value
    .map((t) => `${t.code} - ${t.label || t.code}`)
    .join('\n')
}

function flagValue(v) {
  const s = String(v ?? '').trim()
  return s === '1' || s === '是' || s.toLowerCase() === 'true' ? 1 : 0
}

async function collectFieldPayload(existing = null) {
  const fieldCode = await promptText('字段编码', { defaultValue: existing?.fieldCode || '' })
  const fieldName = await promptText('字段名称', { defaultValue: existing?.fieldName || '' })
  const fieldType = await promptText('字段类型', {
    defaultValue: existing?.fieldType || 'TEXT',
    message: typeOptionMessage()
  })
  if (!fieldCode || !fieldName || !fieldType) return null

  const typeDef = fieldTypes.value.find((x) => x.code === String(fieldType).trim().toUpperCase())
  const required = await promptText('是否必填', {
    defaultValue: String(existing?.requiredFlag ?? 0),
    message: '1=必填，0=可空'
  })
  const sortNo = await promptText('排序号', { defaultValue: String(existing?.sortNo ?? rows.value.length + 1) })
  const payload = {
    id: existing?.id ?? null,
    appId: appId.value,
    modelId: modelId.value,
    fieldCode: String(fieldCode).trim(),
    fieldName: String(fieldName).trim(),
    fieldType: String(fieldType).trim().toUpperCase(),
    requiredFlag: flagValue(required),
    uniqueFlag: existing?.uniqueFlag ?? 0,
    hiddenFlag: existing?.hiddenFlag ?? 0,
    tips: existing?.tips ?? null,
    dictCode: existing?.dictCode ?? null,
    refModelId: existing?.refModelId ?? null,
    refDisplayField: existing?.refDisplayField ?? null,
    relationModuleLabel: existing?.relationModuleLabel ?? null,
    configJson: existing?.configJson ?? null,
    multiFlag: existing?.multiFlag ?? 0,
    defaultValue: existing?.defaultValue ?? null,
    sortNo: Number.isNaN(Number(sortNo)) ? 0 : Number(sortNo),
    status: existing?.status ?? 1
  }

  if (typeDef?.needsDict) {
    payload.dictCode = await promptText('字典编码', {
      defaultValue: existing?.dictCode || '',
      message: '选择/多选字段需要绑定 un_module_dict.dict_code'
    })
  }
  if (typeDef?.needsRef) {
    const refModelId = await promptText('关联模型 ID', { defaultValue: existing?.refModelId ? String(existing.refModelId) : '' })
    if (!refModelId) return null
    payload.refModelId = refModelId
    payload.refDisplayField = await promptText('关联显示字段编码', { defaultValue: existing?.refDisplayField || '' })
    payload.relationModuleLabel = await promptText('关联模块显示名', { defaultValue: existing?.relationModuleLabel || '' })
  }
  if (typeDef?.allowsMulti) {
    const multi = await promptText('是否多选', {
      defaultValue: String(existing?.multiFlag ?? (payload.fieldType === 'MULTI_SELECT' ? 1 : 0)),
      message: '1=多选，0=单选'
    })
    payload.multiFlag = flagValue(multi)
  }

  return payload
}

async function add() {
  const payload = await collectFieldPayload()
  if (!payload) return
  error.value = ''
  try {
    await upsertField(payload)
    notify.success('字段已保存')
    await load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function edit(f) {
  const payload = await collectFieldPayload(f)
  if (!payload) return
  error.value = ''
  try {
    await upsertField(payload)
    notify.success('字段已更新')
    await load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function del(id) {
  if (!(await confirmDialog('删除字段后可能影响已有记录展示，确认删除？', { danger: true, confirmText: '删除' }))) return
  error.value = ''
  try {
    await deleteFields([id])
    notify.success('字段已删除')
    await load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

onMounted(async () => {
  await Promise.all([load(), loadFieldTypes()])
})
</script>

<style scoped>
.actions {
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
}
</style>
