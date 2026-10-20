<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  EyeOutlined,
  LikeFilled,
  LikeOutlined,
  MessageOutlined,
  PlusOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
import {
  addCommunityPost,
  deleteCommunityPost,
  getCommunityStats,
  likeCommunityPost,
  listCommunityPosts,
  listCommunityTags,
  listMyCommunityPosts,
  unlikeCommunityPost,
} from '@/api/communityController'
import { listAppVoByPage } from '@/api/appController'
import ImageCoverPicker from '@/components/ImageCoverPicker.vue'
import { useUserStore } from '@/stores/user'
import { DEFAULT_USER_AVATAR, formatTime, isValidImageUrl } from '@/utils/app'
import type {
  CommunityPostListVO,
  CommunityPostQuery,
  CommunityStatsVO,
  CommunityTagVO,
} from '@/types/community'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

/* ---------------- 列表状态 ---------------- */
const posts = ref<CommunityPostListVO[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const PAGE_SIZE = 9

/** 排序方式：最新 / 最热（点赞）/ 最多浏览 */
type SortKey = 'latest' | 'hot' | 'views'
const sortKey = ref<SortKey>('latest')
const sortOptions = [
  { label: '最新发布', value: 'latest' },
  { label: '最多点赞', value: 'hot' },
  { label: '最多浏览', value: 'views' },
]

const activeTag = ref('')
const keyword = ref('')
const onlyMine = ref(false)

const tags = ref<CommunityTagVO[]>([])
const stats = ref<CommunityStatsVO | null>(null)

/** 正在提交点赞的帖子 ID（防连点） */
const likingIds = ref<Set<string>>(new Set())

const isEmpty = computed(() => !loading.value && posts.value.length === 0)

/** 排序 key → 后端 sortField / sortOrder */
function resolveSort(key: SortKey): { sortField: string; sortOrder: string } {
  if (key === 'hot') return { sortField: 'likeCount', sortOrder: 'descend' }
  if (key === 'views') return { sortField: 'viewCount', sortOrder: 'descend' }
  return { sortField: 'createTime', sortOrder: 'descend' }
}

async function loadPosts(targetPage = page.value) {
  loading.value = true
  try {
    const { sortField, sortOrder } = resolveSort(sortKey.value)
    const body: CommunityPostQuery = {
      pageNum: targetPage,
      pageSize: PAGE_SIZE,
      sortField,
      sortOrder,
    }
    if (activeTag.value) body.tag = activeTag.value
    if (keyword.value.trim()) body.keyword = keyword.value.trim()
    // 只看我的：走 /mine 接口，能同时看到草稿
    const res = onlyMine.value ? await listMyCommunityPosts(body) : await listCommunityPosts(body)
    posts.value = res.data?.records ?? []
    total.value = Number(res.data?.totalRow ?? 0)
    page.value = targetPage
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    loading.value = false
  }
}

async function loadTags() {
  try {
    const res = await listCommunityTags()
    tags.value = res.data ?? []
  } catch {
    // 忽略：标签加载失败不影响主流程
  }
}

async function loadStats() {
  try {
    const res = await getCommunityStats()
    stats.value = res.data ?? null
  } catch {
    // 忽略
  }
}

function selectTag(tag: string) {
  activeTag.value = activeTag.value === tag ? '' : tag
  loadPosts(1)
}

function onSearch() {
  loadPosts(1)
}

function onSortChange(value: string | number) {
  sortKey.value = value as SortKey
  loadPosts(1)
}

function toggleOnlyMine() {
  if (!userStore.isLogin()) {
    goLogin()
    return
  }
  onlyMine.value = !onlyMine.value
  loadPosts(1)
}

/* ---------------- 点赞 ---------------- */
async function toggleLike(post: CommunityPostListVO) {
  if (!userStore.isLogin()) {
    message.warning('登录后才能点赞')
    goLogin()
    return
  }
  const key = String(post.id)
  if (likingIds.value.has(key)) return
  likingIds.value.add(key)
  try {
    const res = post.liked ? await unlikeCommunityPost(post.id) : await likeCommunityPost(post.id)
    post.liked = res.data?.liked ?? false
    post.likeCount = res.data?.likeCount ?? post.likeCount
  } catch {
    // 拦截器已提示
  } finally {
    likingIds.value.delete(key)
  }
}

/* ---------------- 发布 ---------------- */
const publishOpen = ref(false)
const submitting = ref(false)
const myApps = ref<Array<API.AppVo | API.AppUserVo>>([])
const myAppsLoading = ref(false)

const publishForm = reactive({
  title: '',
  content: '',
  cover: '',
  tags: [] as string[],
  appId: undefined as string | undefined,
})

/** 已有标签作为下拉候选（同时允许用户输入新标签，mode=tags） */
const tagOptions = computed(() =>
  tags.value.map((t) => ({ label: `${t.name}（${t.postCount}）`, value: t.name })),
)

const appOptions = computed(() =>
  myApps.value.map((app) => ({
    label: app.appName || '未命名应用',
    value: String(app.id),
  })),
)

async function openPublish() {
  if (!userStore.isLogin()) {
    message.warning('登录后才能发布帖子')
    goLogin()
    return
  }
  publishOpen.value = true
  if (myApps.value.length === 0) loadMyApps()
}

async function loadMyApps() {
  myAppsLoading.value = true
  try {
    const res = await listAppVoByPage({
      pageNum: 1,
      pageSize: 50,
      userId: userStore.currentUser?.id,
      sortField: 'createTime',
      sortOrder: 'descend',
    })
    myApps.value = res.data?.records ?? []
  } catch {
    // 忽略：关联作品是可选项
  } finally {
    myAppsLoading.value = false
  }
}

function resetPublishForm() {
  publishForm.title = ''
  publishForm.content = ''
  publishForm.cover = ''
  publishForm.tags = []
  publishForm.appId = undefined
}

async function submitPublish() {
  const title = publishForm.title.trim()
  const content = publishForm.content.trim()
  if (!title) {
    message.warning('请输入帖子标题')
    return
  }
  if (!content) {
    message.warning('请输入帖子正文')
    return
  }
  if (publishForm.tags.length > 5) {
    message.warning('最多选择 5 个标签')
    return
  }
  submitting.value = true
  try {
    await addCommunityPost({
      title,
      content,
      tags: publishForm.tags,
      cover: publishForm.cover.trim() || undefined,
      appId: publishForm.appId || undefined,
      status: 1,
    })
    message.success('发布成功')
    publishOpen.value = false
    resetPublishForm()
    onlyMine.value = false
    activeTag.value = ''
    await Promise.all([loadPosts(1), loadTags(), loadStats()])
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

/* ---------------- 删除 ---------------- */
async function removePost(post: CommunityPostListVO) {
  try {
    await deleteCommunityPost(post.id)
    message.success('已删除')
    await Promise.all([loadPosts(page.value), loadTags(), loadStats()])
  } catch {
    // 拦截器已提示
  }
}

/* ---------------- 跳转 / 展示 ---------------- */
function goDetail(post: CommunityPostListVO) {
  router.push(`/community/${post.id}`)
}

function goLogin() {
  const redirect = encodeURIComponent(router.currentRoute.value.fullPath)
  router.push(`/user/login?redirect=${redirect}`)
}

function coverOf(post: CommunityPostListVO): string {
  return isValidImageUrl(post.cover) ? (post.cover as string) : ''
}

function avatarOf(post: CommunityPostListVO): string {
  return isValidImageUrl(post.authorAvatar) ? (post.authorAvatar as string) : DEFAULT_USER_AVATAR
}

function authorOf(post: CommunityPostListVO): string {
  return post.authorName || '匿名用户'
}

/** 帖子作者本人或管理员可删除 */
function canDelete(post: CommunityPostListVO): boolean {
  return !!post.mine || userStore.isAdmin()
}

onMounted(() => {
  loadPosts(1)
  loadTags()
  loadStats()
  // 从素材库点「用作封面」跳过来时，带上封面并直接打开发布弹窗
  const coverFromQuery = route.query.cover
  if (typeof coverFromQuery === 'string' && coverFromQuery) {
    openPublish()
    publishForm.cover = coverFromQuery
  }
})

// 登录态变化后，若正在看「我的帖子」则刷新（避免退出登录后残留草稿）
watch(
  () => userStore.isLogin(),
  (login) => {
    if (!login) onlyMine.value = false
  },
)
</script>

<template>
  <div class="community-page">
    <!-- 顶部标题 + 统计 + 发布入口 -->
    <section class="community-hero">
      <div class="hero-left">
        <h1 class="hero-title">社区广场</h1>
        <p class="hero-subtitle">
          分享你用 AI 生成的作品，交流提示词技巧，和其他创作者一起把想法变成网站
        </p>
        <div class="hero-stats">
          <span class="stat-item"><b>{{ stats?.postTotal ?? 0 }}</b> 篇帖子</span>
          <span class="stat-divider" />
          <span class="stat-item"><b>{{ stats?.todayTotal ?? 0 }}</b> 今日新增</span>
          <span class="stat-divider" />
          <span class="stat-item"><b>{{ stats?.commentTotal ?? 0 }}</b> 条评论</span>
          <span class="stat-divider" />
          <span class="stat-item"><b>{{ stats?.userTotal ?? 0 }}</b> 位创作者</span>
        </div>
      </div>
      <a-button type="primary" class="publish-btn" size="large" @click="openPublish">
        <PlusOutlined />
        发布帖子
      </a-button>
    </section>

    <!-- 筛选工具栏 -->
    <section class="community-toolbar">
      <div class="toolbar-row">
        <a-segmented v-model:value="sortKey" :options="sortOptions" @change="onSortChange" />
        <div class="toolbar-right">
          <a-button
            :type="onlyMine ? 'primary' : 'default'"
            class="mine-btn"
            :class="{ active: onlyMine }"
            @click="toggleOnlyMine"
          >
            只看我的
          </a-button>
          <a-input
            v-model:value="keyword"
            class="search-input"
            placeholder="搜索标题或正文"
            allow-clear
            @press-enter="onSearch"
          >
            <template #prefix>
              <SearchOutlined />
            </template>
          </a-input>
          <a-button @click="onSearch">搜索</a-button>
        </div>
      </div>

      <div class="tag-row">
        <span class="tag-label">话题</span>
        <button
          class="tag-chip"
          :class="{ active: activeTag === '' }"
          @click="selectTag('')"
        >
          全部
        </button>
        <button
          v-for="tag in tags"
          :key="tag.id"
          class="tag-chip"
          :class="{ active: activeTag === tag.name }"
          @click="selectTag(tag.name)"
        >
          {{ tag.name }}
          <em class="tag-count">{{ tag.postCount }}</em>
        </button>
      </div>
    </section>

    <!-- 帖子列表 -->
    <a-spin :spinning="loading">
      <div v-if="posts.length > 0" class="post-grid">
        <article
          v-for="post in posts"
          :key="post.id"
          class="post-card"
          @click="goDetail(post)"
        >
          <div class="post-cover">
            <img v-if="coverOf(post)" :src="coverOf(post)" :alt="post.title" class="cover-img" />
            <div v-else class="cover-fallback">💬</div>

            <span v-if="post.appName" class="app-badge" :title="post.appName">
              🔗 {{ post.appName }}
            </span>
            <span v-if="post.status === 0" class="draft-badge">草稿</span>

            <button
              class="like-btn"
              :class="{ liked: post.liked }"
              :title="post.liked ? '取消点赞' : '点赞'"
              @click.stop="toggleLike(post)"
            >
              <LikeFilled v-if="post.liked" />
              <LikeOutlined v-else />
              <span>{{ post.likeCount ?? 0 }}</span>
            </button>
          </div>

          <div class="post-body">
            <h3 class="post-title" :title="post.title">{{ post.title }}</h3>
            <p class="post-excerpt">{{ post.excerpt || '作者还没有写下摘要…' }}</p>

            <div v-if="post.tags && post.tags.length" class="post-tags">
              <span
                v-for="tag in post.tags"
                :key="tag"
                class="mini-tag"
                @click.stop="selectTag(tag)"
              >
                # {{ tag }}
              </span>
            </div>

            <div class="post-foot">
              <div class="author">
                <a-avatar :src="avatarOf(post)" :size="24" />
                <span class="author-name">{{ authorOf(post) }}</span>
                <span class="dot">·</span>
                <span class="time">{{ formatTime(post.createTime) }}</span>
              </div>
              <div class="foot-right">
                <span class="metric"><EyeOutlined /> {{ post.viewCount ?? 0 }}</span>
                <span class="metric"><MessageOutlined /> {{ post.commentCount ?? 0 }}</span>
                <a-popconfirm
                  v-if="canDelete(post)"
                  title="确定删除这篇帖子吗？"
                  ok-text="删除"
                  cancel-text="取消"
                  @confirm="removePost(post)"
                >
                  <a-button type="text" size="small" class="del-btn" @click.stop>删除</a-button>
                </a-popconfirm>
              </div>
            </div>
          </div>
        </article>
      </div>

      <a-empty v-else-if="isEmpty" class="empty-box">
        <template #image>
          <div class="empty-icon">🌱</div>
        </template>
        <template #description>
          <span v-if="onlyMine">你还没有发过帖子，写下第一篇吧</span>
          <span v-else-if="activeTag || keyword">没有找到相关帖子，换个关键词试试</span>
          <span v-else>社区还很安静，来发第一篇文章吧</span>
        </template>
        <a-button type="primary" ghost @click="openPublish">发布帖子</a-button>
      </a-empty>
    </a-spin>

    <a-pagination
      v-if="total > PAGE_SIZE"
      v-model:current="page"
      :total="total"
      :page-size="PAGE_SIZE"
      class="community-pagination"
      show-less-items
      @change="loadPosts"
    />

    <!-- 发布弹窗 -->
    <a-modal
      v-model:open="publishOpen"
      title="发布帖子"
      width="760px"
      :confirm-loading="submitting"
      ok-text="发布"
      cancel-text="取消"
      @ok="submitPublish"
    >
      <div class="publish-form">
        <div class="form-item">
          <label class="form-label">标题 <i>*</i></label>
          <a-input
            v-model:value="publishForm.title"
            :maxlength="200"
            show-count
            placeholder="给帖子起个标题，例如：我用 AI 做了一个番茄钟"
          />
        </div>

        <div class="form-item">
          <label class="form-label">正文 <i>*</i></label>
          <a-textarea
            v-model:value="publishForm.content"
            :auto-size="{ minRows: 8, maxRows: 16 }"
            :maxlength="20000"
            show-count
            placeholder="分享你的作品链接、提示词、踩坑经验…支持换行，详情页会保留排版"
          />
        </div>

        <div class="form-row">
          <div class="form-item">
            <label class="form-label">话题标签</label>
            <a-select
              v-model:value="publishForm.tags"
              mode="tags"
              :options="tagOptions"
              :max-tag-count="5"
              placeholder="选择或输入标签，最多 5 个"
              :token-separators="[',']"
            />
          </div>
          <div class="form-item">
            <label class="form-label">关联作品</label>
            <a-select
              v-model:value="publishForm.appId"
              :options="appOptions"
              :loading="myAppsLoading"
              allow-clear
              show-search
              option-filter-prop="label"
              placeholder="可选：关联自己生成的 AI 应用"
            />
          </div>
        </div>

        <div class="form-item">
          <label class="form-label">封面图（可选）</label>
          <ImageCoverPicker v-model="publishForm.cover" />
          <p class="form-hint">上传的图片会自动存入素材库；留空则自动生成随机封面</p>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.community-page {
  max-width: 1200px;
  margin: 0 auto;
}

/* ---------- 顶部 ---------- */
.community-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding: 18px 26px 22px;
  background: #fff;
  border: 1px solid #eef0f6;
  border-radius: 18px;
  box-shadow: 0 6px 22px rgba(30, 41, 82, 0.06);
}

.hero-title {
  margin: 0;
  font-size: 30px;
  font-weight: 800;
  color: #1f2430;
  letter-spacing: 0.5px;
}

.hero-subtitle {
  margin: 8px 0 14px;
  color: #8a90a6;
  font-size: 14px;
  line-height: 1.7;
  max-width: 620px;
}

.hero-stats {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  color: #6b7280;
  font-size: 13px;
}

.stat-item b {
  color: #4f46e5;
  font-size: 16px;
  margin-right: 3px;
}

.stat-divider {
  width: 1px;
  height: 12px;
  background: #e5e7f0;
}

.publish-btn {
  flex-shrink: 0;
  height: 42px;
  padding: 0 24px;
  border: none;
  border-radius: 999px;
  background: linear-gradient(90deg, #4f46e5, #7c3aed);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  box-shadow: 0 8px 20px rgba(99, 102, 241, 0.32);
}

.publish-btn:hover {
  background: linear-gradient(90deg, #4338ca, #6d28d9) !important;
}

/* ---------- 工具栏 ---------- */
.community-toolbar {
  margin: 18px 0 20px;
  padding: 14px 18px;
  background: #fff;
  border: 1px solid #eef0f6;
  border-radius: 14px;
}

.toolbar-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mine-btn {
  border-radius: 999px;
}

.mine-btn.active {
  border: none;
  background: linear-gradient(90deg, #4f46e5, #7c3aed);
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}

.search-input {
  width: 240px;
  border-radius: 999px;
}

.tag-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #eef0f6;
}

.tag-label {
  color: #98a0b3;
  font-size: 13px;
  margin-right: 2px;
}

.tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 12px;
  border-radius: 999px;
  border: 1px solid #e5e9f5;
  background: #f7f8fc;
  color: #6b7280;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.18s;
}

.tag-chip:hover {
  color: #4f46e5;
  border-color: #c7ccf8;
  background: #eef0ff;
}

.tag-chip.active {
  color: #fff;
  border-color: transparent;
  background: linear-gradient(90deg, #4f46e5, #7c3aed);
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}

.tag-count {
  font-style: normal;
  font-size: 11px;
  opacity: 0.7;
}

/* ---------- 卡片网格 ---------- */
.post-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 18px;
}

.post-card {
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid #eef0f6;
  border-radius: 16px;
  overflow: hidden;
  cursor: pointer;
  transition:
    transform 0.22s ease,
    box-shadow 0.22s ease,
    border-color 0.22s ease;
}

.post-card:hover {
  transform: translateY(-4px);
  border-color: #d8ddf0;
  box-shadow: 0 14px 32px rgba(30, 41, 82, 0.13);
}

.post-cover {
  position: relative;
  height: 168px;
  background: linear-gradient(135deg, #eef1fb, #e4e9f8);
  overflow: hidden;
}

.cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.35s ease;
}

.post-card:hover .cover-img {
  transform: scale(1.04);
}

.cover-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: 40px;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 55%, #d946ef 100%);
}

.app-badge {
  position: absolute;
  left: 10px;
  bottom: 10px;
  max-width: 62%;
  padding: 3px 10px;
  border-radius: 999px;
  background: rgba(15, 18, 40, 0.62);
  backdrop-filter: blur(4px);
  color: #fff;
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.draft-badge {
  position: absolute;
  left: 10px;
  top: 10px;
  padding: 2px 10px;
  border-radius: 999px;
  background: rgba(245, 158, 11, 0.92);
  color: #78350f;
  font-size: 12px;
  font-weight: 600;
}

.like-btn {
  position: absolute;
  right: 10px;
  top: 10px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 12px;
  border: none;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.9);
  color: #6b7280;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.18s;
}

.like-btn:hover {
  color: #e11d48;
  background: #fff;
  transform: translateY(-1px);
}

.like-btn.liked {
  color: #e11d48;
  background: #ffe4ea;
}

.post-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px 12px;
  flex: 1;
}

.post-title {
  margin: 0;
  font-size: 16.5px;
  font-weight: 700;
  color: #1f2430;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.post-excerpt {
  margin: 0;
  color: #8a90a6;
  font-size: 13px;
  line-height: 1.7;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.post-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.mini-tag {
  font-size: 12px;
  color: #6366f1;
  background: #f2f3fd;
  border-radius: 6px;
  padding: 1px 8px;
  transition: background 0.18s;
}

.mini-tag:hover {
  background: #e6e8fb;
}

.post-foot {
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px solid #f3f4fa;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.author {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  color: #8a90a6;
  font-size: 12px;
}

.author-name {
  color: #4b5563;
  max-width: 96px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dot {
  color: #c9cede;
}

.time {
  white-space: nowrap;
}

.foot-right {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #98a0b3;
  font-size: 12px;
  flex-shrink: 0;
}

.metric {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.del-btn {
  color: #b0b6c8;
  padding: 0 4px;
}

.del-btn:hover {
  color: #e11d48 !important;
  background: #fff1f3 !important;
}

/* ---------- 空态 / 分页 ---------- */
.empty-box {
  background: #fff;
  border: 1px dashed #dfe3f0;
  border-radius: 16px;
  padding: 48px 20px;
}

.empty-icon {
  font-size: 46px;
}

.community-pagination {
  margin-top: 24px;
  text-align: center;
}

/* ---------- 发布弹窗 ---------- */
.publish-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-top: 6px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-label {
  font-size: 13px;
  color: #4b5563;
  font-weight: 600;
}

.form-label i {
  color: #e11d48;
  font-style: normal;
}

.form-hint {
  margin: 0;
  color: #a0a6b8;
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 720px) {
  .community-hero {
    flex-direction: column;
    align-items: flex-start;
  }

  .form-row {
    grid-template-columns: 1fr;
  }

  .search-input {
    width: 100%;
  }
}
</style>
