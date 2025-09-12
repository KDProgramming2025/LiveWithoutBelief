import { render, screen } from '@testing-library/react'
import '@testing-library/jest-dom'
import { vi, it, expect } from 'vitest'
import App from './App'

vi.stubGlobal('fetch', (url: string) => {
  if (url.includes('/ingestion/queue')) {
    return Promise.resolve({ json: () => Promise.resolve({ pending: 0, running: 0, failed: 0, items: [] }) }) as any
  }
  return Promise.resolve({ json: () => Promise.resolve([]) }) as any
})

it('renders headings', () => {
  render(<App />)
  expect(screen.getByText(/Ingestion Queue/i)).toBeInTheDocument()
  expect(screen.getByText(/User Support/i)).toBeInTheDocument()
})
