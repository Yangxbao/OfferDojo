<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { progressApi } from '@/api'
import type { ProgressVO } from '@/api/types'
import AbilityRadar from '@/components/AbilityRadar.vue'
import ScoreTrend from '@/components/ScoreTrend.vue'
import { scoreColor } from '@/utils/format'

const loading = ref(true)
const data = ref<ProgressVO | null>(null)

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
</style>
