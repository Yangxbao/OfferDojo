<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { dashboardApi } from '@/api'
import type { DashboardVO } from '@/api/types'
import TaskCard from '@/components/TaskCard.vue'
import AbilityRadar from '@/components/AbilityRadar.vue'
import { DIFFICULTY_LABELS, DIFFICULTY_TYPES, formatDateTime, scoreColor } from '@/utils/format'

const loading = ref(true)
const data = ref<DashboardVO | null>(null)

onMounted(async () => {
  try {
    data.value = await dashboardApi.get()
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page" v-loading="loading">
    <template v-if="data">
      <div class="page-header">
        <h2>今天练什么？</h2>
        <p>{{ data.todayReason }}</p>
      </div>

      <!-- 今日任务 + 继续上次 -->
      <div class="grid grid-2">
        <div class="card" style="border-left: 3px solid var(--jobsim-primary)">
          <h3 class="card-title">今日实战任务</h3>
          <template v-if="data.todayTask">
            <div style="display: flex; gap: 8px; margin-bottom: 8px; flex-wrap: wrap">
              <el-tag size="small" effect="plain">{{ data.todayTask.code }}</el-tag>
              <el-tag size="small" :type="DIFFICULTY_TYPES[data.todayTask.difficulty]" effect="light">
                {{ DIFFICULTY_LABELS[data.todayTask.difficulty] }}
              </el-tag>
              <el-tag size="small" type="info" effect="plain">约 {{ data.todayTask.estimatedMinutes }} 分钟</el-tag>
            </div>
            <h4 style="margin: 0 0 8px; font-size: 16px">{{ data.todayTask.title }}</h4>
            <p class="card-sub" style="margin: 0 0 14px">{{ data.todayTask.summary }}</p>
            <router-link :to="`/tasks/${data.todayTask.id}`">
              <el-button type="primary">开始练习</el-button>
            </router-link>
          </template>
          <el-empty v-else description="暂无可用任务" :image-size="60" />
        </div>

        <div class="card">
          <h3 class="card-title">继续上次任务</h3>
          <template v-if="data.resume">
            <h4 style="margin: 0 0 8px; font-size: 15px">{{ data.resume.taskTitle }}</h4>
            <p class="card-sub" style="margin: 0 0 14px">草稿保存于 {{ formatDateTime(data.resume.updatedAt) }}</p>
            <router-link :to="`/tasks/${data.resume.taskId}`">
              <el-button>继续作答</el-button>
            </router-link>
          </template>
          <el-empty v-else description="暂未保存草稿，去任务中心挑一个开始吧" :image-size="60" />
        </div>
      </div>

      <!-- 统计 + 能力雷达 -->
      <div class="grid grid-3" style="margin-top: 16px">
        <div class="card">
          <div class="stat-value">{{ data.completedTasks }} <span style="font-size: 14px; color: var(--jobsim-text-secondary)">/ {{ data.publishedTasks }}</span></div>
          <div class="stat-label">累计完成任务</div>
          <el-progress
            style="margin-top: 12px"
            :percentage="data.publishedTasks ? (data.completedTasks / data.publishedTasks) * 100 : 0"
            :stroke-width="8"
            :show-text="false"
          />
        </div>
        <div class="card" style="grid-column: span 2">
          <h3 class="card-title">
            能力维度练习情况
            <router-link to="/analysis" class="card-sub">查看完整分析 →</router-link>
          </h3>
          <AbilityRadar :abilities="data.abilities" />
        </div>
      </div>

      <!-- 最近练习 + 推荐内容 -->
      <div class="grid grid-2" style="margin-top: 16px; margin-bottom: 24px">
        <div class="card">
          <h3 class="card-title">
            最近的模拟任务
            <router-link to="/records" class="card-sub">全部记录 →</router-link>
          </h3>
          <el-table v-if="data.recent.length" :data="data.recent" size="small" style="width: 100%">
            <el-table-column prop="taskTitle" label="任务" min-width="160" show-overflow-tooltip />
            <el-table-column label="得分" width="80">
              <template #default="{ row }">
                <span
                  v-if="row.totalScore != null"
                  :style="{ color: scoreColor(row.totalScore), fontWeight: 600 }"
                >{{ row.totalScore.toFixed(1) }}</span>
                <span v-else class="card-sub">—</span>
              </template>
            </el-table-column>
            <el-table-column label="时间" width="90">
              <template #default="{ row }">
                <span class="card-sub">{{ formatDateTime(row.updatedAt).slice(5, 10) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="" width="60">
              <template #default="{ row }">
                <router-link :to="`/records/${row.id}`">
                  <el-link type="primary" :underline="false">查看</el-link>
                </router-link>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="还没有练习记录" :image-size="70" />
        </div>

        <div class="card">
          <h3 class="card-title">
            推荐练习内容
            <router-link to="/tasks" class="card-sub">任务中心 →</router-link>
          </h3>
          <div class="grid grid-3" style="gap: 12px">
            <TaskCard v-for="task in data.recommended" :key="task.id" :task="task" />
          </div>
          <el-empty v-if="!data.recommended.length" description="太棒了，全部任务都已完成" :image-size="70" />
        </div>
      </div>
    </template>
  </div>
</template>
