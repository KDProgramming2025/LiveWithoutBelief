import { createServer } from './server/app.js'
import { env } from './server/config/env.js'

const app = createServer()
const port = env.PORT

app.listen(port, () => {
  // eslint-disable-next-line no-console
  console.log(`LWB server listening on http://0.0.0.0:${port}`)
})
