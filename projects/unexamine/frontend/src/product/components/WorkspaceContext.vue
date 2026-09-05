<script setup lang="ts">
import { SwapOutlined } from '@ant-design/icons-vue'
import { computed } from 'vue'

const props = defineProps<{
  scope: 'platform' | 'system' | 'platform-admin' | 'system-admin'
  name: string
  subtitle: string
  interactive?: boolean
}>()

const scopeLabel = computed(() => ({
  platform: '平台工作区',
  system: '系统工作区',
  'platform-admin': '平台管理',
  'system-admin': '系统管理',
})[props.scope])

const mark = computed(() => props.scope === 'platform' ? '全' : props.scope.includes('admin') ? '管' : props.name.slice(0, 1) || '系')
</script>

<template>
  <span class="workspace-context" :data-scope="scope">
    <span class="workspace-context__mark" aria-hidden="true">{{ mark }}</span>
    <span class="workspace-context__copy">
      <small>{{ scopeLabel }}</small>
      <strong>{{ name }}</strong>
      <span>{{ subtitle }}</span>
    </span>
    <SwapOutlined v-if="interactive" class="workspace-context__switch" aria-hidden="true" />
  </span>
</template>
