<script setup lang="ts">
import { Plus, RefreshCw } from 'lucide-vue-next'

import type { RuntimeQuickCreateModule } from '@/types/quickCreate'

withDefaults(defineProps<{
  open: boolean
  items: RuntimeQuickCreateModule[]
  loading?: boolean
  error?: string
}>(), {
  loading: false,
  error: '',
})

defineEmits<{
  close: []
  refresh: []
  select: [module: RuntimeQuickCreateModule]
}>()
</script>

<template>
  <a-drawer
    class="runtime-quick-create-panel"
    :open="open"
    title="快速新建"
    width="420px"
    @close="$emit('close')"
  >
    <div class="quick-create-toolbar">
      <span>选择模块后打开其真实创建表单</span>
      <a-button
        size="small"
        aria-label="刷新可新建模块"
        :loading="loading"
        @click="$emit('refresh')"
      >
        <RefreshCw :size="15" />刷新
      </a-button>
    </div>

    <a-alert v-if="error" type="error" show-icon :message="error" />
    <a-spin :spinning="loading">
      <ul v-if="items.length" class="quick-create-list">
        <li v-for="item in items" :key="item.moduleCode">
          <button type="button" @click="$emit('select', item)">
            <Plus :size="17" />
            <span>
              <strong>{{ item.moduleName }}</strong>
              <small>{{ item.moduleCode }}</small>
            </span>
          </button>
        </li>
      </ul>
      <a-empty
        v-else-if="!loading && !error"
        description="当前没有可新建记录的模块"
      />
    </a-spin>
  </a-drawer>
</template>

<style scoped>
.quick-create-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  color: #66747e;
}

.quick-create-toolbar .ant-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.quick-create-list {
  display: grid;
  gap: 8px;
  margin: 14px 0 0;
  padding: 0;
  list-style: none;
}

.quick-create-list li {
  border: 1px solid #e1e7ea;
  background: #fff;
}

.quick-create-list button {
  width: 100%;
  min-width: 0;
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  align-items: center;
  gap: 9px;
  padding: 12px;
  border: 0;
  background: transparent;
  color: #087f73;
  text-align: left;
  cursor: pointer;
}

.quick-create-list button span {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.quick-create-list strong {
  color: #263842;
}

.quick-create-list small {
  color: #73808a;
  overflow-wrap: anywhere;
}
</style>
