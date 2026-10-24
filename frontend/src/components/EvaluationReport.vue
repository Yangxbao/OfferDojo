<script setup lang="ts">
import type { EvaluationVO } from '@/api/types'
import { formatDateTime, PROVIDER_LABELS, scoreColor } from '@/utils/format'
import { downloadReportMarkdown } from '@/utils/exportReport'
import { ElMessage } from 'element-plus'

const props = defineProps<{ evaluation: EvaluationVO; taskTitle?: string }>()

function exportReport() {
  if (!props.taskTitle) return
  downloadReportMarkdown(props.taskTitle, props.evaluation)
  ElMessage.success('Markdown 报告已下载')
}
</script>

<template>
  <div class="evaluation-report">
    <!-- 总分与来源 -->
    <div class="report-head card" style="display: flex; align-items: center; gap: 24px; flex-wrap: wrap">
      <el-progress
        type="dashboard"
        :percentage="(evaluation.totalScore / 10) * 100"
        :width="110"
        :color="scoreColor(evaluation.totalScore)"
      >
        <template #default>
          <div style="text-align: center">
            <div style="font-size: 24px; font-weight: 700">{{ evaluation.totalScore.toFixed(1) }}</div>
            <div class="card-sub">总分 / 10</div>
          </div>
        </template>
      </el-progress>
      <div style="flex: 1; min-width: 220px">
        <div style="margin-bottom: 8px">
          <el-tag size="small" :type="evaluation.provider === 'REMOTE' ? 'primary' : 'info'" effect="light">
            {{ PROVIDER_LABELS[evaluation.provider] ?? evaluation.provider }}
          </el-tag>
          <span v-if="evaluation.model" class="card-sub" style="margin-left: 8px">{{ evaluation.model }}</span>
          <span class="card-sub" style="margin-left: 8px">{{ formatDateTime(evaluation.createdAt) }}</span>
          <el-button
            v-if="taskTitle"
            size="small"
            type="primary"
            text
            style="margin-left: 8px"
            @click="exportReport"
          >
            导出 Markdown
          </el-button>
        </div>
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="AI 评分仅供参考，请人工核对技术结论"
          :description="evaluation.notice ?? '评分依据为任务预设的评分标准；技术细节请自行验证后再用于实际工作。'"
        />
      </div>
    </div>

    <!-- 各维度得分 -->
    <div class="card report-section">
      <h3 class="card-title">各维度评分</h3>
      <div v-for="dim in evaluation.dimensionScores" :key="dim.dimension" style="margin-bottom: 14px">
        <div style="display: flex; justify-content: space-between; margin-bottom: 4px">
          <span>
            <strong>{{ dim.dimension }}</strong>
            <el-tag size="small" type="info" effect="plain" style="margin-left: 8px">权重 {{ dim.weight }}%</el-tag>
          </span>
          <span :style="{ color: scoreColor(dim.score), fontWeight: 600 }">{{ dim.score.toFixed(1) }} / 10</span>
        </div>
        <el-progress
          :percentage="dim.score * 10"
          :color="scoreColor(dim.score)"
          :stroke-width="8"
          :show-text="false"
        />
        <div class="card-sub" style="margin-top: 4px">{{ dim.comment }}</div>
      </div>
    </div>

    <div class="grid grid-2 report-section" style="margin-top: 16px">
      <div class="card">
        <h3 class="card-title"><el-icon style="color: #16a34a"><CircleCheck /></el-icon> 答案中的正确做法</h3>
        <ul class="report-list">
          <li v-for="item in evaluation.strengths" :key="item">{{ item }}</li>
        </ul>
      </div>
      <div class="card">
        <h3 class="card-title"><el-icon style="color: #ea580c"><Warning /></el-icon> 存在的问题</h3>
        <ul v-if="evaluation.problems.length" class="report-list">
          <li v-for="item in evaluation.problems" :key="item">{{ item }}</li>
        </ul>
        <el-empty v-else description="没有发现明显问题" :image-size="60" />
      </div>
      <div class="card">
        <h3 class="card-title"><el-icon style="color: #9333ea"><Lightning /></el-icon> 可能遗漏的边界情况</h3>
        <ul v-if="evaluation.edgeCases.length" class="report-list">
          <li v-for="item in evaluation.edgeCases" :key="item">{{ item }}</li>
        </ul>
        <el-empty v-else description="—" :image-size="60" />
      </div>
      <div class="card">
        <h3 class="card-title"><el-icon style="color: #2563eb"><EditPen /></el-icon> 改进建议</h3>
        <ul v-if="evaluation.suggestions.length" class="report-list">
          <li v-for="item in evaluation.suggestions" :key="item">{{ item }}</li>
        </ul>
        <el-empty v-else description="—" :image-size="60" />
      </div>
    </div>

    <!-- 技术追问 -->
    <div class="card report-section">
      <h3 class="card-title"><el-icon><ChatDotRound /></el-icon> 技术追问（建议先自答再看参考）</h3>
      <ol class="report-list">
        <li v-for="item in evaluation.followUps" :key="item">{{ item }}</li>
      </ol>
    </div>

    <!-- 下一步任务 -->
    <div class="card report-section" v-if="evaluation.nextTasks.length">
      <h3 class="card-title"><el-icon><Compass /></el-icon> 建议下一步练习</h3>
      <div class="grid grid-3">
        <router-link
          v-for="task in evaluation.nextTasks"
          :key="task.id"
          :to="`/tasks/${task.id}`"
          class="next-task"
        >
          <div style="font-weight: 600; margin-bottom: 6px">{{ task.title }}</div>
          <div class="card-sub">{{ task.categoryLabel }} · 预计 {{ task.estimatedMinutes }} 分钟</div>
        </router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.next-task {
  display: block;
  padding: 12px 14px;
  border: 1px solid var(--jobsim-border);
  border-radius: 8px;
  color: var(--jobsim-text);
  transition: border-color 0.2s;
}

.next-task:hover {
  border-color: var(--jobsim-primary);
}
</style>
