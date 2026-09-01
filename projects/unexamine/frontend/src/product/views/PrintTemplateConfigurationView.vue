<script setup lang="ts">
import { FilePdfOutlined, PlusOutlined, SaveOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { api, ApiError } from '../api'
import { cyclePrintField, printFieldMode, printPreviewMatchesDraft } from '../print'
import { systemTokens } from '../session'
import type { PrintAdminOverview, PrintFieldOption, PrintPreview, PrintTemplateDefinition } from '../types'

const token = computed(() => systemTokens.value?.accessToken || '')
const loading = ref(false)
const saving = ref(false)
const overview = ref<PrintAdminOverview>()
const selectedId = ref<number>()
const preview = ref<PrintPreview>()
const sampleRecordId = ref<number>()
const form = reactive({
  moduleId: undefined as number | undefined, code: '', name: '', pageSize: 'A4' as 'A4' | 'A5',
  orientation: 'PORTRAIT' as 'PORTRAIT' | 'LANDSCAPE', header: '', footer: '', signatureLabel: '',
  fieldCodes: [] as string[], detailFieldCodes: [] as string[], rowsPerPage: 10, expectedVersion: undefined as number | undefined,
})

const moduleOption = computed(() => overview.value?.modules.find(item => item.id === form.moduleId))
const moduleFields = computed(() => moduleOption.value?.fields || [])
const currentTemplate = computed(() => overview.value?.templates.find(item => item.id === selectedId.value))

function readable(reason: unknown) {
  return reason instanceof ApiError ? `${reason.message}（${reason.code}）` : reason instanceof Error ? reason.message : '操作失败'
}

async function load(preferredId?: number) {
  loading.value = true
  try {
    overview.value = await api<PrintAdminOverview>('/api/print/admin', {}, token.value)
    if (preferredId) {
      const item = overview.value.templates.find(template => template.id === preferredId)
      if (item) fill(item)
    } else if (!selectedId.value && overview.value.templates[0]) fill(overview.value.templates[0])
    else if (!form.moduleId) form.moduleId = overview.value.modules[0]?.id
  } catch (reason) { message.error(readable(reason)) } finally { loading.value = false }
}

function reset() {
  selectedId.value = undefined
  preview.value = undefined
  Object.assign(form, { moduleId: overview.value?.modules[0]?.id, code: '', name: '', pageSize: 'A4',
    orientation: 'PORTRAIT', header: '', footer: '', signatureLabel: '', fieldCodes: [], detailFieldCodes: [],
    rowsPerPage: 10, expectedVersion: undefined })
}

function fill(template: PrintTemplateDefinition) {
  selectedId.value = template.id
  preview.value = undefined
  Object.assign(form, { moduleId: template.moduleId, code: template.code, name: template.name,
    pageSize: template.pageSize, orientation: template.orientation, header: template.layout.header || '',
    footer: template.layout.footer || '', signatureLabel: template.layout.signatureLabel || '',
    fieldCodes: [...template.layout.fieldCodes], detailFieldCodes: [...template.layout.detailFieldCodes],
    rowsPerPage: template.layout.rowsPerPage, expectedVersion: template.version })
}

function fieldMode(field: PrintFieldOption) {
  return printFieldMode(field.code, form.fieldCodes, form.detailFieldCodes)
}

function cycleField(field: PrintFieldOption) {
  const next = cyclePrintField(field.code, form.fieldCodes, form.detailFieldCodes)
  form.fieldCodes = next.fieldCodes
  form.detailFieldCodes = next.detailFieldCodes
  preview.value = undefined
}

function body() {
  return { moduleId: form.moduleId, code: form.code.trim(), name: form.name.trim(), pageSize: form.pageSize,
    orientation: form.orientation, expectedVersion: form.expectedVersion,
    layout: { header: form.header.trim() || null, footer: form.footer.trim() || null,
      signatureLabel: form.signatureLabel.trim() || null, fieldCodes: form.fieldCodes,
      detailFieldCodes: form.detailFieldCodes, rowsPerPage: form.rowsPerPage } }
}

async function save() {
  if (!form.moduleId || !form.code.trim() || !form.name.trim() || !(form.fieldCodes.length + form.detailFieldCodes.length)) {
    message.warning('请选择模块、填写编码名称，并至少加入一个字段')
    return
  }
  saving.value = true
  try {
    const path = selectedId.value ? `/api/print/admin/templates/${selectedId.value}` : '/api/print/admin/templates'
    const saved = await api<PrintTemplateDefinition>(path, {
      method: selectedId.value ? 'PUT' : 'POST', body: JSON.stringify(body()),
    }, token.value)
    message.success(`打印模板草稿 r${saved.draftRevision} 已保存`)
    await load(saved.id)
  } catch (reason) { message.error(readable(reason)) } finally { saving.value = false }
}

async function renderPreview() {
  if (!selectedId.value || !sampleRecordId.value) { message.warning('请先保存模板并填写有权访问的样例记录 ID'); return }
  loading.value = true
  try {
    preview.value = await api<PrintPreview>(`/api/print/admin/templates/${selectedId.value}/preview`, {
      method: 'POST', body: JSON.stringify({ sampleRecordId: sampleRecordId.value }),
    }, token.value)
    message.success(`已按真实记录生成 ${preview.value.pages.length} 页预览`)
  } catch (reason) { message.error(readable(reason)) } finally { loading.value = false }
}

async function publish() {
  const current = currentTemplate.value
  if (!current || !printPreviewMatchesDraft(preview.value, current.draftRevision)) {
    message.warning('请先对当前草稿执行真实记录预览')
    return
  }
  saving.value = true
  try {
    await api(`/api/print/admin/templates/${current.id}/publish`, {
      method: 'POST', body: JSON.stringify({ expectedDraftRevision: current.draftRevision }),
    }, token.value)
    message.success('打印模板已发布为不可变版本')
    await load(current.id)
  } catch (reason) { message.error(readable(reason)) } finally { saving.value = false }
}

onMounted(() => void load())
</script>

<template>
  <section class="print-designer">
    <div class="page-heading"><div><p class="eyebrow">系统后台 · 打印模板</p><h1>可视化打印设计</h1><p>字段选择、纸张预览和页眉页脚配置共同形成草稿；真实记录预览通过后才可发布。</p></div><a-button type="primary" @click="reset"><PlusOutlined />新建模板</a-button></div>
    <a-alert type="info" show-icon message="运行态只读取已发布版本" description="历史版本不可修改；打印时会重新校验记录 DETAIL 与 PRINT 权限以及 PAGE ∩ FILE 字段策略。" />
    <div class="print-template-strip panel-card">
      <button v-for="item in overview?.templates" :key="item.id" type="button" :class="{ active: selectedId === item.id }" @click="fill(item)"><strong>{{ item.name }}</strong><span>{{ item.moduleName }} · r{{ item.draftRevision }}</span><a-tag :color="item.status === 'PUBLISHED' ? 'green' : 'orange'">{{ item.status }}</a-tag></button>
      <a-empty v-if="!overview?.templates.length" :image="false" description="尚未创建打印模板" />
    </div>

    <div class="print-designer-grid">
      <aside class="panel-card print-field-palette">
        <div class="panel-title"><strong>1. 模块字段</strong><span>{{ moduleOption ? `发布 v${moduleOption.publishedVersionNumber}` : '选择模块' }}</span></div>
        <a-select v-model:value="form.moduleId" :disabled="Boolean(selectedId)" style="width:100%" :options="overview?.modules.map(item => ({ value: item.id, label: `${item.name}（${item.code}）` }))" />
        <p class="print-palette-help">点击字段依次切换：普通字段 → 明细字段 → 不使用。</p>
        <button v-for="field in moduleFields" :key="field.code" type="button" :class="['print-field-chip', `mode-${fieldMode(field)}`]" @click="cycleField(field)"><span><strong>{{ field.name }}</strong><small>{{ field.code }} · {{ field.fieldType }}</small></span><a-tag>{{ fieldMode(field) === 'field' ? '正文' : fieldMode(field) === 'detail' ? '明细' : '未使用' }}</a-tag></button>
      </aside>

      <main class="print-canvas-column">
        <div class="panel-card print-canvas-toolbar"><div><strong>2. 打印页面</strong><span>{{ form.pageSize }} · {{ form.orientation === 'PORTRAIT' ? '纵向' : '横向' }}</span></div><div><a-input-number v-model:value="sampleRecordId" :min="1" placeholder="样例记录 ID" /><a-button :loading="loading" :disabled="!selectedId" @click="renderPreview">真实记录预览</a-button></div></div>
        <div v-if="preview" class="print-page-stack">
          <article v-for="page in preview.pages" :key="page.pageNumber" :class="['print-page', `print-page--${form.orientation.toLowerCase()}`, `print-page--${form.pageSize.toLowerCase()}`]">
            <header><h2>{{ form.name }}</h2><p>{{ page.header }}</p><small>{{ page.recordNumber || '业务记录' }} · {{ page.recordTitle }}</small></header>
            <dl><div v-for="field in page.fields" :key="`${page.pageNumber}-${field.code}`" :class="{ detail: field.detail }"><dt>{{ field.detail ? '明细 · ' : '' }}{{ field.name }}</dt><dd>{{ field.value }}</dd></div></dl>
            <div class="print-signature" v-if="page.signatureLabel">{{ page.signatureLabel }}：________________</div>
            <footer><span>{{ page.footer }}</span><b>第 {{ page.pageNumber }} / {{ page.pageCount }} 页</b></footer>
          </article>
          <div class="panel-card print-preview-proof"><strong>预览哈希 {{ preview.previewHash }}</strong><span>模块版本 #{{ preview.moduleVersionId }}/v{{ preview.moduleVersionNumber }} · {{ preview.renderedAt }}</span><span>输出字段 {{ preview.visibleFieldCodes.join('、') || '无' }}；权限省略 {{ preview.omittedFieldCodes.join('、') || '无' }}</span></div>
        </div>
        <a-empty v-else description="保存草稿后，用一条有权记录生成分页预览" />
      </main>

      <aside class="panel-card print-properties">
        <div class="panel-title"><strong>3. 页面属性</strong><span>草稿配置</span></div>
        <a-form layout="vertical"><a-form-item label="模板编码" required><a-input v-model:value="form.code" :disabled="Boolean(selectedId)" placeholder="customer_summary" /></a-form-item><a-form-item label="显示名称" required><a-input v-model:value="form.name" /></a-form-item><div class="form-grid"><a-form-item label="纸张"><a-select v-model:value="form.pageSize" :options="['A4','A5'].map(value => ({ value }))" /></a-form-item><a-form-item label="方向"><a-select v-model:value="form.orientation" :options="[{ value:'PORTRAIT',label:'纵向' },{ value:'LANDSCAPE',label:'横向' }]" /></a-form-item></div><a-form-item label="每页字段行数"><a-input-number v-model:value="form.rowsPerPage" :min="4" :max="24" style="width:100%" /></a-form-item><a-form-item label="页眉"><a-input v-model:value="form.header" placeholder="公司名称 / 单据类型" /></a-form-item><a-form-item label="页脚"><a-textarea v-model:value="form.footer" :rows="2" placeholder="保密说明或联系信息" /></a-form-item><a-form-item label="签章区"><a-input v-model:value="form.signatureLabel" placeholder="审批签章" /></a-form-item></a-form>
        <div class="print-property-actions"><a-button type="primary" :loading="saving" block @click="save"><SaveOutlined />保存草稿</a-button><a-button danger :loading="saving" :disabled="!preview" block @click="publish"><FilePdfOutlined />发布不可变版本</a-button></div>
        <div v-if="currentTemplate?.versions.length" class="print-version-list"><strong>发布历史</strong><span v-for="version in currentTemplate.versions" :key="version.id" :class="{ current: version.current }">v{{ version.versionNumber }} · r{{ version.draftRevision }} · {{ version.snapshotHash.slice(0, 8) }} <b v-if="version.current">当前</b></span></div>
      </aside>
    </div>
  </section>
</template>
