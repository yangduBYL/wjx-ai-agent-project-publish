<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import type { MenuProps } from 'ant-design-vue'
import logoImg from '@/assets/logo.jpg'

export interface MenuItemConfig {
  key: string
  label: string
  path?: string
  children?: MenuItemConfig[]
}

const props = withDefaults(
  defineProps<{
    menuItems?: MenuItemConfig[]
  }>(),
  {
    menuItems: () => [
      { key: 'home', label: '首页', path: '/' },
      { key: 'about', label: '关于', path: '/about' },
    ],
  },
)

const router = useRouter()
const route = useRoute()

const selectedKeys = computed(() => {
  if (route.path === '/') {
    const home = props.menuItems.find((item) => item.path === '/')
    return home ? [home.key] : []
  }
  const matched = props.menuItems.find(
    (item) =>
      item.path &&
      item.path !== '/' &&
      (route.path === item.path || route.path.startsWith(`${item.path}/`)),
  )
  return matched ? [matched.key] : []
})

const menuItemsData = computed<MenuProps['items']>(() =>
  props.menuItems.map((item) => ({
    key: item.key,
    label: item.label,
    children: item.children?.map((child) => ({
      key: child.key,
      label: child.label,
    })),
  })),
)

const onMenuClick: MenuProps['onClick'] = ({ key }) => {
  const findItem = (items: MenuItemConfig[]): MenuItemConfig | undefined => {
    for (const item of items) {
      if (item.key === key) return item
      if (item.children) {
        const found = findItem(item.children)
        if (found) return found
      }
    }
    return undefined
  }
  const target = findItem(props.menuItems)
  if (target?.path) {
    router.push(target.path)
  }
}

const goHome = () => {
  router.push('/')
}
</script>

<template>
  <div class="global-header">
    <div class="header-left" @click="goHome">
      <img :src="logoImg" alt="logo" class="logo" />
      <span class="site-title">ai游戏生成器</span>
    </div>

    <a-menu
      class="header-menu"
      mode="horizontal"
      theme="dark"
      :selected-keys="selectedKeys"
      :items="menuItemsData"
      @click="onMenuClick"
    />

    <div class="header-right">
      <a-button type="primary">登录</a-button>
    </div>
  </div>
</template>

<style scoped>
.global-header {
  display: flex;
  align-items: center;
  width: 100%;
  height: 100%;
  gap: 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
  cursor: pointer;
  user-select: none;
}

.logo {
  width: 36px;
  height: 36px;
  border-radius: 6px;
  object-fit: cover;
}

.site-title {
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  white-space: nowrap;
}

.header-menu {
  flex: 1;
  min-width: 0;
  background: transparent;
  border-bottom: none;
  line-height: 64px;
}

.header-right {
  flex-shrink: 0;
  margin-left: auto;
}

@media (max-width: 768px) {
  .site-title {
    font-size: 15px;
  }

  .logo {
    width: 28px;
    height: 28px;
  }

  .header-menu {
    line-height: 56px;
  }
}

@media (max-width: 480px) {
  .site-title {
    display: none;
  }
}
</style>
