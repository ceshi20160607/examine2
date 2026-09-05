<script setup lang="ts">
import PlatformAdminShell from '../components/PlatformAdminShell.vue'
import ProductPage from '../components/ProductPage.vue'
import ProductPageHeader from '../components/ProductPageHeader.vue'
import ApplicationManagementView from './ApplicationManagementView.vue'
</script>

<template>
  <PlatformAdminShell>
    <ProductPage density="configuration">
      <ProductPageHeader density="configuration" kicker="平台管理" title="应用配置" description="维护平台应用、访问凭证与授权范围。" />
      <ApplicationManagementView context="platform" />
    </ProductPage>
  </PlatformAdminShell>
</template>
