<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import type { ConfiguredField } from '../types'
import { fieldTypes, isSharedRuntimeField } from '../field-capabilities'
import { formSections, materializeFormLayout, parseFormLayout, placeFormField, type FormSection } from '../form-layout'
import RuntimeFieldInput from './RuntimeFieldInput.vue'
import SchemaFormLayout from './SchemaFormLayout.vue'

const props = defineProps<{ layoutJson: string; fields: ConfiguredField[]; pendingField?: ConfiguredField; saving: boolean; propertyDirty?: boolean; propertyIssue?: string }>()
const emit = defineEmits<{ add: [type: string]; select: [field: ConfiguredField]; save: []; publish: []; cancel: [] }>()
const base = ref(parseFormLayout(props.layoutJson))
const sections = ref<FormSection[]>(formSections(base.value, props.fields))
const spans = ref<Record<string, number>>({ ...base.value.fieldSpans })
const baseline = ref('')
const selectedCode = ref('')
const selectedSection = ref(sections.value[0]?.id || 'main')
const device = ref('DESKTOP')
const previewOnly = ref(false)
const issue = ref('')
const paletteSearch = ref('')
const canvas = ref<HTMLElement>()
const previewValues = reactive<Record<string, unknown>>({})
const layout = computed(() => materializeFormLayout(base.value, sections.value, spans.value))
baseline.value = JSON.stringify(layout.value)
const dirty = computed(() => JSON.stringify(layout.value) !== baseline.value)
const allFields = computed(() => props.pendingField ? [...props.fields, props.pendingField] : props.fields)
const selectedField = computed(() => allFields.value.find(field => field.code === selectedCode.value))
const group = computed(() => sections.value.find(section => section.fieldCodes.includes(selectedCode.value)) || sections.value.find(section => section.id === selectedSection.value) || sections.value[0]!)
const palette = computed(() => fieldTypes.filter(field => `${field.name}${field.hint}`.includes(paletteSearch.value)))
const drag = ref<{ type?: string; code?: string; x: number; y: number; startX: number; startY: number; active: boolean; pointerId: number }>()
let suppressClick = false
let clickTimer: number | undefined
function resetPointer() { window.removeEventListener('pointermove', movePointer); window.removeEventListener('pointerup', endPointer); window.removeEventListener('pointercancel', cancelPointer); drag.value = undefined }
function beginPointer(event: PointerEvent, source: { type?: string; code?: string }) {
  if (event.button !== 0 || props.saving || previewOnly.value) return
  event.preventDefault()
  resetPointer()
  drag.value = { ...source, x: event.clientX, y: event.clientY, startX: event.clientX, startY: event.clientY, active: false, pointerId: event.pointerId }
  window.addEventListener('pointermove', movePointer)
  window.addEventListener('pointerup', endPointer)
  window.addEventListener('pointercancel', cancelPointer)
}
function movePointer(event: PointerEvent) {
  if (!drag.value || event.pointerId !== drag.value.pointerId) return
  drag.value.x = event.clientX; drag.value.y = event.clientY
  drag.value.active ||= Math.hypot(event.clientX - drag.value.startX, event.clientY - drag.value.startY) > 6
  if (drag.value.active) event.preventDefault()
}
function endPointer(event: PointerEvent) {
  const source = drag.value
  if (!source || event.pointerId !== source.pointerId) return
  if (source.active) {
    suppressClick = true; window.clearTimeout(clickTimer); clickTimer = window.setTimeout(() => { suppressClick = false }, 0)
    const target = document.elementFromPoint(event.clientX, event.clientY)?.closest<HTMLElement>('[data-form-section]')
    if (target && canvas.value?.contains(target)) {
      const sectionId = target.dataset.formSection || selectedSection.value
      const beforeCode = target.closest<HTMLElement>('[data-form-field]')?.dataset.formField
      if (source.type) addType(source.type, sectionId, beforeCode)
      else if (source.code) { sections.value = placeFormField(sections.value, source.code, sectionId, beforeCode); selectedSection.value = sectionId }
    } else issue.value = '请拖到中间表单画布内'
  }
  resetPointer()
}
function cancelPointer() { resetPointer() }
function addType(type: string, sectionId = selectedSection.value, beforeCode?: string) {
  if (suppressClick && !drag.value?.active) return
  if (props.pendingField) { issue.value = '请先保存或取消正在配置的新字段'; return }
  if (props.propertyDirty) { issue.value = '请先保存当前字段属性'; return }
  issue.value = ''
  sections.value = placeFormField(sections.value, '__pending_field__', sectionId, beforeCode)
  selectedCode.value = '__pending_field__'; selectedSection.value = sectionId
  emit('add', type)
}
function selectField(code: string) {
  if (suppressClick || previewOnly.value || code === selectedCode.value) return
  if (props.pendingField || props.propertyDirty) { issue.value = '请先保存或取消当前字段属性'; return }
  selectedCode.value = code
  selectedSection.value = sections.value.find(section => section.fieldCodes.includes(code))?.id || selectedSection.value
  const field = props.fields.find(field => field.code === code)
  if (field) emit('select', field)
}
function addSection() {
  if (props.pendingField || props.propertyDirty) { issue.value = '请先保存或取消当前字段属性'; return }
  const id = `section-${crypto.randomUUID()}`
  sections.value.push({ id, title: `分组 ${sections.value.length + 1}`, columns: 2, fieldCodes: [], extra: {} })
  selectedSection.value = id; selectedCode.value = ''; emit('cancel')
}
function selectSection(id: string) {
  if (props.pendingField || props.propertyDirty) { issue.value = '请先保存或取消当前字段属性'; return }
  selectedSection.value = id; selectedCode.value = ''; emit('cancel')
}
function moveSelected(sectionId: string) { sections.value = placeFormField(sections.value, selectedCode.value, sectionId); selectedSection.value = sectionId }
function moveAdjacent(offset: number) {
  const codes = group.value.fieldCodes; const index = codes.indexOf(selectedCode.value); const next = index + offset
  if (index < 0 || next < 0 || next >= codes.length) return
  const result = [...codes]; [result[index], result[next]] = [result[next]!, result[index]!]; group.value.fieldCodes = result
}
function removeSelected() {
  if (selectedField.value?.required) { issue.value = '必填字段必须保留在表单中'; return }
  sections.value = sections.value.map(section => ({ ...section, fieldCodes: section.fieldCodes.filter(code => code !== selectedCode.value) }))
  selectedCode.value = ''; emit('cancel')
}
function commitField(field: ConfiguredField) {
  sections.value = sections.value.map(section => ({ ...section, fieldCodes: section.fieldCodes.map(code => code === '__pending_field__' ? field.code : code) }))
  if (spans.value.__pending_field__) { spans.value[field.code] = spans.value.__pending_field__; delete spans.value.__pending_field__ }
  if (selectedCode.value === '__pending_field__') selectedCode.value = field.code
  selectedSection.value = sections.value.find(section => section.fieldCodes.includes(selectedCode.value))?.id || selectedSection.value
  issue.value = ''
}
function markSaved(layoutJson: string) {
  base.value = parseFormLayout(layoutJson); sections.value = formSections(base.value, props.fields); spans.value = { ...base.value.fieldSpans }
  baseline.value = JSON.stringify(layout.value)
}
watch(() => props.layoutJson, value => { if (!dirty.value) markSaved(value) })
watch(() => props.pendingField, (field, previous) => {
  if (!field && previous) sections.value = sections.value.map(section => ({ ...section, fieldCodes: section.fieldCodes.filter(code => code !== '__pending_field__') }))
  if (field) { const config = parseFormLayout(field.configJson); previewValues[field.code] = field.fieldType === 'SECRET' ? undefined : config.defaultValue ?? (field.fieldType === 'BOOLEAN' ? false : undefined) }
}, { deep: true })
watch(allFields, fields => {
  const codes = new Set(fields.map(field => field.code))
  Object.keys(previewValues).forEach(code => { if (!codes.has(code)) delete previewValues[code] })
  fields.forEach(field => { if (field.fieldType === 'SECRET') previewValues[field.code] = undefined
    else if (!(field.code in previewValues)) previewValues[field.code] = parseFormLayout(field.configJson).defaultValue ?? (field.fieldType === 'BOOLEAN' ? false : undefined) })
}, { immediate: true })
onBeforeUnmount(() => { resetPointer(); window.clearTimeout(clickTimer); Object.keys(previewValues).forEach(code => delete previewValues[code]) })
defineExpose({ getLayout: () => layout.value, commitField, markSaved, hasUnsaved: () => dirty.value || Boolean(props.pendingField) || Boolean(props.propertyDirty) })
</script>
<template>
  <div class="module-form-designer">
    <div class="form-designer-toolbar">
      <div><strong>表单设计</strong><small>{{ dirty || propertyDirty || pendingField ? '有未保存修改' : '草稿已保存' }} · 发布后运行页才会切换</small></div>
      <div><a-segmented v-model:value="device" :options="[{ value: 'DESKTOP', label: '桌面' }, { value: 'MOBILE', label: '手机' }]" /><a-button @click="previewOnly = !previewOnly">{{ previewOnly ? '返回设计' : '预览表单' }}</a-button><a-button :loading="saving" :disabled="Boolean(propertyIssue)" @click="emit('save')">保存草稿</a-button><a-button type="primary" :loading="saving" :disabled="Boolean(propertyIssue)" @click="emit('publish')">保存并检查发布</a-button></div>
    </div>
    <a-alert v-if="issue" type="warning" show-icon :message="issue" class="section-alert" />
    <div :class="['form-designer-grid', { 'form-designer-grid--preview': previewOnly }]">
      <aside v-if="!previewOnly" class="form-type-palette">
        <h3>字段类型</h3><a-input v-model:value="paletteSearch" placeholder="搜索字段类型" aria-label="搜索字段类型" allow-clear />
        <div class="form-type-palette__list"><button v-for="field in palette" :key="field.type" type="button" class="form-type-button" :data-field-type="field.type" @pointerdown="beginPointer($event, { type: field.type })" @click="addType(field.type)"><strong>{{ field.name }}</strong><small>{{ field.hint }}</small></button></div>
        <details><summary>已有字段</summary><button v-for="field in fields.filter(field => field.status === 'ACTIVE')" :key="field.code" type="button" class="form-type-button" @pointerdown="beginPointer($event, { code: field.code })" @click="sections = placeFormField(sections, field.code, selectedSection)">{{ field.name }}<small>{{ sections.some(section => section.fieldCodes.includes(field.code)) ? '已放置，可拖动到其他位置' : '拖入表单' }}</small></button></details>
      </aside>
      <main ref="canvas" :class="['form-design-canvas', { 'form-design-canvas--mobile': device === 'MOBILE' }]">
        <div class="form-canvas-heading"><strong>{{ previewOnly ? '运行表单预览' : '表单画布' }}</strong><a-button v-if="!previewOnly" size="small" @click="addSection">添加分组</a-button></div>
        <a-form layout="vertical"><SchemaFormLayout :layout="layout" :fields="allFields" :design="!previewOnly" :selected-code="selectedCode" :mobile="device === 'MOBILE'" @select="selectField" @section="selectSection">
          <template #field-tools="{ field }"><button v-if="!previewOnly" class="form-field-handle" type="button" :aria-label="`拖动${field.name}`" @pointerdown.stop="beginPointer($event, { code: field.code })">⠿</button></template>
          <template #field="{ field }"><a-form-item :label="field.name" :required="field.required"><RuntimeFieldInput v-if="isSharedRuntimeField(field.fieldType)" v-model="previewValues[field.code]" :field="field" /><a-input v-else disabled :placeholder="`${fieldTypes.find(type => type.type === field.fieldType)?.name || field.fieldType}字段`" /></a-form-item></template>
        </SchemaFormLayout></a-form>
      </main>
      <aside v-if="!previewOnly" class="form-field-properties">
        <h3>{{ selectedField ? '字段配置' : '分组配置' }}</h3>
        <div><a-form layout="vertical"><a-form-item label="分组名称"><a-input v-model:value="group.title" aria-label="分组名称" /></a-form-item><a-form-item label="每行列数"><a-select v-model:value="group.columns" :options="[1,2,3].map(value => ({ value, label: `${value} 列` }))" /></a-form-item>
          <template v-if="selectedField"><a-form-item label="字段宽度"><a-select :value="spans[selectedCode] || 1" :options="Array.from({ length: group.columns }, (_,index) => ({ value: index+1, label: `占 ${index+1} 列` }))" @update:value="spans[selectedCode] = Number($event)" /></a-form-item><a-form-item label="所在分组"><a-select :value="group.id" :options="sections.map(section => ({ value: section.id, label: section.title }))" @update:value="moveSelected(String($event))" /></a-form-item><div class="form-field-order"><a-button size="small" @click="moveAdjacent(-1)">上移</a-button><a-button size="small" @click="moveAdjacent(1)">下移</a-button><a-button size="small" :disabled="selectedField.required || selectedCode === '__pending_field__'" @click="removeSelected">从表单移除</a-button></div><a-divider /></template>
          <p v-else>从左侧拖入类型，选中字段后配置名称、校验和默认值。</p>
        </a-form><slot v-if="selectedField" name="properties" /></div>
      </aside>
    </div>
    <Teleport to="body"><div v-if="drag?.active" class="form-drag-ghost" :style="{ left: `${drag.x+12}px`, top: `${drag.y+12}px` }">{{ drag.type ? fieldTypes.find(field => field.type === drag?.type)?.name : allFields.find(field => field.code === drag?.code)?.name }}</div></Teleport>
  </div>
</template>
<style scoped>
.module-form-designer { min-width: 0; padding: 16px; }
.form-designer-toolbar, .form-designer-toolbar > div:last-child, .form-canvas-heading { display: flex; align-items: center; justify-content: space-between; gap: 8px; flex-wrap: wrap; }
.form-designer-toolbar { margin-bottom: 14px; position: sticky; top: 64px; z-index: 2; padding: 8px 0; background: #fff; }.form-designer-toolbar small { display: block; margin-top: 4px; color: #64748b; }
.form-designer-grid { display: grid; grid-template-columns: 180px minmax(280px, 1fr) 290px; gap: 14px; align-items: start; min-width: 0; }
.form-designer-grid--preview { grid-template-columns: minmax(0, 1fr); }
.form-type-palette, .form-field-properties { position: sticky; top: 132px; background: #f8fafc; padding: 12px; border: 1px solid #e2e8f0; border-radius: 8px; min-width: 0; max-height: calc(100dvh - 148px); overflow: auto; }
.form-type-palette h3, .form-field-properties h3 { font-size: 14px; margin: 0 0 12px; }.form-type-palette__list { max-height: 510px; overflow: auto; margin-top: 10px; }
.form-type-button { display: block; width: 100%; padding: 10px; margin-bottom: 7px; border: 1px solid #dbe4ef; border-radius: 6px; background: #fff; text-align: left; cursor: grab; touch-action: none; user-select: none; }.form-type-button small { display: block; font-size: 11px; color: #64748b; margin-top: 4px; }.form-type-button:focus-visible { outline: 2px solid #2563eb; }
.form-design-canvas { min-width: 0; min-height: 330px; padding: 16px; background: #fff; border: 1px solid #dbe4ef; border-radius: 8px; }.form-design-canvas--mobile { width: min(100%,390px); justify-self: center; }.form-canvas-heading { margin-bottom: 18px; }
.form-field-handle { float: right; position: relative; z-index: 1; padding: 0 5px; border: 0; background: transparent; color: #64748b; cursor: grab; touch-action: none; user-select: none; font-size: 20px; }
.form-field-order { display: flex; gap: 5px; flex-wrap: wrap; }.form-field-properties :deep(.form-grid) { grid-template-columns: 1fr; }.form-field-properties p { color: #64748b; font-size: 12px; }
.form-drag-ghost { position: fixed; z-index: 3000; pointer-events: none; background: #2563eb; color: #fff; padding: 8px 16px; border-radius: 5px; box-shadow: 0 4px 14px #0002; }
@media (max-width: 1150px) { .form-designer-grid { grid-template-columns: 145px minmax(250px,1fr) 250px; gap: 9px; }.form-type-palette,.form-field-properties { padding: 9px; } }
@media (max-width: 880px) { .form-designer-grid { grid-template-columns: 130px minmax(0,1fr); }.form-field-properties { position: static; grid-column: 1 / -1; max-height: none; }.form-designer-grid--preview { grid-template-columns: 1fr; } }
@media (max-width: 520px) { .form-designer-grid { grid-template-columns: 1fr; }.form-type-palette__list { max-height: 160px; }.form-field-properties { grid-column: auto; } }
</style>
