import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/providers'
    },
    {
      path: '/providers',
      name: 'Providers',
      component: () => import('@/views/provider/index.vue')
    },
    {
      path: '/agents',
      name: 'Agents',
      component: () => import('@/views/agent/index.vue')
    },
    {
      path: '/chat',
      name: 'Chat',
      component: () => import('@/views/chat/index.vue')
    },
    {
      path: '/knowledge-bases',
      name: 'KnowledgeBases',
      component: () => import('@/views/knowledge/index.vue')
    },
    {
      path: '/knowledge-bases/:id/documents',
      name: 'KnowledgeDocuments',
      component: () => import('@/views/knowledge/documents.vue')
    }
  ]
})

export default router
