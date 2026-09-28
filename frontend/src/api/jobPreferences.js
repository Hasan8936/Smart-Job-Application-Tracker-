import api from './axios'

/** Saved preferences ({status: 'SAVED' | 'SKIPPED', roles, ...}), or null when the user was never asked. */
export async function getJobPreferences() {
    const response = await api.get('/job-preferences')
    return response.status === 204 || !response.data ? null : response.data
}

export async function saveJobPreferences(preferences) {
    const response = await api.put('/job-preferences', preferences)
    return response.data
}

export async function skipJobPreferences() {
    await api.post('/job-preferences/skip')
}

/** Only jobs matching the saved preferences (an empty page when none are saved). */
export async function listRecommendedJobs(params = {}) {
    const response = await api.get('/jobs/recommended', { params })
    return response.data
}
