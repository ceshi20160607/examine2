<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { ReloadOutlined, PlusOutlined, SendOutlined } from '@ant-design/icons-vue'
import { ApiError, api } from '../api'
import { platformContext, platformTokens, systemContext, systemTokens } from '../session'
import { deliveryStatusLabel, messageChannelLabel } from '../message'
import ProductPageHeader from '../components/ProductPageHeader.vue'
import ProductPage from '../components/ProductPage.vue'
import PersonSelect from '../components/PersonSelect.vue'
import { productDateTime } from '../presentation'
import type { MessageDeliveryDiagnosticsView, MessageTemplateView, SystemPeopleDirectory } from '../types'

const props = withDefaults(defineProps<{ context?: 'platform' | 'system' }>(), { context: 'system' })
const token = computed(() => props.context === 'platform' ? platformTokens.value?.accessToken : systemTokens.value?.accessToken)
const current = computed(() => props.context === 'platform' ? platformContext.value : systemContext.value)
const templates = ref<MessageTemplateView[]>([])
const directory = ref<SystemPeopleDirectory>({ departments: [], people: [], permissionVersion: 0 })
const selected = ref<MessageTemplateView>()
const selectedRecipients = ref<number[]>([])
const variableValues = reactive<Record<string, string>>({})
const diagnostics = ref<MessageDeliveryDiagnosticsView>()
const editorOpen = ref(false)
const loading = ref(false)
const busy = ref(false)
const form = reactive({ code: '', name: '', channel: 'IN_APP', subjectTemplate: '', contentTemplate: '', requiredVariables: [] as string[] })

function describeError(error: unknown) {
  if (error instanceof ApiError) return `${error.message}${error.traceId ? `（追踪号 ${error.traceId}）` : ''}`
  return error instanceof Error ? error.message : '操作失败'
}

function deliveryColor(value: string) {
  return ({ DELIVERED: 'green', SENT: 'blue', RETRY_PENDING: 'orange', FAILED: 'red' } as Record<string, string>)[value] || 'default'
}

async function load() {
  if (!token.value) return
  loading.value = true
  try {
    const requests: Promise<unknown>[] = [api<MessageTemplateView[]>('/api/message-templates', {}, token.value)]
    if (props.context === 'system') requests.push(api<SystemPeopleDirectory>('/api/system-directory', {}, token.value))
    const [templateRows, people] = await Promise.all(requests)
    templates.value = templateRows as MessageTemplateView[]
    if (people) directory.value = people as SystemPeopleDirectory
  } catch (error) { message.error(describeError(error)) }
  finally { loading.value = false }
}

function edit(row?: MessageTemplateView) {
  selected.value = row
  diagnostics.value = undefined
  Object.keys(variableValues).forEach(key => delete variableValues[key])
  Object.assign(form, row ? {
    code: row.code, name: row.name, channel: row.channel, subjectTemplate: row.subjectTemplate || '',
    contentTemplate: row.contentTemplate, requiredVariables: [...row.requiredVariables],
  } : { code: '', name: '', channel: 'IN_APP', subjectTemplate: '', contentTemplate: '', requiredVariables: [] })
  if (row) row.requiredVariables.forEach(key => { variableValues[key] = '' })
  selectedRecipients.value = props.context === 'system' ? [] : (current.value?.memberId ? [current.value.memberId] : [])
  editorOpen.value = true
}

async function save() {
  if (!token.value || !form.code.trim() || !form.name.trim() || !form.contentTemplate.trim()) return message.warning('请完整填写模板名称和消息正文')
  busy.value = true
  try {
    selected.value = await api<MessageTemplateView>('/api/message-templates', { method: 'POST', body: JSON.stringify({
      id: selected.value?.id, expectedVersion: selected.value?.version,
      code: form.code.trim().toUpperCase(), name: form.name.trim(), channel: form.channel,
      subjectTemplate: form.subjectTemplate || undefined, contentTemplate: form.contentTemplate,
      requiredVariables: form.requiredVariables,
    }) }, token.value)
    await load()
    message.success('模板草稿已保存')
  } catch (error) { message.error(describeError(error)) }
  finally { busy.value = false }
}

async function publish() {
  if (!token.value || !selected.value) return message.warning('请先保存模板')
  busy.value = true
  try {
    selected.value = await api<MessageTemplateView>(`/api/message-templates/${selected.value.id}/publish`, { method: 'POST' }, token.value)
    await load()
    message.success(`模板版本 V${selected.value.publishedVersion} 已发布`)
  } catch (error) { message.error(describeError(error)) }
  finally { busy.value = false }
}

async function sendTest() {
  if (!token.value || !selected.value?.publishedVersion) return message.warning('请先发布模板')
  if (!selectedRecipients.value.length) return message.warning('请选择接收成员')
  const missing = selected.value.requiredVariables.filter(key => !variableValues[key]?.trim())
  if (missing.length) return message.warning(`请填写：${missing.join('、')}`)
  busy.value = true
  try {
    const sent = await api<{ id: number }>('/api/messages/events', { method: 'POST', body: JSON.stringify({
      templateCode: selected.value.code, sourceType: 'ADMIN_DIAGNOSTIC', dedupKey: `message-diagnostic-${Date.now()}`,
      recipients: selectedRecipients.value.map(id => ({ type: props.context === 'system' ? 'TENANT_MEMBER' : 'PLATFORM_MEMBER', id })),
      variables: Object.fromEntries(selected.value.requiredVariables.map(key => [key, variableValues[key]])),
      sensitivity: 'NORMAL',
    }) }, token.value)
    diagnostics.value = await api<MessageDeliveryDiagnosticsView>(`/api/messages/diagnostics/${sent.id}/deliveries`, {}, token.value)
    message.success('诊断消息已生成，投递状态已读回')
  } catch (error) { message.error(describeError(error)) }
  finally { busy.value = false }
}

onMounted(load)
</script>

<template>
  <ProductPage class="message-template-management" density="configuration">
    <ProductPageHeader density="configuration" kicker="消息管理" title="消息模板" description="配置业务通知的内容和渠道；测试发送与投递状态只在这里可见。">
      <template #actions><a-button :loading="loading" @click="load"><ReloadOutlined />刷新</a-button></template>
      <template #primary><a-button type="primary" @click="edit()"><PlusOutlined />新建模板</a-button></template>
    </ProductPageHeader>
    <a-alert type="info" show-icon message="用户收件箱只展示业务消息" description="成员选择来自当前组织；账号编号、模板 JSON 和渠道回执不会进入普通消息页面。" class="section-alert" />
    <a-table :data-source="templates" :loading="loading" row-key="id" :pagination="false">
      <a-table-column title="模板"><template #default="{ record }"><strong>{{ record.name }}</strong><small>{{ record.code }}</small></template></a-table-column>
      <a-table-column title="渠道"><template #default="{ record }">{{ messageChannelLabel(record.channel) }}</template></a-table-column>
      <a-table-column title="状态"><template #default="{ record }"><a-tag :color="record.status === 'PUBLISHED' ? 'green' : 'orange'">{{ record.status === 'PUBLISHED' ? `已发布 V${record.publishedVersion}` : '草稿' }}</a-tag></template></a-table-column>
      <a-table-column title="更新时间"><template #default="{ record }">{{ productDateTime(record.updatedAt) }}</template></a-table-column>
      <a-table-column title="操作" width="120"><template #default="{ record }"><a-button type="link" @click="edit(record)">配置与验证</a-button></template></a-table-column>
    </a-table>
    <a-empty v-if="!templates.length && !loading" description="还没有消息模板" />

    <a-drawer v-model:open="editorOpen" width="680" title="消息模板配置">
      <a-form layout="vertical">
        <div class="form-grid three"><a-form-item label="模板编码" required><a-input v-model:value="form.code" :disabled="!!selected" /></a-form-item><a-form-item label="业务名称" required><a-input v-model:value="form.name" /></a-form-item><a-form-item label="送达渠道"><a-select v-model:value="form.channel" :disabled="!!selected" :options="[{ value:'IN_APP', label:'站内消息' },{ value:'EMAIL', label:'站内 + 邮件' },{ value:'SMS', label:'站内 + 短信' },{ value:'WEBHOOK', label:'站内 + Webhook' }]" /></a-form-item></div>
        <a-form-item label="消息标题"><a-input v-model:value="form.subjectTemplate" placeholder="例如：流程 {{title}} 已更新" /></a-form-item>
        <a-form-item label="消息正文" required><a-textarea v-model:value="form.contentTemplate" :rows="4" /></a-form-item>
        <a-form-item label="可替换内容"><a-select v-model:value="form.requiredVariables" mode="tags" placeholder="输入变量名后回车，例如 title、actor" /></a-form-item>
        <div class="message-template-actions"><a-button type="primary" :loading="busy" @click="save">保存草稿</a-button><a-button :loading="busy" :disabled="!selected" @click="publish">发布版本</a-button></div>
        <a-divider>管理员投递验证</a-divider>
        <a-alert v-if="!selected?.publishedVersion" type="warning" show-icon message="发布模板后才能发送验证消息" class="section-alert" />
        <template v-else>
          <a-form-item label="接收成员" required>
            <PersonSelect v-if="context === 'system'" v-model="selectedRecipients" :people="directory.people" value-key="tenantMemberId" multiple placeholder="从当前组织选择接收成员" />
            <a-input v-else :value="current?.displayName" disabled addon-before="当前平台成员" />
          </a-form-item>
          <div v-if="selected.requiredVariables.length" class="form-grid two"><a-form-item v-for="key in selected.requiredVariables" :key="key" :label="key" required><a-input v-model:value="variableValues[key]" /></a-form-item></div>
          <a-button block type="primary" :loading="busy" @click="sendTest"><SendOutlined />发送验证消息</a-button>
        </template>
        <section v-if="diagnostics" class="panel-card message-diagnostics">
          <div class="panel-title"><strong>投递诊断</strong><span>{{ diagnostics.subject }}</span></div>
          <div v-for="delivery in diagnostics.deliveries" :key="delivery.id" class="message-diagnostics__row"><span><strong>{{ messageChannelLabel(delivery.channel) }}</strong><small>{{ delivery.destinationMasked }}</small></span><a-tag :color="deliveryColor(delivery.status)">{{ deliveryStatusLabel(delivery.status) }}</a-tag></div>
        </section>
      </a-form>
    </a-drawer>
  </ProductPage>
</template>
