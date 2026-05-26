import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/chat',
    },
    {
      path: '/provider',
      name: 'Provider',
      component: () => import('@/views/provider/ProviderList.vue'),
      meta: { title: '模型提供商' },
    },
    {
      path: '/agent',
      name: 'Agent',
      component: () => import('@/views/agent/AgentList.vue'),
      meta: { title: 'Agent 配置' },
    },
    {
      path: '/chat',
      name: 'Chat',
      component: () => import('@/views/chat/ChatView.vue'),
      meta: { title: '对话' },
    },
  ],
})

export default router
