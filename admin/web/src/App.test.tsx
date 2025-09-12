import { render, screen, waitFor } from '@testing-library/react'
import '@testing-library/jest-dom'
import { vi, it, expect } from 'vitest'
import App from './App'

// Minimal fetch stub for current Admin UI flows
vi.stubGlobal('fetch', (url: string, init?: any) => {
  const u = String(url)
  if (u.includes('/v1/admin/session')) {
    return Promise.resolve({ ok: true, json: async () => ({ authenticated: true, username: 'admin' }) }) as any
  }
  if (u.includes('/v1/admin/articles')) {
    return Promise.resolve({ ok: true, json: async () => ({ items: [] }) }) as any
  }
  if (u.includes('/v1/admin/users/summary')) {
    return Promise.resolve({ ok: true, json: async () => ({ total: 0 }) }) as any
  }
  if (u.includes('/v1/admin/users/search')) {
    return Promise.resolve({ ok: true, json: async () => ({ query: '', users: [] }) }) as any
  }
  if (u.includes('/v1/admin/logout')) {
    return Promise.resolve({ ok: true, json: async () => ({ ok: true }) }) as any
  }
  return Promise.resolve({ ok: true, json: async () => ({}) }) as any
})

it('renders Admin UI with Articles and Users sections', async () => {
  render(<App />)
  await waitFor(() => expect(screen.getByText(/Articles/i)).toBeInTheDocument())
  expect(screen.getByText(/Users/i)).toBeInTheDocument()
})
