<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { followupApi } from '@/api'
import type { FollowupDialogVO } from '@/api/types'
import { formatDateTime, PROVIDER_LABELS } from '@/utils/format'
import { scoreColor } from '@/utils/format'

const props = defineProps<{ evaluationId: number; followUps: string[] }>()

const loading = ref(true)
const submitting = ref(false)
const dialogs = ref<FollowupDialogVO[]>([])
const form = reactive({ question: '', content: '' })

async function load() {
  loading.value = true
  try {
    dialogs.value = await followupApi.list(props.evaluationId)
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!form.question.trim()) {
    ElMessage.warning('请先选择或输入一个追问')
    return
  }
  if (!form.content.trim()) {
    ElMessage.warning('先写下你的回答')
    return
  }
  submitting.value = true
  try {
    const created = await followupApi.answer(props.evaluationId, form.question, form.content)
    dialogs.value.push(created)
    form.content = ''
    ElMessage.success('已收到导师反馈，继续下一问吧')
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="card followup" v-loading="loading">
    <h3 class="card-title">
      <el-icon><ChatLineRound /></el-icon>
      追问模拟面试
      <span class="card-sub">回答评估报告里的技术追问，像真实面试一样被继续追问</span>
    </h3>

    <!-- 历史对话 -->
    <div v-if="dialogs.length" class="dialog-list">
      <div v-for="d in dialogs" :key="d.id" class="dialog-item">
        <div class="q">
          <el-icon><QuestionFilled /></el-icon>
          <span>{{ d.question }}</span>
          <el-tag size="small" :color="scoreColor(d.score)" style="color: #fff; border: none" effect="dark">
            {{ d.score.toFixed(1) }}
          </el-tag>
        </div>
        <div class="a">{{ d.answer }}</div>
        <div class="fb">
          <el-icon><CaretRight /></el-icon>
          <span style="white-space: pre-wrap">{{ d.feedback }}</span>
        </div>
        <div class="card-sub" style="text-align: right">
          {{ formatDateTime(d.createdAt) }} · {{ PROVIDER_LABELS[d.provider] ?? d.provider }}
        </div>
      </div>
    </div>
    <el-empty v-else description="还没有回答过追问——选一个下面的问题开始模拟面试" :image-size="60" />

    <!-- 回答区 -->
    <div class="answer-area">
      <div class="chip-row">
        <el-tag
          v-for="(q, i) in followUps"
          :key="i"
          :type="form.question === q ? 'primary' : 'info'"
          :effect="form.question === q ? 'dark' : 'plain'"
          style="cursor: pointer"
          @click="form.question = q"
        >
          {{ q.length > 28 ? q.slice(0, 28) + '…' : q }}
        </el-tag>
      </div>
      <el-input
        v-model="form.question"
        placeholder="追问问题（可点击上方标签或自行输入）"
        style="margin: 10px 0"
      />
      <el-input
        v-model="form.content"
        type="textarea"
        :rows="4"
        maxlength="10000"
        placeholder="像在面试现场一样，用「结论 → 原因 → 例子」组织你的回答…"
      />
      <div style="margin-top: 10px; display: flex; gap: 10px; align-items: center">
        <el-button type="primary" :loading="submitting" @click="submit">
          {{ submitting ? '导师评估中…' : '提交回答' }}
        </el-button>
        <span class="card-sub">{{ dialogs.length ? `已完成 ${dialogs.length} 轮追问` : '' }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dialog-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
}

.dialog-item {
  border: 1px solid var(--jobsim-border);
  border-radius: 8px;
  padding: 10px 12px;
}

.q {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  margin-bottom: 6px;
  flex-wrap: wrap;
}

.a {
  font-size: 13px;
  line-height: 1.7;
  color: var(--jobsim-text-secondary);
  margin-bottom: 8px;
  white-space: pre-wrap;
}

.fb {
  background: var(--jobsim-primary-light);
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 13px;
  line-height: 1.7;
  display: flex;
  gap: 4px;
  align-items: flex-start;
}

.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
</style>
