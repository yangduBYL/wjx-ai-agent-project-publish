<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { LockOutlined, UserOutlined } from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'
import logoImg from '@/assets/logo.jpg'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)

const form = reactive({
  userAccount: '',
  userPassword: '',
})

async function handleLogin() {
  if (!form.userAccount.trim()) {
    message.warning('请输入账号')
    return
  }
  if (!form.userPassword) {
    message.warning('请输入密码')
    return
  }

  loading.value = true
  try {
    await userStore.login({
      userAccount: form.userAccount.trim(),
      userPassword: form.userPassword,
    })
    message.success('登录成功')
    const redirect = route.query.redirect as string | undefined
    router.replace(redirect || '/')
  } catch {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-logo" @click="router.push('/')">
        <img :src="logoImg" alt="logo" class="auth-logo-img" />
      </div>
      <h1 class="auth-title">欢迎回来</h1>
      <p class="auth-subtitle">登录 AI 应用生成器，继续你的创作</p>

      <a-form class="auth-form" @submit.prevent="handleLogin">
        <a-form-item>
          <a-input
            v-model:value="form.userAccount"
            size="large"
            placeholder="请输入账号"
            autocomplete="username"
          >
            <template #prefix><UserOutlined class="input-icon" /></template>
          </a-input>
        </a-form-item>
        <a-form-item>
          <a-input-password
            v-model:value="form.userPassword"
            size="large"
            placeholder="请输入密码"
            autocomplete="current-password"
            @press-enter="handleLogin"
          >
            <template #prefix><LockOutlined class="input-icon" /></template>
          </a-input-password>
        </a-form-item>

        <a-button
          type="primary"
          size="large"
          block
          class="auth-submit"
          :loading="loading"
          @click="handleLogin"
        >
          登录
        </a-button>
      </a-form>

      <div class="auth-footer">
        还没有账号？
        <router-link to="/user/register">立即注册</router-link>
      </div>
    </div>

    <div class="auth-bg">
      <span class="bg-orb orb-1"></span>
      <span class="bg-orb orb-2"></span>
      <span class="bg-orb orb-3"></span>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0b1026, #1a1440 50%, #2b1055);
  position: relative;
  overflow: hidden;
  padding: 20px;
}

.auth-card {
  position: relative;
  z-index: 2;
  width: 400px;
  max-width: 100%;
  background: rgba(255, 255, 255, 0.97);
  border-radius: 20px;
  padding: 40px 36px 30px;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.35);
  animation: card-in 0.6s cubic-bezier(0.22, 1, 0.36, 1) both;
}

@keyframes card-in {
  from {
    opacity: 0;
    transform: translateY(24px) scale(0.97);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.auth-logo {
  text-align: center;
  margin-bottom: 16px;
  cursor: pointer;
}

.auth-logo-img {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  object-fit: cover;
}

.auth-title {
  text-align: center;
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: #1f2430;
}

.auth-subtitle {
  text-align: center;
  margin: 8px 0 28px;
  color: #98a0b3;
  font-size: 14px;
}

.auth-form :deep(.ant-input-affix-wrapper) {
  border-radius: 10px;
  padding: 8px 12px;
}

.input-icon {
  color: #98a0b3;
}

.auth-submit {
  border-radius: 999px;
  height: 44px;
  font-size: 15px;
  background: linear-gradient(90deg, #4f46e5, #7c3aed);
  border: none;
  margin-top: 4px;
}

.auth-submit:hover {
  background: linear-gradient(90deg, #4338ca, #6d28d9) !important;
}

.auth-footer {
  margin-top: 22px;
  text-align: center;
  color: #98a0b3;
  font-size: 14px;
}

.auth-footer a {
  color: #4f46e5;
  font-weight: 600;
}

/* 背景光晕 */
.auth-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.bg-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.5;
}

.orb-1 {
  width: 460px;
  height: 460px;
  background: #4f46e5;
  top: -140px;
  left: -120px;
}

.orb-2 {
  width: 400px;
  height: 400px;
  background: #7c3aed;
  bottom: -140px;
  right: -100px;
}

.orb-3 {
  width: 220px;
  height: 220px;
  background: #0ea5e9;
  top: 30%;
  left: 60%;
}
</style>
