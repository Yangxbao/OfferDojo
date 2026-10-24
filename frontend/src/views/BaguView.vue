<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { baguApi } from '@/api'
import type { BaguVO, PageResult } from '@/api/types'
import {
  CATEGORY_LABELS,
  DIFFICULTY_LABELS,
  DIFFICULTY_TYPES,
  FREQUENCY_LABELS,
  FREQUENCY_TYPES,
} from '@/utils/format'

const loading = ref(true)
const list = ref<BaguVO[]>([])
const total = ref(0)
const expanded = ref<Set<number>>(new Set())
const revealed = ref<Set<number>>(new Set())
const categories = ref<{ category: string; label: string; count: number }[]>([])

const route = useRoute()

/** 自测模式：先遮住答案，点「显示答案」后自评掌握度（存 localStorage） */
const selfTest = ref(false)
const MASTERY_KEY = 'jobsim.bagu.mastery'
const mastery = ref<Record<string, 'known' | 'unknown'>>(loadMastery())

const masteryStats = computed(() => {
  const values = Object.values(mastery.value)
  return {
    known: values.filter((v) => v === 'known').length,
    unknown: values.filter((v) => v === 'unknown').length,
  }
})

function loadMastery(): Record<string, 'known' | 'unknown'> {
  try {
    return JSON.parse(localStorage.getItem(MASTERY_KEY) || '{}')
  } catch {
    return {}
  }
}

function saveMastery() {
  localStorage.setItem(MASTERY_KEY, JSON.stringify(mastery.value))
}

function mark(item: BaguVO, status: 'known' | 'unknown') {
  mastery.value[item.code] = status
  saveMastery()
  ElMessage.success(status === 'known' ? '已标记为掌握' : '已标记为待巩固，复习区会优先出现')
}

const query = reactive({
  category: route.query.category as string || '',
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
    <div class="page-header" style="display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; flex-wrap: wrap">
      <div>
        <h2>八股复习区</h2>
        <p>
          先背一句话总结，再用大白话理解，最后看要点与追问。支持自测模式（先遮答案再自评）。
        </p>
      </div>
      <div style="text-align: right">
        <el-switch v-model="selfTest" active-text="自测模式" />
        <div class="card-sub" style="margin-top: 4px">
          已掌握 {{ masteryStats.known }} · 待巩固 {{ masteryStats.unknown }}
        </div>
      </div>
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
            <el-tag
              v-if="mastery[item.code]"
              size="small"
              :type="mastery[item.code] === 'known' ? 'success' : 'warning'"
              effect="plain"
            >
              {{ mastery[item.code] === 'known' ? '已掌握' : '待巩固' }}
            </el-tag>
          </div>
          <h3 class="bagu-title">
            {{ item.title }}
            <el-icon class="expand-icon" :class="{ open: expanded.has(item.id) }"><ArrowDown /></el-icon>
          </h3>
          <!-- 普通模式显示一句话总结；自测模式先遮住 -->
          <div v-if="!selfTest" class="one-liner">
            <span class="ol-label">一句话</span>
            {{ item.oneLiner }}
          </div>
          <div v-else-if="!revealed.has(item.id)" class="one-liner" style="text-align: center; color: var(--jobsim-text-secondary)">
            先在脑子里组织答案，再点击下方「显示答案」对照
          </div>
        </div>

        <div v-if="expanded.has(item.id)">
          <!-- 自测模式：显示答案按钮 -->
          <div v-if="selfTest && !revealed.has(item.id)" style="text-align: center; padding: 10px 0">
            <el-button type="primary" @click.stop="revealed.add(item.id); revealed = new Set(revealed)">显示答案</el-button>
          </div>

          <div v-else class="bagu-body">
            <div v-if="selfTest" class="one-liner" style="margin-bottom: 14px">
              <span class="ol-label">一句话</span>
              {{ item.oneLiner }}
            </div>

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

            <!-- 自测模式：自评按钮 -->
            <div v-if="selfTest" class="self-grade">
              <span class="card-sub">这道题你掌握了吗？</span>
              <el-button size="small" type="success" plain @click.stop="mark(item, 'known')">记住了</el-button>
              <el-button size="small" type="warning" plain @click.stop="mark(item, 'unknown')">没记住</el-button>
            </div>
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

.self-grade {
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--jobsim-bg);
  border-radius: 8px;
  padding: 10px 12px;
}
</style>
