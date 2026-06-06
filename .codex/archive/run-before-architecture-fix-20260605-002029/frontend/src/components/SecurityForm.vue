<template>
  <el-form :model="model" label-width="108px">
    <el-form-item label="clientPk" required><el-input-number v-model="model.clientPk" :min="1" /></el-form-item>
    <el-form-item label="keyVersion"><el-input-number v-model="model.keyVersion" :min="1" controls-position="right" placeholder="空值由后端生成" /></el-form-item>
    <el-form-item label="过期时间"><el-input v-model="model.expiresAt" placeholder="2026-12-31T23:59:59" /></el-form-item>
    <el-form-item>
      <el-button type="primary" :icon="Key" @click="$emit('submit')">创建凭证</el-button>
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { Key } from '@element-plus/icons-vue';

defineProps<{
  mode: 'credential';
  model: {
    clientPk?: number;
    keyVersion?: number;
    expiresAt: string;
  };
}>();

defineEmits<{
  submit: [];
}>();
</script>
