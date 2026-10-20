<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { DownOutlined, MenuFoldOutlined, MenuUnfoldOutlined, LogoutOutlined, UserOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import { DEFAULT_USER_AVATAR } from '@/utils/app'
import logoImg from '@/assets/logo.jpg'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const collapsed = ref(false)

/** 侧边栏菜单项 */
interface NavItem {
  key: string
  label: string
  path: string
  icon?: string
  adminOnly?: boolean
}

const navItems: NavItem[] = [
  { key: 'home', label: '首页', path: '/', icon: '🏠' },
  { key: 'project', label: '项目', path: '/project', icon: '📁' },
  { key: 'material', label: '素材库', path: '/material', icon: '🧩' },
  { key: 'community', label: '社区', path: '/community', icon: '💬' },
  { key: 'manage', label: '应用管理', path: '/app/manage', icon: '⚙️', adminOnly: true },
  { key: 'chatManage', label: '对话管理', path: '/chat/manage', icon: '💬', adminOnly: true },
  { key: 'userManage', label: '用户管理', path: '/user/manage', icon: '👥', adminOnly: true },
]

/** 当前用户可见的菜单（管理员额外显示"应用管理""用户管理"） */
const visibleNavs = computed(() => navItems.filter((item) => !item.adminOnly || userStore.isAdmin()))

/** 当前选中项 */
const selectedKey = computed(() => {
  const current = visibleNavs.value.find(
    (item) => route.path === item.path || route.path.startsWith(`${item.path}/`),
  )
  return current ? current.key : 'home'
})

/** 顶部标题：优先取当前侧边栏菜单项，否则用路由标题 */
const headerTitle = computed(() => {
  const current = visibleNavs.value.find(
    (item) => route.path === item.path || route.path.startsWith(`${item.path}/`),
  )
  if (current) return current.label
  return (route.meta?.title as string) || 'AI 应用生成器'
})

function onMenuClick(key: string) {
  const target = visibleNavs.value.find((item) => item.key === key)
  if (target) router.push(target.path)
}

/** 用户头像 */
const userAvatar = computed(() => userStore.currentUser?.userAvatar || DEFAULT_USER_AVATAR)
const userName = computed(() => userStore.currentUser?.userName || userStore.currentUser?.userAccount || '未登录')

function goLogin() {
  const redirect = encodeURIComponent(route.fullPath)
  router.push(`/user/login?redirect=${redirect}`)
}

function goRegister() {
  router.push('/user/register')
}

function handleUserMenuClick({ key }: { key: string }) {
  if (key === 'logout') {
    userStore.logout().then(() => {
      message.success('已退出登录')
      router.push('/')
    })
  } else if (key === 'userCenter') {
    router.push('/user/center')
  } else if (key === 'myapps') {
    router.push('/project')
  }
}

onMounted(() => {
  if (!userStore.loaded) {
    userStore.fetchLoginUser()
  }
})
</script>

<template>
  <a-layout class="home-layout">
    <!-- 左侧导航栏 -->
    <a-layout-sider
      v-model:collapsed="collapsed"
      :trigger="null"
      collapsible
      class="home-sider"
      width="220"
      breakpoint="lg"
    >
      <div class="sider-logo" @click="router.push('/')">
        <img :src="logoImg" alt="logo" class="sider-logo-img" />
        <span v-if="!collapsed" class="sider-logo-text">AI 应用生成器</span>
      </div>

      <a-menu
        theme="dark"
        mode="inline"
        class="sider-menu"
        :selected-keys="[selectedKey]"
        :inline-collapsed="collapsed"
      >
        <a-menu-item v-for="item in visibleNavs" :key="item.key" @click="onMenuClick(item.key)">
          <span class="menu-icon">{{ item.icon }}</span>
          <span>{{ item.label }}</span>
        </a-menu-item>
      </a-menu>

      <!-- 折叠按钮 -->
      <div class="sider-footer">
        <a-button
          type="text"
          class="collapse-btn"
          @click="collapsed = !collapsed"
          :aria-label="collapsed ? '展开导航栏' : '收起导航栏'"
        >
          <MenuUnfoldOutlined v-if="collapsed" />
          <MenuFoldOutlined v-else />
          <span v-if="!collapsed" class="collapse-text">{{ collapsed ? '' : '收起导航栏' }}</span>
        </a-button>
      </div>
    </a-layout-sider>

    <a-layout class="home-main">
      <!-- 顶部用户区 -->
      <div class="main-header">
        <div class="header-title">{{ headerTitle }}</div>

        <div class="header-right">
          <template v-if="userStore.isLogin()">
            <a-dropdown placement="bottomRight">
              <div class="user-info">
                <a-avatar :src="userAvatar" :size="34" />
                <span class="user-name">{{ userName }}</span>
                <span v-if="userStore.isAdmin()" class="admin-tag">管理员</span>
                <DownOutlined class="user-caret" />
              </div>
              <template #overlay>
                <a-menu @click="handleUserMenuClick">
                  <a-menu-item key="userCenter">
                    <UserOutlined />
                    用户中心
                  </a-menu-item>
                  <a-menu-item key="myapps">
                    <UserOutlined />
                    我的项目
                  </a-menu-item>
                  <a-menu-divider />
                  <a-menu-item key="logout">
                    <LogoutOutlined />
                    退出登录
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </template>
          <template v-else>
            <a-button type="primary" class="login-btn" @click="goLogin">登录</a-button>
            <a-button class="register-btn" @click="goRegister">注册</a-button>
          </template>
        </div>
      </div>

      <!-- 内容区 -->
      <a-layout-content class="main-content">
        <RouterView />
      </a-layout-content>

      <div class="main-footer">
        AI 应用生成器 · 让每个人都能轻松创建属于自己的网站应用 · 个人项目「天空之梦」（有事 call me:
        <a href="mailto:3412895047@qq.com">3412895047@qq.com</a>）
      </div>
    </a-layout>
  </a-layout>
</template>

<style scoped>
.home-layout {
  min-height: 100vh;
}

/* ---------- 侧边栏 ---------- */
.home-sider {
  background: linear-gradient(180deg, #101432 0%, #171c4a 100%);
  position: sticky;
  top: 0;
  height: 100vh;
  overflow: hidden;
  z-index: 20;
}

.sider-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 64px;
  padding: 0 18px;
  cursor: pointer;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.sider-logo-img {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  object-fit: cover;
  flex-shrink: 0;
}

.sider-logo-text {
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  white-space: nowrap;
  letter-spacing: 0.5px;
}

.sider-menu {
  flex: 1;
  background: transparent;
  border-inline-end: none;
  padding: 12px 8px;
  overflow-y: auto;
}

.sider-menu :deep(.ant-menu-item) {
  border-radius: 8px;
  margin-bottom: 6px;
  color: rgba(255, 255, 255, 0.72);
  height: 44px;
  line-height: 44px;
}

.sider-menu :deep(.ant-menu-item-selected) {
  background: linear-gradient(90deg, #4f46e5, #7c3aed);
  color: #fff;
  box-shadow: 0 4px 14px rgba(99, 102, 241, 0.45);
}

.sider-menu :deep(.ant-menu-item:hover) {
  color: #fff;
}

.menu-icon {
  margin-right: 8px;
  font-size: 15px;
}

.sider-footer {
  padding: 12px 10px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.collapse-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: rgba(255, 255, 255, 0.75);
  border-radius: 8px;
}

.collapse-btn:hover {
  color: #fff;
  background: rgba(255, 255, 255, 0.1) !important;
}

.collapse-text {
  font-size: 13px;
}

/* ---------- 主区域 ---------- */
.home-main {
  background: #f4f6fb;
  min-width: 0;
}

.main-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  padding: 0 28px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid #eef0f6;
}

.header-title {
  font-size: 17px;
  font-weight: 600;
  color: #1f2430;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px;
  border-radius: 999px;
  cursor: pointer;
  transition: background 0.2s;
}

.user-info:hover {
  background: #f0f2fa;
}

.user-name {
  font-size: 14px;
  color: #333;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admin-tag {
  font-size: 11px;
  color: #7c3aed;
  background: rgba(124, 58, 237, 0.12);
  padding: 1px 8px;
  border-radius: 999px;
}

.user-caret {
  font-size: 11px;
  color: #999;
}

.login-btn {
  border-radius: 999px;
}

.register-btn {
  border-radius: 999px;
}

.main-content {
  padding: 24px 28px;
  min-height: calc(100vh - 60px);
}

.main-footer {
  padding: 14px;
  text-align: center;
  color: #a0a6b8;
  font-size: 13px;
  background: transparent;
}

.main-footer a {
  color: #818cf8;
}

/* 折叠时 logo 居中 */
:deep(.ant-layout-sider-collapsed .sider-logo) {
  justify-content: center;
  padding: 0;
}

:deep(.ant-layout-sider-collapsed .sider-footer) {
  padding: 12px 4px;
}
</style>
