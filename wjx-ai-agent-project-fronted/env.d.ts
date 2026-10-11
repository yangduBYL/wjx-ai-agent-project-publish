/// <reference types="vite/client" />

/**
 * 自定义环境变量类型声明（对应 .env.development / .env.production）
 *
 * 注意：项目 tsconfig 开启了 moduleDetection: "force"，本文件会被当作模块处理，
 * 因此这里必须用 declare global 做全局类型合并（否则 ImportMetaEnv 只在文件内生效）。
 */
declare global {
  interface ImportMetaEnv {
    /** 后端接口基础路径（相对路径，如 /api，经 Vite 代理 / Nginx 转发到后端） */
    readonly VITE_API_BASE_URL: string
    /** 生成代码的静态预览基础路径（如 /api/static，与主页面同源，可视化编辑的前提） */
    readonly VITE_STATIC_BASE_URL: string
    /** 部署服务地址（后端 AppConstant.CODE_DEPLOY_HOST） */
    readonly VITE_DEPLOY_DOMAIN: string
  }

  interface ImportMeta {
    readonly env: ImportMetaEnv
  }
}

export {}
