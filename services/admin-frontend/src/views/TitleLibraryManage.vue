<script setup>
import { ref, onMounted, computed, h, nextTick, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import dayjs from 'dayjs'
import { Card, Input, Select, Button, Table, Tag, Modal, Form, Popconfirm, message, Pagination, Descriptions, DatePicker, Tabs, Spin, InputNumber, Checkbox, CheckboxGroup, Dropdown, Menu, Tooltip, Row, Col, Space, Alert } from 'ant-design-vue'
import { FileTextOutlined, BarChartOutlined, CheckOutlined, CheckCircleOutlined, ExclamationCircleOutlined, EllipsisOutlined, DownOutlined } from '@ant-design/icons-vue'
import { listTitles, saveTitle, deleteTitle, importTitles, matchTodayTitles, matchCheck, matchPreview, matchConfirm, matchOne, unbindRecommendation, batchUnbindRecommendations, generateTitles, getGenerateStatus, cancelGenerate, generatePostsForToday, getGeneratePostStatus, cancelGeneratePost, getDefaultPromptTemplate, savePromptTemplate, exportTitleLibrary, exportTitleLibraryBatch, exportTitleList, exportTitleListBatch, importArticles, sendTitleEmail, batchSendTitleEmail, listUnrecommendedUsers, markTitleUsed, batchChangeTrack, clearRecommendationsByDate, getUserHistory, getUserHomogeneity, saveArticleFeedback, listArticleFeedback, deleteArticleFeedback, generatePostSingle, createGenerationTask, getPostContent, removeAiFlavor, batchAiPassed, batchCopied, autoInsertImages, sendArticleEmail, confirmTitle, batchConfirm, markAiFlavorHeavy, generateImagePost, getImagePosts, batchGenerateImagePost } from '../api/titleLibrary.js'
import { listAiFlavorRules } from '../api/aiFlavorRule.js'
import { listTracks } from '../api/track.js'
import { listUsers } from '../api/user.js'
import { createTitleGenerateTask, listTitleGenerateTasks } from '../api/titleGenerate.js'
import { listPromptTemplates } from '../api/promptTemplate.js'
import request from '../api/request.js'
import { renderAsync } from 'docx-preview'
import { useDocxHighlight } from '../composables/useDocxHighlight.js'

const router = useRouter()
const route = useRoute()
const { highlightStats, applyHighlight, clearHighlight } = useDocxHighlight()

const activeTab = ref(localStorage.getItem('titleLibrary_activeTab') || 'all')

// 兼容旧版 localStorage
if (activeTab.value === 'full' || activeTab.value === 'simple') activeTab.value = 'all'

const tableData = ref([])
const tracks = ref([])
const allUsers = ref([])
const filteredUsersForSelect = computed(() => {
  return allUsers.value
    .filter(u => u.status === 1)
    .map(u => {
      const parts = [u.nickName, u.email, u.wxName].filter(Boolean)
      const suffix = parts.length > 0 ? `（${parts.join('-')}）` : ''
      return { id: u.id, value: u.id, displayText: u.username + suffix }
    })
})
const loading = ref(false)

// 从 localStorage 恢复搜索条件
const savedSearch = JSON.parse(localStorage.getItem('titleLibrary_search') || '{}')

async function handleMarkUsed(record) {
  try {
    await markTitleUsed(record.id)
    record.isUsed = record.isUsed === 1 ? 0 : 1
    message.success(record.isUsed === 1 ? '已标记为使用' : '已取消使用标记')
  } catch (e) {
    message.error('操作失败')
  }
}

const selectedRowKeys = ref([])
const selectedRows = ref([])

const globalDefaultStylePrompt = ref('')

const rowSelection = {
  onChange: (keys, rows) => {
    selectedRowKeys.value = keys
    selectedRows.value = rows
  },
}

// 批量/单个修改赛道
const changeTrackModalOpen = ref(false)
const changeTrackForm = ref({ titleIds: [], trackId: '', singleRecord: null })
const changeTrackLoading = ref(false)

function openBatchChangeTrack() {
  if (selectedRowKeys.value.length === 0) {
    message.warning('请先选择要修改赛道的标题')
    return
  }
  changeTrackForm.value = { titleIds: selectedRowKeys.value, trackId: '', singleRecord: null }
  changeTrackModalOpen.value = true
}

function openSingleChangeTrack(record) {
  changeTrackForm.value = { titleIds: [record.id], trackId: record.trackId || '', singleRecord: record }
  changeTrackModalOpen.value = true
}

async function handleChangeTrackConfirm() {
  if (!changeTrackForm.value.trackId) {
    message.warning('请选择赛道')
    return
  }
  changeTrackLoading.value = true
  try {
    const result = await batchChangeTrack(changeTrackForm.value.titleIds, changeTrackForm.value.trackId)
    message.success(`修改成功 ${result.success} 条` + (result.failed > 0 ? `，失败 ${result.failed} 条` : ''))
    changeTrackModalOpen.value = false
    selectedRowKeys.value = []
    selectedRows.value = []
    loadData()
  } catch (e) {
    message.error('修改失败')
  } finally {
    changeTrackLoading.value = false
  }
}

const searchKeyword = ref(savedSearch.keyword || '')
const searchPlatform = ref(savedSearch.platform || '')
const searchTrack = ref(savedSearch.trackId || '')
const searchUserName = ref(savedSearch.userName || '')
const searchMatched = ref(savedSearch.matched || '1')
const searchPushDate = ref(savedSearch.pushDate ? dayjs(savedSearch.pushDate) : null)
const searchIsUsed = ref(savedSearch.isUsed || '')
const searchIsConfirmed = ref(savedSearch.isConfirmed || '')
const searchAiFlavor = ref(savedSearch.aiFlavor || '')

const filteredTracksForSearch = computed(() => {
  if (!searchPlatform.value) {
    return tracks.value
  }
  return tracks.value.filter(t => {
    if (!t.platforms) return false
    const trackPlatforms = t.platforms.split(/[·、,，\s]+/).filter(Boolean)
    return trackPlatforms.includes(searchPlatform.value)
  })
})

watch(searchPlatform, (newPlatform) => {
  if (!newPlatform) return
  if (!searchTrack.value) return
  const track = tracks.value.find(t => t.id === searchTrack.value)
  if (!track || !track.platforms || !track.platforms.split(/[·、,，\s]+/).filter(Boolean).includes(newPlatform)) {
    searchTrack.value = ''
  }
})

function saveSearchState() {
  const state = {
    keyword: searchKeyword.value,
    platform: searchPlatform.value,
    trackId: searchTrack.value,
    userName: searchUserName.value,
    matched: searchMatched.value,
    pushDate: searchPushDate.value ? searchPushDate.value.format('YYYY-MM-DD') : null,
    isUsed: searchIsUsed.value,
    isConfirmed: searchIsConfirmed.value,
    aiFlavor: searchAiFlavor.value,
  }
  localStorage.setItem('titleLibrary_search', JSON.stringify(state))
}

function saveActiveTab() {
  localStorage.setItem('titleLibrary_activeTab', activeTab.value)
}

// 右侧面板
const PANEL_DATE_KEY = 'titleLibrary_panelDate'
const savedDate = localStorage.getItem(PANEL_DATE_KEY)
const panelDate = ref(savedDate ? dayjs(savedDate) : dayjs())
const activePanelTab = ref('unrecommended')
const activeUnrecommendedSubTab = ref('accountOpened')
const unrecommendedAccountOpened = ref([])
const unrecommendedDistributor = ref([])
const unrecommendedTrial = ref([])
const panelLoading = ref(false)

const unrecommendedColumns = [
  { title: '用户名', dataIndex: 'username', ellipsis: true },
  { title: '邮箱', dataIndex: 'email', ellipsis: true },
  { title: '未推荐赛道', dataIndex: 'missingTracks', ellipsis: true },
]

async function loadPanelData() {
  const dateStr = panelDate.value.format('YYYY-MM-DD')
  panelLoading.value = true
  try {
    const [unrecAcc, unrecDist, unrecTrial] = await Promise.all([
      listUnrecommendedUsers(dateStr, 'accountOpened').catch(() => []),
      listUnrecommendedUsers(dateStr, 'distributor').catch(() => []),
      listUnrecommendedUsers(dateStr, 'trial').catch(() => []),
    ])
    unrecommendedAccountOpened.value = unrecAcc || []
    unrecommendedDistributor.value = unrecDist || []
    unrecommendedTrial.value = unrecTrial || []
  } catch (e) {
    message.error('面板数据加载失败')
  } finally {
    panelLoading.value = false
  }
}

function onPanelDateChange() {
  if (panelDate.value) {
    localStorage.setItem(PANEL_DATE_KEY, panelDate.value.format('YYYY-MM-DD'))
  }
  loadPanelData()
}

const platformOptions = [
  { label: '公众号', value: '公众号' },
  { label: '今日头条', value: '今日头条' },
  { label: '百家号', value: '百家号' },
]

const mainTabs = [
  { key: 'all', label: '全部' },
  { key: 'accountOpened', label: '开户' },
  { key: 'distributor', label: '分成' },
  { key: 'trial', label: '试用' },
]

// 弹窗中赛道选项：按平台排序，显示为"平台-赛道"
const sortedTrackOptions = computed(() => {
  const platformOrder = platformOptions.map(p => p.value)
  const list = [...tracks.value].filter(t => t.name)
  list.sort((a, b) => {
    const pa = a.platforms ? a.platforms.split(/[,，\s]+/).filter(Boolean)[0] : ''
    const pb = b.platforms ? b.platforms.split(/[,，\s]+/).filter(Boolean)[0] : ''
    const ia = platformOrder.indexOf(pa)
    const ib = platformOrder.indexOf(pb)
    if (ia !== ib) return (ia === -1 ? 999 : ia) - (ib === -1 ? 999 : ib)
    return a.name.localeCompare(b.name)
  })
  return list.map(t => {
    // 如果是当前选中的赛道且 form.platform 有值，优先用标题实际的平台（编辑时保持一致）
    const displayPlatform = (form.value.trackId === t.id && form.value.platform)
      ? form.value.platform
      : (t.platforms ? t.platforms.split(/[,，\s]+/).filter(Boolean)[0] : '')
    return { ...t, displayLabel: displayPlatform ? `${displayPlatform} - ${t.name}` : t.name }
  })
})

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || ''

const columns = [
  {
    title: '标题内容',
    key: 'title',
    customRender: ({ record }) => {
      const btnStyle = 'padding: 0; display: inline-block;'
      const nodes = []
      const hasPost = !!record.generatedFileUrl
      if (hasPost) {
        nodes.push(
          h(Button, {
            type: 'link',
            size: 'small',
            style: btnStyle,
            onClick: () => handlePreviewGeneratedArticle(record),
          }, () => record.title)
        )
        if (record.generatedFileUrl) {
          nodes.push(
            h(Button, {
              type: 'link',
              size: 'small',
              style: 'padding: 0; flex-shrink: 0; font-size: 12px;',
              onClick: () => handleDownloadPost(record),
            }, () => '下载')
          )
        }
      } else {
        nodes.push(h('span', { style: btnStyle }, record.title))
      }
      nodes.push(
        h(Button, {
          type: 'link',
          size: 'small',
          style: 'padding: 0; flex-shrink: 0; font-size: 12px; color: ' + (record.isCopied ? '#52c41a' : ''),
          onClick: () => copyRowPrompt(record)
        }, () => record.isCopied ? '✓ 已复制' : '复制提示词')
      )
      return h('div', { style: 'display: flex; flex-wrap: wrap; align-items: center; gap: 8px; line-height: 1.5;' }, nodes)
    },
  },
  { title: '推荐时间', dataIndex: 'pushDate', width: 90 },
  {
    title: '管理用户',
    key: 'userInfo',
    dataIndex: 'recommendUserName',
    ellipsis: true,
    width: 150,
    sorter: true,
    customRender: ({ record }) => {
      if (!record.recommendUserName) return h('span', { style: 'color: #999;' }, '未匹配')
      return h(Button, {
        type: 'link',
        size: 'small',
        style: 'padding: 0; max-width: 140px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; display: inline-block;',
        onClick: () => handleViewUser(record),
      }, () => record.recommendUserName)
    },
  },
  {
    title: '确认状态',
    key: 'confirmStatus',
    width: 100,
    align: 'center',
    customRender: ({ record }) => {
      const status = record.confirmStatus
      if (status === 1) {
        return h(Tag, { color: 'success' }, () => '已确认')
      }
      if (status === 2) {
        return h(Tag, { color: 'error' }, () => '已拒绝')
      }
      return h('span', { style: 'color: #999;' }, '未确认')
    },
  },
  {
    title: 'AI味',
    key: 'aiFlavor',
    width: 90,
    align: 'center',
    customRender: ({ record }) => {
      if (record.aiFlavorStatus === 2) {
        return h(Tag, { color: 'error' }, () => 'AI味重')
      }
      if (record.aiFlavorStatus === 1) {
        return h(Tag, { color: 'success' }, () => '通过')
      }
      return h('span', { style: 'color: #999;' }, '-')
    },
  },
  {
    title: '生成状态',
    key: 'generateStatus',
    width: 90,
    align: 'center',
    customRender: ({ record }) => {
      const status = record.generateStatus
      if (status === 3) return h(Tag, { color: 'blue' }, () => '排队中')
      if (status === 2) return h(Tag, { color: 'orange' }, () => '生成中')
      if (status === 1) return h(Tag, { color: 'green' }, () => '已生成')
      return h(Tag, { color: 'default' }, () => '未生成')
    },
  },
  {
    title: '平台/赛道',
    key: 'platformTrack',
    ellipsis: true,
    width: 130,
    customRender: ({ record }) => {
      return h('span', {}, `${record.platform || '-'} / ${record.trackName || '-'}`)
    },
  },
  { title: '创建时间', dataIndex: 'createdAt', width: 160, sorter: true },
  {
    title: '操作',
    key: 'action',
    width: 240,
    align: 'center',
    fixed: 'right',
    customRender: ({ record }) => {
      const hasFile = !!(record.generatedFileUrl)

      // 生成按钮
      const genBtn = h(Tooltip, { title: '生成文章' }, {
        default: () => h(Button, {
          type: 'text',
          size: 'small',
          loading: generatingPostId.value === record.id,
          onClick: () => handleGeneratePost(record),
          style: { color: '#595959', padding: '0 1px', minWidth: '22px' },
        }, { icon: () => h(FileTextOutlined) }),
      })

      // 推荐概览按钮
      const pushOverviewBtn = h(Tooltip, { title: '推荐概览' }, {
        default: () => h(Button, {
          type: 'text',
          size: 'small',
          onClick: () => router.push('/push-overview'),
          style: { color: '#595959', padding: '0 1px', minWidth: '22px' },
        }, { icon: () => h(BarChartOutlined) }),
      })

      // 确认按钮
      const isConfirmed = record.confirmStatus === 1
      const confirmBtn = h(Tooltip, { title: isConfirmed ? '已确认' : '确认' }, {
        default: () => h(Button, {
          type: 'text',
          size: 'small',
          onClick: () => !isConfirmed && handleConfirm(record),
          style: { color: isConfirmed ? '#52c41a' : '#bfbfbf', padding: '0 1px', minWidth: '22px', cursor: isConfirmed ? 'default' : 'pointer' },
        }, { icon: () => h(CheckOutlined) }),
      })

      // AI检测通过按钮
      const aiPassBtn = h(Tooltip, { title: record.aiFlavorStatus === 1 ? 'AI检测已通过' : '标记AI通过' }, {
        default: () => h(Button, {
          type: 'text',
          size: 'small',
          onClick: () => record.aiFlavorStatus !== 1 && handleAiPassedOne(record),
          style: { color: record.aiFlavorStatus === 1 ? '#1890ff' : '#bfbfbf', padding: '0 1px', minWidth: '22px', cursor: record.aiFlavorStatus === 1 ? 'default' : 'pointer' },
        }, { icon: () => h(CheckCircleOutlined) }),
      })

      // AI味重按钮
      const aiHeavyBtn = h(Tooltip, { title: record.aiFlavorStatus === 2 ? 'AI味重（点击取消）' : '标记AI味重' }, {
        default: () => h(Button, {
          type: 'text',
          size: 'small',
          onClick: () => handleToggleAiFlavorHeavy(record),
          style: { color: record.aiFlavorStatus === 2 ? '#ff4d4f' : '#fa8c16', padding: '0 1px', minWidth: '22px' },
        }, { icon: () => h(ExclamationCircleOutlined) }),
      })

      // 更多下拉：编辑、文件、发邮件、删除
      const moreMenuItems = []
      moreMenuItems.push(
        h(Menu.Item, { key: 'edit', onClick: () => handleEdit(record) }, () => '编辑')
      )
      if (hasFile) {
        moreMenuItems.push(h(Menu.Item, { key: 'dl', onClick: () => downloadArticle(record) }, () => '下载'))
        moreMenuItems.push(h(Menu.Item, { key: 'pv', onClick: () => handlePreviewGeneratedArticle(record) }, () => '预览'))
      }
      if (hasFile && record.recommendUserId) {
        moreMenuItems.push(
          h(Menu.Item, { key: 'semail', onClick: () => sendArticleEmailToUser(record) }, () =>
            h('span', {}, sendArticleEmailLoadingId.value === record.id ? '发送中...' : '发送文章邮件')
          )
        )
      }
      moreMenuItems.push(h(Menu.Divider))
      moreMenuItems.push(
        h(Menu.Item, { key: 'del', danger: true, onClick: () => handleDelete(record) }, () => '删除')
      )

      const moreDropdown = h(Dropdown, {}, {
        default: () => h(Button, {
          type: 'text',
          size: 'small',
          style: { color: '#595959', padding: '0 1px', minWidth: '22px' },
        }, { icon: () => h(EllipsisOutlined) }),
        overlay: () => h(Menu, {}, () => moreMenuItems),
      })

      return h('div', { style: 'display: flex; justify-content: center; align-items: center; gap: 2px; white-space: nowrap;' }, [
        genBtn,
        pushOverviewBtn,
        confirmBtn,
        aiPassBtn,
        aiHeavyBtn,
        moreDropdown,
      ].filter(Boolean))
    },
  },
]

const currentPage = ref(1)
const pageSize = ref(10)
const totalCount = ref(0)
const sortField = ref('createdAt')
const sortOrder = ref('descend')

const paginatedData = computed(() => {
  // 服务端已分页，直接使用
  return tableData.value
})

async function loadData() {
  loading.value = true
  try {
    const params = {}
    if (searchKeyword.value) params.keyword = searchKeyword.value.trim()
    if (searchPlatform.value) params.platform = searchPlatform.value
    if (searchTrack.value) params.trackId = searchTrack.value
    if (searchUserName.value) params.recommendUserName = searchUserName.value.trim()
    if (searchMatched.value !== '' && searchMatched.value !== undefined) params.matched = searchMatched.value
    if (searchPushDate.value) params.pushDate = searchPushDate.value.format('YYYY-MM-DD')
    if (searchIsUsed.value !== '' && searchIsUsed.value !== undefined) params.isUsed = searchIsUsed.value
    if (searchIsConfirmed.value !== '' && searchIsConfirmed.value !== undefined) params.isConfirmed = searchIsConfirmed.value
    if (searchAiFlavor.value !== '' && searchAiFlavor.value !== undefined) params.aiFlavor = searchAiFlavor.value
    // 按用户类型过滤（全部、开户、分成、试用）
    const userTypeMap = { accountOpened: '1', distributor: '2', trial: '3' }
    if (userTypeMap[activeTab.value]) params.userType = userTypeMap[activeTab.value]
    params.page = currentPage.value
    params.pageSize = pageSize.value
    if (sortField.value) {
      params.sortField = sortField.value
      params.sortOrder = sortOrder.value
    }

    // 1. 先加载核心数据（标题列表），拿到立刻渲染表格
    const result = await listTitles(params)
    if (result && Array.isArray(result.list)) {
      tableData.value = result.list
      totalCount.value = result.total || 0
    } else {
      tableData.value = result || []
      totalCount.value = result?.length || 0
    }
    loading.value = false

    // 2. 后台异步加载赛道、用户列表和全局默认样式，不阻塞表格显示
    Promise.all([
      listTracks().catch(() => []),
      listUsers().catch(() => []),
      request.get('/configs').catch(() => null),
    ]).then(([trackList, userList, configs]) => {
      tracks.value = trackList || []
      allUsers.value = (userList || []).map(u => ({
        id: u.id,
        username: u.username,
        nickName: u.nickName || '',
        email: u.email || '',
        wxName: u.wxName || '',
        status: u.status,
        styleConfig: u.styleConfig || '',
      }))
      if (configs && configs.defaultArticleStyle !== undefined) {
        globalDefaultStylePrompt.value = configs.defaultArticleStyle
      }
    }).catch(() => {
      // 辅助数据加载失败不影响主表格
    })
  } catch (e) {
    console.error('loadData error:', e)
    message.error('加载失败: ' + (e?.message || '未知错误'))
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  saveSearchState()
  loadData()
}

function handlePageChange() {
  loadData()
}

function handleTableChange(pagination, filters, sorter) {
  if (sorter && sorter.field) {
    sortField.value = sorter.field === 'userInfo' ? 'recommendUserName' : sorter.field
    sortOrder.value = sorter.order || ''
  } else {
    sortField.value = ''
    sortOrder.value = ''
  }
  currentPage.value = 1
  loadData()
}

function handleReset() {
  searchKeyword.value = ''
  searchPlatform.value = ''
  searchTrack.value = ''
  searchUserName.value = ''
  searchMatched.value = ''
  searchPushDate.value = null
  searchIsUsed.value = ''
  searchIsConfirmed.value = ''
  searchAiFlavor.value = ''
  currentPage.value = 1
  saveSearchState()
  loadData()
}

const modalOpen = ref(false)
const modalTitle = ref('新增标题')
const form = ref({ title: '', description: '', pushDate: null, platform: '', trackId: '', recommendUserId: '', recommendUserName: '' })
const saving = ref(false)

function syncPlatformFromTrack() {
  if (!form.value.trackId) {
    form.value.platform = ''
    return
  }
  const track = tracks.value.find(t => t.id === form.value.trackId)
  if (track && track.platforms) {
    const firstPlatform = track.platforms.split(/[,，\s]+/).filter(Boolean)[0]
    form.value.platform = firstPlatform || ''
  }
}

function handleTrackChange() {
  syncPlatformFromTrack()
}

function handleAdd() {
  modalTitle.value = '新增标题'
  form.value = { title: '', description: '', pushDate: null, platform: '', trackId: '', recommendUserId: '', recommendUserName: '' }
  modalOpen.value = true
}

function handleEdit(record) {
  modalTitle.value = '编辑标题'
  form.value = {
    id: record.id,
    title: record.title,
    description: record.description,
    pushDate: record.pushDate ? dayjs(record.pushDate) : null,
    platform: record.platform,
    trackId: record.trackId,
    recommendUserId: record.recommendUserId || '',
    recommendUserName: record.recommendUserName || '',
    recommendDate: record.recommendDate,
    recommendUserTemplate: record.recommendUserTemplate,
  }
  modalOpen.value = true
}

async function handleUnbind() {
  if (!form.value.id) return
  Modal.confirm({
    title: '确认解绑',
    content: `确定要解绑标题「${form.value.title}」的关联用户吗？`,
    onOk: async () => {
      try {
        await unbindRecommendation(form.value.id)
        message.success('解绑成功')
        modalOpen.value = false
        loadData()
      } catch (e) {
        message.error('解绑失败')
      }
    },
  })
}

const sendEmailLoadingId = ref(null)
const sendArticleEmailLoadingId = ref(null)

// 文章反馈相关
const feedbackList = ref([])
const feedbackInput = ref('')
const feedbackLoading = ref(false)
const feedbackSaving = ref(false)

// 单标题生成文章
const generatingPostId = ref(null)
const batchGenerating = ref(false)

async function handleBatchGenerate() {
  if (selectedRowKeys.value.length === 0) {
    message.warning('请先选择要生成的标题')
    return
  }
  batchGenerating.value = true
  let success = 0
  let failed = 0
  const total = selectedRowKeys.value.length
  for (const id of selectedRowKeys.value) {
    try {
      await createGenerationTask(id)
      success++
    } catch (e) {
      failed++
    }
  }
  batchGenerating.value = false
  if (failed === 0) {
    message.success(`全部 ${total} 条标题的生成任务已创建，系统将在后台自动处理`)
  } else {
    message.warning(`生成任务创建完成：成功 ${success} 条，失败 ${failed} 条`)
  }
  selectedRowKeys.value = []
  selectedRows.value = []
  await loadData()
}

async function handleSendEmail(record) {
  sendEmailLoadingId.value = record.id
  try {
    const result = await sendTitleEmail(record.id)
    message.success(`邮件已发送至 ${result.email || '用户邮箱'}`)
  } catch (e) {
    message.error(e?.response?.data?.msg || e?.message || '发送失败')
  } finally {
    sendEmailLoadingId.value = null
  }
}

// 创建异步生成任务（插入任务表，由定时任务后台处理）
async function handleGeneratePost(record) {
  generatingPostId.value = record.id
  try {
    const result = await createGenerationTask(record.id)
    message.success(result.message || '生成任务已创建，系统将在后台自动处理')
    await loadData()
  } catch (e) {
    message.error(e?.response?.data?.msg || e?.message || '创建生成任务失败')
  } finally {
    generatingPostId.value = null
  }
}

function downloadArticle(record) {
  const url = record.generatedFileUrl
  if (!url) return
  const link = document.createElement('a')
  link.href = url
  link.download = record.generatedFileName || 'article.docx'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

function handlePreviewGeneratedArticle(record) {
  const previewRecord = {
    ...record,
    title: record.title,
    generatedFileUrl: record.generatedFileUrl
  }
  handlePreviewPost(previewRecord)
}

async function sendArticleEmailToUser(record) {
  if (!record.generatedFileUrl) {
    message.warning('该标题尚未生成文章')
    return
  }
  if (!record.recommendUserId) {
    message.warning('该标题未关联用户，无法发送邮件')
    return
  }
  // Get user email
  const user = allUsers.value.find(u => String(u.id) === String(record.recommendUserId))
  if (!user || !user.email) {
    message.warning('关联用户未配置邮箱')
    return
  }
  try {
    sendArticleEmailLoadingId.value = record.id
    await sendArticleEmail(record.id, user.email)
    message.success('文章邮件发送成功')
  } catch (e) {
    message.error('发送邮件失败: ' + (e?.response?.data?.msg || e?.message || '未知错误'))
  } finally {
    sendArticleEmailLoadingId.value = null
  }
}

// 加载文章反馈
async function loadFeedback(trackId, platform) {
  feedbackLoading.value = true
  try {
    const res = await listArticleFeedback({ trackId, platform })
    feedbackList.value = res || []
  } catch (e) {
    console.error('loadFeedback error:', e)
  } finally {
    feedbackLoading.value = false
  }
}

// 保存文章反馈
async function handleSaveFeedback(trackId, platform) {
  const content = feedbackInput.value?.trim()
  if (!content) {
    message.warning('请输入反馈内容')
    return
  }
  feedbackSaving.value = true
  try {
    await saveArticleFeedback({ id: '', trackId, platform, content })
    message.success('反馈已保存')
    feedbackInput.value = ''
    await loadFeedback(trackId, platform)
  } catch (e) {
    message.error(e?.response?.data?.msg || e?.message || '保存失败')
  } finally {
    feedbackSaving.value = false
  }
}

// 删除反馈
async function handleDeleteFeedback(id, trackId, platform) {
  try {
    await deleteArticleFeedback(id)
    message.success('已删除')
    await loadFeedback(trackId, platform)
  } catch (e) {
    message.error(e?.response?.data?.msg || e?.message || '删除失败')
  }
}

async function handleBatchUnbind() {
  if (selectedRowKeys.value.length === 0) {
    message.warning('请先选择要解绑的标题')
    return
  }
  Modal.confirm({
    title: '确认批量解绑',
    content: `确定要解绑 ${selectedRowKeys.value.length} 条标题的关联用户吗？`,
    async onOk() {
      try {
        const result = await batchUnbindRecommendations(selectedRowKeys.value)
        message.success(`解绑成功 ${result.success} 条` + (result.failed > 0 ? `，失败 ${result.failed} 条` : ''))
        selectedRowKeys.value = []
        selectedRows.value = []
        loadData()
      } catch (e) {
        message.error('解绑失败')
      }
    },
  })
}

async function handleBatchAiPassed() {
  if (selectedRowKeys.value.length === 0) {
    message.warning('请先选择要标记的标题')
    return
  }
  try {
    const result = await batchAiPassed(selectedRowKeys.value)
    message.success(`已标记 ${result.success} 条为 AI味检测通过` + (result.failed > 0 ? `，失败 ${result.failed} 条` : ''))
    selectedRowKeys.value = []
    selectedRows.value = []
    loadData()
  } catch (e) {
    message.error(e?.response?.data?.msg || e?.message || '标记失败')
  }
}

async function handleAiPassedOne(record) {
  try {
    await batchAiPassed([record.id])
    record.aiFlavorStatus = 1
    message.success('已标记为 AI味检测通过')
  } catch (e) {
    message.error(e?.response?.data?.msg || e?.message || '标记失败')
  }
}

async function handleConfirm(record) {
  try {
    await confirmTitle(record.id)
    record.isConfirmed = 1
    record.confirmStatus = 1
    message.success('已确认')
  } catch (e) {
    message.error(e?.response?.data?.msg || e?.message || '确认失败')
  }
}

async function handleBatchSendEmail() {
  if (selectedRowKeys.value.length === 0) {
    message.warning('请先选择要发送邮件的标题')
    return
  }
  try {
    const result = await batchSendTitleEmail(selectedRowKeys.value)
    const { success = 0, failed = 0, errors = [] } = result
    if (failed === 0) {
      message.success(`全部发送成功，共 ${success} 封邮件`)
    } else {
      Modal.info({
        title: '批量发送结果',
        width: 520,
        content: h('div', { style: 'max-height: 320px; overflow-y: auto;' }, [
          h('div', { style: 'margin-bottom: 12px; font-size: 14px;' }, [
            h('span', { style: 'color: #52c41a; font-weight: 500;' }, `成功 ${success} 封`),
            h('span', { style: 'margin: 0 8px;' }, ' / '),
            h('span', { style: 'color: #f5222d; font-weight: 500;' }, `失败 ${failed} 封`),
          ]),
          errors.length > 0
            ? h('div', { style: 'display: flex; flex-direction: column; gap: 4px;' },
                errors.map((err, idx) =>
                  h('div', { key: idx, style: 'font-size: 12px; color: #666; padding: 4px 0; border-bottom: 1px dashed #f0f0f0;' },
                    `${err.title || err.titleId}：${err.reason}`
                  )
                )
              )
            : null,
        ]),
      })
    }
    selectedRowKeys.value = []
    selectedRows.value = []
  } catch (e) {
    message.error(e?.response?.data?.msg || e?.message || '批量发送失败')
  }
}

function handleUserChange(userId) {
  if (!userId) {
    form.value.recommendUserId = ''
    form.value.recommendUserName = ''
    return
  }
  const user = allUsers.value.find(u => u.id === userId)
  if (user) {
    form.value.recommendUserId = user.id
    form.value.recommendUserName = user.username
  }
}

async function handleSave() {
  if (!form.value.title || !form.value.title.trim()) {
    message.warning('请输入标题内容')
    return
  }
  saving.value = true
  try {
    const payload = { ...form.value }
    if (payload.pushDate && typeof payload.pushDate.format === 'function') {
      payload.pushDate = payload.pushDate.format('YYYY-MM-DD')
    }
    // 同步 recommendDate，避免后端 getPushDate() 因优先返回旧的 recommendDate 导致日期修改不生效
    payload.recommendDate = payload.pushDate || null
    if (!payload.recommendUserId) {
      payload.recommendUserId = null
      payload.recommendUserName = null
    }
    await saveTitle(payload)
    message.success('保存成功')
    modalOpen.value = false
    loadData()
  } catch (e) {
    message.error('保存失败')
  } finally {
    saving.value = false
  }
}

function handleDelete(record) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除标题「${record.title}」吗？`,
    onOk: async () => {
      try {
        await deleteTitle(record.id)
        message.success('删除成功')
        loadData()
      } catch (e) {
        message.error('删除失败')
      }
    },
  })
}

// Match today（审核制）
const matching = ref(false)
const matchModalOpen = ref(false)
const matchForm = ref({ date: dayjs(localStorage.getItem('matchForm_date') || dayjs().format('YYYY-MM-DD')) })
const matchPreviewData = ref([])      // 待审核匹配列表
const matchPreviewLoading = ref(false)
const matchOneLoadingId = ref(null)   // 正在重配的行
const matchClearing = ref(false)
const matchSelectedKeys = ref([])

const matchRowSelection = {
  onChange: (keys) => {
    matchSelectedKeys.value = keys
  },
}

// 日期选择前置弹框
const matchDateModalOpen = ref(false)
const matchDateForm = ref({ date: dayjs(localStorage.getItem('matchForm_date') || dayjs().format('YYYY-MM-DD')) })

function openMatchDateModal() {
  const saved = localStorage.getItem('matchForm_date')
  matchDateForm.value.date = saved ? dayjs(saved) : dayjs()
  matchDateModalOpen.value = true
}

function handleMatchDateConfirm() {
  const dateStr = matchDateForm.value.date ? matchDateForm.value.date.format('YYYY-MM-DD') : dayjs().format('YYYY-MM-DD')
  localStorage.setItem('matchForm_date', dateStr)
  matchForm.value.date = matchDateForm.value.date
  matchDateModalOpen.value = false
  matchModalOpen.value = true
  runMatchPreview()
}

// 无合适匹配提示弹框
const noMatchPromptOpen = ref(false)
const noMatchPromptTracks = ref([])

function handleNoMatchConfirm() {
  noMatchPromptOpen.value = false
  openV2GenerateModal(noMatchPromptTracks.value)
}

// V2 生成弹框（从 TitleGenerateManage.vue 提取）
const v2GenerateModalOpen = ref(false)
const v2GenerateCount = ref(100)
const v2GeneratePlatforms = ref([])
const v2GenerateTrackIds = ref([])
const v2GenerateInstruction = ref('')
const v2Generating = ref(false)
const v2InstructionMode = ref('manual')
const v2PromptTemplates = ref([])
const v2SelectedPromptId = ref('')
const v2StyleTemplates = ref([])
const v2SelectedStyleId = ref('')
let v2GeneratePollTimer = null

async function openV2GenerateModal(preFillTracks = []) {
  v2GenerateModalOpen.value = true
  v2GenerateCount.value = 100
  v2InstructionMode.value = 'manual'
  v2SelectedPromptId.value = ''
  v2GenerateInstruction.value = ''
  v2SelectedStyleId.value = ''

  // 加载提示词模板
  try {
    const res = await listPromptTemplates({ type: 'generate_title' })
    v2PromptTemplates.value = (res || []).filter(p => p.type === 'generate_title')
  } catch (e) {
    v2PromptTemplates.value = []
  }
  try {
    const res2 = await listPromptTemplates({ type: 'title_style' })
    v2StyleTemplates.value = (res2 || []).filter(p => p.type === 'title_style')
  } catch (e) {
    v2StyleTemplates.value = []
  }

  // 默认选中第一个风格和第一个方向
  if (v2StyleTemplates.value.length > 0) {
    v2SelectedStyleId.value = v2StyleTemplates.value[0].id
  }
  if (v2PromptTemplates.value.length > 0) {
    const firstTpl = v2PromptTemplates.value[0]
    v2SelectedPromptId.value = firstTpl.id
    v2GenerateInstruction.value = firstTpl.content
    v2InstructionMode.value = 'template'
  }

  // 预填赛道和平台
  v2GenerateTrackIds.value = []
  v2GeneratePlatforms.value = []
  if (preFillTracks && preFillTracks.length > 0) {
    for (const t of preFillTracks) {
      if (t.trackId) v2GenerateTrackIds.value.push(t.trackId)
      if (t.platform && !v2GeneratePlatforms.value.includes(t.platform)) {
        v2GeneratePlatforms.value.push(t.platform)
      }
    }
  }
}

function onV2PromptChange(val) {
  const tpl = v2PromptTemplates.value.find(p => p.id === val)
  if (tpl) {
    v2GenerateInstruction.value = tpl.content
  } else {
    v2GenerateInstruction.value = ''
  }
}

function onV2PlatformChange() {
  if (!v2GeneratePlatforms.value || v2GeneratePlatforms.value.length === 0) {
    return
  }
  v2GenerateTrackIds.value = v2GenerateTrackIds.value.filter(trackId => {
    const track = tracks.value.find(t => t.id === trackId)
    if (!track || !track.platforms) return false
    const trackPlatforms = track.platforms.split(',')
    return v2GeneratePlatforms.value.some(p => trackPlatforms.includes(p))
  })
}

const filteredTracksForV2Generate = computed(() => {
  if (!v2GeneratePlatforms.value || v2GeneratePlatforms.value.length === 0) {
    return tracks.value
  }
  return tracks.value.filter(t => {
    if (!t.platforms) return false
    const trackPlatforms = t.platforms.split(',')
    return v2GeneratePlatforms.value.some(p => trackPlatforms.includes(p))
  })
})

async function handleV2Generate() {
  if (!v2GenerateCount.value || v2GenerateCount.value < 1) {
    message.warning('请输入有效的生成数量')
    return
  }
  v2Generating.value = true
  try {
    const result = await createTitleGenerateTask({
      countPerCombo: parseInt(v2GenerateCount.value),
      outputPath: '',
      platforms: v2GeneratePlatforms.value,
      trackIds: v2GenerateTrackIds.value,
      instruction: v2GenerateInstruction.value.trim(),
      styleTemplateId: v2SelectedStyleId.value || undefined,
    })
    v2GenerateModalOpen.value = false
    message.success(result.message || '生成任务已创建')
    // 开始轮询任务状态
    startV2GeneratePoll()
  } catch (e) {
    message.error('提交失败: ' + (e?.response?.data?.msg || e?.message || '未知错误'))
  } finally {
    v2Generating.value = false
  }
}

function startV2GeneratePoll() {
  stopV2GeneratePoll()
  v2GeneratePollTimer = setInterval(async () => {
    try {
      const res = await listTitleGenerateTasks({ status: '' })
      const taskList = res?.list || []
      const latestTask = taskList[0]
      if (!latestTask) return
      if (latestTask.status === 'completed') {
        stopV2GeneratePoll()
        message.success('标题生成完成，正在重新匹配...')
        await runMatchPreview()
        // 如果重新匹配后仍无数据，再次提示
        if (matchPreviewData.value.length === 0 && noMatchPromptTracks.value.length > 0) {
          noMatchPromptOpen.value = true
        }
      } else if (latestTask.status === 'failed') {
        stopV2GeneratePoll()
        message.error('标题生成失败: ' + (latestTask.progressMessage || '未知错误'))
      }
    } catch (e) {
      console.error('poll error:', e)
    }
  }, 5000)
}

function stopV2GeneratePoll() {
  if (v2GeneratePollTimer) {
    clearInterval(v2GeneratePollTimer)
    v2GeneratePollTimer = null
  }
}

// 批量通过选中
async function handleBatchApproveSelected() {
  if (matchSelectedKeys.value.length === 0) {
    message.warning('请先选择要通过的项')
    return
  }
  const selectedRecords = matchPreviewData.value.filter(m => matchSelectedKeys.value.includes(m.titleId))
  if (selectedRecords.length === 0) return
  matching.value = true
  try {
    const dateStr = matchForm.value.date ? matchForm.value.date.format('YYYY-MM-DD') : dayjs().format('YYYY-MM-DD')
    const matches = selectedRecords.map(m => ({
      titleId: m.titleId,
      userId: m.userId,
      editedTitle: m.editedTitle,
    }))
    const result = await matchConfirm(dateStr, matches)
    const saved = result.saved || 0
    message.success(`批量通过成功：${saved} 条已入库`)
    matchPreviewData.value = matchPreviewData.value.filter(m => !matchSelectedKeys.value.includes(m.titleId))
    matchSelectedKeys.value = []
    loadData()
  } catch (e) {
    message.error(e?.response?.data?.msg || e?.message || '批量通过失败')
  } finally {
    matching.value = false
  }
}

// 用户历史弹窗
const historyModalOpen = ref(false)
const historyLoading = ref(false)
const historyTitle = ref('')
const historyList = ref([])
const historyUserId = ref('')
const historyUserName = ref('')

async function openHistoryModal(userId, username, title) {
  historyUserId.value = userId
  historyUserName.value = username
  historyTitle.value = title
  historyList.value = []
  historyModalOpen.value = true
  historyLoading.value = true
  try {
    const data = await getUserHistory(userId)
    if (Array.isArray(data)) {
      if (title) {
        data.forEach(item => {
          const hTitle = item.titleName || ''
          item.similarity = Math.round(similarity(title, hTitle) * 100)
        })
        data.sort((a, b) => b.similarity - a.similarity)
      }
      historyList.value = data
    } else {
      historyList.value = []
    }
  } catch (e) {
    console.error('[history]', e)
    message.error('加载历史记录失败: ' + (e.message || '未知错误'))
  } finally {
    historyLoading.value = false
  }
}

// 字符 bigram 余弦相似度
function similarity(s1, s2) {
  if (!s1 || !s2) return 0
  s1 = s1.replace(/[\s\pP\pM\pZ\pC]/g, '').toLowerCase()
  s2 = s2.replace(/[\s\pP\pM\pZ\pC]/g, '').toLowerCase()
  if (!s1 || !s2) return 0
  const f1 = bigramFreq(s1)
  const f2 = bigramFreq(s2)
  const keys = new Set([...f1.keys(), ...f2.keys()])
  let dot = 0, n1 = 0, n2 = 0
  keys.forEach(k => {
    const a = f1.get(k) || 0, b = f2.get(k) || 0
    dot += a * b
    n1 += a * a
    n2 += b * b
  })
  if (!n1 || !n2) return 0
  return dot / (Math.sqrt(n1) * Math.sqrt(n2))
}

function bigramFreq(s) {
  const m = new Map()
  for (let i = 0; i < s.length - 1; i++) {
    const k = s.slice(i, i + 2)
    m.set(k, (m.get(k) || 0) + 1)
  }
  return m
}

const matchPreviewColumns = [
  { title: '标题', dataIndex: 'title', key: 'title', width: 200 },
  { title: '最高相似度', key: 'maxSimilarity', width: 100 },
  { title: '平台', dataIndex: 'platform', key: 'platform', width: 90 },
  { title: '赛道', dataIndex: 'trackName', key: 'trackName', width: 100 },
  { title: '匹配用户', key: 'username', width: 130 },
]

async function runMatchPreview() {
  matchPreviewData.value = []
  matchPreviewLoading.value = true
  matchSelectedKeys.value = []
  noMatchPromptTracks.value = []
  try {
    const dateStr = matchForm.value.date ? matchForm.value.date.format('YYYY-MM-DD') : dayjs().format('YYYY-MM-DD')
    const result = await matchPreview(dateStr)
    matchPreviewData.value = result.matches || []
    noMatchPromptTracks.value = result.needGenerateTracks || []

    // 加载同质化程度（相似度后端已计算并过滤）
    await loadMatchSimilarities()

    // 按用户名排序
    matchPreviewData.value.sort((a, b) => (a.username || '').localeCompare(b.username || ''))

    // 如果匹配为空但有需要生成的赛道，提示用户
    if (matchPreviewData.value.length === 0 && noMatchPromptTracks.value.length > 0) {
      noMatchPromptOpen.value = true
    }
  } catch (e) {
    message.error('加载匹配预览失败: ' + (e?.response?.data?.msg || e?.message || '未知错误'))
  } finally {
    matchPreviewLoading.value = false
  }
}

// 为匹配预览的每条记录补充同质化程度（相似度后端已返回）
async function loadMatchSimilarities() {
  if (matchPreviewData.value.length === 0) return

  // 按用户分组，只取每个用户的第一条记录去查历史（减少API调用）
  const userMap = new Map()
  for (const item of matchPreviewData.value) {
    if (item.userId && !userMap.has(item.userId)) {
      userMap.set(item.userId, item)
    }
  }

  const userIds = Array.from(userMap.keys())

  // 并行查询所有用户的历史和同质化程度
  const [userHistories, userHomogeneities] = await Promise.all([
    Promise.all(userIds.map(userId => getUserHistory(userId).catch(() => []))),
    Promise.all(userIds.map(userId => getUserHomogeneity(userId).catch(() => 0))),
  ])

  // 建立 userId -> 同质化程度的映射
  const homogeneityMap = new Map()
  userIds.forEach((userId, idx) => {
    const val = userHomogeneities[idx]
    homogeneityMap.set(userId, typeof val === 'number' ? val : 0)
  })

  // 为每条记录补充同质化程度（maxSimilarity 后端已返回，不再覆盖）
  for (const item of matchPreviewData.value) {
    item.homogeneity = homogeneityMap.get(item.userId) || 0
  }
}

async function openMatchModal() {
  openMatchDateModal()
}

async function handleRematchOne(record) {
  if (!record.userId) {
    message.error('用户ID为空，请刷新预览后再试')
    return
  }
  matchOneLoadingId.value = record.titleId
  try {
    const dateStr = matchForm.value.date ? matchForm.value.date.format('YYYY-MM-DD') : dayjs().format('YYYY-MM-DD')
    const result = await matchOne(dateStr, record.userId, record.titleId)
    // 从预览中移除旧标题对应的行，用新标题数据添加一行
    matchPreviewData.value = matchPreviewData.value.filter(m => m.titleId !== record.titleId)
    const newRecord = {
      ...record,
      titleId: result.titleId,
      title: result.title,
      editedTitle: result.title,
      trackId: result.trackId,
      platform: result.platform,
      trackName: result.trackName,
      maxSimilarity: null,
    }
    matchPreviewData.value.push(newRecord)
    matchPreviewData.value.sort((a, b) => (a.username || '').localeCompare(b.username || ''))
    message.success('已重新匹配标题')
  } catch (e) {
    message.error(e?.response?.data?.msg || e?.message || '重配失败')
  } finally {
    matchOneLoadingId.value = null
  }
}

function handleRemoveMatch(record) {
  matchPreviewData.value = matchPreviewData.value.filter(m => m.titleId !== record.titleId)
}

function handleApproveOne(record) {
  const dateStr = matchForm.value.date ? matchForm.value.date.format('YYYY-MM-DD') : dayjs().format('YYYY-MM-DD')
  matchConfirm(dateStr, [{
    titleId: record.titleId,
    userId: record.userId,
    editedTitle: record.editedTitle,
  }]).then(() => {
    message.success('已通过')
    matchPreviewData.value = matchPreviewData.value.filter(m => m.titleId !== record.titleId)
    loadData()
  }).catch(e => {
    message.error(e?.response?.data?.msg || e?.message || '保存失败')
  })
}

async function handleToggleAiFlavorHeavy(record) {
  try {
    const newHeavy = record.aiFlavorStatus !== 2 ? 2 : 0
    const titleId = record.titleId || record.id
    await markAiFlavorHeavy(titleId, newHeavy === 2)
    record.aiFlavorStatus = newHeavy
    message.success(newHeavy === 2 ? '已标记为AI味重' : '已取消AI味重标记')
  } catch (e) {
    message.error(e?.response?.data?.msg || e?.message || '操作失败')
  }
}

function handleTitleEdit(record, newTitle) {
  record.editedTitle = newTitle
}

async function handleMatchConfirm() {
  if (matchPreviewData.value.length === 0) {
    message.warning('没有待匹配的项')
    return
  }
  matching.value = true
  try {
    const dateStr = matchForm.value.date ? matchForm.value.date.format('YYYY-MM-DD') : dayjs().format('YYYY-MM-DD')
    const matches = matchPreviewData.value.map(m => ({
      titleId: m.titleId,
      userId: m.userId,
      editedTitle: m.editedTitle,
    }))
    const result = await matchConfirm(dateStr, matches)
    const saved = result.saved || 0
    message.success(`匹配成功：${saved} 条已入库`)
    matchModalOpen.value = false
    matchPreviewData.value = []
    loadData()
  } catch (e) {
    message.error(e?.response?.data?.msg || e?.message || '匹配失败')
  } finally {
    matching.value = false
  }
}

async function handleClearAndMatch() {
  matchClearing.value = true
  try {
    const dateStr = matchForm.value.date ? matchForm.value.date.format('YYYY-MM-DD') : dayjs().format('YYYY-MM-DD')
    const clearResult = await clearRecommendationsByDate(dateStr)
    message.success(`已清理 ${clearResult.deletedRecommendations || 0} 条推荐记录，${clearResult.clearedTitles || 0} 个标题已重置`)
    await runMatchPreview()
  } catch (e) {
    message.error('清理失败: ' + (e?.response?.data?.msg || e?.message || '未知错误'))
  } finally {
    matchClearing.value = false
  }
}

// Export rule modal
const exportRuleModalOpen = ref(false)
const exportRuleContent = ref('')
const exportRuleSaving = ref(false)
const exportRuleTemplateId = ref(null)

const defaultExportRuleContent = `以下是生成规则：
1、该sheet之前的每个sheet都是需要生成文章，在根目录下根据sheet名称创建文件夹，此文件夹作为该赛道的创作目录，根目录路径是/Users/panyong/aio_project/公众号/自媒体/;
2、创作规则：根据每个sheet里面的标题进行创作，并且你要根据你创作的内容生成一个120个字符以内的(包含标点符号)描述，描述要求符合SEO的原则，填写到"描述"列，"创作日期"列如果为空，就填写为为当天日期，如果不为空，就不用管，如果创作完成，"是否创作完成"填写是；
3、输出规则：输出的文件都是docx格式，并且根据每行数据的日期，在创作目录下创建一个输出目录，输出目录就是"创作日期"，文件输出到输出目录下，例如：/Users/panyong/aio_project/公众号/自媒体/职场/{date}；
4、输出文件样式：根据"样式风格"列的内容，去/Users/panyong/aio_project/小程序/services/admin-backend/styles目录下查找对应的样式文件，参考对应的样式输出文章；
5、文件内容图片生成规则：内容中要插入图片，你可以自行下载相关图片（下载一些和标题呼应的图片，可以多找一些资源，不要总是那么几张，下载的图片必须是16:9的）；
6、有思想、有创造性标题的生成原则：标题要有思想深度和创造性，能引发读者思考和共鸣，严禁做"标题党"，字数严禁超过30个字符；
7、对于"是否创作完成"填写"是"的数据，就不要再重复创作了，要创作"是否创作完成"为空或者"否"的数据；`

async function openExportRuleModal() {
  exportRuleModalOpen.value = true
  exportRuleSaving.value = false
  try {
    const res = await getDefaultPromptTemplate('export_rule')
    if (res && res.content) {
      exportRuleContent.value = res.content
      exportRuleTemplateId.value = res.id || null
    } else {
      exportRuleContent.value = defaultExportRuleContent
      exportRuleTemplateId.value = null
    }
  } catch (e) {
    exportRuleContent.value = defaultExportRuleContent
    exportRuleTemplateId.value = null
  }
}

async function handleSaveExportRule() {
  if (!exportRuleContent.value || !exportRuleContent.value.trim()) {
    message.warning('生成规则内容不能为空')
    return
  }
  exportRuleSaving.value = true
  try {
    const data = {
      id: exportRuleTemplateId.value,
      name: '导出Excel生成规则',
      content: exportRuleContent.value.trim(),
      type: 'export_rule',
      isDefault: 1,
    }
    const res = await savePromptTemplate(data)
    exportRuleTemplateId.value = res.id
    message.success('生成规则保存成功')
  } catch (e) {
    message.error('保存失败: ' + (e?.response?.data?.msg || e?.message || '未知错误'))
  } finally {
    exportRuleSaving.value = false
  }
}

function openExportFileNameModal(type) {
  pendingExportType.value = type
  exportFileNameInput.value = localStorage.getItem(EXPORT_FILENAME_KEY) || ''
  exportFileNameModalOpen.value = true
}

async function handleExportConfirm() {
  const fileName = exportFileNameInput.value.trim()
  if (fileName) {
    localStorage.setItem(EXPORT_FILENAME_KEY, fileName)
  }
  exportFileNameModalOpen.value = false

  if (pendingExportType.value === 'titleList') {
    await doExportTitleList()
  } else if (pendingExportType.value === 'fromRule') {
    await doExportFromRule()
  }
}

async function doExportTitleList() {
  try {
    let blob
    if (selectedRowKeys.value.length > 0) {
      blob = await exportTitleListBatch(selectedRowKeys.value)
    } else {
      const params = {}
      if (searchKeyword.value) params.keyword = searchKeyword.value.trim()
      if (searchPlatform.value) params.platform = searchPlatform.value
      if (searchTrack.value) params.trackId = searchTrack.value
      if (searchUserName.value) params.recommendUserName = searchUserName.value.trim()
      if (searchMatched.value !== '' && searchMatched.value !== undefined) params.matched = searchMatched.value
      if (searchPushDate.value) params.pushDate = searchPushDate.value.format('YYYY-MM-DD')
      if (searchIsUsed.value !== '' && searchIsUsed.value !== undefined) params.isUsed = searchIsUsed.value
      if (searchIsConfirmed.value !== '' && searchIsConfirmed.value !== undefined) params.isConfirmed = searchIsConfirmed.value
      if (searchAiFlavor.value !== '' && searchAiFlavor.value !== undefined) params.aiFlavor = searchAiFlavor.value
      blob = await exportTitleList(params)
    }
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    const defaultName = `标题库_${new Date().toISOString().slice(0,10).replace(/-/g,'')}.xlsx`
    const name = exportFileNameInput.value.trim() || defaultName
    a.download = name.endsWith('.xlsx') ? name : name + '.xlsx'
    a.click()
    URL.revokeObjectURL(url)
    message.success('导出成功')
  } catch (e) {
    message.error('导出失败')
  }
}

async function doExportFromRule() {
  exportRuleModalOpen.value = false
  try {
    const baseName = exportFileNameInput.value.trim() || `标题库导出_${new Date().toISOString().slice(0,10).replace(/-/g,'')}`
    let blob
    if (selectedRowKeys.value.length > 0) {
      blob = await exportTitleLibraryBatch(selectedRowKeys.value, baseName)
    } else {
      const params = { baseName }
      if (searchKeyword.value) params.keyword = searchKeyword.value.trim()
      if (searchPlatform.value) params.platform = searchPlatform.value
      if (searchTrack.value) params.trackId = searchTrack.value
      if (searchUserName.value) params.recommendUserName = searchUserName.value.trim()
      if (searchMatched.value !== '' && searchMatched.value !== undefined) params.matched = searchMatched.value
      if (searchPushDate.value) params.pushDate = searchPushDate.value.format('YYYY-MM-DD')
      if (searchIsUsed.value !== '' && searchIsUsed.value !== undefined) params.isUsed = searchIsUsed.value
      if (searchIsConfirmed.value !== '' && searchIsConfirmed.value !== undefined) params.isConfirmed = searchIsConfirmed.value
      if (searchAiFlavor.value !== '' && searchAiFlavor.value !== undefined) params.aiFlavor = searchAiFlavor.value
      blob = await exportTitleLibrary(params)
    }
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = baseName + '.zip'
    a.click()
    URL.revokeObjectURL(url)
    message.success('导出成功')
  } catch (e) {
    message.error('导出失败')
  }
}

async function handleExportTitleList() {
  openExportFileNameModal('titleList')
}

async function handleExportFromRule() {
  openExportFileNameModal('fromRule')
}

// Import articles modal
const importArticleModalOpen = ref(false)
const importArticleFiles = ref([])
const importArticleLoading = ref(false)
const importArticleResult = ref(null)
const importArticleMode = ref('folder') // 'folder' | 'file'
const importArticleDragActive = ref(false)
const importArticleFileInputRef = ref(null)
const importArticleFolderInputRef = ref(null)

function openImportArticleModal() {
  importArticleModalOpen.value = true
  importArticleFiles.value = []
  importArticleResult.value = null
  importArticleMode.value = 'folder'
  importArticleDragActive.value = false
}

function onImportArticleFileChange(e) {
  importArticleFiles.value = Array.from(e.target.files || [])
}

function triggerImportArticleInput() {
  if (importArticleMode.value === 'folder') {
    importArticleFolderInputRef.value?.click()
  } else {
    importArticleFileInputRef.value?.click()
  }
}

function onImportArticleDragOver(e) {
  e.preventDefault()
  importArticleDragActive.value = true
}

function onImportArticleDragLeave(e) {
  e.preventDefault()
  importArticleDragActive.value = false
}

function onImportArticleDrop(e) {
  e.preventDefault()
  importArticleDragActive.value = false
  const files = Array.from(e.dataTransfer.files || [])
  if (files.length === 0) return
  // 过滤仅保留 .doc / .docx
  importArticleFiles.value = files.filter(f => {
    const name = f.name.toLowerCase()
    return name.endsWith('.doc') || name.endsWith('.docx')
  })
  if (importArticleFiles.value.length === 0) {
    message.warning('未检测到 .doc / .docx 文件')
  }
}

async function handleImportArticles() {
  if (importArticleFiles.value.length === 0) {
    message.warning(importArticleMode.value === 'folder' ? '请选择文件夹' : '请选择文件')
    return
  }
  importArticleLoading.value = true
  try {
    const result = await importArticles(importArticleFiles.value)
    importArticleResult.value = result
    if (result.success > 0) {
      message.success(`导入完成：成功 ${result.success} 条，跳过 ${result.skip} 条`)
      loadData()
    } else {
      message.warning(`未成功导入任何文件，跳过 ${result.skip} 条`)
    }
  } catch (e) {
    message.error('导入失败: ' + (e?.response?.data?.msg || e?.message || '未知错误'))
  } finally {
    importArticleLoading.value = false
  }
}

// Prompt template modal
const promptModalOpen = ref(false)
const promptContent = ref('')
const promptSaving = ref(false)
const promptTemplateId = ref(null)

const defaultPromptContent = `请根据以下标题和描述，生成一篇完整的公众号风格文章。

标题：{title}
描述：{description}

{styleDesc}

{styleRef}
要求：
1. 文章必须围绕标题主题展开，内容充实、有深度、有观点
2. 文章结构清晰，包含开头引入、正文论述、结尾总结
3. 适合公众号传播，语言自然流畅，有阅读吸引力
4. 文章长度适中，约800-1500字
5. 输出纯HTML正文内容（不含html/head/body标签，只返回div包裹的内容），使用h1/h2/p/blockquote等标签组织内容
6. 不要在任何标签上添加 style 属性或 class 属性，保持标签纯净
7. 只输出纯JSON，不要markdown代码块，不要任何额外文字，不要在JSON前添加任何说明

格式：{"content":"<div>文章HTML内容</div>"}`

async function openPromptModal() {
  if (selectedRowKeys.value.length === 0) {
    message.warning('请先选择要生成文章的标题')
    return
  }
  promptModalOpen.value = true
  promptSaving.value = false
  try {
    const res = await getDefaultPromptTemplate('generate_post')
    if (res && res.content) {
      promptContent.value = res.content
      promptTemplateId.value = res.id || null
    } else {
      promptContent.value = defaultPromptContent
      promptTemplateId.value = null
    }
  } catch (e) {
    promptContent.value = defaultPromptContent
    promptTemplateId.value = null
  }
}

async function handleSavePrompt() {
  if (!promptContent.value || !promptContent.value.trim()) {
    message.warning('提示词内容不能为空')
    return
  }
  promptSaving.value = true
  try {
    const data = {
      id: promptTemplateId.value,
      name: '生成文章默认提示词',
      content: promptContent.value.trim(),
      type: 'generate_post',
      isDefault: 1,
    }
    const res = await savePromptTemplate(data)
    promptTemplateId.value = res.id
    message.success('提示词保存成功')
  } catch (e) {
    message.error('保存失败: ' + (e?.response?.data?.msg || e?.message || '未知错误'))
  } finally {
    promptSaving.value = false
  }
}

function handleGenerateFromPrompt() {
  promptModalOpen.value = false
  doGeneratePosts()
}

// 复制提示词弹框
const copyPromptModalOpen = ref(false)
const copyPromptContent = ref('')
const copyPromptDate = ref(dayjs().format('YYYY-MM-DD'))
const copyPromptPno = ref('1')
const copyPromptSno = ref('1')
const copyPromptCurrentId = ref(null)
const copyPromptRecords = ref([])

function loadCopyPromptRecords() {
  try {
    const raw = localStorage.getItem('copyPrompt_records')
    if (raw) {
      copyPromptRecords.value = JSON.parse(raw)
    } else {
      // 兼容旧数据：如果存在旧的单条记录，自动迁移为第一条记录
      const oldContent = localStorage.getItem('copyPrompt_content') || ''
      const oldDate = localStorage.getItem('copyPrompt_date') || dayjs().format('YYYY-MM-DD')
      const oldPno = localStorage.getItem('copyPrompt_pno') || '1'
      const oldSno = localStorage.getItem('copyPrompt_sno') || '1'
      if (oldContent) {
        const record = {
          id: Date.now().toString(),
          name: '记录 1',
          content: oldContent,
          date: oldDate,
          pno: oldPno,
          sno: oldSno,
          createdAt: new Date().toISOString()
        }
        copyPromptRecords.value = [record]
        copyPromptCurrentId.value = record.id
        persistCopyPromptRecords()
      } else {
        copyPromptRecords.value = []
      }
    }
  } catch (e) {
    copyPromptRecords.value = []
  }
}

function persistCopyPromptRecords() {
  localStorage.setItem('copyPrompt_records', JSON.stringify(copyPromptRecords.value))
}

function openCopyPromptModal() {
  loadCopyPromptRecords()
  // 如果有当前记录ID，加载它；否则加载最后一条记录；否则清空
  if (copyPromptCurrentId.value) {
    const rec = copyPromptRecords.value.find(r => r.id === copyPromptCurrentId.value)
    if (rec) {
      copyPromptContent.value = rec.content
      copyPromptDate.value = rec.date
      copyPromptPno.value = rec.pno
      copyPromptSno.value = rec.sno
    } else {
      copyPromptCurrentId.value = null
    }
  }
  if (!copyPromptCurrentId.value && copyPromptRecords.value.length > 0) {
    const last = copyPromptRecords.value[copyPromptRecords.value.length - 1]
    copyPromptCurrentId.value = last.id
    copyPromptContent.value = last.content
    copyPromptDate.value = last.date
    copyPromptPno.value = last.pno
    copyPromptSno.value = last.sno
  }
  if (!copyPromptCurrentId.value) {
    copyPromptContent.value = ''
    copyPromptDate.value = dayjs().format('YYYY-MM-DD')
    copyPromptPno.value = '1'
    copyPromptSno.value = '1'
  }
  copyPromptModalOpen.value = true
}

function saveCopyPrompt() {
  if (!copyPromptContent.value.trim()) {
    message.warning('提示词内容不能为空')
    return
  }
  const payload = {
    content: copyPromptContent.value,
    date: copyPromptDate.value,
    pno: copyPromptPno.value,
    sno: copyPromptSno.value
  }
  if (copyPromptCurrentId.value) {
    const idx = copyPromptRecords.value.findIndex(r => r.id === copyPromptCurrentId.value)
    if (idx >= 0) {
      copyPromptRecords.value[idx] = { ...copyPromptRecords.value[idx], ...payload }
      persistCopyPromptRecords()
      message.success('已保存')
      return
    }
  }
  // 没有当前记录则新增
  const newRecord = {
    id: Date.now().toString(),
    name: '记录 ' + (copyPromptRecords.value.length + 1),
    ...payload,
    createdAt: new Date().toISOString()
  }
  copyPromptRecords.value.push(newRecord)
  copyPromptCurrentId.value = newRecord.id
  persistCopyPromptRecords()
  message.success('已保存')
}

function saveCopyPromptAsNew() {
  if (!copyPromptContent.value.trim()) {
    message.warning('提示词内容不能为空')
    return
  }
  const newRecord = {
    id: Date.now().toString(),
    name: '记录 ' + (copyPromptRecords.value.length + 1),
    content: copyPromptContent.value,
    date: copyPromptDate.value,
    pno: copyPromptPno.value,
    sno: copyPromptSno.value,
    createdAt: new Date().toISOString()
  }
  copyPromptRecords.value.push(newRecord)
  copyPromptCurrentId.value = newRecord.id
  persistCopyPromptRecords()
  message.success('另存成功')
}

function loadCopyPromptRecord(record) {
  copyPromptCurrentId.value = record.id
  copyPromptContent.value = record.content
  copyPromptDate.value = record.date
  copyPromptPno.value = record.pno
  copyPromptSno.value = record.sno
}

function deleteCopyPromptRecord(record, event) {
  event.stopPropagation()
  copyPromptRecords.value = copyPromptRecords.value.filter(r => r.id !== record.id)
  if (copyPromptCurrentId.value === record.id) {
    copyPromptCurrentId.value = null
  }
  persistCopyPromptRecords()
  message.success('已删除')
}

function incrementWithPadding(str) {
  const trimmed = String(str || '').trim()
  if (!trimmed) return '1'
  const num = parseInt(trimmed, 10) || 0
  const next = num + 1
  // 如果原字符串有前导零且长度大于结果的字符串长度，则补齐前导零
  if (trimmed.startsWith('0') && trimmed.length > String(next).length) {
    return String(next).padStart(trimmed.length, '0')
  }
  return String(next)
}

function doCopyPrompt(incrementField) {
  let pno = copyPromptPno.value || '1'
  let sno = copyPromptSno.value || '1'

  if (incrementField === 'pno') {
    pno = incrementWithPadding(pno)
    copyPromptPno.value = pno
  } else if (incrementField === 'sno') {
    sno = incrementWithPadding(sno)
    copyPromptSno.value = sno
  }

  // 保存当前值
  saveCopyPrompt()

  // 替换变量
  let result = copyPromptContent.value || ''
  result = result.replace(/\$\{date\}/g, copyPromptDate.value || '')
  result = result.replace(/\$\{pno\}/g, pno)
  result = result.replace(/\$\{sno\}/g, sno)

  navigator.clipboard.writeText(result).then(() => {
    message.success('已复制到剪贴板')
  }).catch(() => {
    message.error('复制失败')
  })
}

// 行级提示词模板（保存到 tu_prompt_template 表）
const rowPromptTemplate = ref('')
const rowPromptModalOpen = ref(false)
const rowPromptTextAreaRef = ref(null)
const rowPromptType = 'row_prompt'

const availableFields = [
  { key: 'title', label: '标题' },
  { key: 'description', label: '描述' },
  { key: 'platform', label: '平台' },
  { key: 'trackName', label: '赛道名称' },
  { key: 'trackId', label: '赛道ID' },
  { key: 'useCount', label: '使用次数' },
  { key: 'isUsed', label: '是否使用' },
  { key: 'pushDate', label: '推送日期' },
  { key: 'recommendUserName', label: '关联用户' },
  { key: 'recommendUserTemplate', label: '用户模板' },
  { key: 'recommendDate', label: '推荐日期' },
  { key: 'generatedFileName', label: '文章文件名' },
  { key: 'generatedFileUrl', label: '文章链接' },
  { key: 'id', label: 'ID' },
]

function openRowPromptModal() {
  rowPromptModalOpen.value = true
  getDefaultPromptTemplate(rowPromptType).then(res => {
    rowPromptTemplate.value = res?.content || ''
  }).catch(() => {
    rowPromptTemplate.value = ''
  })
}

function saveRowPromptTemplate() {
  savePromptTemplate({
    name: '行级提示词模板',
    content: rowPromptTemplate.value,
    type: rowPromptType,
    isDefault: 1
  }).then(() => {
    message.success('模板已保存')
  }).catch(() => {
    message.error('保存失败')
  })
}

function insertFieldToTemplate(fieldKey) {
  const textarea = rowPromptTextAreaRef.value?.$el?.querySelector('textarea')
  const template = rowPromptTemplate.value || ''
  const placeholder = '${' + fieldKey + '}'
  if (textarea) {
    const start = textarea.selectionStart || template.length
    const end = textarea.selectionEnd || template.length
    rowPromptTemplate.value = template.slice(0, start) + placeholder + template.slice(end)
    nextTick(() => {
      textarea.focus()
      const newPos = start + placeholder.length
      textarea.setSelectionRange(newPos, newPos)
    })
  } else {
    rowPromptTemplate.value = template + placeholder
  }
}

async function copyRowPrompt(record) {
  const template = rowPromptTemplate.value || ''
  if (!template.trim()) {
    message.warning('请先设置提示词模板')
    openRowPromptModal()
    return
  }
  let result = template

  // 1. 先注入样式提示词变量
  let stylePrompt = globalDefaultStylePrompt.value || ''
  // 优先使用当前行数据的关联用户样式，其次用手动选择的目标用户
  const relatedUserId = record.recommendUserId
  if (relatedUserId) {
    const uid = String(relatedUserId)
    const user = allUsers.value.find(u => String(u.id) === uid)
    if (user && user.styleConfig) {
      stylePrompt = user.styleConfig
    } else if (user && !user.styleConfig) {
      message.info(`用户 ${user.username} 未配置文章样式，使用全局默认样式`)
    }
  }
  // 使用 split/join 避免 $ 特殊字符问题
  result = result.split('${stylePrompt}').join(stylePrompt)
  if (template.includes('${stylePrompt}') && !stylePrompt) {
    message.info('当前样式配置为空，${stylePrompt} 已被替换为空字符串')
  }

  // 2. 再注入字段变量
  for (const f of availableFields) {
    const val = record[f.key]
    const replacement = val !== undefined && val !== null ? String(val) : ''
    result = result.split('${' + f.key + '}').join(replacement)
  }

  // 复制到剪贴板：优先用 clipboard API，失败再用 fallback
  let copied = false
  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(result)
      copied = true
    }
  } catch {
    copied = false
  }
  if (!copied) {
    try {
      const textarea = document.createElement('textarea')
      textarea.value = result
      textarea.style.position = 'fixed'
      textarea.style.left = '-9999px'
      document.body.appendChild(textarea)
      textarea.select()
      copied = document.execCommand('copy')
      document.body.removeChild(textarea)
    } catch {
      copied = false
    }
  }
  if (copied) {
    record.isCopied = 1
    message.success('已复制提示词')
    batchCopied([record.id]).catch(() => {
      // 后端标记失败不影响前端体验
    })
  } else {
    message.error('复制失败，请手动复制')
  }
}

// 自动插入图片弹框
const AUTO_INSERT_IMAGES_KEY = 'autoInsertImages'
const autoInsertImagesModalOpen = ref(false)
const autoInsertImagesForm = ref({
  fileDir: localStorage.getItem(AUTO_INSERT_IMAGES_KEY + '_fileDir') || '',
  imageLibDir: localStorage.getItem(AUTO_INSERT_IMAGES_KEY + '_imageLibDir') || '',
  count: parseInt(localStorage.getItem(AUTO_INSERT_IMAGES_KEY + '_count') || '3'),
})
const autoInsertImagesLoading = ref(false)

function openAutoInsertImagesModal() {
  autoInsertImagesModalOpen.value = true
}

function saveAutoInsertImagesParams() {
  localStorage.setItem(AUTO_INSERT_IMAGES_KEY + '_fileDir', autoInsertImagesForm.value.fileDir)
  localStorage.setItem(AUTO_INSERT_IMAGES_KEY + '_imageLibDir', autoInsertImagesForm.value.imageLibDir)
  localStorage.setItem(AUTO_INSERT_IMAGES_KEY + '_count', String(autoInsertImagesForm.value.count))
  message.success('参数已保存')
}

async function handleAutoInsertImages() {
  if (!autoInsertImagesForm.value.fileDir.trim()) {
    message.warning('请填写文件目录')
    return
  }
  if (!autoInsertImagesForm.value.imageLibDir.trim()) {
    message.warning('请填写图片库目录')
    return
  }
  autoInsertImagesLoading.value = true
  try {
    const res = await autoInsertImages({
      fileDir: autoInsertImagesForm.value.fileDir.trim(),
      imageLibDir: autoInsertImagesForm.value.imageLibDir.trim(),
      count: autoInsertImagesForm.value.count,
    })
    if (res && res.exitCode === 0) {
      message.success('插入图片成功')
    } else {
      message.warning('执行完成但可能有异常：' + (res?.stdout || '未知'))
    }
  } catch (e) {
    message.error('调用失败: ' + (e?.response?.data?.msg || e?.message || '未知错误'))
  } finally {
    autoInsertImagesLoading.value = false
  }
}

// 去除AI味弹框
const REMOVE_AI_FLAVOR_PATH_KEY = 'removeAiFlavor_path'
const removeAiFlavorModalOpen = ref(false)
const removeAiFlavorPath = ref(localStorage.getItem(REMOVE_AI_FLAVOR_PATH_KEY) || '')
const removeAiFlavorLoading = ref(false)
const removeAiFlavorRules = ref([
  { from: '', to: '' }
])
const aiFlavorRulesLibrary = ref([])
const removeAiFlavorSelectModalOpen = ref(false)
const selectedAiFlavorRules = ref([])

function openRemoveAiFlavorModal() {
  removeAiFlavorModalOpen.value = true
  loadAiFlavorRulesLibrary()
}

async function loadAiFlavorRulesLibrary() {
  try {
    const result = await listAiFlavorRules()
    aiFlavorRulesLibrary.value = result || []
  } catch (e) {
    console.error('加载规则库失败', e)
  }
}

function saveRemoveAiFlavorPath() {
  localStorage.setItem(REMOVE_AI_FLAVOR_PATH_KEY, removeAiFlavorPath.value)
  message.success('路径已保存')
}

function addRemoveAiFlavorRule() {
  removeAiFlavorRules.value.push({ from: '', to: '' })
}

function removeRemoveAiFlavorRule(index) {
  if (removeAiFlavorRules.value.length > 1) {
    removeAiFlavorRules.value.splice(index, 1)
  }
}

function openRemoveAiFlavorSelectModal() {
  selectedAiFlavorRules.value = []
  removeAiFlavorSelectModalOpen.value = true
}

function confirmImportAiFlavorRules() {
  for (const rule of selectedAiFlavorRules.value) {
    const existIndex = removeAiFlavorRules.value.findIndex(r => r.from === rule.ruleFrom && r.to === rule.ruleTo)
    if (existIndex < 0) {
      removeAiFlavorRules.value.push({ from: rule.ruleFrom, to: rule.ruleTo })
    }
  }
  removeAiFlavorSelectModalOpen.value = false
  if (selectedAiFlavorRules.value.length > 0) {
    message.success('已添加 ' + selectedAiFlavorRules.value.length + ' 条规则')
  }
}

async function handleRemoveAiFlavor() {
  if (!removeAiFlavorPath.value.trim()) {
    message.warning('请先填写绝对路径')
    return
  }
  removeAiFlavorLoading.value = true
  try {
    // 过滤掉空的规则对
    const validRules = removeAiFlavorRules.value
      .filter(r => r.from.trim())
      .map(r => [r.from.trim(), r.to.trim()])
    const params = {
      path: removeAiFlavorPath.value.trim(),
      rules: validRules
    }
    const res = await removeAiFlavor(params)
    if (res && res.exitCode === 0) {
      message.success('去除AI味成功')
    } else {
      message.warning('执行完成但可能有异常：' + (res?.stderr || '未知'))
    }
  } catch (e) {
    message.error('调用失败: ' + (e?.response?.data?.msg || e?.message || '未知错误'))
  } finally {
    removeAiFlavorLoading.value = false
  }
}

// Generate posts for today
const generatingPost = ref(false)
const generatePostTaskId = ref(null)
const generatePostProgress = ref(0)
const generatePostStatusMsg = ref('')
let generatePostPollTimer = null

function stopGeneratePostPoll() {
  if (generatePostPollTimer) {
    clearInterval(generatePostPollTimer)
    generatePostPollTimer = null
  }
}

async function pollGeneratePostStatus(taskId) {
  try {
    const status = await getGeneratePostStatus(taskId)
    if (!status || typeof status !== 'object') {
      console.error('poll post error: status is not an object', status)
      return
    }
    generatePostProgress.value = status.progress || 0
    generatePostStatusMsg.value = status.message || ''
    if (status.status === 'completed') {
      stopGeneratePostPoll()
      generatingPost.value = false
      generatePostTaskId.value = null
      message.success(status.message || '文章生成完成')
      loadData()
    } else if (status.status === 'failed') {
      stopGeneratePostPoll()
      generatingPost.value = false
      generatePostTaskId.value = null
      message.error('生成失败: ' + (status.message || '未知错误'))
    }
  } catch (e) {
    console.error('poll post error:', e)
  }
}

async function doGeneratePosts() {
  generatingPost.value = true
  generatePostProgress.value = 0
  generatePostStatusMsg.value = '提交中...'
  try {
    const result = await generatePostsForToday(selectedRowKeys.value)
    generatePostTaskId.value = result.taskId
    generatePostStatusMsg.value = '任务已提交，开始生成文章...'
    generatePostPollTimer = setInterval(() => {
      if (generatePostTaskId.value) {
        pollGeneratePostStatus(generatePostTaskId.value)
      }
    }, 3000)
  } catch (e) {
    generatingPost.value = false
    message.error('提交失败: ' + (e?.response?.data?.msg || e?.message || '未知错误'))
  }
}

async function handleCancelGeneratePost() {
  if (!generatePostTaskId.value) {
    generatingPost.value = false
    stopGeneratePostPoll()
    return
  }
  try {
    await cancelGeneratePost(generatePostTaskId.value)
    stopGeneratePostPoll()
    generatingPost.value = false
    generatePostTaskId.value = null
    generatePostProgress.value = 0
    generatePostStatusMsg.value = ''
    message.info('已取消生成')
  } catch (e) {
    stopGeneratePostPoll()
    generatingPost.value = false
    generatePostTaskId.value = null
    message.info('已取消生成')
  }
}

// Preview / Download post
const previewModalOpen = ref(false)
const previewRecord = ref(null)
const previewHtmlContent = ref('')
const previewLoading = ref(false)
const docxContainerRef = ref(null)

// 贴图
const imagePostLoading = ref(false)
const imagePostUrls = ref([])
const imagePostModalOpen = ref(false)
const currentImagePostTitleId = ref('')
const imagePreviewOpen = ref(false)
const previewImageUrl = ref('')
const batchImagePostLoading = ref(false)

const previewTimestamp = ref(Date.now())

const previewFileType = computed(() => {
  const url = previewRecord.value?.generatedFileUrl || ''
  const name = url.split('/').pop() || ''
  if (name.endsWith('.pdf')) return 'pdf'
  if (name.endsWith('.txt') || name.endsWith('.md')) return 'text'
  if (name.endsWith('.docx')) return 'docx'
  if (name.endsWith('.doc')) return 'doc'
  return 'other'
})

function getPostFileUrl(postId, download = false) {
  if (!postId) return ''
  return `${apiBaseUrl}/api/subscription-posts/${postId}/file${download ? '?download=1' : ''}`
}

async function handlePreviewPost(record) {
  previewRecord.value = record
  previewHtmlContent.value = ''
  previewTimestamp.value = Date.now()
  previewModalOpen.value = true
  previewLoading.value = true
  clearHighlight(docxContainerRef.value)

  const fileUrl = record.generatedFileUrl
  if (!fileUrl) {
    previewLoading.value = false
    return
  }

  try {
    const cacheBustUrl = fileUrl + (fileUrl.includes('?') ? '&' : '?') + '_t=' + Date.now()
    const type = previewFileType.value
    if (type === 'text') {
      const res = await fetch(cacheBustUrl)
      previewHtmlContent.value = await res.text()
    } else if (type === 'docx') {
      const res = await fetch(cacheBustUrl)
      const blob = await res.blob()
      if (blob.size === 0) {
        throw new Error('文件内容为空')
      }
      previewLoading.value = false
      await nextTick()
      if (docxContainerRef.value) {
        docxContainerRef.value.innerHTML = ''
        await nextTick()
        try {
          await renderAsync(blob, docxContainerRef.value, null, {
            className: 'docx-preview',
            inWrapper: false,
          })
          // 注入自定义样式：去除删除线内容，保留颜色和字体
          const styleEl = document.createElement('style')
          styleEl.textContent = `
            .docx-preview, .docx-preview * {
              font-family: 'Microsoft YaHei', '微软雅黑', sans-serif !important;
            }
            .docx-preview del,
            .docx-preview s,
            .docx-preview strike {
              display: none !important;
            }
            .docx-preview [style*="line-through"] {
              display: none !important;
            }
          `
          docxContainerRef.value.appendChild(styleEl)
          // 违禁词/敏感词高亮
          const checkResult = previewRecord.value?.bannedWordCheckResult
          if (checkResult) {
            let parsed = checkResult
            if (typeof checkResult === 'string') {
              try { parsed = JSON.parse(checkResult) } catch (e) { parsed = null }
            }
            if (parsed && parsed.matches) {
              applyHighlight(docxContainerRef.value, parsed.matches, parsed.totalChars)
            }
          }
        } catch (renderErr) {
          console.error('docx render error:', renderErr)
          docxContainerRef.value.innerHTML = '<div style="color:#999;text-align:center;padding:40px;">文件解析失败，该文件可能不是有效的 docx 格式</div>'
        }
      }
      return
    } else {
      previewHtmlContent.value = ''
    }
  } catch (e) {
    message.error('文件预览失败：' + (e.message || '未知错误'))
    previewHtmlContent.value = ''
  } finally {
    previewLoading.value = false
  }
}

async function copyArticleContent() {
  let text = ''
  if (previewHtmlContent.value) {
    text = previewHtmlContent.value
  } else if (docxContainerRef.value) {
    text = docxContainerRef.value.innerText || ''
  }
  if (!text || !text.trim()) {
    message.warning('暂无可复制的内容')
    return
  }
  try {
    await navigator.clipboard.writeText(text)
    message.success('文章内容已复制')
  } catch (e) {
    const textarea = document.createElement('textarea')
    textarea.value = text
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    message.success('文章内容已复制')
  }
}

function handleDownloadPost(record) {
  const postId = record.id
  if (!postId) return
  const url = getPostFileUrl(postId, true)
  // 从原始路径中提取文件扩展名
  const urlPath = record.generatedFileUrl || ''
  const extMatch = urlPath.split('?')[0].match(/\.([a-zA-Z0-9]+)$/)
  const ext = extMatch ? '.' + extMatch[1] : ''
  const fileName = (record.title || 'download') + ext
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  a.click()
}

async function handleGenerateImagePost(record) {
  if (!record || !record.id) return
  Modal.confirm({
    title: '生成贴图',
    content: '确认生成该文章的贴图？生成后可在邮件推送时一并发送。',
    async onOk() {
      imagePostLoading.value = true
      try {
        const images = await generateImagePost(record.id)
        message.success(`贴图生成成功，共 ${images.length} 张`)
        imagePostUrls.value = images || []
        currentImagePostTitleId.value = record.id
        imagePostModalOpen.value = true
      } catch (e) {
        message.error('贴图生成失败: ' + (e?.response?.data?.msg || e?.message || '未知错误'))
      } finally {
        imagePostLoading.value = false
      }
    },
  })
}

async function openImagePostModal(record) {
  if (!record || !record.id) return
  currentImagePostTitleId.value = record.id
  imagePostUrls.value = []
  imagePostModalOpen.value = true
  imagePostLoading.value = true
  try {
    const images = await getImagePosts(record.id)
    imagePostUrls.value = images || []
  } catch (e) {
    message.error('加载贴图失败')
  } finally {
    imagePostLoading.value = false
  }
}

function handleDownloadImage(url) {
  if (!url) return
  const link = document.createElement('a')
  link.href = url + (url.includes('?') ? '&' : '?') + '_t=' + Date.now() + '&download=1'
  link.download = url.split('/').pop() || 'image.png'
  link.click()
}

function handlePreviewImage(url) {
  if (!url) return
  previewImageUrl.value = url
  imagePreviewOpen.value = true
}

async function handleBatchGenerateImagePost() {
  if (matchSelectedKeys.value.length === 0) {
    message.warning('请先选择要生成贴图的标题')
    return
  }
  Modal.confirm({
    title: '批量生成贴图',
    content: `确认对选中的 ${matchSelectedKeys.value.length} 条标题批量生成贴图？`,
    async onOk() {
      batchImagePostLoading.value = true
      try {
        const res = await batchGenerateImagePost(matchSelectedKeys.value)
        message.success(`批量生成完成：成功 ${res.success} 条，失败 ${res.failed} 条`)
        if (res.errors && res.errors.length > 0) {
          console.warn('批量生成贴图失败明细:', res.errors)
        }
      } catch (e) {
        message.error('批量生成贴图失败: ' + (e?.response?.data?.msg || e?.message || '未知错误'))
      } finally {
        batchImagePostLoading.value = false
      }
    },
  })
}

// User detail modal
const userModalOpen = ref(false)
const selectedUserRecord = ref(null)

function handleViewUser(record) {
  if (record.recommendUserName) {
    router.push({ path: '/users', query: { keyword: record.recommendUserName } })
  }
}

// Import
const importModalOpen = ref(false)
const importExcelFile = ref(null)
const importLoading = ref(false)

function openImportModal() {
  importModalOpen.value = true
  importExcelFile.value = null
}

function onImportFileChange(e) {
  importExcelFile.value = e.target.files?.[0] || null
}

function downloadTemplate() {
  const headers = ['标题', '平台', '赛道名称']
  const csvContent = headers.join(',') + '\n示例标题,公众号,情感故事'
  const blob = new Blob(['\uFEFF' + csvContent], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = '标题导入模板.csv'
  a.click()
  URL.revokeObjectURL(url)
}

async function handleImport() {
  if (!importExcelFile.value) {
    message.warning('请选择 Excel 文件')
    return
  }
  importLoading.value = true
  try {
    const result = await importTitles(importExcelFile.value)
    const created = result.created || 0
    const updated = result.updated || 0
    message.success(`导入完成：新增 ${created} 条，更新 ${updated} 条，跳过 ${result.skip} 条`)
    if (result.errors && result.errors.length) {
      console.warn('导入错误：', result.errors)
    }
    importModalOpen.value = false
    loadData()
  } catch (e) {
    message.error('导入失败')
  } finally {
    importLoading.value = false
  }
}

// Generate titles
const GENERATE_CONFIG_KEY = 'titleLibrary_generateConfig'
const savedGenerateConfig = JSON.parse(localStorage.getItem(GENERATE_CONFIG_KEY) || 'null')

const generateModalOpen = ref(false)
const generateCount = ref(savedGenerateConfig?.count || 3)
const generateOutputPath = ref(savedGenerateConfig?.outputPath || '')
const generatePlatforms = ref(savedGenerateConfig?.platforms || [])
const generateTrackIds = ref(savedGenerateConfig?.trackIds || [])
const generateInstruction = ref(savedGenerateConfig?.instruction || '')
const generating = ref(false)
const generateProgress = ref(0)
const generateTaskId = ref(null)
const generateStatusMsg = ref('')
let generatePollTimer = null

function saveGenerateConfig() {
  localStorage.setItem(GENERATE_CONFIG_KEY, JSON.stringify({
    count: generateCount.value,
    outputPath: generateOutputPath.value,
    platforms: generatePlatforms.value,
    trackIds: generateTrackIds.value,
    instruction: generateInstruction.value,
  }))
}

// Export file name modal
const EXPORT_FILENAME_KEY = 'titleLibrary_exportFileName'
const exportFileNameModalOpen = ref(false)
const exportFileNameInput = ref(localStorage.getItem(EXPORT_FILENAME_KEY) || '')
const pendingExportType = ref(null) // 'titleList' | 'fromRule'

const filteredTracksForGenerate = computed(() => {
  if (!generatePlatforms.value || generatePlatforms.value.length === 0) {
    return tracks.value
  }
  return tracks.value.filter(t => {
    if (!t.platforms) return false
    const trackPlatforms = t.platforms.split(',')
    return generatePlatforms.value.some(p => trackPlatforms.includes(p))
  })
})

function openGenerateModal() {
  generateModalOpen.value = true
}

function onPlatformChange() {
  if (!generatePlatforms.value || generatePlatforms.value.length === 0) {
    return
  }
  // Remove selected tracks that don't support any of the selected platforms
  generateTrackIds.value = generateTrackIds.value.filter(trackId => {
    const track = tracks.value.find(t => t.id === trackId)
    if (!track || !track.platforms) return false
    const trackPlatforms = track.platforms.split(',')
    return generatePlatforms.value.some(p => trackPlatforms.includes(p))
  })
}

function stopGeneratePoll() {
  if (generatePollTimer) {
    clearInterval(generatePollTimer)
    generatePollTimer = null
  }
}

async function pollGenerateStatus(taskId) {
  try {
    const status = await getGenerateStatus(taskId)
    if (!status || typeof status !== 'object') {
      console.error('poll error: status is not an object', status)
      return
    }
    generateProgress.value = status.progress || 0
    generateStatusMsg.value = status.message || ''
    if (status.status === 'completed') {
      stopGeneratePoll()
      generating.value = false
      generateTaskId.value = null
      message.success(`生成完成：共 ${status.total} 条标题，已保存到 ${status.path}`)
    } else if (status.status === 'failed') {
      stopGeneratePoll()
      generating.value = false
      generateTaskId.value = null
      message.error('生成失败: ' + (status.message || '未知错误'))
    }
  } catch (e) {
    console.error('poll error:', e)
  }
}

async function handleGenerate() {
  if (!generateCount.value || generateCount.value < 1) {
    message.warning('请输入有效的生成数量')
    return
  }
  generating.value = true
  generateProgress.value = 0
  generateStatusMsg.value = '提交中...'
  try {
    const result = await generateTitles({
      countPerCombo: generateCount.value,
      outputPath: generateOutputPath.value.trim(),
      platforms: generatePlatforms.value,
      trackIds: generateTrackIds.value,
      instruction: generateInstruction.value.trim(),
    })
    generateTaskId.value = result.taskId
    generateModalOpen.value = false
    saveGenerateConfig()
    generateStatusMsg.value = '任务已提交，开始生成...'
    // Start polling every 3 seconds
    generatePollTimer = setInterval(() => {
      if (generateTaskId.value) {
        pollGenerateStatus(generateTaskId.value)
      }
    }, 3000)
  } catch (e) {
    generating.value = false
    message.error('提交失败: ' + (e?.response?.data?.msg || e?.message || '未知错误'))
  }
}

async function handleCancelGenerate() {
  if (!generateTaskId.value) {
    generating.value = false
    stopGeneratePoll()
    return
  }
  try {
    await cancelGenerate(generateTaskId.value)
    stopGeneratePoll()
    generating.value = false
    generateTaskId.value = null
    generateProgress.value = 0
    generateStatusMsg.value = ''
    message.info('已取消生成')
  } catch (e) {
    // Even if backend call fails, stop local polling
    stopGeneratePoll()
    generating.value = false
    generateTaskId.value = null
    message.info('已取消生成')
  }
}

onMounted(() => {
  // 如果 URL 带有 keyword 查询参数，自动设置搜索条件
  if (route.query.keyword) {
    searchKeyword.value = route.query.keyword
    currentPage.value = 1
  }
  // 如果 URL 带有 recommendDate 查询参数，自动设置推荐日期筛选
  if (route.query.recommendDate) {
    searchPushDate.value = dayjs(route.query.recommendDate)
    currentPage.value = 1
  }
  loadData()
  loadPanelData()
})

</script>

<template>
  <Alert
    v-if="generatingPost"
    type="success"
    show-icon
    style="margin-bottom: 16px;"
  >
    <template #message>
      <Space style="width: 100%; justify-content: space-between;">
        <span><strong>正在生成文章</strong> — {{ generatePostStatusMsg }}</span>
        <Button type="link" danger size="small" @click="handleCancelGeneratePost">取消生成</Button>
      </Space>
    </template>
    <template #description>
      <Progress :percent="generatePostProgress" size="small" status="active" />
    </template>
  </Alert>

  <Tabs v-model:activeKey="activeTab" @change="() => { currentPage = 1; saveActiveTab(); loadData(); }">
    <template #rightExtra>
      <Space>
        <Button size="small" @click="router.push('/task-list')">生成文章任务</Button>
        <Button size="small" @click="router.push('/article-review')">文章审核</Button>
      </Space>
    </template>
    <Tabs.TabPane v-for="t in mainTabs" :key="t.key" :tab="t.label">
      <div style="display: flex; gap: 16px;">
        <!-- 左侧主内容 -->
        <div style="width: 100%; min-width: 0;">
          <Card :title="'标题匹配 — ' + t.label" :bordered="false">
            <!-- 搜索筛选 -->
            <Row :gutter="[12, 12]" style="margin-bottom: 16px;">
              <Col :xs="24" :sm="12" :md="8" :lg="6" :xl="4">
                <Input v-model:value="searchKeyword" placeholder="搜索标题关键词" @pressEnter="handleSearch" />
              </Col>
              <Col :xs="24" :sm="12" :md="8" :lg="6" :xl="4">
                <Select show-search v-model:value="searchPlatform" placeholder="选择平台" allowClear @change="handleSearch" style="width: 100%;">
                  <Select.Option v-for="p in platformOptions" :key="p.value" :value="p.value" :label="p.label">{{ p.label }}</Select.Option>
                </Select>
              </Col>
              <Col :xs="24" :sm="12" :md="8" :lg="6" :xl="4">
                <Select show-search v-model:value="searchTrack" placeholder="选择赛道" allowClear :disabled="!searchPlatform" @change="handleSearch" style="width: 100%;">
                  <Select.Option v-for="t in filteredTracksForSearch" :key="t.id" :value="t.id" :label="t.name">{{ t.name }}</Select.Option>
                </Select>
              </Col>
              <Col :xs="24" :sm="12" :md="8" :lg="6" :xl="4">
                <Input v-model:value="searchUserName" placeholder="关联用户" @pressEnter="handleSearch" />
              </Col>
              <Col :xs="24" :sm="12" :md="8" :lg="6" :xl="4">
                <Select show-search v-model:value="searchMatched" placeholder="匹配状态" allowClear style="width: 100%;">
                  <Select.Option value="1">已匹配</Select.Option>
                  <Select.Option value="0">未匹配</Select.Option>
                </Select>
              </Col>
              <Col :xs="24" :sm="12" :md="8" :lg="6" :xl="4">
                <DatePicker v-model:value="searchPushDate" placeholder="推荐日期" style="width: 100%;" />
              </Col>
              <Col :xs="24" :sm="12" :md="8" :lg="6" :xl="4">
                <Select show-search v-model:value="searchIsConfirmed" placeholder="确认状态" allowClear style="width: 100%;">
                  <Select.Option value="1">已确认</Select.Option>
                  <Select.Option value="0">未确认</Select.Option>
                  <Select.Option value="2">已拒绝</Select.Option>
                </Select>
              </Col>
              <Col :xs="24" :sm="12" :md="8" :lg="6" :xl="4">
                <Select show-search v-model:value="searchAiFlavor" placeholder="AI味检测" allowClear style="width: 100%;">
                  <Select.Option value="0">未检测</Select.Option>
                  <Select.Option value="1">通过</Select.Option>
                  <Select.Option value="2">AI味重</Select.Option>
                </Select>
              </Col>
              <Col :xs="24" :sm="12" :md="8" :lg="6" :xl="4">
                <Space>
                  <Button type="primary" @click="handleSearch">查询</Button>
                  <Button @click="handleReset">重置</Button>
                </Space>
              </Col>
            </Row>

            <!-- 高频操作 -->
            <div style="margin-bottom: 16px;">
              <Space :size="12" wrap style="width: 100%; justify-content: space-between;">
                <Space :size="12" wrap>
                  <Button type="primary" @click="handleAdd">+ 新增标题</Button>
                  <Button type="primary" ghost :loading="matching" @click="openMatchModal">匹配推荐</Button>
                </Space>
                <Dropdown>
                  <Button>更多 <DownOutlined /></Button>
                  <template #overlay>
                    <Menu>
                      <Menu.Item @click="openImportModal">导入标题</Menu.Item>
                      <Menu.Item @click="handleExportTitleList">导出标题</Menu.Item>
                      <Menu.Item @click="openImportArticleModal">导入文章</Menu.Item>
                      <Menu.Item @click="openExportRuleModal">导出</Menu.Item>
                      <Menu.Divider />
                      <Menu.Item @click="openRowPromptModal">提示词模板</Menu.Item>
                      <Menu.Item @click="openRemoveAiFlavorModal">去除AI味</Menu.Item>
                    </Menu>
                  </template>
                </Dropdown>
              </Space>
            </div>

            <!-- 批量操作栏 -->
            <Alert
              v-if="selectedRowKeys.length > 0"
              type="info"
              show-icon
              style="margin-bottom: 16px;"
            >
              <template #message>
                <Space :size="12" wrap align="center">
                  <span>已选择 <strong style="color: #1890ff;">{{ selectedRowKeys.length }}</strong> 项</span>
                  <Button size="small" type="primary" :loading="batchGenerating" @click="handleBatchGenerate">批量生成</Button>
                  <Button size="small" type="primary" ghost @click="handleBatchSendEmail">批量发邮件</Button>
                  <Button size="small" danger @click="handleBatchUnbind">批量解绑</Button>
                  <Button size="small" type="primary" ghost @click="handleBatchAiPassed">检测AI味通过</Button>
                  <Button size="small" type="link" @click="selectedRowKeys = []">取消选择</Button>
                </Space>
              </template>
            </Alert>

            <Table :columns="columns" :data-source="paginatedData" :pagination="false" row-key="id" :loading="loading" :row-selection="rowSelection" :scroll="{ x: 'max-content' }" @change="handleTableChange" />

            <Row justify="end" style="margin-top: 16px;">
              <Col>
                <Pagination
                  v-model:current="currentPage"
                  v-model:pageSize="pageSize"
                  :total="totalCount"
                  show-size-changer
                  :page-size-options="['10', '20', '50']"
                  :show-total="total => `共 ${total} 条`"
                  @change="handlePageChange"
                />
              </Col>
            </Row>
          </Card>
        </div>

        <!-- 右侧用户面板 -->
        <div v-if="false" style="width: 40%; flex-shrink: 0;">
          <Card :bordered="false" style="height: 100%;">
            <div style="margin-bottom: 16px;">
              <div style="font-size: 15px; font-weight: 600; margin-bottom: 12px;">用户推荐/推送监控</div>
              <DatePicker
                v-model:value="panelDate"
                style="width: 100%;"
                @change="onPanelDateChange"
              />
            </div>

            <Tabs v-model:activeKey="activePanelTab">
              <!-- Tab 1: 未推荐用户（分三个子Tab） -->
              <Tabs.TabPane key="unrecommended" :tab="`未推荐用户`">
                <Tabs v-model:activeKey="activeUnrecommendedSubTab" size="small">
                  <Tabs.TabPane key="accountOpened" :tab="`开户 (${unrecommendedAccountOpened.length})`">
                    <div v-if="panelLoading" style="text-align: center; padding: 40px; color: #999;">加载中...</div>
                    <div v-else-if="unrecommendedAccountOpened.length === 0" style="text-align: center; padding: 40px; color: #999;">
                      该日期下所有开户用户均已完整推荐
                    </div>
                    <Table
                      v-else
                      :columns="unrecommendedColumns"
                      :data-source="unrecommendedAccountOpened"
                      :pagination="false"
                      size="small"
                      row-key="id"
                      :scroll="{ y: 440 }"
                    />
                  </Tabs.TabPane>
                  <Tabs.TabPane key="distributor" :tab="`分成 (${unrecommendedDistributor.length})`">
                    <div v-if="panelLoading" style="text-align: center; padding: 40px; color: #999;">加载中...</div>
                    <div v-else-if="unrecommendedDistributor.length === 0" style="text-align: center; padding: 40px; color: #999;">
                      该日期下所有分成用户均已完整推荐
                    </div>
                    <Table
                      v-else
                      :columns="unrecommendedColumns"
                      :data-source="unrecommendedDistributor"
                      :pagination="false"
                      size="small"
                      row-key="id"
                      :scroll="{ y: 440 }"
                    />
                  </Tabs.TabPane>
                  <Tabs.TabPane key="trial" :tab="`试用 (${unrecommendedTrial.length})`">
                    <div v-if="panelLoading" style="text-align: center; padding: 40px; color: #999;">加载中...</div>
                    <div v-else-if="unrecommendedTrial.length === 0" style="text-align: center; padding: 40px; color: #999;">
                      该日期下所有试用用户均已完整推荐
                    </div>
                    <Table
                      v-else
                      :columns="unrecommendedColumns"
                      :data-source="unrecommendedTrial"
                      :pagination="false"
                      size="small"
                      row-key="id"
                      :scroll="{ y: 440 }"
                    />
                  </Tabs.TabPane>
                </Tabs>
              </Tabs.TabPane>
            </Tabs>
          </Card>
        </div>
      </div>
    </Tabs.TabPane>
  </Tabs>

  <Modal
    v-model:open="changeTrackModalOpen"
    :title="changeTrackForm.singleRecord ? '修改赛道' : '批量修改赛道'"
    :confirm-loading="changeTrackLoading"
    @ok="handleChangeTrackConfirm"
  >
    <Form layout="vertical" style="margin-top: 12px;"
    >
      <Form.Item label="选择赛道" required
      >
        <Select show-search v-model:value="changeTrackForm.trackId" placeholder="请选择赛道"
        >
          <Select.Option v-for="t in tracks" :key="t.id" :value="t.id" :label="t.name">{{ t.name }}</Select.Option>
        </Select>
      </Form.Item
      >
      <div v-if="!changeTrackForm.singleRecord" style="color: #999; font-size: 12px;"
      >
        已选择 {{ changeTrackForm.titleIds.length }} 条标题
      </div
      >
    </Form
    >
  </Modal
  >

  <Modal
    v-model:open="matchModalOpen"
    title="匹配推荐"
    :confirm-loading="matching"
    @ok="handleMatchConfirm"
    :width="1200"
  >
    <div style="margin-bottom: 12px; display: flex; gap: 12px; align-items: center;">
      <DatePicker v-model:value="matchForm.date" placeholder="推荐日期" @change="() => { localStorage.setItem('matchForm_date', matchForm.date.format('YYYY-MM-DD')); runMatchPreview(); }" />
      <Button @click="runMatchPreview" :loading="matchPreviewLoading">刷新预览</Button>
      <Button type="primary" :loading="batchImagePostLoading" @click="handleBatchGenerateImagePost">批量生成贴图</Button>
      <Button
        type="primary"
        ghost
        :disabled="matchSelectedKeys.length === 0"
        :loading="matching"
        @click="handleBatchApproveSelected"
      >批量通过选中</Button>
      <Button danger :loading="matchClearing" @click="handleClearAndMatch" style="margin-left: auto;">清理后重新匹配</Button>
    </div>
    <div style="color: #999; font-size: 12px; margin-bottom: 12px;">
      以下为系统预匹配结果，请审核后确认。所有变更前可编辑标题或重新匹配用户。相似度≥20%的标题已被自动过滤。
    </div>

    <!-- 加载中 -->
    <div v-if="matchPreviewLoading" style="text-align: center; padding: 32px;">
      <Spin /> 正在加载匹配预览...
    </div>

    <!-- 匹配预览表格 -->
    <div v-else>
      <Table
        :data-source="matchPreviewData"
        :pagination="{ pageSize: 10 }"
        size="small"
        :scroll="{ x: 900 }"
        row-key="titleId"
        :row-selection="matchRowSelection"
        style="border: 1px solid #f0f0f0; border-radius: 4px;"
      >
        <Table.Column title="标题" key="title" :width="280">
          <template #default="{ record }">
            <Input.TextArea
              :value="record.editedTitle || record.title"
              placeholder="可编辑标题"
              :rows="2"
              :auto-size="{ minRows: 1, maxRows: 3 }"
              size="small"
              @change="(e) => handleTitleEdit(record, e.target.value)"
              style="font-size: 13px; width: 260px;"
            />
            <div v-if="record.editedTitle && record.editedTitle !== record.title" style="font-size: 11px; color: #52c41a;">已修改</div>
          </template>
        </Table.Column>
        <Table.Column title="最高相似度" key="maxSimilarity" :width="80" align="center">
          <template #default="{ record }">
            <Tag v-if="record.maxSimilarity != null" :color="record.maxSimilarity >= 50 ? 'red' : record.maxSimilarity >= 25 ? 'orange' : 'green'" style="font-size: 12px;">
              {{ record.maxSimilarity }}%
            </Tag>
            <span v-else style="color: #999;">-</span>
          </template>
        </Table.Column>
        <Table.Column title="同质化程度" key="homogeneity" :width="90" align="center">
          <template #default="{ record }">
            <Tag v-if="record.homogeneity != null" :color="record.homogeneity >= 50 ? 'red' : record.homogeneity >= 25 ? 'orange' : 'green'" style="font-size: 12px;">
              {{ record.homogeneity }}%
            </Tag>
            <span v-else style="color: #999;">-</span>
          </template>
        </Table.Column>
        <Table.Column title="平台" dataIndex="platform" key="platform" :width="70" />
        <Table.Column title="赛道" dataIndex="trackName" key="trackName" :width="80" />
        <Table.Column title="匹配用户" key="user" :width="90">
          <template #default="{ record }">
            <a style="color: #1890ff;" @click="openHistoryModal(record.userId, record.username, record.editedTitle || record.title)">{{ record.username }}</a>
            <div style="font-size: 11px; color: #999;">{{ record.userEmail }}</div>
          </template>
        </Table.Column>
        <Table.Column title="操作" key="action" :width="150">
          <template #default="{ record }">
            <template v-if="record.approved">
              <Tag color="green">已通过</Tag>
            </template>
            <template v-else>
              <Button type="link" size="small" style="color: #52c41a;" @click="handleApproveOne(record)">通过</Button>
              <Button
                type="link" size="small"
                :loading="matchOneLoadingId === record.titleId"
                @click="handleRematchOne(record)"
              >重配</Button>
              <Button type="link" size="small" @click="openImagePostModal({ id: record.titleId })">贴图</Button>
              <Popconfirm title="确定移除该项？" @confirm="handleRemoveMatch(record)">
                <Button type="link" size="small" danger>移除</Button>
              </Popconfirm>
            </template>
          </template>
        </Table.Column>
      </Table>

      <div v-if="matchPreviewData.length === 0 && !matchPreviewLoading" style="text-align: center; padding: 24px; color: #999;">
        暂无待匹配项
      </div>

      <div style="margin-top: 12px; font-size: 13px; color: #595959;">
        共 <strong>{{ matchPreviewData.length }}</strong> 项待确认匹配
      </div>
    </div>
  </Modal>

  <!-- 日期选择前置弹框 -->
  <Modal
    v-model:open="matchDateModalOpen"
    title="选择推荐日期"
    :mask-closable="false"
    @ok="handleMatchDateConfirm"
  >
    <div style="margin-top: 12px;">
      <DatePicker v-model:value="matchDateForm.date" placeholder="选择推荐日期" style="width: 100%;" />
    </div>
  </Modal>

  <!-- 无合适匹配提示弹框 -->
  <Modal
    v-model:open="noMatchPromptOpen"
    title="没有合适的匹配标题"
    :mask-closable="false"
    @ok="handleNoMatchConfirm"
    @cancel="noMatchPromptOpen = false"
  >
    <div style="margin-top: 12px; font-size: 14px; color: #595959; line-height: 1.8;">
      <p>当前日期下，所有候选标题与历史标题的相似度均≥20%，已被自动过滤。</p>
      <p v-if="noMatchPromptTracks.length > 0">
        以下赛道没有低相似度标题，建议生成新标题：<br>
        <Tag v-for="t in noMatchPromptTracks" :key="t.trackId" color="orange" style="margin-top: 8px;">{{ t.trackName }}</Tag>
      </p>
    </div>
  </Modal>

  <!-- V2 生成标题弹框 -->
  <Modal
    v-model:open="v2GenerateModalOpen"
    title="生成标题（V2-大模型）"
    :mask-closable="false"
    :confirm-loading="v2Generating"
    @ok="handleV2Generate"
    :width="560"
  >
    <Form layout="vertical" style="margin-top: 12px;">
      <div style="background: #e6f7ff; border: 1px solid #91d5ff; border-radius: 8px; padding: 12px 16px; margin-bottom: 16px;">
        <div style="font-size: 13px; color: #096dd9; margin-bottom: 8px;">
          <strong>生成说明</strong>
        </div>
        <div style="font-size: 12px; color: #096dd9; line-height: 1.8;">
          1. 选择平台和赛道，不选则生成全部<br>
          2. 数量指每个平台下每个赛道生成的标题数<br>
          3. 系统会按平台分批调用大模型（kimi/minimax）生成<br>
          4. 生成结果包含标题、平台、赛道名称和文章写作思路
        </div>
      </div>
      <Form.Item label="选择平台">
        <Select
          show-search
          v-model:value="v2GeneratePlatforms"
          mode="multiple"
          placeholder="不选则生成全部平台"
          style="width: 100%;"
          @change="onV2PlatformChange"
        >
          <Select.Option value="公众号">公众号</Select.Option>
          <Select.Option value="今日头条">今日头条</Select.Option>
          <Select.Option value="百家号">百家号</Select.Option>
        </Select>
      </Form.Item>
      <Form.Item label="选择赛道">
        <Select
          show-search
          v-model:value="v2GenerateTrackIds"
          mode="multiple"
          placeholder="不选则生成全部赛道"
          style="width: 100%;"
        >
          <Select.Option v-for="t in filteredTracksForV2Generate" :key="t.id" :value="t.id" :label="t.name">{{ t.name }}</Select.Option>
        </Select>
      </Form.Item>
      <Form.Item label="每个组合生成数量" required>
        <Input v-model:value="v2GenerateCount" type="number" min="1" max="20" placeholder="例如：3" />
      </Form.Item>
      <Form.Item label="标题风格（可选）">
        <Select
          v-model:value="v2SelectedStyleId"
          placeholder="不选则使用默认风格"
          style="width: 100%;"
          allowClear
        >
          <Select.Option v-for="s in v2StyleTemplates" :key="s.id" :value="s.id">{{ s.name }}</Select.Option>
        </Select>
        <div style="font-size: 12px; color: #999; margin-top: 4px;">选择后，AI 将严格按照该风格生成标题</div>
      </Form.Item>
      <Form.Item label="生成方向（可选）">
        <div style="display: flex; gap: 12px; margin-bottom: 8px;">
          <label style="font-size: 13px; cursor: pointer; display: flex; align-items: center; gap: 4px;">
            <input type="radio" v-model="v2InstructionMode" value="manual" />
            手动输入
          </label>
          <label style="font-size: 13px; cursor: pointer; display: flex; align-items: center; gap: 4px;">
            <input type="radio" v-model="v2InstructionMode" value="template" />
            选择提示词模板
          </label>
        </div>
        <div v-if="v2InstructionMode === 'template'">
          <Select
            v-model:value="v2SelectedPromptId"
            placeholder="请选择提示词模板"
            style="width: 100%;"
            @change="onV2PromptChange"
          >
            <Select.Option v-for="p in v2PromptTemplates" :key="p.id" :value="p.id">{{ p.name }}</Select.Option>
          </Select>
        </div>
        <Input.TextArea
          v-model:value="v2GenerateInstruction"
          placeholder="例如：更口语化、更具悬念、突出数字效果、适合抖音风格、偏新闻报道类..."
          :rows="4"
          :maxlength="2000"
          show-count
          :disabled="v2InstructionMode === 'template'"
        />
        <div style="font-size: 12px; color: #999; margin-top: 4px;">不填则使用默认策略生成</div>
      </Form.Item>
    </Form>
  </Modal>

  <Modal v-model:open="modalOpen" :title="modalTitle" :mask-closable="false" :confirm-loading="saving" @ok="handleSave">
    <Form layout="vertical" style="margin-top: 12px;">
      <Form.Item label="标题内容" required>
        <Input.TextArea v-model:value="form.title" placeholder="请输入标题内容" :rows="3" />
      </Form.Item>
      <Form.Item label="描述">
        <Input.TextArea v-model:value="form.description" placeholder="请输入标题SEO描述（30字以内）" :rows="2" />
      </Form.Item>
      <Form.Item label="推荐日期">
        <DatePicker v-model:value="form.pushDate" placeholder="选择推荐日期" style="width: 100%;" />
      </Form.Item>
      <Form.Item label="适用平台-赛道">
        <Select show-search
          v-model:value="form.trackId"
          placeholder="选择平台-赛道"
          allowClear
          @change="handleTrackChange"
        >
          <Select.Option v-for="t in sortedTrackOptions" :key="t.id" :value="t.id" :label="t.displayLabel">{{ t.displayLabel }}</Select.Option>
        </Select>
      </Form.Item>
      <div v-if="!changeTrackForm.singleRecord" style="color: #999; font-size: 12px;"
      >
        已选择 {{ changeTrackForm.titleIds.length }} 条标题
      </div
      >
    </Form
    >
  </Modal
  >


  <Modal v-model:open="modalOpen" :title="modalTitle" :mask-closable="false" :confirm-loading="saving" @ok="handleSave">
    <Form layout="vertical" style="margin-top: 12px;">
      <Form.Item label="标题内容" required>
        <Input.TextArea v-model:value="form.title" placeholder="请输入标题内容" :rows="3" />
      </Form.Item>
      <Form.Item label="描述">
        <Input.TextArea v-model:value="form.description" placeholder="请输入标题SEO描述（30字以内）" :rows="2" />
      </Form.Item>
      <Form.Item label="推荐日期">
        <DatePicker v-model:value="form.pushDate" placeholder="选择推荐日期" style="width: 100%;" />
      </Form.Item>
      <Form.Item label="适用平台-赛道">
        <Select show-search
          v-model:value="form.trackId"
          placeholder="选择平台-赛道"
          allowClear
          @change="handleTrackChange"
        >
          <Select.Option v-for="t in sortedTrackOptions" :key="t.id" :value="t.id">{{ t.displayLabel }}</Select.Option>
        </Select>
      </Form.Item>
      <Form.Item label="关联用户">
        <Select show-search v-model:value="form.recommendUserId" placeholder="选择关联用户（可选）" allowClear style="width: 100%;" @change="handleUserChange">
          <Select.Option v-for="u in filteredUsersForSelect" :key="u.id" :value="u.id" :label="u.displayText">{{ u.displayText }}</Select.Option>
        </Select>
      </Form.Item>
      <Form.Item v-if="form.recommendUserName" label="当前关联">
        <div style="display: flex; align-items: center; gap: 12px;">
          <span style="color: #333;">{{ form.recommendUserName }}</span>
          <span style="color: #999; font-size: 12px;">推荐日期：{{ form.recommendDate || '-' }}</span>
          <span style="color: #999; font-size: 12px;">样式：{{ form.recommendUserTemplate || '-' }}</span>
          <Button v-if="form.id" type="link" danger size="small" style="margin-left: auto;" @click="handleUnbind">解绑</Button>
        </div>
      </Form.Item>
    </Form>
  </Modal>

  <Modal v-model:open="userModalOpen" title="用户详情" :footer="null" :mask-closable="true" width="480">
    <Descriptions v-if="selectedUserRecord" layout="vertical" :column="1" style="margin-top: 12px;">
      <Descriptions.Item label="用户名称">{{ selectedUserRecord.recommendUserName || '-' }}</Descriptions.Item>
      <Descriptions.Item label="用户样式">{{ selectedUserRecord.recommendUserTemplate || '-' }}</Descriptions.Item>
      <Descriptions.Item label="推荐日期">{{ selectedUserRecord.recommendDate || '-' }}</Descriptions.Item>
      <Descriptions.Item label="标题内容">{{ selectedUserRecord.title }}</Descriptions.Item>
      <Descriptions.Item label="适用平台">{{ selectedUserRecord.platform || '-' }}</Descriptions.Item>
      <Descriptions.Item label="关联赛道">{{ selectedUserRecord.trackName || '-' }}</Descriptions.Item>
    </Descriptions>
  </Modal>

  <Modal v-model:open="importModalOpen" title="批量导入标题" :mask-closable="false" :confirm-loading="importLoading" @ok="handleImport">
    <Form layout="vertical" style="margin-top: 12px;">
      <div style="background: #f6ffed; border: 1px solid #b7eb8f; border-radius: 8px; padding: 12px 16px; margin-bottom: 16px;">
        <div style="font-size: 13px; color: #389e0d; margin-bottom: 8px;">
          <strong>导入说明</strong>
        </div>
        <div style="font-size: 12px; color: #389e0d; line-height: 1.8;">
          1. 下载导入模板，按格式填写数据<br>
          2. Excel 列顺序：标题 | 平台 | 赛道名称<br>
          3. 第一行为表头，数据从第二行开始<br>
          4. 赛道名称需与系统中已存在的赛道名称一致<br>
          5. 支持 .xlsx / .xls 格式
        </div>
      </div>
      <Form.Item>
        <Button size="small" @click="downloadTemplate">下载导入模板</Button>
      </Form.Item>
      <Form.Item label="Excel 文件" required>
        <input type="file" accept=".xlsx,.xls" @change="onImportFileChange">
        <div style="font-size: 12px; color: #999; margin-top: 4px;">支持 .xlsx / .xls</div>
      </Form.Item>
    </Form>
  </Modal>

  <Modal v-model:open="exportFileNameModalOpen" title="设置导出文件名" :mask-closable="false" width="400">
    <Form layout="vertical" style="margin-top: 12px;">
      <Form.Item label="文件名">
        <Input v-model:value="exportFileNameInput" placeholder="例如：标题库导出" />
        <div style="font-size: 12px; color: #999; margin-top: 4px;">留空则使用默认文件名，会自动补 .xlsx 后缀</div>
      </Form.Item>
    </Form>
    <template #footer>
      <div style="display: flex; justify-content: flex-end; gap: 12px;">
        <Button @click="exportFileNameModalOpen = false">取消</Button>
        <Button type="primary" @click="handleExportConfirm">确认导出</Button>
      </div>
    </template>
  </Modal>

  <Modal v-model:open="previewModalOpen" title="文章预览" :footer="null" :mask-closable="true" width="700">
    <div v-if="previewRecord" style="margin-top: 12px;">
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
        <div style="font-size: 16px; font-weight: 600;">{{ previewRecord.title }}</div>
        <div style="display: flex; gap: 8px;">
          <Button size="small" @click="copyArticleContent">复制文章内容</Button>
          <Button size="small" :loading="imagePostLoading" @click="handleGenerateImagePost(previewRecord)">生成贴图</Button>
          <Button size="small" @click="openImagePostModal(previewRecord)">查看贴图</Button>
        </div>
      </div>
      <div style="border: 1px solid #f0f0f0; border-radius: 6px; background: #fafafa; min-height: 360px; max-height: 500px; overflow: auto;">
        <div v-if="previewLoading" style="padding: 24px; text-align: center; color: #999;">正在加载预览...</div>
        <pre
          v-else-if="previewHtmlContent"
          style="padding: 16px; margin: 0 auto; max-width: 640px; font-family: monospace; font-size: 13px; line-height: 2.0; white-space: pre-wrap; word-break: break-word; background: #fafafa; border: 0;"
        >{{ previewHtmlContent }}</pre>
        <div
          v-else-if="previewFileType === 'docx'"
          ref="docxContainerRef"
          style="padding: 24px; margin: 0 auto; max-width: 640px; background: #fff; border: 0; line-height: 2.2; font-size: 14px;"
        />
        <div v-else-if="previewRecord.generatedFileUrl">
          <iframe
            :src="previewRecord.generatedFileUrl + (previewRecord.generatedFileUrl.includes('?') ? '&' : '?') + '_t=' + previewTimestamp"
            style="width: 100%; height: 500px; border: 0;"
          />
        </div>
        <div v-else style="padding: 24px; color: #999; text-align: center;">暂无文件</div>
      </div>
      <!-- 违禁词统计栏 -->
      <div v-if="highlightStats.totalChars > 0" style="position: sticky; bottom: 0; background: #fff; border-top: 1px solid #f0f0f0; padding: 8px 16px; display: flex; gap: 16px; font-size: 13px; flex-wrap: wrap; margin-top: 8px;">
        <span>全文:{{ highlightStats.totalChars }}字</span>
        <span>极限词:{{ highlightStats.极限词 || 0 }}个</span>
        <span>诱导词:{{ highlightStats.诱导词 || 0 }}个</span>
        <span>敏感词:{{ highlightStats.敏感词 || 0 }}个</span>
        <span>医疗词:{{ highlightStats.医疗词 || 0 }}个</span>
        <span>金融词:{{ highlightStats.金融词 || 0 }}个</span>
        <span>政治敏感:{{ highlightStats.政治敏感 || 0 }}个</span>
        <span>其他:{{ highlightStats.其他 || 0 }}个</span>
      </div>
    </div>
  </Modal>

  <Modal v-model:open="exportRuleModalOpen" title="导出Excel生成规则" :mask-closable="false" width="720" :footer="null">
    <Form layout="vertical" style="margin-top: 12px;">
      <div style="background: #fff7e6; border: 1px solid #ffd591; border-radius: 8px; padding: 12px 16px; margin-bottom: 16px;">
        <div style="font-size: 13px; color: #d46b08; margin-bottom: 8px;">
          <strong>规则说明</strong>
        </div>
        <div style="font-size: 12px; color: #d46b08; line-height: 1.8;">
          该规则会作为 Excel 最后一个 sheet「生成规则」的内容，Claude 导出时将根据此规则处理文章生成逻辑。
        </div>
      </div>
      <Form.Item label="生成规则内容" required>
        <Input.TextArea v-model:value="exportRuleContent" placeholder="请输入导出Excel的生成规则..." :rows="16" />
      </Form.Item>
      <div style="display: flex; justify-content: flex-end; gap: 12px;">
        <Button @click="exportRuleModalOpen = false">取消</Button>
        <Button type="default" :loading="exportRuleSaving" @click="handleSaveExportRule">保存</Button>
        <Button type="primary" @click="handleExportFromRule">导出</Button>
      </div>
    </Form>
  </Modal>

  <Modal v-model:open="promptModalOpen" title="生成文章提示词" :mask-closable="false" width="720" :footer="null">
    <Form layout="vertical" style="margin-top: 12px;">
      <div style="background: #e6f7ff; border: 1px solid #91d5ff; border-radius: 8px; padding: 12px 16px; margin-bottom: 16px;">
        <div style="font-size: 13px; color: #096dd9; margin-bottom: 8px;">
          <strong>可用变量</strong>
        </div>
        <div style="font-size: 12px; color: #096dd9; line-height: 1.8;">
          <code>{title}</code> — 标题内容&nbsp;&nbsp;
          <code>{description}</code> — 标题描述&nbsp;&nbsp;
          <code>{styleDesc}</code> — 样式描述（字体、颜色、间距等）&nbsp;&nbsp;
          <code>{styleRef}</code> — 样式参考文件路径
        </div>
        <div style="font-size: 12px; color: #096dd9; margin-top: 4px;">
          提示词保存后会作为默认模板，后续生成文章时将自动使用。
        </div>
      </div>
      <Form.Item label="提示词内容" required>
        <Input.TextArea v-model:value="promptContent" placeholder="请输入生成文章的提示词..." :rows="16" />
      </Form.Item>
      <div style="display: flex; justify-content: flex-end; gap: 12px;">
        <Button @click="promptModalOpen = false">取消</Button>
        <Button type="default" :loading="promptSaving" @click="handleSavePrompt">保存</Button>
        <Button type="primary" :loading="generatingPost" @click="handleGenerateFromPrompt">生成</Button>
      </div>
    </Form>
  </Modal>

  <Modal v-model:open="importArticleModalOpen" title="导入文章" :mask-closable="false" width="640" :footer="null">
    <Form layout="vertical" style="margin-top: 12px;">
      <div style="background: #f6ffed; border: 1px solid #b7eb8f; border-radius: 8px; padding: 12px 16px; margin-bottom: 16px;">
        <div style="font-size: 13px; color: #389e0d; margin-bottom: 8px;">
          <strong>导入说明</strong>
        </div>
        <div style="font-size: 12px; color: #389e0d; line-height: 1.8;">
          1. 支持「选择文件夹」批量导入，或「选择文件」逐个/多选导入（仅支持 .doc / .docx 格式）<br>
          2. 系统会根据文件名自动匹配标题库中的记录<br>
          3. 匹配成功后会自动创建文章并关联到对应的推荐记录
        </div>
      </div>
      <Form.Item label="导入方式" required>
        <div style="display: flex; gap: 16px; margin-bottom: 8px;">
          <label style="font-size: 13px; cursor: pointer; display: flex; align-items: center; gap: 4px;">
            <input type="radio" v-model="importArticleMode" value="folder" @change="importArticleFiles = []" />
            选择文件夹
          </label>
          <label style="font-size: 13px; cursor: pointer; display: flex; align-items: center; gap: 4px;">
            <input type="radio" v-model="importArticleMode" value="file" @change="importArticleFiles = []" />
            选择文件
          </label>
        </div>
      </Form.Item>
      <Form.Item :label="importArticleMode === 'folder' ? '选择文件夹' : '选择文件'" required>
        <!-- 隐藏的原生 input -->
        <input
          ref="importArticleFolderInputRef"
          type="file"
          webkitdirectory
          directory
          multiple
          @change="onImportArticleFileChange"
          style="display: none;"
        />
        <input
          ref="importArticleFileInputRef"
          type="file"
          accept=".doc,.docx"
          multiple
          @change="onImportArticleFileChange"
          style="display: none;"
        />

        <!-- 拖拽上传区域 -->
        <div
          class="article-import-dropzone"
          :class="{ active: importArticleDragActive }"
          @click="triggerImportArticleInput"
          @dragover="onImportArticleDragOver"
          @dragleave="onImportArticleDragLeave"
          @drop="onImportArticleDrop"
        >
          <div class="article-import-icon">
            <svg viewBox="0 0 1024 1024" width="48" height="48" fill="currentColor">
              <path d="M544 864V672h128L512 480 352 672h128v192H320v64h384v-64H544zM512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64z m0 832c-212.1 0-384-171.9-384-384s171.9-384 384-384 384 171.9 384 384-171.9 384-384 384z" />
            </svg>
          </div>
          <div class="article-import-title">
            点击或将文件拖拽到这里上传
          </div>
          <div class="article-import-desc">
            支持 {{ importArticleMode === 'folder' ? '选择文件夹批量' : '单个/多个' }}导入，仅支持 .doc / .docx 格式
          </div>
        </div>

        <div v-if="importArticleFiles.length > 0" style="margin-top: 8px; font-size: 12px; color: #666;">
          已选择 {{ importArticleFiles.length }} 个文件
        </div>
      </Form.Item>
      <div v-if="importArticleResult" style="margin-bottom: 16px;">
        <div style="background: #f0f0f0; border-radius: 8px; padding: 12px 16px;">
          <div style="font-size: 13px; color: #333; margin-bottom: 8px;">
            <strong>导入结果</strong>：成功 {{ importArticleResult.success }} 条，跳过 {{ importArticleResult.skip }} 条
          </div>
          <div v-if="importArticleResult.details && importArticleResult.details.length > 0">
            <div style="font-size: 12px; color: #389e0d; margin-bottom: 4px;">成功明细：</div>
            <div v-for="(item, idx) in importArticleResult.details" :key="idx"
                 style="font-size: 12px; color: #666; padding: 2px 0; border-bottom: 1px dashed #e8e8e8;">
              {{ item.file }} → {{ item.title }}（{{ item.user }}）
            </div>
          </div>
          <div v-if="importArticleResult.errors && importArticleResult.errors.length > 0" style="margin-top: 8px;">
            <div style="font-size: 12px; color: #cf1322; margin-bottom: 4px;">失败原因：</div>
            <div v-for="(item, idx) in importArticleResult.errors" :key="idx"
                 style="font-size: 12px; color: #666; padding: 2px 0; border-bottom: 1px dashed #e8e8e8;">
              {{ item.file }}：{{ item.reason }}
            </div>
          </div>
        </div>
      </div>
      <div style="display: flex; justify-content: flex-end; gap: 12px;">
        <Button @click="importArticleModalOpen = false">关闭</Button>
        <Button type="primary" :loading="importArticleLoading" @click="handleImportArticles">开始导入</Button>
      </div>
    </Form>
  </Modal>

  <!-- 用户历史绑定弹窗 -->
  <Modal
    v-model:open="historyModalOpen"
    :title="'历史绑定记录 — ' + historyUserName"
    width="700"
    :footer="null"
  >
    <div style="margin-bottom: 12px;">
      <div style="font-size: 13px; color: #595959; margin-bottom: 4px;">当前匹配标题：<strong>{{ historyTitle }}</strong></div>
      <div style="font-size: 12px; color: #999;">下方为该用户历史上绑定过的标题，百分比为与当前标题的相似度（基于字符语义）</div>
    </div>
    <div v-if="historyLoading" style="text-align: center; padding: 24px;">
      <Spin /> 加载中...
    </div>
    <Table
      v-else
      :data-source="historyList"
      :pagination="{ pageSize: 10 }"
      size="small"
      row-key="recommendDate"
      style="border: 1px solid #f0f0f0; border-radius: 4px;"
    >
      <Table.Column title="历史绑定标题" dataIndex="titleName" key="titleName" :width="300">
        <template #default="{ record }">
          <div style="font-size: 13px;">{{ record.titleName }}</div>
          <div style="font-size: 12px; color: #999;">{{ record.recommendDate }}</div>
        </template>
      </Table.Column>
      <Table.Column title="相似度" key="similarity" :width="100" align="center">
        <template #default="{ record }">
          <div v-if="record.similarity != null">
            <Tag :color="record.similarity >= 50 ? 'red' : record.similarity >= 25 ? 'orange' : 'green'">
              {{ record.similarity }}%
            </Tag>
          </div>
          <div v-else style="color: #999;">-</div>
        </template>
      </Table.Column>
      <Table.Column title="风险" key="risk" :width="80" align="center">
        <template #default="{ record }">
          <span v-if="record.similarity == null" style="color: #999;">-</span>
          <span v-else-if="record.similarity >= 50" style="color: #f5222d; font-weight: 600;">高</span>
          <span v-else-if="record.similarity >= 25" style="color: #fa8c16; font-weight: 600;">中</span>
          <span v-else style="color: #52c41a;">低</span>
        </template>
      </Table.Column>
    </Table>
    <div v-if="!historyLoading && historyList.length === 0" style="text-align: center; padding: 24px; color: #999;">
      暂无历史绑定记录
    </div>
  </Modal>

  <!-- 复制提示词弹窗 -->
  <Modal
    v-model:open="copyPromptModalOpen"
    title="复制提示词"
    :mask-closable="false"
    width="640"
    :footer="null"
  >
    <div style="display: flex; flex-direction: column; gap: 12px;">
      <div>
        <div style="margin-bottom: 4px; font-size: 13px; color: #666;">提示词模板（支持 ${date}、${pno}、${sno} 变量）</div>
        <Input.TextArea v-model:value="copyPromptContent" placeholder="请输入提示词模板..." :rows="8" />
      </div>
      <div style="display: flex; gap: 12px;">
        <div style="flex: 1;">
          <div style="margin-bottom: 4px; font-size: 13px; color: #666;">${date}</div>
          <Input v-model:value="copyPromptDate" placeholder="日期" />
        </div>
        <div style="flex: 1;">
          <div style="margin-bottom: 4px; font-size: 13px; color: #666;">${pno}</div>
          <Input v-model:value="copyPromptPno" placeholder="pno" />
        </div>
        <div style="flex: 1;">
          <div style="margin-bottom: 4px; font-size: 13px; color: #666;">${sno}</div>
          <Input v-model:value="copyPromptSno" placeholder="sno" />
        </div>
      </div>
      <div style="display: flex; gap: 8px; margin-top: 8px;">
        <Button @click="saveCopyPrompt">保存</Button>
        <Button @click="saveCopyPromptAsNew">另存记录</Button>
        <Button type="primary" @click="doCopyPrompt('pno')">pno+1 复制</Button>
        <Button type="primary" @click="doCopyPrompt('sno')">sno+1 复制</Button>
      </div>

      <!-- 保存的记录列表 -->
      <div v-if="copyPromptRecords.length > 0" style="margin-top: 8px;">
        <div style="font-size: 13px; color: #666; margin-bottom: 8px; font-weight: 500;">保存的记录（点击恢复）</div>
        <div style="max-height: 200px; overflow-y: auto; border: 1px solid #f0f0f0; border-radius: 4px;">
          <div
            v-for="record in copyPromptRecords"
            :key="record.id"
            @click="loadCopyPromptRecord(record)"
            :style="{
              padding: '8px 12px',
              cursor: 'pointer',
              borderBottom: '1px solid #f0f0f0',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              background: copyPromptCurrentId === record.id ? '#e6f7ff' : 'transparent'
            }"
          >
            <div style="flex: 1; min-width: 0;">
              <div style="font-size: 13px; font-weight: 500;">{{ record.name }}</div>
              <div style="font-size: 12px; color: #999; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
                {{ record.content ? record.content.slice(0, 40) : '' }}...
              </div>
              <div style="font-size: 11px; color: #bbb; margin-top: 2px;">
                date:{{ record.date }} / pno:{{ record.pno }} / sno:{{ record.sno }}
              </div>
            </div>
            <Button
              type="text"
              danger
              size="small"
              style="margin-left: 8px; flex-shrink: 0;"
              @click="deleteCopyPromptRecord(record, $event)"
            >删除</Button>
          </div>
        </div>
      </div>
    </div>
  </Modal>

  <!-- 去除AI味弹窗 -->
  <Modal
    v-model:open="removeAiFlavorModalOpen"
    title="去除AI味"
    :mask-closable="false"
    width="600"
    :footer="null"
  >
    <div style="display: flex; flex-direction: column; gap: 16px;">
      <div>
        <div style="margin-bottom: 4px; font-size: 13px; color: #666;">文件或目录绝对路径</div>
        <Input v-model:value="removeAiFlavorPath" placeholder="请输入绝对路径，如 /Users/panyong/Documents/article.docx" />
      </div>
      <div>
        <div style="margin-bottom: 8px; font-size: 13px; color: #666;">
          自定义替换规则 <Button type="link" size="small" @click="addRemoveAiFlavorRule">+ 添加规则</Button>
          <Button type="link" size="small" @click="openRemoveAiFlavorSelectModal">从规则库选择</Button>
        </div>
        <div style="max-height: 200px; overflow-y: auto; border: 1px solid #d9d9d9; border-radius: 4px; padding: 8px;">
          <div v-for="(rule, index) in removeAiFlavorRules" :key="index" style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px;">
            <Input v-model:value="rule.from" placeholder="替换前" style="width: 140px;" />
            <span style="color: #999;">→</span>
            <Input v-model:value="rule.to" placeholder="替换后（留空则删除）" style="width: 180px;" />
            <Button type="link" size="small" danger @click="removeRemoveAiFlavorRule(index)">删除</Button>
          </div>
          <div v-if="removeAiFlavorRules.length === 0" style="color: #999; text-align: center; padding: 8px;">暂无规则</div>
        </div>
        <div style="font-size: 12px; color: #999; margin-top: 6px;">
          内置规则：。→，；→，：/:→，、→，「」→空，——→，；短句合并：逗号≤2且仅1个句号的段落 → 句号改逗号并合并到下一段（跳过空段落）。（这些规则始终生效，列表中的规则在此基础上追加执行）
        </div>
      </div>
      <div style="font-size: 12px; color: #999; line-height: 1.6;">
        点击"去除"后将调用本地脚本处理指定路径的文件。
      </div>
      <div style="display: flex; gap: 8px;">
        <Button @click="saveRemoveAiFlavorPath">保存路径</Button>
        <Button type="primary" :loading="removeAiFlavorLoading" @click="handleRemoveAiFlavor">去除</Button>
      </div>
    </div>
  </Modal>

  <!-- 从规则库选择弹窗 -->
  <Modal
    v-model:open="removeAiFlavorSelectModalOpen"
    title="从规则库选择"
    :mask-closable="false"
    width="480"
    @ok="confirmImportAiFlavorRules"
    ok-text="确认添加"
  >
    <div style="max-height: 400px; overflow-y: auto;">
      <div v-if="aiFlavorRulesLibrary.length === 0" style="text-align: center; padding: 40px; color: #999;">
        规则库为空，请先在「AI去除规则」菜单中添加
      </div>
      <CheckboxGroup v-model:value="selectedAiFlavorRules" style="width: 100%;">
        <div v-for="rule in aiFlavorRulesLibrary" :key="rule.id" style="padding: 10px 8px; border-bottom: 1px solid #f0f0f0; display: flex; align-items: center; gap: 8px;">
          <Checkbox :value="rule">{{ '' }}</Checkbox>
          <span style="flex: 1;">{{ rule.ruleFrom }}</span>
          <span style="color: #999; font-size: 12px;">→</span>
          <span style="flex: 1; color: #666;">{{ rule.ruleTo || '（删除）' }}</span>
        </div>
      </CheckboxGroup>
    </div>
  </Modal>

  <!-- 自动插入图片弹窗 -->
  <Modal
    v-model:open="autoInsertImagesModalOpen"
    title="自动插入图片"
    :mask-closable="false"
    width="520"
    :footer="null"
  >
    <div style="display: flex; flex-direction: column; gap: 16px;">
      <div>
        <div style="margin-bottom: 4px; font-size: 13px; color: #666;">文件目录（doc/docx 所在目录）</div>
        <Input v-model:value="autoInsertImagesForm.fileDir" placeholder="如 /Users/panyong/Documents/articles" />
      </div>
      <div>
        <div style="margin-bottom: 4px; font-size: 13px; color: #666;">图片库目录</div>
        <Input v-model:value="autoInsertImagesForm.imageLibDir" placeholder="如 /Users/panyong/Pictures/article_images" />
      </div>
      <div>
        <div style="margin-bottom: 4px; font-size: 13px; color: #666;">每个文件插入图片数量</div>
        <InputNumber v-model:value="autoInsertImagesForm.count" :min="1" :max="20" style="width: 100%;" />
      </div>
      <div style="font-size: 12px; color: #999; line-height: 1.6;">
        点击"插入"后将调用本地脚本处理指定目录及子目录下所有 docx 文件。<br>
        第一张图片插入到文件最前面居中，后续图片随机插入到文末区域换行后居中显示。
      </div>
      <div style="display: flex; gap: 8px;">
        <Button @click="saveAutoInsertImagesParams">保存参数</Button>
        <Button type="primary" :loading="autoInsertImagesLoading" @click="handleAutoInsertImages">插入</Button>
      </div>
    </div>
  </Modal>

  <!-- 行级提示词模板弹窗 -->
  <Modal
    v-model:open="rowPromptModalOpen"
    title="提示词模板设置"
    :mask-closable="false"
    width="640"
    :footer="null"
  >
    <div style="display: flex; flex-direction: column; gap: 12px;">
      <div>
        <div style="margin-bottom: 4px; font-size: 13px; color: #666;">模板内容（点击下方字段插入变量，如 <code>${title}</code>）</div>
        <Input.TextArea
          ref="rowPromptTextAreaRef"
          v-model:value="rowPromptTemplate"
          placeholder="例如：请为平台 ${platform} 的赛道 ${trackName} 生成一篇关于 ${title} 的文章..."
          :rows="8"
        />
      </div>
      <div>
        <div style="margin-bottom: 8px; font-size: 13px; color: #666; font-weight: 500;">可用字段（点击插入）</div>
        <div style="display: flex; flex-wrap: wrap; gap: 8px;">
          <Button
            v-for="f in availableFields"
            :key="f.key"
            size="small"
            @click="insertFieldToTemplate(f.key)"
          >{{ f.label }} <code style="margin-left: 4px;">{{ '${' + f.key + '}' }}</code></Button>
        </div>
      </div>
      <div>
        <div style="margin-bottom: 8px; font-size: 13px; color: #666; font-weight: 500;">样式提示词变量（根据「目标用户」配置自动注入，点击插入）</div>
        <div style="display: flex; flex-wrap: wrap; gap: 8px;">
          <Button size="small" @click="insertFieldToTemplate('stylePrompt')">样式提示词 <code style="margin-left: 4px;">${stylePrompt}</code></Button>
        </div>
      </div>
      <div style="display: flex; gap: 8px; margin-top: 8px;">
        <Button @click="saveRowPromptTemplate">保存模板</Button>
      </div>
    </div>
  </Modal>

  <!-- 贴图预览弹窗 -->
  <Modal v-model:open="imagePostModalOpen" title="贴图预览" :footer="null" :mask-closable="true" width="900">
    <div v-if="imagePostLoading" style="padding: 24px; text-align: center;">
      <Spin />
      <div style="margin-top: 8px; color: #999;">正在加载贴图...</div>
    </div>
    <div v-else-if="imagePostUrls.length === 0" style="padding: 24px; text-align: center; color: #999;">
      暂无贴图，点击"生成贴图"按钮创建
    </div>
    <div v-else style="display: flex; flex-direction: row; gap: 16px; padding: 8px; overflow-x: auto;">
      <div v-for="(url, idx) in imagePostUrls" :key="idx" style="border: 1px solid #f0f0f0; border-radius: 8px; overflow: hidden; flex-shrink: 0; width: 240px;">
        <img :src="url" style="width: 100%; height: 400px; object-fit: cover; display: block; cursor: zoom-in;" @click="handlePreviewImage(url)" />
        <div style="display: flex; justify-content: space-between; align-items: center; padding: 8px 12px; background: #fafafa;">
          <span style="font-size: 12px; color: #999;">第 {{ idx + 1 }} 张</span>
          <Button size="small" @click="handleDownloadImage(url)">下载</Button>
        </div>
      </div>
    </div>
  </Modal>

  <!-- 大图缩放预览 -->
  <Modal v-model:open="imagePreviewOpen" :footer="null" :mask-closable="true" width="auto" style="max-width: 90vw;">
    <div style="display: flex; justify-content: center; align-items: center; padding: 8px;">
      <img :src="previewImageUrl" style="max-width: 100%; max-height: 80vh; border-radius: 8px;" />
    </div>
  </Modal>
</template>

<style scoped>
:deep(.docx-preview img) {
  max-width: 100%;
  height: auto;
}
:deep(.docx-preview table) {
  max-width: 100%;
  border-collapse: collapse;
}
:deep(.ant-tabs-tab:focus),
:deep(.ant-tabs-tab:active),
:deep(.ant-menu-item:focus),
:deep(.ant-menu-item:active) {
  outline: none;
  box-shadow: none;
}

.article-import-dropzone {
  border: 2px dashed #d9d9d9;
  border-radius: 8px;
  padding: 32px 24px;
  text-align: center;
  cursor: pointer;
  transition: all 0.25s ease;
  background: #fafafa;
}
.article-import-dropzone:hover {
  border-color: #1890ff;
  background: #e6f7ff;
}
.article-import-dropzone.active {
  border-color: #1890ff;
  background: #e6f7ff;
}
.article-import-icon {
  color: #1890ff;
  margin-bottom: 12px;
  line-height: 1;
}
.article-import-title {
  font-size: 15px;
  font-weight: 500;
  color: #262626;
  margin-bottom: 6px;
}
.article-import-desc {
  font-size: 12px;
  color: #8c8c8c;
  line-height: 1.6;
}
</style>
