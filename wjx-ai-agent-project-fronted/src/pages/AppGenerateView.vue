<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  ArrowLeftOutlined,
  CheckOutlined,
  CopyOutlined,
  DownloadOutlined,
  EditOutlined,
  LoadingOutlined,
  ReloadOutlined,
  RocketOutlined,
  SendOutlined,
  SyncOutlined,
  UpOutlined,
} from '@ant-design/icons-vue'
import { deploy, downloadAppCode, getappById } from '@/api/appController'
import { pageChathistory } from '@/api/chatHistoryController'
import { chatStream } from '@/utils/sse'
// 可视化编辑（同源 iframe 注入 + postMessage 通信）逻辑集中在 utils/visualEditor.ts，
// 页面只负责：开关编辑模式、展示/移除选中元素、把元素信息拼进提示词
import { buildElementPrompt, VisualEditor, type SelectedElementInfo } from '@/utils/visualEditor'
import {
  asId,
  CodeGenTypeEnum,
  DEFAULT_USER_AVATAR,
  getCodeGenTypeLabel,
  getPreviewUrl,
  isUrlReachable,
} from '@/utils/app'
import { detectLanguage, extractCode, extractCodeParts, extractTreeReport, highlightCode, languageLabel } from '@/utils/code'
import { isZipBlob, parseFileNameFromDisposition, readEnvelopeMessage, saveBlobAsFile } from '@/utils/download'
import { useUserStore } from '@/stores/user'
import aiAvatar from '@/assets/ai-avatar.png'
import 'highlight.js/styles/atom-one-dark.css'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 后端雪花 ID 以字符串返回（Long 序列化），严禁 Number() 转换，否则会丢失精度
const appId = (route.params.appId as string) || ''

/* ---------------- 应用信息 ---------------- */
const app = ref<API.App | null>(null)
const appLoading = ref(true)

/* ---------------- 对话消息 ---------------- */
interface ChatMessage {
  role: 'user' | 'ai'
  /** 流式累积的原始文本 */
  content: string
  /** 消息创建时间（历史消息使用，也是游标分页的游标值） */
  createTime?: string
  /** 是否正在流式生成中 */
  streaming?: boolean
  /** 生成完成后的自然语言介绍（AI 回复中代码前的那段话） */
  intro?: string
  /** 生成完成后提取的纯代码（用于高亮展示与复制） */
  displayContent?: string
  /** 生成完成后的高亮 HTML */
  highlightedHtml?: string
  /** 识别出的代码语言（如 xml / css / javascript） */
  codeLang?: string
  /** 是否为 Vue 工程生成报告：此时 displayContent 是「文件结构树 + 文件说明」，不是源码 */
  report?: boolean
  /** 生成失败原因（后端 business-error 事件，如限流 42900）：气泡里以 ❌ 前缀展示 */
  error?: string
}

const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const sending = ref(false)
const messageListRef = ref<HTMLElement | null>(null)
/** 记录刚完成复制的消息下标（用于展示对勾） */
const copiedIndex = ref<number | null>(null)

/* ---------------- 打字机效果（流式渲染节流） ---------------- */
// SSE 事件到达速度远快于浏览器渲染帧率，直接逐事件追加会被浏览器合并渲染，
// 视觉上"一次性"出现。这里把新到达的文本累积到 pendingText，每 ~33ms 渲染一次，
// 形成逐字输出的打字机效果。
let pendingText = ''
let flushTimer: ReturnType<typeof setTimeout> | null = null

function flushPending(aiMsg: ChatMessage) {
  if (pendingText) {
    aiMsg.content += pendingText
    pendingText = ''
    scrollToBottom()
  }
  flushTimer = null
}

/** 立即把缓冲的文本刷到界面上（流结束时调用） */
function flushNow(aiMsg: ChatMessage) {
  if (flushTimer) {
    clearTimeout(flushTimer)
    flushTimer = null
  }
  if (pendingText) {
    aiMsg.content += pendingText
    pendingText = ''
  }
}

/**
 * 生成完成：提取介绍文本 + 纯代码并高亮（只执行一次，避免流式期间频繁高亮导致卡顿）。
 * AI 的历史消息同样走这里，保证刷新后历史消息的渲染与新生成的消息一致。
 */
function finalizeAiMessage(aiMsg: ChatMessage) {
  const raw = (aiMsg.content || '').trim()
  // Vue 工程模式：回复体是「生成计划 + 逐文件进度 + 文件结构树」，没有源码，
  // 因此结构树不做语法高亮，单独标记为 report 交给「文件结构」面板渲染
  if (isVueProject.value) {
    const report = extractTreeReport(raw)
    if (report) {
      aiMsg.report = true
      aiMsg.intro = report.intro
      aiMsg.displayContent = report.tree
      aiMsg.codeLang = ''
      aiMsg.highlightedHtml = ''
      return
    }
  }
  // 回复里没有代码围栏（AI 的追问/报错说明等纯文本回复）：按普通消息渲染，不展示代码块
  if (!raw.includes('```')) {
    aiMsg.intro = raw
    aiMsg.displayContent = ''
    aiMsg.codeLang = ''
    aiMsg.highlightedHtml = ''
    return
  }
  const { intro, code } = extractCodeParts(raw)
  aiMsg.intro = intro
  aiMsg.displayContent = code
  aiMsg.codeLang = detectLanguage(code)
  aiMsg.highlightedHtml = highlightCode(code)
}

/* ---------------- 预览 ---------------- */
const previewUrl = computed(() => (app.value ? getPreviewUrl(app.value) : ''))
const previewReady = ref(false)
const previewLoading = ref(true)
const iframeKey = ref(0)
/** 是否 Vue 工程模式（构建产物在 dist 目录，地址与其它类型不同） */
const isVueProject = computed(() => app.value?.codeGenType === CodeGenTypeEnum.VUE_PROJECT)
/** Vue 工程异步构建等待中（构建完成后页面会自动刷新出来） */
const previewBuilding = ref(false)
/** 自动刷新（Vue 工程构建等待）定时器 */
let previewRetryTimer: number | null = null
/** Vue 工程构建期间自动刷新预览的次数与间隔 */
const PREVIEW_RETRY_TIMES = 6
const PREVIEW_RETRY_INTERVAL = 10000

/** 清理 Vue 工程构建等待的自动刷新定时器 */
function clearPreviewRetry() {
  if (previewRetryTimer !== null) {
    clearInterval(previewRetryTimer)
    previewRetryTimer = null
  }
  previewBuilding.value = false
}

/**
 * 展示预览面板。
 * Vue 工程模式的构建是异步的（后端 doOnComplete 里 buildProjectAsync：npm install + build），
 * 生成完成后 dist 目录可能还不存在，直接加载 iframe 会看到 404 页面，
 * 因此这里在有限次数内自动刷新预览，等构建完成。
 */
function showPreview() {
  previewReady.value = true
  previewLoading.value = true
  iframeKey.value += 1
  clearPreviewRetry()
  if (!isVueProject.value) return
  previewBuilding.value = true
  let remaining = PREVIEW_RETRY_TIMES
  previewRetryTimer = window.setInterval(() => {
    remaining -= 1
    if (remaining < 0) {
      clearPreviewRetry()
      return
    }
    previewLoading.value = true
    iframeKey.value += 1
  }, PREVIEW_RETRY_INTERVAL)
}

/* ---------------- 可视化编辑（同源 iframe 注入 + postMessage） ---------------- */
// 进入编辑模式后：父页面动态把编辑器脚本注入预览 iframe（子页面），
// 子页面负责「悬浮出边框 / 点击固定加深」，并通过 postMessage 把选中的元素信息回传，
// 父页面把元素信息显示在输入框上方，发送消息时拼进提示词。
// 同源是前提：预览地址必须是相对路径（走 Vite 代理 / Nginx 反代），见 .env.development。
const previewIframeRef = ref<HTMLIFrameElement | null>(null)
/** 是否处于可视化编辑模式 */
const editMode = ref(false)
/** 当前选中的元素信息（展示在输入框上方，可手动移除） */
const selectedElement = ref<SelectedElementInfo | null>(null)
/** 可视化编辑器实例（绑定到具体的 iframe 元素，iframe 被重建时需要新建） */
let visualEditor: VisualEditor | null = null

/**
 * 校验并获取编辑器实例。
 * iframe 元素被整体重建（如刷新预览、生成完成后重新展示）时必须重新创建实例，
 * 否则消息监听仍挂在旧窗口上、也拿不到新文档。
 */
function ensureVisualEditor(): VisualEditor | null {
  const iframe = previewIframeRef.value
  if (!iframe) {
    message.warning('预览尚未就绪，请稍后再试')
    return null
  }
  // 文档还没加载出来时会拿不到 contentDocument，此时不能按「不同源」提示（避免误导）
  if (!iframe.contentDocument || !iframe.contentWindow) {
    message.warning('预览页面还未加载完成，请稍后再试')
    return null
  }
  // 不同源时父页面读不到子页面 DOM，无法注入编辑脚本，直接给出原因
  if (!VisualEditor.isSameOrigin(iframe)) {
    message.warning('预览页面与主页面不同源，无法进入可视化编辑（请确认预览地址走的是本站代理）')
    return null
  }
  if (!visualEditor || visualEditor.iframe !== iframe) {
    visualEditor?.destroy()
    visualEditor = new VisualEditor(iframe, {
      // 子页面回传的选中结果（null 表示取消选中）
      onSelect: (info) => {
        selectedElement.value = info
      },
      // 注入失败等异常：提示用户并退出编辑模式
      onError: (reason) => {
        message.warning(reason)
        exitEditMode()
      },
    })
  }
  return visualEditor
}

/** 进入 / 退出可视化编辑模式（输入框左侧按钮） */
function toggleEditMode() {
  if (editMode.value) {
    exitEditMode()
    return
  }
  if (!previewReady.value) {
    message.warning('请先生成网站，再进入可视化编辑')
    return
  }
  const editor = ensureVisualEditor()
  if (!editor) return
  // 每次进入编辑模式都从「未选中」开始
  selectedElement.value = null
  editor.clearSelection()
  if (!editor.enable()) return
  editMode.value = true
  message.success('已进入可视化编辑：点击右侧网站中的元素进行选择')
}

/** 退出编辑模式：清除选中元素与子页面的高亮边框 */
function exitEditMode() {
  editMode.value = false
  selectedElement.value = null
  visualEditor?.disable()
}

/** 手动移除已选中的元素（点击 alert 的关闭按钮），保持编辑模式以便重新选择 */
function clearSelectedElement() {
  selectedElement.value = null
  visualEditor?.clearSelection()
}

/* ---------------- 部署 ---------------- */
const deploying = ref(false)
const deployUrl = ref('')
/** 部署地址是否可达（8090 静态服务是否启动） */
const deployUnreachable = ref(false)
const deployChecking = ref(false)

/** 当前用户头像：优先用户设置的头像，未设置使用默认头像 */
const currentUserAvatar = computed(() => {
  const av = userStore.currentUser?.userAvatar
  return av ? av : DEFAULT_USER_AVATAR
})

/** 是否本人应用（后端对话接口只允许本人访问） */
const isOwner = computed(() => !!app.value && app.value.userId === userStore.currentUser?.id)
const canChat = computed(() => !!userStore.isLogin() && isOwner.value && !sending.value)

/* ---------------- 限流冷却（后端 Redisson @RateLimit 触发 42900 后） ---------------- */
/** 与后端 AppController#chatToGenCode 上的 @RateLimit(rate = 5, rateInterval = 60) 保持一致 */
const RATE_LIMIT_WINDOW_SECONDS = 60
/** 未登录错误码，与后端 ErrorCode.NOT_LOGIN_ERROR 一致 */
const NOT_LOGIN_CODE = 40100
/** 请求过于频繁错误码，与后端 ErrorCode.TOO_MANY_REQUEST 一致 */
const TOO_MANY_REQUEST_CODE = 42900
/** 冷却剩余秒数（> 0 时禁用发送按钮并提示，避免用户继续点击白白触发限流） */
const cooldownSeconds = ref(0)
let cooldownTimer: ReturnType<typeof setInterval> | null = null

/** 进入限流冷却：每秒刷新剩余秒数，归零后自动恢复可发送 */
function startRateLimitCooldown(seconds = RATE_LIMIT_WINDOW_SECONDS) {
  const deadline = Date.now() + seconds * 1000
  if (cooldownTimer) clearInterval(cooldownTimer)
  const tick = () => {
    cooldownSeconds.value = Math.max(0, Math.ceil((deadline - Date.now()) / 1000))
    if (cooldownSeconds.value <= 0 && cooldownTimer) {
      clearInterval(cooldownTimer)
      cooldownTimer = null
    }
  }
  tick()
  cooldownTimer = setInterval(tick, 1000)
}

function clearRateLimitCooldown() {
  if (cooldownTimer) {
    clearInterval(cooldownTimer)
    cooldownTimer = null
  }
  cooldownSeconds.value = 0
}

/** 是否可以点击发送（冷却期间不拦输入框，只拦发送动作） */
const canSend = computed(() => canChat.value && cooldownSeconds.value === 0)

/** 取消当前流式请求 */
let cancelStream: (() => void) | null = null

/* ---------------- 消息滚动 ---------------- */
async function scrollToBottom() {
  await nextTick()
  const el = messageListRef.value
  if (el) el.scrollTop = el.scrollHeight
}

/* ---------------- 对话历史（游标分页） ---------------- */
/** 每页加载的历史消息条数（与后端默认值保持一致） */
const HISTORY_PAGE_SIZE = 10
/** 首次加载历史中 */
const historyLoading = ref(false)
/** 正在加载更早的一页历史 */
const loadingMore = ref(false)
/** 是否还有更早的历史消息（游标分页） */
const hasMoreHistory = ref(false)
/** 已从后端加载的历史消息条数（不含本次会话新发送的消息） */
const loadedHistoryCount = ref(0)
/** 该应用的历史消息总数（取首页 totalRow，同时用于判断是否展示网站） */
const historyTotal = ref(0)

/** 后端返回的历史记录（创建时间降序）→ 聊天区消息（创建时间升序） */
function toChatMessages(records: API.ChatHistory[]): ChatMessage[] {
  return records
    .slice()
    .reverse()
    .map((item) => {
      // 必须用 reactive()：历史消息同样要参与后续的响应式更新（如复制状态、流式追加）
      const msg = reactive<ChatMessage>({
        role: item.messageType === 'ai' ? 'ai' : 'user',
        content: item.message ?? '',
        createTime: item.createTime,
      })
      if (msg.role === 'ai') finalizeAiMessage(msg)
      return msg
    })
}

/**
 * 加载对话历史：
 *   - 不传游标：加载最近一页（后端按 createTime 降序返回，这里反转成升序展示）
 *   - 传游标：加载更早的一页（游标 = 当前已加载消息里最旧一条的创建时间）
 */
async function loadHistory(cursor?: string) {
  const isFirstPage = !cursor
  if (isFirstPage) historyLoading.value = true
  else loadingMore.value = true
  try {
    const res = await pageChathistory({
      // appId 保持字符串透传（雪花 ID 禁止 Number() 转换）
      appId: asId(appId),
      pageSize: HISTORY_PAGE_SIZE,
      // 游标必须传 ISO 格式（2026-09-10T22:33:50）：接口返回的 createTime 是
      // "2026-09-10 22:33:50"（空格分隔），直接作为参数后端无法反序列化成
      // LocalDateTime（实测返回 50000 系统错误），因此这里把空格替换成 T。
      lastCreateTime: cursor ? cursor.replace(' ', 'T') : undefined,
    })
    const records = res?.data?.records ?? []
    if (isFirstPage) {
      // 注意：后端 Page 的 totalRow 序列化后是字符串（如 "13"），这里显式转成数字
      // （计数不是雪花 ID，转为 Number 没有精度风险）
      historyTotal.value = Number(res?.data?.totalRow ?? records.length) || 0
      messages.value = toChatMessages(records)
    } else {
      // 更早的消息插入到列表最前面，整体仍然按创建时间升序展示
      messages.value.unshift(...toChatMessages(records))
    }
    loadedHistoryCount.value += records.length
    // 首页 totalRow 是该应用的消息总数，游标页 totalRow 是剩余条数；
    // 统一用「已加载条数 < 总数」判断是否还有更早的历史
    hasMoreHistory.value =
      records.length >= HISTORY_PAGE_SIZE && loadedHistoryCount.value < historyTotal.value
  } catch {
    // 非本人/非管理员查看时后端会拒绝（40101），此时静默降级为只读展示
    if (isFirstPage) hasMoreHistory.value = false
  } finally {
    historyLoading.value = false
    loadingMore.value = false
  }
}

/** 点击「加载更多」：按游标加载更早的一页历史，并保持当前阅读位置不跳动 */
async function handleLoadMore() {
  if (loadingMore.value || !hasMoreHistory.value) return
  // 已加载消息里最旧的一条（升序展示，所以就是第一条）的创建时间即游标
  const oldest = messages.value[0]?.createTime
  if (!oldest) return
  const el = messageListRef.value
  const prevHeight = el?.scrollHeight ?? 0
  const prevTop = el?.scrollTop ?? 0
  await loadHistory(oldest)
  await nextTick()
  // 顶部插入内容后，把视口固定在用户原来看到的位置
  if (el) el.scrollTop = prevTop + (el.scrollHeight - prevHeight)
}

/* ---------------- 发送消息 ---------------- */
async function sendMessage(text?: string) {
  const rawText = (text ?? inputText.value).trim()
  if (!rawText) {
    message.warning('请输入消息')
    return
  }
  if (!app.value) return
  if (!userStore.isLogin()) {
    const redirect = encodeURIComponent(route.fullPath)
    router.push(`/user/login?redirect=${redirect}`)
    return
  }
  if (!isOwner.value) {
    message.warning('只能对自己的应用进行对话生成')
    return
  }
  if (sending.value) {
    message.warning('AI 正在生成中，请稍候')
    return
  }
  // 上一次被后端限流（42900）后的冷却期内直接拦住，避免白点一次再吃一个错误
  if (cooldownSeconds.value > 0) {
    message.warning(`请求过于频繁，请 ${cooldownSeconds.value} 秒后再试`)
    return
  }

  // 可视化编辑：把选中的元素信息追加到提示词中，一起发给后端
  const element = selectedElement.value
  const content = element ? buildElementPrompt(rawText, element) : rawText

  // 用户消息 + AI 占位消息（气泡里展示的就是真正发给后端的提示词，与历史记录一致）
  messages.value.push({ role: 'user', content })
  inputText.value = ''
  // 必须用 reactive() 创建：推入响应式数组后，后续对 content/streaming 等字段的
  // 逐字累积修改才会触发视图更新；若用普通对象，改动全部发生在“原始对象”上，
  // Vue 无法感知，界面会一直空白直到生成结束才一次性渲染出全部文本（打字机失效）。
  const aiMsg: ChatMessage = reactive({ role: 'ai', content: '', streaming: true })
  messages.value.push(aiMsg)
  sending.value = true
  scrollToBottom()

  // 发送后：清除选中元素并退出可视化编辑模式，后续流程与之前完全一致
  exitEditMode()

  // SSE 流式接收（后端每个 chunk 包装为 data:{"d":"..."}，结束发送 event:done）
  cancelStream = chatStream(
    '/app/chat/gen/code',
    { appId: appId, message: content },
    {
      onData: (chunk) => {
        // 累积到缓冲，按 ~33ms 节流渲染，形成逐字打字机效果
        pendingText += chunk
        if (!flushTimer) {
          flushTimer = setTimeout(() => flushPending(aiMsg), 33)
        }
      },
      onDone: () => {
        // 1. 立即刷出剩余缓冲
        flushNow(aiMsg)
        aiMsg.streaming = false
        sending.value = false
        // 2. 生成完成：提取纯代码并高亮
        finalizeAiMessage(aiMsg)
        // 3. 流式全部返回后，展示生成的网站效果
        //    （Vue 工程模式后端还在异步构建，内部会自动刷新预览等待 dist 产物）
        showPreview()
        message.success(
          isVueProject.value ? '代码生成完成，Vue 工程正在构建，请稍候…' : '网站生成完成！',
        )
        scrollToBottom()
      },
      // 后端业务错误事件（business-error）：Redisson 限流 42900、未登录 40100、系统错误 50000 等。
      // 以前这种负载会被当成普通数据块拼进气泡（显示成一段 JSON），还会跟着弹出"生成完成"，这里彻底处理掉。
      onBusinessError: (err) => {
        flushNow(aiMsg)
        aiMsg.streaming = false
        sending.value = false
        const text = err.message || '生成过程中出现错误'
        // 原因留在气泡里（❌ 前缀），用户往上翻对话还能看到当时为什么失败
        aiMsg.error = text
        if (err.code === NOT_LOGIN_CODE) {
          message.error('登录已过期，请重新登录')
          const redirect = encodeURIComponent(route.fullPath)
          router.push(`/user/login?redirect=${redirect}`)
          return
        }
        message.error(text)
        // 命中限流：进入冷却倒计时，冷却结束前发送按钮保持禁用
        // （42900 是后端 @RateLimit 抛的；429 是网关层限流，一起当成限流处理）
        if (err.code === TOO_MANY_REQUEST_CODE || err.code === 429) {
          startRateLimitCooldown()
        }
        scrollToBottom()
      },
      // 传输层错误（网络中断、HTTP 状态异常等）：错误信息里已带上后端返回的原因
      onError: (error) => {
        flushNow(aiMsg)
        aiMsg.streaming = false
        sending.value = false
        if (error.code === NOT_LOGIN_CODE || error.message.includes('HTTP 401')) {
          message.error('登录已过期，请重新登录')
          const redirect = encodeURIComponent(route.fullPath)
          router.push(`/user/login?redirect=${redirect}`)
          return
        }
        aiMsg.error = error.message || '生成失败'
        message.error(`生成失败：${error.message}`)
        scrollToBottom()
      },
    },
  )
}

/* ---------------- 部署 ---------------- */
async function handleDeploy() {
  if (!app.value) return
  if (!isOwner.value) {
    message.warning('只能部署自己的应用')
    return
  }
  deploying.value = true
  // Vue 工程模式部署前需要先构建（npm install + npm run build），耗时可能达几分钟，
  // 全局 60s 超时会提前断开，这里单独放宽超时并给出等待提示
  const vueProject = isVueProject.value
  const hideLoading = vueProject
    ? message.loading('Vue 工程正在构建（首次需 npm install），请耐心等待…', 0)
    : null
  try {
    // appId 保持字符串透传（运行时不做转换），避免雪花 ID 精度丢失
    const res = await deploy(
      { appId: asId(app.value.id ?? appId) },
      vueProject ? { timeout: 600000 } : undefined,
    )
    deployUrl.value = res?.data || ''
    deployUnreachable.value = false
    message.success('部署成功！')
    // 异步探测部署地址是否可达（8090 静态服务是否已启动）
    if (deployUrl.value) {
      deployChecking.value = true
      const ok = await isUrlReachable(deployUrl.value)
      deployChecking.value = false
      deployUnreachable.value = !ok
      if (!ok) {
        message.warning('部署成功，但 8090 端口静态服务未启动，暂时无法访问部署地址')
      }
    }
  } catch {
    // 错误已由拦截器提示
  } finally {
    hideLoading?.()
    deploying.value = false
  }
}

function copyDeployUrl() {
  if (!deployUrl.value) return
  navigator.clipboard
    ?.writeText(deployUrl.value)
    .then(() => message.success('部署地址已复制'))
    .catch(() => message.warning('复制失败，请手动复制'))
}

/** 复制 AI 回复中的代码 */
async function copyCode(msg: ChatMessage, index: number) {
  // 生成完成后优先复制提取出的纯代码，流式中复制当前累积内容
  const text = msg.displayContent ?? extractCode(msg.content)
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    copiedIndex.value = index
    message.success(msg.report ? '文件结构已复制' : '代码已复制')
    setTimeout(() => {
      if (copiedIndex.value === index) copiedIndex.value = null
    }, 2000)
  } catch {
    message.warning('复制失败，请手动复制')
  }
}

function openDeployUrl() {
  if (deployUrl.value) window.open(deployUrl.value, '_blank', 'noopener,noreferrer')
}

/** 打开本地预览地址（部署服务不可用时的兜底） */
function openPreviewUrl() {
  if (previewUrl.value) window.open(previewUrl.value, '_blank', 'noopener,noreferrer')
}

/* ---------------- 下载代码 ---------------- */
const downloading = ref(false)

/**
 * 下载当前应用的代码（后端打成 zip 返回）。
 * 走 @api 生成的 downloadAppCode，并把 responseType 设为 blob；
 * 响应拦截器对二进制响应直接返回完整响应对象，因此这里能拿到
 * data（Blob）和 Content-Disposition（下载文件名在后端响应头里）。
 */
async function handleDownloadCode() {
  if (!app.value || downloading.value) return
  if (!isOwner.value) {
    message.warning('只能下载自己的应用代码')
    return
  }
  downloading.value = true
  try {
    // appId 保持字符串透传（雪花 ID 禁止 Number() 转换）
    const res = await downloadAppCode({ appId: asId(app.value.id ?? appId) }, { responseType: 'blob' })
    const blob = res?.data as Blob | undefined
    // 后端业务异常时返回的是 JSON 信封而不是 zip（HTTP 200 + code/message），
    // 直接保存会得到一个内容为报错 JSON 的「假 zip」，这里先识别出来提示用户
    if (!blob || blob.type.includes('json')) {
      message.error((blob && (await readEnvelopeMessage(blob))) || '下载失败，请稍后重试')
      return
    }
    // 再校验一次 ZIP 文件头：响应已提交后失败时拿到的是残缺包，不能存成文件
    if (!(await isZipBlob(blob))) {
      message.error('下载失败：返回内容不是有效的 ZIP 包')
      return
    }
    // 文件名优先用响应头里的，取不到再兜底成 {appId}.zip
    const fileName =
      parseFileNameFromDisposition(res?.headers?.['content-disposition']) || `${app.value.id ?? appId}.zip`
    saveBlobAsFile(blob, fileName)
    message.success('代码下载已开始')
  } catch {
    // 错误已由响应拦截器统一提示
  } finally {
    downloading.value = false
  }
}

/* ---------------- 初始化 ---------------- */
onMounted(async () => {
  // 0. 移除 URL 上的 view 参数：是否自动发送初始消息、是否展示网站，一律由应用自身
  //    状态（是否本人应用 + 有无对话历史 + 是否已部署）决定，不再依赖 URL 参数
  if (route.query.view !== undefined) {
    const query = { ...route.query }
    delete query.view
    router.replace({ path: route.path, query })
  }

  // 1. 加载应用信息（该接口后端直接返回实体，无信封包裹）
  try {
    // id 保持字符串透传，禁止 Number() 转换（雪花 ID 会丢失精度）
    app.value = await getappById({ id: asId(appId) })
    if (!app.value) {
      message.error('应用不存在或已删除')
      router.replace('/')
      return
    }
  } catch {
    message.error('应用不存在或已删除')
    router.replace('/')
    return
  } finally {
    appLoading.value = false
  }

  // 2. 等待登录态就绪（判断应用归属、对话历史权限都要用到登录用户）
  if (!userStore.loaded) {
    await userStore.fetchLoginUser()
  }

  // 3. 加载最近 10 条对话历史（按创建时间升序展示）。
  //    后端只允许应用创建者与管理员查看，其他人直接跳过，避免无权限请求
  //    被后端拒绝后弹出错误提示
  if (isOwner.value || userStore.isAdmin()) {
    await loadHistory()
  }

  // 4. 已有 deployKey 说明已生成过代码并部署，直接展示预览和部署地址
  //    （Vue 工程部署时后端已把 dist 内容拷到部署目录，构建产物必然存在，无需等待）
  if (app.value?.deployKey) {
    deployUrl.value = `http://localhost:8090/${app.value.deployKey}/`
    previewReady.value = true
    previewLoading.value = false
  }

  // 5. 网站展示逻辑：已部署，或该应用至少有 2 条对话记录
  //    （每成功生成一次会写入「用户消息 + AI 消息」两条记录）时，都展示对应的网站
  if (historyTotal.value >= 2) {
    showPreview()
  }

  // 6. 自动发送初始消息：仅当「是自己的应用」且「没有任何对话历史」时，
  //    才把 initPrompt 作为第一条消息触发对话（已有的历史对话不会被覆盖）
  const initPrompt = (route.query.prompt as string | undefined) || app.value?.initprompt
  if (isOwner.value && historyTotal.value === 0 && initPrompt) {
    await nextTick()
    sendMessage(initPrompt)
  }
})

onBeforeUnmount(() => {
  // 离开页面时中断流式请求、预览自动刷新，并销毁可视化编辑器（解绑 iframe 的消息监听）
  cancelStream?.()
  clearPreviewRetry()
  clearRateLimitCooldown()
  visualEditor?.destroy()
  visualEditor = null
})

/** 预览 iframe 加载完成 */
function onPreviewLoaded() {
  // 注意：Vue 工程构建未完成时 dist/index.html 返回 404，浏览器同样会触发 load 事件，
  // 因此这里不能据此判定构建完成，只能关掉 loading 动画
  previewLoading.value = false
  // 编辑模式下 iframe 可能被重新加载（刷新预览、iframeKey 变化重建），
  // 新文档需要重新注入编辑器脚本；注入失败（如不同源）则自动退出编辑模式
  if (editMode.value) {
    const refreshed = visualEditor?.refresh() ?? false
    if (!refreshed) exitEditMode()
  }
}

/** 刷新预览（Vue 工程可能会重新构建，继续自动等待） */
function reloadPreview() {
  showPreview()
}

/** 返回首页 */
function goHome() {
  router.push('/')
}
</script>

<template>
  <div class="gen-page">
    <!-- 顶部栏：左侧应用名称，右侧部署按钮 -->
    <header class="gen-header">
      <div class="header-left">
        <a-button type="text" class="back-btn" @click="goHome">
          <ArrowLeftOutlined />
        </a-button>
        <a-spin :spinning="appLoading" size="small">
          <div class="app-title-area">
            <span class="app-title">{{ app?.appName || 'AI 应用生成' }}</span>
            <span v-if="app?.codeGenType" class="app-type-tag">
              {{ getCodeGenTypeLabel(app.codeGenType) }}
            </span>
          </div>
        </a-spin>
      </div>

      <div class="header-right">
        <!-- 下载代码：位于部署按钮左侧，仅应用创建者可下载（与后端权限校验一致） -->
        <a-button
          class="header-btn"
          :loading="downloading"
          :disabled="!isOwner"
          @click="handleDownloadCode"
        >
          <DownloadOutlined v-if="!downloading" />
          下载代码
        </a-button>
        <template v-if="deployUrl">
          <a-tag :color="deployUnreachable ? 'warning' : 'success'" class="deployed-tag">
            {{ deployUnreachable ? '服务未启动' : '已部署' }}
          </a-tag>
          <a-button class="header-btn" @click="copyDeployUrl">
            <CopyOutlined />
            复制地址
          </a-button>
          <a-button class="header-btn" @click="openDeployUrl">
            <RocketOutlined />
            访问部署
          </a-button>
        </template>
        <a-button
          v-else
          type="primary"
          class="deploy-btn"
          :loading="deploying"
          :disabled="!isOwner"
          @click="handleDeploy"
        >
          <RocketOutlined v-if="!deploying" />
          部署
        </a-button>
      </div>
    </header>

    <!-- 部署成功但静态服务未启动时的提示条 -->
    <a-alert
      v-if="deployUnreachable"
      type="warning"
      show-icon
      banner
      class="deploy-alert"
      message="部署文件已生成，但无法访问部署地址：8090 端口没有静态服务在运行"
      description="请在项目目录启动静态服务器托管 code_deploy 目录（例如：python -m http.server 8090 -d temp/code_deploy），启动后即可通过部署地址访问。"
    >
      <template #action>
        <a-button size="small" class="alert-action-btn" @click="openPreviewUrl">
          先用本地预览查看
        </a-button>
      </template>
    </a-alert>

    <!-- 核心内容区 -->
    <div class="gen-body">
      <!-- 左侧：对话区域 -->
      <section class="chat-panel">
        <div ref="messageListRef" class="chat-messages">
          <!-- 加载更多：用游标加载更早的历史消息（按钮位于消息上方） -->
          <div v-if="hasMoreHistory || loadingMore" class="load-more-wrap">
            <a-button class="load-more-btn" :loading="loadingMore" @click="handleLoadMore">
              <UpOutlined v-if="!loadingMore" />
              加载更多
            </a-button>
            <span v-if="historyTotal" class="load-more-hint">
              已加载 {{ messages.length }} / 共 {{ historyTotal }} 条
            </span>
          </div>

          <!-- 历史消息加载中 -->
          <div v-if="historyLoading && messages.length === 0" class="chat-loading">
            <LoadingOutlined spin />
            正在加载对话历史...
          </div>

          <div v-else-if="messages.length === 0" class="chat-empty">
            <img :src="aiAvatar" alt="AI" class="empty-avatar" />
            <p class="empty-title">与 AI 对话，生成你的网站</p>
            <p class="empty-desc">发送消息后，AI 会实时流式生成网站代码，完成后在右侧展示效果</p>
            <div v-if="app?.initprompt" class="empty-initprompt">
              <span class="label">初始提示词：</span>
              <span class="text">{{ app.initprompt }}</span>
            </div>
          </div>

          <div
            v-for="(msg, index) in messages"
            :key="index"
            class="msg-row"
            :class="msg.role === 'user' ? 'msg-user' : 'msg-ai'"
          >
            <!-- AI 头像（吉祥物），位于消息左侧 -->
            <a-avatar v-if="msg.role === 'ai'" :src="aiAvatar" :size="34" class="msg-avatar" />
            <div class="msg-bubble-wrap">
              <div class="msg-bubble" :class="msg.role === 'user' ? 'bubble-user' : 'bubble-ai'">
                <!-- AI 回复：介绍文本 + 代码块（工具栏 + 内容） -->
                <div v-if="msg.role === 'ai'">
                  <div
                    v-if="msg.intro"
                    class="ai-intro"
                    :class="{ 'intro-only': !msg.streaming && !msg.displayContent }"
                  >
                    {{ msg.intro }}
                  </div>
                  <!-- 生成中或回复中确实包含代码时才展示代码块 -->
                  <div v-if="msg.streaming || msg.displayContent" class="code-wrap">
                    <div class="code-toolbar">
                      <span class="code-lang">
                        <span v-if="msg.streaming" class="lang-generating">
                          <LoadingOutlined spin />
                          生成中
                        </span>
                        <template v-else>{{
                          msg.report ? '文件结构' : languageLabel(msg.codeLang || detectLanguage(msg.content))
                        }}</template>
                      </span>
                      <a-button type="text" size="small" class="copy-btn" @click="copyCode(msg, index)">
                        <CheckOutlined v-if="copiedIndex === index" />
                        <CopyOutlined v-else />
                        {{ copiedIndex === index ? '已复制' : '复制' }}
                      </a-button>
                    </div>
                    <!-- 流式中：纯文本 + 光标（打字机效果） -->
                    <pre v-if="msg.streaming" class="code-block streaming-block">{{ msg.content }}<span
                        class="cursor"
                      >▍</span
                      ></pre>
                    <!-- 生成完成：Vue 工程 → 文件结构树（纯文本，不做语法高亮） -->
                    <pre v-else-if="msg.report" class="code-block tree-block">{{ msg.displayContent }}</pre>
                    <!-- 生成完成：其它模式 → 代码高亮渲染 -->
                    <pre v-else class="code-block highlighted-block" v-html="msg.highlightedHtml || ''"></pre>
                  </div>
                  <!-- 生成失败：后端 business-error 事件（限流 42900 / 未登录 40100 / 系统错误 50000…） -->
                  <div v-if="msg.error" class="ai-error">❌ {{ msg.error }}</div>
                </div>
                <div v-else class="user-text">{{ msg.content }}</div>
              </div>
              <div v-if="msg.role === 'ai' && msg.streaming" class="msg-status">
                <LoadingOutlined spin />
                AI 正在生成代码...
              </div>
            </div>
            <!-- 用户头像（默认使用用户设置的默认头像图），位于消息最右侧 -->
            <a-avatar
              v-if="msg.role === 'user'"
              :src="currentUserAvatar"
              :size="34"
              class="msg-avatar"
            />
          </div>
        </div>

        <!-- 用户消息输入框 -->
        <div class="chat-input-area">
          <!-- 可视化编辑选中的元素信息：显示在输入框上方，可手动移除 -->
          <a-alert
            v-if="selectedElement"
            class="selected-element-alert"
            type="info"
            show-icon
            closable
            :message="`已选中元素 <${selectedElement.tagName}${selectedElement.id ? '#' + selectedElement.id : ''}>`"
            @close="clearSelectedElement"
          >
            <template #description>
              <div class="element-detail">
                <span class="element-item">
                  <em>选择器</em>{{ selectedElement.selector }}
                </span>
                <span class="element-item">
                  <em>class</em>{{ selectedElement.className || '（无）' }}
                </span>
                <span class="element-item">
                  <em>尺寸</em>{{ selectedElement.width }} × {{ selectedElement.height }} px
                </span>
                <span v-if="selectedElement.text" class="element-item">
                  <em>文本</em>{{ selectedElement.text }}
                </span>
              </div>
            </template>
          </a-alert>

          <a-textarea
            v-model:value="inputText"
            class="chat-input"
            placeholder="输入你想让 AI 修改或生成的内容，例如：把标题改成红色、加一个轮播图..."
            :auto-size="{ minRows: 1, maxRows: 4 }"
            :disabled="!canChat"
            @keydown.enter.exact.prevent="sendMessage()"
          />
          <div class="input-footer">
            <span v-if="!isOwner" class="input-hint">仅应用创建者可进行 AI 对话</span>
            <span v-else-if="editMode" class="input-hint">编辑模式：点击右侧网站中的元素进行选择</span>
            <!-- 被后端限流后的冷却提示（@RateLimit 每分钟 5 次） -->
            <span v-else-if="cooldownSeconds > 0" class="input-hint rate-limit-hint">
              {{ cooldownSeconds }} 秒后可再次发送（每分钟最多 5 次）
            </span>
            <span v-else class="input-hint">Enter 发送</span>
            <div class="input-actions">
              <!-- 可视化编辑开关：位于发送按钮左侧 -->
              <a-button
                class="edit-mode-btn"
                :class="{ 'edit-mode-btn-active': editMode }"
                :disabled="!previewReady || previewLoading || sending"
                @click="toggleEditMode"
              >
                <EditOutlined />
                {{ editMode ? '退出编辑' : '可视化编辑' }}
              </a-button>
              <a-button
                type="primary"
                class="send-btn"
                :disabled="!canSend"
                @click="sendMessage()"
              >
                <SendOutlined v-if="!sending" />
                <LoadingOutlined v-else />
                {{ sending ? '生成中' : cooldownSeconds > 0 ? `冷却 ${cooldownSeconds}s` : '发送' }}
              </a-button>
            </div>
          </div>
        </div>
      </section>

      <!-- 右侧：网页展示区域 -->
      <section class="preview-panel" :class="{ 'preview-editing': editMode }">
        <div class="preview-header">
          <div class="preview-header-left">
            <span class="preview-title">
              <SyncOutlined :spin="previewLoading" />
              网站预览
            </span>
            <a-tag v-if="editMode" color="processing" class="preview-mode-tag">编辑模式</a-tag>
          </div>
          <a-button size="small" type="text" @click="reloadPreview">
            <ReloadOutlined />
            刷新
          </a-button>
        </div>

        <div class="preview-body">
          <template v-if="previewReady">
            <a-spin :spinning="previewLoading" wrapper-class-name="preview-spin">
              <iframe
                :key="iframeKey"
                ref="previewIframeRef"
                :src="previewUrl"
                class="preview-iframe"
                referrerpolicy="no-referrer"
                sandbox="allow-scripts allow-same-origin allow-forms allow-popups"
                @load="onPreviewLoaded"
              />
            </a-spin>
            <!-- Vue 工程模式：后端 npm install + build 是异步的，构建期间给出等待提示 -->
            <div v-if="previewBuilding" class="preview-build-tip">
              <LoadingOutlined spin />
              Vue 工程正在构建（首次需安装依赖），构建完成后会自动加载，也可点击右上角刷新
            </div>
          </template>
          <div v-else class="preview-placeholder">
            <img :src="aiAvatar" alt="AI" class="placeholder-avatar" />
            <p class="placeholder-title">等待生成网站...</p>
            <p class="placeholder-desc">AI 完成代码生成后，将在这里实时展示网站效果</p>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.gen-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f4f6fb;
  overflow: hidden;
}

/* ---------- 顶部栏 ---------- */
.gen-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  padding: 0 20px;
  background: #fff;
  border-bottom: 1px solid #eef0f6;
  flex-shrink: 0;
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.back-btn {
  color: #4b5563;
  border-radius: 999px;
}

.back-btn:hover {
  color: #4f46e5 !important;
  background: #eef0ff !important;
}

.app-title-area {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.app-title {
  font-size: 17px;
  font-weight: 700;
  color: #1f2430;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 320px;
}

.app-type-tag {
  font-size: 12px;
  color: #6b7280;
  background: #f1f3fa;
  padding: 2px 10px;
  border-radius: 999px;
  flex-shrink: 0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.deployed-tag {
  margin-inline-end: 0;
}

.deploy-btn {
  border-radius: 999px;
  background: linear-gradient(90deg, #4f46e5, #7c3aed);
  border: none;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.deploy-btn:hover {
  background: linear-gradient(90deg, #4338ca, #6d28d9) !important;
}

.header-btn {
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

/* 部署服务未启动提示条 */
.deploy-alert {
  flex-shrink: 0;
}

.alert-action-btn {
  border-radius: 999px;
}

/* ---------- 主体 ---------- */
.gen-body {
  flex: 1;
  display: flex;
  min-height: 0;
}

/* ---------- 左侧对话区 ---------- */
.chat-panel {
  display: flex;
  flex-direction: column;
  width: 46%;
  min-width: 380px;
  max-width: 620px;
  background: #fff;
  border-right: 1px solid #eef0f6;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

/* 「加载更多」：位于消息上方，用游标加载更早的历史消息 */
.load-more-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.load-more-btn {
  border-radius: 999px;
  height: 30px;
  padding: 0 18px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #4f46e5;
  border-color: #c9cbfb;
  background: #f2f3ff;
}

.load-more-btn:hover {
  color: #4338ca !important;
  border-color: #818cf8 !important;
  background: #e5e7ff !important;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.25);
}

.load-more-hint {
  font-size: 12px;
  color: #a0a6b8;
}

/* 历史消息加载中 */
.chat-loading {
  margin: auto;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #98a0b3;
}

.chat-empty {
  margin: auto;
  text-align: center;
  max-width: 380px;
  color: #98a0b3;
}

.empty-avatar {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  object-fit: cover;
  margin-bottom: 14px;
  box-shadow: 0 8px 24px rgba(99, 102, 241, 0.35);
  animation: float 3s ease-in-out infinite;
}

@keyframes float {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-6px);
  }
}

.empty-title {
  margin: 0 0 6px;
  font-size: 16px;
  font-weight: 600;
  color: #4b5563;
}

.empty-desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
}

.empty-initprompt {
  margin-top: 18px;
  padding: 12px 14px;
  border-radius: 10px;
  background: #f6f7fd;
  text-align: left;
  font-size: 13px;
  line-height: 1.7;
}

.empty-initprompt .label {
  color: #4f46e5;
  font-weight: 600;
}

.empty-initprompt .text {
  color: #6b7280;
  word-break: break-all;
}

/* 消息行 */
.msg-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

/* 用户消息：整体靠右，头像位于消息最右侧（气泡的右边） */
.msg-user {
  flex-direction: row;
  justify-content: flex-end;
}

.msg-avatar {
  flex-shrink: 0;
  margin-top: 2px;
}

.msg-bubble-wrap {
  max-width: 78%;
  min-width: 0;
}

.msg-bubble {
  border-radius: 12px;
  padding: 10px 14px;
  font-size: 14px;
  line-height: 1.65;
  word-break: break-word;
}

.bubble-user {
  background: linear-gradient(135deg, #4f46e5, #6d28d9);
  color: #fff;
  border-top-right-radius: 4px;
}

.bubble-ai {
  background: #f4f5fb;
  color: #1f2937;
  border-top-left-radius: 4px;
  padding: 0;
  overflow: hidden;
  width: 100%;
}

.user-text {
  white-space: pre-wrap;
}

/* AI 代码块 */
.ai-intro {
  padding: 12px 14px 8px;
  color: #4b5563;
  font-size: 13.5px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
}

/* 没有代码块的纯文本 AI 回复：底部补足内边距 */
.ai-intro.intro-only {
  padding-bottom: 12px;
}

/* 生成失败提示（后端 business-error 事件：限流 / 未登录 / 系统错误）：
   浅红底 + 深红字，和代码块视觉区分开，一眼能看出这条消息是失败的 */
.ai-error {
  margin: 4px 14px 12px;
  padding: 8px 12px;
  border: 1px solid #ffd8d6;
  border-radius: 8px;
  background: #fff5f5;
  color: #c33a34;
  font-size: 13px;
  line-height: 1.6;
  word-break: break-all;
}

.code-wrap {
  width: 100%;
  margin: 0 14px 14px;
  border-radius: 12px;
  overflow: hidden;
  background: #141a33;
  border: 1px solid #1e2440;
}

.code-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 5px 8px 5px 14px;
  background: #0f152e;
  border-bottom: 1px solid #1e2440;
}

.code-lang {
  font-size: 12px;
  font-weight: 600;
  color: #818cf8;
  letter-spacing: 0.5px;
}

.lang-generating {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #98a0b3;
  font-weight: 400;
}

.copy-btn {
  color: rgba(255, 255, 255, 0.7);
  font-size: 12px;
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  padding: 0 8px;
}

.copy-btn:hover {
  color: #fff !important;
  background: rgba(255, 255, 255, 0.12) !important;
}

.code-block {
  margin: 0;
  padding: 12px 14px;
  font-family: 'Consolas', 'Menlo', 'Courier New', monospace;
  font-size: 12.5px;
  line-height: 1.6;
  color: #d1d5db;
  background: #141a33;
  max-height: 420px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

/* 高亮后的代码块：hljs 主题色由全局样式提供，这里保证底色一致 */
.highlighted-block {
  background: #141a33;
}

/* Vue 工程的「文件结构」面板：必须用 pre 保留缩进与对齐（占位空格不能被折叠） */
.tree-block {
  background: #141a33;
  white-space: pre;
  word-break: normal;
  overflow-x: auto;
  font-size: 12px;
  line-height: 1.75;
}

.highlighted-block :deep(.hljs-comment),
.highlighted-block :deep(.hljs-quote) {
  color: #7d8799;
  font-style: italic;
}

.cursor {
  color: #818cf8;
  animation: blink 0.8s infinite;
}

@keyframes blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0;
  }
}

.msg-status {
  margin-top: 6px;
  font-size: 12px;
  color: #98a0b3;
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 输入区 */
.chat-input-area {
  padding: 14px 16px 16px;
  border-top: 1px solid #eef0f6;
  background: #fff;
}

.chat-input {
  border-radius: 12px;
  background: #f7f8fc;
  border: 1px solid transparent;
  resize: none;
  font-size: 14px;
}

.chat-input:focus {
  background: #fff;
  border-color: #818cf8;
  box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.12);
}

.input-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.input-hint {
  font-size: 12px;
  color: #a0a6b8;
}

/* 限流冷却提示：用琥珀色和普通提示区分开 */
.rate-limit-hint {
  color: #d4821a;
}

/* ---------- 可视化编辑 ---------- */
/* 输入框上方的「已选中元素」提示条（antd alert，可点击右上角关闭移除） */
.selected-element-alert {
  margin-bottom: 10px;
  border-radius: 10px;
  border-color: #dcdff8;
  background: #f5f6ff;
}

.selected-element-alert :deep(.ant-alert-icon) {
  color: #4f46e5;
}

.element-detail {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 16px;
  font-size: 12.5px;
  line-height: 1.7;
}

.element-item {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  max-width: 100%;
  color: #4b5563;
  word-break: break-all;
}

.element-item em {
  font-style: normal;
  flex-shrink: 0;
  color: #8c93a8;
}

.input-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* 可视化编辑开关：与发送按钮同款胶囊形态 */
.edit-mode-btn {
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border-color: #c9cbfb;
  color: #4f46e5;
  background: #f6f7ff;
}

.edit-mode-btn:hover {
  color: #4338ca !important;
  border-color: #818cf8 !important;
  background: #eef0ff !important;
}

/* 编辑模式激活态：填充主题色，与「已进入编辑」状态呼应 */
.edit-mode-btn-active,
.edit-mode-btn-active:hover {
  color: #fff !important;
  background: #4f46e5 !important;
  border-color: #4f46e5 !important;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.3);
}

.send-btn {
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: linear-gradient(90deg, #4f46e5, #7c3aed);
  border: none;
}

.send-btn:hover {
  background: linear-gradient(90deg, #4338ca, #6d28d9) !important;
}

/* ---------- 右侧预览区 ---------- */
.preview-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: #eef1f7;
}

.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 46px;
  padding: 0 18px;
  background: #fff;
  border-bottom: 1px solid #eef0f6;
}

.preview-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #374151;
}

.preview-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

/* 编辑模式标识：让用户随时知道「点击会选中元素」而不是正常浏览 */
.preview-mode-tag {
  margin-inline-end: 0;
  border-radius: 999px;
}

.preview-body {
  flex: 1;
  padding: 16px;
  min-height: 0;
  position: relative;
}

/* Vue 工程构建等待提示（浮在预览区底部） */
.preview-build-tip {
  position: absolute;
  left: 50%;
  bottom: 26px;
  transform: translateX(-50%);
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: 90%;
  padding: 8px 16px;
  border-radius: 999px;
  background: rgba(31, 36, 48, 0.86);
  color: #fff;
  font-size: 12.5px;
  line-height: 1.5;
  box-shadow: 0 6px 18px rgba(31, 36, 48, 0.25);
  z-index: 5;
}

.preview-spin {
  width: 100%;
  height: 100%;
}

.preview-spin :deep(.ant-spin-container) {
  width: 100%;
  height: 100%;
}

.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 4px 20px rgba(30, 41, 82, 0.1);
}

/* 编辑模式下的预览区高亮：外圈主题色描边，提示当前处于拾取元素状态 */
.preview-editing .preview-iframe {
  box-shadow:
    0 0 0 2px rgba(79, 70, 229, 0.4),
    0 4px 20px rgba(30, 41, 82, 0.12);
}

.preview-placeholder {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: #fff;
  border-radius: 10px;
  color: #98a0b3;
}

.placeholder-avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  object-fit: cover;
  opacity: 0.85;
}

.placeholder-title {
  margin: 8px 0 0;
  font-size: 15px;
  font-weight: 600;
  color: #6b7280;
}

.placeholder-desc {
  margin: 0;
  font-size: 13px;
}

@media (max-width: 900px) {
  .chat-panel {
    width: 100%;
    min-width: 0;
    max-width: none;
  }
  .preview-panel {
    display: none;
  }
}
</style>
