<template>
  <AdminLayout>
    <h2>流程实例 #{{ instanceId }}</h2>
    <div class="toolbar">
      <button type="button" @click="load">刷新</button>
      <router-link class="btn secondary" to="/flow/instances">返回</router-link>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <section v-if="instance" class="summary">
      <p><strong>标题</strong> {{ instance.title || '-' }}</p>
      <p><strong>状态</strong> {{ statusLabel(instance.status) }}</p>
      <p><strong>业务</strong> {{ instance.bizType || '-' }} / {{ instance.bizId || '-' }}</p>
      <p><strong>创建时间</strong> {{ instance.createdAt || instance.createTime || '-' }}</p>
    </section>

    <h3 v-if="tasks.length">待办/任务</h3>
    <table v-if="tasks.length" class="table">
      <thead><tr><th>ID</th><th>节点</th><th>类型</th><th>状态</th><th>处理人</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="t in tasks" :key="t.id">
          <td>{{ t.id }}</td>
          <td>{{ t.nodeName || t.nodeKey }}</td>
          <td>{{ t.taskType || '-' }}</td>
          <td>{{ taskStatusLabel(t.status) }}</td>
          <td>{{ t.assigneePlatId || t.actorPlatId || '-' }}</td>
          <td>
            <router-link
              v-if="Number(t.status) === 1"
              :to="taskTo(t)"
            >办理</router-link>
            <span v-else class="muted">-</span>
          </td>
        </tr>
      </tbody>
    </table>

    <h3 v-if="actions.length">动作记录</h3>
    <table v-if="actions.length" class="table">
      <thead><tr><th>ID</th><th>动作</th><th>节点</th><th>处理人</th><th>时间</th><th>备注</th></tr></thead>
      <tbody>
        <tr v-for="a in actions" :key="a.id">
          <td>{{ a.id }}</td>
          <td>{{ a.actionType || a.action || '-' }}</td>
          <td>{{ a.nodeKey || '-' }}</td>
          <td>{{ a.platId || a.operatorPlatId || '-' }}</td>
          <td>{{ a.createTime || a.createdAt || '-' }}</td>
          <td>{{ a.commentText || a.remark || '-' }}</td>
        </tr>
      </tbody>
    </table>

    <h3 v-if="traces.length">流转轨迹</h3>
    <table v-if="traces.length" class="table">
      <thead><tr><th>ID</th><th>节点</th><th>事件</th><th>时间</th></tr></thead>
      <tbody>
        <tr v-for="tr in traces" :key="tr.id">
          <td>{{ tr.id }}</td>
          <td>{{ tr.nodeKey || tr.nodeName || '-' }}</td>
          <td>{{ tr.traceType || tr.eventType || '-' }}</td>
          <td>{{ tr.createTime || tr.createdAt || '-' }}</td>
        </tr>
      </tbody>
    </table>

    <details v-if="detail">
      <summary>原始详情</summary>
      <pre class="json-pre">{{ JSON.stringify(detail, null, 2) }}</pre>
    </details>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import { getInstance, listInstanceActions, listInstanceTasks, listInstanceTraces } from '../api/flow.js'
import { idToString } from '../utils/id.js'

const route = useRoute()
const instanceId = computed(() => String(route.params.instanceId || ''))
const detail = ref(null)
const instance = ref(null)
const tasks = ref([])
const actions = ref([])
const traces = ref([])
const error = ref('')

const INSTANCE_STATUS = {
  1: '运行中',
  2: '已结束',
  3: '已撤回',
  4: '已终止'
}
const TASK_STATUS = {
  1: '待处理',
  2: '已同意',
  3: '已拒绝',
  4: '已转交',
  5: '已取消/跳过'
}

function statusLabel(status) {
  return INSTANCE_STATUS[Number(status)] || status || '-'
}

function taskStatusLabel(status) {
  return TASK_STATUS[Number(status)] || status || '-'
}

function taskTo(task) {
  return {
    path: '/flow/task',
    query: {
      instanceId: idToString(instanceId.value),
      taskId: idToString(task?.id)
    }
  }
}

async function load() {
  error.value = ''
  try {
    const [ir, tr, ar, traceR] = await Promise.all([
      getInstance(instanceId.value),
      listInstanceTasks(instanceId.value),
      listInstanceActions(instanceId.value),
      listInstanceTraces(instanceId.value)
    ])
    detail.value = ir.data
    instance.value = ir.data?.instance || ir.data?.record || ir.data
    tasks.value = tr.data || []
    actions.value = ar.data || []
    traces.value = traceR.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

onMounted(load)
</script>

<style scoped>
.json-pre {
  background: #f9fafb;
  padding: 0.75rem;
  border-radius: 6px;
  font-size: 0.8rem;
  overflow: auto;
}
.summary {
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 0.75rem 1rem;
  margin-bottom: 1rem;
  display: grid;
  gap: 0.35rem;
}
.summary p {
  margin: 0;
}
h3 {
  margin-top: 1.25rem;
}
</style>
