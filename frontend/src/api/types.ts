/** 与后端 DTO 一一对应的类型定义 */

export interface UserVO {
  id: number
  username: string
  nickname: string
  role: 'USER' | 'ADMIN'
}

export interface AuthResponse {
  token: string
  user: UserVO
}

export interface RubricItem {
  dimension: string
  weight: number
  guidance: string
}

export interface TaskVO {
  id: number
  code: string
  title: string
  category: string
  categoryLabel: string
  difficulty: 'EASY' | 'MEDIUM' | 'HARD' | 'EXPERT'
  tags: string[]
  summary: string
  estimatedMinutes: number
  status: string
  myStatus: 'NOT_STARTED' | 'DRAFTING' | 'COMPLETED'
  myBestScore: number | null
}

export interface TaskDetailVO extends TaskVO {
  background: string
  requirements: string[]
  constraintItems: string[]
  deliverables: string[]
  acceptance: string[]
  rubric: RubricItem[]
  myDraftId: number | null
  myDraftContent: string | null
  myLatestSubmissionId: number | null
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}

export interface DimensionScoreVO {
  dimension: string
  score: number
  weight: number
  comment: string
}

export interface EvaluationVO {
  id: number
  totalScore: number
  provider: 'MOCK' | 'REMOTE' | 'MOCK_FALLBACK'
  model: string | null
  notice: string | null
  createdAt: string
  dimensionScores: DimensionScoreVO[]
  strengths: string[]
  problems: string[]
  edgeCases: string[]
  suggestions: string[]
  followUps: string[]
  nextTasks: TaskVO[]
}

export interface SubmissionVO {
  id: number
  taskId: number
  taskTitle: string
  taskCategory: string
  taskDifficulty: string
  status: string
  totalScore: number | null
  createdAt: string
  updatedAt: string
}

export interface SubmissionDetailVO {
  id: number
  taskId: number
  taskTitle: string
  taskCategory: string
  taskDifficulty: string
  status: string
  content: string
  updatedAt: string
  evaluation: EvaluationVO | null
}

export interface AbilityVO {
  dimension: string
  avgScore: number
  practiceCount: number
  maxScore: number
}

export interface DashboardVO {
  todayTask: TaskVO | null
  todayReason: string
  completedTasks: number
  publishedTasks: number
  abilities: AbilityVO[]
  recent: SubmissionVO[]
  recommended: TaskVO[]
  resume: {
    submissionId: number
    taskId: number
    taskTitle: string
    updatedAt: string
  } | null
}

export interface ProgressVO {
  trend: { date: string; avgScore: number; count: number }[]
  abilities: AbilityVO[]
  weaknesses: { dimension: string; avgScore: number; practiceCount: number; advice: string }[]
  suggestions: TaskVO[]
  completion: {
    completedTasks: number
    publishedTasks: number
    rate: number
    categories: { category: string; label: string; published: number; completed: number }[]
  }
}

export interface AdminTaskVO {
  id: number
  code: string
  title: string
  category: string
  categoryLabel: string
  difficulty: string
  tags: string
  summary: string
  estimatedMinutes: number
  status: string
  sortOrder: number
  createdAt: string
  updatedAt: string
  attempts: number
  userCount: number
  avgScore: number | null
}

export interface AdminOverviewVO {
  userCount: number
  publishedTasks: number
  totalTasks: number
  submissionCount: number
  evaluationCount: number
  avgScore: number
  failureCount24h: number
}

export interface AiLogVO {
  id: number
  provider: string
  model: string | null
  taskCode: string | null
  success: boolean
  errorType: string | null
  message: string | null
  durationMs: number | null
  createdAt: string
}

export interface AdminEvaluationRow {
  id: number
  submissionId: number
  taskId: number
  userId: number
  username: string
  taskTitle: string
  totalScore: number
  provider: string
  notice: string | null
  createdAt: string
}

/** 管理端任务编辑表单（与后端 TaskSaveRequest 对应） */
export interface TaskSaveRequest {
  code: string
  title: string
  category: string
  difficulty: string
  tags: string[]
  summary: string
  estimatedMinutes: number
  background: string
  requirements: string[]
  constraintItems: string[]
  deliverables: string[]
  acceptance: string[]
  rubric: RubricItem[]
  status: string
  sortOrder: number
}

/** 八股复习题目 */
export interface BaguVO {
  id: number
  code: string
  category: string
  categoryLabel: string
  title: string
  frequency: 'HIGH' | 'MID' | 'LOW'
  difficulty: string
  tags: string[]
  oneLiner: string
  plain: string
  points: string[]
  example: string | null
  followUps: string[]
  versionNote: string | null
}

/** 学习路线图阶段 */
export interface RoadmapStageVO {
  order: number
  title: string
  goal: string
  knowledge: string[]
  tasks: {
    id: number
    code: string
    title: string
    difficulty: string
    estimatedMinutes: number
    myStatus: 'NOT_STARTED' | 'DRAFTING' | 'COMPLETED'
    myBestScore: number | null
  }[]
  milestone: string
}

/** 追问模拟面试对话记录 */
export interface FollowupDialogVO {
  id: number
  evaluationId: number
  question: string
  answer: string
  score: number
  feedback: string
  provider: string
  notice: string | null
  createdAt: string
}

/** 开源学习资源元数据 */
export interface LearningResourceVO {
  id: number
  code: string
  track: string
  trackLabel: string
  title: string
  description: string
  url: string
  sourceType: string
  audience: string | null
  prerequisite: string[]
  versionNote: string | null
  licenseSpdx: string | null
  licenseName: string | null
  licensePath: string | null
  licenseStatus: string
  verifiedStatus: string
  verifiedAt: string | null
  verificationEvidence: string | null
  allowCopy: boolean
  allowRedistribute: boolean
  attributionRequired: boolean
  shareAlike: boolean
  rightsBasis: string | null
  adminNote: string | null
  inMyPlan: boolean
}

/** 学习路线节点关联的资要摘要 */
export interface LearningResourceBrief {
  code: string
  title: string
  url: string
  licenseSpdx: string | null
  verifiedStatus: string
  sourceType: string
}

/** 学习路线节点关联的实战任务摘要 */
export interface LearningTaskBrief {
  id: number
  code: string
  title: string
  difficulty: string
  myStatus: string
  myBestScore: number | null
}

/** 学习路线节点 */
export interface LearningNodeVO {
  id: number
  code: string
  nodeOrder: number
  title: string
  goal: string
  knowledge: string[]
  resources: LearningResourceBrief[]
  tasks: LearningTaskBrief[]
  prereqCodes: string[]
  milestone: string | null
  planStatus: string | null
  planNote: string | null
}

/** 学习路线阶段 */
export interface LearningStageVO {
  stageOrder: number
  stageTitle: string
  nodes: LearningNodeVO[]
}

/** 学习路线（一个技术方向） */
export interface LearningTrackVO {
  track: string
  trackLabel: string
  description: string
  stages: LearningStageVO[]
}

/** 个人学习计划条目 */
export interface PlanItemVO {
  id: number
  itemType: string
  itemCode: string
  itemTitle: string
  status: string
  note: string | null
  startedAt: string | null
  completedAt: string | null
  updatedAt: string
}
