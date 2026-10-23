import { describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from './auth'

describe('auth store', () => {
  it('未登录状态：token 与用户为空', () => {
    setActivePinia(createPinia())
    localStorage.clear()
    const auth = useAuthStore()
    expect(auth.isLoggedIn).toBe(false)
    expect(auth.isAdmin).toBe(false)
  })

  it('setAuth 后处于登录态，role=ADMIN 时 isAdmin 为真', () => {
    setActivePinia(createPinia())
    localStorage.clear()
    const auth = useAuthStore()
    auth.setAuth('token-abc', { id: 1, username: 'admin', nickname: '管理员', role: 'ADMIN' })
    expect(auth.isLoggedIn).toBe(true)
    expect(auth.isAdmin).toBe(true)
    expect(auth.token).toBe('token-abc')

    auth.logout()
    expect(auth.isLoggedIn).toBe(false)
    expect(localStorage.getItem('jobsim.token')).toBeNull()
  })
})
