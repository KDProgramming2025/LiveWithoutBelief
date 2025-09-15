import path from 'path'

// Centralized configuration for Admin API
export const CONFIG = {
  SECURE_ROOT: process.env.ADMIN_SECURE_ARTICLES_DIR || '/opt/lwb-admin-api/data/articles',
  PUBLIC_ROOT: process.env.ADMIN_PUBLIC_ARTICLES_DIR || '/var/www/LWB/Articles',
  PUBLIC_URL_PREFIX: (process.env.ADMIN_PUBLIC_ARTICLES_URL_PREFIX || 'https://aparat.feezor.net/LWB/Articles').replace(/\/$/, ''),
  META_FILE: process.env.ADMIN_ARTICLES_META || '/opt/lwb-admin-api/data/articles.json',
  MENU_META_FILE: process.env.ADMIN_MENU_META || '/opt/lwb-admin-api/data/menu.json',
  MENU_PUBLIC_DIR: process.env.ADMIN_PUBLIC_MENU_DIR || '/var/www/LWB/Menu',
  MENU_PUBLIC_URL_PREFIX: (process.env.ADMIN_PUBLIC_MENU_URL_PREFIX || 'https://aparat.feezor.net/LWB/Menu').replace(/\/$/, ''),
  ADMIN_USER: process.env.ADMIN_PANEL_USERNAME || '',
  ADMIN_PASS: process.env.ADMIN_PANEL_PASSWORD || '',
  JWT_SECRET: process.env.ADMIN_PANEL_JWT_SECRET || process.env.PWD_JWT_SECRET || 'CHANGE_ME_DEV',
  DB_URL: process.env.DATABASE_URL,
  MULTIPART_LIMITS: {
    files: 3,
    parts: 20,
    fields: 50,
    fileSize: 256 * 1024 * 1024, // 256 MB
  } as const,
}

export function metaDir() {
  return path.dirname(CONFIG.META_FILE)
}
