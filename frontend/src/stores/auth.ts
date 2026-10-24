import { defineStore } from 'pinia'
import { authApi } from '@/api'
import { clearToken, getToken, setToken } from '@/api/http'
import type { UserVO } from '@/api/types'

const USER_KEY = 'jobsim.user'

function loadStoredUser(): UserVO | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? (JSON.parse(raw) as UserVO) : null
  } catch {
    return null
  }
}

/**
 * 认证状态。
 * token 存 localStorage（简单直观；XSS 风险的讨论见 docs/LEARNING_GUIDE.md）。
 * 登录后调用 fetchMe() 从服务端刷新角色，避免本地缓存的 role 过期。
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: getToken(),
    user: loadStoredUser(),
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    isAdmin: (state) => state.user?.role === 'ADMIN',
  },
  actions: {
    async login(username: string, password: string) {
      const resp = await authApi.login(username, password)
      this.setAuth(resp.token, resp.user)
    },
    async register(username: string, password: string, nickname: string) {
      const resp = await authApi.register(username, password, nickname)
      this.setAuth(resp.token, resp.user)
    },
    async fetchMe() {
      if (!this.token) return
      try {
        this.user = await authApi.me()
        localStorage.setItem(USER_KEY, JSON.stringify(this.user))
      } catch {
        // 401 时拦截器会清理 token 并跳转登录页
      }
    },
    setAuth(token: string, user: UserVO) {
      this.token = token
      this.user = user
      setToken(token)
      localStorage.setItem(USER_KEY, JSON.stringify(user))
    },
    logout() {
      this.token = null
      this.user = null
      clearToken()
      localStorage.removeItem(USER_KEY)
    },
  },
})
