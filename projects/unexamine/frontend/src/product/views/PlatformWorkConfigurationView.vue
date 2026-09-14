<script setup lang="ts">
import PlatformAdminShell from '../components/PlatformAdminShell.vue'
import ProductPage from '../components/ProductPage.vue'
import WorkConfigurationView from './WorkConfigurationView.vue'
</script>

<template>
  <PlatformAdminShell>
    <ProductPage density="configuration">
      <WorkConfigurationView context="platform" mode="configuration" />
    </ProductPage>
  </PlatformAdminShell>
</template>
