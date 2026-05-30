<template>
  <view />
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { hasToken } from '@/utils/guard'
import { getSessionPayload } from '@/store/context'

onMounted(() => {
  if (!hasToken()) {
    uni.reLaunch({ url: '/pages/auth/login' })
    return
  }
  const p = getSessionPayload()
  if (p?.systemId) {
    uni.switchTab({ url: '/pages/tabs/workbench' })
  } else {
    uni.reLaunch({ url: '/pages/platform/systems' })
  }
})
</script>
