<script setup lang="ts">
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { api, ApiError } from '../api'
import { systemTokens } from '../session'
import type { AuditEvent, AuditEventList } from '../types'

const loading = ref(false)
const error = ref('')
const events = ref<AuditEvent[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const filters = reactive({ traceId: '', eventCode: '', objectType: '', objectId: '', resultCode: '' })
const token = computed(() => systemTokens.value?.accessToken || '')

async function load() {
  loading.value = true
  error.value = ''
  const parameters = new URLSearchParams({ page: String(page.value), pageSize: String(pageSize.value) })
  Object.entries(filters).forEach(([key, value]) => { if (value.trim()) parameters.set(key, value.trim()) })
  try {
    const result = await api<AuditEventList>(`/api/admin/audit-events?${parameters}`, {}, token.value)
    events.value = result.events
    total.value = result.total
  } catch (reason) {
    error.value = reason instanceof ApiError
      ? (reason.traceId ? `${reason.message}（追踪号：${reason.traceId}）` : reason.message)
      : '审计记录加载失败'
  } finally { loading.value = false }
}

function search() { page.value = 1; void load() }
onMounted(load)
</script>

<template>
  <div class="audit-page">
    <div class="page-heading"><div><p class="eyebrow">后台配置</p><h1>操作审计</h1><p>按当前系统与租户查询，审计记录不提供业务删除操作。</p></div>
      <a-button :loading="loading" @click="load"><ReloadOutlined />刷新</a-button></div>
    <a-alert v-if="error" type="error" show-icon :message="error" class="section-alert" />
    <section class="audit-filters panel-card">
      <a-input v-model:value="filters.traceId" placeholder="追踪号" allow-clear />
      <a-input v-model:value="filters.eventCode" placeholder="事件编码" allow-clear />
      <a-input v-model:value="filters.objectType" placeholder="对象类型" allow-clear />
      <a-input v-model:value="filters.objectId" placeholder="对象 ID" allow-clear />
      <a-select v-model:value="filters.resultCode" placeholder="执行结果" allow-clear :options="[
        { value: 'SUCCESS', label: '成功' }, { value: 'PERMISSION_DENIED', label: '权限拒绝' },
        { value: 'FIELD_VALIDATION_FAILED', label: '字段校验失败' },
      ]" />
      <a-button type="primary" @click="search"><SearchOutlined />查询</a-button>
    </section>
    <section class="audit-table panel-card">
      <a-table :data-source="events" :loading="loading" row-key="id" :pagination="false" :scroll="{ x: 1050 }">
        <a-table-column title="时间" data-index="occurredAt" :width="175" />
        <a-table-column title="事件" data-index="eventCode" :width="220" />
        <a-table-column title="结果" data-index="resultCode" :width="150"><template #default="{ text }"><a-tag :color="text === 'SUCCESS' ? 'green' : 'orange'">{{ text }}</a-tag></template></a-table-column>
        <a-table-column title="对象" :width="200"><template #default="{ record }">{{ record.objectType || '—' }} · {{ record.objectId || '—' }}</template></a-table-column>
        <a-table-column title="成员" data-index="memberId" :width="90" />
        <a-table-column title="追踪号" data-index="traceId" :width="280"><template #default="{ text }"><code>{{ text }}</code></template></a-table-column>
      </a-table>
      <div class="record-pagination"><a-pagination v-model:current="page" v-model:page-size="pageSize" :total="total" show-size-changer @change="load" /></div>
    </section>
  </div>
</template>
