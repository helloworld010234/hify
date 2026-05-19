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
    }
  ]
})

export default router
