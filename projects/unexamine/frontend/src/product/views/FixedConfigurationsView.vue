<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ReloadOutlined, SaveOutlined } from '@ant-design/icons-vue'
import { api, ApiError } from '../api'
import { platformTokens, systemTokens } from '../session'
import type { ContextSetting, ContextSettingCategory } from '../types'
import FoundationControlsView from './FoundationControlsView.vue'
import DictionaryConfigurationView from './DictionaryConfigurationView.vue'

const props = withDefaults(defineProps<{
  context: 'platform' | 'system'
  initialCategory?: string
}>(), { initialCategory: '' })

const categories = ref<ContextSettingCategory[]>([])
const settings = ref<ContextSetting[]>([])
const selectedCategory = ref('')
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')
const form = reactive({ settingKey: '', value: '{\n  \n}', sensitive: false, expectedVersion: undefined as number | undefined })

const basePath = computed(() => `/api/admin/${props.context}/configurations`)
const token = computed(() => props.context === 'platform' ? platformTokens.value?.accessToken : systemTokens.value?.accessToken)
const selectedName = computed(() => categories.value.find(item => item.code === selectedCategory.value)?.name || '')
const visibleSettings = computed(() => settings.value.filter(item => item.category === selectedCategory.value))

function resetForm() {
  Object.assign(form, { settingKey: '', value: '{\n  \n}', sensitive: false, expectedVersion: undefined })
  error.value = ''
  success.value = ''
}

function edit(setting: ContextSetting) {
  form.settingKey = setting.settingKey
  form.value = typeof setting.value === 'string' ? setting.value : JSON.stringify(setting.value, null, 2)
  form.sensitive = setting.sensitive
  form.expectedVersion = setting.version
  error.value = ''
  success.value = ''
}

async function load() {
  if (!token.value) return
  loading.value = true
  error.value = ''
  try {
    const [catalog, current] = await Promise.all([
      api<ContextSettingCategory[]>(`${basePath.value}/catalog`, {}, token.value),
      api<ContextSetting[]>(basePath.value, {}, token.value),
    ])
    categories.value = catalog
    settings.value = current
    if (!catalog.some(item => item.code === selectedCategory.value)) {
      selectedCategory.value = catalog.some(item => item.code === props.initialCategory)
        ? props.initialCategory : catalog[0]?.code || ''
    }
  } catch (cause) {
    error.value = cause instanceof ApiError ? `${cause.message}（${cause.code}）` : '后台配置加载失败'
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!token.value || !selectedCategory.value || !form.settingKey.trim()) return
  let value: unknown = form.value
  try {
    value = JSON.parse(form.value)
  } catch {
    value = form.value
  }
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    const saved = await api<ContextSetting>(basePath.value, {
      method: 'POST',
      body: JSON.stringify({
        category: selectedCategory.value,
        settingKey: form.settingKey.trim(),
        value,
        sensitive: form.sensitive,
        expectedVersion: form.expectedVersion,
      }),
    }, token.value)
    const reread = await api<ContextSetting[]>(basePath.value, {}, token.value)
    settings.value = reread
    const current = reread.find(item => item.id === saved.id)
    if (!current || current.version !== saved.version) throw new Error('saved version was not readable')
    edit(current)
    success.value = `配置已保存并从当前${props.context === 'platform' ? '平台' : '系统'}上下文读回，版本 v${current.version}。`
  } catch (cause) {
    error.value = cause instanceof ApiError ? `${cause.message}（${cause.code}）` : '配置保存后未能读回，请刷新重试'
  } finally {
    saving.value = false
  }
}

watch(selectedCategory, resetForm)
onMounted(load)
</script>

<template>
  <div class="fixed-config-layout" :class="{ 'is-loading': loading }">
    <section class="panel-card fixed-config-menu">
      <div class="panel-title"><strong>固定一级菜单</strong><a-button type="text" size="small" @click="load"><ReloadOutlined /></a-button></div>
      <button v-for="category in categories" :key="category.code" :class="{ active: selectedCategory === category.code }" @click="selectedCategory = category.code">
        <span>{{ category.name.slice(0, 1) }}</span><strong>{{ category.name }}</strong><small>{{ category.code }}</small>
      </button>
    </section>

    <div class="fixed-config-content">
      <a-alert v-if="error" type="error" show-icon :message="error" closable @close="error = ''" />
      <a-alert v-if="success" type="success" show-icon :message="success" closable @close="success = ''" />
      <FoundationControlsView v-if="context === 'system' && selectedCategory === 'OPERATIONS'" />
      <DictionaryConfigurationView v-if="context === 'system' && selectedCategory === 'DICTIONARY'" />
      <section v-if="selectedCategory !== 'DICTIONARY'" class="panel-card">
        <div class="panel-title"><strong>{{ selectedName }}</strong><span>{{ visibleSettings.length }} 项配置</span></div>
        <div v-if="visibleSettings.length" class="fixed-setting-list">
          <button v-for="setting in visibleSettings" :key="setting.id" @click="edit(setting)">
            <span><strong>{{ setting.settingKey }}</strong><small>{{ setting.valueType }} · {{ setting.updatedAt }}</small></span>
            <a-tag color="blue">v{{ setting.version }}</a-tag>
          </button>
        </div>
        <a-empty v-else description="当前分类还没有配置，可直接新建。" class="fixed-config-empty" />
      </section>

      <section v-if="selectedCategory !== 'DICTIONARY'" class="panel-card fixed-setting-editor">
        <div class="panel-title"><strong>{{ form.expectedVersion === undefined ? '新建配置' : '编辑配置' }}</strong><a-button v-if="form.expectedVersion !== undefined" type="link" @click="resetForm">新建另一项</a-button></div>
        <a-form layout="vertical">
          <a-form-item label="配置键" required extra="小写英文开头，可使用数字、点、横线和下划线">
            <a-input v-model:value="form.settingKey" placeholder="例如 contact.follow-up-days" :disabled="form.expectedVersion !== undefined" />
          </a-form-item>
          <a-form-item label="配置值" required extra="合法 JSON 会按对象保存，普通文本按字符串保存">
            <a-textarea v-model:value="form.value" :rows="8" class="config-value-editor" />
          </a-form-item>
          <a-form-item><a-checkbox v-model:checked="form.sensitive">敏感配置（页面仍不展示明文扩散）</a-checkbox></a-form-item>
          <a-button type="primary" :loading="saving" :disabled="!selectedCategory || !form.settingKey.trim()" @click="save"><SaveOutlined />保存并读回</a-button>
        </a-form>
      </section>
    </div>
  </div>
</template>
