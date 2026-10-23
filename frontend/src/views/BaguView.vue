<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { baguApi } from '@/api'
import type { BaguVO, PageResult } from '@/api/types'
import {
  CATEGORY_LABELS,
  DIFFICULTY_LABELS,
  FREQUENCY_LABELS,
  FREQUENCY_TYPES,
  DIFFICULTY_TYPES,
} from '@/utils/format'

const loading = ref(true)
const list = ref<BaguVO[]>([])
const total = ref(0)
const expanded = ref<Set<number>>(new Set())
const categories = ref<{ category: string; label: string; count: number }[]>([])

const query = reactive({
  category: '',
  frequency: '',
  keyword: '',
  page: 1,
  size: 10,
})

async function load() {
  loading.value = true
  try {
    const result: PageResult<BaguVO> = await baguApi.questions({
      category: query.category || undefined,
      frequency: query.frequency || undefined,
      keyword: query.keyword || undefined,
      page: query.page,
      size: query.size,
    })
    list.value = result.list
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function toggle(item: BaguVO) {
  if (expanded.value.has(item.id)) {
    expanded.value.delete(item.id)
  } else {
    expanded.value.add(item.id)
  }
}

function resetFilters() {
  query.category = ''
  query.frequency = ''
  query.keyword = ''
  query.page = 1
  load()
}

onMounted(async () => {
  categories.value = await baguApi.categories().catch(() => [])
  await load()
})
</script>

<template>
  <div class="page" style="padding-bottom: 24px">
    <div class="page-header">
      <h2>八股复习区</h2>
      <p>
        高频面试知识题库：先背一句话总结，再用大白话理解，最后看要点与追问。AI 评分仅供参考，技术结论请人工核对。
      </p>
    </div>

    <!-- 筛选栏 -->
    <div class="card" style="margin-bottom: 16px">
      <div style="display: flex; gap: 12px; flex-wrap: wrap; align-items: center">
        <el-select v-model="query.frequency" placeholder="全部频率" clearable style="width: 130px" @change="query.page = 1; load()">
          <el-option label="高频" value="HIGH" />
          <el-option label="中频" value="MID" />
          <el-option label="低频" value="LOW" />
        </el-select>
        <el-select v-model="query.category" placeholder="全部类目" clearable style="width: 190px" @change="query.page = 1; load()">
          <el-option v-for="c in categories" :key="c.category" :label="`${c.label} (${c.count})`" :value="c.category" />
          <el-option v-for="(label, code) in CATEGORY_LABELS" v-show="!categories.some(c => c.category === code)" :key="'e-' + code" :label="label" :value="code" />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="搜索问题 / 关键词"
          clearable
          style="width: 220px"
          @change="query.page = 1; load()"
        />
        <el-button text @click="resetFilters">重置</el-button>
        <span class="card-sub" style="margin-left: auto">共 {{ total }} 题</span>
      </div>
    </div>

    <!-- 题目列表 -->
    <div v-loading="loading" class="bagu-list">
      <div v-for="item in list" :key="item.id" class="card bagu-card">
        <div class="bagu-head" @click="toggle(item)">
          <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
            <el-tag v-if="item.frequency === 'HIGH'" size="small" type="danger" effect="dark">高频</el-tag>
            <el-tag v-else size="small" :type="FREQUENCY_TYPES[item.frequency]" effect="light">
              {{ FREQUENCY_LABELS[item.frequency] ?? item.frequency }}
            </el-tag>
            <el-tag size="small" :type="DIFFICULTY_TYPES[item.difficulty]" effect="light">
              {{ DIFFICULTY_LABELS[item.difficulty] ?? item.difficulty }}
            </el-tag>
            <span class="card-sub">{{ item.categoryLabel }}</span>
          </div>
          <h3 class="bagu-title">
            {{ item.title }}
            <el-icon class="expand-icon" :class="{ open: expanded.has(item.id) }"><ArrowDown /></el-icon>
          </h3>
          <div class="one-liner">
            <span class="ol-label">一句话</span>
            {{ item.oneLiner }}
          </div>
        </div>

        <div v-if="expanded.has(item.id)" class="bagu-body">
          <div class="block">
            <div class="block-label"><el-icon><ChatDotSquare /></el-icon> 大白话解释</div>
            <p class="plain">{{ item.plain }}</p>
          </div>

          <div class="block" v-if="item.points.length">
            <div class="block-label"><el-icon><List /></el-icon> 背诵要点</div>
            <ul class="points">
              <li v-for="p in item.points" :key="p">{{ p }}</li>
            </ul>
          </div>

          <div class="block" v-if="item.example">
            <div class="block-label"><el-icon><Document /></el-icon> 示例</div>
            <pre class="example">{{ item.example }}</pre>
          </div>

          <div class="block" v-if="item.followUps.length">
            <div class="block-label"><el-icon><QuestionFilled /></el-icon> 常见追问</div>
            <ul class="points">
              <li v-for="p in item.followUps" :key="p">{{ p }}</li>
            </ul>
          </div>
        </div>
      </div>

      <el-empty v-if="!loading && !list.length" description="没有符合条件的题目" />
    </div>

    <div v-if="total > query.size" style="display: flex; justify-content: center; margin-top: 16px">
      <el-pagination
        v-model:current-page="query.page"
        :page-size="query.size"
        :total="total"
        layout="prev, pager, next"
        @current-change="load"
      />
    </div>
  </div>
</template>

<style scoped>
.bagu-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.bagu-head {
  cursor: pointer;
}

.bagu-title {
  margin: 10px 0;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.expand-icon {
  transition: transform 0.2s;
  color: var(--jobsim-text-secondary);
}

.expand-icon.open {
  transform: rotate(180deg);
}

.one-liner {
  background: var(--jobsim-primary-light);
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.7;
}

.ol-label {
  color: var(--jobsim-primary);
  font-weight: 700;
  margin-right: 8px;
}

.bagu-body {
  border-top: 1px dashed var(--jobsim-border);
  margin-top: 14px;
  padding-top: 14px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.block-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--jobsim-primary);
  margin-bottom: 8px;
}

.plain {
  margin: 0;
  line-height: 1.9;
  font-size: 13.5px;
}

.points {
  margin: 0;
  padding-left: 18px;
  line-height: 1.9;
  font-size: 13.5px;
}

.example {
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 8px;
  padding: 12px 14px;
  font-size: 12.5px;
  line-height: 1.7;
  overflow-x: auto;
  margin: 0;
  font-family: Consolas, Monaco, monospace;
}
</style>
