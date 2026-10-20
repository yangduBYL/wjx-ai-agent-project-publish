/**
 * SSE 流式请求工具
 *
 * 后端 /app/chat/gen/code 为 text/event-stream 流式接口，约定三类事件：
 *  1. 数据块：  data: {"d":"<文本片段>"}
 *  2. 正常结束：event: done / data: {}
 *  3. 业务错误：event: business-error / data: {"error":true,"code":42900,"message":"..."}
 *     —— 由后端 GlobalExceptionHandler.handleSseError 写出，覆盖 Redisson 限流（42900）、
 *        未登录（40100）、系统错误（50000）等；错误负载绝不能被当成生成内容拼进消息气泡。
 *
 * axios 无法消费 SSE 流，这里使用 fetch + ReadableStream 手动读取；
 * 事件解析（含跨分片缓冲）全部交给 utils/sseParser，纯逻辑、可直接单测。
 */
import { SseStreamParser, classifySseEvent, type SseBusinessError } from './sseParser'

/** 流式请求错误：尽量带上后端业务错误码（42900 限流 / 40100 未登录 / 50000 系统错误） */
export interface ChatStreamError extends Error {
  code?: number
}

export interface ChatStreamOptions {
  /** 每个数据块到达时的回调（已经解包出原始文本） */
  onData?: (chunk: string) => void
  /** 流正常结束（收到 done 事件或读取结束）时的回调 */
  onDone?: () => void
  /** 业务错误回调（后端 business-error 事件，或非 SSE 分支返回的错误信封） */
  onBusinessError?: (error: SseBusinessError) => void
  /** 传输层错误回调（网络异常、HTTP 状态异常、浏览器不支持流式等） */
  onError?: (error: ChatStreamError) => void
}

/** 后端接口基础路径（与 request.ts 保持一致，走环境变量 + 代理转发） */
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

/** 拼接请求地址 */
function buildUrl(base: string, params: Record<string, string | number>) {
  const url = new URL(`${API_BASE_URL}${base}`, window.location.origin)
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      url.searchParams.set(key, String(value))
    }
  })
  return url.toString()
}

/** 构造带错误码的异常对象 */
function toStreamError(message: string, code?: number): ChatStreamError {
  const error = new Error(message) as ChatStreamError
  if (typeof code === 'number') error.code = code
  return error
}

/**
 * 从响应体里抽出业务错误（兼容三种形状）：
 *  1. BaseResponse JSON 信封：{"code":42900,"message":"...","data":null}
 *  2. SSE 文本（HTTP 状态异常时后端仍写了 business-error 事件）
 *  3. 纯文本 / 空响应 → 用兜底文案，并把 HTTP 状态码当作错误码透出
 *     （后端 SSE 错误分支若自身失败，客户端只会看到 500/429 + 空响应体，
 *      此时至少让页面知道"是 429 限流"还是"是 500 服务端异常"）
 */
function extractError(body: string, fallback: string, httpStatus = 0): SseBusinessError {
  const text = (body || '').trim()
  if (text) {
    try {
      const obj = JSON.parse(text) as Record<string, unknown>
      const code = typeof obj.code === 'number' ? obj.code : 0
      const message = typeof obj.message === 'string' ? obj.message.trim() : ''
      if (code || message) {
        return { code: code || httpStatus, message: message || fallback, error: true }
      }
    } catch {
      // 不是 JSON，继续往下试
    }
    const matched = /event:\s*(?:business-error|error)[\s\S]*?data:\s*(\{[^\n]*\})/i.exec(text)
    if (matched) {
      try {
        const obj = JSON.parse(matched[1]) as Record<string, unknown>
        const code = typeof obj.code === 'number' ? obj.code : 0
        const message = typeof obj.message === 'string' ? obj.message.trim() : ''
        if (code || message) {
          return { code: code || httpStatus, message: message || fallback, error: true }
        }
      } catch {
        // 忽略，走兜底
      }
    }
  }
  return { code: httpStatus, message: fallback, error: true }
}

/**
 * 发起 SSE 流式对话请求
 * @param url 接口地址（相对路径，如 /app/chat/gen/code）
 * @param params 查询参数
 * @param options 回调集合
 * @returns 取消函数，调用后中断本次请求
 */
export function chatStream(
  url: string,
  params: Record<string, string | number>,
  options: ChatStreamOptions = {},
): () => void {
  const { onData, onDone, onBusinessError, onError } = options
  const controller = new AbortController()
  const fullUrl = buildUrl(url, params)

  ;(async () => {
    try {
      const response = await fetch(fullUrl, {
        method: 'GET',
        // 必须携带 Cookie，才能通过后端登录校验
        credentials: 'include',
        headers: {
          Accept: 'text/event-stream',
        },
        signal: controller.signal,
      })

      // 1. HTTP 层异常（网关限流、后端 5xx 等）：后端一般也会带上错误信息
      if (!response.ok) {
        const body = await response.text().catch(() => '')
        const fallback =
          response.status === 429
            ? '请求过于频繁，请稍后再试'
            : `请求失败：HTTP ${response.status}${response.status >= 500 ? '（服务端异常，请查看后端日志）' : ''}`
        onBusinessError?.(extractError(body, fallback, response.status))
        return
      }

      // 2. 后端没有走 SSE 分支（例如 Accept 头被网关改写，限流异常被当成普通请求处理），
      //    响应体是 BaseResponse JSON 而不是事件流 → 按业务错误上报，避免把 JSON 当成内容渲染
      const contentType = response.headers.get('content-type') || ''
      if (!contentType.includes('text/event-stream')) {
        const body = await response.text().catch(() => '')
        onBusinessError?.(extractError(body, '流式响应格式异常，请稍后重试'))
        return
      }

      if (!response.body) {
        throw toStreamError('当前浏览器不支持流式读取')
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      const parser = new SseStreamParser()
      /** 是否已经收到过任何事件（用于识别"连接建立后立刻被掐断"） */
      let receivedAny = false
      /** 是否已由 done / business-error 事件收尾 */
      let finished = false

      while (!finished) {
        const { done, value } = await reader.read()
        const events = done ? parser.flush() : parser.push(decoder.decode(value, { stream: true }))
        for (const event of events) {
          const action = classifySseEvent(event)
          if (action.kind === 'chunk') {
            receivedAny = true
            onData?.(action.chunk)
          } else if (action.kind === 'done') {
            receivedAny = true
            finished = true
            onDone?.()
            break
          } else if (action.kind === 'business-error') {
            // 后端写完错误事件后还会补一个 done，这里直接收尾并断开，不再继续读
            receivedAny = true
            finished = true
            onBusinessError?.(action.error)
            break
          }
        }
        if (done) break
      }

      if (finished) {
        // 已收尾：主动断开长连接（业务错误或 done 之后都不需要继续读）
        await reader.cancel().catch(() => {})
        return
      }
      if (receivedAny) {
        // 连接正常关闭（未收到 done 事件也视为完成）
        onDone?.()
      } else {
        // 一个事件都没收到就断了：不能当成功，否则页面会假装"生成完成"
        onError?.(toStreamError('连接已断开，未收到任何内容，请重试'))
      }
    } catch (error) {
      if (controller.signal.aborted) {
        // 主动取消，不视为错误
        return
      }
      onError?.(error instanceof Error ? (error as ChatStreamError) : toStreamError(String(error)))
    }
  })()

  // 返回取消函数
  return () => controller.abort()
}
