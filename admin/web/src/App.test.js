import { jsx as _jsx } from "react/jsx-runtime";
import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom';
import { vi, it, expect } from 'vitest';
import App from './App';
vi.stubGlobal('fetch', (url) => {
    if (url.includes('/ingestion/queue')) {
        return Promise.resolve({ json: () => Promise.resolve({ pending: 0, running: 0, failed: 0, items: [] }) });
    }
    return Promise.resolve({ json: () => Promise.resolve([]) });
});
it('renders headings', () => {
    render(_jsx(App, {}));
    expect(screen.getByText(/Ingestion Queue/i)).toBeInTheDocument();
    expect(screen.getByText(/User Support/i)).toBeInTheDocument();
});
