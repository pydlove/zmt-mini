import request from './request.js'

export function listExportTemplates() {
  return request.get('/export-templates')
}

export function getExportTemplate(id) {
  return request.get('/export-templates/' + id)
}

export function saveExportTemplate(data) {
  if (data.id) {
    return request.put('/export-templates/' + data.id, data)
  }
  return request.post('/export-templates', data)
}

export function deleteExportTemplate(id) {
  return request.delete('/export-templates/' + id)
}

export function setDefaultExportTemplate(id) {
  return request.post('/export-templates/' + id + '/set-default')
}
