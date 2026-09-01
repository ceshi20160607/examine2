<script setup lang="ts">
import PlatformAdminShell from '../components/PlatformAdminShell.vue'
import FlowDesignerView from './FlowDesignerView.vue'
</script>

<template>
  <PlatformAdminShell>
    <FlowDesignerView context="platform" />
  </PlatformAdminShell>
</template>
