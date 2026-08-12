<script setup lang="ts">
import { CircleCheck } from 'lucide-vue-next'
import './authPage.css'
</script>

<template>
  <div class="auth-panel password-recovery-panel password-reset-success" role="status" aria-live="polite">
    <ol class="auth-flow-steps" aria-label="密码恢复进度">
      <li><span>1</span>验证账号</li>
      <li><span>2</span>设置密码</li>
      <li class="active"><span>3</span>完成</li>
    </ol>
    <CircleCheck :size="44" />
    <h1>密码重置成功</h1>
    <p>原有登录会话已退出，现在可以使用新密码重新登录。</p>
    <RouterLink class="password-reset-login" to="/auth/login?notice=password-reset">返回登录</RouterLink>
  </div>
</template>
