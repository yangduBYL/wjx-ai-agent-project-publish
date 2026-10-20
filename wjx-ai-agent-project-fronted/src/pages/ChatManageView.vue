<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
  CopyOutlined,
  DeleteOutlined,
  EyeOutlined,
  LinkOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
import { listAllPageChatHistory, remove1 } from '@/api/chatHistoryController'
import { asId, formatTime } from '@/utils/app'

const router = useRouter()

const loading = ref(false)
const dataList = ref<API.ChatHistory[]>([])
const total = ref(0)

const queryForm = reactive<API.ChatHistoryQueryRequest>({
  pageNum: 1,
  pageSize: 10,
  sortField: 'createTime',
  sortOrder: 'descend',
  message: undefined,
  messageType: undefined,
  appId: undefined,
  userId: undefined,
})

/* ---------------- 查询 ---------------- */
async function loadData(pageNum?: number) {
  const target = pageNum ?? queryForm.pageNum ?? 1
  loading.value = true
  try {
    const res = await listAllPageChatHistory({
      ...queryForm,
      pageNum: target,
    })
    dataList.value = res?.data?.records ?? []
    // 后端 Page 的 totalRow 序列化后是字符串（如 "13"），显式转成数字供分页使用
    total.value = Number(res?.data?.totalRow ?? 0) || 0
    queryForm.pageNum = target
  } catch {
    // 错误已由拦截器提示（非管理员会返回无权限）
  } finally {
    loading.value = false
  }
}

function onSearch() {
  loadData(1)
}

function onReset() {
  queryForm.message = undefined
  queryForm.messageType = undefined
  queryForm.appId = undefined
  queryForm.userId = undefined
  loadData(1)
}

/* ---------------- 展示辅助 ---------------- */
/** 消息类型文案（后端枚举值：user / ai） */
function messageTypeText(type?: string) {
  if (type === 'user') return '用户'
  if (type === 'ai') return 'AI'
  return type || '-'
}

/** 消息内容单行预览（换行折叠，超长截断） */
function previewOf(text?: string, max = 160) {
  const flat = (text ?? '').replace(/\s+/g, ' ').trim()
  return flat.length > max ? `${flat.slice(0, max)}…` : flat || '-'
}

/** 是否 AI 生成的消息（AI 消息里通常包含生成的代码） */
function isAiMessage(record: API.ChatHistory) {
  return record.messageType === 'ai'
}

/* ---------------- 详情 ---------------- */
const detailOpen = ref(false)
const detailRecord = ref<API.ChatHistory | null>(null)

function onDetail(record: API.ChatHistory) {
  detailRecord.value = record
  detailOpen.value = true
}

const detailMessage = computed(() => detailRecord.value?.message ?? '')

async function copyDetailMessage() {
  if (!detailMessage.value) return
  try {
    await navigator.clipboard.writeText(detailMessage.value)
    message.success('消息内容已复制')
  } catch {
    message.warning('复制失败，请手动复制')
  }
}

/* ---------------- 查看所属应用（进入该应用的对话页） ---------------- */
function onViewApp(record: API.ChatHistory | null) {
  if (!record?.appId) {
    message.warning('该对话记录没有关联应用')
    return
  }
  // appId 保持字符串透传（雪花 ID 禁止 Number() 转换）
  router.push(`/app/generate/${record.appId}`)
}

/* ---------------- 删除 ---------------- */
function onDelete(record: API.ChatHistory) {
  Modal.confirm({
    title: '删除对话记录',
    content: `确定要删除这条对话记录吗？删除后不可恢复。\n内容：${previewOf(record.message, 60)}`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      // 该接口后端直接返回布尔值（无 BaseResponse 信封）
      const ok = await remove1({ id: asId(record.id) })
      if (ok === true) {
        message.success('删除成功')
        // 如果当前页删空了，回退一页
        const currentPage = queryForm.pageNum ?? 1
        if (dataList.value.length === 1 && currentPage > 1) {
          loadData(currentPage - 1)
        } else {
          loadData()
        }
      } else {
        message.error('删除失败，请稍后重试')
      }
    },
  })
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
        <a-form-item label="消息内容">
          <a-input
            v-model:value="queryForm.message"
            placeholder="按内容模糊查询"
            allow-clear
            style="width: 200px"
            @press-enter="onSearch"
          />
        </a-form-item>
        <a-form-item label="消息类型">
          <a-select
            v-model:value="queryForm.messageType"
            placeholder="全部"
            allow-clear
            style="width: 130px"
            :options="[
              { label: '用户', value: 'user' },
              { label: 'AI', value: 'ai' },
            ]"
          />
        </a-form-item>
        <a-form-item label="应用 ID">
          <!-- 雪花 ID 必须按字符串传输，用文本框而不是数字输入框，避免精度丢失 -->
          <a-input
            v-model:value="queryForm.appId"
            placeholder="应用 ID"
            allow-clear
            style="width: 190px"
            @press-enter="onSearch"
          />
        </a-form-item>
        <a-form-item label="用户 ID">
          <a-input
            v-model:value="queryForm.userId"
            placeholder="创建者 ID"
            allow-clear
            style="width: 190px"
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
        :scroll="{ x: 1400 }"
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
        <a-table-column title="记录 ID" data-index="id" width="180" ellipsis />
        <a-table-column title="消息内容" width="360" ellipsis>
          <template #default="{ record }">
            <span class="msg-cell" :title="record.message">{{ previewOf(record.message, 120) }}</span>
          </template>
        </a-table-column>
        <a-table-column title="消息类型" width="100">
          <template #default="{ record }">
            <a-tag :color="isAiMessage(record) ? 'green' : 'blue'">
              {{ messageTypeText(record.messageType) }}
            </a-tag>
          </template>
        </a-table-column>
        <a-table-column title="应用 ID" width="180" ellipsis>
          <template #default="{ record }">
            <span class="id-cell" :title="String(record.appId ?? '')">{{ record.appId ?? '-' }}</span>
          </template>
        </a-table-column>
        <a-table-column title="用户 ID" width="180" ellipsis>
          <template #default="{ record }">
            <span class="id-cell" :title="String(record.userId ?? '')">{{ record.userId ?? '-' }}</span>
          </template>
        </a-table-column>
        <a-table-column title="创建时间" width="170">
          <template #default="{ record }">{{ formatTime(record.createTime) }}</template>
        </a-table-column>
        <a-table-column title="操作" width="300">
          <template #default="{ record }">
            <div class="action-group">
              <a-button class="ab ab-detail" @click="onDetail(record)">
                <EyeOutlined />
                详情
              </a-button>
              <a-button class="ab ab-visit" @click="onViewApp(record)">
                <LinkOutlined />
                查看应用
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

    <!-- 消息详情 -->
    <a-modal
      v-model:open="detailOpen"
      :title="`对话记录详情（${messageTypeText(detailRecord?.messageType)}）`"
      width="760px"
      :footer="null"
    >
      <div v-if="detailRecord" class="detail-body">
        <a-descriptions :column="2" size="small" bordered>
          <a-descriptions-item label="记录 ID">{{ detailRecord.id }}</a-descriptions-item>
          <a-descriptions-item label="消息类型">
            {{ messageTypeText(detailRecord.messageType) }}
          </a-descriptions-item>
          <a-descriptions-item label="应用 ID">{{ detailRecord.appId ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="用户 ID">{{ detailRecord.userId ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="创建时间">
            {{ formatTime(detailRecord.createTime) }}
          </a-descriptions-item>
          <a-descriptions-item label="消息长度">
            {{ (detailRecord.message ?? '').length }} 字符
          </a-descriptions-item>
        </a-descriptions>

        <div class="detail-message-label">消息内容</div>
        <pre class="detail-message">{{ detailMessage || '（空）' }}</pre>

        <div class="detail-actions">
          <a-button class="action-btn" @click="copyDetailMessage">
            <CopyOutlined />
            复制内容
          </a-button>
          <a-button type="primary" class="action-btn" @click="onViewApp(detailRecord)">
            进入所属应用
          </a-button>
        </div>
      </div>
    </a-modal>
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

.msg-cell {
  color: #1f2430;
}

.id-cell {
  font-family: 'Consolas', 'Menlo', 'Courier New', monospace;
  font-size: 12.5px;
  color: #6b7280;
}

/* ---------- 详情弹窗 ---------- */
.detail-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-message-label {
  font-size: 13px;
  font-weight: 600;
  color: #4b5563;
}

.detail-message {
  margin: 0;
  max-height: 380px;
  overflow: auto;
  padding: 12px 14px;
  border-radius: 10px;
  background: #f7f8fc;
  border: 1px solid #eef0f6;
  font-family: 'Consolas', 'Menlo', 'Courier New', monospace;
  font-size: 12.5px;
  line-height: 1.65;
  color: #374151;
  white-space: pre-wrap;
  word-break: break-word;
}

.detail-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.action-btn {
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
</style>
