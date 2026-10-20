<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  ArrowLeftOutlined,
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  LikeFilled,
  LikeOutlined,
  LinkOutlined,
  MessageOutlined,
} from '@ant-design/icons-vue'
import {
  addCommunityComment,
  deleteCommunityComment,
  deleteCommunityPost,
  getCommunityPost,
  likeCommunityPost,
  listCommunityComments,
  listCommunityTags,
  unlikeCommunityPost,
  updateCommunityPost,
} from '@/api/communityController'
import { useUserStore } from '@/stores/user'
import ImageCoverPicker from '@/components/ImageCoverPicker.vue'
import {
  DEFAULT_USER_AVATAR,
  formatTime,
  getVisitUrl,
  isValidImageUrl,
} from '@/utils/app'
import type { CommunityCommentVO, CommunityPostVO, CommunityTagVO } from '@/types/community'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const postId = computed(() => String(route.params.id ?? ''))

const post = ref<CommunityPostVO | null>(null)
const loading = ref(false)
const notFound = ref(false)

const comments = ref<CommunityCommentVO[]>([])
const commentsLoading = ref(false)

/* ---------------- 加载 ---------------- */
async function loadPost() {
  if (!postId.value) return
  loading.value = true
  notFound.value = false
  try {
    const res = await getCommunityPost(postId.value)
    post.value = res.data ?? null
  } catch {
    notFound.value = true
    post.value = null
  } finally {
    loading.value = false
  }
}

async function loadComments() {
  if (!postId.value) return
  commentsLoading.value = true
  try {
    const res = await listCommunityComments(postId.value)
    comments.value = res.data ?? []
  } catch {
    comments.value = []
  } finally {
    commentsLoading.value = false
  }
}

/* ---------------- 展示计算 ---------------- */
const coverUrl = computed(() => (isValidImageUrl(post.value?.cover) ? post.value?.cover : ''))
const authorAvatar = computed(() =>
  isValidImageUrl(post.value?.authorAvatar) ? (post.value?.authorAvatar as string) : DEFAULT_USER_AVATAR,
)
const authorName = computed(() => post.value?.authorName || '匿名用户')
const canManage = computed(() => !!post.value?.mine || userStore.isAdmin())
const appVisitUrl = computed(() => {
  if (!post.value?.appId) return ''
  return getVisitUrl({
    id: post.value.appId as number,
    codeGenType: post.value.appCodeGenType ?? undefined,
    deployKey: post.value.appDeployKey ?? undefined,
  })
})

/** 评论总数（一级 + 二级） */
const commentTotal = computed(() =>
  comments.value.reduce((sum, c) => sum + 1 + (c.replies?.length ?? 0), 0),
)

/** 打开关联作品（已部署优先，否则打开本地预览） */
function openApp() {
  if (!appVisitUrl.value) return
  window.open(appVisitUrl.value, '_blank', 'noopener,noreferrer')
}

function commentAvatar(comment: CommunityCommentVO): string {
  return isValidImageUrl(comment.avatar) ? (comment.avatar as string) : DEFAULT_USER_AVATAR
}

/* ---------------- 点赞 ---------------- */
const liking = ref(false)

async function toggleLike() {
  if (!post.value) return
  if (!userStore.isLogin()) {
    message.warning('登录后才能点赞')
    goLogin()
    return
  }
  if (liking.value) return
  liking.value = true
  try {
    const res = post.value.liked
      ? await unlikeCommunityPost(post.value.id)
      : await likeCommunityPost(post.value.id)
    post.value.liked = res.data?.liked ?? false
    post.value.likeCount = res.data?.likeCount ?? post.value.likeCount
  } catch {
    // 拦截器已提示
  } finally {
    liking.value = false
  }
}

/* ---------------- 评论 ---------------- */
const commentText = ref('')
const replyTo = ref<CommunityCommentVO | null>(null)
const submittingComment = ref(false)

function startReply(comment: CommunityCommentVO) {
  if (!userStore.isLogin()) {
    message.warning('登录后才能回复')
    goLogin()
    return
  }
  replyTo.value = comment
}

function cancelReply() {
  replyTo.value = null
}

async function submitComment() {
  if (!post.value) return
  if (!userStore.isLogin()) {
    message.warning('登录后才能评论')
    goLogin()
    return
  }
  const content = commentText.value.trim()
  if (!content) {
    message.warning('请输入评论内容')
    return
  }
  submittingComment.value = true
  try {
    await addCommunityComment({
      postId: post.value.id,
      parentId: replyTo.value?.id,
      content,
    })
    message.success(replyTo.value ? '回复成功' : '评论成功')
    commentText.value = ''
    replyTo.value = null
    await loadComments()
    post.value.commentCount = commentTotal.value
  } catch {
    // 拦截器已提示
  } finally {
    submittingComment.value = false
  }
}

async function removeComment(comment: CommunityCommentVO) {
  try {
    await deleteCommunityComment(comment.id)
    message.success('评论已删除')
    await loadComments()
    if (post.value) post.value.commentCount = commentTotal.value
  } catch {
    // 拦截器已提示
  }
}

function canDeleteComment(comment: CommunityCommentVO): boolean {
  return !!comment.mine || userStore.isAdmin()
}

/* ---------------- 编辑 / 删除帖子 ---------------- */
const editOpen = ref(false)
const editing = ref(false)
const tagOptions = ref<CommunityTagVO[]>([])
const editForm = reactive({ title: '', content: '', cover: '', tags: [] as string[] })

async function openEdit() {
  if (!post.value) return
  editForm.title = post.value.title
  editForm.content = post.value.content
  editForm.cover = post.value.cover ?? ''
  editForm.tags = [...(post.value.tags ?? [])]
  editOpen.value = true
  if (tagOptions.value.length === 0) {
    try {
      const res = await listCommunityTags()
      tagOptions.value = res.data ?? []
    } catch {
      // 忽略
    }
  }
}

async function submitEdit() {
  if (!post.value) return
  if (!editForm.title.trim()) {
    message.warning('标题不能为空')
    return
  }
  if (!editForm.content.trim()) {
    message.warning('正文不能为空')
    return
  }
  editing.value = true
  try {
    const res = await updateCommunityPost({
      id: post.value.id,
      title: editForm.title.trim(),
      content: editForm.content,
      cover: editForm.cover,
      tags: editForm.tags,
    })
    post.value = res.data ?? post.value
    editOpen.value = false
    message.success('已保存')
  } catch {
    // 拦截器已提示
  } finally {
    editing.value = false
  }
}

async function removePost() {
  if (!post.value) return
  try {
    await deleteCommunityPost(post.value.id)
    message.success('帖子已删除')
    router.push('/community')
  } catch {
    // 拦截器已提示
  }
}

/* ---------------- 跳转 ---------------- */
function goLogin() {
  const redirect = encodeURIComponent(route.fullPath)
  router.push(`/user/login?redirect=${redirect}`)
}

function goCommunity() {
  router.push('/community')
}

onMounted(() => {
  loadPost()
  loadComments()
})

// 从详情页再点进另一篇帖子时组件复用，需要按 id 重新加载
watch(postId, () => {
  loadPost()
  loadComments()
  commentText.value = ''
  replyTo.value = null
})
</script>

<template>
  <div class="detail-page">
    <a-button type="text" class="back-btn" @click="goCommunity">
      <ArrowLeftOutlined />
      返回社区
    </a-button>

    <a-spin :spinning="loading">
      <!-- 帖子正文 -->
      <article v-if="post" class="post-detail">
        <header class="detail-head">
          <h1 class="detail-title">{{ post.title }}</h1>

          <div class="detail-meta">
            <a-avatar :src="authorAvatar" :size="34" />
            <div class="meta-user">
              <span class="meta-name">{{ authorName }}</span>
              <span class="meta-time">{{ formatTime(post.createTime) }} 发布</span>
            </div>
            <span v-if="post.status === 0" class="draft-chip">草稿</span>
            <div class="meta-right">
              <span class="metric"><EyeOutlined /> {{ post.viewCount ?? 0 }} 浏览</span>
              <span class="metric"><MessageOutlined /> {{ post.commentCount ?? 0 }} 评论</span>
            </div>
          </div>

          <div v-if="post.tags && post.tags.length" class="detail-tags">
            <span v-for="tag in post.tags" :key="tag" class="detail-tag"># {{ tag }}</span>
          </div>
        </header>

        <img v-if="coverUrl" :src="coverUrl" :alt="post.title" class="detail-cover" />

        <div class="detail-content">{{ post.content }}</div>

        <!-- 关联作品 -->
        <section v-if="post.appId" class="linked-app">
          <div class="linked-left">
            <div class="linked-cover">
              <img
                v-if="isValidImageUrl(post.appCover)"
                :src="post.appCover as string"
                :alt="post.appName || '关联作品'"
              />
              <span v-else>🚀</span>
            </div>
            <div class="linked-info">
              <span class="linked-label">作者关联的作品</span>
              <span class="linked-name">{{ post.appName || '未命名应用' }}</span>
            </div>
          </div>
          <a-button type="primary" class="visit-btn" @click="openApp">
            <LinkOutlined />
            打开作品
          </a-button>
        </section>

        <!-- 操作区 -->
        <footer class="detail-actions">
          <button class="like-big" :class="{ liked: post.liked }" @click="toggleLike">
            <LikeFilled v-if="post.liked" />
            <LikeOutlined v-else />
            <span>{{ post.liked ? '已点赞' : '点赞' }}</span>
            <em>{{ post.likeCount ?? 0 }}</em>
          </button>

          <div v-if="canManage" class="manage-actions">
            <a-button @click="openEdit">
              <EditOutlined />
              编辑
            </a-button>
            <a-popconfirm
              title="确定删除这篇帖子吗？删除后不可恢复"
              ok-text="删除"
              cancel-text="取消"
              @confirm="removePost"
            >
              <a-button danger>
                <DeleteOutlined />
                删除
              </a-button>
            </a-popconfirm>
          </div>
        </footer>
      </article>

      <a-empty v-else-if="notFound && !loading" class="empty-box">
        <template #image>
          <div class="empty-icon">🫥</div>
        </template>
        <template #description>帖子不存在或已被作者删除</template>
        <a-button type="primary" ghost @click="goCommunity">回到社区</a-button>
      </a-empty>
    </a-spin>

    <!-- 评论区 -->
    <section v-if="post" class="comment-section">
      <div class="comment-head">
        <h2 class="comment-title">
          <MessageOutlined />
          评论区
          <span class="comment-count">{{ commentTotal }}</span>
        </h2>
        <span class="comment-tip">支持两级回复，友善交流</span>
      </div>

      <!-- 发表框 -->
      <div class="comment-editor">
        <template v-if="userStore.isLogin()">
          <div v-if="replyTo" class="reply-hint">
            正在回复 <b>{{ replyTo.nickname }}</b>
            <a-button type="link" size="small" @click="cancelReply">取消</a-button>
          </div>
          <a-textarea
            v-model:value="commentText"
            :auto-size="{ minRows: 3, maxRows: 8 }"
            :maxlength="1000"
            show-count
            :placeholder="replyTo ? `回复 ${replyTo.nickname}…` : '说说你的看法，或分享你的作品链接…'"
          />
          <div class="editor-foot">
            <a-button type="primary" :loading="submittingComment" class="send-btn" @click="submitComment">
              {{ replyTo ? '发表回复' : '发表评论' }}
            </a-button>
          </div>
        </template>
        <template v-else>
          <div class="login-hint">
            <span>登录后即可参与讨论</span>
            <a-button type="primary" ghost class="login-btn" @click="goLogin">去登录</a-button>
          </div>
        </template>
      </div>

      <!-- 评论列表 -->
      <a-spin :spinning="commentsLoading">
        <div v-if="comments.length" class="comment-list">
          <div v-for="comment in comments" :key="comment.id" class="comment-item">
            <div class="comment-main">
              <a-avatar :src="commentAvatar(comment)" :size="34" />
              <div class="comment-body">
                <div class="comment-meta">
                  <span class="comment-name">{{ comment.nickname || '匿名用户' }}</span>
                  <span class="comment-time">{{ formatTime(comment.createTime) }}</span>
                </div>
                <p class="comment-text">{{ comment.content }}</p>
                <div class="comment-ops">
                  <a-button type="link" size="small" @click="startReply(comment)">回复</a-button>
                  <a-popconfirm
                    v-if="canDeleteComment(comment)"
                    title="删除这条评论？"
                    ok-text="删除"
                    cancel-text="取消"
                    @confirm="removeComment(comment)"
                  >
                    <a-button type="link" size="small" danger>删除</a-button>
                  </a-popconfirm>
                </div>
              </div>
            </div>

            <!-- 二级回复 -->
            <div v-if="comment.replies && comment.replies.length" class="reply-list">
              <div v-for="reply in comment.replies" :key="reply.id" class="reply-item">
                <a-avatar :src="commentAvatar(reply)" :size="28" />
                <div class="comment-body">
                  <div class="comment-meta">
                    <span class="comment-name">{{ reply.nickname || '匿名用户' }}</span>
                    <span v-if="reply.replyToName" class="reply-to">回复 {{ reply.replyToName }}</span>
                    <span class="comment-time">{{ formatTime(reply.createTime) }}</span>
                  </div>
                  <p class="comment-text">{{ reply.content }}</p>
                  <div class="comment-ops">
                    <a-button type="link" size="small" @click="startReply(comment)">回复</a-button>
                    <a-popconfirm
                      v-if="canDeleteComment(reply)"
                      title="删除这条回复？"
                      ok-text="删除"
                      cancel-text="取消"
                      @confirm="removeComment(reply)"
                    >
                      <a-button type="link" size="small" danger>删除</a-button>
                    </a-popconfirm>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <a-empty v-else description="还没有评论，来抢沙发吧" class="comment-empty" />
      </a-spin>
    </section>

    <!-- 编辑弹窗 -->
    <a-modal
      v-model:open="editOpen"
      title="编辑帖子"
      width="720px"
      :confirm-loading="editing"
      ok-text="保存"
      cancel-text="取消"
      @ok="submitEdit"
    >
      <div class="edit-form">
        <div class="form-item">
          <label class="form-label">标题</label>
          <a-input v-model:value="editForm.title" :maxlength="200" show-count />
        </div>
        <div class="form-item">
          <label class="form-label">正文</label>
          <a-textarea
            v-model:value="editForm.content"
            :auto-size="{ minRows: 10, maxRows: 18 }"
            :maxlength="20000"
            show-count
          />
        </div>
        <div class="form-item">
          <label class="form-label">封面图</label>
          <ImageCoverPicker v-model="editForm.cover" />
        </div>
        <div class="form-item">
          <label class="form-label">标签</label>
          <a-select
            v-model:value="editForm.tags"
            mode="tags"
            :max-tag-count="5"
            :token-separators="[',']"
            placeholder="最多 5 个标签"
            :options="tagOptions.map((t) => ({ label: t.name, value: t.name }))"
          />
        </div>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.detail-page {
  max-width: 900px;
  margin: 0 auto;
}

.back-btn {
  margin-bottom: 10px;
  color: #6b7280;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding-left: 0;
}

.back-btn:hover {
  color: #4f46e5;
}

/* ---------- 正文卡片 ---------- */
.post-detail {
  background: #fff;
  border: 1px solid #eef0f6;
  border-radius: 18px;
  padding: 26px 30px 22px;
  box-shadow: 0 8px 26px rgba(30, 41, 82, 0.06);
}

.detail-title {
  margin: 0 0 14px;
  font-size: 27px;
  font-weight: 800;
  color: #1f2430;
  line-height: 1.42;
}

.detail-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.meta-user {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}

.meta-name {
  font-size: 14px;
  color: #374151;
  font-weight: 600;
}

.meta-time {
  font-size: 12px;
  color: #98a0b3;
}

.draft-chip {
  font-size: 12px;
  color: #b45309;
  background: #fef3c7;
  border-radius: 999px;
  padding: 1px 10px;
}

.meta-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 14px;
  color: #98a0b3;
  font-size: 13px;
}

.metric {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.detail-tag {
  font-size: 12.5px;
  color: #6366f1;
  background: #f2f3fd;
  border-radius: 999px;
  padding: 3px 12px;
}

.detail-cover {
  width: 100%;
  max-height: 400px;
  object-fit: cover;
  border-radius: 14px;
  margin: 18px 0 6px;
}

.detail-content {
  margin-top: 18px;
  color: #333a4a;
  font-size: 15px;
  line-height: 1.95;
  white-space: pre-wrap;
  word-break: break-word;
}

/* ---------- 关联作品 ---------- */
.linked-app {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-top: 24px;
  padding: 14px 16px;
  background: #f8f9fe;
  border: 1px solid #e8ebf9;
  border-radius: 14px;
}

.linked-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.linked-cover {
  width: 64px;
  height: 48px;
  border-radius: 10px;
  overflow: hidden;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
}

.linked-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.linked-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.linked-label {
  font-size: 12px;
  color: #98a0b3;
}

.linked-name {
  font-size: 14.5px;
  color: #1f2430;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.visit-btn {
  flex-shrink: 0;
  border: none;
  border-radius: 999px;
  background: linear-gradient(90deg, #4f46e5, #7c3aed);
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.visit-btn:hover {
  background: linear-gradient(90deg, #4338ca, #6d28d9) !important;
}

/* ---------- 操作区 ---------- */
.detail-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 26px;
  padding-top: 18px;
  border-top: 1px solid #f2f4fa;
}

.like-big {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 42px;
  padding: 0 26px;
  border-radius: 999px;
  border: 1px solid #ffd6e0;
  background: #fff5f7;
  color: #e11d48;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.like-big:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 18px rgba(225, 29, 72, 0.16);
}

.like-big.liked {
  background: linear-gradient(90deg, #fb7185, #e11d48);
  border-color: transparent;
  color: #fff;
  box-shadow: 0 8px 18px rgba(225, 29, 72, 0.28);
}

.like-big em {
  font-style: normal;
  font-size: 13px;
  opacity: 0.85;
}

.manage-actions {
  display: flex;
  gap: 8px;
}

/* ---------- 评论区 ---------- */
.comment-section {
  margin-top: 20px;
  background: #fff;
  border: 1px solid #eef0f6;
  border-radius: 18px;
  padding: 22px 26px 26px;
}

.comment-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.comment-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #1f2430;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.comment-count {
  font-size: 13px;
  color: #6366f1;
  background: #eef0ff;
  border-radius: 999px;
  padding: 1px 10px;
}

.comment-tip {
  font-size: 12px;
  color: #a0a6b8;
}

.comment-editor {
  margin: 16px 0 22px;
  padding: 14px;
  background: #f9fafe;
  border: 1px solid #eef0f9;
  border-radius: 14px;
}

.reply-hint {
  margin-bottom: 8px;
  font-size: 13px;
  color: #6b7280;
}

.editor-foot {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}

.send-btn {
  border: none;
  border-radius: 999px;
  padding: 0 22px;
  background: linear-gradient(90deg, #4f46e5, #7c3aed);
}

.send-btn:hover {
  background: linear-gradient(90deg, #4338ca, #6d28d9) !important;
}

.login-hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #8a90a6;
  font-size: 14px;
}

.login-btn {
  border-radius: 999px;
}

.comment-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.comment-item {
  padding-bottom: 16px;
  border-bottom: 1px solid #f4f6fb;
}

.comment-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.comment-main,
.reply-item {
  display: flex;
  gap: 10px;
}

.comment-body {
  flex: 1;
  min-width: 0;
}

.comment-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.comment-name {
  font-size: 13.5px;
  font-weight: 600;
  color: #374151;
}

.reply-to {
  font-size: 12px;
  color: #6366f1;
}

.comment-time {
  font-size: 12px;
  color: #a0a6b8;
}

.comment-text {
  margin: 6px 0 4px;
  color: #333a4a;
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.comment-ops {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-left: -8px;
}

.reply-list {
  margin: 12px 0 0 44px;
  padding: 12px 14px;
  background: #f9fafe;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.comment-empty {
  padding: 24px 0 8px;
}

/* ---------- 空态 / 编辑弹窗 ---------- */
.empty-box {
  background: #fff;
  border: 1px dashed #dfe3f0;
  border-radius: 16px;
  padding: 56px 20px;
}

.empty-icon {
  font-size: 46px;
}

.edit-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-top: 6px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-label {
  font-size: 13px;
  font-weight: 600;
  color: #4b5563;
}

@media (max-width: 640px) {
  .post-detail {
    padding: 18px 16px;
  }

  .detail-title {
    font-size: 22px;
  }

  .meta-right {
    margin-left: 0;
    width: 100%;
  }
}
</style>
