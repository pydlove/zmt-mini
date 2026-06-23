import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '../components/AppLayout.vue'

const routes = [
  {
    path: '/',
    component: AppLayout,
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '仪表盘', perm: 'dashboard' } },
      { path: 'tracks', name: 'TrackManage', component: () => import('../views/TrackManage.vue'), meta: { title: '赛道管理', perm: 'track' } },
      { path: 'bloggers', name: 'BloggerManage', component: () => import('../views/BloggerManage.vue'), meta: { title: '博主管理', perm: 'blogger' } },
      { path: 'posts', name: 'PostManage', component: () => import('../views/PostManage.vue'), meta: { title: '文章管理', perm: 'post' } },
      { path: 'users', name: 'UserManage', component: () => import('../views/UserManage.vue'), meta: { title: '用户管理', perm: 'user' } },
      { path: 'admins', name: 'AdminManage', component: () => import('../views/AdminManage.vue'), meta: { title: '管理员管理', perm: 'admin' } },
      { path: 'roles', name: 'RoleManage', component: () => import('../views/RoleManage.vue'), meta: { title: '角色权限', perm: 'role' } },
      { path: 'guides', name: 'GuideManage', component: () => import('../views/GuideManage.vue'), meta: { title: '创作技巧', perm: 'guide' } },
      { path: 'helps', name: 'HelpManage', component: () => import('../views/HelpManage.vue'), meta: { title: '帮助文档', perm: 'help' } },
      { path: 'export-templates', name: 'ExportTemplateManage', component: () => import('../views/ExportTemplateManage.vue'), meta: { title: '导出模板管理', perm: 'export-template' } },
      { path: 'model-config', name: 'ModelConfigManage', component: () => import('../views/ModelConfigManage.vue'), meta: { title: '模型配置', perm: 'config' } },
      { path: 'config', name: 'ConfigManage', component: () => import('../views/ConfigManage.vue'), meta: { title: '系统配置', perm: 'config' } },
      { path: 'membership-plans', name: 'MembershipPlanManage', component: () => import('../views/MembershipPlanManage.vue'), meta: { title: '会员权益', perm: 'membership-plan' } },
      { path: 'title-library', name: 'TitleLibrarySimple', component: () => import('../views/TitleLibrarySimple.vue'), meta: { title: '标题库', perm: 'title-library' } },
      { path: 'title-library-track-stats', name: 'TitleLibraryTrackStats', component: () => import('../views/TitleLibraryTrackStats.vue'), meta: { title: '赛道统计视图', perm: 'title-library' } },
      { path: 'title-match', name: 'TitleLibraryManage', component: () => import('../views/TitleLibraryManage.vue'), meta: { title: '标题匹配', perm: 'title-library' } },
      { path: 'article-review', name: 'ArticleReviewManage', component: () => import('../views/ArticleReviewManage.vue'), meta: { title: '文章审核', perm: 'title-library' } },
      { path: 'push-overview', name: 'PushOverview', component: () => import('../views/PushOverview.vue'), meta: { title: '推送概览', perm: 'title-library' } },
      { path: 'banned-words', name: 'BannedWordManage', component: () => import('../views/BannedWordManage.vue'), meta: { title: '违禁词管理', perm: 'config' } },
      { path: 'orders', name: 'OrderManage', component: () => import('../views/OrderManage.vue'), meta: { title: '收益管理', perm: 'config' } },
      { path: 'expire-reminder', name: 'ExpireReminder', component: () => import('../views/ExpireReminder.vue'), meta: { title: '到期提醒', perm: 'user' } },
      { path: 'process', name: 'ProcessManage', component: () => import('../views/ProcessManage.vue'), meta: { title: '流程管理', perm: 'title-library' } },
      { path: 'task-list', name: 'TaskListManage', component: () => import('../views/TaskListManage.vue'), meta: { title: '生成文章任务', perm: 'task-list' } },
      { path: 'title-generate', name: 'TitleGenerateManage', component: () => import('../views/TitleGenerateManage.vue'), meta: { title: '生成标题', perm: 'title-generate' } },
      { path: 'announcements', name: 'AnnouncementManage', component: () => import('../views/AnnouncementManage.vue'), meta: { title: '公告管理', perm: 'title-library' } },
      { path: 'prompt-templates', name: 'PromptTemplateManage', component: () => import('../views/PromptTemplateManage.vue'), meta: { title: '提示词管理', perm: 'title-generate' } },
      { path: 'image-library', name: 'ImageLibraryManage', component: () => import('../views/ImageLibraryManage.vue'), meta: { title: '图片库', perm: 'title-library' } },
      { path: 'customer-dialogues', name: 'CustomerDialogueManage', component: () => import('../views/CustomerDialogueManage.vue'), meta: { title: '客服对话', perm: 'config' } },
      { path: 'ai-flavor-rules', name: 'AiFlavorRuleManage', component: () => import('../views/AiFlavorRuleManage.vue'), meta: { title: 'AI去除规则', perm: 'config' } },
      { path: 'writing-styles', name: 'WritingStyleManage', component: () => import('../views/WritingStyleManage.vue'), meta: { title: '写作风格库', perm: 'config' } },
      { path: 'title-banned-words', name: 'TitleBannedWordManage', component: () => import('../views/TitleBannedWordManage.vue'), meta: { title: '标题禁用词库', perm: 'config' } },
      { path: 'user-homogeneity', name: 'UserHomogeneityView', component: () => import('../views/UserHomogeneityView.vue'), meta: { title: '用户同质化', perm: 'title-library' } },
      { path: 'agent-config', name: 'AgentConfigManage', component: () => import('../views/AgentConfigManage.vue'), meta: { title: 'AI Agent 配置', perm: 'config' } },
      { path: 'agent-executions', name: 'AgentExecutionManage', component: () => import('../views/AgentExecutionManage.vue'), meta: { title: 'AI Agent 执行记录', perm: 'config' } },
      // FIXME: AI 检测功能暂时禁用
//       { path: 'article-ai-detect', name: 'ArticleAiDetect', component: () => import('../views/ArticleAiDetect.vue'), meta: { title: 'AI检测', perm: 'title-library' } },
    ],
  },
  { path: '/login', name: 'Login', component: () => import('../views/LoginView.vue'), meta: { title: '管理员登录' } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

function hasRoutePerm(to) {
  if (to.path === '/login') return true
  const perm = to.meta?.perm
  if (!perm) return true
  try {
    const perms = JSON.parse(localStorage.getItem('admin-perms') || '[]')
    if (perms.includes('all')) return true
    return perms.includes(perm)
  } catch (e) {
    return false
  }
}

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('admin-token')
  if (!token && to.path !== '/login') {
    next('/login')
    return
  }
  if (token && !hasRoutePerm(to)) {
    next('/dashboard')
    return
  }
  next()
})

export default router
