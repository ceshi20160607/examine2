<script setup lang="ts">
import PlatformAdminShell from '../components/PlatformAdminShell.vue'
import ProductPageHeader from '../components/ProductPageHeader.vue'
import AiConfigurationView from './AiConfigurationView.vue'
</script>

<template>
  <PlatformAdminShell>
    <ProductPageHeader kicker="平台管理" title="智能助手" description="管理平台可用模型、使用额度和系统授权。" />
    <AiConfigurationView context="platform" />
  </PlatformAdminShell>
</template>
