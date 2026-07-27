<script setup lang="ts">
import { ref } from 'vue'
import { RouterView } from 'vue-router'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import SplashScreen from '@/components/SplashScreen.vue'

// 启动过场动画：页面加载后展示，结束后移除
const splashVisible = ref(true)

function onSplashFinish() {
  splashVisible.value = false
}
</script>

<template>
  <!-- antd 中文语言包：让弹窗/气泡/空态/分页等默认按钮与文案全部为中文 -->
  <a-config-provider :locale="zhCN">
    <RouterView />

    <!-- 启动过场动画 -->
    <SplashScreen v-if="splashVisible" @finish="onSplashFinish" />
  </a-config-provider>
</template>

<!-- 全局样式：管理页胶囊按钮等公用样式 -->
<style>
/* ============ 管理页操作按钮组：每个功能一个独立胶囊按钮，空间不足可横向滚动 ============ */
.action-group {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 2px;
  max-width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: thin;
}

.action-group::-webkit-scrollbar {
  height: 4px;
}

.action-group::-webkit-scrollbar-track {
  background: transparent;
}

.action-group::-webkit-scrollbar-thumb {
  background: rgba(70, 174, 247, 0.4);
  border-radius: 4px;
}

/* 胶囊按钮基础样式（椭圆边框） */
.ab.ant-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 28px;
  padding: 0 14px;
  border-radius: 999px;
  font-size: 12.5px;
  line-height: 1;
  white-space: nowrap;
  border: 1px solid #c4d3ea;
  background: #fff;
  color: #40506b;
  box-shadow: none;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    background-color 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease;
}

.ab.ant-btn:hover,
.ab.ant-btn:focus {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(70, 174, 247, 0.35);
  border-color: #46aef7;
  color: #0b6bb8;
  background: #eaf7ff;
}

.ab.ant-btn:active {
  transform: translateY(0);
}

.ab.ant-btn[disabled],
.ab.ant-btn[disabled]:hover {
  opacity: 0.5;
  transform: none;
  box-shadow: none;
}

/* 详情：中性 */
.ab-detail.ant-btn {
  border-color: #d5dcec;
}

/* 编辑：靛蓝 */
.ab-edit.ant-btn {
  color: #4f46e5;
  border-color: #c9cbfb;
  background: #f2f3ff;
}

.ab-edit.ant-btn:hover {
  color: #4338ca;
  border-color: #818cf8;
  background: #e5e7ff;
}

/* 精选：琥珀 */
.ab-feature.ant-btn {
  color: #b45309;
  border-color: #f3d9a4;
  background: #fff7e8;
}

.ab-feature.ant-btn:hover {
  color: #92400e;
  border-color: #f0b95c;
  background: #ffefd2;
}

/* 取消精选：灰 */
.ab-unfeature.ant-btn {
  color: #6b7280;
  border-color: #d7dbe6;
  background: #f6f7fa;
}

.ab-unfeature.ant-btn:hover {
  color: #374151;
  border-color: #9ca3af;
  background: #eef0f4;
}

/* 部署：绿色 */
.ab-deploy.ant-btn {
  color: #047857;
  border-color: #a7e3cf;
  background: #ecfdf5;
}

.ab-deploy.ant-btn:hover {
  color: #065f46;
  border-color: #34d399;
  background: #d1fae5;
}

/* 访问：天蓝 */
.ab-visit.ant-btn {
  color: #0369a1;
  border-color: #a5d8f3;
  background: #eff8ff;
}

.ab-visit.ant-btn:hover {
  color: #075985;
  border-color: #38bdf8;
  background: #e0f2fe;
}

/* 删除：红色 */
.ab-delete.ant-btn,
.ab-delete.ant-btn-dangerous {
  color: #dc2626;
  border-color: #f5c2c2;
  background: #fef2f2;
}

.ab-delete.ant-btn:hover,
.ab-delete.ant-btn-dangerous:hover {
  color: #b91c1c;
  border-color: #f87171;
  background: #fee2e2;
  box-shadow: 0 4px 12px rgba(220, 38, 38, 0.25);
}
</style>
