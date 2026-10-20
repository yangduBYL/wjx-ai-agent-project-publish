<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { CommentOutlined, LinkOutlined, RocketOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import {
  CodeGenTypeEnum,
  DEFAULT_USER_AVATAR,
  formatTime,
  getCodeGenTypeLabel,
  getDeployUrl,
  getPreviewUrl,
  isValidImageUrl,
  openVisitUrl,
} from '@/utils/app'

const props = defineProps<{
  open: boolean
  app: API.AppUserVo | API.AppVo | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
}>()

const router = useRouter()
const userStore = useUserStore()

const app = computed(() => props.app)

const creatorName = computed(() => {
  const uservo = (app.value as API.AppUserVo | undefined)?.uservo
  return uservo?.userName || uservo?.userAccount || '匿名用户'
})

const creatorAvatar = computed(() => {
  const uservo = (app.value as API.AppUserVo | undefined)?.uservo
  return uservo?.userAvatar || DEFAULT_USER_AVATAR
})

const previewUrl = computed(() => (app.value ? getPreviewUrl(app.value) : ''))
const deployedUrl = computed(() => (app.value ? getDeployUrl(app.value) : ''))
const isOwner = computed(() => app.value?.userId === userStore.currentUser?.id)
/** 封面地址（过滤脏值，避免向当前域名发起无意义请求） */
const coverUrl = computed(() => (isValidImageUrl(app.value?.cover) ? app.value!.cover : ''))

function onVisit() {
  if (app.value) openVisitUrl(app.value)
}

function onChat() {
  if (!app.value) return
  emit('update:open', false)
  router.push(`/app/generate/${app.value.id}`)
}

function onCopy(url: string) {
  navigator.clipboard
    ?.writeText(url)
    .then(() => message.success('链接已复制'))
    .catch(() => message.warning('复制失败，请手动复制'))
}

function onClose() {
  emit('update:open', false)
}

/** 代码生成类型展示 */
const codeGenTypeText = computed(() => {
  const type = app.value?.codeGenType
  if (!type) return '-'
  // 单文件模式补充说明底层就是 HTML；其余类型统一走文案映射
  return type === CodeGenTypeEnum.HTML ? '单文件模式 (HTML)' : getCodeGenTypeLabel(type)
})
</script>

<template>
  <a-modal
    :open="open"
    :footer="null"
    width="720px"
    :title="app?.appName || '应用详情'"
    @cancel="onClose"
  >
    <div v-if="app" class="detail-content">
      <!-- 封面 -->
      <div class="detail-cover">
        <img v-if="coverUrl" :src="coverUrl" :alt="app.appName" class="detail-cover-img" />
        <div v-else class="detail-cover-fallback">
          <span class="cover-char">{{ (app.appName || 'A').charAt(0).toUpperCase() }}</span>
          <span>🚀</span>
        </div>
      </div>

      <!-- 信息 -->
      <div class="detail-info">
        <a-descriptions :column="1" size="middle" bordered>
          <a-descriptions-item label="应用名称">
            {{ app.appName || '未命名应用' }}
          </a-descriptions-item>
          <a-descriptions-item label="创建者">
            <a-avatar :src="creatorAvatar" :size="20" style="margin-right: 6px" />
            {{ creatorName }}
          </a-descriptions-item>
          <a-descriptions-item label="代码模式">
            {{ codeGenTypeText }}
          </a-descriptions-item>
          <a-descriptions-item label="创建时间">
            {{ formatTime(app.createTime) }}
          </a-descriptions-item>
          <a-descriptions-item v-if="app.deployedTime" label="部署时间">
            {{ formatTime(app.deployedTime) }}
          </a-descriptions-item>
          <a-descriptions-item label="初始提示词">
            <div class="init-prompt">{{ app.initprompt || '暂无' }}</div>
          </a-descriptions-item>
          <a-descriptions-item label="本地预览地址">
            <div class="url-line">
              <span class="url-text">{{ previewUrl }}</span>
              <a @click="onCopy(previewUrl)">复制</a>
            </div>
          </a-descriptions-item>
          <a-descriptions-item v-if="deployedUrl" label="部署地址">
            <div class="url-line">
              <span class="url-text">{{ deployedUrl }}</span>
              <a @click="onCopy(deployedUrl)">复制</a>
            </div>
          </a-descriptions-item>
        </a-descriptions>

        <!-- 操作 -->
        <div class="detail-actions">
          <a-button type="primary" class="action-btn" @click="onVisit">
            <LinkOutlined />
            访问应用
          </a-button>
          <a-button v-if="isOwner" class="action-btn" @click="onChat">
            <CommentOutlined />
            进入对话
          </a-button>
          <a-button v-else-if="deployedUrl" class="action-btn" @click="openVisitUrl(app)">
            <RocketOutlined />
            打开部署地址
          </a-button>
        </div>
      </div>
    </div>
  </a-modal>
</template>

<style scoped>
.detail-content {
  display: flex;
  gap: 20px;
}

.detail-cover {
  width: 240px;
  height: 180px;
  flex-shrink: 0;
  border-radius: 12px;
  overflow: hidden;
}

.detail-cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.detail-cover-fallback {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 4px;
  font-size: 20px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6, #d946ef);
  color: #fff;
}

.cover-char {
  font-size: 44px;
  font-weight: 800;
}

.detail-info {
  flex: 1;
  min-width: 0;
}

.init-prompt {
  max-height: 90px;
  overflow-y: auto;
  color: #4b5563;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.url-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.url-text {
  font-size: 12px;
  color: #6b7280;
  word-break: break-all;
}

.detail-actions {
  display: flex;
  gap: 10px;
  margin-top: 18px;
}

.action-btn {
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

@media (max-width: 640px) {
  .detail-content {
    flex-direction: column;
  }
  .detail-cover {
    width: 100%;
  }
}
</style>
