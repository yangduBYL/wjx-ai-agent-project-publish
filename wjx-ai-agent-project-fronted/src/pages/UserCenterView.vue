<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { EditOutlined, SaveOutlined, TeamOutlined } from '@ant-design/icons-vue'
import { update2 } from '@/api/userController'
import { useUserStore } from '@/stores/user'
import { formatTime, isValidImageUrl } from '@/utils/app'

const router = useRouter()
const userStore = useUserStore()

const saving = ref(false)

/** 当前用户信息 */
const user = computed(() => userStore.currentUser)

/** 是否管理员 */
const isAdmin = computed(() => userStore.isAdmin())

/** 默认头像（渐变首字符，替代系统头像） */
const avatarUrl = computed(() => {
  if (user.value?.userAvatar && isValidImageUrl(user.value.userAvatar)) {
    return user.value.userAvatar
  }
  return ''
})

const avatarChar = computed(() => {
  const name = user.value?.userName || user.value?.userAccount || '我'
  return name.charAt(0).toUpperCase()
})

/** 角色标签 */
const roleTag = computed(() => {
  if (isAdmin.value) return { color: 'purple', text: '管理员' }
  return { color: 'blue', text: '普通用户' }
})

/* ---------------- 编辑资料（查改：普通用户可修改自己的昵称/头像/简介） ---------------- */
const editForm = reactive({
  userName: '',
  userProfile: '',
  userAvatar: '',
})

const editing = ref(false)

function startEdit() {
  editForm.userName = user.value?.userName || user.value?.userAccount || ''
  editForm.userProfile = user.value?.userProfile || ''
  editForm.userAvatar = user.value?.userAvatar || ''
  editing.value = true
}

function cancelEdit() {
  editing.value = false
}

async function handleSaveProfile() {
  if (!user.value?.id) return
  if (!editForm.userName.trim()) {
    message.warning('昵称不能为空')
    return
  }
  saving.value = true
  try {
    // 完整字段回传，避免后端 BeanUtil 拷贝时覆盖其他字段
    await update2({
      id: user.value.id as unknown as number,
      userName: editForm.userName.trim(),
      userProfile: editForm.userProfile.trim() || undefined,
      userAvatar: editForm.userAvatar.trim() || undefined,
    })
    message.success('资料已更新')
    editing.value = false
    // 刷新登录态，顶部头像昵称同步更新
    await userStore.fetchLoginUser()
  } catch {
    // 错误已由拦截器提示
  } finally {
    saving.value = false
  }
}

/** 头像预览（编辑输入时实时展示） */
const previewAvatar = computed(() =>
  isValidImageUrl(editForm.userAvatar.trim()) ? editForm.userAvatar.trim() : '',
)

onMounted(() => {
  if (!userStore.loaded) {
    userStore.fetchLoginUser()
  }
})
</script>

<template>
  <div class="user-center">
    <!-- 顶部资料卡 -->
    <div class="profile-card">
      <div class="profile-main">
        <a-avatar :size="84" class="profile-avatar" :src="avatarUrl || undefined">
          {{ avatarChar }}
        </a-avatar>
        <div class="profile-info">
          <div class="profile-name-row">
            <span class="profile-name">{{ user?.userName || user?.userAccount || '未登录' }}</span>
            <a-tag :color="roleTag.color" class="role-tag">{{ roleTag.text }}</a-tag>
          </div>
          <div class="profile-account">账号：{{ user?.userAccount || '-' }}</div>
          <div class="profile-meta">
            <span>ID：{{ user?.id || '-' }}</span>
            <span class="meta-sep">·</span>
            <span>注册时间：{{ formatTime(user?.createTime) }}</span>
          </div>
        </div>
      </div>

      <div class="profile-actions">
        <a-button
          v-if="isAdmin"
          class="manage-entry-btn"
          @click="router.push('/user/manage')"
        >
          <TeamOutlined />
          用户管理
        </a-button>
        <a-button v-if="!editing" type="primary" class="edit-btn" @click="startEdit">
          <EditOutlined />
          编辑资料
        </a-button>
      </div>
    </div>

    <!-- 编辑资料表单 -->
    <div class="section-card">
      <h3 class="section-title">
        <template v-if="editing">编辑资料</template>
        <template v-else>我的资料</template>
      </h3>
      <p class="section-desc">
        普通用户可修改自己的昵称、头像和简介；账号、角色等由系统管理，不可自行修改
      </p>

      <a-form :label-col="{ span: 4 }" :wrapper-col="{ span: 16 }" class="profile-form">
        <a-form-item label="昵称">
          <a-input
            v-model:value="editForm.userName"
            :disabled="!editing"
            placeholder="请输入昵称"
            :maxlength="30"
            show-count
          />
        </a-form-item>

        <a-form-item label="头像">
          <div class="avatar-field">
            <a-input
              v-model:value="editForm.userAvatar"
              :disabled="!editing"
              placeholder="请输入头像图片地址（URL），留空使用默认头像"
              allow-clear
            />
            <a-avatar :size="40" class="avatar-preview" :src="previewAvatar || avatarUrl || undefined">
              {{ avatarChar }}
            </a-avatar>
          </div>
        </a-form-item>

        <a-form-item label="个人简介">
          <a-textarea
            v-model:value="editForm.userProfile"
            :disabled="!editing"
            placeholder="介绍一下自己吧"
            :maxlength="200"
            :rows="3"
            show-count
          />
        </a-form-item>

        <a-form-item v-if="editing" :wrapper-col="{ offset: 4, span: 16 }">
          <a-button type="primary" class="save-btn" :loading="saving" @click="handleSaveProfile">
            <SaveOutlined v-if="!saving" />
            保存修改
          </a-button>
          <a-button class="cancel-btn" @click="cancelEdit">取消</a-button>
        </a-form-item>
      </a-form>
    </div>

    <!-- 我的应用快捷入口 -->
    <div class="section-card shortcut-card">
      <div class="shortcut-info">
        <h3 class="section-title" style="margin-bottom: 4px">我的项目</h3>
        <p class="section-desc" style="margin: 0">管理你创建的应用，查看生成效果与部署状态</p>
      </div>
      <a-button @click="router.push('/project')">进入项目页 →</a-button>
    </div>
  </div>
</template>

<style scoped>
.user-center {
  max-width: 860px;
  margin: 0 auto;
}

/* ---------- 顶部资料卡 ---------- */
.profile-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: linear-gradient(135deg, #1c1f4a 0%, #2b2560 60%, #3b2a78 100%);
  border-radius: 16px;
  padding: 26px 30px;
  color: #fff;
  box-shadow: 0 10px 30px rgba(43, 37, 96, 0.3);
}

.profile-main {
  display: flex;
  align-items: center;
  gap: 20px;
  min-width: 0;
}

.profile-avatar {
  flex-shrink: 0;
  background: linear-gradient(135deg, #6366f1, #a855f7);
  font-size: 30px;
  font-weight: 700;
  border: 3px solid rgba(255, 255, 255, 0.35);
}

.profile-info {
  min-width: 0;
}

.profile-name-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.profile-name {
  font-size: 24px;
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.role-tag {
  border-radius: 999px;
  flex-shrink: 0;
}

.profile-account {
  margin-top: 6px;
  color: rgba(255, 255, 255, 0.75);
  font-size: 14px;
}

.profile-meta {
  margin-top: 4px;
  color: rgba(255, 255, 255, 0.55);
  font-size: 12.5px;
  display: flex;
  gap: 6px;
}

.profile-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex-shrink: 0;
}

.edit-btn {
  border-radius: 999px;
  background: linear-gradient(90deg, #6366f1, #a855f7);
  border: none;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.edit-btn:hover {
  background: linear-gradient(90deg, #4f46e5, #9333ea) !important;
}

.manage-entry-btn {
  border-radius: 999px;
  border-color: rgba(255, 255, 255, 0.5);
  color: #fff;
  background: rgba(255, 255, 255, 0.1);
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.manage-entry-btn:hover {
  border-color: #fff !important;
  color: #fff !important;
  background: rgba(255, 255, 255, 0.2) !important;
}

/* ---------- 资料/表单卡片 ---------- */
.section-card {
  background: #fff;
  border-radius: 14px;
  padding: 22px 26px 26px;
  margin-top: 18px;
  border: 1px solid #eef0f6;
  box-shadow: 0 4px 18px rgba(30, 41, 82, 0.05);
}

.section-title {
  margin: 0 0 4px;
  font-size: 17px;
  font-weight: 700;
  color: #1f2430;
}

.section-desc {
  margin: 0 0 18px;
  color: #98a0b3;
  font-size: 13px;
}

.profile-form {
  margin-top: 4px;
}

.avatar-field {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.avatar-preview {
  flex-shrink: 0;
  background: linear-gradient(135deg, #6366f1, #a855f7);
  font-weight: 600;
}

.save-btn {
  border-radius: 999px;
  padding: 0 26px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: linear-gradient(90deg, #4f46e5, #7c3aed);
  border: none;
}

.cancel-btn {
  border-radius: 999px;
  margin-left: 10px;
}

/* ---------- 快捷入口 ---------- */
.shortcut-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.shortcut-card:hover {
  border-color: #c7cdf5;
  box-shadow: 0 6px 22px rgba(79, 70, 229, 0.1);
}

@media (max-width: 640px) {
  .profile-card {
    flex-direction: column;
    align-items: flex-start;
  }
  .profile-actions {
    flex-direction: row;
  }
}
</style>
