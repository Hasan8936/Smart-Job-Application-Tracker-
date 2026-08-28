import api from './axios'

const ACTIONS_KEY = 'smart-job-tracker-job-actions'

function withoutBlankParams(params) {
    const cleaned = {}
    for (const [key, value] of Object.entries(params)) {
        if (value === null || value === undefined || value === '') continue
        cleaned[key] = value
    }
    return cleaned
}

export async function listJobs(params = {}) {
    const response = await api.get('/jobs', { params: withoutBlankParams(params) })
    return response.data
}

export async function discoverJobs(request = {}) {
    const response = await api.post('/jobs/discover', request)
    return response.data
}

export async function getJob(id) {
    const response = await api.get(`/jobs/${id}`)
    return response.data
}

export function readJobActions() {
    try { return JSON.parse(localStorage.getItem(ACTIONS_KEY) || '{}') } catch { return {} }
}

export function setJobAction(id, action) {
    const actions = readJobActions()
    if (action) actions[id] = action
    else delete actions[id]
    localStorage.setItem(ACTIONS_KEY, JSON.stringify(actions))
    return actions
}

export async function setJobState(id, state) {
    const response = await api.post(`/jobs/${id}/${state === 'SAVED' ? 'save' : 'bookmark'}`)
    return response.data
}

export async function markJobApplied(id) {
    const response = await api.post(`/jobs/${id}/applied`)
    return response.data
}

export async function generateJobDocument(id, type) {
    const response = await api.post(`/jobs/${id}/documents/${type}`)
    return response.data
}

export async function listJobDocuments(id) {
    const response = await api.get(`/jobs/${id}/documents`)
    return response.data
}

export async function updateJobDocument(id, content) {
    const response = await api.put(`/jobs/documents/${id}`, { content })
    return response.data
}