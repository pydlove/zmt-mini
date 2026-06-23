<script setup>
import { computed, inject, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Layout, Avatar, Modal, Form, Input, message, Dropdown, Menu, Space, Breadcrumb, Drawer } from 'ant-design-vue'
import { SearchOutlined, DownOutlined, LockOutlined, LogoutOutlined, BulbOutlined, BulbFilled, MenuOutlined, CloseOutlined } from '@ant-design/icons-vue'
import { onMounted, onUnmounted } from 'vue'
import request from '../api/request.js'

const route = useRoute()
const router = useRouter()

const toggleTheme = inject('toggleTheme')
const isDark = inject('isDark')

const pageTitle = computed(() => route.meta?.title || '管理后台')

const layoutBg = computed(() => isDark.value ? '#141414' : '#f0f2f5')
const pageHeaderBg = computed(() => isDark.value ? '#1f1f1f' : '#fff')
const pageHeaderBorder = computed(() => isDark.value ? '#333' : '#f0f0f0')

const adminUser = computed(() => {
  try {
    return JSON.parse(localStorage.getItem('admin-user') || '{}')
  } catch (e) {
    return {}
  }
})

const adminPerms = computed(() => {
  try {
    return JSON.parse(localStorage.getItem('admin-perms') || '[]')
  } catch (e) {
    return []
  }
})

const isSuperAdmin = computed(() => adminPerms.value.includes('all'))

function hasPerm(perm) {
  if (isSuperAdmin.value) return true
  return adminPerms.value.includes(perm)
}

const menuGroups = [
  {
    label: '概览',
    items: [{ key: '/dashboard', label: '仪表盘', perm: 'dashboard' }],
  },
  {
    label: '内容管理',
    items: [
      { key: '/tracks', label: '赛道管理', perm: 'track' },
      { key: '/bloggers', label: '博主管理', perm: 'blogger' },
      { key: '/posts', label: '文章管理', perm: 'post' },
      { key: '/guides', label: '创作技巧', perm: 'guide' },
      { key: '/export-templates', label: '导出模板管理', perm: 'export-template' },
      { key: '/helps', label: '帮助文档', perm: 'help' },
    ],
  },
  {
    label: '用户管理',
    items: [
      { key: '/users', label: '用户管理', perm: 'user' },
      { key: '/orders', label: '收益管理', perm: 'config' },
      { key: '/expire-reminder', label: '到期提醒', perm: 'user' },
      { key: '/membership-plans', label: '会员权益', perm: 'membership-plan' },
    ],
  },
  {
    label: '工作管理',
    items: [
      { key: '/title-library', label: '标题库', perm: 'title-library' },
      { key: '/title-match', label: '标题匹配', perm: 'title-library' },
      { key: '/article-review', label: '文章审核', perm: 'title-library' },
      { key: '/push-overview', label: '推送概览', perm: 'title-library' },
      { key: '/user-homogeneity', label: '用户同质化', perm: 'title-library' },
      { key: '/announcements', label: '公告管理', perm: 'title-library' },
      { key: '/title-generate', label: '生成标题任务', perm: 'title-generate' },
      { key: '/task-list', label: '生成文章任务', perm: 'task-list' },
      { key: '/prompt-templates', label: '提示词管理', perm: 'title-generate' },
      { key: '/image-library', label: '图片库', perm: 'title-library' },
    ],
  },
  {
    label: '运营管理',
    items: [
      { key: '/customer-dialogues', label: '客服对话', perm: 'config' },
      { key: '/process', label: '流程管理', perm: 'title-library' },
      { key: '/banned-words', label: '违禁词管理', perm: 'config' },
      { key: '/title-banned-words', label: '标题禁用词库', perm: 'config' },
      { key: '/writing-styles', label: '写作风格库', perm: 'config' },
      { key: '/ai-flavor-rules', label: 'AI去除规则', perm: 'config' },
    ],
  },
  {
    label: '系统管理',
    items: [
      { key: '/admins', label: '管理员管理', perm: 'admin' },
      { key: '/roles', label: '角色权限', perm: 'role' },
      { key: '/model-config', label: '模型配置', perm: 'config' },
      { key: '/config', label: '系统配置', perm: 'config' },
      { key: '/agent-config', label: 'AI Agent 配置', perm: 'config' },
      { key: '/agent-executions', label: 'AI Agent 执行记录', perm: 'config' },
    ],
  },
]

const filteredGroups = computed(() => {
  return menuGroups.map(group => ({
    ...group,
    items: group.items.filter(i => hasPerm(i.perm)),
  })).filter(group => group.items.length > 0)
})

const menuItems = computed(() => {
  return filteredGroups.value.map(group => ({
    key: group.label,
    label: group.label,
    children: group.items.map(item => ({
      key: item.key,
      label: item.label,
    })),
  }))
})

const selectedKeys = ref([])

watch(
  () => route.path,
  () => {
    for (const group of filteredGroups.value) {
      for (const item of group.items) {
        if (route.path === item.key) {
          selectedKeys.value = [item.key]
          return
        }
      }
    }
    selectedKeys.value = []
  },
  { immediate: true }
)

const searchKeyword = ref('')
const searchOpen = ref(false)

const isMobile = ref(false)
const drawerOpen = ref(false)

function checkMobile() {
  isMobile.value = window.innerWidth < 768
}

function toggleDrawer() {
  drawerOpen.value = !drawerOpen.value
}

function closeDrawer() {
  drawerOpen.value = false
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})

const allMenuItems = [
  { key: '/dashboard', label: '仪表盘', perm: 'dashboard' },
  { key: '/tracks', label: '赛道管理', perm: 'track' },
  { key: '/bloggers', label: '博主管理', perm: 'blogger' },
  { key: '/posts', label: '文章管理', perm: 'post' },
  { key: '/users', label: '用户管理', perm: 'user' },
  { key: '/guides', label: '创作技巧', perm: 'guide' },
  { key: '/export-templates', label: '导出模板管理', perm: 'export-template' },
  { key: '/helps', label: '帮助文档', perm: 'help' },
  { key: '/subscription-posts', label: '订阅文章', perm: 'subscription-post' },
  { key: '/admins', label: '管理员管理', perm: 'admin' },
  { key: '/roles', label: '角色权限', perm: 'role' },
  { key: '/model-config', label: '模型配置', perm: 'config' },
  { key: '/config', label: '系统配置', perm: 'config' },
  { key: '/membership-plans', label: '会员权益', perm: 'membership-plan' },
  { key: '/title-library', label: '标题库', perm: 'title-library' },
  { key: '/title-match', label: '标题匹配', perm: 'title-library' },
  { key: '/push-overview', label: '推送概览', perm: 'title-library' },
  { key: '/user-homogeneity', label: '用户同质化', perm: 'title-library' },
  { key: '/announcements', label: '公告管理', perm: 'title-library' },
  { key: '/task-list', label: '生成文章任务', perm: 'task-list' },
  { key: '/title-generate', label: '生成标题任务', perm: 'title-generate' },
  { key: '/prompt-templates', label: '提示词管理', perm: 'title-generate' },
  { key: '/image-library', label: '图片库', perm: 'title-library' },
  { key: '/banned-words', label: '违禁词管理', perm: 'config' },
  { key: '/title-banned-words', label: '标题禁用词库', perm: 'config' },
  { key: '/orders', label: '收益管理', perm: 'config' },
  { key: '/expire-reminder', label: '到期提醒', perm: 'user' },
  { key: '/process', label: '流程管理', perm: 'title-library' },
  { key: '/customer-dialogues', label: '客服对话', perm: 'config' },
  { key: '/ai-flavor-rules', label: 'AI去除规则', perm: 'config' },
]

const filteredMenuItems = computed(() => {
  if (!searchKeyword.value.trim()) return []
  const kw = searchKeyword.value.trim().toLowerCase()
  return allMenuItems
    .filter(i => hasPerm(i.perm) && i.label.toLowerCase().includes(kw))
    .slice(0, 8)
})

function onMenuClick({ key }) {
  router.push(key)
  searchKeyword.value = ''
  searchOpen.value = false
  if (isMobile.value) {
    drawerOpen.value = false
  }
}

function closeSearchDropdown() {
  setTimeout(() => {
    searchOpen.value = false
  }, 200)
}

const logoImage = computed(() => {
  return new URL('/src/assets/wechat.png', import.meta.url).href
})

const pwdModalOpen = ref(false)
const pwdForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })

function handleDropdownClick({ key }) {
  if (key === 'logout') {
    localStorage.removeItem('admin-token')
    localStorage.removeItem('admin-user')
    localStorage.removeItem('admin-perms')
    router.push('/login')
  } else if (key === 'password') {
    pwdModalOpen.value = true
    pwdForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  }
}

async function handlePwdOk() {
  if (!pwdForm.value.oldPassword || !pwdForm.value.newPassword || !pwdForm.value.confirmPassword) {
    message.warning('请填写完整密码信息')
    return
  }
  if (pwdForm.value.newPassword !== pwdForm.value.confirmPassword) {
    message.warning('两次输入的新密码不一致')
    return
  }
  try {
    await request.post('/auth/change-password', {
      adminId: adminUser.value.id,
      oldPassword: pwdForm.value.oldPassword,
      newPassword: pwdForm.value.newPassword,
    })
    message.success('密码修改成功')
    pwdModalOpen.value = false
    pwdForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  } catch (e) {
    message.error(e?.response?.data?.msg || e?.message || '修改失败')
  }
}
</script>

<template>
  <Layout class="layout">
    <Layout.Header class="header">
      <div class="header-left">
        <div v-if="isMobile" class="hamburger" @click="toggleDrawer">
          <MenuOutlined />
        </div>
        <div class="logo">
          <img :src="logoImage" />
          <span class="logo-text">知我创作</span>
        </div>
      </div>

      <Menu
        v-if="!isMobile"
        v-model:selectedKeys="selectedKeys"
        mode="horizontal"
        :items="menuItems"
        theme="dark"
        class="header-menu"
        @click="onMenuClick"
      />

      <Space class="header-right" size="middle">
        <div v-if="!isMobile" class="search-wrap">
          <Input
            v-model:value="searchKeyword"
            placeholder="搜索菜单..."
            size="small"
            class="search-input"
            @focus="searchOpen = true"
            @blur="closeSearchDropdown"
          >
            <template #prefix>
              <SearchOutlined />
            </template>
          </Input>
          <div v-if="searchOpen && filteredMenuItems.length > 0" class="search-dropdown">
            <div
              v-for="item in filteredMenuItems"
              :key="item.key"
              class="search-item"
              :class="{ active: route.path === item.key }"
              @mousedown="onMenuClick({ key: item.key })"
            >
              {{ item.label }}
            </div>
          </div>
        </div>

        <div @click="toggleTheme" style="cursor: pointer; color: rgba(255,255,255,0.85); font-size: 16px;">
          <BulbFilled v-if="isDark" />
          <BulbOutlined v-else />
        </div>

        <Dropdown>
          <div class="user-info">
            <Avatar size="small" style="background: #1890ff;">
              {{ (adminUser.name || adminUser.username || 'A')[0].toUpperCase() }}
            </Avatar>
            <span v-if="!isMobile" class="user-name">{{ adminUser.name || adminUser.username }}</span>
            <DownOutlined v-if="!isMobile" />
          </div>
          <template #overlay>
            <Menu @click="handleDropdownClick">
              <Menu.Item key="password">
                <LockOutlined />
                修改密码
              </Menu.Item>
              <Menu.Divider />
              <Menu.Item key="logout">
                <LogoutOutlined />
                退出登录
              </Menu.Item>
            </Menu>
          </template>
        </Dropdown>
      </Space>
    </Layout.Header>

    <Layout.Content class="content">
      <div class="page-header">
        <Breadcrumb>
          <Breadcrumb.Item>
            <router-link to="/dashboard">首页</router-link>
          </Breadcrumb.Item>
          <Breadcrumb.Item>{{ pageTitle }}</Breadcrumb.Item>
        </Breadcrumb>
      </div>
      <div class="page-content">
        <router-view />
      </div>
    </Layout.Content>
  </Layout>

  <!-- 移动端抽屉菜单 -->
  <Drawer
    v-model:open="drawerOpen"
    placement="left"
    width="280"
    :closable="false"
    :body-style="{ padding: 0 }"
    class="mobile-drawer"
  >
    <div class="drawer-header">
      <div class="logo">
        <img :src="logoImage" />
        <span class="logo-text">知我创作</span>
      </div>
      <CloseOutlined class="drawer-close" @click="closeDrawer" />
    </div>
    <Menu
      v-model:selectedKeys="selectedKeys"
      mode="inline"
      :items="menuItems"
      theme="light"
      @click="onMenuClick"
    />
  </Drawer>

  <Modal v-model:open="pwdModalOpen" title="修改密码" :mask-closable="false" width="400" @ok="handlePwdOk">
    <Form layout="vertical" style="margin-top: 12px;">
      <Form.Item label="旧密码" required>
        <Input.Password v-model:value="pwdForm.oldPassword" placeholder="请输入当前密码" />
      </Form.Item>
      <Form.Item label="新密码" required>
        <Input.Password v-model:value="pwdForm.newPassword" placeholder="请输入新密码" />
        <div style="font-size: 12px; color: #999; margin-top: 4px;">密码长度不少于 8 位，需包含字母和数字</div>
      </Form.Item>
      <Form.Item label="确认新密码" required>
        <Input.Password v-model:value="pwdForm.confirmPassword" placeholder="请再次输入新密码" />
      </Form.Item>
    </Form>
  </Modal>
</template>

<style>
* { box-sizing: border-box; }
</style>

<style scoped>
.layout {
  min-height: 100vh;
  background: v-bind(layoutBg);
}

.header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  padding: 0 24px;
  height: 64px;
  background: #001529;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-right: 32px;
  flex-shrink: 0;
}

.logo img {
  width: 28px;
  height: 28px;
  object-fit: cover;
  border-radius: 4px;
}

.logo-text {
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  white-space: nowrap;
}

.header-menu {
  flex: 1;
  background: transparent;
  border-bottom: none;
  min-width: 0;
}

.header-right {
  flex-shrink: 0;
  margin-left: 16px;
}

.search-wrap {
  position: relative;
}

.search-input {
  width: 180px;
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.15);
  color: #fff;
}

.search-input :deep(.ant-input) {
  background: transparent;
  color: #fff;
}

.search-input :deep(.ant-input::placeholder) {
  color: rgba(255, 255, 255, 0.4);
}

.search-input:hover,
.search-input:focus {
  background: rgba(255, 255, 255, 0.12);
  border-color: rgba(255, 255, 255, 0.25);
}

.search-dropdown {
  position: absolute;
  top: calc(100% + 4px);
  right: 0;
  min-width: 200px;
  background: #fff;
  border-radius: 6px;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.12);
  padding: 4px;
  z-index: 1001;
}

.search-item {
  padding: 8px 12px;
  border-radius: 4px;
  font-size: 13px;
  color: #262626;
  cursor: pointer;
  transition: background 0.15s;
}

.search-item:hover {
  background: #f5f5f5;
}

.search-item.active {
  color: #1890ff;
  background: #e6f7ff;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  color: rgba(255, 255, 255, 0.85);
  cursor: pointer;
  transition: color 0.2s;
}

.user-info:hover {
  color: #fff;
}

.user-name {
  font-size: 13px;
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.content {
  padding-top: 64px;
}

.page-header {
  height: 52px;
  background: v-bind(pageHeaderBg);
  padding: 0 24px;
  border-bottom: 1px solid v-bind(pageHeaderBorder);
  display: flex;
  align-items: center;
}

.page-header .ant-breadcrumb {
  font-size: 14px;
}

.page-content {
  padding: 20px 24px;
  min-height: calc(100vh - 64px - 52px);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.hamburger {
  color: #fff;
  font-size: 18px;
  cursor: pointer;
  padding: 4px;
  line-height: 1;
}

.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.drawer-header .logo {
  margin-right: 0;
}

.drawer-header .logo-text {
  color: #001529;
}

.drawer-close {
  font-size: 16px;
  color: #999;
  cursor: pointer;
  padding: 4px;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .header {
    padding: 0 12px;
  }

  .logo-text {
    font-size: 14px;
  }

  .page-header {
    padding: 0 12px;
    height: 44px;
  }

  .page-header .ant-breadcrumb {
    font-size: 13px;
  }

  .page-content {
    padding: 12px;
    min-height: calc(100vh - 64px - 44px);
  }

  /* 全局表格横向滚动 */
  .page-content :deep(.ant-table-wrapper) {
    overflow-x: auto;
  }

  /* 全局 Card 内边距缩减 */
  .page-content :deep(.ant-card-body) {
    padding: 12px;
  }

  /* Modal 在移动端占满宽度 */
  .page-content :deep(.ant-modal) {
    max-width: 96vw !important;
  }
}
</style>
