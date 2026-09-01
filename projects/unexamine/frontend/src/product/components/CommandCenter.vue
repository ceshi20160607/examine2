<script setup lang="ts">
import { ClockCircleOutlined, SearchOutlined, StarFilled, StarOutlined } from '@ant-design/icons-vue'
import { Empty, message } from 'ant-design-vue'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { api, ApiError } from '../api'
import { groupCommandResults, shouldOpenCommandCenter } from '../command-center'
import { platformTokens, systemTokens } from '../session'
import type { CommandCenterExecution, CommandCenterItem, CommandCenterView } from '../types'

const emit = defineEmits<{ execute: [command: CommandCenterItem] }>()
const props = withDefaults(defineProps<{ context?: 'platform' | 'system' }>(), { context: 'system' })
const open = ref(false)
const loading = ref(false)
const saving = ref(false)
const query = ref('')
const error = ref('')
const view = ref<CommandCenterView>({
  results: [], favorites: [], recent: [], state: { favoriteIds: [], recentIds: [] }, invalidated: [],
})
const searchInput = ref<{ focus?: () => void }>()
const selectedIndex = ref(0)
let searchTimer: number | undefined

const token = computed(() => props.context === 'platform'
  ? platformTokens.value?.accessToken || '' : systemTokens.value?.accessToken || '')
const groups = computed(() => groupCommandResults(view.value.results))
const flatResults = computed(() => groups.value.flatMap((group) => group.items))
const favoriteIds = computed(() => new Set(view.value.state.favoriteIds))

function readable(reason: unknown) {
  if (reason instanceof ApiError) return reason.traceId ? `${reason.message}（追踪号：${reason.traceId}）` : reason.message
  return '命令中心请求失败，请稍后重试'
}

async function show() {
  open.value = true
  query.value = ''
  selectedIndex.value = 0
  await load()
  await nextTick()
  searchInput.value?.focus?.()
}

async function load() {
  if (!token.value) return
  loading.value = true
  error.value = ''
  try {
    view.value = await api<CommandCenterView>(
      `/api/command-center?query=${encodeURIComponent(query.value.trim())}`, {}, token.value)
    selectedIndex.value = Math.min(selectedIndex.value, Math.max(0, flatResults.value.length - 1))
  } catch (reason) {
    error.value = readable(reason)
  } finally {
    loading.value = false
  }
}

async function toggleFavorite(command: CommandCenterItem) {
  if (saving.value) return
  saving.value = true
  const next = favoriteIds.value.has(command.id)
    ? view.value.state.favoriteIds.filter((id) => id !== command.id)
    : [...view.value.state.favoriteIds, command.id]
  try {
    const updated = await api<CommandCenterView>('/api/command-center/state', {
      method: 'PUT',
      body: JSON.stringify({
        favoriteIds: next,
        recentIds: view.value.state.recentIds,
        expectedVersion: view.value.state.version,
      }),
    }, token.value)
    view.value = { ...view.value, favorites: updated.favorites, recent: updated.recent, state: updated.state, invalidated: updated.invalidated }
    message.success(next.includes(command.id) ? '已加入收藏' : '已取消收藏')
  } catch (reason) {
    message.error(readable(reason))
    await load()
  } finally {
    saving.value = false
  }
}

async function execute(command: CommandCenterItem) {
  if (loading.value) return
  loading.value = true
  error.value = ''
  try {
    const result = await api<CommandCenterExecution>('/api/command-center/execute', {
      method: 'POST', body: JSON.stringify({ commandId: command.id }),
    }, token.value)
    view.value.state = result.state
    view.value.invalidated = result.invalidated
    open.value = false
    emit('execute', result.command)
  } catch (reason) {
    error.value = readable(reason)
    await load()
  } finally {
    loading.value = false
  }
}

function onSearchKeydown(event: KeyboardEvent) {
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    selectedIndex.value = Math.min(flatResults.value.length - 1, selectedIndex.value + 1)
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    selectedIndex.value = Math.max(0, selectedIndex.value - 1)
  } else if (event.key === 'Enter') {
    const selected = flatResults.value[selectedIndex.value]
    if (!selected) return
    event.preventDefault()
    void execute(selected)
  } else if (event.key === 'Escape') {
    open.value = false
  }
}

function onGlobalKeydown(event: KeyboardEvent) {
  if (!shouldOpenCommandCenter(event)) return
  event.preventDefault()
  void show()
}

watch(query, () => {
  if (!open.value) return
  window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => { selectedIndex.value = 0; void load() }, 180)
})
onMounted(() => window.addEventListener('keydown', onGlobalKeydown))
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onGlobalKeydown)
  window.clearTimeout(searchTimer)
})
</script>

<template>
  <a-button type="text" class="command-trigger" @click="show">
    <SearchOutlined /><span>全局搜索</span><kbd>Ctrl K</kbd>
  </a-button>
  <a-modal v-model:open="open" title="全局命令中心" :footer="null" width="760px" :destroy-on-close="false">
    <a-input
      ref="searchInput"
      v-model:value="query"
      size="large"
      allow-clear
      placeholder="搜索菜单、模块、记录或“新建…”"
      @keydown="onSearchKeydown"
    ><template #prefix><SearchOutlined /></template></a-input>
    <p class="command-hint">↑ ↓ 选择 · Enter 打开 · Esc 关闭；结果按当前系统、租户与实时权限返回。</p>
    <a-alert v-if="error" type="error" show-icon :message="error" class="section-alert"><template #action><a-button size="small" @click="load">重试</a-button></template></a-alert>
    <a-alert v-if="view.invalidated.length" type="warning" show-icon class="section-alert"
      message="部分收藏或最近命令已移除" :description="view.invalidated.map((item) => `${item.id}：${item.reason}`).join('；')" />
    <a-spin :spinning="loading">
      <section v-if="!query && view.favorites.length" class="command-section">
        <div class="command-section__title"><StarFilled />收藏</div>
        <button v-for="command in view.favorites" :key="`favorite-${command.id}`" class="command-row" @click="execute(command)">
          <span><strong>{{ command.label }}</strong><small>{{ command.description }}</small></span>
          <a-button type="text" aria-label="取消收藏" :loading="saving" @click.stop="toggleFavorite(command)"><StarFilled /></a-button>
        </button>
      </section>
      <section v-if="!query && view.recent.length" class="command-section">
        <div class="command-section__title"><ClockCircleOutlined />最近使用</div>
        <button v-for="command in view.recent" :key="`recent-${command.id}`" class="command-row" @click="execute(command)">
          <span><strong>{{ command.label }}</strong><small>{{ command.description }}</small></span>
          <a-button type="text" :aria-label="favoriteIds.has(command.id) ? '取消收藏' : '加入收藏'" :loading="saving" @click.stop="toggleFavorite(command)">
            <StarFilled v-if="favoriteIds.has(command.id)" /><StarOutlined v-else />
          </a-button>
        </button>
      </section>
      <section v-for="group in groups" :key="group.code" class="command-section">
        <div class="command-section__title">{{ group.name }}</div>
        <button v-for="command in group.items" :key="command.id" class="command-row"
          :class="{ selected: flatResults[selectedIndex]?.id === command.id }" @mouseenter="selectedIndex = flatResults.findIndex((item) => item.id === command.id)" @click="execute(command)">
          <span><strong>{{ command.label }}</strong><small>{{ command.description }}</small></span>
          <a-button type="text" :aria-label="favoriteIds.has(command.id) ? '取消收藏' : '加入收藏'" :loading="saving" @click.stop="toggleFavorite(command)">
            <StarFilled v-if="favoriteIds.has(command.id)" /><StarOutlined v-else />
          </a-button>
        </button>
      </section>
      <a-empty v-if="!loading && !groups.length && !view.favorites.length && !view.recent.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="没有匹配且可执行的命令" />
    </a-spin>
  </a-modal>
</template>
