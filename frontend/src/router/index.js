import { createRouter, createWebHistory } from 'vue-router'
import { getProfile, getToken } from '../api/http'

const routes = [
  { path: '/login', component: () => import('../views/Login.vue'), meta: { guest: true } },
  { path: '/register', component: () => import('../views/Register.vue'), meta: { guest: true } },
  {
    path: '/',
    component: () => import('../layouts/AppShell.vue'),
    meta: { auth: true },
    children: [
      { path: '', component: () => import('../views/Home.vue') },
      { path: 'kb/:id/insights', component: () => import('../views/KbInsights.vue') },
      { path: 'kb/:id/graph', component: () => import('../views/KbGraph.vue') },
      { path: 'kb/:id', component: () => import('../views/KbDetail.vue') },
      { path: 'documents', component: () => import('../views/DocumentCenter.vue') },
      { path: 'model-settings', component: () => import('../views/placeholders/ModelSettings.vue') },
      { path: 'prompt-templates', component: () => import('../views/placeholders/PromptTemplates.vue') },
      { path: 'system-settings', component: () => import('../views/placeholders/SystemSettings.vue') },
      { path: 'system-status', component: () => import('../views/placeholders/SystemStatus.vue') },
      { path: 'help-support', component: () => import('../views/placeholders/HelpSupport.vue') },
      { path: 'profile', component: () => import('../views/placeholders/ProfileCenter.vue') },
      { path: 'admin/audit', component: () => import('../views/AdminAudit.vue'), meta: { admin: true } },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const token = getToken()
  if (to.meta.auth && !token) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.meta.guest && token) return { path: '/' }
  const needAdmin = to.matched.some((r) => r.meta.admin)
  if (needAdmin && token) {
    const p = getProfile()
    if (!p || p.role !== 'ADMIN') return { path: '/' }
  }
  return true
})

export default router
