<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PlatformAdminShell from '../components/PlatformAdminShell.vue'
import ProductPage from '../components/ProductPage.vue'
import ProductPageHeader from '../components/ProductPageHeader.vue'
import FixedConfigurationsView from './FixedConfigurationsView.vue'

const route = useRoute()
const router = useRouter()
const category = computed(() => String(route.query.category || 'PLATFORM_INFO'))
const titles: Record<string, { title: string; description: string }> = {
  PLATFORM_INFO: { title: '平台信息', description: '维护平台资料与全局身份接入。' },
  ORGANIZATION: { title: '组织架构', description: '维护平台成员及其组织关系。' },
  AUTHORIZATION: { title: '角色权限', description: '维护平台管理员角色与访问范围。' },
  DICTIONARY: { title: '数据字典', description: '维护跨系统复用的公共基础数据。' },
  SYSTEM: { title: '系统配置', description: '维护系统创建规则与全局系统策略。' },
  BUSINESS_PARAMETER: { title: '其他业务参数', description: '维护平台首页、消息与其他平台级业务参数。' },
  AUDIT: { title: '审计日志', description: '维护平台审计策略与留存参数。' },
  OPERATIONS: { title: '运维配置', description: '维护平台运行、健康与恢复策略。' },
}
const heading = computed(() => titles[category.value] ?? { title: '平台信息', description: '维护平台资料与全局身份接入。' })
</script>

<template>
  <PlatformAdminShell>
    <ProductPage density="configuration">
      <ProductPageHeader density="configuration" kicker="平台管理" :title="heading.title" :description="heading.description" />
      <nav v-if="category === 'PLATFORM_INFO'" class="admin-subnav admin-subnav--text" aria-label="平台信息任务">
        <button class="active" type="button"><span><strong>平台资料</strong><small>平台名称与基础信息</small></span></button>
        <button type="button" @click="router.push('/platform/admin/identity-providers')"><span><strong>统一认证</strong><small>身份源、测试与发布</small></span></button>
      </nav>
      <nav v-else-if="category === 'BUSINESS_PARAMETER'" class="admin-subnav admin-subnav--text" aria-label="平台业务参数任务">
        <button class="active" type="button"><span><strong>参数配置</strong><small>平台级业务参数</small></span></button>
        <button type="button" @click="router.push('/platform/admin/dashboards')"><span><strong>首页配置</strong><small>组件、布局与发布</small></span></button>
        <button type="button" @click="router.push('/platform/admin/messages')"><span><strong>消息模板</strong><small>模板、渠道与诊断</small></span></button>
      </nav>
      <FixedConfigurationsView context="platform" :initial-category="category" single-category />
    </ProductPage>
  </PlatformAdminShell>
</template>
