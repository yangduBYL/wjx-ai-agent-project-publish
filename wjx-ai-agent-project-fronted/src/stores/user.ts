import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getLoginUser, login as loginApi, loginOut, register as registerApi } from '@/api/userController'

/** 用户角色常量（与后端 UserConstant 保持一致） */
export const USER_ROLE = {
  DEFAULT: 'user',
  ADMIN: 'admin',
} as const

/**
 * 全局用户状态：登录态、当前用户信息
 */
export const useUserStore = defineStore('user', () => {
  // 当前登录用户（null 表示未登录）
  const currentUser = ref<API.User | null>(null)
  // 是否已尝试过获取登录用户（避免每次路由跳转都重复请求）
  const loaded = ref(false)

  /** 是否已登录 */
  const isLogin = () => !!currentUser.value

  /** 是否管理员 */
  const isAdmin = () => currentUser.value?.userRole === USER_ROLE.ADMIN

  /** 拉取当前登录用户（会话由后端 Cookie 维护） */
  async function fetchLoginUser() {
    try {
      const res = await getLoginUser()
      currentUser.value = res?.data ?? null
    } catch {
      currentUser.value = null
    } finally {
      loaded.value = true
    }
  }

  /** 登录 */
  async function login(loginForm: API.UserLoginRequest) {
    const res = await loginApi(loginForm)
    if (res?.data) {
      const data = res.data
      currentUser.value = {
        id: data.id,
        userAccount: data.userAccount,
        userName: data.userName,
        userAvatar: data.userAvatar,
        userRole: data.userRole,
        userProfile: data.userProfile != null ? String(data.userProfile) : '',
        createTime: data.createTime,
        updateTime: data.updateTime,
      } as API.User
    }
    return res
  }

  /** 注册 */
  async function register(registerForm: API.UserRegisterRequest) {
    return registerApi(registerForm)
  }

  /** 退出登录 */
  async function logout() {
    try {
      await loginOut()
    } finally {
      currentUser.value = null
      loaded.value = true
    }
  }

  return {
    currentUser,
    loaded,
    isLogin,
    isAdmin,
    fetchLoginUser,
    login,
    register,
    logout,
  }
})
