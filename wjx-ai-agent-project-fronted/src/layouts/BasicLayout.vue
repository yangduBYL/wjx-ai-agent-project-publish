<script setup lang="ts">
import { RouterView } from 'vue-router'
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'
import type { MenuItemConfig } from '@/components/GlobalHeader.vue'

const menuItems: MenuItemConfig[] = [
  { key: 'home', label: '首页', path: '/' },
  { key: 'about', label: '关于', path: '/about' },
  {key:'game',label:'游戏', path: '/game' },
]
</script>

<template>
  <a-layout class="basic-layout">
    <a-layout-header class="basic-layout-header">
      <GlobalHeader :menu-items="menuItems" />
    </a-layout-header>

    <a-layout-content class="basic-layout-content">
      <div class="content-inner">
        <RouterView />
      </div>
    </a-layout-content>

    <a-layout-footer class="basic-layout-footer">
      <GlobalFooter />
    </a-layout-footer>
  </a-layout>
</template>

<style scoped>
.basic-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.basic-layout-header {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  padding: 0 24px;
  background: #001529;
  height: 64px;
  line-height: 64px;
}

.basic-layout-content {
  flex: 1;
  padding: 24px;
  background: #f5f5f5;
}

.content-inner {
  min-height: 100%;
  padding: 24px;
  background: #fff;
  border-radius: 8px;
}

.basic-layout-footer {
  padding: 16px 24px;
  background: #f0f2f5;
  text-align: center;
}

@media (max-width: 768px) {
  .basic-layout-header {
    padding: 0 12px;
    height: 56px;
    line-height: 56px;
  }

  .basic-layout-content {
    padding: 12px;
  }

  .content-inner {
    padding: 16px;
  }

  .basic-layout-footer {
    padding: 12px;
  }
}
</style>
