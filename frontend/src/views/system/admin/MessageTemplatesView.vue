<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'

import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import MessageTemplateManager from '@/components/admin/MessageTemplateManager.vue'
import EventChannelManager from '@/views/system/admin/EventChannelManager.vue'
import EventDeliveryLogManager from '@/views/system/admin/EventDeliveryLogManager.vue'

const route = useRoute()
const systemId = computed(() => String(route.params.systemId))
const activeTab = ref('templates')
</script>

<template>
  <section class="admin-page message-templates-page">
    <AdminPageHeader
      title="消息模板与投递"
      description="维护多渠道模板、脱敏外部渠道配置，并审计每一条独立投递记录。"
    />
    <a-tabs v-model:active-key="activeTab" class="admin-tabs event-administration-tabs">
      <a-tab-pane key="templates" tab="消息模板">
        <MessageTemplateManager :system-id="systemId" />
      </a-tab-pane>
      <a-tab-pane key="channels" tab="渠道配置">
        <EventChannelManager v-if="activeTab === 'channels'" :system-id="systemId" />
      </a-tab-pane>
      <a-tab-pane key="delivery-logs" tab="投递日志">
        <EventDeliveryLogManager v-if="activeTab === 'delivery-logs'" :system-id="systemId" />
      </a-tab-pane>
    </a-tabs>
  </section>
</template>

<style scoped>
.message-templates-page{display:grid;gap:0}.event-administration-tabs{min-width:0}
</style>
