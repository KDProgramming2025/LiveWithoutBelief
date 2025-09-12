import { defineConfig } from 'tsup';

export default defineConfig({
  entry: ['src/index.ts', 'src/server.ts'],
  format: ['cjs'],
  sourcemap: true,
  clean: true,
  target: 'node18',
  dts: false,
  noExternal: ['fastify', '@fastify/cors'],
  platform: 'node',
  minify: false,
});
