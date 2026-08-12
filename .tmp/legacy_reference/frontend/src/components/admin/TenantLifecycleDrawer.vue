<script setup lang="ts">
import { message } from 'ant-design-vue'
import { computed, reactive, ref, watch } from 'vue'

import { ApiRequestError } from '@/services/api'
import { systemAdminApi } from '@/services/admin'
import type { Tenant, TenantDomain, TenantLifecycleOperation, TenantLifecyclePlan, TenantQuota } from '@/types/admin'

const props = defineProps<{ open: boolean; systemId: string; tenant: Tenant | null }>()
const emit = defineEmits<{ 'update:open': [value: boolean]; reload: [] }>()
const loading = ref(false)
const errorMessage = ref('')
const domains = ref<TenantDomain[]>([])
const quotas = ref<TenantQuota[]>([])
const lastOperation = ref<TenantLifecycleOperation | null>(null)
const lifecyclePlan = ref<TenantLifecyclePlan | null>(null)
const impactConfirmed = ref(false)
const domainName = ref('')
const verifyTokens = reactive<Record<string, string>>({})
const quotaForm = reactive({ quotaKey: 'MEMBERS', softLimit: null as number | null, hardLimit: null as number | null })
const lifecycle = reactive({ action: 'BACKUP' as 'BACKUP' | 'RECOVERY' | 'MIGRATION', reason: '', backupOperationId: '', targetTenantId: '', targetTenantVersion: '' })
const title = computed(() => props.tenant ? `${props.tenant.name} · 租户控制面` : '租户控制面')

function report(error: unknown) {
  errorMessage.value = error instanceof ApiRequestError ? error.message : '租户控制面请求失败'
}

async function load() {
  if (!props.open || !props.tenant) return
  loading.value = true
  errorMessage.value = ''
  try {
    const [domainRows, quotaRows] = await Promise.all([
      systemAdminApi.listTenantDomains(props.systemId, props.tenant.id),
      systemAdminApi.listTenantQuotas(props.systemId, props.tenant.id),
    ])
    domains.value = domainRows
    quotas.value = quotaRows
  } catch (error) {
    report(error)
  } finally {
    loading.value = false
  }
}

async function addDomain() {
  if (!props.tenant || !domainName.value.trim()) return
  loading.value = true
  try {
    const created = await systemAdminApi.addTenantDomain(props.systemId, props.tenant.id, domainName.value.trim())
    if (created.verificationToken) verifyTokens[created.id] = created.verificationToken
    domainName.value = ''
    message.success('域名已添加，请使用一次性令牌完成验证')
    await load()
  } catch (error) { report(error) } finally { loading.value = false }
}

async function verifyDomain(domain: TenantDomain) {
  if (!props.tenant || !verifyTokens[domain.id]?.trim()) return
  loading.value = true
  try {
    await systemAdminApi.verifyTenantDomain(props.systemId, props.tenant.id, domain.id, verifyTokens[domain.id]!, domain.version)
    delete verifyTokens[domain.id]
    message.success('域名验证通过')
    await load()
  } catch (error) { report(error) } finally { loading.value = false }
}

async function primary(domain: TenantDomain) {
  if (!props.tenant) return
  loading.value = true
  try {
    await systemAdminApi.makePrimaryTenantDomain(props.systemId, props.tenant.id, domain.id, domain.version)
    message.success('主域名已更新')
    await load()
  } catch (error) { report(error) } finally { loading.value = false }
}

async function disable(domain: TenantDomain) {
  if (!props.tenant) return
  loading.value = true
  try {
    await systemAdminApi.disableTenantDomain(props.systemId, props.tenant.id, domain.id, domain.version)
    message.success('域名已停用')
    await load()
  } catch (error) { report(error) } finally { loading.value = false }
}

function editQuota(quota: TenantQuota) {
  quotaForm.quotaKey = quota.quotaKey
  quotaForm.softLimit = quota.softLimit
  quotaForm.hardLimit = quota.hardLimit
}

async function saveQuota() {
  if (!props.tenant || quotaForm.hardLimit == null || quotaForm.hardLimit < 0) return
  const existing = quotas.value.find(item => item.quotaKey === quotaForm.quotaKey)
  loading.value = true
  try {
    await systemAdminApi.setTenantQuota(props.systemId, props.tenant.id, {
      quotaKey: quotaForm.quotaKey,
      softLimit: quotaForm.softLimit,
      hardLimit: quotaForm.hardLimit, expectedVersion: existing?.version ?? null,
    })
    message.success('租户配额已保存')
    await load()
  } catch (error) { report(error) } finally { loading.value = false }
}

async function previewLifecycle() {
  if (!props.tenant) return
  if (lifecycle.action === 'RECOVERY' && !lifecycle.backupOperationId.trim()) return
  if (lifecycle.action === 'MIGRATION' && (!lifecycle.targetTenantId.trim() || !lifecycle.targetTenantVersion.trim())) return
  loading.value = true
  errorMessage.value = ''
  try {
    lifecyclePlan.value = lifecycle.action === 'RECOVERY'
      ? await systemAdminApi.previewTenantRecovery(props.systemId, props.tenant.id, {
        backupOperationId: lifecycle.backupOperationId,
        expectedTenantVersion: props.tenant.version,
      })
      : await systemAdminApi.previewTenantMigration(props.systemId, props.tenant.id, {
        targetTenantId: lifecycle.targetTenantId,
        expectedSourceVersion: props.tenant.version, expectedTargetVersion: lifecycle.targetTenantVersion,
      })
    impactConfirmed.value = false
    message.success(lifecyclePlan.value.eligible ? '影响预览已生成，请核对后确认' : '影响预览存在阻断项')
  } catch (error) { report(error) } finally { loading.value = false }
}

async function runLifecycle() {
  if (!props.tenant || !lifecycle.reason.trim()) return
  loading.value = true
  errorMessage.value = ''
  try {
    if (lifecycle.action === 'BACKUP') {
      lastOperation.value = await systemAdminApi.backupTenant(props.systemId, props.tenant.id, lifecycle.reason, props.tenant.version)
      lifecycle.backupOperationId = lastOperation.value.id
    } else {
      const plan = lifecyclePlan.value
      if (!plan?.eligible || !plan.confirmationToken || !impactConfirmed.value) return
      lastOperation.value = lifecycle.action === 'RECOVERY'
        ? await systemAdminApi.recoverTenant(props.systemId, props.tenant.id, {
          planOperationId: plan.id, confirmationToken: plan.confirmationToken,
          reason: lifecycle.reason, expectedTenantVersion: props.tenant.version, impactConfirmed: true,
        })
        : await systemAdminApi.migrateTenant(props.systemId, props.tenant.id, {
          planOperationId: plan.id, confirmationToken: plan.confirmationToken,
          targetTenantId: lifecycle.targetTenantId, reason: lifecycle.reason,
          expectedSourceVersion: props.tenant.version, expectedTargetVersion: lifecycle.targetTenantVersion,
          impactConfirmed: true,
        })
    }
    lifecycle.reason = ''
    lifecyclePlan.value = null
    impactConfirmed.value = false
    message.success(`租户${lifecycle.action === 'BACKUP' ? '备份' : lifecycle.action === 'RECOVERY' ? '恢复' : '迁移'}已完成`)
    emit('reload')
  } catch (error) { report(error) } finally { loading.value = false }
}

watch(() => props.open, (open) => { if (open) void load() })
watch(() => [lifecycle.action, lifecycle.backupOperationId, lifecycle.targetTenantId, lifecycle.targetTenantVersion], () => {
  lifecyclePlan.value = null
  impactConfirmed.value = false
})
</script>

<template>
  <a-drawer :open="open" :title="title" width="760" @close="emit('update:open', false)">
    <a-alert v-if="errorMessage" class="admin-alert tenant-lifecycle-error" type="error" show-icon :message="errorMessage" />
    <a-spin :spinning="loading">
      <a-tabs>
        <a-tab-pane key="domains" tab="域名">
          <div class="inline-editor"><a-input v-model:value="domainName" placeholder="tenant.example.com" /><a-button type="primary" @click="addDomain">添加域名</a-button></div>
          <a-list :data-source="domains" item-layout="vertical">
            <template #renderItem="{ item }">
              <a-list-item>
                <a-list-item-meta :title="item.domainName" :description="`${item.status}${item.primary ? ' · 主域名' : ''}`" />
                <div v-if="item.status === 'PENDING'" class="inline-editor"><a-input v-model:value="verifyTokens[item.id]" placeholder="一次性验证令牌" /><a-button @click="verifyDomain(item)">验证</a-button></div>
                <template #actions><a-button v-if="item.status === 'VERIFIED' && !item.primary" type="link" @click="primary(item)">设为主域名</a-button><a-button v-if="item.status !== 'DISABLED' && !item.primary" type="link" danger @click="disable(item)">停用</a-button></template>
              </a-list-item>
            </template>
          </a-list>
        </a-tab-pane>
        <a-tab-pane key="quotas" tab="配额">
          <a-table :data-source="quotas" :pagination="false" row-key="quotaKey" size="small">
            <a-table-column title="配额" data-index="quotaKey" /><a-table-column title="已用" data-index="usedValue" /><a-table-column title="软限制" data-index="softLimit" /><a-table-column title="硬限制" data-index="hardLimit" />
            <a-table-column title="操作"><template #default="{ record }"><a-button type="link" @click="editQuota(record)">编辑</a-button></template></a-table-column>
          </a-table>
          <a-form class="quota-editor" layout="vertical"><a-row :gutter="12"><a-col :span="8"><a-form-item label="配额类型"><a-select v-model:value="quotaForm.quotaKey"><a-select-option v-for="key in ['MEMBERS','MODULES','FIELDS','STORAGE_BYTES','IMPORT_EXPORT_JOBS','OPENAPI_CALLS']" :key="key" :value="key">{{ key }}</a-select-option></a-select></a-form-item></a-col><a-col :span="6"><a-form-item label="软限制"><a-input-number v-model:value="quotaForm.softLimit" :min="0" /></a-form-item></a-col><a-col :span="6"><a-form-item label="硬限制"><a-input-number v-model:value="quotaForm.hardLimit" :min="0" /></a-form-item></a-col><a-col :span="4"><a-form-item label="操作"><a-button type="primary" @click="saveQuota">保存</a-button></a-form-item></a-col></a-row></a-form>
        </a-tab-pane>
        <a-tab-pane key="lifecycle" tab="迁移与恢复">
          <a-alert type="warning" show-icon message="恢复和迁移必须先生成一次性影响预览，再核对阻断项、范围与版本后确认。" />
          <a-form layout="vertical">
            <a-form-item label="操作"><a-radio-group v-model:value="lifecycle.action"><a-radio-button value="BACKUP">备份</a-radio-button><a-radio-button value="RECOVERY">恢复</a-radio-button><a-radio-button value="MIGRATION">迁移</a-radio-button></a-radio-group></a-form-item>
            <a-form-item v-if="lifecycle.action === 'RECOVERY'" label="备份操作 ID" required><a-input v-model:value="lifecycle.backupOperationId" /></a-form-item>
            <template v-if="lifecycle.action === 'MIGRATION'"><a-form-item label="目标租户 ID" required><a-input v-model:value="lifecycle.targetTenantId" /></a-form-item><a-form-item label="目标租户版本" required><a-input v-model:value="lifecycle.targetTenantVersion" /></a-form-item></template>
            <a-form-item label="操作原因" required><a-textarea v-model:value="lifecycle.reason" :rows="3" /></a-form-item>
            <a-space>
              <a-button v-if="lifecycle.action !== 'BACKUP'" :loading="loading" @click="previewLifecycle">生成影响预览</a-button>
              <a-button type="primary" danger :loading="loading" :disabled="lifecycle.action !== 'BACKUP' && (!lifecyclePlan?.eligible || !impactConfirmed)" @click="runLifecycle">{{ lifecycle.action === 'BACKUP' ? '创建备份' : '确认执行' }}</a-button>
            </a-space>
          </a-form>
          <a-card v-if="lifecyclePlan" class="operation-result" size="small" title="一次性影响预览">
            <a-descriptions bordered :column="1" size="small">
              <a-descriptions-item label="计划 / 有效期">{{ lifecyclePlan.id }} / {{ lifecyclePlan.expiresAt }}</a-descriptions-item>
              <a-descriptions-item label="资格 / 数据量">{{ lifecyclePlan.eligible ? '可执行' : '已阻断' }} / {{ lifecyclePlan.rowCount }} 行 / {{ lifecyclePlan.estimatedBytes }} 字节</a-descriptions-item>
              <a-descriptions-item label="数据库版本">{{ lifecyclePlan.databaseMigrationVersion }}</a-descriptions-item>
              <a-descriptions-item label="阻断项">{{ lifecyclePlan.blockers.length ? lifecyclePlan.blockers.join('；') : '无' }}</a-descriptions-item>
              <a-descriptions-item label="表影响"><pre class="impact-json">{{ JSON.stringify(lifecyclePlan.tableImpacts, null, 2) }}</pre></a-descriptions-item>
              <a-descriptions-item label="配额投影"><pre class="impact-json">{{ JSON.stringify(lifecyclePlan.quotaProjection, null, 2) }}</pre></a-descriptions-item>
            </a-descriptions>
            <a-checkbox v-model:checked="impactConfirmed" class="impact-confirm" :disabled="!lifecyclePlan.eligible">我已核对以上影响、目标和一次性确认范围</a-checkbox>
          </a-card>
          <a-descriptions v-if="lastOperation" class="operation-result" bordered :column="1" size="small" title="最近操作结果"><a-descriptions-item label="操作 ID">{{ lastOperation.id }}</a-descriptions-item><a-descriptions-item label="类型 / 状态">{{ lastOperation.operationType }} / {{ lastOperation.status }}</a-descriptions-item><a-descriptions-item label="快照校验">{{ lastOperation.snapshotChecksum }}</a-descriptions-item></a-descriptions>
        </a-tab-pane>
      </a-tabs>
    </a-spin>
  </a-drawer>
</template>

<style scoped>
.inline-editor { display: flex; gap: 8px; margin-bottom: 12px; }
.quota-editor, .operation-result { margin-top: 18px; }
.impact-json { max-height: 180px; margin: 0; overflow: auto; white-space: pre-wrap; word-break: break-all; }
.impact-confirm { margin-top: 12px; }
</style>
