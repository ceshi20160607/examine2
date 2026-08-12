<script setup lang="ts">
import { message } from 'ant-design-vue'
import { FileText, Pencil, Plus, RefreshCw, Send, Trash2 } from 'lucide-vue-next'
import { computed, reactive, ref, watch } from 'vue'

import { ApiRequestError } from '@/services/api'
import { printTemplateApi } from '@/services/print'
import type { ConfigField } from '@/types/config'
import type { PrintCodeBlock, PrintPositionedElement, PrintTemplate } from '@/types/print'

const props = defineProps<{
  systemId: string
  moduleId: string
  fields: ConfigField[]
}>()

const protectedTypes = new Set(['IDENTITY', 'SECRET'])
const templates = ref<PrintTemplate[]>([])
const loading = ref(false)
const saving = ref(false)
const editorOpen = ref(false)
const editing = ref<PrintTemplate | null>(null)
const error = ref('')
const form = reactive({
  code: '', name: '', status: 'ENABLED', paperSize: 'A4', orientation: 'PORTRAIT',
  header: '', title: '{title}', fieldCodes: [] as string[], footer: '',
  positionedElements: [] as PrintPositionedElement[], codeBlocks: [] as PrintCodeBlock[],
})

const eligibleFields = computed(() => props.fields.filter((field) =>
  field.status === 'ENABLED' && !field.hidden && !protectedTypes.has(field.type)))
const codeSourceFields = computed(() => eligibleFields.value.filter((field) => !['RELATION', 'SUBTABLE'].includes(field.type)))

async function load() {
  if (!props.moduleId) return
  loading.value = true
  error.value = ''
  try {
    templates.value = await printTemplateApi.list(props.systemId, props.moduleId)
  } catch (cause) {
    error.value = errorMessage(cause, '打印模板加载失败。')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  Object.assign(form, {
    code: '', name: '', status: 'ENABLED', paperSize: 'A4', orientation: 'PORTRAIT',
    header: '', title: '{title}', fieldCodes: eligibleFields.value.filter((field) => field.showInDetail)
      .slice(0, 12).map((field) => field.code), footer: '', positionedElements: [], codeBlocks: [],
  })
  editorOpen.value = true
}

function openEdit(template: PrintTemplate) {
  editing.value = template
  Object.assign(form, {
    code: template.code,
    name: template.name,
    status: template.status,
    paperSize: template.paperSize,
    orientation: template.orientation,
    header: template.definition.header || '',
    title: template.definition.title,
    fieldCodes: [...template.definition.fieldCodes],
    footer: template.definition.footer,
    positionedElements: [...(template.definition.positionedElements || [])],
    codeBlocks: [...(template.definition.codeBlocks || [])],
  })
  editorOpen.value = true
}

async function save() {
  if (!form.code.trim() || !form.name.trim() || !form.title.trim() || !form.fieldCodes.length) {
    error.value = '请填写编码、名称、标题并选择至少一个字段。'
    return
  }
  saving.value = true
  error.value = ''
  try {
    const body = {
      code: form.code.trim(), name: form.name.trim(), status: form.status,
      paperSize: form.paperSize, orientation: form.orientation, title: form.title.trim(),
      fieldCodes: [...form.fieldCodes], header: form.header.trim(), footer: form.footer.trim(),
      positionedElements: form.positionedElements.map((value) => ({ ...value })),
      codeBlocks: form.codeBlocks.map((value) => ({ ...value })),
    }
    if (editing.value) {
      await printTemplateApi.update(props.systemId, props.moduleId, editing.value.templateId, {
        ...body, expectedVersion: editing.value.version,
      })
    } else {
      await printTemplateApi.create(props.systemId, props.moduleId, body)
    }
    editorOpen.value = false
    await load()
    message.success(editing.value ? '打印模板已保存' : '打印模板已创建')
  } catch (cause) {
    error.value = errorMessage(cause, '打印模板保存失败。')
  } finally {
    saving.value = false
  }
}

function addPosition(kind: PrintPositionedElement['kind'] = 'SIGNATURE') {
  form.positionedElements.push({ kind, label: kind === 'SIGNATURE' ? '签名' : kind === 'SEAL' ? '盖章' : '预印区',
    fieldCode: undefined, xMm: 20, yMm: 220, widthMm: kind === 'SEAL' ? 36 : 50, heightMm: kind === 'SEAL' ? 36 : 24 })
}

function addCode(kind: PrintCodeBlock['kind'] = 'QR') {
  form.codeBlocks.push({ kind, label: kind === 'QR' ? '二维码' : '条码', fieldCode: '{recordNo}' })
}

async function publish(template: PrintTemplate) {
  saving.value = true
  error.value = ''
  try {
    await printTemplateApi.publish(props.systemId, props.moduleId, template.templateId, template.version)
    await load()
    message.success('打印模板已发布')
  } catch (cause) {
    error.value = errorMessage(cause, '打印模板发布失败，请先发布当前模块配置。')
  } finally {
    saving.value = false
  }
}

function errorMessage(cause: unknown, fallback: string) {
  if (cause instanceof ApiRequestError) return cause.message || fallback
  return cause instanceof Error ? cause.message : fallback
}

watch(() => [props.systemId, props.moduleId] as const, () => void load(), { immediate: true })
</script>

<template>
  <section class="print-template-manager">
    <a-alert v-if="error" type="error" show-icon closable :message="error" @close="error = ''" />
    <header>
      <div><strong>打印模板</strong><span>结构化字段绑定，发布后才进入记录打印。</span></div>
      <div class="template-actions">
        <a-button :loading="loading" @click="load"><RefreshCw :size="14" />刷新</a-button>
        <a-button type="primary" :disabled="!eligibleFields.length" @click="openCreate"><Plus :size="14" />新建模板</a-button>
      </div>
    </header>
    <a-spin :spinning="loading">
      <div class="template-list">
        <article v-for="template in templates" :key="template.templateId">
          <FileText :size="20" />
          <div>
            <strong>{{ template.name }}</strong>
            <small>{{ template.code }} · {{ template.paperSize }} · {{ template.orientation }}</small>
            <span>{{ template.definition.fieldCodes.length }} 个字段 · {{ template.definition.title }}</span>
          </div>
          <a-tag :color="template.publishedVersionId ? 'green' : 'orange'">
            {{ template.publishedVersionId ? `已发布 V${template.publishedVersionNo}` : '未发布' }}
          </a-tag>
          <div class="template-actions">
            <a-button size="small" @click="openEdit(template)"><Pencil :size="13" />编辑</a-button>
            <a-button size="small" type="primary" :loading="saving" @click="publish(template)"><Send :size="13" />发布</a-button>
          </div>
        </article>
        <a-empty v-if="!templates.length" description="尚未配置打印模板" />
      </div>
    </a-spin>

    <a-modal v-model:open="editorOpen" width="860px" :title="editing ? '编辑打印模板' : '新建打印模板'" :confirm-loading="saving" @ok="save">
      <a-form layout="vertical">
        <div class="template-form-grid">
          <a-form-item label="模板编码" required><a-input v-model:value="form.code" :disabled="Boolean(editing)" placeholder="contract_print" /></a-form-item>
          <a-form-item label="模板名称" required><a-input v-model:value="form.name" placeholder="合同打印" /></a-form-item>
          <a-form-item label="状态"><a-select v-model:value="form.status"><a-select-option value="ENABLED">启用</a-select-option><a-select-option value="DISABLED">停用</a-select-option><a-select-option value="ARCHIVED">归档</a-select-option></a-select></a-form-item>
          <a-form-item label="纸张"><a-select v-model:value="form.paperSize"><a-select-option value="A4">A4</a-select-option><a-select-option value="A5">A5</a-select-option></a-select></a-form-item>
          <a-form-item label="方向"><a-select v-model:value="form.orientation"><a-select-option value="PORTRAIT">纵向</a-select-option><a-select-option value="LANDSCAPE">横向</a-select-option></a-select></a-form-item>
        </div>
        <a-form-item label="页眉" extra="支持 {title}、{recordNo} 与 {recordId} 占位符"><a-input v-model:value="form.header" :maxlength="300" /></a-form-item>
        <a-form-item label="标题" required extra="支持 {title} 与 {recordNo} 占位符"><a-input v-model:value="form.title" /></a-form-item>
        <a-form-item label="打印字段/关联/明细" required extra="关联字段打印显示值，子表字段打印明细表格"><a-select v-model:value="form.fieldCodes" mode="multiple" :max-tag-count="6" placeholder="选择并排序字段"><a-select-option v-for="field in eligibleFields" :key="field.code" :value="field.code">{{ field.name }} · {{ field.type }}</a-select-option></a-select></a-form-item>
        <a-form-item label="签名、印章与预印定位">
          <div class="composition-toolbar"><a-button size="small" @click="addPosition('SIGNATURE')">添加签名区</a-button><a-button size="small" @click="addPosition('SEAL')">添加印章区</a-button><a-button size="small" @click="addPosition('PREPRINT')">添加预印区</a-button></div>
          <div v-for="(item, index) in form.positionedElements" :key="index" class="position-row">
            <a-select v-model:value="item.kind"><a-select-option value="SIGNATURE">签名</a-select-option><a-select-option value="SEAL">印章</a-select-option><a-select-option value="PREPRINT">预印</a-select-option></a-select>
            <a-input v-model:value="item.label" placeholder="区域名称" />
            <a-select v-model:value="item.fieldCode" allow-clear placeholder="绑定字段"><a-select-option v-for="field in codeSourceFields" :key="field.code" :value="field.code">{{ field.name }}</a-select-option></a-select>
            <a-input-number v-model:value="item.xMm" :min="0" :max="290" addon-after="x mm" />
            <a-input-number v-model:value="item.yMm" :min="0" :max="410" addon-after="y mm" />
            <a-button danger :aria-label="`删除定位${index + 1}`" @click="form.positionedElements.splice(index, 1)"><Trash2 :size="14" /></a-button>
          </div>
        </a-form-item>
        <a-form-item label="二维码/条码">
          <div class="composition-toolbar"><a-button size="small" @click="addCode('QR')">添加二维码</a-button><a-button size="small" @click="addCode('BARCODE')">添加条码</a-button></div>
          <div v-for="(item, index) in form.codeBlocks" :key="index" class="code-row">
            <a-select v-model:value="item.kind"><a-select-option value="QR">二维码</a-select-option><a-select-option value="BARCODE">条码</a-select-option></a-select>
            <a-input v-model:value="item.label" placeholder="标签" />
            <a-select v-model:value="item.fieldCode"><a-select-option value="{recordNo}">记录编号</a-select-option><a-select-option value="{recordId}">记录 ID</a-select-option><a-select-option v-for="field in codeSourceFields" :key="field.code" :value="field.code">{{ field.name }}</a-select-option></a-select>
            <a-button danger :aria-label="`删除编码${index + 1}`" @click="form.codeBlocks.splice(index, 1)"><Trash2 :size="14" /></a-button>
          </div>
        </a-form-item>
        <a-form-item label="页脚" extra="PDF 自动分页并附页码"><a-textarea v-model:value="form.footer" :rows="2" :maxlength="300" /></a-form-item>
      </a-form>
    </a-modal>
  </section>
</template>

<style scoped>
.print-template-manager{display:grid;gap:12px}.print-template-manager>header{display:flex;align-items:flex-start;justify-content:space-between;gap:16px}.print-template-manager>header>div:first-child{display:grid;gap:3px}.print-template-manager>header span,.template-list small,.template-list article>div>span{color:#6d7a83;font-size:12px}.template-actions{display:flex;align-items:center;gap:7px}.template-list{display:grid;gap:8px}.template-list article{display:grid;grid-template-columns:auto minmax(0,1fr) auto auto;align-items:center;gap:12px;padding:12px;border:1px solid #dde5e9;background:#fff}.template-list article>div:nth-child(2){display:grid;gap:2px}.template-form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 12px}
.composition-toolbar{display:flex;gap:7px;margin-bottom:7px}.position-row{display:grid;grid-template-columns:95px minmax(100px,1fr) minmax(120px,1fr) 110px 110px 34px;gap:6px;margin-bottom:6px}.code-row{display:grid;grid-template-columns:100px minmax(120px,1fr) minmax(160px,1fr) 34px;gap:6px;margin-bottom:6px}.position-row>.ant-btn,.code-row>.ant-btn{width:34px;padding:0}
</style>
