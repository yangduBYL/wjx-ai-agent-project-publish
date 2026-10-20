import axios from 'axios'
import type { AxiosRequestConfig } from 'axios'
import { message } from 'ant-design-vue'

/**
 * 后端统一响应结构（对应 BaseResponse）
 */
export interface BaseResponse<T = unknown> {
  code: number
  data: T
  message: string
}

/** 未登录错误码，与后端 ErrorCode.NOT_LOGIN_ERROR 一致 */
const NOT_LOGIN_CODE = 40100

/** 成功状态码 */
const SUCCESS_CODE = 0

/**
 * 后端接口基础路径：
 * 取环境变量 VITE_API_BASE_URL（本地/线上统一为相对路径 /api），
 * 由 Vite 代理（开发）或 Nginx 反代（线上）转发到真正的后端服务，
 * 这样接口请求与页面同源，同时保证 AI 生成网站的预览 iframe 也与主页面同源。
 */
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

const myAxios = axios.create({
  // 全局接口请求地址（相对路径 + 代理转发，见 vite.config.ts）
  baseURL: API_BASE_URL,
  // 超时时间（毫秒）
  timeout: 60000,
  // 必须开启，否则请求无法携带 Cookie，无法完成登录态校验
  withCredentials: true,
})

// 请求拦截器
myAxios.interceptors.request.use(
  (config) => {
    return config
  },
  (error) => {
    return Promise.reject(error)
  },
)

// 响应拦截器：按 code 集中处理错误
// 常规接口返回 BaseResponse 信封（含 code/data/message），成功时直接返回完整信封，
// 与 api 目录下生成代码的泛型保持一致，调用方通过 res.data 获取业务数据；
// 个别接口（如 /app/getAppVoId）后端直接返回实体，无信封结构，则原样透传
myAxios.interceptors.response.use(
  (response) => {
    // 二进制响应（如「下载应用代码」返回的 zip）：没有 BaseResponse 信封，
    // 直接返回完整响应对象，调用方才能拿到 data（Blob）以及
    // Content-Disposition 等响应头（下载文件名就在这里）
    if (response.config?.responseType === 'blob') {
      return response as never
    }

    const res = response.data as BaseResponse | Record<string, unknown>

    // 无 code 字段：非信封结构（后端直接返回的实体），原样返回
    if (!res || typeof res !== 'object' || !('code' in res)) {
      return response.data as never
    }

    const envelope = res as BaseResponse

    // 未登录：跳转登录页（避免在登录相关接口上重复跳转）
    if (envelope.code === NOT_LOGIN_CODE) {
      const requestUrl = String(response.config?.url ?? '')
      const isLoginRelated =
        requestUrl.includes('/user/login') ||
        requestUrl.includes('/user/get/login') ||
        window.location.pathname.includes('/user/login')

      if (!isLoginRelated) {
        message.warning('请先登录')
        const redirect = encodeURIComponent(window.location.href)
        window.location.href = `/user/login?redirect=${redirect}`
      }
      return Promise.reject(envelope)
    }

    // 业务失败
    if (envelope.code !== SUCCESS_CODE) {
      message.error(envelope.message || '请求失败')
      return Promise.reject(envelope)
    }

    // 成功：返回完整响应体
    return response.data as never
  },
  (error) => {
    const errMsg = error.response?.data?.message || error.message || '网络异常，请稍后重试'
    message.error(errMsg)
    return Promise.reject(error)
  },
)

/**
 * 通用请求方法：
 * 泛型 T 对应后端 BaseResponse<T> 的完整信封类型（与 @umijs/openapi 生成的代码一致），
 * 返回值类型为 Promise<BaseResponse<T>>，业务数据通过 res.data 获取。
 */
const request = <T = unknown>(url: string, config: AxiosRequestConfig = {}): Promise<T> => {
  return myAxios.request({ ...config, url }) as Promise<T>
}

export default request
