import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

export function formatDateTime(value?: string | null): string {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—'
}

export function fromNow(value?: string | null): string {
  return value ? dayjs(value).fromNow() : '—'
}

export const DIFFICULTY_LABELS: Record<string, string> = {
  EASY: '基础',
  MEDIUM: '进阶',
  HARD: '挑战',
  EXPERT: '企业实战',
}

export const DIFFICULTY_TYPES: Record<string, string> = {
  EASY: 'success',
  MEDIUM: 'primary',
  HARD: 'warning',
  EXPERT: 'danger',
}

export const MY_STATUS_LABELS: Record<string, string> = {
  NOT_STARTED: '未开始',
  DRAFTING: '草稿中',
  COMPLETED: '已完成',
}

export const MY_STATUS_TYPES: Record<string, string> = {
  NOT_STARTED: 'info',
  DRAFTING: 'warning',
  COMPLETED: 'success',
}

export const CATEGORY_LABELS: Record<string, string> = {
  'api-design': '需求分析与 API 设计',
  'db-design': '数据库设计',
  troubleshooting: '问题排查',
  testing: '测试与质量',
  refactoring: '代码设计与重构',
  'java-core': 'Java 核心与并发',
  framework: 'Spring 与框架',
  cache: 'Redis 与缓存',
  'network-os': '网络与操作系统',
  mq: '消息队列与异步',
  distributed: '分布式与微服务',
  'system-design': '系统设计场景',
  engineering: '工程素养与工具',
}

export const FREQUENCY_LABELS: Record<string, string> = {
  HIGH: '高频',
  MID: '中频',
  LOW: '低频',
}

export const FREQUENCY_TYPES: Record<string, string> = {
  HIGH: 'danger',
  MID: 'warning',
  LOW: 'info',
}

export const PROVIDER_LABELS: Record<string, string> = {
  MOCK: '本地模拟评估',
  REMOTE: 'AI 模型评估',
  MOCK_FALLBACK: '模拟评估（远程服务降级）',
}

/** 分数对应的展示颜色（低于 6 分提示薄弱） */
export function scoreColor(score: number): string {
  if (score >= 8) return '#16a34a'
  if (score >= 6) return '#2563eb'
  if (score > 0) return '#ea580c'
  return '#94a3b8'
}
