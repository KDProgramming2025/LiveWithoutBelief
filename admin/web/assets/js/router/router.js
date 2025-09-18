import { state } from '../core/state.js'
import { viewMenu } from '../views/menu.js'
import { viewArticles } from '../views/articles.js'
import { viewUsers } from '../views/users.js'
import { refreshIcons } from '../icons/lucide.js'

const registry = {
  menu: viewMenu,
  articles: viewArticles,
  users: viewUsers
}

export async function render(view){
  state.view = view
  document.querySelectorAll('.nav-item').forEach(b => b.classList.toggle('active', b.dataset.view === view))
  const content = document.getElementById('content')
  content.classList.toggle('content--wide', view === 'articles')
  content.innerHTML = ''
  content.appendChild(await registry[view]())
  // Re-apply Lucide icons for any newly injected [data-lucide] nodes
  await refreshIcons()
}
