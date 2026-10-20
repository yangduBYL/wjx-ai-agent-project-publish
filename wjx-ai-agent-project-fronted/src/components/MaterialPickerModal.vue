<script setup lang="ts">
/**
 * 素材选择弹窗：列出我的素材，支持在弹窗内直接上传新图片。
 * 选中后通过 select 事件把素材（含 url）抛给父组件。
 */
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { LoadingOutlined, UploadOutlined } from '@ant-design/icons-vue'
import { listMyMaterials, uploadMaterial } from '@/api/materialController'
import { formatFileSize, validateImageFile } from '@/utils/material'
import { formatTime } from '@/utils/app'
import type { MaterialVO } from '@/types/material'

const props = withDefaults(
  defineProps<{
    /** 是否打开 */
    open: boolean
    /** 弹窗标题 */
    title?: string
  }>(),
  { title: '从素材库选择封面' },
)

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'select', material: MaterialVO): void
}>()

const visible = computed({
  get: () => props.open,
  set: (value: boolean) => emit('update:open', value),
})

const materials = ref<MaterialVO[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const PAGE_SIZE = 12
const uploading = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)

async function load(targetPage = page.value) {
  loading.value = true
  try {
    const res = await listMyMaterials({ pageNum: targetPage, pageSize: PAGE_SIZE })
    materials.value = res.data?.records ?? []
    total.value = Number(res.data?.totalRow ?? 0)
    page.value = targetPage
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

watch(
  () => props.open,
  (open) => {
    if (open) load(1)
  },
)

function choose(material: MaterialVO) {
  emit('select', material)
  visible.value = false
  message.success('已选择该图片')
}

function triggerUpload() {
  fileInputRef.value?.click()
}

async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  const error = validateImageFile(file)
  if (error) {
    message.warning(error)
    return
  }
  uploading.value = true
  try {
    const res = await uploadMaterial(file)
    if (res.data) {
      choose(res.data)
    }
  } catch {
    // 拦截器已提示
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <a-modal v-model:open="visible" :title="title" width="820px" :footer="null">
    <div class="picker-toolbar">
      <span class="picker-tip">只显示你上传的图片，选中后自动填入封面</span>
      <a-button :loading="uploading" @click="triggerUpload">
        <UploadOutlined v-if="!uploading" />
        上传新图片
      </a-button>
      <input
        ref="fileInputRef"
        type="file"
        accept="image/jpeg,image/png,image/gif,image/webp"
        class="hidden-input"
        @change="onFileChange"
      />
    </div>

    <a-spin :spinning="loading">
      <div v-if="materials.length" class="picker-grid">
        <div
          v-for="item in materials"
          :key="item.id"
          class="picker-item"
          @click="choose(item)"
        >
          <img :src="item.url" :alt="item.originalName || '素材'" class="picker-img" />
          <div class="picker-meta">
            <span class="picker-name" :title="item.originalName">{{ item.originalName || '未命名' }}</span>
            <span class="picker-size">{{ formatFileSize(item.size) }}</span>
          </div>
          <span class="picker-time">{{ formatTime(item.createTime) }}</span>
        </div>
      </div>

      <a-empty v-else-if="!loading" description="素材库还是空的，先上传一张图片吧" class="picker-empty">
        <template #image>
          <div class="picker-empty-icon">
            <LoadingOutlined v-if="uploading" />
            <span v-else>🖼️</span>
          </div>
        </template>
        <a-button type="primary" ghost :loading="uploading" @click="triggerUpload">上传图片</a-button>
      </a-empty>
    </a-spin>

    <a-pagination
      v-if="total > PAGE_SIZE"
      v-model:current="page"
      :total="total"
      :page-size="PAGE_SIZE"
      class="picker-pagination"
      show-less-items
      @change="load"
    />
  </a-modal>
</template>

<style scoped>
.picker-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.picker-tip {
  color: #8a90a6;
  font-size: 13px;
}

.hidden-input {
  display: none;
}

.picker-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
}

.picker-item {
  position: relative;
  border: 1px solid #eef0f6;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  background: #fff;
  transition: all 0.18s;
}

.picker-item:hover {
  border-color: #818cf8;
  box-shadow: 0 8px 20px rgba(79, 70, 229, 0.14);
  transform: translateY(-2px);
}

.picker-img {
  width: 100%;
  height: 110px;
  object-fit: cover;
  display: block;
  background: #f4f6fb;
}

.picker-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 6px 8px 0;
}

.picker-name {
  font-size: 12px;
  color: #4b5563;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.picker-size {
  font-size: 11px;
  color: #a0a6b8;
  flex-shrink: 0;
}

.picker-time {
  display: block;
  padding: 2px 8px 8px;
  font-size: 11px;
  color: #b6bccd;
}

.picker-empty {
  padding: 36px 0 12px;
}

.picker-empty-icon {
  font-size: 42px;
}

.picker-pagination {
  margin-top: 16px;
  text-align: center;
}
</style>
