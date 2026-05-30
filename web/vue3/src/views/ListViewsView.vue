<template>
  <AdminLayout>
    <h2>列表视图 · app {{ appId }}</h2>
    <div class="toolbar">
      <select v-if="models.length" v-model="modelIdText" @change="loadViews">
        <option value="">选择模型</option>
        <option v-for="m in models" :key="m.id" :value="String(m.id)">
          {{ m.modelName || m.modelCode }} (#{{ m.id }})
        </option>
      </select>
      <input v-else v-model="modelIdText" placeholder="modelId" />
      <button type="button" @click="loadViews">加载视图</button>
      <button type="button" @click="addView">新建视图</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <table v-if="views.length" class="table">
        <thead><tr><th>ID</th><th>编码</th><th>名称</th><th>默认</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="v in views" :key="v.id">
            <td>{{ v.id }}</td>
            <td>{{ v.viewCode }}</td>
            <td>{{ v.viewName }}</td>
            <td>{{ v.defaultFlag }}</td>
            <td class="actions">
              <button type="button" @click="editCols(v)">列配置</button>
              <button type="button" class="secondary" @click="editView(v)">编辑</button>
              <button type="button" class="danger" @click="removeView(v)">删除</button>
            </td>
          </tr>
        </tbody>
    </table>
    <p v-else class="muted">输入 modelId 后加载</p>

    <template v-if="views.length && modelIdText">
      <h3>筛选模板 · model {{ modelIdText }}</h3>
      <div class="toolbar">
        <button type="button" @click="loadFilterTpls">刷新筛选模板</button>
        <button type="button" @click="addFilterTpl">新建筛选模板</button>
      </div>
      <table v-if="filterTpls.length" class="table">
        <thead><tr><th>ID</th><th>编码</th><th>名称</th><th>menuId</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="t in filterTpls" :key="t.id">
            <td>{{ t.id }}</td>
            <td>{{ t.tplCode }}</td>
            <td>{{ t.tplName }}</td>
            <td>{{ t.menuId ?? '—' }}</td>
            <td class="actions">
              <button type="button" @click="editFilterFields(t)">筛选项</button>
              <button type="button" class="secondary" @click="editFilterTpl(t)">编辑</button>
              <button type="button" class="danger" @click="removeFilterTpl(t)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-else class="muted">暂无筛选模板，点击「刷新」或「新建」</p>
    </template>

    <template v-if="activeFilterTpl">
      <h3>筛选项 · tpl #{{ activeFilterTpl.id }}</h3>
      <div class="toolbar">
        <button type="button" @click="addFilterField">添加筛选项</button>
        <button type="button" class="secondary" @click="loadFilterFields">刷新筛选项</button>
      </div>
      <table v-if="filterFields.length" class="table">
        <thead><tr><th>ID</th><th>字段</th><th>操作符</th><th>默认值</th><th>必填</th><th>排序</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="f in filterFields" :key="f.id">
            <td>{{ f.id }}</td>
            <td>{{ fieldLabel(f.fieldId) }}</td>
            <td>{{ opLabel(f.opCode) }}</td>
            <td>{{ f.defaultValue || '—' }}</td>
            <td>{{ f.requiredFlag === 1 ? '是' : '否' }}</td>
            <td>{{ f.sortNo }}</td>
            <td class="actions">
              <button type="button" class="secondary" @click="editFilterField(f)">编辑</button>
              <button type="button" class="danger" @click="removeFilterField(f)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-else class="muted">暂无筛选项</p>
    </template>

    <template v-if="activeView">
      <h3>列 · view #{{ activeView.id }}</h3>
      <div class="toolbar">
        <button type="button" @click="addCol">添加列</button>
        <button type="button" class="secondary" @click="loadCols">刷新列</button>
      </div>
      <table v-if="cols.length" class="table">
        <thead><tr><th>ID</th><th>字段</th><th>标题</th><th>宽度</th><th>排序</th><th>显示</th><th>固定</th><th>格式</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="c in cols" :key="c.id">
            <td>{{ c.id }}</td>
            <td>{{ fieldLabel(c.fieldId) }}</td>
            <td>{{ c.colTitle }}</td>
            <td>{{ c.width ?? '—' }}</td>
            <td>{{ c.sortNo }}</td>
            <td>{{ c.visibleFlag === 0 ? '否' : '是' }}</td>
            <td>{{ fixedLabel(c.fixedType) }}</td>
            <td>{{ formatLabel(c.formatJson) }}</td>
            <td class="actions">
              <button type="button" class="secondary" @click="editCol(c)">编辑</button>
              <button type="button" class="danger" @click="removeCol(c)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </template>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import { listFieldsByModel, listModelsByApp } from '../api/meta'
import {
  deleteFilterTpls,
  deleteFilterFields,
  deleteListViews,
  deleteViewCols,
  listFilterFields,
  listFilterTpls,
  listViewCols,
  listViewsByModel,
  upsertFilterTpl,
  upsertFilterField,
  upsertListView,
  upsertViewCol
} from '../api/module'
import { confirmDialog, promptText } from '../utils/dialog.js'
import { notify } from '../utils/notify.js'

const route = useRoute()
const appId = computed(() => String(route.params.appId || ''))
const modelIdText = ref(String(route.query.modelId || ''))
const models = ref([])
const fields = ref([])
const views = ref([])
const filterTpls = ref([])
const filterFields = ref([])
const cols = ref([])
const activeView = ref(null)
const activeFilterTpl = ref(null)
const error = ref('')

async function loadModels() {
  try {
    const r = await listModelsByApp(appId.value)
    models.value = r.data || []
    if (!modelIdText.value && models.value[0]?.id) {
      modelIdText.value = String(models.value[0].id)
    }
  } catch {
    models.value = []
  }
}

async function loadFields() {
  const modelId = modelIdText.value.trim()
  if (!modelId) {
    fields.value = []
    return
  }
  try {
    const r = await listFieldsByModel(modelId)
    fields.value = r.data || []
  } catch {
    fields.value = []
  }
}

function fieldLabel(fieldId) {
  const id = String(fieldId ?? '')
  const f = fields.value.find((x) => String(x.id) === id)
  return f ? `${f.fieldName || f.fieldCode} (${f.fieldCode})` : `#${id}`
}

function fieldOptionMessage() {
  if (!fields.value.length) return '请输入字段 ID'
  return fields.value
    .slice(0, 50)
    .map((f) => `${f.id} - ${f.fieldName || f.fieldCode} (${f.fieldCode})`)
    .join('\n')
}

function opLabel(value) {
  const map = {
    eq: '等于',
    ne: '不等于',
    like: '包含',
    in: '属于',
    between: '区间',
    gt: '大于',
    ge: '大于等于',
    lt: '小于',
    le: '小于等于'
  }
  return map[value] || value || '等于'
}

function normalizeOp(value) {
  const text = String(value || 'eq').trim().toLowerCase()
  return ['eq', 'ne', 'like', 'in', 'between', 'gt', 'ge', 'lt', 'le'].includes(text) ? text : 'eq'
}

function fixedLabel(value) {
  if (value === 'left') return '左固定'
  if (value === 'right') return '右固定'
  return '—'
}

function prettyJson(value) {
  if (!value) return ''
  if (typeof value !== 'string') return JSON.stringify(value, null, 2)
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return String(value)
  }
}

function normalizeFormatJson(value) {
  const raw = String(value || '').trim()
  if (!raw) return null
  const parsed = JSON.parse(raw)
  if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
    throw new Error('格式配置必须是 JSON 对象')
  }
  return JSON.stringify(parsed)
}

function formatLabel(value) {
  if (!value) return '默认'
  try {
    const obj = typeof value === 'string' ? JSON.parse(value) : value
    const parts = []
    if (obj.emptyText) parts.push(`空值=${obj.emptyText}`)
    if (obj.trim) parts.push('去空格')
    if (obj.dateOnly) parts.push('日期')
    if (obj.numberScale !== undefined && obj.numberScale !== null) parts.push(`小数${obj.numberScale}位`)
    if (obj.prefix || obj.suffix) parts.push('前后缀')
    if (obj.mapping || obj.mappings) parts.push('映射')
    return parts.length ? parts.join(' / ') : '自定义'
  } catch {
    return '自定义'
  }
}

function fixedValue(value) {
  const text = String(value || '').trim().toLowerCase()
  if (text === 'left' || text === '左' || text === '左固定') return 'left'
  if (text === 'right' || text === '右' || text === '右固定') return 'right'
  return null
}

function optionalNumber(value) {
  const text = String(value || '').trim()
  if (!text) return null
  const n = Number(text)
  return Number.isNaN(n) ? null : n
}

async function loadViews() {
  const modelId = modelIdText.value.trim()
  if (!modelId) {
    error.value = '请输入 modelId'
    return
  }
  error.value = ''
  try {
    await loadFields()
    const r = await listViewsByModel(modelId)
    views.value = r.data || []
    activeView.value = null
    activeFilterTpl.value = null
    cols.value = []
    filterFields.value = []
    await loadFilterTpls()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function addView() {
  await saveView()
}

async function editView(view) {
  await saveView(view)
}

async function saveView(existing = null) {
  const modelId = modelIdText.value.trim()
  const viewCode = await promptText('视图编码', { defaultValue: existing?.viewCode || '' })
  const viewName = await promptText('视图名称', { defaultValue: existing?.viewName || '' })
  const defaultFlag = await promptText('是否默认视图', {
    defaultValue: String(existing?.defaultFlag ?? 0),
    message: '1=默认，0=普通'
  })
  if (!modelId || !viewCode || !viewName) return
  error.value = ''
  try {
    await upsertListView({
      id: existing?.id ?? null,
      appId: appId.value,
      modelId,
      viewCode,
      viewName,
      defaultFlag: flagValue(defaultFlag),
      status: existing?.status ?? 1
    })
    notify.success('视图已保存')
    await loadViews()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

function flagValue(v) {
  const s = String(v ?? '').trim()
  return s === '1' || s === '是' || s.toLowerCase() === 'true' ? 1 : 0
}

async function removeView(v) {
  if (!v?.id || !(await confirmDialog(`删除视图 ${v.viewCode || v.id}？`, { danger: true, confirmText: '删除' }))) return
  error.value = ''
  try {
    await deleteListViews([v.id])
    notify.success('视图已删除')
    if (activeView.value?.id === v.id) {
      activeView.value = null
      cols.value = []
    }
    await loadViews()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function editCols(v) {
  activeView.value = v
  await loadCols()
}

async function loadCols() {
  if (!activeView.value) return
  error.value = ''
  try {
    const r = await listViewCols(activeView.value.id)
    cols.value = r.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function loadFilterTpls() {
  const modelId = modelIdText.value.trim()
  if (!modelId) return
  error.value = ''
  try {
    const r = await listFilterTpls(modelId)
    filterTpls.value = r.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function addFilterTpl() {
  await saveFilterTpl()
}

async function editFilterTpl(t) {
  await saveFilterTpl(t)
}

async function saveFilterTpl(existing = null) {
  const modelId = modelIdText.value.trim()
  const tplCode = await promptText('筛选模板编码', { defaultValue: existing?.tplCode || '' })
  const tplName = await promptText('筛选模板名称', { defaultValue: existing?.tplName || '' })
  const menuId = await promptText('menuId', { defaultValue: existing?.menuId ? String(existing.menuId) : '', message: '可留空' })
  if (!modelId || !tplCode || !tplName) return
  error.value = ''
  try {
    await upsertFilterTpl({
      id: existing?.id ?? null,
      appId: appId.value,
      modelId,
      menuId: menuId || null,
      tplCode,
      tplName,
      status: existing?.status ?? 1
    })
    notify.success('筛选模板已保存')
    await loadFilterTpls()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function removeFilterTpl(t) {
  if (!t?.id || !(await confirmDialog('删除筛选模板 ' + (t.tplCode || t.id) + '?', { danger: true, confirmText: '删除' }))) return
  error.value = ''
  try {
    await deleteFilterTpls([t.id])
    if (activeFilterTpl.value?.id === t.id) {
      activeFilterTpl.value = null
      filterFields.value = []
    }
    notify.success('筛选模板已删除')
    await loadFilterTpls()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function editFilterFields(t) {
  activeFilterTpl.value = t
  await loadFilterFields()
}

async function loadFilterFields() {
  if (!activeFilterTpl.value) return
  error.value = ''
  try {
    if (!fields.value.length) await loadFields()
    const r = await listFilterFields(activeFilterTpl.value.id)
    filterFields.value = r.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function addFilterField() {
  await saveFilterField()
}

async function editFilterField(f) {
  await saveFilterField(f)
}

async function saveFilterField(existing = null) {
  if (!activeFilterTpl.value) return
  if (!fields.value.length) await loadFields()
  const defaultField = fields.value.find((f) => !filterFields.value.some((x) => String(x.fieldId) === String(f.id)))
  const fieldId = String((await promptText('筛选字段 ID', {
    defaultValue: existing?.fieldId ? String(existing.fieldId) : (defaultField?.id ? String(defaultField.id) : ''),
    message: fieldOptionMessage()
  })) || '').trim()
  const opCode = await promptText('操作符', {
    defaultValue: existing?.opCode || 'eq',
    message: 'eq/ne/like/in/between/gt/ge/lt/le'
  })
  const defaultValue = await promptText('默认值', {
    defaultValue: existing?.defaultValue || '',
    message: '可留空；in/between 可用英文逗号分隔'
  })
  const requiredFlag = await promptText('是否必填', { defaultValue: String(existing?.requiredFlag ?? 0), message: '1=必填，0=可选' })
  const sortNo = await promptText('排序号', { defaultValue: String(existing?.sortNo ?? filterFields.value.length + 1) })
  if (!fieldId) return
  error.value = ''
  try {
    await upsertFilterField({
      id: existing?.id ?? null,
      tplId: activeFilterTpl.value.id,
      fieldId,
      opCode: normalizeOp(opCode),
      defaultValue: defaultValue || null,
      requiredFlag: flagValue(requiredFlag),
      sortNo: Number.isNaN(Number(sortNo)) ? filterFields.value.length + 1 : Number(sortNo)
    })
    notify.success(existing?.id ? '筛选项已更新' : '筛选项已保存')
    await loadFilterFields()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function removeFilterField(f) {
  if (!f?.id || !(await confirmDialog(`删除筛选项 ${fieldLabel(f.fieldId)}？`, { danger: true, confirmText: '删除' }))) return
  error.value = ''
  try {
    await deleteFilterFields([f.id])
    notify.success('筛选项已删除')
    await loadFilterFields()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function addCol() {
  await saveCol()
}

async function editCol(c) {
  await saveCol(c)
}

async function saveCol(c = null) {
  if (!activeView.value) return
  if (!fields.value.length) await loadFields()
  const defaultField = fields.value.find((f) => !cols.value.some((x) => String(x.fieldId) === String(f.id)))
  const fieldId = String((await promptText('字段 ID', {
    defaultValue: c?.fieldId ? String(c.fieldId) : (defaultField?.id ? String(defaultField.id) : ''),
    message: fieldOptionMessage()
  })) || '').trim()
  const selected = fields.value.find((f) => String(f.id) === fieldId)
  const colTitle = await promptText('列标题', { defaultValue: c?.colTitle || selected?.fieldName || selected?.fieldCode || '' })
  const width = await promptText('列宽度(px)', { defaultValue: c?.width == null ? '' : String(c.width), message: '可留空，如 160' })
  const sortNo = await promptText('排序号', { defaultValue: String(c?.sortNo ?? cols.value.length + 1) })
  const visibleFlag = await promptText('是否显示', { defaultValue: String(c?.visibleFlag ?? 1), message: '1=显示，0=隐藏' })
  const fixedType = await promptText('固定列', { defaultValue: c?.fixedType || '', message: '可留空；left/right 或 左/右' })
  const formatText = await promptText('格式配置 JSON', {
    defaultValue: prettyJson(c?.formatJson),
    multiline: true,
    message: '可留空。示例：{"emptyText":"-","dateOnly":true,"numberScale":2}'
  })
  if (formatText === null) return
  if (!fieldId || !colTitle) return
  error.value = ''
  try {
    const formatJson = normalizeFormatJson(formatText)
    await upsertViewCol({
      id: c?.id ?? null,
      viewId: activeView.value.id,
      fieldId,
      colTitle,
      sortNo: Number.isNaN(Number(sortNo)) ? cols.value.length + 1 : Number(sortNo),
      visibleFlag: flagValue(visibleFlag),
      width: optionalNumber(width),
      fixedType: fixedValue(fixedType),
      formatJson
    })
    notify.success(c?.id ? '列已更新' : '列已保存')
    await loadCols()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function removeCol(c) {
  if (!c?.id || !(await confirmDialog(`删除列 ${c.colTitle || c.id}？`, { danger: true, confirmText: '删除' }))) return
  error.value = ''
  try {
    await deleteViewCols([c.id])
    notify.success('列已删除')
    await loadCols()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

onMounted(async () => {
  await loadModels()
  if (modelIdText.value) await loadViews()
})
</script>

<style scoped>
input,
select {
  padding: 0.35rem 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  min-width: 180px;
}
h3 { margin-top: 1.25rem; }
.actions {
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
}
</style>
