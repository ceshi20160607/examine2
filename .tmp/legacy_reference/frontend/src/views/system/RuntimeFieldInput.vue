<script setup lang="ts">
import { Plus, ScanLine, Trash2 } from 'lucide-vue-next'
import { computed, ref, watch } from 'vue'

import type { RuntimeFieldCapability, RuntimeFieldValue } from '@/types/config'
import RichTextFieldInput from './RichTextFieldInput.vue'
import RuntimeFileFieldInput from './RuntimeFileFieldInput.vue'
import { scanBarcodeImage } from './mobileCapture'
import {
  buildRuntimeCascadeOptions,
  normalizeRuntimeMoneyAmount,
  normalizeRuntimeTime,
  runtimeCurrencies,
  runtimeFixedCurrency,
} from './runtimeFieldModel'

const props = defineProps<{
  field: RuntimeFieldCapability
  modelValue?: unknown
  inputId: string
  storedValue?: RuntimeFieldValue
  systemId?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: unknown]
  blur: []
  clear: []
}>()

const tagWarning = ref('')
const jsonText = ref('')
const jsonError = ref('')
const scanningBarcode = ref(false)
const barcodeScanError = ref('')
const currencies = computed(() => runtimeCurrencies(props.field))
const fixedCurrency = computed(() => runtimeFixedCurrency(props.field))
const cascadeOptions = computed(() => buildRuntimeCascadeOptions(props.field))
const barcodeSymbologies = computed(() => {
  const configured = props.field.schema.symbologies
  return Array.isArray(configured) ? configured.map(String).filter((value) => ['CODE128', 'EAN13'].includes(value)) : ['CODE128']
})
const storedHasValue = computed(() => Boolean(props.storedValue?.displayValue))
const repeatableValues = computed(() => {
  const values = Array.isArray(props.modelValue) ? props.modelValue.map((item) => String(item ?? '')) : []
  return values.length ? values : ['']
})
const statusOptions = computed(() => {
  if (props.field.type !== 'STATUS') return props.field.options
  const current = typeof props.modelValue === 'string' ? props.modelValue : ''
  const allowed = new Set<string>()
  if (current) {
    allowed.add(current)
    const transitions = Array.isArray(props.field.schema.transitions) ? props.field.schema.transitions : []
    for (const item of transitions) {
      if (item && typeof item === 'object' && String((item as Record<string, unknown>).from ?? '') === current) {
        allowed.add(String((item as Record<string, unknown>).to ?? ''))
      }
    }
  } else {
    const initial = Array.isArray(props.field.schema.initialStateIds) ? props.field.schema.initialStateIds : []
    initial.forEach((value) => allowed.add(String(value)))
  }
  return props.field.options.filter((option) => allowed.has(option.value))
})
const stringValue = computed({
  get: () => typeof props.modelValue === 'string' ? props.modelValue : '',
  set: (value: string) => emit('update:modelValue', value),
})
const numberValue = computed<number | undefined>({
  get: () => typeof props.modelValue === 'number' ? props.modelValue : undefined,
  set: (value) => emit('update:modelValue', value),
})
const booleanValue = computed({
  get: () => props.modelValue === true,
  set: (value: boolean) => emit('update:modelValue', value),
})
const arrayValue = computed<string[]>({
  get: () => Array.isArray(props.modelValue) ? props.modelValue.map(String) : [],
  set: (value) => {
    const unique = [...new Set(value.map((item) => item.trim()).filter(Boolean))]
    tagWarning.value = unique.length < value.length ? '重复值已忽略' : ''
    emit('update:modelValue', unique)
  },
})
const moneyCurrency = computed({
  get: () => {
    const current = props.modelValue && typeof props.modelValue === 'object'
      ? String((props.modelValue as Record<string, unknown>).currency ?? '') : ''
    return fixedCurrency.value || current || currencies.value[0]!
  },
  set: (currency: string) => updateMoney('currency', currency),
})
const moneyAmount = computed({
  get: () => props.modelValue && typeof props.modelValue === 'object'
    ? String((props.modelValue as Record<string, unknown>).amount ?? '') : '',
  set: (amount: string) => updateMoney('amount', amount),
})
const progressSliderValue = computed({
  get: () => typeof props.modelValue === 'number' ? props.modelValue : 0,
  set: (value: number) => emit('update:modelValue', value),
})

watch([() => props.field.type, () => props.modelValue], ([type, value]) => {
  if (type !== 'JSON') return
  const next = value === undefined || value === null ? '' : JSON.stringify(value, null, 2)
  if (jsonText.value !== next) jsonText.value = next
  jsonError.value = ''
}, { immediate: true, deep: true })

function updateMoney(key: 'amount' | 'currency', value: string) {
  emit('update:modelValue', {
    amount: moneyAmount.value,
    currency: moneyCurrency.value,
    [key]: value,
  })
}

function updateRange(index: number, value: string, time = false) {
  const next = [...arrayValue.value]
  next[index] = time ? normalizeRuntimeTime(value) : value
  emit('update:modelValue', next)
}

function updateRepeatable(index: number, value: string) {
  const next = [...repeatableValues.value]
  next[index] = value
  emit('update:modelValue', next)
}

function addRepeatable() {
  if (repeatableValues.value.length < 10) emit('update:modelValue', [...repeatableValues.value, ''])
}

function removeRepeatable(index: number) {
  const next = repeatableValues.value.filter((_, itemIndex) => itemIndex !== index)
  emit('update:modelValue', next)
  notifyCommit()
}

function commitRepeatable() {
  const values = repeatableValues.value.map((item) => item.trim()).filter(Boolean)
  const unique = [...new Set(values)]
  tagWarning.value = unique.length < values.length ? '重复值已忽略' : ''
  emit('update:modelValue', unique)
  notifyCommit()
}

function objectValue(): Record<string, unknown> {
  return props.modelValue && typeof props.modelValue === 'object' && !Array.isArray(props.modelValue)
    ? props.modelValue as Record<string, unknown> : {}
}

function objectText(key: string) {
  return String(objectValue()[key] ?? '')
}

function objectNumber(key: string): number | undefined {
  const value = objectValue()[key]
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined
}

function updateObject(key: string, value: unknown) {
  emit('update:modelValue', { ...objectValue(), [key]: value })
}

function updateBarcode(key: 'symbology' | 'payload', value: string) {
  emit('update:modelValue', {
    symbology: objectText('symbology') || barcodeSymbologies.value[0] || 'CODE128',
    payload: objectText('payload'),
    [key]: value,
  })
}

async function scanBarcode(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  scanningBarcode.value = true
  barcodeScanError.value = ''
  try {
    const result = await scanBarcodeImage(file)
    emit('update:modelValue', {
      symbology: result.symbology && barcodeSymbologies.value.includes(result.symbology)
        ? result.symbology
        : objectText('symbology') || barcodeSymbologies.value[0] || 'CODE128',
      payload: result.payload,
    })
    notifyCommit()
  } catch (cause) {
    barcodeScanError.value = cause instanceof Error ? cause.message : '条码识别失败'
  } finally {
    scanningBarcode.value = false
  }
}

function updateJson(value: string) {
  jsonText.value = value
  if (!value.trim()) {
    jsonError.value = ''
    emit('update:modelValue', undefined)
    return
  }
  try {
    const parsed = JSON.parse(value) as unknown
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error('JSON 根值必须是对象')
    jsonError.value = ''
    emit('update:modelValue', parsed)
  } catch (error) {
    jsonError.value = error instanceof Error ? error.message : 'JSON 格式无效'
  }
}

function formatJson() {
  if (jsonError.value || !jsonText.value.trim()) return
  jsonText.value = JSON.stringify(JSON.parse(jsonText.value), null, 2)
}

function commitMoney() {
  if (moneyAmount.value) updateMoney('amount', normalizeRuntimeMoneyAmount(moneyAmount.value, moneyCurrency.value))
  emit('blur')
}

function detectDuplicateTag(event: KeyboardEvent) {
  if (!['Enter', ','].includes(event.key)) return
  const candidate = (event.target as HTMLInputElement | null)?.value.trim()
  tagWarning.value = candidate && arrayValue.value.includes(candidate) ? `标签“${candidate}”已存在` : ''
}

function notifyCommit() {
  emit('blur')
}
</script>

<template>
  <RuntimeFileFieldInput
    v-if="['ATTACHMENT', 'IMAGE', 'FILE_GROUP', 'SIGNATURE'].includes(field.type)"
    :field="field" :model-value="modelValue" :input-id="inputId" :system-id="systemId"
    @update:model-value="(value) => emit('update:modelValue', value)" @blur="notifyCommit"
  />
  <div v-else-if="['PHONE', 'EMAIL'].includes(field.type)" class="repeatable-control">
    <div v-for="(value, index) in repeatableValues" :key="index" class="repeatable-row">
      <a-input
        :id="index === 0 ? inputId : undefined"
        :value="value"
        :type="field.type === 'EMAIL' ? 'email' : 'tel'"
        :inputmode="field.type === 'PHONE' ? 'tel' : 'email'"
        :aria-label="`${field.fieldName}${index + 1}`"
        @update:value="(next: string) => updateRepeatable(index, next)"
        @blur="commitRepeatable"
      />
      <a-tooltip title="删除"><a-button v-if="repeatableValues.length > 1" danger :aria-label="`删除${field.fieldName}${index + 1}`" @click="removeRepeatable(index)"><Trash2 :size="15" /></a-button></a-tooltip>
    </div>
    <a-button v-if="repeatableValues.length < 10" class="add-value" @click="addRepeatable"><Plus :size="15" />添加{{ field.type === 'PHONE' ? '号码' : '邮箱' }}</a-button>
    <span v-if="tagWarning" class="field-warning" role="status">{{ tagWarning }}</span>
  </div>
  <a-input
    v-else-if="field.type === 'TEXT'"
    :id="inputId"
    v-model:value="stringValue"
    :maxlength="Number(field.schema.maxLength ?? 4000)"
    :aria-label="field.fieldName"
    @blur="notifyCommit"
  />
  <a-input
    v-else-if="field.type === 'URL'"
    :id="inputId"
    v-model:value="stringValue"
    type="url"
    inputmode="url"
    :aria-label="field.fieldName"
    @blur="notifyCommit"
  />
  <div v-else-if="field.type === 'IDENTITY'" class="sensitive-control">
    <a-input
      :id="inputId"
      v-model:value="stringValue"
      autocomplete="off"
      :placeholder="storedHasValue && field.masked ? '留空保持当前值' : undefined"
      :aria-label="field.fieldName"
      @blur="notifyCommit"
    />
    <a-button v-if="storedHasValue" danger @click="emit('clear')"><Trash2 :size="15" />清除已保存值</a-button>
    <span v-if="storedHasValue" class="sensitive-hint">{{ props.modelValue === null ? '保存后清除' : field.masked ? '已保存且不可见' : '已保存' }}</span>
  </div>
  <div v-else-if="field.type === 'ADDRESS'" class="address-control">
    <a-input :id="inputId" :value="objectText('display')" aria-label="地址显示文本" placeholder="完整地址" @update:value="(value: string) => updateObject('display', value)" @blur="notifyCommit" />
    <div class="address-grid">
      <a-input :value="objectText('countryCode')" :maxlength="2" aria-label="国家代码" placeholder="国家代码" @update:value="(value: string) => updateObject('countryCode', value.toUpperCase())" @blur="notifyCommit" />
      <a-input :value="objectText('regionCode')" aria-label="地区代码" placeholder="地区代码" @update:value="(value: string) => updateObject('regionCode', value)" @blur="notifyCommit" />
      <a-input :value="objectText('city')" aria-label="城市" placeholder="城市" @update:value="(value: string) => updateObject('city', value)" @blur="notifyCommit" />
      <a-input :value="objectText('district')" aria-label="区县" placeholder="区县" @update:value="(value: string) => updateObject('district', value)" @blur="notifyCommit" />
      <a-input :value="objectText('postalCode')" aria-label="邮政编码" placeholder="邮政编码" @update:value="(value: string) => updateObject('postalCode', value)" @blur="notifyCommit" />
      <a-input :value="objectText('detail')" aria-label="详细地址" placeholder="详细地址" @update:value="(value: string) => updateObject('detail', value)" @blur="notifyCommit" />
    </div>
  </div>
  <div v-else-if="field.type === 'GEO'" class="geo-control">
    <a-input-number :id="inputId" :value="objectNumber('lat')" :min="-90" :max="90" :precision="8" aria-label="纬度" placeholder="纬度" @update:value="(value: number | null) => updateObject('lat', value)" @blur="notifyCommit" />
    <a-input-number :value="objectNumber('lng')" :min="-180" :max="180" :precision="8" aria-label="经度" placeholder="经度" @update:value="(value: number | null) => updateObject('lng', value)" @blur="notifyCommit" />
  </div>
  <div v-else-if="field.type === 'BARCODE'" class="barcode-control">
    <a-select :value="objectText('symbology') || barcodeSymbologies[0]" aria-label="条码制式" @change="(value: string) => updateBarcode('symbology', value)">
      <a-select-option v-for="value in barcodeSymbologies" :key="value" :value="value">{{ value }}</a-select-option>
    </a-select>
    <div class="barcode-entry">
      <a-input :id="inputId" :value="objectText('payload')" aria-label="条码内容" @update:value="(value: string) => updateBarcode('payload', value)" @blur="notifyCommit" />
      <label class="scan-button" :for="`${inputId}-scanner`" :aria-disabled="scanningBarcode">
        <ScanLine :size="15" />{{ scanningBarcode ? '识别中…' : '扫码' }}
      </label>
      <input :id="`${inputId}-scanner`" class="capture-input" type="file" accept="image/*" capture="environment" :disabled="scanningBarcode" aria-label="拍照扫描条码" @change="scanBarcode">
    </div>
    <span v-if="barcodeScanError" class="field-warning barcode-scan-error" role="alert">{{ barcodeScanError }}</span>
  </div>
  <RichTextFieldInput
    v-else-if="field.type === 'RICH_TEXT'"
    :input-id="inputId"
    :label="field.fieldName"
    :model-value="stringValue"
    @update:model-value="(value) => emit('update:modelValue', value)"
    @blur="notifyCommit"
  />
  <div v-else-if="field.type === 'JSON'" class="json-control">
    <a-textarea :id="inputId" :value="jsonText" :rows="8" class="json-editor" :aria-label="field.fieldName" @update:value="updateJson" @blur="notifyCommit" />
    <div class="json-actions"><a-button :disabled="Boolean(jsonError) || !jsonText.trim()" @click="formatJson">格式化</a-button><span v-if="jsonError" class="field-warning" role="alert">{{ jsonError }}</span></div>
  </div>
  <div v-else-if="field.type === 'SECRET'" class="sensitive-control">
    <a-input-password
      :id="inputId"
      v-model:value="stringValue"
      autocomplete="new-password"
      :placeholder="storedHasValue ? '输入新值以替换' : '输入保密值'"
      :visibility-toggle="false"
      :maxlength="4096"
      :aria-label="field.fieldName"
      @blur="notifyCommit"
    />
    <a-button v-if="storedHasValue" danger @click="emit('clear')"><Trash2 :size="15" />清除已保存值</a-button>
    <span class="sensitive-hint">{{ props.modelValue === null ? '保存后清除' : stringValue ? '保存后替换' : storedHasValue ? '已设置' : '未设置' }}</span>
  </div>
  <a-select
    v-else-if="field.type === 'STATUS'"
    :id="inputId"
    v-model:value="stringValue"
    :options="statusOptions"
    :aria-label="field.fieldName"
    placeholder="请选择状态"
    @change="notifyCommit"
  />
  <a-textarea
    v-else-if="field.type === 'TEXTAREA'"
    :id="inputId"
    v-model:value="stringValue"
    :maxlength="Number(field.schema.maxLength ?? 65535)"
    :rows="Number(field.schema.rows ?? 4)"
    :aria-label="field.fieldName"
    @blur="notifyCommit"
  />
  <a-input-number
    v-else-if="['NUMBER', 'PERCENT'].includes(field.type)"
    :id="inputId"
    v-model:value="numberValue"
    class="field-number"
    :min="field.type === 'PERCENT' ? 0 : Number(field.schema.minimum ?? Number.MIN_SAFE_INTEGER)"
    :max="field.type === 'PERCENT' ? 100 : Number(field.schema.maximum ?? Number.MAX_SAFE_INTEGER)"
    :step="Number(field.schema.step ?? (field.type === 'PERCENT' ? 0.0001 : 1))"
    :addon-after="field.type === 'PERCENT' ? '%' : undefined"
    :aria-label="field.fieldName"
    @blur="notifyCommit"
  />
  <div v-else-if="field.type === 'MONEY'" class="money-control">
    <a-select v-model:value="moneyCurrency" :disabled="Boolean(fixedCurrency)" :aria-label="`${field.fieldName}币种`" @change="notifyCommit">
      <a-select-option v-for="currency in currencies" :key="currency" :value="currency">{{ currency }}</a-select-option>
    </a-select>
    <a-input :id="inputId" v-model:value="moneyAmount" inputmode="decimal" :aria-label="`${field.fieldName}金额`" @blur="commitMoney" />
  </div>
  <input
    v-else-if="field.type === 'DATE'"
    :id="inputId"
    v-model="stringValue"
    class="native-field"
    type="date"
    :aria-label="field.fieldName"
    @blur="notifyCommit"
  >
  <input
    v-else-if="field.type === 'DATETIME'"
    :id="inputId"
    v-model="stringValue"
    class="native-field"
    type="datetime-local"
    step="1"
    :aria-label="field.fieldName"
    @blur="notifyCommit"
  >
  <div v-else-if="field.type === 'DATE_RANGE'" class="range-control">
    <input :id="inputId" class="native-field" type="date" :value="arrayValue[0] ?? ''" :aria-label="`${field.fieldName}开始`" @input="updateRange(0, ($event.target as HTMLInputElement).value)" @blur="notifyCommit">
    <span>至</span>
    <input class="native-field" type="date" :value="arrayValue[1] ?? ''" :aria-label="`${field.fieldName}结束`" @input="updateRange(1, ($event.target as HTMLInputElement).value)" @blur="notifyCommit">
  </div>
  <input
    v-else-if="field.type === 'TIME'"
    :id="inputId"
    class="native-field"
    type="time"
    step="1"
    :value="stringValue"
    :aria-label="field.fieldName"
    @input="emit('update:modelValue', normalizeRuntimeTime(($event.target as HTMLInputElement).value))"
    @blur="notifyCommit"
  >
  <div v-else-if="field.type === 'TIME_RANGE'" class="range-control">
    <input :id="inputId" class="native-field" type="time" step="1" :value="arrayValue[0] ?? ''" :aria-label="`${field.fieldName}开始`" @input="updateRange(0, ($event.target as HTMLInputElement).value, true)" @blur="notifyCommit">
    <span>至</span>
    <input class="native-field" type="time" step="1" :value="arrayValue[1] ?? ''" :aria-label="`${field.fieldName}结束`" @input="updateRange(1, ($event.target as HTMLInputElement).value, true)" @blur="notifyCommit">
  </div>
  <a-select
    v-else-if="field.type === 'MULTI_SELECT'"
    :id="inputId"
    v-model:value="arrayValue"
    mode="multiple"
    :options="field.options"
    :max-tag-count="3"
    :aria-label="field.fieldName"
    @blur="notifyCommit"
  />
  <a-cascader
    v-else-if="field.type === 'CASCADE'"
    :id="inputId"
    v-model:value="arrayValue"
    :options="cascadeOptions"
    change-on-select
    :aria-label="field.fieldName"
    @blur="notifyCommit"
  />
  <a-switch
    v-else-if="field.type === 'SWITCH'"
    :id="inputId"
    v-model:checked="booleanValue"
    checked-children="是"
    un-checked-children="否"
    :aria-label="field.fieldName"
    class="compact-switch"
    @change="notifyCommit"
  />
  <a-rate
    v-else-if="field.type === 'RATING'"
    :id="inputId"
    v-model:value="numberValue"
    :count="Number(field.schema.maxRating ?? 5)"
    :aria-label="field.fieldName"
    class="rating-control"
    @change="notifyCommit"
  />
  <div v-else-if="field.type === 'PROGRESS'" class="progress-control">
    <a-slider v-model:value="progressSliderValue" :min="0" :max="100" :step="Number(field.schema.step ?? 1)" :aria-label="`${field.fieldName}滑块`" @change-complete="notifyCommit" />
    <a-input-number :id="inputId" v-model:value="numberValue" :min="0" :max="100" :step="Number(field.schema.step ?? 1)" :aria-label="field.fieldName" @blur="notifyCommit" />
    <span>%</span>
  </div>
  <div v-else-if="field.type === 'TAG'" class="tag-control">
    <a-select
      :id="inputId"
      v-model:value="arrayValue"
      mode="tags"
      :token-separators="[',']"
      :max-tag-count="3"
      :aria-label="field.fieldName"
      @input-key-down="detectDuplicateTag"
      @blur="notifyCommit"
    />
    <span v-if="tagWarning" class="field-warning" role="status">{{ tagWarning }}</span>
  </div>
  <a-select
    v-else
    :id="inputId"
    v-model:value="stringValue"
    :options="field.options"
    :aria-label="field.fieldName"
    placeholder="请选择"
    @change="notifyCommit"
  />
</template>

<style scoped>
.field-number{width:100%}.money-control{display:grid;grid-template-columns:100px minmax(0,1fr);gap:8px}.range-control{display:grid;grid-template-columns:minmax(0,1fr) auto minmax(0,1fr);align-items:center;gap:8px}.progress-control{display:grid;grid-template-columns:minmax(120px,1fr) 96px auto;align-items:center;gap:10px}.compact-switch,.rating-control{justify-self:start}.tag-control,.repeatable-control,.address-control,.json-control,.sensitive-control{display:grid;gap:8px}.repeatable-row{display:grid;grid-template-columns:minmax(0,1fr) 34px;gap:7px}.repeatable-row>.ant-btn{width:34px;padding:0}.repeatable-control>.add-value{justify-self:start;display:inline-flex;align-items:center;gap:6px}.address-grid{display:grid;grid-template-columns:110px 130px minmax(0,1fr);gap:8px}.geo-control{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:8px}.geo-control .ant-input-number{width:100%}.barcode-control{display:grid;grid-template-columns:110px minmax(0,1fr);gap:8px}.sensitive-control>.ant-btn{justify-self:start;display:inline-flex;align-items:center;gap:6px}.sensitive-hint{color:#63717b;font-size:12px}.json-editor :deep(textarea){font:12px/1.55 ui-monospace,SFMono-Regular,Consolas,monospace}.json-actions{display:flex;align-items:center;gap:10px}.field-warning{color:#a15c08;font-size:12px;overflow-wrap:anywhere}.native-field{width:100%;height:32px;padding:4px 11px;border:1px solid #d9d9d9;background:#fff;color:#263640;font:inherit;outline:none}.native-field:focus{border-color:#4096ff;box-shadow:0 0 0 2px rgb(5 145 255 / 10%)}
.barcode-entry{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:8px}.scan-button{display:inline-flex;align-items:center;gap:5px;height:32px;padding:4px 11px;border:1px solid #91caff;border-radius:6px;background:#fff;color:#0958d9;cursor:pointer;white-space:nowrap}.scan-button[aria-disabled="true"]{cursor:not-allowed;opacity:.55}.capture-input{position:absolute;width:1px;height:1px;overflow:hidden;clip-path:inset(50%)}.barcode-scan-error{grid-column:1/-1}
@media(max-width:480px){.money-control{grid-template-columns:88px minmax(0,1fr)}.range-control{grid-template-columns:minmax(0,1fr)}.range-control>span{text-align:center}.progress-control{grid-template-columns:minmax(0,1fr) 86px auto}.address-grid{grid-template-columns:minmax(0,1fr) minmax(0,1fr)}.geo-control,.barcode-control{grid-template-columns:minmax(0,1fr)}}
</style>
