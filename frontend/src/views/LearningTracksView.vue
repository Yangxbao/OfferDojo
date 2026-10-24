<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { learningApi } from '@/api/learning'
import type { LearningTrackVO } from '@/api/learning'
import { MY_STATUS_LABELS, MY_STATUS_TYPES } from '@/utils/format'

const loading = ref(true)
const tracks = ref<LearningTrackVO[]>([])
const activeTrack = ref('java-fullstack')
const planItems = ref<{ id: number; itemType: string; itemCode: string; itemTitle: string; status: string }[]>([])

const currentTrack = computed(() => tracks.value.find((t) => t.track === activeTrack.value))

const TRACK_OPTIONS: { value: string; label: string }[] = [
  { value: 'java-fullstack', label: 'Java 全栈开发' },
  { value: 'ai-fullstack', label: 'AI 全栈应用开发' },
  { value: 'python-agent', label: 'Python Agent 开发' },
]

async function loadTracks() {
  tracks.value = await learningApi.tracks()
}

async function loadPlan() {
  planItems.value = await learningApi.myPlan()
}

async function markNode(nodeCode: string, status: string) {
  try {
    const items = await learningApi.myPlan()
    const existing = items.find((i) => i.itemCode === nodeCode && i.itemType === 'NODE')
    if (existing) {
      await learningApi.updatePlan(existing.id, status)
    } else {
      await learningApi.addPlan('NODE', nodeCode)
      const refreshed = await learningApi.myPlan()
      const created = refreshed.find((i) => i.itemCode === nodeCode && i.itemType === 'NODE')
      if (created && status !== 'TODO') await learningApi.updatePlan(created.id, status)
    }
    ElMessage.success('状态已更新')
    await Promise.all([loadTracks(), loadPlan()])
  } catch {
    ElMessage.error('操作失败')
  }
}

onMounted(async () => {
  try {
    await Promise.all([loadTracks(), loadPlan()])
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page" v-loading="loading" style="padding-bottom: 24px">
    <div class="page-header">
      <h2>成长路线</h2>
      <p>三条方向路线：选择方向，按阶段与前置关系学习，节点关联资源与实战任务。</p>
    </div>

    <!-- 方向选择 -->
    <div style="display: flex; gap: 8px; margin-bottom: 16px; flex-wrap: wrap">
      <el-button
        v-for="t in TRACK_OPTIONS" :key="t.value"
        :type="activeTrack === t.value ? 'primary' : 'default'"
        @click="activeTrack = t.value"
      >{{ t.label }}</el-button>
    </div>

    <template v-if="currentTrack">
      <div class="card" style="margin-bottom: 16px">
        <h3 style="margin: 0 0 4px">{{ currentTrack.trackLabel }}</h3>
        <p class="card-sub" style="margin: 0">{{ currentTrack.description }}</p>
      </div>

      <!-- 阶段列表 -->
      <div class="card" style="margin-bottom: 16px" v-for="stage in currentTrack.stages" :key="stage.stageOrder">
        <h3 class="card-title">第 {{ stage.stageOrder }} 阶段</h3>
        <div v-for="node in stage.nodes" :key="node.code" class="node-card">
          <div style="display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 8px">
            <div>
              <strong style="font-size: 14px">{{ node.title }}</strong>
              <el-tag v-if="node.planStatus === 'DONE'" size="small" type="success" effect="light" style="margin-left: 6px">已完成</el-tag>
              <el-tag v-else-if="node.planStatus === 'IN_PROGRESS'" size="small" type="primary" effect="light" style="margin-left: 6px">学习中</el-tag>
            </div>
            <div style="display: flex; gap: 6px">
              <el-button v-if="!node.planStatus" size="small" text type="primary" @click="markNode(node.code, 'IN_PROGRESS')">+ 计划</el-button>
              <template v-if="node.planStatus">
                <el-button v-if="node.planStatus === 'TODO'" size="small" text type="primary" @click="markNode(node.code, 'IN_PROGRESS')">开始学习</el-button>
                <el-button v-if="node.planStatus !== 'DONE'" size="small" text type="success" @click="markNode(node.code, 'DONE')">标记完成</el-button>
                <el-tag v-else size="small" type="success" effect="light">已完成</el-tag>
              </template>
            </div>
          </div>
          <p class="card-sub" style="margin: 6px 0 10px; line-height: 1.7; font-size: 13px">{{ node.goal }}</p>
          <div style="display: flex; flex-wrap: wrap; gap: 4px; margin-bottom: 10px">
            <el-tag v-for="k in node.knowledge" :key="k" size="small" type="info" effect="plain">{{ k }}</el-tag>
          </div>

          <div v-if="node.resources.length" style="margin-bottom: 10px">
            <div class="section-label">推荐资源</div>
            <div v-for="r in node.resources" :key="r.code" style="display: flex; align-items: center; gap: 6px; margin-bottom: 4px">
              <el-link :href="r.url" target="_blank" type="primary" :underline="false" style="font-size: 12.5px">
                <el-icon style="margin-right: 2px"><Link /></el-icon>{{ r.title }}
              </el-link>
              <el-tag v-if="r.licenseSpdx" size="small" effect="plain" style="font-size: 11px">{{ r.licenseSpdx }}</el-tag>
              <el-tag v-else size="small" type="warning" effect="plain" style="font-size: 11px">许可未核验</el-tag>
            </div>
          </div>

          <div v-if="node.tasks.length" style="margin-bottom: 10px">
            <div class="section-label">实战任务（完成评估才算通过）</div>
            <div v-for="t in node.tasks" :key="t.code" style="display: flex; align-items: center; gap: 6px; margin-bottom: 4px; flex-wrap: wrap">
              <router-link :to="`/tasks/${t.id}`" style="font-size: 12.5px">{{ t.title }}</router-link>
              <el-tag size="small" :type="MY_STATUS_TYPES[t.myStatus]" effect="light">{{ MY_STATUS_LABELS[t.myStatus] }}</el-tag>
              <el-tag v-if="t.myBestScore != null" size="small" type="success" effect="plain">{{ t.myBestScore.toFixed(1) }} 分</el-tag>
            </div>
          </div>

          <div v-if="node.prereqCodes.length" class="card-sub" style="font-size: 11.5px">前置：{{ node.prereqCodes.join('、') }}</div>
          <div v-if="node.milestone" class="milestone" style="font-size: 12px; color: var(--jobsim-text-secondary); margin-top: 6px">
            🏁 {{ node.milestone }}
          </div>
        </div>
      </div>

      <!-- 我的计划 -->
      <div class="card">
        <h3 class="card-title">我的学习计划</h3>
        <el-empty v-if="!planItems.length" description="还没有加入任何资源或节点" :image-size="60" />
        <el-table v-else :data="planItems" size="small" style="width: 100%">
          <el-table-column prop="itemTitle" label="条目" min-width="200" show-overflow-tooltip />
          <el-table-column prop="itemType" label="类型" width="80" />
          <el-table-column prop="status" label="状态" width="100" />
          <el-table-column prop="note" label="心得" min-width="150" show-overflow-tooltip />
        </el-table>
      </div>
    </template>
    <el-empty v-else-if="!loading" description="没有找到该方向的路线" />
  </div>
</template>

<style scoped>
.node-card {
  border: 1px solid var(--jobsim-border);
  border-radius: 8px;
  padding: 12px 14px;
  margin-bottom: 12px;
}

.section-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--jobsim-text-secondary);
  margin-bottom: 4px;
}
</style>
