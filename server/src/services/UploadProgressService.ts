import { EventEmitter } from 'node:events'

export interface UploadStatus {
  loaded: number
  total: number
  status: 'pending' | 'uploading' | 'processing' | 'completed' | 'error'
  message?: string
}

class UploadProgressService extends EventEmitter {
  private uploads = new Map<string, UploadStatus>()

  init(id: string, total: number) {
    this.uploads.set(id, { loaded: 0, total, status: 'pending' })
    this.emit('progress', id, this.uploads.get(id))
  }

  update(id: string, loaded: number) {
    const current = this.uploads.get(id)
    if (current) {
      current.loaded = loaded
      current.status = 'uploading'
      this.emit('progress', id, current)
    }
  }

  setStatus(id: string, status: UploadStatus['status'], message?: string) {
    const current = this.uploads.get(id)
    if (current) {
      current.status = status
      if (message) current.message = message
      this.emit('progress', id, current)
    }
  }

  get(id: string) {
    return this.uploads.get(id)
  }

  cleanup(id: string) {
    this.uploads.delete(id)
  }
}

export const uploadProgress = new UploadProgressService()
