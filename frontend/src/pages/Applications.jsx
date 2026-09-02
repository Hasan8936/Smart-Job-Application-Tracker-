import React, { useEffect, useMemo, useState } from 'react'
import { Plus, Search } from 'lucide-react'
import api from '../api/axios'
import Layout from '../components/Layout'
import ApplicationCard from '../components/ApplicationCard'
import ApplicationDrawer from '../components/ApplicationDrawer'
import { STATUS_ORDER, statusLabel } from '../lib/status'

const emptyForm = { companyName: '', roleTitle: '', jobDescription: '', status: 'APPLIED', appliedDate: '' }

export default function Applications() {
  const [applications, setApplications] = useState([])
  const [loading, setLoading] = useState(true)
  const [query, setQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [form, setForm] = useState(emptyForm)
  const [editingId, setEditingId] = useState(null)

  useEffect(() => { fetchApps() }, [])

  async function fetchApps() {
    try {
      setLoading(true)
      const res = await api.get('/applications')
      setApplications(res.data)
    } catch (e) {
      console.error(e)
    } finally {
      setLoading(false)
    }
  }

  function openCreate() {
    setEditingId(null)
    setForm(emptyForm)
    setDrawerOpen(true)
  }

  function openEdit(app) {
    setEditingId(app.id)
    setForm({
      companyName: app.companyName || '',
      roleTitle: app.roleTitle || '',
      jobDescription: app.jobDescription || '',
      status: app.status || 'APPLIED',
      appliedDate: app.appliedDate || '',
    })
    setDrawerOpen(true)
  }

  async function submit(e) {
    e.preventDefault()
    try {
      if (editingId) {
        await api.put(`/applications/${editingId}`, form)
      } else {
        await api.post('/applications', form)
      }
      setDrawerOpen(false)
      fetchApps()
    } catch (e) {
      console.error(e)
    }
  }

  async function remove(app) {
    if (!window.confirm(`Delete the application to ${app.companyName}? This can't be undone.`)) return
    try {
      await api.delete(`/applications/${app.id}`)
      fetchApps()
    } catch (e) {
      console.error(e)
    }
  }

  const filtered = useMemo(() => {
    return applications.filter((a) => {
      const matchesStatus = statusFilter === 'ALL' || a.status === statusFilter
      const q = query.trim().toLowerCase()
      const matchesQuery = !q || a.companyName?.toLowerCase().includes(q) || a.roleTitle?.toLowerCase().includes(q)
      return matchesStatus && matchesQuery
    })
  }, [applications, query, statusFilter])

  return (
    <Layout
      title="Applications"
      subtitle={`${applications.length} application${applications.length === 1 ? '' : 's'} tracked`}
      actions={
        <button
          onClick={openCreate}
          className="inline-flex items-center gap-1.5 btn-gradient text-sm font-medium px-3.5 py-2 rounded-full"
        >
          <Plus size={15} /> Add application
        </button>
      }
    >
      <div className="flex flex-col sm:flex-row gap-3 mb-5">
        <div className="relative flex-1">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search by company or role"
            className="w-full pl-9 pr-3 py-2.5 rounded-lg border border-line bg-surface text-sm"
          />
        </div>
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="px-3 py-2.5 rounded-lg border border-line bg-surface text-sm sm:w-48"
        >
          <option value="ALL">All statuses</option>
          {STATUS_ORDER.map((s) => (
            <option key={s} value={s}>{statusLabel(s)}</option>
          ))}
        </select>
      </div>

      {loading ? (
        <div className="text-sm text-muted">Loading your applications…</div>
      ) : filtered.length === 0 ? (
        <div className="bg-surface border border-dashed border-line rounded-xl2 p-10 text-center">
          <p className="font-display text-ink text-lg mb-1">
            {applications.length === 0 ? 'No applications yet' : 'Nothing matches those filters'}
          </p>
          <p className="text-sm text-muted">
            {applications.length === 0 ? 'Add the first role you\'ve applied to.' : 'Try a different search term or status.'}
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {filtered.map((a) => (
            <ApplicationCard key={a.id} app={a} onEdit={openEdit} onDelete={remove} />
          ))}
        </div>
      )}

      <ApplicationDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        form={form}
        setForm={setForm}
        onSubmit={submit}
        isEditing={!!editingId}
      />
    </Layout>
  )
}
