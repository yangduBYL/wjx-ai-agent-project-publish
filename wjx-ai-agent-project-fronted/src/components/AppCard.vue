<script setup lang="ts">
import { computed } from 'vue'
import { EyeOutlined, LinkOutlined, StarFilled } from '@ant-design/icons-vue'
import { DEFAULT_USER_AVATAR, isValidImageUrl } from '@/utils/app'
import { useUserStore } from '@/stores/user'

/**
 * 应用卡片：封面（含悬停"访问/详情"操作）+ 信息区
 * 信息区为左右结构：左侧创建者头像，右侧上下（应用标题 / 创建者昵称）
 */
const props = withDefaults(
  defineProps<{
    app: API.AppUserVo | API.AppVo
    /** 卡片尺寸 */
    size?: 'default' | 'small'
  }>(),
  {
    size: 'default',
  },
)

const emit = defineEmits<{
  (e: 'visit', app: API.AppUserVo | API.AppVo): void
  (e: 'detail', app: API.AppUserVo | API.AppVo): void
}>()

const userStore = useUserStore()

/** 封面地址（过滤 "88" 之类的脏值，避免向当前域名发起无意义请求） */
const coverUrl = computed(() => (isValidImageUrl(props.app.cover) ? props.app.cover : ''))

/** 是否精选应用（优先级 >= 99） */
const isFeatured = computed(() => Number(props.app.priority ?? 0) >= 99)

/** 是否本人创建的应用 */
const isOwn = computed(() => String(props.app.userId) === String(userStore.currentUser?.id))

/** 创建者头像：优先记录中的 uservo，本人应用取当前用户头像，否则用默认头像 */
const creatorAvatar = computed(() => {
  const uservo = (props.app as API.AppUserVo).uservo
  const avatar = uservo?.userAvatar || (isOwn.value ? userStore.currentUser?.userAvatar : '')
  return isValidImageUrl(avatar) ? avatar : DEFAULT_USER_AVATAR
})

/** 创建者昵称：优先记录中的 uservo，本人应用取当前用户昵称，否则匿名 */
const creatorName = computed(() => {
  const uservo = (props.app as API.AppUserVo).uservo
  if (uservo?.userName || uservo?.userAccount) {
    return uservo.userName || uservo.userAccount || '匿名用户'
  }
  if (isOwn.value) {
    return userStore.currentUser?.userName || userStore.currentUser?.userAccount || '我'
  }
  return '匿名用户'
})

/** 封面兜底：渐变 + 首字符 */
const fallbackChar = computed(() => (props.app.appName || 'A').charAt(0).toUpperCase())

function onVisit() {
  emit('visit', props.app)
}

function onDetail() {
  emit('detail', props.app)
}
</script>

<template>
  <div class="app-card" :class="`app-card-${size}`" @click="onDetail">
    <!-- 封面 -->
    <div class="app-card-cover">
      <img v-if="coverUrl" :src="coverUrl" :alt="app.appName" class="cover-img" />
      <div v-else class="cover-fallback">
        <span class="cover-char">{{ fallbackChar }}</span>
        <span class="cover-icon">🚀</span>
      </div>

      <span v-if="isFeatured" class="featured-tag">
        <StarFilled />
        精选
      </span>

      <!-- 悬停操作 -->
      <div class="cover-overlay">
        <a-button class="overlay-btn overlay-btn-primary" @click.stop="onVisit">
          <LinkOutlined />
          访问
        </a-button>
        <a-button class="overlay-btn" @click.stop="onDetail">
          <EyeOutlined />
          详情
        </a-button>
      </div>
    </div>

    <!-- 信息区：左右结构（左侧头像 + 右侧标题/昵称上下排布） -->
    <div class="app-card-body">
      <a-avatar :src="creatorAvatar" :size="38" class="creator-avatar" />
      <div class="body-right">
        <div class="app-name" :title="app.appName">{{ app.appName || '未命名应用' }}</div>
        <div class="creator-name" :title="creatorName">{{ creatorName }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.app-card {
  background: #fff;
  border-radius: 14px;
  overflow: hidden;
  border: 1px solid #eef0f6;
  box-shadow: 0 2px 10px rgba(30, 41, 82, 0.05);
  transition:
    transform 0.25s ease,
    box-shadow 0.25s ease;
  cursor: pointer;
}

.app-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 30px rgba(30, 41, 82, 0.14);
  border-color: #d8ddf0;
}

/* ---------- 封面 ---------- */
.app-card-cover {
  position: relative;
  height: 150px;
  overflow: hidden;
  background: linear-gradient(135deg, #eef1fb, #e4e9f8);
}

.app-card-small .app-card-cover {
  height: 120px;
}

.cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.35s ease;
}

.app-card:hover .cover-img {
  transform: scale(1.05);
}

.cover-fallback {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 4px;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 55%, #d946ef 100%);
}

.cover-char {
  font-size: 42px;
  font-weight: 800;
  color: rgba(255, 255, 255, 0.92);
  line-height: 1;
}

.cover-icon {
  font-size: 20px;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.2));
}

/* 精选标签 */
.featured-tag {
  position: absolute;
  top: 10px;
  left: 10px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px;
  border-radius: 999px;
  background: linear-gradient(90deg, #f59e0b, #fbbf24);
  color: #78350f;
  font-size: 12px;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(245, 158, 11, 0.4);
}

/* 悬停操作 */
.cover-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: rgba(15, 18, 40, 0.55);
  backdrop-filter: blur(3px);
  opacity: 0;
  transition: opacity 0.25s ease;
}

.app-card:hover .cover-overlay {
  opacity: 1;
}

.overlay-btn {
  border-radius: 999px;
  border-color: rgba(255, 255, 255, 0.6);
  color: #fff;
  background: rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(4px);
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.overlay-btn:hover {
  border-color: #fff !important;
  color: #fff !important;
  background: rgba(255, 255, 255, 0.25) !important;
}

.overlay-btn-primary {
  border-color: transparent;
  background: linear-gradient(90deg, #4f46e5, #7c3aed);
  color: #fff;
}

.overlay-btn-primary:hover {
  border-color: transparent !important;
  color: #fff !important;
  background: linear-gradient(90deg, #4338ca, #6d28d9) !important;
}

/* ---------- 信息区：左右结构 ---------- */
.app-card-body {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  min-width: 0;
}

.creator-avatar {
  flex-shrink: 0;
  border: 1px solid #eef0f6;
}

.body-right {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.app-name {
  font-size: 14.5px;
  font-weight: 600;
  color: #1f2430;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.creator-name {
  font-size: 12px;
  color: #98a0b3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
