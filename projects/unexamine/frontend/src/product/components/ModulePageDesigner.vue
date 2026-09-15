<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import type { ConfiguredField, RuntimeRecord } from '../types'
import { formSections, materializeFormLayout, parseFormLayout, placeFormField, type FormSection } from '../form-layout'
import { listFieldCodes, listPageSettings, materializeListLayout, pageDensity } from '../page-layout'
import SchemaRecordTable from './SchemaRecordTable.vue'
import SchemaRecordDetail from './SchemaRecordDetail.vue'
import FieldValueDisplay from './FieldValueDisplay.vue'
import ProductStatusTag from './ProductStatusTag.vue'

const props = defineProps<{ pageType: 'LIST' | 'DETAIL'; layoutJson: string; fields: ConfiguredField[]; saving: boolean; samples: RuntimeRecord[]; sampleError?: string }>()
const emit = defineEmits<{ save: []; publish: [] }>()
const base = ref(parseFormLayout(props.layoutJson))
const activeFields = computed(() => props.fields.filter(field => field.status === 'ACTIVE'))
const sections = ref<FormSection[]>(formSections(base.value, activeFields.value))
const codes = ref<string[]>(props.pageType === 'LIST' ? listFieldCodes(base.value, activeFields.value) : sections.value.flatMap(section => section.fieldCodes))
const spans = ref<Record<string, number>>({ ...base.value.fieldSpans })
const widths = ref<Record<string, number>>({ ...parseFormLayout(base.value.columnWidths) } as Record<string, number>)
const fixed = ref<string[]>(Array.isArray(base.value.fixedFieldCodes) ? [...base.value.fixedFieldCodes] as string[] : ['title'])
const title = ref(String(base.value.components?.find(component => component.type === 'TABLE')?.title || '业务记录'))
const density = ref(pageDensity(base.value))
const selectedCode = ref(codes.value[0] || '')
const selectedSection = ref(sections.value[0]!.id)
const selectedSample = ref<number>()
const device = ref('DESKTOP')
const previewOnly = ref(false)
const canvas = ref<HTMLElement>()
const issue = ref('')
const layout = computed(() => props.pageType === 'LIST'
  ? materializeListLayout(base.value, codes.value, widths.value, fixed.value, title.value, density.value)
  : { ...materializeFormLayout(base.value, sections.value, spans.value, 'DETAIL'), density: density.value })
const baseline = ref(JSON.stringify(layout.value))
const dirty = computed(() => JSON.stringify(layout.value) !== baseline.value)
const placed = computed(() => props.pageType === 'LIST' ? codes.value : sections.value.flatMap(section => section.fieldCodes))
const selectedField = computed(() => activeFields.value.find(field => field.code === selectedCode.value))
const group = computed(() => sections.value.find(section => section.fieldCodes.includes(selectedCode.value)) || sections.value.find(section => section.id === selectedSection.value) || sections.value[0]!)
const columns = computed(() => {
  const fields = new Map(activeFields.value.map(field => [field.code, field]))
  const available = [{ code: 'title', label: '标题', width: 210 }, ...codes.value.flatMap(code => {
    const field = fields.get(code); return field ? [{ code, label: field.name, width: 140, field }] : []
  }), { code: 'status', label: '状态', width: 90 }]
  return listPageSettings(layout.value, available).columns
})
const defaults = computed(() => Object.fromEntries(activeFields.value.map(field => [field.code, parseFormLayout(field.configJson).defaultValue])))
const sample = computed(() => props.samples.find(record => record.id === selectedSample.value) || props.samples[0])
const previewRows = computed<Array<Pick<RuntimeRecord, 'id' | 'title' | 'status' | 'fields'>>>(() => sample.value ? [sample.value] : [{ id: -1, title: '尚无业务记录', status: 'ACTIVE', fields: defaults.value }])
const validationIssue = computed(() => !placed.value.length ? '页面至少需要一个业务字段' : '')
let pointer: { code: string; id: number; x: number; y: number; active: boolean } | undefined
let suppressClick = false
let clickTimer: number | undefined
function resetPointer() { window.removeEventListener('pointermove', movePointer); window.removeEventListener('pointerup', endPointer); window.removeEventListener('pointercancel', resetPointer); pointer = undefined }
function beginPointer(event: PointerEvent, code: string) {
  if (event.button !== 0 || props.saving || previewOnly.value) return
  event.preventDefault(); resetPointer(); pointer = { code, id: event.pointerId, x: event.clientX, y: event.clientY, active: false }
  window.addEventListener('pointermove', movePointer); window.addEventListener('pointerup', endPointer); window.addEventListener('pointercancel', resetPointer)
}
function movePointer(event: PointerEvent) { if (pointer && event.pointerId === pointer.id) { pointer.active ||= Math.hypot(event.clientX - pointer.x, event.clientY - pointer.y) > 6; if (pointer.active) event.preventDefault() } }
function endPointer(event: PointerEvent) {
  if (!pointer || event.pointerId !== pointer.id) return
  if (pointer.active) {
    const target = document.elementFromPoint(event.clientX, event.clientY)?.closest<HTMLElement>('[data-page-field], [data-form-section]')
    if (target && canvas.value?.contains(target)) {
      if (props.pageType === 'LIST') {
        const before = target.dataset.pageField
        if (before && before !== pointer.code && codes.value.includes(before)) {
          const next = codes.value.filter(code => code !== pointer!.code); next.splice(next.indexOf(before), 0, pointer.code); codes.value = next
        }
      } else sections.value = placeFormField(sections.value, pointer.code, target.dataset.formSection || group.value.id, target.dataset.formField)
      selectedCode.value = pointer.code; issue.value = ''
    } else issue.value = '请拖到中间预览的字段位置'
    suppressClick = true; window.clearTimeout(clickTimer); clickTimer = window.setTimeout(() => { suppressClick = false }, 0)
  }
  resetPointer()
}
function toggle(code: string, checked: boolean) {
  if (props.pageType === 'LIST') codes.value = checked ? [...new Set([...codes.value, code])] : codes.value.filter(item => item !== code)
  else if (checked) sections.value = placeFormField(sections.value, code, group.value.id)
  else sections.value = sections.value.map(section => ({ ...section, fieldCodes: section.fieldCodes.filter(item => item !== code) }))
  if (checked) selectedCode.value = code
}
function select(code: string) { if (!suppressClick) selectedCode.value = code }
function moveAdjacent(offset: number) {
  const list = props.pageType === 'LIST' ? codes.value : group.value.fieldCodes
  const index = list.indexOf(selectedCode.value), next = index + offset
  if (index < 0 || next < 0 || next >= list.length) return
  const result = [...list]; [result[index], result[next]] = [result[next]!, result[index]!]
  if (props.pageType === 'LIST') codes.value = result; else group.value.fieldCodes = result
}
function toggleFixed(code: string, checked: boolean) { fixed.value = checked ? [...new Set([...fixed.value, code])].slice(0, 3) : fixed.value.filter(item => item !== code) }
function addGroup() { const id = `section-${crypto.randomUUID()}`; sections.value.push({ id, title: `分组 ${sections.value.length + 1}`, columns: 2, fieldCodes: [], extra: {} }); selectedSection.value = id; selectedCode.value = '' }
function selectGroup(id: string) { selectedSection.value = id; selectedCode.value = '' }
function markSaved(value: string) {
  base.value = parseFormLayout(value); sections.value = formSections(base.value, activeFields.value)
  codes.value = props.pageType === 'LIST' ? listFieldCodes(base.value, activeFields.value) : sections.value.flatMap(section => section.fieldCodes)
  spans.value = { ...base.value.fieldSpans }; widths.value = { ...parseFormLayout(base.value.columnWidths) } as Record<string, number>
  fixed.value = Array.isArray(base.value.fixedFieldCodes) ? [...base.value.fixedFieldCodes] as string[] : ['title']; density.value = pageDensity(base.value)
  title.value = String(base.value.components?.find(component => component.type === 'TABLE')?.title || '业务记录'); baseline.value = JSON.stringify(layout.value)
}
watch(() => props.layoutJson, value => { if (!dirty.value) markSaved(value) })
onBeforeUnmount(() => { resetPointer(); window.clearTimeout(clickTimer) })
defineExpose({ getLayout: () => layout.value, markSaved, hasUnsaved: () => dirty.value, getIssue: () => validationIssue.value })
</script>
<template>
  <div class="module-page-designer">
    <div class="page-designer-toolbar"><div><strong>{{ pageType === 'LIST' ? '列表设计' : '详情设计' }}</strong><small>{{ dirty ? '有未保存修改' : '草稿已保存' }} · 发布后生效</small></div><div><a-segmented v-model:value="device" :options="[{ value: 'DESKTOP', label: '桌面' }, { value: 'MOBILE', label: '手机' }]" /><a-button @click="previewOnly = !previewOnly">{{ previewOnly ? '返回设计' : '预览页面' }}</a-button><a-button :loading="saving" :disabled="Boolean(validationIssue)" @click="emit('save')">保存草稿</a-button><a-button type="primary" :loading="saving" :disabled="Boolean(validationIssue)" @click="emit('publish')">保存并检查发布</a-button></div></div>
    <a-alert v-if="issue || validationIssue" type="warning" show-icon :message="issue || validationIssue" />
    <div :class="['page-designer-grid', { 'page-designer-grid--preview': previewOnly }]">
      <aside v-if="!previewOnly" class="page-design-panel"><h3>显示字段</h3><p>勾选字段，拖到中间调整位置。</p><div v-for="field in activeFields" :key="field.code" :class="['page-design-field', { selected: selectedCode === field.code }]" @click="select(field.code)"><button type="button" :aria-label="`拖动${field.name}`" @pointerdown="beginPointer($event, field.code)">⠿</button><a-checkbox :checked="placed.includes(field.code)" :disabled="saving" @change="toggle(field.code, $event.target.checked)">{{ field.name }}</a-checkbox></div></aside>
      <main ref="canvas" :class="['page-design-canvas', { 'page-design-canvas--mobile': device === 'MOBILE' }]">
        <div class="page-sample-toolbar"><strong>{{ pageType === 'LIST' ? title : '记录详情' }}</strong><a-select v-if="samples.length" v-model:value="selectedSample" :placeholder="sample?.title" aria-label="预览业务记录" :options="samples.map(record => ({ value: record.id, label: record.title }))" /><a-button v-if="pageType === 'DETAIL' && !previewOnly" size="small" @click="addGroup">添加分组</a-button></div>
        <p class="preview-source">{{ sample ? '预览当前身份有权查看的业务记录；新字段尚无值时显示为空。' : (sampleError || '尚无业务记录，以下显示字段默认值。') }}</p>
        <SchemaRecordTable v-if="pageType === 'LIST'" :records="previewRows" :columns="columns" :fixed-codes="fixed" :density="density">
          <template #value="{ column, record }"><FieldValueDisplay v-if="column.field" :field="column.field" :value="record.fields[column.code]" summary /><ProductStatusTag v-else-if="column.code === 'status'" :status="record.status" /><strong v-else>{{ record.title }}</strong></template>
        </SchemaRecordTable>
        <SchemaRecordDetail v-else :layout="layout" :fields="activeFields" :values="sample?.fields || defaults" :mobile="device === 'MOBILE'" :design="!previewOnly" :selected-code="selectedCode" @select="select" @section="selectGroup"><template #field-tools="{ field }"><button v-if="!previewOnly" type="button" class="detail-drag-handle" :aria-label="`拖动详情${field.name}`" @pointerdown.stop="beginPointer($event, field.code)">⠿</button></template></SchemaRecordDetail>
      </main>
      <aside v-if="!previewOnly" class="page-design-panel"><h3>页面属性</h3><a-form layout="vertical"><a-form-item v-if="pageType === 'LIST'" label="列表标题"><a-input v-model:value="title" /></a-form-item><a-form-item label="内容密度"><a-radio-group v-model:value="density"><a-radio value="comfortable">舒适</a-radio><a-radio value="compact">紧凑</a-radio></a-radio-group></a-form-item>
        <template v-if="pageType === 'DETAIL'"><a-form-item label="分组名称"><a-input v-model:value="group.title" aria-label="详情分组名称" /></a-form-item><a-form-item label="每行列数"><a-select v-model:value="group.columns" :options="[1,2,3].map(value => ({ value, label: `${value} 列` }))" /></a-form-item><template v-if="selectedField && placed.includes(selectedCode)"><a-form-item label="所在分组"><a-select :value="group.id" :options="sections.map(section => ({ value: section.id, label: section.title }))" @change="sections = placeFormField(sections, selectedCode, String($event))" /></a-form-item><a-form-item label="字段宽度"><a-select :value="spans[selectedCode] || 1" :options="Array.from({ length: group.columns }, (_, index) => ({ value: index+1, label: `占 ${index+1} 列` }))" @change="spans[selectedCode] = Number($event)" /></a-form-item></template></template>
        <template v-else><a-form-item label="标题列宽"><a-input-number v-model:value="widths.title" :min="80" :max="600" :precision="0" placeholder="210" /></a-form-item><a-checkbox :checked="fixed.includes('title')" :disabled="!fixed.includes('title') && fixed.length >= 3" @change="toggleFixed('title', $event.target.checked)">固定标题列</a-checkbox><template v-if="selectedField && placed.includes(selectedCode)"><a-divider>{{ selectedField.name }}</a-divider><a-form-item label="字段列宽"><a-input-number v-model:value="widths[selectedCode]" :min="80" :max="600" :precision="0" placeholder="140" /></a-form-item><a-checkbox :checked="fixed.includes(selectedCode)" :disabled="!fixed.includes(selectedCode) && fixed.length >= 3" @change="toggleFixed(selectedCode, $event.target.checked)">固定此列</a-checkbox></template></template>
        <div v-if="selectedField && placed.includes(selectedCode)" class="page-field-order"><a-button size="small" @click="moveAdjacent(-1)">上移</a-button><a-button size="small" @click="moveAdjacent(1)">下移</a-button><a-button size="small" @click="toggle(selectedCode, false)">从页面移除</a-button></div><p>已放置 {{ placed.length }} 个字段。列表、表单、详情各自保存布局。</p>
      </a-form></aside>
    </div>
  </div>
</template>
<style scoped>
.module-page-designer { padding: 16px; min-width: 0; }.page-designer-toolbar,.page-designer-toolbar > div:last-child,.page-sample-toolbar { display: flex; justify-content: space-between; align-items: center; gap: 8px; flex-wrap: wrap; }.page-designer-toolbar { position: sticky; top: 64px; z-index: 2; padding: 8px 0; background: #fff; margin-bottom: 14px; }.page-designer-toolbar small { display: block; color: #64748b; margin-top: 4px; }
.page-designer-grid { display: grid; grid-template-columns: 180px minmax(280px,1fr) 240px; align-items: start; gap: 14px; }.page-designer-grid--preview { grid-template-columns: 1fr; }.page-design-panel { position: sticky; top: 132px; padding: 12px; border: 1px solid #e2e8f0; background: #f8fafc; border-radius: 8px; max-height: calc(100dvh - 148px); overflow: auto; min-width: 0; }.page-design-panel h3 { font-size: 14px; margin: 0 0 12px; }.page-design-panel p,.preview-source { color: #64748b; font-size: 12px; }
.page-design-field { display: flex; align-items: center; margin-bottom: 6px; padding: 6px 3px; border: 1px solid transparent; border-radius: 5px; }.page-design-field.selected { background: #fff; border-color: #93c5fd; }.page-design-field button,.detail-drag-handle { padding: 0 5px; border: 0; background: transparent; color: #64748b; cursor: grab; touch-action: none; user-select: none; font-size: 20px; }.detail-drag-handle { float: right; }.page-field-order { display: flex; flex-wrap: wrap; gap: 5px; margin: 14px 0; }
.page-design-canvas { padding: 16px; border: 1px solid #dbe4ef; border-radius: 8px; min-width: 0; background: #fff; }.page-design-canvas--mobile { width: min(100%,390px); justify-self: center; }.page-sample-toolbar :deep(.ant-select) { width: min(100%,240px); }
@media (max-width: 1150px) { .page-designer-grid { grid-template-columns: 145px minmax(250px,1fr) 210px; gap: 9px; }.page-designer-grid--preview { grid-template-columns: 1fr; } }
@media (max-width: 880px) { .page-designer-grid { grid-template-columns: 130px minmax(0,1fr); }.page-design-panel:last-child { position: static; grid-column: 1 / -1; max-height: none; }.page-designer-grid--preview { grid-template-columns: 1fr; } }
@media (max-width: 520px) { .page-designer-grid { grid-template-columns: 1fr; }.page-design-panel { position: static; max-height: 190px; }.page-design-panel:last-child { max-height: none; } }
</style>
