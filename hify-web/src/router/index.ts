import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/chat',
  },
  {
    path: '/',
    meta: { title: '工作台' },
    children: [
      {
        path: 'provider',
        name: 'Provider',
        component: () => import('@/views/provider/ProviderList.vue'),
        meta: { title: '模型管理' },
      },
      {
        path: 'agent',
        name: 'Agent',
        component: () => import('@/views/agent/AgentList.vue'),
        meta: { title: 'Agent 管理' },
      },
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('@/views/chat/ChatView.vue'),
        meta: { title: '对话' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

export default router
