import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import HomeLayout from '@/layouts/HomeLayout.vue'
import HomeView from '@/pages/HomeView.vue'
import ProjectView from '@/pages/ProjectView.vue'
import SkillCenterView from '@/pages/SkillCenterView.vue'
import MaterialView from '@/pages/MaterialView.vue'
import CommunityView from '@/pages/CommunityView.vue'
import AppGenerateView from '@/pages/AppGenerateView.vue'
import AppManageView from '@/pages/AppManageView.vue'
import ChatManageView from '@/pages/ChatManageView.vue'
import AppEditView from '@/pages/AppEditView.vue'
import UserLoginView from '@/pages/UserLoginView.vue'
import UserRegisterView from '@/pages/UserRegisterView.vue'
import UserCenterView from '@/pages/UserCenterView.vue'
import UserManageView from '@/pages/UserManageView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: HomeLayout,
      children: [
        {
          path: '',
          name: 'home',
          component: HomeView,
          meta: { title: '首页' },
        },
        {
          path: 'project',
          name: 'project',
          component: ProjectView,
          meta: { title: '项目', requiresAuth: true },
        },
        {
          path: 'skill',
          name: 'skill',
          component: SkillCenterView,
          meta: { title: '技能中心' },
        },
        {
          path: 'material',
          name: 'material',
          component: MaterialView,
          meta: { title: '素材库' },
        },
        {
          path: 'community',
          name: 'community',
          component: CommunityView,
          meta: { title: '社区' },
        },
        {
          path: 'community/:id',
          name: 'communityDetail',
          component: () => import('@/pages/CommunityDetailView.vue'),
          meta: { title: '帖子详情' },
        },
        {
          path: 'app/manage',
          name: 'appManage',
          component: AppManageView,
          meta: { title: '应用管理', requiresAuth: true, requiresAdmin: true },
        },
        {
          path: 'chat/manage',
          name: 'chatManage',
          component: ChatManageView,
          meta: { title: '对话管理', requiresAuth: true, requiresAdmin: true },
        },
        {
          path: 'app/edit/:id',
          name: 'appEdit',
          component: AppEditView,
          meta: { title: '应用信息修改', requiresAuth: true },
        },
        {
          path: 'user/center',
          name: 'userCenter',
          component: UserCenterView,
          meta: { title: '用户中心', requiresAuth: true },
        },
        {
          path: 'user/manage',
          name: 'userManage',
          component: UserManageView,
          meta: { title: '用户管理', requiresAuth: true, requiresAdmin: true },
        },
      ],
    },
    {
      path: '/app/generate/:appId',
      name: 'appGenerate',
      component: AppGenerateView,
      meta: { title: 'AI 对话生成', requiresAuth: true },
    },
    {
      path: '/user/login',
      name: 'userLogin',
      component: UserLoginView,
      meta: { title: '登录' },
    },
    {
      path: '/user/register',
      name: 'userRegister',
      component: UserRegisterView,
      meta: { title: '注册' },
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/',
    },
  ],
})

// 全局前置守卫：登录态 + 权限控制
router.beforeEach(async (to) => {
  const userStore = useUserStore()

  // 首次进入时拉取登录用户（Cookie 会话）
  if (!userStore.loaded) {
    await userStore.fetchLoginUser()
  }

  // 更新页面标题
  if (to.meta?.title) {
    document.title = `${to.meta.title} · AI 应用生成器`
  }

  // 需要登录的页面
  if (to.meta?.requiresAuth && !userStore.isLogin()) {
    const redirect = encodeURIComponent(to.fullPath)
    return {
      path: '/user/login',
      query: { redirect },
    }
  }

  // 仅管理员可访问的页面
  if (to.meta?.requiresAdmin && !userStore.isAdmin()) {
    return { path: '/' }
  }

  return true
})

export default router
