/**
 * 素材库接口（对应后端 /material/**）。
 *
 * 上传使用 multipart/form-data：刻意不手写 Content-Type，
 * 交给浏览器/axios 自动生成带 boundary 的请求头，否则后端解析不到文件。
 */
import request from '@/request'
import type { MaterialPage, MaterialQuery, MaterialResponse, MaterialVO } from '@/types/material'

/** 上传图片（自动存入素材库） */
export async function uploadMaterial(file: File, options?: { [key: string]: any }) {
  const formData = new FormData()
  formData.append('file', file)
  return request<MaterialResponse<MaterialVO>>('/material/upload', {
    method: 'POST',
    data: formData,
    ...(options || {}),
  })
}

/** 我的素材分页列表 */
export async function listMyMaterials(body: MaterialQuery, options?: { [key: string]: any }) {
  return request<MaterialResponse<MaterialPage>>('/material/list/page/mine', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: body,
    ...(options || {}),
  })
}

/** 删除素材（被帖子当封面时后端会拒绝） */
export async function deleteMaterial(id: string | number, options?: { [key: string]: any }) {
  return request<MaterialResponse<boolean>>('/material/delete', {
    method: 'DELETE',
    headers: { 'Content-Type': 'application/json' },
    data: { id },
    ...(options || {}),
  })
}
