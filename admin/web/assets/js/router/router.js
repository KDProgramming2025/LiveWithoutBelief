import { state } from '../core/state.js'
import { viewMenu } from '../views/menu.js'
import { viewArticles } from '../views/articles.js'
import { viewUsers } from '../views/users.js'

const registry = {
  menu: viewMenu,
  articles: viewArticles,
  users: viewUsers
}

export async function render(view){
  state.view = view
  document.querySelectorAll('.nav-item').forEach(b => b.classList.toggle('active', b.dataset.view === view))
  const content = document.getElementById('content')
  content.innerHTML = ''
  content.appendChild(await registry[view]())
}
