<script setup lang="ts">
import { Download, FileImage, Paperclip } from 'lucide-vue-next'
import { computed, ref, watch } from 'vue'

import { fileApi } from '@/services/file'
import { useSessionStore } from '@/stores/session'
import type { RuntimeFieldValue } from '@/types/config'
import type { FileAsset } from '@/types/file'

const props = defineProps<{ field: RuntimeFieldValue; systemId?: string }>()
const session = useSessionStore()
const assets = ref<Record<string, FileAsset>>({})
const ids = computed(() => Array.isArray(props.field.value) ? props.field.value.map(String) : [])
const activeSystemId = computed(() => props.systemId || session.context?.systemId || '')
const image = computed(() => ['IMAGE', 'SIGNATURE'].includes(props.field.type))

watch([ids, activeSystemId], async ([next, systemId]) => {
  if (!systemId) return
  const missing = next.filter((id) => !assets.value[id])
  const values = await Promise.allSettled(missing.map((id) => fileApi.get(systemId, id)))
  values.forEach((value, index) => { if (value.status === 'fulfilled') assets.value[missing[index]!] = value.value })
}, { immediate: true })

async function download(fileId: string) {
  if (!activeSystemId.value) return
  const result = await fileApi.download(activeSystemId.value, fileId)
  const url = URL.createObjectURL(result.blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = result.filename || assets.value[fileId]?.originalName || `file-${fileId}`
  anchor.click()
  URL.revokeObjectURL(url)
}
</script>

<template>
  <div v-if="ids.length" class="runtime-file-display">
    <button v-for="fileId in ids" :key="fileId" type="button" @click="download(fileId)">
      <img v-if="image && activeSystemId" :src="fileApi.thumbnailUrl(activeSystemId, fileId, 64, 64)" alt="">
      <FileImage v-else-if="image" :size="20" /><Paperclip v-else :size="16" />
      <span>{{ assets[fileId]?.originalName || `文件 ${fileId}` }}</span><Download :size="14" />
    </button>
  </div>
  <span v-else>—</span>
</template>

<style scoped>
.runtime-file-display{display:flex;flex-wrap:wrap;gap:6px}.runtime-file-display button{display:inline-flex;align-items:center;gap:6px;max-width:260px;padding:5px 8px;border:1px solid #d9e2e6;background:#fff;color:#245b75;cursor:pointer}.runtime-file-display img{width:34px;height:34px;object-fit:cover}.runtime-file-display span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
</style>
