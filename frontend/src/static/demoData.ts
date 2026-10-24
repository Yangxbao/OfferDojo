/**
 * 静态演示模式的数据源：直接复用后端题库 JSON（同一份内容，不会产生两套维护）。
 * 通过 Vite 的 import.meta.glob 把 backend/src/main/resources 下的 JSON 打进构建产物，
 * 由 demoApi.ts 模拟只读接口；写操作走 localStorage。
 */
import type { TaskVO, TaskDetailVO, BaguVO, PageResult } from '@/api/types'

// ---- 导入后端 JSON（构建时内联，运行时零请求）----
const taskModules = import.meta.glob('../../../backend/src/main/resources/demo/demo-tasks*.json', { eager: true })
const baguModules = import.meta.glob('../../../backend/src/main/resources/bagu/bagu-*.json', { eager: true })
const resourceModules = import.meta.glob('../../../backend/src/main/resources/learning/learning-resources.json', { eager: true })
const reviewModules = import.meta.glob('../../../backend/src/main/resources/learning/quality-reviews.json', { eager: true })
const nodeModules = import.meta.glob('../../../backend/src/main/resources/learning/learning-nodes.json', { eager: true })
const roadmapModules = import.meta.glob('../../../backend/src/main/resources/demo/roadmap.json', { eager: true })

const CATEGORY_LABELS: Record<string, string> = {
  'api-design': '需求分析与 API 设计', 'db-design': '数据库设计', troubleshooting: '问题排查',
  testing: '测试与质量', refactoring: '代码设计与重构', 'java-core': 'Java 核心与并发',
  framework: 'Spring 与框架', cache: 'Redis 与缓存', 'network-os': '网络与操作系统',
  mq: '消息队列与异步', distributed: '分布式与微服务', 'system-design': '系统设计场景',
  engineering: '工程素养与工具', 'ai-app': 'AI 应用与 Agent',
}

interface RawTask {
  code: string; title: string; category: string; difficulty: string
  tags: string; summary: string; estimatedMinutes: number
  background: string; requirements: string[]; constraints: string[]
  deliverables: string[]; acceptance: string[]
  rubric: { dimension: string; weight: number; guidance: string }[]
}

// ---- 任务：raw JSON → TaskVO/TaskDetailVO ----
const rawTasks: RawTask[] = Object.values(taskModules).flatMap((m: any) => m.default?.tasks ?? [])
const taskIndex = new Map<number, TaskDetailVO>()
let tid = 0
for (const t of rawTasks) {
  tid++
  const detail: TaskDetailVO = {
    id: tid, code: t.code, title: t.title, category: t.category,
    categoryLabel: CATEGORY_LABELS[t.category] ?? t.category,
    difficulty: t.difficulty as any, tags: t.tags.split(',').filter(Boolean),
    summary: t.summary, estimatedMinutes: t.estimatedMinutes, status: 'PUBLISHED',
    myStatus: 'NOT_STARTED', myBestScore: null,
    background: t.background, requirements: t.requirements ?? [],
    constraintItems: t.constraints ?? [], deliverables: t.deliverables ?? [],
    acceptance: t.acceptance ?? [], rubric: t.rubric ?? [],
    myDraftId: null, myDraftContent: null, myLatestSubmissionId: null,
  }
  taskIndex.set(tid, detail)
}

// ---- 八股 ----
const rawBagus = Object.values(baguModules).flatMap((m: any) => m.default?.questions ?? [])
const bagus: BaguVO[] = rawBagus.map((q: any, i: number) => ({
  id: i + 1, code: q.code, category: q.category,
  categoryLabel: CATEGORY_LABELS[q.category] ?? q.category,
  title: q.title, frequency: q.frequency, difficulty: q.difficulty,
  tags: (q.tags ?? '').split(',').filter(Boolean),
  oneLiner: q.oneLiner, plain: q.plain,
  points: q.points ?? [], example: q.example ?? null,
  followUps: q.followUps ?? [], versionNote: q.versionNote ?? null,
}))

// ---- 资源（合并质量评审）----
const rawResources: any[] = Object.values(resourceModules).flatMap((m: any) => m.default?.resources ?? [])
const reviews: any[] = Object.values(reviewModules).flatMap((m: any) => m.default?.reviews ?? [])
const newResources: any[] = Object.values(reviewModules).flatMap((m: any) => m.default?.newResources ?? [])
const reviewMap = new Map(reviews.concat(newResources).map((r: any) => [r.code, r]))
const trackLabels: Record<string, string> = {
  'java-fullstack': 'Java 全栈开发', 'ai-fullstack': 'AI 全栈应用开发', 'python-agent': 'Python Agent 开发',
}
const resources = rawResources.concat(newResources).map((r: any, i: number) => {
  const rv = reviewMap.get(r.code) ?? {}
  return {
    id: i + 1, code: r.code, track: r.track, trackLabel: trackLabels[r.track] ?? r.track,
    title: r.title, description: r.description, url: r.url, sourceType: r.sourceType,
    audience: r.audience ?? null, prerequisite: r.prerequisite ?? [],
    versionNote: r.versionNote ?? null,
    licenseSpdx: r.licenseSpdx ?? null, licenseName: r.licenseName ?? null,
    licensePath: r.licensePath ?? null, licenseStatus: r.licenseStatus ?? 'UNKNOWN',
    verifiedStatus: rv.qualityLevel === 'OFFLINE' ? 'OFFLINE' : r.verifiedStatus,
    verifiedAt: null, verificationEvidence: null,
    allowCopy: false, allowRedistribute: false, attributionRequired: true, shareAlike: false,
    rightsBasis: null, adminNote: null,
    categoryTags: '', language: rv.language ?? null, difficultyLevel: rv.difficultyLevel ?? null,
    starsCount: rv.stars ?? null, forksCount: rv.forks ?? null,
    qualityScore: rv.qualityScore ?? null, qualityLevel: rv.qualityLevel ?? null,
    featured: rv.featured === 1, reviewNote: rv.reviewNote ?? null,
    inMyPlan: false,
  }
})

// ---- 三条方向路线节点 ----
const trackNodes = Object.values(nodeModules).flatMap((m: any) => m.default?.nodes ?? [])

// ---- 十阶段路线图 ----
const roadmapStages = Object.values(roadmapModules).flatMap((m: any) => m.default?.stages ?? [])

// ---- localStorage 演示进度（刷新不丢；清浏览器数据会丢失）----
const LS_KEY = 'offerdojo.static-demo.progress'
export interface DemoProgress {
  drafts: Record<string, string>        // taskId → 草稿内容
  submitted: Record<string, { content: string; at: string }>  // taskId → 已提交答案（无 AI 评估）
  plan: Record<string, string>          // nodeCode/resourceCode → TODO/IN_PROGRESS/DONE
}
function loadProgress(): DemoProgress {
  try { return JSON.parse(localStorage.getItem(LS_KEY) ?? '{}') } catch { return {} as any }
}
export function getProgress(): DemoProgress {
  const p = loadProgress()
  return { drafts: p.drafts ?? {}, submitted: p.submitted ?? {}, plan: p.plan ?? {} }
}
function saveProgress(p: DemoProgress) {
  localStorage.setItem(LS_KEY, JSON.stringify(p))
}

/** 计算任务的本地练习状态（供列表/详情展示） */
export function myTaskStatus(taskId: number): { status: string; score: number | null } {
  const p = getProgress()
  const id = String(taskId)
  if (p.submitted[id]) return { status: 'COMPLETED', score: null }
  if (p.drafts[id]) return { status: 'DRAFTING', score: null }
  return { status: 'NOT_STARTED', score: null }
}

export function toTaskVO(d: TaskDetailVO): TaskVO {
  const s = myTaskStatus(d.id)
  return {
    id: d.id, code: d.code, title: d.title, category: d.category,
    categoryLabel: d.categoryLabel, difficulty: d.difficulty, tags: d.tags,
    summary: d.summary, estimatedMinutes: d.estimatedMinutes, status: 'PUBLISHED',
    myStatus: s.status as any, myBestScore: s.score,
  }
}

/** 给任务详情叠加本地进度 */
export function taskDetailWithProgress(d: TaskDetailVO): TaskDetailVO {
  const p = getProgress()
  const s = myTaskStatus(d.id)
  return {
    ...d,
    myStatus: s.status as any,
    myDraftContent: p.drafts[String(d.id)] ?? null,
    myDraftId: p.drafts[String(d.id)] ? d.id : null,
  }
}

export function paginate<T>(list: T[], page = 1, size = 10): PageResult<T> {
  const start = (page - 1) * size
  return { list: list.slice(start, start + size), total: list.length, page, size }
}

export const demoData = {
  tasks: [...taskIndex.values()],
  bagus, resources, trackNodes, roadmapStages,
  saveDraft(taskId: number, content: string) {
    const p = getProgress(); p.drafts[String(taskId)] = content; saveProgress(p)
  },
  submitAnswer(taskId: number, content: string) {
    const p = getProgress(); p.submitted[String(taskId)] = { content, at: new Date().toISOString() }; saveProgress(p)
  },
  setPlanStatus(code: string, status: string) {
    const p = getProgress(); p.plan[code] = status; saveProgress(p)
  },
}
