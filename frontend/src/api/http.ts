import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { demoRequest, isStaticDemo } from '@/static/demoApi'

/** 后端统一响应结构 */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

const TOKEN_KEY = 'jobsim.token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

const instance = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

instance.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

instance.interceptors.response.use(
  (resp) => resp,
  (error) => {
    const status: number | undefined = error.response?.status
    const message: string = error.response?.data?.message ?? '网络异常，请稍后重试'
    if (status === 401) {
      clearToken()
      if (!location.pathname.startsWith('/login')) {
        location.href = '/login'
      }
    }
    ElMessage.error(message)
    return Promise.reject(error)
  },
)

/**
 * 统一请求入口：后端 2xx 时 code 恒为 0，直接返回 data；
 * 非 2xx 由拦截器弹出错误提示并 reject。
 *
 * 静态演示模式（build:static-demo 构建）：请求不发出网络，
 * 由 demoApi 基于内嵌题库 JSON + localStorage 模拟只读接口；
 * 需要后端的能力抛出明确的「演示模式暂不支持」提示。
 */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  if (isStaticDemo()) {
    return demoRequest<T>(config as { url: string; method?: string; params?: any; data?: any })
  }
  const resp = await instance.request<ApiResponse<T>>(config)
  if (resp.data.code !== 0) {
    throw new Error(resp.data.message)
  }
  return resp.data.data
}
