<script setup lang="ts">
import { Clock3, RefreshCw } from 'lucide-vue-next'

import type { RuntimeRecentRecordItem } from '@/types/recentRecords'

withDefaults(defineProps<{
  open: boolean
  items: RuntimeRecentRecordItem[]
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
  refresh: []
  openRecord: [record: RuntimeRecentRecordItem]
  pageChange: [page: number]
}>()

function accessedAt(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN')
}
</script>

<template>
  <a-drawer
    class="runtime-recent-records-panel"
    :open="open"
    title="最近访问"
    width="420px"
    @close="$emit('close')"
  >
    <div class="recent-panel-toolbar">
      <span>当前可见 {{ total }} 条记录</span>
      <a-button
        size="small"
        aria-label="刷新最近访问"
        :loading="loading"
        @click="$emit('refresh')"
      >
        <RefreshCw :size="15" />刷新
      </a-button>
    </div>

    <a-alert v-if="error" type="error" show-icon :message="error" />
    <a-spin :spinning="loading">
      <ul v-if="items.length" class="recent-record-list">
        <li v-for="record in items" :key="record.recentId">
          <button type="button" @click="$emit('openRecord', record)">
            <Clock3 :size="16" />
            <span>
              <strong>{{ record.displayLabel }}</strong>
              <small>
                {{ record.moduleCode }} · {{ record.status }} · 访问 {{ record.accessCount }} 次
                · {{ accessedAt(record.lastAccessedAt) }}
              </small>
            </span>
          </button>
        </li>
      </ul>
      <a-empty v-else-if="!loading && !error" description="还没有最近访问的记录" />
    </a-spin>

    <a-pagination
      v-if="total > size"
      class="recent-pagination"
      :current="page"
      :page-size="size"
      :total="total"
      :show-size-changer="false"
      @change="(nextPage: number) => $emit('pageChange', nextPage)"
    />
  </a-drawer>
</template>

<style scoped>
.recent-panel-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  color: #66747e;
}

.recent-panel-toolbar .ant-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.recent-record-list {
  display: grid;
  gap: 8px;
  margin: 14px 0 0;
  padding: 0;
  list-style: none;
}

.recent-record-list li {
  border: 1px solid #e1e7ea;
  background: #fff;
}

.recent-record-list button {
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

.recent-record-list button span {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.recent-record-list strong {
  color: #263842;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-record-list small {
  color: #73808a;
}

.recent-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}
</style>
