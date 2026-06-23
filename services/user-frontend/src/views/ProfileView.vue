<script setup>
import { ref, onMounted, computed } from 'vue'
import { Card, Tabs, message } from 'ant-design-vue'
import { useViewport } from '../composables/useViewport.js'
import { usePermissions } from '../composables/usePermissions.js'
import { updateAvatar, sendTestEmail } from '../api/user.js'
import { changePassword as changePasswordApi } from '../api/auth.js'
import { useEmailConfig } from '../composables/useEmailConfig.js'
import { Switch } from 'ant-design-vue'
import { CopyOutlined, CheckOutlined } from '@ant-design/icons-vue'

function doCopy(text) {
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.style.cssText = 'position:fixed;left:-9999px;top:0;'
  document.body.appendChild(textarea)
  textarea.focus()
  textarea.select()
  let success = false
  try {
    success = document.execCommand('copy')
  } catch (e) {}
  document.body.removeChild(textarea)
  return success
}

const { isMobile } = useViewport()
const { canEmailPush } = usePermissions()
const { emailConfig, loading: emailLoading, loadEmailConfig, saveEmailConfig } = useEmailConfig()
const activeTab = ref('template')

const user = ref(JSON.parse(localStorage.getItem('user') || '{}'))
const copied = ref(false)

async function copyInviteCode() {
  if (!user.value.inviteCode) return
  const registerUrl = window.location.origin + '/register?invite=' + encodeURIComponent(user.value.inviteCode)
  const text = `推荐你使用知我公众号创作助手！

基于 AI 的公众号爆款文章创作平台，覆盖 36+ 热门赛道，每日智能推荐，让你轻松写出 10w+。

点击链接注册，开启你的爆款创作之旅：
${registerUrl}

注册后请添加客服微信审核，即可使用全部功能。`
  if (doCopy(text)) {
    copied.value = true
    message.success('邀请文案已复制')
    setTimeout(() => { copied.value = false }, 2000)
  } else {
    message.error('复制失败，请手动复制')
  }
}

const isExpired = computed(() => {
  const expire = user.value.expireDate
  if (!expire) return false
  return new Date(expire + 'T23:59:59') < new Date()
})

const fileInput = ref(null)

function triggerAvatarUpload() {
  fileInput.value?.click()
}

async function onAvatarChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    message.warning('请上传图片文件')
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    message.warning('图片大小不能超过 2MB')
    return
  }
  const reader = new FileReader()
  reader.onload = async () => {
    try {
      await updateAvatar(user.value.id, reader.result)
      user.value.avatar = reader.result
      localStorage.setItem('user', JSON.stringify(user.value))
      message.success('头像上传成功')
    } catch (err) {
      message.error('头像上传失败')
    }
  }
  reader.readAsDataURL(file)
  e.target.value = ''
}

const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const testEmailLoading = ref(false)

async function handleSaveEmailConfig() {
  if (!user.value.id) {
    message.error('用户未登录')
    return
  }
  if (!canEmailPush.value) {
    message.warning('您当前的权益暂不支持邮件每日推送')
    return
  }
  if (!emailConfig.value.email) {
    message.warning('请输入邮箱地址')
    return
  }
  try {
    await saveEmailConfig(user.value.id, {
      email: emailConfig.value.email,
      emailReceive: emailConfig.value.emailReceive,
    })
    user.value.email = emailConfig.value.email
    localStorage.setItem('user', JSON.stringify(user.value))
    message.success('邮箱配置已保存')
  } catch (e) {
    message.error('保存失败')
  }
}

async function handleSendTestEmail() {
  if (!user.value.id) {
    message.error('用户未登录')
    return
  }
  if (!emailConfig.value.email) {
    message.warning('请先填写邮箱地址')
    return
  }
  testEmailLoading.value = true
  try {
    await sendTestEmail(user.value.id)
    message.success('测试邮件已发送，请查收')
  } catch (e) {
    message.error(e?.message || '发送失败')
  } finally {
    testEmailLoading.value = false
  }
}

async function changePassword() {
  if (!passwordForm.value.oldPassword || !passwordForm.value.newPassword || !passwordForm.value.confirmPassword) {
    message.warning('请填写完整信息')
    return
  }
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    message.warning('两次输入的新密码不一致')
    return
  }
  try {
    await changePasswordApi({
      userId: user.value.id,
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword,
    })
    message.success('密码修改成功')
    passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  } catch (e) {
    message.error(e?.message || e?.response?.data?.msg || '修改失败')
  }
}

onMounted(() => {
  user.value = JSON.parse(localStorage.getItem('user') || '{}')
  loadEmailConfig(user.value.id)
})
</script>

<template>
  <div :style="{ maxWidth: isMobile ? '100%' : '800px', margin: '0 auto', padding: isMobile ? '0 0 32px' : '0 0 48px' }">
    <Card style="border-radius: 16px; border: 1px solid #f1f5f9; box-shadow: 0 1px 3px rgba(0,0,0,0.04);">
      <!-- Profile Header -->
      <div style="display: flex; align-items: center; justify-content: space-between; padding-bottom: 20px; border-bottom: 1px solid #f1f5f9; margin-bottom: 20px;">
        <div style="display: flex; align-items: center; gap: 16px;">
          <input ref="fileInput" type="file" accept="image/*" style="display: none;" @change="onAvatarChange">
          <div
            @click="triggerAvatarUpload"
            style="width: 64px; height: 64px; border-radius: 50%; background: #e5e7eb; display: flex; align-items: center; justify-content: center; font-size: 24px; color: #6b7280; cursor: pointer; overflow: hidden; position: relative;"
          >
            <img v-if="user.avatar" :src="user.avatar" style="width: 100%; height: 100%; object-fit: cover;">
            <span v-else>U</span>
            <div style="position: absolute; inset: 0; background: rgba(0,0,0,0.3); color: #fff; font-size: 12px; display: flex; align-items: center; justify-content: center; opacity: 0; transition: opacity 0.2s;" @mouseenter="$event.currentTarget.style.opacity = '1'" @mouseleave="$event.currentTarget.style.opacity = '0'">更换头像</div>
          </div>
          <div>
            <div style="font-size: 18px; font-weight: 600; margin-bottom: 4px; color: #111827; display: flex; align-items: center; gap: 8px;">
              {{ user.username || '用户' }}
              <span v-if="isExpired" style="font-size: 11px; padding: 2px 8px; background: #fee2e2; color: #b91c1c; border-radius: 4px; font-weight: 500;">已到期</span>
            </div>
            <div style="font-size: 13px; color: #6b7280;">账号：{{ user.username || '-' }} · 注册时间：{{ user.createdAt ? user.createdAt.slice(0,10) : '-' }}</div>
          </div>
        </div>
      </div>

      <!-- 邀请码 -->
      <div v-if="user.inviteCode" style="display: flex; align-items: center; gap: 12px; padding: 14px 16px; background: #f0f9ff; border: 1px solid #bae6fd; border-radius: 10px; margin-bottom: 20px;">
        <div style="font-size: 13px; color: #0369a1; font-weight: 500; white-space: nowrap;">我的邀请码</div>
        <div style="font-family: monospace; font-size: 16px; font-weight: 600; color: #0284c7; letter-spacing: 2px; flex: 1;">{{ user.inviteCode }}</div>
        <button
          @click="copyInviteCode"
          style="padding: 6px 14px; font-size: 12px; border-radius: 6px; border: 1px solid #7dd3fc; background: #fff; color: #0284c7; cursor: pointer; font-weight: 500; display: flex; align-items: center; gap: 4px;"
        >
          <CopyOutlined v-if="!copied" />
          <CheckOutlined v-else />
          {{ copied ? '已复制' : '复制' }}
        </button>
      </div>

      <Tabs v-model:activeKey="activeTab">
        <Tabs.TabPane key="template" tab="导出模板">
          <div style="max-width: 600px;">
            <div style="font-size: 14px; color: #6b7280; margin-bottom: 16px;">当前账号使用的 DOCX 导出模板由运营人员统一配置，如需调整请联系客服。</div>

            <div :style="{
              padding: '18px',
              borderRadius: '12px',
              border: '2px solid #2563eb',
              background: '#eff6ff'
            }">
              <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px;">
                <div style="font-size: 15px; font-weight: 600; color: #111827;">{{ user.template || '未配置' }}</div>
                <span style="font-size: 11px; padding: 2px 8px; background: #2563eb; color: #fff; border-radius: 4px; font-weight: 500;">当前默认</span>
              </div>
              <div style="font-size: 13px; color: #6b7280; line-height: 1.5;">
                该模板将用于您的文章 DOCX 导出，包含字体、字号、颜色、行距、段距和页边距等排版配置。
              </div>
            </div>
          </div>
        </Tabs.TabPane>

        <Tabs.TabPane key="password" tab="密码修改">
          <div style="max-width: 400px; display: flex; flex-direction: column; gap: 16px;">
            <div style="display: flex; flex-direction: column; gap: 6px;">
              <label style="font-size: 13px; font-weight: 500; color: #374151;">原密码</label>
              <input v-model="passwordForm.oldPassword" type="password" placeholder="请输入原密码" style="padding: 10px 12px; border: 1px solid #e5e7eb; border-radius: 8px; font-size: 14px; outline: none;" />
            </div>
            <div style="display: flex; flex-direction: column; gap: 6px;">
              <label style="font-size: 13px; font-weight: 500; color: #374151;">新密码</label>
              <input v-model="passwordForm.newPassword" type="password" placeholder="请输入新密码" style="padding: 10px 12px; border: 1px solid #e5e7eb; border-radius: 8px; font-size: 14px; outline: none;" />
            </div>
            <div style="display: flex; flex-direction: column; gap: 6px;">
              <label style="font-size: 13px; font-weight: 500; color: #374151;">确认新密码</label>
              <input v-model="passwordForm.confirmPassword" type="password" placeholder="请再次输入新密码" style="padding: 10px 12px; border: 1px solid #e5e7eb; border-radius: 8px; font-size: 14px; outline: none;" />
            </div>
            <div>
              <button @click="changePassword" style="padding: 12px 24px; background: #2563eb; color: #fff; border: none; border-radius: 8px; font-size: 14px; font-weight: 500; cursor: pointer;">确认修改</button>
            </div>
          </div>
        </Tabs.TabPane>

        <Tabs.TabPane key="email" tab="邮件订阅">
          <div style="max-width: 400px; display: flex; flex-direction: column; gap: 16px;">
            <div v-if="!canEmailPush" style="padding: 12px 16px; background: #fef3c7; border: 1px solid #fde68a; border-radius: 8px; font-size: 13px; color: #92400e;">
              您当前的权益暂不支持邮件每日推送
            </div>
            <div style="display: flex; flex-direction: column; gap: 6px;">
              <label style="font-size: 13px; font-weight: 500; color: #374151;">接收邮箱</label>
              <input
                v-model="emailConfig.email"
                type="email"
                placeholder="请输入接收文章的邮箱地址"
                :disabled="!canEmailPush"
                style="padding: 10px 12px; border: 1px solid #e5e7eb; border-radius: 8px; font-size: 14px; outline: none;"
              />
            </div>
            <div style="display: flex; align-items: center; gap: 12px;">
              <label style="font-size: 13px; font-weight: 500; color: #374151;">接收每日推荐文章</label>
              <Switch
                v-model:checked="emailConfig.emailReceive"
                :disabled="!canEmailPush"
                :checkedValue="1"
                :unCheckedValue="0"
              />
            </div>
            <div style="display: flex; gap: 12px;">
              <button
                @click="handleSaveEmailConfig"
                :disabled="emailLoading || !canEmailPush"
                style="padding: 12px 24px; background: #2563eb; color: #fff; border: none; border-radius: 8px; font-size: 14px; font-weight: 500; cursor: pointer;"
              >
                {{ emailLoading ? '保存中...' : '保存配置' }}
              </button>
              <button
                @click="handleSendTestEmail"
                :disabled="testEmailLoading || !canEmailPush || !emailConfig.email"
                style="padding: 12px 24px; background: #fff; color: #2563eb; border: 1px solid #2563eb; border-radius: 8px; font-size: 14px; font-weight: 500; cursor: pointer;"
              >
                {{ testEmailLoading ? '发送中...' : '发送测试邮件' }}
              </button>
            </div>
          </div>
        </Tabs.TabPane>
      </Tabs>
    </Card>

  </div>
</template>
