<script setup lang="ts">
import type { TaskVO } from '@/api/types'
import { DIFFICULTY_LABELS, DIFFICULTY_TYPES, MY_STATUS_LABELS, MY_STATUS_TYPES, scoreColor } from '@/utils/format'

defineProps<{ task: TaskVO }>()
</script>

<template>
  <router-link :to="`/tasks/${task.id}`" class="task-card card">
    <div style="display: flex; justify-content: space-between; align-items: flex-start; gap: 8px">
      <el-tag size="small" effect="plain">{{ task.code }}</el-tag>
      <div style="display: flex; gap: 6px">
        <el-tag size="small" :type="DIFFICULTY_TYPES[task.difficulty]" effect="light">
          {{ DIFFICULTY_LABELS[task.difficulty] ?? task.difficulty }}
        </el-tag>
        <el-tag size="small" :type="MY_STATUS_TYPES[task.myStatus]" effect="light">
          {{ MY_STATUS_LABELS[task.myStatus] ?? task.myStatus }}
        </el-tag>
      </div>
    </div>
    <h3 style="margin: 10px 0 6px; font-size: 15px; line-height: 1.5">{{ task.title }}</h3>
    <p class="card-sub" style="margin: 0; line-height: 1.6">{{ task.summary }}</p>
    <div style="display: flex; justify-content: space-between; margin-top: 12px; align-items: center">
      <span class="card-sub">{{ task.categoryLabel }} · 约 {{ task.estimatedMinutes }} 分钟</span>
      <span
        v-if="task.myBestScore != null"
        :style="{ color: scoreColor(task.myBestScore), fontWeight: 700 }"
      >
        最高 {{ task.myBestScore.toFixed(1) }}
      </span>
    </div>
  </router-link>
</template>

<style scoped>
.task-card {
  display: block;
  color: var(--jobsim-text);
  transition: box-shadow 0.2s, border-color 0.2s;
}

.task-card:hover {
  border-color: var(--jobsim-primary);
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.1);
}
</style>
