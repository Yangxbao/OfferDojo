<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { request } from '@/api/http'

interface UserProject {
  id: number
  name: string
  subtitle: string | null
  summary: string
  problemSolved: string | null
  techStack: string
  category: string
  highlights: string | null
  myRole: string | null
  challenges: string | null
  learnings: string | null
  repoUrl: string | null
  demoUrl: string | null
  coverUrl: string | null
  readmeMd: string | null
  aiAssisted: boolean
  aiUsageNote: string | null
  ghStars: number | null
  ghFetchedAt: string | null
  status: string
  reviewComment: string | null
  updatedAt: string
}

const loading = ref(true)
const tab = ref<'mine' | 'explore'>('mine')
const myProjects = ref<UserProject[]>([])
const publicProjects = ref<UserProject[]>([])
const categories = ref<string[]>([])
const editing = ref<UserProject | null>(null)
const editorVisible = ref(false)
const saving = ref(false)
const exploreQuery = reactive({ category: '', keyword: '', orderBy: 'recent' })

const CATEGORY_LABELS: Record<string, string> = {
  'java-backend': 'Java 后端', frontend: '前端', python: 'Python', 'ai-agent': 'AI / Agent',
  rag: 'RAG', database: '数据库', distributed: '分布式', devops: 'DevOps', testing: '测试', 'open-source': '开源实战',
}
const STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿', PENDING_REVIEW: '待审核', APPROVED: '已发布', REJECTED: '已驳回', WITHDRAWN: '已撤回',
}
const STATUS_TYPES: Record<string, string> = {
  DRAFT: 'info', PENDING_REVIEW: 'warning', APPROVED: 'success', REJECTED: 'danger', WITHDRAWN: 'info',
}

const form = reactive<Partial<UserProject>>({})

async function loadMine() {
  myProjects.value = (await request<{ list: UserProject[] }>({ url: '/my/projects', method: 'get', params: { size: 50 } })).list
}
async function loadPublic() {
  const r = await request<{ list: UserProject[] }>({ url: '/projects', method: 'get', params: { ...exploreQuery, size: 50 } })
  publicProjects.value = r.list
}

function openEditor(item?: UserProject) {
  editing.value = item ?? null
  Object.assign(form, item ?? { aiAssisted: false }, { aiAssisted: item?.aiAssisted ?? false })
  editorVisible.value = true
}

async function save() {
  saving.value = true
  try {
    if (editing.value) {
      await request({ url: `/my/projects/${editing.value.id}`, method: 'put', data: form })
    } else {
      await request({ url: '/my/projects', method: 'post', data: form })
    }
    ElMessage.success('已保存')
    editorVisible.value = false
    await loadMine()
  } finally { saving.value = false }
}

async function submitForReview(id: number) {
  await request({ url: `/my/projects/${id}/submit`, method: 'put' })
  ElMessage.success('已提交审核')
  await loadMine()
}

async function withdraw(id: number) {
  await request({ url: `/my/projects/${id}/withdraw`, method: 'put' })
  ElMessage.success('已撤回')
  await loadMine()
}

async function remove(id: number) {
  await ElMessageBox.confirm('确定删除这个项目草稿？', '确认')
  await request({ url: `/my/projects/${id}`, method: 'delete' })
  ElMessage.success('已删除')
  await loadMine()
}

onMounted(async () => {
  try {
    categories.value = await request<string[]>({ url: '/projects/categories', method: 'get' })
    await Promise.all([loadMine(), loadPublic()])
  } finally { loading.value = false }
})
</script>

<template>
  <div class="page" v-loading="loading" style="padding-bottom: 24px">
    <div class="page-header" style="display:flex;justify-content:space-between;align-items:flex-start;flex-wrap:wrap;gap:12px">
      <div>
        <h2>项目广场</h2>
        <p>展示你真正参与完成的项目，积累面试作品集。Star 数据由平台从 GitHub 获取，无法伪造。</p>
      </div>
      <el-button type="primary" @click="openEditor()">+ 投稿项目</el-button>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="我的项目" name="mine">
        <el-empty v-if="!myProjects.length" description="还没有项目，点击右上角投稿第一个">
          <el-button type="primary" @click="openEditor()">投稿项目</el-button>
        </el-empty>
        <div v-else style="display:flex;flex-direction:column;gap:12px">
          <div v-for="p in myProjects" :key="p.id" class="card">
            <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:8px;flex-wrap:wrap">
              <div>
                <strong style="font-size:15px">{{ p.name }}</strong>
                <el-tag size="small" :type="STATUS_TYPES[p.status]" style="margin-left:8px">{{ STATUS_LABELS[p.status] }}</el-tag>
                <el-tag v-if="p.ghStars != null" size="small" type="warning" effect="plain" style="margin-left:4px">⭐{{ p.ghStars }}</el-tag>
              </div>
              <div style="display:flex;gap:4px;flex-wrap:wrap">
                <el-button v-if="['DRAFT','REJECTED','WITHDRAWN'].includes(p.status)" size="small" text type="primary" @click="openEditor(p)">编辑</el-button>
                <el-button v-if="['DRAFT','REJECTED','WITHDRAWN'].includes(p.status)" size="small" text type="success" @click="submitForReview(p.id)">提交审核</el-button>
                <el-button v-if="!['APPROVED'].includes(p.status)" size="small" text @click="withdraw(p.id)">撤回</el-button>
                <el-button v-if="!['APPROVED'].includes(p.status)" size="small" text type="danger" @click="remove(p.id)">删除</el-button>
              </div>
            </div>
            <p class="card-sub" style="margin:6px 0 4px">{{ p.summary }}</p>
            <div class="card-sub" style="font-size:12px">{{ p.techStack }} · {{ CATEGORY_LABELS[p.category] ?? p.category }} · 更新 {{ p.updatedAt?.slice(0, 10) }}</div>
            <el-alert v-if="p.reviewComment" style="margin-top:8px" type="info" :closable="false"
              :title="p.status === 'REJECTED' ? '驳回意见' : '审核意见'" :description="p.reviewComment" />
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="公共展示区" name="explore">
        <div class="card" style="margin-bottom:12px;display:flex;gap:10px;flex-wrap:wrap;align-items:center">
          <el-select v-model="exploreQuery.category" placeholder="全部分类" clearable style="width:140px" @change="loadPublic">
            <el-option v-for="c in categories" :key="c" :label="CATEGORY_LABELS[c] ?? c" :value="c" />
          </el-select>
          <el-input v-model="exploreQuery.keyword" placeholder="搜索名称/技术栈" clearable style="width:200px" @change="loadPublic" />
          <el-select v-model="exploreQuery.orderBy" style="width:130px" @change="loadPublic">
            <el-option label="最新收录" value="recent" />
            <el-option label="Star 数" value="stars" />
          </el-select>
        </div>
        <el-empty v-if="!publicProjects.length" description="暂无公开项目——成为第一个投稿者！" />
        <div v-else class="grid grid-3">
          <div v-for="p in publicProjects" :key="p.id" class="card">
            <strong style="font-size:14px">{{ p.name }}</strong>
            <p class="card-sub" style="margin:6px 0;line-height:1.6;font-size:12.5px">{{ p.summary }}</p>
            <div class="card-sub" style="font-size:12px">{{ CATEGORY_LABELS[p.category] ?? p.category }} · {{ p.techStack }}</div>
            <div v-if="p.ghStars != null" class="card-sub" style="font-size:12px;margin-top:2px">⭐ {{ p.ghStars }}（平台从 GitHub 获取）</div>
            <div style="margin-top:8px;display:flex;gap:8px">
              <el-link v-if="p.repoUrl" :href="p.repoUrl" target="_blank" type="primary" style="font-size:12px">仓库</el-link>
              <el-link v-if="p.demoUrl" :href="p.demoUrl" target="_blank" type="primary" style="font-size:12px">演示</el-link>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 投稿编辑器 -->
    <el-dialog v-model="editorVisible" :title="editing ? '编辑项目' : '投稿项目'" width="640px" top="4vh">
      <el-form label-width="110px" label-position="left">
        <el-form-item label="项目名称" required><el-input v-model="form.name" maxlength="100" /></el-form-item>
        <el-form-item label="副标题"><el-input v-model="form.subtitle" maxlength="200" /></el-form-item>
        <el-form-item label="项目简介" required><el-input v-model="form.summary" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="解决的问题"><el-input v-model="form.problemSolved" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="技术栈" required><el-input v-model="form.techStack" placeholder="如 Java,Spring Boot,Vue" /></el-form-item>
        <el-form-item label="分类" required>
          <el-select v-model="form.category" style="width:100%">
            <el-option v-for="c in categories" :key="c" :label="CATEGORY_LABELS[c] ?? c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="我的职责"><el-input v-model="form.myRole" type="textarea" :rows="2" placeholder="写你实际负责/理解的部分" /></el-form-item>
        <el-form-item label="遇到的困难"><el-input v-model="form.challenges" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="个人收获"><el-input v-model="form.learnings" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="仓库地址"><el-input v-model="form.repoUrl" placeholder="https://github.com/..." /></el-form-item>
        <el-form-item label="演示地址"><el-input v-model="form.demoUrl" /></el-form-item>
        <el-form-item label="AI 辅助">
          <el-switch v-model="form.aiAssisted" />
          <el-input v-if="form.aiAssisted" v-model="form.aiUsageNote" placeholder="说明 AI 辅助的范围（如：辅助测试编写）" style="margin-top:6px" />
        </el-form-item>
        <el-form-item label="项目说明"><el-input v-model="form.readmeMd" type="textarea" :rows="5" placeholder="Markdown 格式的项目说明（可选）" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>
