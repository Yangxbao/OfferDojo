<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { learningApi } from '@/api/learning'
import type { LearningResourceVO, PageResult } from '@/api/types'

const loading = ref(true)
const list = ref<LearningResourceVO[]>([])
const total = ref(0)
const inPlan = ref<Set<string>>(new Set())

const query = reactive({
  track: '', sourceType: '', licenseStatus: '', verifiedStatus: '',
  qualityLevel: '', keyword: '', orderBy: 'featured', minStars: '' as string,
  page: 1, size: 12,
})

const detail = ref<LearningResourceVO | null>(null)
const detailVisible = ref(false)
const planBusy = ref(false)

const TRACK_OPTIONS = [
  { value: 'java-fullstack', label: 'Java 全栈开发' },
  { value: 'ai-fullstack', label: 'AI 全栈应用开发' },
  { value: 'python-agent', label: 'Python Agent 开发' },
]

const STATUS_LABELS: Record<string, string> = {
  PENDING: '待核验', VERIFIED: '已核验', PUBLISHED: '可展示', OFFLINE: '已下架',
}
const STATUS_TYPES: Record<string, string> = {
  PENDING: 'info', VERIFIED: 'primary', PUBLISHED: 'success', OFFLINE: 'danger',
}
const LICENSE_LABELS: Record<string, string> = {
  VERIFIED: '许可已核验', NOASSERTION: '自定义许可', UNKNOWN: '许可未核验',
}
const TYPE_LABELS: Record<string, string> = {
  ROADMAP: '路线图', NOTES: '笔记', EXAMPLES: '代码示例', DOCS: '官方文档', COURSE: '课程',
}

async function load() {
  loading.value = true
  try {
    const result: PageResult<LearningResourceVO> = await learningApi.resources({
      track: query.track || undefined,
      sourceType: query.sourceType || undefined,
      licenseStatus: query.licenseStatus || undefined,
      verifiedStatus: query.verifiedStatus || undefined,
      qualityLevel: query.qualityLevel || undefined,
      keyword: query.keyword || undefined,
      orderBy: query.orderBy,
      minStars: query.minStars ? Number(query.minStars) : undefined,
      page: query.page,
      size: query.size,
    })
    list.value = result.list
    total.value = result.total
  } finally {
    loading.value = false
  }
}

async function loadInPlan() {
  try {
    inPlan.value = new Set(await learningApi.myPlanResourceCodes())
  } catch { /* 未登录时忽略 */ }
}

function openDetail(item: LearningResourceVO) {
  detail.value = item
  detailVisible.value = true
}

async function addToPlan(item: LearningResourceVO) {
  planBusy.value = true
  try {
    await learningApi.addPlan('RESOURCE', item.code)
    inPlan.value.add(item.code)
    ElMessage.success('已加入学习计划')
    if (detail.value?.code === item.code) detail.value.inMyPlan = true
  } finally {
    planBusy.value = false
  }
}

function resetFilters() {
  Object.assign(query, { track: '', sourceType: '', licenseStatus: '', verifiedStatus: '', qualityLevel: '', keyword: '', orderBy: 'featured', minStars: '', page: 1 })
  load()
}

import { useRoute } from 'vue-router'
const route = useRoute()

onMounted(async () => {
  if (route.query.track) query.track = route.query.track as string
  await Promise.all([load(), loadInPlan()])
})
</script>

<template>
  <div class="page" style="padding-bottom: 24px">
    <div class="page-header">
      <h2>开源学习资源中心</h2>
      <p>经过核验的公开学习资源：只展示元数据与来源链接，不复制原文。核验状态与使用权限透明展示。</p>
    </div>

    <!-- 筛选 -->
    <div class="card" style="margin-bottom: 16px">
      <div style="display: flex; gap: 12px; flex-wrap: wrap; align-items: center">
        <el-select v-model="query.track" placeholder="技术方向" clearable style="width: 180px" @change="query.page = 1; load()">
          <el-option v-for="t in TRACK_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
        <el-select v-model="query.sourceType" placeholder="资源类型" clearable style="width: 130px" @change="query.page = 1; load()">
          <el-option v-for="(label, code) in TYPE_LABELS" :key="code" :label="label" :value="code" />
        </el-select>
        <el-select v-model="query.licenseStatus" placeholder="许可证状态" clearable style="width: 140px" @change="query.page = 1; load()">
          <el-option v-for="(label, code) in LICENSE_LABELS" :key="code" :label="label" :value="code" />
        </el-select>
        <el-select v-model="query.verifiedStatus" placeholder="核验状态" clearable style="width: 140px" @change="query.page = 1; load()">
          <el-option v-for="(label, code) in STATUS_LABELS" :key="code" :label="label" :value="code" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="搜索标题 / 简介" clearable style="width: 200px" @change="query.page = 1; load()" />
        <el-select v-model="query.qualityLevel" placeholder="质量等级" clearable style="width:120px" @change="query.page = 1; load()">
          <el-option label="✨ 精选" value="FEATURED" />
          <el-option label="推荐" value="RECOMMENDED" />
          <el-option label="普通" value="NORMAL" />
          <el-option label="待评估" value="PENDING_EVAL" />
        </el-select>
        <el-select v-model="query.minStars" placeholder="Star 下限" clearable style="width:120px" @change="query.page = 1; load()">
          <el-option label="≥ 1000" value="1000" />
          <el-option label="≥ 100" value="100" />
          <el-option label="≥ 10" value="10" />
        </el-select>
        <el-select v-model="query.orderBy" style="width:140px" @change="query.page = 1; load()">
          <el-option label="精选优先" value="featured" />
          <el-option label="Star 降序" value="stars" />
          <el-option label="质量分降序" value="quality" />
          <el-option label="最新收录" value="recent" />
          <el-option label="最近推送" value="pushed" />
          <el-option label="最近核验" value="verified" />
        </el-select>
        <el-button text @click="resetFilters">重置</el-button>
        <span class="card-sub" style="margin-left: auto">共 {{ total }} 个资源</span>
      </div>
    </div>

    <!-- 列表 -->
    <div v-loading="loading" class="grid grid-3">
      <div v-for="item in list" :key="item.id" class="card resource-card">
        <div style="display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 8px">
          <el-tag size="small" :type="item.verifiedStatus === 'PUBLISHED' ? 'success' : item.verifiedStatus === 'PENDING' ? 'info' : 'primary'" effect="light">
            {{ STATUS_LABELS[item.verifiedStatus] ?? item.verifiedStatus }}
          </el-tag>
          <el-tag size="small" :type="item.licenseStatus === 'UNKNOWN' ? 'warning' : 'success'" effect="plain">
            {{ item.licenseSpdx ?? LICENSE_LABELS[item.licenseStatus] ?? '许可未知' }}
          </el-tag>
          <el-tag size="small" type="info" effect="plain">{{ TYPE_LABELS[item.sourceType] ?? item.sourceType }}</el-tag>
          <el-tag v-if="item.qualityLevel === 'FEATURED'" size="small" type="warning" effect="dark">✨ 精选</el-tag>
          <el-tag v-else-if="item.qualityLevel === 'RECOMMENDED'" size="small" type="success" effect="light">推荐</el-tag>
          <el-tag v-else-if="item.qualityLevel === 'NORMAL'" size="small" type="info" effect="light">普通</el-tag>
          <el-tag v-else-if="item.qualityLevel === 'PENDING_EVAL'" size="small" effect="plain">待评估</el-tag>
          <span v-if="item.starsCount != null" class="card-sub" style="font-size:12px">⭐{{ item.starsCount.toLocaleString() }}</span>
        </div>
        <h3 style="font-size: 14px; margin: 0 0 6px; line-height: 1.5">{{ item.title }}</h3>
        <p class="card-sub" style="margin: 0 0 10px; line-height: 1.6; font-size: 12.5px">{{ item.description }}</p>
        <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
          <el-link :href="item.url" target="_blank" type="primary" :underline="false" style="font-size: 12px">
            <el-icon style="margin-right: 2px"><Link /></el-icon> 原始来源
          </el-link>
          <el-button size="small" text type="primary" @click="openDetail(item)">详情</el-button>
          <el-button
            v-if="!inPlan.has(item.code)"
            size="small" text :loading="planBusy" @click="addToPlan(item)"
          >+ 计划</el-button>
          <el-tag v-else size="small" type="success" effect="plain">已加入计划</el-tag>
        </div>
      </div>
      <el-empty v-if="!loading && !list.length" description="没有符合条件的资源" />
    </div>

    <div v-if="total > query.size" style="display: flex; justify-content: center; margin-top: 16px">
      <el-pagination v-model:current-page="query.page" :page-size="query.size" :total="total" layout="prev, pager, next" @current-change="load" />
    </div>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" :title="detail?.title" size="450px">
      <template v-if="detail">
        <div class="detail-section">
          <div class="detail-label">简介</div>
          <p style="font-size: 13px; line-height: 1.8; margin: 0">{{ detail.description }}</p>
        </div>
        <div class="detail-section">
          <div class="detail-label">核验状态</div>
          <el-tag :type="STATUS_TYPES[detail.verifiedStatus]" effect="light">
            {{ STATUS_LABELS[detail.verifiedStatus] ?? detail.verifiedStatus }}
          </el-tag>
          <span class="card-sub" style="margin-left: 8px">最后核验：{{ detail.verifiedAt ?? '—' }}</span>
        </div>
        <div class="detail-section">
          <div class="detail-label">许可证</div>
          <div style="font-size: 13px">
            <div>SPDX：{{ detail.licenseSpdx ?? '未确认' }}</div>
            <div>{{ detail.licenseName ?? '未知' }}</div>
            <div v-if="detail.licensePath" class="card-sub">{{ detail.licensePath }}</div>
          </div>
        </div>
        <div class="detail-section">
          <div class="detail-label">使用权限</div>
          <div style="font-size: 13px; line-height: 2">
            <div>允许复制：{{ detail.allowCopy ? '是' : '否' }}</div>
            <div>允许再分发：{{ detail.allowRedistribute ? '是' : '否' }}</div>
            <div>要求署名：{{ detail.attributionRequired ? '是' : '否' }}</div>
            <div>要求相同方式共享：{{ detail.shareAlike ? '是' : '否' }}</div>
          </div>
          <div v-if="detail.rightsBasis" class="card-sub" style="margin-top: 4px">依据：{{ detail.rightsBasis }}</div>
        </div>
        <div class="detail-section" v-if="detail.versionNote">
          <div class="detail-label">版本提示</div>
          <div style="font-size: 13px">{{ detail.versionNote }}</div>
        </div>
        <div class="detail-section" v-if="detail.prerequisite.length">
          <div class="detail-label">前置知识</div>
          <div><el-tag v-for="p in detail.prerequisite" :key="p" size="small" effect="plain" style="margin-right: 4px">{{ p }}</el-tag></div>
        </div>
        <div class="detail-section">
          <el-link :href="detail.url" target="_blank" type="primary">
            <el-icon style="margin-right: 4px"><Link /></el-icon> 打开原始来源
          </el-link>
        </div>
        <div style="margin-top: 12px">
          <el-button
            v-if="!detail.inMyPlan" type="primary" style="width: 100%"
            :loading="planBusy" @click="addToPlan(detail)"
          >加入学习计划</el-button>
          <el-alert v-else type="success" :closable="false" title="已在学习计划中" />
        </div>
      </template>
    </el-drawer>
</div>
</template>

<style scoped>
.resource-card {
  display: flex;
  flex-direction: column;
}

.detail-section {
  margin-bottom: 16px;
}

.detail-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--jobsim-text-secondary);
  margin-bottom: 6px;
}
</style>
