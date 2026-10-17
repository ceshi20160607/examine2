<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import PlatformAdminShell from '../components/PlatformAdminShell.vue'
import ProductPage from '../components/ProductPage.vue'
import WorkConfigurationView from './WorkConfigurationView.vue'
import { hasPlatformAdminCapability } from '../permissions'
import { platformContext } from '../session'

const router = useRouter()
const canManageParameters = computed(() => hasPlatformAdminCapability(platformContext.value?.permissions, 'configuration'))
const canManageMessages = computed(() => hasPlatformAdminCapability(platformContext.value?.permissions, 'message'))
</script>

<template>
  <PlatformAdminShell>
    <ProductPage density="configuration">
      <nav class="admin-subnav admin-subnav--text" aria-label="平台业务参数任务">
        <button v-if="canManageParameters" type="button" @click="router.push('/platform/admin/configurations?category=BUSINESS_PARAMETER')"><span><strong>参数配置</strong><small>平台级业务参数</small></span></button>
        <button class="active" type="button"><span><strong>工作配置</strong><small>任务、日志字段与统计口径</small></span></button>
        <button v-if="canManageMessages" type="button" @click="router.push('/platform/admin/messages')"><span><strong>消息模板</strong><small>模板、渠道与诊断</small></span></button>
      </nav>
      <WorkConfigurationView context="platform" mode="configuration" />
    </ProductPage>
  </PlatformAdminShell>
</template>
