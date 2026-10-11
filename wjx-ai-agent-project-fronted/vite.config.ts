import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'


// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), vueDevTools()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // 代理配置：把 /api 前缀的请求转发到后端服务（http://localhost:8123/api）。
    // 作用一：前端所有请求都走相对路径（VITE_API_BASE_URL=/api），无需再写死域名；
    // 作用二（可视化编辑的关键）：AI 生成网站的预览地址 /api/static/xxx 也由本代理提供，
    //         于是预览 iframe 的地址与主页面同源，父页面才能读取子页面 DOM、注入编辑脚本。
    // 线上部署时把这段规则换成 Nginx 的 location /api 反向代理即可。
    proxy: {
      '/api': {
        target: 'http://localhost:8123',
        changeOrigin: true,
        secure: false,
      },
    },
  },
})
