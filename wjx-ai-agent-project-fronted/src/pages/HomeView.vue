<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { SendOutlined, AppstoreOutlined, StarFilled } from '@ant-design/icons-vue'
import { add1, listAppVoByPage, listGoodApp } from '@/api/appController'
import AppCard from '@/components/AppCard.vue'
import AppDetailModal from '@/components/AppDetailModal.vue'
import { useUserStore } from '@/stores/user'
import { getDefaultCover, openVisitUrl } from '@/utils/app'

const router = useRouter()
const userStore = useUserStore()

/* ---------------- 提示词输入区 ---------------- */
const promptText = ref('')
const codeGenType = ref<'html' | 'multi_file'>('html')
const generating = ref(false)

// 示例提示词
const examples = [
  '帮我生成一个个人博客网站，包含首页、文章列表和关于我',
  '制作一个番茄钟计时器网页，有开始、暂停和重置功能',
  '做一个仿微信的聊天界面，包含消息列表和输入框',
  '生成一个星空主题的个人主页，带打字机效果标题',
]

function fillExample(text: string) {
  promptText.value = text
}

async function handleGenerate() {
  const text = promptText.value.trim()
  if (!text) {
    message.warning('请输入你想创建的网站描述')
    return
  }
  if (!userStore.isLogin()) {
    message.warning('请先登录后再创建应用')
    const redirect = encodeURIComponent(router.currentRoute.value.fullPath)
    router.push(`/user/login?redirect=${redirect}`)
    return
  }

  generating.value = true
  try {
    // 1. 创建应用（后端未返回 id，需要 userId 关联到当前用户；cover 默认使用 picsum 随机图片）
    const appName = text.length > 20 ? `${text.slice(0, 20)}…` : text
    await add1({
      appName,
      initprompt: text,
      codeGenType: codeGenType.value,
      priority: 0,
      cover: getDefaultCover(),
      userId: userStore.currentUser?.id,
    })

    // 2. 通过列表接口找到刚创建的应用（按创建时间倒序取最新一条）
    const res = await listAppVoByPage({
      pageNum: 1,
      pageSize: 1,
      userId: userStore.currentUser?.id,
      initprompt: text,
      sortField: 'createTime',
      sortOrder: 'descend',
    })
    const app = res?.data?.records?.[0]
    if (!app?.id) {
      throw new Error('应用创建失败，请稍后重试')
    }

    // 3. 跳转到对话页并自动发送初始提示词
    router.push({
      path: `/app/generate/${app.id}`,
      query: { prompt: text, type: codeGenType.value },
    })
  } catch (e) {
    if (e instanceof Error && e.message !== '应用创建失败，请稍后重试') {
      message.error('创建应用失败，请稍后重试')
    }
  } finally {
    generating.value = false
  }
}

/* ---------------- 我的应用列表 ---------------- */
const myApps = ref<API.AppVo[]>([])
const myAppsLoading = ref(false)
const myAppsTotal = ref(0)
const myAppsPage = ref(1)
const myAppsPageSize = 8
const myAppsKeyword = ref('')

async function loadMyApps(page = myAppsPage.value) {
  if (!userStore.isLogin()) return
  myAppsLoading.value = true
  try {
    const res = await listAppVoByPage({
      pageNum: page,
      pageSize: myAppsPageSize,
      userId: userStore.currentUser?.id,
      appName: myAppsKeyword.value || undefined,
      sortField: 'createTime',
      sortOrder: 'descend',
    })
    myApps.value = res?.data?.records ?? []
    myAppsTotal.value = res?.data?.totalRow ?? 0
    myAppsPage.value = page
  } catch {
    // 请求失败时静默处理（未登录等场景由拦截器提示）
  } finally {
    myAppsLoading.value = false
  }
}

function onMyAppsSearch() {
  myAppsPage.value = 1
  loadMyApps(1)
}

/* ---------------- 精选应用列表 ---------------- */
const goodApps = ref<Array<API.AppUserVo | API.AppVo>>([])
const goodAppsLoading = ref(false)
const goodAppsTotal = ref(0)
const goodAppsPage = ref(1)
const goodAppsPageSize = 8
const goodAppsKeyword = ref('')

async function loadGoodApps(page = goodAppsPage.value) {
  goodAppsLoading.value = true
  try {
    const params: API.AppQueryRequest = {
      pageNum: page,
      pageSize: goodAppsPageSize,
      appName: goodAppsKeyword.value || undefined,
      sortField: 'priority',
      sortOrder: 'descend',
    }
    // 管理员直接调用精选接口（带创建者信息）；普通用户无权限时降级为按优先级排序的通用查询
    try {
      const res = await listGoodApp(params)
      goodApps.value = res?.data?.records ?? []
      goodAppsTotal.value = res?.data?.totalRow ?? 0
    } catch {
      const res = await listAppVoByPage(params)
      goodApps.value = res?.data?.records ?? []
      goodAppsTotal.value = res?.data?.totalRow ?? 0
    }
    goodAppsPage.value = page
  } finally {
    goodAppsLoading.value = false
  }
}

function onGoodAppsSearch() {
  goodAppsPage.value = 1
  loadGoodApps(1)
}

/* ---------------- 详情弹窗 ---------------- */
const detailOpen = ref(false)
const detailApp = ref<API.AppUserVo | API.AppVo | null>(null)

function showDetail(app: API.AppUserVo | API.AppVo) {
  detailApp.value = app
  detailOpen.value = true
}

function handleVisit(app: API.AppUserVo | API.AppVo) {
  openVisitUrl(app)
}

onMounted(() => {
  loadGoodApps(1)
})

// 登录态加载完成后，自动加载"我的应用"
watch(
  () => userStore.loaded,
  (loaded) => {
    if (loaded && userStore.isLogin()) {
      loadMyApps(1)
    }
  },
  { immediate: true },
)
</script>

<template>
  <div class="home-page">
    <!-- 网站标题 -->
    <section class="hero">
      <h1 class="hero-title">
        <span class="grad-text">AI 应用生成器</span>
      </h1>
      <p class="hero-subtitle">输入你的想法，AI 帮你写出代码，一键生成可运行的网站应用</p>
    </section>

    <!-- 提示词输入区 -->
    <section class="prompt-section">
      <div class="prompt-box">
        <a-textarea
          v-model:value="promptText"
          class="prompt-input"
          :auto-size="{ minRows: 3, maxRows: 6 }"
          placeholder="描述你想创建的网站，例如：帮我生成一个个人博客网站，包含首页、文章列表和关于我..."
          :maxlength="500"
          show-count
          @keydown.enter.exact.prevent="handleGenerate"
        />

        <div class="prompt-footer">
          <div class="prompt-options">
            <a-segmented
              v-model:value="codeGenType"
              :options="[
                { label: '单文件模式', value: 'html' },
                { label: '多文件模式', value: 'multi_file' },
              ]"
              size="small"
            />
          </div>

          <a-button
            type="primary"
            class="generate-btn"
            :loading="generating"
            @click="handleGenerate"
          >
            <SendOutlined v-if="!generating" />
            生成应用
          </a-button>
        </div>
      </div>

      <!-- 示例提示词 -->
      <div class="examples">
        <span class="examples-label">灵感：</span>
        <a-tag
          v-for="(item, index) in examples"
          :key="index"
          class="example-tag"
          @click="fillExample(item)"
        >
          {{ item }}
        </a-tag>
      </div>
    </section>

    <!-- 我的应用 -->
    <section class="app-section">
      <div class="section-header">
        <h2 class="section-title">
          <AppstoreOutlined />
          我的应用
        </h2>
        <div class="section-tools">
          <a-input-search
            v-if="userStore.isLogin()"
            v-model:value="myAppsKeyword"
            placeholder="按名称搜索"
            allow-clear
            class="search-input"
            @search="onMyAppsSearch"
          />
          <a-button
            v-if="!userStore.isLogin()"
            type="primary"
            ghost
            size="small"
            @click="router.push('/user/login?redirect=' + encodeURIComponent('/'))"
          >
            登录后查看
          </a-button>
        </div>
      </div>

      <a-spin :spinning="myAppsLoading">
        <div v-if="userStore.isLogin()">
          <div v-if="myApps.length > 0" class="app-grid">
            <AppCard
              v-for="app in myApps"
              :key="app.id"
              :app="app"
              @visit="handleVisit"
              @detail="showDetail"
            />
          </div>
          <a-empty v-else description="还没有应用，输入提示词创建一个吧" class="empty-box">
            <template #image>
              <div class="empty-icon">🚀</div>
            </template>
          </a-empty>

          <a-pagination
            v-if="myAppsTotal > myAppsPageSize"
            v-model:current="myAppsPage"
            :total="myAppsTotal"
            :page-size="myAppsPageSize"
            class="section-pagination"
            @change="loadMyApps"
          />
        </div>
        <a-empty v-else description="登录后查看你的应用" class="empty-box" />
      </a-spin>
    </section>

    <!-- 精选应用 -->
    <section class="app-section">
      <div class="section-header">
        <h2 class="section-title featured-title">
          <StarFilled class="star-icon" />
          精选应用
        </h2>
        <a-input-search
          v-model:value="goodAppsKeyword"
          placeholder="按名称搜索"
          allow-clear
          class="search-input"
          @search="onGoodAppsSearch"
        />
      </div>

      <a-spin :spinning="goodAppsLoading">
        <div v-if="goodApps.length > 0" class="app-grid">
          <AppCard
            v-for="app in goodApps"
            :key="app.id"
            :app="app"
            @visit="handleVisit"
            @detail="showDetail"
          />
        </div>
        <a-empty v-else description="暂无精选应用" class="empty-box">
          <template #image>
            <div class="empty-icon">⭐</div>
          </template>
        </a-empty>

        <a-pagination
          v-if="goodAppsTotal > goodAppsPageSize"
          v-model:current="goodAppsPage"
          :total="goodAppsTotal"
          :page-size="goodAppsPageSize"
          class="section-pagination"
          @change="loadGoodApps"
        />
      </a-spin>
    </section>

    <!-- 详情弹窗 -->
    <AppDetailModal v-model:open="detailOpen" :app="detailApp" />
  </div>
</template>

<style scoped>
.home-page {
  max-width: 1200px;
  margin: 0 auto;
}

/* ---------- 标题区 ---------- */
.hero {
  text-align: center;
  padding: 30px 0 8px;
}

.hero-title {
  margin: 0;
  font-size: 40px;
  font-weight: 800;
  letter-spacing: 1px;
}

.grad-text {
  background: linear-gradient(90deg, #4f46e5, #7c3aed, #d946ef, #4f46e5);
  background-size: 250% auto;
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  animation: title-flow 6s linear infinite;
}

@keyframes title-flow {
  to {
    background-position: 250% center;
  }
}

.hero-subtitle {
  margin: 12px 0 0;
  color: #8a90a6;
  font-size: 15px;
}

/* ---------- 提示词区 ---------- */
.prompt-section {
  margin: 28px auto 10px;
  max-width: 860px;
}

.prompt-box {
  background: #fff;
  border-radius: 16px;
  padding: 16px 16px 12px;
  border: 1px solid #e8ebf5;
  box-shadow: 0 8px 28px rgba(30, 41, 82, 0.08);
  transition: box-shadow 0.25s, border-color 0.25s;
}

.prompt-box:focus-within {
  border-color: #818cf8;
  box-shadow: 0 10px 32px rgba(79, 70, 229, 0.16);
}

.prompt-input {
  border: none;
  outline: none;
  box-shadow: none !important;
  font-size: 15px;
  padding: 4px 6px;
  resize: none;
}

.prompt-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
}

.generate-btn {
  border-radius: 999px;
  padding: 0 26px;
  height: 38px;
  background: linear-gradient(90deg, #4f46e5, #7c3aed);
  border: none;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.generate-btn:hover {
  background: linear-gradient(90deg, #4338ca, #6d28d9) !important;
}

.examples {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 14px;
}

.examples-label {
  color: #98a0b3;
  font-size: 13px;
}

.example-tag {
  cursor: pointer;
  border-radius: 999px;
  padding: 2px 12px;
  color: #6b7280;
  background: #f1f3fa;
  border: 1px solid #e5e9f5;
  transition: all 0.2s;
}

.example-tag:hover {
  color: #4f46e5;
  border-color: #818cf8;
  background: #eef0ff;
}

/* ---------- 应用列表 ---------- */
.app-section {
  margin-top: 40px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.section-title {
  margin: 0;
  font-size: 19px;
  font-weight: 700;
  color: #1f2430;
  display: flex;
  align-items: center;
  gap: 8px;
}

.featured-title {
  color: #b45309;
}

.star-icon {
  color: #f59e0b;
}

.section-tools {
  display: flex;
  align-items: center;
  gap: 10px;
}

.search-input {
  width: 220px;
}

.app-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 18px;
}

.empty-box {
  padding: 40px 0;
  background: #fff;
  border-radius: 14px;
  border: 1px dashed #dfe3f0;
}

.empty-icon {
  font-size: 34px;
}

.section-pagination {
  margin-top: 20px;
  text-align: right;
}

@media (max-width: 1100px) {
  .app-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 800px) {
  .app-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .hero-title {
    font-size: 30px;
  }
  .prompt-footer {
    flex-direction: column;
    align-items: stretch;
  }
  .generate-btn {
    justify-content: center;
  }
}

@media (max-width: 520px) {
  .app-grid {
    grid-template-columns: 1fr;
  }
}
</style>
