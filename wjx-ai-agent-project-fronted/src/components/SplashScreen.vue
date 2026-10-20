<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import aiAvatar from '@/assets/ai-avatar.png'

const emit = defineEmits<{
  (e: 'finish'): void
}>()

// 动画状态：show -> leave（淡出）-> 移除
const show = ref(true)
const leaving = ref(false)
const progress = ref(0)

let timer: ReturnType<typeof setInterval> | null = null
let fadeTimer: ReturnType<typeof setTimeout> | null = null

const DURATION = 2400 // 过场动画总时长（毫秒）

function finish() {
  if (timer) clearInterval(timer)
  if (fadeTimer) clearTimeout(fadeTimer)
  show.value = false
  restoreBodyLock()
  emit('finish')
}

function skip() {
  finish()
}

/** 过场动画期间锁定页面滚动 */
function lockBody() {
  document.body.style.overflow = 'hidden'
}

function restoreBodyLock() {
  document.body.style.overflow = ''
}

onMounted(() => {
  lockBody()
  // 进度条匀速推进，接近满格后淡出
  const start = Date.now()
  timer = setInterval(() => {
    const elapsed = Date.now() - start
    const p = Math.min(elapsed / DURATION, 1)
    progress.value = Math.round(p * 100)
    if (p >= 1) {
      if (timer) clearInterval(timer)
      leaving.value = true
      fadeTimer = setTimeout(() => {
        show.value = false
        restoreBodyLock()
        emit('finish')
      }, 500)
    }
  }, 50)
})

onBeforeUnmount(() => {
  restoreBodyLock()
})
</script>

<template>
  <Transition name="splash-fade">
    <div v-if="show" class="splash" :class="{ 'splash-leaving': leaving }">
      <div class="splash-bg">
        <span class="orb orb-1"></span>
        <span class="orb orb-2"></span>
        <span class="orb orb-3"></span>
      </div>

      <div class="splash-content">
        <div class="splash-mascot-wrap">
          <img :src="aiAvatar" alt="AI 助手" class="splash-mascot" />
          <div class="splash-ring"></div>
        </div>

        <h1 class="splash-title">
          <span class="grad-text">AI 应用生成器</span>
        </h1>
        <p class="splash-subtitle">输入一句话，让 AI 为你生成一个网站应用</p>

        <div class="splash-bar">
          <div class="splash-bar-inner" :style="{ width: `${progress}%` }"></div>
        </div>
        <span class="splash-percent">{{ progress }}%</span>
      </div>

      <button class="splash-skip" @click="skip">跳过</button>
    </div>
  </Transition>
</template>

<style scoped>
.splash {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0b1026 0%, #1a1440 45%, #2b1055 100%);
  overflow: hidden;
}

/* 背景光晕 */
.splash-bg {
  position: absolute;
  inset: 0;
}
.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(70px);
  opacity: 0.55;
  animation: orb-float 6s ease-in-out infinite;
}
.orb-1 {
  width: 420px;
  height: 420px;
  background: #4f46e5;
  top: -120px;
  left: -100px;
}
.orb-2 {
  width: 380px;
  height: 380px;
  background: #7c3aed;
  bottom: -120px;
  right: -80px;
  animation-delay: 1.5s;
}
.orb-3 {
  width: 260px;
  height: 260px;
  background: #0ea5e9;
  top: 40%;
  left: 55%;
  animation-delay: 3s;
}
@keyframes orb-float {
  0%,
  100% {
    transform: translate(0, 0) scale(1);
  }
  50% {
    transform: translate(30px, -30px) scale(1.15);
  }
}

.splash-content {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  animation: content-in 0.9s cubic-bezier(0.22, 1, 0.36, 1) both;
}
@keyframes content-in {
  from {
    opacity: 0;
    transform: translateY(28px) scale(0.96);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

/* 吉祥物 */
.splash-mascot-wrap {
  position: relative;
  width: 148px;
  height: 148px;
  margin-bottom: 22px;
}
.splash-mascot {
  position: relative;
  z-index: 2;
  width: 148px;
  height: 148px;
  border-radius: 50%;
  object-fit: cover;
  border: 3px solid rgba(255, 255, 255, 0.35);
  box-shadow: 0 12px 40px rgba(79, 70, 229, 0.5);
  animation: mascot-bounce 2.2s ease-in-out infinite;
}
.splash-ring {
  position: absolute;
  inset: -14px;
  border-radius: 50%;
  border: 2px dashed rgba(139, 92, 246, 0.7);
  animation: ring-spin 8s linear infinite;
}
@keyframes mascot-bounce {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}
@keyframes ring-spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

/* 标题 */
.splash-title {
  margin: 0;
  font-size: 40px;
  font-weight: 800;
  letter-spacing: 2px;
}
.grad-text {
  background: linear-gradient(90deg, #60a5fa, #a78bfa, #f472b6);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  animation: title-shine 2.4s linear infinite;
  background-size: 200% auto;
}
@keyframes title-shine {
  to {
    background-position: 200% center;
  }
}

.splash-subtitle {
  margin: 12px 0 34px;
  color: rgba(255, 255, 255, 0.65);
  font-size: 15px;
  letter-spacing: 1px;
}

/* 进度条 */
.splash-bar {
  position: relative;
  width: 320px;
  height: 6px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.14);
  overflow: hidden;
}
.splash-bar-inner {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #60a5fa, #a78bfa, #f472b6);
  transition: width 0.12s linear;
}
.splash-percent {
  margin-top: 10px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.55);
  font-variant-numeric: tabular-nums;
}

.splash-skip {
  position: absolute;
  right: 28px;
  bottom: 24px;
  padding: 6px 18px;
  border: 1px solid rgba(255, 255, 255, 0.35);
  border-radius: 999px;
  background: transparent;
  color: rgba(255, 255, 255, 0.8);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.25s;
}
.splash-skip:hover {
  background: rgba(255, 255, 255, 0.15);
  border-color: rgba(255, 255, 255, 0.7);
}

/* 淡出 */
.splash-leaving {
  opacity: 0;
  transition: opacity 0.5s ease;
}
.splash-fade-enter-active,
.splash-fade-leave-active {
  transition: opacity 0.5s ease;
}
.splash-fade-enter-from,
.splash-fade-leave-to {
  opacity: 0;
}
</style>
