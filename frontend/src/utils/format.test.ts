import { describe, expect, it } from 'vitest'
import {
  CATEGORY_LABELS,
  DIFFICULTY_LABELS,
  MY_STATUS_LABELS,
  PROVIDER_LABELS,
  formatDateTime,
  scoreColor,
} from './format'

describe('format 工具函数', () => {
  it('格式化时间', () => {
    expect(formatDateTime('2026-10-23T10:30:00')).toMatch(/^2026-10-23 10:30/)
    expect(formatDateTime(null)).toBe('—')
    expect(formatDateTime(undefined)).toBe('—')
  })

  it('难度与状态映射完整', () => {
    expect(DIFFICULTY_LABELS.EASY).toBe('基础')
    expect(DIFFICULTY_LABELS.MEDIUM).toBe('进阶')
    expect(DIFFICULTY_LABELS.HARD).toBe('挑战')
    expect(MY_STATUS_LABELS.NOT_STARTED).toBe('未开始')
    expect(MY_STATUS_LABELS.DRAFTING).toBe('草稿中')
    expect(MY_STATUS_LABELS.COMPLETED).toBe('已完成')
  })

  it('类目与评估来源映射完整', () => {
    expect(Object.keys(CATEGORY_LABELS)).toHaveLength(13);
    expect(CATEGORY_LABELS['api-design']).toBe('需求分析与 API 设计');
    expect(CATEGORY_LABELS['java-core']).toBe('Java 核心与并发');
    expect(CATEGORY_LABELS['system-design']).toBe('系统设计场景');
    expect(DIFFICULTY_LABELS.EXPERT).toBe('企业实战');
    expect(PROVIDER_LABELS.MOCK).toBe('本地模拟评估');
    expect(PROVIDER_LABELS.REMOTE).toBe('AI 模型评估');
    expect(PROVIDER_LABELS.MOCK_FALLBACK).toContain('降级');
  })

  it('分数颜色区分强弱', () => {
    expect(scoreColor(9)).not.toBe(scoreColor(5))
    expect(scoreColor(5)).not.toBe(scoreColor(0))
  })
})
