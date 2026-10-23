<script setup lang="ts">
import * as echarts from 'echarts'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps<{ points: { date: string; avgScore: number; count: number }[] }>()

const el = ref<HTMLElement>()
let chart: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null

function buildOption(points: { date: string; avgScore: number; count: number }[]): echarts.EChartsOption {
  return {
    grid: { left: 40, right: 16, top: 30, bottom: 28 },
    tooltip: {
      trigger: 'axis',
      formatter: (params: unknown) => {
        const list = params as { dataIndex: number }[]
        const idx = list[0]?.dataIndex ?? 0
        const p = points[idx]
        return p ? `${p.date}<br/>平均分：<b>${p.avgScore}</b><br/>练习次数：${p.count}` : ''
      },
    },
    xAxis: {
      type: 'category',
      data: points.map((p) => p.date.slice(5)),
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#e2e8f0' } },
      axisLabel: { color: '#64748b' },
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 10,
      axisLabel: { color: '#64748b' },
      splitLine: { lineStyle: { color: '#eef2f7' } },
    },
    series: [
      {
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: points.map((p) => p.avgScore),
        itemStyle: { color: '#2563eb' },
        lineStyle: { width: 2.5 },
        areaStyle: { color: 'rgba(37, 99, 235, 0.08)' },
      },
    ],
  }
}

onMounted(() => {
  if (!el.value) return
  chart = echarts.init(el.value)
  chart.setOption(buildOption(props.points))
  resizeObserver = new ResizeObserver(() => chart?.resize())
  resizeObserver.observe(el.value)
})

watch(
  () => props.points,
  (val) => chart?.setOption(buildOption(val)),
)

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div ref="el" style="width: 100%; height: 280px" />
</template>
