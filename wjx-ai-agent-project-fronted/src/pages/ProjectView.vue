<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { PlusOutlined } from '@ant-design/icons-vue'
import { listAppVoByPage, listAppVoByPageByAdmin } from '@/api/appController'
import AppCard from '@/components/AppCard.vue'
import AppDetailModal from '@/components/AppDetailModal.vue'
import { useUserStore } from '@/stores/user'
import { openVisitUrl } from '@/utils/app'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const dataList = ref<Array<API.AppUserVo | API.AppVo>>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20 // 每页最多 20 个
const keyword = ref('')

/** 管理员查看全部应用，普通用户仅自己的应用 */
const isAdmin = computed(() => userStore.isAdmin())

async function loadData(targetPage = page.value) {
  if (!userStore.isLogin()) return
  loading.value = true
  try {
    const params: API.AppQueryRequest = {
      pageNum: targetPage,
      pageSize,
      sortField: 'createTime',
      sortOrder: 'descend',
    }
    if (keyword.value.trim()) {
      params.appName = keyword.value.trim()
    }
    if (isAdmin.value) {
      const res = await listAppVoByPageByAdmin(params)
      dataList.value = res?.data?.records ?? []
      total.value = res?.data?.totalRow ?? 0
    } else {
      // 普通用户：带 userId 过滤，保证只能看到自己的应用
      params.userId = userStore.currentUser?.id
      const res = await listAppVoByPage(params)
      dataList.value = res?.data?.records ?? []
      total.value = res?.data?.totalRow ?? 0
    }
    page.value = targetPage
  } catch {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}

function onSearch() {
  loadData(1)
}

function onCreate() {
  router.push('/')
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

function goManage() {
  router.push('/app/manage')
}

watch(
  () => userStore.loaded,
  (loaded) => {
    if (loaded && userStore.isLogin()) {
      loadData(1)
    }
  },
  { immediate: true },
)
</script>

<template>
  <div class="project-page">
    <!-- 头部 -->
    <div class="page-header">
      <div>
        <h2 class="page-title">我的项目</h2>
        <p class="page-desc">
          {{ isAdmin ? '管理全站所有应用（管理员视图）' : '查看和管理你创建的应用' }}
        </p>
      </div>
      <div class="header-actions">
        <a-button v-if="isAdmin" class="manage-btn" @click="goManage">
          应用管理
        </a-button>
        <a-button type="primary" class="create-btn" @click="onCreate">
          <PlusOutlined />
          创建应用
        </a-button>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="toolbar">
      <a-input-search
        v-model:value="keyword"
        placeholder="按应用名称搜索"
        allow-clear
        class="keyword-input"
        @search="onSearch"
      />
      <span class="total-text">共 {{ total }} 个应用</span>
    </div>

    <!-- 列表 -->
    <a-spin :spinning="loading">
      <template v-if="userStore.isLogin()">
        <div v-if="dataList.length > 0" class="app-grid">
          <AppCard
            v-for="app in dataList"
            :key="app.id"
            :app="app"
            @visit="handleVisit"
            @detail="showDetail"
          />
        </div>
        <a-empty v-else description="暂无应用，点击右上角「创建应用」开始创作" class="empty-box">
          <template #image>
            <div class="empty-icon">📁</div>
          </template>
        </a-empty>
      </template>
      <a-empty v-else description="请先登录后查看项目" class="empty-box">
        <template #image>
          <div class="empty-icon">🔒</div>
        </template>
        <template #default>
          <a-button type="primary" @click="router.push('/user/login?redirect=' + encodeURIComponent('/project'))">
            去登录
          </a-button>
        </template>
      </a-empty>

      <a-pagination
        v-if="total > pageSize"
        v-model:current="page"
        :total="total"
        :page-size="pageSize"
        class="section-pagination"
        @change="loadData"
      />
    </a-spin>

    <AppDetailModal v-model:open="detailOpen" :app="detailApp" />
  </div>
</template>

<style scoped>
.project-page {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 20px;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 800;
  color: #1f2430;
}

.page-desc {
  margin: 6px 0 0;
  color: #8a90a6;
  font-size: 14px;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.create-btn {
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: linear-gradient(90deg, #4f46e5, #7c3aed);
  border: none;
}

.create-btn:hover {
  background: linear-gradient(90deg, #4338ca, #6d28d9) !important;
}

.manage-btn {
  border-radius: 999px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
}

.keyword-input {
  width: 300px;
}

.total-text {
  color: #98a0b3;
  font-size: 13px;
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
  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 520px) {
  .app-grid {
    grid-template-columns: 1fr;
  }
}
</style>
