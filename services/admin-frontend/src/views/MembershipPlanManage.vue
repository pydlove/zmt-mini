<script setup>
import { ref, onMounted } from 'vue'
import { Card, Table, Button, Modal, Form, Input, InputNumber, Switch, message, Tag, Checkbox } from 'ant-design-vue'
import { listMembershipPlans, saveMembershipPlan, deleteMembershipPlan } from '../api/membershipPlan.js'
import { listExportTemplates } from '../api/exportTemplate.js'

const plans = ref([])
const modalOpen = ref(false)
const editingId = ref(null)
const formRef = ref()
const allTemplates = ref([])

const ALL_PLATFORMS = ['公众号', '今日头条', '百家号']

const form = ref({
  name: '',
  price: 0,
  originalPrice: 0,
  features: [],
  featureInput: '',
  trackLimit: 0,
  platformLimit: '',
  expireDays: 0,
  permissionsPlatformCount: 0,
  permissionsTemplates: [],
  permissionsEmailPush: false,
  permissionsOnlinePreview: false,
  permissionsGuideAccess: false,
  sortOrder: 0,
  isActive: true,
})

const columns = [
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '现价', dataIndex: 'price', key: 'price', customRender: ({ text }) => '¥' + text },
  { title: '原价', dataIndex: 'originalPrice', key: 'originalPrice', customRender: ({ text }) => text ? '¥' + text : '-' },
  { title: '权益', dataIndex: 'featuresJson', key: 'features', width: 240 },
  { title: '限额', key: 'limits', width: 180 },
  { title: '排序', dataIndex: 'sortOrder', key: 'sortOrder', width: 80 },
  { title: '状态', dataIndex: 'isActive', key: 'isActive', width: 80 },
  { title: '操作', key: 'action', width: 160 },
]

function parseFeatures(plan) {
  try {
    return plan.featuresJson ? JSON.parse(plan.featuresJson) : []
  } catch (e) {
    return []
  }
}

function parsePermissions(plan) {
  try {
    return plan.permissionsJson ? JSON.parse(plan.permissionsJson) : {}
  } catch (e) {
    return {}
  }
}

async function loadTemplates() {
  try {
    const data = await listExportTemplates()
    allTemplates.value = (data || []).filter(t => t.isDeleted !== 1)
  } catch (e) {
    // silent
  }
}

async function loadData() {
  try {
    const data = await listMembershipPlans()
    plans.value = data || []
  } catch (e) {
    message.error('加载失败')
  }
}

function openModal(plan) {
  modalOpen.value = true
  editingId.value = plan ? plan.id : null
  loadTemplates()
  const perms = plan ? parsePermissions(plan) : {}
  if (plan) {
    const features = parseFeatures(plan)
    form.value = {
      name: plan.name || '',
      price: plan.price || 0,
      originalPrice: plan.originalPrice || 0,
      features: [...features],
      featureInput: '',
      trackLimit: plan.trackLimit ?? 0,
      platformLimit: plan.platformLimit || '',
      expireDays: plan.expireDays ?? 0,
      permissionsPlatformCount: perms.platformCount ?? 0,
      permissionsTemplates: perms.templates || [],
      permissionsEmailPush: !!perms.emailPush,
      permissionsOnlinePreview: !!perms.onlinePreview,
      permissionsGuideAccess: !!perms.guideAccess,
      sortOrder: plan.sortOrder || 0,
      isActive: plan.isActive === 1,
    }
  } else {
    form.value = {
      name: '',
      price: 0,
      originalPrice: 0,
      features: [],
      featureInput: '',
      trackLimit: 0,
      aiLimit: 0,
      platformLimit: '',
      expireDays: 0,
      permissionsPlatformCount: 0,
      permissionsTemplates: [],
      permissionsEmailPush: false,
      permissionsOnlinePreview: false,
      permissionsGuideAccess: false,
      sortOrder: 0,
      isActive: true,
    }
  }
}

function addFeature() {
  const text = form.value.featureInput.trim()
  if (!text) return
  form.value.features.push(text)
  form.value.featureInput = ''
}

function removeFeature(index) {
  form.value.features.splice(index, 1)
}

function moveFeatureUp(index) {
  if (index <= 0) return
  const arr = form.value.features
  const temp = arr[index]
  arr[index] = arr[index - 1]
  arr[index - 1] = temp
}

function moveFeatureDown(index) {
  const arr = form.value.features
  if (index >= arr.length - 1) return
  const temp = arr[index]
  arr[index] = arr[index + 1]
  arr[index + 1] = temp
}

const editingFeatureIndex = ref(-1)
const editingFeatureText = ref('')

function startEditFeature(index) {
  editingFeatureIndex.value = index
  editingFeatureText.value = form.value.features[index]
}

function saveFeatureEdit() {
  const text = editingFeatureText.value.trim()
  if (!text) {
    message.warning('权益内容不能为空')
    return
  }
  if (editingFeatureIndex.value >= 0) {
    form.value.features[editingFeatureIndex.value] = text
  }
  editingFeatureIndex.value = -1
  editingFeatureText.value = ''
}

function cancelFeatureEdit() {
  editingFeatureIndex.value = -1
  editingFeatureText.value = ''
}

async function handleSave() {
  if (!form.value.name) {
    message.warning('请填写套餐名称')
    return
  }
  try {
    const permissions = {
      platformCount: form.value.permissionsPlatformCount ?? 0,
      templates: form.value.permissionsTemplates || [],
      emailPush: form.value.permissionsEmailPush,
      onlinePreview: form.value.permissionsOnlinePreview,
      guideAccess: form.value.permissionsGuideAccess,
    }
    const payload = {
      id: editingId.value || undefined,
      name: form.value.name,
      price: form.value.price ?? 0,
      originalPrice: form.value.originalPrice ?? 0,
      featuresJson: JSON.stringify(form.value.features),
      trackLimit: form.value.trackLimit ?? 0,
      platformLimit: form.value.platformLimit || '',
      expireDays: form.value.expireDays ?? 0,
      permissionsJson: JSON.stringify(permissions),
      sortOrder: form.value.sortOrder ?? 0,
      isActive: form.value.isActive ? 1 : 0,
    }
    await saveMembershipPlan(payload)
    message.success((editingId.value ? '编辑' : '新增') + '成功')
    modalOpen.value = false
    loadData()
  } catch (e) {
    message.error('保存失败')
  }
}

function handleDelete(plan) {
  Modal.confirm({
    title: '确认删除该套餐？',
    content: '删除后不可恢复。',
    async onOk() {
      try {
        await deleteMembershipPlan(plan.id)
        message.success('删除成功')
        loadData()
      } catch (e) {
        message.error('删除失败')
      }
    },
  })
}

onMounted(loadData)
</script>

<template>
  <Card :body-style="{ padding: '24px' }" style="border-radius: 2px;">
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px;">
      <div style="font-size: 16px; font-weight: 500; color: #262626;">会员权益管理</div>
      <Button type="primary" @click="openModal(null)">+ 新增套餐</Button>
    </div>

    <Table :dataSource="plans" :columns="columns" rowKey="id" size="middle" :pagination="false">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'features'">
          <div style="display: flex; flex-wrap: wrap; gap: 4px;">
            <Tag v-for="(f, i) in parseFeatures(record)" :key="i" color="green" size="small">{{ f }}</Tag>
          </div>
        </template>
        <template v-if="column.key === 'limits'">
          <div style="font-size: 12px; color: #595959; line-height: 1.6;">
            <div>赛道: {{ record.trackLimit || '-' }}</div>
            <div>平台: {{ record.platformLimit || '-' }}</div>
            <div>有效期: {{ record.expireDays ? record.expireDays + '天' : '-' }}</div>
          </div>
        </template>
        <template v-if="column.key === 'isActive'">
          <Tag :color="record.isActive === 1 ? 'green' : 'red'">{{ record.isActive === 1 ? '启用' : '禁用' }}</Tag>
        </template>
        <template v-if="column.key === 'action'">
          <div style="display: flex; gap: 12px;">
            <a @click="openModal(record)">编辑</a>
            <a style="color: #f5222d;" @click="handleDelete(record)">删除</a>
          </div>
        </template>
      </template>
    </Table>
  </Card>

  <Modal v-model:open="modalOpen" :title="editingId ? '编辑套餐' : '新增套餐'" :mask-closable="false" @ok="handleSave" width="560">
    <Form layout="vertical" style="margin-top: 12px;">
      <Form.Item label="套餐名称" required>
        <Input v-model:value="form.name" placeholder="如：基础版" />
      </Form.Item>
      <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px;">
        <Form.Item label="现价（元/月）" required>
          <InputNumber v-model:value="form.price" :min="0" :precision="2" style="width: 100%;" />
        </Form.Item>
        <Form.Item label="原价（元/月）">
          <InputNumber v-model:value="form.originalPrice" :min="0" :precision="2" style="width: 100%;" />
        </Form.Item>
      </div>
      <Form.Item label="权益列表">
        <div style="display: flex; gap: 8px; margin-bottom: 8px;">
          <Input v-model:value="form.featureInput" placeholder="输入权益后按回车添加" @pressEnter="addFeature" />
          <Button @click="addFeature">添加</Button>
        </div>
        <div style="display: flex; flex-direction: column; gap: 6px;">
          <div v-for="(f, i) in form.features" :key="i" style="display: flex; align-items: center; gap: 8px; padding: 6px 10px; background: #f6ffed; border: 1px solid #b7eb8f; border-radius: 4px;">
            <template v-if="editingFeatureIndex === i">
              <Input v-model:value="editingFeatureText" size="small" style="flex: 1;" @pressEnter="saveFeatureEdit" />
              <a style="font-size: 12px; color: #1890ff;" @click="saveFeatureEdit">保存</a>
              <a style="font-size: 12px; color: #8c8c8c;" @click="cancelFeatureEdit">取消</a>
            </template>
            <template v-else>
              <span style="flex: 1; font-size: 14px; color: #262626;">{{ f }}</span>
              <a style="font-size: 12px; color: #1890ff;" @click="startEditFeature(i)">编辑</a>
              <a style="font-size: 12px; color: #8c8c8c;" :style="i <= 0 ? { visibility: 'hidden' } : {}" @click="moveFeatureUp(i)">上移</a>
              <a style="font-size: 12px; color: #8c8c8c;" :style="i >= form.features.length - 1 ? { visibility: 'hidden' } : {}" @click="moveFeatureDown(i)">下移</a>
              <a style="font-size: 12px; color: #f5222d;" @click="removeFeature(i)">删除</a>
            </template>
          </div>
        </div>
      </Form.Item>
      <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px;">
        <Form.Item label="订阅赛道上限">
          <InputNumber v-model:value="form.trackLimit" :min="0" style="width: 100%;" placeholder="0 为无限制" />
        </Form.Item>
        <Form.Item label="有效天数">
          <InputNumber v-model:value="form.expireDays" :min="0" style="width: 100%;" placeholder="0 为不过期" />
        </Form.Item>
      </div>
      <div style="border: 1px solid #e5e7eb; border-radius: 8px; padding: 16px; margin-bottom: 16px; background: #fafafa;">
        <div style="font-size: 14px; font-weight: 600; color: #262626; margin-bottom: 12px;">权限配置</div>

        <div style="margin-bottom: 12px;">
          <div style="font-size: 13px; font-weight: 500; color: #595959; margin-bottom: 6px;">可用平台数量（0 为不限制）</div>
          <div>
            <InputNumber v-model:value="form.permissionsPlatformCount" :min="0" :max="3" style="width: 100%;" placeholder="如：3" />
          </div>
        </div>

        <div style="margin-bottom: 12px;">
          <div style="font-size: 13px; font-weight: 500; color: #595959; margin-bottom: 6px;">功能开关</div>
          <div style="display: flex; flex-direction: column; gap: 8px;">
            <div><Switch v-model:checked="form.permissionsEmailPush" size="small" /> <span style="margin-left: 6px; font-size: 13px;">邮件每日推送</span></div>
            <div><Switch v-model:checked="form.permissionsOnlinePreview" size="small" /> <span style="margin-left: 6px; font-size: 13px;">在线预览文章</span></div>
            <div><Switch v-model:checked="form.permissionsGuideAccess" size="small" /> <span style="margin-left: 6px; font-size: 13px;">创作技巧与运营干货学习</span></div>
          </div>
        </div>

        <div>
          <div style="font-size: 13px; font-weight: 500; color: #595959; margin-bottom: 6px;">可选导出模板</div>
          <div v-if="allTemplates.length === 0" style="font-size: 12px; color: #999;">暂无模板数据</div>
          <div v-else style="display: flex; flex-wrap: wrap; gap: 12px;">
            <Checkbox v-for="t in allTemplates" :key="t.id" :value="t.name" :checked="form.permissionsTemplates.includes(t.name)" @update:checked="(val) => {
              if (val) { if (!form.permissionsTemplates.includes(t.name)) form.permissionsTemplates.push(t.name) }
              else { form.permissionsTemplates = form.permissionsTemplates.filter(x => x !== t.name) }
            }">{{ t.name }}</Checkbox>
          </div>
        </div>
      </div>

      <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px;">
        <Form.Item label="排序">
          <InputNumber v-model:value="form.sortOrder" :min="0" style="width: 100%;" />
        </Form.Item>
        <Form.Item label="状态">
          <div style="padding-top: 4px;">
            <Switch v-model:checked="form.isActive" checked-children="启用" un-checked-children="禁用" />
          </div>
        </Form.Item>
      </div>
    </Form>
  </Modal>
</template>
