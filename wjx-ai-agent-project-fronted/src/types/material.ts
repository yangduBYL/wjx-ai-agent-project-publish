/**
 * 素材库类型定义。
 *
 * 手写（对应后端 /material/**，未走 openapi2ts 生成）；ID/文件大小等 Long 字段
 * 后端会序列化成字符串，这里统一 string | number。
 */

/** 素材 */
export interface MaterialVO {
  id: string | number
  /** 访问地址，可直接用作 img src / 社区帖子封面 */
  url: string
  /** 原始文件名 */
  originalName?: string
  /** MIME 类型 */
  contentType?: string
  /** 文件大小（字节，字符串形式） */
  size?: string | number
  /** 上传时间 */
  createTime?: string
}

/** 素材分页信封（MyBatis-Flex Page 字段名） */
export interface MaterialPage {
  records: MaterialVO[]
  totalRow: string | number
  pageNumber: string | number
  pageSize: string | number
  totalPage?: string | number
}

/** 后端统一响应信封 */
export interface MaterialResponse<T> {
  code: number
  data: T
  message: string
}

/** 素材分页查询参数 */
export interface MaterialQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
  userId?: string | number
}
