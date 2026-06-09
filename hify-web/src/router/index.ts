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
      {
        path: 'knowledge-bases',
        name: 'KnowledgeBaseList',
        component: () => import('@/views/knowledge/KnowledgeBaseList.vue'),
        meta: { title: '知识库管理' },
      },
      {
        path: 'knowledge-bases/:kbId/documents',
        name: 'DocumentList',
        component: () => import('@/views/knowledge/DocumentList.vue'),
        meta: { title: '文档管理' },
      },
      {
        path: 'workflows',
        name: 'WorkflowList',
        component: () => import('@/views/workflow/WorkflowList.vue'),
        meta: { title: '工作流管理' },
      },
      {
        path: 'workflows/create',
        name: 'WorkflowCreate',
        component: () => import('@/views/workflow/WorkflowCreate.vue'),
        meta: { title: '新建工作流' },
      },
      {
        path: 'workflows/:id',
        name: 'WorkflowDetail',
        component: () => import('@/views/workflow/WorkflowDetail.vue'),
        meta: { title: '工作流详情' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

export default router
