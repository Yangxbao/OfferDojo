<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { taskApi } from '@/api'
import type { SubmissionDetailVO, TaskDetailVO } from '@/api/types'
import EvaluationReport from '@/components/EvaluationReport.vue'
import FollowupDialog from '@/components/FollowupDialog.vue'
import { DIFFICULTY_LABELS, DIFFICULTY_TYPES, formatDateTime, fromNow, MY_STATUS_LABELS, MY_STATUS_TYPES } from '@/utils/format'

const route = useRoute()
const taskId = Number(route.params.id)

const loading = ref(true)
const task = ref<TaskDetailVO | null>(null)
const content = ref('')
const submitting = ref(false)
const savingDraft = ref(false)
const draftId = ref<number | null>(null)
const lastSavedAt = ref<string | null>(null)
const result = ref<SubmissionDetailVO | null>(null)

// 已有评估结果时默认展示报告页签，否则展示作答页签
const activeTab = ref<'answer' | 'report'>('answer')

async function load() {
  loading.value = true
  try {
    task.value = await taskApi.detail(taskId)
    if (task.value.myDraftId && task.value.myDraftContent) {
      draftId.value = task.value.myDraftId
      content.value = task.value.myDraftContent
    }
    if (task.value.myLatestSubmissionId) {
      const detail = await import('@/api').then((m) => m.submissionApi.detail(task.value!.myLatestSubmissionId!))
      if (detail.evaluation) {
        result.value = detail
        activeTab.value = 'report'
      }
    }
  } finally {
    loading.value = false
  }
}

async function saveDraft(silent = false) {
  if (!content.value.trim()) {
    if (!silent) ElMessage.warning('先写点内容再保存草稿')
    return
  }
  savingDraft.value = true
  try {
    draftId.value = await taskApi.saveDraft(taskId, content.value)
    lastSavedAt.value = new Date().toISOString()
    if (!silent) ElMessage.success('草稿已保存')
  } finally {
    savingDraft.value = false
  }
}

// 每 30 秒自动保存一次草稿（内容有变化时才请求）
let timer: number | null = null
let dirtySinceSave = false
function onInput() {
  dirtySinceSave = true
}
onMounted(() => {
  load()
  timer = window.setInterval(() => {
    if (dirtySinceSave && content.value.trim()) {
      dirtySinceSave = false
      saveDraft(true)
    }
  }, 30000)
})
onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})

async function submit() {
  if (!content.value.trim()) {
    ElMessage.warning('答案内容不能为空')
    return
  }
  submitting.value = true
  try {
    result.value = await taskApi.submit(taskId, content.value)
    activeTab.value = 'report'
    ElMessage.success('提交成功，评估已完成')
  } finally {
    submitting.value = false
  }
}

const wordCount = computed(() => content.value.length)
</script>

<template>
  <div class="page" v-loading="loading">
    <template v-if="task">
      <div class="page-header" style="display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; flex-wrap: wrap">
        <div>
          <div style="display: flex; gap: 8px; margin-bottom: 8px; flex-wrap: wrap">
            <el-tag size="small" effect="plain">{{ task.code }}</el-tag>
            <el-tag size="small" :type="DIFFICULTY_TYPES[task.difficulty]" effect="light">
              {{ DIFFICULTY_LABELS[task.difficulty] }}
            </el-tag>
            <el-tag size="small" :type="MY_STATUS_TYPES[task.myStatus]" effect="light">
              {{ MY_STATUS_LABELS[task.myStatus] }}
            </el-tag>
            <el-tag v-for="tag in task.tags" :key="tag" size="small" type="info" effect="plain">{{ tag }}</el-tag>
          </div>
          <h2>{{ task.title }}</h2>
          <p>{{ task.categoryLabel }} · 预计 {{ task.estimatedMinutes }} 分钟 · 截止前请保存草稿</p>
        </div>
        <el-button v-if="result?.evaluation" @click="activeTab = activeTab === 'report' ? 'answer' : 'report'">
          {{ activeTab === 'report' ? '返回作答' : '查看最新评估报告' }}
        </el-button>
      </div>

      <el-tabs v-model="activeTab">
        <!-- ==================== 作答页签 ==================== -->
        <el-tab-pane label="任务与作答" name="answer">
          <div class="grid" style="grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr); align-items: start">
            <!-- 任务内容 -->
            <div class="card">
              <h3 class="card-title">任务背景</h3>
              <p style="line-height: 1.8; white-space: pre-wrap; margin-top: 0">{{ task.background }}</p>

              <h3 class="card-title" style="margin-top: 20px">业务需求</h3>
              <ul class="report-list">
                <li v-for="item in task.requirements" :key="item">{{ item }}</li>
              </ul>

              <h3 class="card-title" style="margin-top: 20px">约束条件</h3>
              <ul class="report-list">
                <li v-for="item in task.constraintItems" :key="item">{{ item }}</li>
              </ul>

              <h3 class="card-title" style="margin-top: 20px">预期交付物</h3>
              <ul class="report-list">
                <li v-for="item in task.deliverables" :key="item">{{ item }}</li>
              </ul>

              <h3 class="card-title" style="margin-top: 20px">验收标准</h3>
              <ul class="report-list">
                <li v-for="item in task.acceptance" :key="item">{{ item }}</li>
              </ul>

              <h3 class="card-title" style="margin-top: 20px">评分标准</h3>
              <el-table :data="task.rubric" size="small">
                <el-table-column prop="dimension" label="维度" width="110" />
                <el-table-column prop="weight" label="权重" width="70">
                  <template #default="{ row }">{{ row.weight }}%</template>
                </el-table-column>
                <el-table-column prop="guidance" label="评分指引" min-width="240" />
              </el-table>
            </div>

            <!-- 作答区 -->
            <div class="card">
              <h3 class="card-title">
                我的答案
                <span class="card-sub">{{ wordCount }} 字</span>
              </h3>
              <el-input
                v-model="content"
                type="textarea"
                :rows="18"
                maxlength="20000"
                show-word-limit
                placeholder="用 Markdown 组织你的答案：先给结论，再给细节（接口定义 / SQL / 步骤）。支持列表与代码块。"
                @input="onInput"
              />
              <div style="margin-top: 12px; display: flex; gap: 10px; align-items: center; flex-wrap: wrap">
                <el-button type="primary" :loading="submitting" @click="submit">
                  {{ submitting ? '评估中…' : '提交并获取 AI 评估' }}
                </el-button>
                <el-button :loading="savingDraft" @click="saveDraft()">保存草稿</el-button>
                <span class="card-sub">
                  {{ lastSavedAt ? `草稿已保存 ${fromNow(lastSavedAt)}` : '草稿每 30 秒自动保存' }}
                </span>
              </div>
              <el-alert
                style="margin-top: 14px"
                type="info"
                :closable="false"
                show-icon
                title="提示"
                description="提交后会立即返回评估结果；重新提交会生成一次新的练习记录，历史记录都会保留。"
              />
            </div>
          </div>
        </el-tab-pane>

        <!-- ==================== 评估报告页签 ==================== -->
        <el-tab-pane v-if="result?.evaluation" :label="`评估报告`" name="report">
          <EvaluationReport :evaluation="result.evaluation" :task-title="task.title" />
          <FollowupDialog
            v-if="result.evaluation"
            :evaluation-id="result.evaluation.id"
            :follow-ups="result.evaluation.followUps"
            style="margin-top: 16px"
          />
          <div class="card-sub" style="margin: 12px 0 24px">
            提交于 {{ formatDateTime(result.updatedAt) }} · 重新提交会生成新的评估报告 ·
            <router-link :to="`/bagu?category=${task.category}`">复习本类目的八股题 →</router-link>
          </div>
        </el-tab-pane>
      </el-tabs>
    </template>
  </div>
</template>

<style scoped>
@media (max-width: 900px) {
  .grid {
    grid-template-columns: 1fr !important;
  }
}
</style>
