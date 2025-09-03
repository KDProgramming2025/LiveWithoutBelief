import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'node',
    coverage: {
      reporter: ['text', 'lcov'],
      lines: 80,
      statements: 80,
      branches: 70,
      functions: 80,
    }
  }
});