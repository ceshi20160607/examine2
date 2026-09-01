<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ApiError, api } from '../api'
import { activateRotationConfirmation, createSecretConfirmation, oneTimeSecret, performancePath, prepareRotationConfirmation, rotationOutcome } from '../operations-security'
import { systemTokens } from '../session'
import type { OperationsOneTimeSecret, OperationsRotation, OperationsSecurityOverview, OperationsVerificationRun } from '../types'

const basePath = '/api/admin/system/operations/security-performance'
const token = computed(() => systemTokens.value?.accessToken)
const overview = ref<OperationsSecurityOverview>()
const issued = ref<OperationsOneTimeSecret>()
const selectedRotation = ref<OperationsRotation>()
const loading = ref(false)
const busy = ref('')
const error = ref('')
const success = ref('')
const secretForm = reactive({ secretCode: `webhook.primary.${Date.now()}`, secretType: 'WEBHOOK', consumers: 'billing-webhook,crm-webhook', approvalReference: 'CAB-SECRET-', confirmation: '' })
const rotationForm = reactive({ secretRefId: undefined as number | undefined, consumers: 'billing-webhook,crm-webhook', approvalReference: 'CAB-ROTATE-', confirmation: '' })
const activateForm = reactive({ rotationId: undefined as number | undefined, securityRunId: undefined as number | undefined, failedConsumer: '', evidenceReference: 'canary-request-', confirmation: '' })
const securityForm = reactive({ approvalReference: 'CAB-SECURITY-', confirmation: 'RUN SECURITY BASELINE' })
const performanceForm = reactive({ pageSize: 50, fileProbeBytes: 65536, timeoutMillis: 3000, listThresholdMillis: 500, fileThresholdMillis: 500, queueThresholdMillis: 500, statisticsThresholdMillis: 500, approvalReference: 'CAB-PERFORMANCE-' })

const latestSecurity = computed(() => overview.value?.securityRuns[0])
const latestPerformance = computed(() => overview.value?.performanceRuns[0])
const issuedSecret = computed(() => oneTimeSecret(issued.value))
const selectedRef = computed(() => overview.value?.secretRefs.find(item => item.id === rotationForm.secretRefId))
const pendingRotation = computed(() => overview.value?.rotations.find(item => item.id === activateForm.rotationId))
const paths = ['LIST_QUERY', 'FILE_STREAM', 'JOB_QUEUE', 'STATISTICS']
const pathNames: Record<string, string> = { LIST_QUERY: '列表查询', FILE_STREAM: '文件流', JOB_QUEUE: '作业队列', STATISTICS: '统计聚合' }

function consumers(value: string) { return value.split(',').map(item => item.trim()).filter(Boolean) }
function explain(cause: unknown, fallback: string) { return cause instanceof ApiError ? `${cause.message}（${cause.code}${cause.traceId ? ` · requestId ${cause.traceId}` : ''}）` : fallback }
function color(status?: string) { return ['PASSED', 'ACTIVE'].includes(status || '') ? 'green' : ['FAILED', 'FAILED_CANARY', 'BLOCKED_SECURITY', 'THRESHOLD_EXCEEDED'].includes(status || '') ? 'red' : 'blue' }
function path(run: OperationsVerificationRun | undefined, code: string) { return performancePath(run, code) }
function metric(value: unknown) { return value === undefined || value === null || value === '' ? '—' : typeof value === 'object' ? JSON.stringify(value) : String(value) }

async function load() {
  if (!token.value) return
  loading.value = true; error.value = ''
  try {
    overview.value = await api<OperationsSecurityOverview>(basePath, {}, token.value)
    const firstRef = overview.value.secretRefs[0]
    const pending = overview.value.rotations.find(item => item.status === 'AWAITING_CANARY')
    if (firstRef && !rotationForm.secretRefId) rotationForm.secretRefId = firstRef.id
    if (pending && !activateForm.rotationId) chooseRotation(pending)
    if (latestSecurity.value?.status === 'PASSED' && !activateForm.securityRunId) activateForm.securityRunId = latestSecurity.value.id
  } catch (cause) { error.value = explain(cause, '安全与性能数据加载失败') } finally { loading.value = false }
}

async function createSecret() {
  if (!token.value) return
  busy.value = 'create'; error.value = ''; success.value = ''
  try {
    issued.value = await api<OperationsOneTimeSecret>(`${basePath}/secret-refs`, { method: 'POST', body: JSON.stringify({
      secretCode: secretForm.secretCode, secretType: secretForm.secretType, consumerCodes: consumers(secretForm.consumers),
      approvalReference: secretForm.approvalReference, confirmation: secretForm.confirmation,
    }) }, token.value)
    rotationForm.secretRefId = issued.value.secretRef.id
    success.value = `密钥引用 ${issued.value.secretRef.secretCode} 已创建；v1 已生效并持久化，明文仅在下方显示本次。`
    await load()
  } catch (cause) { error.value = explain(cause, '密钥引用创建失败') } finally { busy.value = '' }
}

async function prepareRotation() {
  if (!token.value || !rotationForm.secretRefId) return
  busy.value = 'prepare'; error.value = ''; success.value = ''
  try {
    issued.value = await api<OperationsOneTimeSecret>(`${basePath}/secret-refs/${rotationForm.secretRefId}/rotations`, { method: 'POST', body: JSON.stringify({
      consumerCodes: consumers(rotationForm.consumers), approvalReference: rotationForm.approvalReference, confirmation: rotationForm.confirmation,
    }) }, token.value)
    activateForm.rotationId = issued.value.rotation.id
    activateForm.confirmation = activateRotationConfirmation(issued.value.rotation.id)
    success.value = `${issued.value.rotation.toVersion} 已生成，${issued.value.rotation.activeVersion} 仍在服务；完成使用方灰度后才能切换。`
    await load()
  } catch (cause) { error.value = explain(cause, '新密钥版本准备失败') } finally { busy.value = '' }
}

function chooseRotation(item: OperationsRotation) {
  selectedRotation.value = item
  activateForm.rotationId = item.id
  activateForm.confirmation = activateRotationConfirmation(item.id)
}

async function activateRotation() {
  if (!token.value || !pendingRotation.value || !activateForm.securityRunId) return
  busy.value = 'activate'; error.value = ''; success.value = ''
  const checks = pendingRotation.value.consumerCodes.map(consumerCode => ({ consumerCode,
    status: activateForm.failedConsumer.trim() === consumerCode ? 'FAILED' : 'PASSED', evidenceReference: activateForm.evidenceReference }))
  try {
    selectedRotation.value = await api<OperationsRotation>(`${basePath}/rotations/${pendingRotation.value.id}/activate`, { method: 'POST', body: JSON.stringify({
      securityRunId: activateForm.securityRunId, consumerChecks: checks, confirmation: activateForm.confirmation,
    }) }, token.value)
    success.value = selectedRotation.value.switched ? `轮换成功：${rotationOutcome(selectedRotation.value)}` : `切换被阻止：${rotationOutcome(selectedRotation.value)}`
    await load()
  } catch (cause) { error.value = explain(cause, '轮换验证未完成') } finally { busy.value = '' }
}

async function runSecurity() {
  if (!token.value) return
  busy.value = 'security'; error.value = ''; success.value = ''
  try {
    const run = await api<OperationsVerificationRun>(`${basePath}/security-runs`, { method: 'POST', body: JSON.stringify(securityForm) }, token.value)
    success.value = run.status === 'PASSED' ? `安全基线 #${run.id} 已通过，允许轮换切换。` : `安全基线 #${run.id} 有 ${run.findings.length} 个阻断项，轮换不会切换。`
    activateForm.securityRunId = run.id
    await load()
  } catch (cause) { error.value = explain(cause, '安全基线执行失败') } finally { busy.value = '' }
}

async function runPerformance(strict = false) {
  if (!token.value) return
  busy.value = strict ? 'performance-strict' : 'performance'; error.value = ''; success.value = ''
  const payload = strict ? { ...performanceForm, listThresholdMillis: 0, fileThresholdMillis: 0, queueThresholdMillis: 0, statisticsThresholdMillis: 0 } : performanceForm
  try {
    const run = await api<OperationsVerificationRun>(`${basePath}/performance-runs`, { method: 'POST', body: JSON.stringify(payload) }, token.value)
    success.value = run.status === 'PASSED' ? `性能基线 #${run.id} 的四条真实数据路径均在阈值内。` : `性能基线 #${run.id} 已生成 ${run.findings.length} 个明确优化任务。`
    await load()
  } catch (cause) { error.value = explain(cause, '有界性能检查失败') } finally { busy.value = '' }
}

function dismissSecret() { issued.value = undefined; success.value = '一次性密钥已从当前页面清除；重新加载也不会再次返回。' }

onMounted(load)
</script>

<template>
  <section class="operations-security" :class="{ 'is-loading': loading }">
    <a-alert v-if="error" type="error" show-icon :message="error" closable @close="error = ''" />
    <a-alert v-if="success" type="success" show-icon :message="success" closable @close="success = ''" />
    <section class="panel-card security-hero"><div><p class="eyebrow">引用化密钥 · 安全门禁 · 有界性能</p><h2>安全与关键数据路径</h2><p>新版本先灰度、后切换；安全失败时旧版本继续服务。性能检查只跑真实且有界的数据库、Redis、文件与统计样本。</p></div><div><a-tag :color="color(latestSecurity?.status)">安全 {{ latestSecurity?.status || '未检查' }}</a-tag><a-tag :color="color(latestPerformance?.status)">性能 {{ latestPerformance?.status || '未检查' }}</a-tag></div></section>

    <a-alert v-if="issuedSecret" class="one-time-secret" type="warning" show-icon message="一次性密钥：关闭后不可再次读取">
      <template #action><a-button danger @click="dismissSecret">我已安全保存，立即清除</a-button></template>
      <template #description><div class="one-time-secret__content"><p>{{ issued?.notice }}</p><code>{{ issuedSecret }}</code></div></template>
    </a-alert>

    <div class="security-workbench">
      <section class="panel-card security-form"><div class="panel-title"><div><strong>1. 创建类型化密钥引用</strong><small>只在本次响应显示明文，后续页面和日志仅显示引用与版本。</small></div></div>
        <a-input v-model:value="secretForm.secretCode" placeholder="密钥编码" />
        <a-select v-model:value="secretForm.secretType" :options="['APPLICATION','SSO','WEBHOOK','MODEL'].map(value => ({ value, label: value }))" />
        <a-input v-model:value="secretForm.consumers" placeholder="使用方编码，逗号分隔" />
        <a-input v-model:value="secretForm.approvalReference" placeholder="审批单号" />
        <a-input v-model:value="secretForm.confirmation" :placeholder="createSecretConfirmation(secretForm.secretCode)" />
        <small>确认词：<code>{{ createSecretConfirmation(secretForm.secretCode) }}</code></small>
        <a-button type="primary" :loading="busy === 'create'" :disabled="!secretForm.confirmation" @click="createSecret">创建并显示一次</a-button>
      </section>

      <section class="panel-card security-form"><div class="panel-title"><div><strong>2. 准备新版本</strong><small>此时不切换，当前版本保持 ACTIVE。</small></div></div>
        <a-select v-model:value="rotationForm.secretRefId" placeholder="选择密钥引用"><a-select-option v-for="item in overview?.secretRefs" :key="item.id" :value="item.id">{{ item.secretCode }} · {{ item.currentVersion }} · {{ item.status }}</a-select-option></a-select>
        <a-input v-model:value="rotationForm.consumers" placeholder="本轮灰度使用方" />
        <a-input v-model:value="rotationForm.approvalReference" placeholder="轮换审批单号" />
        <a-input v-model:value="rotationForm.confirmation" :placeholder="selectedRef ? prepareRotationConfirmation(selectedRef.secretCode) : '先选择引用'" />
        <small>确认词：<code>{{ selectedRef ? prepareRotationConfirmation(selectedRef.secretCode) : '先选择引用' }}</code></small>
        <a-button type="primary" :loading="busy === 'prepare'" :disabled="!rotationForm.secretRefId || !rotationForm.confirmation" @click="prepareRotation">生成待灰度版本</a-button>
      </section>

      <section class="panel-card security-form"><div class="panel-title"><div><strong>3. 验证使用方并切换</strong><small>必须绑定已通过的安全运行；任一使用方失败都保留旧版。</small></div></div>
        <a-select v-model:value="activateForm.rotationId" placeholder="待验证轮换" @change="(id: number) => chooseRotation(overview!.rotations.find(item => item.id === id)!)"><a-select-option v-for="item in overview?.rotations.filter(row => row.status === 'AWAITING_CANARY')" :key="item.id" :value="item.id">#{{ item.id }} · {{ item.fromVersion }} → {{ item.toVersion }}</a-select-option></a-select>
        <a-select v-model:value="activateForm.securityRunId" placeholder="安全基线"><a-select-option v-for="item in overview?.securityRuns" :key="item.id" :value="item.id">#{{ item.id }} · {{ item.status }}</a-select-option></a-select>
        <a-input v-model:value="activateForm.evidenceReference" placeholder="灰度 requestId / 证据引用" />
        <a-input v-model:value="activateForm.failedConsumer" placeholder="失败使用方（留空表示全部通过）" />
        <a-input v-model:value="activateForm.confirmation" :placeholder="activateRotationConfirmation(activateForm.rotationId)" />
        <small>确认词：<code>{{ activateRotationConfirmation(activateForm.rotationId) || '先选择轮换' }}</code></small>
        <a-button type="primary" :loading="busy === 'activate'" :disabled="!pendingRotation || !activateForm.securityRunId || !activateForm.confirmation" @click="activateRotation">提交灰度结果并切换</a-button>
      </section>
    </div>

    <section class="panel-card security-baseline"><div class="panel-title"><div><strong>安全基线门禁</strong><small>真实检查数据库、Redis、文件、主密钥、密码散列、应用凭证引用及系统租户边界。</small></div><a-button type="primary" :loading="busy === 'security'" @click="runSecurity">运行安全基线</a-button></div>
      <div class="baseline-summary"><a-input v-model:value="securityForm.approvalReference" placeholder="安全检查审批单号" /><a-input v-model:value="securityForm.confirmation" /><a-tag :color="color(latestSecurity?.status)">#{{ latestSecurity?.id || '—' }} · {{ latestSecurity?.status || '未运行' }}</a-tag></div>
      <div v-if="latestSecurity?.findings.length" class="finding-list"><article v-for="item in latestSecurity.findings" :key="item.id"><a-tag color="red">{{ item.severity }}</a-tag><strong>{{ item.title }}</strong><small>{{ item.detail }}</small></article></div>
    </section>

    <section class="panel-card performance-baseline"><div class="panel-title"><div><strong>有界关键路径性能</strong><small>显示索引、服务端分页、超时、队列、缓存隔离和文件资源占用；超界自动形成优化任务。</small></div><div><a-button :loading="busy === 'performance-strict'" @click="runPerformance(true)">验证超界分支</a-button><a-button type="primary" :loading="busy === 'performance'" @click="runPerformance(false)">运行基线</a-button></div></div>
      <div class="performance-inputs"><a-input-number v-model:value="performanceForm.pageSize" :min="1" :max="200" addon-before="分页" /><a-input-number v-model:value="performanceForm.fileProbeBytes" :min="1024" :max="1048576" addon-before="文件 bytes" /><a-input-number v-model:value="performanceForm.timeoutMillis" :min="100" addon-before="超时 ms" /><a-input v-model:value="performanceForm.approvalReference" placeholder="性能检查审批单号" /></div>
      <div class="performance-paths"><article v-for="code in paths" :key="code" :data-status="metric(path(latestPerformance, code)?.status)"><header><strong>{{ pathNames[code] }}</strong><a-tag :color="color(metric(path(latestPerformance, code)?.status))">{{ metric(path(latestPerformance, code)?.status) }}</a-tag></header><b>{{ metric(path(latestPerformance, code)?.latencyMillis) }} ms</b><small>阈值 {{ metric(path(latestPerformance, code)?.thresholdMillis) }} ms · 超时 {{ metric(path(latestPerformance, code)?.timeoutMillis) }} ms</small><p v-if="code === 'LIST_QUERY'">索引 {{ metric(path(latestPerformance, code)?.index) }} · 分页 {{ metric(path(latestPerformance, code)?.pageSize) }}</p><p v-else-if="code === 'FILE_STREAM'">流式 {{ metric(path(latestPerformance, code)?.streaming) }} · 资源 {{ metric(path(latestPerformance, code)?.resourceBytes) }} B</p><p v-else-if="code === 'JOB_QUEUE'">Redis {{ metric(path(latestPerformance, code)?.redis) }} · 队列 {{ metric(path(latestPerformance, code)?.queue) }}</p><p v-else>数据库聚合 · 租户缓存隔离 {{ metric(path(latestPerformance, code)?.cacheIsolatedBySystemTenant) }}</p></article></div>
      <div v-if="latestPerformance?.findings.length" class="finding-list"><article v-for="item in latestPerformance.findings" :key="item.id"><a-tag color="orange">{{ item.status }}</a-tag><strong>{{ item.title }}</strong><small>{{ item.detail }}</small></article></div>
    </section>

    <section class="panel-card rotation-history"><div class="panel-title"><div><strong>引用与轮换读回</strong><small>没有明文和加密载荷，只有安全引用、版本、状态、使用方证据与失败原因。</small></div></div><div class="secret-ref-list"><article v-for="item in overview?.secretRefs" :key="item.id"><header><strong>{{ item.secretCode }}</strong><a-tag :color="color(item.status)">{{ item.currentVersion }} · {{ item.status }}</a-tag></header><code>{{ item.referencePath }}</code><small>{{ item.secretType }} · 最近验证 {{ item.lastVerifiedAt || '—' }}</small></article></div><div class="rotation-list"><button v-for="item in overview?.rotations.slice(0, 12)" :key="item.id" type="button" @click="selectedRotation = item"><a-tag :color="color(item.status)">{{ item.status }}</a-tag><span><strong>#{{ item.id }} · {{ item.fromVersion }} → {{ item.toVersion }}</strong><small>{{ rotationOutcome(item) }} · {{ item.requestedAt }}</small></span></button></div></section>
  </section>
</template>
