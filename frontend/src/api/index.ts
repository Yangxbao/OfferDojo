import { request } from './http'
import type {
  AdminEvaluationRow,
  AdminOverviewVO,
  AdminTaskVO,
  AiLogVO,
  AuthResponse,
  DashboardVO,
  EvaluationVO,
  PageResult,
  ProgressVO,
  SubmissionDetailVO,
  SubmissionVO,
  TaskDetailVO,
  TaskSaveRequest,
  TaskVO,
  UserVO,
  BaguVO,
  RoadmapStageVO,
} from './types'

export const authApi = {
  login: (username: string, password: string) =>
    request<AuthResponse>({ url: '/auth/login', method: 'post', data: { username, password } }),
  register: (username: string, password: string, nickname: string) =>
    request<AuthResponse>({ url: '/auth/register', method: 'post', data: { username, password, nickname } }),
  me: () => request<UserVO>({ url: '/auth/me', method: 'get' }),
}

export const dashboardApi = {
  get: () => request<DashboardVO>({ url: '/dashboard', method: 'get' }),
}

export const taskApi = {
  page: (params: { category?: string; difficulty?: string; keyword?: string; page?: number; size?: number }) =>
    request<PageResult<TaskVO>>({ url: '/tasks', method: 'get', params }),
  detail: (id: number) => request<TaskDetailVO>({ url: `/tasks/${id}`, method: 'get' }),
  saveDraft: (taskId: number, content: string) =>
    request<number>({ url: `/tasks/${taskId}/draft`, method: 'put', data: { content } }),
  submit: (taskId: number, content: string) =>
    request<SubmissionDetailVO>({ url: `/tasks/${taskId}/submit`, method: 'post', data: { content } }),
}

export const submissionApi = {
  myHistory: (params: { page?: number; size?: number }) =>
    request<PageResult<SubmissionVO>>({ url: '/my/submissions', method: 'get', params }),
  detail: (id: number) => request<SubmissionDetailVO>({ url: `/my/submissions/${id}`, method: 'get' }),
}

export const progressApi = {
  get: () => request<ProgressVO>({ url: '/my/progress', method: 'get' }),
}

export const baguApi = {
  questions: (params: {
    category?: string
    frequency?: string
    keyword?: string
    page?: number
    size?: number
  }) => request<PageResult<BaguVO>>({ url: '/bagu/questions', method: 'get', params }),
  categories: () => request<{ category: string; label: string; count: number }[]>({
    url: '/bagu/categories',
    method: 'get',
  }),
}

export const roadmapApi = {
  get: () => request<RoadmapStageVO[]>({ url: '/roadmap', method: 'get' }),
}

export const adminApi = {
  overview: () => request<AdminOverviewVO>({ url: '/admin/overview', method: 'get' }),
  taskPage: (params: {
    category?: string
    difficulty?: string
    status?: string
    keyword?: string
    page?: number
    size?: number
  }) => request<PageResult<AdminTaskVO>>({ url: '/admin/tasks', method: 'get', params }),
  taskDetail: (id: number) => request<TaskSaveRequest>({ url: `/admin/tasks/${id}`, method: 'get' }),
  createTask: (data: TaskSaveRequest) =>
    request<TaskVO>({ url: '/admin/tasks', method: 'post', data }),
  updateTask: (id: number, data: TaskSaveRequest) =>
    request<TaskVO>({ url: `/admin/tasks/${id}`, method: 'put', data }),
  updateTaskStatus: (id: number, status: string) =>
    request<void>({ url: `/admin/tasks/${id}/status`, method: 'put', params: { status } }),
  evaluations: (params: { taskId?: number; page?: number; size?: number }) =>
    request<PageResult<AdminEvaluationRow>>({ url: '/admin/evaluations', method: 'get', params }),
  aiLogs: (params: { success?: boolean; page?: number; size?: number }) =>
    request<PageResult<AiLogVO>>({ url: '/admin/ai-logs', method: 'get', params }),
}

export type { EvaluationVO }
