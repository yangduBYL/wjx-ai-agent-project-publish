/**
 * 代码块处理工具：从 AI 回复中提取纯代码 + 语言识别 + 高亮
 *
 * 实际 AI 输出格式为：一段自然语言介绍 + ```json 围栏包裹的结构化 JSON
 * （{"title":"...","description":"...","code":"<!DOCTYPE html>..."}），
 * 也可能直接输出纯代码。这里解析出"介绍文本 + 纯代码"，用于聊天区展示与复制。
 */
// 按需引入 highlight.js 核心与常用语言，避免全量打包（体积减小 ~1MB）
import hljs from 'highlight.js/lib/core'
import xml from 'highlight.js/lib/languages/xml'
import css from 'highlight.js/lib/languages/css'
import javascript from 'highlight.js/lib/languages/javascript'
import json from 'highlight.js/lib/languages/json'
import typescript from 'highlight.js/lib/languages/typescript'
import markdown from 'highlight.js/lib/languages/markdown'

hljs.registerLanguage('xml', xml)
hljs.registerLanguage('css', css)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('json', json)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('markdown', markdown)

export interface CodeExtraction {
  /** 自然语言介绍（可能为空） */
  intro: string
  /** 提取出的纯代码 */
  code: string
}

/** 尝试把一段内容按 JSON 解析并取出代码字段（非 JSON 或无 code 字段返回 null） */
function tryParseJsonCode(content: string): string | null {
  const t = content.trim()
  if (!t || (t[0] !== '{' && t[0] !== '[')) return null
  try {
    const obj = JSON.parse(t)
    if (typeof obj.code === 'string') return obj.code
    // 兼容 {html: "...", css: "...", js: "..."} 结构
    if (typeof obj.html === 'string') return obj.html
    return null
  } catch {
    return null
  }
}

/** 从 AI 回复中提取"介绍文本 + 纯代码" */
export function extractCodeParts(raw: string): CodeExtraction {
  const text = raw.trim()

  // 1. 存在 ``` 围栏：围栏内可能是 JSON（含 code 字段）或直接是代码
  const fenceMatch = text.match(/```[\w-]*\s*\n([\s\S]*?)```/)
  if (fenceMatch) {
    const intro = text.slice(0, fenceMatch.index ?? 0).trim()
    const blockContent = fenceMatch[1].trim()
    const parsedCode = tryParseJsonCode(blockContent)
    if (parsedCode !== null) {
      return { intro, code: parsedCode }
    }
    return { intro, code: blockContent }
  }

  // 2. 整个回复就是 JSON
  const parsedCode = tryParseJsonCode(text)
  if (parsedCode !== null) {
    return { intro: '', code: parsedCode }
  }

  // 3. 原样返回
  return { intro: '', code: raw }
}

/** 从 AI 回复文本中提取纯代码（提取失败时原样返回） */
export function extractCode(raw: string): string {
  return extractCodeParts(raw).code
}

/**
 * 根据代码内容粗判语言（返回 highlight.js 的语言名）。
 * 空字符串表示无法判断，交由 highlightAuto 自动识别。
 */
export function detectLanguage(code: string): string {
  const t = code.trimStart()
  if (!t) return ''

  // 完整 HTML 文档
  if (/^<!doctype\s+html/i.test(t) || /^<html[\s>]/i.test(t) || /^<head[\s>]/i.test(t)) {
    return 'xml'
  }
  // 含 <style>/<script> 的 HTML 片段
  if (/<style[\s>]/i.test(t) || /<script[\s>]/i.test(t)) {
    return 'xml'
  }
  // CSS：选择器 + 花括号，或 @media 等
  if (/^@media|^@keyframes|^[.#@]?[\w-]+\s*\{/.test(t)) {
    return 'css'
  }
  // JavaScript / TypeScript
  if (/^(function|const|let|var|class|import|export|async|module\.exports)\b/.test(t)) {
    return 'javascript'
  }
  return ''
}

/** 语言标签展示名 */
export function languageLabel(lang: string): string {
  const map: Record<string, string> = {
    xml: 'HTML',
    css: 'CSS',
    javascript: 'JS',
    typescript: 'TS',
    json: 'JSON',
    markdown: 'MD',
  }
  return map[lang] || lang.toUpperCase() || 'CODE'
}

/** 使用 highlight.js 将代码渲染为高亮 HTML（hljs 输出已做 HTML 转义，可安全 v-html） */
export function highlightCode(code: string): string {
  if (!code) return ''
  const lang = detectLanguage(code)
  if (lang && hljs.getLanguage(lang)) {
    return hljs.highlight(code, { language: lang }).value
  }
  return hljs.highlightAuto(code).value
}

export interface TreeReport {
  /** 围栏之前的介绍部分（生成计划 + 逐文件进度 + 汇总信息） */
  intro: string
  /** 文件结构层级文本 */
  tree: string
}

/**
 * 提取「Vue 工程生成报告」的文件结构块。
 *
 * Vue 模式一次写入十几个文件，后端不再回显源码，而是在回复末尾追加一段
 * ```tree 围栏包裹的「文件结构层级 + 每个文件说明 + 行数/大小」。
 * 这里把它单独取出来交给「文件结构」面板渲染，围栏之前/之后的内容都算介绍文本。
 */
export function extractTreeReport(raw: string): TreeReport | null {
  const text = (raw ?? '').trim()
  const match = text.match(/```tree[ \t]*\r?\n([\s\S]*?)```/)
  if (!match) return null
  const start = match.index ?? 0
  const intro = (text.slice(0, start) + text.slice(start + match[0].length)).trim()
  return { intro, tree: match[1].replace(/\s+$/, '') }
}
