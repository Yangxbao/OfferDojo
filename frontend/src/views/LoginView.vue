<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

/** 一键填入演示账号，方便快速体验完整流程 */
function fillDemo(role: 'student' | 'admin') {
  if (role === 'admin') {
    form.username = 'admin'
    form.password = 'admin123'
  } else {
    form.username = 'student'
    form.password = 'student123'
  }
  ElMessage.info('已填入演示账号，点击登录即可')
}

async function submit() {
  await formRef.value?.validate().catch(() => Promise.reject(new Error('校验失败')))
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    ElMessage.success('登录成功')
    router.push((route.query.redirect as string) || '/')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card card">
      <div class="auth-brand">
        <div class="brand-logo">J</div>
        <h1 class="auth-title">OfferDojo</h1>
        <p class="auth-sub">面向大学生的 AI 岗位实战模拟平台</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="'User'" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" show-password :prefix-icon="'Lock'" />
        </el-form-item>
        <el-button type="primary" style="width: 100%" :loading="loading" @click="submit">
          {{ loading ? '登录中…' : '登录' }}
        </el-button>
      </el-form>

      <div style="margin-top: 16px; display: flex; justify-content: space-between; align-items: center">
        <router-link to="/register">注册新账号</router-link>
        <span class="card-sub">
          演示：
          <el-link type="primary" style="font-size: 12px" @click="fillDemo('student')">学生账号</el-link>
          ·
          <el-link type="warning" style="font-size: 12px" @click="fillDemo('admin')">管理员</el-link>
        </span>
      </div>
    </div>
  </div>
</template>
