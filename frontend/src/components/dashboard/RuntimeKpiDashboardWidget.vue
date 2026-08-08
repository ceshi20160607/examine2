<script setup lang="ts">
import RuntimeKpiCard from '@/components/kpi/RuntimeKpiCard.vue'
import type { KpiTarget } from '@/types/kpi'

defineProps<{ targets: KpiTarget[] }>()
</script>

<template>
  <div class="runtime-kpi-dashboard-widget">
    <div v-if="!targets.length" class="runtime-kpi-empty">
      当前周期没有适用于你的 KPI 目标
    </div>
    <div v-else class="runtime-kpi-targets" :data-target-count="targets.length">
      <RuntimeKpiCard v-for="target in targets" :key="target.id" :target="target" />
    </div>
  </div>
</template>

<style scoped>
.runtime-kpi-dashboard-widget { min-height: 0; flex: 1; overflow: auto; padding: 12px; }
.runtime-kpi-targets { display: grid; gap: 12px; }
.runtime-kpi-empty { display: grid; min-height: 100%; place-content: center; color: #78858d; text-align: center; }
</style>
