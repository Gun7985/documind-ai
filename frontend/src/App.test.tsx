import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { AppRoutes } from './App'

describe('AppRoutes', () => {
  it('renders the document detail placeholder route', () => {
    render(
      <MemoryRouter initialEntries={['/documents/example-id']}>
        <AppRoutes />
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { name: '문서 상세' })).toBeInTheDocument()
  })

  it('renders the not found page for an unknown route', () => {
    render(
      <MemoryRouter initialEntries={['/missing']}>
        <AppRoutes />
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { name: '페이지를 찾을 수 없습니다' })).toBeInTheDocument()
  })
})
