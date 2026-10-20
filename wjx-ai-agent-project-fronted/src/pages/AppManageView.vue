<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
  CloudUploadOutlined,
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  ReloadOutlined,
  RocketOutlined,
  SearchOutlined,
  StarFilled,
} from '@ant-design/icons-vue'
import {
  deleteUsingDelete,
  deploy as deployApi,
  listAppVoByPageByAdmin,
  update1,
} from '@/api/appController'
import {
  asId,
  CODE_GEN_TYPE_COLORS,
  CodeGenTypeEnum,
  DEFAULT_USER_AVATAR,
  formatTime,
  getCodeGenTypeLabel,
  isValidImageUrl,
  openVisitUrl,
} from '@/utils/app'
import AppDetailModal from '@/components/AppDetailModal.vue'

/** 代码模型筛选选项：单文件 / 多文件 / Vue 工程 */
const codeGenTypeOptions = [
  { label: getCodeGenTypeLabel(CodeGenTypeEnum.HTML), value: CodeGenTypeEnum.HTML },
  { label: getCodeGenTypeLabel(CodeGenTypeEnum.MULTI_FILE), value: CodeGenTypeEnum.MULTI_FILE },
  {
    label: getCodeGenTypeLabel(CodeGenTypeEnum.VUE_PROJECT),
    value: CodeGenTypeEnum.VUE_PROJECT,
  },
]

/** 代码模式标签文案（未知类型回退原始值） */
function codeTypeLabel(type?: string): string {
  return getCodeGenTypeLabel(type)
}

/** 代码模式标签颜色 */
function codeTypeColor(type?: string): string {
  return (type && CODE_GEN_TYPE_COLORS[type]) || 'default'
}

const router = useRouter()

const loading = ref(false)
const dataList = ref<API.AppUserVo[]>([])
const total = ref(0)

const queryForm = reactive<API.AppQueryRequest>({
  pageNum: 1,
  pageSize: 10,
  sortField: 'createTime',
  sortOrder: 'descend',
  appName: undefined,
  codeGenType: undefined,
  priority: undefined,
  userId: undefined,
})

/* ---------------- 查询 ---------------- */
async function loadData(pageNum?: number) {
  const target = pageNum ?? queryForm.pageNum ?? 1
  loading.value = true
  try {
    const res = await listAppVoByPageByAdmin({
      ...queryForm,
      pageNum: target,
    })
    dataList.value = res?.data?.records ?? []
    total.value = res?.data?.totalRow ?? 0
    queryForm.pageNum = target
  } catch {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}

function onSearch() {
  loadData(1)
}

function onReset() {
  queryForm.appName = undefined
  queryForm.codeGenType = undefined
  queryForm.priority = undefined
  queryForm.userId = undefined
  loadData(1)
}

/* ---------------- 操作 ---------------- */
/** 编辑：新开页面跳转到应用信息修改页 */
function onEdit(record: API.AppUserVo) {
  router.push(`/app/edit/${record.id}`)
}

/** 删除 */
function onDelete(record: API.AppUserVo) {
  Modal.confirm({
    title: '删除应用',
    content: `确定要删除应用「${record.appName || '未命名应用'}」吗？删除后不可恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      await deleteUsingDelete({ id: record.id })
      message.success('删除成功')
      // 如果当前页删空了，回退一页
      const currentPage = queryForm.pageNum ?? 1
      if (dataList.value.length === 1 && currentPage > 1) {
        loadData(currentPage - 1)
      } else {
        loadData()
      }
    },
  })
}

/** 精选：设置应用优先级为 99 */
function onFeature(record: API.AppUserVo) {
  const alreadyFeatured = Number(record.priority ?? 0) >= 99
  Modal.confirm({
    title: alreadyFeatured ? '取消精选' : '设为精选',
    content: alreadyFeatured
      ? `确定要取消「${record.appName || '未命名应用'}」的精选状态吗？`
      : `确定要将「${record.appName || '未命名应用'}」设为精选应用吗？（优先级将设置为 99）`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      await update1({ id: record.id, priority: alreadyFeatured ? 0 : 99 })
      message.success(alreadyFeatured ? '已取消精选' : '已设为精选')
      loadData()
    },
  })
}

/** 详情 */
const detailOpen = ref(false)
const detailApp = ref<API.AppUserVo | null>(null)

function onDetail(record: API.AppUserVo) {
  detailApp.value = record
  detailOpen.value = true
}

/* ---------------- 部署（未部署的应用提供部署按钮） ---------------- */
const deployingId = ref<string | number | null>(null)

async function onDeploy(record: API.AppUserVo) {
  if (deployingId.value) return
  deployingId.value = record.id ?? null
  // Vue 工程模式部署时需要先执行 npm install + npm run build（可能耗时几分钟），
  // 默认 60s 超时会在构建完成前就断开，这里单独放宽超时时间并给出等待提示
  const isVueProject = record.codeGenType === CodeGenTypeEnum.VUE_PROJECT
  const hideLoading = isVueProject
    ? message.loading('Vue 工程正在构建（首次需 npm install），请耐心等待…', 0)
    : null
  try {
    const res = await deployApi(
      { appId: asId(record.id) },
      isVueProject ? { timeout: 600000 } : undefined,
    )
    message.success('部署成功！')
    // 部署后返回可访问地址
    if (res?.data) {
      Modal.success({
        title: '部署成功',
        content: `应用已部署，可访问地址：${res.data}`,
        okText: '打开访问',
        cancelText: '关闭',
        onOk: () => window.open(res.data, '_blank', 'noopener,noreferrer'),
      })
    }
    // 刷新列表（deployKey 已更新，部署按钮变为访问）
    loadData()
  } catch {
    // 错误已由拦截器提示（如"应用代码不存在，请先生成代码"）
  } finally {
    hideLoading?.()
    deployingId.value = null
  }
}

onMounted(() => {
  loadData(1)
})
</script>

<template>
  <div class="manage-page">
    <!-- 查询表单 -->
    <div class="search-card">
      <a-form layout="inline" class="search-form">
        <a-form-item label="应用名称">
          <a-input
            v-model:value="queryForm.appName"
            placeholder="按名称模糊查询"
            allow-clear
            style="width: 180px"
            @press-enter="onSearch"
          />
        </a-form-item>
        <a-form-item label="代码模式">
          <a-select
            v-model:value="queryForm.codeGenType"
            placeholder="全部"
            allow-clear
            style="width: 140px"
            :options="codeGenTypeOptions"
          />
        </a-form-item>
        <a-form-item label="优先级">
          <a-input-number
            v-model:value="queryForm.priority"
            placeholder="精确匹配"
            style="width: 120px"
            :min="0"
            :max="99"
          />
        </a-form-item>
        <a-form-item label="用户 ID">
          <a-input-number
            v-model:value="queryForm.userId"
            placeholder="创建者 ID"
            style="width: 130px"
            :min="1"
            @press-enter="onSearch"
          />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" class="search-btn" @click="onSearch">
            <SearchOutlined />
            查询
          </a-button>
          <a-button class="reset-btn" @click="onReset">
            <ReloadOutlined />
            重置
          </a-button>
        </a-form-item>
      </a-form>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <a-table
        :data-source="dataList"
        :loading="loading"
        :scroll="{ x: 1350 }"
        :pagination="{
          current: queryForm.pageNum ?? 1,
          pageSize: queryForm.pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t: number) => `共 ${t} 条`,
        }"
        row-key="id"
        size="middle"
        @change="(pag: any) => { queryForm.pageSize = pag.pageSize ?? queryForm.pageSize; loadData(pag.current) }"
      >
        <a-table-column title="ID" data-index="id" width="70" />
        <a-table-column title="应用名称" data-index="appName" width="180" ellipsis>
          <template #default="{ record }">
            <span class="app-name-cell">{{ record.appName || '未命名应用' }}</span>
          </template>
        </a-table-column>
        <a-table-column title="封面" width="90">
          <template #default="{ record }">
            <a-avatar
              :src="isValidImageUrl(record.cover) ? record.cover : undefined"
              shape="square"
              :size="44"
              class="cover-cell"
            >
              {{ (record.appName || 'A').charAt(0) }}
            </a-avatar>
          </template>
        </a-table-column>
        <a-table-column title="代码模式" width="110">
          <template #default="{ record }">
            <a-tag :color="codeTypeColor(record.codeGenType)">
              {{ codeTypeLabel(record.codeGenType) }}
            </a-tag>
          </template>
        </a-table-column>
        <a-table-column title="创建者" width="140">
          <template #default="{ record }">
            <span class="creator-cell">
              <a-avatar :src="record.uservo?.userAvatar || DEFAULT_USER_AVATAR" :size="22" />
              <span class="creator-name">{{
                record.uservo?.userName || record.uservo?.userAccount || '-'
              }}</span>
            </span>
          </template>
        </a-table-column>
        <a-table-column title="优先级" width="90">
          <template #default="{ record }">
            <span v-if="Number(record.priority ?? 0) >= 99" class="featured-cell">
              <StarFilled />
              {{ record.priority }}
            </span>
            <span v-else>{{ record.priority ?? 0 }}</span>
          </template>
        </a-table-column>
        <a-table-column title="部署状态" width="100">
          <template #default="{ record }">
            <a-tag v-if="record.deployKey" color="success">已部署</a-tag>
            <a-tag v-else>未部署</a-tag>
          </template>
        </a-table-column>
        <a-table-column title="创建时间" width="170">
          <template #default="{ record }">{{ formatTime(record.createTime) }}</template>
        </a-table-column>
        <a-table-column title="操作" width="360">
          <template #default="{ record }">
            <div class="action-group">
              <a-button class="ab ab-detail" @click="onDetail(record)">
                <EyeOutlined />
                详情
              </a-button>
              <a-button class="ab ab-edit" @click="onEdit(record)">
                <EditOutlined />
                编辑
              </a-button>
              <a-button
                class="ab"
                :class="Number(record.priority ?? 0) >= 99 ? 'ab-unfeature' : 'ab-feature'"
                @click="onFeature(record)"
              >
                <StarFilled />
                {{ Number(record.priority ?? 0) >= 99 ? '取消精选' : '精选' }}
              </a-button>
              <!-- 已部署：提供访问按钮；未部署：提供部署按钮 -->
              <a-button
                v-if="record.deployKey"
                class="ab ab-visit"
                @click="openVisitUrl(record)"
              >
                <RocketOutlined />
                访问
              </a-button>
              <a-button
                v-else
                class="ab ab-deploy"
                :loading="deployingId === record.id"
                :disabled="!!deployingId"
                @click="onDeploy(record)"
              >
                <CloudUploadOutlined v-if="deployingId !== record.id" />
                部署
              </a-button>
              <a-button class="ab ab-delete" danger @click="onDelete(record)">
                <DeleteOutlined />
                删除
              </a-button>
            </div>
          </template>
        </a-table-column>
      </a-table>
    </div>

    <AppDetailModal v-model:open="detailOpen" :app="detailApp" />
  </div>
</template>

<style scoped>
.manage-page {
  max-width: 1400px;
  margin: 0 auto;
}

.search-card {
  background: #fff;
  border-radius: 12px;
  padding: 18px 20px;
  margin-bottom: 16px;
  border: 1px solid #eef0f6;
}

.search-form :deep(.ant-form-item) {
  margin-bottom: 8px;
}

.search-btn {
  border-radius: 8px;
}

.reset-btn {
  border-radius: 8px;
  margin-left: 8px;
}

.table-card {
  background: #fff;
  border-radius: 12px;
  padding: 8px 16px;
  border: 1px solid #eef0f6;
}

.app-name-cell {
  font-weight: 600;
  color: #1f2430;
}

.cover-cell {
  border-radius: 8px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
}

.creator-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.creator-name {
  max-width: 90px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.featured-cell {
  color: #d97706;
  font-weight: 600;
}
</style>
