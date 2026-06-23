<script setup>
import { ref, onMounted, computed } from 'vue'
import { Table, Button, Input, Modal, Form, Switch, message, Popconfirm, Card, Row, Col, Slider, Space, Tag } from 'ant-design-vue'
import { listExportTemplates, saveExportTemplate, deleteExportTemplate, setDefaultExportTemplate } from '../api/exportTemplate.js'

const templates = ref([])
const loading = ref(false)
const keyword = ref('')

const modalOpen = ref(false)
const modalTitle = ref('')
const formRef = ref()
const defaultConfig = {
  fontFamily: '微软雅黑',
  headingFontFamily: '微软雅黑',
  bodyFontSizePt: 12,
  headingFontSizePt: 16,
  bodyColor: '#262626',
  headingColor: '#07c160',
  lineSpacing: 360,
  paragraphSpacingAfter: 200,
  marginTop: 1440,
  marginBottom: 1440,
  marginLeft: 1800,
  marginRight: 1800,
  quoteBg: '#e6f7ff',
  previewColor: '#07c160',
  description: '',
}
const form = ref({
  id: '',
  name: '',
  type: 'docx',
  config: { ...defaultConfig },
  isDefault: 0,
})

const rules = {
  name: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
}

const filteredList = computed(() => {
  if (!keyword.value.trim()) return templates.value
  const kw = keyword.value.trim().toLowerCase()
  return templates.value.filter(t => t.name && t.name.toLowerCase().includes(kw))
})

function parseConfig(tpl) {
  try {
    return tpl.config ? JSON.parse(tpl.config) : { ...defaultConfig }
  } catch (e) {
    return { ...defaultConfig }
  }
}

function fetchList() {
  loading.value = true
  listExportTemplates()
    .then(res => {
      templates.value = (res || []).map(t => ({ ...t, configObj: parseConfig(t) }))
    })
    .catch(() => {
      message.error('加载导出模板失败')
    })
    .finally(() => {
      loading.value = false
    })
}

function openCreate() {
  modalTitle.value = '新增导出模板'
  form.value = {
    id: '',
    name: '',
    type: 'docx',
    config: { ...defaultConfig },
    isDefault: 0,
  }
  modalOpen.value = true
}

function openEdit(record) {
  modalTitle.value = '编辑导出模板'
  form.value = {
    id: record.id,
    name: record.name,
    type: record.type || 'docx',
    config: { ...defaultConfig, ...record.configObj },
    isDefault: record.isDefault === 1 ? 1 : 0,
  }
  modalOpen.value = true
}

async function handleModalOk() {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  const payload = {
    id: form.value.id || undefined,
    name: form.value.name,
    type: form.value.type || 'docx',
    config: JSON.stringify(form.value.config),
    isDefault: form.value.isDefault,
  }
  try {
    await saveExportTemplate(payload)
    message.success(form.value.id ? '修改成功' : '新增成功')
    modalOpen.value = false
    fetchList()
  } catch (e) {
    message.error(e?.message || '保存失败')
  }
}

async function handleDelete(record) {
  try {
    await deleteExportTemplate(record.id)
    message.success('删除成功')
    fetchList()
  } catch (e) {
    message.error(e?.message || '删除失败')
  }
}

async function handleSetDefault(record) {
  try {
    await setDefaultExportTemplate(record.id)
    message.success('已设为默认')
    fetchList()
  } catch (e) {
    message.error(e?.message || '设置失败')
  }
}

function handleImportJson(file) {
  const reader = new FileReader()
  reader.onload = e => {
    try {
      const data = JSON.parse(e.target.result)
      form.value.config = { ...defaultConfig, ...data }
      if (data.name && !form.value.name) {
        form.value.name = data.name
      }
      message.success('导入成功')
    } catch (err) {
      message.error('JSON 解析失败')
    }
  }
  reader.readAsText(file)
  return false
}

function handleExportJson() {
  const data = { ...form.value.config, name: form.value.name }
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = (form.value.name || 'export-template') + '.json'
  a.click()
  URL.revokeObjectURL(url)
}

const columns = [
  { title: '模板名称', dataIndex: 'name', key: 'name' },
  { title: '类型', dataIndex: 'type', key: 'type' },
  { title: '默认', dataIndex: 'isDefault', key: 'isDefault', width: 100 },
  { title: '操作', key: 'action', width: 220 },
]

onMounted(fetchList)
</script>

<template>
  <div class="export-template-manage">
    <div class="toolbar">
      <Input v-model:value="keyword" placeholder="搜索模板名称" style="width: 240px" />
      <Button type="primary" @click="openCreate">新增模板</Button>
    </div>

    <Table :dataSource="filteredList" :columns="columns" :loading="loading" rowKey="id" bordered>
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'isDefault'">
          <Tag v-if="record.isDefault === 1" color="green">是</Tag>
          <span v-else>否</span>
        </template>
        <template v-if="column.key === 'action'">
          <Space>
            <Button type="link" size="small" @click="openEdit(record)">编辑</Button>
            <Button v-if="record.isDefault !== 1" type="link" size="small" @click="handleSetDefault(record)">设默认</Button>
            <Popconfirm title="确认删除？" @confirm="handleDelete(record)">
              <Button type="link" danger size="small">删除</Button>
            </Popconfirm>
          </Space>
        </template>
      </template>
    </Table>

    <Modal v-model:open="modalOpen" :title="modalTitle" width="960px" @ok="handleModalOk">
      <Row :gutter="16">
        <Col :span="12">
          <Form ref="formRef" :model="form" :rules="rules" layout="vertical">
            <Form.Item label="模板名称" name="name">
              <Input v-model:value="form.name" placeholder="如：公众号标准模板" />
            </Form.Item>
            <Form.Item label="默认模板">
              <Switch v-model:checked="form.isDefault" :checkedValue="1" :unCheckedValue="0" />
            </Form.Item>

            <Card title="排版样式" size="small">
              <Row :gutter="8">
                <Col :span="12">
                  <Form.Item label="正文字体">
                    <Input v-model:value="form.config.fontFamily" />
                  </Form.Item>
                </Col>
                <Col :span="12">
                  <Form.Item label="标题字体">
                    <Input v-model:value="form.config.headingFontFamily" />
                  </Form.Item>
                </Col>
              </Row>
              <Row :gutter="8">
                <Col :span="12">
                  <Form.Item label="正文字号 (pt)">
                    <Slider v-model:value="form.config.bodyFontSizePt" :min="10" :max="20" />
                  </Form.Item>
                </Col>
                <Col :span="12">
                  <Form.Item label="标题字号 (pt)">
                    <Slider v-model:value="form.config.headingFontSizePt" :min="12" :max="24" />
                  </Form.Item>
                </Col>
              </Row>
              <Row :gutter="8">
                <Col :span="12">
                  <Form.Item label="正文颜色">
                    <Input v-model:value="form.config.bodyColor">
                      <template #suffix>
                        <input type="color" v-model="form.config.bodyColor" style="width: 24px; height: 24px; border: none; padding: 0; cursor: pointer;">
                      </template>
                    </Input>
                  </Form.Item>
                </Col>
                <Col :span="12">
                  <Form.Item label="标题颜色">
                    <Input v-model:value="form.config.headingColor">
                      <template #suffix>
                        <input type="color" v-model="form.config.headingColor" style="width: 24px; height: 24px; border: none; padding: 0; cursor: pointer;">
                      </template>
                    </Input>
                  </Form.Item>
                </Col>
              </Row>
              <Row :gutter="8">
                <Col :span="12">
                  <Form.Item label="行距 (twips/240)">
                    <Slider v-model:value="form.config.lineSpacing" :min="240" :max="600" :step="20" />
                    <div style="font-size: 12px; color: #888;">{{ (form.config.lineSpacing / 240).toFixed(2) }} 倍</div>
                  </Form.Item>
                </Col>
                <Col :span="12">
                  <Form.Item label="段后距 (twips)">
                    <Slider v-model:value="form.config.paragraphSpacingAfter" :min="0" :max="400" :step="20" />
                  </Form.Item>
                </Col>
              </Row>
              <Row :gutter="8">
                <Col :span="12">
                  <Form.Item label="上边距 (twips)">
                    <Input v-model:value="form.config.marginTop" type="number" />
                  </Form.Item>
                </Col>
                <Col :span="12">
                  <Form.Item label="下边距 (twips)">
                    <Input v-model:value="form.config.marginBottom" type="number" />
                  </Form.Item>
                </Col>
              </Row>
              <Row :gutter="8">
                <Col :span="12">
                  <Form.Item label="左边距 (twips)">
                    <Input v-model:value="form.config.marginLeft" type="number" />
                  </Form.Item>
                </Col>
                <Col :span="12">
                  <Form.Item label="右边距 (twips)">
                    <Input v-model:value="form.config.marginRight" type="number" />
                  </Form.Item>
                </Col>
              </Row>
              <Form.Item label="引用背景色">
                <Input v-model:value="form.config.quoteBg">
                  <template #suffix>
                    <input type="color" v-model="form.config.quoteBg" style="width: 24px; height: 24px; border: none; padding: 0; cursor: pointer;">
                  </template>
                </Input>
              </Form.Item>
              <Form.Item label="描述">
                <Input.TextArea v-model:value="form.config.description" :rows="2" placeholder="用于提示词注入和卡片展示" />
              </Form.Item>
            </Card>

            <div style="margin-top: 12px;">
              <Space>
                <Button size="small" @click="handleExportJson">导出 JSON</Button>
                <a-upload :beforeUpload="handleImportJson" accept=".json" :showUploadList="false">
                  <Button size="small">导入 JSON</Button>
                </a-upload>
              </Space>
            </div>
          </Form>
        </Col>
        <Col :span="12">
          <Card title="实时预览" size="small">
            <div class="preview-article"
                 :style="{
                   fontFamily: form.config.fontFamily,
                   fontSize: form.config.bodyFontSizePt * 1.33 + 'px',
                   color: form.config.bodyColor,
                   lineHeight: form.config.lineSpacing / 240,
                   background: '#fff',
                   padding: '16px',
                   borderRadius: '4px',
                   border: '1px solid #eee'
                 }">
              <h1 :style="{ fontFamily: form.config.headingFontFamily, fontSize: form.config.headingFontSizePt * 1.33 + 'px', color: form.config.headingColor, marginBottom: form.config.paragraphSpacingAfter / 20 * 1.33 + 'px' }">
                示例文章标题
              </h1>
              <p :style="{ marginBottom: form.config.paragraphSpacingAfter / 20 * 1.33 + 'px' }">
                这是一段示例正文，用于预览当前导出模板的字体、字号、颜色和行距效果。你可以通过左侧表单实时调整样式配置。
              </p>
              <h2 :style="{ fontFamily: form.config.headingFontFamily, fontSize: form.config.headingFontSizePt * 1.1 * 1.33 + 'px', color: form.config.headingColor, marginTop: '20px', marginBottom: form.config.paragraphSpacingAfter / 20 * 1.33 + 'px' }">
                01 | 小节标题示例
              </h2>
              <p :style="{ marginBottom: form.config.paragraphSpacingAfter / 20 * 1.33 + 'px' }">
                小节正文示例。好的排版能让阅读体验提升一个档次，让读者更愿意读完、收藏和转发。
              </p>
              <blockquote :style="{ background: form.config.quoteBg, borderLeft: '4px solid ' + form.config.headingColor, padding: '12px 16px', margin: '16px 0' }">
                这是一段引用文本，背景色和左边框会随模板配置变化。
              </blockquote>
            </div>
          </Card>
        </Col>
      </Row>
    </Modal>
  </div>
</template>

<style scoped>
.export-template-manage {
  padding: 16px;
}
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
</style>
