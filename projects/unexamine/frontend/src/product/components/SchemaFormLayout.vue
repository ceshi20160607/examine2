<script setup lang="ts">
import { computed } from 'vue'
import type { ConfiguredField } from '../types'
import { fieldSpan, formSections } from '../form-layout'
const props = defineProps<{ layout?: unknown; fields: ConfiguredField[]; design?: boolean; selectedCode?: string; mobile?: boolean; dropHint?: string }>()
const emit = defineEmits<{ select: [code: string]; section: [id: string] }>()
const sections = computed(() => formSections(props.layout, props.fields))
const byCode = computed(() => new Map(props.fields.map(field => [field.code, field])))
</script>
<template>
  <div :class="['schema-form-layout', { 'schema-form-layout--design': design, 'schema-form-layout--mobile': mobile }]">
    <section v-for="section in sections" :key="section.id" :data-form-section="section.id" class="schema-form-section">
      <h3 @click="design && emit('section', section.id)">{{ section.title }}<slot name="section-tools" :section="section" /></h3>
      <div class="schema-form-grid" :style="{ gridTemplateColumns: `repeat(${mobile ? 1 : section.columns}, minmax(0, 1fr))` }">
        <div v-for="code in section.fieldCodes" :key="code" :data-form-field="code" :data-form-section="section.id"
          :class="['schema-form-cell', { selected: design && code === selectedCode }]"
          :style="{ gridColumn: `span ${mobile ? 1 : fieldSpan(layout, code, section.columns)}` }" @click="design && emit('select', code)">
          <slot name="field-tools" :field="byCode.get(code)!" :section="section" />
          <slot name="field" :field="byCode.get(code)!" :section="section" />
        </div>
        <div v-if="design" class="schema-form-drop" :data-form-section="section.id">{{ dropHint || '拖入字段，或点击左侧类型添加' }}</div>
      </div>
    </section>
  </div>
</template>
<style scoped>
.schema-form-layout { display: grid; gap: 18px; min-width: 0; }
.schema-form-section { min-width: 0; }
.schema-form-section h3 { display: flex; align-items: center; justify-content: space-between; margin: 0 0 14px; padding-bottom: 9px; border-bottom: 1px solid #e2e8f0; font-size: 15px; color: #334155; }
.schema-form-grid { display: grid; gap: 12px 16px; min-width: 0; }
.schema-form-cell { min-width: 0; }
.schema-form-layout--design .schema-form-cell { position: relative; padding: 12px; border: 1px solid #e2e8f0; border-radius: 6px; cursor: pointer; }
.schema-form-cell.selected { border-color: #2563eb; background: #f8fbff; }
.schema-form-drop { grid-column: 1 / -1; padding: 18px; border: 1px dashed #cbd5e1; border-radius: 6px; text-align: center; color: #64748b; font-size: 12px; }
.schema-form-cell :deep(.ant-form-item) { margin-bottom: 0; }
@media (max-width: 600px) { .schema-form-grid { grid-template-columns: 1fr !important; } .schema-form-cell { grid-column: span 1 !important; } }
</style>
