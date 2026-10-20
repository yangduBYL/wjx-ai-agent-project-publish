<script setup lang="ts">
/**
 * 图片封面选择器（发布 / 编辑帖子共用）：
 * 支持「上传本地图片（自动存入素材库）」「从素材库选择」「粘贴图片链接」三种方式，
 * 并实时预览封面。v-model 绑定封面 URL。
 */
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { PictureOutlined, PlusOutlined, UploadOutlined } from '@ant-design/icons-vue'
import { uploadMaterial } from '@/api/materialController'
import { validateImageFile } from '@/utils/material'
import MaterialPickerModal from '@/components/MaterialPickerModal.vue'
import type { MaterialVO } from '@/types/material'

const coverUrl = defineModel<string>({ default: '' })

const uploading = ref(false)
const pickerOpen = ref(false)
const urlInput = ref('')
const fileInputRef = ref<HTMLInputElement | null>(null)

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
    if (res.data?.url) {
      coverUrl.value = res.data.url
      message.success('封面已上传，并存入素材库')
    }
  } catch {
    // 拦截器已提示
  } finally {
    uploading.value = false
  }
}

function onPick(material: MaterialVO) {
  coverUrl.value = material.url
}

function applyUrl() {
  const value = urlInput.value.trim()
  if (!value) return
  if (!/^(https?:\/\/|\/\/|\/)/i.test(value)) {
    message.warning('请填写以 http(s):// 或 / 开头的图片地址')
    return
  }
  coverUrl.value = value
  urlInput.value = ''
}

function clearCover() {
  coverUrl.value = ''
}
</script>

<template>
  <div class="cover-picker">
    <!-- 预览 / 空态 -->
    <div v-if="coverUrl" class="cover-preview">
      <img :src="coverUrl" alt="封面预览" class="cover-img" />
      <div class="cover-mask">
        <a-button size="small" :loading="uploading" @click="triggerUpload">更换</a-button>
        <a-button size="small" danger @click="clearCover">移除</a-button>
      </div>
    </div>
    <div v-else class="cover-empty" :class="{ loading: uploading }" @click="triggerUpload">
      <PlusOutlined v-if="!uploading" class="empty-icon" />
      <span>{{ uploading ? '上传中…' : '点击上传封面图' }}</span>
      <small>支持 JPG / PNG / GIF / WebP，最大 10MB</small>
    </div>

    <!-- 操作区 -->
    <div class="cover-tools">
      <a-button size="small" :loading="uploading" @click="triggerUpload">
        <UploadOutlined v-if="!uploading" />
        上传本地图片
      </a-button>
      <a-button size="small" @click="pickerOpen = true">
        <PictureOutlined />
        从素材库选择
      </a-button>
      <a-input
        v-model:value="urlInput"
        size="small"
        class="cover-url"
        placeholder="或粘贴图片链接后回车"
        @press-enter="applyUrl"
        @blur="applyUrl"
      />
    </div>

    <MaterialPickerModal v-model:open="pickerOpen" @select="onPick" />

    <input
      ref="fileInputRef"
      type="file"
      accept="image/jpeg,image/png,image/gif,image/webp"
      class="hidden-input"
      @change="onFileChange"
    />
  </div>
</template>

<style scoped>
.cover-picker {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cover-preview {
  position: relative;
  width: 100%;
  height: 150px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #eef0f6;
  background: #f4f6fb;
}

.cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.cover-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: rgba(15, 18, 40, 0.45);
  opacity: 0;
  transition: opacity 0.2s;
}

.cover-preview:hover .cover-mask {
  opacity: 1;
}

.cover-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 130px;
  border: 1px dashed #d8ddf0;
  border-radius: 12px;
  background: #fafbff;
  color: #8a90a6;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.cover-empty:hover {
  border-color: #818cf8;
  background: #f5f6ff;
  color: #4f46e5;
}

.cover-empty.loading {
  cursor: progress;
  color: #6366f1;
}

.empty-icon {
  font-size: 22px;
}

.cover-empty small {
  color: #b0b6c8;
  font-size: 11.5px;
}

.cover-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.cover-url {
  flex: 1;
  min-width: 180px;
}

.hidden-input {
  display: none;
}
</style>
