<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { request } from '@/api/http'

interface Sub {
  id: number
  category: string
  categoryLabel: string
  questionType: string
  difficulty: string
  title: string
  content: string
  myAnswer: string | null
  referenceAnswer: string | null
  example: string | null
  tags: string | null
  companyInfo: string | null
  referenceUrl: string | null
  anonymous: boolean
  licenseGrant: string | null
  status: string
  reviewComment: string | null
  authorName?: string
  originLabel?: string
  reviewedAt?: string | null
}

const loading = ref(true)
const tab = ref<'community' | 'mine'>('community')
const community = ref<Sub[]>([])
const mine = ref<Sub[]>([])
const editing = ref<Sub | null>(null)
const editorVisible = ref(false)
const saving = ref(false)
const query = reactive({ category: '', questionType: '', keyword: '' })

const CATEGORY_LABELS: Record<string, string> = {
  'api-design': '需求分析与 API 设计', 'db-design': '数据库设计', troubleshooting: '问题排查',
  testing: '测试与质量', refactoring: '代码设计与重构', 'java-core': 'Java 核心与并发',
  framework: 'Spring 与框架', cache: 'Redis 与缓存', 'network-os': '网络与操作系统',
  mq: '消息队列与异步', distributed: '分布式与微服务', 'system-design': '系统设计场景',
  engineering: '工程素养与工具', 'ai-app': 'AI 应用与 Agent',
}
const TYPE_LABELS: Record<string, string> = {
  BAGU: '八股题', INTERVIEW: '面试复盘', PROJECT: '项目实战', SYSTEM_DESIGN: '系统设计', DEBUG: '问题排查',
}
const STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿', PENDING_REVIEW: '待审核', APPROVED: '已发布', REJECTED: '已驳回', WITHDRAWN: '已撤回',
}
const STATUS_TYPES: Record<string, string> = {
  DRAFT: 'info', PENDING_REVIEW: 'warning', APPROVED: 'success', REJECTED: 'danger', WITHDRAWN: 'info',
}

const form = reactive<Partial<Sub>>({ anonymous: false })

async function loadCommunity() {
  const r = await request<{ list: Sub[] }>({ url: '/bagu/community', method: 'get', params: { ...query, size: 50 } })
  community.value = r.list
}
async function loadMine() {
  const r = await request<{ list: Sub[] }>({ url: '/my/bagu-submissions', method: 'get', params: { size: 50 } })
  mine.value = r.list
}

function openEditor(item?: Sub) {
  editing.value = item ?? null
  Object.assign(form, item ?? { anonymous: false, questionType: 'BAGU', difficulty: 'MEDIUM', licenseGrant: null })
  editorVisible.value = true
}

async function save() {
  saving.value = true
  try {
    if (editing.value) await request({ url: `/my/bagu-submissions/${editing.value.id}`, method: 'put', data: form })
    else await request({ url: '/my/bagu-submissions', method: 'post', data: form })
    ElMessage.success('已保存草稿')
    editorVisible.value = false
    await loadMine()
  } finally { saving.value = false }
}

async function submitReview(id: number) {
  try {
    await request({ url: `/my/bagu-submissions/${id}/submit`, method: 'put' })
    ElMessage.success('已提交审核')
    await loadMine()
  } catch { /* 拦截器已提示（如未选授权） */ }
}

async function withdraw(id: number) {
  await request({ url: `/my/bagu-submissions/${id}/withdraw`, method: 'put' })
  ElMessage.success('已撤回'); await loadMine()
}
async function remove(id: number) {
  await ElMessageBox.confirm('确定删除这条草稿？', '确认')
  await request({ url: `/my/bagu-submissions/${id}`, method: 'delete' })
  ElMessage.success('已删除'); await loadMine()
}

onMounted(async () => {
  try { await Promise.all([loadCommunity(), loadMine()]) } finally { loading.value = false }
})
</script>

<template>
  <div class="page" v-loading="loading" style="padding-bottom: 24px">
    <div class="page-header" style="display:flex;justify-content:space-between;align-items:flex-start;flex-wrap:wrap;gap:12px">
      <div>
        <h2>社区题库</h2>
        <p>同学们投稿的真实面试题与复盘（审核通过后展示）。社区内容与官方 125 题分开，来源标注清晰。</p>
      </div>
      <el-button type="primary" @click="openEditor()">+ 投稿题目</el-button>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="社区题库" name="community">
        <div class="card" style="margin-bottom:12px;display:flex;gap:10px;flex-wrap:wrap">
          <el-select v-model="query.category" placeholder="全部分类" clearable style="width:170px" @change="loadCommunity">
            <el-option v-for="(l, c) in CATEGORY_LABELS" :key="c" :label="l" :value="c" />
          </el-select>
          <el-select v-model="query.questionType" placeholder="全部类型" clearable style="width:120px" @change="loadCommunity">
            <el-option v-for="(l, t) in TYPE_LABELS" :key="t" :label="l" :value="t" />
          </el-select>
          <el-input v-model="query.keyword" placeholder="搜索" clearable style="width:180px" @change="loadCommunity" />
        </div>
        <el-empty v-if="!community.length" description="社区题库还是空的——投稿第一题！" />
        <div v-else style="display:flex;flex-direction:column;gap:10px">
          <div v-for="s in community" :key="s.id" class="card">
            <div style="display:flex;gap:6px;flex-wrap:wrap;margin-bottom:6px">
              <el-tag size="small" effect="plain">{{ s.categoryLabel }}</el-tag>
              <el-tag size="small" type="info" effect="plain">{{ TYPE_LABELS[s.questionType] }}</el-tag>
              <el-tag size="small" :type="s.difficulty === 'HARD' ? 'danger' : s.difficulty === 'EASY' ? 'success' : 'primary'" effect="light">{{ s.difficulty }}</el-tag>
              <el-tag size="small" type="warning" effect="plain">{{ s.originLabel }}</el-tag>
            </div>
            <strong style="font-size:14px">{{ s.title }}</strong>
            <p class="card-sub" style="margin:6px 0;line-height:1.7">{{ s.content }}</p>
            <div class="card-sub" style="font-size:12px">
              作者：{{ s.authorName }}<template v-if="s.companyInfo"> · {{ s.companyInfo }}</template>
              <template v-if="s.reviewedAt"> · 审核通过 {{ s.reviewedAt.slice(0, 10) }}</template>
            </div>
            <el-link v-if="s.referenceAnswer" type="primary" style="font-size:12px;margin-top:4px" @click="ElMessage.info('参考答案请在详情查看')">含参考答案</el-link>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="我的投稿" name="mine">
        <el-empty v-if="!mine.length" description="还没有投稿" />
        <div v-else style="display:flex;flex-direction:column;gap:10px">
          <div v-for="s in mine" :key="s.id" class="card">
            <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:8px;flex-wrap:wrap">
              <div>
                <strong style="font-size:14px">{{ s.title }}</strong>
                <el-tag size="small" :type="STATUS_TYPES[s.status]" style="margin-left:8px">{{ STATUS_LABELS[s.status] }}</el-tag>
                <el-tag v-if="s.licenseGrant === 'PUBLIC_DISPLAY'" size="small" type="success" effect="plain" style="margin-left:4px">已授权公开</el-tag>
                <el-tag v-else size="small" type="info" effect="plain" style="margin-left:4px">仅审核</el-tag>
              </div>
              <div style="display:flex;gap:4px">
                <el-button v-if="['DRAFT','REJECTED','WITHDRAWN'].includes(s.status)" size="small" text type="primary" @click="openEditor(s)">编辑</el-button>
                <el-button v-if="['DRAFT','REJECTED','WITHDRAWN'].includes(s.status)" size="small" text type="success" @click="submitReview(s.id)">提交审核</el-button>
                <el-button v-if="s.status !== 'APPROVED'" size="small" text @click="withdraw(s.id)">撤回</el-button>
                <el-button v-if="s.status !== 'APPROVED'" size="small" text type="danger" @click="remove(s.id)">删除</el-button>
              </div>
            </div>
            <el-alert v-if="s.reviewComment" style="margin-top:8px" type="info" :closable="false"
              :title="s.status === 'REJECTED' ? '驳回意见' : '审核意见'" :description="s.reviewComment" />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 投稿编辑器 -->
    <el-dialog v-model="editorVisible" :title="editing ? '编辑投稿' : '投稿题目'" width="620px" top="4vh">
      <el-alert type="warning" :closable="false" style="margin-bottom:12px" show-icon title="投稿须知"
        description="请勿提交个人隐私、他人隐私、账号令牌、受 NDA 约束或无权转载的内容；分享面试经历请匿名化概括。你需确认有权提交所投内容。" />
      <el-form label-width="100px" label-position="left">
        <el-form-item label="标题" required><el-input v-model="form.title" maxlength="300" /></el-form-item>
        <el-form-item label="分类" required>
          <el-select v-model="form.category" style="width:100%">
            <el-option v-for="(l, c) in CATEGORY_LABELS" :key="c" :label="l" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="form.questionType" style="width:100%">
            <el-option v-for="(l, t) in TYPE_LABELS" :key="t" :label="l" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-radio-group v-model="form.difficulty">
            <el-radio value="EASY">基础</el-radio><el-radio value="MEDIUM">进阶</el-radio><el-radio value="HARD">挑战</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="题目内容" required><el-input v-model="form.content" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="我的答案"><el-input v-model="form.myAnswer" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="参考答案"><el-input v-model="form.referenceAnswer" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="公司/岗位"><el-input v-model="form.companyInfo" placeholder="选填，建议匿名化（如：某大厂实习）" /></el-form-item>
        <el-form-item label="参考链接"><el-input v-model="form.referenceUrl" placeholder="https://..." /></el-form-item>
        <el-form-item label="匿名展示"><el-switch v-model="form.anonymous" /></el-form-item>
        <el-form-item label="授权方式" required>
          <el-radio-group v-model="form.licenseGrant">
            <el-radio value="REVIEW_ONLY">仅提交审核</el-radio>
            <el-radio value="PUBLIC_DISPLAY">授权公开展示</el-radio>
          </el-radio-group>
          <div class="card-sub" style="font-size:11.5px;margin-top:4px">选择「仅审核」的内容不会被发布到公共题库；选择「公开展示」即授权平台展示该内容并保留你的署名选择。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>
