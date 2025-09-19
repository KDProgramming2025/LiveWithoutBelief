import express, { Router } from 'express'
import { Pool } from 'pg'
import { MenuService } from '../../services/MenuService.js'

// Public menu router: exposes read-only list of menu items created via admin
// Response shape mirrors MenuService.MenuItem[]
export const menuRouter: Router = Router()

const pool = new Pool()
const menuSvc = new MenuService(pool)

menuRouter.get('/', async (_req: express.Request, res: express.Response) => {
  const items = await menuSvc.list()
  res.json({ items })
})
