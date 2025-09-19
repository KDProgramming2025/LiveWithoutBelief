export interface ServerUser {
  id: string
  username?: string | null
  createdAt?: string
  lastLogin?: string
}

export interface RegisterRes {
  user: ServerUser
}

export interface PwdRes {
  user: ServerUser
}
