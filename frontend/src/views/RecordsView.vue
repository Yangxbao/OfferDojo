<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { submissionApi } from '@/api'
import type { PageResult, SubmissionVO } from '@/api/types'
import {
  CATEGORY_LABELS,
  DIFFICULTY_LABELS,
  DIFFICULTY_TYPES,
  formatDateTime,
  scoreColor,
} from '@/utils/format'

const loading = ref(true)
const page = ref(1)
const size = 10
const total = ref(0)
const list = ref<SubmissionVO[]>([])

async function load() {
  loading.value = true
  try {
    const result: PageResult<SubmissionVO> = await submissionApi.myHistory({ page: page.value, size })
    list.value = result.list
    total.value = result.total
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>练习记录</h2>
      <p>每次提交都会保留历史记录，点击查看当时的答案与评估报告</p>
    </div>

    <div class="card" v-loading="loading">
      <el-table v-if="list.length" :data="list" style="width: 100%">
        <el-table-column prop="taskTitle" label="任务" min-width="240" show-overflow-tooltip />
        <el-table-column label="类目" width="160">
          <template #default="{ row }">{{ CATEGORY_LABELS[row.taskCategory] ?? row.taskCategory }}</template>
        </el-table-column>
        <el-table-column label="难度" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="DIFFICULTY_TYPES[row.taskDifficulty]" effect="light">
              {{ DIFFICULTY_LABELS[row.taskDifficulty] ?? row.taskDifficulty }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="得分" width="90">
          <template #default="{ row }">
            <span v-if="row.totalScore != null" :style="{ color: scoreColor(row.totalScore), fontWeight: 700 }">
              {{ row.totalScore.toFixed(1) }}
            </span>
            <span v-else class="card-sub">—</span>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="150">
          <template #default="{ row }">
            <span class="card-sub">{{ formatDateTime(row.updatedAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="" width="80" fixed="right">
          <template #default="{ row }">
            <router-link :to="`/records/${row.id}`">
              <el-link type="primary" :underline="false">查看报告</el-link>
            </router-link>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else-if="!loading" description="还没有提交过任务，去任务中心开始第一次练习吧">
        <router-link to="/tasks"><el-button type="primary">去任务中心</el-button></router-link>
      </el-empty>

      <div v-if="total > size" style="display: flex; justify-content: center; margin-top: 16px">
        <el-pagination
          v-model:current-page="page"
          :page-size="size"
          :total="total"
          layout="prev, pager, next"
          @current-change="load"
        />
      </div>
    </div>
  </div>
</template>
