<script setup lang="ts">
import PlatformAdminShell from '../components/PlatformAdminShell.vue'
import ProductPageHeader from '../components/ProductPageHeader.vue'
import FixedConfigurationsView from './FixedConfigurationsView.vue'
</script>

<template>
  <PlatformAdminShell>
    <ProductPageHeader kicker="平台管理" title="平台配置" description="维护平台信息和跨系统生效的业务参数。" />
    <FixedConfigurationsView context="platform" initial-category="PLATFORM_INFO" />
  </PlatformAdminShell>
</template>
