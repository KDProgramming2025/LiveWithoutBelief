// Shared types for Admin API

export type UserSummary = { id: string; username: string; createdAt: string; lastLogin?: string };

export type AdminJwt = { sub: string; typ: 'admin'; iat: number; exp: number };

export type ArticleMeta = {
  id: string; // slug
  title: string;
  createdAt: string;
  updatedAt: string;
  order: number;
  filename: string; // stored original docx filename
  securePath: string; // /opt/lwb-admin-api/data/articles/slug/...
  publicPath: string; // /var/www/LWB/Articles/slug
  cover?: string; // absolute public URL
  icon?: string;  // absolute public URL
};

export type MenuItem = {
  id: string; // slug
  title: string;
  label: string;
  order: number;
  updatedAt: string;
  icon?: string; // absolute public URL
};
