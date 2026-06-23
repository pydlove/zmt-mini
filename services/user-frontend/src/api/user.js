import request from './request.js'

export function getUserPlan(userId) {
  return request.get('/users/' + userId + '/plan')
}

export function updateAvatar(userId, avatar) {
  return request.put('/users/' + userId + '/avatar', { avatar })
}

export function getEmailConfig(userId) {
  return request.get('/users/' + userId + '/email-config')
}

export function updateEmailConfig(userId, data) {
  return request.put('/users/' + userId + '/email', data)
}

export function sendTestEmail(userId) {
  return request.post('/users/' + userId + '/email/test')
}
