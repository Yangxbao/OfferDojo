<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { careerApi, progressApi } from '@/api'
import type { ProgressVO } from '@/api/types'
import AbilityRadar from '@/components/AbilityRadar.vue'
import ScoreTrend from '@/components/ScoreTrend.vue'
import { scoreColor } from '@/utils/format'

const loading = ref(true)
const data = ref<ProgressVO | null>(null)

const resumeLoading = ref(false)
const resumeMarkdown = ref('')
const jdLoading = ref(false)
const jdText = ref('')
const jdResult = ref<{
  tasks: { task: { id: number; title: string }; score: number; matched: string[] }[]
  bagu: { id: number; title: string; categoryLabel: string; score: number; matched: string[] }[]
  totalTaskHits: number
  totalBaguHits: number
} | null>(null)

async function genResume() {
  resumeLoading.value = true
  try {
    const result = await careerApi.resumeMaterial()
    resumeMarkdown.value = result.markdown
  } finally {
    resumeLoading.value = false
  }
}

async function copyResume() {
  await navigator.clipboard.writeText(resumeMarkdown.value)
  ElMessage.success('已复制到剪贴板')
}

function downloadResume() {
  const blob = new Blob([resumeMarkdown.value], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = '简历素材草稿.md'
  a.click()
  URL.revokeObjectURL(url)
}

async function matchJd() {
  jdLoading.value = true
  try {
    jdResult.value = await careerApi.matchJd(jdText.value)
  } finally {
    jdLoading.value = false
  }
}

onMounted(async () => {
  try {
    data.value = await progressApi.get()
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page" v-loading="loading" style="padding-bottom: 24px">
    <template v-if="data">
      <div class="page-header">
        <h2>能力分析</h2>
        <p>统计基于你的真实练习与评估数据，AI 评分仅供参考</p>
      </div>

      <!-- 总览指标 -->
      <div class="grid grid-4" style="margin-bottom: 16px">
        <div class="card">
          <div class="stat-value">{{ data.completion.completedTasks }}</div>
          <div class="stat-label">已完成任务</div>
        </div>
        <div class="card">
          <div class="stat-value">{{ data.completion.publishedTasks }}</div>
          <div class="stat-label">已发布任务</div>
        </div>
        <div class="card">
          <div class="stat-value" :style="{ color: scoreColor(data.completion.rate / 10) }">
            {{ data.completion.rate }}%
          </div>
          <div class="stat-label">任务完成率</div>
        </div>
        <div class="card">
          <div class="stat-value">{{ data.trend.length ? data.trend[data.trend.length - 1].avgScore : '—' }}</div>
          <div class="stat-label">最近一次练习均分</div>
        </div>
      </div>

      <!-- 趋势 + 雷达 -->
      <div class="grid grid-2" style="margin-bottom: 16px">
        <div class="card">
          <h3 class="card-title">评分趋势（近 30 天）</h3>
          <ScoreTrend v-if="data.trend.length" :points="data.trend" />
          <el-empty v-else description="还没有练习数据" :image-size="70" />
        </div>
        <div class="card">
          <h3 class="card-title">能力雷达</h3>
          <AbilityRadar :abilities="data.abilities" />
        </div>
      </div>

      <!-- 薄弱环节 + 类目完成率 -->
      <div class="grid grid-2" style="margin-bottom: 16px">
        <div class="card">
          <h3 class="card-title">薄弱环节与建议</h3>
          <el-empty v-if="!data.weaknesses.length" description="暂无明显薄弱维度，继续保持" :image-size="70" />
          <div v-for="w in data.weaknesses" :key="w.dimension" style="margin-bottom: 16px">
            <div style="display: flex; justify-content: space-between; margin-bottom: 4px">
              <strong>{{ w.dimension }}</strong>
              <span class="card-sub">练习 {{ w.practiceCount }} 次 · 均分 {{ w.avgScore.toFixed(1) }}</span>
            </div>
            <div class="card-sub" style="line-height: 1.7">{{ w.advice }}</div>
          </div>
        </div>
        <div class="card">
          <h3 class="card-title">各类目完成情况</h3>
          <div v-for="c in data.completion.categories" :key="c.category" style="margin-bottom: 12px">
            <div style="display: flex; justify-content: space-between; margin-bottom: 4px">
              <span>{{ c.label }}</span>
              <span class="card-sub">{{ c.completed }} / {{ c.published }}</span>
            </div>
            <el-progress
              :percentage="c.published ? (c.completed / c.published) * 100 : 0"
              :stroke-width="8"
              :show-text="false"
            />
          </div>
        </div>
      </div>

      <!-- 建议任务 -->
      <div class="card">
        <h3 class="card-title">基于薄弱环节的任务建议</h3>
        <el-empty v-if="!data.suggestions.length" description="全部任务已完成" :image-size="70" />
        <div class="grid grid-3">
          <router-link
            v-for="task in data.suggestions"
            :key="task.id"
            :to="`/tasks/${task.id}`"
            class="suggest-item"
          >
            <div style="font-weight: 600; margin-bottom: 6px">{{ task.title }}</div>
            <div class="card-sub">{{ task.categoryLabel }} · 预计 {{ task.estimatedMinutes }} 分钟</div>
          </router-link>
        </div>
      </div>

      <!-- 求职闭环：简历素材 + JD 匹配 -->
      <div class="grid grid-2" style="margin-top: 16px">
        <div class="card">
          <h3 class="card-title">
            简历素材草稿
            <el-button size="small" type="primary" :loading="resumeLoading" @click="genResume">生成</el-button>
          </h3>
          <p class="card-sub" style="margin-top: 0">
            基于你的真实练习与评估记录自动生成（练习口径），请人工改写后再用于简历。
          </p>
          <el-input
            v-if="resumeMarkdown"
            v-model="resumeMarkdown"
            type="textarea"
            :rows="12"
            style="margin-top: 10px; font-size: 12.5px"
          />
          <div v-if="resumeMarkdown" style="margin-top: 10px; display: flex; gap: 8px">
            <el-button size="small" @click="copyResume">复制 Markdown</el-button>
            <el-button size="small" @click="downloadResume">下载 .md</el-button>
          </div>
          <el-empty v-if="!resumeMarkdown && !resumeLoading" description="点击「生成」按钮试试" :image-size="60" />
        </div>

        <div class="card">
          <h3 class="card-title">JD 反向匹配</h3>
          <p class="card-sub" style="margin-top: 0">
            粘贴目标岗位的职位描述，按标签与关键词命中推荐需要补的任务和高频八股。
          </p>
          <el-input
            v-model="jdText"
            type="textarea"
            :rows="6"
            maxlength="10000"
            placeholder="粘贴岗位 JD，例如：熟悉 MySQL 索引优化与事务，了解 Redis 缓存，掌握 Spring Boot…"
            style="margin-top: 10px"
          />
          <el-button
            type="primary"
            style="margin-top: 10px"
            :loading="jdLoading"
            :disabled="!jdText.trim()"
            @click="matchJd"
          >
            匹配我的短板
          </el-button>

          <template v-if="jdResult">
            <div class="section-label" style="margin-top: 14px">
              建议补的任务（命中 {{ jdResult.totalTaskHits }} 个）
            </div>
            <div v-for="hit in jdResult.tasks" :key="hit.task.id" class="jd-hit">
              <router-link :to="`/tasks/${hit.task.id}`" class="jd-link">{{ hit.task.title }}</router-link>
              <div class="card-sub">命中：{{ hit.matched.join('、') }}</div>
            </div>
            <div v-if="!jdResult.tasks.length" class="card-sub">任务没有直接命中的关键词</div>
            <div class="section-label" style="margin-top: 12px">
              建议复习的高频八股（命中 {{ jdResult.totalBaguHits }} 个）
            </div>
            <div v-for="hit in jdResult.bagu" :key="hit.id" class="jd-hit">
              <router-link to="/bagu" class="jd-link">{{ hit.title }}</router-link>
              <div class="card-sub">{{ hit.categoryLabel }} · 命中：{{ hit.matched.join('、') }}</div>
            </div>
            <div v-if="!jdResult.bagu.length" class="card-sub">高频八股没有直接命中的关键词</div>
          </template>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.suggest-item {
  display: block;
  padding: 12px 14px;
  border: 1px solid var(--jobsim-border);
  border-radius: 8px;
  color: var(--jobsim-text);
  transition: border-color 0.2s;
}

.suggest-item:hover {
  border-color: var(--jobsim-primary);
}

.jd-hit {
  padding: 8px 0;
  border-bottom: 1px dashed var(--jobsim-border);
  line-height: 1.6;
}

.jd-link {
  font-size: 13px;
  font-weight: 500;
}

.section-label {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
}
</style>
