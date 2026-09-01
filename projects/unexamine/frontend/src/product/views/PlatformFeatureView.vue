<script setup lang="ts">
import { AppstoreOutlined, ArrowRightOutlined, RobotOutlined, SafetyCertificateOutlined } from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import PlatformShell from '../components/PlatformShell.vue'
import ProductPageHeader from '../components/ProductPageHeader.vue'

const router = useRouter()
</script>

<template>
  <PlatformShell>
    <ProductPageHeader kicker="平台工作区" title="智能助手" description="从正确的业务范围进入助手，避免跨系统读取或写入数据。">
      <template #actions><a-button type="primary" @click="router.push('/platform')"><AppstoreOutlined />选择系统</a-button></template>
    </ProductPageHeader>
    <section class="panel-card platform-ai-entry">
      <div class="platform-ai-entry__icon"><RobotOutlined /></div>
      <div><h2>在业务系统内使用智能助手</h2><p>平台入口不会直接读取任何系统业务数据。选择一个已有权限的系统后，助手会在该系统和工作空间范围内查询、生成草稿，并在写入前要求你确认。</p></div>
      <a-button type="link" @click="router.push('/platform')">选择要处理的系统 <ArrowRightOutlined /></a-button>
    </section>
    <section class="platform-ai-boundary-grid">
      <article class="panel-card"><SafetyCertificateOutlined /><strong>权限不扩大</strong><p>助手沿用当前账号、字段和数据范围权限。</p></article>
      <article class="panel-card"><RobotOutlined /><strong>先生成结果</strong><p>查询给出来源，写入先生成可编辑草稿。</p></article>
      <article class="panel-card"><AppstoreOutlined /><strong>按系统隔离</strong><p>切换系统后重新建立工作范围，不混用数据。</p></article>
    </section>
  </PlatformShell>
</template>
