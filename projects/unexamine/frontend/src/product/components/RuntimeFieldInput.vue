<script setup lang="ts">
import { ScanOutlined } from '@ant-design/icons-vue'
import { computed, ref } from 'vue'
import type { RuntimeField } from '../types'
import RichTextEditor from './RichTextEditor.vue'
import RuntimeAttachmentFieldInput from './RuntimeAttachmentFieldInput.vue'
import StructuredValueEditor from './StructuredValueEditor.vue'
import { emptyJsonValue, newJsonSchema, type JsonSchema } from '../structured-content'
import { dateTimeInputChange, dateTimeInputValue } from '../date-time-input'
import { moneyConfiguration, moneyPrecisionError } from '../field-capabilities'

type Choice = { value: string | number; label: string; description?: string }
type CascadeChoice = { value: number; label: string; children?: CascadeChoice[] }
type ChildRow = { rowKey: string; fields: Record<string, unknown> }
const props = defineProps<{
  field: Pick<RuntimeField, 'fieldType' | 'configJson' | 'required'>; modelValue?: unknown; preserveExisting?: boolean
  options?: Choice[]; childFields?: RuntimeField[]; childOptions?: Record<string, Choice[]>
  cascadeOptions?: CascadeChoice[]; childCascades?: Record<string, CascadeChoice[]>
  token?: string
}>()
const emit = defineEmits<{ 'update:modelValue': [value: unknown]; 'reference-search': [field: RuntimeField, search: string] }>()
const scanning = ref(false)
const scanError = ref('')

/** 手机拍摄或选择码图后在本地识别，识别结果仍作为普通字段值提交。 */
async function scanCode(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || scanning.value) return
  scanning.value = true
  scanError.value = ''
  const url = URL.createObjectURL(file)
  try {
    const { BrowserMultiFormatReader } = await import('@zxing/browser')
    const result = await new BrowserMultiFormatReader().decodeFromImageUrl(url)
    const value = result.getText().trim()
    if (!value) throw new Error('空码')
    emit('update:modelValue', value)
  } catch {
    scanError.value = '未识别到条码或二维码，请重新拍摄、选择清晰图片或手动输入。'
  } finally {
    URL.revokeObjectURL(url)
    scanning.value = false
  }
}

const configuration = computed(() => {
  try { return JSON.parse(props.field.configJson || '{}') as { placeholder?: string; maxLength?: number; richTextMode?: string; jsonSchema?: JsonSchema; multiple?: boolean; allowIntermediate?: boolean; maxRows?: number; options?: Array<{ code?: string; value?: string; label?: string; status?: string; disabled?: boolean }> } }
  catch { return {} }
})
const selectionOptions = computed(() => props.options || (configuration.value.options || []).filter(option => option.status !== 'DISABLED' && !option.disabled).flatMap(option => {
  const value = option.value || option.code
  return value ? [{ value, label: option.label || value }] : []
}))
const placeholder = computed(() => configuration.value.placeholder || ({
  PHONE: '请输入电话号码', EMAIL: 'name@example.com', URL: 'https://example.com', ID_CARD: '请输入身份证号',
} as Record<string, string>)[props.field.fieldType])
const maxLength = computed(() => Number.isInteger(configuration.value.maxLength) ? configuration.value.maxLength : undefined)
const inputType = computed(() => ({ EMAIL: 'email', URL: 'url' } as Record<string, string>)[props.field.fieldType] || 'text')
const inputMode = computed(() => ({ PHONE: 'tel', EMAIL: 'email', URL: 'url' } as Record<string, string>)[props.field.fieldType])
const dateTimeValue = computed(() => dateTimeInputValue(props.modelValue))
const timeValue = computed(() => typeof props.modelValue === 'string' && /^\d{2}:\d{2}$/.test(props.modelValue)
  ? `${props.modelValue}:00` : props.modelValue)
const timeRangeValue = computed(() => Array.isArray(props.modelValue)
  ? props.modelValue.map(value => typeof value === 'string' && /^\d{2}:\d{2}$/.test(value) ? `${value}:00` : value) : undefined)
const money = computed(() => moneyConfiguration(props.field.configJson))
const moneyError = computed(() => moneyPrecisionError(props.modelValue, money.value.precision))
const numericValue = computed(() => {
  if (props.modelValue === undefined || props.modelValue === null || props.modelValue === '') return undefined
  const value = Number(props.modelValue)
  return Number.isFinite(value) ? value : undefined
})
const placeValue = computed<Record<string, unknown>>(() => props.modelValue && typeof props.modelValue === 'object'
  && !Array.isArray(props.modelValue) ? props.modelValue as Record<string, unknown> : {})
function changePlace(key: string, value: unknown) {
  const next = { ...placeValue.value, [key]: value }
  // 未填的各项不占用业务值，坐标 0 仍是有效输入。
  emit('update:modelValue', Object.values(next).some(part => part !== undefined && part !== null && part !== '') ? next : undefined)
}
const childRows = computed<ChildRow[]>(() => Array.isArray(props.modelValue)
  ? props.modelValue.filter((row): row is ChildRow => Boolean(row && typeof row === 'object' && !Array.isArray(row)
      && 'fields' in row && typeof row.fields === 'object' && row.fields !== null && !Array.isArray(row.fields))) : [])
function addChildRow() {
  emit('update:modelValue', [...childRows.value, { rowKey: crypto.randomUUID(), fields: {} }])
}
function removeChildRow(index: number) {
  emit('update:modelValue', childRows.value.filter((_, rowIndex) => rowIndex !== index))
}
function updateChildField(index: number, code: string, value: unknown) {
  emit('update:modelValue', childRows.value.map((row, rowIndex) => rowIndex === index
    ? { ...row, fields: { ...row.fields, [code]: value } } : row))
}
function changeDateTime(value: unknown) {
  const changed = dateTimeInputChange(props.modelValue, value)
  // 控件同秒事件不是实际修改，避免触发上层草稿改写旧表达式或丢失原精度。
  if (changed !== props.modelValue) emit('update:modelValue', changed)
}
function changeTemporalRange(value: unknown) {
  // 范围控件清空时不提交半个范围，服务端只接收完整的起止数组。
  emit('update:modelValue', Array.isArray(value) && value.length === 2 && value.every(item => typeof item === 'string' && item)
    ? value : undefined)
}
</script>

<template>
  <div v-if="field.fieldType === 'SUBTABLE'" class="subtable-input">
    <a-empty v-if="!childFields?.length" description="请先在模块配置中添加子表列并发布" />
    <template v-else>
      <div v-for="(row, index) in childRows" :key="row.rowKey || index" class="subtable-row">
        <div class="subtable-row__heading"><strong>明细 {{ index + 1 }}</strong><a-button size="small" danger @click="removeChildRow(index)">删除此行</a-button></div>
        <div v-for="column in childFields" :key="column.id" class="subtable-cell">
          <label>{{ column.name }}<span v-if="column.required"> *</span></label>
          <RuntimeFieldInput :field="column" :model-value="row.fields[column.code]" :preserve-existing="preserveExisting"
            :options="childOptions?.[column.code]" :cascade-options="childCascades?.[column.code]" :token="token" @update:model-value="updateChildField(index, column.code, $event)"
            @reference-search="(selected, search) => emit('reference-search', selected, search)" />
        </div>
      </div>
      <a-button :disabled="childRows.length >= (configuration.maxRows || 100)" @click="addChildRow">添加明细行</a-button>
    </template>
  </div>
  <div v-else-if="field.fieldType === 'SECRET'">
    <!-- 不绑定读取值；空输入表示保留，只有用户明确清空才提交 null。 -->
    <a-input-password :value="typeof modelValue === 'string' ? modelValue : ''" :visibility-toggle="false" autocomplete="new-password" :maxlength="maxLength" :placeholder="placeholder || (preserveExisting ? '输入新密码或密钥；留空保留原值' : '输入密码或密钥')" @update:value="emit('update:modelValue', $event === '' ? undefined : $event)" />
    <a-button v-if="preserveExisting && !field.required" type="link" size="small" @click="emit('update:modelValue', modelValue === null ? undefined : null)">{{ modelValue === null ? '取消清空' : '清空原值' }}</a-button>
    <small v-if="modelValue === null">保存后清空原值</small>
  </div>
  <RuntimeAttachmentFieldInput v-else-if="['ATTACHMENT', 'IMAGE', 'FILE', 'FILE_GROUP'].includes(field.fieldType) && token" :field="field" :model-value="modelValue as number | number[]" :token="token" @update:model-value="emit('update:modelValue', $event)" />
  <RichTextEditor v-else-if="field.fieldType === 'RICH_TEXT'" :model-value="modelValue" :mode="configuration.richTextMode" :placeholder="placeholder" :max-length="maxLength" @update:model-value="emit('update:modelValue', $event)" />
  <div v-else-if="field.fieldType === 'JSON'">
    <a-button v-if="!field.required && modelValue === undefined" @click="emit('update:modelValue', emptyJsonValue(configuration.jsonSchema || newJsonSchema()))">填写内容</a-button>
    <template v-else><StructuredValueEditor :schema="configuration.jsonSchema || newJsonSchema()" :model-value="modelValue" @update:model-value="emit('update:modelValue', $event)" /><a-button v-if="!field.required" type="link" size="small" @click="emit('update:modelValue', undefined)">清空内容</a-button></template>
  </div>
  <a-textarea v-else-if="field.fieldType === 'MULTILINE_TEXT'" :value="modelValue as string" :rows="4" :placeholder="placeholder" :maxlength="maxLength" :show-count="Boolean(maxLength)" @update:value="emit('update:modelValue', $event)" />
  <div v-else-if="field.fieldType === 'MONEY'" class="money-input">
    <div class="money-input__control"><span v-if="money.currency">{{ money.currency }}</span><a-input-number :value="modelValue as string | number" string-mode style="width:100%" :placeholder="placeholder || `金额，最多 ${money.precision} 位小数`" @update:value="emit('update:modelValue', $event)" /></div>
    <small v-if="moneyError" class="money-input__error">{{ moneyError }}</small>
  </div>
  <div v-else-if="field.fieldType === 'ADDRESS'" class="place-input">
    <div class="place-input__regions">
      <a-input :value="placeValue.province as string" placeholder="省 / 直辖市" :maxlength="80" @update:value="changePlace('province', $event)" />
      <a-input :value="placeValue.city as string" placeholder="城市" :maxlength="80" @update:value="changePlace('city', $event)" />
      <a-input :value="placeValue.district as string" placeholder="区 / 县" :maxlength="80" @update:value="changePlace('district', $event)" />
    </div>
    <a-input :value="placeValue.detail as string" placeholder="详细地址（必填）" :maxlength="500" @update:value="changePlace('detail', $event)" />
    <a-button v-if="!field.required && modelValue !== undefined" type="link" size="small" @click="emit('update:modelValue', undefined)">清空地址</a-button>
  </div>
  <div v-else-if="field.fieldType === 'LOCATION'" class="place-input">
    <div class="place-input__regions">
      <a-input-number :value="placeValue.latitude as number" :min="-90" :max="90" :precision="6" style="width:100%" placeholder="纬度 -90～90" @update:value="changePlace('latitude', $event)" />
      <a-input-number :value="placeValue.longitude as number" :min="-180" :max="180" :precision="6" style="width:100%" placeholder="经度 -180～180" @update:value="changePlace('longitude', $event)" />
    </div>
    <a-input :value="placeValue.name as string" placeholder="位置名称（可选）" :maxlength="120" @update:value="changePlace('name', $event)" />
    <a-button v-if="!field.required && modelValue !== undefined" type="link" size="small" @click="emit('update:modelValue', undefined)">清空位置</a-button>
  </div>
  <a-input-number v-else-if="field.fieldType === 'NUMBER'" :value="modelValue as number" style="width:100%" :placeholder="placeholder" @update:value="emit('update:modelValue', $event)" />
  <div v-else-if="field.fieldType === 'PERCENT'" class="percent-input">
    <a-input-number :value="modelValue as number" :min="0" :max="100" style="width:100%" :placeholder="placeholder || '输入 0 到 100'" @update:value="emit('update:modelValue', $event)" />
    <span>%</span>
  </div>
  <div v-else-if="field.fieldType === 'RATING'" class="rating-input">
    <!-- 评分保存为 0～5 数值；0 分是有效值，非必填字段单独提供清空操作。 -->
    <a-rate :value="numericValue" :count="5" allow-half allow-clear @update:value="emit('update:modelValue', $event)" />
    <span>{{ numericValue === undefined ? '未评分' : `${numericValue} / 5` }}</span>
    <a-button v-if="!field.required && numericValue !== undefined" type="link" size="small" @click="emit('update:modelValue', undefined)">清空</a-button>
  </div>
  <div v-else-if="field.fieldType === 'PROGRESS'" class="progress-input">
    <div class="progress-input__control"><a-input-number :value="numericValue" :min="0" :max="100" :precision="0" style="width:100%" :placeholder="placeholder || '输入 0 到 100'" @update:value="emit('update:modelValue', $event)" /><span>%</span></div>
    <a-progress v-if="numericValue !== undefined" :percent="numericValue" :show-info="false" size="small" />
  </div>
  <a-select v-else-if="['SINGLE_SELECT', 'STATUS'].includes(field.fieldType)" :value="modelValue as string" :options="selectionOptions" allow-clear @update:value="emit('update:modelValue', $event)" />
  <a-select v-else-if="['MULTI_SELECT', 'TAG'].includes(field.fieldType)" :value="modelValue as string[]" :options="selectionOptions" mode="multiple" allow-clear :placeholder="field.fieldType === 'TAG' ? '选择标签' : undefined" @update:value="emit('update:modelValue', $event)" />
  <a-select v-else-if="field.fieldType === 'REFERENCE'" :value="modelValue as number | number[]" :mode="configuration.multiple ? 'multiple' : undefined" :options="options" show-search allow-clear :filter-option="false" :placeholder="configuration.multiple ? '搜索并选择多条业务记录' : '搜索并选择业务记录'" @update:value="emit('update:modelValue', $event)" @search="(search: string) => emit('reference-search', field as RuntimeField, search)" />
  <a-select v-else-if="['MEMBER', 'DEPARTMENT'].includes(field.fieldType)" :value="modelValue as number" :options="options || []" show-search allow-clear option-filter-prop="label" :placeholder="field.fieldType === 'MEMBER' ? '选择成员' : '选择部门'" @update:value="emit('update:modelValue', $event)" />
  <a-cascader v-else-if="field.fieldType === 'CASCADE'" :value="modelValue as number[]" :options="cascadeOptions || []" :change-on-select="Boolean(configuration.allowIntermediate)" allow-clear show-search @update:value="emit('update:modelValue', $event)" />
  <a-date-picker v-else-if="field.fieldType === 'DATE'" :value="modelValue as string" value-format="YYYY-MM-DD" style="width:100%" :placeholder="placeholder || '选择日期'" @update:value="emit('update:modelValue', $event)" />
  <a-range-picker v-else-if="field.fieldType === 'DATE_RANGE'" :value="modelValue as string[]" value-format="YYYY-MM-DD" style="width:100%" :placeholder="['开始日期', '结束日期']" @update:value="changeTemporalRange" />
  <div v-else-if="field.fieldType === 'DATETIME'">
    <a-date-picker :value="dateTimeValue" show-time value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" :placeholder="placeholder || '选择日期和时间'" @update:value="changeDateTime" />
    <small>北京时间（UTC+08:00）</small>
  </div>
  <a-time-picker v-else-if="field.fieldType === 'TIME'" :value="timeValue as string" value-format="HH:mm:ss" format="HH:mm:ss" style="width:100%" :placeholder="placeholder || '选择时间'" @update:value="emit('update:modelValue', $event || undefined)" />
  <a-time-range-picker v-else-if="field.fieldType === 'TIME_RANGE'" :value="timeRangeValue as string[]" value-format="HH:mm:ss" format="HH:mm:ss" style="width:100%" :placeholder="['开始时间', '结束时间']" @update:value="changeTemporalRange" />
  <a-switch v-else-if="field.fieldType === 'BOOLEAN'" :checked="Boolean(modelValue)" checked-children="是" un-checked-children="否" @update:checked="emit('update:modelValue', $event)" />
  <div v-else-if="['BARCODE', 'QRCODE'].includes(field.fieldType)" class="code-input">
    <a-input :value="modelValue as string" :maxlength="maxLength" placeholder="扫码或手动输入" @update:value="emit('update:modelValue', $event)" />
    <label class="ant-btn ant-btn-default" :class="{ disabled: scanning }"><ScanOutlined />{{ scanning ? '正在识别' : '扫码录入' }}
      <input class="visually-hidden" type="file" accept="image/*" capture="environment" :disabled="scanning" @change="scanCode" />
    </label>
    <small v-if="scanError" class="code-input__error">{{ scanError }}</small>
  </div>
  <a-input v-else :value="modelValue as string" :type="inputType" :inputmode="inputMode" :maxlength="maxLength" :placeholder="placeholder" :autocomplete="field.fieldType === 'ID_CARD' ? 'off' : undefined" @update:value="emit('update:modelValue', $event)" />
</template>

<style scoped>
.subtable-input { display: grid; gap: 10px; }
.subtable-row { padding: 12px; border: 1px solid #dbe4ef; border-radius: 8px; background: #f8fafc; }
.subtable-row__heading { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 8px; }
.subtable-cell { margin-top: 8px; }
.subtable-cell label { display: block; margin-bottom: 4px; color: #334155; }
.subtable-cell label span { color: #dc2626; }
.money-input__control { display: flex; align-items: center; gap: 8px; }
.money-input__control > span { color: #475569; white-space: nowrap; }
.money-input__control :deep(.ant-input-number) { flex: 1; }
.money-input__error { display: block; margin-top: 4px; color: #dc2626; }
.percent-input { display: flex; align-items: center; gap: 8px; }
.percent-input > span { color: #475569; }
.rating-input { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.rating-input > span { color: #475569; }
.progress-input__control { display: flex; align-items: center; gap: 8px; }
.progress-input__control > span { color: #475569; }
.progress-input :deep(.ant-progress) { margin-bottom: 0; }
.place-input { display: grid; gap: 8px; }
.place-input__regions { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
.code-input { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.code-input > :first-child { flex: 1 1 180px; }
.code-input__error { flex-basis: 100%; color: #dc2626; }
@media (max-width: 640px) { .place-input__regions { grid-template-columns: 1fr; } }
</style>
