<script setup lang="ts">
import { message } from 'ant-design-vue'
import { BellRing, Pencil, RefreshCw, Rocket } from 'lucide-vue-next'
import { onMounted, reactive, ref, watch } from 'vue'

import { ApiRequestError } from '@/services/api'
import { messageTemplateApi } from '@/services/event'
import type { DeliveryChannel, MessageTemplate } from '@/types/event'

const props = defineProps<{ systemId: string }>()
const templates = ref<MessageTemplate[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const editing = ref<MessageTemplate | null>(null)
const channelOptions: Array<{ label: string, value: DeliveryChannel }> = [
  { label: '站内信', value: 'INBOX' },
  { label: '邮件', value: 'EMAIL' },
  { label: 'Webhook', value: 'WEBHOOK' },
]
const form = reactive({
  name: '', enabled: true, titleTemplate: '', bodyTemplate: '', channels: [] as DeliveryChannel[],
})

async function load() {
  loading.value = true
  error.value = ''
  try { templates.value = await messageTemplateApi.list(props.systemId) }
  catch (cause) { error.value = text(cause, '消息模板加载失败') }
  finally { loading.value = false }
}

function edit(item: MessageTemplate) {
  editing.value = item
  form.name = item.name
  form.enabled = item.enabled
  form.titleTemplate = item.titleTemplate
  form.bodyTemplate = item.bodyTemplate
  form.channels = [...item.channels]
}

async function save() {
  if (!editing.value) return
  saving.value = true
  error.value = ''
  if (!form.channels.length) {
    error.value = '至少选择一个投递渠道'
    saving.value = false
    return
  }
  try {
    const updated = await messageTemplateApi.update(props.systemId, editing.value.templateCode, {
      expectedVersion: editing.value.version,
      name: form.name,
      enabled: form.enabled,
      titleTemplate: form.titleTemplate,
      bodyTemplate: form.bodyTemplate,
      channels: [...form.channels],
    })
    editing.value = updated
    message.success('消息模板草稿已保存')
    await load()
    edit(templates.value.find((item) => item.templateCode === updated.templateCode) ?? updated)
  } catch (cause) { error.value = text(cause, '消息模板保存失败') }
  finally { saving.value = false }
}

async function publish(item = editing.value) {
  if (!item) return
  saving.value = true
  error.value = ''
  try {
    const published = await messageTemplateApi.publish(props.systemId, item.templateCode, item.version)
    message.success(`已发布 V${published.publishedVersion}`)
    await load()
    edit(templates.value.find((value) => value.templateCode === published.templateCode) ?? published)
  } catch (cause) { error.value = text(cause, '消息模板发布失败') }
  finally { saving.value = false }
}

function text(cause: unknown, fallback: string) {
  if (cause instanceof ApiRequestError) return cause.message || cause.code || fallback
  return cause instanceof Error ? cause.message : fallback
}

function variableLabels(item: MessageTemplate) {
  return item.allowedVariables.map((name) => `{${name}}`).join('、')
}

onMounted(load)
watch(() => props.systemId, () => { editing.value = null; void load() })
</script>

<template>
  <section class="message-template-manager">
    <header>
      <div><h3><BellRing :size="18" />异步结果消息模板</h3><p>导入、导出和打印终态统一通过已发布模板发送站内消息。</p></div>
      <a-button :loading="loading" @click="load"><RefreshCw :size="15" />刷新</a-button>
    </header>
    <a-alert v-if="error" type="error" show-icon closable :message="error" @close="error = ''" />
    <a-spin :spinning="loading">
      <div class="template-grid">
        <article v-for="item in templates" :key="item.templateCode" :class="{ active: editing?.templateCode === item.templateCode }">
          <div><strong>{{ item.name }}</strong><code>{{ item.templateCode }}</code></div>
          <div class="template-state">
            <a-tag :color="item.publishedEnabled ? 'green' : 'default'">运行 V{{ item.publishedVersion ?? '-' }}</a-tag>
            <a-tag v-if="item.publishedSourceDraftVersion !== item.version" color="orange">有未发布修改</a-tag>
          </div>
          <div class="template-channels"><a-tag v-for="channel in item.channels" :key="channel">{{ channel }}</a-tag></div>
          <p>{{ item.titleTemplate }}</p>
          <a-button size="small" @click="edit(item)"><Pencil :size="14" />编辑</a-button>
        </article>
      </div>
      <div v-if="editing" class="template-editor">
        <div class="editor-heading"><div><strong>{{ editing.templateCode }}</strong><span>可用变量：{{ variableLabels(editing) }}</span></div></div>
        <a-form layout="vertical" :model="form" @finish="save">
          <a-form-item label="模板名称" required><a-input v-model:value="form.name" :maxlength="128" /></a-form-item>
          <a-form-item label="启用运行版本"><a-switch v-model:checked="form.enabled" /></a-form-item>
          <a-form-item label="投递渠道" required>
            <a-checkbox-group v-model:value="form.channels" class="template-channel-options" :options="channelOptions" />
            <small class="template-channel-hint">一次通知会按所选渠道形成相互独立的投递记录；任一渠道失败不会回滚其他渠道。</small>
          </a-form-item>
          <a-form-item label="消息标题" required><a-input v-model:value="form.titleTemplate" :maxlength="200" /></a-form-item>
          <a-form-item label="消息正文" required><a-textarea v-model:value="form.bodyTemplate" :rows="4" :maxlength="4000" show-count /></a-form-item>
          <div class="editor-actions"><a-button html-type="submit" :loading="saving">保存草稿</a-button><a-button type="primary" :loading="saving" @click="publish()"><Rocket :size="15" />发布</a-button></div>
        </a-form>
      </div>
    </a-spin>
  </section>
</template>

<style scoped>
.message-template-manager{display:grid;gap:16px}.message-template-manager>header,.message-template-manager h3,.editor-heading,.editor-actions{display:flex;align-items:center}.message-template-manager>header,.editor-heading{justify-content:space-between;gap:16px}.message-template-manager h3{gap:8px;margin:0}.message-template-manager header p{margin:4px 0 0;color:#64748b}.template-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.template-grid article{display:grid;gap:9px;padding:13px;border:1px solid #dce3e7;background:#fff}.template-grid article.active{border-color:#0f766e;background:#f0fdfa}.template-grid article>div:first-child{display:flex;flex-direction:column;gap:3px}.template-grid code{font-size:11px;color:#64748b}.template-grid p{margin:0;color:#475569}.template-state,.template-channels{display:flex;flex-wrap:wrap;gap:6px}.template-editor{padding:16px;border:1px solid #dce3e7;background:#f8fafc}.editor-heading{margin-bottom:14px}.editor-heading>div{display:flex;flex-direction:column;gap:4px}.editor-heading span,.template-channel-hint{font-size:12px;color:#64748b}.template-channel-options{display:flex;flex-wrap:wrap;gap:14px}.template-channel-hint{display:block;margin-top:7px;line-height:1.55}.editor-actions{justify-content:flex-end;gap:8px}@media(max-width:900px){.template-grid{grid-template-columns:1fr}}
</style>
