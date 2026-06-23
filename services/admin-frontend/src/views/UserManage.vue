<script setup>
import { ref, computed, onMounted, h, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Card, Input, InputNumber, Select, Button, Table, Tag, Modal, Form, message, Pagination, Checkbox, Upload, Switch, Tabs, DatePicker, Dropdown, Menu } from 'ant-design-vue'
import { listUsers, getUserTracks, addUserTrack, removeUserTrack, exportUsers, importUsers, batchUpdateAdmin } from '../api/user.js'
import { createOrder } from '../api/order.js'
import { listTracks } from '../api/track.js'
import { listCreations } from '../api/creation.js'
import { listExportTemplates } from '../api/exportTemplate.js'
import { uploadFile } from '../api/upload.js'
import { listMembershipPlans } from '../api/membershipPlan.js'
import request from '../api/request.js'

const route = useRoute()
const activeTab = ref('accountOpened')

// 从 localStorage 恢复
try {
  const savedTab = localStorage.getItem('userManage_activeTab')
  if (savedTab && ['accountOpened', 'distributor', 'trial', 'disabled', 'all'].includes(savedTab)) {
    activeTab.value = savedTab
  }
} catch (e) {}

const savedSearchMap = JSON.parse(localStorage.getItem('userManage_searchMap') || '{}')

// 每个 Tab 独立的搜索条件
const defaultSearch = () => ({ username: '', email: '', wxName: '', trackId: undefined, platform: undefined, adminId: undefined })
const searchMap = ref({
  accountOpened: savedSearchMap.accountOpened || defaultSearch(),
  distributor: savedSearchMap.distributor || defaultSearch(),
  trial: savedSearchMap.trial || defaultSearch(),
  disabled: savedSearchMap.disabled || defaultSearch(),
  all: savedSearchMap.all || defaultSearch(),
})

function saveUserSearchState() {
  localStorage.setItem('userManage_searchMap', JSON.stringify(searchMap.value))
  localStorage.setItem('userManage_activeTab', activeTab.value)
}

const data = ref([])
const allTracks = ref([])
const allTemplates = ref([])
const allPlans = ref([])
const allOperators = ref([])

const currentAdmin = ref({})
try {
  currentAdmin.value = JSON.parse(localStorage.getItem('admin-user') || '{}')
} catch (e) {
  currentAdmin.value = {}
}
const isSuperAdmin = computed(() => currentAdmin.value.role === '超级管理员')
const isOperatorAdmin = computed(() => currentAdmin.value.role === '运营管理员')

const columns = [
  {
    title: '用户名',
    key: 'username',
    width: 160,
    customRender: ({ record }) => {
      const name = record.username || '-'
      const nick = record.nickName
      if (nick) {
        return h('span', { title: `${name} (${nick})` }, [name, h('span', { style: 'color: #8c8c8c; font-size: 12px;' }, ` (${nick})`)])
      }
      return h('span', {}, name)
    },
  },
  { title: '邮箱', dataIndex: 'email', key: 'email', width: 150 },
  { title: '公众号名称', key: 'wxName', width: 160,
    customRender: ({ record }) => {
      const name = record.wxName || ''
      return h('div', { style: 'display: flex; align-items: center; gap: 4px;' }, [
        h('span', { style: 'flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;' }, name),
        name ? h(Button, {
          type: 'link', size: 'small', style: 'padding: 0; flex-shrink: 0;',
          onClick: (e) => {
            e.stopPropagation()
            const text = name
            try {
              if (typeof navigator !== 'undefined' && navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
                navigator.clipboard.writeText(text).then(() => message.success('已复制')).catch(() => message.error('复制失败'))
              } else {
                const ta = document.createElement('textarea')
                ta.value = text
                ta.style.position = 'fixed'
                ta.style.left = '-9999px'
                document.body.appendChild(ta)
                ta.select()
                if (document.execCommand('copy')) message.success('已复制')
                else message.error('复制失败')
                document.body.removeChild(ta)
              }
            } catch (e) {
              message.error('复制失败')
            }
          }
        }, () => '复制') : null,
      ])
    }
  },
  { title: '归属运营者', dataIndex: 'adminId', key: 'adminId', width: 120 },
  { title: '赛道信息', key: 'trackInfo', width: 140 },
  { title: '可选赛道', dataIndex: 'trackLimitText', key: 'trackLimitText', width: 90 },
  {
    title: '用户类型',
    key: 'userType',
    width: 80,
    customRender: ({ record }) => {
      const color = record.userType === 2 ? 'purple' : record.userType === 3 ? 'orange' : 'blue'
      return h(Tag, { color }, () => record.userTypeText || '-')
    },
  },
  { title: '状态', key: 'status', width: 75 },
  {
    title: '注册/到期',
    key: 'registerExpire',
    width: 190,
    customRender: ({ record }) => {
      const today = new Date().toISOString().slice(0, 10)
      const isExpired = record.expireDate && record.expireDate !== '-' && record.expireDate < today
      return h('div', {}, [
        h('div', { style: 'font-size: 12px; color: #8c8c8c;' }, `注册：${record.registerTime || '-'}`),
        h('div', { style: `font-size: 12px; ${isExpired ? 'color: #f5222d; font-weight: 500;' : 'color: #8c8c8c;'}` }, `到期：${record.expireDate || '-'}`),
      ])
    },
  },
  {
    title: '套餐/样式',
    key: 'planStyle',
    width: 140,
    customRender: ({ record }) => {
      return h('span', {}, `${record.membershipPlanName || '-'} / ${record.template || '-'}`)
    },
  },
  { title: '最近登录', dataIndex: 'lastLogin', key: 'lastLogin', width: 145 },
  {
    title: '操作',
    key: 'action',
    width: 200,
    fixed: 'right',
    customRender: ({ record }) => {
      const items = [
        { key: 'edit', label: '编辑', action: () => handleEdit(record) },
        { key: 'toggle', label: record.status === 1 ? '禁用' : '启用', action: () => toggleStatus(record) },
        { key: 'rp', label: '重置密码', action: () => resetPassword(record) },
        { key: 'cp', label: '复制信息', action: () => copyAccountInfo(record) },
        { key: 'ti', label: '赛道信息', action: () => openTrackInfoModal(record) },
        needsRecommend(record) ? { key: 'rc', label: '推荐', action: () => openRecommendModal(record) } : null,
        { key: 'nt', label: '设定下一个标题', action: () => openNextTitleModal(record) },
        { key: 'st', label: '文章样式', action: () => openStyleModal(record) },
      ].filter(Boolean)

      return h(Dropdown, {}, {
        default: () => h('a', { style: 'cursor: pointer;' }, '操作'),
        overlay: () => h(Menu, {}, () => items.map(it => h(Menu.Item, { key: it.key, onClick: it.action }, () => it.label)))
      })
    },
  },
]

const addModalOpen = ref(false)
const editModalOpen = ref(false)
const creationModalOpen = ref(false)
const currentUser = ref({})
const creationRecords = ref([])
const creationColumns = [
  { title: '标题', dataIndex: 'title', key: 'title', ellipsis: true },
  { title: '赛道', dataIndex: 'trackName', key: 'trackName', width: 100 },
  { title: '模式', dataIndex: 'mode', key: 'mode', width: 80 },
  { title: '状态', key: 'reviewed', width: 80 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 130 },
]
const platformOptions = ['公众号', '今日头条', '百家号']

const userTypeOptions = [
  { label: '开户', value: 1 },
  { label: '分成', value: 2 },
  { label: '试用', value: 3 },
]

const addForm = ref({ username: '', contactType: '手机号', contact: '', password: 'Abc123456', trackLimit: 0, platformLimit: [], expireDate: '2026-12-31', remark: '', canSetEmail: 0, membershipPlanId: undefined, userType: 1, wxName: '', nickName: '' })
const editForm = ref({ id: null, username: '', trackLimit: 0, platformLimit: [], expireDate: '2026-12-31', status: 1, remark: '', canSetEmail: 0, membershipPlanId: undefined, template: '', userType: 1, wxName: '', nickName: '', email: '', invitedBy: '' })

const currentPage = ref(1)
const pageSize = ref(10)
const selectedRowKeys = ref([])

const rowSelection = {
  onChange: (keys) => {
    selectedRowKeys.value = keys
  },
}

const filteredData = computed(() => {
  return data.value
})

const tableColumns = computed(() => {
  if (activeTab.value === 'disabled') {
    return columns.filter(c => c.key !== 'trackInfo')
  }
  return columns
})

const trialTabText = computed(() => {
  const count = data.value.filter(u => u.userType === 3).length
  return `试用用户 (${count})`
})

const distributorTabText = computed(() => {
  const count = data.value.filter(u => u.userType === 2).length
  return `分成用户 (${count})`
})

const paginatedData = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredData.value.slice(start, start + pageSize.value)
})

// Recommend modal state
const recommendModalOpen = ref(false)
const recommendUser = ref({})
const recommendUserTracks = ref([])
const recommendUserPosts = ref([])
const selectedRecommendTrackId = ref(null)
const recommendForm = ref({ title: '', description: '', fileUrl: '', fileName: '' })
const recommendUploading = ref(false)
const previewMode = ref(false)
const previewContent = ref('')
const previewLoading = ref(false)

// Next title modal state
const nextTitleModalOpen = ref(false)
const nextTitleUser = ref({})
const nextTitleForm = ref({ recommendDate: '', items: [] })
const nextTitleLoading = ref(false)
const nextTitleUserTrackIds = ref([])

// Import modal state
const importModalOpen = ref(false)
const importExcelFile = ref(null)
const importLoading = ref(false)

// Order creation modal state
const orderModalOpen = ref(false)
const orderForm = ref({ userId: '', type: 'renew', planId: '', amount: '', remark: '' })
const orderLoading = ref(false)

// Batch update admin modal state
const batchAdminModalOpen = ref(false)
const batchAdminId = ref(undefined)
const batchAdminLoading = ref(false)

function openOrderModal(userId, type) {
  orderForm.value = { userId, type, planId: '', amount: '', remark: '' }
  orderModalOpen.value = true
}

async function handleCreateOrder() {
  if (!orderForm.value.planId) {
    message.warning('请选择套餐')
    return
  }
  orderLoading.value = true
  try {
    const payload = {
      userId: orderForm.value.userId,
      planId: orderForm.value.planId,
      type: orderForm.value.type,
      remark: orderForm.value.remark || undefined,
    }
    if (orderForm.value.amount) {
      payload.amount = parseFloat(orderForm.value.amount)
    }
    await createOrder(payload)
    message.success(orderForm.value.type === 'renew' ? '续费订单创建成功' : '升级订单创建成功')
    orderModalOpen.value = false
  } catch (e) {
    message.error('创建失败')
  } finally {
    orderLoading.value = false
  }
}

const selectedRecommendTrack = computed(() => {
  return recommendUserTracks.value.find(t => t.id === selectedRecommendTrackId.value) || null
})

const userSelectedTemplate = computed(() => {
  const name = recommendUser.value?.template
  if (!name) return null
  return allTemplates.value.find(t => t.name === name) || null
})

const trackHasPost = computed(() => {
  if (!selectedRecommendTrackId.value) return false
  return recommendUserPosts.value.some(p => p.trackId === selectedRecommendTrackId.value)
})

const previewFileType = computed(() => {
  const name = recommendForm.value.fileName || ''
  if (name.endsWith('.pdf')) return 'pdf'
  if (name.endsWith('.txt') || name.endsWith('.md')) return 'text'
  if (name.endsWith('.doc') || name.endsWith('.docx')) return 'word'
  return 'other'
})

function needsRecommend(user) {
  const userTracks = user.trackIds || []
  return userTracks.length > 0
}

async function openRecommendModal(record) {
  recommendUser.value = record
  recommendModalOpen.value = true
  selectedRecommendTrackId.value = null
  recommendForm.value = { title: '', description: '', fileUrl: '', fileName: '' }
  recommendUserPosts.value = []

  try {
    const [tracks, templates] = await Promise.all([
      getUserTracks(record.id),
      listExportTemplates(),
    ])
    allTemplates.value = templates || []
    const trackIds = (tracks || []).map(t => t.trackId)
    recommendUserTracks.value = allTracks.value.filter(t => trackIds.includes(t.id))
    recommendUserPosts.value = []

    if (recommendUserTracks.value.length > 0) {
      selectedRecommendTrackId.value = recommendUserTracks.value[0].id
    }
  } catch (e) {
    message.error('加载用户赛道失败')
  }
}

function selectRecommendTrack(trackId) {
  selectedRecommendTrackId.value = trackId
  recommendForm.value = { title: '', description: '', fileUrl: '', fileName: '' }
  previewMode.value = false
  previewContent.value = ''
}

async function handleRecommendUpload({ file }) {
  recommendUploading.value = true
  try {
    const url = await uploadFile(file)
    recommendForm.value.fileUrl = url
    recommendForm.value.fileName = file.name
    if (!recommendForm.value.title) {
      const nameWithoutExt = file.name.replace(/\.[^/.]+$/, '')
      recommendForm.value.title = nameWithoutExt
    }
    message.success('上传成功')
  } catch (e) {
    message.error('上传失败')
  } finally {
    recommendUploading.value = false
  }
}

async function handleRecommendPreview() {
  if (!recommendForm.value.fileUrl) return
  const type = previewFileType.value
  if (type === 'pdf') {
    previewMode.value = true
    previewContent.value = ''
    return
  }
  if (type === 'text') {
    previewLoading.value = true
    previewMode.value = true
    try {
      const fileUrl = recommendForm.value.fileUrl
      const cacheBustUrl = fileUrl + (fileUrl.includes('?') ? '&' : '?') + '_t=' + Date.now()
      const res = await fetch(cacheBustUrl)
      const text = await res.text()
      previewContent.value = text
    } catch (e) {
      previewContent.value = '读取文件失败'
    } finally {
      previewLoading.value = false
    }
    return
  }
  previewMode.value = true
  previewContent.value = ''
}

function backToEdit() {
  previewMode.value = false
  previewContent.value = ''
}

async function saveRecommend() {
  if (!selectedRecommendTrackId.value) {
    message.warning('请选择赛道')
    return
  }
  if (!recommendForm.value.title) {
    message.warning('请输入文章标题')
    return
  }
  message.info('订阅文章功能已废弃，请通过标题匹配和生成文章流程操作')
  recommendModalOpen.value = false
}

async function loadOperators() {
  try {
    const list = await request.get('/admins')
    allOperators.value = (list || []).filter(a => a.role === '运营管理员')
  } catch (e) {
    // ignore
  }
}

async function loadData() {
  try {
    const s = searchMap.value[activeTab.value]
    // 构建 keyword：把 username/email/wxName 合并成一个 keyword 传给后端
    const keywords = []
    if (s.username) keywords.push(s.username)
    if (s.email) keywords.push(s.email)
    if (s.wxName) keywords.push(s.wxName)
    const keyword = keywords.length > 0 ? keywords.join(' ') : undefined

    const params = {}
    if (activeTab.value === 'disabled') {
      params.status = 0
    } else if (activeTab.value !== 'all') {
      params.status = 1
    }
    if (activeTab.value === 'accountOpened') {
      params.userType = 1
    }
    if (activeTab.value === 'distributor') {
      params.userType = 2
    }
    if (activeTab.value === 'trial') {
      params.userType = 3
    }
    if (keyword) params.keyword = keyword
    if (s.platform) params.platform = s.platform
    if (s.trackId) params.trackId = s.trackId
    // 运营管理员自动只查看自己的用户
    if (isOperatorAdmin.value && currentAdmin.value.id) {
      params.adminId = currentAdmin.value.id
    } else if (s.adminId) {
      params.adminId = s.adminId
    }

    // 1. 先加载核心数据（用户列表），拿到立刻渲染表格
    console.log('[UserManage] loadData params:', params)
    const uList = await listUsers(params) || []
    console.log('[UserManage] uList count:', uList.length, 'first:', uList[0]?.username)
    const userNameMap = {}
    uList.forEach(u => { userNameMap[u.id] = u.username })
    data.value = uList.map(u => ({
      ...u,
      email: u.email || '-',
      statusText: u.status === 1 ? '正常' : '已禁用',
      registerTime: u.createdAt ? u.createdAt.slice(0, 10) : '-',
      expireDate: u.expireDate || '-',
      lastLogin: u.lastLogin ? u.lastLogin.slice(0, 16).replace('T', ' ') : '-',
      trackLimitText: `${u.trackLimit || 0}`,
      platformLimitText: (u.platformLimit || '').split(/[,，]/).filter(Boolean).join('、') || '全部平台',
      membershipPlanName: '-',
      trackIds: u.trackIds || [],
      template: u.template || '-',
      invitedByName: u.invitedBy ? (userNameMap[u.invitedBy] || u.invitedBy) : '-',
      userTypeText: u.userType === 2 ? '分成' : u.userType === 3 ? '试用' : '开户',
    }))

    // 2. 后台异步加载辅助数据，不阻塞表格渲染
    Promise.all([
      listTracks(),
      listMembershipPlans(),
      listExportTemplates().catch(() => []),
    ]).then(([tList, pList, styleList]) => {
      allTracks.value = tList || []
      allPlans.value = pList || []
      allTemplates.value = styleList || []

      const planMap = {}
      allPlans.value.forEach(p => { planMap[p.id] = p.name })
      const trackMap = {}
      allTracks.value.forEach(t => { trackMap[t.id] = t })

      // 补齐会员套餐名称和赛道信息
      data.value = data.value.map(u => {
        const trackNames = (u.trackIds || []).map(tid => {
          const t = trackMap[tid]
          return t ? `${t.platforms || '-'} - ${t.name}` : tid
        })
        return {
          ...u,
          membershipPlanName: planMap[u.membershipPlanId] || '-',
          trackInfo: activeTab.value !== 'disabled' ? (trackNames.join('\n') || '-') : '-',
        }
      })
    }).catch(() => {
      // 辅助数据加载失败不影响主表格
    })
  } catch (e) {
    message.error('加载失败')
  }
}

function handleSearch() {
  currentPage.value = 1
  saveUserSearchState()
  loadData()
}

function handleReset() {
  searchMap.value[activeTab.value] = defaultSearch()
  currentPage.value = 1
  saveUserSearchState()
  loadData()
}

async function handleExport() {
  try {
    const s = searchMap.value[activeTab.value]
    const keywords = []
    if (s.username) keywords.push(s.username)
    if (s.email) keywords.push(s.email)
    if (s.wxName) keywords.push(s.wxName)
    const keyword = keywords.length > 0 ? keywords.join(' ') : undefined

    const params = { keyword }
    if (activeTab.value === 'disabled') {
      params.status = 0
    } else if (activeTab.value !== 'all') {
      params.status = 1
    }
    if (activeTab.value === 'accountOpened') {
      params.userType = 1
    }
    if (activeTab.value === 'distributor') {
      params.userType = 2
    }
    if (activeTab.value === 'trial') {
      params.userType = 3
    }
    if (s.platform) params.platform = s.platform
    if (s.trackId) params.trackId = s.trackId
    if (isOperatorAdmin.value && currentAdmin.value.id) {
      params.adminId = currentAdmin.value.id
    } else if (s.adminId) {
      params.adminId = s.adminId
    }
    if (selectedRowKeys.value.length > 0) {
      params.userIds = selectedRowKeys.value
    }
    const blob = await exportUsers(params)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '用户列表_' + new Date().toISOString().slice(0, 10) + '.xlsx'
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
    message.success('导出成功')
  } catch (e) {
    message.error('导出失败')
  }
}

function openImportModal() {
  importModalOpen.value = true
  importExcelFile.value = null
}

function handleImportFileChange(e) {
  importExcelFile.value = e.target.files?.[0] || null
}

async function handleImport() {
  if (!importExcelFile.value) {
    message.warning('请选择 Excel 文件')
    return
  }
  importLoading.value = true
  try {
    const result = await importUsers(importExcelFile.value)
    message.success(`导入完成：成功 ${result.success || 0} 条，跳过 ${result.skip || 0} 条`)
    importModalOpen.value = false
    loadData()
  } catch (e) {
    message.error(e?.message || '导入失败')
  } finally {
    importLoading.value = false
  }
}

function downloadTemplate() {
  const headers = ['用户名', '手机号', '邮箱', '微信号', '可选赛道数', '可访问平台', '到期时间', '会员套餐', '默认样式', '状态', '备注']
  const sample = ['示例用户', '13800138000', 'user@example.com', 'wxid_xxx', '3', '公众号,今日头条', '2026-12-31', '基础版', '公众号标准模板', '正常', '']
  const csvContent = [headers.join(','), sample.join(',')].join('\n')
  const blob = new Blob(['\uFEFF' + csvContent], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = '用户导入模板.csv'
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

function handlePlatformLimitChange(formRef, newVal) {
  const planId = formRef.membershipPlanId
  const limit = getPlanPlatformCount(planId)
  if (limit > 0 && newVal.length > limit) {
    message.warning(`该权益仅支持 ${limit} 个平台`)
    return
  }
  formRef.platformLimit = newVal
}

function getPlanPlatformCount(planId) {
  if (!planId) return 0
  const plan = allPlans.value.find(p => p.id === planId)
  if (!plan || !plan.permissionsJson) return 0
  try {
    const perms = JSON.parse(plan.permissionsJson)
    return perms.platformCount || 0
  } catch (e) {
    return 0
  }
}

function computeExpireDate(planId, fallback) {
  if (!planId) return fallback || '2026-12-31'
  const plan = allPlans.value.find(p => p.id === planId)
  if (!plan || !plan.expireDays || plan.expireDays <= 0) return fallback || '2026-12-31'
  const d = new Date()
  d.setDate(d.getDate() + plan.expireDays)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function handleAdd() {
  addModalOpen.value = true
  addForm.value = { username: '', contactType: '手机号', contact: '', password: 'Abc123456', trackLimit: 0, platformLimit: ['公众号'], template: '公众号标准模板', expireDate: '2026-12-31', remark: '', canSetEmail: 0, membershipPlanId: undefined, userType: 1, wxName: '', nickName: '' }
}

function handleEdit(record) {
  editModalOpen.value = true
  const rawPlatforms = record.platformLimit || ''
  editForm.value = { id: record.id, username: record.username || '', trackLimit: record.trackLimit || 0, platformLimit: rawPlatforms ? rawPlatforms.split(/[,，]/).map(s => s.trim()).filter(Boolean) : ['公众号'], expireDate: record.expireDate || '2026-12-31', status: record.status === 1 ? 1 : 0, remark: record.remark || '', canSetEmail: record.canSetEmail === 1 ? 1 : 0, membershipPlanId: record.membershipPlanId || undefined, template: record.template || '', inviteCode: record.inviteCode || '', invitedBy: record.invitedBy || '', userType: record.userType || 1, wxName: record.wxName || '', nickName: record.nickName || '', email: record.email || '', adminId: record.adminId || undefined }
}

async function saveAdd() {
  if (!addForm.value.username) {
    message.warning('请填写用户名')
    return
  }
  const platformCount = getPlanPlatformCount(addForm.value.membershipPlanId)
  if (platformCount > 0 && (addForm.value.platformLimit || []).length > platformCount) {
    message.warning(`该权益仅支持 ${platformCount} 个平台，当前已勾选 ${addForm.value.platformLimit.length} 个`)
    return
  }
  try {
    const payload = {
      username: addForm.value.username,
      password: addForm.value.password,
      status: 1,
      trackLimit: parseInt(addForm.value.trackLimit, 10) || 0,
      platformLimit: (addForm.value.platformLimit || []).join(','),
      expireDate: addForm.value.expireDate,
      remark: addForm.value.remark,
      canSetEmail: addForm.value.canSetEmail ? 1 : 0,
      membershipPlanId: addForm.value.membershipPlanId || undefined,
      template: addForm.value.template || '公众号标准模板',
      userType: addForm.value.userType || 1,
      wxName: addForm.value.wxName || undefined,
      nickName: addForm.value.nickName || undefined,
    }
    if (addForm.value.contactType === '手机号') payload.phone = addForm.value.contact
    else if (addForm.value.contactType === '邮箱') payload.email = addForm.value.contact
    else if (addForm.value.contactType === '微信号') payload.wxId = addForm.value.contact
    await request.post('/users', payload)
    message.success('开户成功')
    addModalOpen.value = false
    loadData()
  } catch (e) {
    message.error('开户失败')
  }
}

async function saveEdit() {
  if (!editForm.value.id) {
    message.error('用户ID缺失，请重新打开编辑')
    return
  }
  const platformCount = getPlanPlatformCount(editForm.value.membershipPlanId)
  if (platformCount > 0 && (editForm.value.platformLimit || []).length > platformCount) {
    message.warning(`该权益仅支持 ${platformCount} 个平台，当前已勾选 ${editForm.value.platformLimit.length} 个`)
    return
  }
  const payload = {
    username: editForm.value.username || undefined,
    trackLimit: parseInt(editForm.value.trackLimit, 10) || 0,
    platformLimit: (editForm.value.platformLimit || []).join(','),
    expireDate: editForm.value.expireDate,
    status: editForm.value.status,
    remark: editForm.value.remark,
    canSetEmail: editForm.value.canSetEmail ? 1 : 0,
    membershipPlanId: editForm.value.membershipPlanId || undefined,
    template: editForm.value.template || undefined,
    inviteCode: editForm.value.inviteCode || undefined,
    invitedBy: editForm.value.invitedBy || undefined,
    userType: editForm.value.userType || 1,
    wxName: editForm.value.wxName || undefined,
    nickName: editForm.value.nickName || undefined,
    email: editForm.value.email || undefined,
    adminId: editForm.value.adminId || undefined,
  }
  console.log('saveEdit payload:', payload, 'id:', editForm.value.id)
  try {
    await request.put('/users/' + editForm.value.id, payload)
    message.success('保存修改成功')
    editModalOpen.value = false
    loadData()
  } catch (e) {
    console.error('保存失败:', e)
    message.error('保存失败: ' + (e?.message || '未知错误'))
  }
}

async function toggleStatus(record) {
  const newStatus = record.status === 1 ? 0 : 1

  // 启用用户时，检查是否需要创建订单
  if (newStatus === 1 && record.membershipPlanId) {
    const plan = allPlans.value.find(p => p.id === record.membershipPlanId)
    if (plan && plan.price > 0) {
      const planName = plan.name
      const planPrice = plan.price
      Modal.confirm({
        title: '启用用户并创建订单',
        content: h('div', [
          h('p', { style: { marginBottom: '8px' } }, `该用户关联的会员套餐为「${planName}」，是否同时创建订单？`),
          h('p', { style: { margin: 0, color: '#52c41a', fontWeight: '500' } }, `订单金额：¥${planPrice.toFixed(2)}`),
        ]),
        async onOk() {
          try {
            await createOrder({
              userId: record.id,
              planId: record.membershipPlanId,
              type: 'open_account',
              remark: '管理员启用时创建',
            })
            message.success('订单创建成功')
          } catch (e) {
            // 订单创建失败不阻止启用用户
            message.warning('订单创建失败，但用户将正常启用')
          }
          try {
            await request.put('/users/' + record.id, { status: 1 })
            message.success('已启用')
            loadData()
          } catch (e) {
            message.error('启用失败')
          }
        },
      })
      return
    }
  }

  // 普通启用/禁用
  try {
    await request.put('/users/' + record.id, { status: newStatus })
    message.success(newStatus === 1 ? '已启用' : '已禁用')
    loadData()
  } catch (e) {
    message.error('操作失败')
  }
}

async function resetPassword(record) {
  try {
    await request.put('/users/' + record.id, { password: 'Abc123456' })
    message.success('密码已重置为 Abc123456')
  } catch (e) {
    message.error('重置失败')
  }
}

function copyAccountInfo(record) {
  const text = `【知我公众号创作助手】账号开通通知

登录地址：http://www.mmshuo.tech/login
用户名：${record.username || '-'}
密码：Abc123456
邮箱：${record.email || '-'}
套餐：${record.membershipPlanName || '-'}
到期时间：${record.expireDate || '-'}
可访问平台：${record.platformLimitText || '全部平台'}
可选赛道数：${record.trackLimitText || '-'}

首次登录后请尽快修改密码。`

  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(text).then(() => {
      message.success('开户信息已复制到剪贴板')
    }).catch(() => {
      fallbackCopy(text)
    })
  } else {
    fallbackCopy(text)
  }
}

function fallbackCopy(text) {
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.style.position = 'fixed'
  textarea.style.left = '-9999px'
  document.body.appendChild(textarea)
  textarea.select()
  try {
    document.execCommand('copy')
    message.success('开户信息已复制到剪贴板')
  } catch {
    message.error('复制失败，请手动复制')
  }
  document.body.removeChild(textarea)
}

async function viewCreations(record) {
  currentUser.value = record
  try {
    const list = await listCreations({ userId: record.id })
    creationRecords.value = (list || []).map(c => ({
      ...c,
      reviewedText: c.reviewed === 1 ? '已审阅' : '未审阅',
      createdAt: c.createdAt ? c.createdAt.slice(0, 16).replace('T', ' ') : '-',
    }))
    creationModalOpen.value = true
  } catch (e) {
    message.error('加载创作记录失败')
  }
}

// Track info modal state
const trackInfoModalOpen = ref(false)
const trackInfoUser = ref({})
const trackInfoList = ref([])
const trackInfoLoading = ref(false)
const selectedAddTrackId = ref(null)
const addTrackLoading = ref(false)

// Style config modal state
const styleModalOpen = ref(false)
const styleUser = ref({})
const styleForm = ref('')
const styleThemeColor = ref('#fa541c')
const styleTitleFontSize = ref(16)
const styleContentFontSize = ref(12)
const styleLoading = ref(false)

function openStyleModal(record) {
  styleUser.value = record
  styleForm.value = record.styleConfig || ''
  styleThemeColor.value = record.themeColor || '#fa541c'
  styleTitleFontSize.value = record.titleFontSize || 16
  styleContentFontSize.value = record.contentFontSize || 12
  styleModalOpen.value = true
}

async function saveStyle() {
  if (!styleUser.value.id) return
  styleLoading.value = true
  try {
    const payload = {
      styleConfig: styleForm.value,
      themeColor: styleThemeColor.value,
      titleFontSize: styleTitleFontSize.value,
      contentFontSize: styleContentFontSize.value
    }
    await request.put('/users/' + styleUser.value.id, payload)
    message.success('文章样式已保存')
    styleModalOpen.value = false
    // 更新本地数据
    const idx = data.value.findIndex(u => u.id === styleUser.value.id)
    if (idx !== -1) {
      data.value[idx].styleConfig = payload.styleConfig
      data.value[idx].themeColor = payload.themeColor
      data.value[idx].titleFontSize = payload.titleFontSize
      data.value[idx].contentFontSize = payload.contentFontSize
    }
  } catch (e) {
    message.error('保存失败')
  } finally {
    styleLoading.value = false
  }
}

const availableTracksForAdd = computed(() => {
  const subscribedIds = new Set((trackInfoList.value || []).map(t => t.id))
  return (allTracks.value || []).filter(t => !subscribedIds.has(t.id))
})

async function openNextTitleModal(record) {
  nextTitleUser.value = record
  const tomorrow = new Date()
  tomorrow.setDate(tomorrow.getDate() + 1)
  const tomorrowStr = tomorrow.toISOString().slice(0, 10)
  let trackIds = []
  try {
    const userTracks = await getUserTracks(record.id)
    trackIds = (userTracks || []).map(ut => ut.trackId)
  } catch (e) {
    // ignore
  }
  // 确保赛道列表已加载
  if (!allTracks.value || allTracks.value.length === 0) {
    try {
      const tList = await listTracks()
      allTracks.value = tList || []
    } catch (e) {
      // ignore
    }
  }
  nextTitleUserTrackIds.value = trackIds
  const items = trackIds.map(tid => ({ trackId: tid, title: '' }))

  // 查询该日期已有标题并回填
  try {
    const existing = await request.get('/users/' + record.id + '/next-title', { params: { date: tomorrowStr } })
    if (existing && Array.isArray(existing)) {
      for (const item of existing) {
        const trackId = item.track_id || item.trackId
        const title = item.titleLibraryTitle || item.title_library_title || item.title || ''
        const found = items.find(i => i.trackId === trackId)
        if (found) {
          found.title = title
        }
      }
    }
  } catch (e) {
    // 查询失败不影响打开弹窗
  }

  nextTitleForm.value = { recommendDate: tomorrowStr, items }
  nextTitleModalOpen.value = true
}

async function loadExistingTitlesByDate(date) {
  if (!date || !nextTitleUser.value.id) return
  // 先清空所有标题
  for (const item of nextTitleForm.value.items) {
    item.title = ''
  }
  try {
    const existing = await request.get('/users/' + nextTitleUser.value.id + '/next-title', { params: { date } })
    if (existing && Array.isArray(existing)) {
      for (const item of existing) {
        const trackId = item.track_id || item.trackId
        const title = item.titleLibraryTitle || item.title_library_title || item.title || ''
        const found = nextTitleForm.value.items.find(i => i.trackId === trackId)
        if (found) {
          found.title = title
        }
      }
    }
  } catch (e) {
    // 查询失败不影响操作
  }
}

async function saveNextTitle() {
  if (!nextTitleForm.value.recommendDate) {
    message.warning('请选择推荐日期')
    return
  }
  if (nextTitleUserTrackIds.value.length === 0) {
    message.warning('该用户未订阅任何赛道，无法设定标题')
    return
  }
  const validItems = nextTitleForm.value.items.filter(item => item.title && item.title.trim())
  if (validItems.length === 0) {
    message.warning('请至少输入一个标题')
    return
  }
  nextTitleLoading.value = true
  try {
    await request.post('/users/' + nextTitleUser.value.id + '/next-title', {
      recommendDate: nextTitleForm.value.recommendDate,
      items: validItems.map(item => ({ trackId: item.trackId, title: item.title.trim() })),
    })
    message.success('设定成功')
    nextTitleModalOpen.value = false
  } catch (e) {
    message.error('设定失败：' + (e?.message || '未知错误'))
  } finally {
    nextTitleLoading.value = false
  }
}

function openBatchAdminModal() {
  if (selectedRowKeys.value.length === 0) {
    message.warning('请先勾选要修改的用户')
    return
  }
  batchAdminId.value = undefined
  batchAdminModalOpen.value = true
}

async function handleBatchUpdateAdmin() {
  if (selectedRowKeys.value.length === 0) {
    message.warning('请选择用户')
    return
  }
  batchAdminLoading.value = true
  try {
    await batchUpdateAdmin(selectedRowKeys.value, batchAdminId.value || null)
    message.success('批量修改成功')
    batchAdminModalOpen.value = false
    selectedRowKeys.value = []
    loadData()
  } catch (e) {
    message.error(e?.message || '批量修改失败')
  } finally {
    batchAdminLoading.value = false
  }
}

const batchStyleModalOpen = ref(false)
const batchStyleForm = ref('')
const batchThemeColor = ref('#fa541c')
const batchTitleFontSize = ref(16)
const batchContentFontSize = ref(12)
const batchStyleLoading = ref(false)

function openBatchStyleModal() {
  if (selectedRowKeys.value.length === 0) {
    message.warning('请先勾选要配置的用户')
    return
  }
  batchStyleForm.value = ''
  batchThemeColor.value = '#fa541c'
  batchTitleFontSize.value = 16
  batchContentFontSize.value = 12
  batchStyleModalOpen.value = true
}

async function handleBatchSaveStyle() {
  if (selectedRowKeys.value.length === 0) {
    message.warning('请选择用户')
    return
  }
  batchStyleLoading.value = true
  try {
    await request.post('/users/batch-style', {
      userIds: selectedRowKeys.value,
      styleConfig: batchStyleForm.value,
      themeColor: batchThemeColor.value,
      titleFontSize: batchTitleFontSize.value,
      contentFontSize: batchContentFontSize.value,
    })
    message.success('批量配置样式成功')
    batchStyleModalOpen.value = false
    selectedRowKeys.value = []
    loadData()
  } catch (e) {
    message.error(e?.message || '批量配置失败')
  } finally {
    batchStyleLoading.value = false
  }
}

async function openTrackInfoModal(record) {
  trackInfoUser.value = record
  trackInfoModalOpen.value = true
  trackInfoLoading.value = true
  try {
    const userTracks = await getUserTracks(record.id)
    const trackIds = (userTracks || []).map(ut => ut.trackId)
    trackInfoList.value = allTracks.value.filter(t => trackIds.includes(t.id)).map(t => ({
      ...t,
      platformDisplay: (t.platforms || '').split(/[·、,，\s]+/).filter(Boolean).join('、') || '-',
    }))
  } catch (e) {
    message.error('加载用户赛道失败')
    trackInfoList.value = []
  } finally {
    trackInfoLoading.value = false
  }
}

function handleCancelUserTrack(track) {
  Modal.confirm({
    title: '确认取消订阅该赛道？',
    content: `将取消用户「${trackInfoUser.value.username || ''}」对「${track.name}」赛道的订阅。`,
    async onOk() {
      try {
        await removeUserTrack(trackInfoUser.value.id, track.id)
        message.success('已取消订阅')
        trackInfoList.value = trackInfoList.value.filter(t => t.id !== track.id)
        // Also refresh main list to update trackIds
        loadData()
      } catch (e) {
        message.error('取消订阅失败')
      }
    },
  })
}

async function handleAddUserTrack() {
  if (!selectedAddTrackId.value) {
    message.warning('请选择要添加的赛道')
    return
  }
  const track = allTracks.value.find(t => t.id === selectedAddTrackId.value)
  if (!track) return
  addTrackLoading.value = true
  try {
    await addUserTrack(trackInfoUser.value.id, selectedAddTrackId.value)
    message.success(`已为用户添加「${track.name}」赛道订阅`)
    trackInfoList.value.push({
      ...track,
      platformDisplay: (track.platforms || '').split(/[·、,，\s]+/).filter(Boolean).join('、') || '-',
    })
    selectedAddTrackId.value = null
    loadData()
  } catch (e) {
    message.error(e?.response?.data?.msg || e?.message || '添加订阅失败')
  } finally {
    addTrackLoading.value = false
  }
}

watch(activeTab, () => {
  currentPage.value = 1
  loadData()
})

onMounted(() => {
  if (route.query.keyword) {
    searchMap.value['all'].username = route.query.keyword
    activeTab.value = 'all'
    // 清理 query，避免刷新页面重复触发
    const newQuery = { ...route.query }
    delete newQuery.keyword
    window.history.replaceState({}, '', `${window.location.pathname}?${new URLSearchParams(newQuery).toString()}`)
  }
  loadOperators()
  loadData()
})
</script>

<template>
  <Card :body-style="{ padding: '24px' }" style="border-radius: 2px;">
    <Tabs v-model:activeKey="activeTab" @change="handleSearch">
      <Tabs.TabPane key="accountOpened" :tab="'开户用户'">
        <div style="display: flex; gap: 12px; margin-bottom: 16px; align-items: center; flex-wrap: wrap;">
          <Input v-model:value="searchMap[activeTab].username" placeholder="用户名" style="width: 160px;" />
          <Input v-model:value="searchMap[activeTab].email" placeholder="邮箱" style="width: 160px;" />
          <Input v-model:value="searchMap[activeTab].wxName" placeholder="公众号名称" style="width: 160px;" />
          <Select show-search v-model:value="searchMap[activeTab].platform" placeholder="平台" style="width: 130px;" allowClear>
            <Select.Option value="公众号">公众号</Select.Option>
            <Select.Option value="今日头条">今日头条</Select.Option>
            <Select.Option value="百家号">百家号</Select.Option>
          </Select>
          <Select show-search v-model:value="searchMap[activeTab].trackId" placeholder="赛道" style="width: 160px;" allowClear>
            <Select.Option v-for="t in allTracks" :key="t.id" :value="t.id" :label="t.name">{{ t.name }}</Select.Option>
          </Select>
          <Select show-search v-if="isSuperAdmin" v-model:value="searchMap[activeTab].adminId" placeholder="归属运营者" style="width: 160px;" allowClear>
            <Select.Option v-for="op in allOperators" :key="op.id" :value="op.id" :label="op.name || op.username">{{ op.name || op.username }}</Select.Option>
          </Select>
          <Button type="primary" @click="handleSearch">查询</Button>
          <Button @click="handleReset">重置</Button>
          <Button style="margin-left: auto;" @click="handleExport">导出 Excel</Button>
          <Button style="margin-left: 12px;" @click="openBatchAdminModal">批量修改运营者</Button>
          <Button style="margin-left: 12px;" @click="openBatchStyleModal">批量配置样式</Button>
          <Button style="margin-left: 12px;" @click="openImportModal">批量导入</Button>
          <Button type="primary" style="margin-left: 12px;" @click="handleAdd">+ 新增用户</Button>
        </div>
        <Table :columns="tableColumns" :data-source="paginatedData" :pagination="false" row-key="id" :row-selection="rowSelection" :scroll="{ x: 'max-content' }">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <Tag :color="record.status === 1 ? 'green' : 'red'">{{ record.statusText }}</Tag>
            </template>
            <template v-if="column.key === 'adminId'">
              <span style="font-size: 12px; color: #666;">{{ record.adminId ? (allOperators.find(op => op.id === record.adminId)?.name || record.adminId) : '-' }}</span>
            </template>
            <template v-if="column.key === 'trackInfo'">
              <pre style="font-size: 12px; color: #666; margin: 0; font-family: inherit; white-space: pre-wrap; word-break: break-word;">{{ record.trackInfo || '-' }}</pre>
            </template>
          </template>
        </Table>
      </Tabs.TabPane>

      <Tabs.TabPane key="distributor" :tab="'分成用户'">
        <div style="display: flex; gap: 12px; margin-bottom: 16px; align-items: center; flex-wrap: wrap;">
          <Input v-model:value="searchMap[activeTab].username" placeholder="用户名" style="width: 160px;" />
          <Input v-model:value="searchMap[activeTab].email" placeholder="邮箱" style="width: 160px;" />
          <Input v-model:value="searchMap[activeTab].wxName" placeholder="公众号名称" style="width: 160px;" />
          <Select show-search v-model:value="searchMap[activeTab].platform" placeholder="平台" style="width: 130px;" allowClear>
            <Select.Option value="公众号">公众号</Select.Option>
            <Select.Option value="今日头条">今日头条</Select.Option>
            <Select.Option value="百家号">百家号</Select.Option>
          </Select>
          <Select show-search v-model:value="searchMap[activeTab].trackId" placeholder="赛道" style="width: 160px;" allowClear>
            <Select.Option v-for="t in allTracks" :key="t.id" :value="t.id" :label="t.name">{{ t.name }}</Select.Option>
          </Select>
          <Select show-search v-if="isSuperAdmin" v-model:value="searchMap[activeTab].adminId" placeholder="归属运营者" style="width: 160px;" allowClear>
            <Select.Option v-for="op in allOperators" :key="op.id" :value="op.id" :label="op.name || op.username">{{ op.name || op.username }}</Select.Option>
          </Select>
          <Button type="primary" @click="handleSearch">查询</Button>
          <Button @click="handleReset">重置</Button>
          <Button style="margin-left: auto;" @click="handleExport">导出 Excel</Button>
          <Button style="margin-left: 12px;" @click="openBatchAdminModal">批量修改运营者</Button>
          <Button style="margin-left: 12px;" @click="openBatchStyleModal">批量配置样式</Button>
          <Button style="margin-left: 12px;" @click="openImportModal">批量导入</Button>
          <Button type="primary" style="margin-left: 12px;" @click="handleAdd">+ 新增用户</Button>
        </div>
        <Table :columns="tableColumns" :data-source="paginatedData" :pagination="false" row-key="id" :row-selection="rowSelection" :scroll="{ x: 'max-content' }">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <Tag :color="record.status === 1 ? 'green' : 'red'">{{ record.statusText }}</Tag>
            </template>
            <template v-if="column.key === 'adminId'">
              <span style="font-size: 12px; color: #666;">{{ record.adminId ? (allOperators.find(op => op.id === record.adminId)?.name || record.adminId) : '-' }}</span>
            </template>
            <template v-if="column.key === 'trackInfo'">
              <pre style="font-size: 12px; color: #666; margin: 0; font-family: inherit; white-space: pre-wrap; word-break: break-word;">{{ record.trackInfo || '-' }}</pre>
            </template>
          </template>
        </Table>
      </Tabs.TabPane>

      <Tabs.TabPane key="trial" :tab="'试用用户'">
        <div style="display: flex; gap: 12px; margin-bottom: 16px; align-items: center; flex-wrap: wrap;">
          <Input v-model:value="searchMap[activeTab].username" placeholder="用户名" style="width: 160px;" />
          <Input v-model:value="searchMap[activeTab].email" placeholder="邮箱" style="width: 160px;" />
          <Input v-model:value="searchMap[activeTab].wxName" placeholder="公众号名称" style="width: 160px;" />
          <Select show-search v-model:value="searchMap[activeTab].platform" placeholder="平台" style="width: 130px;" allowClear>
            <Select.Option value="公众号">公众号</Select.Option>
            <Select.Option value="今日头条">今日头条</Select.Option>
            <Select.Option value="百家号">百家号</Select.Option>
          </Select>
          <Select show-search v-model:value="searchMap[activeTab].trackId" placeholder="赛道" style="width: 160px;" allowClear>
            <Select.Option v-for="t in allTracks" :key="t.id" :value="t.id" :label="t.name">{{ t.name }}</Select.Option>
          </Select>
          <Select show-search v-if="isSuperAdmin" v-model:value="searchMap[activeTab].adminId" placeholder="归属运营者" style="width: 160px;" allowClear>
            <Select.Option v-for="op in allOperators" :key="op.id" :value="op.id" :label="op.name || op.username">{{ op.name || op.username }}</Select.Option>
          </Select>
          <Button type="primary" @click="handleSearch">查询</Button>
          <Button @click="handleReset">重置</Button>
          <Button style="margin-left: auto;" @click="handleExport">导出 Excel</Button>
          <Button style="margin-left: 12px;" @click="openBatchAdminModal">批量修改运营者</Button>
          <Button style="margin-left: 12px;" @click="openBatchStyleModal">批量配置样式</Button>
          <Button style="margin-left: 12px;" @click="openImportModal">批量导入</Button>
          <Button type="primary" style="margin-left: 12px;" @click="handleAdd">+ 新增用户</Button>
        </div>
        <Table :columns="tableColumns" :data-source="paginatedData" :pagination="false" row-key="id" :row-selection="rowSelection" :scroll="{ x: 'max-content' }">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <Tag :color="record.status === 1 ? 'green' : 'red'">{{ record.statusText }}</Tag>
            </template>
            <template v-if="column.key === 'adminId'">
              <span style="font-size: 12px; color: #666;">{{ record.adminId ? (allOperators.find(op => op.id === record.adminId)?.name || record.adminId) : '-' }}</span>
            </template>
            <template v-if="column.key === 'trackInfo'">
              <pre style="font-size: 12px; color: #666; margin: 0; font-family: inherit; white-space: pre-wrap; word-break: break-word;">{{ record.trackInfo || '-' }}</pre>
            </template>
          </template>
        </Table>
      </Tabs.TabPane>

      <Tabs.TabPane key="disabled" :tab="'禁用用户'">
        <div style="display: flex; gap: 12px; margin-bottom: 16px; align-items: center; flex-wrap: wrap;">
          <Input v-model:value="searchMap[activeTab].username" placeholder="用户名" style="width: 160px;" />
          <Input v-model:value="searchMap[activeTab].email" placeholder="邮箱" style="width: 160px;" />
          <Input v-model:value="searchMap[activeTab].wxName" placeholder="公众号名称" style="width: 160px;" />
          <Select show-search v-model:value="searchMap[activeTab].platform" placeholder="平台" style="width: 130px;" allowClear>
            <Select.Option value="公众号">公众号</Select.Option>
            <Select.Option value="今日头条">今日头条</Select.Option>
            <Select.Option value="百家号">百家号</Select.Option>
          </Select>
          <Select show-search v-model:value="searchMap[activeTab].trackId" placeholder="赛道" style="width: 160px;" allowClear>
            <Select.Option v-for="t in allTracks" :key="t.id" :value="t.id" :label="t.name">{{ t.name }}</Select.Option>
          </Select>
          <Select show-search v-if="isSuperAdmin" v-model:value="searchMap[activeTab].adminId" placeholder="归属运营者" style="width: 160px;" allowClear>
            <Select.Option v-for="op in allOperators" :key="op.id" :value="op.id" :label="op.name || op.username">{{ op.name || op.username }}</Select.Option>
          </Select>
          <Button type="primary" @click="handleSearch">查询</Button>
          <Button @click="handleReset">重置</Button>
          <Button style="margin-left: auto;" @click="handleExport">导出 Excel</Button>
          <Button style="margin-left: 12px;" @click="openBatchAdminModal">批量修改运营者</Button>
          <Button style="margin-left: 12px;" @click="openBatchStyleModal">批量配置样式</Button>
          <Button style="margin-left: 12px;" @click="openImportModal">批量导入</Button>
          <Button type="primary" style="margin-left: 12px;" @click="handleAdd">+ 新增用户</Button>
        </div>
        <Table :columns="tableColumns" :data-source="paginatedData" :pagination="false" row-key="id" :row-selection="rowSelection" :scroll="{ x: 'max-content' }">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <Tag :color="record.status === 1 ? 'green' : 'red'">{{ record.statusText }}</Tag>
            </template>
            <template v-if="column.key === 'adminId'">
              <span style="font-size: 12px; color: #666;">{{ record.adminId ? (allOperators.find(op => op.id === record.adminId)?.name || record.adminId) : '-' }}</span>
            </template>
            <template v-if="column.key === 'trackInfo'">
              <span style="font-size: 12px; color: #666;">{{ record.trackInfo || '-' }}</span>
            </template>
          </template>
        </Table>
      </Tabs.TabPane>

      <Tabs.TabPane key="all" :tab="'全部用户'">
        <div style="display: flex; gap: 12px; margin-bottom: 16px; align-items: center; flex-wrap: wrap;">
          <Input v-model:value="searchMap[activeTab].username" placeholder="用户名" style="width: 160px;" />
          <Input v-model:value="searchMap[activeTab].email" placeholder="邮箱" style="width: 160px;" />
          <Input v-model:value="searchMap[activeTab].wxName" placeholder="公众号名称" style="width: 160px;" />
          <Select show-search v-model:value="searchMap[activeTab].platform" placeholder="平台" style="width: 130px;" allowClear>
            <Select.Option value="公众号">公众号</Select.Option>
            <Select.Option value="今日头条">今日头条</Select.Option>
            <Select.Option value="百家号">百家号</Select.Option>
          </Select>
          <Select show-search v-model:value="searchMap[activeTab].trackId" placeholder="赛道" style="width: 160px;" allowClear>
            <Select.Option v-for="t in allTracks" :key="t.id" :value="t.id" :label="t.name">{{ t.name }}</Select.Option>
          </Select>
          <Select show-search v-if="isSuperAdmin" v-model:value="searchMap[activeTab].adminId" placeholder="归属运营者" style="width: 160px;" allowClear>
            <Select.Option v-for="op in allOperators" :key="op.id" :value="op.id" :label="op.name || op.username">{{ op.name || op.username }}</Select.Option>
          </Select>
          <Button type="primary" @click="handleSearch">查询</Button>
          <Button @click="handleReset">重置</Button>
          <Button style="margin-left: auto;" @click="handleExport">导出 Excel</Button>
          <Button style="margin-left: 12px;" @click="openBatchAdminModal">批量修改运营者</Button>
          <Button style="margin-left: 12px;" @click="openBatchStyleModal">批量配置样式</Button>
          <Button style="margin-left: 12px;" @click="openImportModal">批量导入</Button>
          <Button type="primary" style="margin-left: 12px;" @click="handleAdd">+ 新增用户</Button>
        </div>
        <Table :columns="tableColumns" :data-source="paginatedData" :pagination="false" row-key="id" :row-selection="rowSelection" :scroll="{ x: 'max-content' }">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <Tag :color="record.status === 1 ? 'green' : 'red'">{{ record.statusText }}</Tag>
            </template>
            <template v-if="column.key === 'adminId'">
              <span style="font-size: 12px; color: #666;">{{ record.adminId ? (allOperators.find(op => op.id === record.adminId)?.name || record.adminId) : '-' }}</span>
            </template>
            <template v-if="column.key === 'trackInfo'">
              <pre style="font-size: 12px; color: #666; margin: 0; font-family: inherit; white-space: pre-wrap; word-break: break-word;">{{ record.trackInfo || '-' }}</pre>
            </template>
          </template>
        </Table>
      </Tabs.TabPane>
    </Tabs>

    <div style="display: flex; justify-content: flex-end; margin-top: 16px;">
      <Pagination v-model:current="currentPage" v-model:page-size="pageSize" :total="filteredData.length" show-size-changer :page-size-options="['10', '20', '50', '100']" :show-total="total => `共 ${total} 条`" />
    </div>
  </Card>

  <Modal v-model:open="addModalOpen" title="新增用户（管理员开户）" :mask-closable="false" :width="700" @ok="saveAdd">
    <Form layout="vertical" style="margin-top: 12px;">
      <Form.Item label="用户名" required>
        <Input v-model:value="addForm.username" placeholder="请输入用户名" />
      </Form.Item>
      <Form.Item label="联系方式">
        <div style="display: grid; grid-template-columns: 120px 1fr; gap: 12px; align-items: flex-end;">
          <Select show-search v-model:value="addForm.contactType">
            <Select.Option value="手机号">手机号</Select.Option>
            <Select.Option value="微信号">微信号</Select.Option>
            <Select.Option value="邮箱">邮箱</Select.Option>
          </Select>
          <Input v-model:value="addForm.contact" placeholder="请输入联系方式" />
        </div>
      </Form.Item>
      <Form.Item label="公众号名称">
        <Input v-model:value="addForm.wxName" placeholder="请输入公众号名称" />
      </Form.Item>
      <Form.Item label="微信名称">
        <Input v-model:value="addForm.nickName" placeholder="请输入微信名称" />
      </Form.Item>
      <Form.Item label="初始密码" required>
        <Input v-model:value="addForm.password" readonly />
        <div style="font-size: 12px; color: #999; margin-top: 4px;">系统将自动生成初始密码，用户首次登录后建议修改</div>
      </Form.Item>
      <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px;">
        <Form.Item label="可选赛道数" required>
          <Input type="number" v-model:value="addForm.trackLimit" min="0" />
        </Form.Item>
        <Form.Item label="授权到期时间" required>
          <Input type="date" v-model:value="addForm.expireDate" />
        </Form.Item>
      </div>
      <Form.Item label="会员套餐">
        <Select show-search v-model:value="addForm.membershipPlanId" placeholder="请选择会员套餐" allow-clear style="width: 240px;" @change="(val) => { addForm.expireDate = computeExpireDate(val) }">
          <Select.Option v-for="p in allPlans" :key="p.id" :value="p.id" :label="p.name">{{ p.name }}</Select.Option>
        </Select>
      </Form.Item>
      <div style="font-size: 12px; color: #999; margin-top: -8px; margin-bottom: 16px;">可选赛道数 0 表示不限制。选择套餐后会自动计算到期时间</div>
      <Form.Item label="可访问平台" required>
        <Checkbox.Group :value="addForm.platformLimit" :options="platformOptions" @change="(val) => handlePlatformLimitChange(addForm, val)" />
      </Form.Item>
      <Form.Item label="功能权限">
        <Checkbox v-model:checked="addForm.canSetEmail" :true-value="1" :false-value="0">允许设置邮箱接收文章</Checkbox>
      </Form.Item>
      <Form.Item label="用户类型">
        <Select show-search v-model:value="addForm.userType" style="width: 200px;">
          <Select.Option v-for="opt in userTypeOptions" :key="opt.value" :value="opt.value" :label="opt.label">{{ opt.label }}</Select.Option>
        </Select>
      </Form.Item>
      <Form.Item label="导出模板">
        <div style="display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 12px; margin-top: 8px;">
          <div
            v-for="t in allTemplates"
            :key="t.id"
            @click="addForm.template = t.name"
            :style="{
              border: addForm.template === t.name ? '2px solid ' + (t.configObj?.previewColor || '#1890ff') : '1px solid #e8e8e8',
              borderRadius: '8px',
              padding: '12px',
              cursor: 'pointer',
              background: addForm.template === t.name ? '#f6ffed' : '#fff',
              transition: 'all 0.2s'
            }"
          >
            <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px;">
              <div :style="{ width: '12px', height: '12px', borderRadius: '50%', background: t.configObj?.previewColor || '#999' }"></div>
              <span style="font-weight: 600; font-size: 14px;">{{ t.name }}</span>
            </div>
            <div style="font-size: 12px; color: #888; line-height: 1.5;">{{ t.configObj?.description || '-' }}</div>
            <div v-if="t.isDefault === 1" style="margin-top: 8px;">
              <Tag size="small" color="green">默认</Tag>
            </div>
          </div>
        </div>
        <div style="font-size: 12px; color: #999; margin-top: 8px;">点击卡片为用户选择导出 Word 时使用的模板样式</div>
      </Form.Item>
      <Form.Item label="备注">
        <Input.TextArea v-model:value="addForm.remark" placeholder="可选填，如：客户来源、特殊说明等" :rows="3" />
      </Form.Item>
    </Form>
  </Modal>

  <Modal v-model:open="editModalOpen" title="编辑用户" :mask-closable="false" :width="700" @ok="saveEdit">
    <Form layout="vertical" style="margin-top: 12px;">
      <Form.Item label="用户名">
        <Input v-model:value="editForm.username" placeholder="请输入用户名" />
      </Form.Item>
      <Form.Item label="邮箱">
        <Input v-model:value="editForm.email" placeholder="请输入邮箱" />
      </Form.Item>
      <Form.Item label="公众号名称">
        <Input v-model:value="editForm.wxName" placeholder="请输入公众号名称" />
      </Form.Item>
      <Form.Item label="微信名称">
        <Input v-model:value="editForm.nickName" placeholder="请输入微信名称" />
      </Form.Item>
      <Form.Item v-if="isSuperAdmin" label="归属运营者">
        <Select show-search v-model:value="editForm.adminId" placeholder="请选择归属运营者" allow-clear style="width: 100%;">
          <Select.Option v-for="op in allOperators" :key="op.id" :value="op.id" :label="op.name || op.username">{{ op.name || op.username }}</Select.Option>
        </Select>
      </Form.Item>
      <div v-if="editForm.userType === 1" style="margin-bottom: 16px; padding: 12px; background: #f6ffed; border: 1px solid #b7eb8f; border-radius: 6px;">
        <div style="font-size: 13px; color: #52c41a; font-weight: 500; margin-bottom: 8px;">订单操作</div>
        <div style="display: flex; gap: 12px;">
          <Button size="small" @click="openOrderModal(editForm.id, 'renew')">创建续费订单</Button>
          <Button size="small" type="primary" @click="openOrderModal(editForm.id, 'upgrade')">创建升级订单</Button>
        </div>
      </div>
      <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px;">
        <Form.Item label="邀请码">
          <Input v-model:value="editForm.inviteCode" placeholder="请输入邀请码" />
        </Form.Item>
        <Form.Item label="邀请人">
          <Select show-search v-model:value="editForm.invitedBy" placeholder="请选择邀请人" allow-clear style="width: 100%;">
            <Select.Option v-for="u in data" :key="u.id" :value="u.id" :label="u.username">{{ u.username }}</Select.Option>
          </Select>
        </Form.Item>
      </div>
      <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px;">
        <Form.Item label="可选赛道数" required>
          <Input type="number" v-model:value="editForm.trackLimit" min="0" />
        </Form.Item>
        <Form.Item label="授权到期时间" required>
          <Input type="date" v-model:value="editForm.expireDate" />
        </Form.Item>
      </div>
      <Form.Item label="会员套餐">
        <Select show-search v-model:value="editForm.membershipPlanId" placeholder="请选择会员套餐" allow-clear style="width: 240px;" @change="(val) => { editForm.expireDate = computeExpireDate(val, editForm.expireDate) }">
          <Select.Option v-for="p in allPlans" :key="p.id" :value="p.id" :label="p.name">{{ p.name }}</Select.Option>
        </Select>
      </Form.Item>
      <div style="font-size: 12px; color: #999; margin-top: -8px; margin-bottom: 16px;">0 表示不限制赛道数。选择套餐后会自动计算到期时间</div>
      <Form.Item label="可访问平台" required>
        <Checkbox.Group :value="editForm.platformLimit" :options="platformOptions" @change="(val) => handlePlatformLimitChange(editForm, val)" />
      </Form.Item>
      <Form.Item label="导出模板">
        <div style="display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 12px; margin-top: 8px;">
          <div
            v-for="t in allTemplates"
            :key="t.id"
            @click="editForm.template = t.name"
            :style="{
              border: editForm.template === t.name ? '2px solid ' + (t.configObj?.previewColor || '#1890ff') : '1px solid #e8e8e8',
              borderRadius: '8px',
              padding: '12px',
              cursor: 'pointer',
              background: editForm.template === t.name ? '#f6ffed' : '#fff',
              transition: 'all 0.2s'
            }"
          >
            <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px;">
              <div :style="{ width: '12px', height: '12px', borderRadius: '50%', background: t.configObj?.previewColor || '#999' }"></div>
              <span style="font-weight: 600; font-size: 14px;">{{ t.name }}</span>
            </div>
            <div style="font-size: 12px; color: #888; line-height: 1.5;">{{ t.configObj?.description || '-' }}</div>
            <div v-if="t.isDefault === 1" style="margin-top: 8px;">
              <Tag size="small" color="green">默认</Tag>
            </div>
          </div>
        </div>
        <div style="font-size: 12px; color: #999; margin-top: 8px;">点击卡片为用户选择导出 Word 时使用的模板样式</div>
      </Form.Item>
      <Form.Item label="功能权限">
        <Checkbox v-model:checked="editForm.canSetEmail" :true-value="1" :false-value="0">允许设置邮箱接收文章</Checkbox>
      </Form.Item>
      <Form.Item label="账号状态">
        <Select show-search v-model:value="editForm.status">
          <Select.Option :value="1">正常</Select.Option>
          <Select.Option :value="0">已禁用</Select.Option>
        </Select>
      </Form.Item>
      <Form.Item label="用户类型">
        <Select show-search v-model:value="editForm.userType" style="width: 200px;">
          <Select.Option v-for="opt in userTypeOptions" :key="opt.value" :value="opt.value" :label="opt.label">{{ opt.label }}</Select.Option>
        </Select>
      </Form.Item>
      <Form.Item label="备注">
        <Input.TextArea v-model:value="editForm.remark" placeholder="可选填" :rows="3" />
      </Form.Item>
    </Form>
  </Modal>

  <Modal v-model:open="creationModalOpen" :title="`${currentUser.username || ''} 的创作记录`" :footer="null" :width="1000">
    <Table :columns="creationColumns" :data-source="creationRecords" :pagination="false" row-key="id" size="small" style="margin-top: 12px;">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'reviewed'">
          <Tag :color="record.reviewed === 1 ? 'green' : 'orange'">{{ record.reviewedText }}</Tag>
        </template>
      </template>
    </Table>
    <div v-if="!creationRecords.length" style="text-align: center; color: #999; padding: 24px;">暂无创作记录</div>
  </Modal>

  <!-- Recommend Modal -->
  <Modal
    v-model:open="recommendModalOpen"
    :title="`为 ${recommendUser.username || ''} 推荐文章`"
    :mask-closable="false"
    :width="900"
    :footer="null"
  >
    <div style="display: flex; gap: 0; margin-top: 12px; min-height: 480px;">
      <!-- Left: Track List -->
      <div style="width: 200px; border-right: 1px solid #f0f0f0; padding-right: 16px; flex-shrink: 0;">
        <div style="font-size: 14px; font-weight: 600; margin-bottom: 12px; color: #262626;">订阅赛道</div>
        <div
          v-for="t in recommendUserTracks"
          :key="t.id"
          @click="selectRecommendTrack(t.id)"
          :style="{
            padding: '10px 12px',
            borderRadius: '6px',
            cursor: 'pointer',
            marginBottom: '8px',
            fontSize: '13px',
            background: selectedRecommendTrackId === t.id ? '#e6f7ff' : 'transparent',
            border: selectedRecommendTrackId === t.id ? '1px solid #1890ff' : '1px solid transparent',
            color: selectedRecommendTrackId === t.id ? '#1890ff' : '#595959',
          }"
        >
          <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 4px;">
            <span style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">{{ t.name }}</span>
            <Tag v-if="recommendUserPosts.some(p => p.trackId === t.id)" size="small" color="green" style="flex-shrink: 0; margin-left: 6px;">已推荐</Tag>
            <Tag v-else size="small" color="orange" style="flex-shrink: 0; margin-left: 6px;">待推荐</Tag>
          </div>
          <div style="font-size: 11px; color: selectedRecommendTrackId === t.id ? '#69c0ff' : '#bfbfbf';">{{ t.platforms || '-' }}</div>
        </div>
        <div v-if="!recommendUserTracks.length" style="color: #999; font-size: 13px; padding: 12px 0;">该用户未订阅任何赛道</div>
      </div>

      <!-- Right: Form / Preview -->
      <div style="flex: 1; padding-left: 24px;">
        <div v-if="selectedRecommendTrack" style="max-width: 560px;">
          <!-- Template Info -->
          <div style="background: #f6ffed; border: 1px solid #b7eb8f; border-radius: 6px; padding: 12px 16px; margin-bottom: 20px;">
            <div style="display: flex; align-items: center; gap: 8px;">
              <span style="font-size: 13px; color: #52c41a; flex-shrink: 0;">用户默认样式：</span>
              <template v-if="userSelectedTemplate">
                <span style="font-size: 14px; font-weight: 600; color: #262626;">{{ userSelectedTemplate.name }}</span>
                <Tag v-if="userSelectedTemplate.configObj?.description" size="small" color="blue">{{ userSelectedTemplate.configObj.description.split(' / ')[0] }}</Tag>
              </template>
              <span v-else style="font-size: 14px; font-weight: 500; color: #8c8c8c;">{{ recommendUser.template || '未设置' }}</span>
            </div>
            <div v-if="userSelectedTemplate?.configObj?.description" style="font-size: 12px; color: #8c8c8c; margin-top: 4px; padding-left: 86px;">
              {{ userSelectedTemplate.configObj.description }}
            </div>
          </div>

          <!-- Edit Mode -->
          <div v-if="!previewMode">
            <Form layout="vertical">
              <Form.Item label="文章标题" required>
                <Input v-model:value="recommendForm.title" placeholder="请输入文章标题" />
              </Form.Item>

              <Form.Item label="订阅文章文件">
                <div style="display: flex; gap: 12px; align-items: center;">
                  <Upload
                    :customRequest="handleRecommendUpload"
                    :showUploadList="false"
                    accept=".doc,.docx,.pdf,.txt,.md"
                  >
                    <Button :loading="recommendUploading">
                      {{ recommendUploading ? '上传中...' : '点击上传' }}
                    </Button>
                  </Upload>
                  <Button v-if="recommendForm.fileUrl" type="link" @click="handleRecommendPreview">
                    预览文件
                  </Button>
                </div>
                <div v-if="recommendForm.fileName" style="margin-top: 8px; font-size: 12px; color: #666;">
                  已上传：{{ recommendForm.fileName }}
                </div>
              </Form.Item>

              <Form.Item label="文章描述">
                <Input.TextArea
                  v-model:value="recommendForm.description"
                  :rows="5"
                  placeholder="请输入文章描述"
                />
              </Form.Item>

              <div style="display: flex; justify-content: flex-end; gap: 12px; margin-top: 8px;">
                <Button @click="recommendModalOpen = false">取消</Button>
                <Button type="primary" @click="saveRecommend">保存</Button>
              </div>
            </Form>
          </div>

          <!-- Preview Mode -->
          <div v-else>
            <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px;">
              <div style="font-size: 16px; font-weight: 600;">
                {{ recommendForm.fileName || '文件预览' }}
              </div>
              <Button size="small" @click="backToEdit">返回编辑</Button>
            </div>

            <div style="border: 1px solid #f0f0f0; border-radius: 6px; background: #fafafa; min-height: 360px; max-height: 480px; overflow: auto;">
              <!-- PDF -->
              <iframe
                v-if="previewFileType === 'pdf'"
                :src="recommendForm.fileUrl"
                style="width: 100%; height: 480px; border: 0;"
              />
              <!-- Text -->
              <pre v-else-if="previewFileType === 'text'" style="padding: 16px; margin: 0; font-family: monospace; font-size: 13px; line-height: 1.6; white-space: pre-wrap; word-break: break-word;">{{ previewLoading ? '加载中...' : previewContent }}</pre>
              <!-- Word / Other -->
              <div v-else style="padding: 48px 24px; text-align: center; color: #999;">
                <div style="font-size: 40px; margin-bottom: 16px;">📄</div>
                <div style="font-size: 14px; margin-bottom: 8px;">该文件格式暂不支持浏览器在线预览</div>
                <div style="font-size: 12px; color: #bbb; margin-bottom: 16px;">请下载后用相应软件打开查看</div>
                <Button type="primary" @click="() => { window.open(recommendForm.fileUrl, '_blank') }">下载文件</Button>
              </div>
            </div>
          </div>
        </div>
        <div v-else style="display: flex; align-items: center; justify-content: center; height: 100%; color: #999;">
          请选择左侧赛道
        </div>
      </div>
    </div>
  </Modal>

  <!-- Track Info Modal -->
  <Modal
    v-model:open="trackInfoModalOpen"
    :title="`${trackInfoUser.username || ''} 的赛道订阅`"
    :footer="null"
    :width="560"
  >
    <div style="margin-top: 12px;">
      <div style="display: flex; gap: 12px; margin-bottom: 16px;">
        <Select show-search
          v-model:value="selectedAddTrackId"
          placeholder="选择要添加的赛道"
          style="flex: 1;"
          allowClear
        >
          <Select.Option v-for="t in availableTracksForAdd" :key="t.id" :value="t.id" :label="t.name + '（' + ((t.platforms || '').split(/[,，\s]+/).filter(Boolean).join('、') || '-') + '）'">
            {{ t.name }}（{{ (t.platforms || '').split(/[,，\s]+/).filter(Boolean).join('、') || '-' }}）
          </Select.Option>
        </Select>
        <Button
          type="primary"
          :loading="addTrackLoading"
          :disabled="!selectedAddTrackId"
          @click="handleAddUserTrack"
        >
          添加订阅
        </Button>
      </div>
      <div v-if="trackInfoLoading" style="text-align: center; padding: 40px; color: #999;">加载中...</div>
      <div v-else-if="!trackInfoList.length" style="text-align: center; padding: 40px; color: #999;">该用户未订阅任何赛道</div>
      <div v-else>
        <div
          v-for="t in trackInfoList"
          :key="t.id"
          style="display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; border: 1px solid #f0f0f0; border-radius: 6px; margin-bottom: 8px; background: #fafafa;"
        >
          <div>
            <div style="font-size: 14px; font-weight: 500; color: #262626;">{{ t.name }}</div>
            <div style="font-size: 12px; color: #8c8c8c; margin-top: 2px;">{{ t.platformDisplay }}</div>
          </div>
          <a style="color: #f5222d; font-size: 13px;" @click="handleCancelUserTrack(t)">取消订阅</a>
        </div>
      </div>
    </div>
  </Modal>

  <!-- Next Title Modal -->
  <Modal
    v-model:open="nextTitleModalOpen"
    :title="`为 ${nextTitleUser.username || ''} 设定下一个标题`"
    :mask-closable="false"
    :confirm-loading="nextTitleLoading"
    @ok="saveNextTitle"
    :width="600"
  >
    <Form layout="vertical" style="margin-top: 12px;">
      <Form.Item label="推荐日期" required>
        <DatePicker v-model:value="nextTitleForm.recommendDate" valueFormat="YYYY-MM-DD" placeholder="选择推荐日期" style="width: 100%;" @change="(date) => loadExistingTitlesByDate(date || '')" />
      </Form.Item>
      <template v-if="nextTitleUserTrackIds.length === 0">
        <div style="color: #f5222d; font-size: 14px; margin-bottom: 16px;">该用户未订阅任何赛道，请先订阅赛道后再设定标题</div>
      </template>
      <template v-else>
        <div v-for="(item, index) in nextTitleForm.items" :key="item.trackId" style="margin-bottom: 16px; padding: 12px; background: #f5f5f5; border-radius: 4px;">
          <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px;">
            <span style="font-size: 13px; color: #666; font-weight: 500;">赛道 {{ index + 1 }}：</span>
            <Tag color="blue">{{ (allTracks.find(t => t.id === item.trackId)?.platforms || '-') }} - {{ (allTracks.find(t => t.id === item.trackId)?.name || '-') }}</Tag>
          </div>
          <Input v-model:value="item.title" :placeholder="`请输入该赛道的标题`" />
        </div>
      </template>
    </Form>
  </Modal>

  <!-- Create Order Modal -->
  <Modal
    v-model:open="orderModalOpen"
    :title="orderForm.type === 'renew' ? '创建续费订单' : '创建升级订单'"
    :mask-closable="false"
    :confirm-loading="orderLoading"
    @ok="handleCreateOrder"
    :width="480"
  >
    <Form layout="vertical" style="margin-top: 12px;">
      <Form.Item label="套餐" required>
        <Select show-search v-model:value="orderForm.planId" placeholder="选择套餐" allowClear>
          <Select.Option v-for="p in allPlans" :key="p.id" :value="p.id" :label="p.name + '（¥' + p.price + '）'">{{ p.name }}（¥{{ p.price }}）</Select.Option>
        </Select>
      </Form.Item>
      <Form.Item label="金额（留空自动取套餐价格）">
        <Input v-model:value="orderForm.amount" placeholder="自动计算" />
      </Form.Item>
      <Form.Item label="备注">
        <Input v-model:value="orderForm.remark" placeholder="选填" />
      </Form.Item>
    </Form>
  </Modal>

  <!-- Import Modal -->
  <Modal
    v-model:open="importModalOpen"
    title="批量导入用户"
    :mask-closable="false"
    :confirm-loading="importLoading"
    @ok="handleImport"
    :width="560"
  >
    <Form layout="vertical" style="margin-top: 12px;">
      <div style="background: #f6ffed; border: 1px solid #b7eb8f; border-radius: 4px; padding: 12px; font-size: 13px; color: #52c41a; margin-bottom: 16px;">
        <div style="font-weight: 600; margin-bottom: 8px;">导入说明</div>
        <div>1. 下载模板，按格式填写用户信息</div>
        <div>2. Excel 列顺序：用户名 | 手机号 | 邮箱 | 微信号 | 可选赛道数 | 可访问平台 | 到期时间 | 会员套餐 | 默认样式 | 状态 | 备注</div>
        <div>3. 用户名重复时会覆盖更新该用户，不重复则新增</div>
        <div>4. 新增用户初始密码为 Abc123456</div>
      </div>
      <Button size="small" @click="downloadTemplate">下载导入模板</Button>
      <Form.Item label="Excel 文件" required style="margin-top: 16px;">
        <input
          type="file"
          accept=".xlsx,.xls,.csv"
          style="display: block; margin-top: 8px;"
          @change="handleImportFileChange"
        />
      </Form.Item>
    </Form>
  </Modal>

  <!-- Batch Update Admin Modal -->
  <Modal
    v-model:open="batchAdminModalOpen"
    title="批量修改运营者"
    :mask-closable="false"
    :confirm-loading="batchAdminLoading"
    @ok="handleBatchUpdateAdmin"
    :width="480"
  >
    <Form layout="vertical" style="margin-top: 12px;">
      <div style="margin-bottom: 16px; color: #666; font-size: 13px;">
        已选择 <strong style="color: #1890ff;">{{ selectedRowKeys.length }}</strong> 位用户
      </div>
      <Form.Item label="归属运营者">
        <Select show-search v-model:value="batchAdminId" placeholder="请选择归属运营者（留空表示取消归属）" allow-clear style="width: 100%;">
          <Select.Option v-for="op in allOperators" :key="op.id" :value="op.id" :label="op.name || op.username">{{ op.name || op.username }}</Select.Option>
        </Select>
      </Form.Item>
    </Form>
  </Modal>

  <!-- Batch Style Modal -->
  <Modal
    v-model:open="batchStyleModalOpen"
    title="批量配置文章样式"
    :mask-closable="false"
    :confirm-loading="batchStyleLoading"
    @ok="handleBatchSaveStyle"
    :width="640"
  >
    <Form layout="vertical" style="margin-top: 12px;">
      <div style="margin-bottom: 16px; color: #666; font-size: 13px;">
        已选择 <strong style="color: #1890ff;">{{ selectedRowKeys.length }}</strong> 位用户
      </div>
      <Form.Item label="文章主题色">
        <div style="display: flex; align-items: center; gap: 12px;">
          <input
            v-model="batchThemeColor"
            type="color"
            style="width: 48px; height: 32px; border: 1px solid #d9d9d9; border-radius: 4px; cursor: pointer; padding: 2px;"
          />
          <Input
            v-model:value="batchThemeColor"
            style="width: 120px;"
            placeholder="#fa541c"
          />
          <span style="font-size: 12px; color: #888;">用于文章中的着重色、小标题颜色</span>
        </div>
      </Form.Item>
      <div style="display: flex; gap: 16px;">
        <Form.Item label="标题字号 (pt)" style="flex: 1;">
          <InputNumber v-model:value="batchTitleFontSize" :min="10" :max="32" style="width: 100%;" />
        </Form.Item>
        <Form.Item label="正文字号 (pt)" style="flex: 1;">
          <InputNumber v-model:value="batchContentFontSize" :min="10" :max="32" style="width: 100%;" />
        </Form.Item>
      </div>
      <Form.Item label="样式提示词">
        <Input.TextArea
          v-model:value="batchStyleForm"
          :rows="6"
          placeholder="请输入要批量应用的样式提示词..."
        />
      </Form.Item>
      <div style="background: #f6ffed; border: 1px solid #b7eb8f; border-radius: 4px; padding: 12px; font-size: 12px; color: #52c41a;">
        <div style="font-weight: 600; margin-bottom: 8px;">使用说明</div>
        <div>此处配置的样式将批量应用到已勾选的所有用户。</div>
        <div style="margin-top: 4px;">样式提示词留空表示不清空原有提示词；主题色和字号不填则使用默认值。</div>
      </div>
    </Form>
  </Modal>

  <!-- Style Config Modal -->
  <Modal
    v-model:open="styleModalOpen"
    :title="`文章样式提示词 — ${styleUser.username || ''}`"
    :mask-closable="false"
    :confirm-loading="styleLoading"
    @ok="saveStyle"
    :width="640"
  >
    <Form layout="vertical" style="margin-top: 12px;">
      <Form.Item label="文章主题色">
        <div style="display: flex; align-items: center; gap: 12px;">
          <input
            v-model="styleThemeColor"
            type="color"
            style="width: 48px; height: 32px; border: 1px solid #d9d9d9; border-radius: 4px; cursor: pointer; padding: 2px;"
          />
          <Input
            v-model:value="styleThemeColor"
            style="width: 120px;"
            placeholder="#fa541c"
          />
          <span style="font-size: 12px; color: #888;">用于文章中的着重色、小标题颜色</span>
        </div>
      </Form.Item>
      <div style="display: flex; gap: 16px;">
        <Form.Item label="标题字号 (pt)" style="flex: 1;">
          <InputNumber v-model:value="styleTitleFontSize" :min="10" :max="32" style="width: 100%;" />
        </Form.Item>
        <Form.Item label="正文字号 (pt)" style="flex: 1;">
          <InputNumber v-model:value="styleContentFontSize" :min="10" :max="32" style="width: 100%;" />
        </Form.Item>
      </div>
      <Form.Item label="样式提示词">
        <Input.TextArea
          v-model:value="styleForm"
          :rows="8"
          placeholder="请输入该用户专属的文章排版样式描述..."
        />
      </Form.Item>
      <div style="background: #f6ffed; border: 1px solid #b7eb8f; border-radius: 4px; padding: 12px; font-size: 12px; color: #52c41a;">
        <div style="font-weight: 600; margin-bottom: 8px;">使用说明</div>
        <div>在此处填写该用户的专属样式描述，支持任意自然语言。</div>
        <div style="margin-top: 4px;">在「标题库」的提示词模板中使用 <span style="font-family: monospace;">${stylePrompt}</span> 变量即可引用此处内容。</div>
        <div style="margin-top: 4px;">主题色会应用到生成 DOCX 文件的标题和高亮文字上。</div>
      </div>
    </Form>
  </Modal>
</template>
