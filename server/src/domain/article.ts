export interface ManifestItemDto {
  id: string
  title: string
  slug: string
  version: number
  updatedAt: string
  wordCount: number
}

export interface SectionDto {
  order: number
  kind: string
  level?: number
  text?: string
  html?: string
  mediaRefId?: string
}

export interface MediaDto {
  id: string
  type: string
  filename?: string
  contentType?: string
  src?: string
  checksum?: string
}

export interface ArticleDto {
  id: string
  slug: string
  title: string
  version: number
  wordCount: number
  updatedAt: string
  checksum: string
  signature?: string
  html?: string
  text?: string
  sections: SectionDto[]
  media: MediaDto[]
}
