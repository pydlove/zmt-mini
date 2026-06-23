<script setup>
import { ref, onMounted, onUnmounted, computed, h } from 'vue'
import { useRouter } from 'vue-router'
import { Table, Tag, Input, Select, Button, Modal, Form, message, Popconfirm } from 'ant-design-vue'
import { listTracks } from '../api/track.js'
import { createTitleGenerateTask, listTitleGenerateTasks, cancelTitleGenerateTask, stopTitleGenerateTask, getTaskTitles } from '../api/titleGenerate.js'
import { listPromptTemplates } from '../api/promptTemplate.js'

const router = useRouter()

function goToTitleLibrary() {
  router.push('/title-library')
}

// ---- 生成弹框状态 ----
const GENERATE_CONFIG_KEY = 'titleGenerate_generateConfig'
const savedGenerateConfig = JSON.parse(localStorage.getItem(GENERATE_CONFIG_KEY) || 'null')

const generateModalOpen = ref(false)
const generateCount = ref(savedGenerateConfig?.count || 3)
const generateOutputPath = ref(savedGenerateConfig?.outputPath || '')
const generatePlatforms = ref(savedGenerateConfig?.platforms || [])
const generateTrackIds = ref(savedGenerateConfig?.trackIds || [])
const generateInstruction = ref(savedGenerateConfig?.instruction || '')
const generating = ref(false)
const instructionMode = ref('manual')
const promptTemplates = ref([])
const selectedPromptId = ref('')
const styleTemplates = ref([])
const selectedStyleId = ref(savedGenerateConfig?.styleId || '')

const platformOptions = [
  { label: '公众号', value: '公众号' },
  { label: '今日头条', value: '今日头条' },
  { label: '百家号', value: '百家号' },
]

const tracks = ref([])

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

function saveGenerateConfig() {
  localStorage.setItem(GENERATE_CONFIG_KEY, JSON.stringify({
    count: generateCount.value,
    outputPath: generateOutputPath.value,
    platforms: generatePlatforms.value,
    trackIds: generateTrackIds.value,
    instruction: generateInstruction.value,
    styleId: selectedStyleId.value,
  }))
}

async function openGenerateModal() {
  generateModalOpen.value = true
  instructionMode.value = 'manual'
  selectedPromptId.value = ''
  try {
    const res = await listPromptTemplates({ type: 'generate_title' })
    promptTemplates.value = (res || []).filter(p => p.type === 'generate_title')
  } catch (e) {
    promptTemplates.value = []
  }
  try {
    const res2 = await listPromptTemplates({ type: 'title_style' })
    styleTemplates.value = (res2 || []).filter(p => p.type === 'title_style')
  } catch (e) {
    styleTemplates.value = []
  }
}

function onPromptChange(val) {
  const tpl = promptTemplates.value.find(p => p.id === val)
  if (tpl) {
    generateInstruction.value = tpl.content
  } else {
    generateInstruction.value = ''
  }
}

function onPlatformChange() {
  if (!generatePlatforms.value || generatePlatforms.value.length === 0) {
    return
  }
  generateTrackIds.value = generateTrackIds.value.filter(trackId => {
    const track = tracks.value.find(t => t.id === trackId)
    if (!track || !track.platforms) return false
    const trackPlatforms = track.platforms.split(',')
    return generatePlatforms.value.some(p => trackPlatforms.includes(p))
  })
}

async function handleGenerate() {
  if (!generateCount.value || generateCount.value < 1) {
    message.warning('请输入有效的生成数量')
    return
  }
  generating.value = true
  try {
    const result = await createTitleGenerateTask({
      countPerCombo: parseInt(generateCount.value),
      outputPath: generateOutputPath.value.trim(),
      platforms: generatePlatforms.value,
      trackIds: generateTrackIds.value,
      instruction: generateInstruction.value.trim(),
      styleTemplateId: selectedStyleId.value || undefined,
    })
    generateModalOpen.value = false
    saveGenerateConfig()
    message.success(result.message || '生成任务已创建')
    fetchTasks()
  } catch (e) {
    message.error('提交失败: ' + (e?.response?.data?.msg || e?.message || '未知错误'))
  } finally {
    generating.value = false
  }
}

// ---- 任务列表状态 ----
const tasks = ref([])
const loading = ref(false)
const statusFilter = ref('')

// ---- 详情弹框状态 ----
const detailModalOpen = ref(false)
const detailTaskId = ref('')
const detailTitles = ref([])
const detailLoading = ref(false)

async function openDetailModal(record) {
  detailTaskId.value = record.id
  detailModalOpen.value = true
  detailLoading.value = true
  try {
    const res = await getTaskTitles(record.id)
    detailTitles.value = res || []
  } catch (e) {
    message.error('加载详情失败: ' + (e?.response?.data?.msg || e?.message || '未知错误'))
    detailTitles.value = []
  } finally {
    detailLoading.value = false
  }
}

const statusOptions = [
  { label: '全部', value: '' },
  { label: '排队中', value: 'pending' },
  { label: '进行中', value: 'processing' },
  { label: '已停止', value: 'stopped' },
  { label: '已完成', value: 'completed' },
  { label: '失败', value: 'failed' },
]

const stepItems = [
  { title: '准备数据' },
  { title: '大模型生成' },
  { title: '解析入库' },
  { title: '生成Excel' },
  { title: '完成' },
]

function fetchTasks() {
  loading.value = true
  listTitleGenerateTasks({
    status: statusFilter.value || undefined,
  })
    .then(res => {
      tasks.value = res?.list || []
    })
    .catch(() => {
      message.error('加载生成标题任务失败')
    })
    .finally(() => {
      loading.value = false
    })
}

function onStatusChange() {
  fetchTasks()
}

function getStatusTag(status, step) {
  switch (status) {
    case 'pending':
      return { color: 'blue', text: '排队中' }
    case 'processing':
      return { color: 'orange', text: step ? (step === 1 ? '准备数据' : step === 2 ? '大模型生成' : step === 3 ? '解析入库' : step === 4 ? '生成Excel' : '处理中') : '进行中' }
    case 'stopped':
      return { color: 'default', text: '已停止' }
    case 'completed':
      return { color: 'green', text: '已完成' }
    case 'failed':
      return { color: 'red', text: '失败' }
    default:
      return { color: 'default', text: status }
  }
}

async function handleCancel(record) {
  try {
    await cancelTitleGenerateTask(record.id)
    message.success('任务已取消')
    fetchTasks()
  } catch (e) {
    message.error(e?.response?.data?.msg || '取消失败')
  }
}

async function handleRerun(record) {
  try {
    generatePlatforms.value = record.platforms ? JSON.parse(record.platforms) : []
  } catch (e) { generatePlatforms.value = [] }
  try {
    generateTrackIds.value = record.trackIds ? JSON.parse(record.trackIds) : []
  } catch (e) { generateTrackIds.value = [] }
  generateCount.value = record.countPerCombo || 3
  generateInstruction.value = record.instruction || ''
  selectedStyleId.value = record.styleTemplateId || ''
  generateOutputPath.value = ''
  await openGenerateModal()
  message.info('已载入上次任务配置，请确认后点击生成')
}

async function handleStop(record) {
  try {
    await stopTitleGenerateTask(record.id)
    message.success('任务已停止')
    fetchTasks()
  } catch (e) {
    message.error(e?.response?.data?.msg || '停止失败')
  }
}

const columns = [
  {
    title: '任务ID',
    dataIndex: 'id',
    key: 'id',
    width: 220,
    ellipsis: true,
  },
  {
    title: '状态',
    key: 'status',
    width: 110,
    customRender: ({ record }) => {
      const tag = getStatusTag(record.status, record.progressStep)
      return h(Tag, { color: tag.color }, () => tag.text)
    },
  },
  {
    title: '平台-赛道',
    key: 'platformTrack',
    width: 200,
    ellipsis: true,
    customRender: ({ record }) => {
      let platforms = []
      let trackIds = []
      try {
        platforms = record.platforms ? JSON.parse(record.platforms) : []
      } catch (e) { platforms = [] }
      try {
        trackIds = record.trackIds ? JSON.parse(record.trackIds) : []
      } catch (e) { trackIds = [] }
      if (platforms.length === 0 && trackIds.length === 0) {
        return h('span', { style: 'color: #999;' }, '全部')
      }
      const items = []
      // 笛卡尔积展示平台-赛道组合
      if (trackIds.length === 0) {
        platforms.forEach(p => items.push(p))
      } else if (platforms.length === 0) {
        trackIds.forEach(tid => {
          const t = tracks.value.find(tr => tr.id === tid)
          items.push(t ? t.name : tid)
        })
      } else {
        platforms.forEach(p => {
          trackIds.forEach(tid => {
            const t = tracks.value.find(tr => tr.id === tid)
            items.push(`${p}-${t ? t.name : tid}`)
          })
        })
      }
      return h('span', { title: items.join('、') }, items.join('、'))
    },
  },
  {
    title: '进度',
    key: 'progress',
    width: 320,
    customRender: ({ record }) => {
      const status = record.status
      const step = record.progressStep || 0
      const msg = record.progressMessage || ''

      const config = {
        pending:    { color: '#1890ff', bg: '#e6f7ff', tag: 'blue',    text: '排队中' },
        processing: { color: '#fa8c16', bg: '#fff7e6', tag: 'orange',  text: stepItems[Math.min(step - 1, 4)]?.title || '处理中' },
        stopped:    { color: '#bfbfbf', bg: '#f5f5f5', tag: 'default', text: '已停止' },
        completed:  { color: '#52c41a', bg: '#f6ffed', tag: 'success', text: '已完成' },
        failed:     { color: '#ff4d4f', bg: '#fff2f0', tag: 'error',   text: '失败' },
      }[status] || config.pending

      const percent = status === 'completed' ? 100 : Math.min(Math.round((step / 5) * 100), 95)

      return h('div', { style: 'min-width: 180px;' }, [
        h('div', { style: 'display: flex; align-items: center; gap: 8px; margin-bottom: 6px;' }, [
          h(Tag, { color: config.tag, style: 'font-size: 12px; line-height: 18px; padding: 0 6px; margin: 0;' }, () => config.text),
          h('span', { style: 'font-size: 12px; color: #888; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 140px;' }, msg),
        ]),
        h('div', { style: `width: 100%; height: 6px; background: ${config.bg}; border-radius: 3px; overflow: hidden;` }, [
          h('div', { style: `height: 100%; width: ${percent}%; background: ${config.color}; border-radius: 3px; transition: width 0.3s ease;` }),
        ]),
      ])
    },
  },
  {
    title: '重复数',
    dataIndex: 'duplicateCount',
    key: 'duplicateCount',
    width: 80,
    align: 'center',
    customRender: ({ record }) => {
      const val = record.duplicateCount
      return val != null && val > 0 ? h('span', { style: 'color: #ff4d4f; font-weight: 500;' }, val) : h('span', { style: 'color: #999;' }, '0')
    },
  },
  {
    title: '插入数',
    dataIndex: 'insertedCount',
    key: 'insertedCount',
    width: 80,
    align: 'center',
    customRender: ({ record }) => {
      const val = record.insertedCount
      return val != null && val > 0 ? h('span', { style: 'color: #52c41a; font-weight: 500;' }, val) : h('span', { style: 'color: #999;' }, '0')
    },
  },
  {
    title: '创建时间',
    dataIndex: 'createdAt',
    key: 'createdAt',
    width: 160,
  },
  {
    title: '操作',
    key: 'action',
    width: 140,
    fixed: 'right',
    align: 'center',
    customRender: ({ record }) => {
      const buttons = []
      buttons.push(h(Button, { type: 'link', size: 'small', onClick: () => openDetailModal(record) }, () => '详情'))
      if (record.status === 'pending') {
        buttons.push(h(Popconfirm, {
          title: '确定取消该任务吗？',
          onConfirm: () => handleCancel(record),
        }, () => h(Button, { type: 'link', size: 'small', danger: true }, () => '取消')))
      }
      if (record.status === 'processing') {
        buttons.push(h(Popconfirm, {
          title: '确定停止该任务吗？',
          onConfirm: () => handleStop(record),
        }, () => h(Button, { type: 'link', size: 'small', danger: true }, () => '停止')))
      }
      if (record.status === 'completed' && record.resultFileUrl) {
        buttons.push(h('a', { href: record.resultFileUrl, target: '_blank', rel: 'noopener noreferrer', style: 'font-size: 13px;' }, () => '下载结果'))
      }
      buttons.push(h(Button, { type: 'link', size: 'small', onClick: () => handleRerun(record) }, () => '再跑一次'))
      return h('div', { style: 'display: flex; justify-content: center; align-items: center; gap: 8px; flex-wrap: wrap;' }, buttons)
    },
  },
]

onMounted(() => {
  listTracks().then(res => {
    tracks.value = res || []
  }).catch(() => {})
  fetchTasks()
  const interval = setInterval(fetchTasks, 5000)
  onUnmounted(() => clearInterval(interval))
})
</script>

<template>
  <div>
    <div style="display: flex; gap: 12px; margin-bottom: 16px;">
      <Button type="primary" @click="openGenerateModal">生成标题</Button>
      <Button @click="goToTitleLibrary">标题库</Button>
      <Select
        v-model:value="statusFilter"
        :options="statusOptions"
        style="width: 140px; margin-left: auto;"
        placeholder="状态筛选"
        @change="onStatusChange"
      />
    </div>

    <Table
      :columns="columns"
      :dataSource="tasks"
      :loading="loading"
      rowKey="id"
      :pagination="{ pageSize: 20 }"
      size="small"
      :scroll="{ x: 1200 }"
    />

    <Modal
      v-model:open="generateModalOpen"
      title="生成标题（V2-大模型）"
      :mask-closable="false"
      :confirm-loading="generating"
      @ok="handleGenerate"
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
            4. 生成结果包含标题、平台、赛道名称和文章写作思路（用于后续文章撰写）
          </div>
        </div>
        <Form.Item label="选择平台">
          <Select
            show-search
            v-model:value="generatePlatforms"
            mode="multiple"
            placeholder="不选则生成全部平台"
            style="width: 100%;"
            @change="onPlatformChange"
          >
            <Select.Option v-for="p in platformOptions" :key="p.value" :value="p.value" :label="p.label">{{ p.label }}</Select.Option>
          </Select>
        </Form.Item>
        <Form.Item label="选择赛道">
          <Select
            show-search
            v-model:value="generateTrackIds"
            mode="multiple"
            placeholder="不选则生成全部赛道"
            style="width: 100%;"
          >
            <Select.Option v-for="t in filteredTracksForGenerate" :key="t.id" :value="t.id" :label="t.name">{{ t.name }}</Select.Option>
          </Select>
        </Form.Item>
        <Form.Item label="每个组合生成数量" required>
          <Input v-model:value="generateCount" type="number" min="1" max="20" placeholder="例如：3" />
        </Form.Item>
        <Form.Item label="标题风格（可选）">
          <Select
            v-model:value="selectedStyleId"
            placeholder="不选则使用默认风格"
            style="width: 100%;"
            allowClear
          >
            <Select.Option v-for="s in styleTemplates" :key="s.id" :value="s.id">{{ s.name }}</Select.Option>
          </Select>
          <div style="font-size: 12px; color: #999; margin-top: 4px;">选择后，AI 将严格按照该风格生成标题</div>
        </Form.Item>
        <Form.Item label="生成方向（可选）">
          <div style="display: flex; gap: 12px; margin-bottom: 8px;">
            <label style="font-size: 13px; cursor: pointer; display: flex; align-items: center; gap: 4px;">
              <input type="radio" v-model="instructionMode" value="manual" />
              手动输入
            </label>
            <label style="font-size: 13px; cursor: pointer; display: flex; align-items: center; gap: 4px;">
              <input type="radio" v-model="instructionMode" value="template" />
              选择提示词模板
            </label>
          </div>
          <div v-if="instructionMode === 'template'">
            <Select
              v-model:value="selectedPromptId"
              placeholder="请选择提示词模板"
              style="width: 100%;"
              @change="onPromptChange"
            >
              <Select.Option v-for="p in promptTemplates" :key="p.id" :value="p.id">{{ p.name }}</Select.Option>
            </Select>
          </div>
          <Input.TextArea
            v-model:value="generateInstruction"
            placeholder="例如：更口语化、更具悬念、突出数字效果、适合抖音风格、偏新闻报道类..."
            :rows="4"
            :maxlength="2000"
            show-count
            :disabled="instructionMode === 'template'"
          />
          <div style="font-size: 12px; color: #999; margin-top: 4px;">不填则使用默认策略生成</div>
        </Form.Item>
      </Form>
    </Modal>

    <Modal
      v-model:open="detailModalOpen"
      :title="`任务详情 (${detailTaskId})`"
      :footer="null"
      :width="720"
    >
      <div style="margin-top: 12px;">
        <Table
          :dataSource="detailTitles"
          :loading="detailLoading"
          rowKey="id"
          size="small"
          :pagination="{ pageSize: 10 }"
          :scroll="{ y: 400 }"
        >
          <Table.Column title="标题" dataIndex="title" key="title" ellipsis />
          <Table.Column title="平台" dataIndex="platform" key="platform" width="100" />
          <Table.Column title="赛道" dataIndex="trackName" key="trackName" width="140" />
          <Table.Column title="写作思路" dataIndex="description" key="description" ellipsis />
          <Table.Column title="创建时间" dataIndex="createdAt" key="createdAt" width="160" />
        </Table>
        <div v-if="!detailLoading && detailTitles.length === 0" style="text-align: center; padding: 24px; color: #999;">
          该任务暂无生成的标题
        </div>
      </div>
    </Modal>
  </div>
</template>
