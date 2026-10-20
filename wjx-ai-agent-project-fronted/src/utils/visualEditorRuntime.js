/**
 * 可视化编辑 —— 注入到子页面（预览 iframe）中运行的运行时脚本
 *
 * 主网站（父页面）进入编辑模式时，通过 iframe.contentDocument 把本文件的源码
 * 以字符串形式动态注入子页面（见 utils/visualEditor.ts 的 ?raw 引入），
 * 脚本运行在「子页面自己」的上下文中，负责：
 *   1. 鼠标悬浮元素 → 元素出现浅色虚线边框（悬浮高亮）
 *   2. 点击元素 → 边框固定为深色实线（选中），并通过 postMessage 把元素信息回传主网站
 *   3. 编辑模式下拦截子页面自身的交互（链接跳转、按钮提交等），避免误触
 *
 * 前提：父子页面必须同源（本地用 Vite 代理、线上用 Nginx 代理保证预览地址为相对路径），
 * 否则父页面既拿不到 contentDocument，postMessage 的 targetOrigin 也无法匹配。
 *
 * 实现说明：
 *   - 不改动子页面 DOM 结构，也不注入 <style>，全部通过 CSSOM 改写内联样式，
 *     因此不会被子页面的 CSP（style-src / script-src）策略拦掉；
 *   - 会备份被改写元素的原内联样式，退出编辑时逐字还原；
 *   - 监听统一注册在 window 的「捕获阶段」，保证先于子页面自身的监听执行，
 *     不会被子页面里的 stopPropagation 挡掉。
 */
;(function () {
  'use strict'

  /** 与主网站约定的消息类型（utils/visualEditor.ts 中同名常量） */
  const MESSAGE_TYPE = 'wjx:visual-editor:select'
  /** 悬浮态边框：浅色虚线 */
  const HOVER_OUTLINE = '2px dashed #818cf8'
  /** 选中态边框：比悬浮态更深、更粗，用于「固定加深」的视觉反馈 */
  const SELECTED_OUTLINE = '3px solid #4f46e5'
  /** 边框内缩，避免边框超出元素自身所占区域 */
  const OUTLINE_OFFSET = '-2px'
  /** 生成选择器时最多向上追溯的层级 */
  const MAX_SELECTOR_DEPTH = 6
  /** 回传的文本内容最多截取的字符数 */
  const MAX_TEXT_LENGTH = 60

  // 父子同源，targetOrigin 取当前页面的源即可（既满足 postMessage 的安全校验，
  // 也不会因为端口/域名不一致导致消息被丢弃）
  const TARGET_ORIGIN = window.location.origin
  // 主网站在注入本脚本之前写入子窗口的一次性令牌：回传消息时带上它，
  // 主网站据此确认消息确实来自「本次注入的编辑器」，而不依赖 event.source 的对象身份比对
  const TOKEN = window.__WJX_VISUAL_EDITOR_TOKEN__ || ''

  /** 是否处于编辑模式 */
  let active = false
  /** 当前悬浮高亮的元素 */
  let hoverEl = null
  /** 当前被选中（边框固定加深）的元素 */
  let selectedEl = null
  /** 被改写内联样式的元素 → 原始内联样式备份（元素被移除后自动回收） */
  const savedStyles = new WeakMap()

  /* ---------------- 内联样式读写 ---------------- */

  function saveStyle(el) {
    if (!el || !el.style || savedStyles.has(el)) return
    savedStyles.set(el, {
      outline: el.style.outline,
      outlineOffset: el.style.outlineOffset,
      cursor: el.style.cursor,
    })
  }

  function restoreStyle(el) {
    if (!el || !el.style) return
    const saved = savedStyles.get(el)
    if (!saved) return
    el.style.outline = saved.outline
    el.style.outlineOffset = saved.outlineOffset
    el.style.cursor = saved.cursor
    savedStyles.delete(el)
  }

  /** 给元素加上边框（先备份原样式，退出编辑时还原） */
  function applyOutline(el, outline) {
    if (!el || el.nodeType !== 1 || !el.style) return
    saveStyle(el)
    el.style.outline = outline
    el.style.outlineOffset = OUTLINE_OFFSET
    el.style.cursor = 'crosshair'
  }

  /* ---------------- 元素信息描述 ---------------- */

  /** 生成元素到根节点的选择器路径，如 body > div#app > main > button:nth-of-type(2) */
  function buildSelector(el) {
    const parts = []
    let node = el
    let depth = 0
    while (node && node.nodeType === 1 && depth < MAX_SELECTOR_DEPTH) {
      // 有 id 就足够定位，直接终止向上追溯（id 在页面中唯一）
      if (node.id) {
        parts.unshift('#' + node.id)
        break
      }
      let part = node.tagName.toLowerCase()
      const classNames = (node.getAttribute('class') || '').trim().split(/\s+/).filter(Boolean)
      if (classNames.length) {
        part += '.' + classNames.slice(0, 2).join('.')
      }
      const parent = node.parentElement
      if (parent) {
        const sameTagSiblings = Array.prototype.filter.call(
          parent.children,
          (child) => child.tagName === node.tagName,
        )
        if (sameTagSiblings.length > 1) {
          part += ':nth-of-type(' + (sameTagSiblings.indexOf(node) + 1) + ')'
        }
      }
      parts.unshift(part)
      node = node.parentElement
      depth += 1
    }
    return parts.join(' > ') || el.tagName.toLowerCase()
  }

  /** 汇总选中元素的信息（回传给主网站，最终拼进提示词交给 AI） */
  function describe(el) {
    const rect = typeof el.getBoundingClientRect === 'function' ? el.getBoundingClientRect() : null
    return {
      tagName: el.tagName.toLowerCase(),
      id: el.id || '',
      className: (el.getAttribute('class') || '').trim(),
      selector: buildSelector(el),
      text: (el.textContent || '').replace(/\s+/g, ' ').trim().slice(0, MAX_TEXT_LENGTH),
      width: rect ? Math.round(rect.width) : 0,
      height: rect ? Math.round(rect.height) : 0,
    }
  }

  /** 通过 postMessage 把选中结果回传主网站（info 为 null 表示取消选中） */
  function postMessageToParent(payload) {
    try {
      window.parent.postMessage(
        { type: MESSAGE_TYPE, token: TOKEN, payload: payload },
        TARGET_ORIGIN,
      )
    } catch {
      // 直接打开预览页（没有父页面）或跨域时忽略，不影响子页面自身
    }
  }

  /* ---------------- 交互事件 ---------------- */

  function clearHover() {
    if (hoverEl) {
      restoreStyle(hoverEl)
      hoverEl = null
    }
  }

  function clearSelection() {
    if (selectedEl) {
      restoreStyle(selectedEl)
      selectedEl = null
    }
    postMessageToParent(null)
  }

  function onMouseOver(event) {
    if (!active) return
    const el = event.target
    // 根节点（html）不参与选择；已被选中的元素保持深色边框，不被悬浮态覆盖
    if (!el || el.nodeType !== 1 || el === document.documentElement) return
    if (el === selectedEl || el === hoverEl) return
    clearHover()
    hoverEl = el
    applyOutline(el, HOVER_OUTLINE)
  }

  function onMouseOut(event) {
    if (!active || !hoverEl) return
    const el = event.target
    // relatedTarget 为空：鼠标移出了文档（跑到父页面区域），同样需要清理边框
    const stillInside = event.relatedTarget && el.contains(event.relatedTarget)
    if (el === hoverEl && !stillInside) clearHover()
  }

  function onClick(event) {
    if (!active) return
    // 编辑模式下屏蔽子页面自身的交互（链接跳转、按钮提交、折叠展开等），只做元素选中
    event.preventDefault()
    event.stopPropagation()
    const el = event.target
    if (!el || el.nodeType !== 1) return
    if (el === selectedEl) return
    // 上一个选中元素还原成普通状态（边框不叠加）
    if (selectedEl) restoreStyle(selectedEl)
    clearHover()
    selectedEl = el
    applyOutline(el, SELECTED_OUTLINE)
    postMessageToParent(describe(el))
  }

  function onSubmit(event) {
    if (!active) return
    event.preventDefault()
  }

  function registerListeners() {
    // 捕获阶段监听：先于子页面自身的监听执行，避免被子页面的 stopPropagation 拦截
    window.addEventListener('mouseover', onMouseOver, true)
    window.addEventListener('mouseout', onMouseOut, true)
    window.addEventListener('click', onClick, true)
    window.addEventListener('submit', onSubmit, true)
  }

  function removeListeners() {
    window.removeEventListener('mouseover', onMouseOver, true)
    window.removeEventListener('mouseout', onMouseOut, true)
    window.removeEventListener('click', onClick, true)
    window.removeEventListener('submit', onSubmit, true)
  }

  /* ---------------- 对外 API（主网站通过 iframe.contentWindow 调用） ---------------- */

  const api = {
    /** 版本号：主网站用它判断脚本是否注入成功 */
    version: 1,
    isActive: function () {
      return active
    },
    /** 进入编辑模式 */
    enable: function () {
      active = true
      // 统一光标样式，提示当前处于「拾取元素」状态
      document.documentElement.style.cursor = 'crosshair'
    },
    /** 退出编辑模式：清掉所有高亮与选中，并通知主网站取消选中 */
    disable: function () {
      active = false
      clearHover()
      clearSelection()
      document.documentElement.style.cursor = ''
    },
    /** 仅清除选中（保持编辑模式，供主网站移除单个选中元素时调用） */
    clearSelection: function () {
      clearSelection()
    },
    /** 释放：退出编辑模式并解绑全部监听 */
    destroy: function () {
      active = false
      clearHover()
      if (selectedEl) restoreStyle(selectedEl)
      selectedEl = null
      document.documentElement.style.cursor = ''
      removeListeners()
      window.__WJX_VISUAL_EDITOR__ = null
    },
  }

  // 同名脚本可能被重复注入（预览刷新后重新进入编辑模式），先销毁旧实例，
  // 避免同一文档上挂多套监听导致一次点击触发多次回调
  const previous = window.__WJX_VISUAL_EDITOR__
  if (previous && typeof previous.destroy === 'function') previous.destroy()

  registerListeners()
  window.__WJX_VISUAL_EDITOR__ = api
})()
