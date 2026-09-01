<script setup lang="ts">
import { useRouter } from 'vue-router'
import PlatformShell from '../components/PlatformShell.vue'
import TodoWorkspaceView from './TodoWorkspaceView.vue'

const router = useRouter()
function openFlow(target: { taskId?: number; instanceId?: number }) {
  void router.push({ path: '/platform/flows', query: {
    ...(target.taskId ? { taskId: String(target.taskId) } : {}),
    ...(target.instanceId ? { instanceId: String(target.instanceId) } : {}),
  } })
}
</script>

<template>
  <PlatformShell><TodoWorkspaceView context="platform" @open-flow="openFlow" /></PlatformShell>
</template>
