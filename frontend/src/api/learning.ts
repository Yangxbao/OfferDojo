import type { LearningResourceVO, LearningTrackVO, PlanItemVO, PageResult } from './types'
import { request } from './http'

export const learningApi = {
  resources: (params: {
    track?: string
    sourceType?: string
    licenseStatus?: string
    verifiedStatus?: string
    qualityLevel?: string
    keyword?: string
    orderBy?: string
    minStars?: number
    featuredOnly?: boolean
    page?: number
    size?: number
  }) => request<PageResult<LearningResourceVO>>({ url: '/learning/resources', method: 'get', params }),

  resourceDetail: (id: number) =>
    request<LearningResourceVO>({ url: `/learning/resources/${id}`, method: 'get' }),

  tracks: (track?: string) =>
    request<LearningTrackVO[]>({ url: '/learning/tracks', method: 'get', params: { track: track || undefined } }),

  myPlan: (status?: string) =>
    request<PlanItemVO[]>({ url: '/learning/plan', method: 'get', params: { status: status || undefined } }),

  addPlan: (itemType: string, itemCode: string) =>
    request<PlanItemVO>({ url: '/learning/plan', method: 'post', data: { itemType, itemCode } }),

  updatePlan: (id: number, status: string, note?: string) =>
    request<PlanItemVO>({ url: `/learning/plan/${id}`, method: 'put', data: { status, note } }),

  removePlan: (id: number) =>
    request<void>({ url: `/learning/plan/${id}`, method: 'delete' }),

  myPlanResourceCodes: async (): Promise<string[]> => {
    const items = await request<PlanItemVO[]>({ url: '/learning/plan', method: 'get', params: { itemType: 'RESOURCE' } })
    return items.map((i) => i.itemCode)
  },
}

export type { LearningResourceVO, LearningTrackVO, PlanItemVO }
