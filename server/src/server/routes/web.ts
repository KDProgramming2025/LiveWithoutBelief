import express from 'express'

// Static micro-site to support password registration with ALTCHA
export function createWebRouter() {
  const router = express.Router()
  const html = `<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>LWB Registration</title>
    <script type="module" src="https://cdn.jsdelivr.net/gh/altcha-org/altcha@main/dist/altcha.min.js" async defer></script>
    <style>
      body{font-family:system-ui,-apple-system,Segoe UI,Roboto,Ubuntu,Cantarell,Noto Sans,sans-serif;max-width:640px;margin:40px auto;padding:0 16px}
      form{display:grid;gap:12px}
      input,button{font-size:16px;padding:10px;border:1px solid #ccc;border-radius:8px}
      button{background:#1e80ff;color:#fff;border:none;cursor:pointer}
      button:disabled{background:#aaa}
      .card{padding:16px;border:1px solid #e2e2e2;border-radius:10px}
      .row{display:flex;gap:12px}
      .row > *{flex:1}
    </style>
  </head>
  <body>
    <h1>Create account</h1>
    <div class="card">
      <form id="regForm">
        <div class="row">
          <input id="username" name="username" placeholder="username" required />
          <input id="password" name="password" placeholder="password" type="password" required />
        </div>
        <altcha-widget id="altcha" name="altcha" challengeurl="/v1/altcha/challenge" auto="onsubmit"></altcha-widget>
        <button type="submit" id="submit">Register</button>
      </form>
      <pre id="out" style="white-space:pre-wrap"></pre>
    </div>
    <script type="module">
      const form = document.getElementById('regForm')
      const out = document.getElementById('out')
      form.addEventListener('submit', async (e) => {
        e.preventDefault()
        const fd = new FormData(form)
        const body = Object.fromEntries(fd.entries())
        const res = await fetch('/v1/auth/pwd/register', { method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify(body) })
        const json = await res.json().catch(()=>({}))
        out.textContent = JSON.stringify(json, null, 2)
      })
    </script>
  </body>
</html>`

  router.get('/register', (_req, res) => res.type('html').send(html))
  return router
}
