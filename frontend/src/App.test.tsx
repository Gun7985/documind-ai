import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { AppRoutes } from './App'
import { AuthContext } from './auth/AuthContext'
import type { AuthContextValue } from './auth/AuthContext'

function authValue(authenticated: boolean): AuthContextValue {
  return {
    configured: true,
    loading: false,
    session: null,
    user: authenticated ? ({ id: 'user-id' } as AuthContextValue['user']) : null,
    signUp: vi.fn(),
    signIn: vi.fn(),
    signOut: vi.fn().mockResolvedValue({ error: null }),
    requestPasswordReset: vi.fn(),
    updatePassword: vi.fn(),
  }
}

function renderRoute(path: string, authenticated = false) {
  return render(
    <AuthContext.Provider value={authValue(authenticated)}>
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
      </MemoryRouter>
    </AuthContext.Provider>,
  )
}

describe('AppRoutes', () => {
  it('renders the document detail route for an authenticated user', () => {
    renderRoute('/documents/example-id', true)
    expect(screen.getByRole('heading', { name: '문서 상세' })).toBeInTheDocument()
  })

  it('redirects an unauthenticated dashboard request to login', () => {
    renderRoute('/dashboard')
    expect(screen.getByRole('heading', { name: '로그인' })).toBeInTheDocument()
  })

  it('renders the not found page for an unknown route', () => {
    renderRoute('/missing')
    expect(screen.getByRole('heading', { name: '페이지를 찾을 수 없습니다' })).toBeInTheDocument()
  })
})
