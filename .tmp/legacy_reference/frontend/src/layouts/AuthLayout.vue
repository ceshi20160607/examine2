<script setup lang="ts">
import { Blocks, ShieldCheck } from 'lucide-vue-next'
</script>

<template>
  <main class="auth-shell">
    <a class="skip-link" href="#auth-main">跳到登录内容</a>
    <section class="auth-brand" aria-label="examine2">
      <div class="brand-mark"><Blocks :size="26" /></div>
      <div>
        <strong>examine2</strong>
        <span>业务系统工作台</span>
      </div>
    </section>
    <section id="auth-main" class="auth-content" tabindex="-1">
      <RouterView />
    </section>
    <footer class="auth-footer"><ShieldCheck :size="16" /> 企业级身份与权限隔离</footer>
  </main>
</template>
