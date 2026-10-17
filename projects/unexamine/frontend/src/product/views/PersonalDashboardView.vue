<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, ApiError } from '../api'
import { dashboardComponentSpan, dashboardSourceLabels, moveDashboardComponent } from '../dashboard'
import ProductPage from '../components/ProductPage.vue'
import ProductPageHeader from '../components/ProductPageHeader.vue'
import { platformTokens, systemTokens } from '../session'
import type { DashboardComponentInput, DashboardComponentType, DashboardDefinition, PersonalDashboardOverview } from '../types'
import DashboardRuntimeView from './DashboardRuntimeView.vue'

const route = useRoute()
const router = useRouter()
const props = withDefaults(defineProps<{ context?: 'platform' | 'system' }>(), { context: 'system' })
const token = computed(() => props.context === 'platform' ? platformTokens.value?.accessToken : systemTokens.value?.accessToken)
const editing = computed(() => route.query.edit === '1')
const overview = ref<PersonalDashboardOverview>()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')
const runtimeRevision = ref(0)
const form = reactive({ name: '我的工作首页', description: '', components: [] as DashboardComponentInput[], expectedVersion: undefined as number | undefined })
const componentForm = reactive({ title: '', componentType: 'METRIC' as DashboardComponentType, dataSourceId: undefined as number | undefined, width: 2 })
const types: { value: DashboardComponentType; label: string }[] = [
  { value: 'METRIC', label: '核心数字' }, { value: 'CHART', label: '统计图表' },
  { value: 'RANKING', label: '业务排行' }, { value: 'LIST', label: '业务列表' },
  { value: 'PROGRESS', label: '进度指标' },
]
const widths = [
  { value: 1, label: '1/4 行' }, { value: 2, label: '1/2 行' },
  { value: 3, label: '3/4 行' }, { value: 4, label: '整行' },
]

/** 个人首页的标识只供后台绑定组件，成员无需填写技术编码。 */
function newComponentKey() {
  return `personal_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 7)}`
}

function fill(saved: DashboardDefinition | null) {
  if (!saved) {
    Object.assign(form, { name: '我的工作首页', description: '', components: [], expectedVersion: undefined })
    return
  }
  Object.assign(form, {
    name: saved.name,
    description: saved.description || '',
    expectedVersion: saved.version,
    components: saved.components.map(({ id: _id, version: _version, ...component }) => ({ ...component })),
  })
}

async function load() {
  if (!token.value) return
  loading.value = true
  error.value = ''
  try {
    overview.value = await api<PersonalDashboardOverview>('/api/analytics/personal', {}, token.value)
    fill(overview.value.dashboard)
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '个人首页暂时无法加载，请重试。'
  } finally { loading.value = false }
}

function addComponent() {
  if (!componentForm.title.trim()) { error.value = '请填写组件标题。'; return }
  if (!componentForm.dataSourceId) { error.value = '请选择要展示的数据。'; return }
  if (!overview.value?.dataSources.some(source => source.id === componentForm.dataSourceId)) {
    error.value = '所选数据已不可用，请刷新后重新选择。'; return
  }
  form.components.push({
    componentKey: newComponentKey(), componentType: componentForm.componentType,
    title: componentForm.title.trim(), dataSourceId: componentForm.dataSourceId,
    layout: { width: componentForm.width }, queryParameters: {},
    displayConfig: { refreshSeconds: 60, chartType: 'BAR', accentColor: '#315efb' },
    drillTarget: {}, sortOrder: form.components.length * 10,
  })
  componentForm.title = ''
  componentForm.dataSourceId = undefined
  error.value = ''
}

function move(from: number, to: number) {
  form.components = moveDashboardComponent(form.components, from, to)
}

function remove(index: number) {
  form.components.splice(index, 1)
  form.components = moveDashboardComponent(form.components, -1, -1)
}

async function save() {
  if (!token.value) return
  if (!form.name.trim()) { error.value = '请填写个人首页名称。'; return }
  if (!form.components.length) { error.value = '请至少加入一个业务组件。'; return }
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    await api<DashboardDefinition>('/api/analytics/personal', {
      method: 'PUT', body: JSON.stringify({
        name: form.name.trim(), description: form.description.trim(),
        components: form.components, expectedVersion: form.expectedVersion ?? null,
      }),
    }, token.value)
    await load()
    runtimeRevision.value++
    success.value = '个人首页已保存。'
    const query = { ...route.query }
    delete query.edit
    await router.replace({ path: route.path, query })
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '保存失败，请稍后重试。'
  } finally { saving.value = false }
}

function closeEditor() {
  const query = { ...route.query }
  delete query.edit
  void router.replace({ path: route.path, query })
}

watch(() => token.value, load)
onMounted(load)
</script>

<template>
  <ProductPage class="personal-dashboard-page">
    <template v-if="!editing">
      <a-alert v-if="success" type="success" show-icon :message="success" class="section-alert" />
      <DashboardRuntimeView :key="runtimeRevision" :context="context" variant="PERSONAL" />
    </template>
    <template v-else>
      <ProductPageHeader kicker="个人工作区" title="设置我的首页" description="从当前有权查看的已发布业务数据中选择内容，保存后只显示在你的首页。">
        <template #actions><a-button @click="closeEditor">返回我的首页</a-button></template>
      </ProductPageHeader>
      <a-alert v-if="error" type="error" show-icon :message="error" class="section-alert" />
      <a-skeleton v-if="loading && !overview" active />
      <template v-else>
        <section class="panel-card personal-dashboard-form">
          <div class="form-grid"><a-form-item label="首页名称" required><a-input v-model:value="form.name" maxlength="100" /></a-form-item>
            <a-form-item label="使用说明"><a-input v-model:value="form.description" maxlength="300" placeholder="例如：我每天关注的业务进展" /></a-form-item></div>
        </section>
        <section class="panel-card personal-dashboard-form">
          <div class="panel-title"><strong>添加业务组件</strong><span>数据范围始终按你当前的权限计算</span></div>
          <a-empty v-if="!overview?.dataSources.length" :image="false" description="当前没有可用于个人首页的已发布数据，请联系管理员配置数据源。" />
          <template v-else>
            <div class="form-grid"><a-form-item label="组件标题" required><a-input v-model:value="componentForm.title" placeholder="例如：本月业务金额" /></a-form-item>
              <a-form-item label="展示方式"><a-select v-model:value="componentForm.componentType" :options="types" /></a-form-item></div>
            <div class="form-grid"><a-form-item label="选择业务数据" required><a-select v-model:value="componentForm.dataSourceId" show-search option-filter-prop="label" placeholder="选择当前可用的数据" :options="overview.dataSources.map(source => ({ value: source.id, label: `${source.name} · ${dashboardSourceLabels[source.sourceType]}` }))" /></a-form-item>
              <a-form-item label="占用宽度"><a-select v-model:value="componentForm.width" :options="widths" /></a-form-item></div>
            <a-button type="dashed" block @click="addComponent">加入我的首页</a-button>
          </template>
        </section>
        <section class="personal-dashboard-grid" aria-label="我的首页布局">
          <article v-for="(component, index) in form.components" :key="component.componentKey" class="panel-card personal-dashboard-card" :style="{ gridColumn: `span ${dashboardComponentSpan(component.layout)}` }">
            <small>{{ types.find(type => type.value === component.componentType)?.label }} · {{ overview?.dataSources.find(source => source.id === component.dataSourceId)?.name || '数据已不可用' }}</small>
            <strong>{{ component.title }}</strong>
            <div><a-button size="small" :disabled="index === 0" @click="move(index, index - 1)">前移</a-button>
              <a-button size="small" :disabled="index === form.components.length - 1" @click="move(index, index + 1)">后移</a-button>
              <a-button size="small" danger @click="remove(index)">删除</a-button></div>
          </article>
          <a-empty v-if="!form.components.length" description="添加常用的业务数据，组成自己的首页。" />
        </section>
        <div class="form-actions"><a-button type="primary" :loading="saving" @click="save">保存我的首页</a-button><a-button @click="closeEditor">取消</a-button></div>
      </template>
    </template>
  </ProductPage>
</template>

<style scoped>
.personal-dashboard-form { margin-bottom: 18px; padding: 22px; }
.personal-dashboard-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; margin: 18px 0; }
.personal-dashboard-card { display: grid; gap: 12px; min-width: 0; padding: 18px; }
.personal-dashboard-card small { color: #67758b; }
.personal-dashboard-card strong { font-size: 18px; overflow-wrap: anywhere; }
.personal-dashboard-card > div { display: flex; flex-wrap: wrap; gap: 8px; }
@media (max-width: 900px) { .personal-dashboard-card { grid-column: span 4 !important; } }
</style>
