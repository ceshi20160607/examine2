<script setup lang="ts">
import { ref } from 'vue'
import WorkLogsView from './WorkLogsView.vue'
import WorkProjectsView from './WorkProjectsView.vue'
import WorkConfigurationView from './WorkConfigurationView.vue'
import OrdinaryTasksView from './OrdinaryTasksView.vue'

withDefaults(defineProps<{ context?: 'platform' | 'system' }>(), { context: 'platform' })
const active = ref<'ordinary' | 'projects' | 'logs' | 'configuration' | 'calendar'>('ordinary')
</script>

<template>
  <div class="work-hub-page">
    <a-segmented v-model:value="active" class="work-hub-tabs" :options="[
      { value: 'ordinary', label: '普通任务' },
      { value: 'projects', label: '项目任务' },
      { value: 'logs', label: '每日日志' },
      { value: 'calendar', label: '日历统计' },
      { value: 'configuration', label: '工作配置' },
    ]" />
    <OrdinaryTasksView v-if="active === 'ordinary'" :context="context" />
    <WorkProjectsView v-else-if="active === 'projects'" :context="context" />
    <WorkLogsView v-else-if="active === 'logs'" :context="context" />
    <WorkConfigurationView v-else-if="active === 'calendar'" :key="`${context}:calendar`" :context="context" mode="calendar" />
    <WorkConfigurationView v-else :key="`${context}:configuration`" :context="context" mode="configuration" />
  </div>
</template>
