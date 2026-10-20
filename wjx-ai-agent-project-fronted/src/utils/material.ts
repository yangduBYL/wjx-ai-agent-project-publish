/**
 * 素材相关通用工具（上传校验 / 大小格式化）。
 */

/** 允许的图片扩展名（与后端 MaterialServiceImpl 保持一致） */
const ALLOWED_EXT = ['jpg', 'jpeg', 'png', 'gif', 'webp']

/** 单张图片最大 10MB（与后端一致） */
export const MAX_MATERIAL_SIZE = 10 * 1024 * 1024

/**
 * 上传前的前端校验：返回错误文案，通过则返回空串。
 * 后端仍会再校验一遍（前端校验只为少走一次网络请求）。
 */
export function validateImageFile(file: File): string {
  const ext = (file.name.split('.').pop() || '').toLowerCase()
  const mimeOk = /^image\/(jpeg|jpg|png|gif|webp)$/i.test(file.type)
  if (!mimeOk && !ALLOWED_EXT.includes(ext)) {
    return '仅支持 JPG / PNG / GIF / WebP 图片'
  }
  if (file.size > MAX_MATERIAL_SIZE) {
    return '图片不能超过 10MB'
  }
  return ''
}

/** 字节数 → 可读大小 */
export function formatFileSize(size?: string | number): string {
  const bytes = Number(size ?? 0)
  if (!bytes || Number.isNaN(bytes)) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}
