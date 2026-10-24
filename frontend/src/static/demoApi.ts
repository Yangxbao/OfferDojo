/**
 * 静态演示模式的迷你 API 路由器：匹配只读接口返回静态数据；
 * 写操作保存到 localStorage 或返回明确的「演示模式不可用」错误。
 * 只在 __STATIC_DEMO__ 构建标志开启时被 http.ts 调用（正式构建零引用）。
 */
import { demoData, myTaskStatus, paginate, taskDetailWithProgress, toTaskVO } from './demoData'
import type { ApiResponse } from '@/api/http'

export function isStaticDemo(): boolean {
  return !!(import.meta as any).env?.__STATIC_DEMO__
}

export class DemoUnavailableError extends Error {
  constructor(feature: string) {
    super(`静态演示模式暂不支持「${feature}」——该功能需要部署后端服务后使用`)
  }
}

/** 模拟网络延迟，让加载态真实呈现 */
const delay = (ms = 120) => new Promise((r) => setTimeout(r, ms))

function ok<T>(data: T): ApiResponse<T> {
  return { code: 0, message: 'ok', data }
}

interface ReqOpts {
  url: string
  method?: string
  params?: Record<string, any>
  data?: any
}

/** 用 localStorage 模拟的三条方向路线（含进度） */
function learningTracks() {
  const trackSet = new Map<string, any[]>()
  for (const n of demoData.trackNodes as any[]) {
    if (!trackSet.has(n.track)) trackSet.set(n.track, [])
    ;(trackSet.get(n.track)! as any[]).push(n)
  }
  const progress = (() => {
    try { return (JSON.parse(localStorage.getItem('offerdojo.static-demo.progress') ?? '{}') as any).plan ?? {} }
    catch { return {} }
  })()
  return [...trackSet.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([track, nodes]) => {
    const stages = new Map<number, any[]>()
    for (const n of nodes) {
      if (!stages.has(n.stageOrder)) stages.set(n.stageOrder, [])
      stages.get(n.stageOrder)!.push(n)
    }
    return {
      track,
      trackLabel: { 'java-fullstack': 'Java 全栈开发', 'ai-fullstack': 'AI 全栈应用开发', 'python-agent': 'Python Agent 开发' }[track] ?? track,
      description: '',
      stages: [...stages.entries()].sort(([a], [b]) => a - b).map(([order, ns]) => ({
        stageOrder: order,
        stageTitle: `第 ${order} 阶段`,
        nodes: ns.sort((a, b) => a.nodeOrder - b.nodeOrder).map((n) => ({
          id: n.code, code: n.code, nodeOrder: n.nodeOrder, title: n.title, goal: n.goal,
          knowledge: n.knowledge ?? [],
          resources: (n.resourceCodes ?? []).map((code: string) => {
            const r = (demoData.resources as any[]).find((x) => x.code === code)
            return r ? { code: r.code, title: r.title, url: r.url, licenseSpdx: r.licenseSpdx, verifiedStatus: r.verifiedStatus, sourceType: r.sourceType } : null
          }).filter(Boolean),
          tasks: (n.taskCodes ?? []).map((code: string) => {
            const t = demoData.tasks.find((x) => x.code === code)
            if (!t) return null
            const s = myTaskStatus(t.id)
            return { code: t.code, title: t.title, difficulty: t.difficulty, myStatus: s.status, myBestScore: s.score }
          }).filter(Boolean),
          prereqCodes: n.prereqCodes ?? [],
          milestone: n.milestone ?? null,
          planStatus: progress[n.code] ?? null,
          planNote: null,
        })),
      })),
    }
  })
}

/** 工作台数据：全部基于本地真实进度计算（不虚构统计） */
function dashboard() {
  const tasks = demoData.tasks
  const completed = tasks.filter((t) => myTaskStatus(t.id).status === 'COMPLETED').length
  const recent = tasks
    .filter((t) => myTaskStatus(t.id).status === 'COMPLETED')
    .slice(0, 5)
    .map((t) => {
      const p = JSON.parse(localStorage.getItem('offerdojo.static-demo.progress') ?? '{}')
      const s = (p.submitted ?? {})[String(t.id)]
      return {
        id: t.id, taskId: t.id, taskTitle: t.title, taskCategory: t.category,
        taskDifficulty: t.difficulty, status: 'EVALUATED', totalScore: null,
        createdAt: s?.at ?? '', updatedAt: s?.at ?? '',
      }
    })
  const todo = tasks.filter((t) => myTaskStatus(t.id).status !== 'COMPLETED')
  const todayTask = todo.length ? toTaskVO(todo[0]) : null
  return {
    todayTask, todayReason: '静态演示模式：按题库顺序推荐（未连接后端，无个性化数据）',
    completedTasks: completed, publishedTasks: tasks.length,
    abilities: [], recent, recommended: todo.slice(0, 3).map(toTaskVO), resume: null,
  }
}

function progress() {
  const tasks = demoData.tasks
  const completed = tasks.filter((t) => myTaskStatus(t.id).status === 'COMPLETED')
  const catMap = new Map<string, number>()
  for (const t of completed) catMap.set(t.category, (catMap.get(t.category) ?? 0) + 1)
  return {
    trend: [], abilities: [], weaknesses: [],
    suggestions: tasks.filter((t) => myTaskStatus(t.id).status !== 'COMPLETED').slice(0, 3).map(toTaskVO),
    completion: {
      completedTasks: completed.length, publishedTasks: tasks.length,
      rate: tasks.length ? Math.round((completed.length / tasks.length) * 1000) / 10 : 0,
      categories: [...new Set(tasks.map((t) => t.category))].map((c) => ({
        category: c, label: '', published: tasks.filter((t) => t.category === c).length,
        completed: catMap.get(c) ?? 0,
      })),
    },
  }
}

/** 核心路由表：url 匹配（method 区分读写） */
export async function demoRequest<T>(opts: ReqOpts): Promise<T> {
  const { url, method = 'get', params = {} } = opts
  const path = url.replace(/^\//, '').split('?')[0] // 去掉 query
  await delay()

  // ---------- 认证：演示身份（本地标记，不伪装服务器认证）----------
  if (path === 'auth/me') {
    return ok({ id: 0, username: 'static-demo', nickname: '静态演示用户', role: 'USER' }) as any
  }

  // ---------- 任务 ----------
  if (path === 'tasks' && method === 'get') {
    let list = demoData.tasks
    if (params.category) list = list.filter((t) => t.category === params.category)
    if (params.difficulty) list = list.filter((t) => t.difficulty === params.difficulty)
    if (params.keyword) list = list.filter((t) => t.title.includes(params.keyword))
    const vos = list.map(toTaskVO)
    return ok(paginate(vos, params.page ?? 1, params.size ?? 10)) as any
  }
  const mTask = path.match(/^tasks\/(\d+)$/)
  if (mTask && method === 'get') {
    const d = demoData.tasks.find((t) => t.id === Number(mTask[1]))
    if (!d) throw new Error('任务不存在')
    return ok(taskDetailWithProgress(d)) as any
  }
  const mDraft = path.match(/^tasks\/(\d+)\/draft$/)
  if (mDraft && (method === 'put' || method === 'PUT')) {
    demoData.saveDraft(Number(mDraft[1]), opts.data?.content ?? '')
    return Number(mDraft[1]) as any // 与真实接口一致：返回草稿 id
  }
  const mSubmit = path.match(/^tasks\/(\d+)\/submit$/)
  if (mSubmit && (method === 'post' || method === 'POST')) {
    // 诚实边界：答案保存到本地，但不伪造 AI 评估成功
    demoData.submitAnswer(Number(mSubmit[1]), opts.data?.content ?? '')
    throw new DemoUnavailableError(
      `AI 评估（答案已保存到当前浏览器，部署后端后可重新提交获取评估）`)
  }

  // ---------- 八股 ----------
  if (path === 'bagu/questions') {
    let list = demoData.bagus
    if (params.category) list = list.filter((q) => q.category === params.category)
    if (params.frequency) list = list.filter((q) => q.frequency === params.frequency)
    if (params.keyword) list = list.filter((q) =>
      q.title.includes(params.keyword) || q.oneLiner.includes(params.keyword))
    return ok(paginate(list, params.page ?? 1, params.size ?? 10)) as any
  }
  if (path === 'bagu/categories') {
    const catMap = new Map<string, number>()
    for (const q of demoData.bagus) catMap.set(q.category, (catMap.get(q.category) ?? 0) + 1)
    return ok([...catMap.entries()].map(([category, count]) => ({
      category, label: demoData.bagus.find((q) => q.category === category)!.categoryLabel, count,
    }))) as any
  }
  if (path === 'bagu/community') {
    return ok(paginate([], 1, 10)) as any // 诚实：社区功能需后端
  }

  // ---------- 资源中心 ----------
  if (path === 'learning/resources') {
    let list = demoData.resources.filter((r: any) => r.verifiedStatus !== 'OFFLINE')
    if (params.track) list = list.filter((r: any) => r.track === params.track)
    if (params.licenseStatus) list = list.filter((r: any) => r.licenseStatus === params.licenseStatus)
    if (params.verifiedStatus) list = list.filter((r: any) => r.verifiedStatus === params.verifiedStatus)
    if (params.qualityLevel) list = list.filter((r: any) => r.qualityLevel === params.qualityLevel)
    if (params.keyword) list = list.filter((r: any) => r.title.includes(params.keyword) || r.description.includes(params.keyword))
    const sorted = [...list].sort((a: any, b: any) =>
      (Number(b.featured) - Number(a.featured)) || ((b.qualityScore ?? 0) - (a.qualityScore ?? 0)))
    return ok(paginate(sorted, params.page ?? 1, params.size ?? 12)) as any
  }
  const mRes = path.match(/^learning\/resources\/(\d+)$/)
  if (mRes) {
    const r = demoData.resources.find((x: any) => x.id === Number(mRes[1]))
    if (!r) throw new Error('资源不存在')
    return ok(r) as any
  }
  if (path === 'learning/resources/quality-stats') {
    const stats: Record<string, number> = {}
    for (const r of demoData.resources.filter((x: any) => x.verifiedStatus !== 'OFFLINE')) {
      const lv = (r as any).qualityLevel ?? 'PENDING_EVAL'
      stats[lv] = (stats[lv] ?? 0) + 1
    }
    return ok(stats) as any
  }
  if (path === 'learning/tracks') {
    const tracks = learningTracks()
    return ok(params.track ? tracks.filter((t) => t.track === params.track) : tracks) as any
  }
  if (path === 'roadmap') {
    // 十阶段路线图（本地进度叠加）
    return ok(demoData.roadmapStages.map((s: any) => ({
      ...s,
      tasks: (s.taskCodes ?? []).map((code: string) => {
        const t = demoData.tasks.find((x) => x.code === code)
        if (!t) return null
        const st = myTaskStatus(t.id)
        return { id: t.id, code: t.code, title: t.title, difficulty: t.difficulty,
          estimatedMinutes: t.estimatedMinutes, myStatus: st.status, myBestScore: st.score }
      }).filter(Boolean),
    }))) as any
  }
  if (path === 'learning/plan' && method === 'get') {
    const p = JSON.parse(localStorage.getItem('offerdojo.static-demo.progress') ?? '{}')
    return ok(Object.entries((p.plan ?? {}) as Record<string, string>).map(([code, status]) => ({
      id: code, itemType: code.startsWith('RES-') ? 'RESOURCE' : 'NODE', itemCode: code,
      itemTitle: demoData.trackNodes.find((n: any) => n.code === code)?.title ?? code,
      status, note: null, startedAt: null, completedAt: null, updatedAt: '',
    }))) as any
  }
  if (path === 'learning/plan' && (method === 'post' || method === 'POST')) {
    demoData.setPlanStatus(opts.data.itemCode, 'TODO')
    return ok({ id: opts.data.itemCode }) as any
  }
  const mPlan = path.match(/^learning\/plan\/(.+)$/)
  if (mPlan && (method === 'put' || method === 'PUT')) {
    // 静态模式下 id 即 code
    demoData.setPlanStatus(mPlan[1], opts.data?.status ?? 'IN_PROGRESS')
    return ok({ id: mPlan[1] }) as any
  }

  // ---------- 工作台 / 能力分析 ----------
  if (path === 'dashboard') return ok(dashboard()) as any
  if (path === 'my/progress') return ok(progress()) as any
  if (path === 'my/submissions') {
    const p = JSON.parse(localStorage.getItem('offerdojo.static-demo.progress') ?? '{}')
    const rows = Object.entries((p.submitted ?? {}) as Record<string, any>).map(([id, v]) => {
      const t = demoData.tasks.find((x) => x.id === Number(id))!
      return { id: Number(id), taskId: t.id, taskTitle: t.title, taskCategory: t.category,
        taskDifficulty: t.difficulty, status: 'EVALUATED', totalScore: null,
        createdAt: v.at, updatedAt: v.at }
    })
    return ok(paginate(rows, params.page ?? 1, params.size ?? 10)) as any
  }
  const mSub = path.match(/^my\/submissions\/(\d+)$/)
  if (mSub) {
    const p = JSON.parse(localStorage.getItem('offerdojo.static-demo.progress') ?? '{}')
    const s = (p.submitted ?? {})[mSub[1]]
    const t = demoData.tasks.find((x) => x.id === Number(mSub[1]))!
    if (!s) throw new Error('记录不存在')
    return ok({ id: Number(mSub[1]), taskId: t.id, taskTitle: t.title, taskCategory: t.category,
      taskDifficulty: t.difficulty, status: 'EVALUATED', content: s.content,
      updatedAt: s.at, evaluation: null }) as any // 无评估（诚实）
  }

  // ---------- 明确不可用（需后端）----------
  if (path.startsWith('auth/login') || path.startsWith('auth/register')) {
    throw new DemoUnavailableError('服务器登录/注册（演示模式自动使用本地演示身份）')
  }
  if (path.startsWith('github/top') && !path.includes('categories')) {
    // 榜单需实时调 GitHub API（后端代理）——静态模式给明确提示
    throw new DemoUnavailableError('GitHub 实时榜单（需后端代理 GitHub API）')
  }
  if (path === 'github/top/categories') {
    return ok([
      { key: 'java-backend', label: 'Java 后端' }, { key: 'frontend', label: '前端' },
      { key: 'python', label: 'Python' }, { key: 'ai-agent', label: 'AI / Agent' },
      { key: 'rag', label: 'RAG' }, { key: 'database', label: '数据库' },
      { key: 'devops', label: 'DevOps' }, { key: 'testing', label: '测试' },
    ]) as any
  }
  if (path.startsWith('my/evaluations')) {
    throw new DemoUnavailableError('追问模拟面试（需后端 AI 评估）')
  }
  if (path.startsWith('my/projects')) {
    throw new DemoUnavailableError('项目投稿（需后端审核流程）')
  }
  if (path.startsWith('my/bagu-submissions')) {
    throw new DemoUnavailableError('社区题库投稿（需后端审核流程）')
  }
  if (path === 'my/resume-material') {
    throw new DemoUnavailableError('简历素材生成（需后端读取练习记录）')
  }
  if (path === 'jd/match') {
    throw new DemoUnavailableError('JD 反向匹配（需后端检索）')
  }

  throw new DemoUnavailableError(path)
}
