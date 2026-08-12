<script setup lang="ts">
import { Search } from 'lucide-vue-next'

import type { RuntimeGlobalSearchItem } from '@/types/globalSearch'

withDefaults(defineProps<{
  open: boolean
  keyword: string
  items: RuntimeGlobalSearchItem[]
  page: number
  size: number
  total: number
  loading?: boolean
  error?: string
}>(), {
  loading: false,
  error: '',
})

defineEmits<{
  close: []
  'update:keyword': [keyword: string]
  search: []
  openRecord: [record: RuntimeGlobalSearchItem]
  pageChange: [page: number]
}>()
</script>

<template>
  <a-drawer
    class="runtime-global-search-panel"
    :open="open"
    title="全局搜索"
    width="480px"
    @close="$emit('close')"
  >
    <a-input-search
      class="global-search-input"
      :value="keyword"
      placeholder="搜索可见模块中的记录"
      enter-button="搜索"
      allow-clear
      :maxlength="100"
      :loading="loading"
      @update:value="$emit('update:keyword', String($event ?? ''))"
      @search="$emit('search')"
    />
    <p class="global-search-hint">输入 2–100 个字符，只搜索当前有权访问的活动记录。</p>

    <a-alert v-if="error" type="error" show-icon :message="error" />

    <a-spin :spinning="loading">
      <div v-if="items.length" class="global-search-results">
        <p class="global-search-summary">找到 {{ total }} 条结果</p>
        <ul>
          <li
            v-for="record in items"
            :key="`${record.moduleCode}:${record.recordId}`"
          >
            <button type="button" @click="$emit('openRecord', record)">
              <Search :size="16" />
              <span>
                <strong>{{ record.displayLabel }}</strong>
                <small>
                  {{ record.moduleName }}（{{ record.moduleCode }}）
                  · {{ record.recordNo }} · {{ record.status }}
                </small>
                <small v-if="record.matchedFieldCodes.length">
                  匹配字段：{{ record.matchedFieldCodes.join('、') }}
                </small>
              </span>
            </button>
          </li>
        </ul>
      </div>
      <a-empty
        v-else-if="!loading && !error && keyword.trim().length >= 2"
        description="没有找到匹配的活动记录"
      />
    </a-spin>

    <a-pagination
      v-if="total > size"
      class="global-search-pagination"
      :current="page"
      :page-size="size"
      :total="total"
      :show-size-changer="false"
      @change="(nextPage: number) => $emit('pageChange', nextPage)"
    />
  </a-drawer>
</template>

<style scoped>
.global-search-hint {
  margin: 8px 0 14px;
  color: #73808a;
  font-size: 12px;
}

.global-search-summary {
  margin: 14px 0 8px;
  color: #66747e;
}

.global-search-results ul {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.global-search-results li {
  border: 1px solid #e1e7ea;
  background: #fff;
}

.global-search-results button {
  width: 100%;
  min-width: 0;
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  align-items: start;
  gap: 9px;
  padding: 12px;
  border: 0;
  background: transparent;
  color: #087f73;
  text-align: left;
  cursor: pointer;
}

.global-search-results button span {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.global-search-results strong {
  color: #263842;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.global-search-results small {
  color: #73808a;
  overflow-wrap: anywhere;
}

.global-search-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}
</style>
