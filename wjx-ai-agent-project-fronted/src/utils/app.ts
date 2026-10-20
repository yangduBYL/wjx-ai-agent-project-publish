/**
 * 应用相关的通用工具
 */

/**
 * 生成代码的静态预览基础路径。
 *
 * 必须使用【相对路径】（/api/static）而不是 http://localhost:8123/api/static：
 * 预览 iframe 只有和主页面同源，父页面才能读取子页面 DOM、注入可视化编辑脚本
 * （本地由 Vite 代理转发，线上由 Nginx 反代，见 .env.development / vite.config.ts）。
 */
export const STATIC_BASE_URL = import.meta.env.VITE_STATIC_BASE_URL || '/api/static'

/** 部署域名（后端 AppConstant.CODE_DEPLOY_HOST 对应的静态服务器） */
export const DEPLOY_BASE_URL = import.meta.env.VITE_DEPLOY_DOMAIN || 'http://localhost:8090'

/** 用户默认头像（未设置头像时使用） */
export const DEFAULT_USER_AVATAR =
  'https://ccstatic-1252317822.file.myqcloud.com/portraitimg/2025-04-12/6d0ccdf2995e93480deb5a42f26483e7jF0StHXlNysU15N.jpg'

/**
 * 探测 URL 是否可达（仅判断目标服务器是否在监听，不关心业务状态码）。
 * 用于部署后检查 8090 端口的静态服务是否已启动。
 */
export async function isUrlReachable(url: string, timeoutMs = 3000): Promise<boolean> {
  try {
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), timeoutMs)
    try {
      // no-cors：服务器在线时请求总能返回（opaque response），连接失败才抛错
      await fetch(url, { method: 'HEAD', mode: 'no-cors', signal: controller.signal })
      return true
    } finally {
      clearTimeout(timer)
    }
  } catch {
    return false
  }
}

/**
 * 代码生成类型（与后端 CodeTypeEnum 的 code 字段一一对应）
 */
export const CodeGenTypeEnum = {
  /** 原生 HTML 单文件模式 */
  HTML: 'html',
  /** 原生多文件模式 */
  MULTI_FILE: 'multi_file',
  /** Vue 工程模式（生成完整 Vue3 工程，构建产物在 dist 目录） */
  VUE_PROJECT: 'vue_project',
} as const

/** 代码生成类型联合类型 */
export type CodeGenType = (typeof CodeGenTypeEnum)[keyof typeof CodeGenTypeEnum]

/** 各代码生成类型的中文文案 */
export const CODE_GEN_TYPE_LABELS: Record<string, string> = {
  [CodeGenTypeEnum.HTML]: '单文件模式',
  [CodeGenTypeEnum.MULTI_FILE]: '多文件模式',
  [CodeGenTypeEnum.VUE_PROJECT]: 'Vue 工程模式',
}

/** 各代码生成类型的标签颜色（antd a-tag） */
export const CODE_GEN_TYPE_COLORS: Record<string, string> = {
  [CodeGenTypeEnum.HTML]: 'blue',
  [CodeGenTypeEnum.MULTI_FILE]: 'purple',
  [CodeGenTypeEnum.VUE_PROJECT]: 'green',
}

/** 代码生成类型 → 中文文案（未知类型回退为原始值） */
export function getCodeGenTypeLabel(type?: string): string {
  if (!type) return '-'
  return CODE_GEN_TYPE_LABELS[type] ?? type
}

/**
 * 生成代码的静态资源预览地址：
 * `/api/static/{codeGenType}_{appId}/`
 *
 * Vue 工程模式的构建产物位于工程的 dist 目录下（vite.config 里 base 为 './'，
 * 资源使用相对路径），因此浏览地址需要追加 dist/index.html。
 */
export function getStaticPreviewUrl(
  codeGenType: string | undefined,
  appId: string | number | undefined,
): string {
  const baseUrl = `${STATIC_BASE_URL}/${codeGenType ?? CodeGenTypeEnum.HTML}_${appId}/`
  // 如果是 Vue 项目，浏览地址需要添加 dist 后缀
  if (codeGenType === CodeGenTypeEnum.VUE_PROJECT) {
    return `${baseUrl}dist/index.html`
  }
  return baseUrl
}

/** 生成代码的本地预览地址（应用维度，内部复用 getStaticPreviewUrl） */
export function getPreviewUrl(app: { codeGenType?: string; id?: number }): string {
  return getStaticPreviewUrl(app.codeGenType, app.id)
}

/** 部署后的可访问地址（未部署时返回空） */
export function getDeployUrl(app: { deployKey?: string }): string {
  return app.deployKey ? `${DEPLOY_BASE_URL}/${app.deployKey}/` : ''
}

/**
 * 生成默认应用封面地址：使用 https://picsum.photos 随机图片服务，
 * seed 保证每个应用获得一张固定、且互不相同的封面图
 */
export function getDefaultCover(): string {
  return `https://picsum.photos/seed/${Date.now()}-${Math.floor(Math.random() * 99999)}/800/600`
}

/** "访问"按钮：已部署则打开部署地址，否则打开本地预览地址 */
export function getVisitUrl(app: { deployKey?: string; codeGenType?: string; id?: number }): string {
  return getDeployUrl(app) || getPreviewUrl(app)
}

/** 打开访问地址（新标签页） */
export function openVisitUrl(app: { deployKey?: string; codeGenType?: string; id?: number }): void {
  window.open(getVisitUrl(app), '_blank', 'noopener,noreferrer')
}

/** 将后端时间字符串格式化为可读时间 */
export function formatTime(time?: string): string {
  if (!time) return '-'
  return time.replace('T', ' ').slice(0, 19)
}

/**
 * 判断封面地址是否是可加载的 URL。
 * 历史数据中的 cover 可能是 "88" 之类的脏值，直接作为 img src 会向当前域名发起
 * 无意义的请求（如 http://localhost:5174/88），这里只允许 http(s)/协议相对/data 图片。
 */
export function isValidImageUrl(url?: string | null): boolean {
  if (!url) return false
  return /^(https?:\/\/|\/\/|data:image\/)/i.test(url.trim())
}

/**
 * 后端雪花 ID（Long）经 Jackson 序列化为字符串返回（如 "452414752273256448"），
 * 若通过 Number() 转换会超出 JS 安全整数范围而丢失精度，导致查询失败。
 * 本函数仅做类型层面的声明绕过（openapi2ts 将 Long 生成为 number），运行时不做任何转换。
 */
export function asId(id: string | number | null | undefined): number {
  return (id ?? '') as unknown as number
}
