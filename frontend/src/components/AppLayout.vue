<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { CATEGORY_LABELS } from '@/utils/format'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const menus = computed(() => {
  const base = [
    { path: '/', title: '工作台', icon: 'Odometer' },
    { path: '/roadmap', title: '学习路线图', icon: 'Guide' },
    { path: '/tasks', title: '任务中心', icon: 'Files' },
    { path: '/bagu', title: '八股复习', icon: 'Reading' },
    { path: '/resources', title: '资源中心', icon: 'Collection' },
    { path: '/tracks', title: '成长路线', icon: 'Guide' },
    { path: '/github-top', title: 'GitHub 榜单', icon: 'Trophy' },
    { path: '/projects', title: '项目广场', icon: 'FolderOpened' },
    { path: '/community', title: '社区题库', icon: 'EditPen' },
    { path: '/records', title: '练习记录', icon: 'Clock' },
    { path: '/analysis', title: '能力分析', icon: 'TrendCharts' },
  ]
  if (auth.isAdmin) {
    base.push(
      { path: '/admin', title: '后台总览', icon: 'DataAnalysis' },
      { path: '/admin/tasks', title: '任务管理', icon: 'Setting' },
    )
  }
  return base
})

const activePath = computed(() => '/' + (route.path.split('/')[1] ?? ''))

const mobileMenuOpen = ref(false)

function handleCommand(command: string) {
  if (command === 'logout') {
    auth.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}

const currentGroupTitle = computed(() => CATEGORY_LABELS[route.params.category as string] ?? '')
</script>

<template>
  <div class="layout">
    <aside class="layout-sidebar">
      <div class="brand">
        <div class="brand-logo">J</div>
        <div class="brand-name">
          OfferDojo
          <small>AI 岗位实战模拟</small>
        </div>
      </div>
      <el-menu class="sidebar-menu" :default-active="activePath" router background-color="transparent">
        <el-menu-item v-for="menu in menus" :key="menu.path" :index="menu.path" class="sidebar-item">
          <el-icon><component :is="menu.icon" /></el-icon>
          <span>{{ menu.title }}</span>
        </el-menu-item>
      </el-menu>
      <div class="sidebar-footer">面向大学生的实战练习平台</div>
    </aside>

    <div class="layout-main">
      <header class="layout-topbar">
        <div style="display: flex; align-items: center; gap: 10px">
          <el-button class="mobile-only" text @click="mobileMenuOpen = true">
            <el-icon><Menu /></el-icon>
          </el-button>
          <strong style="font-size: 15px">{{ route.meta.title }}</strong>
          <span v-if="currentGroupTitle" class="card-sub">{{ currentGroupTitle }}</span>
        </div>
        <el-dropdown @command="handleCommand">
          <span style="display: flex; align-items: center; gap: 8px; cursor: pointer">
            <el-avatar :size="30" style="background: var(--jobsim-primary)">
              {{ (auth.user?.nickname || auth.user?.username || '?').slice(0, 1) }}
            </el-avatar>
            <span>{{ auth.user?.nickname || auth.user?.username }}</span>
            <el-tag v-if="auth.isAdmin" size="small" type="warning" effect="light">管理员</el-tag>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>{{ auth.user?.username }}</el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </header>

      <main class="layout-content">
        <router-view />
      </main>
    </div>

    <!-- 移动端抽屉导航 -->
    <el-drawer v-model="mobileMenuOpen" direction="ltr" size="220px" :with-header="false">
      <div class="brand" style="padding-left: 0">
        <div class="brand-logo">J</div>
        <div class="brand-name" style="color: var(--jobsim-text)">
          OfferDojo
          <small style="color: var(--jobsim-text-secondary)">AI 岗位实战模拟</small>
        </div>
      </div>
      <el-menu :default-active="activePath" router @select="mobileMenuOpen = false">
        <el-menu-item v-for="menu in menus" :key="menu.path" :index="menu.path">
          <el-icon><component :is="menu.icon" /></el-icon>
          <span>{{ menu.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-drawer>
  </div>
</template>

<style scoped>
.sidebar-item {
  color: var(--jobsim-sidebar-text) !important;
  margin: 4px 10px;
  border-radius: 8px;
}

.sidebar-item.is-active {
  background: rgba(255, 255, 255, 0.1) !important;
  color: var(--jobsim-sidebar-active) !important;
}

.sidebar-item:hover {
  background: rgba(255, 255, 255, 0.06) !important;
  color: #fff !important;
}

.mobile-only {
  display: none;
}

@media (max-width: 720px) {
  .mobile-only {
    display: inline-flex;
  }
}
</style>
