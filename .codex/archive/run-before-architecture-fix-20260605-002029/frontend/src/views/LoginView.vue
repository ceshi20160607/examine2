<template>
  <div class="login-page">
    <section class="login-panel">
      <div class="page-title">
        <div>
          <h1>登录平台</h1>
          <p>进入平台中心、系统配置、运行记录和运维视图。</p>
        </div>
      </div>
      <el-tabs v-model="mode" stretch>
        <el-tab-pane label="账号登录" name="login">
          <el-form :model="loginForm" label-position="top" @submit.prevent="submitLogin">
            <el-form-item label="账号" required>
              <el-input v-model="loginForm.account" autocomplete="username" placeholder="admin" />
            </el-form-item>
            <el-form-item label="密码" required>
              <el-input
                v-model="loginForm.password"
                type="password"
                autocomplete="current-password"
                show-password
                placeholder="admin123"
              />
            </el-form-item>
            <el-button type="primary" :loading="auth.loading" class="full-button" @click="submitLogin">
              登录
            </el-button>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="注册账号" name="register">
          <el-form :model="registerForm" label-position="top" @submit.prevent="submitRegister">
            <el-form-item label="账号" required>
              <el-input v-model="registerForm.account" />
            </el-form-item>
            <el-form-item label="姓名" required>
              <el-input v-model="registerForm.realName" />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="registerForm.mobile" />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="registerForm.email" />
            </el-form-item>
            <el-form-item label="密码" required>
              <el-input v-model="registerForm.password" type="password" show-password />
            </el-form-item>
            <el-button type="primary" :loading="auth.loading" class="full-button" @click="submitRegister">
              注册
            </el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
      <el-alert
        class="login-note"
        type="info"
        :closable="false"
        title="默认后端初始化账号可用 admin / admin123；登录后通过系统列表进入带 systemId、tenantId 的上下文。"
      />
    </section>
    <section class="login-copy">
      <h1>可配置业务系统平台</h1>
      <p>
        第一屏直接进入可操作后台。平台中心、应用配置、运行记录、流程、文件任务、OpenAPI 和运维健康按后端接口真实调用。
      </p>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '../stores/auth';

const auth = useAuthStore();
const router = useRouter();
const route = useRoute();
const mode = ref<'login' | 'register'>('login');

const loginForm = reactive({
  account: 'admin',
  password: 'admin123'
});

const registerForm = reactive({
  account: '',
  realName: '',
  mobile: '',
  email: '',
  password: ''
});

async function submitLogin() {
  await auth.login(loginForm.account, loginForm.password);
  await auth.loadMe().catch(() => undefined);
  router.push(String(route.query.redirect || '/'));
}

async function submitRegister() {
  await auth.register({ ...registerForm });
  ElMessage.success('注册成功，请登录');
  mode.value = 'login';
  loginForm.account = registerForm.account;
}
</script>

<style scoped>
.full-button {
  width: 100%;
}

.login-note {
  margin-top: 18px;
}
</style>
