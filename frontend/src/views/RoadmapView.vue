<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { roadmapApi } from '@/api'
import type { RoadmapStageVO } from '@/api/types'
import { DIFFICULTY_LABELS, DIFFICULTY_TYPES, MY_STATUS_LABELS, MY_STATUS_TYPES } from '@/utils/format'

const loading = ref(true)
const stages = ref<RoadmapStageVO[]>([])

function stageProgress(stage: RoadmapStageVO) {
  if (!stage.tasks.length) return 0
  const done = stage.tasks.filter((t) => t.myStatus === 'COMPLETED').length
  return Math.round((done / stage.tasks.length) * 100)
}

const overallProgress = computed(() => {
  const all = stages.value.flatMap((s) => s.tasks)
  if (!all.length) return 0
  return Math.round((all.filter((t) => t.myStatus === 'COMPLETED').length / all.length) * 100)
})

onMounted(async () => {
  try {
    stages.value = await roadmapApi.get()
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page" v-loading="loading" style="padding-bottom: 24px">
    <div class="page-header">
      <h2>学习路线图</h2>
      <p>
        从零基础到企业实战的分阶段练习路径：先学知识、再做任务、达成里程碑。
        <template v-if="!loading"> 你已完成路线全程的 <strong>{{ overallProgress }}%</strong>。</template>
        <br />
        <span class="card-sub">进度口径：任务「已提交且完成评估」才计入完成，仅保存草稿不计入。</span>
      </p>
    </div>

    <el-empty v-if="!loading && !stages.length" description="路线图暂未配置" />

    <el-timeline v-else class="roadmap">
      <el-timeline-item
        v-for="stage in stages"
        :key="stage.order"
        :timestamp="stage.title"
        placement="top"
        :type="stageProgress(stage) === 100 ? 'success' : stageProgress(stage) > 0 ? 'primary' : undefined"
        :hollow="stageProgress(stage) === 0"
      >
        <div class="card stage-card">
          <div class="stage-head">
            <strong>{{ stage.title }}</strong>
            <el-progress
              :percentage="stageProgress(stage)"
              :stroke-width="8"
              style="width: 140px"
              :show-text="false"
            />
            <span class="card-sub">{{ stageProgress(stage) }}%</span>
          </div>

          <p class="goal">{{ stage.goal }}</p>

          <div class="section-label">先学知识</div>
          <div class="knowledge">
            <el-tag v-for="k in stage.knowledge" :key="k" size="small" type="info" effect="plain">{{ k }}</el-tag>
          </div>

          <div class="section-label">推荐任务（按顺序完成）</div>
          <div class="task-list">
            <router-link
              v-for="(task, index) in stage.tasks"
              :key="task.id"
              :to="`/tasks/${task.id}`"
              class="task-row"
            >
              <span class="order-no">{{ index + 1 }}</span>
              <el-tag size="small" :type="DIFFICULTY_TYPES[task.difficulty]" effect="light">
                {{ DIFFICULTY_LABELS[task.difficulty] ?? task.difficulty }}
              </el-tag>
              <span class="task-title">{{ task.title }}</span>
              <span class="card-sub">约 {{ task.estimatedMinutes }} 分钟</span>
              <el-tooltip
                v-if="task.myStatus === 'DRAFTING'"
                content="草稿尚未提交，不计入阶段进度；提交并完成评估后才算完成"
                placement="top"
              >
                <el-tag size="small" :type="MY_STATUS_TYPES[task.myStatus]" effect="light">
                  {{ MY_STATUS_LABELS[task.myStatus] }}
                </el-tag>
              </el-tooltip>
              <el-tag v-else size="small" :type="MY_STATUS_TYPES[task.myStatus]" effect="light">
                {{ MY_STATUS_LABELS[task.myStatus] }}
              </el-tag>
              <el-tag v-if="task.myBestScore != null" size="small" type="success" effect="plain">
                {{ task.myBestScore.toFixed(1) }} 分
              </el-tag>
            </router-link>
          </div>

          <div class="milestone">
            <el-icon style="color: var(--jobsim-primary)"><Flag /></el-icon>
            <span><strong>里程碑：</strong>{{ stage.milestone }}</span>
          </div>
        </div>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<style scoped>
.stage-card {
  margin-bottom: 6px;
}

.stage-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.goal {
  color: var(--jobsim-text-secondary);
  font-size: 13px;
  line-height: 1.7;
  margin: 0 0 12px;
}

.section-label {
  font-size: 12px;
  color: var(--jobsim-text-secondary);
  margin-bottom: 6px;
  font-weight: 600;
}

.knowledge {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 14px;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 14px;
}

.task-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid var(--jobsim-border);
  border-radius: 8px;
  color: var(--jobsim-text);
  transition: border-color 0.2s;
}

.task-row:hover {
  border-color: var(--jobsim-primary);
}

.order-no {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--jobsim-primary-light);
  color: var(--jobsim-primary);
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.task-title {
  flex: 1;
  font-size: 13px;
  font-weight: 500;
}

.milestone {
  display: flex;
  gap: 6px;
  align-items: flex-start;
  background: var(--jobsim-primary-light);
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.6;
}
</style>
