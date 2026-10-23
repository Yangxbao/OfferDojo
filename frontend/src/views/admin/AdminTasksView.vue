<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api'
import type { AdminTaskVO, PageResult, RubricItem, TaskSaveRequest } from '@/api/types'
import { CATEGORY_LABELS, DIFFICULTY_LABELS, formatDateTime } from '@/utils/format'

/** 全系统固定的六个能力维度（与后端 AbilityCalculator 保持一致） */
const ALL_DIMENSIONS = ['需求理解', '方案设计', '问题定位', '正确性', '工程规范', '沟通表达']

const loading = ref(true)
const page = ref(1)
const size = 10
const total = ref(0)
const list = ref<AdminTaskVO[]>([])
const query = reactive({ category: '', status: '', keyword: '' })

const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref()

const form = reactive<TaskSaveRequest>(emptyForm())

function emptyForm(): TaskSaveRequest {
  return {
    code: '',
    title: '',
    category: 'api-design',
    difficulty: 'EASY',
    tags: [],
    summary: '',
    estimatedMinutes: 30,
    background: '',
    requirements: [],
    constraintItems: [],
    deliverables: [],
    acceptance: [],
    rubric: [],
    status: 'PUBLISHED',
    sortOrder: 0,
  }
}

/** 评分标准编辑的临时状态：勾选维度 + 权重 + 指引 */
const rubricDraft = reactive(
  Object.fromEntries(
    ALL_DIMENSIONS.map((d) => [d, { enabled: false, weight: 20, guidance: '' }]),
  ) as Record<string, { enabled: boolean; weight: number; guidance: string }>,
)

const rubricTotalWeight = computed(() =>
  ALL_DIMENSIONS.filter((d) => rubricDraft[d].enabled).reduce((sum, d) => sum + (rubricDraft[d].weight || 0), 0),
)

function buildRubric(): RubricItem[] {
  return ALL_DIMENSIONS.filter((d) => rubricDraft[d].enabled).map((d) => ({
    dimension: d,
    weight: rubricDraft[d].weight,
    guidance: rubricDraft[d].guidance,
  }))
}

function fillRubricDraft(rubric: RubricItem[]) {
  ALL_DIMENSIONS.forEach((d) => {
    const found = rubric.find((r) => r.dimension === d)
    rubricDraft[d].enabled = !!found
    rubricDraft[d].weight = found?.weight ?? 20
    rubricDraft[d].guidance = found?.guidance ?? ''
  })
}

/** 文本框一行一条 ↔ 字符串数组 */
function linesToArr(text: string): string[] {
  return text.split('\n').map((s) => s.trim()).filter(Boolean)
}
function arrToLines(arr: string[]): string {
  return (arr ?? []).join('\n')
}
const textFields = reactive({
  requirements: '',
  constraintItems: '',
  deliverables: '',
  acceptance: '',
})

const rules = {
  code: [
    { required: true, message: '请输入任务编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9-]{2,32}$/, message: '字母、数字或中划线', trigger: 'blur' },
  ],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  summary: [{ required: true, message: '请输入简介', trigger: 'blur' }],
  background: [{ required: true, message: '请输入任务背景', trigger: 'blur' }],
  estimatedMinutes: [{ required: true, message: '请输入预计用时', trigger: 'blur' }],
}

async function load() {
  loading.value = true
  try {
    const result: PageResult<AdminTaskVO> = await adminApi.taskPage({
      category: query.category || undefined,
      status: query.status || undefined,
      keyword: query.keyword || undefined,
      page: page.value,
      size,
    })
    list.value = result.list
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, emptyForm())
  textFields.requirements = ''
  textFields.constraintItems = ''
  textFields.deliverables = ''
  textFields.acceptance = ''
  fillRubricDraft([])
  dialogVisible.value = true
}

async function openEdit(row: AdminTaskVO) {
  editingId.value = row.id
  const detail = await adminApi.taskDetail(row.id)
  Object.assign(form, detail)
  textFields.requirements = arrToLines(detail.requirements)
  textFields.constraintItems = arrToLines(detail.constraintItems)
  textFields.deliverables = arrToLines(detail.deliverables)
  textFields.acceptance = arrToLines(detail.acceptance)
  fillRubricDraft(detail.rubric)
  dialogVisible.value = true
}

async function save() {
  await formRef.value?.validate().catch(() => Promise.reject(new Error('校验失败')))
  form.requirements = linesToArr(textFields.requirements)
  form.constraintItems = linesToArr(textFields.constraintItems)
  form.deliverables = linesToArr(textFields.deliverables)
  form.acceptance = linesToArr(textFields.acceptance)
  form.rubric = buildRubric()
  if (!form.rubric.length) {
    ElMessage.warning('至少勾选一个评分维度')
    return
  }
  if (rubricTotalWeight.value !== 100) {
    ElMessage.warning('评分维度权重之和必须等于 100')
    return
  }
  saving.value = true
  try {
    if (editingId.value == null) {
      await adminApi.createTask(form)
      ElMessage.success('任务已创建')
    } else {
      await adminApi.updateTask(editingId.value, form)
      ElMessage.success('任务已更新')
    }
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: AdminTaskVO) {
  const next = row.status === 'PUBLISHED' ? 'OFFLINE' : 'PUBLISHED'
  await ElMessageBox.confirm(
    next === 'OFFLINE' ? '下架后用户端将看不到该任务，已产生的记录保留。确认下架？' : '确认重新上架该任务？',
    '确认操作',
  )
  await adminApi.updateTaskStatus(row.id, next)
  ElMessage.success(next === 'OFFLINE' ? '已下架' : '已上架')
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header" style="display: flex; justify-content: space-between; align-items: flex-start">
      <div>
        <h2>任务管理</h2>
        <p>新建、编辑、上下架练习任务（评分标准权重之和需为 100）</p>
      </div>
      <el-button type="primary" @click="openCreate">新建任务</el-button>
    </div>

    <div class="card" style="margin-bottom: 16px">
      <div style="display: flex; gap: 12px; flex-wrap: wrap">
        <el-select v-model="query.category" placeholder="全部类目" clearable style="width: 190px" @change="page = 1; load()">
          <el-option v-for="(label, code) in CATEGORY_LABELS" :key="code" :label="label" :value="code" />
        </el-select>
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px" @change="page = 1; load()">
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="已下架" value="OFFLINE" />
          <el-option label="草稿" value="DRAFT" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="按标题搜索" clearable style="width: 200px" @change="page = 1; load()" />
      </div>
    </div>

    <div class="card" v-loading="loading" style="margin-bottom: 24px">
      <el-table :data="list" style="width: 100%">
        <el-table-column prop="code" label="编码" width="90" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="categoryLabel" label="类目" width="150" />
        <el-table-column label="难度" width="70">
          <template #default="{ row }">{{ DIFFICULTY_LABELS[row.difficulty] ?? row.difficulty }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'PUBLISHED' ? 'success' : row.status === 'OFFLINE' ? 'info' : 'warning'" effect="light">
              {{ row.status === 'PUBLISHED' ? '已发布' : row.status === 'OFFLINE' ? '已下架' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="练习统计" width="160">
          <template #default="{ row }">
            <span class="card-sub">
              {{ row.attempts }} 次 / {{ row.userCount }} 人
              <template v-if="row.avgScore != null"> · 均分 {{ row.avgScore }}</template>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="140">
          <template #default="{ row }">
            <span class="card-sub">{{ formatDateTime(row.updatedAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" style="margin-right: 12px" @click="openEdit(row)">编辑</el-link>
            <el-link :type="row.status === 'PUBLISHED' ? 'danger' : 'success'" :underline="false" @click="toggleStatus(row)">
              {{ row.status === 'PUBLISHED' ? '下架' : '上架' }}
            </el-link>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="total > size" style="display: flex; justify-content: center; margin-top: 16px">
        <el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="prev, pager, next" @current-change="load" />
      </div>
    </div>

    <!-- 新建 / 编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="editingId == null ? '新建任务' : `编辑任务：${form.code}`" width="760px" top="5vh">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px" label-position="left">
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px">
          <el-form-item label="任务编码" prop="code">
            <el-input v-model="form.code" placeholder="如 API-013" :disabled="editingId != null" />
          </el-form-item>
          <el-form-item label="预计用时" prop="estimatedMinutes">
            <el-input-number v-model="form.estimatedMinutes" :min="5" :max="480" style="width: 100%" />
          </el-form-item>
          <el-form-item label="任务标题" prop="title" style="grid-column: span 2">
            <el-input v-model="form.title" />
          </el-form-item>
          <el-form-item label="类目">
            <el-select v-model="form.category" style="width: 100%">
              <el-option v-for="(label, code) in CATEGORY_LABELS" :key="code" :label="label" :value="code" />
            </el-select>
          </el-form-item>
          <el-form-item label="难度">
            <el-select v-model="form.difficulty" style="width: 100%">
              <el-option v-for="(label, code) in DIFFICULTY_LABELS" :key="code" :label="label" :value="code" />
            </el-select>
          </el-form-item>
          <el-form-item label="标签" style="grid-column: span 2">
            <el-input v-model="form.tags" placeholder="多个标签用英文逗号分隔，如：REST,接口设计" @change="form.tags = ($event as string).split(',').map(s => s.trim()).filter(Boolean)" />
          </el-form-item>
          <el-form-item label="一句话简介" prop="summary" style="grid-column: span 2">
            <el-input v-model="form.summary" maxlength="500" show-word-limit />
          </el-form-item>
        </div>

        <el-form-item label="任务背景" prop="background">
          <el-input v-model="form.background" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="业务需求">
          <el-input v-model="textFields.requirements" type="textarea" :rows="4" placeholder="一行一条" />
        </el-form-item>
        <el-form-item label="约束条件">
          <el-input v-model="textFields.constraintItems" type="textarea" :rows="3" placeholder="一行一条" />
        </el-form-item>
        <el-form-item label="交付物">
          <el-input v-model="textFields.deliverables" type="textarea" :rows="3" placeholder="一行一条" />
        </el-form-item>
        <el-form-item label="验收标准">
          <el-input v-model="textFields.acceptance" type="textarea" :rows="3" placeholder="一行一条" />
        </el-form-item>

        <el-form-item label="评分标准">
          <div style="width: 100%">
            <div v-for="d in ALL_DIMENSIONS" :key="d" style="display: flex; gap: 10px; margin-bottom: 8px; align-items: center">
              <el-checkbox v-model="rubricDraft[d].enabled" :label="d" style="width: 90px" />
              <el-input-number v-model="rubricDraft[d].weight" :min="1" :max="100" size="small" style="width: 90px" :disabled="!rubricDraft[d].enabled" />
              <el-input v-model="rubricDraft[d].guidance" size="small" placeholder="评分指引：什么样的答案在这个维度得高分" :disabled="!rubricDraft[d].enabled" />
            </div>
            <div class="card-sub">权重合计：{{ rubricTotalWeight }}%（需等于 100）</div>
          </div>
        </el-form-item>

        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="PUBLISHED">发布</el-radio>
            <el-radio value="DRAFT">存为草稿</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
