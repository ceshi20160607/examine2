<script setup lang="ts">
import { computed } from 'vue'
import { MoreOutlined } from '@ant-design/icons-vue'
import { actionsAtLocation, type ActionLocation } from '../action-placement'
import type { ConfiguredAction } from '../types'
const props = defineProps<{ actions: ConfiguredAction[]; location: ActionLocation; can: (code: string) => boolean; disabledReason?: (code: string) => string; moduleName?: string; menu?: boolean; size?: 'small' | 'middle' }>()
const emit = defineEmits<{ run: [code: string] }>()
const visible = computed(() => actionsAtLocation(props.actions, props.location, props.can))
function label(action: ConfiguredAction) { return action.code === 'CREATE' && props.moduleName ? `${action.name}${props.moduleName}` : action.name }
function run(code: string) { if (props.can(code) && !props.disabledReason?.(code)) emit('run', code) }
</script>
<template>
  <div v-if="visible.length" class="runtime-action-group" :data-action-location="location" @click.stop>
    <a-dropdown v-if="menu" trigger="click"><a-button :size="size"><MoreOutlined />更多操作</a-button><template #overlay><a-menu @click="run(String($event.key))"><a-menu-item v-for="action in visible" :key="action.code" :disabled="Boolean(disabledReason?.(action.code))" :danger="action.code === 'DELETE'">{{ label(action) }}</a-menu-item></a-menu></template></a-dropdown>
    <template v-else><a-tooltip v-for="action in visible" :key="action.code" :title="disabledReason?.(action.code)"><a-button :size="size" :type="action.code === 'CREATE' || action.code === 'UPDATE' ? 'primary' : 'default'" :danger="action.code === 'DELETE'" :disabled="Boolean(disabledReason?.(action.code))" :data-action-code="action.code" @click="run(action.code)">{{ label(action) }}</a-button></a-tooltip></template>
  </div>
</template>
<style scoped>
.runtime-action-group { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
</style>
