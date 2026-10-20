<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
import {
  add as addUserApi,
  getUserById,
  listUserVoByPage,
  remove as removeUserApi,
  update2 as updateUserApi,
} from '@/api/userController'
import { asId, formatTime, isValidImageUrl } from '@/utils/app'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

/** 新增用户的默认初始密码（需与后端 addUser 默认一致，且不少于 8 位，否则新用户无法登录） */
const DEFAULT_USER_PASSWORD = '12345678'

const loading = ref(false)
const dataList = ref<API.UserVo[]>([])
const total = ref(0)

const queryForm = reactive<API.UserQueryRequest>({
  pageNum: 1,
  pageSize: 10,
  sortField: 'createTime',
  sortOrder: 'descend',
  userName: undefined,
  userAccount: undefined,
  userRole: undefined,
  id: undefined,
})

const roleOptions = [
  { label: '普通用户', value: 'user' },
  { label: '管理员', value: 'admin' },
]

/** 角色展示 */
function roleTagInfo(role?: string) {
  if (role === 'admin') return { color: 'purple', text: '管理员' }
  return { color: 'blue', text: '普通用户' }
}

/** 是否当前登录用户自己（禁止删除自己） */
function isSelf(record: API.UserVo) {
  return String(record.id) === String(userStore.currentUser?.id)
}

/* ---------------- 列表查询 ---------------- */
async function loadData(pageNum?: number) {
  const target = pageNum ?? queryForm.pageNum ?? 1
  loading.value = true
  try {
    const res = await listUserVoByPage({ ...queryForm, pageNum: target })
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
  queryForm.userName = undefined
  queryForm.userAccount = undefined
  queryForm.userRole = undefined
  queryForm.id = undefined
  loadData(1)
}

/* ---------------- 新增/编辑弹窗 ---------------- */
const modalOpen = ref(false)
const modalMode = ref<'add' | 'edit'>('add')
const modalSaving = ref(false)

const form = reactive<{
  id?: number
  userAccount?: string
  userName?: string
  userAvatar?: string
  userRole?: string
  userProfile?: string
}>({
  id: undefined,
  userAccount: '',
  userName: '',
  userAvatar: '',
  userRole: 'user',
  userProfile: '',
})

/** 弹窗内头像预览 */
const formAvatarPreview = computed(() =>
  isValidImageUrl(form.userAvatar || '') ? form.userAvatar : '',
)

function openAddModal() {
  modalMode.value = 'add'
  form.id = undefined
  form.userAccount = ''
  form.userName = ''
  form.userAvatar = ''
  form.userRole = 'user'
  form.userProfile = ''
  modalOpen.value = true
}

function openEditModal(record: API.UserVo) {
  modalMode.value = 'edit'
  form.id = record.id
  form.userAccount = record.userAccount || ''
  form.userName = record.userName || ''
  form.userAvatar = record.userAvatar || ''
  form.userRole = record.userRole || 'user'
  form.userProfile = record.userProfile || ''
  modalOpen.value = true
}

async function handleModalOk() {
  if (modalMode.value === 'add' && !form.userAccount?.trim()) {
    message.warning('请输入账号')
    return
  }
  if (!form.userName?.trim()) {
    message.warning('请输入昵称')
    return
  }

  modalSaving.value = true
  try {
    if (modalMode.value === 'add') {
      await addUserApi({
        userAccount: form.userAccount?.trim(),
        userName: form.userName.trim(),
        userAvatar: form.userAvatar?.trim() || undefined,
        userRole: form.userRole,
        userProfile: form.userProfile?.trim() || undefined,
      })
      message.success(`用户创建成功（默认密码：${DEFAULT_USER_PASSWORD}）`)
    } else {
      // 完整字段回传，避免后端 BeanUtil 拷贝时覆盖其他字段
      await updateUserApi({
        id: form.id as unknown as number,
        userAccount: form.userAccount?.trim() || undefined,
        userName: form.userName.trim(),
        userAvatar: form.userAvatar?.trim() || undefined,
        userRole: form.userRole,
        userProfile: form.userProfile?.trim() || undefined,
      })
      message.success('用户信息已更新')
      // 若修改的是自己，同步刷新登录态
      if (String(form.id) === String(userStore.currentUser?.id)) {
        await userStore.fetchLoginUser()
      }
    }
    modalOpen.value = false
    loadData()
  } catch {
    // 错误已由拦截器提示
  } finally {
    modalSaving.value = false
  }
}

/* ---------------- 详情（根据 id 查询用户） ---------------- */
const detailOpen = ref(false)
const detailLoading = ref(false)
const detailUser = ref<API.UserVo | null>(null)

async function showDetail(record: API.UserVo) {
  detailLoading.value = true
  detailOpen.value = true
  detailUser.value = null
  try {
    // 调用 /user/get/{id} 按 id 实时查询
    const res = await getUserById({ id: asId(record.id) })
    detailUser.value = res?.data ?? null
  } catch {
    detailOpen.value = false
  } finally {
    detailLoading.value = false
  }
}

/* ---------------- 删除 ---------------- */
function onDelete(record: API.UserVo) {
  if (isSelf(record)) {
    message.warning('不能删除当前登录的账号')
    return
  }
  Modal.confirm({
    title: '删除用户',
    content: `确定要删除用户「${record.userName || record.userAccount || record.id}」吗？删除后不可恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      await removeUserApi({ id: record.id as unknown as number })
      message.success('删除成功')
      const currentPage = queryForm.pageNum ?? 1
      if (dataList.value.length === 1 && currentPage > 1) {
        loadData(currentPage - 1)
      } else {
        loadData()
      }
    },
  })
}

onMounted(() => {
  loadData(1)
})
</script>

<template>
  <div class="user-manage-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">用户管理</h2>
        <p class="page-desc">管理平台所有用户：新增、编辑、删除、分页查询与按 ID 查询（仅管理员可见）</p>
      </div>
      <a-button type="primary" class="add-btn" @click="openAddModal">
        <PlusOutlined />
        新增用户
      </a-button>
    </div>

    <!-- 查询表单 -->
    <div class="search-card">
      <a-form layout="inline" class="search-form">
        <a-form-item label="用户名">
          <a-input
            v-model:value="queryForm.userName"
            placeholder="按昵称模糊查询"
            allow-clear
            style="width: 160px"
            @press-enter="onSearch"
          />
        </a-form-item>
        <a-form-item label="账号">
          <a-input
            v-model:value="queryForm.userAccount"
            placeholder="按账号模糊查询"
            allow-clear
            style="width: 160px"
            @press-enter="onSearch"
          />
        </a-form-item>
        <a-form-item label="角色">
          <a-select
            v-model:value="queryForm.userRole"
            placeholder="全部"
            allow-clear
            style="width: 130px"
            :options="roleOptions"
          />
        </a-form-item>
        <a-form-item label="用户 ID">
          <a-input-number
            v-model:value="queryForm.id"
            placeholder="精确匹配"
            style="width: 160px"
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
        :scroll="{ x: 1100 }"
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
        <a-table-column title="ID" data-index="id" width="180" ellipsis />
        <a-table-column title="头像" width="70">
          <template #default="{ record }">
            <a-avatar
              :src="isValidImageUrl(record.userAvatar) ? record.userAvatar : undefined"
              :size="36"
              class="avatar-cell"
            >
              {{ (record.userName || record.userAccount || 'U').charAt(0).toUpperCase() }}
            </a-avatar>
          </template>
        </a-table-column>
        <a-table-column title="昵称" data-index="userName" width="140" ellipsis />
        <a-table-column title="账号" data-index="userAccount" width="160" ellipsis />
        <a-table-column title="角色" width="100">
          <template #default="{ record }">
            <a-tag :color="roleTagInfo(record.userRole).color">
              {{ roleTagInfo(record.userRole).text }}
            </a-tag>
          </template>
        </a-table-column>
        <a-table-column title="个人简介" data-index="userProfile" ellipsis>
          <template #default="{ record }">{{ record.userProfile || '-' }}</template>
        </a-table-column>
        <a-table-column title="创建时间" width="170">
          <template #default="{ record }">{{ formatTime(record.createTime) }}</template>
        </a-table-column>
        <a-table-column title="操作" width="300">
          <template #default="{ record }">
            <div class="action-group">
              <a-button class="ab ab-detail" @click="showDetail(record)">
                <EyeOutlined />
                详情
              </a-button>
              <a-button class="ab ab-edit" @click="openEditModal(record)">
                <EditOutlined />
                编辑
              </a-button>
              <a-button
                class="ab ab-delete"
                danger
                :disabled="isSelf(record)"
                @click="onDelete(record)"
              >
                <DeleteOutlined />
                删除
              </a-button>
            </div>
          </template>
        </a-table-column>
      </a-table>
    </div>

    <!-- 新增 / 编辑弹窗 -->
    <a-modal
      v-model:open="modalOpen"
      :title="modalMode === 'add' ? '新增用户' : '编辑用户'"
      :confirm-loading="modalSaving"
      @ok="handleModalOk"
    >
      <a-form :label-col="{ span: 5 }" :wrapper-col="{ span: 17 }" class="modal-form">
        <a-form-item v-if="modalMode === 'add'" label="账号" required>
          <a-input v-model:value="form.userAccount" placeholder="登录账号（至少 4 位）" />
        </a-form-item>
        <a-form-item v-else label="账号">
          <a-input v-model:value="form.userAccount" placeholder="登录账号" />
        </a-form-item>
        <a-form-item label="昵称" required>
          <a-input v-model:value="form.userName" placeholder="用户昵称" :maxlength="30" />
        </a-form-item>
        <a-form-item label="角色">
          <a-radio-group v-model:value="form.userRole">
            <a-radio value="user">普通用户</a-radio>
            <a-radio value="admin">管理员</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="头像">
          <div class="modal-avatar-field">
            <a-input v-model:value="form.userAvatar" placeholder="头像图片地址（URL）" allow-clear />
            <a-avatar :size="36" class="modal-avatar" :src="formAvatarPreview || undefined">
              {{ (form.userName || 'U').charAt(0).toUpperCase() }}
            </a-avatar>
          </div>
        </a-form-item>
        <a-form-item label="个人简介">
          <a-textarea
            v-model:value="form.userProfile"
            placeholder="个人简介"
            :rows="3"
            :maxlength="200"
            show-count
          />
        </a-form-item>
        <a-form-item v-if="modalMode === 'add'" label="初始密码">
          <a-input :value="DEFAULT_USER_PASSWORD" disabled />
          <div class="form-tip">新增用户默认密码为 {{ DEFAULT_USER_PASSWORD }}（不少于 8 位），可在登录后修改</div>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 详情弹窗（按 ID 查询） -->
    <a-modal v-model:open="detailOpen" :footer="null" title="用户详情">
      <a-spin :spinning="detailLoading">
        <div v-if="detailUser" class="detail-content">
          <a-descriptions :column="1" size="middle" bordered>
            <a-descriptions-item label="ID">{{ detailUser.id }}</a-descriptions-item>
            <a-descriptions-item label="昵称">{{ detailUser.userName || '-' }}</a-descriptions-item>
            <a-descriptions-item label="账号">{{ detailUser.userAccount || '-' }}</a-descriptions-item>
            <a-descriptions-item label="角色">
              <a-tag :color="roleTagInfo(detailUser.userRole).color">
                {{ roleTagInfo(detailUser.userRole).text }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="个人简介">{{ detailUser.userProfile || '-' }}</a-descriptions-item>
            <a-descriptions-item label="创建时间">
              {{ formatTime(detailUser.createTime) }}
            </a-descriptions-item>
          </a-descriptions>
        </div>
        <a-empty v-else-if="!detailLoading" description="未查询到该用户" />
      </a-spin>
    </a-modal>
  </div>
</template>

<style scoped>
.user-manage-page {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
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
  font-size: 13.5px;
}

.add-btn {
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: linear-gradient(90deg, #4f46e5, #7c3aed);
  border: none;
}

.add-btn:hover {
  background: linear-gradient(90deg, #4338ca, #6d28d9) !important;
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

.avatar-cell {
  background: linear-gradient(135deg, #6366f1, #a855f7);
  font-weight: 600;
}

.modal-form {
  padding-top: 8px;
}

.modal-avatar-field {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}

.modal-avatar {
  flex-shrink: 0;
  background: linear-gradient(135deg, #6366f1, #a855f7);
  font-weight: 600;
}

.form-tip {
  font-size: 12px;
  color: #98a0b3;
  margin-top: 4px;
}

.detail-content {
  padding-top: 4px;
}

@media (max-width: 640px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
