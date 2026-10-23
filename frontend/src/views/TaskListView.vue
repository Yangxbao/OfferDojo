<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { taskApi } from '@/api'
import type { PageResult, TaskVO } from '@/api/types'
import TaskCard from '@/components/TaskCard.vue'
import { CATEGORY_LABELS, DIFFICULTY_LABELS, MY_STATUS_LABELS } from '@/utils/format'

const loading = ref(true)
const result = ref<PageResult<TaskVO> | null>(null)
const query = reactive({
  category: '',
  difficulty: '',
  myStatus: '',
  keyword: '',
  page: 1,
  size: 12,
})

async function load() {
  loading.value = true
  try {
    result.value = await taskApi.page({
      category: query.category || undefined,
      difficulty: query.difficulty || undefined,
      keyword: query.keyword || undefined,
      page: query.page,
      size: query.size,
    })
  } finally {
    loading.value = false
  }
}

// myStatus 是前端本地过滤（后端返回的列表项自带该字段）
function filterByMyStatus(list: TaskVO[]): TaskVO[] {
  if (!query.myStatus) return list
  return list.filter((t) => t.myStatus === query.myStatus)
}

onMounted(load)
watch(
  () => [query.category, query.difficulty, query.keyword],
  () => {
    query.page = 1
    load()
  },
)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>任务中心</h2>
      <p>Java 后端实习方向的模拟实战任务，先读题再作答，AI 会按评分标准给出结构化反馈</p>
    </div>

    <div class="card" style="margin-bottom: 16px">
      <div style="display: flex; gap: 12px; flex-wrap: wrap; align-items: center">
        <el-select v-model="query.category" placeholder="全部类目" clearable style="width: 200px">
          <el-option v-for="(label, code) in CATEGORY_LABELS" :key="code" :label="label" :value="code" />
        </el-select>
        <el-select v-model="query.difficulty" placeholder="全部难度" clearable style="width: 140px">
          <el-option v-for="(label, code) in DIFFICULTY_LABELS" :key="code" :label="label" :value="code" />
        </el-select>
        <el-select v-model="query.myStatus" placeholder="全部状态" clearable style="width: 140px">
          <el-option v-for="(label, code) in MY_STATUS_LABELS" :key="code" :label="label" :value="code" />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="按标题搜索"
          clearable
          style="width: 220px"
          :prefix-icon="'Search'"
        />
        <span class="card-sub" style="margin-left: auto">共 {{ result?.total ?? 0 }} 个任务</span>
      </div>
    </div>

    <div v-loading="loading">
      <div v-if="result && filterByMyStatus(result.list).length" class="grid grid-3" style="margin-bottom: 24px">
        <TaskCard v-for="task in filterByMyStatus(result.list)" :key="task.id" :task="task" />
      </div>
      <el-empty v-else-if="!loading" description="没有符合条件的任务，换个筛选条件试试" />

      <div v-if="result && result.total > query.size" style="display: flex; justify-content: center; padding-bottom: 24px">
        <el-pagination
          v-model:current-page="query.page"
          :page-size="query.size"
          :total="result.total"
          layout="prev, pager, next"
          @current-change="load"
        />
      </div>
    </div>
  </div>
</template>
