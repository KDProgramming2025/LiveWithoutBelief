import { jsx as _jsx } from "react/jsx-runtime";
import { render, screen, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import { vi, it, expect } from 'vitest';
import App from './App';
// Minimal fetch stub for current Admin UI flows
vi.stubGlobal('fetch', (url, init) => {
    const u = String(url);
    if (u.includes('/v1/admin/session')) {
        return Promise.resolve({ ok: true, json: async () => ({ authenticated: true, username: 'admin' }) });
    }
    if (u.includes('/v1/admin/articles')) {
        return Promise.resolve({ ok: true, json: async () => ({ items: [] }) });
    }
    if (u.includes('/v1/admin/users/summary')) {
        return Promise.resolve({ ok: true, json: async () => ({ total: 0 }) });
    }
    if (u.includes('/v1/admin/users/search')) {
        return Promise.resolve({ ok: true, json: async () => ({ query: '', users: [] }) });
    }
    if (u.includes('/v1/admin/logout')) {
        return Promise.resolve({ ok: true, json: async () => ({ ok: true }) });
    }
    return Promise.resolve({ ok: true, json: async () => ({}) });
});
it('renders Admin UI with Articles section and Users navigation', async () => {
    render(_jsx(App, {}));
    await waitFor(() => expect(screen.getAllByText(/Articles/i).length).toBeGreaterThan(0));
    // Users appears in the nav item text
    expect(screen.getByText(/Users/i)).toBeInTheDocument();
});
