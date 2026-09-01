<script setup lang="ts">
import { useRouter } from 'vue-router'
import PlatformShell from '../components/PlatformShell.vue'
import MessageWorkspaceView from './MessageWorkspaceView.vue'
import type { MessageOpenResult } from '../types'

const router = useRouter()
function openTarget(target: MessageOpenResult) {
  void router.push(target.route)
}
</script>

<template>
  <PlatformShell><MessageWorkspaceView context="platform" @open-target="openTarget" /></PlatformShell>
</template>
