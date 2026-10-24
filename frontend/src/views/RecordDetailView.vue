<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { submissionApi } from '@/api'
import type { SubmissionDetailVO } from '@/api/types'
import EvaluationReport from '@/components/EvaluationReport.vue'
import FollowupDialog from '@/components/FollowupDialog.vue'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const loading = ref(true)
const detail = ref<SubmissionDetailVO | null>(null)

onMounted(async () => {
  try {
    detail.value = await submissionApi.detail(Number(route.params.id))
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page" v-loading="loading">
    <template v-if="detail">
      <div class="page-header">
        <h2>{{ detail.taskTitle }}</h2>
        <p>
          提交于 {{ formatDateTime(detail.updatedAt) }}
          <router-link :to="`/tasks/${detail.taskId}`" style="margin-left: 8px">再次练习 →</router-link>
        </p>
      </div>

      <el-row :gutter="16" style="margin-bottom: 16px">
        <el-col :xs="24" :md="10">
          <div class="card">
            <h3 class="card-title">我的答案</h3>
            <pre style="white-space: pre-wrap; font-family: inherit; line-height: 1.8; margin: 0; font-size: 13px">{{ detail.content }}</pre>
          </div>
        </el-col>
        <el-col :xs="24" :md="14">
          <template v-if="detail.evaluation">
            <EvaluationReport :evaluation="detail.evaluation" :task-title="detail.taskTitle" />
            <FollowupDialog
              :evaluation-id="detail.evaluation.id"
              :follow-ups="detail.evaluation.followUps"
              style="margin-top: 16px"
            />
          </template>
          <div v-else class="card">
            <el-empty description="这份记录还没有评估报告（可能是草稿）" />
          </div>
        </el-col>
      </el-row>
    </template>
    <el-empty v-else-if="!loading" description="记录不存在或没有权限查看" />
  </div>
</template>
