<script setup lang="ts">
/**
 * 素材库：用户上传的图片统一管理。
 * 参考个人博客系统 blog-server 的 media 表 + /admin/upload 接口改写：
 * 这里按用户隔离、支持分页/搜索/删除，并可直接把素材用作社区帖子封面。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  CloudUploadOutlined,
  CopyOutlined,
  DeleteOutlined,
  LinkOutlined,
  SearchOutlined,
  UploadOutlined,
} from '@ant-design/icons-vue'
import { deleteMaterial, listMyMaterials, uploadMaterial } from '@/api/materialController'
import { formatFileSize, validateImageFile } from '@/utils/material'
import { formatTime, isValidImageUrl } from '@/utils/app'
import type { MaterialVO } from '@/types/material'

const router = useRouter()

const materials = ref<MaterialVO[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const PAGE_SIZE = 12
const keyword = ref('')
const uploading = ref(false)
const uploadingName = ref('')
const dragging = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)

const isEmpty = computed(() => !loading.value && materials.value.length === 0)

async function load(targetPage = page.value) {
  loading.value = true
  try {
    const res = await listMyMaterials({
      pageNum: targetPage,
      pageSize: PAGE_SIZE,
      keyword: keyword.value.trim() || undefined,
    })
    materials.value = res.data?.records ?? []
    total.value = Number(res.data?.totalRow ?? 0)
    page.value = targetPage
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function onSearch() {
  load(1)
}

/* ---------------- 上传 ---------------- */
function triggerUpload() {
  fileInputRef.value?.click()
}

async function uploadFiles(files: File[]) {
  if (!files.length) return
  const valid = files.filter((file) => {
    const error = validateImageFile(file)
    if (error) message.warning(`${file.name}：${error}`)
    return !error
  })
  if (!valid.length) return

  uploading.value = true
  let success = 0
  try {
    for (const file of valid) {
      uploadingName.value = file.name
      try {
        await uploadMaterial(file)
        success += 1
      } catch {
        // 单张失败不阻断其它图片，拦截器已提示
      }
    }
    if (success > 0) {
      message.success(`已上传 ${success} 张图片`)
      await load(1)
    }
  } finally {
    uploading.value = false
    uploadingName.value = ''
  }
}

async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  await uploadFiles(files)
}

function onDrop(event: DragEvent) {
  dragging.value = false
  const files = Array.from(event.dataTransfer?.files ?? [])
  uploadFiles(files)
}

function onDragOver() {
  dragging.value = true
}

function onDragLeave() {
  dragging.value = false
}

/* ---------------- 素材操作 ---------------- */
async function copyUrl(item: MaterialVO) {
  try {
    await navigator.clipboard.writeText(item.url)
    message.success('图片链接已复制')
  } catch {
    // 剪贴板 API 在非 https / 旧浏览器不可用，降级到 execCommand
    const textarea = document.createElement('textarea')
    textarea.value = item.url
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    try {
      document.execCommand('copy')
      message.success('图片链接已复制')
    } catch {
      message.warning('复制失败，请手动选择链接')
    } finally {
      document.body.removeChild(textarea)
    }
  }
}

function useAsCover(item: MaterialVO) {
  router.push({ path: '/community', query: { cover: item.url } })
}

async function removeMaterial(item: MaterialVO) {
  try {
    await deleteMaterial(item.id)
    message.success('素材已删除')
    await load(page.value)
  } catch {
    // 被帖子引用时后端会返回具体原因，拦截器已提示
  }
}

onMounted(() => load(1))
</script>

<template>
  <div class="material-page">
    <!-- 顶部 -->
    <section class="material-hero">
      <div class="hero-left">
        <h1 class="hero-title">素材库</h1>
        <p class="hero-subtitle">
          上传的图片都会保存在这里，可以直接复制链接使用，或一键设为社区帖子的封面
        </p>
      </div>
      <a-button type="primary" class="upload-btn" size="large" @click="triggerUpload">
        <UploadOutlined />
        上传图片
      </a-button>
      <input
        ref="fileInputRef"
        type="file"
        accept="image/jpeg,image/png,image/gif,image/webp"
        multiple
        class="hidden-input"
        @change="onFileChange"
      />
    </section>

    <!-- 拖拽上传区 + 搜索 -->
    <section class="material-toolbar">
      <div
        class="drop-zone"
        :class="{ dragging, uploading }"
        @click="triggerUpload"
        @dragover.prevent="onDragOver"
        @dragleave.prevent="onDragLeave"
        @drop.prevent="onDrop"
      >
        <CloudUploadOutlined class="drop-icon" />
        <div class="drop-text">
          <span v-if="uploading">正在上传：{{ uploadingName || '图片' }}…</span>
          <span v-else>点击或拖拽图片到这里上传</span>
          <small>支持 JPG / PNG / GIF / WebP，单张最大 10MB，可多选</small>
        </div>
      </div>

      <div class="search-row">
        <a-input
          v-model:value="keyword"
          class="search-input"
          placeholder="搜索文件名"
          allow-clear
          @press-enter="onSearch"
        >
          <template #prefix>
            <SearchOutlined />
          </template>
        </a-input>
        <a-button @click="onSearch">搜索</a-button>
        <span class="count-tip">共 {{ total }} 张</span>
      </div>
    </section>

    <!-- 素材网格 -->
    <a-spin :spinning="loading">
      <div v-if="materials.length" class="material-grid">
        <div v-for="item in materials" :key="item.id" class="material-card">
          <div class="card-cover">
            <a-image v-if="isValidImageUrl(item.url)" :src="item.url" :alt="item.originalName" class="card-img" />
            <div v-else class="card-img fallback">图片地址无效</div>
            <div class="card-mask">
              <a-button size="small" class="mask-btn" @click.stop="copyUrl(item)">
                <CopyOutlined />
                复制链接
              </a-button>
              <a-button size="small" type="primary" class="mask-btn primary" @click.stop="useAsCover(item)">
                <LinkOutlined />
                用作封面
              </a-button>
            </div>
          </div>
          <div class="card-meta">
            <span class="card-name" :title="item.originalName">{{ item.originalName || '未命名图片' }}</span>
            <a-popconfirm
              title="删除后不可恢复，确定删除这张素材吗？"
              ok-text="删除"
              cancel-text="取消"
              @confirm="removeMaterial(item)"
            >
              <a-button type="text" size="small" danger class="del-btn">
                <DeleteOutlined />
              </a-button>
            </a-popconfirm>
          </div>
          <div class="card-sub">
            <span>{{ formatFileSize(item.size) }}</span>
            <span>{{ formatTime(item.createTime) }}</span>
          </div>
        </div>
      </div>

      <a-empty v-else-if="isEmpty" class="empty-box">
        <template #image>
          <div class="empty-icon">🖼️</div>
        </template>
        <template #description>
          <span v-if="keyword">没有找到文件名为「{{ keyword }}」的素材</span>
          <span v-else>素材库还是空的，上传第一张图片吧</span>
        </template>
        <a-button type="primary" ghost @click="triggerUpload">上传图片</a-button>
      </a-empty>
    </a-spin>

    <a-pagination
      v-if="total > PAGE_SIZE"
      v-model:current="page"
      :total="total"
      :page-size="PAGE_SIZE"
      class="material-pagination"
      show-less-items
      @change="load"
    />
  </div>
</template>

<style scoped>
.material-page {
  max-width: 1200px;
  margin: 0 auto;
}

/* ---------- 顶部 ---------- */
.material-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  padding: 20px 26px;
  background: #fff;
  border: 1px solid #eef0f6;
  border-radius: 18px;
  box-shadow: 0 6px 22px rgba(30, 41, 82, 0.06);
}

.hero-title {
  margin: 0;
  font-size: 28px;
  font-weight: 800;
  color: #1f2430;
}

.hero-subtitle {
  margin: 8px 0 0;
  color: #8a90a6;
  font-size: 14px;
  line-height: 1.7;
  max-width: 640px;
}

.upload-btn {
  flex-shrink: 0;
  height: 42px;
  padding: 0 24px;
  border: none;
  border-radius: 999px;
  background: linear-gradient(90deg, #4f46e5, #7c3aed);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  box-shadow: 0 8px 20px rgba(99, 102, 241, 0.3);
}

.upload-btn:hover {
  background: linear-gradient(90deg, #4338ca, #6d28d9) !important;
}

.hidden-input {
  display: none;
}

/* ---------- 拖拽区 ---------- */
.material-toolbar {
  margin: 18px 0 20px;
  padding: 16px 18px;
  background: #fff;
  border: 1px solid #eef0f6;
  border-radius: 16px;
}

.drop-zone {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  border: 1px dashed #d8ddf0;
  border-radius: 14px;
  background: #fafbff;
  cursor: pointer;
  transition: all 0.2s;
}

.drop-zone:hover,
.drop-zone.dragging {
  border-color: #818cf8;
  background: #f3f4ff;
}

.drop-zone.uploading {
  cursor: progress;
  border-color: #a5b4fc;
}

.drop-icon {
  font-size: 30px;
  color: #818cf8;
}

.drop-text {
  display: flex;
  flex-direction: column;
  gap: 3px;
  color: #4b5563;
  font-size: 14px;
}

.drop-text small {
  color: #a0a6b8;
  font-size: 12px;
}

.search-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 14px;
}

.search-input {
  width: 260px;
  border-radius: 999px;
}

.count-tip {
  margin-left: auto;
  color: #98a0b3;
  font-size: 13px;
}

/* ---------- 网格 ---------- */
.material-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

.material-card {
  background: #fff;
  border: 1px solid #eef0f6;
  border-radius: 14px;
  overflow: hidden;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    border-color 0.2s ease;
}

.material-card:hover {
  transform: translateY(-3px);
  border-color: #d8ddf0;
  box-shadow: 0 12px 26px rgba(30, 41, 82, 0.12);
}

.card-cover {
  position: relative;
  height: 140px;
  background: #f4f6fb;
  overflow: hidden;
}

.card-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.card-img :deep(img) {
  width: 100%;
  height: 140px;
  object-fit: cover;
  display: block;
}

.card-img.fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #b0b6c8;
  font-size: 13px;
}

.card-mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: rgba(15, 18, 40, 0.5);
  opacity: 0;
  transition: opacity 0.2s;
  /* 只让按钮可点，其余区域穿透到下层 a-image，点击图片即可放大预览 */
  pointer-events: none;
}

.material-card:hover .card-mask {
  opacity: 1;
}

.mask-btn {
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  width: 112px;
  justify-content: center;
  pointer-events: auto;
}

.mask-btn.primary {
  border: none;
  background: linear-gradient(90deg, #4f46e5, #7c3aed);
}

.card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 8px 10px 0;
}

.card-name {
  font-size: 13px;
  color: #374151;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.del-btn {
  flex-shrink: 0;
  padding: 0 4px;
  color: #b0b6c8;
}

.card-sub {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 2px 10px 10px;
  color: #a0a6b8;
  font-size: 11.5px;
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

.material-pagination {
  margin-top: 22px;
  text-align: center;
}

@media (max-width: 720px) {
  .material-hero {
    flex-direction: column;
    align-items: flex-start;
  }

  .search-input {
    width: 100%;
  }
}
</style>
