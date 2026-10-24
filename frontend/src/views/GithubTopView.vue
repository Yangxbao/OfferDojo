<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { request } from '@/api/http'

interface GhItem {
  repoFullName: string
  description: string | null
  url: string
  stars: number
  forks: number
  language: string | null
  licenseSpdx: string | null
  githubCreatedAt: string | null
  githubPushedAt: string | null
  topics: string[]
}
interface TopResult {
  success: boolean
  message?: string
  items?: GhItem[]
  fetchedAt?: string
  sortBasis: string
  totalCount?: number
}

const loading = ref(true)
const categories = ref<{ key: string; label: string }[]>([])
const activeCategory = ref('java-backend')
const result = ref<TopResult | null>(null)

async function load() {
  loading.value = true
  try {
    result.value = await request<TopResult>({ url: '/github/top', method: 'get', params: { category: activeCategory.value, limit: 20 } })
  } finally {
    loading.value = false
  }
}

function fmtDate(s: string | null): string {
  return s ? s.slice(0, 10) : '—'
}

onMounted(async () => {
  categories.value = await request<{ key: string; label: string }[]>({ url: '/github/top/categories', method: 'get' })
  await load()
})
</script>

<template>
  <div class="page" style="padding-bottom: 24px">
    <div class="page-header">
      <h2>GitHub 热门项目榜</h2>
      <p>基于 GitHub 官方 Search API 的真实数据（Star 数、时间均为 API 返回值）。排序口径在下方明示。</p>
    </div>

    <div style="display: flex; gap: 8px; margin-bottom: 16px; flex-wrap: wrap">
      <el-button v-for="c in categories" :key="c.key" size="small"
        :type="activeCategory === c.key ? 'primary' : 'default'"
        @click="activeCategory = c.key; load()">{{ c.label }}</el-button>
    </div>

    <div v-if="result" class="card" style="margin-bottom: 16px">
      <div class="card-sub" style="line-height: 1.8">
        📊 {{ result.sortBasis }}
        <template v-if="result.success && result.fetchedAt">｜数据更新于 {{ result.fetchedAt.replace('T', ' ').slice(0, 16) }}</template>
      </div>
      <div class="card-sub" v-if="result.success" style="font-size: 12px">
        ℹ️ 这是「检索结果内的 Star 排序」，不代表 GitHub 全球绝对排名。近期 Star 增长需要多时间点快照计算，历史数据积累中暂不展示，防止虚构。
      </div>
    </div>

    <div v-loading="loading">
      <el-alert v-if="result && !result.success" type="warning" :closable="false"
        :title="result.message || 'GitHub 暂时不可用'" description="请稍后刷新，或前往「资源中心」浏览已登记的本地资源。" show-icon />

      <div v-else-if="result?.items?.length" class="rank-list">
        <div v-for="(item, i) in result.items" :key="item.repoFullName" class="card rank-item">
          <div style="display: flex; gap: 14px; align-items: flex-start">
            <div class="rank-no" :class="{ top: i < 3 }">{{ i + 1 }}</div>
            <div style="flex: 1; min-width: 0">
              <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 4px">
                <el-link :href="item.url" target="_blank" type="primary" style="font-size: 15px; font-weight: 600">{{ item.repoFullName }}</el-link>
                <el-tag v-if="item.language" size="small" effect="plain">{{ item.language }}</el-tag>
                <el-tag v-if="item.licenseSpdx" size="small" type="success" effect="plain">{{ item.licenseSpdx }}</el-tag>
              </div>
              <p class="card-sub" style="margin: 0 0 8px; line-height: 1.6">{{ item.description ?? '暂无可靠介绍（等待审核补充）' }}</p>
              <div style="display: flex; gap: 14px; flex-wrap: wrap; font-size: 12px; color: var(--jobsim-text-secondary)">
                <span>⭐ {{ item.stars.toLocaleString() }}</span>
                <span>🍴 {{ item.forks.toLocaleString() }}</span>
                <span>创建 {{ fmtDate(item.githubCreatedAt) }}</span>
                <span>最近推送 {{ fmtDate(item.githubPushedAt) }}</span>
              </div>
              <div v-if="item.topics?.length" style="margin-top: 6px; display: flex; gap: 4px; flex-wrap: wrap">
                <el-tag v-for="t in item.topics.slice(0, 5)" :key="t" size="small" type="info" effect="plain" style="font-size: 11px">{{ t }}</el-tag>
              </div>
            </div>
          </div>
        </div>
      </div>
      <el-empty v-else-if="!loading" description="该分类暂无结果" />
    </div>
  </div>
</template>

<style scoped>
.rank-no {
  width: 32px; height: 32px; border-radius: 8px; flex-shrink: 0;
  background: var(--jobsim-bg); color: var(--jobsim-text-secondary);
  display: flex; align-items: center; justify-content: center; font-weight: 700;
}
.rank-no.top { background: #f59e0b; color: #fff; }
.rank-list { display: flex; flex-direction: column; gap: 10px; }
</style>
