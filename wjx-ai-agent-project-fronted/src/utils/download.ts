/**
 * 文件下载相关工具：解析响应头里的文件名 + 触发浏览器保存
 */

/**
 * 从 Content-Disposition 响应头解析文件名，解析不出来返回空串。
 * 兼容两种写法：
 *   1. filename*=UTF-8''%E4%B8%AD%E6%96%87.zip  （RFC 5987，中文文件名走这个）
 *   2. filename="466449797149380608.zip"
 */
export function parseFileNameFromDisposition(disposition?: string | null): string {
  if (!disposition) return ''

  // 1. 优先取 filename*=（带编码声明的）
  const utf8Match = disposition.match(/filename\*\s*=\s*([^;]+)/i)
  if (utf8Match) {
    const raw = utf8Match[1].trim().replace(/^["']|["']$/g, '')
    const value = raw.includes("''") ? raw.slice(raw.indexOf("''") + 2) : raw
    try {
      return decodeURIComponent(value)
    } catch {
      return value
    }
  }

  // 2. 其次取普通的 filename=
  const plainMatch = disposition.match(/filename\s*=\s*([^;]+)/i)
  if (plainMatch) {
    return plainMatch[1].trim().replace(/^["']|["']$/g, '')
  }

  return ''
}

/** 把 Blob 保存为本地文件（临时 URL 用完即释放，避免内存泄漏） */
export function saveBlobAsFile(blob: Blob, fileName: string): void {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.style.display = 'none'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

/**
 * 校验 Blob 是否真的是 ZIP 包（ZIP 文件头固定为 PK\x03\x04 / PK\x05\x06 / PK\x07\x08）。
 * 后端在响应已提交后才抛异常时，前端会收到一个残缺的包，这里拦下来避免存出坏文件。
 */
export async function isZipBlob(blob: Blob): Promise<boolean> {
  if (!blob || blob.size < 4) return false
  const head = new Uint8Array(await blob.slice(0, 4).arrayBuffer())
  const isPk = head[0] === 0x50 && head[1] === 0x4b
  return isPk && (head[2] === 0x03 || head[2] === 0x05 || head[2] === 0x07)
}

/**
 * 读取「本该是二进制、实际却返回了 JSON 信封」的响应体，取出后端的错误信息。
 * 后端业务异常统一返回 BaseResponse（HTTP 200 + code/message），
 * 下载接口遇到这类响应时拿到的是 JSON 而不是 zip，需要单独提示。
 */
export async function readEnvelopeMessage(blob: Blob): Promise<string> {
  try {
    const parsed = JSON.parse(await blob.text())
    return typeof parsed?.message === 'string' ? parsed.message : ''
  } catch {
    return ''
  }
}
