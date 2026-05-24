<template>
  <AdminLayout>
    <h2>流程任务</h2>
    <p class="muted">instanceId={{ instanceId }} taskId={{ taskId }}</p>
    <p><label>备注 <input v-model="commentText" style="min-width:240px" /></label></p>
    <div class="toolbar">
      <button type="button" @click="approve">同意</button>
      <button type="button" @click="reject">拒绝</button>
      <button type="button" class="secondary" @click="claim">领取</button>
      <button type="button" class="secondary" @click="unclaim">取消领取</button>
    </div>
    <div class="toolbar">
      <button type="button" class="secondary" @click="withdraw">撤回实例</button>
      <button type="button" class="secondary" @click="terminate">终止实例</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <pre v-if="resultText" class="pre">{{ resultText }}</pre>
  </AdminLayout>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import { actTask } from '../api/flow'

const route = useRoute()
const router = useRouter()
const instanceId = computed(() => String(route.query.instanceId || ''))
const taskId = computed(() => String(route.query.taskId || ''))
const commentText = ref('')
const error = ref('')
const resultText = ref('')
const acting = ref(false)

async function act(path, body) {
  if (!instanceId.value) return
  if (path.includes('/tasks/') && !taskId.value) return
  acting.value = true
  error.value = ''
  resultText.value = ''
  try {
    const r = await actTask(path, body)
    resultText.value = JSON.stringify(r?.data ?? null, null, 2)
    const nextTaskId = String(r?.data?.nextTaskId || '')
    if (nextTaskId && confirm('打开下一任务?')) {
      router.replace({ path: '/flow/task', query: { instanceId: instanceId.value, taskId: nextTaskId } })
    }
  } catch (e) {
    error.value = e?.message || String(e)
  } finally {
    acting.value = false
  }
}

function approve() {
  act(`/v1/system/flow/instances/${instanceId.value}/tasks/${taskId.value}/approve`, {
    commentText: commentText.value || null
  })
}
function reject() {
  act(`/v1/system/flow/instances/${instanceId.value}/tasks/${taskId.value}/reject`, {
    commentText: commentText.value || null
  })
}
function claim() {
  act(`/v1/system/flow/instances/${instanceId.value}/tasks/${taskId.value}/claim`)
}
function unclaim() {
  act(`/v1/system/flow/instances/${instanceId.value}/tasks/${taskId.value}/unclaim`)
}
function withdraw() {
  act(`/v1/system/flow/instances/${instanceId.value}/withdraw`)
}
function terminate() {
  act(`/v1/system/flow/instances/${instanceId.value}/terminate`, { reason: commentText.value || null })
}
</script>

<style src="./admin-shared.css"></style>
<style scoped>
input { padding: 0.35rem 0.5rem; border: 1px solid #d1d5db; border-radius: 6px; }
.pre { background: #fff; border: 1px solid #e5e7eb; padding: 0.75rem; border-radius: 6px; font-size: 0.85rem; }
</style>
