<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { adminApi } from '@/api'
import type { AdminOverviewVO, AdminEvaluationRow, AiLogVO, PageResult } from '@/api/types'
import { formatDateTime, PROVIDER_LABELS, scoreColor } from '@/utils/format'

const loading = ref(true)
const overview = ref<AdminOverviewVO | null>(null)
const evalPage = ref(1)
const evalTotal = ref(0)
const evaluations = ref<AdminEvaluationRow[]>([])
const logPage = ref(1)
const logTotal = ref(0)
const logs = ref<AiLogVO[]>([])

async function loadOverview() {
  overview.value = await adminApi.overview()
}

async function loadEvaluations() {
  const result: PageResult<AdminEvaluationRow> = await adminApi.evaluations({ page: evalPage.value, size: 8 })
  evaluations.value = result.list
  evalTotal.value = result.total
}

async function loadLogs() {
  const result: PageResult<AiLogVO> = await adminApi.aiLogs({ page: logPage.value, size: 8 })
  logs.value = result.list
  logTotal.value = result.total
}

onMounted(async () => {
  try {
    await Promise.all([loadOverview(), loadEvaluations(), loadLogs()])
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page" v-loading="loading" style="padding-bottom: 24px">
    <div class="page-header">
      <h2>后台总览</h2>
      <p>平台数据与 AI 评估运行情况（仅管理员可见）</p>
    </div>

    <div class="grid grid-4" style="margin-bottom: 16px" v-if="overview">
      <div class="card">
        <div class="stat-value">{{ overview.userCount }}</div>
        <div class="stat-label">注册用户</div>
      </div>
      <div class="card">
        <div class="stat-value">{{ overview.publishedTasks }} <span class="card-sub">/ {{ overview.totalTasks }}</span></div>
        <div class="stat-label">已发布 / 全部任务</div>
      </div>
      <div class="card">
        <div class="stat-value">{{ overview.evaluationCount }}</div>
        <div class="stat-label">累计评估次数</div>
      </div>
      <div class="card">
        <div class="stat-value" :style="{ color: overview.avgScore ? scoreColor(overview.avgScore) : undefined }">
          {{ overview.avgScore || '—' }}
        </div>
        <div class="stat-label">全站平均分</div>
      </div>
    </div>

    <div class="grid grid-2" style="margin-bottom: 16px">
      <div class="card">
        <h3 class="card-title">最近评估记录</h3>
        <el-table :data="evaluations" size="small" style="width: 100%">
          <el-table-column prop="username" label="用户" width="90" />
          <el-table-column prop="taskTitle" label="任务" min-width="180" show-overflow-tooltip />
          <el-table-column label="得分" width="70">
            <template #default="{ row }">
              <span :style="{ color: scoreColor(row.totalScore), fontWeight: 600 }">{{ row.totalScore.toFixed(1) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="来源" width="150">
            <template #default="{ row }">
              <el-tag size="small" type="info" effect="light">
                {{ PROVIDER_LABELS[row.provider] ?? row.provider }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="90">
            <template #default="{ row }">
              <span class="card-sub">{{ formatDateTime(row.createdAt).slice(5, 10) }}</span>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="evalTotal > 8" style="display: flex; justify-content: center; margin-top: 12px">
          <el-pagination
            v-model:current-page="evalPage"
            :page-size="8"
            :total="evalTotal"
            layout="prev, pager, next"
            @current-change="loadEvaluations"
          />
        </div>
      </div>

      <div class="card">
        <h3 class="card-title">
          AI 调用日志
          <el-tag v-if="overview" size="small" :type="overview.failureCount24h > 0 ? 'danger' : 'success'" effect="light">
            近 24h 失败 {{ overview.failureCount24h }}
          </el-tag>
        </h3>
        <el-table :data="logs" size="small" style="width: 100%">
          <el-table-column label="状态" width="70">
            <template #default="{ row }">
              <el-tag size="small" :type="row.success ? 'success' : 'danger'" effect="light">
                {{ row.success ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="provider" label="来源" width="80" />
          <el-table-column prop="taskCode" label="任务" width="90" />
          <el-table-column prop="errorType" label="错误类型" width="100">
            <template #default="{ row }">{{ row.errorType ?? '—' }}</template>
          </el-table-column>
          <el-table-column prop="message" label="说明" min-width="150" show-overflow-tooltip />
          <el-table-column label="耗时" width="80">
            <template #default="{ row }">{{ row.durationMs != null ? row.durationMs + 'ms' : '—' }}</template>
          </el-table-column>
        </el-table>
        <div v-if="logTotal > 8" style="display: flex; justify-content: center; margin-top: 12px">
          <el-pagination
            v-model:current-page="logPage"
            :page-size="8"
            :total="logTotal"
            layout="prev, pager, next"
            @current-change="loadLogs"
          />
        </div>
      </div>
    </div>
  </div>
</template>
