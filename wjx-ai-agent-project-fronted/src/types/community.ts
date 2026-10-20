/**
 * 社区模块类型定义。
 *
 * 说明：本模块接口是手工新增的（后端 /community/**），未走 openapi2ts 重新生成，
 * 因此类型放在这里而不是 src/api/typings.d.ts 里，避免下次生成 api 时被覆盖。
 *
 * 注意：后端所有 Long（雪花 ID、计数型 Long）都经 Jackson 序列化成字符串，
 * 这里的 id 类型写成 string | number 只是为了兼容类型声明，运行时禁止 Number() 转换。
 */

/** 分页信封（MyBatis-Flex Page 的字段名） */
export interface CommunityPage<T> {
  records: T[]
  totalRow: string | number
  pageNumber: string | number
  pageSize: string | number
  totalPage?: string | number
}

/** 后端统一响应信封 */
export interface CommunityResponse<T> {
  code: number
  data: T
  message: string
}

/** 帖子列表项 */
export interface CommunityPostListVO {
  id: string | number
  title: string
  excerpt?: string
  cover?: string
  appId?: string | number | null
  appName?: string | null
  authorId?: string | number
  authorName?: string
  authorAvatar?: string
  status?: number
  viewCount?: number
  likeCount?: number
  commentCount?: number
  tags?: string[]
  liked?: boolean
  mine?: boolean
  createTime?: string
}

/** 帖子详情 */
export interface CommunityPostVO extends CommunityPostListVO {
  content: string
  appCover?: string | null
  appCodeGenType?: string | null
  appDeployKey?: string | null
  updateTime?: string
}

/** 评论（含二级回复） */
export interface CommunityCommentVO {
  id: string | number
  postId: string | number
  parentId?: string | number | null
  authorId?: string | number
  nickname?: string
  avatar?: string
  replyToName?: string | null
  content: string
  mine?: boolean
  createTime?: string
  replies?: CommunityCommentVO[]
}

/** 标签（带帖子数） */
export interface CommunityTagVO {
  id: string | number
  name: string
  postCount: string | number
}

/** 点赞结果 */
export interface CommunityLikeResultVO {
  liked: boolean
  likeCount: number
  message: string
}

/** 社区统计 */
export interface CommunityStatsVO {
  postTotal: string | number
  todayTotal: string | number
  commentTotal: string | number
  userTotal: string | number
}

/** 帖子分页查询参数 */
export interface CommunityPostQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
  tag?: string
  authorId?: string | number
  appId?: string | number
  status?: number
  onlyMine?: boolean
  sortField?: string
  sortOrder?: string
}

/** 发布帖子参数 */
export interface CommunityPostAddRequest {
  title: string
  content: string
  excerpt?: string
  cover?: string
  appId?: string | number | null
  tags?: string[]
  status?: number
}

/** 更新帖子参数（appId 传 0 表示解除关联） */
export interface CommunityPostUpdateRequest {
  id: string | number
  title?: string
  content?: string
  excerpt?: string
  cover?: string
  appId?: string | number | null
  tags?: string[]
  status?: number
}

/** 发表评论参数 */
export interface CommunityCommentAddRequest {
  postId: string | number
  parentId?: string | number | null
  content: string
}
