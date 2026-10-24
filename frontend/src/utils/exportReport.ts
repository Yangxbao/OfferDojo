import type { EvaluationVO } from '@/api/types'
import { PROVIDER_LABELS } from './format'

/** 把评估报告导出为 Markdown 文件（面试前的复习资料） */
export function downloadReportMarkdown(taskTitle: string, evaluation: EvaluationVO): void {
  const md = buildReportMarkdown(taskTitle, evaluation)
  const blob = new Blob([md], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `评估报告-${taskTitle.slice(0, 30)}.md`
  a.click()
  URL.revokeObjectURL(url)
}

export function buildReportMarkdown(taskTitle: string, evaluation: EvaluationVO): string {
  const lines: string[] = []
  lines.push(`# 评估报告：${taskTitle}`)
  lines.push('')
  lines.push(`- 总分：**${evaluation.totalScore.toFixed(1)} / 10**`)
  lines.push(`- 评估来源：${PROVIDER_LABELS[evaluation.provider] ?? evaluation.provider}${evaluation.model ? `（${evaluation.model}）` : ''}`)
  lines.push(`- 时间：${evaluation.createdAt}`)
  lines.push('')
  lines.push('> AI 评分仅供参考，请人工核对技术结论。')

  lines.push('')
  lines.push('## 各维度评分')
  for (const d of evaluation.dimensionScores) {
    lines.push(`- **${d.dimension}**：${d.score.toFixed(1)} / 10（权重 ${d.weight}%）—— ${d.comment}`)
  }

  if (evaluation.strengths.length) {
    lines.push('')
    lines.push('## 答案中的正确做法')
    evaluation.strengths.forEach((s) => lines.push(`- ${s}`))
  }
  if (evaluation.problems.length) {
    lines.push('')
    lines.push('## 存在的问题')
    evaluation.problems.forEach((s) => lines.push(`- ${s}`))
  }
  if (evaluation.edgeCases.length) {
    lines.push('')
    lines.push('## 可能遗漏的边界情况')
    evaluation.edgeCases.forEach((s) => lines.push(`- ${s}`))
  }
  if (evaluation.suggestions.length) {
    lines.push('')
    lines.push('## 改进建议')
    evaluation.suggestions.forEach((s) => lines.push(`- ${s}`))
  }
  if (evaluation.followUps.length) {
    lines.push('')
    lines.push('## 技术追问（自测）')
    evaluation.followUps.forEach((s, i) => lines.push(`${i + 1}. ${s}`))
  }
  if (evaluation.nextTasks.length) {
    lines.push('')
    lines.push('## 建议下一步练习')
    evaluation.nextTasks.forEach((t) => lines.push(`- ${t.title}（${t.categoryLabel}）`))
  }
  lines.push('')
  lines.push('---')
  lines.push('*由 OfferDojo 生成*')
  return lines.join('\n')
}
