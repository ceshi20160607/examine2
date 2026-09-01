<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import PlatformShell from '../components/PlatformShell.vue'
import FlowRuntimeView from './FlowRuntimeView.vue'

const route = useRoute()
const initialInstanceId = computed(() => Number(route.query.instanceId) || undefined)
const initialTaskId = computed(() => Number(route.query.taskId) || undefined)
</script>

<template>
  <PlatformShell>
    <FlowRuntimeView context="platform" :initial-instance-id="initialInstanceId" :initial-task-id="initialTaskId" />
  </PlatformShell>
</template>
