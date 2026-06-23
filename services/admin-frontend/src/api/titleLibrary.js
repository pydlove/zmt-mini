import request from './request.js'

export function listTitles(params) {
  return request.get('/title-library', { params })
}

export function getTrackStats() {
  return request.get('/title-library/track-stats')
}

export function saveTitle(data) {
  if (data.id) {
    return request.put('/title-library/' + data.id, data)
  }
  return request.post('/title-library', data)
}

export function deleteTitle(id) {
  return request.delete('/title-library/' + id)
}

export function importTitles(excelFile) {
  const formData = new FormData()
  formData.append('excel', excelFile)
  return request.post('/title-library/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function matchCheck(date) {
  const params = {}
  if (date) params.date = date
  return request.get('/title-library/match-check', { params })
}

export function matchTodayTitles(date) {
  const params = {}
  if (date) params.date = date
  return request.post('/title-library/match-today', null, { params })
}

export function matchPreview(date) {
  const params = {}
  if (date) params.date = date
  return request.get('/title-library/match-preview', { params }).then(res => {
    // 兼容新旧格式：旧格式直接返回数组，新格式返回 { matches, needGenerateTracks }
    if (Array.isArray(res)) {
      return { matches: res, needGenerateTracks: [] }
    }
    return res || { matches: [], needGenerateTracks: [] }
  })
}

export function matchConfirm(date, matches) {
  return request.post('/title-library/match-confirm', matches, { params: { date } })
}

export function matchOne(date, userId, titleId) {
  return request.get('/title-library/match-one', { params: { date, userId, titleId } })
}

export function unbindRecommendation(titleId) {
  return request.delete('/title-library/' + titleId + '/recommendation')
}

export function batchUnbindRecommendations(titleIds) {
  return request.post('/title-library/batch-unbind', { titleIds })
}

export function generateTitles(data) {
  return request.post('/title-library/generate', data)
}

export function getGenerateStatus(taskId) {
  return request.get('/title-library/generate-status', { params: { taskId } })
}

export function cancelGenerate(taskId) {
  return request.post('/title-library/generate-cancel', null, { params: { taskId } })
}

export function generatePostsForToday(titleIds) {
  return request.post('/title-library/generate-posts-for-today', { titleIds })
}

export function getGeneratePostStatus(taskId) {
  return request.get('/title-library/generate-post-status', { params: { taskId } })
}

export function cancelGeneratePost(taskId) {
  return request.post('/title-library/generate-post-cancel', null, { params: { taskId } })
}

export function exportTitleLibrary(params) {
  return request.post('/title-library/export', null, {
    params,
    responseType: 'blob',
  })
}

export function exportTitleLibraryBatch(titleIds, baseName) {
  return request.post('/title-library/export', null, {
    params: { titleIds, baseName },
    responseType: 'blob',
    paramsSerializer: {
      indexes: null,
      serialize: (p) => {
        const parts = []
        if (p.titleIds) {
          for (const id of p.titleIds) {
            parts.push(`titleIds=${encodeURIComponent(id)}`)
          }
        }
        if (p.baseName) {
          parts.push(`baseName=${encodeURIComponent(p.baseName)}`)
        }
        return parts.join('&')
      }
    }
  })
}

export function exportTitleList(params) {
  return request.post('/title-library/export-titles', null, {
    params,
    responseType: 'blob',
  })
}

export function exportTitleListBatch(titleIds) {
  return request.post('/title-library/export-titles', null, {
    params: { titleIds },
    responseType: 'blob',
    paramsSerializer: {
      indexes: null,
      serialize: (p) => {
        const parts = []
        if (p.titleIds) {
          for (const id of p.titleIds) {
            parts.push(`titleIds=${encodeURIComponent(id)}`)
          }
        }
        return parts.join('&')
      }
    }
  })
}

export function importArticles(files) {
  const formData = new FormData()
  for (const file of files) {
    formData.append('files', file)
  }
  return request.post('/title-library/import-articles', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function sendTitleEmail(titleId) {
  return request.post('/title-library/' + titleId + '/send-email')
}

export function batchSendTitleEmail(titleIds) {
  return request.post('/title-library/batch-send-email', { titleIds })
}

export function getDefaultPromptTemplate(type) {
  return request.get('/prompt-templates/default', { params: { type } })
}

export function savePromptTemplate(data) {
  return request.post('/prompt-templates', data)
}

export function listUnrecommendedUsers(date, type) {
  const params = { date }
  if (type) params.type = type
  return request.get('/title-library/unrecommended-users', { params })
}

export function listUnpushedUsers(date) {
  return request.get('/title-library/unpushed-users', { params: { date } })
}

export function getPushOverview(params) {
  return request.get('/title-library/push-overview', { params })
}

export function batchPushEmail(data) {
  return request.post('/title-library/batch-push-email', data)
}

export function markTitleUsed(id) {
  return request.post('/title-library/' + id + '/used')
}

export function batchChangeTrack(titleIds, trackId) {
  return request.post('/title-library/batch-change-track', { titleIds, trackId })
}

export function clearRecommendationsByDate(date) {
  const params = {}
  if (date) params.date = date
  return request.post('/title-library/clear-recommendations', null, { params })
}

export function getUserHistory(userId) {
  return request.get('/title-library/user-history/' + userId)
}

export function getUserHomogeneity(userId) {
  return request.get('/title-library/user-homogeneity/' + userId)
}

export function listUserHomogeneity(params = {}) {
  const query = new URLSearchParams()
  if (params.keyword) query.append('keyword', params.keyword)
  if (params.sortField) query.append('sortField', params.sortField)
  if (params.sortOrder) query.append('sortOrder', params.sortOrder)
  if (params.page) query.append('page', params.page)
  if (params.pageSize) query.append('pageSize', params.pageSize)
  const qs = query.toString()
  return request.get('/title-library/user-homogeneity-list' + (qs ? '?' + qs : ''))
}

// 文章反馈相关
export function saveArticleFeedback(data) {
  return request.post('/title-library/feedback', data)
}

export function listArticleFeedback(params) {
  return request.get('/title-library/feedback', { params })
}

export function deleteArticleFeedback(id) {
  return request.delete('/title-library/feedback/' + id)
}

// 单标题生成文章（同步直接生成，已废弃，请使用 createGenerationTask）
export function generatePostSingle(id) {
  return request.post('/title-library/' + id + '/generate-post')
}

// 创建异步生成任务（插入任务表，由定时任务消费）
export function createGenerationTask(id) {
  return request.post('/title-library/' + id + '/create-generation-task')
}

// 查询标题生成任务状态
export function getTitleTaskStatus(id) {
  return request.get('/title-library/' + id + '/task-status')
}

// 获取文章内容（用于预览反馈）
export function getPostContent(id) {
  return request.get('/title-library/' + id + '/post-content')
}

// 去除AI味：调用本地 python 脚本
export function removeAiFlavor(params) {
  return request.post('/title-library/remove-ai-flavor', params)
}

// 自动插入图片：调用本地 python 脚本
export function autoInsertImages(data) {
  return request.post('/title-library/auto-insert-images', data)
}

// 批量标记 AI 味检测通过
export function batchAiPassed(titleIds) {
  return request.post('/title-library/batch-ai-passed', { titleIds })
}

// 批量标记复制提示词
export function batchCopied(titleIds) {
  return request.post('/title-library/batch-copied', { titleIds })
}

// 标记/取消标记AI味重
export function markAiFlavorHeavy(titleId, heavy = true) {
  return request.post('/title-library/mark-ai-flavor-heavy', null, { params: { titleId, heavy } })
}

// 确认标题
export function confirmTitle(id) {
  return request.post('/title-library/' + id + '/confirm')
}

// 批量确认标题
export function batchConfirm(titleIds) {
  return request.post('/title-library/batch-confirm', { titleIds })
}

// ========== 文章审核管理 ==========

// 查询待审核列表
export function listPendingReview(date) {
  return request.get('/title-library/pending-review', { params: { date } })
}

// 查询审核历史
export function listReviewHistory(date) {
  return request.get('/title-library/review-history', { params: { date } })
}

// 单条审核操作
export function reviewTitle(id, action) {
  return request.post('/title-library/' + id + '/review', null, { params: { action } })
}

// 批量审核操作
export function batchReview(titleIds, action) {
  return request.post('/title-library/batch-review', { titleIds, action })
}

// 发送文章邮件（带附件）
export function sendArticleEmail(titleId, email) {
  return request({
    url: `/title-library/${titleId}/send-article-email`,
    method: 'post',
    data: { email }
  })
}

// 生成文章贴图
export function generateImagePost(titleId, style = '') {
  const qs = style ? `?style=${encodeURIComponent(style)}` : ''
  return request.post(`/title-library/${titleId}/generate-image-post${qs}`)
}

// 批量生成文章贴图
export function batchGenerateImagePost(titleIds, style = '') {
  return request.post('/title-library/batch-generate-image-post', { titleIds, style })
}

// 查询文章贴图列表
export function getImagePosts(titleId) {
  return request.get(`/title-library/${titleId}/image-posts`)
}
