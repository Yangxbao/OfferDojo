<script setup lang="ts">
import * as echarts from 'echarts'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { AbilityVO } from '@/api/types'

const props = defineProps<{ abilities: AbilityVO[] }>()

const el = ref<HTMLElement>()
let chart: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null

function buildOption(abilities: AbilityVO[]): echarts.EChartsOption {
  return {
    radar: {
      indicator: abilities.map((a) => ({ name: a.dimension, max: a.maxScore || 10 })),
      radius: '65%',
      splitNumber: 4,
      axisName: { color: '#475569', fontSize: 12 },
      splitArea: { areaStyle: { color: ['#ffffff', '#f8fafc'] } },
      splitLine: { lineStyle: { color: '#e2e8f0' } },
    },
    series: [
      {
        type: 'radar',
        symbolSize: 4,
        data: [
          {
            value: abilities.map((a) => a.avgScore),
            name: '平均得分',
            itemStyle: { color: '#2563eb' },
            areaStyle: { color: 'rgba(37, 99, 235, 0.15)' },
            lineStyle: { width: 2 },
          },
        ],
        tooltip: { show: true },
      },
    ],
    tooltip: {
      trigger: 'item',
      formatter: (params: unknown) => {
        const p = params as { value: number[] }
        const dims = abilities.map((a, i) => `${a.dimension}：<b>${p.value[i] ?? 0}</b> 分`).join('<br/>')
        return dims
      },
    },
  }
}

onMounted(() => {
  if (!el.value) return
  chart = echarts.init(el.value)
  chart.setOption(buildOption(props.abilities))
  resizeObserver = new ResizeObserver(() => chart?.resize())
  resizeObserver.observe(el.value)
})

watch(
  () => props.abilities,
  (val) => chart?.setOption(buildOption(val)),
)

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div ref="el" style="width: 100%; height: 300px" />
</template>
