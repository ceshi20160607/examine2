<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { flowApi } from '@/services/flow'
import type {
  FlowNodeExecution,
  FlowNodeExecutionEvent,
  FlowNodeForm,
  FlowNodeFormHistory,
} from '@/types/flow'

const props = defineProps<{
  systemId: string
  instanceId: string
  canDecide: boolean
}>()

const nodeCode = ref('')
const form = ref<FlowNodeForm | null>(null)
const formHistory = ref<FlowNodeFormHistory[]>([])
const execution = ref<Pick<FlowNodeExecution, 'status' | 'version'> | null>(null)
const executionHistory = ref<FlowNodeExecutionEvent[]>([])
const edits = ref<Record<string, string>>({})
const nodeInput = ref('{}')
const busy = ref(false)
const error = ref('')
const notice = ref('')
const formKey = ref(newKey('form'))
const executeKey = ref(newKey('execute'))
const resumeKey = ref(newKey('resume'))

const hasEditableForm = computed(() => form.value?.fields.some(field => field.editable) ?? false)
const hasFormChanges = computed(() => form.value?.fields.some(field => (
  field.editable
  && (edits.value[field.fieldCode] ?? '') !== displayValue(field.value)
)) ?? false)
const canResume = computed(() => Boolean(
  execution.value && execution.value.status.startsWith('WAITING_'),
))

watch(() => props.instanceId, () => {
  nodeCode.value = ''
  form.value = null
  formHistory.value = []
  execution.value = null
  executionHistory.value = []
  error.value = ''
  notice.value = ''
})

async function load() {
  const code = nodeCode.value.trim()
  if (!code) {
    error.value = '请输入已发布图中的节点代码'
    return
  }
  busy.value = true
  error.value = ''
  notice.value = ''
  const results = await Promise.allSettled([
    flowApi.nodeForm(props.systemId, props.instanceId, code),
    flowApi.nodeFormHistory(props.systemId, props.instanceId, code),
    flowApi.extensionNodeHistory(props.systemId, props.instanceId, code),
  ])
  const formResult = results[0]
  if (formResult.status === 'fulfilled') {
    form.value = formResult.value
    edits.value = Object.fromEntries(formResult.value.fields.map(field => (
      [field.fieldCode, displayValue(field.value)]
    )))
  } else {
    form.value = null
  }
  const historyResult = results[1]
  formHistory.value = historyResult.status === 'fulfilled' ? historyResult.value : []
  const executionResult = results[2]
  executionHistory.value = executionResult.status === 'fulfilled' ? executionResult.value : []
  const latest = executionHistory.value.at(-1)
  if (latest) {
    execution.value = {
      status: latest.toStatus,
      version: Math.max(0, latest.sequence - 1),
    }
  } else {
    execution.value = null
  }
  if (!form.value && !executionHistory.value.length) {
    error.value = firstReason(results) ?? '节点没有表单或执行历史'
  }
  busy.value = false
}

async function saveForm() {
  if (!form.value) return
  const values: Record<string, unknown> = {}
  try {
    form.value.fields.filter(field => field.editable).forEach((field) => {
      values[field.fieldCode] = parseValue(edits.value[field.fieldCode] ?? '', field.value)
    })
  } catch (cause) {
    error.value = message(cause)
    return
  }
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    const result = await flowApi.writeNodeForm(
      props.systemId,
      props.instanceId,
      nodeCode.value.trim(),
      {
        expectedSnapshotVersion: form.value.snapshotVersion,
        expectedRecordVersion: form.value.recordVersion,
        values,
      },
      formKey.value,
    )
    form.value = result.form
    formHistory.value = await flowApi.nodeFormHistory(
      props.systemId, props.instanceId, nodeCode.value.trim(),
    )
    formKey.value = newKey('form')
    notice.value = `表单已写回业务记录 v${result.form.recordVersion}，历史序号 ${result.history.sequence}`
  } catch (cause) {
    error.value = message(cause)
  } finally {
    busy.value = false
  }
}

async function execute() {
  await runNode(false)
}

async function resume() {
  await runNode(true)
}

async function runNode(resuming: boolean) {
  const code = nodeCode.value.trim()
  if (!code) return
  let input: Record<string, unknown>
  try {
    const parsed = JSON.parse(nodeInput.value) as unknown
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') throw new Error('节点输入必须是 JSON 对象')
    input = parsed as Record<string, unknown>
  } catch (cause) {
    error.value = message(cause)
    return
  }
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    const key = resuming ? resumeKey.value : executeKey.value
    execution.value = resuming
      ? await flowApi.resumeExtensionNode(
        props.systemId, props.instanceId, code, execution.value!.version, input, key,
      )
      : await flowApi.executeExtensionNode(
        props.systemId, props.instanceId, code, null, input, key,
      )
    executionHistory.value = await flowApi.extensionNodeHistory(
      props.systemId, props.instanceId, code,
    )
    if (resuming) resumeKey.value = newKey('resume')
    else executeKey.value = newKey('execute')
    notice.value = `节点${resuming ? '恢复' : '执行'}成功：${execution.value.status} / v${execution.value.version}`
  } catch (cause) {
    error.value = message(cause)
  } finally {
    busy.value = false
  }
}

function newKey(operation: string) {
  return `flow-${operation}-${crypto.randomUUID()}`
}

function displayValue(value: unknown) {
  return typeof value === 'string' ? value : value == null ? '' : JSON.stringify(value)
}

function parseValue(value: string, original: unknown) {
  if (typeof original === 'number') {
    const parsed = Number(value)
    if (!Number.isFinite(parsed)) throw new Error('数字字段格式不正确')
    return parsed
  }
  if (typeof original === 'boolean') return value === 'true'
  if (original !== null && typeof original === 'object') return JSON.parse(value)
  return value
}

function firstReason(results: PromiseSettledResult<unknown>[]) {
  const failed = results.find(result => result.status === 'rejected') as PromiseRejectedResult | undefined
  return failed ? message(failed.reason) : null
}

function message(cause: unknown) {
  return cause instanceof Error ? cause.message : '流程节点操作失败'
}
</script>

<template>
  <section class="flow-node-runtime">
    <header>
      <div><h3>节点表单与执行</h3><p>读取已发布快照；写入和恢复均使用乐观锁与显式幂等键。</p></div>
      <span v-if="execution" class="flow-node-status">{{ execution.status }} · v{{ execution.version }}</span>
    </header>
    <div class="flow-node-selector">
      <input v-model.trim="nodeCode" maxlength="60" placeholder="节点代码，如 form_review" aria-label="节点代码">
      <button type="button" :disabled="busy || !nodeCode" @click="load">读取节点</button>
    </div>
    <p v-if="error" class="flow-node-error" role="alert">{{ error }}</p>
    <p v-if="notice" class="flow-node-notice" role="status">{{ notice }}</p>

    <section v-if="form" class="flow-node-form">
      <header><h4>业务表单</h4><span>{{ form.moduleCode }} / 记录 {{ form.recordId }} / v{{ form.recordVersion }}</span></header>
      <label v-for="field in form.fields" :key="field.fieldCode" :class="{ required: field.required }">
        <span>{{ field.fieldCode }} <small>{{ field.mode }}</small></span>
        <textarea v-if="field.editable" v-model="edits[field.fieldCode]" rows="2" :required="field.required"></textarea>
        <output v-else>{{ displayValue(field.value) || '—' }}</output>
      </label>
      <div class="flow-node-idempotency">本次表单幂等键：<code>{{ formKey }}</code></div>
      <button v-if="hasEditableForm && canDecide" class="primary flow-node-form-save" type="button" :disabled="busy || !hasFormChanges" @click="saveForm">{{ hasFormChanges ? '按当前版本写回' : '没有待写回改动' }}</button>
    </section>

    <section v-if="formHistory.length" class="flow-node-history">
      <h4>表单写入历史</h4>
      <ol><li v-for="item in formHistory" :key="item.sequence">#{{ item.sequence }} · 记录 v{{ item.recordVersionBefore }} → v{{ item.recordVersionAfter }} · {{ item.occurredAt }}</li></ol>
    </section>

    <section class="flow-node-execution">
      <h4>节点执行 / 恢复</h4>
      <textarea v-model="nodeInput" rows="4" spellcheck="false" aria-label="节点输入 JSON"></textarea>
      <div class="flow-node-idempotency">
        <span>执行键：<code>{{ executeKey }}</code></span>
        <span>恢复键：<code>{{ resumeKey }}</code></span>
      </div>
      <div v-if="canDecide" class="flow-node-actions">
        <button class="primary flow-node-execute" type="button" :disabled="busy || !nodeCode || Boolean(execution)" @click="execute">首次执行</button>
        <button class="flow-node-resume" type="button" :disabled="busy || !canResume" @click="resume">按 v{{ execution?.version ?? '—' }} 恢复</button>
      </div>
      <p v-else>当前成员只有读取权限，不能写表单或执行节点。</p>
    </section>

    <section v-if="executionHistory.length" class="flow-node-history">
      <h4>执行历史</h4>
      <ol><li v-for="item in executionHistory" :key="item.sequence">#{{ item.sequence }} · {{ item.fromStatus ?? 'NEW' }} → {{ item.toStatus }} · {{ item.occurredAt }}</li></ol>
    </section>
  </section>
</template>

<style scoped>
.flow-node-runtime,.flow-node-form,.flow-node-history,.flow-node-execution { display: grid; gap: 10px; }
.flow-node-runtime { margin-top: 16px; padding: 14px; border: 1px solid #cbd5e1; border-radius: 10px; background: #f8fafc; }
.flow-node-runtime>header,.flow-node-form>header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
h3,h4,p { margin: 0; }
.flow-node-runtime header p,.flow-node-form header span,.flow-node-idempotency { color: #64748b; font-size: 12px; }
.flow-node-selector,.flow-node-actions { display: flex; gap: 8px; }
.flow-node-selector input { flex: 1; }
input,textarea,button { min-height: 34px; padding: 6px 8px; border: 1px solid #cbd5e1; border-radius: 7px; background: #fff; }
textarea { resize: vertical; }
button { cursor: pointer; }
button:disabled { cursor: not-allowed; opacity: .55; }
button.primary { border-color: #2563eb; background: #2563eb; color: #fff; }
.flow-node-form label { display: grid; gap: 5px; }
.flow-node-form label.required>span::after { content: ' *'; color: #dc2626; }
.flow-node-form output { min-height: 34px; padding: 7px; border-radius: 6px; background: #eef2f7; overflow-wrap: anywhere; }
.flow-node-idempotency { display: grid; gap: 3px; overflow-wrap: anywhere; }
.flow-node-idempotency code { color: #334155; }
.flow-node-status { padding: 4px 8px; border-radius: 999px; background: #dbeafe; color: #1d4ed8; }
.flow-node-error,.flow-node-notice { padding: 8px; border-radius: 6px; }
.flow-node-error { background: #fef2f2; color: #b91c1c; }
.flow-node-notice { background: #f0fdf4; color: #15803d; }
.flow-node-history ol { display: grid; gap: 5px; margin: 0; padding-left: 22px; }
</style>
