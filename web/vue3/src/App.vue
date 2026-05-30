<template>
  <router-view />
  <DialogHost />
  <ToastHost />
</template>

<script setup>
import DialogHost from './components/DialogHost.vue'
import ToastHost from './components/ToastHost.vue'
</script>
