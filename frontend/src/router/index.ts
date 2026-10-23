import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import AppLayout from '@/components/AppLayout.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/RegisterView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: AppLayout,
      children: [
        { path: '', name: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '工作台' } },
        { path: 'tasks', name: 'tasks', component: () => import('@/views/TaskListView.vue'), meta: { title: '任务中心' } },
        { path: 'tasks/:id', name: 'task-detail', component: () => import('@/views/TaskDetailView.vue'), meta: { title: '任务详情' } },
        { path: 'records', name: 'records', component: () => import('@/views/RecordsView.vue'), meta: { title: '练习记录' } },
        { path: 'records/:id', name: 'record-detail', component: () => import('@/views/RecordDetailView.vue'), meta: { title: '评估报告' } },
        { path: 'analysis', name: 'analysis', component: () => import('@/views/AnalysisView.vue'), meta: { title: '能力分析' } },
        { path: 'admin', name: 'admin-home', component: () => import('@/views/admin/AdminHomeView.vue'), meta: { title: '后台总览', requiresAdmin: true } },
        { path: 'admin/tasks', name: 'admin-tasks', component: () => import('@/views/admin/AdminTasksView.vue'), meta: { title: '任务管理', requiresAdmin: true } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin && !auth.isAdmin) {
    ElMessage.warning('该页面仅管理员可访问')
    return { name: 'dashboard' }
  }
  if (auth.isLoggedIn && (to.name === 'login' || to.name === 'register')) {
    return { name: 'dashboard' }
  }
  document.title = to.meta.title ? `${to.meta.title} · JobSim AI` : 'JobSim AI'
  return true
})

export default router
