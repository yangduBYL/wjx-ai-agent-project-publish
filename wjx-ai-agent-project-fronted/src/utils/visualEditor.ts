/**
 * 可视化编辑（主网站侧）控制器
 *
 * 职责：
 *   1. 进入编辑模式时，动态把 visualEditorRuntime.js 注入到预览 iframe 的子页面中；
 *   2. 监听子页面通过 postMessage 回传的「用户选中的元素」信息，回调给页面组件；
 *   3. iframe 重新加载 / 被整体重建后，重新注入并保持编辑模式。
 *
 * 前提：父子页面必须同源（本地由 Vite 代理 /api、线上由 Nginx 代理保证预览地址是
 * 相对路径），否则父页面读不到 iframe.contentDocument，编辑脚本无从注入。
 * 页面组件的用法见 pages/AppGenerateView.vue 的「可视化编辑」小节。
 */
import runtimeSource from './visualEditorRuntime.js?raw'

/** 子页面回传消息的类型（与 visualEditorRuntime.js 中的常量保持一致） */
export const VISUAL_EDITOR_MESSAGE_TYPE = 'wjx:visual-editor:select'

/** 子窗口上挂载编辑器 API 的属性名（与 visualEditorRuntime.js 保持一致） */
const GLOBAL_API_KEY = '__WJX_VISUAL_EDITOR__'

/** 子窗口上保存注入令牌的属性名（与 visualEditorRuntime.js 保持一致） */
const GLOBAL_TOKEN_KEY = '__WJX_VISUAL_EDITOR_TOKEN__'

/** 子页面回传的「用户选中的页面元素」信息 */
export interface SelectedElementInfo {
  /** 标签名（小写，如 div） */
  tagName: string
  /** 元素 id（可能为空字符串） */
  id: string
  /** 元素 class 原始字符串（可能为空字符串） */
  className: string
  /** 元素到根节点的选择器路径（如 body > div#app > main > button） */
  selector: string
  /** 元素文本内容（空白已折叠，最长 60 字） */
  text: string
  /** 元素宽度（px） */
  width: number
  /** 元素高度（px） */
  height: number
}

/** 注入到子页面中的编辑器 API */
export interface InjectedVisualEditorApi {
  version: number
  isActive: () => boolean
  enable: () => void
  disable: () => void
  clearSelection: () => void
  destroy: () => void
}

export interface VisualEditorOptions {
  /** 子页面元素被选中（取消选中时 info 为 null）时回调 */
  onSelect?: (info: SelectedElementInfo | null) => void
  /** 无法进入编辑模式时回调（不同源、脚本无法执行等），用于给用户提示 */
  onError?: (reason: string) => void
}

/** 子页面回传的消息体结构 */
interface VisualEditorMessage {
  type?: string
  /** 注入令牌：由主网站在注入脚本前写入子窗口，脚本回传时原样带回 */
  token?: string
  payload?: SelectedElementInfo | null
}

export class VisualEditor {
  /** 绑定的预览 iframe（iframe 被整体重建后需要新建实例） */
  readonly iframe: HTMLIFrameElement
  private readonly options: VisualEditorOptions
  /** 已注入编辑器脚本的子文档，用于识别 iframe 是否被重新加载 */
  private injectedDoc: Document | null = null
  /** 子页面中的编辑器 API 引用 */
  private api: InjectedVisualEditorApi | null = null
  /** 当前是否处于编辑模式 */
  private active = false
  /**
   * 本次实例的注入令牌（一次性随机串）。
   * 注入脚本前写入子窗口，子页面回传消息时带上；主网站据此校验消息来源，
   * 比比对 `event.source` 与 `iframe.contentWindow` 的对象身份更可靠
   * （不同浏览器对 event.source 的实现存在差异）。
   */
  private readonly token = `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`

  constructor(iframe: HTMLIFrameElement, options: VisualEditorOptions = {}) {
    this.iframe = iframe
    this.options = options
    window.addEventListener('message', this.handleMessage)
  }

  /**
   * 判断预览页面与主页面是否同源。
   * 不同源时 iframe.contentDocument 无法读取（跨域会抛异常），可视化编辑不可用。
   */
  static isSameOrigin(iframe: HTMLIFrameElement | null | undefined): boolean {
    if (!iframe) return false
    try {
      const win = iframe.contentWindow
      if (!win || !iframe.contentDocument) return false
      return win.location.origin === window.location.origin
    } catch {
      // 跨域访问 location / contentDocument 会抛安全异常，说明不同源
      return false
    }
  }

  /** 当前是否处于编辑模式 */
  get isActive(): boolean {
    return this.active
  }

  /**
   * 进入编辑模式：注入编辑器脚本并激活。
   * @returns 是否成功进入（失败原因通过 onError 回调抛出）
   */
  enable(): boolean {
    const api = this.ensureInjected()
    if (!api) return false
    api.enable()
    this.active = true
    return true
  }

  /**
   * iframe 重新加载后调用：丢弃旧文档的引用，向新文档重新注入脚本并保持编辑模式。
   * @returns 是否仍然处于编辑模式
   */
  refresh(): boolean {
    if (!this.active) return false
    this.api = null
    this.injectedDoc = null
    return this.enable()
  }

  /** 退出编辑模式（子页面会清掉高亮并回传「取消选中」） */
  disable(): void {
    this.active = false
    this.invokeApi('disable')
  }

  /** 仅清除选中元素，保持编辑模式（输入框上方的 alert 被手动移除时调用） */
  clearSelection(): void {
    this.invokeApi('clearSelection')
  }

  /** 释放资源：解绑消息监听并销毁子页面中的编辑器实例 */
  destroy(): void {
    window.removeEventListener('message', this.handleMessage)
    this.invokeApi('destroy')
    this.api = null
    this.injectedDoc = null
    this.active = false
  }

  /** 读取子页面文档（不同源 / 尚未加载时返回 null） */
  private getChildDocument(): Document | null {
    try {
      return this.iframe.contentDocument ?? null
    } catch {
      return null
    }
  }

  /**
   * 确保编辑器脚本已注入子页面当前文档，并返回子页面的编辑器 API。
   * 注入失败（不同源、iframe 未加载、子页面禁用脚本）时返回 null 并回调 onError。
   */
  private ensureInjected(): InjectedVisualEditorApi | null {
    const doc = this.getChildDocument()
    const win = this.iframe.contentWindow
    if (!doc || !win) {
      this.options.onError?.('预览页面还未加载完成，请稍后重试')
      return null
    }
    if (!VisualEditor.isSameOrigin(this.iframe)) {
      this.options.onError?.(
        '预览页面与主页面不同源，无法进入可视化编辑（请确认预览地址走的是本站代理）',
      )
      return null
    }
    // 同一份文档且已注入过：直接复用，避免重复注入挂多套监听
    if (this.injectedDoc === doc && this.api) return this.api

    // 注入脚本：源码以字符串形式写入 <script>，在子页面自己的上下文中执行
    // 先把本次实例的令牌写入子窗口，注入的脚本会把它带在回传消息里
    ;(win as unknown as Record<string, unknown>)[GLOBAL_TOKEN_KEY] = this.token
    const script = doc.createElement('script')
    script.setAttribute('data-visual-editor-runtime', '')
    script.textContent = runtimeSource
    ;(doc.head ?? doc.documentElement).appendChild(script)
    // 执行完成后移除脚本节点，保持子页面 DOM 干净（API 已挂在 window 上）
    script.remove()

    const api = (win as unknown as Record<string, unknown>)[GLOBAL_API_KEY] as
      | InjectedVisualEditorApi
      | undefined
    if (!api || typeof api.enable !== 'function') {
      this.options.onError?.('无法在预览页面中启用可视化编辑（页面可能限制了脚本执行）')
      return null
    }
    this.injectedDoc = doc
    this.api = api
    return api
  }

  /** 安全调用子页面 API：iframe 卸载后调用会抛异常，这里统一忽略 */
  private invokeApi(method: 'disable' | 'clearSelection' | 'destroy'): void {
    const api = this.api
    if (!api) return
    try {
      api[method]()
    } catch {
      // iframe 已被卸载（文档已销毁），无需处理
    }
  }

  /** 子页面 postMessage 的接收端：校验消息来源后回调给页面组件 */
  private handleMessage = (event: MessageEvent) => {
    const data = event.data as VisualEditorMessage | null
    if (!data || data.type !== VISUAL_EDITOR_MESSAGE_TYPE) return
    // 1. 令牌校验（主要依据）：令牌由主网站在注入脚本前写入预览 iframe 的子窗口，
    //    只有本次注入进去的脚本才知道它，跨域页面拿不到，因此比比对 origin /
    //    event.source 的对象身份更可靠（后者在不同浏览器/环境下实现差异较大）。
    if (data.token !== this.token) return
    // 2. origin 校验（兜底）：拿到明确来源时要求同源（可视化编辑本身也要求父子同源）；
    //    个别环境（如无头 DOM 测试）取不到 origin 时为空串，此时以令牌为准。
    if (event.origin && event.origin !== window.location.origin) return
    this.options.onSelect?.(data.payload ?? null)
  }
}

/**
 * 把选中的元素信息拼进用户输入的提示词，交给后端 AI 做「定向修改」。
 * 元素描述放在用户输入之后，并用固定标题分隔，便于大模型定位目标元素。
 */
export function buildElementPrompt(content: string, info: SelectedElementInfo): string {
  const detail = [
    '【本次修改针对以下页面元素（用户在可视化编辑中选中）】',
    `- 标签：<${info.tagName}>`,
    `- 选择器：${info.selector}`,
    `- id：${info.id || '（无）'}`,
    `- class：${info.className || '（无）'}`,
    `- 尺寸：${info.width} × ${info.height} px`,
    `- 文本内容：${info.text || '（无文本）'}`,
    '',
    '请只修改上述元素，页面其它部分保持不变。',
  ]
  return `${content}\n\n${detail.join('\n')}`
}
