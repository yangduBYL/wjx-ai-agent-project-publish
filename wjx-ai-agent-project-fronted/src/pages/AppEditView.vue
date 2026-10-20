<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { SaveOutlined } from '@ant-design/icons-vue'
import { getappById, update1 } from '@/api/appController'
import { asId } from '@/utils/app'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 后端雪花 ID 以字符串返回（Long 序列化），严禁 Number() 转换，否则会丢失精度
const appId = (route.params.id as string) || ''

const loading = ref(false)
const submitting = ref(false)
const notFound = ref(false)

const form = reactive<{
  id?: number
  appName?: string
  cover?: string
  priority?: number
  initprompt?: string
}>({
  id: undefined,
  appName: '',
  cover: '',
  priority: 0,
  initprompt: '',
})

/** 是否管理员（管理员可编辑任意应用，普通用户仅自己的应用） */
const isAdmin = computed(() => userStore.isAdmin())
/** 是否允许编辑（本人应用或管理员） */
const canEdit = computed(
  () => isAdmin.value || appOwnerId.value === userStore.currentUser?.id,
)
const appOwnerId = ref<number | undefined>(undefined)

onMounted(async () => {
  loading.value = true
  try {
    // 该接口后端直接返回实体，无信封包裹；id 保持字符串透传
    const app = await getappById({ id: asId(appId) })
    if (!app) {
      notFound.value = true
      return
    }
    form.id = app.id
    form.appName = app.appName ?? ''
    form.cover = app.cover ?? ''
    form.priority = app.priority ?? 0
    form.initprompt = app.initprompt ?? ''
    appOwnerId.value = app.userId

    if (!userStore.loaded) {
      await userStore.fetchLoginUser()
    }
    // 权限校验：普通用户只能编辑自己的应用
    if (!isAdmin.value && appOwnerId.value !== userStore.currentUser?.id) {
      message.error('只能编辑自己的应用')
      router.replace('/')
    }
  } catch {
    notFound.value = true
  } finally {
    loading.value = false
  }
})

async function handleSubmit() {
  if (!form.id) return
  if (!form.appName?.trim()) {
    message.warning('应用名称不能为空')
    return
  }
  if (!canEdit.value) {
    message.error('无权限编辑该应用')
    return
  }

  submitting.value = true
  try {
    const payload: API.AppUpdateRequest = {
      id: form.id,
      appName: form.appName.trim(),
    }
    // 管理员额外支持修改封面和优先级
    if (isAdmin.value) {
      payload.cover = form.cover?.trim() || undefined
      payload.priority = form.priority ?? 0
    }
    await update1(payload)
    message.success('保存成功')
    // 管理页编辑后返回上一页，普通用户返回项目页
    if (isAdmin.value) {
      router.back()
    } else {
      router.push('/project')
    }
  } catch {
    // 错误已由拦截器提示
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="edit-page">
    <div class="edit-card">
      <a-page-header
        class="edit-header"
        title="应用信息修改"
        :sub-title="isAdmin ? '管理员可修改名称、封面和优先级' : '目前仅支持修改应用名称'"
        @back="router.back()"
      />

      <a-spin :spinning="loading">
        <a-empty v-if="notFound" description="应用不存在或已被删除" />

        <a-form v-else-if="form.id" :label-col="{ span: 4 }" :wrapper-col="{ span: 16 }" class="edit-form">
          <a-form-item label="应用 ID">
            <a-input :value="String(form.id)" disabled />
          </a-form-item>

          <a-form-item label="应用名称" required>
            <a-input
              v-model:value="form.appName"
              placeholder="请输入应用名称"
              :maxlength="50"
              show-count
            />
          </a-form-item>

          <!-- 管理员专属字段 -->
          <template v-if="isAdmin">
            <a-form-item label="应用封面">
              <a-input
                v-model:value="form.cover"
                placeholder="请输入封面图片地址（URL）"
                allow-clear
              />
            </a-form-item>

            <a-form-item label="优先级">
              <a-input-number
                v-model:value="form.priority"
                :min="0"
                :max="99"
                style="width: 200px"
              />
              <div class="form-tip">设为 99 即为精选应用，将在精选列表优先展示</div>
            </a-form-item>
          </template>

          <a-form-item label="初始提示词">
            <div class="initprompt-view">{{ form.initprompt || '暂无' }}</div>
          </a-form-item>

          <a-form-item :wrapper-col="{ offset: 4, span: 16 }">
            <a-button
              type="primary"
              class="save-btn"
              :loading="submitting"
              :disabled="!canEdit"
              @click="handleSubmit"
            >
              <SaveOutlined v-if="!submitting" />
              保存修改
            </a-button>
            <a-button class="cancel-btn" @click="router.back()">取消</a-button>
          </a-form-item>
        </a-form>
      </a-spin>
    </div>
  </div>
</template>

<style scoped>
.edit-page {
  max-width: 760px;
  margin: 0 auto;
  padding: 8px 0;
}

.edit-card {
  background: #fff;
  border-radius: 14px;
  padding: 20px 28px 32px;
  border: 1px solid #eef0f6;
  box-shadow: 0 4px 18px rgba(30, 41, 82, 0.06);
}

.edit-header {
  padding: 0 0 16px;
  margin-bottom: 20px;
  border-bottom: 1px solid #f0f2f8;
}

.edit-form {
  margin-top: 8px;
}

.form-tip {
  font-size: 12px;
  color: #98a0b3;
  margin-top: 4px;
}

.initprompt-view {
  background: #f7f8fc;
  border-radius: 8px;
  padding: 10px 14px;
  color: #4b5563;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 120px;
  overflow-y: auto;
}

.save-btn {
  border-radius: 999px;
  padding: 0 28px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: linear-gradient(90deg, #4f46e5, #7c3aed);
  border: none;
}

.save-btn:hover {
  background: linear-gradient(90deg, #4338ca, #6d28d9) !important;
}

.cancel-btn {
  border-radius: 999px;
  margin-left: 12px;
}

@media (max-width: 640px) {
  .edit-card {
    padding: 16px;
  }
}
</style>
