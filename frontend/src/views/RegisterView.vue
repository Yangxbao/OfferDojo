<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', nickname: '', password: '', confirm: '' })

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z0-9_]{3,20}$/,
      message: '字母、数字或下划线，长度 3-20',
      trigger: 'blur',
    },
  ],
  nickname: [{ max: 20, message: '昵称最长 20 个字符', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度 6-64 位', trigger: 'blur' },
  ],
  confirm: [
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

async function submit() {
  await formRef.value?.validate().catch(() => Promise.reject(new Error('校验失败')))
  loading.value = true
  try {
    await auth.register(form.username, form.password, form.nickname)
    ElMessage.success('注册成功，已自动登录')
    router.push('/')
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
        <h1 class="auth-title">创建账号</h1>
        <p class="auth-sub">注册后即可开始模拟任务练习</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名（字母/数字/下划线）" :prefix-icon="'User'" />
        </el-form-item>
        <el-form-item prop="nickname">
          <el-input v-model="form.nickname" placeholder="昵称（选填）" :prefix-icon="'Postcard'" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码（至少 6 位）" show-password :prefix-icon="'Lock'" />
        </el-form-item>
        <el-form-item prop="confirm">
          <el-input v-model="form.confirm" type="password" placeholder="确认密码" show-password :prefix-icon="'Lock'" />
        </el-form-item>
        <el-button type="primary" style="width: 100%" :loading="loading" @click="submit">
          {{ loading ? '注册中…' : '注册并登录' }}
        </el-button>
      </el-form>

      <div style="margin-top: 16px; text-align: center">
        <router-link to="/login">已有账号？去登录</router-link>
      </div>
    </div>
  </div>
</template>
