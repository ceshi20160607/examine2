<script setup lang="ts">
import { RefreshCw, Star, Trash2 } from 'lucide-vue-next'

import type { RuntimeFavoriteItem } from '@/types/favorites'

withDefaults(defineProps<{
  open: boolean
  items: RuntimeFavoriteItem[]
  page: number
  size: number
  total: number
  loading?: boolean
  error?: string
  removingId?: string
}>(), {
  loading: false,
  error: '',
  removingId: '',
})

defineEmits<{
  close: []
  refresh: []
  openFavorite: [favorite: RuntimeFavoriteItem]
  remove: [favorite: RuntimeFavoriteItem]
  pageChange: [page: number]
}>()

function updatedAt(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN')
}
</script>

<template>
  <a-drawer
    class="runtime-favorites-panel"
    :open="open"
    title="我的收藏"
    width="420px"
    @close="$emit('close')"
  >
    <div class="favorites-panel-toolbar">
      <span>当前可见 {{ total }} 项</span>
      <a-button
        size="small"
        aria-label="刷新收藏"
        :loading="loading"
        @click="$emit('refresh')"
      >
        <RefreshCw :size="15" />刷新
      </a-button>
    </div>

    <a-alert v-if="error" type="error" show-icon :message="error" />
    <a-spin :spinning="loading">
      <ul v-if="items.length" class="favorite-list">
        <li v-for="favorite in items" :key="favorite.favoriteId">
          <button
            class="favorite-target"
            type="button"
            @click="$emit('openFavorite', favorite)"
          >
            <Star :size="16" fill="currentColor" />
            <span>
              <strong>{{ favorite.displayLabel }}</strong>
              <small>
                {{ favorite.type === 'MODULE' ? '模块' : `记录 · ${favorite.moduleCode}` }}
                <template v-if="favorite.status"> · {{ favorite.status }}</template>
                · {{ updatedAt(favorite.updatedAt) }}
              </small>
            </span>
          </button>
          <a-button
            class="favorite-remove"
            size="small"
            danger
            :loading="removingId === favorite.favoriteId"
            :disabled="Boolean(removingId)"
            :aria-label="`取消收藏${favorite.displayLabel}`"
            @click="$emit('remove', favorite)"
          >
            <Trash2 :size="15" />
          </a-button>
        </li>
      </ul>
      <a-empty v-else-if="!loading && !error" description="还没有收藏模块或记录" />
    </a-spin>

    <a-pagination
      v-if="total > size"
      class="favorite-pagination"
      :current="page"
      :page-size="size"
      :total="total"
      :show-size-changer="false"
      @change="(nextPage: number) => $emit('pageChange', nextPage)"
    />
  </a-drawer>
</template>

<style scoped>
.favorites-panel-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  color: #66747e;
}

.favorites-panel-toolbar .ant-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.favorite-list {
  display: grid;
  gap: 8px;
  margin: 14px 0 0;
  padding: 0;
  list-style: none;
}

.favorite-list li {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  border: 1px solid #e1e7ea;
  background: #fff;
}

.favorite-target {
  min-width: 0;
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  align-items: center;
  gap: 9px;
  padding: 12px;
  border: 0;
  background: transparent;
  color: #a96500;
  text-align: left;
  cursor: pointer;
}

.favorite-target span {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.favorite-target strong {
  color: #263842;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.favorite-target small {
  color: #73808a;
}

.favorite-remove {
  margin-right: 8px;
}

.favorite-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}
</style>
