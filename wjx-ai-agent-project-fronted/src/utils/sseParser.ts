/**
 * SSE 事件解析（纯逻辑，不依赖浏览器与 Vite 环境变量）
 *
 * 单独抽出来是因为这里最容易出错、又最难在页面上复现：
 *  1. 一个事件可能被网络拆到多个 chunk（甚至在 \n\n 中间、在 \r 与 \n 之间断开）；
 *  2. 后端除了正常数据块（data:{"d":"..."}）与结束事件（event:done），
 *     还会写业务错误事件（event:business-error，见 GlobalExceptionHandler.handleSseError），
 *     例如 @RateLimit 限流抛出的 42900「AI对话请求过于频繁，请稍后再试」；
 *  3. 错误负载绝不能被当成代码内容拼进气泡。
 *
 * 纯函数/纯类 → 可以用 Node 直接跑断言（scripts/sseParser.test.mjs）。
 */

/** 一条已解析完成的 SSE 事件 */
export interface SseEvent {
  /** 事件名，缺省为 message */
  event: string
  /** data: 行拼接后的原始数据（多行用 \n 连接） */
  data: string
}

/** 后端写出的业务错误负载：{"error":true,"code":42900,"message":"..."} */
export interface SseBusinessError {
  /** 业务错误码：42900 请求过于频繁、40100 未登录、50000 系统错误……（对应后端 ErrorCode） */
  code: number
  /** 面向用户的错误信息 */
  message: string
  /** 是否为后端 business-error 事件标记（兜底识别出来的为 true） */
  error?: boolean
}

/** 一条事件交给页面消费时的语义 */
export type SseEventAction =
  /** 正常数据块（已从 {"d":"..."} 解包出原文） */
  | { kind: 'chunk'; chunk: string }
  /** 流正常结束 */
  | { kind: 'done' }
  /** 业务错误（限流 / 未登录 / 系统错误等） */
  | { kind: 'business-error'; error: SseBusinessError }
  /** 空事件、心跳、无意义负载：不处理 */
  | { kind: 'ignore' }

/**
 * 识别 JSON 对象形式的业务错误负载。
 * 兼容两种形状：
 *  1. 后端 SSE 错误事件：{"error":true,"code":42900,"message":"..."}
 *  2. BaseResponse 信封：{"code":42900,"message":"...","data":null}（非 SSE 分支或代理改写时出现）
 */
function asBusinessError(value: unknown): SseBusinessError | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null
  const obj = value as Record<string, unknown>
  const hasCode = typeof obj.code === 'number'
  const code = hasCode ? (obj.code as number) : 0
  const message = typeof obj.message === 'string' ? obj.message.trim() : ''
  if (obj.error === true) {
    return { code, message: message || '生成过程中出现错误', error: true }
  }
  // 没有 error 标记，但带非 0 code + message 的信封（防御式兜底）
  if (hasCode && code !== 0 && message) {
    return { code, message, error: true }
  }
  return null
}

/**
 * 解析一条完整的原始事件文本（首尾空行已由调用方剥掉）
 * @returns 事件对象；整块都是注释/空行时返回 null
 */
export function parseSseBlock(block: string): SseEvent | null {
  const lines = block.replace(/\r/g, '').split('\n')
  let event = 'message'
  const dataLines: string[] = []
  let hasField = false
  for (const line of lines) {
    // 空行与以 ':' 开头的注释行（SSE 心跳）忽略
    if (!line || line.startsWith(':')) continue
    hasField = true
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      // 规范里 data: 后可以有一个空格，去掉它但保留其余内容
      dataLines.push(line.slice(5).replace(/^ /, ''))
    }
  }
  if (!hasField) return null
  return { event, data: dataLines.join('\n') }
}

/**
 * 决定一条事件该怎么消费：这是前端「自定义错误处理」的核心。
 * 返回值里的 chunk 才是真正要拼进气泡的文本。
 */
export function classifySseEvent(ev: SseEvent): SseEventAction {
  const name = ev.event.toLowerCase()
  if (name === 'done') return { kind: 'done' }
  // 业务错误事件：data 一定是 JSON 负载，解析失败也要按错误处理，绝不能当内容
  if (name === 'business-error' || name === 'error') {
    let payload: unknown = null
    try {
      payload = ev.data ? JSON.parse(ev.data) : null
    } catch {
      payload = null
    }
    return {
      kind: 'business-error',
      error: asBusinessError(payload) ?? { code: 0, message: '生成过程中出现错误', error: true },
    }
  }
  if (!ev.data) return { kind: 'ignore' }

  let parsed: unknown
  try {
    parsed = JSON.parse(ev.data)
  } catch {
    // 非 JSON：按原样文本输出（兼容后端直接发纯文本的写法）
    return { kind: 'chunk', chunk: ev.data }
  }

  if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
    const obj = parsed as Record<string, unknown>
    if (typeof obj.d === 'string') {
      return { kind: 'chunk', chunk: obj.d }
    }
    // 兜底：代理/网关把 event: 行吞掉时，错误负载会伪装成普通 message 事件
    const err = asBusinessError(parsed)
    if (err) return { kind: 'business-error', error: err }
    // 形如 {} 的无意义负载不往气泡里塞
    return { kind: 'ignore' }
  }
  return { kind: 'chunk', chunk: ev.data }
}

/**
 * 增量解析器：push 任意长度的分片，返回其中已经完整的事件。
 * 关键点是跨分片的缓冲处理（含在 \r 与 \n 之间被拆开的情况）。
 */
export class SseStreamParser {
  private buffer = ''
  /** 上一分片以孤立的 \r 结尾，等下一分片判断它是不是 \r\n */
  private pendingCR = false

  push(text: string): SseEvent[] {
    if (this.pendingCR) {
      this.pendingCR = false
      // 下一分片以 \n 开头 → 它们本是一个 \r\n，保留一个 \n 即可
      if (!text.startsWith('\n')) text = '\n' + text
    }
    if (text.endsWith('\r')) {
      this.pendingCR = true
      text = text.slice(0, -1)
    }
    this.buffer += text.replace(/\r\n?/g, '\n')

    const events: SseEvent[] = []
    let idx: number
    while ((idx = this.buffer.indexOf('\n\n')) !== -1) {
      const block = this.buffer.slice(0, idx)
      this.buffer = this.buffer.slice(idx + 2)
      const ev = parseSseBlock(block)
      if (ev) events.push(ev)
    }
    return events
  }

  /** 流结束时把残留缓冲也解析掉（最后一个事件可能没有结尾空行） */
  flush(): SseEvent[] {
    const rest = this.buffer
    this.buffer = ''
    this.pendingCR = false
    if (!rest.trim()) return []
    const ev = parseSseBlock(rest)
    return ev ? [ev] : []
  }
}
