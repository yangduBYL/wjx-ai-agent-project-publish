/**
 * 社区模块接口（对应后端 /community/post、/community/comment、/community/tag）。
 *
 * 手写而非 openapi2ts 生成：新增接口尚未重新生成 api 层，
 * 这里沿用生成代码的调用方式（request 泛型 + BaseResponse 信封），
 * 返回值为完整信封，调用方通过 res.data 取业务数据。
 */
import request from '@/request'
import type {
  CommunityCommentAddRequest,
  CommunityCommentVO,
  CommunityLikeResultVO,
  CommunityPage,
  CommunityPostAddRequest,
  CommunityPostListVO,
  CommunityPostQuery,
  CommunityPostUpdateRequest,
  CommunityPostVO,
  CommunityResponse,
  CommunityStatsVO,
  CommunityTagVO,
} from '@/types/community'

/** 帖子分页列表（公开） */
export async function listCommunityPosts(body: CommunityPostQuery, options?: { [key: string]: any }) {
  return request<CommunityResponse<CommunityPage<CommunityPostListVO>>>('/community/post/list/page', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: body,
    ...(options || {}),
  })
}

/** 我的帖子分页列表（含草稿，需登录） */
export async function listMyCommunityPosts(body: CommunityPostQuery, options?: { [key: string]: any }) {
  return request<CommunityResponse<CommunityPage<CommunityPostListVO>>>('/community/post/list/page/mine', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: body,
    ...(options || {}),
  })
}

/** 帖子详情（已发布帖子会自增浏览数） */
export async function getCommunityPost(id: string | number, options?: { [key: string]: any }) {
  return request<CommunityResponse<CommunityPostVO>>(`/community/post/get/${id}`, {
    method: 'GET',
    ...(options || {}),
  })
}

/** 发布帖子 */
export async function addCommunityPost(body: CommunityPostAddRequest, options?: { [key: string]: any }) {
  return request<CommunityResponse<CommunityPostVO>>('/community/post/add', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: body,
    ...(options || {}),
  })
}

/** 更新帖子（作者或管理员） */
export async function updateCommunityPost(body: CommunityPostUpdateRequest, options?: { [key: string]: any }) {
  return request<CommunityResponse<CommunityPostVO>>('/community/post/update', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    data: body,
    ...(options || {}),
  })
}

/** 删除帖子（作者或管理员） */
export async function deleteCommunityPost(id: string | number, options?: { [key: string]: any }) {
  return request<CommunityResponse<boolean>>('/community/post/delete', {
    method: 'DELETE',
    headers: { 'Content-Type': 'application/json' },
    data: { id },
    ...(options || {}),
  })
}

/** 点赞 */
export async function likeCommunityPost(id: string | number, options?: { [key: string]: any }) {
  return request<CommunityResponse<CommunityLikeResultVO>>(`/community/post/${id}/like`, {
    method: 'POST',
    ...(options || {}),
  })
}

/** 取消点赞 */
export async function unlikeCommunityPost(id: string | number, options?: { [key: string]: any }) {
  return request<CommunityResponse<CommunityLikeResultVO>>(`/community/post/${id}/like`, {
    method: 'DELETE',
    ...(options || {}),
  })
}

/** 评论列表（两级盖楼） */
export async function listCommunityComments(postId: string | number, options?: { [key: string]: any }) {
  return request<CommunityResponse<CommunityCommentVO[]>>('/community/comment/list', {
    method: 'GET',
    params: { postId },
    ...(options || {}),
  })
}

/** 发表评论 / 回复 */
export async function addCommunityComment(body: CommunityCommentAddRequest, options?: { [key: string]: any }) {
  return request<CommunityResponse<CommunityCommentVO>>('/community/comment/add', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: body,
    ...(options || {}),
  })
}

/** 删除评论（评论作者或管理员） */
export async function deleteCommunityComment(id: string | number, options?: { [key: string]: any }) {
  return request<CommunityResponse<boolean>>('/community/comment/delete', {
    method: 'DELETE',
    headers: { 'Content-Type': 'application/json' },
    data: { id },
    ...(options || {}),
  })
}

/** 标签列表（带帖子数） */
export async function listCommunityTags(options?: { [key: string]: any }) {
  return request<CommunityResponse<CommunityTagVO[]>>('/community/tag/list', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 社区统计 */
export async function getCommunityStats(options?: { [key: string]: any }) {
  return request<CommunityResponse<CommunityStatsVO>>('/community/post/stats', {
    method: 'GET',
    ...(options || {}),
  })
}
