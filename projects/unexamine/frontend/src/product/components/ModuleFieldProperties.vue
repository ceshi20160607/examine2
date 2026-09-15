<script setup lang="ts">
import { builtInTextFormat, defaultEditor, fieldTypes, supportsTextConstraints, supportsUniqueValue, supportsFieldSearch } from '../field-capabilities'
import StructuredSchemaEditor from './StructuredSchemaEditor.vue'
import StructuredValueEditor from './StructuredValueEditor.vue'
import RichTextEditor from './RichTextEditor.vue'
import { emptyJsonValue, type JsonSchema } from '../structured-content'
export interface FieldConfigurationForm {
  code: string; name: string; fieldType: string; required: boolean; uniqueValue: boolean; searchable: boolean
  dictionaryId?: number; referenceModuleId?: number; sortOrder: number; status: string; optionsText: string
  defaultValue?: unknown; placeholder: string; minLength?: number; maxLength?: number
  patternMode: string; customPattern: string; richTextMode: string; jsonSchema: JsonSchema
  currency: string; precision: number; maxDepth: number; allowIntermediate: boolean; saveMode: string
  sequenceCode: string; expression: string; modelCode: string; humanConfirmation: boolean; sensitive: boolean; readOnly: boolean; version: number
}
defineProps<{
  fieldForm: FieldConfigurationForm; editing?: boolean; fieldConfigurationIssue: string
  dictionaryOptions: Array<{ value: number; label: string }>; referenceModuleOptions: Array<{ value: number; label: string }>
  availableAiModels: Array<{ code: string; name: string; provider: string }>
}>()
const patternModeOptions = [
  { value: 'NONE', label: '不限附加格式' }, { value: 'DIGITS', label: '仅数字' },
  { value: 'LETTERS', label: '仅英文字母' }, { value: 'ALPHANUMERIC', label: '英文字母和数字' },
  { value: 'CHINESE', label: '仅中文' }, { value: 'CUSTOM', label: '自定义正则表达式' },
]
</script>
<template>
      <a-form layout="vertical">
        <a-form-item label="字段类型"><a-select v-if="!editing" v-model:value="fieldForm.fieldType" show-search :options="fieldTypes.map(item => ({ value: item.type, label: `${item.name} · ${item.hint}` }))" /><a-input v-else :value="fieldTypes.find((item) => item.type === fieldForm.fieldType)?.name" disabled /></a-form-item>
        <a-form-item label="字段名称" required><a-input v-model:value="fieldForm.name" placeholder="例如：客户名称、联系电话、跟进结果" /></a-form-item>
        <div class="form-grid"><a-form-item label="排序"><a-input-number v-model:value="fieldForm.sortOrder" :min="0" /></a-form-item>
          <a-form-item label="是否必填"><a-switch v-model:checked="fieldForm.required" /></a-form-item></div>
        <div class="form-grid"><a-form-item label="可搜索"><a-switch v-model:checked="fieldForm.searchable" :disabled="!supportsFieldSearch(fieldForm.fieldType)" /></a-form-item>
          <a-form-item label="唯一值" extra="在当前组织和模块内约束非空值；由数据库并发保证。"><a-switch v-model:checked="fieldForm.uniqueValue" :disabled="!supportsUniqueValue(fieldForm.fieldType)" /></a-form-item></div>
        <a-form-item label="提示文案"><a-input v-model:value="fieldForm.placeholder" placeholder="页面输入提示" /></a-form-item>
        <template v-if="supportsTextConstraints(fieldForm.fieldType)">
          <a-alert v-if="builtInTextFormat(fieldForm.fieldType)" type="info" show-icon class="section-alert" :message="builtInTextFormat(fieldForm.fieldType)" />
          <div class="form-grid"><a-form-item label="最短长度"><a-input-number v-model:value="fieldForm.minLength" :min="0" :max="100000" placeholder="不限" style="width:100%" /></a-form-item>
            <a-form-item label="最长长度"><a-input-number v-model:value="fieldForm.maxLength" :min="1" :max="100000" placeholder="不限" style="width:100%" /></a-form-item></div>
          <a-form-item v-if="fieldForm.fieldType !== 'RICH_TEXT'" label="附加格式规则" extra="常用格式直接选择；只有特殊业务规则才需要自定义正则表达式。"><a-select v-model:value="fieldForm.patternMode" :options="patternModeOptions" /></a-form-item>
          <a-form-item v-if="fieldForm.patternMode === 'CUSTOM'" label="自定义正则表达式" required extra="按完整字段值校验，例如 ^VIP-[0-9]{6}$"><a-input v-model:value="fieldForm.customPattern" placeholder="输入完整匹配表达式" /></a-form-item>
        </template>
        <a-form-item v-if="fieldForm.fieldType === 'RICH_TEXT'" label="格式工具栏"><a-radio-group v-model:value="fieldForm.richTextMode"><a-radio value="BASIC">基础格式</a-radio><a-radio value="FULL">含列表、标题和链接</a-radio></a-radio-group></a-form-item>
        <a-form-item v-if="fieldForm.fieldType === 'JSON'" label="内容结构" extra="添加属性、选择类型和必填条件；支持嵌套属性组和多行列表。"><StructuredSchemaEditor v-model="fieldForm.jsonSchema" /></a-form-item>
        <a-form-item label="默认值" extra="可留空；创建记录时会自动带入并接受同一套服务端校验。">
          <RichTextEditor v-if="fieldForm.fieldType === 'RICH_TEXT'" v-model="fieldForm.defaultValue" :mode="fieldForm.richTextMode" :max-length="fieldForm.maxLength" />
          <template v-else-if="fieldForm.fieldType === 'JSON'">
            <a-button v-if="fieldForm.defaultValue === undefined" @click="fieldForm.defaultValue = emptyJsonValue(fieldForm.jsonSchema)">设置默认内容</a-button>
            <template v-else><StructuredValueEditor v-model="fieldForm.defaultValue" :schema="fieldForm.jsonSchema" /><a-button size="small" @click="fieldForm.defaultValue = undefined">不设置默认值</a-button></template>
          </template>
          <a-input-number v-else-if="defaultEditor(fieldForm.fieldType) === 'NUMBER'" v-model:value="fieldForm.defaultValue as number" style="width:100%" placeholder="输入默认数字" />
          <a-date-picker v-else-if="defaultEditor(fieldForm.fieldType) === 'DATE'" v-model:value="fieldForm.defaultValue as string" value-format="YYYY-MM-DD" style="width:100%" placeholder="选择默认日期" />
          <a-date-picker v-else-if="defaultEditor(fieldForm.fieldType) === 'DATETIME'" v-model:value="fieldForm.defaultValue as string" show-time value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" placeholder="选择默认日期和时间" />
          <a-select v-else-if="defaultEditor(fieldForm.fieldType) === 'BOOLEAN'" v-model:value="fieldForm.defaultValue" allow-clear placeholder="不设置默认值" :options="[{ value: true, label: '是' }, { value: false, label: '否' }]" />
          <a-input v-else v-model:value="fieldForm.defaultValue as string" placeholder="输入默认内容" />
        </a-form-item>
        <a-form-item v-if="editing" label="状态"><a-radio-group v-model:value="fieldForm.status"><a-radio value="ACTIVE">启用</a-radio><a-radio value="DISABLED">停用</a-radio></a-radio-group></a-form-item>
        <a-form-item v-if="['SINGLE_SELECT', 'MULTI_SELECT', 'STATUS', 'TAG'].includes(fieldForm.fieldType)" label="发布字典" extra="绑定后模块发布会检查字典是否已经发布；也可以留空使用手动选项。">
          <a-select v-model:value="fieldForm.dictionaryId" allow-clear show-search :options="dictionaryOptions" />
        </a-form-item>
        <a-form-item v-if="['SINGLE_SELECT', 'MULTI_SELECT', 'STATUS', 'TAG'].includes(fieldForm.fieldType) && !fieldForm.dictionaryId" label="手动选项" extra="每行一个，格式：值|显示名称">
          <a-textarea v-model:value="fieldForm.optionsText" :rows="7" />
        </a-form-item>
        <template v-if="fieldForm.fieldType === 'CASCADE'">
          <a-alert type="info" show-icon message="级联字段只能选择一种数据源" class="section-alert" />
          <a-form-item label="层级字典"><a-select v-model:value="fieldForm.dictionaryId" allow-clear :options="dictionaryOptions" @change="fieldForm.referenceModuleId = undefined" /></a-form-item>
          <a-form-item label="或模块数据源"><a-select v-model:value="fieldForm.referenceModuleId" allow-clear :options="referenceModuleOptions" @change="fieldForm.dictionaryId = undefined" /></a-form-item>
          <div class="form-grid"><a-form-item label="最大层级"><a-input-number v-model:value="fieldForm.maxDepth" :min="1" :max="20" /></a-form-item><a-form-item label="允许选中间层"><a-switch v-model:checked="fieldForm.allowIntermediate" /></a-form-item></div>
          <a-form-item label="保存方式"><a-radio-group v-model:value="fieldForm.saveMode"><a-radio value="FINAL">最终项</a-radio><a-radio value="PATH">完整路径</a-radio></a-radio-group></a-form-item>
        </template>
        <template v-if="fieldForm.fieldType === 'MONEY'">
          <div class="form-grid"><a-form-item label="币种" required><a-input v-model:value="fieldForm.currency" placeholder="CNY" /></a-form-item><a-form-item label="小数精度" required><a-input-number v-model:value="fieldForm.precision" :min="0" :max="6" /></a-form-item></div>
        </template>
        <a-form-item v-if="['REFERENCE', 'LOOKUP', 'SUBTABLE', 'SUMMARY', 'AGGREGATE'].includes(fieldForm.fieldType)" label="目标模块" required extra="选择要关联的业务模块，运行页展示目标记录的业务名称。">
          <a-select v-model:value="fieldForm.referenceModuleId" show-search :options="referenceModuleOptions" />
        </a-form-item>
        <a-form-item v-if="fieldForm.fieldType === 'AUTO_NUMBER'" label="编号规则" required><a-input v-model:value="fieldForm.sequenceCode" placeholder="例如：客户编号" /></a-form-item>
        <a-form-item v-if="['FORMULA', 'CALCULATION'].includes(fieldForm.fieldType)" label="表达式" required><a-textarea v-model:value="fieldForm.expression" :rows="4" placeholder="例如 amount * tax_rate" /></a-form-item>
        <template v-if="fieldForm.fieldType === 'AI_FILL'">
          <a-form-item label="授权模型" required><a-select v-model:value="fieldForm.modelCode" show-search :options="availableAiModels.map(model => ({ value: model.code, label: `${model.name} · ${model.provider}` }))" placeholder="仅显示当前系统已授权模型" /></a-form-item>
          <a-form-item label="写入控制"><a-checkbox v-model:checked="fieldForm.humanConfirmation">每次写入前必须人工确认</a-checkbox></a-form-item>
        </template>
        <a-form-item v-if="fieldForm.fieldType === 'SECRET'" label="敏感字段"><a-checkbox v-model:checked="fieldForm.sensitive">按敏感字段权限、脱敏和审计处理</a-checkbox></a-form-item>
        <a-form-item v-if="fieldForm.fieldType.startsWith('SYSTEM_')" label="系统字段"><a-checkbox v-model:checked="fieldForm.readOnly">只读，由系统维护</a-checkbox></a-form-item>
        <a-alert v-if="fieldConfigurationIssue" type="warning" show-icon :message="fieldConfigurationIssue" />
      </a-form>
</template>
